package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The guild map's extras, drawn over the map Wynntils shows: under each territory's tag how long
 * its guild has held it (red while it cannot be attacked yet) and what it produces, and for the
 * hovered territory a card with its holder, held time, cooldown, defence, treasury, and per
 * resource what it stores and makes an hour. Plain values only; the bridge reads them.
 */
public final class GuildMapLook {

    private GuildMapLook() {}

    /** One resource of a territory: what it stores (of its cap) and makes an hour. */
    public record Res(String key, int colour, int stored, int cap, int perHour) {}

    /**
     * A territory as the map shows it. {@code acquired} in epoch millis (0: not known); levels 1
     * (very low) to 5 (very high), 0 when unknown; {@code known}: the game sent its resources.
     */
    public record Territory(String name, String guild, String prefix, int guildColour, long acquired, boolean hq,
            String treasury, int treasuryLevel, String defence, int defenceLevel, List<Res> res, int routes, boolean known) {}

    /** A territory cannot be attacked for ten minutes after it is taken. */
    static final long COOLDOWN_MS = 10 * 60_000L;

    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int TEXT = 0xFFE8E8E8;

    /** The held time and produce under a territory's tag, centred on its middle (screen coordinates). */
    public static void label(GuiGraphics g, Territory t, float cx, float cy, long now) {
        Font font = Minecraft.getInstance().font;
        int x = Math.round(cx);
        int y = Math.round(cy) + 6;
        if (t.acquired() > 0) {
            long held = Math.max(0, now - t.acquired());
            g.drawCenteredString(font, shortHeld(held), x, y, held < COOLDOWN_MS ? RED : GREEN);
            y += font.lineHeight + 2;
        }
        List<Res> made = new ArrayList<>();
        for (Res r : t.res()) {
            if (r.perHour() > 0 && icon(r.key()) != null) {
                made.add(r);
            }
        }
        if (made.isEmpty()) {
            return;
        }
        int total = -2;
        for (Res r : made) {
            total += icon(r.key()).w() + 2;
        }
        int ix = x - total / 2;
        for (Res r : made) {
            Icon ic = icon(r.key());
            g.blit(RenderPipelines.GUI_TEXTURED, ic.id(), ix, y, 0f, 0f, ic.w(), ic.h(), ic.w(), ic.h(), ic.w(), ic.h());
            ix += ic.w() + 2;
        }
    }

    /** The hovered territory's card, in the map's top right corner where Wynntils puts its own. */
    public static void card(GuiGraphics g, Territory t, int screenW, long now) {
        Font font = Minecraft.getInstance().font;
        int w = 210;
        int pad = 6;
        int x = screenW - 10 - w;
        int y = 50;
        int lh = font.lineHeight;
        // the height first: header, pills, the resource table, the footer
        List<String[]> pills = pills(t, now);
        int pillRows = pillRows(font, pills, w - 2 * pad);
        int rows = t.known() ? t.res().size() : 0;
        int h = pad + lh * 2 + 2 + pad + pillRows * (lh + 4) + (rows > 0 ? pad + lh + rows * (lh + 5) : 0)
                + (t.known() && t.routes() > 0 ? pad + lh : 0) + pad;
        g.fill(x, y, x + w, y + h, 0xF014161B);
        g.renderOutline(x, y, w, h, 0xFF3A3D45);
        g.fill(x, y, x + 3, y + h, t.guildColour() | 0xFF000000);
        int cy = y + pad;
        // header: the territory and its guild, the held time on the right
        g.drawString(font, t.name(), x + pad + 3, cy, TEXT, false);
        String guild = t.guild() == null || t.guild().isBlank() ? L.t("wynnlabs.guildmap.no_guild")
                : t.guild() + (t.prefix() == null || t.prefix().isBlank() ? "" : " [" + t.prefix() + "]");
        g.drawString(font, guild, x + pad + 3, cy + lh + 2, 0xFFE8B84A, false);
        if (t.acquired() > 0) {
            String held = longHeld(Math.max(0, now - t.acquired()));
            g.drawString(font, held, x + w - pad - font.width(held), cy, TEXT, false);
            String sub = L.t("wynnlabs.guildmap.held");
            g.drawString(font, sub, x + w - pad - font.width(sub), cy + lh + 2, MUTED, false);
        }
        cy += lh * 2 + 2 + pad;
        // pills: cooldown, defence, treasury, HQ
        int px = x + pad;
        for (String[] p : pills) {
            int pw = font.width(p[0]) + 8;
            if (px + pw > x + w - pad && px > x + pad) {
                px = x + pad;
                cy += lh + 4;
            }
            int ink = (int) Long.parseLong(p[1], 16);
            g.fill(px, cy - 1, px + pw, cy + lh + 1, (ink & 0x00FFFFFF) | 0x40000000);
            g.drawString(font, p[0], px + 4, cy, ink, false);
            px += pw + 4;
        }
        cy += lh + 4;
        if (rows > 0) {
            cy += pad;
            int nameW = 52;
            int perW = 34;
            int barX = x + pad + nameW;
            int barW = w - 2 * pad - nameW - perW - 4;
            g.drawString(font, L.t("wynnlabs.guildmap.resource"), x + pad, cy, MUTED, false);
            g.drawString(font, L.t("wynnlabs.guildmap.stored"), barX, cy, MUTED, false);
            String per = L.t("wynnlabs.guildmap.per_hour");
            g.drawString(font, per, x + w - pad - font.width(per), cy, MUTED, false);
            cy += lh + 2;
            for (Res r : t.res()) {
                g.drawString(font, L.t("wynnlabs.guildmap.res." + r.key().toLowerCase(java.util.Locale.ROOT)), x + pad, cy, r.colour() | 0xFF000000, false);
                int fillW = r.cap() <= 0 ? 0 : Math.round(barW * Math.min(1f, r.stored() / (float) r.cap()));
                g.fill(barX, cy, barX + barW, cy + 3, 0xFF2A2D33);
                if (fillW > 0) {
                    g.fill(barX, cy, barX + fillW, cy + 3, r.colour() | 0xFF000000);
                }
                String amount = compact(r.stored()) + " / " + compact(r.cap());
                g.pose().pushMatrix();
                g.pose().translate(barX, cy + 4);
                g.pose().scale(0.75f, 0.75f);
                g.drawString(font, amount, 0, 0, 0xFFAAAAAA, false);
                g.pose().popMatrix();
                String hour = r.perHour() > 0 ? compact(r.perHour()) : "–";
                g.drawString(font, hour, x + w - pad - font.width(hour), cy, r.perHour() > 0 ? TEXT : 0xFF666666, false);
                cy += lh + 5;
            }
        }
        if (t.known() && t.routes() > 0) {
            cy += pad - 2;
            g.fill(x + 1, cy - 3, x + w - 1, cy - 2, 0xFF2A2D33);
            g.drawString(font, L.n("wynnlabs.guildmap.routes", t.routes()), x + pad, cy, MUTED, false);
        }
    }

