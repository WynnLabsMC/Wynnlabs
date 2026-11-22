package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

import java.util.List;

/** Kibble in a feeder queue: read by name, eaten in its own time, with its own effect. */
class KibbleTest {

    @Test
    void readsKindAndTierFromTheName() {
        Material h = Material.queued("Hearty Kibble III");
        assertNotNull(h);
        assertEquals(Kibble.HEARTY, h.kibble());
        assertEquals(3, h.level());
        assertEquals(Kibble.SUGARCRUNCH, Material.queued("Sugarcrunch II").kibble());
        assertEquals(2, Material.queued("Sugarcrunch II").level());
        assertEquals(Kibble.GOLDEN_BLEND, Material.queued("Golden Blend Kibble").kibble());
        assertEquals(1, Material.queued("Golden Blend Kibble").level());
        assertEquals(Kibble.NOSWEAT, Material.queued("Nosweat Nuggets").kibble());
        assertNull(Material.queued("Not A Food"));
        // a regular material still reads as one
        Material copper = Material.queued(MountMaterials.all().get(0).name());
        assertNotNull(copper);
        assertNull(copper.kibble());
    }

    @Test
    void effects() {
        MountStats s = MountStats.uniform(5, 10, 30);
        MountStats hearty = s.fed(Material.queued("Hearty Kibble II"));
        assertEquals(14, hearty.limit(0));
        assertEquals(5, hearty.level(0));
        assertEquals(30, hearty.max(0));
        MountStats golden = s.fed(Material.queued("Golden Blend"));
        assertEquals(32, golden.max(7));
        assertEquals(10, golden.limit(7));
        MountStats nosweat = s.fed(Material.queued("Nosweat Nuggets"));
        assertEquals(8, nosweat.level(3));
        assertEquals(s, s.fed(Material.queued("Sugarcrunch I")));
        // limits stay within maximums, values within limits
        assertEquals(30, MountStats.uniform(1, 29, 30).fed(Material.queued("Hearty III")).limit(0));
        assertEquals(10, MountStats.uniform(9, 10, 30).fed(Material.queued("Nosweat Nuggets")).level(0));
    }

    @Test
    void timersUseTheKibblesOwnEatingTime() {
        MountStats s = MountStats.fresh();
        Material food = MountMaterials.all().get(0);
        long[] t = FeederRead.timers(s, List.of(Material.queued("Sugarcrunch I"), Material.queued("Hearty Kibble I"), food));
        assertArrayEquals(new long[] {180, 8 * 3600, s.fed(Material.queued("Hearty Kibble I")).feedTimerSec()}, t);
    }
}
