package io.github.anilken.wynnlabs.model;

/**
 * A profession bomb the client was told about (Wynntils reads the bomb messages): which one, on
 * which world, how long it still runs, and whether that world is the player's. Everyone hears of
 * their own world's bombs; only some ranks hear of the others', so a list of them is never complete.
 *
 * @param speed       a Profession Speed bomb (else a Profession XP bomb)
 * @param world       the world's name ("WC12")
 * @param remainingMs how long it still runs
 * @param here        on the player's world
 */
public record ProfBomb(boolean speed, String world, long remainingMs, boolean here) {}
