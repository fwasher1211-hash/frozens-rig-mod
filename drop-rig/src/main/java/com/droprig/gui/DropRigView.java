package com.droprig.gui;

import com.droprig.core.KeyBindings;
import com.droprig.core.Side;
import com.droprig.gui.render.Anim;
import com.droprig.gui.render.Col;
import com.droprig.gui.render.FontSpec;
import com.droprig.gui.render.Gfx;
import com.droprig.gui.render.Paint;
import com.droprig.gui.render.TextSink;

import java.util.List;
import java.util.Set;

import static com.droprig.gui.Theme.*;

/**
 * Layout, drawing, animation and hit-testing for the DROP RIG panel, in a fixed 404×326 layout
 * space (the screen scales it to fit). No Minecraft types are used here: the in-game screen feeds
 * it a {@link State} and executes the {@link Intent}s it returns, which keeps every control wired
 * to real settings and lets the exact same code render offline previews.
 */
public final class DropRigView {
    public static final float W = 404, H = 326;
    private static final float PAD = 18;
    private static final float HEADER_CY = 31;
    private static final float TOG_W = 40, TOG_H = 22, TOG_X = W - PAD - TOG_W, TOG_Y = HEADER_CY - TOG_H / 2;
    private static final float SECTION_CY = 72;
    private static final float CARD_Y = 84, CARD_W = 169, CARD_H = 142, GAP = 30;
    private static final float CHIPS_TOP = 52;
    private static final float KEYS_CY = 246, KEY_Y = 256, KEY_H = 30;
    private static final float FOOT_LINE_Y = 298, FOOT_CY = 311;
    private static final float CHIP_H = 18, CHIP_GAP = 5;

    /** Everything the view displays. Filled from the real config/rigger every frame. */
    public static final class State {
        public boolean enabled;
        public Side rigged = Side.ONE;
        public List<String> labels1 = List.of();
        public List<String> labels2 = List.of();
        public Set<String> seen1 = Set.of();
        public Set<String> seen2 = Set.of();
        /** "x, y, z" of the detected dropper, or null. */
        public String detect1, detect2;
        /** Last round (null if none). */
        public String real1, real2, shown1, shown2;
        public float lastRoundAge = Float.MAX_VALUE;
        public String toggleKey = "J", menuKey = "RIGHT SHIFT";
        public boolean toggleIsDefault = true, menuIsDefault = true;
        public String version = "1.0.0";
    }

    public enum Intent {
        NONE, TOGGLE_ENABLED, SELECT_ONE, SELECT_TWO, RECORD_TOGGLE, RECORD_MENU, RESET_TOGGLE, RESET_MENU, CANCEL_RECORD
    }

    // ---------------------------------------------------------------- animation state

    private float time;
    private final Anim on = new Anim(0), sel = new Anim(0);
    private final Anim hToggle = new Anim(0), hPill = new Anim(0);
    private final Anim[] hCard = {new Anim(0), new Anim(0)};
    private final Anim[] hRow = {new Anim(0), new Anim(0)};
    private final Anim[] hChip = {new Anim(0), new Anim(0)};
    private final Anim[] hReset = {new Anim(0), new Anim(0)};
    private final Anim[] rec = {new Anim(0), new Anim(0)};
    private boolean primed;

    private KeyBindings.Action recording;
    private KeyBindings.Action errorAction;
    private String errorMessage;
    private float errorT;
    private final float[] savedT = new float[2];
    private final float[] resetSpinT = new float[2];
    private Side pulseSide;
    private float pulseT = 1;

    // hit boxes measured during the last render: x, y, w, h
    private final float[][] chipBox = new float[2][4];
    private final float[][] resetBox = new float[2][4];
    private final float[] pillBox = new float[4];

    public KeyBindings.Action recording() {
        return recording;
    }

    public void startRecording(KeyBindings.Action a) {
        recording = a;
        errorAction = null;
    }

    public void stopRecording() {
        recording = null;
    }

    public void flashError(KeyBindings.Action a, String message) {
        errorAction = a;
        errorMessage = message;
        errorT = 1f;
    }

    public void flashSaved(KeyBindings.Action a) {
        savedT[a.ordinal()] = 1f;
        errorAction = null;
    }

    public void spinReset(KeyBindings.Action a) {
        resetSpinT[a.ordinal()] = 1f;
        flashSaved(a);
    }

    public void pulse(Side side) {
        pulseSide = side;
        pulseT = 0;
    }

    // ---------------------------------------------------------------- input

