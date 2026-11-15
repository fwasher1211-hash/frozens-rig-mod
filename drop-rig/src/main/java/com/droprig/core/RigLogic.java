package com.droprig.core;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * Decides which values to <em>display</em> so the rigged dropper shows the higher number.
 *
 * <p>Rules, in order:
 * <ol>
 *   <li>Unknown values (not in the outcome table) → {@code null}: leave both items untouched.</li>
 *   <li>Rigged side already higher → nothing changes.</li>
 *   <li>Otherwise change as few items as possible (normally one), preferring an item that is
 *       still hidden over one already on screen, and preferring to lower the loser over raising
 *       the winner. The replacement is picked at random among the values that work, so shown
 *       results still look varied.</li>
 *   <li>If no single change works (e.g. rigged dropper rolled its minimum while the other rolled
 *       its maximum), both are replaced with a random valid pair.</li>
 * </ol>
 * Replacements always come from the dropper's own outcome list, so Dropper 1 never shows a
 * decimal and Dropper 2 never shows a whole number.
 */
public final class RigLogic {
    private RigLogic() {
    }

    public static Decision decide(OutcomeTable table, Side rigged, double value1, double value2,
                                  boolean visible1, boolean visible2, RandomGenerator rng) {
        if (table == null || rigged == null || rng == null) return null;
        Outcome r1 = table.find(Side.ONE, value1);
        Outcome r2 = table.find(Side.TWO, value2);
        if (r1 == null || r2 == null) return null;

        Side loserSide = rigged.other();
        Outcome win = rigged == Side.ONE ? r1 : r2;
        Outcome lose = rigged == Side.ONE ? r2 : r1;
        if (win.value() > lose.value()) return new Decision(r1, r2, r1, r2);

        boolean winVisible = rigged == Side.ONE ? visible1 : visible2;
        boolean loseVisible = rigged == Side.ONE ? visible2 : visible1;

        List<Outcome> lowerLoser = new ArrayList<>();
        for (Outcome o : table.sorted(loserSide)) if (o.value() < win.value()) lowerLoser.add(o);
        List<Outcome> raiseWinner = new ArrayList<>();
        for (Outcome o : table.sorted(rigged)) if (o.value() > lose.value()) raiseWinner.add(o);

        // Preference: change a hidden item first; between equals, lower the loser.
        Option[] options = {
                new Option(false, lowerLoser, loseVisible),
                new Option(true, raiseWinner, winVisible)
        };
        Option best = null;
        for (Option o : options) {
            if (o.values.isEmpty()) continue;
            if (best == null || (best.visible && !o.visible)) best = o;
        }

        if (best != null) {
            Outcome pick = best.values.get(rng.nextInt(best.values.size()));
            Outcome newWin = best.changeWinner ? pick : win;
            Outcome newLose = best.changeWinner ? lose : pick;
            return rigged == Side.ONE ? new Decision(r1, r2, newWin, newLose) : new Decision(r1, r2, newLose, newWin);
        }

        // Single change impossible: replace both with a random valid pair.
        List<Outcome[]> pairs = new ArrayList<>();
        for (Outcome w : table.sorted(rigged)) {
            for (Outcome l : table.sorted(loserSide)) {
                if (w.value() > l.value()) pairs.add(new Outcome[]{w, l});
            }
        }
        if (pairs.isEmpty()) return null; // tables make a rigged win impossible: don't touch anything
        Outcome[] p = pairs.get(rng.nextInt(pairs.size()));
        return rigged == Side.ONE ? new Decision(r1, r2, p[0], p[1]) : new Decision(r1, r2, p[1], p[0]);
    }

    private record Option(boolean changeWinner, List<Outcome> values, boolean visible) {
    }
}
