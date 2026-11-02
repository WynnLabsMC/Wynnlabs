package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.crafting.WynnBuilderCode;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Crafted pieces in WynnBuilder build links: the crafter code inside a link read back as its
 * recipe (the current form and the older "CR-" one), made into a piece the model scores, and
 * written again by the export so a round trip keeps it.
 */
class WynnBuilderCraftedTest {

    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+-";

    /** Depurinated Genome (Wynntils' ingredient data, October 2026). */
    private static final Ingredient GENOME = ingredient("Depurinated Genome",
            new Object[][] {{"mainAttackDamage", 22, 24}, {"rawAgility", -4, -4}, {"rawDefence", -4, -4},
                    {"rawDexterity", -4, -4}, {"rawIntelligence", -4, -4}, {"rawStrength", -4, -4}, {"spellDamage", 22, 24}},
            new int[] {-10, -10, -10, -10, -10});

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void aCraftedWeaponInALinkIsItsRecipe() {
        RecipeCode.Recipe recipe = relik(GENOME.name());
        String code = WynnBuilderCode.encode(recipe);
        assertNotNull(code);
        WynnBuilderLink.Build b = WynnBuilderLink.decode(WynnBuilderExport.SITE + linkWithWeapon(code));
        assertNotNull(b);
        assertEquals(1, b.pieces().size());
        WynnBuilderLink.Piece p = b.pieces().get(0);
        assertEquals(WynnBuilderLink.Kind.CRAFTED, p.kind());
        assertEquals(WynnBuilderLink.WEAPON, p.slot());
        assertEquals(code, p.craft(), "the code's own letters");
        assertEquals(recipe, p.recipe());
        assertEquals("T6", p.powders(), "the powders after it");
    }

    @Test
    void olderLinksKeepTheirCraftedCodes() {
        WynnBuilderLink.Build c = WynnBuilderLink.decode("https://wynnbuilder.github.io/builder?v=18#10_09V0DPCR-17B9F9fAM8r9t9i510Qz0Jf0x2CR-19c4N4N4N4N4N9c910uH0QJ2M1G-s-s1C1g010003610003610003600z0z0+0+0+0+0-1T2Y2Y2Z2Z2a2azskR5OxIc0");
        assertNotNull(c);
        assertEquals("CR-17B9F9fAM8r9t9i51", c.pieces().get(2).craft());
        assertNotNull(c.pieces().get(2).recipe(), "crafted leggings");
        assertEquals("leggings", c.pieces().get(2).recipe().type());
        assertEquals("bracelet", c.pieces().get(6).recipe().type(), "crafted bracelet");
        assertEquals("Alkatraz", c.pieces().get(8).name(), "the pieces after them still line up");
    }

    @Test
    void theExportWritesItBack() {
        RecipeCode.Recipe weapon = relik(GENOME.name());
        RecipeCode.Recipe legs = new RecipeCode.Recipe("leggings", 1, 2, 1, Craft.Speed.NORMAL, Arrays.asList(new String[6]));
        List<WynnBuilderLink.Piece> pieces = List.of(
                new WynnBuilderLink.Piece(2, WynnBuilderLink.Kind.CRAFTED, -1, null, null, "E3", WynnBuilderCode.encode(legs)),
                new WynnBuilderLink.Piece(8, WynnBuilderLink.Kind.CRAFTED, -1, null, null, "T6T6", WynnBuilderCode.encode(weapon)));
        WynnBuilderLink.Build in = new WynnBuilderLink.Build(null, false, pieces, List.of(), null, 0, "");
        WynnBuilderLink.Build out = WynnBuilderLink.decode(WynnBuilderExport.link(in, null));
        assertNotNull(out);
        assertEquals(2, out.pieces().size());
        assertEquals(legs, out.pieces().get(0).recipe());
        assertEquals("E3", out.pieces().get(0).powders());
        assertEquals(weapon, out.pieces().get(1).recipe());
        assertEquals("T6T6", out.pieces().get(1).powders());
        // and once more: decode → export → decode gives the same
        WynnBuilderLink.Build again = WynnBuilderLink.decode(WynnBuilderExport.link(out, null));
        assertEquals(out.pieces(), again.pieces());
    }

