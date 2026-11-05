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

    /** The produce icons' height on the map, in GUI pixels. */
    private static final float ICON_H = 12f;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;

    /**
     * The held time and produce under a territory's tag, centred on its middle (screen
     * coordinates), shrunk to fit the territory's box ({@code boxW} wide, its bottom edge at
     * {@code bottom}); what cannot fit even shrunk is left out, so nothing spills onto a neighbour.
     */
    public static void label(GuiGraphics g, Territory t, float cx, float cy, float boxW, float bottom, long now) {
        Font font = Minecraft.getInstance().font;
        float room = boxW - 4;
        float y = cy + 6;
        if (t.acquired() > 0) {
            long held = Math.max(0, now - t.acquired());
            String s = shortHeld(held);
            float scale = Math.min(1f, Math.min(room / font.width(s), (bottom - y - 1) / font.lineHeight));
            if (scale < 0.6f) {
                return;                         // no room even for the time
            }
            g.pose().pushMatrix();
            g.pose().translate(cx, y);
            g.pose().scale(scale, scale);
            g.drawCenteredString(font, s, 0, 0, held < COOLDOWN_MS ? RED : GREEN);
            g.pose().popMatrix();
            y += (font.lineHeight + 2) * scale;
        }
        List<Icon> made = new ArrayList<>();
        int total = -2;
        int tall = 0;
        for (Res r : t.res()) {
            Icon ic = icon(r.key());
            if (r.perHour() > 0 && ic != null) {
                made.add(ic);
                total += ic.w() + 2;
                tall = Math.max(tall, ic.h());
            }
        }
        if (made.isEmpty()) {
            return;
        }
        // at most about a line of text tall, smaller when the box is narrow or short
        float scale = Math.min(ICON_H / tall, Math.min(room / total, (bottom - y - 1) / tall));
        if (scale < 0.3f) {
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(cx - total * scale / 2, y);
        g.pose().scale(scale, scale);
        int ix = 0;
        for (Icon ic : made) {
            g.blit(RenderPipelines.GUI_TEXTURED, ic.id(), ix, 0, 0f, 0f, ic.w(), ic.h(), ic.w(), ic.h(), ic.w(), ic.h());
            ix += ic.w() + 2;
        }
        g.pose().popMatrix();
    }

    /**
     * The hovered territory's card, dark like the side panel, in the map's top right corner ({@code right}: the free edge, left of the side panel).
     */
    public static void card(GuiGraphics g, Territory t, int right, int top, long now) {
        Font font = Minecraft.getInstance().font;
        int w = 214;
        int pad = 8;
        int x = right - 8 - w;
        int y = top + 8;
        int lh = font.lineHeight;
        List<Pill> pills = pills(t, now);
        int pillRows = pillRows(font, pills, w - 2 * pad);
        int rows = t.known() ? t.res().size() : 0;
        int rowH = lh + 9;
        long cooling = t.acquired() > 0 ? COOLDOWN_MS - Math.max(0, now - t.acquired()) : 0;
        int h = pad + lh * 2 + 3 + 6 + pillRows * (lh + 5) + (cooling > 0 ? 7 : 0) + (rows > 0 ? 4 + rows * rowH : 0) + 4 + lh + pad;
        GuildMapInk.face(g, x, y, w, h);
        int cy = y + pad;
        // header: the holder's colour, the territory and its guild; the held time on the right
        g.fill(x + pad, cy, x + pad + 3, cy + lh * 2 + 3, t.guildColour() | 0xFF000000);
        int tx = x + pad + 7;
        g.drawString(font, t.name(), tx, cy, GuildMapInk.INK, false);
        String guild = t.guild() == null || t.guild().isBlank() ? L.t("wynnlabs.guildmap.no_guild")
                : t.guild() + (t.prefix() == null || t.prefix().isBlank() ? "" : " · " + t.prefix());
        g.drawString(font, guild, tx, cy + lh + 3, GuildMapInk.PRICE, false);
        if (t.acquired() > 0) {
            String held = longHeld(Math.max(0, now - t.acquired()));
            g.drawString(font, held, x + w - pad - font.width(held), cy, GuildMapInk.INK, false);
            String sub = L.t("wynnlabs.guildmap.held");
            g.drawString(font, sub, x + w - pad - font.width(sub), cy + lh + 3, GuildMapInk.INK_3, false);
        }
        cy += lh * 2 + 3 + 6;
        // pills: cooldown or ready, defence, treasury, HQ
        int px = x + pad;
        for (Pill p : pills) {
            int pw = font.width(p.text()) + 8;
            if (px + pw > x + w - pad && px > x + pad) {
                px = x + pad;
                cy += lh + 5;
            }
            GuildMapInk.box(g, px, cy - 2, pw, lh + 3, p.fill(), p.ink());
            g.drawString(font, p.text(), px + 4, cy, p.ink(), false);
            px += pw + 4;
        }
        cy += lh + 5;
        if (cooling > 0) {
            // the cooldown running out: a bar that empties over the ten minutes
            int bw = w - 2 * pad;
            g.fill(x + pad, cy, x + pad + bw, cy + 3, GuildMapInk.WELL);
            g.fill(x + pad, cy, x + pad + Math.round(bw * cooling / (float) COOLDOWN_MS), cy + 3, GuildMapInk.PRICE);
            cy += 7;
        }
        if (rows > 0) {
            cy += 4;
            // one column for the icons, one for the hour's gains: every bar starts and ends alike
            int hourCol = 0;
            for (Res r : t.res()) {
                hourCol = Math.max(hourCol, font.width(hourText(r)));
            }
            int barX = x + pad + 14 + 4;
            int barW = x + w - pad - hourCol - 6 - barX;
            for (Res r : t.res()) {
                // the icon, the storage bar with its amounts under it, the hour's gain on the right
                drawIcon(g, font, r.key(), x + pad, cy, 9);
                String hour = hourText(r);
                int hourW = font.width(hour);
                g.fill(barX, cy + 1, barX + barW, cy + 7, GuildMapInk.WELL);
                int fill = r.cap() <= 0 ? 0 : Math.round(barW * Math.min(1f, r.stored() / (float) r.cap()));
                if (fill > 0) {
                    g.fill(barX, cy + 1, barX + fill, cy + 7, r.colour() | 0xFF000000);
                }
                g.renderOutline(barX - 1, cy, barW + 2, 8, GuildMapInk.LINE);
                g.drawString(font, hour, x + w - pad - hourW, cy, r.perHour() > 0 ? GuildMapInk.GOOD : GuildMapInk.INK_3, false);
                String amount = L.t("wynnlabs.guildmap.res." + r.key().toLowerCase(java.util.Locale.ROOT)) + "  "
                        + compact(r.stored()) + " / " + compact(r.cap());
                g.pose().pushMatrix();
                g.pose().translate(barX, cy + 9);
                g.pose().scale(0.75f, 0.75f);
                g.drawString(font, amount, 0, 0, GuildMapInk.INK_3, false);
                g.pose().popMatrix();
                cy += rowH;
            }
        }
        // the footer: trade routes, and where the numbers come from
        cy += 2;
        g.fill(x + pad, cy, x + w - pad, cy + 1, GuildMapInk.LINE);
        cy += 3;
        String foot = t.known() && t.routes() > 0 ? L.n("wynnlabs.guildmap.routes", t.routes()) + " · " : "";
        g.drawString(font, foot + L.t("wynnlabs.guildmap.live"), x + pad, cy, GuildMapInk.INK_3, false);
    }

    /** "+9.8K/h", or a dash for a resource the territory does not make. */
    private static String hourText(Res r) {
        return r.perHour() > 0 ? "+" + compact(r.perHour()) + L.t("wynnlabs.guildmap.per_hour_short") : "—";
    }

    /** A pill on the card: its text, its tinted face and its ink. */
    private record Pill(String text, int fill, int ink) {}

    private static List<Pill> pills(Territory t, long now) {
        List<Pill> out = new ArrayList<>();
        if (t.acquired() > 0) {
            long left = COOLDOWN_MS - Math.max(0, now - t.acquired());
            out.add(left > 0 ? new Pill(L.t("wynnlabs.guildmap.cooldown", clock(left)), 0xFF2A1A1A, GuildMapInk.BAD)
                    : new Pill(L.t("wynnlabs.guildmap.ready"), 0xFF17261A, GuildMapInk.GOOD));
        }
        if (t.known() && t.defenceLevel() > 0) {
            int[] c = level(t.defenceLevel(), false);
            out.add(new Pill(L.t("wynnlabs.guildmap.defence", t.defence()), c[0], c[1]));
        }
        if (t.known() && t.treasuryLevel() > 0) {
            int[] c = level(t.treasuryLevel(), true);
            out.add(new Pill(L.t("wynnlabs.guildmap.treasury", t.treasury()), c[0], c[1]));
        }
        if (t.hq()) {
            out.add(new Pill("★ Guild HQ", 0xFF24182E, 0xFFD58CFF));
        }
        return out;
    }

    private static int pillRows(Font font, List<Pill> pills, int width) {
        int rows = 1;
        int used = 0;
        for (Pill p : pills) {
            int pw = font.width(p.text()) + 8;
            if (used > 0 && used + pw > width) {
                rows++;
                used = 0;
            }
            used += pw + 4;
        }
        return rows;
    }

    /**
     * Very low to very high as {face, ink} on the dark card: green to red for defence (weak is easy to
     * take), the other way round for treasury.
     */
    static int[] level(int level, boolean goodHigh) {
        int[][] tones = {{0xFF17261A, 0xFF8FE39A}, {0xFF20261A, 0xFFC6E36A}, {0xFF2A2516, 0xFFFFD24A},
                {0xFF2A1F16, 0xFFFFA45A}, {0xFF2A1A1A, 0xFFFF8A8A}};
        int i = Math.max(1, Math.min(5, level)) - 1;
        return tones[goodHigh ? 4 - i : i];
    }

    /**
     * A resource's mark, {@code h} pixels tall, at (x, y): the map icon for a material,
     * Wynncraft's emerald coin for emeralds; returns its width.
     */
    static int drawIcon(GuiGraphics g, Font font, String key, int x, int y, int h) {
        if ("EMERALDS".equals(key)) {
            // Wynncraft's own emerald: the "²" its font draws as the coin in every price
            float s = h / (float) font.lineHeight;
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().scale(s, s);
            g.drawString(font, EMERALD, 0, 0, 0xFFFFFFFF, false);
            g.pose().popMatrix();
            return Math.round(font.width(EMERALD) * s);
        }
        Icon ic = icon(key);
        if (ic == null) {
            return 0;
        }
        float s = h / (float) ic.h();
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s, s);
        g.blit(RenderPipelines.GUI_TEXTURED, ic.id(), 0, 0, 0f, 0f, ic.w(), ic.h(), ic.w(), ic.h(), ic.w(), ic.h());
        g.pose().popMatrix();
        return Math.round(ic.w() * s);
    }

    /** "just now", "32s ago", "3m ago", "2h ago". */
    public static String ago(long ms) {
        long s = Math.max(0, ms) / 1000;
        if (s < 10) {
            return L.t("wynnlabs.guildmap.ago_now");
        }
        if (s < 60) {
            return L.t("wynnlabs.guildmap.ago_s", s);
        }
        if (s < 3600) {
            return L.t("wynnlabs.guildmap.ago_m", s / 60);
        }
        return L.t("wynnlabs.guildmap.ago_h", s / 3600);
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

    /** The character Wynncraft's font draws as its emerald coin. */
    private static final String EMERALD = "²";

    private record Icon(Identifier id, int w, int h) {}

    private static final Icon ORE = new Icon(Identifier.fromNamespaceAndPath("wynntils", "textures/icons/map/mining.png"), 18, 14);
    private static final Icon WOOD = new Icon(Identifier.fromNamespaceAndPath("wynntils", "textures/icons/map/woodcutting.png"), 16, 17);
    private static final Icon FISH = new Icon(Identifier.fromNamespaceAndPath("wynntils", "textures/icons/map/fishing.png"), 16, 14);
    private static final Icon CROPS = new Icon(Identifier.fromNamespaceAndPath("wynntils", "textures/icons/map/farming.png"), 15, 19);

    /** The map icon of a material resource; null for emeralds (every territory makes them). */
    private static Icon icon(String key) {
        return switch (key) {
            case "EMERALDS" -> null;
            case "ORE" -> ORE;
            case "WOOD" -> WOOD;
            case "FISH" -> FISH;
            case "CROPS" -> CROPS;
            default -> null;
        };
    }
}
