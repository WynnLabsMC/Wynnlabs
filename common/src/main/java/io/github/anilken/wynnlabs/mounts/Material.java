package io.github.anilken.wynnlabs.mounts;

/** A feedable material: English item name (e.g. "Copper Ingot"), level and kind. */
public record Material(String name, int level, MaterialKind kind) {
    /** Points per stat of {@link MaterialKind#stats()}, before the max cap. */
    public int[] points() {
        return kind.points(level);
    }
}
