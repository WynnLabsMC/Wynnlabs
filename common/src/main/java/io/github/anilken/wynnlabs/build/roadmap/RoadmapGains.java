package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Exclusive;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Gear;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Step;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap.Versus;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The numbers and reasons a road map row shows: what a pick adds to its milestone's set, what it
 * gains over what you wear, which of the two a row's pill shows, and the reason lines.
 */
final class RoadmapGains {

    private RoadmapGains() {}

    /**
     * For each slot changed by this milestone ({@code placed}: slot type name → the build slot
     * of its latest pick), the % your build gains with the pick instead of what you wear now,
     * at the milestone's level and skill points. Always against what you wear now ({@code now}):
     * <ul>
     * <li>that piece alone in what you wear now: a better piece always shows a bigger number,
     *     also across milestones (inside the milestone's full build the other upgrades shrink
     *     one slot's share: a Lv 93 Mythic at Lv 120 showed less than a Lv 88 Rare at Lv 100).
     *     When the milestone's other upgrades make it better still, the reasons start with a
     *     line that says so;</li>
     * <li>a pick that cannot go into what you wear, or loses there (another pick carries its
     *     skill points): its own share of the milestone's set, what the set loses without it, in
     *     per cent of what you wear, with its reasons as plain amounts. The whole set's gain on one
     *     piece's pill once read +407120 % beside single pieces' numbers; with the set judged
     *     without the pick, the reasons started from the set ("Main Attack: 13257 → 19976")
     *     while 22 was equipped. When even that cannot be told, no number and a line that says
     *     the piece needs the rest of the set;</li>
     * <li>a slot you have nothing in: no per cent (against nothing it only explodes), the
     *     reasons as plain amounts.</li>
     * </ul>
     * With the reason lines of each slot ({@link #why}) and what each was judged against.
     */
    static Versus versus(Map<String, Damage.Piece> build, Map<String, Damage.Piece> worn,
            Map<String, String> placed, int m, int earned) {
        Map<String, Double> out = new LinkedHashMap<>();
        Map<String, List<String>> why = new LinkedHashMap<>();
        Map<String, Roadmap.Against> against = new LinkedHashMap<>();
        Build.Eval now = Build.allocate(worn, m, earned);
        Build.Eval full = Build.allocate(build, m, earned);
        for (var e : placed.entrySet()) {
            String slot = e.getValue();
            Damage.Piece was = worn.get(slot);
            Damage.Piece pick = build.get(slot);
            if (pick == null || was != null && was.key().equals(pick.key())) {
                continue;
            }
            double alone = Double.NaN;
            Build.Eval with = null;
            if (now != null && now.result().objective() > 0) {
                Map<String, Damage.Piece> one = new LinkedHashMap<>(worn);
                one.put(slot, pick);
                with = Build.allocate(one, m, earned);
                if (with != null && with.result().objective() > 0) {
                    alone = (with.result().objective() / now.result().objective() - 1) * 100;
                }
            }
            double together = Double.NaN;
            Build.Eval back = null;
            if (full != null && full.result().objective() > 0) {
                Map<String, Damage.Piece> rest = new LinkedHashMap<>(build);
                rest.put(slot, was);
                back = Build.allocate(rest, m, earned);
                if (back == null && was != null) {
                    // what you wear there no longer fits the set (its skill points, two Black
                    // Catalysts at Lv 90): the set without anything there, or alone the pick
                    // looked like a loss
                    rest.remove(slot);
                    back = Build.allocate(rest, m, earned);
                }
                if (back != null && back.result().objective() > 0) {
                    together = (full.result().objective() / back.result().objective() - 1) * 100;
                }
            }
            List<String> lines = new ArrayList<>();
            boolean nowOk = now != null && now.result().objective() > 0;
            boolean backOk = back != null && back.result().objective() > 0 && full != null;
            if (was == null) {
                // an empty slot now: plain amounts, no per cent
                if (with != null) {
                    lines.addAll(GainAmounts.amounts(now, with));
                    against.put(e.getKey(), new Roadmap.Against(worn, now, slot));
                } else if (backOk) {
                    lines.add(L.t("wynnlabs.road.why_needs_set", m));
                    lines.addAll(GainAmounts.amounts(back, full));
                    against.put(e.getKey(), new Roadmap.Against(build, back, slot));
                } else {
                    continue;
                }
            } else if (!Double.isNaN(alone) && alone >= 0) {
                out.put(e.getKey(), alone);
                if (!Double.isNaN(together) && together > alone + 0.5) {
                    lines.add(L.t("wynnlabs.road.why_better_together", m));
                }
                lines.addAll(why(now, with, m));
                against.put(e.getKey(), new Roadmap.Against(worn, now, slot));
            } else if (nowOk && backOk) {
                // its share of the set: what the set loses without it, in per cent of what you wear
                double unit = now.result().objective();
                out.put(e.getKey(), (full.result().objective() - back.result().objective()) / unit * 100);
                lines.add(L.t("wynnlabs.road.why_together", m));
                lines.addAll(GainAmounts.amounts(back, full));
                against.put(e.getKey(), new Roadmap.Against(build, back, slot, unit));
            } else if (nowOk && full != null && full.result().objective() > 0) {
                lines.add(L.t("wynnlabs.road.why_needs_set_gain", m,
                        gainText((full.result().objective() / now.result().objective() - 1) * 100)));
            } else {
                continue;
            }
            why.put(e.getKey(), new ArrayList<>(lines.subList(0, Math.min(lines.size(), Roadmap.WHY_LINES))));
        }
        return new Versus(out, why, against);
    }

