package org.juke.framework.metadata;

import java.util.ArrayList;
import java.util.List;

public class JukeParameter {
	public String className;

	public boolean isParameterized = false;
	public boolean isArray = false;
	public JukeParameterizedType type= null;



	public List<JukeParameter> list = new ArrayList<JukeParameter>();
	public boolean isParameterized() {
		return isParameterized;
	}

	public void setParameterized(boolean isParameterized) {
		this.isParameterized = isParameterized;
	}
	public JukeParameterizedType getType() {
		return type;
	}

	public void setType(JukeParameterizedType type) {
		this.type = type;
	}

	public List<JukeParameter> getList() {
		return list;
	}
	
	public boolean isArray() {
		return isArray;
	}

	public void setArray(boolean isArray) {
		this.isArray = isArray;
	}
	
	public void setList(List<JukeParameter> list) {
		this.list = list;
	}


	public String getClassName() {
		return className;
	}

	public void setClassName(String className) {
		this.className = className;
	}
	
}
