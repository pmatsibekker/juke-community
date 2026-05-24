package org.juke.framework.metadata;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class JukeParameterizedType {
	String rawType;
	ArrayList<JukeParameterizedType> actualTypeArguments = new ArrayList<JukeParameterizedType>();
	boolean isParameterized=false;
	boolean isArray = false;
	
	public boolean isArray() {
		return isArray;
	}

	public void setArray(boolean isArray) {
		this.isArray = isArray;
	}

	public String getRawType() {
		return rawType;
	}

	public void setRawType(String rawType) {
		this.rawType = rawType;
	}


	public ArrayList<JukeParameterizedType> getActualTypeArguments() {
		return actualTypeArguments;
	}

	public void setActualTypeArguments(ArrayList<JukeParameterizedType> actualTypeArguments) {
		this.actualTypeArguments = actualTypeArguments;
	}

	public boolean isParameterized() {
		return isParameterized;
	}

	public void setParameterized(boolean isParameterized) {
		this.isParameterized = isParameterized;
	}

	
	
}
