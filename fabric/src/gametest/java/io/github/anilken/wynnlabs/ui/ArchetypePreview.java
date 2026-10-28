package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.TreePlanner;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import java.lang.reflect.Field;
import java.nio.file.Path;

/**
 * The archetype list in the J menu preview ({@code -PpreviewOnly=archetypes} for these alone):
 * opened from the home's HUD at GUI scale 2, 3 and 4 (large, standard, compact) and from the
 * tree's chip at 3 and 4, with the recording's archetype and again with two combined (the chip's
 * "Summoner + Ritualist", the picked pair's other splits under it).
 */
final class ArchetypePreview {

    private ArchetypePreview() {}

    static void shots(ClientGameTestContext ctx, Path out) throws Exception {
        String before = ctx.computeOnClient(mc -> TreePlanner.chosen());
        for (String choice : new String[] {before, "Summoner+Ritualist"}) {
            String tag = choice.equals(before) ? "" : "-hybrid";
            ctx.runOnClient(mc -> TreePlanner.choose(choice));
            ctx.getInput().setCursorPos(2, 2);
            for (int scale : new int[] {2, 3, 4}) {
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(scale);
                    mc.resizeDisplay();
                    set(mc.screen, "tab", 0);
                });
                ctx.waitTicks(3);
                ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).archPicker.openAt("#qarch:"));
                ctx.waitTicks(3);
                shot(ctx, out, "jmenu-archetypes" + tag + "-gui" + scale);
                ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).archPicker.close());
            }
            for (int scale : new int[] {3, 4}) {
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(scale);
                    mc.resizeDisplay();
                    set(mc.screen, "tab", 9);
                });
                ctx.waitTicks(5);
                ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).archPicker.openAt("#tree:arch"));
                ctx.waitTicks(3);
                shot(ctx, out, "jmenu-archetypes" + tag + "-tree" + (scale == 3 ? "" : "-gui" + scale));
                ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).archPicker.close());
            }
        }
        ctx.runOnClient(mc -> {
            TreePlanner.choose(before);
            mc.options.guiScale().set(3);
            mc.resizeDisplay();
        });
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }

    private static void set(Object o, String field, int value) {
        try {
            Field f = o.getClass().getDeclaredField(field);
            f.setAccessible(true);
            f.setInt(o, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
