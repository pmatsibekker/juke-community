package org.juke.framework.support;

/**
 * Concrete implementation of IRestClient that simulates returning
 * different object types from the same method based on the responseType argument.
 */
public class RestClientImpl implements IRestClient {

    @Override
    public String getAsString(String url) {
        return "Response from " + url;
    }

    @Override
    public SimpleResult getAsResult(String url) {
        return new SimpleResult("ok", 200);
    }

    /**
     * Simulates RestTemplate.getForEntity behavior — the responseType argument
     * determines the type of the returned object.
     */
    @Override
    public Object getForEntity(String url, Class<?> responseType) {
        if (responseType == String.class) {
            return "string-response-from-" + url;
        } else if (responseType == SimpleResult.class) {
            return new SimpleResult("ok", 200);
        } else if (responseType == Integer.class) {
            return 42;
        } else {
            return "unknown-type";
        }
    }

    @Override
    public Object getForEntity(String url, Class<?> responseType, Object... uriVariables) {
        // same behavior but with extra uri variables — delegates to simpler form
        return getForEntity(url, responseType);
    }
}

