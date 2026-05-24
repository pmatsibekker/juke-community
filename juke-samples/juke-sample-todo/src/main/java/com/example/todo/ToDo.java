package com.example.todo;

/**
 * ToDo item payload.
 */
public class ToDo {

    private final long id;
    private String title;
    private boolean completed;

    public ToDo() {
        this.id = 0;
        this.title = "";
        this.completed = false;
    }

    public ToDo(long id, String title, boolean completed) {
        this.id = id;
        this.title = title;
        this.completed = completed;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    @Override
    public String toString() {
        return "ToDo{id=" + id + ", title='" + title + "', completed=" + completed + "}";
    }
}
