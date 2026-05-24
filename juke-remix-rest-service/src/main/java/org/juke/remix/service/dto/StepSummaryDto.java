package org.juke.remix.service.dto;

/**
 * Aggregate step counts rolled up across every entry in a session.
 */
public class StepSummaryDto {

    private final int totalSteps;
    private final int completedSteps;
    private final int inProgressSteps;
    private final int notStartedSteps;

    public StepSummaryDto(int totalSteps, int completedSteps, int inProgressSteps, int notStartedSteps) {
        this.totalSteps = totalSteps;
        this.completedSteps = completedSteps;
        this.inProgressSteps = inProgressSteps;
        this.notStartedSteps = notStartedSteps;
    }

    public int getTotalSteps() {
        return totalSteps;
    }

    public int getCompletedSteps() {
        return completedSteps;
    }

    public int getInProgressSteps() {
        return inProgressSteps;
    }

    public int getNotStartedSteps() {
        return notStartedSteps;
    }
}
