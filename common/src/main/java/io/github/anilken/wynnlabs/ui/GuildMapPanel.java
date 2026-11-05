package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.guild.GuildApi;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The guild map's side panel, dark like the card on the map: a
 * search box, switches for the map's extras, the guilds by territories held (with how many are
 * online), and for a picked guild its numbers, what it makes an hour, who is online where and its
 * territories (click one to centre the map on it). Says when the guild numbers were last asked.
 * Collapses to one button. It keeps where things were drawn, so a click is answered by what was
 * under it.
 */
public final class GuildMapPanel {

    /** What a click asks the map to do. */
    public record Click(String what, String arg) {}

    /** One guild in the list. */
    record Guild(String name, String prefix, int colour, int count, String hq) {}

    private record Hit(int x0, int y0, int x1, int y1, Click click) {}

    /** The panel's width inside its frame. */
    public static final int W = 186;
    /** The frame's width beyond the panel on each side, and the gap to the screen's edge. */
    static final int EDGE = 4;

    public boolean open = true;
    public boolean routes = true;
    public boolean timers = true;
    /** The search box's text, and whether it has the keyboard. */
    public String query = "";
    public boolean typing;
    /** The picked guild's name, or null for the list of guilds. */
    public String guild;
    private int scroll;
    private int contentH;
    private int viewH;
    private final List<Hit> hits = new ArrayList<>();

    /** The width the panel takes from the right of the screen, frame included (0 when closed). */
    public int taken() {
        return open ? W + 2 * EDGE : 0;
    }

    /**
     * True when (mx, my) is over the panel (or its collapsed button). The panel lies inside the
     * map's drawn area, along its {@code right} edge from {@code top} to {@code bottom}.
     */
    public boolean over(int right, int top, int bottom, double mx, double my) {
        if (!open) {
            return mx >= right - 30 && mx < right - 4 && my >= top + 4 && my < top + 30;
        }
        return mx >= right - W - 2 * EDGE && mx < right && my >= top && my < bottom;
    }

    public void draw(GuiGraphics g, Font font, int mapRight, int mapTop, int mapBottom, List<GuildMapLook.Territory> all, int mx, int my) {
        hits.clear();
        long now = System.currentTimeMillis();
        if (!open) {
            int x = mapRight - 26;
            GuildMapInk.face(g, x - 3, mapTop + 6, 22, 22);
            g.drawString(font, "«", x + 8 - font.width("«") / 2, mapTop + 13, GuildMapInk.INK, false);
            hits.add(new Hit(x - 3, mapTop + 6, x + 19, mapTop + 28, new Click("open", null)));
            return;
        }
        int x = mapRight - EDGE - W;
        int y = mapTop + EDGE;
        int h = mapBottom - mapTop - 2 * EDGE;
        int pad = 8;
        int lh = font.lineHeight;
        GuildMapInk.face(g, x, y, W, h);
        int left = x + pad;
        int right = x + W - pad;
        int cy = y + pad;
        g.drawString(font, L.t("wynnlabs.guildmap.title"), left, cy, GuildMapInk.INK, false);
        g.drawString(font, "»", right - font.width("»"), cy, GuildMapInk.INK_3, false);
        hits.add(new Hit(right - 12, cy - 2, right + 4, cy + lh, new Click("close", null)));
        cy += lh + 5;
        // the search box: guilds by name or tag, territories by name
        int boxH = lh + 6;
        GuildMapInk.box(g, left, cy, right - left, boxH, GuildMapInk.WELL, typing ? GuildMapInk.GOLD_EDGE : GuildMapInk.LINE);
        boolean hint = query.isEmpty() && !typing;
        String shown = hint ? L.t("wynnlabs.guildmap.search")
                : query + (typing && now / 500 % 2 == 0 ? "_" : "");
        g.drawString(font, tail(font, shown, right - left - 8), left + 4, cy + 3, hint ? GuildMapInk.INK_3 : GuildMapInk.INK, false);
        hits.add(new Hit(left, cy, right, cy + boxH, new Click("search", null)));
        cy += boxH + 5;
        // the switches
        int cx = chip(g, font, left, cy, L.t("wynnlabs.guildmap.routes_switch"), routes, new Click("routes", null));
        chip(g, font, cx + 4, cy, L.t("wynnlabs.guildmap.timers_switch"), timers, new Click("timers", null));
        cy += lh + 7;
        g.fill(left, cy, right, cy + 1, GuildMapInk.LINE);
        cy += 5;
        int top = cy;
        int bottom = y + h - pad;
        viewH = bottom - top;
        g.enableScissor(x + 3, top, x + W - 3, bottom);
        int ry = top - scroll;
        List<Guild> guilds = guilds(all);
        Guild picked = guild == null ? null : guilds.stream().filter(gd -> gd.name().equals(guild)).findFirst().orElse(null);
        String q = query.toLowerCase(Locale.ROOT).strip();
        if (picked == null) {
            guild = null;
            ry = list(g, font, guilds, all, q, left, right, ry, top, mx, my, now);
        } else {
            ry = detail(g, font, picked, all, q, left, right, ry, top, mx, my, now);
        }
        g.disableScissor();
        contentH = ry + scroll - top;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        // a click on a row scrolled out of sight does nothing
        hits.removeIf(hit -> (hit.click().what().equals("guild") || hit.click().what().equals("territory")
                || hit.click().what().equals("back")) && (hit.y1() <= top || hit.y0() >= bottom));
    }

