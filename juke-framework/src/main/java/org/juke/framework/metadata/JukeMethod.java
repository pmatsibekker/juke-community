package org.juke.framework.metadata;

import java.util.ArrayList;
import java.util.List;

public class JukeMethod {
	String method;
	int overloadedSignature;

	List<JukeParameter> inputParameters = new ArrayList<JukeParameter>();
	JukeParameter outputResult ;
	
	public String getMethod() {
		return method;
	}
	public void setMethod(String method) {
		this.method = method;
	}
	public List<JukeParameter> getInputParameters() {
		return inputParameters;
	}
	public void setInputParameters(List<JukeParameter> inputParameters) {
		this.inputParameters = inputParameters;
	}
	public JukeParameter getOutputResult() {
		return outputResult;
	}
	public void setOutputResult(JukeParameter outputResult) {
		this.outputResult = outputResult;
	}
	public int getOverloadedSignature() {
		return overloadedSignature;
	}
	public void setOverloadedSignature(int overloadedSignature) {
		this.overloadedSignature = overloadedSignature;
	}
}