    /**
     * Each row's step as what its pick adds to the milestone's finished {@code set}: the set
     * judged with it and with the slot as the milestone started ({@code start}: the last
     * milestone's piece, or empty), not as the set stood when the pick went in. Picks that pay off
     * together (a set's pieces, one carrying another's skill points) showed −17 % one after
     * another while the whole set was well ahead; a pick that really costs the set something still
     * shows it. A row whose slot cannot go back alone (the set no longer fits) goes back with one
     * other change of the milestone ({@link #withPartner}), never to an empty slot where something
     * was (a set without its weapon says nothing about the weapon); one that cannot even so
     * keeps its number, a target's (NaN) too. Runs on the finished set, after the search's.
     */
    static void stepsInSet(List<Gear> gear, Map<String, Damage.Piece> set, Map<String, Damage.Piece> start,
            Map<String, String> placed, int m, int earned, int[] prev) {
        Build.Eval full = Build.allocate(set, m, earned);
        if (full == null) {
            return;
        }
        double fullJ = SetJudge.judged(full, prev, m);
        for (int i = 0; i < gear.size(); i++) {
            Gear g = gear.get(i);
            String slot = placed.get(g.slot());
            Damage.Piece pick = slot == null ? null : set.get(slot);
            if (Double.isNaN(g.gain()) || pick == null || !pick.name().equalsIgnoreCase(g.info().name())) {
                continue;
            }
            Damage.Piece was = start.get(slot);
            if (was != null && was.key().equals(pick.key())) {
                continue;
            }
            // never the slot left empty where something was: a Lv 70 set with no weapon at all
            // (Void Catalyst's 40 Dexterity no longer fit) made Lightshow read +2104 %
            double without = withSlot(set, slot, was, m, earned, prev);
            if (Double.isNaN(without)) {
                // the pick carries what another of the milestone's changes needs (a new weapon's
                // skill points): it goes back together with that one, and the row shows what the
                // two add. Left at the slot-by-slot number, The Modulator read −21 % at Lv 90
                without = withPartner(set, start, slot, was, m, earned, prev);
            }
            if (Double.isNaN(without) || without <= 0) {
                continue;
            }
            double rowGain = (fullJ / without - 1) * 100;
            gear.set(i, new Gear(g.slot(), g.info(), rowGain, g.source(), g.price(), g.alt(),
                    g.altSource(), g.detail(), g.alternatives(), setGain(g.options(), rowGain)));
        }
    }

    /** {@code set} judged with {@code p} in {@code slot} (null: empty); NaN when that cannot be worn. */
    private static double withSlot(Map<String, Damage.Piece> set, String slot, Damage.Piece p, int m, int earned,
            int[] prev) {
        Map<String, Damage.Piece> trial = new LinkedHashMap<>(set);
        trial.remove(slot);
        if (p != null) {
            if (Exclusive.clash(trial, slot, Exclusive.group(p)) != null) {
                return Double.NaN;
            }
            trial.put(slot, p);
        }
        Build.Eval ev = Build.allocate(trial, m, earned);
        return ev == null ? Double.NaN : SetJudge.judged(ev, prev, m);
    }

