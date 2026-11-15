package com.droprig.core;

/**
 * What the two dropped items should look like on screen. {@code real*} is what the server
 * actually dropped; {@code shown*} is what Drop Rig displays.
 */
public record Decision(Outcome real1, Outcome real2, Outcome shown1, Outcome shown2) {

    public Outcome real(Side side) {
        return side == Side.ONE ? real1 : real2;
    }

    public Outcome shown(Side side) {
        return side == Side.ONE ? shown1 : shown2;
    }

    public boolean changed(Side side) {
        return real(side) != shown(side);
    }

    public boolean anyChanged() {
        return changed(Side.ONE) || changed(Side.TWO);
    }

    /** The side whose shown value is higher. */
    public Side shownWinner() {
        return shown1.value() > shown2.value() ? Side.ONE : Side.TWO;
    }
}
