package io.github.anilken.wynnlabs.model;

import java.util.List;

/**
 * A mount as its item shows it (reins in the inventory, the bank or the enclosure): its type
 * ("WYVERN", "HORSE", "ADASAUR"), its name, potential and energy, and each stat's level, limit
 * (what feeding can reach now) and max (what training can unlock). {@code estimated}: Wynntils
 * guessed the maxes. {@code skin} ("Azure-Horn", its colours; many mounts share one) and
 * {@code state} ("Ready to Feed/Breed", "Feeding in 2h 14m") come from its lore, null when not
 * read. Read only; nothing here changes the game.
 */
public record MountRead(String type, String name, int potential, int energy, int energyMax, List<Stat> stats,
        boolean estimated, String primary, String secondary, String skin, String state) {

    /** Without the lore's skin and state (an item read without them). */
    public MountRead(String type, String name, int potential, int energy, int energyMax, List<Stat> stats,
                     boolean estimated, String primary, String secondary) {
        this(type, name, potential, energy, energyMax, stats, estimated, primary, secondary, null, null);
    }

    /** One stat: its id ("SPEED"), English name ("Speed"), level, limit and max. */
    public record Stat(String id, String name, int level, int limit, int max) {}

    /** The same read with its skin and state line from the lore. */
    public MountRead with(String skinLine, String stateLine) {
        return new MountRead(type, name, potential, energy, energyMax, stats, estimated, primary, secondary, skinLine, stateLine);
    }

    /** The same read with its potential from the lore ("438 Potential"; the maxes may not be known). */
    public MountRead withPotential(int p) {
        return new MountRead(type, name, p, energy, energyMax, stats, estimated, primary, secondary, skin, state);
    }

    /** The same read with these maxes (in its stats' order) known, no longer estimated. */
    public MountRead withMaxes(int[] max) {
        List<Stat> out = new java.util.ArrayList<>();
        for (int i = 0; i < stats.size(); i++) {
            Stat s = stats.get(i);
            out.add(new Stat(s.id(), s.name(), s.level(), s.limit(), i < max.length ? max[i] : s.max()));
        }
        return new MountRead(type, name, potential, energy, energyMax, List.copyOf(out), false, primary, secondary, skin, state);
    }

    /** Its maxes in its stats' order. */
    public int[] maxes() {
        return stats.stream().mapToInt(Stat::max).toArray();
    }

    /** The name to show: "Wyvern · Azure-Horn" when its skin is known, else the item's name. */
    public String shown() {
        return skin != null && !skin.isBlank() ? name + " · " + skin : name;
    }

    /**
     * Whether this is the free mount the game hands out (it "cannot be traded, bred, or upgraded"):
     * every stat at a limit and max of 1, so there is nothing to train.
     */
    public boolean free() {
        return !stats.isEmpty() && stats.stream().allMatch(s -> s.limit() <= 1 && s.max() <= 1);
    }

    /** The average of the stats' limits (breeding asks for a high one). */
    public double averageLimit() {
        return stats.isEmpty() ? 0 : stats.stream().mapToInt(Stat::limit).average().orElse(0);
    }

    /**
     * What stays the same while it is fed and trained: type, potential, colours and maxes. Not an
     * identity (two fresh mounts of one skin share it): the place a mount is seen in keeps it apart.
     */
    public String key() {
        StringBuilder b = new StringBuilder(type).append('|').append(potential).append('|')
                .append(primary).append('|').append(secondary);
        for (Stat s : stats) {
            b.append('|').append(s.id()).append('=').append(s.max());
        }
        return b.toString();
    }
}
