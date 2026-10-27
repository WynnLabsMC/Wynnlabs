package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Listings;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Tier;
import io.github.anilken.wynnlabs.search.BoxMarket;
import io.github.anilken.wynnlabs.search.ItemIndex;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The box window's two bars and its menus. The title row: the thing in an HD slot, its name in its
 * rarity's colour ("+N build" when it adds to yours), its kind and level; on the right when it was
 * read, and the magnifier (one item), Refresh and close as icon buttons. The filter row: Hepsi /
 * Tanımlı / Tanımsız, "Fits your build N", the sort as a menu; a small window has
 * the picked item and the kinds as menus too, and İlanlar / İstatistik for one item. Every menu is
 * a {@link UiMenu}, drawn over the page with the real mouse.
 */
final class BoxBar {

    private static final int NONE = 0;
    private static final int SORT = 1;
    private static final int ITEM = 2;
    private static final int KIND = 3;

    private final BoxScreen s;
    private int menu = NONE;
    private int[] menuAt;
    private final UiMenu.State menuState = new UiMenu.State();
    private final List<Runnable> menuHits = new ArrayList<>();
    private final List<int[]> menuBoxes = new ArrayList<>();

    BoxBar(BoxScreen s) {
        this.s = s;
    }

    /** The magnifier into Search for {@code name} (its tooltip "Open in Search"); this window stays under it. */
    void searchButton(GuiGraphics g, int x, int y, String name, int mx, int my) {
        if (Ui.iconButton(g, "search", x, y, false, mx, my)) {
            s.tip(List.of(L.c("wynnlabs.box.search_tip")));
        }
        s.hit(x, y, Ui.CHIP_H + 2, Ui.CHIP_H, () -> openSearch(name));
        s.searchRect[0] = x;
        s.searchRect[1] = y;
        s.searchRect[2] = Ui.CHIP_H + 2;
        s.searchRect[3] = Ui.CHIP_H;
    }

    /** The menu on Search with the item's card picked; Esc there comes back to this window. Nothing is sent. */
    void openSearch(String name) {
        net.minecraft.client.Minecraft.getInstance().setScreen(RoadmapScreen.search(name, s));
    }

    // ---- the title row ----

    /** Draws the title row and its hairline; returns where the page starts. */
    int title(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int h = Ui.CHIP_H;
        // the right end: Refresh, the magnifier (one item), when it was read (close is the frame's seal)
        int bx = x + w - (Ui.CHIP_H + 2);
        Listings.Refresh r = s.refreshState();
        boolean running = r != null && r.running();
        boolean can = s.canRefresh();
        if (can) {
            if (Ui.iconButton(g, "refresh", bx, y, false, mx, my)) {
                s.tip(List.of(L.c("wynnlabs.box.refresh")));
            }
            s.hit(bx, y, Ui.CHIP_H + 2, h, s::refresh);
        } else {
            Kit.pill(g, bx, y, Ui.CHIP_H + 2, h, false, false);
            Art.icon(g, "refresh", bx + Ui.iconIn(Ui.CHIP_H + 2, 8), y + Ui.iconIn(h, 8), 8, Theme.LINE);
        }
        int right = bx;
        if (s.single != null) {
            right -= Ui.CHIP_H + 2 + Theme.SP_2;
            searchButton(g, right, y, s.single.item().name(), mx, my);
        }
        String status = running ? L.t("wynnlabs.box.refreshing", r.done(), r.total()) : updated(r);
        int sw = status.isEmpty() ? 0 : Type.META.width(s.font(), status);
        if (sw > 0) {
            Type.META.drawRight(g, s.font(), status, right - Theme.SP_3, Type.META.yIn(y, h), running ? Theme.WARN : Theme.INK_3);
            right -= sw + Theme.SP_3;
        }
        // the left: the slot, the name, its seal, the kind and level
        int sy = y + (h - 18) / 2;
        Kit.slot(g, x, sy, 18, 0);
        ItemStack icon = s.single != null ? s.singleIcon
                : s.left.icon(new BoxMarket.Item(s.boxName(), s.box.slot(), s.box.tier(), s.box.high(), null, 0, false));
        if (icon != null) {
            g.renderItem(icon, x + 1, sy + 1);
        }
        int nx = x + 18 + Theme.SP_3;
        Tier tier = s.single != null ? s.single.item().tier() : s.box.tier();
        Type role = s.compact ? Type.LABEL : Type.HEADING;
        int room = right - Theme.SP_3 - nx;
        String name = role.fit(s.font(), s.boxName(), room);
        role.draw(g, s.font(), name, nx, role.yIn(y, h), HomePart.tierColour(tier));
        nx += role.width(s.font(), name) + Theme.SP_3;
        int stars = s.single != null ? BoxPlainRows.stars(s.single.item().name()) : 0;
        for (int i = 0; i < stars; i++) {
            Art.icon(g, "star-filled", nx, y + Ui.iconIn(h, 7), 7, Theme.GOLD_DEEP);
            nx += 8;
        }
        nx += stars > 0 ? Theme.SP_2 : 0;
        int gain = s.single != null ? s.gainOf(s.single.item().name()) : 0;
        if (gain > 0) {
            boolean word = right - nx > 160;
            int gw = BoxChips.buildSeal(g, s.font(), gain, word, nx, y + Ui.iconIn(h, Ui.SEAL_H));
            if (BoxChips.in(mx, my, nx, y, gw, h)) {
                s.tip(List.of(L.c("wynnlabs.box.build_tip", gain).withStyle(ChatFormatting.GRAY)));
            }
            nx += gw + Theme.SP_3;
        }
        Type.META.draw(g, s.font(), Type.META.fit(s.font(), meta(), right - Theme.SP_3 - nx), nx, Type.META.yIn(y, h), Theme.INK_3);
        int hy = y + h + Theme.SP_3;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_4;
    }

