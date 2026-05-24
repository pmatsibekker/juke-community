package org.juke.remix.service.dto;

/**
 * Per-entry step progress for a single Juke session. An "entry" is a
 * class-and-method key such as {@code com.example.IGreetingsService.$greeting}
 * for which the session's ZIP holds one or more sequenced responses.
 */
public class StepStatusDto {

    public enum Status {
        NOT_STARTED,
        IN_PROGRESS,
        COMPLETED
    }

    private final String entry;
    private final int currentIndex;
    private final int totalLength;
    private final Status status;

    public StepStatusDto(String entry, int currentIndex, int totalLength, Status status) {
        this.entry = entry;
        this.currentIndex = currentIndex;
        this.totalLength = totalLength;
        this.status = status;
    }

    public String getEntry() {
        return entry;
    }

    public int getCurrentIndex() {
        return currentIndex;
    }

    public int getTotalLength() {
        return totalLength;
    }

    public String getStatus() {
        return status.name().toLowerCase();
    }
}
