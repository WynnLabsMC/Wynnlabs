package io.github.anilken.wynnlabs.mounts;

/** Mount kinds. A freshly bought mount of any kind starts every stat at level 1, limit 10, max 30. */
public enum MountType {
    HORSE("Horse"),
    WYVERN("Wyvern"),
    ADASAUR("Adasaur");

    private final String display;

    MountType(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }

    public static MountType parse(String name) {
        if (name == null) {
            return null;
        }
        for (MountType t : values()) {
            if (t.name().equalsIgnoreCase(name.trim())) {
                return t;
            }
        }
        return null;
    }
}
