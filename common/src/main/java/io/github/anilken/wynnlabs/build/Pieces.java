package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Roll;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.StatRange;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import io.github.anilken.wynnlabs.build.Damage.Piece;

import static io.github.anilken.wynnlabs.build.Damage.ELEMENTS;
import static io.github.anilken.wynnlabs.build.Damage.FEEL;
import static io.github.anilken.wynnlabs.build.Damage.HITS;
import static io.github.anilken.wynnlabs.build.Damage.SET;
import static io.github.anilken.wynnlabs.build.Damage.SETS;
import static io.github.anilken.wynnlabs.build.Damage.TIER;
import static io.github.anilken.wynnlabs.build.Damage.defKey;

/**
 * The model's pieces ({@link Damage.Piece}) made from what the mod reads: a piece from the item
 * data with every roll at the middle of its range, one you carry with its own rolls, both with
 * their empty powder slots filled the way players fill them, and their base defences.
 */
final class Pieces {

    private Pieces() {}

    /** Powders (WynnBuilder powders.js): per element e/t/w/f/a and tier I–VII: {min, max, convert %}. */
    private static final double[][][] POWDER = {
        {{4, 5, 17}, {6, 7, 21}, {7, 9, 25}, {8, 9, 31}, {9, 11, 38}, {11, 12, 46}, {12, 14, 52}},
        {{1, 8, 9}, {1, 12, 11}, {2, 14, 13}, {2, 15, 17}, {3, 17, 22}, {4, 19, 28}, {5, 21, 32}},
        {{3, 4, 13}, {5, 6, 15}, {6, 8, 17}, {7, 8, 21}, {8, 10, 26}, {10, 13, 32}, {11, 15, 38}},
        {{2, 5, 14}, {4, 7, 16}, {5, 9, 19}, {6, 9, 24}, {7, 11, 30}, {9, 14, 37}, {10, 16, 44}},
        {{2, 6, 11}, {3, 9, 14}, {4, 11, 17}, {5, 11, 22}, {7, 12, 28}, {8, 15, 35}, {9, 17, 42}}};
    private static final double[] POWDER_HP = {5, 10, 20, 30, 45, 60, 75};
    private static final int[] POWDER_LEVEL = {1, 5, 15, 25, 40, 55, 70};

    private static int elementIndex(String el) {
        return switch (el) {
            case "earth" -> 0;
            case "thunder" -> 1;
            case "water" -> 2;
            case "fire" -> 3;
            default -> 4;
        };
    }

    /**
     * Powder tiers are not shown on the item: assume the best tier the item's level allows,
     * capped by rules.model.powderTier (default VI, what most players use).
     */
    static int powderTier(int itemLevel) {
        int t = 1;
        for (int i = 0; i < POWDER_LEVEL.length; i++) {
            if (itemLevel >= POWDER_LEVEL[i]) {
                t = i + 1;
            }
        }
        return Math.max(1, Math.min(t, (int) Rules.sub("model", "powderTier", 6)));
    }

    static double tierOf(String speed) {
        return switch (speed) {
            case "SUPER_SLOW" -> 0;
            case "VERY_SLOW" -> 1;
            case "SLOW" -> 2;
            case "NORMAL" -> 3;
            case "FAST" -> 4;
            case "VERY_FAST" -> 5;
            default -> 6;
        };
    }

    /**
     * Very slow weapons hit hard on paper but feel sluggish and leave gaps while levelling;
     * rules.model.attackSpeedFeel holds the factor per speed (SUPER_SLOW, VERY_SLOW, SLOW...).
     */
    static double feel(String speed) {
        double fallback = switch (speed) {
            case "SUPER_SLOW" -> 0.7;
            case "VERY_SLOW" -> 0.8;
            case "SLOW" -> 0.95;
            default -> 1.0;
        };
        var m = Rules.get().getAsJsonObject("model");
        var f = m == null ? null : m.getAsJsonObject("attackSpeedFeel");
        return f != null && f.has(speed) ? f.get(speed).getAsDouble() : fallback;
    }

    static Piece piece(ItemData info) {
        return piece(info, null);
    }

