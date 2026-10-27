package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.BuildSources;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.DevHooks;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.SkillKind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The build window's RESULTS view: the ranked builds as cards (score, label, price, the nine
 * pieces as icons), the picked one's details (changes against what you wear, skill points, the
 * equip order, what to get and where from), and Cancel / "Apply build N".
 */
final class BuildResultsView {

    private final BuildWindow win;
    private final BuildControls ui;

    BuildResultsView(BuildWindow win, BuildControls ui) {
        this.win = win;
        this.ui = ui;
    }

    void drawResults(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        if (DevHooks.ON) {
            DevHooks.part("results", g.pose(), x, y - 3, w, h + 3);
        }
        Around.Result r = win.result;
        int n = r == null ? 0 : r.builds().size();
        // what they are around (the tab says how many), and the level planned for when above yours
        String around = r == null || r.around().isEmpty() ? L.t("wynnlabs.results.around_none") : L.t("wynnlabs.results.around", String.join(", ", r.around()));
        if (r != null && r.level() > GameState.current().level()) {
            around += " · " + L.t("wynnlabs.results.planned_for", r.level());
        }
        String shown = Type.META.fit(ui.font, around, w);
        Type.META.draw(g, ui.font, shown, x, y, Theme.INK_3);
        if (!shown.equals(around)) {
            host.tip(x, y, w, Type.META.lineHeight, List.of(Component.literal(around).withStyle(ChatFormatting.LIGHT_PURPLE)));
        }
        int top = y + Type.META.lineHeight + Theme.SP_4;
        int footY = y + h - Ui.BUTTON_H;
        if (r == null || r.builds().isEmpty()) {
            empty(g, host, r, x, top, w, footY - Theme.SP_4 - top, mx, my);
            return;
        }
        if (win.card != shownCard) {
            detailScroll = 0;                       // another build: its details from the top
            shownCard = win.card;
        }
        win.card = Math.max(0, Math.min(win.card, n - 1));
        if (r.problem() != null) {
            // the chosen items could not be held: these are the builds without them
            top += Ui.notice(g, ui.font, Ui.Level.WARNING, List.of(L.t("wynnlabs.pick.problem", r.problem().text())), null, x, top, w, 40, mx, my,
                    new int[1][]) + Theme.SP_4;
        }
        int bodyH = footY - Theme.SP_4 - top;
        boolean two = !win.compact && w >= 380;             // the compact menu: one column, the cards on top
        if (two) {
            int lw = Math.max(176, Math.min(220, w * 40 / 100));
            int ch = Math.min(CARD_H, (bodyH - (n - 1) * Theme.SP_3) / n);
            for (int i = 0; i < n; i++) {
                cardBox(g, host, r.builds().get(i), i, x, top + i * (ch + Theme.SP_3), lw, ch, mx, my, false);
            }
            scrolledDetails(g, host, r.builds().get(win.card), x + lw + Theme.SP_6, top, w - lw - Theme.SP_6, bodyH, 18, mx, my);
        } else {
            // narrow: the cards two by two on top, the details under them
            int cols = Math.min(2, n);
            int rowsN = (n + cols - 1) / cols;
            int cw = (w - (cols - 1) * Theme.SP_3) / cols;
            int ch = SMALL_CARD_H;
            for (int i = 0; i < n; i++) {
                cardBox(g, host, r.builds().get(i), i, x + (i % cols) * (cw + Theme.SP_3), top + (i / cols) * (ch + Theme.SP_3), cw, ch, mx, my, true);
            }
            int used = rowsN * (ch + Theme.SP_3) + Theme.SP_3;
            scrolledDetails(g, host, r.builds().get(win.card), x, top + used, w, bodyH - used, 14, mx, my);
        }
        footer(g, host, x, footY, w, mx, my, true);
    }

