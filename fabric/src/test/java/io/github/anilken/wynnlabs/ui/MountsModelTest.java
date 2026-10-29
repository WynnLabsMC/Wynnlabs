package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.anilken.wynnlabs.model.MountRead;
import io.github.anilken.wynnlabs.mounts.Mount;
import io.github.anilken.wynnlabs.mounts.MountStat;
import io.github.anilken.wynnlabs.mounts.MountType;
import io.github.anilken.wynnlabs.mounts.Range;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class MountsModelTest {

    private static final String[] IDS = {"SPEED", "ACCELERATION", "ALTITUDE", "ENERGY", "HANDLING", "TOUGHNESS", "BOOST", "TRAINING"};

    private static MountRead read(String type, String altitudeId) {
        List<MountRead.Stat> stats = new ArrayList<>();
        for (int i = 0; i < IDS.length; i++) {
            String id = i == 2 ? altitudeId : IDS[i];
            stats.add(new MountRead.Stat(id, id, i + 1, 10 + i, 30 + i));
        }
        return new MountRead(type, "Test " + type, 0, 10, 30, stats, false, "", "");
    }

    @Test
    void aWyvernsStatsLandInThePlannersOrder() {
        Mount m = MountsModel.convert(read("WYVERN", "ALTITUDE"));
        assertEquals(MountType.WYVERN, m.type());
        assertEquals(3, m.stats().get(MountStat.ALTITUDE).level());
        assertEquals(17, m.stats().get(MountStat.TRAINING).limit());
        assertEquals(30 * 8 + 28, m.potential());
    }

    @Test
    void aHorsesJumpHeightIsItsAltitude() {
        Mount m = MountsModel.convert(read("HORSE", "JUMP_HEIGHT"));
        assertEquals(12, m.stats().get(MountStat.ALTITUDE).limit());
    }

    @Test
    void aMountMissingAStatOrOfAnUnknownTypeIsLeftOut() {
        MountRead r = read("WYVERN", "ALTITUDE");
        MountRead seven = new MountRead(r.type(), r.name(), 0, 0, 0, r.stats().subList(0, 7), false, "", "");
        assertNull(MountsModel.convert(seven));
        assertNull(MountsModel.convert(read("DRAGON", "ALTITUDE")));
    }

    @Test
    void theKeyStaysWhileItIsFedButNotAcrossMounts() {
        MountRead a = read("WYVERN", "ALTITUDE");
        List<MountRead.Stat> fed = new ArrayList<>();
        for (MountRead.Stat s : a.stats()) {
            fed.add(new MountRead.Stat(s.id(), s.name(), s.level() + 1, s.limit() + 2, s.max()));
        }
        MountRead later = new MountRead(a.type(), a.name(), a.potential(), 5, 30, fed, false, "", "");
        assertEquals(a.key(), later.key());
        assertNotEquals(a.key(), read("HORSE", "JUMP_HEIGHT").key());
    }

    @Test
    void estimatesAreAlwaysRanges() {
        assertEquals("~620–700", MountsText.range(new Range(620, 700)));
        assertEquals("~700", MountsText.range(Range.of(700)));
    }
}
