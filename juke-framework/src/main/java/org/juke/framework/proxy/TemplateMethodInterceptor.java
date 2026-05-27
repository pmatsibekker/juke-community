package org.juke.framework.proxy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.config.ConfigUtil;
import org.juke.framework.config.JukeSpringContextHolder;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.juke.framework.storage.JukeStorage;
import org.juke.framework.storage.JukeHelper;
import org.juke.framework.storage.JukeTransformerUtil;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.metadata.DataProgramSchedule;
import org.juke.framework.metadata.JukeStateBuilder;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.JukeSessionEntry;
import org.juke.framework.session.SessionRegistry;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Method-level interceptor for Spring Template classes (RestTemplate, JdbcTemplate, etc.).
 * <p>
 * This handler sits between the caller and the real template instance. In <b>record</b>
 * mode it delegates to the real template, captures the return value, and writes it to
 * the Juke ZIP via {@link JukeHelper}. In <b>replay</b> mode it reads the previously
 * recorded value from the ZIP instead of hitting the real upstream system.
 * <p>
 * Method call sequencing uses the existing {@link DataProgramSchedule} infrastructure
 * so that repeated calls to the same method are indexed identically to DAO recordings
 * (1-based, clamped at max during replay).
 *
 * <h3>Recording identifier format</h3>
 * <pre>
 * {templateName}.{methodName}.{sequenceIndex}.json
 * </pre>
 * For example: {@code RestTemplate.getForObject.1.json}, {@code JdbcTemplate.queryForList.2.json}
 *
 * @see org.juke.framework.annotation.Juke
 */
