package org.juke.framework.storage;

import org.apache.commons.io.FilenameUtils;
import org.juke.framework.config.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.juke.framework.proxy.JukeState;
import org.juke.framework.exception.JukeAccessException;
import org.juke.framework.exception.JukeStorageException;
import org.juke.framework.metadata.DataProgramSchedule;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ZipUtil {
	private static final Logger LOG = LoggerFactory.getLogger(ZipUtil.class);

	private String zipPath = "";
	private String identifier = "";
	private ZipFile file;
	private File f ;
	private HashMap<String, String> entriesMap = new HashMap<>();
	DataProgramSchedule schedule =new DataProgramSchedule();
	
	private final static int BUF_SIZE = 1024;

	private static final String DELIM = "/";
	private static final String JSON = ".json";
	private static final String ZIP = ".zip";
	private static final String BAK = ".bak";
	private static final String DOT = ".";
	public static final String SLASH="/";

	
	
	public ZipUtil(String path, String identifier) {
		this.zipPath = FilenameUtils.normalize(path);
		this.identifier = FilenameUtils.normalize(identifier);
		try {
			final String mode = ConfigUtil.getJukeMode();
			if (mode != null && mode.equalsIgnoreCase(JukeState.RECORD)) {

				this.f = File.createTempFile("juke",ZIP);
			} else {
				this.f = new File(path, identifier + ZIP);
			}

		} catch (IOException e) {
			LOG.error("Failed to initialize ZipUtil for path={}, identifier={}: {}",
					path, identifier, e.getMessage(), e);
		}
	}

	public String getZipPath() {
		return zipPath;
	}

	public void setZipPath(String zipPath) {
		this.zipPath = zipPath;
	}

	public String getIdentifier() {
		return identifier;
	}

	public void setIDentifier(String identifier) {
		this.identifier = identifier;
	}

	public ZipFile getFile() {
		return file;
	}

	public void setFile(ZipFile file) {
		this.file = file;
	}

	public HashMap<String, String> getEntriesMap() {
		return entriesMap;
	}

	public void setEntriesMap(HashMap<String, String> entriesMap) {
		this.entriesMap = entriesMap;
	}

	public boolean exists() {
		return new File(getZipName()).exists();
	}

	public String getZipName() {
		return zipPath + DELIM + identifier + ZIP;

	}


	public  String readStringFromZipFile(String zipFileName, String fileName) throws IOException {
		try (ZipFile zipFile = new ZipFile(new File(zipFileName))) {
			ZipEntry zipEntry = zipFile.getEntry(fileName);
			if (zipEntry == null) {
				// Try range entry fallback (e.g. "Method.2.json" -> "Method.[1-3].json")
				zipEntry = findRangeEntryInZipFile(zipFile, fileName);
			}
			if (zipEntry == null) {
				throw new IOException("Entry '" + fileName + "' not found in zip: " + zipFileName);
			}
			try (InputStream is = zipFile.getInputStream(zipEntry);
				 InputStreamReader isr = new InputStreamReader(is);
				 BufferedReader reader = new BufferedReader(isr)) {
				StringBuilder sb = new StringBuilder();
				String str;
				while ((str = reader.readLine()) != null) {
					sb.append(str);
				}
				return sb.toString();
			}
		}
	}

	/**
	 * Finds a range ZIP entry in a given ZipFile that covers the requested file name.
	 * For example, if fileName is "Method.2.json" and the ZIP contains "Method.[1-3].json",
	 * this returns that range ZipEntry.
	 */
	private static ZipEntry findRangeEntryInZipFile(ZipFile zipFile, String fileName) {
		if (!fileName.endsWith(JSON)) return null;
		String withoutJson = fileName.substring(0, fileName.length() - JSON.length());
		int lastDot = withoutJson.lastIndexOf('.');
		if (lastDot <= 0) return null;
		String numStr = withoutJson.substring(lastDot + 1);
		if (!numStr.matches("\\d+")) return null;
		int requestedIndex = Integer.parseInt(numStr);
		String baseKey = withoutJson.substring(0, lastDot);

		java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry ze = entries.nextElement();
			String name = ze.getName();
			if (name.startsWith(baseKey + ".[") && name.endsWith("]" + JSON)) {
				String rangePart = name.substring(baseKey.length() + 1, name.length() - JSON.length());
				if (rangePart.startsWith("[") && rangePart.endsWith("]") && rangePart.contains("-")) {
					String inner = rangePart.substring(1, rangePart.length() - 1);
					String[] parts = inner.split("-");
					if (parts.length == 2) {
						try {
							int rangeStart = Integer.parseInt(parts[0]);
							int rangeEnd = Integer.parseInt(parts[1]);
							if (requestedIndex >= rangeStart && requestedIndex <= rangeEnd) {
								return ze;
							}
						} catch (NumberFormatException ignored) {}
					}
				}
			}
		}
		return null;
	}

	public void close() throws IOException {
		if (file != null) {
			file.close();
			file = null;
		}
	}

	/** Closes the ZipFile handle silently, releasing any file lock. */
	private void close_safe() {
		try {
			close();
		} catch (IOException e) {
			LOG.warn("Failed to close zip file: {}", getZipName(), e);
		}
	}

	public void removeZipFile() throws IOException {
		if (exists()) {
			close();
		}
		File f = new File(getZipName());
		removeFile(new File(f.getPath() + BAK));

		f.renameTo(new File(f.getPath() + BAK));

	}

	public static void removeFile(File f) {
		if (f.exists()) {
			f.delete();
		}
	}

	public static void copyFile(File sourceFile, File destFile) throws IOException {
		//copy a file from [sourceFile] to [destFile]
		Files.copy(sourceFile.toPath(), destFile.toPath());

	}
	public String insertKey(String entry, int index) {
		
		return entry+DOT+ index + JSON;
	}
	 
	
 	public void addIncrementEntry(String entry, String content) {
 		int length=schedule.add(entry);
 		entriesMap.put(insertKey(entry,length), content);
 	}
	public void addEntry(String entry, String content) {
		entriesMap.put(entry+JSON, content);
	}

	/**
	 * Adds an entry with an exact key (no auto-incrementing).
	 * The key should already include the .json extension if needed.
	 */
	public void addDirectEntry(String exactKey, String content) {
		entriesMap.put(exactKey, content);
	}

	/**
	 * Returns the current sequence index for the given entry identifier,
	 * or 0 if no entries have been recorded for it yet.
	 */
	public int getCurrentSequence(String entry) {
		return schedule.current(entry);
	}
	public FileInputStream readFromZipFile() throws IOException{
		return new FileInputStream (new File (this.getZipName()));
	}
	public  void createZipFile(String zipFileName, HashMap<String, String> files) throws IOException {
		//create a zip file
		ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(zipFileName));
		//for each file in the HashMap
		for (String fileName : files.keySet()) {
			//create a zip entry
			ZipEntry zipEntry = new ZipEntry(fileName);
			//add the zip entry to the zip file
			zipOut.putNextEntry(zipEntry);
			//get the file content from the HashMap
			String fileContent = files.get(fileName);
			//write the file content to the zip file
			zipOut.write(fileContent.getBytes());
		}
		//close the zip file
		zipOut.close();
	}
	public void writeToZipFile() throws IOException{
		removeZipFile();

		this.zipPath = f.getParent();
		this.identifier= f.getName().split(ZIP)[0];
		// Compact consecutive identical entries before writing
		HashMap<String, String> compacted = compactEntries(entriesMap);
		createZipFile(f.getPath(), compacted);

	}

	/**
	 * Compacts consecutive duplicate entries into range notation.
	 * <p>
	 * For example, if the entries map contains:
	 * <pre>
	 *   IDataService.fetchData@String.1.json -> "hello"
	 *   IDataService.fetchData@String.2.json -> "hello"
	 *   IDataService.fetchData@String.3.json -> "world"
	 * </pre>
	 * The compacted result will be:
	 * <pre>
	 *   IDataService.fetchData@String.[1-2].json -> "hello"
	 *   IDataService.fetchData@String.3.json     -> "world"
	 * </pre>
	 * Entries that don't match the .N.json pattern (like juke.json) are passed through unchanged.
	 */
	public static HashMap<String, String> compactEntries(HashMap<String, String> entries) {
		// Group entries by their base key (everything before .N.json)
		// Preserve insertion order within each group
		java.util.LinkedHashMap<String, java.util.TreeMap<Integer, String>> grouped = new java.util.LinkedHashMap<>();
		HashMap<String, String> nonSequenced = new HashMap<>();

		for (java.util.Map.Entry<String, String> e : entries.entrySet()) {
			String fileName = e.getKey();
			String content = e.getValue();
			// Try to parse as baseKey.N.json
			if (fileName.endsWith(JSON)) {
				String withoutJson = fileName.substring(0, fileName.length() - JSON.length());
				int lastDot = withoutJson.lastIndexOf('.');
				if (lastDot > 0) {
					String numStr = withoutJson.substring(lastDot + 1);
					if (numStr.matches("\\d+")) {
						String baseKey = withoutJson.substring(0, lastDot);
						int index = Integer.parseInt(numStr);
						grouped.computeIfAbsent(baseKey, k -> new java.util.TreeMap<>()).put(index, content);
						continue;
					}
				}
			}
			// Non-sequenced entry (juke.json, juke-mappings.json, etc.)
			nonSequenced.put(fileName, content);
		}

		// Now compact each group: merge consecutive entries with identical content
		HashMap<String, String> result = new HashMap<>(nonSequenced);
		for (java.util.Map.Entry<String, java.util.TreeMap<Integer, String>> group : grouped.entrySet()) {
			String baseKey = group.getKey();
			java.util.TreeMap<Integer, String> indexedEntries = group.getValue();

			// Walk through sorted indices and merge consecutive identical values
			Integer rangeStart = null;
			Integer rangePrev = null;
			String rangeContent = null;

			for (java.util.Map.Entry<Integer, String> ie : indexedEntries.entrySet()) {
				int idx = ie.getKey();
				String content = ie.getValue();

				if (rangeStart == null) {
					// Start a new range
					rangeStart = idx;
					rangePrev = idx;
					rangeContent = content;
				} else if (content.equals(rangeContent) && idx == rangePrev + 1) {
					// Extend the current range
					rangePrev = idx;
				} else {
					// Flush the current range and start a new one
					flushRange(result, baseKey, rangeStart, rangePrev, rangeContent);
					rangeStart = idx;
					rangePrev = idx;
					rangeContent = content;
				}
			}
			// Flush the last range
			if (rangeStart != null) {
				flushRange(result, baseKey, rangeStart, rangePrev, rangeContent);
			}
		}

		return result;
	}

	/**
	 * Writes a single entry or a range entry to the result map.
	 * Single: baseKey.1.json
	 * Range:  baseKey.[1-3].json
	 */
	private static void flushRange(HashMap<String, String> result, String baseKey,
									int start, int end, String content) {
		if (start == end) {
			// Single entry
			result.put(baseKey + DOT + start + JSON, content);
		} else {
			// Range entry
			result.put(baseKey + DOT + "[" + start + "-" + end + "]" + JSON, content);
		}
	}
	public static Set<String> getFileNamesFromZipFile(String zipFileName) throws IOException {
		Set<String> fileNames = new HashSet<>();
		try (ZipFile zipFile = new ZipFile(zipFileName)) {
			ZipEntry[] zipEntries = zipFile.stream().toArray(ZipEntry[]::new);
			for (int i = 0; i < zipEntries.length; i++) {
				fileNames.add(zipEntries[i].getName());
			}
		}
		return fileNames;
	}


	
	public ZipFile getZipFile() {
		return file;
	}

	public void open() {
		try {
			if (file != null) {
				file.close();

			}
			 File f=new File(getZipName());
			 if (!f.exists()) {
				 LOG.error("Zip file does not exist: {}", f.getAbsolutePath());
			 }
			file = new ZipFile(f);
		} catch (IOException e) {
			LOG.error("Failed to open zip file: {} - {}", getZipName(), e.getMessage());
			throw new JukeStorageException("Failed to open zip file: " + getZipName(), e);
		}
	}

	public static String getMethodFromZipEntryName(String zipEntryName) {
		// Strip the .N.json suffix
		String cleaned  = zipEntryName.split("\\.\\d+\\.json")[0];

		String method;
		if (cleaned.contains(".$")) {
			// Legacy format: org.example.IService.$methodName or org.example.IService.$methodName@Type
			method = cleaned.split("\\.\\$")[1];
		} else {
			// Short format: IService.methodName or IService.methodName@Type
			int firstDot = cleaned.indexOf('.');
			method = (firstDot >= 0) ? cleaned.substring(firstDot + 1) : cleaned;
		}

		// Strip @discriminator suffix if present (type-aware recording)
		if (method.contains("@")) {
			method = method.substring(0, method.indexOf("@"));
		}
		// Strip overload hash suffix (digits appended by JukeParser)
		method = method.split("[-?\\.\\d*]")[0];
	 
		return method;
	}
	public String readFromZipFile(String entry) {
		
		ZipEntry zentry= null;
		open();
		if (file == null) {
			throw new JukeAccessException("Zip file is not open: " + getZipName() + " — cannot read " + entry + JSON);
		}
		InputStream is = null;
		try{
			zentry = file.getEntry(entry+JSON);
			if (zentry == null) {
				// Try range entry fallback (e.g. entry="Method.2" -> look for "Method.[1-3].json")
				zentry = findRangeEntry(entry);
			}
			is = file.getInputStream(zentry);
			
		}catch (Exception e) {
			close_safe();
			throw new JukeAccessException ("Failed to find " + entry + JSON);
			
		}
		
		InputStreamReader isr=null;
		try{
			isr = new InputStreamReader(is);
			
		}catch (NullPointerException npe) {
			close_safe();
			throw new JukeAccessException ("Could not open stream " + entry + JSON);
		}
		BufferedReader reader = new BufferedReader(isr);
		StringBuffer sb = new StringBuffer();
		String str=null;
		try {
			while (( str = reader.readLine())!=null) {
				sb.append(str);
			}
		}catch (IOException e) {
			close_safe();
			throw new JukeAccessException ("Can not read date for "+entry+" from "+getZipName());
		}
		
		try {
			reader.close();
			isr.close();
			is.close();
		}catch (IOException e) {
			throw new JukeAccessException ("Can not close streams for "+getZipName());
		} finally {
			close_safe();
		}
		return sb.toString();
	}

	/**
	 * Finds a range ZIP entry that covers the given sequential entry.
	 * For example, if entry is "IDataService.fetchData@String.2",
	 * and the ZIP contains "IDataService.fetchData@String.[1-3].json",
	 * this returns that range ZipEntry.
	 *
	 * @param entry the entry name WITHOUT .json suffix (e.g. "Method.2")
	 * @return the matching ZipEntry, or null if not found
	 */
	private ZipEntry findRangeEntry(String entry) {
		// Parse the requested index from the entry name
		int lastDot = entry.lastIndexOf('.');
		if (lastDot <= 0) return null;
		String numStr = entry.substring(lastDot + 1);
		if (!numStr.matches("\\d+")) return null;
		int requestedIndex = Integer.parseInt(numStr);
		String baseKey = entry.substring(0, lastDot);

		// Scan ZIP entries for a matching range
		java.util.Enumeration<? extends ZipEntry> entries = file.entries();
		while (entries.hasMoreElements()) {
			ZipEntry ze = entries.nextElement();
			String name = ze.getName();
			// Look for pattern: baseKey.[start-end].json
			if (name.startsWith(baseKey + ".[") && name.endsWith("]" + JSON)) {
				// Extract the range: "[start-end]"
				String rangePart = name.substring(baseKey.length() + 1, name.length() - JSON.length());
				// rangePart should be "[N-M]"
				if (rangePart.startsWith("[") && rangePart.endsWith("]") && rangePart.contains("-")) {
					String inner = rangePart.substring(1, rangePart.length() - 1);
					String[] parts = inner.split("-");
					if (parts.length == 2) {
						try {
							int rangeStart = Integer.parseInt(parts[0]);
							int rangeEnd = Integer.parseInt(parts[1]);
							if (requestedIndex >= rangeStart && requestedIndex <= rangeEnd) {
								return ze;
							}
						} catch (NumberFormatException ignored) {}
					}
				}
			}
		}
		return null;
	}


}