    /**
     * A piece from the item data with every roll at the middle of its range. {@code powders}: the
     * ones in its slots as a WynnBuilder link writes them ("T6T6E7", each its own tier); null
     * fills every slot the way players fill them ({@link #assumedPowders}).
     */
    static Piece piece(ItemData info, String powders) {
        Map<String, Double> stats = new HashMap<>();
        for (StatRange v : info.stats()) {
            stats.merge(v.key(), (v.min() + v.max()) / 2.0, Double::sum);
        }
        Map<SkillKind, Integer> reqs = new java.util.EnumMap<>(SkillKind.class);
        info.requirements().skills().forEach(r -> reqs.put(r.skill(), r.points()));
        Map<String, Double> shares = new HashMap<>();
        info.base().damages().forEach(d -> {
            String el = d.type().toLowerCase(Locale.ROOT);
            if (List.of(ELEMENTS).contains(el)) {
                shares.merge(el, (d.low() + d.high()) / 2.0, Double::sum);
            }
        });
        addDefences(stats, info.base());
        info.base().attackSpeed().ifPresent(a -> {
            stats.put(FEEL, feel(a));
            stats.put(TIER, tierOf(a));
        });
        info.set().ifPresent(si -> {
            SETS.put(si.name(), si);
            stats.put(SET + si.name(), 1.0);
        });
        double dps = info.base().averageDps();
        Map<String, Double> sh = normalise(shares);
        double health = info.base().health();
        // a piece you would buy gets its slots filled, as the one you wear has them: weapons with
        // their own strongest element (players powder a weapon in its element), armour for health
        List<Powder> ps = powders != null ? linkPowders(powders)
                : tiered(assumedPowders(info, sh), powderTier(info.requirements().level()));
        if (!ps.isEmpty()) {
            if (info.slot() != null && info.slot().isWeapon()) {
                Powdered out = powdered(ps, dps, sh, stats);
                dps = out.dps();
                sh = out.shares();
            } else {
                health += powderHealth(ps);
            }
        }
        String key = "info:" + info.name() + (powders == null ? "" : "|" + powders);
        return new Piece(null, key, info.name(), info.slot(), info.requirements().level(), stats,
                reqs, dps, sh, health);
    }

    /** One powder: its element (earth … air) and tier (1-7). */
    record Powder(String element, int tier) {}

    private static final String POWDER_LETTERS = "ETWFA";
    private static final String[] POWDER_ELEMENTS = {"earth", "thunder", "water", "fire", "air"};

    /** A link's powders ("T6T6E7"): each letter's element at the tier after it; what it cannot read is left out. */
    static List<Powder> linkPowders(String s) {
        List<Powder> out = new java.util.ArrayList<>();
        for (int i = 0; i + 1 < s.length(); i += 2) {
            int el = POWDER_LETTERS.indexOf(Character.toUpperCase(s.charAt(i)));
            int tier = Character.digit(s.charAt(i + 1), 10);
            if (el >= 0 && tier >= 1 && tier <= POWDER_LEVEL.length) {
                out.add(new Powder(POWDER_ELEMENTS[el], tier));
            }
        }
        return out;
    }

    private static List<Powder> tiered(List<String> elements, int tier) {
        List<Powder> out = new java.util.ArrayList<>();
        for (String e : elements) {
            out.add(new Powder(e.toLowerCase(Locale.ROOT), tier));
        }
        return out;
    }

    /** Armour powders: health by tier, the same for every element. */
    private static double powderHealth(List<Powder> ps) {
        double h = 0;
        for (Powder p : ps) {
            h += POWDER_HP[p.tier() - 1];
        }
        return h;
    }

    /**
     * The powders a piece from the item data is judged with: every slot, the weapon's strongest
     * element (none for a neutral-only weapon), any element for armour (only health counts).
     * rules.model.assumePowders 0 turns it off.
     */
    static List<String> assumedPowders(ItemData info, Map<String, Double> shares) {
        return assumedPowders(info, shares, info.powderSlots(), null);
    }

    /**
     * {@code n} assumed powders for a piece of {@code info}: a weapon's in {@code keep} (the
     * element its own powders have) or else its strongest element; armour's in any element. Empty
     * when powders are turned off or a weapon has no element to take.
     */
    private static List<String> assumedPowders(ItemData info, Map<String, Double> shares, int n, String keep) {
        if (n <= 0 || Rules.sub("model", "assumePowders", 1) <= 0) {
            return List.of();
        }
        String el = "earth";
        if (info.slot() != null && info.slot().isWeapon()) {
            el = keep == null ? null : keep.toLowerCase(Locale.ROOT);
            double best = 0;
            for (var e : shares.entrySet()) {
                if (keep == null && !e.getKey().equals("neutral") && e.getValue() > best) {
                    best = e.getValue();
                    el = e.getKey();
                }
            }
            if (el == null) {
                return List.of();
            }
        }
        return java.util.Collections.nCopies(n, el);
    }

