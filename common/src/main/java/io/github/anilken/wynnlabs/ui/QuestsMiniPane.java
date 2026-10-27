package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.quests.MiniQuests;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.MobWiki;
import io.github.anilken.wynnlabs.search.Sources;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A mini-quest's pane (the approved build/quests-mockup/mini-items.html): the header with its kind,
 * level, province, distance and, when you cannot start it yet, why; the task on a soft card with
 * the post's place and "Yol ›"; what to bring, each item with its picture, how many you hold (bag
 * and bank) and its Trade Market price; the rewards as chips; the mob it sends you after; where the
 * post is (and, on a locked gathering post, your profession's way to it); the post on the map
 * filling the right column; Pusula and Takip et at the foot.
 */
final class QuestsMiniPane extends MenuPaint {

    private static final int ROW = 22;

    private final QuestsTab tab;
    private final SearchIcons icons = new SearchIcons();

    QuestsMiniPane(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        String name = tab.questSelected;
        QuestBook.Entry q = QuestBook.get(name);
        MiniQuests.Mini m = MiniQuests.post(name);
        boolean gather = m != null ? !m.kind().equals("COMBAT") : !name.startsWith("Slay ");
        String bookTask = q == null || q.task() == null || q.task().isBlank() ? null : q.task();
        int[] at = q != null && q.x() != null ? new int[] {q.x(), q.y(), q.z()} : m != null ? m.at() : null;
        int foot = y + h - Ui.BUTTON_H;
        int cy = y + header(g, x, y, w, name, q, m, gather, at, mx, my);
        if (at == null) {
            String why = MiniQuests.loading() ? L.t("wynnlabs.quests.wiki_loading") : L.t("wynnlabs.quests.mini_unknown");
            Type.MUTED.drawWrapped(g, font(), why, x, cy, w, 3, Theme.INK_2);
            foot(g, x, foot, w, name, q, null, mx, my);
            return;
        }
        cy = taskCard(g, x, cy, w, name, m, gather, bookTask, at, mx, my) + Theme.SP_5;
        // the post on the map: the right column, as tall as the pane leaves (a strip at the foot when compact)
        boolean compact = menu.layout.compact();
        List<QuestMap.Place> post = List.of(new QuestMap.Place("1", at, L.t(gather ? "wynnlabs.quests.mini_post_gather"
                : "wynnlabs.quests.mini_post_slay"), QuestMapLayout.NOW));
        boolean mapOk = QuestMap.available(post);
        int mapW = !compact && mapOk ? Math.max(160, w * 40 / 100) : 0;
        int lw = mapW > 0 ? w - mapW - Theme.SP_6 : w;
        int top = cy;
        // each block only when it fits above the foot (a small window drops the last ones)
        int room = foot - Theme.SP_4;
        int block = Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3 + Ui.CHIP_H;
        cy = needs(g, x, cy, lw, name, m, bookTask, mx, my);
        if (cy + block <= room) {
            cy = rewards(g, x, cy, lw, q, m);
        }
        if (cy + block <= room) {
            cy = mob(g, x, cy, lw, name, m, q);
        }
        if (cy + block <= room) {
            cy = where(g, x, cy, lw, m, at, mx, my);
        }
        if (mapW > 0) {
            tab.map.draw(g, x + lw + Theme.SP_6, top, mapW, foot - Theme.SP_4 - top, post, null, true, "mini|" + name, mx, my);
        } else if (mapOk && foot - Theme.SP_4 - cy >= 48) {
            tab.map.draw(g, x, cy, w, foot - Theme.SP_4 - cy, post, null, false, "mini|" + name + "|s", mx, my);
        }
        foot(g, x, foot, w, name, q, at, mx, my);
    }

    // ---- the header ----

    /** Kind icon, name, "Sv 40 · Nesaak · 728 m", the requirement with its reason; the guide/points switch on the right. */
    private int header(GuiGraphics g, int x, int y, int w, String name, QuestBook.Entry q, MiniQuests.Mini m, boolean gather,
            int[] at, int mx, int my) {
        int places = at == null ? 0 : 1;
        String guideL = L.t("wynnlabs.quests.view_guide");
        String pointsL = L.t("wynnlabs.quests.view_points", places);
        int gw = Ui.iconChipWidth(font(), "script", guideL, false);
        int pw = Ui.iconChipWidth(font(), "map-pin", pointsL, false);
        int sx = x + w - gw - Theme.SP_1 - pw;
        int sy = Type.TITLE.onCaps(y, Ui.CHIP_H);
        Ui.iconChip(g, font(), "script", guideL, false, sx, sy, gw, true, mx, my);
        menu.hits.add(new MenuHit(sx, sy, gw, Ui.CHIP_H, null, null, "#qview:0"));
        Ui.iconChip(g, font(), "map-pin", pointsL, false, sx + gw + Theme.SP_1, sy, pw, false, mx, my);
        menu.hits.add(new MenuHit(sx + gw + Theme.SP_1, sy, pw, Ui.CHIP_H, null, null, "#qview:3"));
        Art.icon(g, gather ? "pick" : "sword", x, Type.TITLE.onCaps(y, 10), 10, Theme.INK_2);
        int tx = x + 10 + Theme.SP_2;
        Type.TITLE.draw(g, font(), Type.TITLE.fit(font(), name, sx - Theme.SP_4 - tx), tx, y);
        y += Type.TITLE.lineHeight + Theme.SP_1;
        List<String> f = new ArrayList<>();
        int level = m != null ? m.level() : q == null ? 0 : q.level();
        if (level > 0) {
            f.add(L.t("wynnlabs.quests.mini_level", level));
        }
        if (m != null && !m.province().isEmpty()) {
            f.add(m.province());
        }
        String d = at == null ? null : QuestsData.distance(at);
        if (d != null) {
            f.add(d);
        }
        String facts = String.join(" · ", f);
        Type.META.draw(g, font(), facts, tx, y, Theme.INK_3);
        MiniQuests.Req r = MiniQuests.requirement(m, Wyn.level(), Professions::level, q);
        if (q == null || !"COMPLETED".equals(q.status())) {
            String why = reason(r);
            int rx = tx + Type.META.width(font(), facts) + Theme.SP_4;
            int col = r.met() ? Theme.GOOD : Theme.BAD;
            Art.icon(g, r.met() ? "check" : "x", rx, Type.META.onCaps(y, 7), 7, col);
            Type.META.draw(g, font(), Type.META.fit(font(), why, x + w - rx - 9), rx + 9, y, col);
        }
        return Type.TITLE.lineHeight + Theme.SP_1 + Type.META.lineHeight + Theme.SP_4;
    }

    /** "Açık · Sv 40", "Sv 65 gerekli · sende Sv 44", "Mining Sv 28 gerekli · sende Sv 12". */
    static String reason(MiniQuests.Req r) {
        boolean combat = r.skill().equals("COMBAT");
        if (r.met()) {
            return combat ? L.t("wynnlabs.quests.mini_req_open", r.need()) : L.t("wynnlabs.quests.mini_req_open_prof", Professions.name(r.skill()), r.need());
        }
        return combat ? L.t("wynnlabs.quests.mini_req_combat", r.need(), Math.max(0, r.have()))
                : L.t("wynnlabs.quests.mini_req_prof", Professions.name(r.skill()), r.need(), Math.max(0, r.have()));
    }

    // ---- the task ----

    /** The task in LEAD on a soft card, the post's coordinates with a pin, "Yol ›" and the distance; returns the y under it. */
    private int taskCard(GuiGraphics g, int x, int y, int w, String name, MiniQuests.Mini m, boolean gather, String bookTask,
            int[] at, int mx, int my) {
        int pad = Theme.SP_5;
        int goW = GuidePane.goW(font());
        // the book's line without its brackets and coordinates (they are under it), or the wiki's items
        String task = bookTask != null ? bookTask.replaceAll("\\s*(at)?\\s*\\[-?\\d+,\\s*-?\\d+,\\s*-?\\d+]", "")
                .replaceAll("\\s*\\[[A-Za-z]+ Lv\\. \\d+]", "")
                .replaceAll("\\[([^\\]]+)]", "$1").trim()
                : L.t(gather ? "wynnlabs.quests.mini_task_gather" : "wynnlabs.quests.mini_task_slay",
                        m == null ? "" : m.items().replace(" or ", " " + L.t("wynnlabs.quests.mini_or") + " "), "").replaceAll("[:\\s]+$", "");
        int tw = w - 2 * pad - goW - Theme.SP_5;
        List<net.minecraft.util.FormattedCharSequence> lines = Type.LEAD.wrap(font(), task, tw);
        int n = Math.min(2, Math.max(1, lines.size()));
        int h = pad + n * Type.LEAD.lineHeight + Theme.SP_1 + Type.META.lineHeight + pad;
        Kit.softCard(g, x, y, w, h);
        Type.LEAD.drawWrapped(g, font(), task, x + pad, y + pad, tw, 2, Type.LEAD.colour);
        String coords = at[0] + ", " + at[1] + ", " + at[2];
        int ty = y + pad + n * Type.LEAD.lineHeight + Theme.SP_1;
        Type.META.draw(g, font(), coords, x + pad, ty, Theme.LINK);
        Art.icon(g, "map-pin", x + pad + Type.META.width(font(), coords) + Theme.SP_1, Type.META.onCaps(ty, 8), 8, Theme.GOLD_EDGE);
        String go = "/compass at " + at[0] + " " + at[1] + " " + at[2];
        menu.hits.add(new MenuHit(x + pad, ty, Type.META.width(font(), coords) + 10, Type.META.lineHeight,
                null, List.of(L.c("wynnlabs.quests.point_click").withStyle(ChatFormatting.GRAY)), go));
        int gx = x + w - pad - goW;
        int gy = y + pad;
        Ui.small(g, font(), L.t("wynnlabs.guide.go"), gx, gy, goW, mx, my);
        menu.hits.add(new MenuHit(gx, gy, goW, Ui.SMALL_H, null, null, go));
        String d = QuestsData.distance(at);
        if (d != null) {
            Type.META.drawRight(g, font(), d, x + w - pad, gy + Ui.SMALL_H + Theme.SP_1, Theme.INK_3);
        }
        return y + h;
    }

    // ---- what to bring ----

    /** A section's title and hairline; returns the y under it. */
    private int head(GuiGraphics g, String title, int x, int y, int w) {
        Type.LABEL.draw(g, font(), title, x, y);
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        return y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
    }

    /** Each item to bring (alternatives under "ya da"): picture, name, ×count, how many you hold, its price; then Toplam. */
    private int needs(GuiGraphics g, int x, int y, int w, String name, MiniQuests.Mini m, String bookTask, int mx, int my) {
        List<MiniQuests.Need> needs = QuestsCache.kept("mini|needs|" + name + "|" + bookTask + "|" + (m != null), 5_000,
                () -> MiniQuests.needs(bookTask, m));
        if (needs.isEmpty()) {
            return y;
        }
        // the market is asked once a minute at most, for this mini's items only (and only when prices are on)
        QuestsCache.kept("mini|prices|" + name, 60_000, () -> {
            Prices.request(needs.stream().map(MiniQuests.Need::name).toList());
            return Boolean.TRUE;
        });
        y = head(g, L.t("wynnlabs.quests.mini_needs"), x, y, w);
        double cheapest = Double.MAX_VALUE;
        for (int i = 0; i < needs.size(); i++) {
            MiniQuests.Need n = needs.get(i);
            if (i > 0) {
                Type.META.draw(g, font(), L.t("wynnlabs.quests.mini_or"), x + 18 + Theme.SP_2, y - Theme.SP_2, Theme.INK_3);
                y += Type.META.lineHeight - Theme.SP_2;
            }
            double total = row(g, x, y, w, n, mx, my);
            if (total >= 0) {
                cheapest = Math.min(cheapest, total);
            }
            y += ROW;
        }
        if (needs.size() > 1 && cheapest < Double.MAX_VALUE) {
            String t = L.t("wynnlabs.quests.mini_total", Prices.fmt(cheapest));
            Type.LABEL.drawRight(g, font(), t, x + w, y, Theme.PRICE);
            y += Type.LABEL.lineHeight;
        }
        return y + Theme.SP_4;
    }

    /** One item's row; returns what buying the rest would cost (-1 when there is no price or nothing to buy). */
    private double row(GuiGraphics g, int x, int y, int w, MiniQuests.Need n, int mx, int my) {
        Entry e = entry(n.name());
        Kit.slot(g, x, y - 1, 18, 0);
        ItemStack stack = e == null ? null : icons.of(e);
        if (stack != null && !stack.isEmpty()) {
            g.renderItem(stack, x + 1, y);
        } else {
            Art.icon(g, "package", x + 5, y + 4, 8, Theme.INK_3);
        }
        String shown = e != null ? e.name() : n.name();
        int nx = x + 18 + Theme.SP_2;
        // what you hold, by the data's name or, without it, the wiki's ("Snow Clumps" is held as "Snow Clump")
        Sources.Owned own = QuestsCache.kept("mini|own|" + shown, 1_000, () -> owned(shown));
        int bank = own.bank().values().stream().mapToInt(Integer::intValue).sum();
        int have = own.bag() + bank;
        boolean enough = have >= n.count();
        Prices.Price p = Prices.of(shown);
        Double unit = p == null ? null : p.typical();
        int priceW = 70;
        Type.BODY.draw(g, font(), Type.BODY.fit(font(), shown, w - 18 - Theme.SP_2 - priceW - 24), nx, y - 1);
        String cnt = "×" + n.count();
        Type.BODY.draw(g, font(), cnt, nx + Math.min(Type.BODY.width(font(), shown), w - 18 - Theme.SP_2 - priceW - 24) + Theme.SP_2, y - 1, Theme.INK_3);
        int by = y + Type.BODY.lineHeight;
        Art.pill(g, nx, by + 2, 46, 3, Theme.WELL, 0);
        Art.pill(g, nx, by + 2, Math.max(3, 46 * Math.min(have, n.count()) / Math.max(1, n.count())), 3, enough ? Theme.GOOD : Theme.GOLD_DEEP, 0);
        String hv = L.t("wynnlabs.quests.mini_have", Math.min(have, 999), n.count());
        Type.META.draw(g, font(), hv, nx + 50, by - 1, enough ? Theme.GOOD : Theme.INK_2);
        if (enough) {
            Art.icon(g, "check", nx + 50 + Type.META.width(font(), hv) + Theme.SP_1, Type.META.onCaps(by - 1, 7), 7, Theme.GOOD);
        }
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(shown).withStyle(ChatFormatting.WHITE));
        tip.add(L.c("wynnlabs.quests.mini_have_tip", own.bag(), bank).withStyle(ChatFormatting.GRAY));
        int right = x + w;
        double total = -1;
        if (enough) {
            Type.META.drawRight(g, font(), L.t("wynnlabs.quests.mini_enough"), right, y + 2, Theme.INK_3);
        } else if (unit == null) {
            Type.BODY.drawRight(g, font(), "—", right, y + 2, Theme.INK_3);
            String why = !Settings.on("priceApi") && p == null ? "wynnlabs.quests.mini_price_off"
                    : e != null && !e.tradable() ? "wynnlabs.quests.mini_price_untradable" : "wynnlabs.quests.mini_price_none";
            tip.add(L.c(why).withStyle(ChatFormatting.GOLD));
        } else {
            int buy = n.count() - have;
            total = unit * buy;
            Type.LABEL.drawRight(g, font(), Prices.fmt(total), right, y - 1, Theme.PRICE);
            Type.META.drawRight(g, font(), buy + " × " + Prices.fmt(unit), right, by, Theme.INK_3);
            tip.add(L.c("wynnlabs.quests.mini_price_tip", Prices.fmt(unit), buy, Prices.fmt(total)).withStyle(ChatFormatting.GOLD));
            String basis = p.when();
            if (basis != null) {
                tip.add(Component.literal(basis).withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        menu.hits.add(new MenuHit(x, y - 1, w, ROW - 2, null, tip, null));
        return total;
    }

    /** What you hold of an item by its name or its singular. */
    private static Sources.Owned owned(String name) {
        for (String n : new String[] {name, name.endsWith("es") ? name.substring(0, name.length() - 2) : null,
                name.endsWith("s") ? name.substring(0, name.length() - 1) : null}) {
            if (n != null) {
                Sources.Owned o = SearchCardSections.owned(n);
                if (o.any()) {
                    return o;
                }
            }
        }
        return Sources.Owned.NONE;
    }

    /** The item data's entry for a wiki or book name ("Snow Clumps" finds "Snow Clump"), or null. */
    private static Entry entry(String name) {
        return QuestsCache.kept("mini|entry|" + name, 10_000, () -> {
            ItemIndex index = ItemIndex.get();
            if (index.missing() != null) {
                ItemIndex.startLoading(io.github.anilken.wynnlabs.core.Platform.get().gameDir(),
                        io.github.anilken.wynnlabs.core.Dirs.config().resolve("chests.json"));
            }
            Entry e = index.byName(name);
            if (e == null && name.endsWith("es")) {
                e = index.byName(name.substring(0, name.length() - 2));
            }
            if (e == null && name.endsWith("s")) {
                e = index.byName(name.substring(0, name.length() - 1));
            }
            return e;
        });
    }

    // ---- rewards, the mob, where ----

    /** Combat XP, the profession's XP with its icon. */
    private int rewards(GuiGraphics g, int x, int y, int w, QuestBook.Entry q, MiniQuests.Mini m) {
        long combat = m != null ? (m.kind().equals("COMBAT") ? m.xp() : m.combatXp()) : q == null ? 0 : q.xp();
        if (combat <= 0 && (m == null || m.kind().equals("COMBAT"))) {
            return y;
        }
        y = head(g, L.t("wynnlabs.quests.mini_reward"), x, y, w);
        int cx = x;
        if (combat > 0) {
            String t = L.t("wynnlabs.quests.mini_xp", QuestAdvisor.fmt(combat));
            int cw = Ui.iconChipWidth(font(), "trending-up", t, false);
            Ui.iconChip(g, font(), "trending-up", t, false, cx, y, cw, false, -1, -1);
            cx += cw + Theme.SP_2;
        }
        if (m != null && !m.kind().equals("COMBAT") && m.xp() > 0) {
            String t = L.t("wynnlabs.quests.mini_prof_xp", QuestAdvisor.fmt(m.xp()), Professions.name(m.kind()));
            int cw = Ui.iconChipWidth(font(), professionIcon(m.kind()), t, false);
            if (cx + cw <= x + w) {
                Ui.iconChip(g, font(), professionIcon(m.kind()), t, false, cx, y, cw, false, -1, -1);
            }
        }
        return y + Ui.CHIP_H + Theme.SP_5;
    }

    private static String professionIcon(String kind) {
        return switch (kind) {
            case "MINING" -> "pick";
            case "WOODCUTTING" -> "hammer";
            case "FARMING" -> "leaf";
            case "FISHING" -> "droplet";
            default -> "sparkles";
        };
    }

    /** The mob a slaying post sends you after, from the wiki's mob list. */
    private int mob(GuiGraphics g, int x, int y, int w, String name, MiniQuests.Mini m, QuestBook.Entry q) {
        if (m != null && !m.kind().equals("COMBAT")) {
            return y;
        }
        int level = m != null ? m.level() : q == null ? 0 : q.level();
        MiniQuests.MobInfo mob = QuestsCache.kept("mini|mob|" + name, 60_000, () -> MiniQuests.mob(name, level, MobWiki.kept()));
        if (mob == null) {
            return y;
        }
        y = head(g, L.t("wynnlabs.quests.mini_enemy"), x, y, w);
        List<String> f = new ArrayList<>();
        f.add(mob.name());
        if (mob.max() > 0) {
            f.add(mob.min() == mob.max() ? L.t("wynnlabs.quests.mini_level", mob.max()) : L.t("wynnlabs.quests.mini_levels", mob.min(), mob.max()));
        }
        if (!mob.where().isEmpty()) {
            f.add(mob.where());
        }
        Art.icon(g, "skull", x, Type.BODY.onCaps(y, 8), 8, Theme.INK_3);
        Type.BODY.draw(g, font(), Type.BODY.fit(font(), String.join(" · ", f), w - 8 - Theme.SP_2), x + 8 + Theme.SP_2, y, Theme.INK_2);
        return y + Type.BODY.lineHeight + Theme.SP_5;
    }

    /** The post's place and province with an "Open on map" chip; a locked gathering post, your profession's way there. */
    private int where(GuiGraphics g, int x, int y, int w, MiniQuests.Mini m, int[] at, int mx, int my) {
        if (m == null || m.place().isEmpty()) {
            return y;
        }
        y = head(g, L.t("wynnlabs.quests.mini_where"), x, y, w);
        String place = m.place() + (m.province().isEmpty() ? "" : " · " + m.province());
        String open = L.t("wynnlabs.quests.mini_open_map");
        int cw = Ui.iconChipWidth(font(), "map-2", open, false);
        boolean chip = 12 + 48 + Theme.SP_4 + cw <= w; // room for a few words of the place too
        int ty = Type.BODY.yIn(y, Ui.CHIP_H);
        Art.icon(g, "map-pin", x, Type.BODY.onCaps(ty, 8), 8, Theme.INK_3);
        int room = w - 8 - Theme.SP_2 - (chip ? cw + Theme.SP_4 : 0);
        Type.BODY.draw(g, font(), Type.BODY.fit(font(), place, room), x + 8 + Theme.SP_2, ty, Theme.INK_2);
        if (chip) {
            Ui.iconChip(g, font(), "map-2", open, false, x + w - cw, y, cw, false, mx, my);
            menu.hits.add(new MenuHit(x + w - cw, y, cw, Ui.CHIP_H, null, null, "#qmapopen:" + at[0] + " " + at[2]));
        }
        y += Ui.CHIP_H - Type.BODY.lineHeight;
        y += Type.BODY.lineHeight + Theme.SP_3;
        if (!m.kind().equals("COMBAT")) {
            int have = Professions.level(m.kind());
            if (have >= 0 && have < m.level()) {
                String t = L.t("wynnlabs.quests.mini_prof_way", Professions.name(m.kind()), have, m.level());
                Type.BODY.draw(g, font(), t, x, y, Theme.INK_2);
                int bx = x + Type.BODY.width(font(), t) + Theme.SP_3;
                Art.pill(g, bx, Type.BODY.onCaps(y, 3), 80, 3, Theme.WELL, 0);
                Art.pill(g, bx, Type.BODY.onCaps(y, 3), Math.max(3, 80 * have / m.level()), 3, Theme.GOLD_DEEP, 0);
                Type.META.draw(g, font(), L.t("wynnlabs.quests.mini_levels_left", m.level() - have), bx + 84, y + 1, Theme.INK_3);
                y += Type.BODY.lineHeight + Theme.SP_3;
            }
        }
        return y + Theme.SP_2;
    }

    // ---- the foot ----

    /** Pusula › (the main action), Takip et. */
    private void foot(GuiGraphics g, int x, int y, int w, String name, QuestBook.Entry q, int[] at, int mx, int my) {
        int bx = x;
        if (at != null) {
            String t = L.t("wynnlabs.quests.mini_compass");
            int bw = Ui.iconButtonLabelWidth(font(), t);
            Ui.iconButtonLabel(g, font(), "compass", t, bx, y, bw, mx, my);
            menu.hits.add(new MenuHit(bx, y, bw, Ui.BUTTON_H, null, null, "/compass at " + at[0] + " " + at[1] + " " + at[2]));
            bx += bw + Theme.SP_2;
        }
        if (q != null && !"COMPLETED".equals(q.status())) {
            boolean on = name.equals(QuestsData.tracked());
            String t = L.t(on ? "wynnlabs.quests.btn_tracked" : "wynnlabs.quests.btn_track");
            int tw = Ui.iconChipWidth(font(), "bookmark", t, false);
            int cy = y + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
            Ui.iconChip(g, font(), "bookmark", t, false, bx, cy, tw, on, mx, my);
            menu.hits.add(new MenuHit(bx, cy, tw, Ui.CHIP_H, null, null, "#qtrack:" + name));
        }
    }
}
