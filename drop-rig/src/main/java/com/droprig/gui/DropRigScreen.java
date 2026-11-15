package com.droprig.gui;

import com.droprig.DropRigClient;
import com.droprig.config.DropRigConfig;
import com.droprig.core.KeyBindings;
import com.droprig.core.Outcome;
import com.droprig.core.OutcomeTable;
import com.droprig.core.Side;
import com.droprig.gui.render.Anim;
import com.droprig.gui.render.Gfx;
import com.droprig.rig.DisplayRigger;
import com.droprig.mixin.DrawContextAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The DROP RIG settings screen. Every control reads from and writes to the real config
 * ({@link DropRigClient}), which is what the rigging logic uses, so the GUI always shows the
 * actual state.
 */
public final class DropRigScreen extends Screen {
    private static final float OPEN_SECONDS = 0.28f;
    private static final float CLOSE_SECONDS = 0.18f;

    private final DropRigView view = new DropRigView();
    private final DropRigView.State state = new DropRigView.State();
    private final Gfx gfx = new Gfx();
    private final McText text = new McText();
    private final ShapeElement.Collector[] collectors = {new ShapeElement.Collector(), new ShapeElement.Collector()};
    private int frame;
    private boolean backgroundDrawn;
    private final String version;

    private long openNanos = -1;
    private long lastNanos;
    private boolean closing;
    private boolean closeQueued;
    private float closeProgress;
    private float vis;
    private float scale = 1, originX, originY;

    private OutcomeTable cachedTable;
    private final Set<String> seen1 = new HashSet<>(), seen2 = new HashSet<>();

    public DropRigScreen() {
        super(Text.literal("Drop Rig"));
        version = FabricLoader.getInstance().getModContainer(DropRigClient.MOD_ID)
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("1.0.0");
    }

