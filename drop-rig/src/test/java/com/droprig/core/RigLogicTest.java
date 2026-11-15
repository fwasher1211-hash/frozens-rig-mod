package com.droprig.core;

import com.droprig.config.DropRigConfig;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Exhaustive checks over every Dropper 1 × Dropper 2 combination for both selections. */
class RigLogicTest {
    private static final OutcomeTable TABLE = OutcomeTable.parse(DropRigConfig.DEFAULT_DROPPER_1, DropRigConfig.DEFAULT_DROPPER_2);

    @Test
    void tableRecognisesBothDroppers() {
        assertEquals(9, TABLE.sorted(Side.ONE).size());
        assertEquals(9, TABLE.sorted(Side.TWO).size());
        assertEquals(Side.ONE, TABLE.classify(4));
        assertEquals(Side.TWO, TABLE.classify(4.2));
        assertNull(TABLE.classify(4.5), "unknown numbers must not be claimed by either dropper");
        assertNull(TABLE.classify(10));
        assertEquals("9.2", TABLE.declared(Side.TWO).get(0).label(), "display order follows the config");
        assertEquals(1.2, TABLE.sorted(Side.TWO).get(0).value(), 1e-9);
    }

    @Test
    void everyCombinationGivesRiggedDropperTheHigherNumber() {
        int combos = 0;
        for (Side rigged : Side.values()) {
            for (Outcome a : TABLE.sorted(Side.ONE)) {
                for (Outcome b : TABLE.sorted(Side.TWO)) {
                    for (int vis = 0; vis < 4; vis++) {
                        boolean v1 = (vis & 1) != 0, v2 = (vis & 2) != 0;
                        for (long seed = 0; seed < 8; seed++) {
                            Decision d = RigLogic.decide(TABLE, rigged, a.value(), b.value(), v1, v2, new Random(seed));
                            assertNotNull(d, () -> "no decision for " + a.label() + " vs " + b.label());
                            Outcome win = d.shown(rigged), lose = d.shown(rigged.other());
                            assertTrue(win.value() > lose.value(),
                                    rigged + " must win: real " + a.label() + "/" + b.label() + " shown " + d.shown1().label() + "/" + d.shown2().label());
                            assertNotNull(TABLE.find(Side.ONE, d.shown1().value()), "Dropper 1 must only show its own numbers");
                            assertNotNull(TABLE.find(Side.TWO, d.shown2().value()), "Dropper 2 must only show its own numbers");
                            assertEquals(a, d.real1());
                            assertEquals(b, d.real2());
                            combos++;
                        }
                    }
                }
            }
        }
        assertEquals(2 * 9 * 9 * 4 * 8, combos);
    }

    @Test
    void alreadyCorrectRoundsAreLeftAlone() {
        for (Side rigged : Side.values()) {
            for (Outcome a : TABLE.sorted(Side.ONE)) {
                for (Outcome b : TABLE.sorted(Side.TWO)) {
                    boolean alreadyWins = rigged == Side.ONE ? a.value() > b.value() : b.value() > a.value();
                    Decision d = RigLogic.decide(TABLE, rigged, a.value(), b.value(), false, false, new Random(1));
                    assertEquals(!alreadyWins, d.anyChanged(), "change only when needed: " + a.label() + " vs " + b.label());
                }
            }
        }
    }

    @Test
    void changesAtMostOneItemUnlessImpossible() {
        for (Side rigged : Side.values()) {
            for (Outcome a : TABLE.sorted(Side.ONE)) {
                for (Outcome b : TABLE.sorted(Side.TWO)) {
                    Decision d = RigLogic.decide(TABLE, rigged, a.value(), b.value(), false, false, new Random(7));
                    int changes = (d.changed(Side.ONE) ? 1 : 0) + (d.changed(Side.TWO) ? 1 : 0);
                    // Only "Dropper 1 rigged, rolled 1, Dropper 2 rolled 9.2" needs both changed:
                    // nothing in Dropper 1 beats 9.2 and nothing in Dropper 2 is below 1.
                    boolean impossibleSingle = rigged == Side.ONE && a.value() == 1 && b.value() == 9.2;
                    if (impossibleSingle) assertEquals(2, changes);
                    else assertTrue(changes <= 1, "expected one change for " + a.label() + " vs " + b.label());
                }
            }
        }
    }

    @Test
    void prefersChangingTheItemNobodyHasSeenYet() {
        // Dropper 1 rigged, real 3 vs 7.2. Dropper 2 already visible -> must raise Dropper 1.
        Decision d = RigLogic.decide(TABLE, Side.ONE, 3, 7.2, false, true, new Random(3));
        assertFalse(d.changed(Side.TWO));
        assertTrue(d.changed(Side.ONE));
        assertTrue(d.shown1().value() > 7.2);

        // Dropper 1 already visible -> lower Dropper 2 instead.
        d = RigLogic.decide(TABLE, Side.ONE, 3, 7.2, true, false, new Random(3));
        assertFalse(d.changed(Side.ONE));
        assertTrue(d.shown2().value() < 3);
    }

    @Test
    void unknownValuesAreNeverTouched() {
        assertNull(RigLogic.decide(TABLE, Side.ONE, 3.5, 7.2, false, false, new Random(1)));
        assertNull(RigLogic.decide(TABLE, Side.TWO, 3, 70, false, false, new Random(1)));
        assertNull(RigLogic.decide(null, Side.TWO, 3, 7.2, false, false, new Random(1)));
    }

    @Test
    void impossibleTablesReturnNull() {
        OutcomeTable silly = OutcomeTable.parse(java.util.List.of("1", "2"), java.util.List.of("5.2", "6.2"));
        assertNull(RigLogic.decide(silly, Side.ONE, 1, 5.2, false, false, new Random(1)));
        assertNotNull(RigLogic.decide(silly, Side.TWO, 1, 5.2, false, false, new Random(1)));
    }
}
