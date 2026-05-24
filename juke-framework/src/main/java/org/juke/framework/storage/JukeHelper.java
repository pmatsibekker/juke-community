package org.juke.framework.storage;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.juke.framework.config.ConfigUtil;
import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.exception.JukeInputMismatchException;
import org.juke.framework.exception.JukeStorageException;
import org.juke.framework.runtime.JukeRuntimeHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;


public class JukeHelper {
	private static final Logger LOG = LoggerFactory.getLogger(JukeHelper.class);
	// Phase 5.5 — singleton handle, never reassigned after class init.
	private static final JukeHelper instance = new JukeHelper();

	/**
	 * Returns the ObjectMapper from the current {@link JukeRuntimeHolder}.
	 * Phase 3 Step C migration — reads from the runtime holder instead of the
	 * legacy {@link Marshaller} static.
	 */
	private static ObjectMapper mapper() {
		return JukeRuntimeHolder.current().marshaller();
	}

	/**
	 * Returns the DAO from the current {@link JukeRuntimeHolder}.
	 * Phase 3 Step D: the {@code jukeDAO} static field is gone and the holder
	 * is the single source of truth.
	 */
	private static JukeStorage dao() {
		return JukeRuntimeHolder.current().storage();
	}
	static {
		try {
			// Install the bootstrap DAO into the holder.
			setJukeDao(new JukeZipDAOImpl(ConfigUtil.getJukePath(), ConfigUtil.getJukeZip()));
		} catch (JukeAccessException e) {
			LOG.error("Failed to initialize Juke DAO in JukeHelper static init for path={}, zip={}: {}",
					ConfigUtil.getJukePath(), ConfigUtil.getJukeZip(), e.getMessage(), e);
		}
	}
	public static JukeHelper getInstance() {
		return instance;
	}


	public static JukeStorage getJukeDAO() {
		// Phase 3 Step D — read the DAO directly from the runtime holder.
		return dao();
	}
	public static void setJukeDao(JukeStorage dao) {
		// Phase 3 Step D — the holder is authoritative; install the DAO into
		// it atomically.
		JukeRuntimeHolder.update(r -> r.withStorage(dao));
	}
	
	
	public void write() {
		dao().write();
	}
	public boolean writeToFile(String identifier, Object o) {
		String content= null;
		try {
			content = JukeTransformerUtil.writeValueAsString(o);
			//JukeClass JukeClass=  JukeConfigBuilder.set(ISampleService.class).build();
			//content= objectMapper.writeValueAsString(o);
			return dao().writeToFile(identifier, content);

		}catch (Exception e) {
			throw new JukeAccessException ("Could not write "+ identifier);
		}
	}

	/**
	 * Writes type metadata alongside a recorded entry so that replay can
	 * deserialize to the correct concrete type. The metadata is stored as a
	 * sidecar entry with a ".type" suffix sharing the same sequence number.
	 * <p>
	 * The entry name format is {@code {baseId}.{seq}.type.json} so that replay
	 * can look up {@code {baseId}.{seq}.type} after resolving the sequenced id.
	 *
	 * @param identifier the base identifier (same one passed to writeToFile)
	 * @param typeName   the canonical class name of the recorded result
	 */
	public void writeTypeMetadata(String identifier, String typeName) {
		try {
			int seq = dao().getCurrentSequence(identifier);
			String exactKey = identifier + "." + seq + ".type.json";
			dao().writeDirectEntry(exactKey, typeName);
		} catch (Exception e) {
			LOG.warn("Could not write type metadata for {}: {}", identifier, e.getMessage());
		}
	}

	// ----------------------------------------------------------------
	// Input-args recording (sidecar .args entries)
	// ----------------------------------------------------------------

