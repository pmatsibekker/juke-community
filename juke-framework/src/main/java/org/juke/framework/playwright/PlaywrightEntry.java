package org.juke.framework.playwright;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents a single request/response pair captured from a Playwright recording.
 * Mirrors the structure of a HAR entry with method, url, and JSON response body.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaywrightEntry {

    private String method;
    private String url;
    private int statusCode;
    private String responseBody;

    public PlaywrightEntry() {
    }

    public PlaywrightEntry(String method, String url, int statusCode, String responseBody) {
        this.method = method;
        this.url = url;
        this.statusCode = statusCode;
        this.responseBody = responseBody;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }

    /**
     * Returns the endpoint key used to match against juke-approved.json entries.
     * Format: "METHOD /path" (e.g. "GET /api/orders")
     */
    public String getEndpointKey() {
        if (method == null || url == null) {
            return "";
        }
        // Strip scheme + host, keep path and query
        String path = url;
        int schemeEnd = url.indexOf("://");
        if (schemeEnd >= 0) {
            int pathStart = url.indexOf('/', schemeEnd + 3);
            path = (pathStart >= 0) ? url.substring(pathStart) : "/";
        }
        return method.toUpperCase() + " " + path;
    }
}

