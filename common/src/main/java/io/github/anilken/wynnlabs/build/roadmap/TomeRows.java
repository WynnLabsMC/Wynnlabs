package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Exclusive;
import io.github.anilken.wynnlabs.build.Tomes;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Gear;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Option;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Slot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * The rows of a milestone whose set only fits with the guild tome it assumes ({@link Tomes}):
 * each such pick says so, with the best piece for its slot without the tome ("Without the tome:
 * Ascended Lament (−6%)"), and that piece in the row's comparison panel, so the other way is one
 * click. Only the picked slots are judged again, without the tome; no second search.
 */
final class TomeRows {

    private TomeRows() {}

    /**
     * {@code why} (row name → reason lines) with the tome lines in front for each pick of
     * {@code build} that needs the assumed tome; the rows of {@code gear} get the slot's best piece
     * without the tome in their panel, judged by {@code rejudge} as the row's other choices were.
     * {@code why} as it was when the set fits without the tome, or none is assumed.
     */
    static Map<String, List<String>> apply(Map<String, Damage.Piece> build, Map<String, String> placed, List<Gear> gear,
            Map<Slot, List<ItemData>> pools, Map<String, Damage.Piece> start, int m, int earned,
            Map<String, List<String>> why, BiFunction<Gear, Option, Option> rejudge) {
        Build.Eval full = Build.allocate(build, m, earned);
        if (full == null || full.tome() == null || !full.tome().assumed() || full.result().objective() <= 0) {
            return why;
        }
        if (without(() -> Build.allocate(build, m, earned)) != null) {
            return why;                         // fits without it: the milestone's line says it is assumed
        }
        Map<String, List<String>> out = new LinkedHashMap<>(why);
        int limit = (int) Rules.sub("roadmap", "tomeAltPool", 20);
        for (int i = 0; i < gear.size(); i++) {
            Gear g = gear.get(i);
            String slot = placed.get(g.slot());
            Damage.Piece pick = slot == null ? null : build.get(slot);
            if (RoadmapRows.isPowder(g) || pick == null || !pick.name().equalsIgnoreCase(g.info().name())) {
                continue;
            }
            Slot t = slot.startsWith("RING") ? Slot.RING : slot.equals("WEAPON") ? Slot.WEAPON : g.info().slot();
            List<ItemData> pool = new ArrayList<>();
            Damage.Piece had = start == null ? null : start.get(slot);
            ItemData hadInfo = had == null ? null : Roadmap.findGear(had.name());
            if (hadInfo != null) {
                pool.add(hadInfo);
            }
            List<ItemData> ranked = t.isWeapon() ? SetJudge.weaponPool(pools) : pools.get(t);
            if (ranked != null) {
                ranked.stream().limit(limit).forEach(pool::add);
            }
            ItemData best = null;
            double bestObj = 0;
            for (ItemData info : pool) {
                if (info.name().equalsIgnoreCase(pick.name()) || info.requirements().level() > m
                        || !CandidateRules.panelAllows(info, m)
                        || Exclusive.clash(build, slot, Exclusive.group(info)) != null) {
                    continue;
                }
                Map<String, Damage.Piece> trial = new LinkedHashMap<>(build);
                trial.put(slot, info == hadInfo ? had : OwnedGear.pieceOf(info));
                Build.Eval e = without(() -> Build.allocate(trial, m, earned));
                if (e != null && e.result().objective() > bestObj) {
                    best = info;
                    bestObj = e.result().objective();
                }
            }
            if (best == null) {
                continue;                       // no one piece here fits it without the tome
            }
            List<String> lines = new ArrayList<>();
            lines.add(L.t("wynnlabs.road.why_needs_tome", full.tome().text()));
            lines.add(L.t("wynnlabs.road.why_without_tome", best.name(),
                    RoadmapGains.gainText((bestObj / full.result().objective() - 1) * 100)));
            lines.addAll(out.getOrDefault(g.slot(), List.of()));
            out.put(g.slot(), lines);
            gear.set(i, withOption(g, best, slot, build, full, m, earned, rejudge));
        }
        return out;
    }

    /** {@code body} with no tome assumed (your real tomes still count). */
    private static <T> T without(java.util.function.Supplier<T> body) {
        Tomes.Scope s = Tomes.active();
        return Tomes.with(s == null ? null : new Tomes.Scope(s.real(), false), body);
    }

    /** {@code g} with {@code info} second in its panel (after the pick), so the panel always shows it. */
    private static Gear withOption(Gear g, ItemData info, String slot, Map<String, Damage.Piece> build, Build.Eval full,
            int m, int earned, BiFunction<Gear, Option, Option> rejudge) {
        List<Option> opts = new ArrayList<>(g.options() == null ? List.of() : g.options());
        Option have = null;
        for (Option o : opts) {
            if (o.info().name().equalsIgnoreCase(info.name())) {
                have = o;
            }
        }
        if (have != null) {
            opts.remove(have);
        } else {
            // judged with the tome like the panel's others: the panel compares pieces, the line above the tome
            have = RoadmapRows.option(info, slot, build, full, m, earned);
            have = have == null ? null : rejudge.apply(g, have);
        }
        if (have == null) {
            return g;
        }
        opts.add(Math.min(1, opts.size()), have);
        return new Gear(g.slot(), g.info(), g.gain(), g.source(), g.price(), g.alt(), g.altSource(), g.detail(),
                g.alternatives(), opts);
    }
}
