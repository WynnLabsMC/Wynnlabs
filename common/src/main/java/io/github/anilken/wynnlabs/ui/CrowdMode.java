package io.github.anilken.wynnlabs.ui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.mixin.PowerupLayerAccessor;
import io.github.anilken.wynnlabs.mixin.PowerupModelAccessor;
import io.github.anilken.wynnlabs.mixin.PowerupSpriteAccessor;
import io.github.anilken.wynnlabs.mounts.PowerupPrint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crowd mode: in a packed fight the spell effects are not drawn. Wynncraft draws bodies and
 * effects alike as stacks of item displays ("Oak Boat" models riding an empty root display).
 * A body (a mob, a summon, a pet) has a name label, a text display made right after its stack;
 * an effect has none. Other players' summons ("Name's Puppet") are never drawn, your own and
 * the mobs stay; the label-less stacks and the particles are left out beyond the radius, all
 * of them at radius 0; damage numbers farther than the radius (four blocks at 0) go too.
 * Nothing the game sends is changed, only what is drawn.
 */
public final class CrowdMode {

    private CrowdMode() {}

    /** "Name's Puppet", "Name's Mob Totem": a summon and its owner. */
    private static final Pattern SUMMON = Pattern.compile("^([A-Za-z0-9_]{2,16})'s ");
    /** Damage numbers: digits (and the pack's glyphs) and nothing else. */
    private static final Pattern NUMBER = Pattern.compile("^[\\d,.\\s\\p{Co}\\x{D0000}-\\x{DFFFF}]+$");

    /** A stack of this many displays or more is a body (a boss, a pet, a decoration), not a spell effect. */
    static final int BODY_PARTS = 4;
    /** The root display of every stack → how many displays ride in it (one tick old). */
    private static volatile Map<Integer, Integer> stacks = Map.of();
    private static volatile boolean on;
    private static volatile int radius;
    private static volatile double radiusSq;
    private static volatile String me = "";
    /** The players on the server now (the tab list), lower-cased: a label is a summon only when its owner is one of them. */
    private static volatile java.util.Set<String> players = java.util.Set.of();
    /** The root display of every labelled stack → true to hide it (another player's summon). */
    private static volatile Map<Integer, Boolean> bodies = Map.of();
    /** The root displays of stacks with a part in a known effect texture ({@link CrowdEffects}), one tick old. */
    private static volatile java.util.Set<Integer> effects = java.util.Set.of();
    /** A boat model number → whether its texture is a known effect: each model is looked at once. */
    private static final Map<Float, Boolean> EFFECT_MODELS = new HashMap<>();
    /** The largest texture side fingerprinted: effect sheets run to 256 (a flame's frames 512 tall). */
    private static final int MAX_SPRITE = 1024;

    /** Whether a Shift key is held now. */
    public static boolean shiftDown(Minecraft client) {
        var w = client.getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /** Flips the switch (Shift with the menu key), telling in the middle of the screen like the coach. */
    public static void flip(Minecraft client) {
        Settings.toggle("crowdMode");
        boolean now = Settings.on("crowdMode");
        io.github.anilken.wynnlabs.coach.Coach.note(io.github.anilken.wynnlabs.core.L.t(
                now ? "wynnlabs.crowd.on" : "wynnlabs.crowd.off"), now ? "eye" : "sparkles",
                now ? io.github.anilken.wynnlabs.ui.Hud.GOLD : io.github.anilken.wynnlabs.ui.Hud.CREAM);
    }

    /** Raids and lootruns: a boss's own effects must stay in sight, so crowd mode rests there. */
    private static boolean resting;

    /** Once a tick: the switch, the radius and which stacks have a label. */
    public static void onTick(Minecraft mc) {
        boolean wanted = Settings.on("crowdMode");
        boolean rest = wanted && mc.player != null && (io.github.anilken.wynnlabs.game.Wyn.inRaid()
                || io.github.anilken.wynnlabs.game.Wyn.lootrunState().running());
        if (rest != resting) {
            resting = rest;
            io.github.anilken.wynnlabs.coach.Coach.note(io.github.anilken.wynnlabs.core.L.t(
                    rest ? "wynnlabs.crowd.resting" : "wynnlabs.crowd.on"), rest ? "sparkles" : "eye",
                    rest ? io.github.anilken.wynnlabs.ui.Hud.CREAM : io.github.anilken.wynnlabs.ui.Hud.GOLD);
        }
        on = wanted && !rest;
        if (!on || mc.level == null || mc.player == null) {
            bodies = Map.of();
            return;
        }
        if (++ticks % 20 == 0) {
            dumpIfAsked(mc);
        }
        radius = Settings.number("crowdRadius", 0);
        radiusSq = (double) radius * radius;
        me = mc.getUser().getName();
        java.util.Set<String> online = new java.util.HashSet<>();
        if (mc.getConnection() != null) {
            for (var info : mc.getConnection().getOnlinePlayers()) {
                online.add(info.getProfile().name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        players = online;
        Map<Integer, Boolean> found = new HashMap<>();
        Map<Integer, Integer> sizes = new HashMap<>();
        java.util.Set<Integer> marked = new java.util.HashSet<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Display.ItemDisplay d) {
                int rootId = root(e).getId();
                sizes.merge(rootId, 1, Integer::sum);
                if (effect(mc, d.getSlot(0).get())) {
                    marked.add(rootId);
                }
            }
            if (!(e instanceof Display.TextDisplay t)) {
                continue;
            }
            String text = t.getText().getString();
            if (text.isBlank() || NUMBER.matcher(text).matches()) {
                continue;
            }
            boolean hide = othersSummon(text);
            // the label is made right after the stack: the entity before it is part of the stack
            Entity before = mc.level.getEntity(e.getId() - 1);
            Entity root = before instanceof Display ? root(before) : null;
            if (root == null || root.position().distanceToSqr(e.position()) > 9) {
                root = nearestRoot(mc, e.position());
            }
            if (root != null) {
                found.merge(root.getId(), hide, Boolean::logicalAnd);
            }
        }
        bodies = found;
        stacks = sizes;
        effects = marked;
    }

    /** Whether the stack is a boat model whose texture {@link CrowdEffects} knows as a spell effect. */
    private static boolean effect(Minecraft mc, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BoatItem)) {
            return false;
        }
        CustomModelData c = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (c == null || c.floats().isEmpty()) {
            return false;
        }
        return EFFECT_MODELS.computeIfAbsent(c.floats().get(0), k -> {
            try {
                return CrowdEffects.effect(print(mc, stack)) != null;
            } catch (Throwable t) {
                return false;
            }
        });
    }

