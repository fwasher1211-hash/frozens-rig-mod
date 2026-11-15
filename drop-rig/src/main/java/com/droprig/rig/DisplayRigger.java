package com.droprig.rig;

import com.droprig.config.DropRigConfig;
import com.droprig.core.Decision;
import com.droprig.core.NumberText;
import com.droprig.core.Outcome;
import com.droprig.core.OutcomeTable;
import com.droprig.core.RigSession;
import com.droprig.core.Side;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Bridges Minecraft item entities and the pure {@link RigSession}.
 *
 * <p>Flow: an item entity appears → its name is read once its data arrives → if the number is a
 * Dropper 1 or Dropper 2 outcome (and it popped out next to a dropper), it's held invisible for a
 * few ticks → when the other dropper's item shows up the two are paired → {@code RigLogic} picks
 * what to display → the <em>client-side copy</em> of the item is relabelled. The server's item is
 * never touched; that is not something a client can do.
 *
 * <p>When rigging is off this class does nothing at all: no tracking, no hiding, no changes, and
 * any items it had relabelled are restored.
 */
public final class DisplayRigger {
    /** How long an item may take to receive its data before it is ignored. */
    private static final int CLASSIFY_TIMEOUT = 5;
    /** Ignore items that load right after joining a world or teleporting (they are old drops). */
    private static final int SETTLE_TICKS = 40;

    private record Fresh(long tick, double x, double y, double z) {
    }

    private static final class Applied {
        final Side side;
        final Outcome shown;
        ItemStack originalStack;
        Text originalName;
        ItemStack appliedStack;
        Text appliedName;

        Applied(Side side, Outcome shown) {
            this.side = side;
            this.shown = shown;
        }
    }

    /** Last round, for the GUI. */
    public record LastRound(String real1, String real2, String shown1, String shown2, boolean changed, long timeMs) {
    }

    /** Where each dropper was last detected, for the GUI. */
    public record Detection(BlockPos pos, long timeMs) {
    }

    private final RigSession session = new RigSession(null, new Random());
    private final Map<Integer, Fresh> fresh = new HashMap<>();
    private final Map<Integer, Applied> applied = new HashMap<>();
    private final OutcomeCatalog catalog = new OutcomeCatalog();
    private final Map<Side, Detection> detections = new EnumMap<>(Side.class);

    private OutcomeTable table = OutcomeTable.parse(DropRigConfig.DEFAULT_DROPPER_1, DropRigConfig.DEFAULT_DROPPER_2);
    private DropRigConfig config = new DropRigConfig();
    private LastRound lastRound;
    private long tick;
    private long ignoreUntil;
    private ClientWorld lastWorld;
    private double lastX, lastY, lastZ;
    private boolean hasLastPos;

    // ---------------------------------------------------------------- configuration

    public void applyConfig(DropRigConfig cfg) {
        this.config = cfg;
        this.table = OutcomeTable.parse(cfg.dropper1Outcomes, cfg.dropper2Outcomes);
        session.configure(table, cfg.sessionSettings());
        session.setRigged(cfg.rigged());
        if (session.isEnabled() != cfg.enabled) {
            if (cfg.enabled) {
                session.setEnabled(true);
            } else {
                session.setEnabled(false);
                restoreAll();
                fresh.clear();
            }
        }
    }

    public OutcomeTable table() {
        return table;
    }

    public LastRound lastRound() {
        return lastRound;
    }

    public Detection detection(Side side) {
        return detections.get(side);
    }

    public boolean hasSeen(Side side, String label) {
        return catalog.has(side, label);
    }

    // ---------------------------------------------------------------- events

    public void onJoin() {
        reset(false);
        ignoreUntil = tick + SETTLE_TICKS;
    }

    /** Forget everything. {@code restore} puts relabelled items back first. */
    public void reset(boolean restore) {
        if (restore) restoreAll();
        else applied.clear();
        fresh.clear();
        session.clear();
        catalog.clear();
        detections.clear();
        lastRound = null;
        hasLastPos = false;
    }