    public Intent click(float mx, float my) {
        if (recording != null) {
            KeyBindings.Action a = recording;
            if (inside(chipBox[a.ordinal()], mx, my)) return Intent.CANCEL_RECORD;
        }
        if (inside(TOG_X - 2, TOG_Y - 2, TOG_W + 4, TOG_H + 4, mx, my) || inside(pillBox, mx, my)) return Intent.TOGGLE_ENABLED;
        if (inside(cardX(Side.ONE), CARD_Y, CARD_W, CARD_H, mx, my)) return Intent.SELECT_ONE;
        if (inside(cardX(Side.TWO), CARD_Y, CARD_W, CARD_H, mx, my)) return Intent.SELECT_TWO;
        for (KeyBindings.Action a : KeyBindings.Action.values()) {
            int i = a.ordinal();
            if (inside(resetBox[i], mx, my)) return a == KeyBindings.Action.TOGGLE_RIG ? Intent.RESET_TOGGLE : Intent.RESET_MENU;
            if (inside(chipBox[i], mx, my) || inside(rowX(a), KEY_Y, CARD_W, KEY_H, mx, my) && !inside(resetBox[i], mx, my)) {
                return a == KeyBindings.Action.TOGGLE_RIG ? Intent.RECORD_TOGGLE : Intent.RECORD_MENU;
            }
        }
        return recording != null ? Intent.CANCEL_RECORD : Intent.NONE;
    }

    public boolean isOverPanel(float mx, float my) {
        return inside(0, 0, W, H, mx, my);
    }

