package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.model.MountRead;
import io.github.anilken.wynnlabs.mounts.PowerupLabel;
import io.github.anilken.wynnlabs.mounts.PowerupTable;
import io.github.anilken.wynnlabs.mounts.Powerups;
import io.github.anilken.wynnlabs.mounts.RideWatch;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * -PpreviewOnly=powerups: the powerup labels drawn by the real world pass over the test world,
 * one of each of the ten powerups spread in front of the player at 6 to 40 blocks, for a ridden
 * Wyvern (Acceleration, Toughness and Training maxed: dimmed) and a Horse (Altitude reads
 * "Jump Height"). No powerup models: the labels stand on their own.
 */
final class PowerupPreview {

    private PowerupPreview() {}

    static void shots(ClientGameTestContext ctx, Path out) {
        ctx.runOnClient(mc -> {
            mc.setScreen(null);
            mc.player.setXRot(10);
            mc.player.setYRot(0);
        });
        ctx.waitTicks(5);
        scene(ctx, RidePreview.wyvern());
        shot(ctx, out, "powerups-wyvern");
        scene(ctx, RidePreview.horse());
        shot(ctx, out, "powerups-horse");
        ctx.runOnClient(mc -> {
            Powerups.preview(null);
            RideWatch.preview(null, null, null);
        });
    }

    private static void scene(ClientGameTestContext ctx, MountRead mount) {
        ctx.runOnClient(mc -> {
            RideWatch.preview(mount, null, null);
            Vec3 eye = mc.player.getEyePosition();
            Vec3 ahead = Vec3.directionFromRotation(0, mc.player.getYRot());
            Vec3 right = new Vec3(-ahead.z, 0, ahead.x);
            List<PowerupTable.Kind> kinds = new ArrayList<>(PowerupTable.all().values());
            kinds.sort(Comparator.comparing(PowerupTable.Kind::name));
            double[][] spots = {{6, -3}, {9, 5}, {12, -8}, {15, 10}, {18, -14}, {22, 2}, {26, 16}, {30, -10}, {35, 7}, {40, -20}};
            List<Powerups.Shown> labels = new ArrayList<>();
            for (int i = 0; i < kinds.size(); i++) {
                PowerupLabel.Label l = PowerupLabel.of(kinds.get(i), mount, RideWatch.energy());
                Vec3 at = eye.add(ahead.scale(spots[i][0])).add(right.scale(spots[i][1])).add(0, -1.5, 0);    // on the ground (eye 1.62 above it); the label 3.5 above
                labels.add(new Powerups.Shown(at.x, at.y, at.z, l.text(), l.colour(), l.dim(), l.size()));
            }
            Powerups.preview(labels);
        });
        ctx.waitTicks(5);
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }
}