    public void onLoad(ItemEntity item) {
        if (!config.enabled || tick < ignoreUntil) return;
        fresh.put(item.getId(), new Fresh(tick, item.getX(), item.getY(), item.getZ()));
    }

    public void onUnload(ItemEntity item) {
        int id = item.getId();
        fresh.remove(id);
        applied.remove(id);
        session.forget(id);
    }

    /**
     * Render hook (see {@code EntityRendererMixin}). Must be cheap: called for every item entity
     * every frame. Returns true while a drop is being held back.
     */
    public boolean shouldHide(ItemEntity item) {
        if (fresh.isEmpty() && !session.hasHidden()) return false;
        int id = item.getId();
        Fresh f = fresh.get(id);
        if (f != null && tryClassify(item, f)) fresh.remove(id);
        return session.isHidden(id);
    }

    public void tick(MinecraftClient client) {
        tick++;
        ClientWorld world = client.world;
        if (world != lastWorld) {
            if (lastWorld != null) reset(false);
            lastWorld = world;
            ignoreUntil = tick + SETTLE_TICKS;
        }
        if (world == null || client.player == null) return;

        // A big jump in position = teleport/respawn: items loading now are old, not new drops.
        double px = client.player.getX(), py = client.player.getY(), pz = client.player.getZ();
        if (hasLastPos) {
            double dx = px - lastX, dy = py - lastY, dz = pz - lastZ;
            if (dx * dx + dy * dy + dz * dz > 100) ignoreUntil = Math.max(ignoreUntil, tick + SETTLE_TICKS / 2);
        }
        lastX = px;
        lastY = py;
        lastZ = pz;
        hasLastPos = true;

        if (!config.enabled) return;

        if (!fresh.isEmpty()) {
            Iterator<Map.Entry<Integer, Fresh>> it = fresh.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, Fresh> e = it.next();
                Entity ent = world.getEntityById(e.getKey());
                if (!(ent instanceof ItemEntity item) || item.isRemoved()) {
                    it.remove();
                    continue;
                }
                if (tryClassify(item, e.getValue()) || tick - e.getValue().tick() > CLASSIFY_TIMEOUT) it.remove();
            }
        }

