package io.github.anilken.wynnlabs.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.MaterialKind;
import io.github.anilken.wynnlabs.mounts.MountMaterials;
import io.github.anilken.wynnlabs.mounts.MountStats;
import org.junit.jupiter.api.Test;

import java.util.List;

class MountTipsTest {

    private final Material copper = MountMaterials.of(MaterialKind.INGOT, 1);

    @Test
    void aMaterialSaysWhatItGivesEachFeederRowTheBiggestFirst() {
        MountStats fresh = MountStats.fresh();
        int[] g = fresh.gainsFrom(copper);
        List<MountTips.Line> lines = MountTips.texts(copper,
                List.of(new MountTips.Fed(4, "Ivory", fresh), new MountTips.Fed(2, "Crimson", fresh)), 2);
        assertEquals(2, lines.size());
        assertEquals(2, lines.get(0).row(), "equal gains: the lower row first");
        assertEquals("Crimson", lines.get(0).name());
        assertTrue(lines.get(0).gains().startsWith("+"), lines.get(0).gains());
        assertTrue(lines.get(0).gains().contains("Toughness") || lines.get(0).gains().contains("Energy"), lines.get(0).gains());
        assertTrue(g[io.github.anilken.wynnlabs.mounts.MountStat.TOUGHNESS.ordinal()] > 0);
    }

    @Test
    void aMountThatGainsNothingIsLeftOutAndTwoLinesAtMost() {
        MountStats full = MountStats.uniform(30, 30, 30);
        MountStats fresh = MountStats.fresh();
        List<MountTips.Line> lines = MountTips.texts(copper, List.of(new MountTips.Fed(1, "Azure", full),
                new MountTips.Fed(2, "Crimson", fresh), new MountTips.Fed(3, "Ivory", fresh), new MountTips.Fed(4, "Pearl", fresh)), 2);
        assertEquals(2, lines.size());
        assertTrue(lines.stream().noneMatch(l -> l.name().equals("Azure")), "full limits: nothing to gain");
    }
}
