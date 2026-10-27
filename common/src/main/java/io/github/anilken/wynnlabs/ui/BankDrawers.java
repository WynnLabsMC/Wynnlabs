package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.Verdict;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The bank's two drawers. A slot's: where it is first (bank, page, slot, and its other places),
 * then what it is (verdict, quests, professions, a box's outcomes), then "if you want to sell it"
 * (Trade Market or Blacksmith), with "Open in Search" and "Copy name". "To sell": what the rules
 * say to sell, by where, with the total and the list to copy.
 */
final class BankDrawers {

    private final BankTab tab;
    /** This frame's click areas in the drawer: {x, y, w, h} and what they do. */
    final List<Object[]> acts = new ArrayList<>();
    private BankIndex.Item item;
    private BankIndex.Page page;

    BankDrawers(BankTab tab) {
        this.tab = tab;
    }

    BankIndex.Item item() {
        return item;
    }

    void openItem(UiDrawer drawer, BankIndex.Item it, BankIndex.Page p) {
        item = it;
        page = p;
        drawer.show(it.st().name(), this::itemContent);
    }

    void openSell(UiDrawer drawer) {
        item = null;
        drawer.show(L.t("wynnlabs.bank.sell_title"), this::sellContent);
    }

    private void act(int x, int y, int w, int h, Runnable r) {
        acts.add(new Object[] {new int[] {x, y, w, h}, r});
    }

    private int head(GuiGraphics g, Font font, String title, String meta, int x, int y, int w) {
        Type.LABEL.draw(g, font, title, x, y);
        if (meta != null) {
            int mx = x + Type.LABEL.width(font, title) + Theme.SP_2;
            Type.META.draw(g, font, Type.META.fit(font, meta, x + w - mx), mx, y + 1, Theme.INK_3);
        }
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        return y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
    }

    private static void icon(GuiGraphics g, ItemStack stack, int x, int y, int box) {
        Kit.slot(g, x, y, box, 0);
        if (stack == null) {
            return;
        }
        int isz = box - 4;
        g.pose().pushMatrix();
        g.pose().translate(x + 2, y + 2);
        g.pose().scale(isz / 16f, isz / 16f);
        g.renderItem(stack, 0, 0);
        g.pose().popMatrix();
    }

    // ---- one slot ----

    private int itemContent(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        Font font = tab.font();
        BankIndex.Item it = item;
        if (it == null) {
            return 0;
        }
        Vault.Stored st = it.st();
        Vault.Group gr = it.group();
        int cy = y;
        // what it is, in one line
        List<String> what = new ArrayList<>();
        if (it.kind() != BankIndex.Kind.OTHER) {
            what.add(it.kind().label());
        }
        if (it.tier() != null) {
            what.add(MenuPaint.nice(it.tier().name()));
        }
        if (st.level() > 0) {
            what.add("Lv " + st.level());
        }
        if (!what.isEmpty()) {
            Type.META.draw(g, font, Type.META.fit(font, String.join(" · ", what), w), x, cy, Theme.INK_2);
            cy += Type.META.lineHeight + Theme.SP_4;
        }
        // where: the page, the bank, the slot, how many
        icon(g, it.stack(), x, cy, 24);
        int tx = x + 24 + Theme.SP_3;
        Type.HEADING.draw(g, font, Type.HEADING.fit(font, page.name(), x + w - tx), tx, cy);
        int idx = st.slot();
        String slotNo = idx < 0 ? "?" : String.valueOf((idx / 9) * BankIndex.get().cols + idx % 9 + 1);
        String where = groupTitle(page) + " · " + L.t("wynnlabs.bank.where_line", page.number(), slotNo, st.count());
        Type.META.draw(g, font, Type.META.fit(font, where, x + w - tx), tx, cy + Type.HEADING.lineHeight, Theme.INK_2);
        cy += 24 + Theme.SP_5;
        // the same item elsewhere
        if (gr != null && gr.places().size() > 1) {
            cy = head(g, font, L.t("wynnlabs.bank.other_places"), null, x, cy, w);
            for (Vault.Stored o : gr.places()) {
                if (o == st) {
                    continue;
                }
                String t = Vault.label(o.page()) + (o.slot() >= 0 ? " · " + L.t("wynnlabs.bank.slot_no",
                        (o.slot() / 9) * BankIndex.get().cols + o.slot() % 9 + 1) : "");
                String n = "×" + o.count();
                boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + Type.LINK.lineHeight;
                Type.LINK.draw(g, font, Type.LINK.fit(font, t, w - Type.META.width(font, n) - Theme.SP_3), x, cy, hover ? Theme.INK : Theme.LINK);
                Type.META.drawRight(g, font, n, x + w, cy + 1, Theme.INK_3);
                act(x, cy, w, Type.LINK.lineHeight, () -> tab.goTo(o));
                cy += Type.LINK.lineHeight + Theme.SP_2;
            }
            cy += Theme.SP_4 - Theme.SP_2;
        }
        // what it is to you
        List<String[]> lines = aboutLines(it);
        if (!lines.isEmpty()) {
            cy = head(g, font, L.t("wynnlabs.bank.this_item"), null, x, cy, w);
            for (String[] l : lines) {
                Type role = l[1] == null ? Type.BODY : Type.LABEL;
                cy = role.drawWrapped(g, font, l[0], x, cy, w, 0, l[1] == null ? Theme.INK_2 : Theme.INK) + Theme.SP_1;
            }
            cy += Theme.SP_4 - Theme.SP_1;
        }
        // if you want to sell it
        if (gr != null && gr.reachable()) {
            cy = sellRows(g, font, it, x, cy, w);
        }
        // the actions
        cy += Theme.SP_2;
        String open = L.t("wynnlabs.bank.open_search");
        int bw = Ui.iconButtonLabelWidth(font, open);
        Ui.iconButtonLabel(g, font, "search", open, x, cy, bw, mx, my);
        String name = st.name();
        act(x, cy, bw, Ui.BUTTON_H, () -> tab.openSearch(name));
        String copy = L.t("wynnlabs.bank.copy_name");
        int cw = Ui.iconChipWidth(font, "copy", copy, false);
        int chy = cy + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        Ui.iconChip(g, font, "copy", copy, false, x + bw + Theme.SP_2, chy, cw, false, mx, my);
        tab.menu.hits.add(new MenuHit(x + bw + Theme.SP_2, chy, cw, Ui.CHIP_H, null, null, name));
        cy += Ui.BUTTON_H;
        return cy - y;
    }

    private static String groupTitle(BankIndex.Page p) {
        for (BankIndex.Group gr : BankIndex.get().groups) {
            if (gr.pages().contains(p)) {
                return gr.title();
            }
        }
        return Vault.bankName(p.key());
    }

    /** The verdict (a title line, {text, "title"}) and why, then quests, professions, a box's outcomes. */
    private static List<String[]> aboutLines(BankIndex.Item it) {
        Vault.Stored st = it.st();
        Vault.Group gr = it.group();
        List<String[]> out = new ArrayList<>();
        if (st.verdict() != null) {
            Verdict v = Verdict.of(st.verdict());
            String where = it.mark() == Theme.MARK_SELL && gr != null ? Vault.place(gr) : null;
            out.add(new String[] {v.label() + (where == null ? "" : " → " + where), "title"});
            String note = Vault.noteNow(st);
            out.add(new String[] {note != null ? note : v.meaning(), null});
        }
        if (gr != null) {
            for (String q : Vault.questsFor(gr)) {
                out.add(new String[] {L.t("wynnlabs.bank.tip_quest", q), null});
            }
            if (!gr.reachable()) {
                out.add(new String[] {L.t("wynnlabs.bank.tip_other_char"), null});
            }
        }
        String prof = Vault.professions(st);
        if (prof != null) {
            out.add(new String[] {L.t("wynnlabs.bank.tip_prof", prof), null});
        }
        if (Vault.boxOf(st) != null) {
            List<Component> box = new ArrayList<>();
            BankTips.boxLines(Vault.boxOf(st), box, true);
            for (Component c : box) {
                String s = c.getString().strip();
                if (!s.isEmpty()) {
                    out.add(new String[] {s, null});
                }
            }
        }
        return out;
    }

    /** "If you want to sell it": the Trade Market's price (or why there is none) and the Blacksmith. */
    private int sellRows(GuiGraphics g, Font font, BankIndex.Item it, int x, int y, int w) {
        Vault.Group gr = it.group();
        String place = it.mark() == Theme.MARK_SELL ? Vault.place(gr) : null;
        int cy = head(g, font, L.t("wynnlabs.bank.if_sell"), null, x, y, w);
        Double unit = BankIndex.unitPrice(gr.name());
        String price = unit == null ? "—" : "~" + Prices.fmt(unit) + (gr.total() > 1 ? " · " + L.t("wynnlabs.bank.all_worth", Prices.fmt(unit * gr.total())) : "");
        cy = sellRow(g, font, "shopping-cart", L.t("wynnlabs.bank.place_market"), price, "Trade Market".equals(place),
                unit == null ? Theme.INK_3 : Theme.PRICE, x, cy, w);
        if (unit == null) {
            cy = Type.META.drawWrapped(g, font, L.t("wynnlabs.bank.unpriced_one"), x + 8 + Theme.SP_2, cy - Theme.SP_2, w - 8 - Theme.SP_2, 2,
                    Theme.WARN) + Theme.SP_2;
        }
        cy = sellRow(g, font, "hammer", L.t("wynnlabs.bank.place_blacksmith"), null, "Blacksmith".equals(place), Theme.INK_2, x, cy, w);
        return cy + Theme.SP_4 - Theme.SP_2;
    }

    private int sellRow(GuiGraphics g, Font font, String icon, String name, String value, boolean best, int ink, int x, int y, int w) {
        Art.icon(g, icon, x, Type.BODY.onCaps(y, 8), 8, Theme.INK_2);
        Type.BODY.draw(g, font, name, x + 8 + Theme.SP_2, y, Theme.INK);
        int rx = x + w;
        if (best) {
            String b = L.t("wynnlabs.bank.advised");
            int sw = Type.META.width(font, b) + 2 * Theme.SP_2;
            Ui.seal(g, font, b, rx - sw, y + (Type.BODY.lineHeight - Ui.SEAL_H) / 2, Theme.GOLD_EDGE);
            rx -= sw + Theme.SP_2;
        }
        if (value != null) {
            Type.BODY.drawRight(g, font, value, rx, y, ink);
        }
        return y + Type.BODY.lineHeight + Theme.SP_2;
    }

    // ---- to sell ----

    private int sellContent(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        Font font = tab.font();
        BankIndex idx = BankIndex.get();
        List<Vault.Group> market = new ArrayList<>();
        List<Vault.Group> smith = new ArrayList<>();
        for (Vault.Group j : idx.junk) {
            ("Trade Market".equals(Vault.place(j)) ? market : smith).add(j);
        }
        int cy = y;
        if (!idx.junk.isEmpty()) {
            String sum = L.t("wynnlabs.bank.sell_summary", idx.junk.size(), market.size(), smith.size());
            Type.META.draw(g, font, Type.META.fit(font, sum, w), x, cy, Theme.INK_2);
            cy += Type.META.lineHeight + Theme.SP_4;
        }
        if (idx.junk.isEmpty()) {
            Type.MUTED.draw(g, font, Type.MUTED.fit(font, L.t("wynnlabs.bank.sell_none"), w), x, cy);
            return cy + Type.MUTED.lineHeight - y;
        }
        StringBuilder list = new StringBuilder();
        cy = sellGroup(g, font, L.t("wynnlabs.bank.place_market"), market, true, x, cy, w, mx, my, list);
        cy = sellGroup(g, font, L.t("wynnlabs.bank.place_blacksmith"), smith, false, x, cy, w, mx, my, list);
        // the total and the list to copy
        cy += Theme.SP_2;
        String copy = L.t("wynnlabs.bank.copy_list");
        int cw = Ui.iconChipWidth(font, "copy", copy, false);
        if (idx.marketWorth > 0) {
            String t = Type.LABEL.fit(font, L.t("wynnlabs.bank.total", Prices.fmt(idx.marketWorth)), w - cw - Theme.SP_3);
            Type.LABEL.draw(g, font, t, x, Type.LABEL.yIn(cy, Ui.CHIP_H), Theme.PRICE);
        }
        Ui.iconChip(g, font, "copy", copy, false, x + w - cw, cy, cw, false, mx, my);
        tab.menu.hits.add(new MenuHit(x + w - cw, cy, cw, Ui.CHIP_H, null, null, list.toString().strip()));
        cy += Ui.CHIP_H;
        return cy - y;
    }

    private int sellGroup(GuiGraphics g, Font font, String title, List<Vault.Group> rows, boolean priced, int x, int y, int w,
                          int mx, int my, StringBuilder list) {
        if (rows.isEmpty()) {
            return y;
        }
        int cy = head(g, font, title, String.valueOf(rows.size()), x, y, w);
        list.append(title).append(":\n");
        int rowH = Math.max(18, Type.BODY.lineHeight);
        for (Vault.Group gr : rows) {
            Double v = priced ? Vault.value(gr) : null;
            String val = v == null ? (priced ? "—" : "") : "~" + Prices.fmt(v);
            boolean hover = mx >= x && mx < x + w && my >= cy && my < cy + rowH;
            if (hover) {
                g.fill(x - Theme.SP_1, cy - 1, x + w + Theme.SP_1, cy + rowH + 1, Theme.CARD_HOVER);
            }
            icon(g, Vault.stackOf(gr.first()), x, cy, 18);
            int ty = Type.BODY.yIn(cy, rowH);
            int vw = val.isEmpty() ? 0 : Type.NUMBER.width(font, val) + Theme.SP_3;
            String count = gr.total() > 1 ? "×" + gr.total() : "";
            int cw = count.isEmpty() ? 0 : Type.META.width(font, count) + Theme.SP_2;
            int nx = x + 18 + Theme.SP_3;
            String name = Type.BODY.fit(font, gr.name(), x + w - vw - cw - nx);
            Type.BODY.draw(g, font, name, nx, ty);
            if (!count.isEmpty()) {
                Type.META.draw(g, font, count, nx + Type.BODY.width(font, name) + Theme.SP_2, ty + 1, Theme.INK_3);
            }
            if (!val.isEmpty()) {
                Type.NUMBER.drawRight(g, font, val, x + w, ty, v == null ? Theme.INK_3 : Theme.PRICE);
            }
            act(x, cy, w, rowH, () -> tab.openStored(gr.first()));
            list.append("- ").append(gr.name()).append(gr.total() > 1 ? " x" + gr.total() : "").append(val.isEmpty() ? "" : " " + val).append('\n');
            cy += rowH + Theme.SP_2;
        }
        return cy + Theme.SP_4 - Theme.SP_2;
    }
}
