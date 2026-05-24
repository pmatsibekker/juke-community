package org.juke.framework.metadata;

public class DataProgram {
	int index = 0;
	int length = 0;
	String content="";

	public DataProgram() {
		
		
	};
	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public int getIndex() {
		return index;
	}
	public void setIndex(int index) {
		this.index = index;
	}
	public int getLength() {
		return length;
	}
	public void setLength(int length) {
		this.length = length;
		if (length >0 && index == 0)
			index=1;
	}

}
