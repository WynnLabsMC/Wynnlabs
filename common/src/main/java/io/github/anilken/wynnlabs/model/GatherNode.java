package io.github.anilken.wynnlabs.model;

/**
 * A gathering node the client sees, from the game's label over it.
 *
 * @param resource the resource's name ("Silver")
 * @param entityId the label's entity
 * @param x        the label's block x
 * @param y        the label's block y
 * @param z        the label's block z
 */
public record GatherNode(String resource, int entityId, int x, int y, int z) {}
