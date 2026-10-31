package io.github.anilken.wynnlabs.quests;

import io.github.anilken.wynnlabs.game.Wyn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The Content Book the player opens by hand: its filter, and each entry's status and level from its lore. */
class ContentBookPageTest {

    private static Wyn.BookSlot slot(String name, String... lore) {
        return new Wyn.BookSlot(name, List.of(lore));
    }

    @Test
    void statusComesFromTheFirstLoreLine() {
        assertEquals("COMPLETED", ContentBookPage.status(List.of("§aAlready completed")));
        assertEquals("STARTED", ContentBookPage.status(List.of("§aCurrently in progress")));
        assertEquals("STARTED", ContentBookPage.status(List.of("§aCurrently tracking")));
        assertEquals("AVAILABLE", ContentBookPage.status(List.of("§eCan be started")));
        assertEquals("AVAILABLE", ContentBookPage.status(List.of("§aCan be explored")));
        assertEquals("UNAVAILABLE", ContentBookPage.status(List.of("§cCannot be started yet")));
        // extra info before the dash, as the book writes for some kinds
        assertEquals("COMPLETED", ContentBookPage.status(List.of("§7Lv. 32 - §aAlready completed")));
        assertNull(ContentBookPage.status(List.of("§7Scroll down to see more")), "not an entry");
        assertNull(ContentBookPage.status(List.of()));
    }

    @Test
    void levelIsReadFromTheRequirementLine() {
        assertEquals(32, ContentBookPage.level(List.of("§aAlready completed", "", "§a✔À§7 Combat Lv. Min: 32")));
        assertEquals(60, ContentBookPage.level(List.of("§eCan be started", "", "§c✖À§7 Recommended Combat Lv: 60")));
        assertEquals(0, ContentBookPage.level(List.of("§aAlready completed", "", "§7Distance: §aNear")));
    }

    @Test
    void entriesBecomeLinesAndOtherItemsAreSkipped() {
        List<Wyn.ContentLine> lines = ContentBookPage.lines(List.of(
                slot("Hill Hovel", "§aAlready completed", "", "§a✔À§7 Combat Lv. Min: 8"),
                slot("Wolf Den", "§eCan be started", "", "§a✔À§7 Combat Lv. Min: 10"),
                slot("§8Empty page", "§7Nothing here"),
                slot("", "§aAlready completed")));
        assertEquals(List.of(
                new Wyn.ContentLine("Hill Hovel", "COMPLETED", 8, ""),
                new Wyn.ContentLine("Wolf Den", "AVAILABLE", 10, "")), lines);
    }

    @Test
    void theActiveFilterNamesTheKind() {
        Wyn.BookSlot filter = slot("Filter", "§7Click to change the filter", "", "§7- §8Quest", "§f- §7Cave", "§7- §8Dungeon");
        assertEquals(ContentType.CAVE, ContentBookPage.filterOf(filter));
        assertNull(ContentBookPage.filterOf(slot("Filter", "§7- §8Quest", "§7- §8Cave")), "no active line");
        assertNull(ContentBookPage.filterOf(slot("Filter", "§f- §7All")), "a filter the mod keeps no list for");
        assertNull(ContentBookPage.filterOf(null));
    }
}
