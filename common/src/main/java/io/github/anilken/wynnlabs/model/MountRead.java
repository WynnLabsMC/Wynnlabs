package io.github.anilken.wynnlabs.model;

import java.util.List;

/**
 * A mount as its item shows it (reins in the inventory, the bank or the enclosure): its type
 * ("WYVERN", "HORSE", "ADASAUR"), its name, potential and energy, and each stat's level, limit
 * (what feeding can reach now) and max (what training can unlock). {@code estimated}: Wynntils
 * guessed the maxes. Read only; nothing here changes the game.
 */
public record MountRead(String type, String name, int potential, int energy, int energyMax, List<Stat> stats,
        boolean estimated, String primary, String secondary) {

    /** One stat: its id ("SPEED"), English name ("Speed"), level, limit and max. */
    public record Stat(String id, String name, int level, int limit, int max) {}

    /** The average of the stats' limits (breeding asks for a high one). */
    public double averageLimit() {
        return stats.isEmpty() ? 0 : stats.stream().mapToInt(Stat::limit).average().orElse(0);
    }

    /** What stays the same while it is fed and trained: type, name, potential, colours and maxes. */
    public String key() {
        StringBuilder b = new StringBuilder(type).append('|').append(name).append('|').append(potential).append('|')
                .append(primary).append('|').append(secondary);
        for (Stat s : stats) {
            b.append('|').append(s.id()).append('=').append(s.max());
        }
        return b.toString();
    }
}
