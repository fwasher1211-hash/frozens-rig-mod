package com.droprig.config;

import com.droprig.core.KeyBindings;
import com.droprig.core.RigSession;
import com.droprig.core.Side;

import java.util.ArrayList;
import java.util.List;

/** Saved settings (config/droprig.json). Field names are the JSON keys. */
public final class DropRigConfig {
    public static final List<String> DEFAULT_DROPPER_1 = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
    public static final List<String> DEFAULT_DROPPER_2 = List.of("9.2", "8.2", "7.2", "6.2", "5.2", "4.2", "3.2", "2.2", "1.2");

    /** Master toggle. */
    public boolean enabled = false;
    /** Which dropper shows the higher number: 1 or 2. */
    public int riggedDropper = 1;
    /** GLFW key codes; -1 = unbound. */
    public int toggleKey = KeyBindings.DEFAULT_TOGGLE;
    public int menuKey = KeyBindings.DEFAULT_MENU;

    /** The numbers each dropper can produce. */
    public List<String> dropper1Outcomes = new ArrayList<>(DEFAULT_DROPPER_1);
    public List<String> dropper2Outcomes = new ArrayList<>(DEFAULT_DROPPER_2);

    /** Ticks a fresh drop stays hidden while waiting for the other dropper (20 ticks = 1 s). */
    public int holdTicks = 6;
    /** Ticks within which two drops still count as one round. */
    public int pairWindowTicks = 60;
    /** Max blocks between the two dropped items for them to count as one round. */
    public double maxPairDistance = 12.0;
    /** Only items that appear within this many blocks of you are considered. */
    public double detectionRadius = 48.0;
    /** Only consider items that appear next to a dropper or dispenser block. */
    public boolean requireDropperNearby = true;
    /** Show an action-bar message when the toggle key is pressed. */
    public boolean showToggleMessage = true;

    public Side rigged() {
        return Side.fromNumber(riggedDropper);
    }

    public RigSession.Settings sessionSettings() {
        return new RigSession.Settings(holdTicks, pairWindowTicks, maxPairDistance);
    }

    /** Repairs anything missing or out of range after loading a hand-edited file. */
    public DropRigConfig sanitize() {
        if (riggedDropper != 1 && riggedDropper != 2) riggedDropper = 1;
        if (dropper1Outcomes == null || dropper1Outcomes.isEmpty()) dropper1Outcomes = new ArrayList<>(DEFAULT_DROPPER_1);
        if (dropper2Outcomes == null || dropper2Outcomes.isEmpty()) dropper2Outcomes = new ArrayList<>(DEFAULT_DROPPER_2);
        holdTicks = clamp(holdTicks, 0, 40);
        pairWindowTicks = clamp(pairWindowTicks, holdTicks, 400);
        if (!(maxPairDistance > 0) || maxPairDistance > 64) maxPairDistance = 12.0;
        if (!(detectionRadius > 0) || detectionRadius > 256) detectionRadius = 48.0;
        KeyBindings keys = new KeyBindings(toggleKey, menuKey);
        toggleKey = keys.get(KeyBindings.Action.TOGGLE_RIG);
        menuKey = keys.get(KeyBindings.Action.OPEN_MENU);
        return this;
    }

    public KeyBindings keyBindings() {
        return new KeyBindings(toggleKey, menuKey);
    }

    public void applyKeyBindings(KeyBindings keys) {
        toggleKey = keys.get(KeyBindings.Action.TOGGLE_RIG);
        menuKey = keys.get(KeyBindings.Action.OPEN_MENU);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
