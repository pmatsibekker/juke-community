package org.juke.remix.service.dto;

/**
 * Snapshot of the most recent replay call resolved on a session. Surfaced on
 * {@link SessionStatusDto#getLastCall()} so a status grid can show meaningful
 * progress — "which JSON did I just hand back?" — instead of a bare step
 * count that doesn't identify the call.
 *
 * <h3>Fields</h3>
 * <ul>
 *   <li>{@code entry} — the unsequenced ZIP-entry key (matches
 *       {@link StepStatusDto#getEntry()}, e.g.
 *       {@code com.example.IGreetingsService.$greeting}). Useful for
 *       cross-referencing the {@code steps} array.</li>
 *   <li>{@code displayName} — the human-friendly form
 *       {@code <SimpleType>.<method>.<sequence>} (e.g.
 *       {@code IGreetingsService.greeting.7}) — what the requester
 *       typically wants to read.</li>
 *   <li>{@code sequence} — the 1-based sequence number within the entry.</li>
 *   <li>{@code at} — ISO-8601 timestamp at which the call was resolved.</li>
 * </ul>
 */
public class LastCallDto {

    private final String entry;
    private final String displayName;
    private final int sequence;
    private final String at;

    public LastCallDto(String entry, String displayName, int sequence, String at) {
        this.entry = entry;
        this.displayName = displayName;
        this.sequence = sequence;
        this.at = at;
    }

    public String getEntry() {
        return entry;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getSequence() {
        return sequence;
    }

    public String getAt() {
        return at;
    }
}
