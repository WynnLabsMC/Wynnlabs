package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.core.RuleEditor;
import io.github.anilken.wynnlabs.core.UpdateCheck;
import io.github.anilken.wynnlabs.core.Words;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * The settings' two pages that are not rows: About (the update card, the links, the version and
 * Wynntils) and the Wynnventory key under its row (where prices come from, the key box with Save
 * and Remove). Clicks come back through {@link SettingsTab}.
 */
final class SettingsPages extends MenuPaint {

    private final SettingsTab tab;

    SettingsPages(RoadmapScreen menu, SettingsTab tab) {
        super(menu);
        this.tab = tab;
    }

    static final String REPO = "https://github.com/anilken/wynnlabs";
    /** The About page's links: the source, a new issue (its template chooser), the licence. */
    private static final String[][] ABOUT_LINKS = {
        {"wynnlabs.about.source", REPO},
        {"wynnlabs.about.report", REPO + "/issues/new/choose"},
        {"wynnlabs.about.licence", REPO + "/blob/main/LICENSE"}};

    // ---- settings: About ----

    /** The update card, made on first use. */
    private AboutUpdateCard updateCard;

    private AboutUpdateCard updateCard() {
        if (updateCard == null) {
            updateCard = new AboutUpdateCard(menu);
        }
        return updateCard;
    }