	/**
	 * Records the input arguments of a method invocation as a sidecar
	 * {@code .args.json} entry sharing the same sequence number as the
	 * response entry that was just written via {@link #writeToFile}.
	 * <p>
	 * Must be called <b>after</b> {@code writeToFile(identifier, result)}
	 * so that the sequence counter has already been incremented.
	 * <p>
	 * Entry name format: {@code {baseId}.{seq}.args.json}
	 *
	 * @param identifier the base identifier (same one passed to writeToFile)
	 * @param method     the java.lang.reflect.Method being recorded
	 * @param args       the actual arguments (may be null for zero-arg methods)
	 */
	public void writeInputArgs(String identifier, Method method, Object[] args) {
		try {
			int seq = dao().getCurrentSequence(identifier);

			List<String> paramTypes = new ArrayList<>();
			for (Class<?> pt : method.getParameterTypes()) {
				paramTypes.add(pt.getCanonicalName());
			}

			List<Object> argList = args != null ? Arrays.asList(args) : new ArrayList<>();

			InputArgsRecord record = new InputArgsRecord(method.getName(), paramTypes, argList);
			String json = mapper().writerWithDefaultPrettyPrinter().writeValueAsString(record);

			String exactKey = identifier + "." + seq + ".args.json";
			dao().writeDirectEntry(exactKey, json);
			LOG.debug("Recorded input args for {}.{}", identifier, seq);
		} catch (Exception e) {
			LOG.warn("Could not write input args for {}: {}", identifier, e.getMessage());
		}
	}

	/**
	 * Reads the {@code .args} sidecar entry for a given sequenced identifier.
	 *
	 * @param sequencedId e.g. "OrderService.bill.1"
	 * @return the deserialized {@link InputArgsRecord}, or null if no args
	 *         entry exists (e.g. old recordings)
	 */
	public static InputArgsRecord readInputArgs(String sequencedId) {
		try {
			String json = dao().asString(sequencedId + ".args");
			if (json != null && !json.trim().isEmpty()) {
				return mapper().readValue(json, InputArgsRecord.class);
			}
		} catch (JukeStorageException | java.io.IOException e) {
			// Silently ignore missing .args.json files (legacy zips)
			LOG.trace("No input-args sidecar for {}: {}", sequencedId, e.getMessage());
		} catch (Exception e) {
			LOG.warn("Error reading input-args for {}: {}", sequencedId, e.getMessage());
		}
		return null;
	}

	/**
	 * Validates that the current replay arguments match what was recorded.
	 * <p>
	 * Behaviour depends on the {@code juke.args-validation} configuration:
	 * <ul>
	 *   <li><b>warn</b> (default) — logs a WARNING on mismatch</li>
	 *   <li><b>strict</b> — throws {@link JukeInputMismatchException}</li>
	 *   <li><b>off</b> — does nothing</li>
	 * </ul>
	 *
	 * @param sequencedId the sequenced entry id (e.g. "OrderService.bill.1")
	 * @param method      the method being replayed
	 * @param args        the current arguments
	 */
	public static void validateInputArgs(String sequencedId, Method method, Object[] args) {
		String mode = ConfigUtil.getArgsValidationMode();
		if ("off".equalsIgnoreCase(mode)) {
			return;
		}

		InputArgsRecord recorded = readInputArgs(sequencedId);
		if (recorded == null) {
			// No recorded args (old zip) — nothing to validate
			return;
		}

		List<Object> currentArgs = args != null ? Arrays.asList(args) : new ArrayList<>();
		List<Object> recordedArgs = recorded.getArguments() != null
				? recorded.getArguments() : new ArrayList<>();

		// Compare serialized JSON forms for deep equality
		boolean match;
		try {
			ObjectMapper om = mapper();
			String currentJson = om.writeValueAsString(currentArgs);
			String recordedJson = om.writeValueAsString(recordedArgs);
			match = currentJson.equals(recordedJson);
		} catch (Exception e) {
			LOG.warn("Could not serialize args for comparison on {}: {}", sequencedId, e.getMessage());
			return;
		}

		if (!match) {
			String message = "Recorded args: " + recordedArgs + "  Current args: " + currentArgs;
			if ("strict".equalsIgnoreCase(mode)) {
				throw new JukeInputMismatchException(sequencedId, message);
			} else {
				// warn mode (default)
				LOG.warn("INPUT MISMATCH [{}]: {}", sequencedId, message);
			}
		}
	}
}