    /** "Unique · Lv 66-70 · Identify 36-38 E", or the one item's tier, type and level. */
    private String meta() {
        List<String> parts = new ArrayList<>();
        if (s.box != null) {
            parts.add(MenuPaint.nice(s.box.tier().name()));
            parts.add("Lv " + s.box.low() + "–" + s.box.high());
            Klass k = s.items.isEmpty() ? null : s.items.get(0).klass();
            if (k != null && k != Klass.NONE) {
                parts.add(MenuPaint.nice(k.name()));
            }
            int low = s.verdict != null ? s.verdict.costLow() : s.box.tier().identificationCost(s.box.low());
            int high = s.verdict != null ? s.verdict.costHigh() : s.box.tier().identificationCost(s.box.high());
            parts.add(L.t("wynnlabs.box.identify_cost", low == high ? low + " E" : low + "–" + high + " E"));
            return String.join(" · ", parts);
        }
        BoxMarket.Item it = s.single.item();
        if (it.tier() != Tier.NORMAL) {
            parts.add(MenuPaint.nice(it.tier().name()));
        }
        if (it.slot() != io.github.anilken.wynnlabs.model.Slot.UNKNOWN) {
            parts.add(MenuPaint.nice(it.slot().name()));
        }
        if (it.level() > 0) {
            parts.add("Lv " + it.level());
        }
        if (it.klass() != null && it.klass() != Klass.NONE) {
            parts.add(MenuPaint.nice(it.klass().name()));
        }
        return String.join(" · ", parts);
    }

    /** "2 min ago": the last refresh, else the newest answer among the box's items. */
    private String updated(Listings.Refresh r) {
        long t = r != null && !r.running() ? r.finishedAt() : 0;
        if (t == 0) {
            for (BoxMarket.Item it : s.items) {
                t = Math.max(t, Listings.fetchedAt(it.name()));
            }
        }
        return t <= 0 ? "" : MenuPaint.ago(t);
    }

    // ---- the filter row ----