public class TemplateMethodInterceptor implements InvocationHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TemplateMethodInterceptor.class);

    /** Methods that are never intercepted. */
    private static final Set<String> DEFAULT_EXCLUDED = new HashSet<>(
            Arrays.asList("toString", "hashCode", "equals", "getClass", "notify",
                    "notifyAll", "wait", "clone", "finalize"));

    private final Object realTemplate;
    private final String templateName;
    private final String jukeState;
    private final Set<String> excludedMethods;


    /** Schedule built from existing ZIP entries for replay. */
    private DataProgramSchedule schedule;
    private boolean replayInitialized = false;

    /** ReentrantLock replaces synchronized to avoid pinning Java 21 virtual threads. */
    private final ReentrantLock lock = new ReentrantLock();

    private static final ObjectMapper objectMapper;
    static {
        objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    /**
     * @param realTemplate    the actual template instance (e.g. a real RestTemplate)
     * @param templateName    logical name used as prefix in identifiers (e.g. "RestTemplate")
     * @param jukeState       "record", "replay", or "ignore"
     * @param extraExclusions additional method names to skip
     */
    public TemplateMethodInterceptor(Object realTemplate,
                                     String templateName,
                                     String jukeState,
                                     String[] extraExclusions) {
        this.realTemplate = realTemplate;
        this.templateName = templateName;
        this.jukeState = jukeState;

        Set<String> excluded = new HashSet<>(DEFAULT_EXCLUDED);
        if (extraExclusions != null) {
            Collections.addAll(excluded, extraExclusions);
        }
        this.excludedMethods = Collections.unmodifiableSet(excluded);
    }

    // ---------------------------------------------------------------- Core

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        String methodName = method.getName();

        // Never intercept infrastructure methods
        if (excludedMethods.contains(methodName)) {
            return method.invoke(realTemplate, args);
        }

        // --- NEW: check for active per-session playback context ---
        try {
            ApplicationContext appCtx = JukeSpringContextHolder.get();
            if (appCtx != null) {
                JukeSessionContext sessionCtx = appCtx.getBean(JukeSessionContext.class);
                if (sessionCtx.isPlaybackActive()) {
                    SessionRegistry registry = appCtx.getBean(SessionRegistry.class);
                    if (registry.get(sessionCtx.getSessionId()).isPresent()) {
                        LOG.debug("Session-aware replay active for template {} (session {})",
                                templateName, sessionCtx.getSessionId());
                        return handleSessionReplay(method, args, sessionCtx, registry);
                    }
                }
            }
        } catch (Exception e) {
            LOG.trace("No active Juke session context for template: {}", e.getMessage());
        }
        // --- END session-aware check ---

        String effectiveState = resolveState();

        if (JukeState.RECORD.equalsIgnoreCase(effectiveState)) {
            return handleRecord(method, args);
        } else if (JukeState.REPLAY.equalsIgnoreCase(effectiveState)) {
            return handleReplay(method, args);
        } else {
            // Passthrough — IGNORE / NONE / DISABLE
            return method.invoke(realTemplate, args);
        }
    }

    private Object handleSessionReplay(Method method, Object[] args,
                                       JukeSessionContext sessionCtx,
                                       SessionRegistry registry) throws Throwable {
        lock.lock();
        try {
            Optional<JukeSessionEntry> entryOpt = registry.get(sessionCtx.getSessionId());
            if (!entryOpt.isPresent()) {
                LOG.info("Juke session {} expired, falling through to real template for {}",
                        sessionCtx.getSessionId(), method.getName());
                return method.invoke(realTemplate, args);
            }

            JukeSessionEntry entry = entryOpt.get();
            JukeStorage dao = entry.getDao();
            DataProgramSchedule sessionSchedule = entry.getScheduleFor(realTemplate.getClass());

            String methodName = method.getName();
            String baseId = templateName + "." + methodName;
            String sequencedId = sessionSchedule.getNextAvailable(baseId);

            try {
                // Try to read the type metadata first for correct deserialization
                String typeMeta = null;
                try {
                    String meta = dao.asString(sequencedId + ".type");
                    typeMeta = (meta != null && !meta.trim().isEmpty()) ? meta.trim() : null;
                } catch (Exception e) {
                    // Ignore
                }

                Object result;
                if (typeMeta != null) {
                    Class<?> runtimeType = Class.forName(typeMeta);
                    String json = dao.asString(sequencedId);
                    result = JukeTransformerUtil.readValueAsType(json, runtimeType);
                } else {
                    // Fall back to raw string
                    String json = dao.asString(sequencedId);
                    result = objectMapper.readTree(json);
                }

                // ── Per-call input comparison & recording ───────────────────────────────
                {
                    java.time.Instant callAt = java.time.Instant.now();
                    java.util.List<Object> recordedArgs = java.util.Collections.emptyList();
                    java.util.List<Object> actualArgsList = args != null
                            ? new java.util.ArrayList<>(Arrays.asList(args))
                            : java.util.Collections.emptyList();
                    boolean inputMatched = true;
                    try {
                        String argsJson = dao.asString(sequencedId + ".args");
                        @SuppressWarnings("unchecked")
                        Map<String, Object> argsData =
                                new ObjectMapper().readValue(argsJson, Map.class);
                        @SuppressWarnings("unchecked")
                        java.util.List<Object> rec = (java.util.List<Object>) argsData
                                .getOrDefault("arguments", java.util.Collections.emptyList());
                        recordedArgs = rec;

                        com.fasterxml.jackson.databind.JsonNode recordedNode = new ObjectMapper().valueToTree(recordedArgs);
                        com.fasterxml.jackson.databind.JsonNode liveNode = new ObjectMapper().valueToTree(actualArgsList);
                        java.util.List<org.juke.framework.validation.FieldIgnoreRule> rules =
                                org.juke.framework.validation.JukeIgnorableScanner.scanArgs(args);
                        org.juke.framework.validation.InputValidationResult validationResult =
                                new org.juke.framework.validation.InputDiffEngine().diff(
                                        realTemplate.getClass().getName(), "", 0, recordedNode, liveNode, rules);
                        inputMatched = !validationResult.hasDiffs();
                    } catch (Exception noSidecar) {
                        // ignore
                    }
                    entry.recordCall(sequencedId, callAt, methodName, JukeSessionEntry.extractSequenceFromKey(sequencedId),
                            recordedArgs, actualArgsList, inputMatched);
                }

                validateSessionInputArgs(dao, sequencedId, method, args);

                LOG.debug("REPLAY [{}] -> {}", sequencedId,
                        result != null ? result.getClass().getSimpleName() : "null");
                return result;
            } catch (Exception e) {
                LOG.error("Failed to replay template result for {}: {}", sequencedId, e.getMessage());
                throw new JukeAccessException("Replay failed for " + sequencedId + ": " + e.getMessage());
            }
        } finally {
            lock.unlock();
        }
    }

    private void validateSessionInputArgs(JukeStorage dao, String sequencedId, Method method, Object[] args) {
        String mode = ConfigUtil.getArgsValidationMode();
        if ("off".equalsIgnoreCase(mode)) {
            return;
        }

        java.util.List<Object> recordedArgs = null;
        try {
            String json = dao.asString(sequencedId + ".args");
            if (json != null && !json.trim().isEmpty()) {
                org.juke.framework.storage.InputArgsRecord recorded =
                        JukeRuntimeHolder.current().marshaller().readValue(json, org.juke.framework.storage.InputArgsRecord.class);
                recordedArgs = recorded.getArguments();
            }
        } catch (Exception e) {
            // Ignore
        }

        if (recordedArgs == null) {
            return;
        }

        java.util.List<Object> currentArgs = args != null ? Arrays.asList(args) : new java.util.ArrayList<>();
        java.util.List<Object> recArgs = recordedArgs;

        boolean match;
        try {
            ObjectMapper om = JukeRuntimeHolder.current().marshaller();
            String currentJson = om.writeValueAsString(currentArgs);
            String recordedJson = om.writeValueAsString(recArgs);
            match = currentJson.equals(recordedJson);
        } catch (Exception e) {
            return;
        }

        if (!match) {
            String message = "Recorded args: " + recArgs + "  Current args: " + currentArgs;
            if ("strict".equalsIgnoreCase(mode)) {
                throw new org.juke.framework.exception.JukeInputMismatchException(sequencedId, message);
            } else {
                LOG.warn("INPUT MISMATCH [{}]: {}", sequencedId, message);
            }
        }
    }

    // -------------------------------------------------------------- Record

    private Object handleRecord(Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        String baseId = templateName + "." + methodName;

        // Invoke the real template
        Object result = method.invoke(realTemplate, args);

        try {
            // JukeHelper.writeToFile delegates to addIncrementEntry which
            // appends .N.json automatically via ZipUtil's DataProgramSchedule
            JukeHelper.getInstance().writeToFile(baseId, result);
            LOG.debug("RECORD [{}] -> {}", baseId, result != null ? result.getClass().getSimpleName() : "null");

            // Record input arguments for replay-time validation
            JukeHelper.getInstance().writeInputArgs(baseId, method, args);

            // Store the runtime type so replay can deserialize correctly. Use the
            // binary name (getName, e.g. "a.b.Outer$Inner") — replay reloads it
            // via Class.forName, which rejects the dotted canonical name for
            // nested classes (records/DTOs declared inside another type).
            if (result != null) {
                JukeHelper.getInstance().writeTypeMetadata(baseId, result.getClass().getName());
            }
        } catch (JukeAccessException e) {
            LOG.error("Failed to record template result for {}: {}", baseId, e.getMessage());
        }

        return result;
    }

    // -------------------------------------------------------------- Replay

    private Object handleReplay(Method method, Object[] args) throws Throwable {
        lock.lock();
        try {
        String methodName = method.getName();
        String baseId = templateName + "." + methodName;

        ensureReplayInitialized();

        // Let DataProgramSchedule handle the 1-based index and clamping
        String sequencedId = schedule.getNextAvailable(baseId);

        try {
            // Try to read the type metadata first for correct deserialization
            String typeMeta = readTypeMeta(sequencedId);
            Object result;
            JukeStorage dao = JukeRuntimeHolder.current().storage();
            if (typeMeta != null) {
                Class<?> runtimeType = Class.forName(typeMeta);
                String json = dao.asString(sequencedId);
                result = JukeTransformerUtil.readValueAsType(json, runtimeType);
            } else {
                // Fall back to raw string
                String json = dao.asString(sequencedId);
                result = objectMapper.readTree(json);
            }

            // Validate input arguments match what was recorded
            JukeHelper.validateInputArgs(sequencedId, method, args);

            LOG.debug("REPLAY [{}] -> {}", sequencedId,
                    result != null ? result.getClass().getSimpleName() : "null");
            return result;
        } catch (Exception e) {
            LOG.error("Failed to replay template result for {}: {}", sequencedId, e.getMessage());
            throw new JukeAccessException("Replay failed for " + sequencedId + ": " + e.getMessage());
        }
    } finally {
        lock.unlock();
    }
}

    /**
     * Reads the ".type" sidecar entry for a given identifier.
     * Returns null if no type metadata exists.
     */
    private String readTypeMeta(String identifier) {
        try {
            String meta = JukeRuntimeHolder.current().storage().asString(identifier + ".type");
            return (meta != null && !meta.trim().isEmpty()) ? meta.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Initialises the {@link DataProgramSchedule} from the ZIP file entries
     * so that replay knows the available sequence counts for each method.
     */
    private void ensureReplayInitialized() {
        if (replayInitialized) {
            return;
        }

        // Ensure the DAO is set up — read via the runtime holder (Phase 3 C).
        if (JukeRuntimeHolder.current().storage() == null) {
            JukeHelper.setJukeDao(new JukeZipDAOImpl(
                    ConfigUtil.getJukePath(), ConfigUtil.getJukeZip()));
        }

        JukeStateBuilder built = new JukeStateBuilder.Builder(
                JukeRuntimeHolder.current().storage().getFileNames()).build();
        this.schedule = built.getSchedule();
        this.replayInitialized = true;
    }

    // -------------------------------------------------------------- State

    /**
     * Resolves the effective Juke state: field-level annotation wins,
     * then global state, then default to IGNORE.
     */
    private String resolveState() {
        // If the annotation specified an explicit record/replay, use it
        if (JukeState.RECORD.equalsIgnoreCase(jukeState)
                || JukeState.REPLAY.equalsIgnoreCase(jukeState)) {
            return jukeState;
        }
        // "juke" means follow the global state — read from the runtime holder
        // (Phase 3 Step C) so that session-scoped runtimes could override the
        // legacy static later.
        if (JukeState.JUKE.equalsIgnoreCase(jukeState)) {
            String global = JukeRuntimeHolder.current().mode().legacyString();
            if (global != null && (JukeState.RECORD.equalsIgnoreCase(global)
                    || JukeState.REPLAY.equalsIgnoreCase(global))) {
                return global;
            }
        }
        return JukeState.IGNORE;
    }

    // -------------------------------------------------------------- Accessors

    public Object getRealTemplate() {
        return realTemplate;
    }

    public String getTemplateName() {
        return templateName;
    }
}

