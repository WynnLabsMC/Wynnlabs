package io.github.anilken.wynnlabs.mounts;

import io.github.anilken.wynnlabs.game.ItemJson;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The floating name over the feeder the player opened ("Click to Open Feeder" / "Guild Universal
 * Feeder"): the feeder label nearest the player's line of sight, a few blocks away at most.
 */
final class FeederLabel {

    private static final double REACH = 8;

    private FeederLabel() {}

    /** The label's name line ("Guild Universal Feeder"), its whole text when it has none; null when none is near. */
    static String read(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = mc.player.getViewVector(1f);
        List<Display.TextDisplay> near = new ArrayList<>();
        Display.TextDisplay best = null;
        double bestDot = -2;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Display.TextDisplay t) || t.getText() == null) {
                continue;
            }
            Vec3 to = e.position().subtract(eye);
            double d = to.length();
            if (d > REACH || d < 1e-3 || !text(t).toLowerCase(Locale.ROOT).contains("feeder")) {
                continue;
            }
            near.add(t);
            double dot = to.scale(1 / d).dot(look);
            if (dot > bestDot) {
                bestDot = dot;
                best = t;
            }
        }
        if (best == null) {
            return null;
        }
        // the name may be a display of its own, right above or under "Click to Open Feeder" (never a
        // neighbouring feeder's, a block or more to the side)
        for (Display.TextDisplay t : near) {
            if (t != best && Math.abs(t.getX() - best.getX()) < 0.6 && Math.abs(t.getZ() - best.getZ()) < 0.6
                    && Math.abs(t.getY() - best.getY()) < 1.5 && text(t).toLowerCase(Locale.ROOT).contains("universal")) {
                return nameLine(text(t));
            }
        }
        return nameLine(text(best));
    }

    private static String text(Display.TextDisplay t) {
        return t.getText().getString();
    }

    /** The line that names the feeder: the one after "Click to Open Feeder", or the text itself. */
    static String nameLine(String text) {
        String[] lines = text.split("\n");
        for (String l : lines) {
            String s = ItemJson.clean(l);           // per line: cleaning folds line breaks into spaces
            if (!s.isEmpty() && !s.toLowerCase(Locale.ROOT).startsWith("click")) {
                return s;
            }
        }
        return ItemJson.clean(text);
    }
}