        session.tick(tick); // held items whose time ran out simply become visible
        maintainApplied(world);
    }

    // ---------------------------------------------------------------- classification

    /** @return true when the item has been handled (accepted or rejected) and needs no more checks. */
    private boolean tryClassify(ItemEntity item, Fresh f) {
        ItemStack stack = item.getStack();
        if (stack.isEmpty()) return false; // data not received yet
        if (!config.enabled) return true;

        Text entityName = item.getCustomName();
        Text stackName = stack.get(DataComponentTypes.CUSTOM_NAME);
        Double value = entityName != null ? NumberText.lastValue(entityName.getString()) : null;
        if (value == null && stackName != null) value = NumberText.lastValue(stackName.getString());
        if (value == null) return true;

        Side side = table.classify(value);
        if (side == null) return true;
        Outcome outcome = table.find(side, value);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            double r = config.detectionRadius;
            if (client.player.squaredDistanceTo(f.x(), f.y(), f.z()) > r * r) return true;
        }
        BlockPos dropper = findDropper(item.getEntityWorld(), f.x(), f.y(), f.z());
        if (config.requireDropperNearby && dropper == null) return true;

        catalog.record(side, outcome.label(), stack, entityName);
        if (dropper != null) detections.put(side, new Detection(dropper, System.currentTimeMillis()));

        RigSession.Admission admission = session.offer(new RigSession.Drop(item.getId(), side, outcome, f.tick(), f.x(), f.y(), f.z()));
        if (admission.round() != null) resolve(MinecraftClient.getInstance().world, admission.round());
        return true;
    }

    private static BlockPos findDropper(World world, double x, double y, double z) {
        if (world == null) return null;
        BlockPos base = BlockPos.ofFloored(x, y, z);
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    m.set(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    BlockState state = world.getBlockState(m);
                    if (!state.isOf(Blocks.DROPPER) && !state.isOf(Blocks.DISPENSER)) continue;
                    double cx = m.getX() + 0.5 - x, cy = m.getY() + 0.5 - y, cz = m.getZ() + 0.5 - z;
                    double sq = cx * cx + cy * cy + cz * cz;
                    if (sq < bestSq) {
                        bestSq = sq;
                        best = m.toImmutable();
                    }
                }
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- applying

    private void resolve(ClientWorld world, RigSession.Round round) {
        Decision d = round.decision();
        if (d == null) {
            lastRound = new LastRound(round.one().outcome().label(), round.two().outcome().label(),
                    round.one().outcome().label(), round.two().outcome().label(), false, System.currentTimeMillis());
            return;
        }
        if (d.changed(Side.ONE)) apply(world, round.one().id(), Side.ONE, d.shown1());
        if (d.changed(Side.TWO)) apply(world, round.two().id(), Side.TWO, d.shown2());
        lastRound = new LastRound(d.real1().label(), d.real2().label(), d.shown1().label(), d.shown2().label(),
                d.anyChanged(), System.currentTimeMillis());
    }

    private void apply(ClientWorld world, int id, Side side, Outcome target) {
        if (world == null) return;
        Entity ent = world.getEntityById(id);
        if (!(ent instanceof ItemEntity item)) return;
        Applied a = new Applied(side, target);
        relabel(item, a);
        applied.put(id, a);
    }

    /** Builds and sets the display stack/name for {@code a.shown} from the item's current real data. */
    private void relabel(ItemEntity item, Applied a) {
        ItemStack real = item.getStack();
        Text realName = item.getCustomName();
        a.originalStack = real;
        a.originalName = realName;

        ItemStack shownStack;
        Text shownName = realName;
        OutcomeCatalog.Snapshot snap = catalog.get(a.side, a.shown.label());
        if (snap != null) {
            shownStack = snap.stack().copy();
            shownStack.setCount(real.getCount());
            if (realName != null) {
                Text fromSnap = snap.entityName();
                shownName = fromSnap != null ? fromSnap : orKeep(TextRelabel.relabel(realName, a.shown.label()), realName);
            }
        } else {
            shownStack = real.copy();
            Text custom = shownStack.get(DataComponentTypes.CUSTOM_NAME);
            if (custom != null) {
                Text r = TextRelabel.relabel(custom, a.shown.label());
                if (r != null) shownStack.set(DataComponentTypes.CUSTOM_NAME, r);
            }
            if (realName != null) shownName = orKeep(TextRelabel.relabel(realName, a.shown.label()), realName);
        }

        item.setStack(shownStack);
        if (realName != null && shownName != realName) item.setCustomName(shownName);
        a.appliedStack = item.getStack();
        a.appliedName = item.getCustomName();
    }

    private static Text orKeep(Text t, Text fallback) {
        return t != null ? t : fallback;
    }

    /** If the server re-sends an item's data, our display copy is replaced; re-apply it. */
    private void maintainApplied(ClientWorld world) {
        if (applied.isEmpty()) return;
        Iterator<Map.Entry<Integer, Applied>> it = applied.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Applied> e = it.next();
            Entity ent = world.getEntityById(e.getKey());
            if (!(ent instanceof ItemEntity item) || item.isRemoved()) {
                it.remove();
                continue;
            }
            Applied a = e.getValue();
            boolean stackResynced = item.getStack() != a.appliedStack;
            boolean nameResynced = item.getCustomName() != a.appliedName;
            if (stackResynced || nameResynced) {
                if (!stackResynced) item.setStack(a.originalStack);
                if (!nameResynced && a.originalName != null) item.setCustomName(a.originalName);
                relabel(item, a);
            }
        }
    }

    private void restoreAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client == null ? null : client.world;
        if (world != null) {
            for (Map.Entry<Integer, Applied> e : applied.entrySet()) {
                Entity ent = world.getEntityById(e.getKey());
                if (!(ent instanceof ItemEntity item) || item.isRemoved()) continue;
                Applied a = e.getValue();
                if (item.getStack() == a.appliedStack) item.setStack(a.originalStack);
                if (a.originalName != null && item.getCustomName() == a.appliedName) item.setCustomName(a.originalName);
            }
        }
        applied.clear();
    }
}
