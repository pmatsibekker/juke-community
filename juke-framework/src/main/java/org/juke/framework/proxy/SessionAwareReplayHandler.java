package org.juke.framework.proxy;

import org.juke.framework.storage.JukeStorage;
import org.juke.framework.exception.JukeReplayException;
import org.juke.framework.exception.TunerGeneratedException;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.metadata.JukeMethod;
import org.juke.framework.metadata.JukeConfigBuilder;
import org.juke.framework.metadata.JukeParser;
import org.juke.framework.metadata.DataProgramSchedule;
import org.juke.framework.session.JukeSessionContext;
import org.juke.framework.session.JukeSessionEntry;
import org.juke.framework.session.SessionRegistry;
import org.juke.framework.tuner.JukeCommandProcessorChain;
import org.juke.framework.tuner.ProcessObject;
import org.juke.framework.validation.FieldIgnoreRule;
import org.juke.framework.validation.InputDiffEngine;
import org.juke.framework.validation.InputValidationResult;
import org.juke.framework.validation.JukeIgnorableScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A per-session replay handler that reads replay data from a session-specific
 * {@link JukeStorage} DAO instead of the global static handler cache.
 * <p>
 * This handler is created for each request where a valid Juke session cookie
 * is present. It uses the {@link JukeSessionContext} (request-scoped) and
 * {@link SessionRegistry} (singleton) to obtain the correct DAO and
 * schedule for the current test session.
 * <p>
 * <b>Key difference from {@link ReplayHandler}:</b> there is no static cache,
 * and the DAO + schedule are retrieved from the {@link JukeSessionEntry}
 * (which lives in the thread-safe {@link SessionRegistry}) rather than
 * from a shared static map.
 *
 * @param <T> the proxy interface type
 */
public class SessionAwareReplayHandler<T> extends BaseHandler<T> {

    private static final Logger LOG = LoggerFactory.getLogger(SessionAwareReplayHandler.class);

    /** Stateless, thread-safe; reused across calls. */
    private static final InputDiffEngine INPUT_DIFF = new InputDiffEngine();
    private static final ObjectMapper COMPARE_MAPPER = new ObjectMapper();

    private final JukeSessionContext sessionContext;
    private final SessionRegistry registry;

