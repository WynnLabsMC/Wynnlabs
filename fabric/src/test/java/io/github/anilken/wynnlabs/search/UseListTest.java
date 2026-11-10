package io.github.anilken.wynnlabs.search;

import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.mounts.MountMaterials;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The uses of an item as list lines and their keys: order, the bracket shown, the crafts the stock allows. */
class UseListTest {

    private static final Map<String, Long> STOCK = Map.of("Koi Oil", 24L, "Cobalt Gem", 26L, "Dark Planks", 12L);

    private static List<UseList.Line> koiOil(int level) {
        return UseList.of(Uses.of("Koi Oil"), n -> STOCK.getOrDefault(n, 0L), p -> level);
    }

    @Test
    void aKeyIsItsItemAndType() {
        assertEquals("use:Koi Oil:ring", UseKey.of("Koi Oil", "ring"));
        UseKey k = UseKey.parse("use:Koi Oil:ring");
        assertEquals("Koi Oil", k.item());
        assertEquals("ring", k.type());
        assertEquals("use:Koi Oil:ring", k.key());
        assertFalse(k.feed());
        assertNull(k.profession());
        assertTrue(UseKey.parse("use:Koi Oil:feed").feed());
        assertEquals("armouring", UseKey.parse("use:Lucky Rabbit's Foot:prof-armouring").profession());
    }

    @Test
    void anItemNameMayHoldAColon() {
        UseKey k = UseKey.parse("use:Relic: Shard:scroll");
        assertEquals("Relic: Shard", k.item());
        assertEquals("scroll", k.type());
    }

    @Test
    void otherKeysAreNotUses() {
        assertFalse(UseKey.isUse("Koi Oil"));
        assertFalse(UseKey.isUse("mob:Tumbleweed"));
        assertFalse(UseKey.isUse(null));
        assertNull(UseKey.parse("mob:Tumbleweed"));
        assertNull(UseKey.parse("use:"));
        assertNull(UseKey.parse("use:Koi Oil"));
        assertNull(UseKey.parse("use:Koi Oil:"));
        assertNull(UseKey.parse("use::ring"));
        assertTrue(UseKey.isUse("use:Koi Oil:ring"));
    }

    @Test
    void koiOilListsItsCraftsThenTheMountFeed() {
        List<UseList.Line> lines = koiOil(0);
        assertEquals(List.of("use:Koi Oil:potion", "use:Koi Oil:ring", "use:Koi Oil:bracelet", "use:Koi Oil:necklace",
                "use:Koi Oil:scroll", "use:Koi Oil:relik", "use:Koi Oil:feed"), lines.stream().map(UseList.Line::key).toList());
        assertEquals(6, UseList.count(lines, UseList.Kind.CRAFT));
        assertEquals(1, UseList.count(lines, UseList.Kind.FEED));
        assertEquals(0, UseList.count(lines, UseList.Kind.PROFESSION));
        UseList.Line feed = UseList.find(lines, "use:Koi Oil:feed");
        assertNotNull(feed.feed());
        assertNull(feed.craft());
        assertNull(UseList.find(lines, "use:Koi Oil:boots"));
    }

    @Test
    void craftsCountBothMaterials() {
        List<UseList.Line> lines = koiOil(0);
        // 24 Koi Oil and 26 Cobalt Gem: a ring takes 4 + 4, a bracelet 4 + 8, a necklace 4 + 12
        assertEquals(6, UseList.find(lines, "use:Koi Oil:ring").crafts());
        assertEquals(3, UseList.find(lines, "use:Koi Oil:bracelet").crafts());
        assertEquals(2, UseList.find(lines, "use:Koi Oil:necklace").crafts());
        // nothing of the other material: none
        assertEquals(0, UseList.find(lines, "use:Koi Oil:potion").crafts());
        assertEquals(0, UseList.find(lines, "use:Koi Oil:scroll").crafts());
        // a relik takes 8 Koi Oil and 4 Dark Planks
        UseList.Line relik = UseList.find(lines, "use:Koi Oil:relik");
        assertEquals(3, relik.crafts());
        assertEquals(8, relik.bracket().amount());
        assertEquals("Dark Planks", relik.bracket().other());
        assertEquals(4, relik.bracket().otherAmount());
        assertEquals("woodworking", relik.profession());
    }

    @Test
    void theBracketFollowsTheProfessionLevel() {
        Uses.Craft ring = Uses.of("Koi Oil").crafts().get(1);
        assertEquals("60-63", UseList.bracket(ring, 0).label());          // not known: the lowest
        assertEquals("60-63", UseList.bracket(ring, 12).label());         // below them all: the lowest
        assertEquals("60-63", UseList.bracket(ring, 60).label());
        assertEquals("65-67", UseList.bracket(ring, 66).label());
        assertEquals("67-69", UseList.bracket(ring, 120).label());
        assertFalse(UseList.reached(UseList.bracket(ring, 0), 0));
        assertFalse(UseList.reached(UseList.bracket(ring, 12), 12));
        assertTrue(UseList.reached(UseList.bracket(ring, 66), 66));
        assertEquals("65-67", UseList.find(koiOil(66), "use:Koi Oil:ring").bracket().label());
    }

    @Test
    void anIngredientListsItsProfessions() {
        Uses.Result r = Uses.of("Lucky Rabbit's Foot", CraftData.all(), List.of("Armouring", "tailoring"), MountMaterials.all());
        List<UseList.Line> lines = UseList.of(r, n -> 0, p -> 0);
        assertEquals(List.of("use:Lucky Rabbit's Foot:prof-armouring", "use:Lucky Rabbit's Foot:prof-tailoring"),
                lines.stream().map(UseList.Line::key).toList());
        assertEquals(UseList.Kind.PROFESSION, lines.get(0).kind());
        assertEquals("armouring", lines.get(0).profession());
        assertEquals("armouring", UseKey.parse(lines.get(0).key()).profession());
    }

    @Test
    void aProfessionCraftsItsTypes() {
        assertEquals(List.of("ring", "bracelet", "necklace"), UseList.types("jeweling", CraftData.all()));
        assertEquals(List.of("potion"), UseList.types("Alchemism", CraftData.all()));
        assertEquals(List.of(), UseList.types("fishing", CraftData.all()));
    }
}
