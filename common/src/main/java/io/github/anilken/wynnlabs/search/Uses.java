package io.github.anilken.wynnlabs.search;

import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Types;
import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.MountMaterials;
import io.github.anilken.wynnlabs.mounts.MountStat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * What an item is used for: the crafting recipes a material goes into (one {@link Craft} per item
 * type), the professions an ingredient can be crafted with, and other uses such as feeding a
 * mount. Plain data, no game types; the Search tab draws it.
 */
public final class Uses {

    /** One level bracket of a craft: this material's amount and the other material with its amount. */
    public record Bracket(int minLevel, int maxLevel, int amount, String other, int otherAmount) {

        public String label() {
            return minLevel + "-" + maxLevel;
        }
    }

    /**
     * One item type the material is crafted into ("ring"), with its profession ("jeweling"), the
     * level range across its brackets, and the amounts of the lowest bracket: this material, the
     * other material and its amount. {@code mixed} is true when a bracket needs a different amount or
     * a different other material; {@link #brackets} then holds each one, lowest first.
     */
    public record Craft(String type, String profession, int minLevel, int maxLevel, int amount, String other,
                        int otherAmount, boolean mixed, List<Bracket> brackets) {

        public String levels() {
            return minLevel + "-" + maxLevel;
        }
    }

    /** A use outside crafting. */
    public sealed interface Other permits MountFeed {}

    /**
     * Fed to a mount: the material (its level and kind) and the stats it raises with the points each
     * gets before a mount's caps, the biggest first ("+15 Handling, +9 Training, +4 Altitude" for Koi
     * Oil). What it gives one particular mount depends on that mount's stats:
     * {@code MountStats.gainsFrom(material)}.
     */
    public record MountFeed(Material material, List<MountStat> stats, List<Integer> points) implements Other {}

    /** Everything found for one name; every list is empty when the item has no known use. */
    public record Result(String name, List<Craft> crafts, List<String> ingredientProfessions, List<Other> other) {

        public boolean empty() {
            return crafts.isEmpty() && ingredientProfessions.isEmpty() && other.isEmpty();
        }
    }

    private Uses() {}

    /** The uses of a search entry: its crafts and mount feed, and an ingredient's professions. */
    public static Result of(Entry e) {
        List<String> profs = e.kind() == Kind.INGREDIENT ? e.professions() : List.of();
        return of(e.name(), CraftData.all(), profs, MountMaterials.all());
    }

    /** The uses of an item name in the shipped recipes and mount materials (no ingredient professions). */
    public static Result of(String name) {
        return of(name, CraftData.all(), List.of(), MountMaterials.all());
    }

    /**
     * The uses of {@code name} ("Koi Oil", "Koi Oil T2", "Refined Koi Oil"): every recipe naming it
     * as a material grouped by item type and sorted by profession then type,
     * {@code ingredientProfessions} as given (lower case, repeats left out; pass an ingredient's
     * professions, else an empty list), and the mount feed when a mount material has that name.
     */
    public static Result of(String name, List<CraftData.Recipe> recipes, List<String> ingredientProfessions,
                            List<Material> mountMaterials) {
        String key = key(name);
        Map<String, List<CraftData.Recipe>> byType = new LinkedHashMap<>();
        Map<String, Integer> slot = new LinkedHashMap<>();
        for (CraftData.Recipe r : recipes) {
            int i = indexOf(r, key);
            if (i >= 0) {
                byType.computeIfAbsent(r.type(), t -> new ArrayList<>()).add(r);
                slot.putIfAbsent(r.id(), i);
            }
        }
        List<Craft> crafts = new ArrayList<>();
        for (List<CraftData.Recipe> rs : byType.values()) {
            rs.sort(Comparator.comparingInt(CraftData.Recipe::minLevel));
            List<Bracket> brackets = new ArrayList<>();
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            for (CraftData.Recipe r : rs) {
                int i = slot.get(r.id());
                CraftData.Material own = r.materials().get(i);
                CraftData.Material other = r.materials().size() > 1 ? r.materials().get(1 - Math.min(i, 1)) : null;
                if (other == own) {
                    other = null;
                }
                brackets.add(new Bracket(r.minLevel(), r.maxLevel(), own.amount(),
                        other == null ? null : other.shown(), other == null ? 0 : other.amount()));
                lo = Math.min(lo, r.minLevel());
                hi = Math.max(hi, r.maxLevel());
            }
            Bracket first = brackets.get(0);
            boolean mixed = false;
            for (Bracket b : brackets) {
                if (b.amount() != first.amount() || b.otherAmount() != first.otherAmount()
                        || !Objects.equals(b.other(), first.other())) {
                    mixed = true;
                    break;
                }
            }
            CraftData.Recipe r0 = rs.get(0);
            crafts.add(new Craft(r0.type(), r0.profession(), lo, hi, first.amount(), first.other(), first.otherAmount(),
                    mixed, List.copyOf(brackets)));
        }
        crafts.sort(Comparator.comparing(Craft::profession).thenComparingInt(c -> typeOrder(c.type())));

        List<String> profs = new ArrayList<>();
        if (ingredientProfessions != null) {
            for (String p : ingredientProfessions) {
                String lp = p == null ? "" : p.trim().toLowerCase(Locale.ROOT);
                if (!lp.isEmpty() && !profs.contains(lp)) {
                    profs.add(lp);
                }
            }
        }

        List<Other> other = new ArrayList<>();
        for (Material m : mountMaterials) {
            if (key(m.name()).equals(key)) {
                other.add(feed(m));
                break;
            }
        }
        return new Result(name, List.copyOf(crafts), List.copyOf(profs), List.copyOf(other));
    }

