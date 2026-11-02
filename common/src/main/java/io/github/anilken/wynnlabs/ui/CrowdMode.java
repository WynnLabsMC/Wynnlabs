package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crowd mode: in a packed fight only your own effects are drawn. Other players' summons
 * (totems, puppets, effigies, birds, mob totems: text labels "Name's Puppet" with the item
 * display under them) are never drawn; the spell effects Wynncraft draws as item displays
 * ("Oak Boat" models), the particles, and the damage numbers farther than the radius are left
 * out. Nothing the game sends is changed, only what is drawn.
 */
public final class CrowdMode {

    private CrowdMode() {}

    /** "Name's Puppet", "Name's Mob Totem": a summon and its owner. */
    private static final Pattern SUMMON = Pattern.compile("^([A-Za-z0-9_]{2,16})'s ");
    /** Damage numbers: digits (and the pack's glyphs) and nothing else. */
    private static final Pattern NUMBER = Pattern.compile("^[\\d,.\\s\\p{Co}\\x{D0000}-\\x{DFFFF}]+$");

    private static volatile boolean on;
    private static volatile double radiusSq = 16;
    /** Where other players' summon labels stood last tick: their bodies sit under them. */
    private static volatile List<Vec3> summons = List.of();
    private static volatile String me = "";

    /** Once a tick: the switch, the radius and where the others' summons are. */
    public static void onTick(Minecraft mc) {
        on = Settings.on("crowdMode");
        if (!on || mc.level == null || mc.player == null) {
            summons = List.of();
            return;
        }
        int r = Settings.number("crowdRadius", 4);
        radiusSq = (double) r * r;
        me = mc.getUser().getName();
        List<Vec3> found = new ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Display.TextDisplay t && othersSummon(t.getText().getString())) {
                found.add(e.position());
            }
        }
        summons = found;
    }

    /** Whether the label names another player's summon. */
    static boolean othersSummon(String text) {
        Matcher m = SUMMON.matcher(text);
        return m.find() && !m.group(1).equalsIgnoreCase(me);
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
            return NUMBER.matcher(text).matches() && !text.isBlank() && far(e);
        }
        if (e instanceof Display.ItemDisplay) {
            return far(e) || underSummon(e);
        }
        return false;
    }

    /** Whether a particle at this spot is left out. */
    public static boolean dropParticle(double x, double y, double z) {
        if (!on) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.position().distanceToSqr(x, y, z) > radiusSq;
    }

    private static boolean far(Entity e) {
        return Minecraft.getInstance().player.distanceToSqr(e) > radiusSq;
    }

    /** An item display within a block of another player's summon label, under it: its body. */
    private static boolean underSummon(Entity e) {
        Vec3 p = e.position();
        for (Vec3 s : summons) {
            double dx = p.x - s.x;
            double dz = p.z - s.z;
            double dy = s.y - p.y;
            if (dx * dx + dz * dz < 1.0 && dy > -0.5 && dy < 3.5) {
                return true;
            }
        }
        return false;
    }
}