    /**
     * About: whether this is the newest Wynn Labs and what to do about it ({@link AboutUpdateCard}),
     * the links (source, a bug report, the licence), the mod's version and loader, and which
     * Wynntils features work.
     */
    void drawAboutPage(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        UpdateCheck.State s = UpdateCheck.state();
        String mine = UpdateScreen.vName(s.own().isEmpty() ? UpdateCheck.version.get() : s.own());
        String loader = UpdateScreen.loaderLabel();
        int cardH = updateCard().draw(g, x, y, w, mx, my);
        int cy = y + cardH + Theme.SP_5;

        // the links, each opened in the browser only after the game's "open this link?" prompt
        Type.LABEL.draw(g, font(), L.t("wynnlabs.about.links"), x, cy);
        Kit.hair(g, x, cy + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        cy += Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
        int lx = x;
        for (int i = 0; i < ABOUT_LINKS.length; i++) {
            String label = L.t(ABOUT_LINKS[i][0]);
            int bw = Ui.iconChipWidth(font(), "arrow-up-right", label, false);
            if (lx > x && lx + bw > x + w) {
                lx = x;
                cy += Ui.CHIP_H + Theme.SP_2;
            }
            boolean bh = Ui.iconChip(g, font(), "arrow-up-right", label, false, lx, cy, bw, false, mx, my);
            menu.toggles.add(new Object[] {new int[] {lx, cy, bw, Ui.CHIP_H}, "#link:" + i});
            if (bh) {
                menu.hits.add(new MenuHit(lx, cy, bw, Ui.CHIP_H, null, List.of(L.c("wynnlabs.about.link_tip", ABOUT_LINKS[i][1])), null));
            }
            lx += bw + Theme.SP_2;
        }
        cy += Ui.CHIP_H + Theme.SP_4;
        Type.MUTED.draw(g, font(), Type.MUTED.fit(font(), L.t("wynnlabs.about.mod_line", mine, loader), w), x, cy);
        cy += Type.MUTED.lineHeight + Theme.SP_4;
        // which Wynntils features work, when there is room for it
        if (y + h - cy >= 30) {
            drawAbout(g, x, cy, w);
        }
    }

    /** The About page's buttons: the update card's, the setting, a link. */
    void aboutClick(String id) {
        if (id.equals("#upd:setting")) {
            // Prices and data, the update check picked
            for (int c = 0; c < SettingsRows.SETTING_CATS.length; c++) {
                if (SettingsRows.SETTING_CATS[c].equals("data")) {
                    tab.settingsCat = c;
                    tab.clearView();
                    List<SettingsRows.SetRow> rows = SettingsRows.settingRows("data");
                    for (int i = 0; i < rows.size(); i++) {
                        if (rows.get(i).id().equals("updateCheck")) {
                            tab.pickCat = c;
                            tab.pickRow = i;
                        }
                    }
                }
            }
            return;
        }
        if (updateCard().click(id) || !id.startsWith("#link:")) {
            return;
        }
        int i = Integer.parseInt(id.substring("#link:".length()));
        if (i >= 0 && i < ABOUT_LINKS.length) {
            updateCard().openLink(ABOUT_LINKS[i][1]);
        }
    }

    /** About: the Wynntils version and which of its features work with it, on a soft card. */
    private void drawAbout(GuiGraphics g, int x, int y, int w) {
        var down = Wyn.down();
        String title = L.t("wynnlabs.wyn.about", Wyn.version());
        String line = L.t(down.isEmpty() ? "wynnlabs.wyn.all_ok" : "wynnlabs.wyn.some_off");
        int pad = Theme.SP_3;
        int h = pad + Type.LABEL.lineHeight + Type.BODY.lineHeight * (1 + down.size()) + pad;
        Kit.softCard(g, x, y, w, h);
        Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), title, w - 2 * pad), x + pad, y + pad);
        int ly = y + pad + Type.LABEL.lineHeight;
        int lc = down.isEmpty() ? Theme.GOOD : Theme.BAD;
        Art.icon(g, down.isEmpty() ? "check" : "x", x + pad, Type.BODY.onCaps(ly, 8), 8, lc);
        Type.BODY.draw(g, font(), Type.BODY.fit(font(), line, w - 2 * pad - 10), x + pad + 8 + Theme.SP_1, ly, lc);
        for (var e : down.entrySet()) {
            ly += Type.BODY.lineHeight;
            Type.META.draw(g, font(), Type.META.fit(font(), Wyn.name(e.getKey()) + " — " + e.getValue(), w - 2 * pad - 10),
                    x + pad + 10, ly + 1, Theme.INK_2);
        }
    }

    // ---- the Wynnventory key ----

    /** The height {@link #drawKeyDetail} takes under the key's row. */
    static int keyDetailHeight() {
        return SettingsRows.keyBoxNeeded() ? 2 * Type.META.lineHeight + Theme.SP_2 + Ui.SMALL_H : 3 * Type.META.lineHeight;
    }

    /**
     * Under the key's row: where the prices and bank notes come from, then the key (built in, or
     * the box to paste your own with Save and Remove).
     */
    void drawKeyDetail(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Words problem = Prices.problem;
        String pr = problem == null ? null : problem.text();
        String an = Vault.annotateProblem;
        String prices = pr == null ? L.t("wynnlabs.settings.prices_ok") : "✖ " + pr;
        String bank = an == null ? L.t("wynnlabs.settings.bank_ok") : L.t("wynnlabs.settings.bank_bad", an);
        Type.META.draw(g, font(), Type.META.fit(font(), prices, w), x, y, pr == null ? Theme.GOOD : Theme.BAD);
        Type.META.draw(g, font(), Type.META.fit(font(), bank, w), x, y + Type.META.lineHeight, an == null ? Theme.GOOD : Theme.BAD);
        int ky = y + 2 * Type.META.lineHeight;
        tab.keyBoxShown = SettingsRows.keyBoxNeeded();
        if (!tab.keyBoxShown) {
            // the key built into the mod works: nothing to ask for
            Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.settings.key") + L.t("wynnlabs.settings.key_builtin"), w),
                    x, ky, Theme.INK_2);
            return;
        }
        int by = ky + Theme.SP_2;
        String masked = Prices.maskedKey();
        String save = L.t("wynnlabs.settings.save");
        String remove = L.t("wynnlabs.settings.remove");
        int sw = Ui.chipWidth(font(), save);
        int rw = masked == null ? 0 : Ui.chipWidth(font(), remove) + Theme.SP_2;
        int kw = Math.max(60, Math.min(220, w - sw - rw - Theme.SP_2));
        UiInput.input(g, font(), tab.keyBox, L.t("wynnlabs.settings.key_box"), x, by - 1, kw, true, false, mx, my);
        int sx = x + kw + Theme.SP_2;
        Ui.small(g, font(), save, sx, by, sw, mx, my);
        menu.toggles.add(new Object[] {new int[] {sx, by, sw, Ui.SMALL_H}, "#keysave"});
        if (masked != null) {
            // removing the key: a thin pill in the error colour
            int dx = sx + sw + Theme.SP_2;
            int dw = rw - Theme.SP_2;
            boolean hover = mx >= dx && mx < dx + dw && my >= by && my < by + Ui.SMALL_H;
            Kit.pill(g, dx, by, dw, Ui.SMALL_H, false, hover);
            Type.BODY.draw(g, font(), remove, dx + (dw - Type.BODY.width(font(), remove)) / 2, Type.BODY.yIn(by, Ui.SMALL_H), Theme.BAD);
            menu.toggles.add(new Object[] {new int[] {dx, by, dw, Ui.SMALL_H}, "#keydel"});
            String state = Prices.keyInvalid ? L.t("wynnlabs.settings.key_invalid", masked) : masked;
            int tx = dx + dw + Theme.SP_3;
            if (x + w - tx > 30) {
                Type.META.draw(g, font(), Type.META.fit(font(), state, x + w - tx), tx, Type.META.yIn(by, Ui.SMALL_H),
                        Prices.keyInvalid ? Theme.BAD : Theme.INK_2);
            }
        }
    }

    /** The search box ({@link UiInput}). */
    void searchField(GuiGraphics g, int x, int y, int w, int mx, int my) {
        tab.searchHit = UiInput.search(g, font(), tab.searchBox, L.t("wynnlabs.settings.search_hint"), true, x, y, w, mx, my);
    }

    /** The rules' reset as an error notice at the page's foot (ending at {@code bottom}); returns its height. */
    int rulesNotice(GuiGraphics g, int x, int bottom, int w, int mx, int my) {
        boolean armed = System.currentTimeMillis() - tab.resetArmedAt < SettingsTab.RESET_WINDOW_MS;
        Ui.Action act = new Ui.Action("refresh", L.t(armed ? "wynnlabs.rules.reset_confirm" : "wynnlabs.rules.reset"));
        String text = L.t("wynnlabs.rules.per_character") + ". " + L.t("wynnlabs.rules.reset_notice");
        int lines = Math.min(2, Type.BODY.wrap(font(), text, Ui.noticeTextRoom(font(), act, w)).size());
        int nh = Ui.noticeHeight(lines);
        int[][] hit = new int[1][];
        Ui.notice(g, font(), Ui.Level.ERROR, List.of(text), act, x, bottom - nh, w, nh, mx, my, hit);
        if (hit[0] != null) {
            menu.toggles.add(new Object[] {hit[0], "#rulesreset"});
        }
        return nh;
    }

    /** A row picked, flipped or put back to its default, by its "#…:cat:index" ref. */
    void settingsAction(String id) {
        int[] ref = ref(id);
        if (ref[0] < 0 || ref[0] >= SettingsRows.SETTING_CATS.length) {
            return;
        }
        List<SettingsRows.SetRow> rows = SettingsRows.settingRows(SettingsRows.SETTING_CATS[ref[0]]);
        int i = ref[1];
        if (i < 0 || i >= rows.size() || rows.get(i).head()) {
            return;
        }
        SettingsRows.SetRow r = rows.get(i);
        tab.pickCat = ref[0];
        tab.pickRow = i;
        if (id.startsWith("#ssel:")) {
            return;
        }
        if (id.startsWith("#stog:")) {
            switch (r.kind()) {
                case "switch" -> {
                    Settings.toggle(r.id());
                    if ("webFont".equals(r.id())) {
                        menu.reinit();           // init() again: the screen and its boxes take the new font now
                    }
                }
                case "advice" -> adviceHere(!Rules.adviceHere());
                default -> RuleEditor.step(r.r(), 1);
            }
            return;
        }
        // #sreset: this one setting back to its default
        switch (r.kind()) {
            case "switch" -> {
                Settings.reset(r.id());
                if ("webFont".equals(r.id())) {
                    menu.reinit();
                }
            }
            case "step" -> Settings.resetNumber(r.id());
            case "advice" -> adviceHere(true);
            case "scale" -> Settings.resetNumber("menuScale");
            case "lang" -> {
                Settings.setNumber("langOverride", 0);
                menu.rebuildMap();
            }
            case "fill" -> Settings.resetNumber("menuFill");
            case "rule" -> RuleEditor.reset(r.r());
            default -> {
                return;
            }
        }
        menu.flash(L.t("wynnlabs.settings.reset_done", SettingsRows.rowTitle(r)));
    }

    /** Advice on this character on or off; the road map follows. */
    private void adviceHere(boolean on) {
        Rules.setAdviceHere(on);
        menu.steps = menu.rebuild();
        menu.refreshAsked = Rules.adviceHere();
    }

    /** A row ref "#…:cat:index" as {cat, index}. */
    static int[] ref(String id) {
        String[] p = id.split(":");
        return new int[] {Integer.parseInt(p[1]), Integer.parseInt(p[2])};
    }
}
