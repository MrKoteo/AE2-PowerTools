package com.ae2powertools.features.maintainer;


/**
 * Represents the current state of a maintainer entry.
 */
public enum MaintainerState {
    /**
     * Recipe is disabled by user.
     */
    DISABLED(0x40303040),

    /**
     * Recipe has finished running and is waiting for the next scheduled run.
     */
    IDLE(0x00000000),

    /**
     * Task is scheduled to run (waiting for its time slot).
     */
    SCHEDULED(0x4080C0FF),

    /**
     * Task is actively running (crafting in progress).
     */
    RUNNING(0x4040FF40),

    /**
     * Task is running but stalled (e.g., waiting for resources).
     */
    STALLED(0x40FFFF00),

    /**
     * Task could not be run (missing recipe, no free CPU, missing resources).
     */
    ERROR(0x40FF4040),

    /**
     * Post-crafting issue (no space for output).
     */
    POST_ERROR(0x40C040FF);

    private final int backgroundColor;

    MaintainerState(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public boolean isError() {
        return this == ERROR || this == POST_ERROR;
    }

    public boolean isActive() {
        return this == RUNNING || this == SCHEDULED || this == STALLED;
    }
}
