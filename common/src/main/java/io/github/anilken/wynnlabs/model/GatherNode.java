package io.github.anilken.wynnlabs.model;

/**
 * A gathering node the client sees (from the game's label over it).
 *
 * @param resource the resource's name ("Silver")
 * @param x        the label's block x
 * @param y        the label's block y
 * @param z        the label's block z
 * @param left     what a left click gathers ("Ingot"), or null when the label does not say
 * @param right    what a right click gathers ("Gem"), or null when the label does not say
 */
public record GatherNode(String resource, int x, int y, int z, String left, String right) {}
