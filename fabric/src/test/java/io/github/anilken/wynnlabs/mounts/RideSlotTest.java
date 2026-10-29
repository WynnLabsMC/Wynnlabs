package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

class RideSlotTest {

    /** A hotbar: slot → what reads as a mount there (absent: not a mount). */
    private static final Map<Integer, String> BAR = new HashMap<>();

    private static String read(int slot) {
        return BAR.get(slot);
    }

    @Test
    void theSlotLastUsedWithAMountIsKeptAndOtherUsesDoNotMoveIt() {
        RideSlot s = new RideSlot();
        assertEquals(-1, s.slot());
        s.used(4, true);                      // the mount summoned from slot 4
        s.used(0, false);                     // then a weapon used
        assertEquals(4, s.slot());
        s.used(9, true);                      // outside the hotbar: ignored
        assertEquals(4, s.slot());
    }

    @Test
    void whileRidingTheHeldMountWinsElseTheRememberedSlotElseNothing() {
        RideSlot s = new RideSlot();
        s.used(4, true);
        BAR.clear();
        BAR.put(4, "wyvern");
        assertEquals(4, s.from(0, RideSlotTest::read), "holding a sword: read from the summon slot");
        BAR.put(0, "horse");
        assertEquals(0, s.from(0, RideSlotTest::read), "a mount in hand wins");
        BAR.clear();
        assertEquals(-1, s.from(0, RideSlotTest::read), "the slot no longer holds a mount: no data");
    }

    @Test
    void withoutAnyUseOnlyAHeldMountCounts() {
        RideSlot s = new RideSlot();
        BAR.clear();
        BAR.put(2, "wyvern");
        assertEquals(-1, s.from(0, RideSlotTest::read));
        assertEquals(2, s.from(2, RideSlotTest::read));
    }

    @Test
    void wynntilsSlotWinsThenTheHeldOrLastUsedMountThenTheFirstCarriedOne() {
        RideSlot s = new RideSlot();
        BAR.clear();
        BAR.put(4, "wyvern");
        BAR.put(20, "horse");
        assertEquals(20, s.choose(20, 0, RideSlotTest::read), "the summoned mount's item, outside the hotbar too");
        assertEquals(4, s.choose(-1, 4, RideSlotTest::read), "Wynntils knows no mount: the one in hand");
        s.used(4, true);
        assertEquals(4, s.choose(-2, 0, RideSlotTest::read), "no mount model: the last used slot");
        assertEquals(4, s.choose(7, 0, RideSlotTest::read), "Wynntils' slot holds no mount: the last used slot");
        RideSlot fresh = new RideSlot();
        assertEquals(4, fresh.choose(-1, 0, RideSlotTest::read), "nothing known: the first carried mount");
        BAR.clear();
        assertEquals(-1, fresh.choose(-1, 0, RideSlotTest::read), "no mount carried");
    }

    @Test
    void anInventorySlotIsFoundAtTheEndOfAMenusContents() {
        assertEquals(63 - 9 + 3, RideSlot.inMenu(3, 63));    // a six-row chest: hotbar last
        assertEquals(63 - 36, RideSlot.inMenu(9, 63));       // the first main inventory slot
        assertEquals(63 - 10, RideSlot.inMenu(35, 63));
        assertEquals(-1, RideSlot.inMenu(-1, 63));
        assertEquals(-1, RideSlot.inMenu(3, 20));
    }
}
