package com.droprig.rig;

import com.droprig.core.NumberText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Replaces the number inside a styled {@link Text} while keeping its colours and formatting. */
final class TextRelabel {
    private TextRelabel() {
    }

    static String plain(Text text) {
        return text == null ? null : text.getString();
    }

    /** Returns a copy of {@code text} with its last number replaced, or null if it has no number. */
    static Text relabel(Text text, String label) {
        if (text == null) return null;
        List<String> strings = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        text.visit((style, string) -> {
            strings.add(string);
            styles.add(style);
            return Optional.empty();
        }, Style.EMPTY);
        List<String> replaced = NumberText.replaceLast(strings, label);
        if (replaced == null) return null;
        MutableText out = Text.empty();
        for (int i = 0; i < replaced.size(); i++) {
            if (replaced.get(i).isEmpty()) continue;
            out.append(Text.literal(replaced.get(i)).setStyle(styles.get(i)));
        }
        return out;
    }
}
