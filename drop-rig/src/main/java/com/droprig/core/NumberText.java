package com.droprig.core;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds and replaces the number shown in an item name such as "§e7.2", "Roll: 3" or "7.2 Points".
 * The <em>last</em> number in the text is used, and legacy § formatting codes are skipped so that
 * "§63" reads as 3, not 63.
 */
public final class NumberText {
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private NumberText() {
    }

    /** Position of a number inside the original string; {@code end} is exclusive. */
    public record Match(int start, int end, double value) {
    }

    public static Match findLast(String s) {
        if (s == null || s.isEmpty()) return null;
        StringBuilder stripped = new StringBuilder(s.length());
        int[] origin = new int[s.length()];
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                i++; // skip the code character too
                continue;
            }
            origin[stripped.length()] = i;
            stripped.append(c);
        }
        Matcher m = NUMBER.matcher(stripped);
        Match last = null;
        while (m.find()) {
            double v;
            try {
                v = Double.parseDouble(m.group());
            } catch (NumberFormatException e) {
                continue;
            }
            last = new Match(origin[m.start()], origin[m.end() - 1] + 1, v);
        }
        return last;
    }

    /** Convenience: the value of the last number, or null. */
    public static Double lastValue(String s) {
        Match m = findLast(s);
        return m == null ? null : m.value();
    }

    /**
     * Replaces the last number across a list of styled text segments (kept separate so styles
     * survive). Returns the new segment list, or null if no number was found.
     */
    public static List<String> replaceLast(List<String> segments, String replacement) {
        StringBuilder all = new StringBuilder();
        for (String seg : segments) all.append(seg);
        Match m = findLast(all.toString());
        if (m == null) return null;

        List<String> out = new ArrayList<>(segments.size());
        int pos = 0;
        boolean inserted = false;
        for (String seg : segments) {
            int segStart = pos, segEnd = pos + seg.length();
            pos = segEnd;
            if (segEnd <= m.start() || segStart >= m.end()) {
                out.add(seg);
                continue;
            }
            StringBuilder b = new StringBuilder();
            if (m.start() > segStart) b.append(seg, 0, m.start() - segStart);
            if (!inserted) {
                b.append(replacement);
                inserted = true;
            }
            if (m.end() < segEnd) b.append(seg, m.end() - segStart, seg.length());
            out.add(b.toString());
        }
        return out;
    }
}
