package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.core.Rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Where a build's damage and healing come from, one entry per source, worked out the way
 * WynnBuilder does: each hit from its own conversion through the whole damage pipeline (the
 * weapon's base DPS, % and raw IDs, skill points, Strength and the crit-weighted average), times
 * its hits a second and how many there are. The entries are each spell the model casts (with
 * its parts: one Aura wave, the first wave from every Totem, all waves; one Totem tick, a Totem's
 * ticks a second, all Totems'), a lasting spell, the main attack, each summon and area of the
 * ability tree, the tree's heals and, apart from the totals, the ultimates. The model's totals
 * are their sum.
 *
 * <p>How much of a fight a summon or an area is really out and hitting is not in these numbers:
 * it is a weight of the score alone, {@link #fightShare}, kept in this one place.
 */
public final class Sources {

    private Sources() {}

    /** A part of a source as WynnBuilder lists it ("First Wave", "Tick DPS"): its damage, or DPS for a rate. */
    public record Part(String name, double value) {}

    /**
     * One source. {@code kind}: spell, lasting, main, summon, area, other (summons the tree does
     * not name), heal or ultimate. {@code from}: the spell it comes with (null: none known).
     * {@code dps} what it adds to the build's DPS (an ultimate: while it is up, not in the
     * totals); {@code perHit} one hit, {@code hitsPerSecond} and {@code count} how often and how
     * many hit; {@code perCast} and {@code castsPerSecond} for spells; {@code heal} and
     * {@code healPerSecond} for heals (health points); {@code parts} the spell's parts. 0 where
     * it does not apply.
     */
    public record Source(String name, String kind, String from, double dps, double perHit, double hitsPerSecond,
            double count, double perCast, double castsPerSecond, double heal, double healPerSecond, List<Part> parts) {

        /** False for heals and ultimates: an ultimate is on a meter, not in the model's totals. */
        public boolean counted() {
            return !kind.equals("ultimate") && !kind.equals("heal");
        }
    }

    /**
     * A summon or area of the tree as the overlay took it: per-hit conversions (the tree's own
     * bonuses for it in), hits a second, how many, the factor on each hit ("each additional
     * Effigy … 0.8x") and its {@link #fightShare}, read once with the overlay.
     */
    record Passive(String name, String noun, boolean summon, String from, double[] conv, double rate, int count,
            double mult, double share) {}

    /**
     * The share of a fight a summon ({@code summon}) or an area is out and hitting, for the score
     * only: puppets and hounds die, wait to be summoned again and walk to the next pack
     * (rules.model.summonAlive, 0.6), a trap or a sigil hits what stands in it
     * (rules.model.areaUptime, 0.5). The numbers shown are the whole of what each deals.
     */
    public static double fightShare(boolean summon) {
        double v = summon ? Rules.sub("model", "summonAlive", 0.6) : Rules.sub("model", "areaUptime", 0.5);
        return Double.isFinite(v) ? Math.max(0.05, Math.min(1, v)) : summon ? 0.6 : 0.5;
    }

    /** What the summons and areas deal a second: in full (shown, in the totals) and as the score weighs them. */
    record Summons(double shown, double scored) {}

    /**
     * The tree's summons and areas for one evaluation (the overlay in force), or without a tree
     * the class profile's summons (rules.model.summonNeutral … times summonHitsPerSecond and
     * passiveDpsFactor). {@code byElement}, when given, gets their DPS per element and
     * {@code out} one entry each.
     */
    static Summons summons(TreeModel.Overlay o, Map<String, Double> s, Map<String, Double> shares, double baseDps,
            double[] skillBonus, double multiplier, double[] byElement, List<Source> out) {
        double shown = 0;
        double scored = 0;
        double[] el = byElement == null ? null : new double[Damage.ELEMENTS.length];
        if (o != null && o.passives != null) {
            for (Passive p : o.passives) {
                if (el != null) {
                    java.util.Arrays.fill(el, 0);
                }
                double hit = Damage.hit(s, Damage.split(p.conv(), shares), baseDps, skillBonus, true, el)
                        * multiplier * p.mult();
                double times = p.rate() * p.count();
                double dps = hit * times;
                shown += dps;
                scored += dps * p.share();
                add(byElement, el, multiplier * p.mult() * times);
                if (out != null) {
                    out.add(new Source(p.name(), p.summon() ? "summon" : "area", p.from(), dps, hit, p.rate(), p.count(),
                            0, 0, 0, 0, List.of()));
                }
            }
            return new Summons(shown, scored);
        }
        double factor = Damage.model("passiveDpsFactor", 0);
        if (factor <= 0) {
            return new Summons(0, 0);
        }
        double[] pc = {Damage.model("summonNeutral", 0.16), Damage.model("summonEarth", 0.02),
            Damage.model("summonThunder", 0), Damage.model("summonWater", 0), Damage.model("summonFire", 0),
            Damage.model("summonAir", 0.02)};
        double hit = Damage.hit(s, Damage.split(pc, shares), baseDps, skillBonus, true, el) * multiplier;
        double times = Damage.model("summonHitsPerSecond", 6) * factor;
        shown = hit * times;
        add(byElement, el, multiplier * times);
        if (out != null) {
            out.add(new Source("Summons", "other", null, shown, hit, times, 1, 0, 0, 0, 0, List.of()));
        }
        return new Summons(shown, shown * fightShare(true));
    }

    private static void add(double[] into, double[] el, double scale) {
        if (into != null && el != null) {
            for (int i = 0; i < into.length; i++) {
                into[i] += el[i] * scale;
            }
        }
    }

    // ---- the whole list -----------------------------------------------------------------------

    /**
     * What one evaluation needs for the list: the build's stats, the weapon's element shares and
     * base DPS, the skill bonuses per element, Strength and crit's multiplier, casts per 5 s, main
     * attack hits a second, max health and the summons' entries ({@link #summons}).
     */
    record Inputs(Map<String, Double> stats, Map<String, Double> shares, double baseDps, double[] skillBonus,
            double multiplier, double casts, double mainHits, double health, List<Source> summons,
            Healing.Result healing) {

        Inputs(Map<String, Double> stats, Map<String, Double> shares, double baseDps, double[] skillBonus,
                double multiplier, double casts, double mainHits, double health, List<Source> summons) {
            this(stats, shares, baseDps, skillBonus, multiplier, casts, mainHits, health, summons, null);
        }
    }

    /** Fills {@code parts.sources()} for one evaluation, with the tree overlay in force. */
    static void fill(Damage.Breakdown parts, Inputs in) {
        List<Source> out = parts.sources();
        double shareSum = 0;
        for (Damage.SpellPart sp : parts.spells()) {
            if (!sp.lasting()) {
                shareSum += sp.share();
            }
        }
        Map<String, Double> castRate = new TreeMap<>();
        Map<String, JsonObject> entries = spellEntries();
        for (Damage.SpellPart sp : parts.spells()) {
            JsonObject e = entries.get(sp.name());
            double perCast = sp.perCast();
            double count = num(e, "perCount", 1);
            // a lasting spell is cast once per Totem every time it runs out
            double rate = sp.lasting() ? count / sp.duration() : shareSum > 0 ? sp.share() / shareSum * in.casts() / 5.0 : 0;
            castRate.put(sp.name(), rate);
            double waves = num(e, "waves", 1);
            // every hit of the cast: all ticks of every Totem, every wave from every Totem
            double hits = num(e, "hits", 0);
            List<Part> ps = new ArrayList<>();
            if (sp.lasting()) {
                // a Totem: a tick, one Totem's ticks a second, every Totem's
                double perTotem = perCast / count / sp.duration();
                if (hits > 0) {
                    ps.add(new Part("Tick", perCast / hits));
                }
                if (count > 1) {
                    ps.add(new Part("Tick DPS (each)", perTotem));
                }
                ps.add(new Part("Tick DPS", perCast / sp.duration()));
                out.add(new Source(sp.name(), "lasting", null, perCast / sp.duration(),
                        hits > 0 ? perCast / hits : 0, hits > 0 ? hits / count / sp.duration() : 0, count, perCast, rate,
                        0, 0, ps));
            } else {
                // Aura: one wave, the first wave from every Totem, all its waves
                if (count > 1 || waves > 1) {
                    ps.add(new Part("Single Wave", perCast / count / waves));
                    if (count > 1) {
                        ps.add(new Part("First Wave", perCast / waves));
                    }
                    ps.add(new Part("Total Damage", perCast));
                }
                out.add(new Source(sp.name(), "spell", null, perCast * rate, hits > 0 ? perCast / hits : 0, 0, count,
                        perCast, rate, 0, 0, ps));
            }
        }
        double main = parts.mainTotal();
        out.add(new Source("Main Attack", "main", null, main, in.mainHits() > 0 ? main / in.mainHits() : 0,
                in.mainHits(), 1, 0, 0, 0, 0, List.of()));
        out.addAll(in.summons());
        TreeModel.Overlay o = TreeModel.active();
        if (o != null && o.heals != null) {
            double efficiency = Math.max(0, 1 + in.stats().getOrDefault("healingEfficiency", 0.0) / 100.0);
            Healing.Result healing = in.healing();
            for (SourceText.Heal h : o.heals) {
                // regeneration (Sunflare's) is not raised by Healing Efficiency
                double heal = h.fraction() * in.health() * (h.efficiency() ? efficiency : 1);
                // a heal naming no spell has no casts to go by: its own clock, else none a second;
                // the heal cast as often as the mana pays for it (Healing)
                double casts = h.spell() == null ? 0
                        : healing != null && h.spell().equals(healing.spell()) ? healing.casts()
                        : castRate.getOrDefault(h.spell(), 0.0);
                double perSecond = h.every() > 0 ? heal / h.every() : heal * casts;
                out.add(new Source(h.name(), "heal", h.spell(), 0, 0, h.every() > 0 ? 1 / h.every() : 0, 1, 0,
                        casts, heal, perSecond, List.of()));
            }
        }
        if (o != null && o.ultimates != null) {
            for (SourceText.Ultimate u : o.ultimates) {
                double hit = Damage.hit(in.stats(), Damage.split(u.conv(), in.shares()), in.baseDps(), in.skillBonus(),
                        true, null) * in.multiplier();
                out.add(new Source(u.name(), "ultimate", null, hit * u.rate(), hit, u.rate(), 1, 0, 0, 0, 0, List.of()));
            }
        }
    }

    private static double num(JsonObject e, String key, double fallback) {
        return e != null && e.has(key) ? e.get(key).getAsDouble() : fallback;
    }

    /** rules.model.spells as the overlay in force has them, by name. */
    private static Map<String, JsonObject> spellEntries() {
        Map<String, JsonObject> out = new TreeMap<>();
        JsonElement el = Rules.get().has("model") ? Rules.get().getAsJsonObject("model").get("spells") : null;
        if (el == null || !el.isJsonArray()) {
            return out;
        }
        for (JsonElement e : TreeModel.spells(el.getAsJsonArray())) {
            if (e.isJsonObject() && e.getAsJsonObject().has("name")) {
                out.put(e.getAsJsonObject().get("name").getAsString(), e.getAsJsonObject());
            }
        }
        return out;
    }

    // ---- for the /damage route ----------------------------------------------------------------

    static JsonArray json(List<Source> sources) {
        JsonArray a = new JsonArray();
        for (Source s : sources) {
            JsonObject j = new JsonObject();
            j.addProperty("name", s.name());
            j.addProperty("kind", s.kind());
            if (s.from() != null) {
                j.addProperty("from", s.from());
            }
            j.addProperty("counted", s.counted());
            j.addProperty("dps", r1(s.dps()));
            put(j, "perHit", s.perHit());
            put(j, "hitsPerSecond", r2(s.hitsPerSecond()));
            if (s.count() != 1) {
                j.addProperty("count", s.count());
            }
            put(j, "perCast", s.perCast());
            put(j, "castsPerSecond", r2(s.castsPerSecond()));
            put(j, "heal", s.heal());
            put(j, "healPerSecond", s.healPerSecond());
            if (!s.parts().isEmpty()) {
                JsonArray ps = new JsonArray();
                for (Part p : s.parts()) {
                    JsonObject pj = new JsonObject();
                    pj.addProperty("name", p.name());
                    pj.addProperty("value", r1(p.value()));
                    ps.add(pj);
                }
                j.add("parts", ps);
            }
            a.add(j);
        }
        return a;
    }

    private static void put(JsonObject j, String key, double v) {
        if (v != 0) {
            j.addProperty(key, Math.abs(v) < 10 ? r2(v) : r1(v));
        }
    }

    private static double r1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private static double r2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
