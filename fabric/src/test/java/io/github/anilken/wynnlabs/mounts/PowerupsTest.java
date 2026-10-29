package io.github.anilken.wynnlabs.mounts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.model.MountRead;
import io.github.anilken.wynnlabs.mounts.PowerupGroups.Group;
import io.github.anilken.wynnlabs.mounts.PowerupGroups.Part;
import org.junit.jupiter.api.Test;

import java.util.List;

class PowerupsTest {

    private static final double FAR = Double.MAX_VALUE;

    @Test
    void theFingerprintIsSha256OfArgbIntsBigEndianRowByRow() {
        int[][] px = {{0xFF000000, 0x80FF0000}, {0x0000FF00, 0x12345678}};
        // python: sha256(b"".join(struct.pack(">I", p) for row in px for p in row))
        assertEquals("ab61973b27979e354adeac9346991980e55f51846c05d24765c5913223206716",
                PowerupPrint.of(2, 2, (x, y) -> px[y][x]));
    }

    @Test
    void theTableKnowsAllTenPowerupsAndTheirStats() {
        assertEquals(10, PowerupTable.all().size());
        PowerupTable.Kind speed = PowerupTable.of("E1BFF8CFBD3FD657968BC89B944CBB7714067B68F2D3B6D544AF6E2F98CEFE69");
        assertNotNull(speed);
        assertEquals(MountStat.SPEED, speed.stat());
        assertEquals(0xFDDA61, speed.colour());
        PowerupTable.Kind boost = PowerupTable.of("a62a04b132fd96a7616d66618500c8fd84d1a3bbb5290a4b7adc43e78928a8f2");
        assertTrue(boost.energyBoost());
        assertNull(boost.stat());
        assertNull(PowerupTable.of("0000"));
        assertTrue(PowerupTable.all().values().stream().allMatch(PowerupTable.Kind::verified));
    }

    @Test
    void displaysAtOneSpotMakeAPowerupTypedByTheLowestFloat() {
        List<Group<String>> gs = PowerupGroups.of(List.of(
                new Part<>("ring", 10.5, 64, 3.5, 5900f, false, FAR),
                new Part<>("type", 10.5, 64, 3.5, 5833f, false, FAR),
                new Part<>("glow", 10.5, 64, 3.5, 5901f, false, FAR),
                new Part<>("alone", 20.5, 64, 3.5, 5797f, false, FAR),
                new Part<>("near", 10.5, 64, 3.51, 1f, false, FAR)), 3);
        assertEquals(1, gs.size());
        assertEquals("type", gs.get(0).type());
        assertEquals(3, gs.get(0).size());
    }

    @Test
    void theVehiclesOwnPartsAreLeftOut() {
        List<Group<String>> gs = PowerupGroups.of(List.of(
                new Part<>("riding a", 1, 70, 1, 1f, true, 0.5),
                new Part<>("riding b", 1, 70, 1, 2f, true, 0.5),
                new Part<>("close a", 2, 70, 2, 1f, false, 2.9),
                new Part<>("close b", 2, 70, 2, 2f, false, 2.9),
                new Part<>("free a", 9, 70, 9, 3f, false, 3.1),
                new Part<>("free b", 9, 70, 9, 4f, false, 3.1)), 3);
        assertEquals(1, gs.size());
        assertEquals("free a", gs.get(0).type());
    }

    private static MountRead mount(String type, MountRead.Stat... stats) {
        return new MountRead(type, type, 300, 20, 30, List.of(stats), false, "", "");
    }

    @Test
    void aMaxedStatIsDimmedAndTheMountsOwnNameIsUsed() {
        PowerupTable.Kind altitude = new PowerupTable.Kind("Altitude", MountStat.ALTITUDE, 0xF1F1F1, true);
        PowerupTable.Kind speed = new PowerupTable.Kind("Speed", MountStat.SPEED, 0xFDDA61, true);
        MountRead horse = mount("HORSE", new MountRead.Stat("SPEED", "Speed", 21, 21, 40),
                new MountRead.Stat("JUMP_HEIGHT", "Jump Height", 3, 10, 30));
        PowerupLabel.Label jump = PowerupLabel.of(altitude, horse, null);
        assertEquals("Jump Height", jump.text());
        assertFalse(jump.dim());
        assertTrue(PowerupLabel.of(speed, horse, null).dim());
        // past the limit, and an unknown limit never dims
        assertTrue(PowerupLabel.maxed(new MountRead.Stat("SPEED", "Speed", 22, 21, 40)));
        assertFalse(PowerupLabel.maxed(new MountRead.Stat("SPEED", "Speed", 5, 0, 0)));
        // no mount read: the powerup's own name, nothing dimmed
        PowerupLabel.Label bare = PowerupLabel.of(altitude, null, null);
        assertEquals("Altitude", bare.text());
        assertFalse(bare.dim());
    }

    @Test
    void theGradientRunsRedYellowGreen() {
        assertEquals(0xFF0040, PowerupLabel.gradient(0));
        assertEquals(0xFFFF40, PowerupLabel.gradient(0.5));
        assertEquals(0x00FF40, PowerupLabel.gradient(1));
        assertEquals(0xFF8040, PowerupLabel.gradient(0.25));
        assertEquals(0xFF0040, PowerupLabel.gradient(-1));
    }

    @Test
    void energyBoostShowsWhatItWouldGive() {
        PowerupTable.Kind eb = PowerupTable.of("a62a04b132fd96a7616d66618500c8fd84d1a3bbb5290a4b7adc43e78928a8f2");
        MountRead m = mount("WYVERN", new MountRead.Stat("BOOST", "Boost", 10, 12, 40));
        PowerupLabel.Label l = PowerupLabel.of(eb, m, new RideEnergy.Shown(10, 30, false));
        assertEquals("20/35.5 Energy", l.text());
        // less than its headroom: smaller, coloured by 20 / 35.5 on the red → yellow → green scale
        assertEquals(0.75f, l.size());
        assertEquals(PowerupLabel.gradient(20 / 35.5), l.colour());
        // the full headroom: Energy Boost's own colour, normal size
        PowerupLabel.Label full = PowerupLabel.of(eb, m, new RideEnergy.Shown(0, 60, false));
        assertEquals(0x00FF40, full.colour());
        assertEquals(1f, full.size());
        // headroom round1(30 + 7.5) − 2, the gain capped by it
        assertEquals("35.5/35.5 Energy", PowerupLabel.energyGain(m, new RideEnergy.Shown(0, 60, false)));
        // an estimated pool: the name until the item is read, and the label asks for the pool
        RideEnergy.Shown estimate = new RideEnergy.Shown(10, 30, true);
        assertEquals("Energy Boost", PowerupLabel.of(eb, m, estimate).text());
        assertTrue(PowerupLabel.needsPool(eb, estimate));
        assertFalse(PowerupLabel.needsPool(eb, new RideEnergy.Shown(10, 30, false)));
        assertFalse(PowerupLabel.needsPool(PowerupTable.of("e1bff8cfbd3fd657968bc89b944cbb7714067b68f2d3b6d544af6e2f98cefe69"), estimate));
        // no Boost stat: headroom 50
        assertEquals("30/50 Energy", PowerupLabel.energyGain(mount("HORSE"), new RideEnergy.Shown(0, 30, false)));
        // no pool at all: the name
        assertNull(PowerupLabel.energyGain(m, null));
        assertEquals("Energy Boost", PowerupLabel.of(eb, m, null).text());
    }
}