    /** The guilds by territories held, and with a search the territories whose name matches. */
    private int list(GuiGraphics g, Font font, List<Guild> guilds, List<GuildMapLook.Territory> all, String q,
            int left, int right, int ry, int top, int mx, int my, long now) {
        int lh = font.lineHeight;
        g.drawString(font, L.t("wynnlabs.guildmap.top_guilds"), left, ry, GuildMapInk.INK_2, false);
        long newest = GuildApi.newest();
        if (newest > 0 && Settings.on("guildApi")) {
            small(g, font, L.t("wynnlabs.guildmap.online_updated", GuildMapLook.ago(now - newest)), right, ry + 1, true);
        }
        ry += lh + 4;
        for (Guild gd : guilds) {
            int rank = guilds.indexOf(gd) + 1;
            if (!q.isEmpty() && !gd.name().toLowerCase(Locale.ROOT).contains(q)
                    && !gd.prefix().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            hover(g, left, right, ry, lh, top, mx, my);
            g.drawString(font, String.valueOf(rank), left, ry, GuildMapInk.INK_3, false);
            swatch(g, left + 16, ry + 1, gd.colour());
            // the online count from the guild API, for the rows in sight
            boolean seen = ry + lh > top && ry < top + viewH;
            GuildApi.Info api = seen && rank <= 40 ? GuildApi.listed(gd.prefix()) : null;
            int r = right;
            if (api != null) {
                String on = "● " + api.online();
                g.drawString(font, on, r - font.width(on), ry, api.online() > 0 ? GuildMapInk.GOOD : GuildMapInk.INK_3, false);
                r -= 28;
            }
            String count = String.valueOf(gd.count());
            g.drawString(font, count, r - font.width(count), ry, GuildMapInk.PRICE, false);
            int nx = left + 28;
            g.drawString(font, fit(font, gd.name(), r - font.width(count) - 4 - nx), nx, ry, GuildMapInk.INK, false);
            hits.add(new Hit(left - 4, ry - 2, right + 4, ry + lh + 2, new Click("guild", gd.name())));
            ry += lh + 5;
        }
        if (!q.isEmpty()) {
            List<GuildMapLook.Territory> found = new ArrayList<>();
            for (GuildMapLook.Territory t : all) {
                if (t.name().toLowerCase(Locale.ROOT).contains(q)) {
                    found.add(t);
                }
            }
            found.sort(Comparator.comparing(GuildMapLook.Territory::name));
            if (!found.isEmpty()) {
                ry = section(g, font, L.t("wynnlabs.guildmap.territories_list"), String.valueOf(found.size()), left, right, ry);
            }
            for (GuildMapLook.Territory t : found) {
                ry = territoryRow(g, font, t, t.guildColour(), left, right, ry, top, mx, my, now);
            }
        }
        return ry;
    }

    /** A picked guild: its numbers, its production, who is online where, its territories. */
    private int detail(GuiGraphics g, Font font, Guild picked, List<GuildMapLook.Territory> all, String q,
            int left, int right, int ry, int top, int mx, int my, long now) {
        int lh = font.lineHeight;
        GuildApi.Info api = GuildApi.picked(picked.prefix());
        // the head: its colour, its name, the tag and level, how many are online
        g.fill(left, ry, left + 3, ry + lh * 2 + 3, picked.colour() | 0xFF000000);
        // back to the list: a framed button with the list's name, lit under the mouse
        String back = "‹ " + L.t("wynnlabs.guildmap.back");
        int bw = font.width(back) + 8;
        int bx = right - bw;
        // the click area reaches a few pixels past the button, so it never needs aiming
        boolean lit = mx >= bx - 4 && mx < right + 6 && my >= ry - 5 && my < ry + lh + 5 && my >= top;
        GuildMapInk.box(g, bx, ry - 2, bw, lh + 3, lit ? GuildMapInk.SELECTED : GuildMapInk.WELL,
                lit ? GuildMapInk.GOLD_EDGE : GuildMapInk.INK_3);
        g.drawString(font, back, bx + 4, ry, lit ? GuildMapInk.PRICE : GuildMapInk.INK, false);
        hits.add(new Hit(bx - 4, ry - 5, right + 6, ry + lh + 5, new Click("back", null)));
        g.drawString(font, fit(font, picked.name(), bx - 4 - (left + 7)), left + 7, ry, GuildMapInk.INK, false);
        String sub = picked.prefix() + (api == null ? "" : " · " + L.t("wynnlabs.guildmap.level", api.level()));
        g.drawString(font, sub, left + 7, ry + lh + 3, GuildMapInk.INK_3, false);
        if (api != null) {
            String on = L.t("wynnlabs.guildmap.online", api.online());
            g.drawString(font, on, right - font.width(on), ry + lh + 3, api.online() > 0 ? GuildMapInk.GOOD : GuildMapInk.INK_3, false);
        }
        ry += lh * 2 + 6;
        // when the guild's numbers were asked
        if (Settings.on("guildApi")) {
            String when = GuildApi.asking(picked.prefix()) ? L.t("wynnlabs.guildmap.updating")
                    : api == null ? L.t("wynnlabs.guildmap.loading")
                    : L.t("wynnlabs.guildmap.updated", GuildMapLook.ago(now - api.at()));
            small(g, font, when, left, ry, false);
            ry += lh;
        }
        // the numbers, two by two
        List<String[]> tiles = new ArrayList<>();
        tiles.add(new String[] {L.t("wynnlabs.guildmap.territories"), String.valueOf(picked.count())});
        if (api != null) {
            tiles.add(new String[] {L.t("wynnlabs.guildmap.members"), String.valueOf(api.members())});
            tiles.add(new String[] {L.t("wynnlabs.guildmap.wars"), String.format(Locale.ROOT, "%,d", api.wars())});
            if (api.created().length() >= 10) {
                tiles.add(new String[] {L.t("wynnlabs.guildmap.created"), api.created().substring(0, 10)});
            }
        }
        int tileW = (right - left - 4) / 2;
        int tileH = lh * 2 + 4;
        for (int i = 0; i < tiles.size(); i++) {
            int tx = left + (i % 2) * (tileW + 4);
            int ty = ry + (i / 2) * (tileH + 4);
            GuildMapInk.box(g, tx, ty, tileW, tileH, GuildMapInk.CARD, GuildMapInk.LINE);
            small(g, font, tiles.get(i)[0], tx + 4, ty + 3, false);
            g.drawString(font, fit(font, tiles.get(i)[1], tileW - 8), tx + 4, ty + lh + 1, i == 0 ? GuildMapInk.PRICE : GuildMapInk.INK, false);
        }
        ry += ((tiles.size() + 1) / 2) * (tileH + 4) + 2;
        // what the guild makes an hour, all its territories together
        Map<String, Integer> made = new LinkedHashMap<>();
        for (GuildMapLook.Territory t : all) {
            if (picked.name().equals(t.guild())) {
                for (GuildMapLook.Res r : t.res()) {
                    made.merge(r.key(), r.perHour(), Integer::sum);
                }
            }
        }
        made.values().removeIf(v -> v <= 0);
        if (!made.isEmpty() || picked.hq() != null) {
            ry = section(g, font, L.t("wynnlabs.guildmap.per_hour"), null, left, right, ry);
            int px = left;
            for (Map.Entry<String, Integer> m : made.entrySet()) {
                String v = GuildMapLook.compact(m.getValue());
                int need = 12 + font.width(v) + 8;
                if (px + need > right && px > left) {
                    px = left;
                    ry += lh + 3;
                }
                int iw = GuildMapLook.drawIcon(g, font, m.getKey(), px, ry, 9);
                g.drawString(font, v, px + iw + 2, ry, GuildMapInk.INK, false);
                px += iw + 2 + font.width(v) + 8;
            }
            if (!made.isEmpty()) {
                ry += lh + 3;
            }
            if (picked.hq() != null) {
                small(g, font, "HQ · " + picked.hq(), left, ry, false);
                ry += lh;
            }
        }
        // what its territories hold together, and what its HQ holds (resources flow there)
        Map<String, int[]> held = new LinkedHashMap<>();
        GuildMapLook.Territory hq = null;
        for (GuildMapLook.Territory t : all) {
            if (!picked.name().equals(t.guild()) || !t.known()) {
                continue;
            }
            if (t.hq()) {
                hq = t;
            }
            for (GuildMapLook.Res r : t.res()) {
                int[] v = held.computeIfAbsent(r.key(), k -> new int[3]);
                v[0] += r.stored();
                v[1] += r.cap();
                v[2] = r.colour();
            }
        }
        if (!held.isEmpty()) {
            ry = section(g, font, L.t("wynnlabs.guildmap.storage"), null, left, right, ry);
            for (Map.Entry<String, int[]> e : held.entrySet()) {
                ry = storageRow(g, font, e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], left, right, ry);
            }
        }
        if (hq != null) {
            ry = section(g, font, L.t("wynnlabs.guildmap.hq_storage"), null, left, right, ry);
            for (GuildMapLook.Res r : hq.res()) {
                ry = storageRow(g, font, r.key(), r.stored(), r.cap(), r.colour(), left, right, ry);
            }
        }
        // who is online, and on which world
        if (api != null && !api.onlineMembers().isEmpty()) {
            ry = section(g, font, L.t("wynnlabs.guildmap.online_members"), String.valueOf(api.onlineMembers().size()), left, right, ry);
            // "[Chief] name" with the world on the right
            for (GuildApi.Member mb : api.onlineMembers()) {
                String world = mb.world() == null ? "" : mb.world();
                String rank = "[" + mb.rank() + "] ";
                g.drawString(font, rank, left, ry, GuildMapInk.PRICE, false);
                int nx = left + font.width(rank);
                g.drawString(font, fit(font, mb.name(), right - font.width(world) - 4 - nx), nx, ry, GuildMapInk.INK, false);
                g.drawString(font, world, right - font.width(world), ry, GuildMapInk.INK_3, false);
                ry += lh + 3;
            }
        }
        // its territories, each with how long it has been held
        List<GuildMapLook.Territory> own = new ArrayList<>();
        for (GuildMapLook.Territory t : all) {
            if (picked.name().equals(t.guild()) && (q.isEmpty() || t.name().toLowerCase(Locale.ROOT).contains(q))) {
                own.add(t);
            }
        }
        own.sort(Comparator.comparing(GuildMapLook.Territory::name));
        ry = section(g, font, L.t("wynnlabs.guildmap.territories_list"), String.valueOf(own.size()), left, right, ry);
        for (GuildMapLook.Territory t : own) {
            ry = territoryRow(g, font, t, picked.colour(), left, right, ry, top, mx, my, now);
        }
        return ry;
    }

