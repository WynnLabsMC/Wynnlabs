package io.github.anilken.wynnlabs.mounts;

/**
 * The eight trainable mount stats, in the planner's fixed order (the index is used in packed states).
 * {@link #ALTITUDE} is the slot horses show as Jump Height: the same materials feed it.
 */
public enum MountStat {
    SPEED("Speed"),
    ACCELERATION("Acceleration"),
    ALTITUDE("Altitude"),
    ENERGY("Energy"),
    HANDLING("Handling"),
    TOUGHNESS("Toughness"),
    BOOST("Boost"),
    TRAINING("Training");

    public static final int COUNT = 8;
    private static final MountStat[] ALL = values();

    private final String display;

    MountStat(String display) {
        this.display = display;
    }

    /** English game name (stat names stay English in both languages). */
    public String display() {
        return display;
    }

    public static MountStat of(int index) {
        return ALL[index];
    }

    /** Parses a game or enum name; "Jump Height" maps to ALTITUDE. Returns null when unknown. */
    public static MountStat parse(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim().replace(' ', '_').toUpperCase(java.util.Locale.ROOT);
        if (n.equals("JUMP_HEIGHT") || n.equals("JUMP")) {
            return ALTITUDE;
        }
        for (MountStat s : ALL) {
            if (s.name().equals(n)) {
                return s;
            }
        }
        return null;
    }
}
