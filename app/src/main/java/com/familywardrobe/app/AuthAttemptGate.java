package com.familywardrobe.app;

/** One SDK task at a time. Cancellation rejects its result but waits for the SDK to settle. */
public final class AuthAttemptGate {
    private long sequence;
    private long active;
    private boolean cancelled;
    public synchronized long begin() {
        if (active != 0) return 0;
        active = ++sequence;
        cancelled = false;
        return active;
    }
    public synchronized void cancel() { if (active != 0) cancelled = true; }
    public synchronized boolean isBusy() { return active != 0; }
    public synchronized boolean isCancelling() { return active != 0 && cancelled; }
    public synchronized boolean finish(long attempt) {
        if (attempt == 0 || attempt != active) return false;
        boolean accepted = !cancelled;
        active = 0;
        cancelled = false;
        return accepted;
    }
}
