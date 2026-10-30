package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.model.MountRead;
import org.junit.jupiter.api.Test;

import java.util.List;

class RideViewTest {

    @Test
    void ridingWithAnUnreadableItemStillGivesAPanelWithItsTypeEnergyAndANote() {
        RideView v = RideView.of(null, "WYVERN", "entity.minecraft.llama", new int[] {44, 48}, -1);
        assertEquals("Wyvern", v.title());
        assertEquals("", v.kind());
        assertEquals("92%", v.energy());
        assertEquals(0.92f, v.fill(), 0.001);
        assertTrue(v.stats().isEmpty());
        assertEquals(RideView.NOT_READ, v.note());
    }

    @Test
    void withoutWynntilsTheVehicleNamesItAndTheEnergyIsADash() {
        RideView v = RideView.of(null, "no model", "entity.minecraft.llama", null, -1);
        assertEquals("Llama", v.title());
        assertEquals("–", v.energy());
        assertEquals(RideView.NOT_READ, v.note());
    }

    @Test
    void aReadMountShowsItsSkinTypeStatsAndItemEnergy() {
        MountRead m = new MountRead("WYVERN", "Wyvern Flute", 240, 196, 204,
                List.of(new MountRead.Stat("SPEED", "Speed", 1, 10, 10)), true, "", "", "Golden-Tusk", null);
        RideView v = RideView.of(m, "WYVERN", null, new int[] {44, 48}, 46);
        assertEquals("Golden-Tusk", v.title());
        assertEquals("Wyvern", v.kind());
        assertEquals("~187/204", v.energy(), "the bar moved since the read: an estimate");
        assertEquals(1, v.stats().size());
        assertNull(v.note());
        assertEquals(false, v.maxKnown());
    }

    @Test
    void theFreeMountShowsItsNameAndEnergyWithoutStatRowsOrANote() {
        List<MountRead.Stat> ones = List.of(new MountRead.Stat("SPEED", "Speed", 1, 1, 1),
                new MountRead.Stat("ACCELERATION", "Acceleration", 1, 1, 1), new MountRead.Stat("ALTITUDE", "Altitude", 1, 1, 1),
                new MountRead.Stat("ENERGY", "Energy", 1, 1, 1));
        MountRead m = new MountRead("WYVERN", "Wyvern Flute", 8, 200, 200, ones, true, "", "", "Azure-Cinder", null);
        RideView v = RideView.of(m, "WYVERN", "entity.minecraft.llama", null, -1);
        assertEquals("Azure-Cinder", v.title());
        assertEquals("Wyvern", v.kind());
        assertEquals("200/200", v.energy());
        assertTrue(v.stats().isEmpty());
        assertNull(v.note(), "not \"stats not read yet\": they were read");
    }

    @Test
    void theSameInputsGiveAnEqualView() {
        assertEquals(RideView.of(null, "WYVERN", null, new int[] {10, 48}, -1), RideView.of(null, "WYVERN", null, new int[] {10, 48}, -1));
    }
}
