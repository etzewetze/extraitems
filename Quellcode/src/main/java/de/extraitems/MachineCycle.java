package de.extraitems;

/** Small, deterministic state machine shared by timed processing blocks. */
final class MachineCycle {
    enum Decision { IDLE, START, PROCESSING, CANCEL, WAITING_OUTPUT, COMPLETE }

    private MachineCycle() {}

    static Decision decide(long readyAt, long now, boolean expectedInputPresent, boolean outputFits) {
        if (readyAt <= 0) return expectedInputPresent ? Decision.START : Decision.IDLE;
        if (!expectedInputPresent) return Decision.CANCEL;
        if (now < readyAt) return Decision.PROCESSING;
        return outputFits ? Decision.COMPLETE : Decision.WAITING_OUTPUT;
    }
}
