package io.github.anilken.wynnlabs.search;

import io.github.anilken.wynnlabs.crafting.ToolLadder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Which hovered names open which view of the uses key, and the keys of the cards outside the index. */
class InfoItemsTest {

    @Test
    void toolsWardsAndRunesAreKnownByName() {
        assertEquals(InfoItems.Kind.TOOL, InfoItems.of("Cobalt Rod T8"));
        assertEquals(InfoItems.Kind.TOOL, InfoItems.of("Stone Pickaxe T1"));
        assertEquals(InfoItems.Kind.TOOL, InfoItems.of("Dernic Scythe T13"));
        assertEquals(InfoItems.Kind.WARD, InfoItems.of("Blue Ward"));
        assertEquals(InfoItems.Kind.WARD, InfoItems.of("Pink Ward"));
        assertEquals(InfoItems.Kind.RUNE, InfoItems.of("Nii Rune"));
        assertEquals(InfoItems.Kind.RUNE, InfoItems.of("Tol Rune"));
        assertNull(InfoItems.of("Koi Oil"));
        assertNull(InfoItems.of("Cobalt Rod"));             // no tier: not a tool's name
        assertNull(InfoItems.of("Teal Ward"));              // not one of the seven
        assertNull(InfoItems.of("Bear Mask"));
        assertNull(InfoItems.of(null));
    }

    @Test
    void aToolsKindIsTheWordInItsName() {
        assertEquals(ToolLadder.Kind.ROD, InfoItems.toolKind("Cobalt Rod T8"));
        assertEquals(ToolLadder.Kind.PICKAXE, InfoItems.toolKind("Cobalt Pickaxe T8"));     // not the axe inside the word
        assertEquals(ToolLadder.Kind.AXE, InfoItems.toolKind("Cobalt Axe T8"));
        assertEquals(ToolLadder.Kind.SCYTHE, InfoItems.toolKind("Cobalt Scythe T8"));
        assertEquals("fishing", InfoItems.toolKind("Cobalt Rod T8").profession);
        assertNull(InfoItems.toolKind("Blue Ward"));
    }

    @Test
    void aCardsKeyIsItsName() {
        assertEquals("info:Cobalt Rod T8", InfoItems.key("Cobalt Rod T8"));
        assertEquals("Cobalt Rod T8", InfoItems.name("info:Cobalt Rod T8"));
        assertNull(InfoItems.name("Cobalt Rod T8"));
        assertNull(InfoItems.name("info:"));
        assertNull(InfoItems.name("use:Koi Oil:ring"));
        assertNull(InfoItems.name(null));
    }

    @Test
    void onlyAnEntrysKeyIsARecentSearch() {
        assertTrue(InfoItems.recent("Koi Oil"));
        assertTrue(InfoItems.recent("mob:Tumbleweed"));
        assertFalse(InfoItems.recent("use:Koi Oil:ring"));
        assertFalse(InfoItems.recent("info:Blue Ward"));
        assertFalse(InfoItems.recent("made:Scroll of Dusk"));
        assertFalse(InfoItems.recent(null));
    }

    @Test
    void theUsesKeyIsNeverDeadOverAnItem() {
        Uses.Result none = Uses.of("Bear Mask");
        // an item the index knows: its row, its card (how to get it), its uses when it has any
        assertEquals(UseTarget.ITEM, UseTarget.of(true, none, "Bear Mask"));
        assertEquals(UseTarget.ITEM, UseTarget.of(true, Uses.of("Koi Oil"), "Koi Oil"));
        // a name only the recipes know
        assertEquals(UseTarget.ITEM, UseTarget.of(false, Uses.of("Koi Oil T2"), "Koi Oil T2"));
        // a tool, a ward, a rune: their own card
        assertEquals(UseTarget.INFO, UseTarget.of(false, Uses.of("Cobalt Rod T8"), "Cobalt Rod T8"));
        assertEquals(UseTarget.INFO, UseTarget.of(false, Uses.of("Blue Ward"), "Blue Ward"));
        assertEquals(UseTarget.INFO, UseTarget.of(false, Uses.of("Az Rune"), "Az Rune"));
        // anything else: the search with its name, not a dead key
        assertEquals(UseTarget.SEARCH, UseTarget.of(false, Uses.of("Corkian Amplifier III"), "Corkian Amplifier III"));
        assertEquals(UseTarget.SEARCH, UseTarget.of(false, none, "Bear Mask"));
    }
}
