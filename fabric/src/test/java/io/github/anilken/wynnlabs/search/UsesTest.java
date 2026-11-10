package io.github.anilken.wynnlabs.search;

import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.mounts.MountMaterials;
import io.github.anilken.wynnlabs.mounts.MountStat;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What an item is used for: its crafts from the shipped recipes, an ingredient's professions, mount feed. */
class UsesTest {

    private static String line(Uses.Craft c) {
        return c.type() + " " + c.profession() + " " + c.levels() + " " + c.amount() + "+" + c.otherAmount() + " " + c.other();
    }

    @Test
    void koiOilGoesIntoSixCrafts() {
        Uses.Result r = Uses.of("Koi Oil");
        assertEquals(List.of(
                "potion alchemism 60-69 4+8 Millet Grains",
                "ring jeweling 60-69 4+4 Cobalt Gem",
                "bracelet jeweling 60-69 4+8 Cobalt Gem",
                "necklace jeweling 60-69 4+12 Cobalt Gem",
                "scroll scribing 60-69 4+4 Dark Paper",
                "relik woodworking 60-69 8+4 Dark Planks"), r.crafts().stream().map(UsesTest::line).toList());
        Uses.Craft ring = r.crafts().get(1);
        assertFalse(ring.mixed());
        assertEquals(List.of("60-63", "63-65", "65-67", "67-69"), ring.brackets().stream().map(Uses.Bracket::label).toList());
        assertTrue(r.ingredientProfessions().isEmpty());
        assertFalse(r.empty());
    }

    @Test
    void tierSuffixAndRefinedNameFindTheSameCrafts() {
        List<Uses.Craft> plain = Uses.of("Koi Oil").crafts();
        assertEquals(plain, Uses.of("Koi Oil T1").crafts());
        assertEquals(plain, Uses.of("koi oil t3").crafts());
        assertEquals(plain, Uses.of("Refined Koi Oil").crafts());
    }

    @Test
    void koiMeatGoesIntoFood() {
        List<Uses.Craft> crafts = Uses.of("Koi Meat").crafts();
        assertEquals(List.of("food cooking 60-69 8+4 Millet Grains"), crafts.stream().map(UsesTest::line).toList());
    }

    @Test
    void mountFeedIsAnOtherUse() {
        Uses.Result r = Uses.of("Koi Oil T2");
        assertEquals(1, r.other().size());
        Uses.MountFeed f = assertInstanceOf(Uses.MountFeed.class, r.other().get(0));
        assertEquals("Koi Oil", f.material().name());
        assertEquals(List.of(MountStat.HANDLING, MountStat.TRAINING, MountStat.ALTITUDE), f.stats());
        assertEquals(List.of(15, 9, 4), f.points());
    }

    @Test
    void planksMatchTheMountsPlank() {
        Uses.Result r = Uses.of("Dark Planks");
        assertTrue(r.crafts().stream().anyMatch(c -> c.type().equals("relik") && "Koi Oil".equals(c.other())));
        assertEquals("Dark Plank", ((Uses.MountFeed) r.other().get(0)).material().name());
    }

    @Test
    void anItemWithNoUseIsEmpty() {
        Uses.Result r = Uses.of("Nirvana");
        assertTrue(r.empty());
        assertTrue(Uses.of("").empty());
        assertTrue(Uses.of((String) null).empty());
    }

    @Test
    void ingredientListsItsProfessions() {
        Entry e = new Entry("Ancient Currency", Kind.INGREDIENT, "ingredient", 60, null, 2, Klass.NONE, Map.of(), null,
                null, null, new Entry.Icon(null, Float.NaN, null, null), List.of(), List.of(),
                List.of("WOODWORKING", "jeweling", "Woodworking"), Map.of(), null);
        Uses.Result r = Uses.of(e);
        assertEquals(List.of("woodworking", "jeweling"), r.ingredientProfessions());
        assertTrue(r.crafts().isEmpty());
        assertFalse(r.empty());
    }

    @Test
    void mixedBracketsAreMarked() {
        List<CraftData.Recipe> recipes = List.of(
                recipe("Ring1-3", 1, 3, 2, "Copper Gem", 1),
                recipe("Ring3-5", 3, 5, 3, "Copper Gem", 1),
                recipe("Ring5-7", 5, 7, 3, "Granite Gem", 2));
        Uses.Result r = Uses.of("Oak Oil", recipes, List.of(), MountMaterials.all());
        Uses.Craft c = r.crafts().get(0);
        assertTrue(c.mixed());
        assertEquals("1-7", c.levels());
        assertEquals(2, c.amount());
        assertEquals("Copper Gem", c.other());
        assertEquals("Granite Gem", c.brackets().get(2).other());
        // 12 oil, 3 copper, 3 granite: 6, 3, then 1 craft (granite runs out); the craft says the least
        assertEquals(3, Uses.craftsWith(12, 3, c.brackets().get(1)));
        assertEquals(1, Uses.craftsWith(12, 3, c.brackets().get(2)));
        assertEquals(1, Uses.craftsWith(12, 3, c));
    }

    @Test
    void craftsWithCountsTheScarcerMaterial() {
        Uses.Craft necklace = Uses.of("Koi Oil").crafts().stream().filter(c -> c.type().equals("necklace")).findFirst().orElseThrow();
        assertEquals(2, Uses.craftsWith(10, 30, necklace));     // 4 oil + 12 gems each: oil allows 2, gems 2
        assertEquals(1, Uses.craftsWith(40, 12, necklace));
        assertEquals(0, Uses.craftsWith(3, 100, necklace));
        assertEquals(0, Uses.craftsWith(-5, -5, necklace));
    }

    @Test
    void keyDropsRefinedTierAndPlural() {
        assertEquals("koi oil", Uses.key("Refined Koi Oil T3"));
        assertEquals("dark plank", Uses.key("Dark Planks"));
        assertNull(Uses.of("Koi Oil").crafts().stream().filter(c -> c.other() == null).findFirst().orElse(null));
    }

    private static CraftData.Recipe recipe(String id, int lo, int hi, int amount, String other, int otherAmount) {
        return new CraftData.Recipe(id, "ring", "jeweling", lo, hi,
                List.of(new CraftData.Material("Refined Oak Oil", "Oak Oil", amount),
                        new CraftData.Material("Refined " + other, other, otherAmount)),
                null, null, null, null, 0);
    }
}