    /** The fingerprint of the stack's main sprite as drawn ({@link PowerupPrint}), null when it has none. */
    private static String print(Minecraft mc, ItemStack stack) {
        ItemStackRenderState state = new ItemStackRenderState();
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.FIXED, mc.level, null, 0);
        TextureAtlasSprite main = null;
        PowerupModelAccessor acc = (PowerupModelAccessor) state;
        ItemStackRenderState.LayerRenderState[] layers = acc.wynnlabs$layers();
        for (int i = 0; i < acc.wynnlabs$layerCount() && main == null; i++) {
            java.util.List<BakedQuad> quads = ((PowerupLayerAccessor) layers[i]).wynnlabs$quads();
            for (BakedQuad q : quads) {
                if (!q.sprite().contents().name().getPath().endsWith("empty")) {
                    main = q.sprite();
                    break;
                }
            }
        }
        if (main == null) {
            main = state.pickParticleIcon(RandomSource.create(0));
        }
        if (main == null) {
            return null;
        }
        NativeImage img = ((PowerupSpriteAccessor) main.contents()).wynnlabs$image();
        if (img == null || img.getWidth() > MAX_SPRITE || img.getHeight() > MAX_SPRITE) {
            return null;
        }
        return PowerupPrint.of(img.getWidth(), img.getHeight(), img::getPixel);
    }

    private static int ticks;

    /**
     * Study aid: when {@code config/wynnlabs/crowd-dump.request} exists, every display entity
     * within 48 blocks is written to {@code crowd-dump.txt} (id, kind, age in ticks, item, model
     * data, ride root, height over the player, the nearest label) and the request file removed.
     */
    private static void dumpIfAsked(Minecraft mc) {
        try {
            java.nio.file.Path dir = io.github.anilken.wynnlabs.core.Dirs.config();
            java.nio.file.Path ask = dir.resolve("crowd-dump.request");
            if (!java.nio.file.Files.exists(ask)) {
                return;
            }
            java.nio.file.Files.deleteIfExists(ask);
            StringBuilder out = new StringBuilder();
            Vec3 me0 = mc.player.position();
            for (Entity e : mc.level.entitiesForRendering()) {
                if (e instanceof net.minecraft.world.entity.item.ItemEntity it && e.distanceToSqr(me0) <= 64 * 64) {
                    out.append("DROP\t").append(e.getId()).append('\t').append(e.tickCount).append('\t')
                            .append(io.github.anilken.wynnlabs.game.ItemJson.clean(it.getItem().getHoverName().getString()))
                            .append(" x").append(it.getItem().getCount()).append('\t')
                            .append(String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f", e.getX(), e.getY(), e.getZ()))
                            .append('\t').append(String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(e.distanceToSqr(me0)))).append('\n');
                    continue;
                }
                if (!(e instanceof Display) || e.distanceToSqr(me0) > 48 * 48) {
                    continue;
                }
                String kind = e instanceof Display.TextDisplay ? "text" : e instanceof Display.ItemDisplay ? "item" : "block";
                String item = "";
                String model = "";
                if (e instanceof Display.ItemDisplay d) {
                    ItemStack st = d.getSlot(0).get();
                    item = st.isEmpty() ? "-" : net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
                    var cmd = st.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
                    model = cmd == null ? "" : String.valueOf(cmd.floats());
                }
                String text = e instanceof Display.TextDisplay t ? t.getText().getString().replace('\n', ' ') : "";
                Entity root = root(e);
                out.append(e.getId()).append('\t').append(kind).append('\t').append(e.tickCount).append('\t')
                        .append(item).append('\t').append(model).append('\t').append(root.getId())
                        .append('\t').append(String.format(java.util.Locale.ROOT, "%.1f", e.getY() - me0.y))
                        .append('\t').append(String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(e.distanceToSqr(me0))))
                        .append('\t').append(bodies.containsKey(root.getId()) ? "labelled:" + bodies.get(root.getId()) : "")
                        .append('\t').append(hideEntity(e) ? "HIDDEN" : "").append('\t').append(text).append('\n');
            }
            java.nio.file.Files.writeString(dir.resolve("crowd-dump.txt"), out.toString());
        } catch (Throwable t) {
            System.out.println("[WynnLabs] crowd dump failed: " + t);
        }
    }

    /** The bottom of the entity's ride chain. */
    private static Entity root(Entity e) {
        Entity r = e;
        for (int i = 0; i < 8 && r.getVehicle() != null; i++) {
            r = r.getVehicle();
        }
        return r;
    }

    /** The root display standing under a label (within a block, up to four below), else null. */
    private static Entity nearestRoot(Minecraft mc, Vec3 label) {
        Entity best = null;
        double bestD = 1.0;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof Display.ItemDisplay) || e.getVehicle() != null) {
                continue;
            }
            Vec3 p = e.position();
            double dx = p.x - label.x;
            double dz = p.z - label.z;
            double dy = label.y - p.y;
            double d = dx * dx + dz * dz;
            if (d < bestD && dy > -0.5 && dy < 4.0) {
                best = e;
                bestD = d;
            }
        }
        return best;
    }

    /** The player a summon label names ("Name's Puppet"), null for any other label. */
    static String owner(String text) {
        Matcher m = SUMMON.matcher(text);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Whether the label names another player's summon: its owner is a player on the server, not
     * you. A boss or a quest mob named with an apostrophe ("Greg's Ghost") is nobody's summon and
     * stays drawn, raid bosses included.
     */
    static boolean othersSummon(String text) {
        return othersSummon(text, me, players);
    }

    static boolean othersSummon(String text, String me, java.util.Set<String> players) {
        String owner = owner(text);
        return owner != null && !owner.equalsIgnoreCase(me) && players.contains(owner.toLowerCase(java.util.Locale.ROOT));
    }

    /** Whether to leave this entity undrawn. */
    public static boolean hideEntity(Entity e) {
        if (!on) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || e == mc.player) {
            return false;
        }
        if (e instanceof Display.TextDisplay t) {
            String text = t.getText().getString();
            if (othersSummon(text)) {
                return true;
            }
            // a damage number away from you is someone else's
            return NUMBER.matcher(text).matches() && !text.isBlank() && far(e, Math.max(radius, 4));
        }
        if (e instanceof Display.ItemDisplay d) {
            Entity root = root(e);
            Boolean body = bodies.get(root.getId());
            if (body != null) {
                return body;                  // a labelled stack: another player's summon, or kept
            }
            // a known effect texture: a spell effect however many displays it is built of
            if (effects.contains(root.getId())) {
                return radius == 0 || far(e, radius);
            }
            // a spell effect is a stack of up to three displays (an empty root and one or two boat models),
            // whatever its age: a totem's rings linger for a minute. A body is built of more: a
            // boss of boat models, a decoration, a pet
            if (stacks.getOrDefault(root.getId(), 1) >= BODY_PARTS) {
                return false;
            }
            // a cosmetic rides a player, a prop is no boat: drawn; a label-less stack is an effect
            if (!(root instanceof Display) || !vfx(d)) {
                return false;
            }
            return radius == 0 || far(e, radius);
        }
        return false;
    }

    /** Whether a particle at this spot is left out. */
    public static boolean dropParticle(double x, double y, double z) {
        if (!on) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && (radius == 0 || mc.player.position().distanceToSqr(x, y, z) > radiusSq);
    }

    /** A spell effect as Wynncraft draws them: a boat model, or an item display with nothing in it. */
    private static boolean vfx(Display.ItemDisplay d) {
        ItemStack s = d.getSlot(0).get();
        return s.isEmpty() || s.getItem() instanceof BoatItem;
    }

    private static boolean far(Entity e, int blocks) {
        return Minecraft.getInstance().player.distanceToSqr(e) > (double) blocks * blocks;
    }
}
