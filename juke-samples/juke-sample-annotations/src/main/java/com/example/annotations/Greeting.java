package com.example.annotations;

/**
 * Payload used by the annotation examples. Owned per-sample.
 */
public class Greeting {

    private final long id;
    private final String content;

    public Greeting() {
        this.id = 0;
        this.content = "";
    }

    public Greeting(long id, String content) {
        this.id = id;
        this.content = content;
    }

    public long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }
}