    /** No build: the empty page with why, and the one way on (the budget off, or back to the items). */
    private void empty(GuiGraphics g, BuildWindow.Host host, Around.Result r, int x, int y, int w, int h, int mx, int my) {
        boolean budget = r != null && r.problem() == null && r.tuning().budget() > 0;
        String title = r != null && r.problem() != null ? L.t("wynnlabs.pick.problem", r.problem().text())
                : budget ? L.t("wynnlabs.results.none_budget", BuildText.budgetText(r.tuning().budget())) : L.t("wynnlabs.results.none");
        UiEmpty.Action a = budget ? new UiEmpty.Action("coin", L.t("wynnlabs.results.budget_off"), "budgetNone")
                : new UiEmpty.Action("arrow-left", L.t("wynnlabs.results.back"), "back");
        int[] box = UiEmpty.draw(g, ui.font, x, y, w, h, win.compact, "sparkles", title, List.of(), a, null, mx, my);
        if (box != null) {
            ui.btn(box[0], box[1], box[2], box[3], a.id(), null);
        }
    }

    /** A win.card's height: its score line, its price line and the row of item icons. */
    private static final int CARD_H = 2 * Theme.SP_3 + 12 + Theme.SP_2 + 18;
    /** A win.compact win.card's: the score line, then the 12px icons with the price beside them. */
    private static final int SMALL_CARD_H = 2 * Theme.SP_3 + 12 + Theme.SP_2 + 14;

