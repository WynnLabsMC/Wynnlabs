package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class FeederReadTest {

    /** A mount's lore as the game writes it (glyphs left out; one leading space kept like the dump). */
    private static List<String> mountLore(String skin, String state) {
        return List.of("Wyvern Reins", "", "240 Potential", " " + skin, "Energy 200/200", "", " Combat Level 84",
                " Quest The Canyon Guides", "", "Level/Limit (Max)", "Speed 1/10 (30)", "Acceleration 1/10 (30)",
                "Altitude 1/10 (30)", "Energy 1/10 (30)", "Handling 1/10 (30)", "Toughness 1/10 (30)", "Boost 1/10 (30)",
                "Training 1/10 (30)", "", state, "", " Left-Click to Pickup", " Right-Click to Rename");
    }

    private static final String GLYPH = "󿿿";       // the empty queue slot's nameless placeholder

    /**
     * The Mount Feeder as the first in-game dump showed it: an empty header row (y 18), then five
     * rows of nine (y 36-108): the mount (or the "Drag a mount…" placeholder), a spacer, five queue
     * slots (placeholders when empty), two Silverbull-locked ones. Added for the tests: row 2 eats a
     * Copper Ingot, row 3 has a second mount in its queue (breeding), row 5 is empty.
     */
    private static List<FeederRead.SlotInfo> dump() {
        List<FeederRead.SlotInfo> out = new ArrayList<>();
        for (int c = 0; c < 9; c++) {
            out.add(new FeederRead.SlotInfo(c, 8 + 18 * c, 18, "", List.of(), false, true));
        }
        String[] skins = {"Azure-Horn", "Hollow-Cinder", "Golden-Tusk", "Cerulean-Shell"};
        for (int r = 0; r < 5; r++) {
            int y = 36 + 18 * r;
            for (int c = 0; c < 9; c++) {
                int i = 9 + r * 9 + c;
                int x = 8 + 18 * c;
                FeederRead.SlotInfo s;
                if (c == 0) {
                    s = r < 4 ? new FeederRead.SlotInfo(i, x, y, "Wyvern Reins", mountLore(skins[r], "Ready to Feed/Breed"), true, false)
                            : new FeederRead.SlotInfo(i, x, y, "Drag a mount into this slot to", List.of("place it into the enclosure!"),
                                    false, false);
                } else if (c == 1) {
                    s = new FeederRead.SlotInfo(i, x, y, "", List.of(), false, true);
                } else if (c >= 7) {
                    s = new FeederRead.SlotInfo(i, x, y, "Silverbull Membership is", List.of("required to use this slot!"), false, false);
                } else if (r == 4) {
                    s = new FeederRead.SlotInfo(i, x, y, "", List.of(), false, true);
                } else if (r == 1 && c == 2) {
                    s = new FeederRead.SlotInfo(i, x, y, "Copper Ingot", List.of("Mining Level 1"), false, false);
                } else if (r == 2 && c == 2) {
                    s = new FeederRead.SlotInfo(i, x, y, "Wyvern Reins", mountLore("Pearl-Fang", "Ready to Feed/Breed"), true, false);
                } else {
                    s = new FeederRead.SlotInfo(i, x, y, GLYPH, List.of(), false, false);
                }
                out.add(s);
            }
        }
        return out;
    }

    private static Map<Integer, FeederRead.SlotInfo> byIndex(List<FeederRead.SlotInfo> all) {
        Map<Integer, FeederRead.SlotInfo> m = new HashMap<>();
        all.forEach(s -> m.put(s.index(), s));
        return m;
    }

    @Test
    void theHeaderIsSkippedAndTheMountSlotsAre9To45() {
        List<FeederRead.Row> rows = FeederRead.rows(dump());
        assertEquals(5, rows.size());
        assertEquals(List.of(9, 18, 27, 36, 45), rows.stream().map(FeederRead.Row::mountSlot).toList());
        assertEquals(List.of(11, 12, 13, 14, 15, 16, 17), rows.get(0).trough(), "the spacer (col 1) is never the queue");
        assertEquals(5, rows.get(0).open(), "two Silverbull slots locked");
    }

    @Test
    void theNextFoodGoesInTheFirstEmptyQueueSlotThePlaceholderCountsAsEmpty() {
        List<FeederRead.SlotInfo> all = dump();
        List<FeederRead.Row> rows = FeederRead.rows(all);
        assertEquals(11, FeederRead.firstFree(rows.get(0), byIndex(all)));
        assertEquals(21, FeederRead.firstFree(rows.get(1), byIndex(all)), "after the Copper Ingot being eaten");
        assertEquals(47, FeederRead.firstFree(rows.get(4), byIndex(all)));
    }

    @Test
    void theQueueHoldsFoodsAndASecondMountMeansBreeding() {
        List<FeederRead.SlotInfo> all = dump();
        List<FeederRead.Row> rows = FeederRead.rows(all);
        assertEquals(List.of("Copper Ingot"), FeederRead.pending(rows.get(1), byIndex(all)));
        assertEquals(List.of(), FeederRead.pending(rows.get(2), byIndex(all)), "the partner is not food");
        assertEquals(29, FeederRead.partner(rows.get(2), byIndex(all)));
        assertEquals(-1, FeederRead.partner(rows.get(0), byIndex(all)));
    }

    @Test
    void theSkinAndTheStateComeFromTheMountsLore() {
        List<String> lore = mountLore("Azure-Horn", "Ready to Feed/Breed");
        assertEquals("Azure-Horn", FeederRead.skin(lore));
        assertEquals("Ready to Feed/Breed", FeederRead.state(lore));
        assertNull(FeederRead.skin(List.of("Copper Ingot")));
    }

    @Test
    void clickHintsAreNeitherTheSkinNorTheState() {
        List<String> lore = new ArrayList<>(mountLore("Azure-Horn", ""));
        lore.add(lore.size() - 2, " Left-Click for Flute");
        lore.set(lore.size() - 1, " Drop to Release");
        assertNull(FeederRead.state(lore), "no state line: the click hint is not one");
        assertNull(FeederRead.skin(List.of("240 Potential", " Left-Click for Flute")));
        assertTrue(FeederRead.clickHint("Right-Click to Rename"));
        assertTrue(FeederRead.clickHint("Shift-Click to Pickup"));
        assertFalse(FeederRead.clickHint("Ready to Feed/Breed"));
    }

    @Test
    void theTimeLeftIsOnTheStateLineOnly() {
        assertEquals(8040, FeederRead.stateSeconds("Feeding in 2h 14m"));
        assertEquals(2700, FeederRead.stateSeconds("Breeding in 45m"));
        assertEquals(21, FeederRead.stateSeconds("Feeding in 21s"), "the second dump: seconds alone under a minute");
        assertEquals(-1, FeederRead.stateSeconds("Ready to Feed/Breed"));
        assertEquals(-1, FeederRead.stateSeconds(null));
    }

    @Test
    void aFoodIsKnownByItsNameOrItsKindAndLevel() {
        assertEquals("Copper Ingot", FeederRead.material("Copper Ingot", List.of()).name());
        Material wood = FeederRead.material("Something Plank", List.of("Woodcutting Level 20"));
        assertNotNull(wood);
        assertEquals(MaterialKind.WOOD, wood.kind());
        assertEquals(20, wood.level());
        assertNull(FeederRead.material("Silverbull Membership is", List.of()));
        // the second dump: the level glued to "Level" (the gap was a glyph)
        Material paper = FeederRead.material("Unknown Paper", List.of("Woodcutting Level󿿿1"));
        assertNotNull(paper);
        assertEquals(MaterialKind.PAPER, paper.kind());
        assertEquals(1, paper.level());
        assertEquals("Oak Paper", FeederRead.material("Oak Paper", List.of("Woodcutting Level1")).name());
    }

    @Test
    void timeLeftParsesOnlyFromALineAboutTime() {
        assertEquals(845, FeederRead.remaining(List.of("Ready in 14:05")));
        assertEquals(-1, FeederRead.remaining(List.of("Level 30", "Price: 20")), "no time line: no timer rather than a wrong one");
    }

    @Test
    void slotKFreesAtTheFirstOnesTimeLeftPlusTheTimersBeforeIt() {
        assertArrayEquals(new long[] {600, 4200, 11400, 11700}, FeederRead.finishTimes(600, new long[] {3600, 3600, 7200, 300}));
        assertArrayEquals(new long[] {}, FeederRead.finishTimes(600, new long[] {}));
    }

    @Test
    void eachQueuedItemsTimerFollowsTheLimitsBeforeIt() {
        Material copper = MountMaterials.of(MaterialKind.INGOT, 1);
        long[] t = FeederRead.timers(MountStats.fresh(), List.of(copper, copper));
        assertEquals(60, t[0], "a fresh mount (average limit 10) eats in a minute");
        assertEquals(2, t.length);
    }
}