    /**
     * A piece you have with its empty powder slots filled the way {@link #piece(ItemData)} fills
     * a piece you would buy (a weapon in the element its powders already have, else its own
     * strongest; armour for health), so the road map weighs what you have and what you would buy
     * alike. Its own powders stay. A piece from the item data, a crafted one or one with every
     * slot taken comes back as it is. The /build "now" keeps the real state.
     */
    static Piece withAssumedPowders(Piece p) {
        ItemView g = ownGear(p);
        if (g == null || g.data().powderSlots() <= g.rolled().powders().size()) {
            return p;
        }
        // a key of its own: Build's memo knows a set by its keys, and the piece scores otherwise now
        Piece out = piece(g, p.key() + "|powdered", p.stack(), true);
        return out == null ? p : out;
    }

    /**
     * What {@link #withAssumedPowders} adds to a piece you have: {element, count, tier}, the
     * element null for armour (its powders count for health in any element); null for none.
     */
    static Object[] addedPowders(Piece p) {
        ItemView g = ownGear(p);
        if (g == null) {
            return null;
        }
        ItemData info = g.data();
        List<String> real = g.rolled().powders();
        List<String> add = assumedPowders(info, normalise(sharesOf(info)), info.powderSlots() - real.size(),
                real.isEmpty() ? null : real.get(0));
        if (add.isEmpty()) {
            return null;
        }
        boolean weapon = info.slot() != null && info.slot().isWeapon();
        return new Object[] {weapon ? add.get(0) : null, add.size(), powderTier(info.level())};
    }

    /**
     * The powders the road map assumes in a piece on top of its own, as {count, tier}: a piece
     * from the item data ({@code info}) has every slot filled, one you have its empty ones.
     */
    static int[] assumedCount(Piece p, ItemData info) {
        if (p == null || p.crafted()) {
            return new int[2];
        }
        if (p.stack() == null || p.stack().isEmpty()) {
            int n = info == null ? 0 : assumedPowders(info, normalise(sharesOf(info))).size();
            return new int[] {n, n == 0 ? 0 : powderTier(info.requirements().level())};
        }
        Object[] a = addedPowders(p);
        return a == null ? new int[2] : new int[] {(int) a[1], (int) a[2]};
    }

    /** The identified gear behind a piece you have (its stack), or null. */
    private static ItemView ownGear(Piece p) {
        if (p == null || p.crafted() || p.stack() == null || p.stack().isEmpty()) {
            return null;
        }
        Optional<ItemView> v = GameState.current().view(p.stack());
        return v.isEmpty() || !v.get().isGear() || v.get().unidentified() || v.get().rolled() == null ? null : v.get();
    }

    /** A weapon's base damage by element, not normalised. */
    private static Map<String, Double> sharesOf(ItemData info) {
        Map<String, Double> shares = new HashMap<>();
        info.base().damages().forEach(d -> {
            String el = d.type().toLowerCase(Locale.ROOT);
            if (List.of(ELEMENTS).contains(el)) {
                shares.merge(el, (d.low() + d.high()) / 2.0, Double::sum);
            }
        });
        return shares;
    }

    /**
     * The powders a weapon pick is judged with, for its tooltip: {element, count, tier}; null when
     * it gets none (no slots, neutral only, powders turned off) and for armour, whose powders only
     * count for health in any element.
     */
    static Object[] powderHint(ItemData info) {
        if (info.slot() == null || !info.slot().isWeapon()) {
            return null;
        }
        List<String> p = assumedPowders(info, normalise(sharesOf(info)));
        return p.isEmpty() ? null : new Object[] {p.get(0), p.size(), powderTier(info.requirements().level())};
    }

    /** A weapon's DPS and element shares with its powders in. */
    private record Powdered(double dps, Map<String, Double> shares) {}

    /**
     * Weapon powders (WynnBuilder's applyPowders): each converts its share of the weapon's own
     * neutral damage while neutral is left, and adds its flat damage. A second element's powders
     * convert from the weapon's neutral too, not from what the first element left.
     */
    private static Powdered powdered(List<Powder> powders, double dps, Map<String, Double> sh,
            Map<String, Double> stats) {
        int t = (int) Math.round(stats.getOrDefault(TIER, 3.0));
        double perHit = dps / HITS[Math.max(0, Math.min(6, t))];
        Map<String, Double> parts = new HashMap<>();
        sh.forEach((k, v) -> parts.put(k, v * perHit));
        double neutralBase = parts.getOrDefault("neutral", 0.0);
        for (Powder pw : powders) {
            double[] row = POWDER[elementIndex(pw.element())][pw.tier() - 1];
            double neutral = parts.getOrDefault("neutral", 0.0);
            double moved = Math.min(neutral, row[2] / 100.0 * neutralBase);
            parts.put("neutral", neutral - moved);
            parts.merge(pw.element(), moved + (row[0] + row[1]) / 2.0, Double::sum);
        }
        double sum = parts.values().stream().mapToDouble(Double::doubleValue).sum();
        return new Powdered(sum * HITS[Math.max(0, Math.min(6, t))], normalise(parts));
    }

