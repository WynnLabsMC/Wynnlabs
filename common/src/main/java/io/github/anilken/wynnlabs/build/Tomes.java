package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.SkillKind;

import java.util.List;
import java.util.function.Supplier;

/**
 * Skill points from tomes for the road map's spreads. Tome points count like the gear's own
 * points (for every requirement, past the 100 you may assign), so a spread with a tome is the
 * assigned points plus the tome's, and a requirement needs that much less assigned.
 *
 * <p>Your real tomes count at every milestone. With none yet, a milestone from Lv 100 on that
 * lies ahead assumes one guild Tome of Allegiance (rules.model.futureGuildTome, 1 = on): +4 to
 * one skill (Brute's, Sadist's, Mastermind's, Arsonist's, Ghost's) or +1 to every skill
 * (Assimilator's), whichever scores best for the set. The milestone you are at and the build you
 * wear never assume one.
 *
 * <p>The scope is per thread ({@link #use}, {@link #with}), like the milestone's tree: the
 * search's workers take the milestone's along.
 */
public final class Tomes {

    private Tomes() {}

    /** One tome's points per skill (SkillKind order); {@code assumed}: a guild tome you do not have yet. */
    public record Tome(int[] points, boolean assumed) {

        /** The guild tome's name ("Mastermind's Tome of Allegiance"), null for your real tomes. */
        public String name() {
            if (!assumed) {
                return null;
            }
            int one = single();
            return switch (one) {
                case 0 -> "Brute's Tome of Allegiance";
                case 1 -> "Sadist's Tome of Allegiance";
                case 2 -> "Mastermind's Tome of Allegiance";
                case 3 -> "Arsonist's Tome of Allegiance";
                case 4 -> "Ghost's Tome of Allegiance";
                default -> "Assimilator's Tome of Allegiance";
            };
        }

        /** The one skill it gives to, -1 when it gives to more than one. */
        int single() {
            int at = -1;
            for (int i = 0; i < points.length; i++) {
                if (points[i] != 0) {
                    if (at >= 0) {
                        return -1;
                    }
                    at = i;
                }
            }
            return at;
        }

        /** "Mastermind's Tome of Allegiance (+4 Intelligence)", "Assimilator's … (+1 to every skill)". */
        public String text() {
            int one = single();
            String what = one < 0 ? L.t("wynnlabs.road.tome_all")
                    : L.t("wynnlabs.road.tome_skill", points[one], skillName(SkillKind.values()[one]));
            String n = name();
            return n == null ? what : n + " (" + what + ")";
        }
    }

    /** The tome points in force: your real ones, and whether a guild tome is assumed on top of none. */
    public record Scope(int[] real, boolean guild) {

        String key() {
            return java.util.Arrays.toString(real) + (guild ? "+guild" : "");
        }
    }

    /** Lazily made: a field set where it is declared stays null after a hot swap. */
    private static volatile ThreadLocal<Scope> slot;

    private static ThreadLocal<Scope> slot() {
        ThreadLocal<Scope> t = slot;
        if (t == null) {
            synchronized (Tomes.class) {
                if (slot == null) {
                    slot = new ThreadLocal<>();
                }
                t = slot;
            }
        }
        return t;
    }

    /** The scope on this thread, or null (no tomes: every spread outside a road map run). */
    public static Scope active() {
        ThreadLocal<Scope> t = slot;
        return t == null ? null : t.get();
    }

    /** Sets this thread's scope (null: none). */
    public static void use(Scope s) {
        if (s == null) {
            if (slot != null) {
                slot.remove();
            }
        } else {
            slot().set(s);
        }
    }

    /** Runs {@code body} with {@code s} in force on this thread, the one before restored after. */
    public static <T> T with(Scope s, Supplier<T> body) {
        ThreadLocal<Scope> t = slot();
        Scope before = t.get();
        use(s);
        try {
            return body.get();
        } finally {
            if (before == null) {
                t.remove();
            } else {
                t.set(before);
            }
        }
    }

    /** Part of the memo's and the beam cache's keys: another scope spreads the points otherwise. */
    public static String key() {
        Scope s = active();
        return s == null ? "" : s.key();
    }

    /** Your real tome points, per skill. */
    public static int[] real(GameState state) {
        int[] out = new int[SkillKind.values().length];
        for (SkillKind s : SkillKind.values()) {
            out[s.ordinal()] = Math.max(0, state.tomePoints(s));
        }
        return out;
    }

    /**
     * The scope for milestone {@code m} when you are Lv {@code level} with {@code real} tome
     * points: a guild tome assumed only ahead of you, from Lv 100 (the tomes' level), with none yet.
     */
    public static Scope forMilestone(int[] real, int m, int level) {
        boolean none = java.util.Arrays.stream(real).sum() == 0;
        boolean guild = none && m > level && m >= 100 && Rules.sub("model", "futureGuildTome", 1) > 0;
        return none && !guild ? null : new Scope(real, guild);
    }

    /** The tome the quick spreads use: the assumed guild tome as +1 to every skill, else your real tomes, else null. */
    static Tome base() {
        Scope s = active();
        if (s == null) {
            return null;
        }
        if (s.guild()) {
            return ALL;
        }
        return java.util.Arrays.stream(s.real()).sum() == 0 ? null : new Tome(s.real(), false);
    }

    /** Whether a guild tome is assumed in this scope. */
    public static boolean guild() {
        Scope s = active();
        return s != null && s.guild();
    }

    /** +1 to every skill (Assimilator's). */
    static final Tome ALL = new Tome(new int[] {1, 1, 1, 1, 1}, true);

    /** +4 to skill {@code i} alone. */
    static Tome four(int i) {
        int[] p = new int[SkillKind.values().length];
        p[i] = 4;
        return new Tome(p, true);
    }

    /** {@code alloc} plus the tome's points (a new array), or {@code alloc} itself without a tome. */
    static int[] plus(int[] alloc, Tome t) {
        if (t == null) {
            return alloc;
        }
        int[] out = alloc.clone();
        for (int i = 0; i < out.length; i++) {
            out[i] += t.points()[i];
        }
        return out;
    }

    /** What each requirement needs assigned once the tome's points count: {@code min} less them, never under 0. */
    static int[] less(int[] min, Tome t) {
        if (t == null) {
            return min;
        }
        int[] out = min.clone();
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.max(0, out[i] - t.points()[i]);
        }
        return out;
    }

    /** The guild tomes a set's spread is tried with besides {@link #ALL}: every +4 one. */
    static List<Tome> fours() {
        return List.of(four(0), four(1), four(2), four(3), four(4));
    }

    private static String skillName(SkillKind k) {
        return L.t(switch (k) {
            case STRENGTH -> "wynnlabs.caution.strength";
            case DEXTERITY -> "wynnlabs.caution.dexterity";
            case INTELLIGENCE -> "wynnlabs.caution.intelligence";
            case DEFENCE -> "wynnlabs.caution.defence";
            case AGILITY -> "wynnlabs.caution.agility";
        });
    }
}
