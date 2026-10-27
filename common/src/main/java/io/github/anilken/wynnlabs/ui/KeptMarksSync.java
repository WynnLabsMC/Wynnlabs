package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.search.KeptMarks;

import net.minecraft.client.Minecraft;

/**
 * Puts the kept search marks on Wynntils' map when the menu is not open: after a restart, a
 * character change, or a change made elsewhere. Once a second it compares the store and its
 * version with what it last handed over; the menu's search card does the rest while it shows.
 */
public final class KeptMarksSync {

    private static KeptMarks shownStore;
    private static int shownVersion = -1;

    private KeptMarksSync() {}

    public static void onTick(Minecraft client) {
        if (client.player == null || client.player.tickCount % 20 != 0 || client.screen instanceof RoadmapScreen) {
            return;
        }
        KeptMarks k = KeptMarks.get();
        int v = k.version();
        if (k == shownStore && v == shownVersion) {
            return;
        }
        shownStore = k;
        shownVersion = v;
        Wyn.showMarks(k.all());
    }
}
