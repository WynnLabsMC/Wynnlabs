package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.advice.SellSummary;
import io.github.anilken.wynnlabs.coach.Coach;
import io.github.anilken.wynnlabs.coach.Today;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.quests.Discoveries;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestGuides;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Today tab's parts: the day's card, "Yapılacaklar" (today's work, each with its one action),
 * "Sıradaki" (the quest, where to level, the nearest secret discovery, this week's raid pool), the
 * professions and the last session. One row a thing: an icon, a BODY line, a META line under it,
 * the action in a right column. What they read (files, the bank, the bag) is kept a few seconds.
 */
final class TodayRows extends MenuPaint {

    /** A row: a body and a meta line with SP_2 inside the wash, top and bottom. */
    static final int ROW = Theme.SP_2 + Type.BODY.lineHeight + Type.META.lineHeight + Theme.SP_2;

    private List<Vault.Group> junk = List.of();
    private List<Vault.Find> finds = List.of();
    private SellSummary.Summary bag;
    private HomeData home;
    private JsonObject last;
    private JsonObject measured;
    private String blocked = "";
    private long readAt;

    TodayRows(RoadmapScreen menu) {
        super(menu);
        othersChips = new TodayOthers(menu);
    }

    /** The other characters' chips and their "+N" list. */
    final TodayOthers othersChips;

    /** Read again on the next refresh. */
    void stale() {
        readAt = 0;
    }

    /** What the rows read, again every three seconds. */
    void refresh() {
        long now = System.currentTimeMillis();
        if (now - readAt < 3000 && home != null) {
            return;
        }
        readAt = now;
        junk = Vault.junk();
        finds = Vault.forMe();
        try {
            bag = SellSummary.compute(Minecraft.getInstance());
        } catch (Throwable t) {
            bag = null;
        }
        home = HomeData.read();
        last = Coach.lastSession();
        measured = Coach.measured();
        blocked = blockedLine();
    }

    // ---- the day ----

    /** Nothing read today: an info notice, the way to it in its line. */
    int unread(GuiGraphics g, int x, int y, int w, int mx, int my) {
        List<String> lines = List.of(L.t("wynnlabs.today.unread_title") + ". " + L.t("wynnlabs.today.unread_sub"));
        return Ui.notice(g, font(), Ui.Level.INFO, lines, null, x, y, w, Ui.noticeHeight(2), mx, my, new int[1][]);
    }

    /** The day's card: the objectives with their bars and the reward; the other characters beside (under, small). */
    int dayCard(GuiGraphics g, int x, int y, int w, Today.Day mine, List<Today.Day> all, boolean small, int mx, int my) {
        List<Today.Objective> objs = mine.objectives();
        String today = Today.utcDate();
        Boolean reward = null;
        long readAt = 0;
        for (Today.Day d : all) {
            if (today.equals(d.date()) && d.reward() != null && d.seen() >= readAt) {
                reward = d.reward();
                readAt = d.seen();
            }
        }
        List<Today.Day> others = all.subList(1, all.size());
        int pad = Theme.SP_4;
        int line = Type.BODY.lineHeight + Theme.SP_2;
        // the objectives set the height; the other characters' chips fit beside them (under them, small)
        int h = pad + Type.LABEL.lineHeight + Theme.SP_3 + objs.size() * line - Theme.SP_2
                + (small && !others.isEmpty() ? Theme.SP_3 + TodayOthers.height(2) : 0) + pad;
        Kit.softCard(g, x, y, w, h);
        int lw = small || others.isEmpty() ? w : w * 3 / 5;
        int ix = x + pad;
        int right = x + lw - pad;
        String head = L.t("wynnlabs.today.daily");
        Type.LABEL.draw(g, font(), head, ix, y + pad);
        long done = objs.stream().filter(o -> o.max() > 0 && o.score() >= o.max()).count();
        int sx = ix + Type.LABEL.width(font(), head) + Theme.SP_3;
        Ui.seal(g, font(), done + " / " + objs.size(), sx, Type.LABEL.onCaps(y + pad, Ui.SEAL_H), Theme.SEAL_2);
        if (reward != null) {
            String rt = L.t(reward ? "wynnlabs.today.reward_ready" : "wynnlabs.today.reward_taken");
            int rw = Type.META.width(font(), rt) + 2 * Theme.SP_2;
            int ry = Type.LABEL.onCaps(y + pad, Ui.SEAL_H);
            Ui.seal(g, font(), rt, right - rw, ry, reward ? Theme.GOLD_DEEP : Theme.INK_3);
            menu.hits.add(new MenuHit(right - rw, ry, rw, Ui.SEAL_H, null, TodayTab.tip(L.t("wynnlabs.today.reward_tip")), null));
        }
        int oy = y + pad + Type.LABEL.lineHeight + Theme.SP_3;
        int barW = small ? 60 : 90;
        for (Today.Objective o : objs) {
            boolean ok = o.max() > 0 && o.score() >= o.max();
            Art.icon(g, ok ? "check" : "target", ix, Type.BODY.onCaps(oy, 8), 8, ok ? Theme.GOOD : Theme.INK_3);
            String num = o.max() > 0 ? o.score() + "/" + o.max() : String.valueOf(o.score());
            int nw = Type.NUMBER.width(font(), num);
            Type.NUMBER.draw(g, font(), num, right - nw, oy, ok ? Theme.GOOD : Theme.INK_2);
            int bx = right - nw - Theme.SP_3 - barW;
            if (o.max() > 0) {
                Art.pill(g, bx, Type.BODY.onCaps(oy, 4), barW, 4, Theme.WELL, 0);
                if (o.score() > 0) {
                    Art.pill(g, bx, Type.BODY.onCaps(oy, 4), Math.max(4, barW * Math.min(o.score(), o.max()) / o.max()), 4,
                            ok ? Theme.GREEN : Theme.TEAL, 0);
                }
            }
            int tx = ix + 8 + Theme.SP_2;
            (ok ? Type.MUTED : Type.BODY).draw(g, font(), Type.BODY.fit(font(), o.goal(), bx - Theme.SP_3 - tx), tx, oy);
            oy += line;
        }
        if (others.isEmpty()) {
            return h;
        }
        if (small) {
            othersChips.draw(g, ix, oy - Theme.SP_2 + Theme.SP_3, w - 2 * pad, 2, others, mx, my);
            return h;
        }
        // beside: the title, then as many lines of chips as the objectives leave (two at most)
        int vx = x + lw;
        Kit.hair(g, vx, y + pad, 1, h - 2 * pad, Theme.LINE);
        int ox = vx + Theme.SP_5;
        Type.LABEL.draw(g, font(), L.t("wynnlabs.today.others"), ox, y + pad);
        int cy = y + pad + Type.LABEL.lineHeight + Theme.SP_3;
        int lines = Math.max(1, Math.min(2, (y + h - pad - cy + Theme.SP_2) / (Ui.CHIP_H + Theme.SP_2)));
        othersChips.draw(g, ox, cy, x + w - pad - ox, lines, others, mx, my);
        return h;
    }

    private static String daily(Today.Day d, String today) {
        if (!today.equals(d.date()) || d.objectives().isEmpty()) {
            return "—";
        }
        long done = d.objectives().stream().filter(o -> o.max() > 0 && o.score() >= o.max()).count();
        return done + "/" + d.objectives().size();
    }


    // ---- the rows ----

    /** A section's title and its hairline; returns the height. */
    private int head(GuiGraphics g, String title, int x, int y, int w) {
        Type.LABEL.draw(g, font(), title, x, y);
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        return Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
    }

    /**
     * One row: the icon, the line and its meta, the action on the right; the row lights under the
     * mouse when it does something. Returns the y under it and its SP_2 of paper.
     */
    private int row(GuiGraphics g, int x, int y, int w, String icon, int ink, String text, String meta, String action,
                    List<Component> tip, String cmd, QuestBook.Entry quest, int mx, int my) {
        boolean click = cmd != null || quest != null;
        boolean hover = click && mx >= x - Theme.SP_2 && mx < x + w + Theme.SP_2 && my >= y && my < y + ROW;
        if (hover) {
            g.fill(x - Theme.SP_2, y, x + w + Theme.SP_2, y + ROW, Theme.CARD_HOVER);
        }
        int ty = y + Theme.SP_2;
        Art.icon(g, icon, x, Type.BODY.onCaps(ty, 8), 8, ink == Theme.WARN ? Theme.WARN : Theme.INK_3);
        int tx = x + 8 + Theme.SP_3;
        int aw = action == null ? 0 : Type.BODY.width(font(), action) + 2 * Theme.SP_3;
        int room = x + w - tx - (aw > 0 ? aw + Theme.SP_3 : 0);
        Type.BODY.draw(g, font(), Type.BODY.fit(font(), text, room), tx, ty, ink);
        if (meta != null && !meta.isEmpty()) {
            Type.META.draw(g, font(), Type.META.fit(font(), meta, room), tx, ty + Type.BODY.lineHeight, Theme.INK_3);
        }
        if (action != null) {
            int by = ty + (Type.BODY.lineHeight - Ui.SMALL_H) / 2;
            Ui.small(g, font(), action, x + w - aw, by, aw, mx, my);
        }
        if (quest != null) {
            menu.hits.add(new MenuHit(x - Theme.SP_2, y, w + 2 * Theme.SP_2, ROW, null, tip, null, quest));
        } else {
            menu.hits.add(new MenuHit(x - Theme.SP_2, y, w + 2 * Theme.SP_2, ROW, null, tip, cmd));
        }
        return y + ROW + Theme.SP_2;
    }

    /** Today's work: tickets, the bank's sellables and upgrades, the bag, a mana warning. */
    int todo(GuiGraphics g, int x, int y, int w, Today.Day mine, int mx, int my) {
        y += head(g, L.t("wynnlabs.today.todo"), x, y, w);
        int y0 = y;
        if (mine != null && mine.tickets() > 0) {
            y = row(g, x, y, w, "ticket", Theme.INK, L.n("wynnlabs.today.tickets_short", mine.tickets()), L.t("wynnlabs.today.tickets_where"),
                    L.t("wynnlabs.today.go_compass"), Glossary.mentions("Spectral Ticket") ? List.of(Component.literal("Spectral Ticket")) : null,
                    "/compass at 437 70 -1613", null, mx, my);
        }
        if (!junk.isEmpty()) {
            long tm = junk.stream().filter(j -> "Trade Market".equals(Vault.place(j))).count();
            y = row(g, x, y, w, "coin", Theme.INK, L.n("wynnlabs.today.todo_bank", junk.size()),
                    tm > 0 ? L.t("wynnlabs.today.bank_market", tm) : "", L.t("wynnlabs.today.go_bank"), null, "#qgo:bank", null, mx, my);
        }
        if (!finds.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Vault.Find f : finds) {
                names.add(f.item().name() + " +" + RoadmapScreen.pct(Math.round(f.gain())));
            }
            y = row(g, x, y, w, "diamond", Theme.INK, L.t("wynnlabs.today.vault_head"), String.join(", ", names),
                    L.t("wynnlabs.today.go_bank"), null, "#qgo:bank", null, mx, my);
        }
        if (bag != null && bag.blacksmith().size() + bag.market().size() > 0) {
            String meta = L.t("wynnlabs.today.bag_meta", bag.blacksmith().size(), bag.market().size())
                    + (bag.marketValue() > 0 ? " · ~" + Prices.fmt(bag.marketValue()) : "");
            // the Trade Market slot limit as a quick switch: the counts follow at once
            boolean limited = io.github.anilken.wynnlabs.advice.MarketSlots.limited();
            String chip = limited ? L.t("wynnlabs.today.slot_limit", io.github.anilken.wynnlabs.advice.MarketSlots.limit())
                    : L.t("wynnlabs.today.no_slot_limit");
            List<Component> tip = List.of(Component.literal(chip).withStyle(ChatFormatting.WHITE),
                    L.c(limited ? "wynnlabs.today.slot_limit_tip_on" : "wynnlabs.today.slot_limit_tip_off").withStyle(ChatFormatting.GRAY));
            y = row(g, x, y, w, "shopping-cart", Theme.INK, L.t("wynnlabs.today.bag_head"), meta, chip, tip, "#qslots", null, mx, my);
        }
        int mf = Coach.manaFailsLast5Min();
        if (mf > 0) {
            y = row(g, x, y, w, "droplet", Theme.WARN, L.n("wynnlabs.today.mana_fails", mf), L.t("wynnlabs.today.mana_hint"), null, null, null,
                    null, mx, my);
        }
        if (y == y0) {
            Type.MUTED.draw(g, font(), L.t("wynnlabs.today.nothing_todo"), x, y);
            y += Type.BODY.lineHeight;
        }
        return y;
    }

    /** What is next: the quest, where to level, the nearest secret discovery, this week's raid pool. */
    int next(GuiGraphics g, int x, int y, int w, int mx, int my) {
        y += head(g, L.t("wynnlabs.today.next_head"), x, y, w);
        String go = L.t("wynnlabs.guide.go");
        if (!menu.quests.isEmpty()) {
            QuestAdvisor.Suggestion q = menu.quests.get(0);
            QuestBook.Entry e = q.quest();
            String meta = L.t("wynnlabs.today.quest_meta", e.level()) + (q.distance() >= 0 ? " · " + RoadmapScreen.dist(q.distance()) : "");
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(QuestGuides.title(e.name())).withStyle(ChatFormatting.AQUA));
            for (String r : q.reasons()) {
                tip.add(Component.literal("• " + L.cap(r)).withStyle(ChatFormatting.GRAY));
            }
            tip.add(L.c("wynnlabs.today.track_tip").withStyle(ChatFormatting.DARK_GRAY));
            y = row(g, x, y, w, "script", Theme.INK, QuestGuides.title(e.name()), meta, L.t("wynnlabs.today.track"), tip, null, e, mx, my);
        }
        String raw = menu.steps.isEmpty() ? null : HomeNext.safest(menu.steps.get(0).places());
        if (raw != null) {
            String place = RoadmapScreen.shortPlace(raw);
            String name = place.contains(" (") ? place.substring(0, place.indexOf(" (")) : place;
            String lv = place.contains(" (") ? place.substring(place.indexOf(" (") + 2, place.length() - 1) : "";
            String cmd = RoadmapScreen.compassFor(raw);
            y = row(g, x, y, w, "pick", Theme.INK, name, L.t("wynnlabs.today.grind_meta") + (lv.isEmpty() ? "" : " · " + lv),
                    cmd != null ? go : null, List.of(Component.literal(place)), cmd != null ? cmd : name, null, mx, my);
        }
        Discoveries.Entry d = home == null ? null : home.near();
        if (d != null) {
            String meta = L.t("wynnlabs.today.secret_meta", d.minLevel()) + " · " + RoadmapScreen.dist(home.nearDist());
            y = row(g, x, y, w, "crystal-ball", Theme.INK, d.name(), meta, go,
                    List.of(Component.literal(d.name()).withStyle(ChatFormatting.LIGHT_PURPLE)), "#qdisc:" + d.id(), null, mx, my);
        }
        if (home != null) {
            String meta = home.pool().isEmpty() ? L.t("wynnlabs.homev.week_none")
                    : L.n("wynnlabs.today.pool_meta", home.pool().size()) + (home.raidsUp() && home.dryPulls() >= 0
                    ? " · " + L.n("wynnlabs.homev.week_dry", home.dryPulls()) : "");
            y = row(g, x, y, w, "shield", Theme.INK, L.t("wynnlabs.homev.week"), meta, null, null,
                    "#qopen:" + RoadmapScreen.TAB_ENDGAME, null, mx, my);
        }
        return y;
    }

    // ---- the professions and the last session ----

    /** The professions in columns: the name, the level; gathering ones with a thin bar; the quest one holds back. */
    int professions(GuiGraphics g, int x, int y, int w, int cols) {
        Map<String, Integer> lv = Professions.levels();
        if (lv.values().stream().allMatch(v -> v < 0)) {
            return y;
        }
        y += head(g, L.t("wynnlabs.prof.title"), x, y, w);
        int colW = (w - (cols - 1) * Theme.SP_4) / cols;
        int rowH = Type.META.lineHeight + Theme.SP_2 + 2;
        for (int i = 0; i < Professions.ALL.size(); i++) {
            String p = Professions.ALL.get(i);
            int v = lv.get(p);
            int cx = x + (i % cols) * (colW + Theme.SP_4);
            int cy = y + (i / cols) * rowH;
            String val = v < 0 ? "?" : String.valueOf(v);
            int vw = Type.NUMBER.width(font(), val);
            Type.META.draw(g, font(), Type.META.fit(font(), Professions.name(p), colW - vw - Theme.SP_2), cx, cy + 1, Theme.INK_2);
            Type.NUMBER.draw(g, font(), val, cx + colW - vw, cy, Theme.INK);
            if (Professions.gathering(p) && v >= 0) {
                int bw = colW - vw - Theme.SP_3;
                Art.pill(g, cx, cy + Type.META.lineHeight + 1, bw, 2, Theme.WELL, 0);
                Art.pill(g, cx, cy + Type.META.lineHeight + 1, Math.max(2, bw * Math.min(v, 100) / 100), 2, Theme.TEAL, 0);
            }
        }
        y += ((Professions.ALL.size() + cols - 1) / cols) * rowH;
        if (!blocked.isEmpty()) {
            y += Theme.SP_1;
            Type.META.draw(g, font(), Type.META.fit(font(), blocked, w), x, y, Theme.WARN);
            y += Type.META.lineHeight;
        }
        return y;
    }

    /** The quest a profession holds back ("Fishing +5 eksik: …"), worked out on the refresh. */
    private static String blockedLine() {
        int level = Wyn.level();
        int asked = 0;
        for (QuestBook.Entry e : QuestBook.all()) {
            if (!"UNAVAILABLE".equals(e.status()) || e.level() > level || asked >= 6) {
                continue;
            }
            asked++;                                // a few wiki pages at a time
            QuestWiki.Info qi = QuestWiki.get(e.name());
            Map<String, int[]> miss = qi == null ? Map.of() : Professions.missing(qi.professions());
            if (!miss.isEmpty()) {
                List<String> parts = new ArrayList<>();
                miss.forEach((p, hn) -> parts.add(Professions.name(p) + " +" + (hn[1] - hn[0])));
                return L.t("wynnlabs.prof.blocked", String.join(", ", parts), e.name());
            }
        }
        return "";
    }

    /** The last session in one line of numbers, its tips in the head's tooltip; "Ölümler ›" and "Hasar ›" after it. */
    int last(GuiGraphics g, int x, int y, int w, int mx, int my) {
        if (last == null) {
            return y;
        }
        try {
            String title = L.t("wynnlabs.today.last_head", ago(last.get("end").getAsLong()));
            int hy = y;
            y += head(g, title, x, y, w);
            List<String> tips = Coach.sessionTips(last);
            if (!tips.isEmpty()) {
                List<Component> tip = new ArrayList<>();
                for (String t : tips) {
                    tip.add(Component.literal("• " + t).withStyle(ChatFormatting.GRAY));
                }
                menu.hits.add(new MenuHit(x, hy, Type.LABEL.width(font(), title), Type.LABEL.lineHeight, null, tip, null));
            }
            long mins = (last.get("end").getAsLong() - last.get("start").getAsLong()) / 60000;
            double lvs = last.get("levels").getAsDouble();
            int em = last.get("emeralds").getAsInt();
            int de = last.get("deaths").getAsInt();
            List<String[]> items = new ArrayList<>();       // {number, unit, colour}
            items.add(new String[] {String.valueOf(mins), L.t("wynnlabs.today.u_minutes"), "0"});
            items.add(new String[] {(lvs >= 0 ? "+" : "") + String.format(Locale.ROOT, "%.2f", lvs), L.t("wynnlabs.today.u_levels"), lvs > 0 ? "1" : "0"});
            items.add(new String[] {(em >= 0 ? "+" : "") + Prices.fmt(em), L.t("wynnlabs.today.u_emeralds"), em > 0 ? "1" : "0"});
            items.add(new String[] {String.valueOf(de), L.t("wynnlabs.today.u_deaths"), de >= 2 ? "2" : "0"});
            if (measured != null && measured.has("casts") && measured.get("casts").getAsInt() >= 5 && measured.has("avgPerCast")) {
                items.add(new String[] {"~" + Math.round(measured.get("avgPerCast").getAsDouble()), L.t("wynnlabs.today.u_per_cast"), "0"});
            }
            int cx = x;
            for (String[] it : items) {
                int nw = Type.NUMBER.width(font(), it[0]);
                int uw = Type.META.width(font(), it[1]);
                if (cx + nw + Theme.SP_1 + uw > x + w) {
                    break;
                }
                int ink = "1".equals(it[2]) ? Theme.GOOD : "2".equals(it[2]) ? Theme.BAD : Theme.INK;
                Type.NUMBER.draw(g, font(), it[0], cx, y, ink);
                Type.META.draw(g, font(), it[1], cx + nw + Theme.SP_1, y + 1, Theme.INK_3);
                cx += nw + Theme.SP_1 + uw + Theme.SP_5;
            }
            y += Type.NUMBER.lineHeight + Theme.SP_2;
            // the deaths and the damage: their sections under the tree's rail entry
            int lx = x;
            for (String[] link : new String[][] {{"skull", L.t("wynnlabs.today.deaths_link"), "#qopen:3"},
                    {"sword", L.t("wynnlabs.today.damage_link"), "#qopen:" + RoadmapScreen.TAB_DAMAGE}}) {
                // another tab opens: an icon chip
                int lw = Ui.iconChipWidth(font(), link[0], link[1], false);
                Ui.iconChip(g, font(), link[0], link[1], false, lx, y, lw, false, mx, my);
                menu.hits.add(new MenuHit(lx, y, lw, Ui.CHIP_H, null, null, link[2]));
                lx += lw + Theme.SP_2;
            }
            return y + Ui.CHIP_H;
        } catch (Throwable t) {
            return y;
        }
    }
}