    @Override
    protected void init() {
        text.calibrate(this.textRenderer);
        if (openNanos < 0) {
            openNanos = Util.getMeasuringTimeNano();
            lastNanos = openNanos;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ rendering

    /**
     * Frosted backdrop: vanilla menu blur plus a soft tint. Minecraft calls this before
     * {@link #render}; if a future version stops doing so, {@link #render} calls it itself, and the
     * flag guarantees the blur is only applied once per frame.
     */
    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        if (backgroundDrawn) return;
        backgroundDrawn = true;
        if (this.client == null || this.client.world == null) {
            super.renderBackground(ctx, mouseX, mouseY, deltaTicks);
            return;
        }
        applyBlur(ctx);
        int top = Math.round(0x48 * vis) << 24 | 0x050A14;
        int bottom = Math.round(0x70 * vis) << 24 | 0x02040A;
        ctx.fillGradient(0, 0, this.width, this.height, top, bottom);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float deltaTicks) {
        long now = Util.getMeasuringTimeNano();
        float dt = Math.min(0.1f, Math.max(0f, (now - lastNanos) / 1e9f));
        lastNanos = now;
        float intro = (now - openNanos) / 1e9f;

        vis = Anim.outCubic(intro / OPEN_SECONDS);
        if (closing) {
            closeProgress = Math.min(1f, closeProgress + dt / CLOSE_SECONDS);
            vis *= 1f - Anim.inCubic(closeProgress);
            if (closeProgress >= 1f) finishClose();
        }

        if (!backgroundDrawn) renderBackground(ctx, mouseX, mouseY, deltaTicks);
        backgroundDrawn = false; // ready for the next frame
        ctx.createNewRootLayer(); // panel sits above the blurred backdrop

        layout();
        fillState();

        float mx = (mouseX - originX) / scale, my = (mouseY - originY) / scale;
        Matrix3x2fStack matrices = ctx.getMatrices();
        matrices.pushMatrix();
        matrices.translate(originX, originY);
        matrices.scale(scale, scale);

        float px = (float) (1.0 / (this.client.getWindow().getScaleFactor() * (double) scale));
        ShapeElement.Collector shapes = collectors[frame++ & 1];
        shapes.reset();
        gfx.begin(shapes, px);
        text.begin();
        view.render(gfx, text, state, mx, my, dt, vis, closing ? 10f : intro);
        gfx.end();

        // One element for all geometry, then the text (which Minecraft layers above it).
        ((DrawContextAccessor) ctx).droprig$getState().addSimpleElement(
                new ShapeElement(shapes, new Matrix3x2f(matrices), new ScreenRect(0, 0, this.width, this.height)));
        text.flush(ctx);

        matrices.popMatrix();
    }

    /** Fits the 404x326 layout to the window; never tiny on large windows, never clipped on small ones. */
    private void layout() {
        float fit = Math.min((this.width - 24f) / DropRigView.W, (this.height - 24f) / DropRigView.H);
        float comfortable = Math.max(1f, 0.56f * this.height / DropRigView.H);
        float base = Math.max(0.25f, Math.min(fit, comfortable));
        scale = base * (0.94f + 0.06f * vis);
        originX = this.width / 2f - DropRigView.W * scale / 2f;
        originY = this.height / 2f - DropRigView.H * scale / 2f + (1f - vis) * 8f;
    }

    // ------------------------------------------------------------------ state

    private void fillState() {
        DropRigConfig cfg = DropRigClient.config();
        DisplayRigger rig = DropRigClient.rigger();
        state.enabled = cfg.enabled;
        state.rigged = cfg.rigged();
        state.version = version;

        OutcomeTable table = rig.table();
        if (table != cachedTable) {
            cachedTable = table;
            state.labels1 = labels(table, Side.ONE);
            state.labels2 = labels(table, Side.TWO);
        }
        seen1.clear();
        seen2.clear();
        for (String l : state.labels1) if (rig.hasSeen(Side.ONE, l)) seen1.add(l);
        for (String l : state.labels2) if (rig.hasSeen(Side.TWO, l)) seen2.add(l);
        state.seen1 = seen1;
        state.seen2 = seen2;
        state.detect1 = format(rig.detection(Side.ONE));
        state.detect2 = format(rig.detection(Side.TWO));

        DisplayRigger.LastRound last = rig.lastRound();
        if (last != null) {
            state.real1 = last.real1();
            state.real2 = last.real2();
            state.shown1 = last.shown1();
            state.shown2 = last.shown2();
            state.lastRoundAge = (System.currentTimeMillis() - last.timeMs()) / 1000f;
        } else {
            state.real1 = state.real2 = state.shown1 = state.shown2 = null;
            state.lastRoundAge = Float.MAX_VALUE;
        }

        KeyBindings keys = cfg.keyBindings();
        state.toggleKey = keyName(keys.get(KeyBindings.Action.TOGGLE_RIG));
        state.menuKey = keyName(keys.get(KeyBindings.Action.OPEN_MENU));
        state.toggleIsDefault = keys.isDefault(KeyBindings.Action.TOGGLE_RIG);
        state.menuIsDefault = keys.isDefault(KeyBindings.Action.OPEN_MENU);
    }

    private static List<String> labels(OutcomeTable table, Side side) {
        List<String> out = new ArrayList<>();
        for (Outcome o : table.declared(side)) out.add(o.label());
        return List.copyOf(out);
    }

    private static String format(DisplayRigger.Detection d) {
        if (d == null) return null;
        BlockPos p = d.pos();
        return p.getX() + ", " + p.getY() + ", " + p.getZ();
    }

    static String keyName(int code) {
        if (code == KeyBindings.UNBOUND) return "NONE";
        String n = InputUtil.Type.KEYSYM.createFromCode(code).getLocalizedText().getString().toUpperCase(Locale.ROOT);
        switch (n) {
            case "LEFT ARROW": return "LEFT";
            case "RIGHT ARROW": return "RIGHT";
            case "UP ARROW": return "UP";
            case "DOWN ARROW": return "DOWN";
            case "CAPS LOCK": return "CAPS";
            case "PAGE UP": return "PG UP";
            case "PAGE DOWN": return "PG DN";
            case "INSERT": return "INS";
            case "DELETE": return "DEL";
            default: break;
        }
        return n.replace("LEFT ", "L-").replace("RIGHT ", "R-").replace("CONTROL", "CTRL")
                .replace("KEYPAD ", "NUM ").replace("SHIFT", "SHIFT");
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) return true;
        double mouseX = click.x(), mouseY = click.y();
        if (click.buttonInfo().button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (view.recording() != null) {
                view.stopRecording();
                return true;
            }
            return super.mouseClicked(click, doubled);
        }
        float mx = (float) ((mouseX - originX) / scale), my = (float) ((mouseY - originY) / scale);
        DropRigConfig cfg = DropRigClient.config();
        DropRigView.Intent intent = view.click(mx, my);
        switch (intent) {
            case TOGGLE_ENABLED -> {
                view.stopRecording();
                DropRigClient.setEnabled(!cfg.enabled);
                clickSound(cfg.enabled ? 1.7f : 1.3f);
            }
            case SELECT_ONE -> select(Side.ONE);
            case SELECT_TWO -> select(Side.TWO);
            case RECORD_TOGGLE -> toggleRecording(KeyBindings.Action.TOGGLE_RIG);
            case RECORD_MENU -> toggleRecording(KeyBindings.Action.OPEN_MENU);
            case RESET_TOGGLE -> resetKey(KeyBindings.Action.TOGGLE_RIG);
            case RESET_MENU -> resetKey(KeyBindings.Action.OPEN_MENU);
            case CANCEL_RECORD -> view.stopRecording();
            case NONE -> {
                return super.mouseClicked(click, doubled);
            }
        }
        return true;
    }

