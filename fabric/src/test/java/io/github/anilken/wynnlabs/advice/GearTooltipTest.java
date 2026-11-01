package io.github.anilken.wynnlabs.advice;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Roll;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.StatRange;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The short gear tooltip on the recorded Shaman's identified pieces: the header with the overall
 * roll and rerolls, the verdict the advice already has, at most six identifications with "+N more",
 * the requirement chips, the footer lines and the Shift hint; nothing for a box, an unidentified
 * piece, with Shift held or the setting off; and the roll colours, negative stats included.
 */
@Tag("heavy")
class GearTooltipTest {

    private FixtureState state;

    @BeforeEach
    void setUp() throws Exception {
        state = FixtureState.load(Fixtures.root().resolve("shaman-44"));
        state.install("en");
    }

    @AfterEach
    void tearDown() {
        FixtureState.uninstall();
    }

    private ItemStack gear(String name) {
        return state.inventory().stream()
                .filter(s -> !s.isEmpty() && state.view(s).map(v -> v.data() != null && v.data().name().equals(name)).orElse(false))
                .findFirst().orElseThrow();
    }

    private static <T extends TipRow> List<T> all(List<TipRow> rows, Class<T> kind) {
        return rows.stream().filter(kind::isInstance).map(kind::cast).toList();
    }

    @Test
    void anIdentifiedPieceWithManyRollsListsSixThenMore() {
        ItemStack stack = gear("Chained Pixels");
        List<TipRow> rows = GearTooltip.rows(stack, List.of(stack.getHoverName()), false);
        assertNotNull(rows);

        TipRow.Header head = assertInstanceOf(TipRow.Header.class, rows.get(0));
        assertSame(stack, head.icon());
        assertEquals("Chained Pixels", head.name().getString());
        assertEquals("42%", head.overall().getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), head.overall().getStyle().getColor());
        assertEquals("Legendary · Leggings · Lv 38", head.sub().getString());
        assertNull(head.rerolls());

        // the verdict band is the advice the badge and the full tooltip show, when there is one
        Advice.Entry e = BoxTooltip.entry(stack);
        List<TipRow.Band> bands = all(rows, TipRow.Band.class);
        assertEquals(e == null ? 0 : 1, bands.size());
        if (e != null) {
            assertTrue(bands.get(0).title().getString().startsWith(e.verdict().label()));
            assertEquals(rows.get(1), bands.get(0));
        }

        // the five fixed skill points first, each its own row without a bar, a rule, then every
        // rolled stat with its bar
        List<TipRow.Id> ids = all(rows, TipRow.Id.class);
        assertEquals(8, ids.size());
        assertTrue(ids.subList(0, 5).stream().allMatch(i -> i.percent() == null && i.value().getString().equals("+4")));
        assertInstanceOf(TipRow.Separator.class, rows.get(rows.indexOf(ids.get(4)) + 1));
        assertEquals("Combat Experience", ids.get(5).name().getString());
        assertEquals("+6%", ids.get(5).value().getString());
        assertTrue(ids.subList(5, 8).stream().allMatch(i -> i.percent() != null));

        TipRow.Chips chips = all(rows, TipRow.Chips.class).get(0);
        assertEquals(List.of("Lv 38 ✓", "Str 8 ✓", "Dex 8 ✓", "Int 8 ✓", "Def 8 ✓", "Agi 8 ✓"),
                chips.chips().stream().map(TipRow.Chip::text).toList());

