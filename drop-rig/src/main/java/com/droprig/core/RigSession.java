package com.droprig.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Pairs a Dropper 1 drop with a Dropper 2 drop into a round and asks {@link RigLogic} what to
 * display. Pure Java so the whole flow (pairing, holding, toggling) is unit-tested.
 *
 * <p>Freshly dropped items are held invisible for {@code holdTicks} so a partner dropped on the
 * same press can be paired before either number is seen. After that the item becomes visible
 * but can still be paired until {@code pairWindowTicks} have passed.
 */
public final class RigSession {

    public record Settings(int holdTicks, int pairWindowTicks, double maxPairDistance) {
        public Settings {
            holdTicks = Math.max(0, holdTicks);
            pairWindowTicks = Math.max(holdTicks, pairWindowTicks);
            maxPairDistance = maxPairDistance <= 0 ? 12 : maxPairDistance;
        }

        public static Settings defaults() {
            return new Settings(6, 60, 12);
        }
    }

    /** A detected drop. Coordinates are where the item first appeared. */
    public record Drop(int id, Side side, Outcome outcome, long tick, double x, double y, double z) {
    }

    /** A completed round. {@code decision} is null when the values couldn't be handled safely. */
    public record Round(Drop one, Drop two, Decision decision) {
    }

    /** Result of offering a drop. */
    public record Admission(boolean accepted, boolean hidden, Round round) {
        public static final Admission IGNORED = new Admission(false, false, null);
    }

    private static final class Pending {
        final Drop drop;

        Pending(Drop drop) {
            this.drop = drop;
        }
    }

    private final List<Pending> pendingOne = new ArrayList<>();
    private final List<Pending> pendingTwo = new ArrayList<>();
    private final Set<Integer> hidden = new HashSet<>();
    private final RandomGenerator rng;

    private OutcomeTable table;
    private Settings settings = Settings.defaults();
    private boolean enabled;
    private Side rigged = Side.ONE;

    public RigSession(OutcomeTable table, RandomGenerator rng) {
        this.table = table;
        this.rng = rng;
    }

    public void configure(OutcomeTable table, Settings settings) {
        this.table = table;
        this.settings = settings == null ? Settings.defaults() : settings;
    }

    public OutcomeTable table() {
        return table;
    }

    public Settings settings() {
        return settings;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Turning off releases every held item and forgets pending drops; returns ids to reveal. */
    public List<Integer> setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) return clear();
        return List.of();
    }

    public Side rigged() {
        return rigged;
    }

    public void setRigged(Side side) {
        if (side != null) this.rigged = side;
    }

    public boolean isHidden(int id) {
        return hidden.contains(id);
    }

    public boolean hasHidden() {
        return !hidden.isEmpty();
    }

    public int pendingCount() {
        return pendingOne.size() + pendingTwo.size();
    }

    public Admission offer(Drop drop) {
        if (!enabled || drop == null || drop.outcome() == null || table == null || !table.isUsable()) {
            return Admission.IGNORED;
        }
        List<Pending> others = drop.side() == Side.ONE ? pendingTwo : pendingOne;
        Pending partner = null;
        double maxSq = settings.maxPairDistance() * settings.maxPairDistance();
        for (Pending p : others) {
            if (drop.tick() - p.drop.tick() > settings.pairWindowTicks()) continue;
            if (distSq(p.drop, drop) > maxSq) continue;
            if (partner == null || p.drop.tick() < partner.drop.tick()) partner = p;
        }

        if (partner != null) {
            others.remove(partner);
            boolean partnerVisible = !hidden.contains(partner.drop.id());
            hidden.remove(partner.drop.id());
            Drop one = drop.side() == Side.ONE ? drop : partner.drop;
            Drop two = drop.side() == Side.TWO ? drop : partner.drop;
            boolean vis1 = one == drop ? false : partnerVisible; // the new drop hasn't been rendered yet
            boolean vis2 = two == drop ? false : partnerVisible;
            Decision decision = RigLogic.decide(table, rigged, one.outcome().value(), two.outcome().value(), vis1, vis2, rng);
            return new Admission(true, false, new Round(one, two, decision));
        }

        (drop.side() == Side.ONE ? pendingOne : pendingTwo).add(new Pending(drop));
        boolean hide = settings.holdTicks() > 0;
        if (hide) hidden.add(drop.id());
        return new Admission(true, hide, null);
    }

    /** Advances time. Returns the ids whose hold just ended (they should become visible). */
    public List<Integer> tick(long now) {
        List<Integer> reveal = new ArrayList<>();
        expire(pendingOne, now, reveal);
        expire(pendingTwo, now, reveal);
        return reveal;
    }

    private void expire(List<Pending> list, long now, List<Integer> reveal) {
        Iterator<Pending> it = list.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            long age = now - p.drop.tick();
            if (age >= settings.holdTicks() && hidden.remove(p.drop.id())) reveal.add(p.drop.id());
            if (age > settings.pairWindowTicks()) it.remove();
        }
    }

    /** Called when an entity disappears. */
    public void forget(int id) {
        hidden.remove(id);
        pendingOne.removeIf(p -> p.drop.id() == id);
        pendingTwo.removeIf(p -> p.drop.id() == id);
    }

    /** Forgets everything; returns the ids that were hidden. */
    public List<Integer> clear() {
        List<Integer> reveal = new ArrayList<>(hidden);
        hidden.clear();
        pendingOne.clear();
        pendingTwo.clear();
        return reveal;
    }

    private static double distSq(Drop a, Drop b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
