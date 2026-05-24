package org.juke.framework.metadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DataProgramSchedule {

	private HashMap<String, DataProgram> requestsIndexMap = new HashMap<>();
		
	public DataProgram getProgram(String entry) {
		if(requestsIndexMap.containsKey(entry))
			return requestsIndexMap.get(entry);
		
		DataProgram dp= new DataProgram();
		requestsIndexMap.put(entry,dp);
		return dp;
	}
	public int add(String entry) {

		DataProgram program=getProgram(entry);
		
		int newindex = program.getIndex() + 1;
		program.setIndex(newindex);
		if (program.getLength() < newindex)
			program.setLength(newindex);
		
		requestsIndexMap.put(entry, program);
		return newindex;
	}
	public int increment(String entry) {

		DataProgram program=getProgram(entry);
		
		int newindex = program.getIndex() + 1;
		if (program.getLength() < newindex)
			newindex=program.getLength();
	
		program.setIndex(newindex);
		
		requestsIndexMap.put(entry, program);
		return newindex;
	}	
	public int current(String entry) {

		DataProgram program=getProgram(entry);
		
		return program.getIndex() ;
			
		
	}	
	public int next(String entry) {

		DataProgram program=getProgram(entry);
		
		int newindex = program.getIndex() + 1;
		
		if (newindex > program.getLength())
			return program.getLength();
		
		return newindex;
	}	
	
	public int size( String entry) {
		DataProgram program=getProgram(entry);
		
		return program.getLength();
		
		
	}
	

	/**
	 * Returns an unmodifiable point-in-time copy of the per-entry program map.
	 * Used by status/monitoring callers that must read progress concurrently
	 * with the hot-path mutations in {@link #add(String)} / {@link #increment(String)}.
	 */
	public Map<String, DataProgram> snapshotPrograms() {
		return Collections.unmodifiableMap(new HashMap<>(requestsIndexMap));
	}

	public String getNextAvailable(String unsequencedEntry){
		int idx = current(unsequencedEntry);
		// Entries are 1-based (.1.json, .2.json, ...) so first call should return 1
		if (idx < 1) idx = 1;
		// Clamp to the last available entry when sequence is exhausted
		int maxIdx = size(unsequencedEntry);
		if (maxIdx > 0 && idx > maxIdx) {
			idx = maxIdx;
		}
		String result = unsequencedEntry + "." + idx;
		increment(unsequencedEntry);
		return result;
	}


}