    private void select(Side side) {
        view.stopRecording();
        if (DropRigClient.config().rigged() != side) {
            DropRigClient.setRigged(side);
            view.pulse(side);
        }
        clickSound(1.5f);
    }

    private void toggleRecording(KeyBindings.Action action) {
        if (view.recording() == action) view.stopRecording();
        else view.startRecording(action);
        clickSound(1.6f);
    }

    private void resetKey(KeyBindings.Action action) {
        view.stopRecording();
        KeyBindings keys = DropRigClient.config().keyBindings();
        if (keys.reset(action) == KeyBindings.Result.ASSIGNED) DropRigClient.applyKeyBindings(keys);
        view.spinReset(action);
        clickSound(1.4f);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        int keyCode = input.key();
        KeyBindings.Action rec = view.recording();
        if (rec != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                view.stopRecording();
                return true;
            }
            if (keyCode < 0) {
                view.flashError(rec, "That key can't be bound");
                return true;
            }
            int key = keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE ? KeyBindings.UNBOUND : keyCode;
            KeyBindings keys = DropRigClient.config().keyBindings();
            switch (keys.assign(rec, key)) {
                case ASSIGNED -> {
                    DropRigClient.applyKeyBindings(keys);
                    view.stopRecording();
                    view.flashSaved(rec);
                    clickSound(1.8f);
                }
                case UNCHANGED -> {
                    view.stopRecording();
                    view.flashSaved(rec);
                }
                case CONFLICT -> view.flashError(rec, "Already used by " + rec.other().label);
                case RESERVED -> view.flashError(rec, "That key can't be bound");
            }
            return true;
        }
        int menu = DropRigClient.config().menuKey;
        if (menu != KeyBindings.UNBOUND && keyCode == menu) {
            close();
            return true;
        }
        return super.keyPressed(input);
    }

    private void clickSound(float pitch) {
        if (this.client != null) {
            this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
    }

    // ------------------------------------------------------------------ closing

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            closeProgress = 0f;
            view.stopRecording();
        }
    }

    private void finishClose() {
        if (closeQueued || this.client == null) return;
        closeQueued = true;
        this.client.send(() -> {
            if (this.client.currentScreen == this) this.client.setScreen(null);
        });
    }
}
