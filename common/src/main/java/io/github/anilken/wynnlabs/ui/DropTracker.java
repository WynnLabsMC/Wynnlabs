package io.github.anilken.wynnlabs.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.search.TrackedMobs;

import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Tracked items on the ground ("Track" on an item's search card, setting trackedDrops): a short
 * beam in the item's name colour above each matching drop (the loot chests' beam, {@link Beams})
 * and, for the nearest {@link #NAME_CAP} within {@link #NAME_RANGE} blocks that the camera can
 * see, the item's name ("×3" for a stack) floating just above it, like the powerup labels. A drop
 * matches by its cleaned hover name ({@link TrackedMobs#itemKey}); a new one nearby gets the
 * tracked mobs' notice ("Bob's Tear nearby · 12 m").
 *
 * <p>Read-only: it only looks at item entities the client already has. The world is scanned
 * four times a second; each frame only draws the last scan's list. Everything is depth tested.
 */
public final class DropTracker {

    /** Drops farther than this get no beam; the last 15% fade out. */
    static final double RANGE = 64;
    /** At most this many beams, the nearest first. */
    static final int CAP = 30;
    /** Names only this close... */
    static final double NAME_RANGE = 32;
    /** ...and only for the nearest this many in sight. */
    static final int NAME_CAP = 20;
    /** A notice for the same item at most this often. */
    private static final long TELL_MS = 30_000;
    private static final float HEIGHT = 8f;
    /** The name floats this far above the drop. */
    private static final double LIFT = 0.9;

    /** What a drop's stack says, kept per entity id while its count stays the same. */
    private record Look(int count, String key, String text, int width, int colour) {}

    /** One drop to draw: the entity, its colour and its name (null: in range for a beam only). */
    private record Marked(ItemEntity entity, int colour, String text, int width) {}

    private static final Int2ObjectOpenHashMap<Look> LOOKS = new Int2ObjectOpenHashMap<>();
    private static final IntOpenHashSet SEEN = new IntOpenHashSet();
    private static final Object2LongOpenHashMap<String> TOLD = new Object2LongOpenHashMap<>();
    private static int tick;
    private static boolean failed;
    /** What the frames draw, nearest first; replaced (never changed) by each scan. */
    private static volatile List<Marked> shown = List.of();

    private DropTracker() {}

    // ---- the pure parts ----

    /** The indices of the first {@code n} distances (squared) within {@code range}, nearest first, at most {@code cap}. */
    static int[] nearest(double[] distSq, int n, double range, int cap) {
        double max = range * range;
        List<Integer> in = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (distSq[i] <= max) {
                in.add(i);
            }
        }
        in.sort((a, b) -> Double.compare(distSq[a], distSq[b]));
        int[] out = new int[Math.min(cap, in.size())];
        for (int i = 0; i < out.length; i++) {
            out[i] = in.get(i);
        }
        return out;
    }

    /** The floating text: the name, and "×3" for a stack. */
    static String label(String name, int count) {
        return count > 1 ? name + " ×" + count : name;
    }

    /** The colour the game writes the name in: its first coloured, visible part; else {@code fallback}. */
    static int nameColour(Component name, int fallback) {
        int[] found = {-1};
        name.visit((style, text) -> {
            if (style.getColor() != null && !ItemJson.clean(text).isBlank()) {
                found[0] = style.getColor().getValue() & 0xFFFFFF;
                return Optional.of(Boolean.TRUE);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return found[0] < 0 ? fallback : found[0];
    }

    /** Beams fade out over the last 15% of the range, so they do not pop. */
    static float fade(double dist) {
        double edge = RANGE * 0.15;
        return (float) Math.max(0, Math.min(1, (RANGE - dist) / edge));
    }

    // ---- the scan (4 Hz) ----

    public static void onTick(Minecraft mc) {
        if (++tick % 5 != 0) {
            return;
        }
        if (failed || mc.player == null || mc.level == null || !Settings.on("trackedDrops")) {
            shown = List.of();
            return;
        }
        Set<String> keys = TrackedMobs.items().keys();
        if (keys.isEmpty()) {
            shown = List.of();
            return;
        }
        try {
            shown = scan(mc, keys);
        } catch (Throwable t) {
            shown = List.of();                     // a marker must never break the tick
        }
    }

    /** Whether this drop has a beam now (the dust column of valuable drops then leaves it alone). */
    public static boolean marks(int entityId) {
        for (Marked m : shown) {
            if (m.entity().getId() == entityId) {
                return true;
            }
        }
        return false;
    }

    private static List<Marked> scan(Minecraft mc, Set<String> keys) {
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        List<ItemEntity> found = new ArrayList<>();
        List<Look> looks = new ArrayList<>();
        DoubleArrayList dist = new DoubleArrayList();
        double max = RANGE * RANGE;
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ItemEntity ie) || ie.isRemoved()) {
                continue;
            }
            double d2 = ie.distanceToSqr(cam);
            if (d2 > max) {
                continue;
            }
            Look l = look(mc, ie);
            if (l.key().isEmpty() || !keys.contains(l.key())) {
                continue;
            }
            found.add(ie);
            looks.add(l);
            dist.add(d2);
        }
        if (LOOKS.size() > 2000) {
            LOOKS.clear();
        }
        if (SEEN.size() > 512) {
            SEEN.clear();
        }
        if (found.isEmpty()) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        int[] order = nearest(dist.elements(), dist.size(), RANGE, CAP);
        List<Marked> out = new ArrayList<>(order.length);
        int names = 0;
        for (int i : order) {
            ItemEntity ie = found.get(i);
            Look l = looks.get(i);
            boolean named = names < NAME_CAP && dist.getDouble(i) <= NAME_RANGE * NAME_RANGE
                    && inSight(mc, cam, new Vec3(ie.getX(), ie.getY() + 0.4, ie.getZ()));
            if (named) {
                names++;
            }
            out.add(new Marked(ie, l.colour(), named ? l.text() : null, l.width()));
            if (SEEN.add(ie.getId()) && now - TOLD.getLong(l.key()) > TELL_MS) {
                TOLD.put(l.key(), now);
                MobTracker.tell(mc, L.t("wynnlabs.track.near", TrackedMobs.items().name(l.key()),
                        RoadmapScreen.dist((int) Math.round(Math.sqrt(dist.getDouble(i))))), now);
            }
        }
        return List.copyOf(out);
    }

    /** The drop's key, text and colour, worked out again only when its stack count changes. */
    private static Look look(Minecraft mc, ItemEntity ie) {
        ItemStack stack = ie.getItem();
        int count = stack.getCount();
        Look l = LOOKS.get(ie.getId());
        if (l != null && l.count() == count) {
            return l;
        }
        Component hover = stack.getHoverName();
        String name = ItemJson.clean(hover.getString());
        String text = label(name, count);
        l = new Look(count, TrackedMobs.itemKey(name), text, mc.font.width(text), nameColour(hover, 0xFFFFFF));
        LOOKS.put(ie.getId(), l);
        return l;
    }

    /** One ray from the camera: in sight when nothing is hit, or the hit is within half a block of the drop. */
    private static boolean inSight(Minecraft mc, Vec3 from, Vec3 to) {
        HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < 0.25;
    }

    /** Forgets every drop (leaving a server). */
    public static void reset() {
        shown = List.of();
        LOOKS.clear();
        SEEN.clear();
    }

    // ---- the markers (world) ----

    /** Every frame: the beams, then the names, farthest first. */
    public static void renderWorld(PoseStack pose, MultiBufferSource buffers) {
        List<Marked> list = shown;
        if (list.isEmpty() || failed) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) {
            return;
        }
        try {
            Camera camera = mc.gameRenderer.getMainCamera();
            Vec3 c = camera.position();
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            VertexConsumer quads = buffers.getBuffer(RenderTypes.debugQuads());
            for (int i = list.size() - 1; i >= 0; i--) {
                Marked m = list.get(i);
                ItemEntity e = m.entity();
                if (e.isRemoved()) {
                    continue;
                }
                float x = (float) (Mth.lerp(pt, e.xo, e.getX()) - c.x);
                float y = (float) (Mth.lerp(pt, e.yo, e.getY()) - c.y + 0.25);
                float z = (float) (Mth.lerp(pt, e.zo, e.getZ()) - c.z);
                float a = fade(Math.sqrt(x * x + y * y + z * z));
                if (a >= 0.02f) {
                    Beams.draw(pose, quads, x, y, z, HEIGHT, m.colour(), a);
                }
            }
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
            for (int i = list.size() - 1; i >= 0; i--) {
                Marked m = list.get(i);
                ItemEntity e = m.entity();
                if (m.text() == null || e.isRemoved()) {
                    continue;
                }
                double x = Mth.lerp(pt, e.xo, e.getX()) - c.x;
                double y = Mth.lerp(pt, e.yo, e.getY()) - c.y + LIFT;
                double z = Mth.lerp(pt, e.zo, e.getZ()) - c.z;
                double d = Math.sqrt(x * x + y * y + z * z);
                PowerupLabels.draw(pose, buffers, mc.font, camera, x, y, z, m.text(), m.width(),
                        0xFF000000 | m.colour(), PowerupLabels.scale(d, 100, 0.8f));
            }
        } catch (Throwable t) {
            failed = true;                          // once broken, stay out of the frame loop
            shown = List.of();
            System.out.println("[WynnLabs] tracked drops failed, off until restart: " + t);
        } finally {
            if (buffers instanceof MultiBufferSource.BufferSource source) {
                source.endBatch();
            }
        }
    }
}
