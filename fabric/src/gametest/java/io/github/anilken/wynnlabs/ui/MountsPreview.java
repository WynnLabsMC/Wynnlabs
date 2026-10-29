package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.model.MountRead;
import io.github.anilken.wynnlabs.mounts.FeedGoal;
import io.github.anilken.wynnlabs.mounts.FeedPlan;
import io.github.anilken.wynnlabs.mounts.FeedPlanner;
import io.github.anilken.wynnlabs.mounts.MountBook;
import io.github.anilken.wynnlabs.mounts.MountType;
import io.github.anilken.wynnlabs.mounts.RoadMap;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * -PpreviewOnly=mounts: the Mounts tab on four seeded mounts (two Wyverns partly fed, a fresh
 * Wyvern, a fresh Horse): the road map (low, high, the options menu, the first step opened), the
 * mounts with the first picked, the type menu, the places, and the empty page with no mount read.
 */
final class MountsPreview {

    private MountsPreview() {}

    private static final String[] IDS = {"SPEED", "ACCELERATION", "ALTITUDE", "ENERGY", "HANDLING", "TOUGHNESS", "BOOST", "TRAINING"};
    private static final String[] NAMES = {"Speed", "Acceleration", "Altitude", "Energy", "Handling", "Toughness", "Boost", "Training"};

    private static MountRead mount(String type, String name, int[] level, int[] limit, int[] max, int energy, String primary) {
        List<MountRead.Stat> stats = new ArrayList<>();
        int potential = 0;
        for (int i = 0; i < IDS.length; i++) {
            boolean horse = type.equals("HORSE") && i == 2;
            stats.add(new MountRead.Stat(horse ? "JUMP_HEIGHT" : IDS[i], horse ? "Jump Height" : NAMES[i], level[i], limit[i], max[i]));
            potential += max[i];
        }
        return new MountRead(type, name, potential, energy, 30, List.copyOf(stats), false, primary, "");
    }

    private static int[] all(int v) {
        int[] a = new int[8];
        java.util.Arrays.fill(a, v);
        return a;
    }

    /** The seeded mounts, read a few minutes apart. */
    static List<MountBook.Seen> mounts() {
        long now = System.currentTimeMillis();
        return List.of(
                new MountBook.Seen(mount("WYVERN", "Azure Wyvern", new int[] {14, 12, 30, 9, 8, 11, 10, 12},
                        new int[] {22, 21, 32, 20, 19, 20, 18, 21}, new int[] {58, 52, 61, 54, 50, 57, 53, 53}, 18, "Azure"),
                        MountBook.Where.ENCLOSURE, now - 12 * 60_000),
                new MountBook.Seen(mount("WYVERN", "Crimson Wyvern", new int[] {10, 9, 20, 8, 7, 9, 8, 9},
                        new int[] {18, 17, 21, 16, 15, 17, 16, 17}, new int[] {55, 50, 52, 48, 51, 53, 52, 51}, 22, "Crimson"),
                        MountBook.Where.BANK, now - 40 * 60_000),
                new MountBook.Seen(mount("WYVERN", "Ivory Wyvern", all(1), all(10), all(30), 30, "Ivory"),
                        MountBook.Where.INVENTORY, now - 3 * 60_000),
                new MountBook.Seen(mount("HORSE", "Chestnut Horse", all(1), all(10), all(30), 30, "Chestnut"),
                        MountBook.Where.INVENTORY, now - 3 * 60_000));
    }

    private static final long H = 3600;

    /** The Mounts card and the road map's "Now" with the exact plan in one state (the planner's estimate as its plan). */
    private static MountsModel.FeedView view(MountsModel.FeedState state, boolean proven) {
        MountsModel.Owned crimson = null;
        MountsModel model = new MountsModel();
        for (MountsModel.Owned o : model.owned()) {
            if (o.mount().name().startsWith("Crimson")) {
                crimson = o;
            }
        }
        var m = crimson.mount();
        FeedPlan est = FeedPlanner.estimate(m.stats(), FeedGoal.breedReady(), RoadMap.Options.defaults(MountType.WYVERN).feedOptions());
        FeedPlan plan = new FeedPlan(est.goalReached(), proven, est.steps(), est.totalTimeSec(), est.feedTimeSec(), est.trainingTimeSec(),
                est.coveredTrainingSec(), est.end(), est.explored(), est.source());
        long saved = System.currentTimeMillis() - 2 * H * 1000;
        return new MountsModel.FeedView(plan, state, state == MountsModel.FeedState.RUNNING ? 0 : saved,
                state == MountsModel.FeedState.RUNNING ? 4_210 : 0);
    }