    /** One territory row: its holder's colour, its name and its held time; a click centres the map on it. */
    private int territoryRow(GuiGraphics g, Font font, GuildMapLook.Territory t, int colour, int left, int right,
            int ry, int top, int mx, int my, long now) {
        int lh = font.lineHeight;
        hover(g, left, right, ry, lh, top, mx, my);
        swatch(g, left, ry + 1, colour);
        String held = "";
        if (t.acquired() > 0) {
            long ms = Math.max(0, now - t.acquired());
            held = GuildMapLook.shortHeld(ms);
            g.drawString(font, held, right - font.width(held), ry, ms < GuildMapLook.COOLDOWN_MS ? GuildMapInk.BAD : GuildMapInk.GOOD, false);
        }
        g.drawString(font, fit(font, t.name(), right - font.width(held) - 4 - (left + 11)), left + 11, ry, GuildMapInk.INK, false);
        hits.add(new Hit(left - 4, ry - 2, right + 4, ry + lh + 2, new Click("territory", t.name())));
        return ry + lh + 4;
    }

    /**
     * One resource in store: its icon, a bar of how full it is, and "12.4K / 80K" on the right,
     * red from 90% full on (a full store wastes what the territories make).
     */
    private static int storageRow(GuiGraphics g, Font font, String key, int stored, int cap, int colour,
            int left, int right, int ry) {
        int lh = font.lineHeight;
        boolean full = cap > 0 && stored >= cap * 0.9;
        GuildMapLook.drawIcon(g, font, key, left, ry, 9);
        String amount = GuildMapLook.compact(stored) + " / " + GuildMapLook.compact(cap);
        int aw = font.width(amount);
        int barX = left + 14;
        int barW = Math.max(10, right - aw - 6 - barX);
        g.fill(barX, ry + 2, barX + barW, ry + 7, GuildMapInk.WELL);
        int fill = cap <= 0 ? 0 : Math.round(barW * Math.min(1f, stored / (float) cap));
        if (fill > 0) {
            g.fill(barX, ry + 2, barX + fill, ry + 7, (full ? GuildMapInk.BAD : colour) | 0xFF000000);
        }
        g.drawString(font, amount, right - aw, ry, full ? GuildMapInk.BAD : GuildMapInk.INK_2, false);
        return ry + lh + 3;
    }

