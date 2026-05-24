package org.juke.framework.playwright;

/**
 * A single difference found between expected and actual JSON response values.
 */
public class JsonDiff {

    private String path;
    private Object expected;
    private Object actual;

    public JsonDiff() {
    }

    public JsonDiff(String path, Object expected, Object actual) {
        this.path = path;
        this.expected = expected;
        this.actual = actual;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Object getExpected() {
        return expected;
    }

    public void setExpected(Object expected) {
        this.expected = expected;
    }

    public Object getActual() {
        return actual;
    }

    public void setActual(Object actual) {
        this.actual = actual;
    }

    @Override
    public String toString() {
        return "JsonDiff{path='" + path + "', expected=" + expected + ", actual=" + actual + '}';
    }
}

