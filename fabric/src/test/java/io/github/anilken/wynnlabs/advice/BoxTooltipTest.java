package io.github.anilken.wynnlabs.advice;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The short box tooltip on the recorded Shaman's banked boxes: the name and level range, the
 * rarity pill and the count, the verdict the advice already has, the bank line, at most eight
 * drops by level then name with "+N more" after them, and the Shift hint; nothing with Shift
 * held, the setting off, or for an item that is not a box.
 */
@Tag("heavy")
class BoxTooltipTest {

    private static final ItemView.Box ELEVEN = new ItemView.Box(Slot.CHESTPLATE, Tier.UNIQUE, 71, 75);
    private static final ItemView.Box EIGHT = new ItemView.Box(Slot.LEGGINGS, Tier.UNIQUE, 66, 70);

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

    private ItemStack boxStack(ItemView.Box box) {
        return state.bankStacks().values().stream().flatMap(List::stream)
                .filter(s -> state.view(s).map(v -> box.equals(v.box())).orElse(false))
                .findFirst().orElseThrow();
    }

    /** A box's lore as the game hands it: the name, the pill in the pack's font, the range, the bank line wrapped. */
    private static List<Component> lore(String name) {
        FontDescription pillFont = new FontDescription.Resource(Identifier.parse("minecraft:banner/pill"));
        return List.of(Component.literal(name),
                Component.literal("").withStyle(s -> s.withFont(pillFont)),
                Component.literal("71-75 Level Range"),
                Component.literal("▣ Shaman bank p. 3 ×2,").withStyle(ChatFormatting.DARK_AQUA),
                Component.literal("Mage bank p. 1").withStyle(ChatFormatting.DARK_AQUA),
                Component.literal("◆ Possible drops (past prices):").withStyle(ChatFormatting.GOLD));
    }

    private static String text(TipRow r) {
        return ((TipRow.Text) r).left().getString();
    }

    @Test
    void aBoxWithManyDropsListsEightByLevelThenMore() {
        ItemStack stack = boxStack(ELEVEN);
        String name = stack.getHoverName().getString();
        List<TipRow> rows = BoxTooltip.rows(stack, lore(name), false);
        assertNotNull(rows);

        TipRow.Text head = assertInstanceOf(TipRow.Text.class, rows.get(0));
        assertEquals("Unidentified Chestplate", head.left().getString());
        assertEquals("Lv 71–75", head.right().getString());
        assertEquals("  · 11 possible drops", text(rows.get(1)));
        assertInstanceOf(TipRow.Separator.class, rows.get(2));

        // the verdict row is the advice the badge and the full tooltip show, when there is one
        Advice.Entry e = AutoAdvice.cached(stack);
        int at = 3;
        if (e != null && e.verdict() != null && e.verdict() != Verdict.NOTE) {
            String verdict = text(rows.get(at++));
            assertTrue(verdict.startsWith(e.verdict().label()), verdict);
            String where = Advice.sellPlace(e);
            if (where != null) {
                assertTrue(verdict.endsWith(" → " + where), verdict);
            }
        }
        assertEquals("▣ Shaman bank p. 3 ×2, Mage bank p. 1", text(rows.get(at++)));
        assertInstanceOf(TipRow.Separator.class, rows.get(at++));

        List<TipRow.Item> items = rows.stream().filter(r -> r instanceof TipRow.Item)
                .map(r -> (TipRow.Item) r).toList();
        assertEquals(BoxTooltip.MAX_ITEMS, items.size());
        List<ItemData> expected = state.possibleGears(ELEVEN).stream()
                .sorted(Comparator.comparingInt((ItemData i) -> i.requirements().level()).thenComparing(ItemData::name))
                .limit(BoxTooltip.MAX_ITEMS).toList();
        assertEquals(expected, items.stream().map(TipRow.Item::data).toList());
        for (TipRow.Item it : items) {
            assertEquals(it.data().requirements().level(), it.level());
            assertFalse(it.price().getString().isEmpty());
        }
        assertEquals(rows.get(at), items.get(0));
        assertEquals("+3 more", text(rows.get(rows.size() - 2)));
        assertEquals("⇧ Shift: Why · Rules · Full lore", text(rows.get(rows.size() - 1)));
    }

    @Test
    void eightDropsNeedNoMoreRowAndNoBankLineMeansNoBankRow() {
        ItemStack stack = boxStack(EIGHT);
        List<Component> lines = List.of(stack.getHoverName(), Component.literal("66-70 Level Range"));
        List<TipRow> rows = BoxTooltip.rows(stack, lines, false);
        assertNotNull(rows);
        assertEquals(8, rows.stream().filter(r -> r instanceof TipRow.Item).count());
        assertEquals("[UNIQUE]  · 8 possible drops", text(rows.get(1)));
        assertTrue(rows.stream().noneMatch(r -> r instanceof TipRow.Text t && t.left().getString().startsWith("+")));
        assertTrue(rows.stream().noneMatch(r -> r instanceof TipRow.Text t && t.left().getString().startsWith("▣")));
        assertInstanceOf(TipRow.Item.class, rows.get(rows.size() - 2));
    }

    @Test
    void shiftTheSettingOffOrAnotherItemKeepTheFullTooltip() {
        ItemStack stack = boxStack(ELEVEN);
        List<Component> lines = lore(stack.getHoverName().getString());
        assertNull(BoxTooltip.rows(stack, lines, true));
        ItemStack other = state.inventory().stream()
                .filter(s -> !s.isEmpty() && state.view(s).map(v -> !v.isBox()).orElse(true))
                .findFirst().orElseThrow();
        assertNull(BoxTooltip.rows(other, lines, false));

        JsonObject off = state.data.settings().deepCopy();
        off.addProperty("boxCompactTooltip", false);
        Settings.use(off);
        assertNull(BoxTooltip.rows(stack, lines, false));
    }

    @Test
    void pricesLoseTheirTildeAndMedian() {
        assertEquals("1.0 LE", BoxTooltip.shortPrice("~1.0 LE"));
        assertEquals("5 EB", BoxTooltip.shortPrice("~5 EB (median 9 EB)"));
    }
}
