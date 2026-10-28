package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.BuildStats;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the Home's stats list and spell panel show: the build you wear, and the build they compare
 * it with. By default: the road map's build for the level pill selected, else the one "Detailed"
 * put here, else the applied plan, else the mod's suggested build (with its own skill points);
 * none when the suggestion is what you wear. The header's menu picks another (a saved build,
 * none), kept per character ({@link StatsChoice}); a pick wins over the pills, and choosing a
 * pill hands the choice back to "Automatic". The toggle shows the other build's numbers with the
 * change from your gear instead (for the game session).
 */
final class StatsSource {

    /** The default order. */
    static final String AUTO = "auto";
    static final String NONE = "none";

    /**
     * What is shown this frame: the main numbers, the other build's (null: none), its name, and
     * whether the main ones are the other build's ({@code flipped}: the change is from your gear).
     */
    record Shown(BuildStats.Side now, BuildStats.Side other, String name, boolean flipped) {}

    /** The view toggle, for this game session. */
    private static boolean flip;

    /** A build the window's "Detailed" put here, its rank, and its numbers; null: none. */
    private Roadmap.Candidate pick;
    private int pickRank;
    private BuildStats.Side pickSide;
    /** What you wear and the suggested build, worked out for this build plan. */
    private Build.Plan wornFor;
    private BuildStats.Side wornSide;
    private BuildStats.Side suggestedSide;
    /** The level pill's road map step this frame (null: "now" or the plan pill), and each step's numbers. */
    private Roadmap.Step step;
    private final Map<Roadmap.Step, BuildStats.Side> stepSides = new IdentityHashMap<>();

    /** "Detailed" on a found build's card: that build compared, the default order again. */
    void pick(Roadmap.Candidate c, int level, int rank) {
        pick = c;
        pickRank = rank;
        pickSide = BuildStats.candidate(c.set(), c.eval(), level);
        StatsChoice.set(AUTO);
    }

    /** The ✕ by a found build's name: the applied plan again. */
    void clearPick() {
        pick = null;
        pickSide = null;
    }

    boolean picked() {
        return pick != null;
    }

    /** The Plan pill is selected this frame: "Automatic" compares with the applied plan. */
    private boolean planPill;

    /** The pill selected last frame ("now", "plan" or a level), to see a new one chosen; null before. */
    private String pillSeen;

    /** The level pill's step this frame; null for "now" and the plan pill. */
    void level(Roadmap.Step s) {
        step = s != null && s.set() != null && s.build() != null && !s.build().isEmpty() ? s : null;
    }

    /** Whether the Plan pill is selected this frame. */
    void planPill(boolean on) {
        planPill = on;
    }

    /**
     * The pill selected this frame ({@code key}: which one, e.g. its place in the row): another one
     * chosen hands the comparison back to "Automatic", so the new pill steers it.
     */
    void pill(String key) {
        if (pillSeen != null && !key.equals(pillSeen)) {
            StatsChoice.set(AUTO);
        }
        pillSeen = key;
    }

    void choose(String id) {
        StatsChoice.set(id);
    }

    /** The row ticked in the header's menu. */
    String choice() {
        return StatsChoice.ticked(StatsChoice.get(), available());
    }

    /** What is compared this frame (an option id, or {@link #NONE}). */
    String target() {
        return StatsChoice.target(StatsChoice.get(), available(), planPill);
    }

    /** The options there are to compare with this frame (Automatic and "none" aside). */
    private java.util.Set<String> available() {
        java.util.Set<String> out = new java.util.HashSet<>();
        if (step != null) {
            out.add("level");
        }
        if (pick != null) {
            out.add("pick");
        }
        if (Plans.current() != null) {
            out.add("plan");
        }
        out.add("suggested");
        for (Plans.Build b : Plans.saved()) {
            out.add("saved:" + b.id());
        }
        return out;
    }

    void flip() {
        flip = !flip;
    }

    boolean flipped() {
        return flip;
    }

