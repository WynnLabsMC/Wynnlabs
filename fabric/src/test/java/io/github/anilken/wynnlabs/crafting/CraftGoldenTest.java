package io.github.anilken.wynnlabs.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Recipes players share on WynnBuilder, as the targets our suggestions aim for. Each is read
 * from its crafter link, crafted (numbers as WynnBuilder shows them; where its floating point
 * floors one lower, the exact value), and then searched for with the default settings of the
 * goal it serves: the suggestion must keep that goal's durability floor and caps and score at
 * least as much as the shared recipe. The ingredients are the game's (October 2026), trimmed
 * to what the calculator reads.
 */
@Tag("heavy")
class CraftGoldenTest {

    /** A shared recipe: its link's hash, the goal set it serves, and its craft as compared. */
    record Golden(String hash, String goal, String craft) {}

    static final List<Golden> GOLDENS = List.of(
            new Golden("4OaSOWnqUio8PSe81", "rawspell", "eff [115, -15, 0, 340, 175, 170] req [0, 48, 51, 61, 0] hp 4137..4205 dur 103..109 | gatherXpBonus 10..13 healthRegenRaw 171..196 manaRegen 10..10 rawDefence 8..10 rawDexterity 10..13 rawFireSpellDamage 70..78 rawSpellDamage 170..195"),
            new Golden("4mama4X4X4X4XCf81", "rawmelee", "eff [0, 0, 160, 160, 160, 160] req [0, 88, 0, 0, 0] hp - dur 99..105 | rawMainAttackDamage 160..192 thunderDamage 16..24"),
            new Golden("48g8gKQKQKZOQ4e81", "poison", "eff [125, 125, 175, 175, 200, 200] req [106, 130, 0, 0, 0] hp 4137..4205 dur 42..48 | mainAttackDamage 22..46 poison 2100..2274 rawAttackSpeed 2..2 spellDamage 14..14"),
            // WynnBuilder: health -4408..-3818 (its floating point floors -650 × 2.7 to -1756)
            new Golden("4KZaZWcOnWnGaSe81", "thunder", "eff [100, 85, 130, 100, 115, 270] req [116, 122, 0, -11, 0] hp 4137..4205 dur 111..117 | damage 19..19 earthDamage 43..48 fireDefence -13..-11 mainAttackDamage -10..0 rawAttackSpeed 1..1 rawHealth -4408..-3817 rawNeutralDamage -46..-46 rawStrength 5..7 thunderDamage 43..51"),
            new Golden("4mama8e8e8e8eCfa0", "thunder", "eff [0, 0, 160, 160, 160, 160] req [0, 32, 0, 0, 0] hp - dur 130..135 | gatherXpBonus 4..4 rawSpellDamage 32..32 thunderDamage 16..24"),
            // WynnBuilder: hp 3632..3779 (2595 × 1.4 floored in floating point)
            new Golden("48gaZ0X0X0X0Xqd81", "health", "eff [115, 125, 140, 165, 165, 165] req [0, 0, -77, 100, 0] hp 3633..3780 dur 126..136 | rawHealth 6985..7301 rawIntelligence -58..-46 walkSpeed -46..-34"),
            // WynnBuilder: hp 3632..3779, health 6439..6899
            new Golden("4unOnedSdyaSdCe81", "health", "eff [60, -460, 50, -100, 60, 60] req [-166, 0, -6, 0, 0] hp 3633..3780 dur 144..154 | damage -88..-88 earthDefence 14..16 rawHealth 6440..6900 rawSpellDamage -24..-21 waterDefence 14..16"),
            new Golden("44XOc4X4X4X4Xafa0", "rawmelee", "eff [125, 100, 125, 125, 125, 125] req [0, 90, 0, 0, 0] hp - dur 152..157 | rawMainAttackDamage 155..185 thunderDamage 15..25"),
            new Golden("4KZaZCQqsWnGb4ea0", "mana", "eff [100, 45, 130, 60, 75, 317] req [0, 116, 0, -7, -12] hp 3693..3755 dur 149..154 | mainAttackDamage -20..-15 manaSteal 21..25 rawAttackSpeed 1..1 rawHealth -488..-432 rawMainAttackDamage 91..104 rawNeutralDamage -30..-30 rawThunderDamage 15..18 walkSpeed 22..31"),
            new Golden("4OayaWnufSt8PSe81", "fire", "eff [115, -15, 0, 315, 175, 145] req [47, 0, 42, 95, 0] hp 4137..4205 dur 132..138 | earthDefence -5..-4 fireDamage 44..53 manaRegen 8..8 rawHealth -1733..-1575 rawSpellDamage 58..65 rawStrength 8..8 reflection 24..28 spellDamage 12..14 waterDefence -5..-4"),
            new Golden("4mamaWJWJWJWJCfa0", "rawspell", "eff [0, 0, 160, 160, 160, 160] req [0, 0, 0, 52, 0] hp - dur 154..159 | fireDamage 16..16 gatherXpBonus 4..4 rawSpellDamage 76..96"),
            new Golden("4mamaWJWJWJWJyfa0", "rawspell", "eff [0, 0, 160, 160, 160, 160] req [0, 0, 0, 52, 0] hp - dur 154..159 | fireDamage 16..16 gatherXpBonus 4..4 rawSpellDamage 76..96"));

