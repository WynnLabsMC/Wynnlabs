package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.GatherNode;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.Kind;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Boxes around gathering nodes while a gathering tool is in hand (setting nodeBoxes: 0 off,
 * 1 one colour, 2 rainbow): only the nodes the game shows now, since the map data's node
 * positions are out of date. Drawn as the game's own see-through debug boxes, made each tick;
 * nothing is sent.
 */
public final class NodeBoxes {

    private static final int GOLD = 0xFFE071;

    /** Resource ("SILVER") → its profession; read again when the item index changes. */
    private static final Map<String, String> PROFESSION = new HashMap<>();
    private static ItemIndex readFor;

    private NodeBoxes() {}

    public static void onTick(Minecraft mc) {
        int mode = Settings.choice("nodeBoxes");
        if (++ticks % 40 == 0) {
            wynntilsBoxes(mode != 0);
        }
        if (mode == 0 || mc.level == null || mc.player == null) {
            LIVE.clear();
            return;
        }
        List<String> held;
        try {
            held = Wyn.heldToolProfessions();
        } catch (Throwable t) {
            return;
        }
        if (held.isEmpty()) {
            LIVE.clear();
            return;
        }
        read();
        try {
            // the nodes the game shows: bright, where the node's block is under its label
            List<BlockPos> liveAt = new ArrayList<>();
            for (GatherNode n : Wyn.gatherNodes()) {
                String prof = PROFESSION.get(n.resource().toUpperCase(Locale.ROOT));
                if (prof == null || !held.contains(prof)) {
                    continue;
                }
                int down = switch (prof) {
                    case "mining", "fishing" -> 1;
                    case "farming" -> 2;
                    default -> 0;
                };
                BlockPos at = new BlockPos(n.x(), n.y() - down, n.z());
                liveAt.add(at);
            }
            LIVE.clear();
            LIVE.addAll(liveAt);
        } catch (Throwable t) {
            LIVE.clear();
        }
    }

    public static void reset() {
        readFor = null;
        LIVE.clear();
    }

    private static int ticks;
    /** Whether this session turned Wynntils' white node boxes off (they are put back when ours go off). */
    private static boolean tookWynntils;

    /**
     * Ours on: Wynntils' own white boxes off, so the two do not sit on top of each other. Ours
     * off: theirs back on, but only when it was us who turned them off.
     */
    private static void wynntilsBoxes(boolean ours) {
        try {
            Boolean theirs = Wyn.wynntilsNodeHighlight(null);
            if (theirs == null) {
                return;
            }
            if (ours && theirs) {
                Wyn.wynntilsNodeHighlight(false);
                tookWynntils = true;
            } else if (!ours && tookWynntils && !theirs) {
                Wyn.wynntilsNodeHighlight(true);
                tookWynntils = false;
            }
        } catch (Throwable ignored) {
            // Wynntils changed: leave its setting alone
        }
    }

    /** What the last tick picked. Read on the render thread. */
    private static final List<BlockPos> LIVE = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Draws the picked boxes as the level render starts (while the game takes debug boxes), the rainbow moving each frame. */
    public static void drawFrame() {
        int mode = Settings.choice("nodeBoxes");
        if (mode == 0 || LIVE.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            for (BlockPos at : LIVE) {
                box(at, colour(mode, at, now, 0xFF));
            }
        } catch (IllegalStateException e) {
            // no collector at this point of the frame: nothing drawn
        }
    }

    private static void box(BlockPos at, int argb) {
        Gizmos.cuboid(at, GizmoStyle.stroke(argb)).setAlwaysOnTop();
    }

    /** One colour, or a rainbow that runs over time and shifts along the ground. */
    static int colour(int mode, BlockPos at, long now, int alpha) {
        int rgb = GOLD;
        if (mode == 2) {
            float hue = (float) (((now % 3000L) / 3000.0 + (at.getX() + at.getZ()) * 0.02) % 1.0);
            rgb = Mth.hsvToRgb(hue < 0 ? hue + 1 : hue, 0.85f, 1f);
        }
        return alpha << 24 | (rgb & 0xFFFFFF);
    }

    /** The resources' professions, once per item index. */
    private static void read() {
        ItemIndex index = ItemIndex.get();
        if (index == readFor) {
            return;
        }
        readFor = index;
        PROFESSION.clear();
        for (Entry e : index.all()) {
            if (e.kind() == Kind.RESOURCE && e.type() != null) {
                PROFESSION.put(e.name().toUpperCase(Locale.ROOT), e.type().toLowerCase(Locale.ROOT));
            }
        }
    }
}