    static Piece piece(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Optional<ItemView> p = GameState.current().view(stack);
        if (p.isEmpty()) {
            return null;
        }
        return piece(p.get(), ItemJson.keyOf(stack), stack);
    }

    /** The same from the mod's reading of an item; {@code stack} may be null (not one you carry). */
    static Piece piece(ItemView item, String key, ItemStack stack) {
        return piece(item, key, stack, false);
    }

    /** {@code fill}: its empty powder slots filled too ({@link #withAssumedPowders}). */
    private static Piece piece(ItemView item, String key, ItemStack stack, boolean fill) {
        Map<String, Double> stats = new HashMap<>();
        Map<SkillKind, Integer> reqs = new java.util.EnumMap<>(SkillKind.class);
        Map<String, Double> shares = new HashMap<>();
        if (item.isGear()) {
            ItemView g = item;
            if (g.unidentified()) {
                return null;
            }
            ItemData info = g.data();
            for (Roll v : g.rolled().rolls()) {
                stats.merge(v.key(), (double) v.value(), Double::sum);
            }
            info.requirements().skills().forEach(r -> reqs.put(r.skill(), r.points()));
            var fixed = info.base();
            fixed.damages().forEach(d -> {
                String el = d.type().toLowerCase(Locale.ROOT);
                if (List.of(ELEMENTS).contains(el)) {
                    shares.merge(el, (d.low() + d.high()) / 2.0, Double::sum);
                }
            });
            addDefences(stats, fixed);
            fixed.attackSpeed().ifPresent(a -> {
                stats.put(FEEL, feel(a));
                stats.put(TIER, tierOf(a));
            });
            info.set().ifPresent(si -> {
                SETS.put(si.name(), si);
                stats.put(SET + si.name(), 1.0);
            });
            double dps = fixed.averageDps();
            Map<String, Double> sh = normalise(shares);
            double health = fixed.health();
            List<String> powders = g.rolled().powders();
            if (fill) {
                List<String> add = assumedPowders(info, sh, info.powderSlots() - powders.size(),
                        powders.isEmpty() ? null : powders.get(0));
                if (!add.isEmpty()) {
                    List<String> all = new java.util.ArrayList<>(powders);
                    all.addAll(add);
                    powders = all;
                }
            }
            if (!powders.isEmpty()) {
                List<Powder> ps = tiered(powders, powderTier(info.level()));
                if (info.slot() != null && info.slot().isWeapon()) {
                    // weapon powders: convert part of the neutral damage and add flat damage
                    Powdered out = powdered(ps, dps, sh, stats);
                    dps = out.dps();
                    sh = out.shares();
                } else {
                    // armour powders: health by tier (same for every element)
                    health += powderHealth(ps);
                }
            }
            return new Piece(stack, key, info.name(), info.slot(), info.level(), stats, reqs,
                    dps, sh, health);
        }
        if (item.isCrafted()) {
            ItemView.Crafted c = item.crafted();
            for (Roll v : c.rolls()) {
                stats.merge(v.key(), (double) v.value(), Double::sum);
            }
            c.requirements().skills().forEach(r -> reqs.put(r.skill(), r.points()));
            c.damages().forEach(d -> {
                String el = d.type().toLowerCase(Locale.ROOT);
                if (List.of(ELEMENTS).contains(el)) {
                    shares.merge(el, (d.low() + d.high()) / 2.0, Double::sum);
                }
            });
            return new Piece(stack, key, c.name(), c.slot(), c.level(), stats, reqs,
                    c.dps(),
                    normalise(shares), c.health(), true);
        }
        return null;
    }


    /** A piece's base elemental defences ("Earth Defence 100") into its stats, under {@link #defKey}. */
    private static void addDefences(Map<String, Double> stats, io.github.anilken.wynnlabs.model.BaseStats base) {
        if (base == null || base.defences() == null) {
            return;
        }
        for (var d : base.defences()) {
            stats.merge(defKey(d.element().toLowerCase(Locale.ROOT)), (double) d.value(), Double::sum);
        }
    }

    private static Map<String, Double> normalise(Map<String, Double> m) {
        double sum = m.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Double> out = new HashMap<>();
        if (sum <= 0) {
            out.put("neutral", 1.0);
            return out;
        }
        m.forEach((k, v) -> out.put(k, v / sum));
        return out;
    }
}
