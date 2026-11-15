package com.droprig.core;

/** One of the two droppers. Pure Java: no Minecraft types, so it can be unit-tested. */
public enum Side {
    ONE(1),
    TWO(2);

    public final int number;

    Side(int number) {
        this.number = number;
    }

    public Side other() {
        return this == ONE ? TWO : ONE;
    }

    /** Maps a stored number (1 or 2) to a side; anything unexpected falls back to {@link #ONE}. */
    public static Side fromNumber(int n) {
        return n == 2 ? TWO : ONE;
    }
}
