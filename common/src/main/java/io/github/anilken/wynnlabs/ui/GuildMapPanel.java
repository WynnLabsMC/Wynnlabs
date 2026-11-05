package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The guild map's side panel: switches for the map's extras, the guilds by territories held, and
 * for a picked guild its territories (click one to centre the map on it). Collapses to one button.
 * It keeps where things were drawn, so a click is answered by what was under it.
 */
public final class GuildMapPanel {

    /** What a click asks the map to do. */
    public record Click(String what, String arg) {}

    /** One guild in the list. */
    record Guild(String name, String prefix, int colour, int count, String hq) {}

    private record Hit(int x0, int y0, int x1, int y1, Click click) {}

    public static final int W = 180;
    private static final int ONLINE_W = 26;

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

    private static final int BG = 0xF014161B;
    private static final int LINE = 0xFF2A2D33;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFF9A9A9A;
    private static final int GOLD = 0xFFF2C24A;

    /** True when (mx, my) is over the panel (or its collapsed button). */
    public boolean over(int screenW, int screenH, double mx, double my) {
        int x = screenW - (open ? W : 18) - 4;
        int h = open ? screenH - 8 : 18;
        return mx >= x && mx < screenW - 4 && my >= 4 && my < 4 + h;
    }

    public void draw(GuiGraphics g, Font font, int screenW, int screenH, List<GuildMapLook.Territory> all, int mx, int my) {
        hits.clear();
        if (!open) {
            int x = screenW - 22;
            g.fill(x, 4, x + 18, 22, BG);
            g.drawCenteredString(font, "«", x + 9, 9, TEXT);
            hits.add(new Hit(x, 4, x + 18, 22, new Click("open", null)));
            return;
        }
        int x = screenW - W - 4;
        int y = 4;
        int h = screenH - 8;
        int pad = 6;
        int lh = font.lineHeight;
        g.fill(x, y, x + W, y + h, BG);
        g.renderOutline(x, y, W, h, 0xFF3A3D45);
        int cy = y + pad;
        g.drawString(font, L.t("wynnlabs.guildmap.title"), x + pad, cy, TEXT, false);
        g.drawString(font, "»", x + W - pad - font.width("»"), cy, MUTED, false);
        hits.add(new Hit(x + W - pad - 12, cy - 2, x + W - 2, cy + lh, new Click("close", null)));
        cy += lh + 6;
        // the search box: guilds by name or tag, territories by name
        int boxH = lh + 6;
        g.fill(x + pad, cy, x + W - pad, cy + boxH, 0xFF1E2127);
        g.renderOutline(x + pad, cy, W - 2 * pad, boxH, typing ? GOLD : 0xFF33363D);
        boolean hint = query.isEmpty() && !typing;
        String shown = hint ? L.t("wynnlabs.guildmap.search")
                : query + (typing && System.currentTimeMillis() / 500 % 2 == 0 ? "_" : "");
        g.drawString(font, tail(font, shown, W - 2 * pad - 8), x + pad + 4, cy + 3, hint ? 0xFF777777 : TEXT, false);
        hits.add(new Hit(x + pad, cy, x + W - pad, cy + boxH, new Click("search", null)));
        cy += boxH + 6;
        // the switches
        int cx = x + pad;
        cx = chip(g, font, cx, cy, L.t("wynnlabs.guildmap.routes_switch"), routes, new Click("routes", null));
        chip(g, font, cx + 4, cy, L.t("wynnlabs.guildmap.timers_switch"), timers, new Click("timers", null));
        cy += lh + 8;
        int top = cy;
        viewH = y + h - pad - top;
        g.enableScissor(x + 1, top, x + W - 1, y + h - pad);
        int ry = top - scroll;
        List<Guild> guilds = guilds(all);
        Guild picked = guild == null ? null : guilds.stream().filter(gd -> gd.name().equals(guild)).findFirst().orElse(null);
        if (picked == null) {
            guild = null;
            g.drawString(font, L.t("wynnlabs.guildmap.top_guilds"), x + pad, ry, MUTED, false);
            ry += lh + 4;
            String q = query.toLowerCase(java.util.Locale.ROOT).strip();
            for (Guild gd : guilds) {
                int rank = guilds.indexOf(gd) + 1;
                if (!q.isEmpty() && !gd.name().toLowerCase(java.util.Locale.ROOT).contains(q)
                        && !gd.prefix().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    continue;
                }
                boolean hover = mx >= x && mx < x + W && my >= ry - 2 && my < ry + lh + 2 && my >= top;
                if (hover) {
                    g.fill(x + 1, ry - 2, x + W - 1, ry + lh + 1, 0x30FFFFFF);
                }
                g.drawString(font, String.valueOf(rank), x + pad, ry, MUTED, false);
                g.fill(x + pad + 16, ry + 1, x + pad + 23, ry + 8, gd.colour() | 0xFF000000);
                // the online count from the guild API, for the rows in sight
                boolean seen = ry + lh > top && ry < top + viewH;
                io.github.anilken.wynnlabs.guild.GuildApi.Info api = seen && rank <= 31
                        ? io.github.anilken.wynnlabs.guild.GuildApi.listed(gd.prefix()) : null;
                int right = x + W - pad;
                if (api != null) {
                    String on = "● " + api.online();
                    g.drawString(font, on, right - font.width(on), ry, api.online() > 0 ? 0xFF7DFF7D : 0xFF666666, false);
                    right -= ONLINE_W;
                }
                String count = String.valueOf(gd.count());
                int countX = right - font.width(count);
                String name = fit(font, gd.name(), countX - (x + pad + 28) - 4);
                g.drawString(font, name, x + pad + 28, ry, TEXT, false);
                g.drawString(font, count, countX, ry, GOLD, false);
                hits.add(new Hit(x, ry - 2, x + W, ry + lh + 2, new Click("guild", gd.name())));
                ry += lh + 4;
            }
            if (!q.isEmpty()) {
                // territories whose name matches, each with its holder's colour
                List<GuildMapLook.Territory> found = new ArrayList<>();
                for (GuildMapLook.Territory t : all) {
                    if (t.name().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                        found.add(t);
                    }
                }
                found.sort(Comparator.comparing(GuildMapLook.Territory::name));
                if (!found.isEmpty()) {
                    ry += 4;
                    g.drawString(font, L.t("wynnlabs.guildmap.territories_list"), x + pad, ry, MUTED, false);
                    ry += lh + 4;
                }
                for (GuildMapLook.Territory t : found) {
                    ry = territoryRow(g, font, x, ry, t, t.guildColour(), mx, my, top);
                }
            }
        } else {
            g.drawString(font, fit(font, picked.name(), W - 2 * pad - 12), x + pad, ry, picked.colour() | 0xFF000000, false);
            g.drawString(font, "×", x + W - pad - font.width("×"), ry, MUTED, false);
            hits.add(new Hit(x + W - pad - 10, ry - 2, x + W, ry + lh, new Click("back", null)));
            ry += lh + 2;
            io.github.anilken.wynnlabs.guild.GuildApi.Info api = io.github.anilken.wynnlabs.guild.GuildApi.picked(picked.prefix());
            String sub = "[" + picked.prefix() + "]" + (api == null ? "" : " · " + L.t("wynnlabs.guildmap.level", api.level()));
            g.drawString(font, sub, x + pad, ry, MUTED, false);
            if (api != null) {
                String on = L.t("wynnlabs.guildmap.online", api.online());
                g.drawString(font, on, x + W - pad - font.width(on), ry, api.online() > 0 ? 0xFF7DFF7D : 0xFF666666, false);
            }
            ry += lh + 6;
            ry = pair(g, font, x, ry, L.t("wynnlabs.guildmap.territories"), String.valueOf(picked.count()), GOLD);
            if (api != null) {
                ry = pair(g, font, x, ry, L.t("wynnlabs.guildmap.members"), String.valueOf(api.members()), TEXT);
                ry = pair(g, font, x, ry, L.t("wynnlabs.guildmap.wars"), String.format(java.util.Locale.ROOT, "%,d", api.wars()), TEXT);
                if (api.created().length() >= 10) {
                    ry = pair(g, font, x, ry, L.t("wynnlabs.guildmap.created"), api.created().substring(0, 10), TEXT);
                }
            } else if (io.github.anilken.wynnlabs.core.Settings.on("guildApi")) {
                g.drawString(font, L.t("wynnlabs.guildmap.loading"), x + pad, ry, MUTED, false);
                ry += lh + 3;
            }
            if (picked.hq() != null) {
                ry = pair(g, font, x, ry, "HQ", fit(font, picked.hq(), W / 2), TEXT);
            }
            // what the guild makes an hour, all its territories together
            Map<String, int[]> made = new LinkedHashMap<>();
            for (GuildMapLook.Territory t : all) {
                if (!picked.name().equals(t.guild())) {
                    continue;
                }
                for (GuildMapLook.Res r : t.res()) {
                    int[] v = made.computeIfAbsent(r.key(), k -> new int[] {0, r.colour()});
                    v[0] += r.perHour();
                }
            }
            for (Map.Entry<String, int[]> m : made.entrySet()) {
                if (m.getValue()[0] > 0) {
                    ry = pair(g, font, x, ry, L.t("wynnlabs.guildmap.res." + m.getKey().toLowerCase(java.util.Locale.ROOT)),
                            GuildMapLook.compact(m.getValue()[0]) + L.t("wynnlabs.guildmap.per_hour_short"), m.getValue()[1] | 0xFF000000);
                }
            }
            if (api != null && !api.onlineMembers().isEmpty()) {
                ry += 4;
                g.fill(x + pad, ry, x + W - pad, ry + 1, LINE);
                ry += 5;
                g.drawString(font, L.t("wynnlabs.guildmap.online_members"), x + pad, ry, MUTED, false);
                ry += lh + 4;
                for (io.github.anilken.wynnlabs.guild.GuildApi.Member mb : api.onlineMembers()) {
                    String world = mb.world() == null ? "" : mb.world();
                    String rank = "[" + mb.rank() + "] ";
                    g.drawString(font, rank, x + pad, ry, 0xFFE8B84A, false);
                    int nx = x + pad + font.width(rank);
                    g.drawString(font, fit(font, mb.name(), x + W - pad - font.width(world) - 4 - nx), nx, ry, TEXT, false);
                    g.drawString(font, world, x + W - pad - font.width(world), ry, MUTED, false);
                    ry += lh + 3;
                }
            }
            ry += 4;
            g.fill(x + pad, ry, x + W - pad, ry + 1, LINE);
            ry += 5;
            g.drawString(font, L.t("wynnlabs.guildmap.territories_list"), x + pad, ry, MUTED, false);
            ry += lh + 4;
            List<GuildMapLook.Territory> own = new ArrayList<>();
            for (GuildMapLook.Territory t : all) {
                if (picked.name().equals(t.guild())) {
                    own.add(t);
                }
            }
            own.sort(Comparator.comparing(GuildMapLook.Territory::name));
            String q = query.toLowerCase(java.util.Locale.ROOT).strip();
            for (GuildMapLook.Territory t : own) {
                if (q.isEmpty() || t.name().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                    ry = territoryRow(g, font, x, ry, t, picked.colour(), mx, my, top);
                }
            }
        }
        g.disableScissor();
        contentH = ry + scroll - top;
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - viewH)));
        // a click on a row scrolled out of sight does nothing
        hits.removeIf(hit -> hit.click().what().equals("guild") || hit.click().what().equals("territory")
                ? hit.y1() <= top || hit.y0() >= top + viewH : false);
    }

    /** One territory row: its holder's colour and its name; a click centres the map on it. */
    private int territoryRow(GuiGraphics g, Font font, int x, int ry, GuildMapLook.Territory t, int colour, int mx, int my, int top) {
        int lh = font.lineHeight;
        if (mx >= x && mx < x + W && my >= ry - 2 && my < ry + lh + 2 && my >= top) {
            g.fill(x + 1, ry - 2, x + W - 1, ry + lh + 1, 0x30FFFFFF);
        }
        g.fill(x + 6, ry + 1, x + 13, ry + 8, colour | 0xFF000000);
        g.drawString(font, fit(font, t.name(), W - 24), x + 17, ry, TEXT, false);
        hits.add(new Hit(x, ry - 2, x + W, ry + lh + 2, new Click("territory", t.name())));
        return ry + lh + 4;
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

    private int chip(GuiGraphics g, Font font, int x, int y, String text, boolean on, Click click) {
        int w = font.width(text) + 10;
        g.fill(x, y - 2, x + w, y + font.lineHeight + 1, on ? GOLD : 0xFF2A2D33);
        g.drawString(font, text, x + 5, y, on ? 0xFF3A2A00 : 0xFFCCCCCC, false);
        hits.add(new Hit(x, y - 2, x + w, y + font.lineHeight + 1, click));
        return x + w;
    }

    private static int pair(GuiGraphics g, Font font, int x, int y, String label, String value, int ink) {
        g.drawString(font, label, x + 6, y, MUTED, false);
        g.drawString(font, value, x + W - 6 - font.width(value), y, ink, false);
        return y + font.lineHeight + 3;
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
