package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.crafting.TestIngredients;
import io.github.anilken.wynnlabs.crafting.WynnBuilderCode;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The community list's model: the file read (unknown fields ignored, malformed entries skipped),
 * the filters (class with "any" sets, level inside the range, every tag), and an entry compared
 * with what a recorded Shaman has: equipped, in the bag, on a bank page, missing with a cached
 * price and without one, and a crafted weapon owned (a crafted relik of the recipe's level in the
 * bag) or missing (its cost to craft instead of a market price).
 */
class CommunityBuildsTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    private static String entry(String id, String klass, int min, int max, String tags, String extra) {
        return "{\"id\":\"" + id + "\",\"name\":\"" + id + "\",\"link\":\"https://wynnbuilder.github.io/builder/#CZ0\",\"class\":\"" + klass
                + "\",\"levelMin\":" + min + ",\"levelMax\":" + max + ",\"tags\":[" + tags + "],\"description\":\"d\",\"source\":\"forum\","
                + "\"checked\":\"2026-11-04\",\"outdated\":false" + extra + "}";
    }

    @Test
    void parsesAndFilters() {
        String json = "{\"version\":1,\"tags\":[\"raid\",\"spell\"],\"builds\":["
                + entry("mage-a", "mage", 80, 106, "\"raid\",\"spell\"", ",\"stars\":5") + ","
                + entry("mage-low", "mage", 20, 40, "\"spell\"", "") + ","
                + entry("archer-a", "archer", 85, 106, "\"raid\"", "") + ","
                + entry("set-any", "any", 1, 106, "\"xp-bonus\"", "") + ","
                + "{\"id\":\"broken\"},"
                + entry("bad-class", "druid", 1, 5, "\"raid\"", "") + ","
                + entry("mage-a", "mage", 1, 2, "\"raid\"", "")
                + "]}";
        CommunityBuilds.Doc d = CommunityBuilds.parse(json);
        assertNotNull(d);
        assertEquals(4, d.builds().size(), "the unknown field is ignored; the broken, odd-class and repeated entries are skipped");
        assertEquals(3, d.skipped());
        assertNull(CommunityBuilds.parse("not json"));

        List<String> mage85 = CommunityBuilds.filter(d.builds(), Klass.MAGE, 85, List.of()).stream().map(CommunityBuilds.Entry::id).toList();
        assertEquals(List.of("mage-a", "set-any"), mage85, "the class's builds holding level 85, and the sets for every class");
        assertEquals(List.of("archer-a", "set-any"),
                CommunityBuilds.filter(d.builds(), Klass.ARCHER, 90, null).stream().map(CommunityBuilds.Entry::id).toList());
        assertEquals(List.of("mage-a"),
                CommunityBuilds.filter(d.builds(), Klass.MAGE, 85, List.of("raid", "spell")).stream().map(CommunityBuilds.Entry::id).toList(),
                "every tag must be on the entry");
        assertTrue(CommunityBuilds.filter(d.builds(), Klass.SHAMAN, 10, List.of("raid")).isEmpty());
        assertEquals(4, CommunityBuilds.filter(d.builds(), null, 0, List.of()).size());
        assertTrue(d.builds().get(3).hasRole("xp"));
    }

    /** A relik recipe in the highest bracket up to level 44, the genome first, three-star materials. */
    private static RecipeCode.Recipe relik() {
        int lv = CraftData.ofType("relik").stream().mapToInt(CraftData.Recipe::minLevel).filter(l -> l <= 44).max().orElseThrow();
        List<String> slots = Arrays.asList(new String[6]);
        slots.set(0, "Depurinated Genome");
        return new RecipeCode.Recipe("relik", lv, 3, 3, Craft.Speed.FAST, slots);
    }

    private static ItemData pick(GameState s, Slot slot, Set<String> not) {
        return s.allGear().filter(i -> i.slot() == slot && i.level() <= 44 && i.level() >= 10 && !not.contains(i.name())
                        && i.requirements().klass().isEmpty())
                .findFirst().orElseThrow();
    }

    @Test
    void comparesWithWhatThePlayerHas() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        GameState s = GameState.current();
        Set<String> used = new java.util.HashSet<>();
        ItemData helmet = pick(s, Slot.HELMET, used);
        ItemData chest = pick(s, Slot.CHESTPLATE, used);
        ItemData legs = pick(s, Slot.LEGGINGS, used);
        ItemData boots = pick(s, Slot.BOOTS, used);
        ItemData necklace = pick(s, Slot.NECKLACE, used);
        RecipeCode.Recipe recipe = relik();
        List<WynnBuilderLink.Piece> pieces = new ArrayList<>();
        ItemData[] at = {helmet, chest, legs, boots, null, null, null, necklace};
        for (int i = 0; i < at.length; i++) {
            if (at[i] != null) {
                pieces.add(new WynnBuilderLink.Piece(i, WynnBuilderLink.Kind.ITEM, -1, at[i].name(), null, ""));
            }
        }
        pieces.add(new WynnBuilderLink.Piece(WynnBuilderLink.WEAPON, WynnBuilderLink.Kind.CRAFTED, -1, null, null, "",
                WynnBuilderCode.encode(recipe)));
        String link = WynnBuilderExport.link(new WynnBuilderLink.Build(null, false, pieces, List.of(), null, 0, ""), null);
        CommunityBuilds.Entry e = new CommunityBuilds.Entry("shaman-test-44-aaaaaa", "Test", link, "shaman", 40, 106, List.of("spell"),
                "d", "forum", "2026-11-04", false, null);
        assertTrue(CommunityBuilds.hasCrafted(e));
        assertTrue(CommunityBuilds.searchWords(e).contains("Depurinated Genome"), "an ingredient finds the build");

        int lv = CraftData.find("relik", recipe.minLevel()).maxLevel();
        CommunityBuilds.Owned owned = new CommunityBuilds.Owned(Map.of(helmet.name(), 1), Map.of(chest.name(), 1),
                Map.of(legs.name(), 3), Map.of(helmet.name(), 1, chest.name(), 1, legs.name(), 1),
                List.of(new CommunityBuilds.CraftedOwn(Slot.RELIK, lv, CommunityBuilds.Place.BAG, 0)));
        Prices.Price two = new Prices.Price(2 * 4096 * 64, null, null, null, null, 0, null, null);
        CommunityBuilds.Compare c = CommunityBuilds.compare(s, e, owned, n -> n.equals(boots.name()) ? two : null,
                TestIngredients.byName()::get, r -> 1234L, false);
        assertNotNull(c);
        assertEquals(6, c.total());
        assertEquals(4, c.owned(), "helmet equipped, chestplate in the bag, leggings banked, the crafted relik in the bag");
        Map<String, CommunityBuilds.Piece> by = new java.util.HashMap<>();
        c.pieces().forEach(p -> by.put(p.name(), p));
        assertEquals(CommunityBuilds.Place.EQUIPPED, by.get(helmet.name()).place());
        assertEquals(CommunityBuilds.Place.BAG, by.get(chest.name()).place());
        assertEquals(CommunityBuilds.Place.BANK, by.get(legs.name()).place());
        assertEquals(3, by.get(legs.name()).bankPage());
        assertEquals(CommunityBuilds.Place.MISSING, by.get(boots.name()).place());
        assertEquals(2.0 * 4096 * 64, by.get(boots.name()).price(), 0.1, "the cached price");
        assertNull(by.get(necklace.name()).price(), "no price");
        CommunityBuilds.Piece relik = by.get("Crafted Relik");
        assertNotNull(relik, "the crafted weapon named as the game names it");
        assertTrue(relik.crafted());
        assertEquals(CommunityBuilds.Place.BAG, relik.place(), "matched by type and the recipe's level");
        assertEquals(2L * 4096 * 64, c.knownCost(), "the known total: the boots");
        assertEquals(1, c.unpriced());
        assertEquals(5, c.need().length);

        // without the crafted relik: missing, its cost to craft in the total, no market price
        CommunityBuilds.Owned none = new CommunityBuilds.Owned(Map.of(), Map.of(), Map.of(), Map.of(), List.of());
        CommunityBuilds.Compare m = CommunityBuilds.compare(s, e, none, n -> null, TestIngredients.byName()::get, r -> 1234L, false);
        CommunityBuilds.Piece missing = m.pieces().stream().filter(CommunityBuilds.Piece::crafted).findFirst().orElseThrow();
        assertEquals(CommunityBuilds.Place.MISSING, missing.place());
        assertNull(missing.price());
        assertEquals(1234L, missing.craftCost());
        assertEquals(1234L, m.knownCost());
        assertEquals(0, m.owned());
        assertFalse(m.kept().isEmpty());

        // the fixture's own snapshot reads without throwing and counts what the Shaman wears
        CommunityBuilds.Owned real = CommunityBuilds.owned(s);
        assertNotNull(real);
    }

    /** A build's totals are its own: the same whatever the player wears or owns, at the build's top level. */
    @Test
    void totalsIgnoreWhatThePlayerWears() throws Exception {
        FixtureState fx = FixtureState.load(Fixtures.root().resolve("shaman-44"));
        fx.install("en");
        GameState s = GameState.current();
        Set<String> used = new java.util.HashSet<>();
        List<WynnBuilderLink.Piece> pieces = new ArrayList<>();
        Slot[] slots = {Slot.HELMET, Slot.CHESTPLATE, Slot.LEGGINGS, Slot.BOOTS, null, null, null, Slot.NECKLACE};
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] != null) {
                ItemData it = pick(s, slots[i], used);
                used.add(it.name());
                pieces.add(new WynnBuilderLink.Piece(i, WynnBuilderLink.Kind.ITEM, -1, it.name(), null, ""));
            }
        }
        pieces.add(new WynnBuilderLink.Piece(WynnBuilderLink.WEAPON, WynnBuilderLink.Kind.CRAFTED, -1, null, null, "",
                WynnBuilderCode.encode(relik())));
        String link = WynnBuilderExport.link(new WynnBuilderLink.Build(null, false, pieces, List.of(), null, 0, ""), null);
        CommunityBuilds.Entry e = new CommunityBuilds.Entry("shaman-test-50-bbbbbb", "Test", link, "shaman", 40, 50, List.of("spell"),
                "d", "forum", "2026-11-04", false, null);

        CommunityBuilds.Totals worn = CommunityBuilds.totals(set(s, e), e.levelMax());
        assertNotNull(worn);
        assertEquals(50, worn.level(), "the build's own top level");
        assertTrue(worn.ehp() > 0 && worn.health() > 0);
        CommunityBuilds.Compare a = CommunityBuilds.compare(s, e, CommunityBuilds.owned(s), n -> null, TestIngredients.byName()::get,
                r -> null, true);

        fx.fresh();                                     // nothing worn, carried or banked now
        assertEquals(worn, CommunityBuilds.totals(set(s, e), e.levelMax()), "the gear worn changes nothing");
        CommunityBuilds.Compare b = CommunityBuilds.compare(s, e, CommunityBuilds.Owned.NONE, n -> null, TestIngredients.byName()::get,
                r -> null, true);
        assertEquals(worn, a.totals());
        assertEquals(a.totals(), b.totals());
        com.google.gson.JsonObject o = CommunityBuilds.json(List.of(e), Map.of(e.id(), b)).get(0).getAsJsonObject();
        assertEquals(50, o.getAsJsonObject("totals").get("level").getAsInt());
        assertTrue(o.getAsJsonObject("totals").has("ehp"));
    }

    private static Map<String, Damage.Piece> set(GameState s, CommunityBuilds.Entry e) {
        return LinkImport.set(LinkImport.of(s, WynnBuilderLink.decode(e.link()), TestIngredients.byName()::get));
    }
}
