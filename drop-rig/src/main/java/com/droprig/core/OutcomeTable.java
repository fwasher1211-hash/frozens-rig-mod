package com.droprig.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The set of numbers each dropper can produce. Used to recognise which dropper a dropped
 * item came from (Dropper 1 = whole numbers, Dropper 2 = x.2 numbers by default) and to pick
 * replacement values that still belong to the right dropper.
 */
public final class OutcomeTable {
    public static final double EPSILON = 1e-6;

    private final Map<Side, List<Outcome>> sorted = new EnumMap<>(Side.class);
    private final Map<Side, List<Outcome>> declared = new EnumMap<>(Side.class);

    private OutcomeTable(List<String> one, List<String> two) {
        build(Side.ONE, one);
        build(Side.TWO, two);
    }

    public static OutcomeTable parse(List<String> dropperOne, List<String> dropperTwo) {
        return new OutcomeTable(dropperOne == null ? List.of() : dropperOne,
                dropperTwo == null ? List.of() : dropperTwo);
    }

    private void build(Side side, List<String> labels) {
        List<String> clean = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (String raw : labels) {
            if (raw == null) continue;
            String label = raw.trim();
            double v;
            try {
                v = Double.parseDouble(label);
            } catch (NumberFormatException e) {
                continue; // ignore junk entries instead of crashing
            }
            if (Double.isNaN(v) || Double.isInfinite(v)) continue;
            boolean dup = false;
            for (double existing : values) {
                if (Math.abs(existing - v) < EPSILON) {
                    dup = true;
                    break;
                }
            }
            if (dup) continue;
            clean.add(label);
            values.add(v);
        }

        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < clean.size(); i++) order.add(i);
        order.sort(Comparator.comparingDouble(values::get));
        int[] rankOf = new int[clean.size()];
        for (int r = 0; r < order.size(); r++) rankOf[order.get(r)] = r;

        List<Outcome> decl = new ArrayList<>();
        for (int i = 0; i < clean.size(); i++) decl.add(new Outcome(clean.get(i), values.get(i), rankOf[i]));
        List<Outcome> asc = new ArrayList<>(decl);
        asc.sort(Comparator.comparingInt(Outcome::rank));
        declared.put(side, Collections.unmodifiableList(decl));
        sorted.put(side, Collections.unmodifiableList(asc));
    }

    /** Outcomes in ascending numeric order. */
    public List<Outcome> sorted(Side side) {
        return sorted.get(side);
    }

    /** Outcomes in the order the user listed them (used for display). */
    public List<Outcome> declared(Side side) {
        return declared.get(side);
    }

    public boolean isUsable() {
        return !sorted.get(Side.ONE).isEmpty() && !sorted.get(Side.TWO).isEmpty();
    }

    /** Returns the outcome of {@code side} equal to {@code value}, or null if it isn't one of that dropper's numbers. */
    public Outcome find(Side side, double value) {
        for (Outcome o : sorted.get(side)) {
            if (Math.abs(o.value() - value) < EPSILON) return o;
        }
        return null;
    }

    public Outcome findLabel(Side side, String label) {
        for (Outcome o : sorted.get(side)) {
            if (o.label().equals(label)) return o;
        }
        return null;
    }

    /**
     * Works out which dropper a value belongs to. Returns null when the value belongs to neither
     * dropper or (ambiguously) to both, so unexpected items are never touched.
     */
    public Side classify(double value) {
        boolean one = find(Side.ONE, value) != null;
        boolean two = find(Side.TWO, value) != null;
        if (one == two) return null;
        return one ? Side.ONE : Side.TWO;
    }
}
