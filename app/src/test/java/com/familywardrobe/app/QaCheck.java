package com.familywardrobe.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Assertion helper for the QA regression suites (QA-REPORT-0.14.13.md).
 *
 * Every assertion is tagged with the test-design technique that produced it, so the summary line
 * shows how the five techniques cover each suite. A known, still-open bug is asserted with
 * {@link #knownBug}: it passes while the bug reproduces and FAILS as soon as the bug is fixed, so the
 * fix is noticed and the case is turned into an ordinary regression assertion (strict expected-failure).
 *
 * No Android or JUnit dependency: the same cases run from host checks (javac) and from Gradle JUnit.
 */
public final class QaCheck {
    public enum Technique {
        EP("equivalence classes"), BVA("boundary values"), ST("state transitions"),
        PW("pairwise"), EG("error guessing");
        final String title;
        Technique(String title) { this.title = title; }
    }

    private final String suite;
    private final Map<Technique, Integer> counts = new EnumMap<>(Technique.class);
    private final TreeSet<String> reproduced = new TreeSet<>();

    public QaCheck(String suite) { this.suite = suite; }

    private void count(Technique technique) { counts.merge(technique, 1, Integer::sum); }

    public void isTrue(Technique technique, String label, boolean condition) {
        count(technique);
        if (!condition) throw new AssertionError(suite + " [" + technique + "] " + label);
    }

    public void isFalse(Technique technique, String label, boolean condition) { isTrue(technique, label, !condition); }

    public void equal(Technique technique, String label, Object expected, Object actual) {
        count(technique);
        if (!Objects.equals(expected, actual))
            throw new AssertionError(suite + " [" + technique + "] " + label + ": expected <" + expected + "> but was <" + actual + ">");
    }

    /** The call must throw {@code expected} (or a subclass). */
    public void raises(Technique technique, String label, Class<? extends Throwable> expected, ThrowingRunnable call) {
        count(technique);
        try {
            call.run();
        } catch (Throwable actual) {
            if (expected.isInstance(actual)) return;
            throw new AssertionError(suite + " [" + technique + "] " + label + ": expected " + expected.getSimpleName()
                + " but got " + actual, actual);
        }
        throw new AssertionError(suite + " [" + technique + "] " + label + ": expected " + expected.getSimpleName() + ", nothing thrown");
    }

    /**
     * Strict expected failure for an open bug. {@code fixedBehaviour} states what must hold once the bug
     * is fixed; while it still fails the bug is reported as reproduced and the suite keeps passing.
     */
    public void knownBug(Technique technique, String id, String title, ThrowingRunnable fixedBehaviour) {
        count(technique);
        try {
            fixedBehaviour.run();
        } catch (AssertionError | RuntimeException reproducedNow) {
            reproduced.add(id);
            return;
        } catch (Throwable unexpected) {
            throw new AssertionError(suite + " " + id + " failed unexpectedly", unexpected);
        }
        throw new AssertionError(suite + " " + id + " (" + title + ") no longer reproduces. It looks fixed: "
            + "replace knownBug(...) with a regular assertion and update QA-REPORT-0.14.13.md.");
    }

    /** Assertion used inside knownBug blocks and helpers. */
    public static void expect(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    public int total() { int n = 0; for (int value : counts.values()) n += value; return n; }

    public String summary() {
        StringBuilder text = new StringBuilder("PASS: ").append(suite).append(": ").append(total()).append(" assertions (");
        boolean first = true;
        for (Technique technique : Technique.values()) {
            if (!first) text.append(", ");
            first = false;
            text.append(technique).append('=').append(counts.getOrDefault(technique, 0));
        }
        text.append(')');
        if (!reproduced.isEmpty()) text.append("; open bugs still reproduced: ").append(String.join(", ", reproduced));
        return text.toString();
    }

    public int assertions(Technique technique) { return counts.getOrDefault(technique, 0); }

    public java.util.Set<String> reproducedBugs() { return java.util.Collections.unmodifiableSet(reproduced); }

    public void done() { System.out.println(summary()); }

    @FunctionalInterface
    public interface ThrowingRunnable { void run() throws Exception; }

    /**
     * Deterministic all-pairs (2-wise) design. Returns rows of value indexes such that every pair of
     * values of every two parameters appears in at least one row. Coverage is verified before return.
     */
    public static List<int[]> allPairs(int... sizes) {
        int n = sizes.length;
        if (n < 2) throw new IllegalArgumentException("Pairwise needs at least two parameters");
        for (int size : sizes) if (size < 1) throw new IllegalArgumentException("Empty parameter");
        // uncovered[a][b][va*sizes[b]+vb]
        boolean[][][] uncovered = new boolean[n][n][];
        int remaining = 0;
        for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++) {
            uncovered[a][b] = new boolean[sizes[a] * sizes[b]];
            Arrays.fill(uncovered[a][b], true);
            remaining += sizes[a] * sizes[b];
        }
        List<int[]> rows = new ArrayList<>();
        while (remaining > 0) {
            int[] row = new int[n];
            Arrays.fill(row, -1);
            // Seed the row with the first still-uncovered pair.
            seed:
            for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++) for (int i = 0; i < uncovered[a][b].length; i++)
                if (uncovered[a][b][i]) { row[a] = i / sizes[b]; row[b] = i % sizes[b]; break seed; }
            for (int p = 0; p < n; p++) {
                if (row[p] >= 0) continue;
                int best = 0, bestGain = -1;
                for (int v = 0; v < sizes[p]; v++) {
                    int gain = 0;
                    for (int q = 0; q < n; q++) {
                        if (q == p || row[q] < 0) continue;
                        int a = Math.min(p, q), b = Math.max(p, q);
                        int va = a == p ? v : row[q], vb = b == p ? v : row[q];
                        if (uncovered[a][b][va * sizes[b] + vb]) gain++;
                    }
                    if (gain > bestGain) { bestGain = gain; best = v; }
                }
                row[p] = best;
            }
            for (int a = 0; a < n; a++) for (int b = a + 1; b < n; b++) {
                int index = row[a] * sizes[b] + row[b];
                if (uncovered[a][b][index]) { uncovered[a][b][index] = false; remaining--; }
            }
            rows.add(row);
        }
        verifyPairs(rows, sizes);
        return rows;
    }

    static void verifyPairs(List<int[]> rows, int... sizes) {
        for (int a = 0; a < sizes.length; a++) for (int b = a + 1; b < sizes.length; b++)
            for (int va = 0; va < sizes[a]; va++) for (int vb = 0; vb < sizes[b]; vb++) {
                boolean found = false;
                for (int[] row : rows) if (row[a] == va && row[b] == vb) { found = true; break; }
                if (!found) throw new AssertionError("Pairwise design misses parameters " + a + "/" + b + " values " + va + "/" + vb);
            }
    }
}
