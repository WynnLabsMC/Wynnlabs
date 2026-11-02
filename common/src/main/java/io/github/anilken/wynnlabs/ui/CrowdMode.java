package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crowd mode: in a packed fight the spell effects are not drawn. Other players' summons
 * (totems, puppets, effigies, birds, mob totems: text labels "Name's Puppet" with the item
 * display under them) are never drawn, your own stay; the spell effects Wynncraft draws as
 * item displays ("Oak Boat" models standing on their own: a mob's body rides its mob and is
 * drawn) and the particles are left out beyond the radius, all of
 * them at radius 0; damage numbers farther than the radius (four blocks at 0) go too. Nothing
 * the game sends is changed, only what is drawn.
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
    /** Where other players' summon labels stood last tick, and where yours did: bodies sit under them. */
    private static volatile List<Vec3> summons = List.of();
    private static volatile List<Vec3> mine = List.of();
    private static volatile String me = "";

    /** Once a tick: the switch, the radius and where the others' summons are. */
    public static void onTick(Minecraft mc) {
        on = Settings.on("crowdMode");
        if (!on || mc.level == null || mc.player == null) {
            summons = List.of();
            return;
        }
        radius = Settings.number("crowdRadius", 0);
        radiusSq = (double) radius * radius;
        me = mc.getUser().getName();
        List<Vec3> others = new ArrayList<>();
        List<Vec3> own = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Display.TextDisplay t) {
                String owner = owner(t.getText().getString());
                if (owner != null) {
                    (owner.equalsIgnoreCase(me) ? own : others).add(e.position());
                }
            }
        }
        summons = others;
        mine = own;
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
            // a mob's body or a cosmetic rides something that is not a display (the invisible mob,
            // a player), a prop is no boat, your own summon stands under your label: all drawn; a
            // spell effect stands on its own or in a stack of displays riding each other
            if (ridesSomethingElse(e) || !vfx(d) || under(e, mine)) {
                return false;
            }
            return radius == 0 || far(e, radius) || under(e, summons);
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

    /** Whether the bottom of the entity's ride chain is something other than a display. */
    private static boolean ridesSomethingElse(Entity e) {
        Entity v = e.getVehicle();
        int hops = 0;
        while (v != null && hops++ < 8) {
            if (!(v instanceof Display)) {
                return true;
            }
            v = v.getVehicle();
        }
        return false;
    }

    /** A spell effect as Wynncraft draws them: a boat model, or an item display with nothing in it. */
    private static boolean vfx(Display.ItemDisplay d) {
        ItemStack s = d.getSlot(0).get();
        return s.isEmpty() || s.getItem() instanceof BoatItem;
    }

    private static boolean far(Entity e, int blocks) {
        return Minecraft.getInstance().player.distanceToSqr(e) > (double) blocks * blocks;
    }

    /** An item display within a block of one of these summon labels, under it: its body. */
    private static boolean under(Entity e, List<Vec3> labels) {
        Vec3 p = e.position();
        for (Vec3 s : labels) {
            double dx = p.x - s.x;
            double dz = p.z - s.z;
            double dy = s.y - p.y;
            if (dx * dx + dz * dz < 1.0 && dy > -0.5 && dy < 4.0) {
                return true;
            }
        }
        return false;
    }
}
