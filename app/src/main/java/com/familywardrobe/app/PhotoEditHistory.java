package com.familywardrobe.app;

import java.util.Arrays;

/** Immutable command history. A completed gesture is one command, never one command per move. */
public final class PhotoEditHistory {
    public static final int MAX_STEPS = 128;
    public static final int MAX_CHARACTERS = 160_000;
    private final String[] steps;
    private final int cursor;

    public PhotoEditHistory() { this(new String[0], 0); }
    public PhotoEditHistory(String[] steps, int cursor) {
        if (steps == null || steps.length > MAX_STEPS || cursor < 0 || cursor > steps.length)
            throw new IllegalArgumentException("Invalid photo history");
        long length = 0;
        for (String step : steps) {
            if (step == null || step.isEmpty()) throw new IllegalArgumentException("Empty photo step");
            length += step.length();
        }
        if (length > MAX_CHARACTERS) throw new IllegalArgumentException("Photo history too large");
        this.steps = steps.clone(); this.cursor = cursor;
    }
    public int getCursor() { return cursor; }
    public String[] getSteps() { return steps.clone(); }
    public String[] getAppliedSteps() { return Arrays.copyOf(steps, cursor); }
    public String getCurrent() { return cursor == 0 ? null : steps[cursor - 1]; }
    public boolean canUndo() { return cursor > 0; }
    public boolean canRedo() { return cursor < steps.length; }
    public boolean canAppend(String step) {
        if (step == null || step.isEmpty() || cursor >= MAX_STEPS) return false;
        long length = step.length();
        for (int i = 0; i < cursor; i++) length += steps[i].length();
        return length <= MAX_CHARACTERS;
    }
    public PhotoEditHistory append(String step) {
        if (!canAppend(step)) throw new IllegalStateException("Photo history is full");
        String[] next = Arrays.copyOf(steps, cursor + 1);
        next[cursor] = step; // Only a new committed action discards the redo branch.
        return new PhotoEditHistory(next, cursor + 1);
    }
    public PhotoEditHistory undo() { return canUndo() ? new PhotoEditHistory(steps, cursor - 1) : this; }
    public PhotoEditHistory redo() { return canRedo() ? new PhotoEditHistory(steps, cursor + 1) : this; }
}
