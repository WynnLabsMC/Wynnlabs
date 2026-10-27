package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.model.Tier;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The bank's filter row and its menus, from the left SP_2 apart: the kind chips, Tier ▾ (each tier
 * in its colour, with its count), the banks ▾ (which banks only) and Sort ▾ (the order the layout
 * has: the pages' order, or name, kind, tier, value); on the right the layout ▾ (banks' pages, list,
 * all side by side). The kinds and tiers fold into "Filter ▾" when they do not fit (always on a
 * small window). No menu repeats what another chip holds. The open menu is drawn by the tab's popup.
 */
final class BankMenus {

    private final BankTab tab;

    BankMenus(BankTab tab) {
        this.tab = tab;
    }

    // the menus: which is open, under which chip
    static final int MENU_FILTER = 1;
    static final int MENU_TIER = 2;
    static final int MENU_BANKS = 3;
    static final int MENU_SORT = 4;
    static final int MENU_VIEW = 5;
    int menuOpen;
    final int[][] anchors = new int[6][];
    final UiMenu.State menuState = new UiMenu.State();
    final List<Object[]> menuRows = new ArrayList<>();

    static final BankIndex.Kind[] KINDS = {null, BankIndex.Kind.WEAPON, BankIndex.Kind.ARMOUR, BankIndex.Kind.ACCESSORY,
        BankIndex.Kind.MATERIAL, BankIndex.Kind.POTION, BankIndex.Kind.QUEST};
    private static final String[] VIEW_ICONS = {"box", "list-check", "package"};
    private static final String[] VIEW_KEYS = {"wynnlabs.bank.view_pages", "wynnlabs.bank.view_list", "wynnlabs.bank.view_all"};
    private static final String[] SORT_KEYS = {"wynnlabs.bank.sort_name", "wynnlabs.bank.sort_kind", "wynnlabs.bank.sort_tier",
        "wynnlabs.bank.sort_value"};

    // ---- the filter row ----

    /** The colour a tier's dot and name take on paper (the item names' tier hues); muted for Normal and Crafted. */
    static int tierInk(Tier t) {
        int c = HomePart.tierColour(t);
        return c == Theme.INK ? Theme.INK_3 : c;
    }

    private String sortLabel() {
        BankPages p = tab.pages;
        return p.view == BankPages.VIEW_PAGES ? L.t(p.fullFirst ? "wynnlabs.bank.order_full" : "wynnlabs.bank.order_pages")
                : L.t(SORT_KEYS[p.sort]);
    }

    private String banksLabel(BankIndex idx) {
        List<BankIndex.Bank> exist = new ArrayList<>();
        List<BankIndex.Bank> on = new ArrayList<>();
        for (BankIndex.Bank b : BankIndex.Bank.values()) {
            if (idx.groups.stream().anyMatch(gr -> gr.bank() == b)) {
                exist.add(b);
                if (tab.pages.shown(b)) {
                    on.add(b);
                }
            }
        }
        return on.size() == exist.size() ? L.t("wynnlabs.bank.banks_all") : on.size() == 1 ? bankLabel(on.get(0))
                : L.n("wynnlabs.bank.banks_some", on.size());
    }