    /** Draws the filter row (nothing for an item without rolls on a large window); returns where the panes start. */
    int filters(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int h = Ui.CHIP_H;
        int cx = x;
        if (s.single != null && s.compact) {
            String[] tabs = {L.t("wynnlabs.box.tab_listings"), L.t("wynnlabs.box.tab_stats")};
            String[] icons = {"tag", "list-check"};
            for (int i = 0; i < 2; i++) {
                int cw = Ui.iconChipWidth(s.font(), icons[i], tabs[i], false);
                Ui.iconChip(g, s.font(), icons[i], tabs[i], false, cx, y, cw, s.tab == i, mx, my);
                int t = i;
                s.hit(cx, y, cw, h, () -> s.tab = t);
                cx += cw + Theme.SP_2;
            }
        } else if (!s.rolled()) {
            return y;
        }
        if (s.single == null && s.compact) {
            String picked = s.selected == null ? L.t("wynnlabs.box.items_title") : s.selected;
            cx += menuChip(g, null, Type.BODY.fit(s.font(), picked, 110), cx, y, ITEM, mx, my) + Theme.SP_2;
            cx += menuChip(g, "filter", L.t("wynnlabs.box.filter." + s.filter.name().toLowerCase(Locale.ROOT)), cx, y, KIND, mx, my)
                    + Theme.SP_2;
        } else if (s.single == null || !s.compact) {
            for (BoxMarket.Filter f : BoxMarket.Filter.values()) {
                // what the chip filters is the listings on the right: their count is in its head, not here
                String label = L.t("wynnlabs.box.filter." + f.name().toLowerCase(Locale.ROOT));
                int cw = Ui.iconChipWidth(s.font(), null, label, false);
                Ui.iconChip(g, s.font(), null, label, false, cx, y, cw, s.filter == f, mx, my);
                s.hit(cx, y, cw, h, () -> s.filter = f);
                cx += cw + Theme.SP_2;
            }
        }
        if (s.box != null) {
            cx += Theme.SP_2;
            int n = s.fitting();
            if (n > 0) {
                String label = s.compact ? String.valueOf(n) : L.t("wynnlabs.box.filter_build", n);
                int cw = Ui.iconChipWidth(s.font(), "target", label, false);
                Ui.iconChip(g, s.font(), "target", label, false, cx, y, cw, s.buildOnly, mx, my);
                s.hit(cx, y, cw, h, () -> s.buildOnly = !s.buildOnly);
            } else {
                // nothing fits: the chip stays, dimmed, and says why
                String label = L.t(s.compact ? "wynnlabs.box.filter_build_none_short" : "wynnlabs.box.filter_build_none");
                int cw = Ui.iconChipWidth(s.font(), "target", label, false);
                Kit.pill(g, cx, y, cw, h, false, false);
                int tx = cx + (cw - 8 - Theme.SP_1 - Type.BODY.width(s.font(), label)) / 2;
                Art.icon(g, "target", tx, Type.BODY.onCaps(Type.BODY.yIn(y, h), 8), 8, Theme.LINE);
                Type.BODY.draw(g, s.font(), label, tx + 8 + Theme.SP_1, Type.BODY.yIn(y, h), Theme.INK_3);
                if (BoxChips.in(mx, my, cx, y, cw, h)) {
                    s.tip(List.of(L.c("wynnlabs.box.filter_build_none_tip").withStyle(ChatFormatting.GRAY)));
                }
            }
        }
        if (s.rolled() && (s.single == null || !s.compact || s.tab == 0)) {
            String sort = L.t(s.sort == BoxMarket.Sort.ROLL ? "wynnlabs.box.sort_roll" : "wynnlabs.box.sort_price");
            int sw = Ui.iconChipWidth(s.font(), "arrow-down", sort, true);
            menuChip(g, "arrow-down", sort, x + w - sw, y, SORT, mx, my);
        }
        return y + h + Theme.SP_4;
    }

    private int menuChip(GuiGraphics g, String icon, String label, int x, int y, int id, int mx, int my) {
        int w = UiMenu.chip(g, s.font(), icon, label, x, y, menu == id, mx, my);
        s.hit(x, y, w, Ui.CHIP_H, () -> {
            menu = menu == id ? NONE : id;
            menuAt = new int[] {x, y, w, Ui.CHIP_H};
            menuState.reset();
        });
        return w;
    }

