package org.juke.framework.storage;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Captures the input arguments of a recorded method invocation so that
 * during replay Juke can verify the caller still sends the same inputs.
 * <p>
 * Stored as a sidecar entry alongside the response:
 * <pre>
 *   OrderService.bill.1.json       ← response
 *   OrderService.bill.1.args.json  ← this record
 * </pre>
 */
public class InputArgsRecord {

    private final String method;
    private final List<String> parameterTypes;
    private final List<Object> arguments;

    @JsonCreator
    public InputArgsRecord(
            @JsonProperty("method") String method,
            @JsonProperty("parameterTypes") List<String> parameterTypes,
            @JsonProperty("arguments") List<Object> arguments) {
        this.method = method;
        this.parameterTypes = parameterTypes;
        this.arguments = arguments;
    }

    public String getMethod() {
        return method;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public List<Object> getArguments() {
        return arguments;
    }

    @Override
    public String toString() {
        return "InputArgsRecord{method='" + method + "', parameterTypes=" + parameterTypes
                + ", arguments=" + arguments + '}';
    }
}

