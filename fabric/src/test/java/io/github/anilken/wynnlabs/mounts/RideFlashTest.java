package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.anilken.wynnlabs.model.MountRead;
import org.junit.jupiter.api.Test;

import java.util.List;

class RideFlashTest {

    private static MountRead wyvern(int speed, int boost) {
        return new MountRead("WYVERN", "Wyvern", 400, 18, 30, List.of(new MountRead.Stat("SPEED", "Speed", speed, 21, 58),
                new MountRead.Stat("BOOST", "Boost", boost, 12, 38)), false, "Azure", "");
    }

    @Test
    void theFirstReadShowsNothing() {
        RideFlash f = new RideFlash();
        f.update(wyvern(12, 4), 0);
        assertEquals(0, f.delta("SPEED", 0));
    }

    @Test
    void aGainShowsForTwoSecondsThenGoes() {
        RideFlash f = new RideFlash();
        f.update(wyvern(12, 4), 0);
        f.update(wyvern(13, 4), 1000);
        assertEquals(1, f.delta("SPEED", 1000));
        assertEquals(1, f.delta("SPEED", 1000 + RideFlash.MS - 1));
        assertEquals(0, f.delta("SPEED", 1000 + RideFlash.MS));
        assertEquals(0, f.delta("BOOST", 1500));
    }

    @Test
    void aLossIsNegativeAndASecondChangeInsideTheWindowAddsUpAndRestartsIt() {
        RideFlash f = new RideFlash();
        f.update(wyvern(12, 4), 0);
        f.update(wyvern(12, 3), 100);
        assertEquals(-1, f.delta("BOOST", 100));
        f.update(wyvern(13, 3), 200);
        f.update(wyvern(15, 3), 1500);
        assertEquals(3, f.delta("SPEED", 1500 + RideFlash.MS - 1));
        f.update(wyvern(16, 3), 1500 + RideFlash.MS + 10);
        assertEquals(1, f.delta("SPEED", 1500 + RideFlash.MS + 10), "after the window a change starts afresh");
    }

    @Test
    void anotherMountStartsANewBaselineWithoutAFlash() {
        RideFlash f = new RideFlash();
        f.update(wyvern(12, 4), 0);
        MountRead horse = new MountRead("HORSE", "Horse", 200, 9, 24,
                List.of(new MountRead.Stat("SPEED", "Speed", 3, 10, 30)), false, "Chestnut", "");
        f.update(horse, 100);
        assertEquals(0, f.delta("SPEED", 100));
    }
}