    /** The row; returns the y under it (SP_4 after). */
    int filters(GuiGraphics g, BankIndex idx, int x, int y, int w, boolean compact, int mx, int my) {
        BankPages p = tab.pages;
        String tierLabel = p.tier == null ? L.t("wynnlabs.bank.tier") : MenuPaint.nice(p.tier.name());
        String banks = banksLabel(idx);
        String sort = sortLabel();
        String view = L.t(VIEW_KEYS[p.view]);
        int viewW = Ui.iconChipWidth(tab.font(), VIEW_ICONS[p.view], view, true);
        int rest = Ui.iconChipWidth(tab.font(), "building-bank", banks, true) + Theme.SP_2
                + Ui.iconChipWidth(tab.font(), "arrows-horizontal", sort, true) + Theme.SP_4 + viewW;
        int wide = Theme.SP_3 + Ui.iconChipWidth(tab.font(), "x", tierLabel, true) + Theme.SP_2 + rest;
        for (BankIndex.Kind k : KINDS) {
            wide += Ui.chipWidth(tab.font(), kindLabel(k)) + Theme.SP_2;
        }
        boolean fold = compact || wide > w;
        int cx = x;
        if (fold) {
            String f = p.kind == null && p.tier == null ? L.t("wynnlabs.bank.filter")
                    : p.kind != null ? p.kind.label() + (p.tier != null ? " · " + tierLabel : "") : tierLabel;
            cx += menuChip(g, MENU_FILTER, "filter", f, cx, y, mx, my) + Theme.SP_2;
        } else {
            for (BankIndex.Kind k : KINDS) {
                String label = kindLabel(k);
                int cw = Ui.chipWidth(tab.font(), label);
                Ui.chip(g, tab.font(), label, cx, y, cw, p.kind == k, false, false, mx, my);
                tab.acts.add(new Object[] {new int[] {cx, y, cw, Ui.CHIP_H}, (Runnable) () -> setKind(k)});
                cx += cw + Theme.SP_2;
            }
            cx += Theme.SP_3;
            cx += tierChip(g, tierLabel, cx, y, mx, my) + Theme.SP_2;
        }
        cx += menuChip(g, MENU_BANKS, "building-bank", banks, cx, y, mx, my) + Theme.SP_2;
        menuChip(g, MENU_SORT, "arrows-horizontal", sort, cx, y, mx, my);
        menuChip(g, MENU_VIEW, VIEW_ICONS[p.view], view, x + w - viewW, y, mx, my);
        return y + Ui.CHIP_H + Theme.SP_4;
    }

    /** Tier ▾: the picked tier's dot before its name, in the menu chip's own face. */
    private int tierChip(GuiGraphics g, String label, int x, int y, int mx, int my) {
        Tier t = tab.pages.tier;
        if (t == null) {
            return menuChip(g, MENU_TIER, null, label, x, y, mx, my);
        }
        int cw = Ui.iconChipWidth(tab.font(), "x", label, true);
        boolean hover = mx >= x && mx < x + cw && my >= y && my < y + Ui.CHIP_H;
        Kit.pill(g, x, y, cw, Ui.CHIP_H, menuOpen == MENU_TIER, hover);
        int ty = Type.BODY.yIn(y, Ui.CHIP_H);
        int lw = Type.BODY.width(tab.font(), label);
        int fx = x + (cw - (8 + Theme.SP_1 + lw + Theme.SP_1 + 6)) / 2;
        int dy = Type.BODY.onCaps(ty, 6);
        g.fill(fx + 1, dy, fx + 7, dy + 6, tierInk(t));
        Type.BODY.draw(g, tab.font(), label, fx + 8 + Theme.SP_1, ty, Theme.INK);
        Art.icon(g, "chevron-down", fx + 8 + Theme.SP_1 + lw + Theme.SP_1, Type.BODY.onCaps(ty, 6), 6, Theme.INK_3);
        register(MENU_TIER, new int[] {x, y, cw, Ui.CHIP_H});
        return cw;
    }

    int menuChip(GuiGraphics g, int which, String icon, String label, int x, int y, int mx, int my) {
        int cw = UiMenu.chip(g, tab.font(), icon, label, x, y, menuOpen == which, mx, my);
        register(which, new int[] {x, y, cw, Ui.CHIP_H});
        return cw;
    }

    private void register(int which, int[] box) {
        anchors[which] = box;
        tab.acts.add(new Object[] {box, (Runnable) () -> {
            menuOpen = menuOpen == which ? 0 : which;
            menuState.reset();
        }});
    }

    static String bankLabel(BankIndex.Bank b) {
        return L.t(switch (b) {
            case ACCOUNT -> "wynnlabs.bank.group_account";
            case MINE -> "wynnlabs.bank.group_mine";
            case OTHER -> "wynnlabs.bank.group_other";
        });
    }