    /**
     * {@code set} with {@code slot} and one other slot the milestone changed both as they were
     * when it started ({@code start}; empty where nothing was): the best such that can be worn, so
     * the row claims the least it can. Never a slot left empty that held something: a set without
     * its leggings says nothing about the leggings (Omniverse read +617 % that way). NaN when none
     * can be worn.
     */
    private static double withPartner(Map<String, Damage.Piece> set, Map<String, Damage.Piece> start, String slot,
            Damage.Piece was, int m, int earned, int[] prev) {
        double best = Double.NaN;
        for (String other : set.keySet()) {
            Damage.Piece now = set.get(other);
            Damage.Piece then = start.get(other);
            if (other.equals(slot) || now == null || then != null && then.key().equals(now.key())) {
                continue;
            }
            Map<String, Damage.Piece> trial = new LinkedHashMap<>(set);
            trial.remove(slot);
            trial.remove(other);
            if (was != null) {
                if (Exclusive.clash(trial, slot, Exclusive.group(was)) != null) {
                    continue;
                }
                trial.put(slot, was);
            }
            if (then != null) {
                if (Exclusive.clash(trial, other, Exclusive.group(then)) != null) {
                    continue;
                }
                trial.put(other, then);
            }
            Build.Eval ev = Build.allocate(trial, m, earned);
            if (ev != null) {
                double j = SetJudge.judged(ev, prev, m);
                if (Double.isNaN(best) || j > best) {
                    best = j;
                }
            }
        }
        return best;
    }

    /**
     * The number a row's pill shows on milestone {@code selected}: the gain over what you wear
     * now ({@link Step#vsWorn}; a bag piece carries its own). The hover's headline, its reasons
     * ({@link #shownWhy}) and the comparison panel are judged against the same builds. A Lv 97
     * spear once read "+22 %" over the milestone before while its reasons were against the Lv 4
     * spear worn ("Main Attack: 22 → 2295"). NaN: nothing to compare with (a new slot whose pick
     * cannot be judged against what you wear, a target).
     */
    static double shownGain(List<Step> steps, int selected, Gear u) {
        if (u == null || Double.isNaN(u.gain()) || steps.isEmpty() || RoadmapRows.fromBag(u)) {
            return u == null ? Double.NaN : steps.isEmpty() || RoadmapRows.fromBag(u) ? u.gain() : Double.NaN;
        }
        Step s = steps.get(Math.max(0, Math.min(selected, steps.size() - 1)));
        Double v = s.vsWorn().get(u.slot());
        return v == null ? Double.NaN : v;
    }

    /** The reason lines a row's hover shows, against the same builds as {@link #shownGain}; empty when none. */
    static List<String> shownWhy(List<Step> steps, int selected, Gear u) {
        if (u == null) {
            return List.of();
        }
        if (steps.isEmpty() || RoadmapRows.fromBag(u)) {
            return u.detail() == null ? List.of() : u.detail();
        }
        Step s = steps.get(Math.max(0, Math.min(selected, steps.size() - 1)));
        List<String> w = s.whyVsWorn().get(u.slot());
        return w == null ? List.of() : w;
    }

    /**
     * {@code options} judged the way their row's number was ({@code a}): each option in the
     * pick's place, the pick first at {@code rowGain}, the others best gain first. Options the
     * base cannot wear drop out. The panel was judged against the set as it stood when the pick
     * went in, labelled "Now": a Lv 97 spear read −17 % there under a row far above what you wear.
     */
    static List<Roadmap.Option> againstWorn(List<Roadmap.Option> options, String pickName, Damage.Piece pick,
            Roadmap.Against a, double rowGain, int m, int earned) {
        if (options == null || options.isEmpty() || a == null || a.from() == null) {
            return options;
        }
        Map<String, Damage.Piece> base = new LinkedHashMap<>(a.base());
        base.remove(a.slot());
        Roadmap.Option first = null;
        List<Roadmap.Option> rest = new ArrayList<>();
        for (Roadmap.Option o : options) {
            boolean isPick = o.info().name().equalsIgnoreCase(pickName);
            Damage.Piece piece = isPick && pick != null ? pick : OwnedGear.pieceOf(o.info());
            if (io.github.anilken.wynnlabs.build.Exclusive.clash(base, a.slot(),
                    io.github.anilken.wynnlabs.build.Exclusive.group(piece)) != null) {
                continue;
            }
            Roadmap.Option n = RoadmapRows.option(o.info(), piece, a.slot(), base, a.from(), m, earned);
            if (n == null) {
                continue;
            }
            double g = Double.isNaN(a.unit()) ? n.gain()
                    : (n.to().objective() - a.from().result().objective()) / a.unit() * 100;
            Roadmap.Option kept = new Roadmap.Option(o.info(), isPick && !Double.isNaN(rowGain) ? rowGain : g, o.price(), o.source(),
                    o.cautions(), n.skills(), n.respec(), n.factorPct(), n.from(), n.to(), o.set());
            if (isPick && first == null) {
                first = kept;
            } else {
                rest.add(kept);
            }
        }
        rest.sort((x, y) -> Double.compare(y.gain(), x.gain()));
        if (first != null) {
            rest.add(0, first);
        }
        return rest;
    }

