package io.github.anilken.wynnlabs.mounts;

/**
 * Every breeding number in one place, to be fitted to real data later. These are ESTIMATES (guide video and forum),
 * not verified game facts.
 *
 * @param eligibleAvgLimit  a parent can breed once its average limit reaches this
 * @param limitBonus        child limit = parents' average limit + this ...
 * @param limitSpread       ... +- this
 * @param maxBonus          child max = parents' average limit + this ...
 * @param maxSpread         ... +- this
 * @param levelSpread       child level = the higher parent's level +- this
 * @param breedSec          breeding time. Verified in game (NPC Sain): both parents go in the SAME Mount Feeder row,
 *                          so a breeding holds one of the rows for all of it and that row feeds nothing else
 * @param statCap           estimates are clamped to this (the planner's model limit)
 */
public record BreedConstants(int eligibleAvgLimit, int limitBonus, int limitSpread, int maxBonus, int maxSpread,
                             int levelSpread, long breedSec, int statCap) {
    public static final BreedConstants ESTIMATE = new BreedConstants(20, 10, 2, 30, 7, 2, 6 * 3600L, 255);
}