    /** A section's rule and title, with a count on the right; returns the y under it. */
    private static int section(GuiGraphics g, Font font, String title, String count, int left, int right, int ry) {
        ry += 3;
        g.fill(left, ry, right, ry + 1, GuildMapInk.LINE);
        ry += 5;
        g.drawString(font, title, left, ry, GuildMapInk.INK_2, false);
        if (count != null) {
            g.drawString(font, count, right - font.width(count), ry, GuildMapInk.INK_3, false);
        }
        return ry + font.lineHeight + 4;
    }

    /** Small grey text (three quarters size), left-aligned at x or, with {@code alignRight}, ending at x. */
    private static void small(GuiGraphics g, Font font, String s, int x, int y, boolean alignRight) {
        g.pose().pushMatrix();
        g.pose().translate(alignRight ? x - font.width(s) * 0.75f : x, y);
        g.pose().scale(0.75f, 0.75f);
        g.drawString(font, s, 0, 0, GuildMapInk.INK_3, false);
        g.pose().popMatrix();
    }

    private static void swatch(GuiGraphics g, int x, int y, int colour) {
        g.fill(x - 1, y - 1, x + 8, y + 8, 0xFF000000);
        g.fill(x, y, x + 7, y + 7, colour | 0xFF000000);
    }

    private static void hover(GuiGraphics g, int left, int right, int ry, int lh, int top, int mx, int my) {
        if (mx >= left - 4 && mx < right + 4 && my >= ry - 2 && my < ry + lh + 2 && my >= top) {
            g.fill(left - 4, ry - 2, right + 4, ry + lh + 1, GuildMapInk.CARD_HOVER);
        }
    }