    /**
     * One build's win.card: rank and score with its label, the price (coin and amount), and its nine
     * pieces as item icons in the panel's slot order, each framed by what it is (purple a chosen
     * item, green a change from what you wear, plain unchanged); an icon's hover is the item's tooltip.
     */
    private void cardBox(GuiGraphics g, BuildWindow.Host host, Roadmap.Candidate c, int i, int x, int y, int w, int h, int mx, int my, boolean small) {
        boolean on = i == win.card;
        Kit.softCard(g, x, y, w, h);
        if (on) {
            Kit.outline(g, x, y, w, h + 1, Theme.GOLD_DEEP);
        }
        String score = "#" + (i + 1) + " · " + String.format(Locale.ROOT, "%,d", Math.round(c.score()));
        if (small && ui.width(score, true) > w / 2) {
            score = "#" + (i + 1) + " · " + shortNum(c.score());
        }
        int pad = Theme.SP_3;
        int titleY = y + pad;
        int sealX = x + w - pad;
        if (!c.labels().isEmpty()) {
            // the label as a light seal on the title row's right: its glyph and word, the glyph alone when tight
            String l = c.labels().get(0);
            int col = BuildText.labelColour(l);
            String name = L.t(labelKey(l, false));
            int wordW = Type.META.width(ui.font, name);
            boolean word = w - 2 * pad - ui.width(score, true) - Theme.SP_3 >= 8 + Theme.SP_1 + wordW + 2 * Theme.SP_2;
            int sw = word ? 8 + Theme.SP_1 + wordW + 2 * Theme.SP_2 : Ui.SEAL_H + 2;
            sealX = x + w - pad - sw;
            int sy = Type.LABEL.onCaps(titleY, Ui.SEAL_H);
            Kit.seal(g, sealX, sy, sw, Ui.SEAL_H, KindBadge.light(col));
            Kit.pillLine(g, sealX, sy, sw, Ui.SEAL_H, col);
            int gx = word ? sealX + Theme.SP_2 : sealX + (sw - 8) / 2;
            Art.icon(g, BuildText.labelIcon(l), gx, sy + (Ui.SEAL_H - 8) / 2, 8, col);
            if (word) {
                Type.META.draw(g, ui.font, name, gx + 8 + Theme.SP_1, Type.META.yIn(sy, Ui.SEAL_H), col);
            }
            host.tip(sealX, sy, sw, Ui.SEAL_H, List.of(L.c(labelKey(l, false)).withStyle(ChatFormatting.WHITE),
                    L.c(labelKey(l, true)).withStyle(ChatFormatting.GRAY)));
        }
        Type.LABEL.draw(g, ui.font, ui.fit(score, sealX - Theme.SP_3 - x - pad, true), x + pad, titleY, Theme.INK);
        // the items' row under it, the price at its right end; the icons shrink before the price is cut
        String priceText = priceText(c.price());
        int priceW = priceText == null ? 0 : (Double.isNaN(c.price()) || c.price() <= 0 ? 0 : 10) + ui.width(priceText, false);
        int room = w - 2 * pad - priceW - Theme.SP_3;
        int s = small ? 12 : 16;
        while (s > 10 && 9 * (s + 3) - 1 > room) {
            s -= 2;
        }
        int iy = titleY + Type.LABEL.lineHeight + Theme.SP_2 + 1;
        icons(g, host, x + pad, iy, s, c.set(), c.fixed(), BuildSources.worn(), room);
        price(g, c.price(), x + w - pad - priceW, Type.BODY.yIn(iy - 1, s + 2), priceW);
        ui.btn(x, y, w, h, "card", i);
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(score).withStyle(ChatFormatting.WHITE));
        for (String l : c.labels()) {
            tip.add(L.c(labelKey(l, true)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tip.add(L.c("wynnlabs.results.squares_tip").withStyle(ChatFormatting.DARK_GRAY));
        // the win.card's own tooltip on its text only: the icons have the items'
        host.tip(x, y, w, iy - y - 1, tip);
    }

    /** "1.2 LE" after the coin, "nothing to buy", or "price unknown", within {@code room}. */
    /** The price's words: "1.2 LE", "nothing to buy" or "price unknown". */
    private static String priceText(double price) {
        if (Double.isNaN(price) || price <= 0) {
            return L.t(Double.isNaN(price) ? "wynnlabs.results.price_unknown" : "wynnlabs.results.price_free");
        }
        return Prices.fmt(price);
    }

    private void price(GuiGraphics g, double price, int x, int y, int room) {
        if (Double.isNaN(price) || price <= 0) {
            String t = priceText(price);
            Kit.text(g, ui.font, ui.fit(t, room, false), x, y, Theme.INK_3);
            return;
        }
        if (room < 20) {
            return;
        }
        Art.icon(g, "coin", x, y, 8, Theme.PRICE);
        Kit.text(g, ui.font, ui.fit(Prices.fmt(price), room - 10, false), x + 10, y, Theme.PRICE);
    }


    private static String shortNum(double v) {
        return v >= 10_000 ? Math.round(v / 1000) + "k" : String.valueOf(Math.round(v));
    }

    /**
     * The nine pieces as item icons of {@code s} pixels in the panel's slot order, each in a 1px
     * frame: purple a chosen item, green a change from what you wear, the slot's edge colour when
     * it stays; an empty slot is its frame alone. Each icon's hover is the item's own tooltip
     * with where it comes from. Returns the width drawn; fewer gaps when {@code room} is tight.
     */
    private int icons(GuiGraphics g, BuildWindow.Host host, int x, int y, int s, Map<String, Damage.Piece> set, java.util.Set<String> fixed,
            Map<String, String> worn, int room) {
        int gap = room >= 9 * (s + 2) + 8 * 2 ? 2 : 1;
        int step = s + 2 + gap;
        for (int i = 0; i < Plans.SLOTS.length; i++) {
            String slot = Plans.SLOTS[i];
            Damage.Piece p = set.get(slot);
            String name = p == null ? null : p.name();
            boolean locked = fixed.contains(slot);
            int frame = locked ? BuildControls.CHOSEN : name != null && !name.equals(worn.get(slot)) ? Theme.GREEN : Theme.INK_3;
            int fx = x + i * step;
            // the kit's item slot; its edge in the slot's colour (none when it stays as you wear it)
            Kit.slot(g, fx, y - 1, s + 2, frame == Theme.INK_3 ? 0 : frame);
            if (name == null) {
                continue;
            }
            ItemData info = BuildSources.itemOf(name);
            ItemStack icon = info == null ? ItemStack.EMPTY : host.icon(info);
            if (!icon.isEmpty()) {
                renderIcon(g, icon, fx + 1, y, s);
                host.item(fx, y - 1, s + 2, s + 2, info, icon, itemExtra(name, locked, p.stack() != null));
                if (locked && win.firstIcon == null) {
                    win.firstIcon = new int[] {fx, y - 1, s + 2, s + 2};
                }
            }
        }
        return 9 * step - gap;
    }

    /** An item icon at 16 or 12 pixels (scaled down whole). */
    private static void renderIcon(GuiGraphics g, ItemStack stack, int x, int y, int s) {
        if (s == 16) {
            g.renderItem(stack, x, y);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(s / 16f, s / 16f);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    /** The lines under an item's own tooltip: where it comes from, and "chosen by you" for a chosen one. */
    private List<Component> itemExtra(String name, boolean locked, boolean ownedStack) {
        List<Component> out = new ArrayList<>();
        boolean have = ownedStack || BuildSources.haveNames().contains(name) || BuildSources.worn().containsValue(name);
        Object[] src = BuildSources.source(name);
        String where = have ? L.t("wynnlabs.results.have")
                : src[1] != null ? L.t("wynnlabs.results.market", Prices.fmt((double) src[1])) : (String) src[0];
        out.add(Component.literal(where).withStyle(have ? ChatFormatting.GREEN : ChatFormatting.DARK_AQUA));
        if (locked) {
            out.add(L.c("wynnlabs.results.chosen_by_you").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return out;
    }

    /**
     * The picked build: its nine pieces as icons, what changes against what you wear, its skill
     * points, the equip order, and what to get (each with its icon and where it comes from, a
     * price after the coin). {@code s}: the icon size.
     */
    /** The details' scroll and bar: the whole of them reachable at every size (wheel, drag, track). */
    int detailScroll;
    private int shownCard = -1;
    private int detailMax;
    int[] detailBox;
    final ScrollBar detailBar = new ScrollBar();

    /** The details in their own clipped, scrolling column, the bar on its right when they do not fit. */
    private void scrolledDetails(GuiGraphics g, BuildWindow.Host host, Roadmap.Candidate c, int x, int y, int w, int h, int s, int mx, int my) {
        detailBox = new int[] {x, y, w, h};
        int cw = detailMax > 0 ? w - ScrollBar.W - Theme.SP_3 : w;
        detailScroll = Math.max(0, Math.min(detailScroll, detailMax));
        boolean in = BuildControls.in(mx, my, x, y, w, h);
        g.enableScissor(x - Theme.SP_2, y - Theme.SP_1, x + w, y + h);
        int top = y - detailScroll;
        int end = details(g, host, c, x, top, cw, 100_000, s, in ? mx : -1, in ? my : -1);
        g.disableScissor();
        int total = end - top + Theme.SP_2;
        detailMax = Math.max(0, total - h);
        detailBar.place(x + w - ScrollBar.W, y, h, h, total, detailScroll);
        detailBar.draw(g, mx, my);
    }

    private int details(GuiGraphics g, BuildWindow.Host host, Roadmap.Candidate c, int x, int y, int w, int h, int s, int mx, int my) {
        int bottom = y + h;
        Map<String, String> worn = BuildSources.worn();
        Build.Plan plan = Build.plan();
        Damage.Result now = plan == null ? null : plan.current();
        Damage.Result to = c.eval().result();
        // the changes: damage, effective health, mana regen, walk speed, each a row
        List<String[]> deltas = new ArrayList<>();
        if (now != null && now.factors().length > 0 && to.factors().length > 0) {
            deltas.add(new String[] {L.t("wynnlabs.results.d_damage"), pct(now.factors()[0], to.factors()[0])});
        }
        if (now != null) {
            deltas.add(new String[] {L.t("wynnlabs.results.d_ehp"), pct(now.ehp(), to.ehp())});
        }
        double regenNow = plan == null || plan.worn() == null ? 0 : stat(plan.worn(), "manaRegen");
        double regenTo = stat(c.set(), "manaRegen");
        if (Math.abs(regenTo - regenNow) >= 0.5) {
            deltas.add(new String[] {L.t("wynnlabs.results.d_mana"), (regenTo - regenNow >= 0 ? "+" : "−") + Math.round(Math.abs(regenTo - regenNow))});
        }
        if (now != null && Math.abs(to.speed() - now.speed()) >= 1) {
            deltas.add(new String[] {L.t("wynnlabs.results.d_speed"), (to.speed() - now.speed() >= 0 ? "+" : "−")
                + Math.round(Math.abs(to.speed() - now.speed())) + "%"});
        }
        // the title, "Detailed ›" at its right: every stat on the Home
        String more = L.t("wynnlabs.results.detailed");
        int mw = Type.LINK.width(ui.font, more);
        int ly = title(g, L.t("wynnlabs.results.vs_now"), null, x, y, w, more, mx, my);
        ui.btn(x + w - mw, y, mw, Type.LABEL.lineHeight, "detailed", null);
        host.tip(x + w - mw, y, mw, Type.LABEL.lineHeight, List.of(L.c("wynnlabs.results.detailed_tip").withStyle(ChatFormatting.GRAY)));
        for (String[] d : deltas) {
            if (ly + Type.BODY.lineHeight > bottom) {
                break;
            }
            Type.BODY.draw(g, ui.font, d[0], x, ly, Theme.INK_2);
            Type.LABEL.drawRight(g, ui.font, d[1], x + w, ly, d[1].startsWith("−") ? Theme.BAD : Theme.GOOD);
            ly += Type.BODY.lineHeight + Theme.SP_1;
        }
        ly += Theme.SP_3;
        // the skill points to assign
        StringBuilder sp = new StringBuilder();
        int[] a = c.eval().allocation();
        for (SkillKind k : SkillKind.values()) {
            if (a[k.ordinal()] > 0) {
                sp.append(sp.length() > 0 ? " · " : "").append(k.displayName(), 0, 3).append(' ').append(a[k.ordinal()]);
            }
        }
        if (ly + Type.META.lineHeight <= bottom) {
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, L.t("wynnlabs.results.points", sp.length() == 0 ? "—" : sp.toString()), w), x, ly, Theme.INK_2);
            ly += Type.META.lineHeight + Theme.SP_3;
        }
        String order = equipOrder(c);
        if (order != null && ly + Ui.noticeHeight(1) <= bottom) {
            ly += Ui.notice(g, ui.font, Ui.Level.WARNING, List.of(L.t("wynnlabs.results.order", order)), null, x, ly, w, 40, mx, my, new int[1][])
                    + Theme.SP_4;
        }
        if (ly + Type.LABEL.lineHeight > bottom) {
            return ly;
        }
        java.util.Set<String> have = BuildSources.haveNames();
        List<String> slots = new ArrayList<>();
        for (String slot : Plans.SLOTS) {
            Damage.Piece p = c.set().get(slot);
            if (p != null && !p.name().equals(worn.get(slot))) {
                slots.add(slot);
            }
        }
        ly = title(g, L.t("wynnlabs.results.to_get"), slots.isEmpty() ? null : String.valueOf(slots.size()), x, ly, w, null, mx, my);
        if (slots.isEmpty()) {
            Type.BODY.draw(g, ui.font, Type.BODY.fit(ui.font, L.t("wynnlabs.results.nothing_to_get"), w), x, ly, Theme.GOOD);
            return ly + Type.BODY.lineHeight;
        }
        int textH = Type.BODY.lineHeight + Type.META.lineHeight;
        int pitch = Math.max(s, textH) + Theme.SP_2;
        for (int i = 0; i < slots.size(); i++) {
            if (ly + pitch > bottom || ly + 2 * pitch > bottom && i < slots.size() - 1) {
                int left = slots.size() - i;
                Type.META.draw(g, ui.font, L.n("wynnlabs.results.more", left, left), x, ly, Theme.INK_3);
                break;
            }
            String slot = slots.get(i);
            Damage.Piece p = c.set().get(slot);
            boolean locked = c.fixed().contains(slot);
            boolean owned = p.stack() != null || have.contains(p.name());
            ItemData info = BuildSources.itemOf(p.name());
            ItemStack icon = info == null ? ItemStack.EMPTY : host.icon(info);
            int sy = ly + (Math.max(s, textH) - s) / 2;
            int is = Math.min(16, s - 2);
            Kit.slot(g, x, sy, s, 0);
            if (!icon.isEmpty()) {
                renderIcon(g, icon, x + (s - is) / 2, sy + (s - is) / 2, is);
                host.item(x, sy, s, s, info, icon, itemExtra(p.name(), locked, p.stack() != null));
            }
            int tx = x + s + Theme.SP_2;
            Component nm = Component.literal(Type.BODY.fit(ui.font, p.name(), w - (tx - x) - 12))
                    .withStyle(info == null ? ChatFormatting.WHITE : info.tier().colour());
            Kit.text(g, ui.font, nm, tx, ly, Theme.INK);
            if (locked) {
                Art.icon(g, "lock", tx + ui.font.width(nm) + Theme.SP_2, Type.BODY.onCaps(ly, 8), 8, BuildControls.CHOSEN);
            }
            String where;
            int ink = Theme.INK_3;
            if (owned) {
                where = L.t("wynnlabs.results.have");
                ink = Theme.GOOD;
            } else {
                Object[] src = BuildSources.source(p.name());
                where = src[1] != null ? L.t("wynnlabs.results.market", Prices.fmt((double) src[1])) : (String) src[0];
            }
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, where, w - (tx - x)), tx, ly + Type.BODY.lineHeight, ink);
            ly += pitch;
        }
        return ly;
    }

    /** A section's title (LABEL, its META count), a link at its right, a hairline; returns the y under it. */
    private int title(GuiGraphics g, String text, String meta, int x, int y, int w, String link, int mx, int my) {
        Type.LABEL.draw(g, ui.font, text, x, y, Theme.INK);
        if (meta != null) {
            Type.META.draw(g, ui.font, meta, x + Type.LABEL.width(ui.font, text) + Theme.SP_3, y + 1, Theme.INK_3);
        }
        if (link != null) {
            int lw = Type.LINK.width(ui.font, link);
            boolean hov = BuildControls.in(mx, my, x + w - lw, y, lw, Type.LABEL.lineHeight);
            Type.LINK.draw(g, ui.font, link, x + w - lw, y, hov ? Theme.GOLD_DEEP : Theme.LINK);
        }
        int hy = y + Type.LABEL.lineHeight + Theme.SP_2;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_3;
    }

    private static String pct(double from, double to) {
        if (from <= 0) {
            return "—";
        }
        double p = (to / from - 1) * 100;
        return (p >= 0 ? "+" : "−") + Math.round(Math.abs(p)) + "%";
    }

    private static double stat(Map<String, Damage.Piece> set, String key) {
        double s = 0;
        for (Damage.Piece p : set.values()) {
            if (p != null) {
                s += p.stats().getOrDefault(key, 0.0);
            }
        }
        return s;
    }

    /**
     * The order to put the pieces on when it matters: a piece whose skill requirement is met only
     * by another piece's skill bonus goes on after it ("Yume before Prologue"); null when any order works.
     */
    private static String equipOrder(Roadmap.Candidate c) {
        int[] a = c.eval().allocation();
        for (Damage.Piece q : c.set().values()) {
            if (q == null) {
                continue;
            }
            for (var e : q.reqs().entrySet()) {
                if (a[e.getKey().ordinal()] >= e.getValue()) {
                    continue;
                }
                String key = "raw" + e.getKey().displayName();
                for (Damage.Piece p : c.set().values()) {
                    if (p != null && p != q && p.helpsRequirements() && p.stats().getOrDefault(key, 0.0) > 0) {
                        return L.t("wynnlabs.results.before", p.name(), q.name());
                    }
                }
            }
        }
        return null;
    }

    /** "Items" (the results stay) on the left, the purple "Apply build N" (or "Overwrite" for a saved build's recompute) on the right. */
    private void footer(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int mx, int my, boolean any) {
        String apply = win.overwriteId != null ? L.t("wynnlabs.results.overwrite", win.card + 1) : L.t("wynnlabs.results.apply", win.card + 1);
        int aw = ui.controlW("check", apply, w / 2, BuildControls.PRIMARY);
        if (any) {
            ui.control(g, host, x + w - aw, y, Ui.BUTTON_H, "check", apply, BuildControls.PRIMARY, false, w / 2, "apply", null,
                    List.of(L.c("wynnlabs.results.apply_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        }
        String back = L.t("wynnlabs.results.back");
        ui.control(g, host, x, y + (Ui.BUTTON_H - Ui.CHIP_H) / 2, Ui.CHIP_H, "arrow-left", back, BuildControls.WOOD, false, w / 3, "back", null,
                List.of(L.c("wynnlabs.results.back_tip").withStyle(ChatFormatting.GRAY)), mx, my);
    }

    /** A label's pill text (or its tooltip line). */
    private static String labelKey(String label, boolean tip) {
        return switch (label) {
            case "best" -> tip ? "wynnlabs.results.label_best_tip" : "wynnlabs.results.label_best";
            case "cheap" -> tip ? "wynnlabs.results.label_cheap_tip" : "wynnlabs.results.label_cheap";
            case "tanky" -> tip ? "wynnlabs.results.label_tanky_tip" : "wynnlabs.results.label_tanky";
            default -> tip ? "wynnlabs.results.label_fast_tip" : "wynnlabs.results.label_fast";
        };
    }
}
