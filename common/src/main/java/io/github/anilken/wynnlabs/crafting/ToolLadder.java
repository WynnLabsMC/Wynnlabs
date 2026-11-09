package io.github.anilken.wynnlabs.crafting;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Prices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A gathering tool kind's upgrade ladder, tier by tier: the best tier the player has ("yours"),
 * the tiers below it (past), the best tier above it the profession level allows ("next"), the
 * others it allows (open) and those it does not yet (locked); and a way to get a tool written
 * short ("~1LE 35EB", "2 frags", "4E merchant"). Plain data in, plain data out.
 */
public final class ToolLadder {

    /** The four gathering tool kinds, by profession; {@code word} is the kind in a tool's name ("Cobalt Pickaxe T8"). */
    public enum Kind {
        PICKAXE("mining", "Pickaxe"), AXE("woodcutting", "Axe"), ROD("fishing", "Rod"), SCYTHE("farming", "Scythe");

        public final String profession;
        public final String word;

        Kind(String profession, String word) {
            this.profession = profession;
            this.word = word;
        }

        /** The kind of a gathering profession ("mining", "MINING"), or null for any other. */
        public static Kind of(String profession) {
            for (Kind k : values()) {
                if (k.profession.equalsIgnoreCase(profession)) {
                    return k;
                }
            }
            return null;
        }
    }

    /** Where the player keeps a tool, the nearest first. */
    public enum Where { HAND, BAG, BANK }

    public enum State { PAST, YOURS, OPEN, NEXT, LOCKED }

    public record Tool(String name, int tier, int level) {}

    /** One tier on the ladder: the tool, its state and where the player has it (null: not had). */
    public record Rung(Tool tool, State state, Where where) {}

    private ToolLadder() {}

    /**
     * The ladder of one kind, lowest tier first. {@code level} is the profession level (0 when not
     * known: nothing above yours is then called next or locked); {@code owned} is where the player
     * has each tier.
     */
    public static List<Rung> ladder(List<Tool> tools, int level, Map<Integer, Where> owned) {
        List<Tool> sorted = new ArrayList<>(tools);
        sorted.sort(Comparator.comparingInt(Tool::tier));
        int best = 0;
        for (Tool t : sorted) {
            if (owned.containsKey(t.tier())) {
                best = Math.max(best, t.tier());
            }
        }
        int next = -1;
        if (level > 0) {
            for (Tool t : sorted) {
                if (t.tier() > best && t.level() <= level) {
                    next = Math.max(next, t.tier());
                }
            }
        }
        List<Rung> out = new ArrayList<>();
        for (Tool t : sorted) {
            State s;
            if (t.tier() < best) {
                s = State.PAST;
            } else if (t.tier() == best) {
                s = State.YOURS;
            } else if (level <= 0) {
                s = State.OPEN;
            } else if (t.level() > level) {
                s = State.LOCKED;
            } else {
                s = t.tier() == next ? State.NEXT : State.OPEN;
            }
            out.add(new Rung(t, s, owned.get(t.tier())));
        }
        return out;
    }

    /** The tier the player has at its best, or null. */
    public static Rung yours(List<Rung> ladder) {
        for (Rung r : ladder) {
            if (r.state() == State.YOURS) {
                return r;
            }
        }
        return null;
    }

    /**
     * The tool to aim for: the next one (the best the level allows above yours), else the first
     * one above yours; null when yours is the last.
     */
    public static Rung next(List<Rung> ladder) {
        Rung first = null;
        for (Rung r : ladder) {
            if (r.state() == State.NEXT) {
                return r;
            }
            if (first == null && (r.state() == State.OPEN || r.state() == State.LOCKED)) {
                first = r;
            }
        }
        return first;
    }

    /** The nearer of two places (null counts as nowhere). */
    public static Where nearer(Where a, Where b) {
        if (a == null) {
            return b;
        }
        return b == null || a.ordinal() <= b.ordinal() ? a : b;
    }

    /** Whether an item's name is the tool of this kind and tier ("Cobalt Pickaxe T8"). */
    public static boolean isTool(String name, Kind kind, int tier) {
        return name != null && ToolWays.tier(name) == tier && (" " + name + " ").contains(" " + kind.word + " ");
    }

    /** The dungeon a fragment drops from: its name without " Fragment" ("Corrupted Infested Pit"). */
    public static String dungeonOf(String fragment) {
        return fragment.endsWith(" Fragment") ? fragment.substring(0, fragment.length() - " Fragment".length()) : fragment;
    }

    /** The way to show as the cheapest: the first with a price (ways come cheapest first), else the first. */
    public static ToolWays.Way cheapest(List<ToolWays.Way> ways) {
        for (ToolWays.Way w : ways) {
            if (w.total() != null) {
                return w;
            }
        }
        return ways.isEmpty() ? null : ways.get(0);
    }

    /** A way written short: "~1LE 35EB" (market), "4E merchant", "2 frags", "8EB + 6 frags"; "" for none. */
    public static String summary(ToolWays.Way w) {
        if (w == null) {
            return "";
        }
        String frags = w.fragments() > 0 ? L.n("wynnlabs.tools.frags", w.fragments()) : null;
        if (w.total() == null) {
            return frags != null ? frags : L.t("wynnlabs.box.ways_no_price");
        }
        if (w.kind() == ToolWays.Kind.MERCHANT) {
            return L.t("wynnlabs.tools.short_merchant", Prices.fmt(w.total()));
        }
        // market prices are a guess ("~"); a Dungeon Merchant's emeralds are fixed
        String money = (w.kind() == ToolWays.Kind.DUNGEON ? "" : "~") + Prices.fmt(w.total());
        if (frags == null) {
            return money;
        }
        return w.total() > 0 ? money + " + " + frags : frags;
    }
}