    /** A typed character into the search box (while it has the focus). */
    public void type(int codepoint) {
        if (query.length() < 32 && !Character.isISOControl(codepoint)) {
            query += new String(Character.toChars(codepoint));
            scroll = 0;
        }
    }

    /** Backspace in the search box. */
    public void erase() {
        if (!query.isEmpty()) {
            query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
            scroll = 0;
        }
    }

    /** What a click at (mx, my) asks for, or null; the panel's own switches are applied here. */
    public Click click(double mx, double my) {
        typing = false;
        for (Hit h : hits) {
            if (mx >= h.x0() && mx < h.x1() && my >= h.y0() && my < h.y1()) {
                switch (h.click().what()) {
                    case "search" -> typing = true;
                    case "open" -> open = true;
                    case "close" -> open = false;
                    case "routes" -> routes = !routes;
                    case "timers" -> timers = !timers;
                    case "guild" -> {
                        guild = h.click().arg();
                        scroll = 0;
                    }
                    case "back" -> {
                        guild = null;
                        scroll = 0;
                    }
                    default -> { }
                }
                return h.click();
            }
        }
        return null;
    }

    public void scroll(double amount) {
        scroll = Math.max(0, Math.min(Math.max(0, contentH - viewH), scroll - (int) Math.round(amount * 20)));
    }

