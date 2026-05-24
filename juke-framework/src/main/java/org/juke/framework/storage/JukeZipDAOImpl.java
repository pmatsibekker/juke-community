package org.juke.framework.storage;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.exception.JukeStorageException;
import org.juke.framework.proxy.JukeNameFormatter;
import org.juke.framework.metadata.JukeClass;
import org.juke.framework.runtime.JukeRuntimeHolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class JukeZipDAOImpl implements JukeStorage {

	/**
	 * Returns the ObjectMapper from the current {@link JukeRuntimeHolder}.
	 * Phase 3 Step C migration — reads from the runtime holder instead of the
	 * legacy {@link Marshaller} static so that future per-session runtimes can
	 * supply their own mapper without touching globals.
	 */
	private static ObjectMapper mapper() {
		return JukeRuntimeHolder.current().marshaller();
	}

	private static final String JSON = ".json";

	private static final Logger LOG = LoggerFactory.getLogger(JukeZipDAOImpl.class);
	public static final String juke = "juke";
	private static final String ZIP_SUFFIX = ".zip";

	private ZipUtil zipper = null;
	/**
	 * Folder root that hosts every recording this DAO can enumerate via the
	 * {@link JukeStorage} recording-store methods. For session-scoped
	 * (per-recording) instances this is the parent of the bound ZIP; for
	 * the autoconfig-registered folder-rooted bean it is the configured
	 * {@code juke.storage.folder.path} directory.
	 */
	private final String folderRoot;
	HashMap<String,JukeClass> JukeClassMap= null;
	public JukeZipDAOImpl(String path, String zipName) {
		path = FilenameUtils.normalize(path);
		zipName = FilenameUtils.normalize(zipName);
		this.folderRoot = path;
		// Ensure the recording directory exists before ZipUtil tries to write
		// to it — without this, record/end throws "The system cannot find the
		// path specified" when the directory was never created manually.
		new java.io.File(path).mkdirs();
		zipper = new ZipUtil(path, zipName);
		// JukeClassMap is loaded lazily on first readFromFile() call

	}

	/**
	 * Folder-rooted constructor — creates an instance that supports the
	 * Phase 5.A {@link JukeStorage} recording-store API
	 * ({@link #listRecordings}, {@link #loadRecording},
	 * {@link #saveRecording}) but is <strong>not</strong> bound to a
	 * specific recording for the per-recording API. Calls to
	 * {@link #readFromFile(Class, String)}, {@link #writeToFile},
	 * {@link #write}, etc. on a folder-rooted instance will fail at I/O
	 * time because the underlying {@link ZipUtil} is not initialised.
	 *
	 * <p>Used by {@code JukeStorageAutoConfiguration} to register the
	 * default folder-backed bean.
	 */
	public JukeZipDAOImpl(String folderPath) {
		this.folderRoot = FilenameUtils.normalize(folderPath);
		this.zipper = null;
	}

	/**
	 * Open the DAO against a specific {@link File}. Used by Phase 2's
	 * {@code BundleBackedSessionResolver} which materialises the recording BLOB to a per-session
	 * temp file rather than locating it under the configured juke path.
	 *
	 * <p>The file's parent directory becomes the {@code path} and its name (with a {@code .zip}
	 * suffix stripped if present) becomes the {@code zipName}, so the underlying
	 * {@link ZipUtil} resolves back to the same physical file via {@code new File(path, name+".zip")}.
	 */
	public JukeZipDAOImpl(File zipFile) {
		File parent = zipFile.getParentFile();
		String parentPath = parent == null ? "." : parent.getAbsolutePath();
		String name = zipFile.getName();
		if (name.toLowerCase().endsWith(".zip")) {
			name = name.substring(0, name.length() - 4);
		}
		this.folderRoot = FilenameUtils.normalize(parentPath);
		zipper = new ZipUtil(this.folderRoot, FilenameUtils.normalize(name));
	}
	
	
	public boolean exists() {
		return this.zipper.exists();
	}


	@Override
	public String asString(String identifier) {
		 try {
			 return zipper.readStringFromZipFile(this.zipper.getZipName(), identifier + JSON);

			 } catch (Exception e) {
			// Caller decides severity (many call sites probe for optional sidecar
			// entries); log at debug and let the thrown exception carry context.
			LOG.debug("Unable to read {} from zip {}: {}", identifier, this.zipper.getZipName(), e.getMessage());
			throw new JukeAccessException("Unable to read " + identifier + " from zip", e);

		}
	 
	}

	
	@Override
	public <T> T readFromFile(Class<T> clazz, String identifier) {
		 if (JukeClassMap ==null)
			 getJukeClassMap();


		 String text= this.zipper.readFromZipFile(identifier);
		 try {
			// Jackson-driven deserialization: runtime erases T, so JukeTransformerUtil
			// returns Object. The class metadata we pass in pins the target type.
			@SuppressWarnings("unchecked")
			T result = (T) JukeTransformerUtil.readValue(text,
					JukeClassMap.get(clazz.getCanonicalName()),
					ZipUtil.getMethodFromZipEntryName(identifier));
			return result;
		} catch (Exception e) {
			LOG.error("Unable to deserialize {} for {} from zip: {}", identifier, clazz, e.getMessage(), e);
			throw new JukeAccessException("Unable to read " + identifier + " for " + clazz + " from zip", e);

		}



	}

	public <TTarget> TTarget readFromFile(TTarget type, Class<TTarget> clazz, String identifier) {
		 if (JukeClassMap ==null)
			 getJukeClassMap();

		 String text= this.zipper.readFromZipFile(identifier);
		 try {
			// Same rationale as the {@code (Class<T>, String)} overload: the class
			// metadata passed to Jackson is the source of truth for the target type.
			@SuppressWarnings("unchecked")
			TTarget result = (TTarget) JukeTransformerUtil.readValue(text,
					JukeClassMap.get(clazz.getCanonicalName()),
					ZipUtil.getMethodFromZipEntryName(identifier));
			return result;
		} catch (Exception e) {
			LOG.error("Unable to deserialize {} for {} from zip: {}", identifier, clazz, e.getMessage(), e);
			throw new JukeAccessException("Unable to read " + identifier + " for " + clazz + " from zip", e);

		}


	}

	@Override
	public <T> T readFromFileAsType(Class<?> interfaceClass, String identifier, Class<T> runtimeType) {
		String text = this.zipper.readFromZipFile(identifier);
		try {
			return JukeTransformerUtil.readValueAsType(text, runtimeType);
		} catch (Exception e) {
			LOG.warn("Type-aware deserialization failed for {} as {}, falling back to standard deserialization: {}",
					identifier, runtimeType.getName(), e.getMessage());
			// Fall back to standard deserialization using JukeClass metadata.
			// readFromFile takes Class<T> but we hold Class<?> here; Jackson
			// erases T at runtime, so the runtime type is governed by the
			// recorded JukeClass metadata, not the generic signature.
			@SuppressWarnings("unchecked")
			T fallback = (T) readFromFile(interfaceClass, identifier);
			return fallback;
		}
	}

	public HashMap<String,JukeClass>  getJukeClassMap() {
		String JukeClassTxt= this.zipper.readFromZipFile(juke);
		
		try {
			ObjectMapper om = mapper();
			HashMap<String,JukeClass> juke2Class=om.readValue(JukeClassTxt, om.getTypeFactory().constructMapType(HashMap.class, String.class, JukeClass.class));
			JukeClassMap=juke2Class;
			return juke2Class;
		} catch (JsonProcessingException e) {
			LOG.error("Unable to convert juke.json to juke class map from {}: {}",
					this.zipper.getZipName(), e.getMessage(), e);
			throw new JukeAccessException("Unable to convert juke.json to juke class map", e);
		}
		
		
	 
	}

	@Override
	public boolean writeToFile(String identifier, String o) {
		if (identifier.equals("juke")) {
			zipper.addEntry(identifier, o);
		}else {
			
		
			zipper.addIncrementEntry(identifier, o);
		}
		return false;
	}

	@Override
	public void writeDirectEntry(String exactKey, String content) {
		zipper.addDirectEntry(exactKey, content);
	}

	@Override
	public int getCurrentSequence(String identifier) {
		return zipper.getCurrentSequence(identifier);
	}


	@Override
	public String write() {
		String content = null;

		
		try {

			ObjectMapper objectMapper= new ObjectMapper();
			objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
			
			 content=new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString( JukeClass.instance());

			zipper.addEntry("juke", content);

			// Write entry mappings so humans can understand what each short name maps to
			java.util.Map<String, JukeNameFormatter.EntryMapping> mappings = JukeNameFormatter.getEntryMappings();
			if (!mappings.isEmpty()) {
				String mappingsJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappings);
				zipper.addEntry("juke-mappings", mappingsJson);
			}

			zipper.writeToZipFile();
			
		}catch (IOException e ) {
			throw new JukeAccessException ("Unable to close zipfile: " + e.getMessage(), e);
		}
		return zipper.getZipName();
	}
	@Override
	public String path() {
		return this.zipper.getZipName();
		
	}
	
	@Override
	public Set<String> getFileNames() {
		try {
			return ZipUtil.getFileNamesFromZipFile(this.zipper.getZipName());
		} catch (IOException e) {
			throw new JukeStorageException("failed to list entries in zip " + this.zipper.getZipName(), e);
		}
	}

	// ── Recording-store API (Phase 5.A) ─────────────────────────────────
	//
	// The folder-backed implementation: each *.zip directly under the
	// folder root is one recording. The recording name is the file name
	// minus the .zip suffix. Names accepted by loadRecording /
	// saveRecording may be supplied with or without the suffix.

	@Override
	public List<String> listRecordings() {
		Path root = Paths.get(this.folderRoot);
		if (!Files.isDirectory(root)) {
			return List.of();
		}
		try (Stream<Path> children = Files.list(root)) {
			List<String> names = new ArrayList<>();
			children.filter(Files::isRegularFile)
					.map(p -> p.getFileName().toString())
					.filter(n -> n.toLowerCase().endsWith(ZIP_SUFFIX))
					.map(n -> n.substring(0, n.length() - ZIP_SUFFIX.length()))
					.sorted(Comparator.naturalOrder())
					.forEach(names::add);
			return names;
		} catch (IOException e) {
			throw new JukeStorageException(
					"failed to enumerate recordings under " + this.folderRoot, e);
		}
	}

	@Override
	public byte[] loadRecording(String name) {
		Path zip = recordingPath(name);
		if (!Files.isRegularFile(zip)) {
			throw new JukeStorageException("no such recording: " + name + " (looked at " + zip + ")");
		}
		try {
			return Files.readAllBytes(zip);
		} catch (IOException e) {
			throw new JukeStorageException("failed to read recording " + name, e);
		}
	}

	@Override
	public void saveRecording(String name, byte[] bytes) {
		if (bytes == null || bytes.length == 0) {
			throw new IllegalArgumentException(
					"saveRecording bytes must be non-null and non-empty");
		}
		Path zip = recordingPath(name);
		Path parent = zip.getParent();
		try {
			if (parent != null && !Files.isDirectory(parent)) {
				Files.createDirectories(parent);
			}
			Files.write(zip, bytes);
		} catch (IOException e) {
			throw new JukeStorageException("failed to write recording " + name, e);
		}
	}

	/** Resolve a recording name to its file path under the folder root. */
	private Path recordingPath(String name) {
		String trimmed = name == null ? "" : name.trim();
		if (trimmed.isEmpty()) {
			throw new IllegalArgumentException("recording name must not be blank");
		}
		// Strip a trailing .zip if the caller supplied one — the suffix is
		// canonical-implied by the folder-backed layout.
		if (trimmed.toLowerCase().endsWith(ZIP_SUFFIX)) {
			trimmed = trimmed.substring(0, trimmed.length() - ZIP_SUFFIX.length());
		}
		return Paths.get(this.folderRoot, trimmed + ZIP_SUFFIX);
	}
}
