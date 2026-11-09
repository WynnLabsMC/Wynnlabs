package io.github.anilken.wynnlabs.crafting;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The crafted item finder: an item crafted from a known layout is turned into what its tooltip
 * would show (a value inside each range), and the search must find that layout, or one with the
 * very same craft, as an exact match. An item no layout can make gets the closest ones.
 */
class HowMadeTest {

    private static final long BUDGET_MS = 8_000;

    private static Map<String, Ingredient> by() {
        return CraftGoldenTest.byName();
    }

    private static List<Ingredient> slots(String... names) {
        Map<String, Ingredient> by = by();
        Ingredient[] out = new Ingredient[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = names[i] == null ? Ingredient.NONE : by.get(names[i]);
            assertNotNull(out[i], names[i]);
        }
        return Craft.slots(out);
    }

    /** What the item shows: each identification at a point inside its range ({@code at}: 0 the low end, 1 the high end). */
    private static HowMade.Target target(String type, CraftData.Recipe recipe, Craft.Result r, double at) {
        List<HowMade.Stat> ids = new ArrayList<>();
        r.ids().forEach((k, range) -> {
            int v = (int) Math.round(range.min() + (range.max() - range.min()) * at);
            if (v != 0) {
                ids.add(new HowMade.Stat(k, k, "", v));
            }
        });
        boolean consumable = Types.consumable(type);
        int[] req = null;
        if (!consumable) {
            req = new int[5];
            for (int s = 0; s < 5; s++) {
                req[s] = Math.max(0, r.requirements()[s]);
            }
        }
        return new HowMade.Target("Test", type, recipe.maxLevel(), ids, consumable ? r.charges() : -1,
                consumable ? mid(r.duration()) : -1, consumable ? -1 : mid(r.durability()),
                r.health() == null || consumable ? -1 : mid(r.health()), req);
    }

    private static int mid(Craft.Range r) {
        return r == null ? -1 : (r.min() + r.max()) / 2;
    }

    /** Same craft: identifications, requirements, charges and the duration or durability range. */
    private static boolean same(Craft.Result a, Craft.Result b) {
        return a.ids().equals(b.ids()) && java.util.Arrays.equals(a.requirements(), b.requirements())
                && a.charges() == b.charges() && java.util.Objects.equals(a.duration(), b.duration())
                && java.util.Objects.equals(a.durability(), b.durability());
    }

    private static void findsIt(String type, String recipeId, int t1, int t2, List<Ingredient> layout, double at) {
        CraftData.Recipe recipe = CraftData.byId(recipeId);
        assertNotNull(recipe, recipeId);
        Craft.Result made = Craft.craft(recipe, t1, t2, Craft.Speed.NORMAL, layout);
        HowMade.Target t = target(type, recipe, made, at);
        assertEquals(recipe, HowMade.recipe(type, t.level()), "the recipe by level");
        assertTrue(HowMade.rows(t, recipe, made).stream().allMatch(HowMade.Row::ok), "the item fits its own layout");
        long t0 = System.nanoTime();
        HowMade.Answer a = HowMade.solve(t, CraftGoldenTest.ingredients(), n -> null, BUDGET_MS);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertFalse(a.layouts().isEmpty(), recipeId + ": nothing found");
        HowMade.Found best = a.best();
        System.out.println(recipeId + " " + a.verdict() + " exact " + a.exactCount() + " complete " + a.complete() + " in "
                + ms + " ms; best " + best.names() + " T" + best.tier1() + "/T" + best.tier2());
        assertTrue(best.exact(), recipeId + ": best misses " + best.rows().stream().filter(r -> !r.ok()).toList());
        assertTrue(a.verdict() == HowMade.Verdict.EXACT || a.verdict() == HowMade.Verdict.POSSIBLE);
        for (HowMade.Found f : a.layouts()) {
            if (f.exact()) {
                Craft.Result again = Craft.craft(f.recipe(), f.tier1(), f.tier2(), Craft.Speed.NORMAL, f.slots());
                assertTrue(HowMade.rows(t, recipe, again).stream().allMatch(HowMade.Row::ok), "an exact layout really fits");
            }
        }
        boolean found = a.layouts().stream().anyMatch(f -> same(f.result(), made));
        assertTrue(found || a.verdict() == HowMade.Verdict.POSSIBLE,
                recipeId + ": neither the layout nor one with its craft among " + a.layouts().stream().map(HowMade.Found::names).toList());
    }

