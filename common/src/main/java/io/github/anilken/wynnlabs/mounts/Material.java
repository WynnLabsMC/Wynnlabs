package io.github.anilken.wynnlabs.mounts;

/**
 * A feedable material: English item name (e.g. "Copper Ingot"), level and kind. Kibble has no kind:
 * {@code kibble} says which, and {@code level} is its tier.
 */
public record Material(String name, int level, MaterialKind kind, Kibble kibble) {
    public Material(String name, int level, MaterialKind kind) {
        this(name, level, kind, null);
    }

    /** Points per stat of {@link MaterialKind#stats()}, before the max cap; none for kibble. */
    public int[] points() {
        return kind == null ? new int[0] : kind.points(level);
    }

    /** A queued food by name: a material of the table, else kibble; null when it is neither. */
    public static Material queued(String name) {
        Material m = MountMaterials.byName(name);
        return m != null ? m : Kibble.material(name);
    }
}