    /** The guilds holding territories, most first (then by name). */
    static List<Guild> guilds(List<GuildMapLook.Territory> all) {
        Map<String, Guild> by = new LinkedHashMap<>();
        for (GuildMapLook.Territory t : all) {
            if (t.guild() == null || t.guild().isBlank()) {
                continue;
            }
            Guild old = by.get(t.guild());
            String hq = old != null && old.hq() != null ? old.hq() : t.hq() ? t.name() : null;
            by.put(t.guild(), new Guild(t.guild(), t.prefix(), t.guildColour(), old == null ? 1 : old.count() + 1, hq));
        }
        List<Guild> out = new ArrayList<>(by.values());
        out.sort(Comparator.comparingInt(Guild::count).reversed().thenComparing(Guild::name));
        return out;
    }

    /** A switch: a framed chip with a check while on. */
    private int chip(GuiGraphics g, Font font, int x, int y, String text, boolean on, Click click) {
        String shown = (on ? "✓ " : "") + text;
        int w = font.width(shown) + 10;
        GuildMapInk.box(g, x, y - 2, w, font.lineHeight + 3, on ? GuildMapInk.SELECTED : GuildMapInk.WELL, on ? GuildMapInk.GOLD_EDGE : GuildMapInk.LINE);
        g.drawString(font, shown, x + 5, y, on ? GuildMapInk.PRICE : GuildMapInk.INK_3, false);
        hits.add(new Hit(x, y - 2, x + w, y + font.lineHeight + 1, click));
        return x + w;
    }

    /** The end of {@code s} that fits in {@code w} (a long search shows what was typed last). */
    private static String tail(Font font, String s, int w) {
        String t = s;
        while (t.length() > 1 && font.width(t) > w) {
            t = t.substring(1);
        }
        return t;
    }

    private static String fit(Font font, String s, int w) {
        if (s == null) {
            return "";
        }
        if (font.width(s) <= w) {
            return s;
        }
        String t = s;
        while (!t.isEmpty() && font.width(t + "…") > w) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "…";
    }
}
