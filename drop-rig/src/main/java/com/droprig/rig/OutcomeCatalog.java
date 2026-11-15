package com.droprig.rig;

import com.droprig.core.Side;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Remembers what each real outcome looked like the first time it was seen (exact item stack and
 * nameplate). When Drop Rig needs to show "8", it reuses the real "8" item if it has seen one, so
 * the item type, count, enchant glint and name all match perfectly. Otherwise it falls back to
 * editing the number in the name.
 */
final class OutcomeCatalog {
    record Snapshot(ItemStack stack, Text entityName) {
    }

    private final Map<Side, Map<String, Snapshot>> seen = new EnumMap<>(Side.class);

    OutcomeCatalog() {
        for (Side s : Side.values()) seen.put(s, new HashMap<>());
    }

    void record(Side side, String label, ItemStack stack, Text entityName) {
        if (stack == null || stack.isEmpty()) return;
        seen.get(side).put(label, new Snapshot(stack.copy(), entityName));
    }

    Snapshot get(Side side, String label) {
        return seen.get(side).get(label);
    }

    boolean has(Side side, String label) {
        return seen.get(side).containsKey(label);
    }

    int count(Side side) {
        return seen.get(side).size();
    }

    void clear() {
        for (Map<String, Snapshot> m : seen.values()) m.clear();
    }
}