    // ---- the menus ----

    boolean menuOpen() {
        return menu != NONE;
    }

    void closeMenu() {
        menu = NONE;
    }

    private List<UiMenu.Item> items() {
        List<UiMenu.Item> out = new ArrayList<>();
        switch (menu) {
            case SORT -> {
                out.add(UiMenu.Item.of("roll", null, L.t("wynnlabs.box.sort_roll"), null, s.sort == BoxMarket.Sort.ROLL));
                out.add(UiMenu.Item.of("price", null, L.t("wynnlabs.box.sort_price"), null, s.sort == BoxMarket.Sort.PRICE));
            }
            case KIND -> {
                for (BoxMarket.Filter f : BoxMarket.Filter.values()) {
                    out.add(UiMenu.Item.of(f.name(), null, L.t("wynnlabs.box.filter." + f.name().toLowerCase(Locale.ROOT)),
                            null, s.filter == f));
                }
            }
            case ITEM -> {
                for (BoxMarket.Row r : s.rows) {
                    int gain = s.gainOf(r.name());
                    String meta = r.lowest() != null ? io.github.anilken.wynnlabs.market.Prices.fmt(r.lowest()) : null;
                    out.add(UiMenu.Item.of(r.name(), null, r.name(), meta, r.name().equals(s.selected))
                            .sub("Lv " + r.item().level() + (gain > 0 ? " · " + L.t("wynnlabs.box.build_seal", gain) : ""))
                            .tint(HomePart.tierColour(r.item().tier())));
                }
            }
            default -> {
            }
        }
        return out;
    }

    void drawMenu(GuiGraphics g, int mx, int my) {
        if (menuAt == null) {
            return;
        }
        menuHits.clear();
        menuBoxes.clear();
        List<UiMenu.Item> list = items();
        UiMenu.draw(g, s.font(), UiMenu.Spec.under(menuAt, s.width, s.height), menuState, list, mx, my, row -> {
            menuBoxes.add(new int[] {row.x(), row.y(), row.w(), row.h()});
            String id = row.item().id();
            menuHits.add(() -> pick(id));
        });
    }

    private void pick(String id) {
        switch (menu) {
            case SORT -> s.sort = "roll".equals(id) ? BoxMarket.Sort.ROLL : BoxMarket.Sort.PRICE;
            case KIND -> s.filter = BoxMarket.Filter.valueOf(id);
            case ITEM -> {
                s.selected = id;
                s.right.reset();
            }
            default -> {
            }
        }
        menu = NONE;
    }

    /** A click while a menu is open: a row picks it, the bar scrolls it, anywhere else only closes it. */
    void menuClick(double mx, double my) {
        if (menuState.press(mx, my)) {
            return;
        }
        for (int i = 0; i < menuBoxes.size(); i++) {
            int[] b = menuBoxes.get(i);
            if (BoxChips.in(mx, my, b[0], b[1], b[2], b[3])) {
                menuHits.get(i).run();
                return;
            }
        }
        menu = NONE;
    }

    boolean menuDrag(double my) {
        return menuState.drag(my);
    }

    void menuRelease() {
        menuState.release();
    }

    void menuWheel(double v) {
        menuState.wheel(v);
    }

    void menuKey(int key) {
        UiMenu.Item it = menuState.key(key);
        if (it != null) {
            pick(it.id());
        }
    }

    /** The box's possible items, as Wynntils lists them, each once. */
    static List<BoxMarket.Item> items(io.github.anilken.wynnlabs.model.ItemView.Box box) {
        List<BoxMarket.Item> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (io.github.anilken.wynnlabs.model.ItemData d : io.github.anilken.wynnlabs.game.GameState.current().possibleGears(box)) {
            if (seen.add(d.name())) {
                Klass k = d.requirements().klass().orElse(d.slot().klass());
                out.add(new BoxMarket.Item(d.name(), d.slot(), d.tier(), d.level(), k, box.tier().identificationCost(d.level()),
                        io.github.anilken.wynnlabs.market.Shopping.tradable(d)));
            }
        }
        return out;
    }

}
