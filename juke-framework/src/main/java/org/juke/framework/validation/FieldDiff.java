package org.juke.framework.validation;

/**
 * One mismatched leaf in an {@link InputValidationResult}.
 *
 * @param jsonPath the JSONPath expression that locates the leaf (e.g. {@code $.orderId})
 * @param expected stringified value from the recording
 * @param actual   stringified value from the live call
 */
public record FieldDiff(String jsonPath, String expected, String actual) {}
