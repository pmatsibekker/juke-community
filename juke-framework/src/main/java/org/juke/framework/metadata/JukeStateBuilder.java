package org.juke.framework.metadata;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

import org.juke.framework.exception.JukeStorageException;
import org.juke.framework.storage.JukeZipDAOImpl;
import org.juke.framework.storage.ZipUtil;
import org.juke.framework.metadata.JukeClass;

public class JukeStateBuilder {
	Builder builder;
	JukeStateBuilder(Builder builder) {
	this.builder = builder;
		 
	}
	public DataProgramSchedule getSchedule() {
		return builder.schedule().getSchedule();
		
		
		
	}
	public static void main(String[] args) {

	}

	

	public static class Builder {
		private static final String JukeMethodIDENTIFIER = ".$";
		private static final String JSON = ".json";
		private static final String juke = "juke.json";
		Set<String> fileNames = null;
		Set<String> filtered = new HashSet<>();;
	
		DataProgramSchedule schedule = null;
		public Builder(String zipFile) {

			try {
				this.fileNames =  ZipUtil.getFileNamesFromZipFile(zipFile);
			} catch (IOException e) {
				throw new JukeStorageException("failed to enumerate entries in zip " + zipFile, e);
			}

			this.filtered = filter(fileNames);

		}
		public Builder(Set<String> fileNames) {
			this.fileNames = fileNames;
			this.filtered = filter(fileNames);

		}

		private Builder schedule() {
				
		 
			this.schedule = new DataProgramSchedule();
			for (String file : filtered) {
				String[] cleaned = this.split(file);

				if (cleaned != null) {

					int count = Integer.parseInt(cleaned[1]);
					String classAndMethod = cleaned[0];
					DataProgram dp = schedule.getProgram(classAndMethod);
					if (dp.getLength() < count) {
						dp.setLength(count);
					}

				}

			}

			return this;
		}

		private String[] split(String file) {
			String cleaned = file.substring(0, file.length()- JSON.length());
			
		
			int index = cleaned.lastIndexOf('.');
			if (index > -1 && cleaned.endsWith(".") == false) {
				String numStr = cleaned.substring(index + 1);
				if (numStr.matches("\\d+")) {
					String classAndMethod = cleaned.substring(0, index );
					String count = cleaned.substring(index + 1);
					return new String[] { classAndMethod, count };
				}
				// Handle range notation: Method.[1-3] -> returns max of range
				if (numStr.startsWith("[") && numStr.endsWith("]") && numStr.contains("-")) {
					String inner = numStr.substring(1, numStr.length() - 1);
					String[] parts = inner.split("-");
					if (parts.length == 2 && parts[0].matches("\\d+") && parts[1].matches("\\d+")) {
						String classAndMethod = cleaned.substring(0, index);
						String endOfRange = parts[1]; // Use the max index
						return new String[] { classAndMethod, endOfRange };
					}
				}
			}
			return null;
		}

		private Set<String> filter(Set<String> fileNames) {
			filtered.clear();

			for (String file : fileNames) {
				// Skip type metadata sidecar entries (e.g., "...type.1.json")
				if (file.contains(".type.")) {
					continue;
				}
				// Skip input-args sidecar entries (e.g., "...args.json")
				if (file.contains(".args.")) {
					continue;
				}
				// Skip metadata files
				if (file.equals("juke.json") || file.equals("juke-mappings.json")) {
					continue;
				}
				// Accept entries that end with .N.json (where N is a number)
				// This handles both legacy format (with .$) and new short format
				String cleaned = file.substring(0, file.length()- JSON.length());
				int index = cleaned.lastIndexOf('.');
				if (index > -1 && !cleaned.endsWith(".")) {
					String numStr = cleaned.substring(index + 1);
					if (numStr.matches("\\d+")) {
						filtered.add(file);
					}
					// Also accept range entries like Method.[1-3].json
					if (numStr.startsWith("[") && numStr.endsWith("]") && numStr.contains("-")) {
						filtered.add(file);
					}
				}
			}
			return filtered;
		}

		public Builder setFiles(Set<String> fileNames) {
			this.fileNames = this.filter(fileNames);

			return this;

		}
		public DataProgramSchedule getJukeClass() {
			return this.schedule;
		}
		public DataProgramSchedule getSchedule() {
			return this.schedule;
		}
		public JukeStateBuilder build() {
			JukeStateBuilder builder = new JukeStateBuilder(this);
			schedule();
			

			return builder;
		}

	}

}