    private static boolean inside(float x, float y, float w, float h, float mx, float my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    private static boolean inside(float[] b, float mx, float my) {
        return b[2] > 0 && inside(b[0], b[1], b[2], b[3], mx, my);
    }

    private static float cardX(Side s) {
        return s == Side.ONE ? PAD : PAD + CARD_W + GAP;
    }

    private static float rowX(KeyBindings.Action a) {
        return a == KeyBindings.Action.TOGGLE_RIG ? PAD : PAD + CARD_W + GAP;
    }

    // ---------------------------------------------------------------- render

    private Gfx g;
    private TextSink t;

    /**
     * @param vis overall visibility 0..1 (open/close animation)
     * @param intro seconds since the panel was opened (drives the staggered entrance)
     */
    public void render(Gfx g, TextSink t, State s, float mx, float my, float dt, float vis, float intro) {
        this.g = g;
        this.t = t;
        time += dt;
        if (!primed) {
            on.snap(s.enabled ? 1 : 0);
            sel.snap(s.rigged == Side.TWO ? 1 : 0);
            primed = true;
        }
        step(s, mx, my, dt);

        g.alpha = vis;
        g.dy = 0;
        panel();

        section(vis, intro, 0);
        header(s);

        section(vis, intro, 1);
        t.draw(FontSpec.LABEL, "RIGGED DROPPER", PAD, SECTION_CY + g.dy, a(TEXT_3), 1.4f, 0);
        text(FontSpec.CAPTION, "Choose which dropper shows the higher number", W - PAD, SECTION_CY, TEXT_3, 0, 1);
        card(s, Side.ONE);
        card(s, Side.TWO);
        connector();

        section(vis, intro, 2);
        keybinds(s);
        footer(s);

        this.g = null;
        this.t = null;
    }

    private void section(float vis, float intro, int index) {
        float k = Anim.outCubic((intro - 0.05f - index * 0.06f) / 0.42f);
        g.alpha = vis * k;
        g.dy = (1 - k) * 10f;
    }

    private void step(State s, float mx, float my, float dt) {
        on.step(s.enabled ? 1 : 0, 12, dt);
        sel.step(s.rigged == Side.TWO ? 1 : 0, 10, dt);
        hToggle.step(inside(TOG_X, TOG_Y, TOG_W, TOG_H, mx, my) ? 1 : 0, 16, dt);
        hPill.step(inside(pillBox, mx, my) ? 1 : 0, 16, dt);
        for (Side side : Side.values()) {
            hCard[side.ordinal()].step(inside(cardX(side), CARD_Y, CARD_W, CARD_H, mx, my) ? 1 : 0, 14, dt);
        }
        for (KeyBindings.Action a : KeyBindings.Action.values()) {
            int i = a.ordinal();
            hRow[i].step(inside(rowX(a), KEY_Y, CARD_W, KEY_H, mx, my) ? 1 : 0, 14, dt);
            hChip[i].step(inside(chipBox[i], mx, my) ? 1 : 0, 16, dt);
            hReset[i].step(inside(resetBox[i], mx, my) ? 1 : 0, 16, dt);
            rec[i].step(recording == a ? 1 : 0, 14, dt);
            savedT[i] = Math.max(0, savedT[i] - dt / 0.7f);
            resetSpinT[i] = Math.max(0, resetSpinT[i] - dt / 0.55f);
        }
        errorT = Math.max(0, errorT - dt / 2.2f);
        pulseT = Math.min(1, pulseT + dt / 0.65f);
    }

    /** Text helper that applies the current section fade and slide. */
    private void text(FontSpec f, String s, float x, float cy, int color, float tracking, float align) {
        t.draw(f, s, x, cy + g.dy, Col.mulA(color, g.alpha), tracking, align);
    }

    private int a(int color) {
        return Col.mulA(color, g.alpha);
    }

    private int accentNow() {
        return Col.lerp(SLATE, ACCENT, on.get());
    }

    // ---------------------------------------------------------------- panel

    private void panel() {
        g.shadow(0, 0, W, H, 18, 40, 0x7A000000, 12);
        g.shadow(0, 0, W, H, 18, 8, 0x3C000000, 2);
        g.rect(0, 0, W, H, 18, Paint.vertical(0, H, PANEL_TOP, PANEL_BOTTOM));

        // ambient aurora, drifting slowly; brighter when rigging is active
        float o = on.get();
        float drift = (float) Math.sin(time * 0.45f) * 10f;
        g.glow(120 + drift, 70, 135, 54, Col.alpha(ACCENT, 0.06f + 0.07f * o));
        g.glow(300 - drift, 262, 104, 46, Col.alpha(ACCENT_3, 0.04f + 0.05f * o));

        // top sheen and glass edge
        g.rect(0, 0, W, 120, 18, Paint.vertical(0, 120, 0x10FFFFFF, 0x00FFFFFF));
        g.border(0, 0, W, H, 18, Math.max(g.px, 0.5f), Paint.vertical(0, H, 0x38FFFFFF, 0x0EFFFFFF));
        g.fadeLineH(40, 1.2f, W - 80, 0x30FFFFFF);
    }

    // ---------------------------------------------------------------- header

    private void header(State s) {
        float o = on.get(), sv = sel.get();

        // logo: glass tile with two bars; the rigged dropper's bar is the tall one
        float lx = PAD, ly = HEADER_CY - 14;
        g.shadow(lx, ly, 28, 28, 9, 12, Col.alpha(ACCENT_2, 0.38f * o), 2);
        g.rect(lx, ly, 28, 28, 9, Paint.diagonal(lx, ly, 28, 28,
                Col.lerp(0xFF4A5568, ACCENT, o), Col.lerp(0xFF2A3242, ACCENT_3, o)));
        g.rect(lx, ly, 28, 14, 9, Paint.vertical(ly, 14, 0x40FFFFFF, 0x00FFFFFF));
        g.border(lx, ly, 28, 28, 9, Math.max(g.px, 0.5f), Paint.vertical(ly, 28, 0x66FFFFFF, 0x14FFFFFF));
        float base = ly + 21;
        float h1 = Anim.lerp(14, 7, sv), h2 = Anim.lerp(7, 14, sv);
        g.rect(lx + 7, base - h1, 5, h1, 2.2f, Col.alpha(0xFFFFFF, Anim.lerp(0.95f, 0.55f, sv)));
        g.rect(lx + 16, base - h2, 5, h2, 2.2f, Col.alpha(0xFFFFFF, Anim.lerp(0.55f, 0.95f, sv)));

        text(FontSpec.TITLE, "DROP RIG", lx + 38, HEADER_CY - 5, TEXT, 1.8f, 0);
        text(FontSpec.BODY, "Advanced Dropper Controls", lx + 38, HEADER_CY + 9.5f, TEXT_2, 0, 0);

        // status pill
        float wOn = t.width(FontSpec.BADGE, "ACTIVE", 1.1f), wOff = t.width(FontSpec.BADGE, "DISABLED", 1.1f);
        float pw = 26 + Anim.lerp(wOff, wOn, o);
        float ph = 18, pyy = HEADER_CY - ph / 2, pxx = TOG_X - 10 - pw;
        pillBox[0] = pxx;
        pillBox[1] = pyy;
        pillBox[2] = pw;
        pillBox[3] = ph;
        float hp = hPill.get();
        g.rect(pxx, pyy, pw, ph, 9, Col.lerp(0x0DFFFFFF, 0x14FFFFFF, hp));
        g.rect(pxx, pyy, pw, ph, 9, Col.alpha(ACCENT, 0.07f * o));
        g.border(pxx, pyy, pw, ph, 9, Math.max(g.px, 0.5f), Col.lerp(0x1AFFFFFF, Col.alpha(ACCENT, 0.35f), o));
        float dx = pxx + 10, dyy = HEADER_CY;
        if (o > 0.01f) {
            float ph1 = (time % 1.8f) / 1.8f;
            g.circle(dx, dyy, 2.6f + 5f * Anim.outCubic(ph1), Col.alpha(ACCENT, 0.45f * (1 - ph1) * o));
            g.circle(dx, dyy, 5f, Col.alpha(ACCENT, 0.12f * o));
        }
        g.circle(dx, dyy, 2.6f, Col.lerp(SLATE, ACCENT, o));
        text(FontSpec.BADGE, "ACTIVE", pxx + 17, HEADER_CY, Col.mulA(ACCENT_SOFT, o), 1.1f, 0);
        text(FontSpec.BADGE, "DISABLED", pxx + 17, HEADER_CY, Col.mulA(TEXT_2, 1 - o), 1.1f, 0);

        // toggle switch
        float ht = hToggle.get();
        float ease = Anim.inOutCubic(o);
        g.shadow(TOG_X, TOG_Y, TOG_W, TOG_H, 11, 10, Col.alpha(ACCENT_2, 0.45f * o), 0);
        g.rect(TOG_X, TOG_Y, TOG_W, TOG_H, 11, Paint.vertical(TOG_Y, TOG_H,
                Col.lerp(0x18FFFFFF, 0x24FFFFFF, ht), Col.lerp(0x0CFFFFFF, 0x14FFFFFF, ht)));
        float saved = g.alpha;
        g.alpha = saved * o;
        g.rect(TOG_X, TOG_Y, TOG_W, TOG_H, 11, Paint.diagonal(TOG_X, TOG_Y, TOG_W, TOG_H, ACCENT, ACCENT_2));
        g.rect(TOG_X, TOG_Y, TOG_W, TOG_H / 2, 11, Paint.vertical(TOG_Y, TOG_H / 2, 0x33FFFFFF, 0x00FFFFFF));
        g.alpha = saved;
        g.border(TOG_X, TOG_Y, TOG_W, TOG_H, 11, Math.max(g.px, 0.5f),
                Col.lerp(Col.lerp(0x24FFFFFF, 0x3AFFFFFF, ht), 0x55FFFFFF, o));
        float stretch = 4f * (float) Math.sin(Math.PI * ease);
        float kw = 16 + stretch, kh = 16;
        float kcx = Anim.lerp(TOG_X + 11, TOG_X + TOG_W - 11, ease);
        float kx = kcx - kw / 2, ky = TOG_Y + (TOG_H - kh) / 2;
        g.shadow(kx, ky, kw, kh, 8, 5, 0x50000000, 1.2f);
        g.rect(kx, ky, kw, kh, 8, Paint.vertical(ky, kh, 0xFFFFFFFF, Col.lerp(0xFFC9D3E1, 0xFFE6F7FF, o)));
    }

    // ---------------------------------------------------------------- dropper cards

    private void card(State s, Side side) {
        int i = side.ordinal();
        float o = on.get();
        float sv = side == Side.ONE ? 1 - sel.get() : sel.get();
        float hv = hCard[i].get();
        float x = cardX(side), y = CARD_Y - 1.5f * hv, w = CARD_W, h = CARD_H, r = 14;
        int acc = accentNow();

        // depth + selection glow
        g.shadow(x, y, w, h, r, 12, Col.alpha(0x000000, 0.28f + 0.1f * hv), 5);
        g.shadow(x, y, w, h, r, 18, Col.alpha(Col.lerp(SLATE, ACCENT_2, o), (0.18f + 0.22f * o) * sv), 0);

        g.rect(x, y, w, h, r, Paint.vertical(y, h, Col.lerp(GLASS_HI, 0x20FFFFFF, hv * 0.6f), GLASS_LO));
        if (sv > 0.01f) {
            float saved = g.alpha;
            g.alpha = saved * sv;
            g.rect(x, y, w, h, r, Paint.diagonal(x, y, w, h, Col.alpha(acc, 0.16f), Col.alpha(Col.lerp(SLATE, ACCENT_3, o), 0.03f)));
            g.alpha = saved;
        }
        g.rect(x, y, w, 40, r, Paint.vertical(y, 40, 0x0DFFFFFF, 0x00FFFFFF));
        g.border(x, y, w, h, r, Math.max(g.px, 0.5f), Paint.vertical(y, h, Col.lerp(0x22FFFFFF, 0x34FFFFFF, hv), 0x0AFFFFFF));
        if (sv > 0.01f) {
            float saved = g.alpha;
            g.alpha = saved * sv;
            float phase = time * 1.4f + (side == Side.ONE ? 0 : 3.1f);
            g.border(x, y, w, h, r, Math.max(g.px * 1.6f, 0.9f), Paint.sweep(x + w / 2, y + h / 2, phase,
                    Col.alpha(Col.lerp(SLATE, ACCENT_2, o), 0.40f), Col.lerp(Col.alpha(SLATE, 0.9f), ACCENT, o)));
            g.alpha = saved;
        }
        if (pulseSide == side && pulseT < 1) {
            float k = Anim.outCubic(pulseT), grow = 7 * k;
            g.border(x - grow, y - grow, w + grow * 2, h + grow * 2, r + grow, Math.max(g.px * 1.5f, 0.8f),
                    Col.alpha(acc, 0.55f * (1 - pulseT)));
        }

        // title, range and badge
        String title = side == Side.ONE ? "DROPPER 1" : "DROPPER 2";
        List<String> labels = side == Side.ONE ? s.labels1 : s.labels2;
        text(FontSpec.HEADING, title, x + 14, y + 20, Col.lerp(TEXT_2, TEXT, Math.max(sv, hv * 0.5f)), 0.9f, 0);
        String range = labels.isEmpty() ? "No outcomes" : labels.get(0) + "  –  " + labels.get(labels.size() - 1);
        text(FontSpec.CAPTION, range, x + 14, y + 36, TEXT_3, 0, 0);
        badge(x + w - 14, y + 36, sv, o);

        // radio indicator
        float rx = x + w - 21, ry = y + 20;
        g.circle(rx, ry, 6.5f, Col.lerp(0x0FFFFFFF, 0x1AFFFFFF, hv));
        g.border(rx - 6.5f, ry - 6.5f, 13, 13, 6.5f, Math.max(g.px, 0.6f), Col.lerp(0x40FFFFFF, 0x00FFFFFF, sv));
        if (sv > 0.01f) {
            float k = Anim.outBack(sv);
            g.circle(rx, ry, 6.5f * Math.min(1, k), Paint.diagonal(rx - 6.5f, ry - 6.5f, 13, 13,
                    Col.mulA(Col.lerp(0xFF8592A6, ACCENT, o), sv), Col.mulA(Col.lerp(0xFF5D6A7E, ACCENT_2, o), sv)));
            float ck = Anim.clamp01((sv - 0.35f) / 0.65f);
            int cc = Col.mulA(INK, ck);
            g.line(rx - 2.9f, ry + 0.1f, rx - 0.9f, ry + 2.1f, 1.3f, cc);
            g.line(rx - 0.9f, ry + 2.1f, rx + 3.0f, ry - 2.0f, 1.3f, cc);
        }

        // outcome chips
        Set<String> seen = side == Side.ONE ? s.seen1 : s.seen2;
        String shown = side == Side.ONE ? s.shown1 : s.shown2;
        float hl = s.lastRoundAge < 4f ? 1 - Anim.inCubic(s.lastRoundAge / 4f) : 0;
        float cw = (w - 28 - CHIP_GAP * 2) / 3f;
        int count = Math.min(9, labels.size());
        for (int k = 0; k < count; k++) {
            String label = labels.get(k);
            float cx = x + 14 + (k % 3) * (cw + CHIP_GAP);
            float cy = y + CHIPS_TOP + (k / 3) * (CHIP_H + CHIP_GAP);
            boolean isShown = label.equals(shown);
            float lit = isShown ? hl : 0;
            if (lit > 0) g.shadow(cx, cy, cw, CHIP_H, 6, 7, Col.alpha(acc, 0.35f * lit), 0);
            g.rect(cx, cy, cw, CHIP_H, 6, Col.lerp(Col.lerp(0x0AFFFFFF, 0x10FFFFFF, sv), Col.alpha(acc, 0.24f), lit));
            g.border(cx, cy, cw, CHIP_H, 6, Math.max(g.px, 0.5f), Col.lerp(Col.lerp(0x12FFFFFF, 0x1AFFFFFF, sv), Col.alpha(acc, 0.75f), lit));
            if (seen.contains(label)) g.circle(cx + cw - 5, cy + 5, 1.3f, Col.alpha(Col.lerp(SLATE, ACCENT, o), 0.85f));
            int tc = Col.lerp(Col.lerp(0xFF8E9BB0, TEXT, sv), 0xFFFFFFFF, lit);
            text(FontSpec.HEADING, label, cx + cw / 2, cy + CHIP_H / 2, tc, 0, 0.5f);
        }

        // detection status
        String det = side == Side.ONE ? s.detect1 : s.detect2;
        float fy = y + 129;
        int dot = det != null ? Col.lerp(SLATE, MINT, o) : SLATE_2;
        if (det != null) g.circle(x + 17, fy, 4.2f, Col.alpha(dot, 0.16f));
        g.circle(x + 17, fy, 2.2f, dot);
        text(FontSpec.CAPTION, det != null ? "Detected at " + det : "Waiting for first drop", x + 25, fy,
                det != null ? TEXT_2 : TEXT_3, 0, 0);
    }

    /** HIGHER / LOWER badge, right-aligned at {@code right}; cross-fades with the selection. */
    private void badge(float right, float cy, float sv, float o) {
        float wHi = t.width(FontSpec.BADGE, "HIGHER NUMBER", 0.7f);
        float wLo = t.width(FontSpec.BADGE, "LOWER NUMBER", 0.7f);
        float bw = 8 + 6 + 5 + Anim.lerp(wLo, wHi, sv) + 8, bh = 15;
        float bx = right - bw, by = cy - bh / 2;
        float saved = g.alpha;

        if (sv > 0.01f) {
            g.alpha = saved * sv;
            g.shadow(bx, by, bw, bh, 7.5f, 8, Col.alpha(Col.lerp(SLATE, ACCENT_2, o), 0.45f * (0.75f + 0.25f * (float) Math.sin(time * 2.4f))), 0);
            g.rect(bx, by, bw, bh, 7.5f, Paint.diagonal(bx, by, bw, bh, Col.lerp(0xFF8592A6, ACCENT, o), Col.lerp(0xFF5D6A7E, ACCENT_2, o)));
            g.rect(bx, by, bw, bh / 2, 7.5f, Paint.vertical(by, bh / 2, 0x40FFFFFF, 0x00FFFFFF));
            g.chevronV(bx + 11, cy, 2.6f, 1.6f, 1, 1.25f, INK);
            g.alpha = saved;
            text(FontSpec.BADGE, "HIGHER NUMBER", bx + 19, cy, Col.mulA(INK, sv), 0.7f, 0);
        }
        if (sv < 0.99f) {
            g.alpha = saved * (1 - sv);
            g.rect(bx, by, bw, bh, 7.5f, 0x0CFFFFFF);
            g.border(bx, by, bw, bh, 7.5f, Math.max(g.px, 0.5f), 0x22FFFFFF);
            g.chevronV(bx + 11, cy, 2.6f, 1.6f, -1, 1.25f, TEXT_3);
            g.alpha = saved;
            text(FontSpec.BADGE, "LOWER NUMBER", bx + 19, cy, Col.mulA(TEXT_2, 1 - sv), 0.7f, 0);
        }
    }

    /** VS medallion between the cards, with chevrons flowing toward the rigged dropper. */
    private void connector() {
        float cx = PAD + CARD_W + GAP / 2, cy = CARD_Y + CARD_H / 2;
        float o = on.get();
        float dir = Anim.lerp(-1, 1, Anim.inOutCubic(sel.get()));
        g.fadeLineV(cx - g.px / 2, CARD_Y + 10, CARD_H - 20, 0x22FFFFFF);
        for (int row = -1; row <= 1; row += 2) {
            float yy = cy + row * 34;
            for (int k = 0; k < 3; k++) {
                float order = dir >= 0 ? k : 2 - k;
                float wave = 0.5f + 0.5f * (float) Math.sin(time * 4.2f - order * 1.1f);
                float alpha = Anim.lerp(0.22f, 0.25f + 0.65f * wave, o);
                g.chevronH(cx - 5.5f + k * 5.5f, yy, 1.7f, 3f, dir, 1.15f, Col.alpha(Col.lerp(SLATE, ACCENT, o), alpha));
            }
        }
        g.shadow(cx - 12, cy - 12, 24, 24, 12, 10, 0x55000000, 2);
        g.circle(cx, cy, 12, Paint.vertical(cy - 12, 24, 0xFF1B2438, 0xFF0F1524));
        g.circle(cx, cy, 12, Col.alpha(ACCENT, 0.06f * o));
        g.border(cx - 12, cy - 12, 24, 24, 12, Math.max(g.px, 0.5f), Paint.vertical(cy - 12, 24, 0x36FFFFFF, 0x10FFFFFF));
        text(FontSpec.BADGE, "VS", cx + 0.3f, cy, Col.lerp(TEXT_3, TEXT_2, o), 0.6f, 0.5f);
    }

    // ---------------------------------------------------------------- keybinds

    private void keybinds(State s) {
        t.draw(FontSpec.LABEL, "KEYBINDS", PAD, KEYS_CY + g.dy, a(TEXT_3), 1.4f, 0);
        if (recording != null) {
            float pulse = 0.65f + 0.35f * (float) Math.sin(time * 5);
            text(FontSpec.CAPTION, "Press a key   ·   Esc to cancel   ·   Backspace to unbind",
                    W - PAD, KEYS_CY, Col.mulA(ACCENT_SOFT, pulse), 0, 1);
        } else if (errorT > 0 && errorMessage != null) {
            text(FontSpec.CAPTION, errorMessage, W - PAD, KEYS_CY, Col.mulA(ROSE, Math.min(1, errorT * 3)), 0, 1);
        }
        keyRow(KeyBindings.Action.TOGGLE_RIG, s.toggleKey, s.toggleIsDefault);
        keyRow(KeyBindings.Action.OPEN_MENU, s.menuKey, s.menuIsDefault);
    }

    private void keyRow(KeyBindings.Action action, String keyName, boolean isDefault) {
        int i = action.ordinal();
        float x = rowX(action), y = KEY_Y, w = CARD_W, h = KEY_H, cy = y + h / 2;
        float hv = hRow[i].get(), rv = rec[i].get();
        int acc = ACCENT;

        g.rect(x, y, w, h, 10, Paint.vertical(y, h, Col.lerp(0x10FFFFFF, 0x17FFFFFF, hv), 0x07FFFFFF));
        g.border(x, y, w, h, 10, Math.max(g.px, 0.5f), Col.lerp(0x1AFFFFFF, 0x2AFFFFFF, hv));

        // label with a small icon
        float ix = x + 12;
        if (action == KeyBindings.Action.TOGGLE_RIG) {
            g.rect(ix, cy - 2.7f, 10, 5.4f, 2.7f, Col.lerp(0x55FFFFFF, Col.alpha(ACCENT, 0.95f), on.get()));
            g.circle(Anim.lerp(ix + 2.7f, ix + 7.3f, on.get()), cy, 1.8f, 0xFF0E1422);
        } else {
            g.border(ix, cy - 3.6f, 10, 7.2f, 2, 0.9f, 0x80FFFFFF);
            g.line(ix + 2.4f, cy - 1.2f, ix + 7.6f, cy - 1.2f, 0.9f, 0x80FFFFFF);
            g.line(ix + 2.4f, cy + 1.2f, ix + 5.6f, cy + 1.2f, 0.9f, 0x80FFFFFF);
        }
        String label = action.label;
        text(FontSpec.BODY, label, x + 27, cy, TEXT, 0, 0);
        float labelEnd = x + 27 + t.width(FontSpec.BODY, label);

        // reset button
        float rcx = x + w - 16, rr = 9;
        float hr = hReset[i].get();
        resetBox[i][0] = rcx - rr;
        resetBox[i][1] = cy - rr;
        resetBox[i][2] = rr * 2;
        resetBox[i][3] = rr * 2;
        float dim = isDefault ? 0.4f : 1f;
        g.circle(rcx, cy, rr, Col.alpha(0xFFFFFF, (0.03f + 0.07f * hr) * dim));
        g.border(rcx - rr, cy - rr, rr * 2, rr * 2, rr, Math.max(g.px, 0.5f), Col.alpha(0xFFFFFF, (0.10f + 0.10f * hr) * dim));
        float spin = -hr * 0.7f - Anim.outCubic(1 - resetSpinT[i]) * (resetSpinT[i] > 0 ? (float) Math.PI * 2 : 0);
        int ic = Col.mulA(Col.lerp(TEXT_2, TEXT, hr), dim);
        float ar = 3.7f, a0 = (float) Math.toRadians(-60) + spin, a1 = (float) Math.toRadians(225) + spin;
        g.arc(rcx, cy, ar, a0, a1, 1.15f, ic);
        float hx = rcx + (float) Math.cos(a0) * ar, hy = cy + (float) Math.sin(a0) * ar;
        float tx = (float) -Math.sin(a0), ty = (float) Math.cos(a0); // tangent (clockwise)
        float nx = (float) Math.cos(a0), ny = (float) Math.sin(a0);
        g.triangle(hx - tx * 2.4f, hy - ty * 2.4f, hx + nx * 1.9f + tx * 0.6f, hy + ny * 1.9f + ty * 0.6f,
                hx - nx * 1.9f + tx * 0.6f, hy - ny * 1.9f + ty * 0.6f, ic);

        // key chip
        String shown = rv > 0.5f ? "Press key" : keyName;
        float chipRight = rcx - rr - 5;
        float maxW = chipRight - (labelEnd + 8);
        float tw = t.width(FontSpec.LABEL, shown, 0.5f);
        while (tw + 16 > maxW && shown.length() > 2) {
            shown = shown.substring(0, shown.length() - 2) + "…";
            tw = t.width(FontSpec.LABEL, shown, 0.5f);
        }
        float cw = Math.min(Math.max(40, tw + 16), Math.max(40, maxW)), ch = 20;
        float err = errorAction == action ? errorT : 0;
        float shake = err > 0.7f ? (float) Math.sin(time * 70) * 2.2f * (err - 0.7f) / 0.3f : 0;
        float cx = chipRight - cw + shake, cyy = cy - ch / 2;
        chipBox[i][0] = chipRight - cw;
        chipBox[i][1] = cyy;
        chipBox[i][2] = cw;
        chipBox[i][3] = ch;
        float hc = hChip[i].get();
        float sv = savedT[i];

        g.rect(cx, cyy, cw, ch, 7, Paint.vertical(cyy, ch, Col.lerp(0x18FFFFFF, 0x24FFFFFF, hc), 0x0CFFFFFF));
        if (rv > 0.01f) {
            float saved = g.alpha;
            g.alpha = saved * rv;
            g.shadow(cx, cyy, cw, ch, 7, 8, Col.alpha(ACCENT_2, 0.35f + 0.15f * (float) Math.sin(time * 5)), 0);
            g.rect(cx, cyy, cw, ch, 7, Col.alpha(acc, 0.14f));
            g.border(cx, cyy, cw, ch, 7, Math.max(g.px * 1.5f, 0.8f), Paint.sweep(cx + cw / 2, cyy + ch / 2, time * 3.2f, Col.alpha(ACCENT_2, 0.45f), ACCENT));
            g.alpha = saved;
        }
        int borderC = Col.lerp(0x26FFFFFF, 0x40FFFFFF, hc);
        borderC = Col.lerp(borderC, Col.alpha(MINT, 0.9f), sv);
        borderC = Col.lerp(borderC, Col.alpha(ROSE, 0.95f), Math.min(1, err * 2.5f));
        g.border(cx, cyy, cw, ch, 7, Math.max(g.px, 0.5f), Col.mulA(borderC, 1 - rv));
        int tc = Col.lerp(Col.lerp(TEXT, ACCENT_SOFT, rv), MINT, sv);
        if ("NONE".equals(shown) && rv < 0.5f) tc = TEXT_3;
        text(FontSpec.LABEL, shown, cx + cw / 2, cy, tc, 0.5f, 0.5f);
    }

    // ---------------------------------------------------------------- footer

    private void footer(State s) {
        g.fadeLineH(PAD, FOOT_LINE_Y, W - PAD * 2, 0x24FFFFFF);
        t.draw(FontSpec.BADGE, "LAST ROUND", PAD, FOOT_CY + g.dy, a(TEXT_3), 1.2f, 0);
        float x = PAD + t.width(FontSpec.BADGE, "LAST ROUND", 1.2f) + 10;
        if (!s.enabled) {
            text(FontSpec.CAPTION, "Rigging is off · droppers show their real results", x, FOOT_CY, TEXT_3, 0, 0);
        } else if (s.shown1 == null) {
            text(FontSpec.CAPTION, "Waiting for both droppers…", x, FOOT_CY, TEXT_3, 0, 0);
        } else {
            x = roundPart(x, "D1", s.shown1, s.real1, s.rigged == Side.ONE);
            text(FontSpec.CAPTION, "·", x + 2, FOOT_CY, TEXT_3, 0, 0);
            roundPart(x + 10, "D2", s.shown2, s.real2, s.rigged == Side.TWO);
        }
        text(FontSpec.CAPTION, "Display only · v" + s.version, W - PAD, FOOT_CY, TEXT_3, 0, 1);
    }

    private float roundPart(float x, String name, String shown, String real, boolean winner) {
        text(FontSpec.CAPTION, name, x, FOOT_CY, TEXT_3, 0, 0);
        x += t.width(FontSpec.CAPTION, name) + 4;
        text(FontSpec.LABEL, shown, x, FOOT_CY, winner ? ACCENT : TEXT_2, 0, 0);
        x += t.width(FontSpec.LABEL, shown) + 3;
        if (real != null && !real.equals(shown)) {
            String r = "(real " + real + ")";
            text(FontSpec.CAPTION, r, x, FOOT_CY, TEXT_3, 0, 0);
            x += t.width(FontSpec.CAPTION, r) + 3;
        }
        return x + 3;
    }
}