    @Test
    void theImportScoresTheBuildWithIt() throws Exception {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        FixtureState.load(dir).install("en");
        GameState state = GameState.current();
        RecipeCode.Recipe recipe = relik(GENOME.name());
        WynnBuilderLink.Build b = WynnBuilderLink.decode(WynnBuilderExport.SITE + linkWithWeapon(WynnBuilderCode.encode(recipe)));
        Map<String, Ingredient> ings = Map.of(GENOME.name(), GENOME);

        LinkImport.Result r = LinkImport.of(state, b, ings::get);
        LinkImport.Row row = r.rows().get(0);
        assertEquals(LinkImport.Why.KEPT, row.why());
        Damage.Piece made = row.crafted();
        assertNotNull(made);
        assertEquals("Crafted Relik", made.name());
        assertEquals(Slot.RELIK, made.type());
        assertTrue(made.crafted());
        assertEquals(CraftData.find("relik", recipe.minLevel()).maxLevel(), made.level());
        assertTrue(made.baseDps() > 0, "a weapon hits");
        assertEquals(23.0, made.stats().get("spellDamage"), 0.6, "the middle of the genome's range");
        assertEquals(Klass.SHAMAN, LinkImport.klass(r.rows(), state), "the crafted weapon's class");

        Map<String, Damage.Piece> set = LinkImport.set(r);
        assertEquals(made, set.get("WEAPON"));
        Map<String, Damage.Piece> without = new LinkedHashMap<>(set);
        without.remove("WEAPON");
        int[] points = new int[5];
        int level = made.level();
        double with = Damage.breakdown(set, points, level).total();
        double none = Damage.breakdown(without, points, level).total();
        assertTrue(with > none, "the crafted weapon adds damage: " + with + " vs " + none);

        // an ingredient the game does not know: the piece stays left out, as before
        LinkImport.Result unknown = LinkImport.of(state, b, n -> null);
        assertEquals(LinkImport.Why.CRAFTED, unknown.rows().get(0).why());
        assertNull(unknown.rows().get(0).crafted());
    }

    /** A relik recipe in the highest bracket up to level 44 with the ingredient in its first slot, three-star materials, fast. */
    private static RecipeCode.Recipe relik(String first) {
        int lv = CraftData.ofType("relik").stream().mapToInt(CraftData.Recipe::minLevel).filter(l -> l <= 44).max().orElseThrow();
        List<String> slots = Arrays.asList(new String[6]);
        slots.set(0, first);
        return new RecipeCode.Recipe("relik", lv, 3, 3, Craft.Speed.FAST, slots);
    }

    /**
     * A link in the newest data version with only a crafted weapon (the code's letters as the
     * site appends them) powdered T6, no tomes, the points left to the site and the top level.
     */
    private static String linkWithWeapon(String code) {
        WynnBuilderLink.Widths w = WynnBuilderLink.table().versions().get(WynnBuilderLink.table().versions().size() - 1);
        StringBuilder bits = new StringBuilder();
        put(bits, 12, 6);
        put(bits, WynnBuilderLink.table().versions().size() - 1, 10);
        for (int slot = 0; slot < 8; slot++) {
            put(bits, 0, 2);
            put(bits, 0, w.items());
            if (slot < 4) {
                put(bits, 0, 1);
            }
        }
        put(bits, 1, 2);
        for (char c : code.substring(3).toCharArray()) {
            put(bits, DIGITS.indexOf(c), 6);
        }
        put(bits, 1, 1);                                // powders: one T6 thunder, then done
        put(bits, 1 * w.powderTiers() + 5, w.powderBits());
        put(bits, 7, 3);
        put(bits, 0, 1);                                // no tomes
        put(bits, 1, 1);                                // points left to the site
        put(bits, 0, 1);                                // the top level
        bits.append('0');                               // no aspects, an empty tree
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < bits.length(); i += 6) {
            int v = 0;
            for (int j = 0; j < 6 && i + j < bits.length(); j++) {
                if (bits.charAt(i + j) == '1') {
                    v |= 1 << j;
                }
            }
            out.append(DIGITS.charAt(v));
        }
        return out.toString();
    }

    private static void put(StringBuilder bits, int value, int n) {
        for (int i = 0; i < n; i++) {
            bits.append((value >> i & 1) == 1 ? '1' : '0');
        }
    }

    private static Ingredient ingredient(String name, Object[][] ids, int[] req) {
        Map<String, Ingredient.Stat> m = new LinkedHashMap<>();
        for (Object[] id : ids) {
            m.put((String) id[0], new Ingredient.Stat((Integer) id[1], (Integer) id[2], (String) id[0], ""));
        }
        return new Ingredient(name, 3, 1, List.of("woodworking"), m, new int[6], -162, 0, 0, req);
    }
}
