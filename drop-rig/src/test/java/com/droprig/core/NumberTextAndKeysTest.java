package com.droprig.core;

import com.droprig.config.ConfigStore;
import com.droprig.config.DropRigConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NumberTextAndKeysTest {

    @Test
    void readsTheLastNumberAndSkipsFormatting() {
        assertEquals(7.2, NumberText.lastValue("7.2"), 1e-9);
        assertEquals(3.0, NumberText.lastValue("§63"), 1e-9, "§6 is a colour code, not a digit");
        assertEquals(3.0, NumberText.lastValue("Dropper 1: 3"), 1e-9);
        assertEquals(9.2, NumberText.lastValue("Roll §l9.2§r!"), 1e-9);
        assertNull(NumberText.lastValue("Diamond"));
        assertNull(NumberText.lastValue(null));
    }

    @Test
    void replacesNumberAcrossStyledSegments() {
        assertEquals(List.of("Roll: ", "8", " pts"), NumberText.replaceLast(List.of("Roll: ", "3", " pts"), "8"));
        assertEquals(List.of("§e1.2"), NumberText.replaceLast(List.of("§e7.2"), "1.2"));
        // A number split across two segments ("7" + ".2") collapses into the first one.
        assertEquals(List.of("x 9.2", ""), NumberText.replaceLast(List.of("x 7", ".2"), "9.2"));
        assertNull(NumberText.replaceLast(List.of("no digits"), "1"));
    }

    @Test
    void keybindAssignmentRules() {
        KeyBindings k = new KeyBindings(KeyBindings.DEFAULT_TOGGLE, KeyBindings.DEFAULT_MENU);
        assertEquals(KeyBindings.Action.TOGGLE_RIG, k.actionFor(KeyBindings.DEFAULT_TOGGLE));
        assertEquals(KeyBindings.Result.ASSIGNED, k.assign(KeyBindings.Action.TOGGLE_RIG, 75)); // K
        assertEquals(75, k.get(KeyBindings.Action.TOGGLE_RIG));
        assertEquals(KeyBindings.Action.TOGGLE_RIG, k.actionFor(75));
        assertNull(k.actionFor(KeyBindings.DEFAULT_TOGGLE), "old key no longer toggles");
        assertEquals(KeyBindings.Result.CONFLICT, k.assign(KeyBindings.Action.TOGGLE_RIG, KeyBindings.DEFAULT_MENU));
        assertEquals(KeyBindings.Result.RESERVED, k.assign(KeyBindings.Action.TOGGLE_RIG, KeyBindings.ESCAPE));
        assertEquals(KeyBindings.Result.UNCHANGED, k.assign(KeyBindings.Action.TOGGLE_RIG, 75));
        assertEquals(KeyBindings.Result.ASSIGNED, k.assign(KeyBindings.Action.TOGGLE_RIG, KeyBindings.UNBOUND));
        assertNull(k.actionFor(KeyBindings.UNBOUND));
        assertEquals(KeyBindings.Result.ASSIGNED, k.reset(KeyBindings.Action.TOGGLE_RIG));
        assertTrue(k.isDefault(KeyBindings.Action.TOGGLE_RIG));
    }

    @Test
    void resetStealsDefaultFromOtherAction() {
        KeyBindings k = new KeyBindings(KeyBindings.DEFAULT_TOGGLE, 80);
        k.assign(KeyBindings.Action.TOGGLE_RIG, 81);
        k.assign(KeyBindings.Action.OPEN_MENU, KeyBindings.DEFAULT_TOGGLE);
        k.reset(KeyBindings.Action.TOGGLE_RIG);
        assertEquals(KeyBindings.DEFAULT_TOGGLE, k.get(KeyBindings.Action.TOGGLE_RIG));
        assertEquals(KeyBindings.UNBOUND, k.get(KeyBindings.Action.OPEN_MENU));
    }

    @Test
    void configSurvivesRestartAndBadFiles() throws Exception {
        Path dir = Files.createTempDirectory("droprig");
        Path file = dir.resolve("droprig.json");
        ConfigStore store = new ConfigStore(file, s -> { });

        DropRigConfig cfg = store.load();
        assertFalse(cfg.enabled);
        cfg.enabled = true;
        cfg.riggedDropper = 2;
        cfg.toggleKey = 75;
        assertTrue(store.save(cfg));

        DropRigConfig again = new ConfigStore(file, s -> { }).load();
        assertTrue(again.enabled);
        assertEquals(2, again.riggedDropper);
        assertEquals(Side.TWO, again.rigged());
        assertEquals(75, again.toggleKey);

        Files.writeString(file, "{ this is not json");
        DropRigConfig recovered = new ConfigStore(file, s -> { }).load();
        assertFalse(recovered.enabled, "broken file falls back to defaults");
        assertTrue(Files.exists(dir.resolve("droprig.json.broken")));

        Files.writeString(file, "{\"riggedDropper\": 7, \"holdTicks\": -5, \"toggleKey\": 344, \"menuKey\": 344}");
        DropRigConfig fixed = new ConfigStore(file, s -> { }).load();
        assertEquals(1, fixed.riggedDropper);
        assertEquals(0, fixed.holdTicks);
        assertNotEquals(fixed.toggleKey, fixed.menuKey, "duplicate keys are repaired");
    }
}