    /** A community target (rules.roadmap.targets): its row has no gain, it is there for the set. */
    static boolean isTarget(Gear g) {
        return g != null && Rules.list("roadmap", "targets").stream().anyMatch(n -> n.equalsIgnoreCase(g.info().name()));
    }

    /**
     * A row's pill: the whole per cent ("+12%", "+407120%"), never shortened; with no number
     * "New" (nothing to compare with), or "Set" for a community target.
     */
    static String pill(double gain, Gear g) {
        if (Double.isNaN(gain)) {
            return L.t(isTarget(g) ? "wynnlabs.screen.pill_set" : "wynnlabs.screen.pill_new");
        }
        return gainText(gain);
    }

    /**
     * A gain as the menu writes it, the whole per cent in the game's language: "+12%", "−3%",
     * "+10107%" (Turkish "+%10107"). The gear footer and the hover write it so.
     */
    static String gainText(double gain) {
        return L.pct((gain < -0.5 ? "−" : "+") + Math.round(Math.abs(gain)));
    }

    /**
     * Why build {@code b} beats {@code a}, biggest change first, at most {@link Roadmap#WHY_LINES}
     * lines in the game's language (the {@link #explain} lines plus attack speed): spell damage,
     * main attack, effective health, mana, walk speed, XP, attack speed; a change of skill
     * points (or a respec) always gets the last line when there is one. No XP line at the level
     * cap, where the model gives XP no weight.
     */
    static List<String> why(Build.Eval a, Build.Eval b, int level) {
        Damage.Result x = a.result();
        Damage.Result y = b.result();
        List<Object[]> lines = new ArrayList<>();      // {weight, text}
        rel(lines, L.t("wynnlabs.road.spell_damage"), x.spellDps(), y.spellDps());
        rel(lines, L.t("wynnlabs.road.main_attack"), x.mainDps(), y.mainDps());
        rel(lines, L.t("wynnlabs.road.effective_health"), x.ehp(), y.ehp());
        double mx = mana(x);
        double my = mana(y);
        if (mx > 0 && Math.abs(my / mx - 1) >= 0.01) {
            lines.add(new Object[] {Math.abs(my / mx - 1), manaLine(mx, my)});
        }
        if (Math.round(y.speed()) != Math.round(x.speed())) {
            lines.add(new Object[] {Math.abs(y.speed() - x.speed()) / 100.0 * Rules.sub("model", "speedWeight", 0.3),
                    L.t("wynnlabs.road.walk_speed", L.pct(signed(x.speed())), L.pct(signed(y.speed())))});
        }
        if (Math.round(y.xp()) != Math.round(x.xp()) && level < Rules.sub("model", "maxLevel", 120)) {
            lines.add(new Object[] {Math.abs(y.xp() - x.xp()) / 100.0 * Rules.sub("model", "xpWeight", 1.0),
                    L.t("wynnlabs.road.xp_bonus", L.pct(Math.round(x.xp())), L.pct(Math.round(y.xp())))});
        }
        if (x.factors().length > 5 && y.factors().length > 5) {
            rel(lines, L.t("wynnlabs.damage.factor_attack_speed"), x.factors()[5] * 100, y.factors()[5] * 100);
        }
        lines.sort((p, q) -> Double.compare((double) q[0], (double) p[0]));
        List<String> skills = new ArrayList<>();
        boolean down = false;
        for (SkillKind s : SkillKind.values()) {
            int i = s.ordinal();
            if (a.allocation()[i] != b.allocation()[i]) {
                skills.add(s.displayName().substring(0, 3) + " " + a.allocation()[i] + "→" + b.allocation()[i]);
                down |= b.allocation()[i] < a.allocation()[i];
            }
        }
        List<String> out = new ArrayList<>();
        int room = skills.isEmpty() ? Roadmap.WHY_LINES : Roadmap.WHY_LINES - 1;
        for (int i = 0; i < lines.size() && out.size() < room; i++) {
            out.add((String) lines.get(i)[1]);
        }
        if (!skills.isEmpty()) {
            out.add(down ? L.t("wynnlabs.road.skills_respec", String.join(", ", skills))
                    : L.t("wynnlabs.road.skills", String.join(", ", skills)));
        }
        return out;
    }

