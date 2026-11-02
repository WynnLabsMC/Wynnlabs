package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
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

    private static volatile boolean on;
    private static volatile int radius;
    private static volatile double radiusSq;
    private static volatile String me = "";
    /** The root display of every labelled stack → true to hide it (another player's summon). */
    private static volatile Map<Integer, Boolean> bodies = Map.of();

    /** The key that flips the switch while no screen is open, telling on the action bar. */
    public static void registerKey(io.github.anilken.wynnlabs.core.Platform platform,
            net.minecraft.client.KeyMapping.Category category) {
        net.minecraft.client.KeyMapping k = platform.registerKey(new net.minecraft.client.KeyMapping(
                "key.wynnlabs.crowd", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
                org.lwjgl.glfw.GLFW.GLFW_KEY_J, category));
        platform.onEndTick(client -> {
            while (k.consumeClick()) {
                if (client.player == null || client.screen != null) {
                    continue;
                }
                Settings.toggle("crowdMode");
                boolean now = Settings.on("crowdMode");
                client.gui.setOverlayMessage(io.github.anilken.wynnlabs.core.L.c(
                        now ? "wynnlabs.crowd.on" : "wynnlabs.crowd.off"), false);
            }
        });
    }

    /** Once a tick: the switch, the radius and which stacks have a label. */
    public static void onTick(Minecraft mc) {
        on = Settings.on("crowdMode");
        if (!on || mc.level == null || mc.player == null) {
            bodies = Map.of();
            return;
        }
        radius = Settings.number("crowdRadius", 0);
        radiusSq = (double) radius * radius;
        me = mc.getUser().getName();
        Map<Integer, Boolean> found = new HashMap<>();
        for (Entity e : mc.level.entitiesForRendering()) {
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

    /** Whether the label names another player's summon. */
    static boolean othersSummon(String text) {
        String owner = owner(text);
        return owner != null && !owner.equalsIgnoreCase(me);
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