    /**
     * How many times the stock allows a craft: {@code ownThis} of this material, {@code ownOther}
     * of the other one. When the brackets need different amounts, the lowest count of any bracket.
     */
    public static long craftsWith(long ownThis, long ownOther, Craft c) {
        long best = Long.MAX_VALUE;
        for (Bracket b : c.brackets()) {
            best = Math.min(best, craftsWith(ownThis, ownOther, b));
        }
        return best == Long.MAX_VALUE ? 0 : best;
    }

    /** How many times the stock allows one bracket's recipe. */
    public static long craftsWith(long ownThis, long ownOther, Bracket b) {
        long n = b.amount() > 0 ? Math.max(0, ownThis) / b.amount() : Long.MAX_VALUE;
        if (b.other() != null && b.otherAmount() > 0) {
            n = Math.min(n, Math.max(0, ownOther) / b.otherAmount());
        }
        return n == Long.MAX_VALUE ? 0 : n;
    }

    /**
     * The name compared across sources: lower case, without "Refined " or a trailing tier
     * (" T1".." T3"), and "Planks" as "Plank" (recipes say "Dark Planks", mount data "Dark Plank").
     */
    static String key(String name) {
        if (name == null) {
            return "";
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.startsWith("refined ")) {
            n = n.substring(8).trim();
        }
        if (n.matches(".* t[1-3]")) {
            n = n.substring(0, n.length() - 3).trim();
        }
        if (n.endsWith(" planks")) {
            n = n.substring(0, n.length() - 1);
        }
        return n;
    }

    private static int indexOf(CraftData.Recipe r, String key) {
        if (key.isEmpty()) {
            return -1;
        }
        List<CraftData.Material> ms = r.materials();
        for (int i = 0; i < ms.size(); i++) {
            if (key(ms.get(i).shown()).equals(key) || key(ms.get(i).item()).equals(key)) {
                return i;
            }
        }
        return -1;
    }

    private static int typeOrder(String type) {
        int i = Types.ALL.indexOf(type);
        return i < 0 ? Types.ALL.size() : i;
    }

    private static MountFeed feed(Material m) {
        int[] p = m.points();
        List<MountStat> stats = m.kind().stats();
        List<Integer> order = new ArrayList<>();
        for (int j = 0; j < p.length; j++) {
            order.add(j);
        }
        order.sort((a, b) -> Integer.compare(p[b], p[a]));      // the biggest first (stable: kind order on ties)
        List<MountStat> outStats = new ArrayList<>();
        List<Integer> outPoints = new ArrayList<>();
        for (int j : order) {
            outStats.add(stats.get(j));
            outPoints.add(p[j]);
        }
        return new MountFeed(m, List.copyOf(outStats), List.copyOf(outPoints));
    }
}