    @Test
    void scroll() {
        findsIt("scroll", "Scroll80-83", 2, 1,
                slots("Essence Of Dusk", "Essence Of Dusk", "Fairy Dust", "Magic Feather"), 0.0);
    }

    @Test
    void potion() {
        findsIt("potion", "Potion57-59", 3, 3, slots("Poison Sac", "Cursed Venom Sac", "Death Whistle Leaf"), 1.0);
    }

    @Test
    void helmetWithPositionEffects() {
        // Zealous Illuminator lifts the slots it touches (right of it and under it) by 25 %
        findsIt("helmet", "Helmet97-99", 1, 2,
                slots("Zealous Illuminator", "Tough Skin", "Stone Plating", null, null, "Cyclops Eye"), 0.5);
    }

    /** Recipes players share, as crafted items: those with at most {@link HowMade#MAX_METAS} position-effect ingredients are found. */
    @Test
    void sharedRecipes() {
        Map<String, Ingredient> by = by();
        for (CraftGoldenTest.Golden g : CraftGoldenTest.GOLDENS) {
            RecipeCode.Recipe rc = WynnBuilderCode.decode(g.hash());
            CraftData.Recipe rec = CraftData.find(rc.type(), rc.minLevel());
            List<Ingredient> layout = CraftGoldenTest.slots(rc, by);
            long metas = layout.stream().filter(Ingredient::meta).count();
            if (metas > HowMade.MAX_METAS) {
                continue;
            }
            findsIt(rc.type(), rec.id(), rc.tier1(), rc.tier2(), layout, 0.5);
        }
    }

    @Test
    void unreachableGetsTheClosest() {
        CraftData.Recipe recipe = CraftData.byId("Scroll80-83");
        HowMade.Target t = new HowMade.Target("Test", "scroll", recipe.maxLevel(),
                List.of(new HowMade.Stat("rawSpellDamage", "Spell Damage", "", 900)), 3, 600, -1, -1, null);
        HowMade.Answer a = HowMade.solve(t, CraftGoldenTest.ingredients(), n -> null, 3_000);
        assertEquals(HowMade.Verdict.CLOSEST, a.verdict());
        HowMade.Found best = a.best();
        assertNotNull(best);
        assertTrue(best.result().ids().containsKey("rawSpellDamage"), "the closest still brings spell damage: " + best.names());
    }

    @Test
    void noRecipeForTheLevel() {
        HowMade.Target t = new HowMade.Target("Test", "scroll", 500, List.of(), 3, 100, -1, -1, null);
        assertEquals(HowMade.Verdict.NONE, HowMade.solve(t, CraftGoldenTest.ingredients(), n -> null, 500).verdict());
    }

    @Test
    void costsCountWhatHasAPrice() {
        CraftData.Recipe recipe = CraftData.byId("Scroll80-83");
        List<Ingredient> l = slots("Essence Of Dusk", "Fairy Dust");
        HowMade.Cost c = HowMade.ingredientsCost(l, n -> n.equals("Fairy Dust") ? 500L : null);
        assertEquals(new HowMade.Cost(500, 1, 1), c);
        HowMade.Cost m = HowMade.materialsCost(recipe, 1, 2, n -> n.equals("Bass Oil T1") ? 10L : null);
        assertEquals(new HowMade.Cost(50, 1, 1), m);
    }
}
