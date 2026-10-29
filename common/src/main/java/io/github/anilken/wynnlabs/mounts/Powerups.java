package io.github.anilken.wynnlabs.mounts;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.mixin.PowerupDisplayAccessor;
import io.github.anilken.wynnlabs.mixin.PowerupLayerAccessor;
import io.github.anilken.wynnlabs.mixin.PowerupModelAccessor;
import io.github.anilken.wynnlabs.mixin.PowerupSpriteAccessor;
import io.github.anilken.wynnlabs.model.MountRead;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The mount powerups around a rider, for their floating labels (setting powerupLabels). Every
 * {@link #SCAN_EVERY} ticks while riding, the oak boat item displays within {@link #RANGE} blocks
 * are grouped into powerups ({@link PowerupGroups}); each type display's item is resolved through
 * the client's item model resolver, its main sprite hashed once ({@link PowerupPrint}) and looked
 * up in {@link PowerupTable}. Only powerups in plain sight get a label. Reads only: nothing is
 * sent, clicked or fetched.
 */
public final class Powerups {

    public static final int SCAN_EVERY = 10;
    public static final double RANGE = 200;
    /** The ridden mount's own model: displays this close to the vehicle are its parts. */
    public static final double MOUNT_RANGE = 3;
    /** Sprites bigger than this are shared sheets, never a powerup's own texture: not hashed. */
    private static final int MAX_SPRITE = 64;
    /** The line of sight aims this far above the displays' position. */
    private static final double SIGHT_LIFT = 1.0;
    /** A ray that stops this close to its target (squared, blocks²) still sees it. */
    private static final double SIGHT_SLACK_SQ = 0.25;

    /** One label to draw: where (world), its text, colour (RGB), whether it is dimmed and its size (1 = normal). */
    public record Shown(double x, double y, double z, String text, int colour, boolean dim, float size) {}

    private static final Map<String, String> PRINTS = new HashMap<>();
    /** What each type display (by entity id) was found to be; a null value: not a powerup. */
    private static final Map<Integer, PowerupTable.Kind> KINDS = new HashMap<>();
    private static List<Shown> shown = List.of();
    private static List<Shown> preview;
    private static long tick;

    private Powerups() {}

    /** The labels' switch (on by default). */
    public static boolean on() {
        return Settings.on("powerupLabels");
    }

    /** The labels to draw now (a preview's, else the last scan's). */
    public static List<Shown> shown() {
        return preview != null ? preview : shown;
    }

    /** Previews: draw these labels (null ends it). */
    public static void preview(List<Shown> labels) {
        preview = labels;
    }

    public static void onTick(Minecraft mc) {
        if (++tick % SCAN_EVERY != 0) {
            return;
        }
        var p = mc.player;
        if (p == null || mc.level == null || p.getVehicle() == null || !on()) {
            shown = List.of();
            if (p == null) {
                KINDS.clear();
            }
            return;
        }
        try {
            shown = scan(mc, p.getVehicle());
        } catch (Throwable t) {
            shown = List.of();                     // a label must never break the tick
        }
    }

    private static List<Shown> scan(Minecraft mc, Entity vehicle) {
        var p = mc.player;
        Entity root = vehicle.getRootVehicle();
        double r2 = RANGE * RANGE;
        List<PowerupGroups.Part<Display.ItemDisplay>> parts = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Display.ItemDisplay d) || d.distanceToSqr(p) > r2) {
                continue;
            }
            ItemStack s = stack(d);
            if (s == null || !s.is(Items.OAK_BOAT)) {
                continue;
            }
            parts.add(new PowerupGroups.Part<>(d, d.getX(), d.getY(), d.getZ(), model(s),
                    d.getRootVehicle() == root, d.distanceTo(vehicle)));
        }
        if (parts.isEmpty()) {
            return List.of();
        }
        if (KINDS.size() > 4096) {
            KINDS.clear();
        }
        MountRead mount = RideWatch.current();
        RideEnergy.Shown pool = RideWatch.energy();
        Vec3 eye = mc.gameRenderer.getMainCamera().position();
        List<Shown> out = new ArrayList<>();
        boolean needPool = false;
        for (PowerupGroups.Group<Display.ItemDisplay> g : PowerupGroups.of(parts, MOUNT_RANGE)) {
            int key = g.type().getId();
            PowerupTable.Kind kind;
            if (KINDS.containsKey(key)) {
                kind = KINDS.get(key);
            } else {
                kind = PowerupTable.of(print(mc, stack(g.type())));
                KINDS.put(key, kind);
            }
            if (kind == null || !inSight(mc, eye, new Vec3(g.x(), g.y() + SIGHT_LIFT, g.z()))) {
                continue;
            }
            needPool |= mount != null && PowerupLabel.needsPool(kind, pool);
            PowerupLabel.Label l = PowerupLabel.of(kind, mount, pool);
            out.add(new Shown(g.x(), g.y(), g.z(), l.text(), l.colour(), l.dim(), l.size()));
        }
        if (needPool) {
            RideWatch.askForPool();             // the real pool, through the hidden mount refresh
        }
        return List.copyOf(out);
    }

    /** One ray from the camera: in sight when nothing is hit, or the hit is within half a block of the target. */
    private static boolean inSight(Minecraft mc, Vec3 from, Vec3 to) {
        HitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.MISS || hit.getLocation().distanceToSqr(to) < SIGHT_SLACK_SQ;
    }

    private static ItemStack stack(Display.ItemDisplay d) {
        return ((PowerupDisplayAccessor) d).wynnlabs$stack();
    }

    /** The first custom model data float (the type display has the lowest), MAX_VALUE without one. */
    private static float model(ItemStack stack) {
        CustomModelData c = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return c == null || c.floats().isEmpty() ? Float.MAX_VALUE : c.floats().get(0);
    }

    /** The fingerprint of the stack's main sprite (hashed once per sprite), null when it has none. */
    private static String print(Minecraft mc, ItemStack stack) {
        if (stack == null) {
            return null;
        }
        ItemStackRenderState state = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.FIXED, mc.level, null, 0);
        TextureAtlasSprite main = null;
        PowerupModelAccessor acc = (PowerupModelAccessor) state;
        ItemStackRenderState.LayerRenderState[] layers = acc.wynnlabs$layers();
        for (int i = 0; i < acc.wynnlabs$layerCount() && main == null; i++) {
            List<BakedQuad> quads = ((PowerupLayerAccessor) layers[i]).wynnlabs$quads();
            if (!quads.isEmpty()) {
                main = quads.get(0).sprite();
            }
        }
        if (main == null) {
            main = state.pickParticleIcon(RandomSource.create(0));
        }
        if (main == null) {
            return null;
        }
        TextureAtlasSprite sprite = main;
        String id = sprite.contents().name().toString();
        String print = PRINTS.computeIfAbsent(id, k -> hash(sprite));
        return print.isEmpty() ? null : print;
    }

    private static String hash(TextureAtlasSprite sprite) {
        NativeImage img = ((PowerupSpriteAccessor) sprite.contents()).wynnlabs$image();
        if (img == null || img.getWidth() > MAX_SPRITE || img.getHeight() > MAX_SPRITE) {
            return "";
        }
        return PowerupPrint.of(img.getWidth(), img.getHeight(), img::getPixel);
    }
}