    /** The header's list: {id, label} for every build there is to compare with. */
    List<String[]> options() {
        List<String[]> out = new ArrayList<>();
        // "Automatic" names what it compares with now
        String auto = StatsChoice.target(AUTO, available(), planPill);
        String autoName = NONE.equals(auto) ? null : (String) resolveName(auto);
        out.add(new String[] {AUTO, autoName == null ? L.t("wynnlabs.stats.src_auto")
                : L.t("wynnlabs.stats.src_auto_is", autoName)});
        if (step != null) {
            out.add(new String[] {"level", L.t("wynnlabs.stats.col_level", step.level())});
        }
        if (pick != null) {
            out.add(new String[] {"pick", L.t("wynnlabs.stats.col_build", pickRank)});
        }
        Plans.Build applied = Plans.current();
        if (applied != null) {
            out.add(new String[] {"plan", L.t("wynnlabs.stats.src_plan", planName(applied))});
        }
        out.add(new String[] {"suggested", L.t("wynnlabs.stats.col_suggested")});
        for (Plans.Build b : Plans.saved()) {
            out.add(new String[] {"saved:" + b.id(), L.t("wynnlabs.stats.src_saved", planName(b))});
        }
        out.add(new String[] {NONE, L.t("wynnlabs.stats.src_none")});
        return out;
    }

    private static String planName(Plans.Build b) {
        return b.name().isEmpty() ? L.t("wynnlabs.stats.col_plan") : b.name();
    }

    /** This frame's sides; {@code now} null before there is a build plan. */
    Shown shown() {
        Build.Plan plan = Build.plan();
        if (plan != wornFor) {
            wornFor = plan;
            wornSide = BuildStats.worn(plan);
            suggestedSide = BuildStats.suggested(plan);
            stepSides.clear();
        }
        if (wornSide == null) {
            return new Shown(null, null, null, false);
        }
        // the header's pick while it is there; otherwise the pills and the default order
        String c = target();
        Object[] o = resolve(c, plan);
        if (o[0] == null && !NONE.equals(c)) {
            o = resolve("suggested", plan);         // a plan that could not be worked out
        }
        BuildStats.Side other = (BuildStats.Side) o[0];
        String name = other == null ? null : (String) o[1];
        if (flip && other != null) {
            return new Shown(other, wornSide, name, true);
        }
        return new Shown(wornSide, other, name, false);
    }

    /** An option's name, without working out its numbers. */
    private String resolveName(String c) {
        return switch (c) {
            case "level" -> step == null ? null : L.t("wynnlabs.stats.col_level", step.level());
            case "pick" -> L.t("wynnlabs.stats.col_build", pickRank);
            case "plan" -> Plans.current() == null ? null : planName(Plans.current());
            case "suggested" -> L.t("wynnlabs.stats.col_suggested");
            default -> {
                Plans.Build b = c.startsWith("saved:") ? Plans.byId(c.substring(6)) : null;
                yield b == null ? null : planName(b);
            }
        };
    }

    /** A plan's numbers; a failure is told once in the log rather than leaving the list without a reason. */
    private static BuildStats.Side planned(Plans.Build b, Build.Plan plan) {
        try {
            return BuildStats.planned(b, plan);
        } catch (Throwable t) {
            if (!failTold) {
                failTold = true;
                System.out.println("[WynnLabs] plan stats failed: " + t);
            }
            return null;
        }
    }

    private static boolean failTold;

    /** One choice's side and name ({side, name}; side null when there is none). */
    private Object[] resolve(String c, Build.Plan plan) {
        switch (c) {
            case "level" -> {
                if (step == null) {
                    return new Object[] {null, null};
                }
                if (stepSides.size() > 24) {
                    stepSides.clear();
                }
                BuildStats.Side s = stepSides.computeIfAbsent(step,
                        st -> BuildStats.candidate(st.build(), st.set(), st.level()).at(st.level(), null));
                return new Object[] {s, L.t("wynnlabs.stats.col_level", step.level())};
            }
            case "pick" -> {
                return new Object[] {pickSide, L.t("wynnlabs.stats.col_build", pickRank)};
            }
            case "plan" -> {
                Plans.Build applied = Plans.current();
                return new Object[] {planned(applied, plan), applied == null ? null : planName(applied)};
            }
            case "suggested" -> {
                return new Object[] {suggestedSide, L.t("wynnlabs.stats.col_suggested")};
            }
            case NONE -> {
                return new Object[] {null, null};
            }
            default -> {
                Plans.Build b = c.startsWith("saved:") ? Plans.byId(c.substring(6)) : null;
                return new Object[] {b == null ? null : planned(b, plan), b == null ? null : planName(b)};
            }
        }
    }
}
