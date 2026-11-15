package com.droprig.core;

/**
 * One possible result of a dropper.
 *
 * @param label the text exactly as it should be displayed (e.g. "7.2")
 * @param value the numeric value used for comparisons
 * @param rank  0-based position when the dropper's outcomes are sorted ascending
 */
public record Outcome(String label, double value, int rank) {
}