    static void shots(ClientGameTestContext ctx, Path out) {
        ctx.runOnClient(mc -> {
            MountBook.preview(mounts());
            MountsModel.Options o = MountsModel.options();
            o.type = "WYVERN";
            o.target = 700;
            ((RoadmapScreen) mc.screen).openTab(RoadmapScreen.TAB_MOUNT_ROAD);
        });
        // the road map's estimates come from the planner thread (about a second the first time)
        ctx.waitTicks(120);
        shot(ctx, out, "mounts-road-700");
        shopHover(ctx, out);
        step(ctx, out, "mounts-road-step", t -> t.road.now.open = true);
        step(ctx, out, "mounts-road-menu", t -> {
            t.road.now.open = false;
            t.toggle(MountsTab.OPTIONS_MENU);
        });
        ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).mounts.toggle(MountsTab.TARGET_MENU));
        ctx.waitTicks(120);         // each target's road is asked once, then kept
        shot(ctx, out, "mounts-target-menu");
        ctx.runOnClient(mc -> {
            ((RoadmapScreen) mc.screen).mounts.open = MountsTab.NONE;
            MountsModel.options().target = 1200;
        });
        ctx.waitTicks(120);
        shot(ctx, out, "mounts-road-1200");
        ctx.runOnClient(mc -> MountsModel.options().target = 700);
        // the exact plan's four states, on the road map's "Now" and on the Mounts card
        String[] names = {"none", "running", "cached", "stale"};
        MountsModel.FeedState[] states = {MountsModel.FeedState.NONE, MountsModel.FeedState.RUNNING, MountsModel.FeedState.CACHED,
            MountsModel.FeedState.STALE};
        for (int i = 0; i < states.length; i++) {
            MountsModel.FeedState st = states[i];
            step(ctx, out, "mounts-feed-" + names[i], t -> {
                MountsModel.previewView = view(st, st == MountsModel.FeedState.CACHED);
                t.menu.openTab(RoadmapScreen.TAB_MOUNT_ROAD);
            });
            step(ctx, out, "mounts-card-" + names[i], t -> t.menu.openTab(RoadmapScreen.TAB_MOUNTS));
        }
        ctx.runOnClient(mc -> MountsModel.previewView = null);
        step(ctx, out, "mounts-list", t -> t.menu.openTab(RoadmapScreen.TAB_MOUNTS));
        step(ctx, out, "mounts-type-menu", t -> {
            t.menu.openTab(RoadmapScreen.TAB_MOUNT_ROAD);
            t.toggle(MountsTab.TYPE_MENU);
        });
        step(ctx, out, "mounts-places", t -> {
            t.open = MountsTab.NONE;
            t.menu.openTab(RoadmapScreen.TAB_MOUNT_PLACES);
        });
        step(ctx, out, "mounts-empty", t -> {
            MountBook.preview(List.of());
            t.menu.openTab(RoadmapScreen.TAB_MOUNTS);
        });
        ctx.runOnClient(mc -> {
            MountBook.preview(null);
            MountsModel.previewRoad = null;
            MountsModel.previewView = null;
        });
    }

    /**
     * The shopping list's first food row: its tooltip (inside the window), the copy button's
     * tooltip, and the check right after a click on it. Nothing when the list is not shown (small).
     */
    private static void shopHover(ClientGameTestContext ctx, Path out) {
        int[] row = ctx.computeOnClient(mc -> ((RoadmapScreen) mc.screen).mounts.road.now.shopRowAt);
        if (row == null) {
            return;
        }
        double s = ctx.computeOnClient(mc -> (double) MenuScale.effective());
        ctx.getInput().setCursorPos((row[0] + row[2] / 3.0) * s, (row[1] + row[3] / 2.0) * s);
        ctx.waitTicks(5);
        shot(ctx, out, "mounts-shop-tip");
        ctx.getInput().setCursorPos((row[0] + row[2] - 8) * s, (row[1] + row[3] / 2.0) * s);
        ctx.waitTicks(5);
        shot(ctx, out, "mounts-shop-copy");
        ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(2);
        shot(ctx, out, "mounts-shop-copied");
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(2);
    }

    private static void step(ClientGameTestContext ctx, Path out, String name, Consumer<MountsTab> change) {
        ctx.runOnClient(mc -> change.accept(((RoadmapScreen) mc.screen).mounts));
        ctx.waitTicks(10);
        shot(ctx, out, name);
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }
}