        TipRow.Centred shift = assertInstanceOf(TipRow.Centred.class, rows.get(rows.size() - 1));
        assertEquals("⇧ Shift: Why · Rules · Full lore", shift.text().getString());
    }

    @Test
    void theWeightedRollsFromTheFullTooltipFollowTheIdentifications() {
        ItemStack stack = gear("Heavensent");
        List<Component> lines = List.of(stack.getHoverName(), Component.literal("Heavensent [69.0%]"),
                Component.literal("Combat Experience [+6%] [30.0%]"),
                Component.empty(), Component.literal("\u2696 Nori"), Component.literal("Main Scale [26.8%]"),
                Component.literal("Riftwalker Scale [41.6%]"), Component.empty(), Component.literal("\u2696 Wynnpool"),
                Component.literal("Arcanist Scale [96.5%]"), Component.empty());
        List<TipRow> rows = GearTooltip.rows(stack, lines, false);
        assertNotNull(rows);
        List<TipRow.Id> ids = all(rows, TipRow.Id.class);
        List<TipRow.Id> scales = ids.subList(ids.size() - 3, ids.size());
        assertEquals(List.of("Main Scale", "Riftwalker Scale", "Arcanist Scale"),
                scales.stream().map(i -> i.name().getString()).toList());
        assertEquals(List.of("26.8%", "41.6%", "96.5%"), scales.stream().map(i -> i.value().getString()).toList());
        assertEquals(List.of(ChatFormatting.RED, ChatFormatting.GOLD, ChatFormatting.AQUA),
                scales.stream().map(TipRow.Id::colour).toList());
        int nori = rows.indexOf(scales.get(0)) - 1;
        assertEquals("\u2696 Nori", ((TipRow.Text) rows.get(nori)).left().getString());
        assertInstanceOf(TipRow.Separator.class, rows.get(nori - 1));
        assertEquals("\u2696 Wynnpool", ((TipRow.Text) rows.get(rows.indexOf(scales.get(2)) - 1)).left().getString());
        // without such lines, nothing is added
        assertEquals(List.of(), GearTooltip.scales(List.of(stack.getHoverName())));
    }

    @Test
    void aWeaponShowsRerollsClassDamageAndPowderSlots() {
        ItemStack stack = gear("Heavensent");
        List<Component> lines = List.of(stack.getHoverName(),
                Component.literal("▣ Shaman bank p. 3").withStyle(ChatFormatting.DARK_AQUA));
        List<TipRow> rows = GearTooltip.rows(stack, lines, false);
        assertNotNull(rows);
        TipRow.Header head = (TipRow.Header) rows.get(0);
        assertEquals("69%", head.overall().getString());
        assertEquals("2 rerolls", head.rerolls().getString());
        assertEquals("Legendary · Relik · Lv 42", head.sub().getString());
        List<TipRow.Id> ids = all(rows, TipRow.Id.class);
        assertEquals(6, ids.size());
        // the fixed skill points come first in the same columns, without a bar, then a rule
        assertEquals(List.of("Intelligence", "Defence", "Agility"), ids.subList(0, 3).stream().map(i -> i.name().getString()).toList());
        assertEquals(List.of("+10", "-8", "+10"), ids.subList(0, 3).stream().map(i -> i.value().getString()).toList());
        assertTrue(ids.subList(0, 3).stream().allMatch(i -> i.percent() == null));
        assertInstanceOf(TipRow.Separator.class, rows.get(rows.indexOf(ids.get(2)) + 1));
        assertSame(ids.get(3), rows.get(rows.indexOf(ids.get(2)) + 2));
        assertTrue(ids.subList(3, 6).stream().allMatch(i -> i.percent() != null));
        // the base stats sit right under the verdict band: DPS with the attack speed, then the damages
        int dps = rows.indexOf(all(rows, TipRow.Text.class).stream()
                .filter(t -> t.left().getString().startsWith("⚔")).findFirst().orElseThrow());
        assertEquals("⚔ 270 DPS · Fast (2.5 hits/s)", ((TipRow.Text) rows.get(dps)).left().getString());
        assertEquals("❉ 48–60  ❋ 48–60", ((TipRow.Text) rows.get(dps + 1)).left().getString());
        assertEquals(List.of("Lv 42 ✓", "Shaman ✓", "Int 17 ✓", "Agi 17 ✓"),
                all(rows, TipRow.Chips.class).get(0).chips().stream().map(TipRow.Chip::text).toList());

        List<TipRow.Pair> footer = all(rows, TipRow.Pair.class);
        assertInstanceOf(TipRow.Separator.class, rows.get(rows.indexOf(footer.get(0)) - 1));
        assertEquals("◈ 2 powder slots", footer.get(0).left().getString());
        assertNull(footer.get(0).right());
        TipRow.Pair second = footer.get(footer.size() - 1);
        assertEquals("▣ Shaman bank p. 3", second.right().getString());
        if (second.left() != null) {
            assertTrue(second.left().getString().startsWith("◇ Trade Market "), second.left().getString());
        }
    }

    @Test
    void unmetRequirementsAreRedWithYourValue() {
        state.atLevel(30);
        ItemStack stack = gear("Grisly Gauntlet");
        ItemView v = state.view(stack).orElseThrow();
        ItemView.Marks failing = new ItemView.Marks(true, false, Map.of(SkillKind.STRENGTH, false), true);
        List<TipRow.Chip> chips = GearTooltip.chips(v.data(), failing);
        assertEquals(List.of("Lv 37 ✗ (30)", "Str 20 ✗ (25)"), chips.stream().map(TipRow.Chip::text).toList());
        assertTrue(chips.stream().allMatch(c -> Boolean.FALSE.equals(c.met())));
        // without the game's checkmarks the character's own points decide: 25 Strength is enough
        List<TipRow.Chip> fromPoints = GearTooltip.chips(v.data(), new ItemView.Marks(false, false, Map.of(), false));
        assertEquals("Str 20 ✓", fromPoints.get(1).text());
    }

    @Test
    void aBoxShiftOrTheSettingOffKeepTheFullTooltipAnUnidentifiedPieceShowsRanges() {
        ItemStack stack = gear("Warsong");
        List<Component> lines = List.of(stack.getHoverName());
        assertNull(GearTooltip.rows(stack, lines, true));

        ItemStack box = state.bankStacks().values().stream().flatMap(List::stream)
                .filter(s -> state.view(s).map(ItemView::isBox).orElse(false)).findFirst().orElseThrow();
        assertNull(GearTooltip.rows(box, lines, false));

        ItemView v = state.view(stack).orElseThrow();
        ItemView unid = new ItemView(v.kind(), v.type(), v.plain(), v.name(), v.level(), v.professions(), v.data(), null,
                v.crafted(), v.box(), v.qualityTier(), v.powder(), v.potion(), v.dungeonKey(), v.skillPoint(),
                v.crystalCount(), v.abilityName());
        // not identified yet: the ranges each stat can roll, the fixed ones first, no overall
        List<TipRow> unidRows = GearTooltip.rows(state.stackOf("Warsong", unid), lines, false);
        assertNotNull(unidRows);
        TipRow.Header unidHead = (TipRow.Header) unidRows.get(0);
        assertNull(unidHead.overall());
        assertEquals("Legendary \u00b7 Ring \u00b7 Lv 33 \u00b7 Unidentified", unidHead.sub().getString());
        List<TipRow.Id> unidIds = all(unidRows, TipRow.Id.class);
        assertEquals(List.of("Dexterity", "Strength", "Main Attack Damage", "Mana Regen", "Spell Damage"),
                unidIds.stream().map(i -> i.name().getString()).toList());
        assertEquals(List.of("+2", "+2", "+2% to +8%", "-5/5s to -3/5s", "+2% to +8%"),
                unidIds.stream().map(i -> i.value().getString()).toList());
        assertTrue(unidIds.stream().allMatch(i -> i.percent() == null));
        assertEquals(ChatFormatting.RED, unidIds.get(3).colour());

        JsonObject off = state.data.settings().deepCopy();
        off.addProperty("gearCompactTooltip", false);
        Settings.use(off);
        assertNull(GearTooltip.rows(stack, lines, false));
    }

    @Test
    void rollColoursFollowWhereTheRollSitsNegativeStatsToo() {
        assertEquals(ChatFormatting.RED, GearTooltip.quality(39.4));
        assertEquals(ChatFormatting.GOLD, GearTooltip.quality(40));
        assertEquals(ChatFormatting.GOLD, GearTooltip.quality(69.4));
        assertEquals(ChatFormatting.GREEN, GearTooltip.quality(70));
        assertEquals(ChatFormatting.GREEN, GearTooltip.quality(89));
        assertEquals(ChatFormatting.AQUA, GearTooltip.quality(90));

        // Warsong's Mana Regen −4 of −5…−3: half way, as the game's overall counts it
        StatRange mr = new StatRange("manaRegen", "Mana Regen", "PER_5_S", false, -5, -3);
        TipRow.Id id = GearTooltip.id(new Roll("manaRegen", "Mana Regen", "PER_5_S", -4, mr));
        assertEquals(50.0, id.percent(), 1e-9);
        assertEquals(ChatFormatting.GOLD, id.colour());
        assertEquals("-4/5s", id.value().getString());
        // −3 is the best roll of that range
        assertEquals(100.0, GearTooltip.percent(new Roll("manaRegen", "Mana Regen", "PER_5_S", -3, mr)), 1e-9);
        // where less is better, the lowest value is the top roll
        StatRange cost = new StatRange("raw1stSpellCost", "1st Spell Cost", "RAW", true, 1, 5);
        assertEquals(100.0, GearTooltip.percent(new Roll("raw1stSpellCost", "1st Spell Cost", "RAW", 1, cost)), 1e-9);
        assertEquals(ChatFormatting.AQUA, GearTooltip.id(new Roll("raw1stSpellCost", "1st Spell Cost", "RAW", 1, cost)).colour());
        // a stat with one value: no %, grey
        StatRange fixed = new StatRange("rawStrength", "Strength", "RAW", false, 5, 5);
        TipRow.Id f = GearTooltip.id(new Roll("rawStrength", "Strength", "RAW", 5, fixed));
        assertNull(f.percent());
        assertEquals(ChatFormatting.GRAY, f.colour());

        // the recorded pieces: Grisly Gauntlet's overall 39.9 % reads 40 % and takes 40's colour; Precious' 86.9 % is green
        TipRow.Header grisly = (TipRow.Header) GearTooltip.rows(gear("Grisly Gauntlet"), List.of(), false).get(0);
        assertEquals("40%", grisly.overall().getString());
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), grisly.overall().getStyle().getColor());
        assertEquals(ChatFormatting.GREEN, GearTooltip.quality(state.view(gear("Precious")).orElseThrow().rolled().overallPercent()));
    }
}
