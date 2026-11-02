package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.BaseStats;
import io.github.anilken.wynnlabs.model.Capped;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Requirement;
import io.github.anilken.wynnlabs.model.Roll;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.Tier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Crafted pieces you wear in the links the mod writes: as WynnBuilder custom items ("CI-…") with
 * the numbers the game shows, read back into the same model piece. The text form is checked
 * against what WynnBuilder's own encoder wrote (November 2026).
 */
class WynnBuilderCustomTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void textAsTheSiteWritesIt() {
        String[][] site = {{"Crafted Spear", "CraftedSpear-v6"}, {"100-120", "100-120-"}, {"Hero", "Hero-"},
            {"Ünïcode ☃ Wand", "ncodeWand-K50xxGS5vuqt6"}, {"a b c d", "abcd-d211"}, {"   ", "W000"}};
        for (String[] s : site) {
            assertEquals(s[1], WynnBuilderCustom.Bootstring.encode(s[0]), s[0]);
            assertEquals(s[0], WynnBuilderCustom.Bootstring.decode(s[1]), s[1]);
        }
    }

    @Test
    void aWornCraftedWeaponGoesOutAsACustomItem() {
        ItemView.Crafted spear = spear();
        WynnBuilderLink.Piece p = WynnBuilderExport.crafted(WynnBuilderLink.WEAPON, spear);
        assertEquals(WynnBuilderLink.Kind.CUSTOM, p.kind());
        assertTrue(p.code().startsWith("CI-"), p.code());

        WynnBuilderCustom.Item item = WynnBuilderCustom.of(spear);
        assertEquals("FAST", item.speed(), "the speed whose hits a second give the DPS the item shows");
        assertEquals(Map.of("neutral", "100-120", "fire", "5-9"), item.damages());
        assertEquals(List.of(30, 0, 0, 12, 0), item.reqs());

        WynnBuilderLink.Build in = new WynnBuilderLink.Build(null, false, List.of(p), List.of(), null, 0, "");
        WynnBuilderLink.Build out = WynnBuilderLink.decode(WynnBuilderExport.link(in, null));
        assertNotNull(out);
        assertEquals(1, out.pieces().size());
        WynnBuilderLink.Piece back = out.pieces().get(0);
        assertEquals(WynnBuilderLink.Kind.CUSTOM, back.kind());
        assertEquals(p.code(), back.code(), "the code's letters come back as written");
        // the site reads a crafted weapon's neutral damage as one base number (it shows 0.9 to 1.1 of it):
        // the range goes out as its middle, so the site's damage averages the game's; fire stays as it is
        assertEquals(Map.of("neutral", "110-110", "fire", "5-9"), back.custom().damages());
        assertEquals(new WynnBuilderCustom.Item(item.name(), item.slot(), item.level(), item.crafted(), item.klass(), item.reqs(),
                item.health(), back.custom().damages(), item.speed(), item.powderSlots(), item.ids()), back.custom(), "the rest as it was");

        Damage.Piece before = WynnBuilderCustom.piece(item, "a");
        Damage.Piece after = WynnBuilderCustom.piece(back.custom(), "a");
        assertEquals(before, after);
        assertEquals("Crafted Spear", after.name());
        assertEquals(Slot.SPEAR, after.type());
        assertEquals(44, after.level());
        assertTrue(after.crafted());
        assertEquals(23.0, after.stats().get("spellDamage"));
        assertEquals(-4.0, after.stats().get("rawStrength"));
        assertEquals(-1100.0, after.stats().get("rawHealth"));
        assertEquals(30, after.reqs().get(SkillKind.STRENGTH));
        assertEquals((110 + 7) * Damage.HITS[4], after.baseDps(), 1e-9);

        // and once more through the export: the same letters
        assertEquals(WynnBuilderExport.link(in, null), WynnBuilderExport.link(out, null));
    }

    @Test
    void armourAndOddNumbers() {
        ItemView.Crafted helmet = new ItemView.Crafted("Hëlm of a Thousand Names", Slot.HELMET, Tier.CRAFTED, 101, 0, 3125, List.of(),
                new Requirement(101, Optional.empty(), List.of(new Requirement.SkillReq(SkillKind.AGILITY, 77)), Optional.empty()),
                List.of(roll("rawAgility", 13), roll("1stSpellCost", -12), roll("walkSpeed", 1), roll("healthRegenRaw", 2147),
                        roll("notAnId", 9)),
                null, List.of(), 3, Capped.EMPTY);
        WynnBuilderCustom.Item item = WynnBuilderCustom.of(helmet);
        assertNull(item.speed());
        assertEquals(Map.of("rawAgility", 13, "1stSpellCost", -12, "walkSpeed", 1, "healthRegenRaw", 2147), item.ids(),
                "what the site has no id for stays out");
        String code = WynnBuilderCustom.encode(item);
        assertEquals(item, WynnBuilderCustom.decode(code));
        assertEquals(item, WynnBuilderCustom.decode(code.substring(3)), "without the CI- prefix");
        assertNull(WynnBuilderCustom.decode("CI-1hello"), "the site's older text form");
        // a custom weapon that is not crafted keeps its ranges exactly: the site reads them as they are
        WynnBuilderCustom.Item plain = new WynnBuilderCustom.Item("Plain Spear", Slot.SPEAR, 44, false, null, List.of(0, 0, 0, 0, 0), 0,
                Map.of("neutral", "100-120", "fire", "5-9"), "FAST", 2, Map.of());
        assertEquals(plain, WynnBuilderCustom.decode(WynnBuilderCustom.encode(plain)));
        assertNull(WynnBuilderCustom.decode("CI-"));
    }

    @Test
    void theImportKeepsACustomPiece() throws Exception {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        FixtureState.load(dir).install("en");
        GameState state = GameState.current();
        ItemView.Crafted relik = new ItemView.Crafted("Crafted Relik", Slot.RELIK, Tier.CRAFTED, 44, (int) Math.round(80 * Damage.HITS[3]), 0,
                List.of(new BaseStats.Damage("NEUTRAL", 70, 90)), new Requirement(44, Optional.empty(), List.of(), Optional.empty()),
                List.of(roll("spellDamage", 20)), Klass.SHAMAN, List.of(), 2, Capped.EMPTY);
        WynnBuilderLink.Build in = new WynnBuilderLink.Build(null, false, List.of(WynnBuilderExport.crafted(WynnBuilderLink.WEAPON, relik)),
                List.of(), null, 0, "");
        LinkImport.Result r = LinkImport.of(state, WynnBuilderLink.decode(WynnBuilderExport.link(in, null)), n -> null);
        LinkImport.Row row = r.rows().get(0);
        assertEquals(LinkImport.Why.KEPT, row.why());
        assertNotNull(row.made());
        assertEquals("Crafted Relik", row.made().name());
        assertEquals("NORMAL", WynnBuilderCustom.of(relik).speed());
        assertEquals(Klass.SHAMAN, LinkImport.klass(r.rows(), state));
        assertEquals(row.made(), LinkImport.set(r).get("WEAPON"));
    }

    /** A crafted spear as the game shows it: fast, neutral and fire damage, three ids, two requirements. */
    private static ItemView.Crafted spear() {
        double perHit = (100 + 120) / 2.0 + (5 + 9) / 2.0;
        return new ItemView.Crafted("Crafted Spear", Slot.SPEAR, Tier.CRAFTED, 44, (int) Math.round(perHit * Damage.HITS[4]), 0,
                List.of(new BaseStats.Damage("NEUTRAL", 100, 120), new BaseStats.Damage("FIRE", 5, 9)),
                new Requirement(44, Optional.empty(), List.of(new Requirement.SkillReq(SkillKind.STRENGTH, 30),
                        new Requirement.SkillReq(SkillKind.DEFENCE, 12)), Optional.empty()),
                List.of(roll("spellDamage", 23), roll("rawStrength", -4), roll("rawHealth", -1100)),
                null, List.of("FIRE"), 2, Capped.EMPTY);
    }

    private static Roll roll(String key, int value) {
        return new Roll(key, key, "", value, null);
    }
}
