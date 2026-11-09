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
    /** How close (blocks across) a map node and a shown one must be to count as the same node. */
    private static final int MATCH = 4;
    /** The last measured height shift, kept for when no node is seen both ways. */
    private static int lastShift;
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
            LIVE.clear();
            FAR_NOW.clear();
            LABEL_AT.clear();
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
            FAR_NOW.clear();
            LABEL_AT.clear();
            return;
        }
        read();
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        try {
            // the nodes the game shows: bright, where the node's block is under its label
            List<int[]> live = new ArrayList<>();
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
                live.add(new int[] {at.getX(), at.getZ(), at.getY()});
                liveAt.add(at);
            }
            // the map's nodes further off: faint, the nearest first. A node whose label the game
            // showed this session and no longer does, though it is near enough for its label to
            // load, has just been gathered: like the game's own boxes, it gets none until it is back
            double loaded = 0;
            for (int[] l : live) {
                loaded = Math.max(loaded, Math.hypot(l[0] - px, l[1] - pz));
            }
            List<Area> far = new ArrayList<>();
            for (String prof : held) {
                for (Area a : FAR.getOrDefault(prof, List.of())) {
                    double dx = a.x() - px;
                    double dz = a.z() - pz;
                    if (dx * dx + dz * dz > (double) REACH * REACH || Math.abs(a.y() - py) >= 48) {
                        continue;
                    }
                    long key = key(a);
                    if (near(live, a)) {
                        SEEN.add(key);
                        continue;
                    }
                    if (SEEN.contains(key) && Math.sqrt(dx * dx + dz * dz) <= loaded + MATCH) {
                        continue;                       // gathered: back when its label is
                    }
                    far.add(a);
                }
            }
            far.sort(java.util.Comparator.comparingDouble(a -> (a.x() - px) * (a.x() - px) + (a.z() - pz) * (a.z() - pz)));
            // the map data's heights sit off from where the game shows the nodes (a fishing node a block
            // under the water): the shift is measured on the nodes seen both ways and put on the rest
            int shift = shift(held, live);
            lastShift = shift;
            List<BlockPos> farAt = new ArrayList<>();
            List<BlockPos> labelAt = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < far.size() && i < MAX_FAR; i++) {
                BlockPos at = new BlockPos(far.get(i).x(), far.get(i).y() + shift, far.get(i).z());
                farAt.add(at);
                // one name for each little group of nodes, so the field does not fill with text
                String name = NAME.get(key(far.get(i)));
                boolean named = false;
                for (BlockPos l : labelAt) {
                    named |= Math.abs(l.getX() - at.getX()) <= LABEL_GAP && Math.abs(l.getZ() - at.getZ()) <= LABEL_GAP;
                }
                if (name != null && !named) {
                    labelAt.add(at);
                    labels.add(name);
                }
            }
            LABEL_AT.clear();
            LABEL_AT.addAll(labelAt);
            LABELS.clear();
            LABELS.addAll(labels);
            LIVE.clear();
            LIVE.addAll(liveAt);
            FAR_NOW.clear();
            FAR_NOW.addAll(farAt);
        } catch (Throwable t) {
            LIVE.clear();
            FAR_NOW.clear();
        }
    }

    public static void reset() {
        readFor = null;
        SEEN.clear();
        LIVE.clear();
        FAR_NOW.clear();
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

    /** What the last tick picked: the bright boxes and the faint ones. Read on the render thread. */
    private static final List<BlockPos> LIVE = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<BlockPos> FAR_NOW = new java.util.concurrent.CopyOnWriteArrayList<>();

    /** Draws the picked boxes as the level render starts (while the game takes debug boxes), the rainbow moving each frame. */
    public static void drawFrame() {
        int mode = Settings.choice("nodeBoxes");
        if (mode == 0 || LIVE.isEmpty() && FAR_NOW.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        try {
            for (BlockPos at : LIVE) {
                box(at, colour(mode, at, now, 0xFF));
            }
            for (BlockPos at : FAR_NOW) {
                box(at, colour(mode, at, now, 0x60));
            }
            for (int i = 0; i < LABEL_AT.size() && i < LABELS.size(); i++) {
                BlockPos at = LABEL_AT.get(i);
                Gizmos.billboardText(LABELS.get(i), net.minecraft.world.phys.Vec3.atLowerCornerWithOffset(at, 0.5, 1.4, 0.5),
                        net.minecraft.gizmos.TextGizmo.Style.forColorAndCentered(colour(mode, at, now, 0xD0))).setAlwaysOnTop();
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

    /** Whether a map node is where the game already shows one (its box would double). */
    private static boolean near(List<int[]> live, Area a) {
        return match(live, a) != null;
    }

    /** The faint boxes' names, one for each little group, and where they go. Read on the render thread. */
    private static final List<BlockPos> LABEL_AT = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final List<String> LABELS = new java.util.concurrent.CopyOnWriteArrayList<>();
    /** How close (blocks across) faint boxes share one name. */
    private static final int LABEL_GAP = 8;
    /** A map node's resource name ("Salmon"), by position. */
    private static final Map<Long, String> NAME = new HashMap<>();

    /** Map nodes whose label the game showed this session, by position. */
    private static final java.util.Set<Long> SEEN = new java.util.HashSet<>();

    private static long key(Area a) {
        return ((long) a.x() & 0x3FFFFFF) << 38 | ((long) a.z() & 0x3FFFFFF) << 12 | (a.y() & 0xFFF);
    }

    /** The node the game shows for a map node (within {@link #MATCH} blocks across), or null. */
    private static int[] match(List<int[]> live, Area a) {
        for (int[] l : live) {
            if (Math.abs(l[0] - a.x()) <= MATCH && Math.abs(l[1] - a.z()) <= MATCH) {
                return l;
            }
        }
        return null;
    }

    /** How far a map node shown by the game sits below its box, most often; 0 when none is seen both ways. */
    private static int shift(List<String> held, List<int[]> live) {
        Map<Integer, Integer> votes = new HashMap<>();
        for (String prof : held) {
            for (Area a : FAR.getOrDefault(prof, List.of())) {
                int[] l = match(live, a);
                if (l != null && Math.abs(l[2] - a.y()) <= 8) {
                    votes.merge(l[2] - a.y(), 1, Integer::sum);
                }
            }
        }
        return votes.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(lastShift);
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
        NAME.clear();
        for (Entry e : index.all()) {
            if (e.kind() != Kind.RESOURCE || e.type() == null) {
                continue;
            }
            String prof = e.type().toLowerCase(Locale.ROOT);
            PROFESSION.put(e.name().toUpperCase(Locale.ROOT), prof);
            List<Area> nodes = index.places().nodes(e.name());
            FAR.computeIfAbsent(prof, k -> new ArrayList<>()).addAll(nodes);
            for (Area a : nodes) {
                NAME.put(key(a), e.name());
            }
        }
    }
}
