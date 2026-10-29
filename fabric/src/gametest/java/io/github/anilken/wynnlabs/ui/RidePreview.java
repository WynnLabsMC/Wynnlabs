package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.model.MountRead;
import io.github.anilken.wynnlabs.mounts.RideWatch;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * -PpreviewOnly=ride: the mount panel on the HUD (forced on, no riding needed): a Wyvern at rest,
 * the same with Speed +1, Handling +2 and Boost −1 showing, a Horse whose maxes are not known
 * (Jump Height, no gold ticks), and the move screen with the panel picked.
 */
final class RidePreview {

    private RidePreview() {}

    private static final String[] IDS = {"SPEED", "ACCELERATION", "ALTITUDE", "ENERGY", "HANDLING", "TOUGHNESS", "BOOST", "TRAINING"};
    private static final String[] NAMES = {"Speed", "Acceleration", "Altitude", "Energy", "Handling", "Toughness", "Boost", "Training"};

    private static MountRead mount(String type, String name, String skin, int[][] v, int energy, int energyMax, boolean estimated) {
        List<MountRead.Stat> stats = new ArrayList<>();
        int pot = 0;
        for (int i = 0; i < IDS.length; i++) {
            boolean jump = type.equals("HORSE") && i == 2;
            stats.add(new MountRead.Stat(jump ? "JUMP_HEIGHT" : IDS[i], jump ? "Jump Height" : NAMES[i], v[i][0], v[i][1], v[i][2]));
            pot += v[i][2];
        }
        return new MountRead(type, name, pot, energy, energyMax, List.copyOf(stats), estimated, "Azure", "", skin, null);
    }

    static MountRead wyvern() {
        return mount("WYVERN", "Wyvern", "Azure-Horn", new int[][] {{12, 21, 58}, {21, 21, 44}, {9, 18, 52}, {15, 19, 40},
            {6, 14, 35}, {18, 18, 47}, {4, 12, 38}, {30, 30, 30}}, 18, 30, false);
    }

    static MountRead horse() {
        return mount("HORSE", "Chestnut Horse", null, new int[][] {{8, 15, 30}, {5, 15, 30}, {3, 10, 30}, {10, 10, 30},
            {7, 12, 30}, {4, 9, 30}, {2, 8, 30}, {14, 20, 30}}, 9, 24, true);
    }

    static void shots(ClientGameTestContext ctx, Path out) {
        ctx.runOnClient(mc -> {
            mc.setScreen(null);
            RideWatch.preview(wyvern(), null, null);
        });
        ctx.waitTicks(10);
        shot(ctx, out, "ride-wyvern");
        ctx.runOnClient(mc -> RideWatch.preview(wyvern(), new String[] {"SPEED", "HANDLING", "BOOST"}, new int[] {1, 2, -1}));
        ctx.waitTicks(5);
        shot(ctx, out, "ride-flash");
        ctx.runOnClient(mc -> RideWatch.preview(horse(), null, null));
        ctx.waitTicks(5);
        shot(ctx, out, "ride-horse");
        ctx.runOnClient(mc -> {
            RideWatch.preview(wyvern(), null, null);
            mc.setScreen(new HudEditScreen(null, RidePanel.ID));
        });
        ctx.waitTicks(5);
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(5);
        shot(ctx, out, "ride-move");
        ctx.runOnClient(mc -> {
            mc.setScreen(null);
            RideWatch.preview(null, null, null);
        });
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }
}