    /** The card's pills as {text, ink in hex}. */
    private static List<String[]> pills(Territory t, long now) {
        List<String[]> out = new ArrayList<>();
        if (t.acquired() > 0) {
            long left = COOLDOWN_MS - Math.max(0, now - t.acquired());
            out.add(left > 0 ? new String[] {L.t("wynnlabs.guildmap.cooldown", clock(left)), "FFFF9A9A"}
                    : new String[] {L.t("wynnlabs.guildmap.ready"), "FF8FE39A"});
        }
        if (t.known() && t.defenceLevel() > 0) {
            out.add(new String[] {L.t("wynnlabs.guildmap.defence", t.defence()), hex(levelInk(t.defenceLevel(), false))});
        }
        if (t.known() && t.treasuryLevel() > 0) {
            out.add(new String[] {L.t("wynnlabs.guildmap.treasury", t.treasury()), hex(levelInk(t.treasuryLevel(), true))});
        }
        if (t.hq()) {
            out.add(new String[] {"HQ", "FFFFD24A"});
        }
        return out;
    }

    private static int pillRows(Font font, List<String[]> pills, int width) {
        int rows = 1;
        int used = 0;
        for (String[] p : pills) {
            int pw = font.width(p[0]) + 8;
            if (used > 0 && used + pw > width) {
                rows++;
                used = 0;
            }
            used += pw + 4;
        }
        return rows;
    }

    /** Very low to very high: green to red for defence (weak is easy to take), the other way for treasury. */
    static int levelInk(int level, boolean goodHigh) {
        int[] inks = {0xFF8FE39A, 0xFFC6E36A, 0xFFFFD24A, 0xFFFFA45A, 0xFFFF7A7A};
        int i = Math.max(1, Math.min(5, level)) - 1;
        return inks[goodHigh ? 4 - i : i];
    }

    private static String hex(int ink) {
        return Integer.toHexString(ink).toUpperCase(java.util.Locale.ROOT);
    }

    /** "35m45s", "1h3m", "2d4h": the label's held time. */
    static String shortHeld(long ms) {
        long s = ms / 1000;
        long d = s / 86400;
        long h = s % 86400 / 3600;
        long m = s % 3600 / 60;
        if (d > 0) {
            return d + "d" + h + "h";
        }
        if (h > 0) {
            return h + "h" + m + "m";
        }
        return m + "m" + s % 60 + "s";
    }

    /** "2:43:38" or "3d 4:05:00": the card's held time. */
    static String longHeld(long ms) {
        long s = ms / 1000;
        long d = s / 86400;
        String clock = String.format(java.util.Locale.ROOT, "%d:%02d:%02d", s % 86400 / 3600, s % 3600 / 60, s % 60);
        return d > 0 ? d + "d " + clock : clock;
    }

    /** "5:58": a cooldown's minutes and seconds. */
    static String clock(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(java.util.Locale.ROOT, "%d:%02d", s / 60, s % 60);
    }

    /** "416", "1.2K", "36K", "1.5M". */
    public static String compact(int n) {
        if (n < 1000) {
            return String.valueOf(n);
        }
        double v = n >= 1_000_000 ? n / 1_000_000.0 : n / 1000.0;
        String unit = n >= 1_000_000 ? "M" : "K";
        return (v < 10 && Math.round(v * 10) % 10 != 0 ? String.format(java.util.Locale.ROOT, "%.1f", v) : String.valueOf(Math.round(v))) + unit;
    }

    private record Icon(Identifier id, int w, int h) {}

    private static final Icon ORE = new Icon(Identifier.fromNamespaceAndPath("wynntils", "textures/icons/map/mining.png"), 18, 14);
    private static final Icon WOOD = new Icon(Identifier.fromNamespaceAndPath("wynntils", "textures/icons/map/woodcutting.png"), 16, 17);
    private static final Icon FISH = new Icon(Identifier.fromNamespaceAndPath("wynntils", "textures/icons/map/fishing.png"), 16, 14);
    private static final Icon CROPS = new Icon(Identifier.fromNamespaceAndPath("wynntils", "textures/icons/map/farming.png"), 15, 19);

    /** The map icon of a material resource; null for emeralds (every territory makes them). */
    private static Icon icon(String key) {
        return switch (key) {
            case "ORE" -> ORE;
            case "WOOD" -> WOOD;
            case "FISH" -> FISH;
            case "CROPS" -> CROPS;
            default -> null;
        };
    }
}