    /** The mana part of a build's score (Damage.FACTORS), 0 when unknown. */
    private static double mana(Damage.Result r) {
        return r.factors().length > 2 ? r.factors()[2] : 0;
    }

    /** "Mana: ×1.12 → ×1.25". */
    private static String manaLine(double from, double to) {
        return L.t("wynnlabs.road.mana", String.format(java.util.Locale.ROOT, "×%.2f", from),
                String.format(java.util.Locale.ROOT, "×%.2f", to));
    }

    /** A "name: from → to (±n %)" line weighted by its relative change; none under 2 %. */
    private static void rel(List<Object[]> lines, String name, double from, double to) {
        if (from <= 0 || Math.abs(to / from - 1) < 0.02) {
            return;
        }
        List<String> one = new ArrayList<>();
        line(one, name, from, to, "", true);
        lines.add(new Object[] {Math.abs(to / from - 1), one.get(0)});
    }

    /** What changes in the build with the pick in: spells, health, casting, speed, skill points. */
    static List<String> explain(Build.Eval a, Build.Eval b) {
        List<String> out = new ArrayList<>();
        Damage.Result x = a.result();
        Damage.Result y = b.result();
        line(out, L.t("wynnlabs.road.spell_damage"), x.spellDps(), y.spellDps(), "", true);
        line(out, L.t("wynnlabs.road.main_attack"), x.mainDps(), y.mainDps(), "", true);
        line(out, L.t("wynnlabs.road.effective_health"), x.ehp(), y.ehp(), "", true);
        if (Math.abs(mana(y) - mana(x)) >= 0.01) {
            out.add(manaLine(mana(x), mana(y)));
        }
        if (Math.round(y.speed()) != Math.round(x.speed())) {
            out.add(L.t("wynnlabs.road.walk_speed", L.pct(signed(x.speed())), L.pct(signed(y.speed()))));
        }
        if (Math.round(y.xp()) != Math.round(x.xp())) {
            out.add(L.t("wynnlabs.road.xp_bonus", L.pct(Math.round(x.xp())), L.pct(Math.round(y.xp()))));
        }
        List<String> sk = new ArrayList<>();
        boolean down = false;
        for (SkillKind s : SkillKind.values()) {
            int i = s.ordinal();
            if (a.allocation()[i] != b.allocation()[i]) {
                sk.add(s.displayName().substring(0, 3) + " " + a.allocation()[i] + "→" + b.allocation()[i]);
                down |= b.allocation()[i] < a.allocation()[i];
            }
        }
        if (!sk.isEmpty()) {
            out.add(down ? L.t("wynnlabs.road.skills_respec", String.join(", ", sk))
                    : L.t("wynnlabs.road.skills", String.join(", ", sk)));
        }
        return out;
    }

    private static void line(List<String> out, String name, double from, double to, String unit, boolean pct) {
        if (from <= 0 || Math.abs(to / from - 1) < 0.02) {
            return;                             // under 2 %: not worth a line
        }
        String change = L.t("wynnlabs.road.change", name, Math.round(from), Math.round(to));
        out.add(pct ? change + L.t("wynnlabs.road.change_pct", to >= from ? "+" : "−",
                Math.round(Math.abs(to / from - 1) * 100)) : change);
    }

    private static String signed(double v) {
        long n = Math.round(v);
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    /**
     * The options with a set's pick (first, marked) at its row's gain, and the others judged in
     * that set moved with it (their gains are on the pick's scale): the panel and the row agree.
     */
    private static List<Roadmap.Option> setGain(List<Roadmap.Option> options, double rowGain) {
        if (options == null || options.isEmpty() || !options.get(0).set()) {
            return options;
        }
        double ratio = (1 + rowGain / 100) / (1 + options.get(0).gain() / 100);
        List<Roadmap.Option> out = new java.util.ArrayList<>();
        for (Roadmap.Option o : options) {
            out.add(o.set() ? o.withGain(((1 + o.gain() / 100) * ratio - 1) * 100) : o);
        }
        out.set(0, options.get(0).withGain(rowGain));
        // the pick first, the others best gain first again (only those judged in the set moved)
        List<Roadmap.Option> rest = new java.util.ArrayList<>(out.subList(1, out.size()));
        rest.sort((x, y) -> Double.compare(y.gain(), x.gain()));
        out.subList(1, out.size()).clear();
        out.addAll(rest);
        return out;
    }
}
