package com.droprig.core;

import com.droprig.config.DropRigConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class RigSessionTest {
    private static final OutcomeTable TABLE = OutcomeTable.parse(DropRigConfig.DEFAULT_DROPPER_1, DropRigConfig.DEFAULT_DROPPER_2);

    private static RigSession session(boolean enabled, Side rigged) {
        RigSession s = new RigSession(TABLE, new Random(42));
        s.configure(TABLE, new RigSession.Settings(6, 60, 12));
        s.setEnabled(enabled);
        s.setRigged(rigged);
        return s;
    }

    private static RigSession.Drop drop(int id, Side side, double value, long tick, double x) {
        return new RigSession.Drop(id, side, TABLE.find(side, value), tick, x, 64, 0);
    }

    @Test
    void simultaneousDropsPairAndRiggedSideWins() {
        for (Side rigged : Side.values()) {
            for (Outcome a : TABLE.sorted(Side.ONE)) {
                for (Outcome b : TABLE.sorted(Side.TWO)) {
                    RigSession s = session(true, rigged);
                    RigSession.Admission first = s.offer(drop(1, Side.ONE, a.value(), 100, 0));
                    assertTrue(first.hidden(), "first drop is held while waiting for its partner");
                    assertTrue(s.isHidden(1));
                    RigSession.Admission second = s.offer(drop(2, Side.TWO, b.value(), 100, 2));
                    assertNotNull(second.round());
                    assertFalse(s.isHidden(1), "pairing releases the held drop");
                    Decision d = second.round().decision();
                    assertTrue(d.shown(rigged).value() > d.shown(rigged.other()).value());
                }
            }
        }
    }

    @Test
    void disabledSessionIgnoresEverything() {
        RigSession s = session(false, Side.ONE);
        assertSame(RigSession.Admission.IGNORED, s.offer(drop(1, Side.ONE, 3, 0, 0)));
        assertSame(RigSession.Admission.IGNORED, s.offer(drop(2, Side.TWO, 7.2, 0, 1)));
        assertFalse(s.isHidden(1));
        assertEquals(0, s.pendingCount());
    }

    @Test
    void togglingOffReleasesHeldItemsAndOnWorksAgain() {
        RigSession s = session(true, Side.ONE);
        s.offer(drop(1, Side.ONE, 3, 0, 0));
        assertTrue(s.isHidden(1));
        List<Integer> revealed = s.setEnabled(false);
        assertEquals(List.of(1), revealed);
        assertFalse(s.isHidden(1));
        assertEquals(0, s.pendingCount());

        s.setEnabled(true);
        s.offer(drop(3, Side.ONE, 3, 10, 0));
        RigSession.Admission a = s.offer(drop(4, Side.TWO, 7.2, 10, 1));
        assertNotNull(a.round());
        assertTrue(a.round().decision().shown1().value() > a.round().decision().shown2().value());
    }

    @Test
    void switchingRiggedDropperAppliesToNextRound() {
        RigSession s = session(true, Side.ONE);
        s.offer(drop(1, Side.ONE, 9, 0, 0));
        Decision d1 = s.offer(drop(2, Side.TWO, 1.2, 0, 1)).round().decision();
        assertEquals(Side.ONE, d1.shownWinner());

        s.setRigged(Side.TWO);
        s.offer(drop(3, Side.ONE, 9, 20, 0));
        Decision d2 = s.offer(drop(4, Side.TWO, 1.2, 20, 1)).round().decision();
        assertEquals(Side.TWO, d2.shownWinner());
    }

    @Test
    void heldItemIsRevealedAfterHoldButStillPairs() {
        RigSession s = session(true, Side.TWO);
        s.offer(drop(1, Side.ONE, 9, 0, 0));
        assertTrue(s.tick(5).isEmpty());
        assertEquals(List.of(1), s.tick(6), "hold ends after holdTicks");
        assertFalse(s.isHidden(1));
        // Partner arrives later but inside the pair window; the visible Dropper 1 item should stay
        // as-is and the new Dropper 2 item is the one adjusted.
        RigSession.Round r = s.offer(drop(2, Side.TWO, 1.2, 30, 1)).round();
        assertNotNull(r);
        assertFalse(r.decision().changed(Side.ONE));
        assertTrue(r.decision().shown2().value() > 9);
    }

    @Test
    void dropsOutsideWindowOrDistanceDoNotPair() {
        RigSession s = session(true, Side.ONE);
        s.offer(drop(1, Side.ONE, 3, 0, 0));
        s.tick(61);
        assertEquals(0, s.pendingCount(), "expired drops are forgotten");
        assertNull(s.offer(drop(2, Side.TWO, 7.2, 62, 1)).round());

        RigSession far = session(true, Side.ONE);
        far.offer(drop(1, Side.ONE, 3, 0, 0));
        assertNull(far.offer(drop(2, Side.TWO, 7.2, 0, 40)).round(), "items 40 blocks apart are different games");
    }

    @Test
    void forgettingAnEntityClearsIt() {
        RigSession s = session(true, Side.ONE);
        s.offer(drop(1, Side.ONE, 3, 0, 0));
        s.forget(1);
        assertFalse(s.isHidden(1));
        assertNull(s.offer(drop(2, Side.TWO, 7.2, 1, 1)).round());
    }

    @Test
    void zeroHoldNeverHides() {
        RigSession s = new RigSession(TABLE, new Random(1));
        s.configure(TABLE, new RigSession.Settings(0, 60, 12));
        s.setEnabled(true);
        assertFalse(s.offer(drop(1, Side.ONE, 3, 0, 0)).hidden());
        assertFalse(s.isHidden(1));
    }
}
