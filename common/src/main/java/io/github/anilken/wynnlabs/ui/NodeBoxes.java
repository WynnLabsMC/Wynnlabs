package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.GatherNode;
import io.github.anilken.wynnlabs.search.Area;
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
 * 1 one colour, 2 rainbow). The nodes the game shows now get a bright box; the nodes of the map
 * data further off (up to {@link #REACH} blocks) a faint one, so a field's extent shows before its
 * labels load. Drawn as the game's own see-through debug boxes, made each tick; nothing is sent.
 */
public final class NodeBoxes {

    /** How far (blocks) the map data's nodes get a faint box. */
    static final int REACH = 100;
    /** At most this many faint boxes, the nearest first. */
    private static final int MAX_FAR = 300;
    private static final int GOLD = 0xFFE071;

    /** Profession ("mining") → the map data's nodes of its resources; read again when the item index changes. */
    private static final Map<String, List<Area>> FAR = new HashMap<>();
    /** Resource ("SILVER") → its profession. */
    private static final Map<String, String> PROFESSION = new HashMap<>();
    private static ItemIndex readFor;

    private NodeBoxes() {}

    public static void onTick(Minecraft mc) {
        int mode = Settings.choice("nodeBoxes");
        if (++ticks % 40 == 0) {
            wynntilsBoxes(mode != 0);
        }
        if (mode == 0 || mc.level == null || mc.player == null) {
            return;
        }
        List<String> held;
        try {
            held = Wyn.heldToolProfessions();
        } catch (Throwable t) {
            return;
        }
        if (held.isEmpty()) {
            return;
        }
        read();
        long now = System.currentTimeMillis();
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        try {
            // the nodes the game shows: bright, where the node's block is under its label
            List<int[]> live = new ArrayList<>();
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
                live.add(new int[] {at.getX(), at.getZ()});
                box(at, colour(mode, at, now, 0xFF));
            }
            // the map's nodes further off: faint, the nearest first
            List<Area> far = new ArrayList<>();
            for (String prof : held) {
                for (Area a : FAR.getOrDefault(prof, List.of())) {
                    double dx = a.x() - px;
                    double dz = a.z() - pz;
                    if (dx * dx + dz * dz <= (double) REACH * REACH && Math.abs(a.y() - py) < 48 && !near(live, a)) {
                        far.add(a);
                    }
                }
            }
            far.sort(java.util.Comparator.comparingDouble(a -> (a.x() - px) * (a.x() - px) + (a.z() - pz) * (a.z() - pz)));
            for (int i = 0; i < far.size() && i < MAX_FAR; i++) {
                BlockPos at = new BlockPos(far.get(i).x(), far.get(i).y(), far.get(i).z());
                box(at, colour(mode, at, now, 0x60));
            }
        } catch (Throwable ignored) {
            // no tick collector, or no data yet: nothing drawn this tick
        }
    }

    public static void reset() {
        readFor = null;
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

    /** Whether a map node is where the game already shows one (its box would double). */
    private static boolean near(List<int[]> live, Area a) {
        for (int[] l : live) {
            if (Math.abs(l[0] - a.x()) <= 2 && Math.abs(l[1] - a.z()) <= 2) {
                return true;
            }
        }
        return false;
    }

    /** The resources' professions and the map data's nodes per profession, once per item index. */
    private static void read() {
        ItemIndex index = ItemIndex.get();
        if (index == readFor) {
            return;
        }
        readFor = index;
        FAR.clear();
        PROFESSION.clear();
        for (Entry e : index.entries()) {
            if (e.kind() != Kind.RESOURCE || e.type() == null) {
                continue;
            }
            String prof = e.type().toLowerCase(Locale.ROOT);
            PROFESSION.put(e.name().toUpperCase(Locale.ROOT), prof);
            FAR.computeIfAbsent(prof, k -> new ArrayList<>()).addAll(index.places().nodes(e.name()));
        }
    }
}