    public SessionAwareReplayHandler(T service, Class<T> interfaceClass,
                                     JukeSessionContext sessionContext,
                                     SessionRegistry registry) {
        this.setService(service);
        this.setInterfaceClass(interfaceClass);
        this.sessionContext = sessionContext;
        this.registry = registry;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // Skip Object methods
        if (!JukeMethodFilter.shouldIntercept(method)) {
            if ("toString".equals(method.getName()))
                return "JukeSessionReplayProxy[" + this.getInterfaceClass().getSimpleName() + "]";
            if ("hashCode".equals(method.getName()))
                return System.identityHashCode(proxy);
            if ("equals".equals(method.getName()))
                return proxy == args[0];
            return null;
        }

        // Look up the session entry
        Optional<JukeSessionEntry> entryOpt = registry.get(sessionContext.getSessionId());
        if (!entryOpt.isPresent()) {
            // Session expired or invalidated — fall through to real service
            LOG.info("Juke session {} expired, falling through to real service for {}",
                    sessionContext.getSessionId(), method.getName());
            return method.invoke(this.getService(), args);
        }

        JukeSessionEntry entry = entryOpt.get();
        JukeStorage dao = entry.getDao();
        Class<?> targetClass = this.getInterfaceClass();

        // Ensure JukeClass metadata is available for this interface
        if (!JukeClass.instance().containsKey(targetClass.getCanonicalName())) {
            JukeClass jukeClass = JukeConfigBuilder.set(targetClass).build();
            JukeClass.instance().put(targetClass.getCanonicalName(), jukeClass);
            this.setJukeClass(jukeClass);
        }

        // Get the per-session schedule for this interface
        DataProgramSchedule schedule = entry.getScheduleFor(targetClass);

        // Resolve methods from the target type
        Method[] methods = targetClass.getMethods();
        List<Method> foundMethods = JukeParser.isInMethods(method.getName(), methods);

        String parsedMethodName = JukeParser.getMethodName(targetClass, method);
        String callerFullName = targetClass.getName() + ".$" + parsedMethodName;

        // Detect Class<?> argument for type-aware replay
        String typeDiscriminator = TypeDiscriminatorUtil.extractTypeDiscriminator(method, args);
        Class<?> runtimeType = TypeDiscriminatorUtil.extractTypeDiscriminatorClass(method, args);

        TunerGeneratedException tge = null;
        ProcessObject po = new ProcessObject();
        Object result = null;

        try {
            for (int i = 0; i < foundMethods.size(); i++) {
                if (JukeParser.isAssignableFromArguments(foundMethods.get(i), args)
                        && callerFullName.equalsIgnoreCase(
                        targetClass.getName() + ".$" + JukeParser.getMethodName(targetClass, method))) {
                    try {
                        String fullName = callerFullName;

                        // Build short name matching what RecordHandler writes
                        String shortName = JukeNameFormatter.buildShortIdentifier(
                                targetClass, parsedMethodName, typeDiscriminator);

                        // Try short discriminated name first, then short plain, then legacy full names
                        String sequencedFullName;
                        if (typeDiscriminator != null && schedule.size(shortName) > 0) {
                            sequencedFullName = schedule.getNextAvailable(shortName);
                        } else {
                            String shortPlain = JukeNameFormatter.buildShortIdentifier(
                                    targetClass, parsedMethodName, null);
                            if (schedule.size(shortPlain) > 0) {
                                sequencedFullName = schedule.getNextAvailable(shortPlain);
                            } else {
                                String discriminatedName = TypeDiscriminatorUtil.buildRecordIdentifier(
                                        fullName, typeDiscriminator);
                                if (typeDiscriminator != null && schedule.size(discriminatedName) > 0) {
                                    sequencedFullName = schedule.getNextAvailable(discriminatedName);
                                } else {
                                    sequencedFullName = schedule.getNextAvailable(fullName);
                                }
                            }
                        }

                        LOG.info("Session {} invoke() reading entry '{}' from DAO path: {}",
                                sessionContext.getSessionId(), sequencedFullName, dao.path());

                        // ── Per-call input comparison ─────────────────────────────────────
                        // Try to read the .args.json sidecar so we can compare what
                        // the recording expected vs what the test actually sent.
                        // This powers the report endpoint's deviation detection.
                        {
                            Instant callAt = Instant.now();
                            List<Object> recordedArgs = Collections.emptyList();
                            List<Object> actualArgsList = args != null
                                    ? new ArrayList<>(Arrays.asList(args))
                                    : Collections.emptyList();
                            boolean inputMatched = true; // conservative default when no sidecar
                            try {
                                String argsJson = dao.asString(sequencedFullName + ".args");
                                @SuppressWarnings("unchecked")
                                Map<String, Object> argsData =
                                        new ObjectMapper().readValue(argsJson, Map.class);
                                @SuppressWarnings("unchecked")
                                List<Object> rec = (List<Object>) argsData
                                        .getOrDefault("arguments", Collections.emptyList());
                                recordedArgs = rec;
                                inputMatched = inputMatches(recordedArgs, args);
                            } catch (Exception noSidecar) {
                                // .args.json not present — leave defaults (matched=true, empty lists)
                            }
                            int seq    = JukeSessionEntry.extractSequenceFromKey(sequencedFullName);
                            String meth = JukeSessionEntry.extractMethodFromKey(sequencedFullName);
                            entry.recordCall(sequencedFullName, callAt, meth, seq,
                                    recordedArgs, actualArgsList, inputMatched);
                        }

                        String json = dao.asString(sequencedFullName);

                        // Use type-aware deserialization when we have a runtime type discriminator
                        if (runtimeType != null && runtimeType != Object.class) {
                            result = dao.readFromFileAsType(targetClass, sequencedFullName, runtimeType);
                        } else {
                            result = dao.readFromFile(targetClass, sequencedFullName);
                        }

                        po.setSignature(sequencedFullName);
                        po.setJson(json);
                        po.setObject(result);
                        JukeCommandProcessorChain.execute(po);

                        break;
                    } catch (TunerGeneratedException tgexception) {
                        tge = tgexception;
                    } catch (Exception e) {
                        LOG.info("Exception found: {} -> {}", e.getClass().getSimpleName(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            throw new JukeReplayException("Unexpected invocation exception during session replay of "
                    + method.getName() + ": " + e.getMessage(), e);
        }

        if (tge != null) {
            throw tge.getWrappedException();
        }
        return result;
    }

    /**
     * Creates a JDK dynamic proxy backed by a {@code SessionAwareReplayHandler}.
     *
     * @param service        the real service implementation (used as fallback)
     * @param interfaceClass the proxy interface
     * @param sessionContext the request-scoped session context
     * @param registry       the session registry singleton
     * @param <T>            the interface type
     * @return a proxy instance implementing {@code interfaceClass}
     */
    public static <T> T newProxy(T service, Class<T> interfaceClass,
                                 JukeSessionContext sessionContext,
                                 SessionRegistry registry) {
        return JukeProxyFactory.createInterfaceProxy(
                interfaceClass,
                new SessionAwareReplayHandler<>(service, interfaceClass, sessionContext, registry));
    }

    // ── Arg-comparison helpers ────────────────────────────────────────────────

    /**
     * Returns {@code true} when the live arguments match what was recorded,
     * ignoring fields marked {@link org.juke.framework.annotation.JukeIgnorable}.
     *
     * <p>Both sides are serialized to JSON trees and compared structurally by
     * the {@link InputDiffEngine}, so a recorded argument (parsed from the
     * {@code .args.json} sidecar into maps/lists) is compared apples-to-apples
     * against the live object — not by {@code toString()}, which never matched
     * for non-trivial DTOs. Ignore rules are scanned off the live argument
     * types so {@code @JukeIgnorable} (e.g. a randomly generated confirmation
     * number) is skipped, the same way the Enterprise scenario service skips
     * database-seeded rules.
     *
     * <p>Any failure to serialize or diff is treated as a match, preserving the
     * previous conservative default rather than spuriously flagging a deviation.
     */
    private boolean inputMatches(List<Object> recordedArgs, Object[] liveArgs) {
        try {
            List<Object> liveList = liveArgs != null
                    ? Arrays.asList(liveArgs) : Collections.emptyList();
            JsonNode recordedNode = COMPARE_MAPPER.valueToTree(recordedArgs);
            JsonNode liveNode = COMPARE_MAPPER.valueToTree(liveList);
            List<FieldIgnoreRule> rules = JukeIgnorableScanner.scanArgs(liveArgs);
            InputValidationResult result = INPUT_DIFF.diff(
                    getInterfaceClass().getName(), "", 0, recordedNode, liveNode, rules);
            return !result.hasDiffs();
        } catch (RuntimeException e) {
            LOG.warn("Input comparison failed for {}; treating as matched: {}",
                    getInterfaceClass().getName(), e.toString());
            return true;
        }
    }
}
