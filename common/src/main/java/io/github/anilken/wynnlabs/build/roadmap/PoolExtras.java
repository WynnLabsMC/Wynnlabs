package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.GearScore;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Requirement;
import io.github.anilken.wynnlabs.model.Slot;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * A slot pool's second half: the pieces a ranking by one swap into the last milestone's set
 * leaves out, though a whole set could want them. The slot pool ranks each piece in the old set
 * with its old skill points, so a piece that asks for another skill, a set piece that pays only
 * with its set, or a weapon of another element ranks low or cannot be worn there at all, and the
 * search never tried it. Here: every member of a set the pool already holds, each skill's (a
 * weapon: each element's) best rules.roadmap.poolPerSkill (2) and the best
 * rules.roadmap.poolStandalone (4) overall, judged on their own (with the old set's weapon only,
 * the skill points spread for them; a weapon alone; only a stat-score shortlist is judged so),
 * and what you have, at most
 * rules.roadmap.poolOwned (6). None of them already in the pool, in that order. Deterministic.
 */
final class PoolExtras {

    private PoolExtras() {}

    /**
     * The extras for slot type {@code t}: {@code head} is its pool, {@code wide} everything the
     * milestone allows there, {@code weapon} the old set's weapon (null: none), {@code piece} the
     * piece each item is judged as.
     */
    static List<ItemData> of(Slot t, List<ItemData> head, List<ItemData> wide, Set<String> owned, Damage.Piece weapon,
            Function<ItemData, Damage.Piece> piece, int m, int earned) {
        Set<String> in = new HashSet<>();
        head.forEach(i -> in.add(i.name()));
        Set<String> sets = new HashSet<>();
        head.forEach(i -> i.set().ifPresent(s -> sets.add(s.name())));
        return of(t, in, sets, wide, owned, weapon, piece, m, earned);
    }

    /** {@link #of} for a pool given by its names ({@code in}) and the sets its pieces belong to ({@code sets}). */
    static List<ItemData> of(Slot t, Set<String> in, Set<String> sets, List<ItemData> wide, Set<String> owned,
            Damage.Piece weapon, Function<ItemData, Damage.Piece> piece, int m, int earned) {
        if (wide == null || wide.isEmpty()) {
            return List.of();
        }
        // judged alone only the few worth it: by the stat score (GearScore) each skill's best
        // rules.roadmap.poolShortlist (4) and twice that overall, every set member and what you have
        int shortN = (int) Rules.sub("roadmap", "poolShortlist", 4);
        List<ItemData> byStats = new ArrayList<>();
        for (ItemData info : wide) {
            if (info.requirements().level() <= m && !in.contains(info.name())) {
                byStats.add(info);
            }
        }
        Map<ItemData, Double> stat = new java.util.HashMap<>();
        byStats.forEach(i -> stat.put(i, GearScore.score(i)));
        byStats.sort((x, y) -> {
            int by = Double.compare(stat.get(y), stat.get(x));
            return by != 0 ? by : x.name().compareTo(y.name());
        });
        List<ItemData> shortlist = new ArrayList<>();
        int[] perOrient = new int[6];
        for (int i = 0; i < byStats.size(); i++) {
            ItemData info = byStats.get(i);
            int o = t.isWeapon() ? SetSearch.element(info) : orientation(info) + 1;
            boolean set = info.set().isPresent() && sets.contains(info.set().get().name());
            if (set || owned.contains(info.name()) || i < 2 * shortN || perOrient[o]++ < shortN) {
                shortlist.add(info);
            }
        }
        // each piece's own score: alone with the old weapon (a weapon alone), the points spread for it
        Map<ItemData, Double> alone = new LinkedHashMap<>();
        for (ItemData info : shortlist) {
            Map<String, Damage.Piece> b = new LinkedHashMap<>();
            if (t.isWeapon()) {
                b.put("WEAPON", piece.apply(info));
            } else {
                String slot = Build.slotOf(t, 0);
                if (slot == null || weapon == null) {
                    continue;
                }
                b.put("WEAPON", weapon);
                b.put(slot, piece.apply(info));
            }
            Roadmap.tick();
            Build.Eval e = Build.allocate(b, m, earned);
            if (e != null) {
                alone.put(info, e.result().objective());
            }
        }
        List<ItemData> byScore = new ArrayList<>(alone.keySet());
        byScore.sort((x, y) -> {
            int by = Double.compare(alone.get(y), alone.get(x));
            return by != 0 ? by : x.name().compareTo(y.name());
        });
        List<ItemData> out = new ArrayList<>();
        Set<String> taken = new HashSet<>(in);
        // the rest of every set the pool holds a piece of
        for (ItemData info : byScore) {
            if (info.set().isPresent() && sets.contains(info.set().get().name()) && taken.add(info.name())) {
                out.add(info);
            }
        }
        // each skill's (each element's) best on its own, the first of each, then the second …
        int per = (int) Rules.sub("roadmap", "poolPerSkill", 2);
        List<List<ItemData>> bySkill = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            bySkill.add(new ArrayList<>());
        }
        for (ItemData info : byScore) {
            int o = t.isWeapon() ? SetSearch.element(info) : orientation(info) + 1;
            if (o > 0 && bySkill.get(o).size() < per) {
                bySkill.get(o).add(info);
            }
        }
        for (int r = 0; r < per; r++) {
            for (List<ItemData> l : bySkill) {
                if (r < l.size() && taken.add(l.get(r).name())) {
                    out.add(l.get(r));
                }
            }
        }
        int top = (int) Rules.sub("roadmap", "poolStandalone", 4);
        for (int i = 0; i < byScore.size() && i < top; i++) {
            if (taken.add(byScore.get(i).name())) {
                out.add(byScore.get(i));
            }
        }
        // what you have: it needs no buy (the best on their own first)
        int mine = (int) Rules.sub("roadmap", "poolOwned", 6);
        int n = 0;
        List<ItemData> ownedFirst = new ArrayList<>(byScore);
        for (ItemData info : wide) {
            if (!alone.containsKey(info) && info.requirements().level() <= m && !in.contains(info.name())) {
                ownedFirst.add(info);                   // cannot be judged alone: after the rest
            }
        }
        for (ItemData info : ownedFirst) {
            if (n >= mine) {
                break;
            }
            if (owned.contains(info.name()) && !in.contains(info.name())) {
                n++;
                if (taken.add(info.name())) {
                    out.add(info);
                }
            }
        }
        return out;
    }

    /** The skill a piece leans on: the one it asks the most points of, else the one it gives most; -1 for none. */
    static int orientation(ItemData info) {
        int at = -1;
        int most = 0;
        for (Requirement.SkillReq r : info.requirements().skills()) {
            if (r.points() > most) {
                most = r.points();
                at = r.skill().ordinal();
            }
        }
        if (at >= 0) {
            return at;
        }
        int[] give = new int[5];
        for (var s : info.stats()) {
            int k = switch (s.key()) {
                case "rawStrength" -> 0;
                case "rawDexterity" -> 1;
                case "rawIntelligence" -> 2;
                case "rawDefence" -> 3;
                case "rawAgility" -> 4;
                default -> -1;
            };
            if (k >= 0) {
                give[k] = Math.max(give[k], s.max());
            }
        }
        for (int k = 0; k < 5; k++) {
            if (give[k] > most) {
                most = give[k];
                at = k;
            }
        }
        return at;
    }
}