    /**
     * A search time far above what a full search takes here: the search runs to the end, so its
     * answer is the same on any machine. A short slice stops it where the machine's speed says,
     * and a slow or busy runner then gets a weaker answer.
     */
    static final long NO_DEADLINE_MS = 600_000;

    private static volatile List<Ingredient> all;

    /** The game's ingredients from the test resource, by the calculator's record. */
    static List<Ingredient> ingredients() {
        List<Ingredient> a = all;
        if (a != null) {
            return a;
        }
        List<Ingredient> out = new ArrayList<>();
        try (var in = CraftGoldenTest.class.getResourceAsStream("/crafting/ingredients.json")) {
            JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : doc.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                Map<String, Ingredient.Stat> ids = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> id : o.getAsJsonObject("i").entrySet()) {
                    JsonArray r = id.getValue().getAsJsonArray();
                    ids.put(id.getKey(), new Ingredient.Stat(r.get(0).getAsInt(), r.get(1).getAsInt(), id.getKey(), ""));
                }
                List<String> profs = new ArrayList<>();
                o.getAsJsonArray("p").forEach(p -> profs.add(p.getAsString()));
                out.add(new Ingredient(e.getKey(), o.get("t").getAsInt(), o.get("l").getAsInt(), List.copyOf(profs), ids,
                        ints(o.getAsJsonArray("pos")), o.get("d").getAsInt(), o.get("c").getAsInt(), o.get("du").getAsInt(),
                        ints(o.getAsJsonArray("r"))));
            }
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        all = List.copyOf(out);
        return all;
    }

    private static int[] ints(JsonArray a) {
        int[] v = new int[a.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = a.get(i).getAsInt();
        }
        return v;
    }

    static Map<String, Ingredient> byName() {
        Map<String, Ingredient> m = new HashMap<>();
        ingredients().forEach(i -> m.put(i.name(), i));
        return m;
    }

    static List<Ingredient> slots(RecipeCode.Recipe r, Map<String, Ingredient> by) {
        List<Ingredient> s = new ArrayList<>();
        for (String n : r.slots()) {
            s.add(n == null ? Ingredient.NONE : by.get(n));
        }
        return s;
    }

    /** A craft as compared: effectiveness, requirements, health, durability and the identifications by name. */
    static String show(Craft.Result r) {
        StringBuilder ids = new StringBuilder();
        new TreeMap<>(r.ids()).forEach((k, v) -> ids.append(ids.isEmpty() ? "" : " ").append(k).append(' ')
                .append(v.min()).append("..").append(v.max()));
        return "eff " + Arrays.toString(r.effectiveness()) + " req " + Arrays.toString(r.requirements())
                + " hp " + (r.health() == null ? "-" : r.health().min() + ".." + r.health().max())
                + " dur " + r.durability().min() + ".." + r.durability().max() + " | " + ids;
    }

    /** The default goal set for a key at a recipe's level, no skill points known. */
    static Suggestions.GoalSet set(String key, CraftData.Recipe r) {
        return Suggestions.goalSets(List.of(key), r.maxLevel(), null).get(0);
    }

    static CraftSearch.Goal goal(String key, CraftData.Recipe r) {
        return set(key, r).goal();
    }

    @Test
    void sharedRecipesCraftAsOnWynnBuilder() {
        Map<String, Ingredient> by = byName();
        for (Golden g : GOLDENS) {
            RecipeCode.Recipe rc = WynnBuilderCode.decode(WynnBuilderCode.LINK + g.hash());
            assertNotNull(rc, g.hash());
            CraftData.Recipe rec = CraftData.find(rc.type(), rc.minLevel());
            Craft.Result res = Craft.craft(rec, rc.tier1(), rc.tier2(), rc.speed(), slots(rc, by));
            assertEquals(g.craft(), show(res), g.hash());
            assertEquals(WynnBuilderCode.LINK + g.hash(), WynnBuilderCode.link(rc), "the link goes back the same way");
            assertTrue(WynnBuilderCode.looksLike(WynnBuilderCode.LINK + g.hash()), "a pasted link is read as WynnBuilder's");
        }
    }

    @Test
    void defaultGoalsReachEverySharedRecipe() {
        Map<String, Ingredient> by = byName();
        StringBuilder report = new StringBuilder();
        for (Golden g : GOLDENS) {
            RecipeCode.Recipe rc = WynnBuilderCode.decode(g.hash());
            CraftData.Recipe rec = CraftData.find(rc.type(), rc.minLevel());
            List<Ingredient> gs = slots(rc, by);
            Craft.Result gr = Craft.craft(rec, rc.tier1(), rc.tier2(), rc.speed(), gs);
            CraftSearch.Goal goal = goal(g.goal(), rec);
            assertTrue(goal.allows(gr), g.hash() + " keeps its goal's floor and caps: " + show(gr));
            double golden = goal.score(gs);
            CraftSearch.Request q = new CraftSearch.Request(rec, rc.tier1(), rc.tier2(), Craft.Speed.NORMAL, goal, ingredients(),
                    Map.of(), 132, true, n -> null);
            long t0 = System.nanoTime();
            CraftSearch.Layout l = Suggestions.best(q, CraftSearch.Tier.ANY, 3_000, new CraftSearch.Budget(10_000),
                    set(g.goal(), rec).core());
            long ms = (System.nanoTime() - t0) / 1_000_000;
            assertNotNull(l, g.hash());
            assertTrue(goal.allows(l.result()), g.hash() + " suggestion within the goal: " + show(l.result()));
            assertTrue(l.score() >= golden - 1e-9, g.hash() + " " + g.goal() + ": " + l.score() + " < shared " + golden
                    + " with " + names(l));
            boolean same = sorted(l.slots()).equals(sorted(gs));
            report.append(String.format("%s %-9s %-16s shared %.1f ours %.1f %s %dms %s%n", g.hash(), g.goal(), rec.id(),
                    golden, l.score(), same ? "SAME" : "BEATS", ms, same ? "" : names(l) + " -> " + show(l.result())));
        }
        System.out.println(report);
    }

    @Test
    void defaultSuggestionsKeepDurabilityAndCaps() {
        List<String> types = new ArrayList<>(Types.ARMOUR);
        types.addAll(Types.ACCESSORIES);
        WhatCanICraft.Stock stock = new WhatCanICraft.Stock(Map.of(), Map.of(), Map.of());
        List<Suggestions.GoalSet> goals = Suggestions.goalSets(Suggestions.order(true, null), 119, null);
        long t0 = System.nanoTime();
        List<Suggestions.Suggestion> out = Suggestions.run(types, goals, 119, stock, ingredients(), n -> null, 150,
                new CraftSearch.Budget(120_000), null);
        System.out.println("suggestions at Lv 119: " + out.size() + " in " + (System.nanoTime() - t0) / 1_000_000 + " ms");
        assertTrue(out.size() > 40, "plenty: " + out.size());
        for (Suggestions.Suggestion s : out) {
            Craft.Result r = s.layout().result();
            int floor = Math.min(s.goal().goal().durabilityFloor(), Suggestions.FALLBACK_FLOOR);
            assertTrue(r.durability().min() >= floor, s.goal().key() + " " + s.recipe().id() + " durability " + r.durability());
            int sum = 0;
            for (int k = 0; k < 5; k++) {
                assertTrue(r.requirements()[k] <= Suggestions.CAP, s.goal().key() + " " + s.recipe().id() + " "
                        + Arrays.toString(r.requirements()));
                sum += r.requirements()[k];
            }
            assertTrue(sum <= Suggestions.TOTAL_CAP, s.goal().key() + " " + s.recipe().id() + " " + Arrays.toString(r.requirements()));
        }
    }

    /**
     * Boots of raw melee bought with everything else (Giant Teratoma ×3, Zealous Illuminator ×2,
     * Negative Rafflesia): 764-891 raw melee for -1265..-1102 health, -49..-42 % health regen,
     * -40 neutral damage and 129 strength. Players pass on it; the raw melee goal must too.
     */
    @Test
    void rawMeleeBootsDoNotTradeAwayEverything() {
        Map<String, Ingredient> by = byName();
        RecipeCode.Recipe rc = WynnBuilderCode.decode("4ukWpWnukukWpqe81");
        CraftData.Recipe rec = CraftData.find(rc.type(), rc.minLevel());
        List<Ingredient> heavy = slots(rc, by);
        Suggestions.GoalSet g = set("rawmelee", rec);
        CraftSearch.Request q = new CraftSearch.Request(rec, 3, 3, Craft.Speed.NORMAL, g.goal(), ingredients(), Map.of(), 132, true,
                n -> null);
        CraftSearch.Layout l = Suggestions.best(q, CraftSearch.Tier.ANY, 3_000, new CraftSearch.Budget(10_000), g.core());
        System.out.println("boots raw melee: heavy " + g.goal().score(heavy) + ", ours "
                + (l == null ? "none" : l.score() + " " + names(l) + " -> " + show(l.result())));
        if (l != null) {
            // something else, or the same idea with far smaller losses
            Craft.Result r = l.result();
            assertTrue(!sorted(l.slots()).equals(sorted(heavy)), "not the heavy trade: " + show(r));
            Craft.Range hp = r.ids().get("rawHealth");
            Craft.Range regen = r.ids().get("healthRegen");
            assertTrue(hp == null || hp.mean() > -600, "half the health loss at most: " + show(r));
            assertTrue(regen == null || regen.mean() > -35, "less regeneration lost: " + show(r));
            assertTrue(r.requirements()[0] <= 100, "less strength: " + show(r));
            assertTrue(r.ids().get("rawNeutralDamage") == null || r.ids().get("rawNeutralDamage").mean() > -20, show(r));
        }
    }

    private static List<String> sorted(List<Ingredient> slots) {
        return slots.stream().map(Ingredient::name).sorted().toList();
    }

    static List<String> names(CraftSearch.Layout l) {
        return l.slots().stream().map(Ingredient::name).toList();
    }
}