    static String kindLabel(BankIndex.Kind k) {
        return k == null ? L.t("wynnlabs.bank.filter_all") : k.label();
    }

    void setKind(BankIndex.Kind k) {
        tab.pages.kind = k;
        tab.jump = true;
    }

    /** The open menu's rows: ids "k:" kind, "t:" tier, "b:" bank, "o:" page order, "s:" sort, "v:" layout (-1: all). */
    List<UiMenu.Item> menuItems(BankIndex idx) {
        BankPages p = tab.pages;
        UiMenu.Items items = new UiMenu.Items();
        if (menuOpen == MENU_FILTER) {
            for (int i = 0; i < KINDS.length; i++) {
                items.add(UiMenu.Item.of("k:" + (i - 1), null, kindLabel(KINDS[i]), null, p.kind == KINDS[i]));
            }
            items.divide();
        }
        if (menuOpen == MENU_FILTER || menuOpen == MENU_TIER) {
            Map<Tier, Integer> counts = new EnumMap<>(Tier.class);
            idx.stacks.forEach(it -> {
                if (it.tier() != null) {
                    counts.merge(it.tier(), 1, Integer::sum);
                }
            });
            items.add(UiMenu.Item.of("t:-1", null, L.t("wynnlabs.bank.tier_all"), null, p.tier == null));
            for (Tier t : idx.tiers) {
                items.add(UiMenu.Item.of("t:" + t.ordinal(), null, MenuPaint.nice(t.name()), String.valueOf(counts.getOrDefault(t, 0)),
                        p.tier == t).tint(tierInk(t)));
            }
        }
        if (menuOpen == MENU_BANKS) {
            for (BankIndex.Group gr : idx.groups) {
                if (items.list.stream().noneMatch(i -> i.id().equals("b:" + gr.bank().ordinal()))) {
                    items.add(UiMenu.Item.of("b:" + gr.bank().ordinal(), null, bankLabel(gr.bank()), null, p.shown(gr.bank())));
                }
            }
        }
        if (menuOpen == MENU_SORT) {
            if (p.view == BankPages.VIEW_PAGES) {
                items.add(UiMenu.Item.of("o:0", null, L.t("wynnlabs.bank.order_pages"), null, !p.fullFirst));
                items.add(UiMenu.Item.of("o:1", null, L.t("wynnlabs.bank.order_full"), null, p.fullFirst));
            } else {
                for (int i = 0; i < SORT_KEYS.length; i++) {
                    items.add(UiMenu.Item.of("s:" + i, null, L.t(SORT_KEYS[i]), null, p.sort == i));
                }
            }
        }
        if (menuOpen == MENU_VIEW) {
            for (int i = 0; i < VIEW_KEYS.length; i++) {
                items.add(UiMenu.Item.of("v:" + i, VIEW_ICONS[i], L.t(VIEW_KEYS[i]), null, p.view == i));
            }
        }
        return items.list;
    }

    /** A menu row picked: a choice closes the menu, a bank's tick stays open. */
    void pick(String id) {
        int i = Integer.parseInt(id.substring(2));
        BankPages p = tab.pages;
        switch (id.charAt(0)) {
            case 'k' -> {
                setKind(i < 0 ? null : KINDS[i + 1]);
                menuOpen = 0;
            }
            case 't' -> {
                p.tier = i < 0 ? null : Tier.values()[i];
                tab.jump = true;
                menuOpen = 0;
            }
            case 'b' -> {
                int on = 0;
                for (boolean b : p.banks) {
                    on += b ? 1 : 0;
                }
                if (!p.banks[i] || on > 1) {
                    p.banks[i] = !p.banks[i];     // never every bank off
                }
            }
            case 's' -> {
                p.sort = i;
                Settings.setNumber("bankSort", i);
                menuOpen = 0;
            }
            case 'v' -> {
                p.view = i;
                Settings.setNumber("bankView", i);
                tab.jump = true;
                menuOpen = 0;
            }
            default -> {
                p.fullFirst = i == 1;
                menuOpen = 0;
            }
        }
    }
}
