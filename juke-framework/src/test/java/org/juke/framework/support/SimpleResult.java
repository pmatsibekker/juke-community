package org.juke.framework.support;

/**
 * Simple POJO used as one of the "responseType" return types.
 */
public class SimpleResult {
    private String status;
    private int code;

    public SimpleResult() {
    }

    public SimpleResult(String status, int code) {
        this.status = status;
        this.code = code;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SimpleResult that = (SimpleResult) o;
        return code == that.code &&
                (status != null ? status.equals(that.status) : that.status == null);
    }

    @Override
    public int hashCode() {
        int result = status != null ? status.hashCode() : 0;
        result = 31 * result + code;
        return result;
    }

    @Override
    public String toString() {
        return "SimpleResult{status='" + status + "', code=" + code + "}";
    }
}

