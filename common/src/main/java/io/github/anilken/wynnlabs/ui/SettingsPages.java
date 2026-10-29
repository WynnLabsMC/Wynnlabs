package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.MarketSlots;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.RuleEditor;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.UpdateCheck;
import io.github.anilken.wynnlabs.core.Words;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings' parts that are not rows: About (behind the "?": the update card, the keys, the
 * links, the version and Wynntils), the Wynnventory key under live prices, the per-character
 * notice with its reset, and what a click on a row does. Clicks come back through {@link SettingsTab}.
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

    // ---- About ----

    /** The update card, made on first use. */
    private AboutUpdateCard updateCard;

    private AboutUpdateCard updateCard() {
        if (updateCard == null) {
            updateCard = new AboutUpdateCard(menu);
        }
        return updateCard;
    }

    /**
     * About (the "?" and the rail's Help): whether this is the newest Wynn Labs and what to do
     * about it ({@link AboutUpdateCard}); with a newer release its first changes and "All changes ›";
     * the keys as the player has bound them; the links (source, a bug report, the licence); one META
     * line with the mod's version, loader and Wynntils, and a warning only when a Wynntils feature is off.
     */
    void drawAboutPage(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        UpdateCheck.State s = UpdateCheck.state();
        String mine = UpdateScreen.vName(s.own().isEmpty() ? UpdateCheck.version.get() : s.own());
        String loader = UpdateScreen.loaderLabel();
        int bottom = y + h;
        int cy = y + updateCard().draw(g, x, y, w, mx, my) + Theme.SP_5;
        // the changes only when the keys still fit under them (else the card's "Changes" opens them)
        int sectionHead = Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
        int changesH = sectionHead + 3 * (Type.BODY.lineHeight + Theme.SP_1) + Type.LINK.lineHeight + Theme.SP_5;
        if (s.status() == UpdateCheck.Status.NEWER && cy + changesH + AboutKeys.height(font(), w) <= bottom) {
            cy = changes(g, s, x, cy, w, mx, my) + Theme.SP_5;
        }
        if (cy + sectionHead + Ui.CHIP_H > bottom) {
            return;
        }
        cy = AboutKeys.draw(g, font(), x, cy, w, bottom) + Theme.SP_5;
        // the links, each opened in the browser only after the game's "open this link?" prompt
        if (cy + sectionHead + Ui.CHIP_H > bottom) {
            return;
        }
        cy = head(g, L.t("wynnlabs.about.links"), x, cy, w);
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
        if (cy + Type.META.lineHeight > bottom) {
            return;
        }
        // the version and Wynntils in one line; a warning only when one of its features is off
        var down = Wyn.down();
        if (down.isEmpty()) {
            String line = L.t("wynnlabs.about.mod_line", mine, loader) + " · " + L.t("wynnlabs.wyn.about", Wyn.version()) + ": "
                    + L.t("wynnlabs.wyn.all_ok");
            Art.icon(g, "check", x, Type.META.onCaps(cy, 8), 8, Theme.GOOD);
            Type.META.draw(g, font(), Type.META.fit(font(), line, w - 11), x + 11, cy, Theme.INK_3);
            return;
        }
        Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.about.mod_line", mine, loader), w), x, cy, Theme.INK_3);
        cy += Type.META.lineHeight + Theme.SP_3;
        List<String> lines = new ArrayList<>();
        lines.add(L.t("wynnlabs.wyn.about", Wyn.version()) + ": " + L.t("wynnlabs.wyn.some_off"));
        for (var e : down.entrySet()) {
            lines.add(Wyn.name(e.getKey()) + " — " + e.getValue());
        }
        if (y + h - cy > Ui.noticeHeight(1)) {
            Ui.notice(g, font(), Ui.Level.WARNING, lines, null, x, cy, w, y + h - cy, mx, my, new int[1][]);
        }
    }

    /** A section's LABEL title over a hairline; returns where its content starts. */
    private int head(GuiGraphics g, String title, int x, int y, int w) {
        Type.LABEL.draw(g, font(), title, x, y);
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        return y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
    }

    /** The release's first changes as bullets (at most three) and "All changes ›"; returns the bottom. */
    private int changes(GuiGraphics g, UpdateCheck.State s, int x, int y, int w, int mx, int my) {
        if (changesFor == null || !changesFor.equals(s.latest())) {
            changesFor = s.latest();
            changes = new ArrayList<>();
            for (io.github.anilken.wynnlabs.core.ReleaseNotes.Line l : io.github.anilken.wynnlabs.core.ReleaseNotes.lines(s.notes())) {
                if (l.bullet() && changes.size() < 3) {
                    changes.add(l.text());
                }
            }
        }
        if (changes.isEmpty()) {
            return y - Theme.SP_5;
        }
        int cy = head(g, L.t("wynnlabs.about.in_release", s.latest()), x, y, w);
        for (String c : changes) {
            Art.icon(g, "circle-dot", x, Type.BODY.onCaps(cy, 6), 6, Theme.INK_3);
            Type.BODY.draw(g, font(), Type.BODY.fit(font(), c, w - 10), x + 10, cy, Theme.INK_2);
            cy += Type.BODY.lineHeight + Theme.SP_1;
        }
        String all = L.t("wynnlabs.about.all_changes");
        int aw = Ui.iconChipWidth(font(), "script", all, false);
        cy += Theme.SP_2;
        Ui.iconChip(g, font(), "script", all, false, x, cy, aw, false, mx, my);
        menu.toggles.add(new Object[] {new int[] {x, cy, aw, Ui.CHIP_H}, "#upd:notes"});
        return cy + Ui.CHIP_H;
    }

    private String changesFor;
    private List<String> changes = List.of();

    /** The About page's buttons: the update card's, the setting, a link. */
    void aboutClick(String id) {
        if (id.equals("#upd:setting")) {
            tab.showSetting("updates");             // Online & updates, "New versions" picked
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

    // ---- the Wynnventory key ----

    /** The height {@link #drawKeyDetail} takes under the key's row. */
    static int keyDetailHeight() {
        return SettingsText.keyBoxNeeded() ? 2 * Type.META.lineHeight + Theme.SP_2 + Ui.SMALL_H : 3 * Type.META.lineHeight;
    }

    /**
     * Under the live prices' note, once "Key…" is open: where the prices and bank notes come from,
     * then the key (built in, or the box to paste your own with Save and Remove).
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
        tab.keyBoxShown = SettingsText.keyBoxNeeded();
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

    // ---- this character's rules ----

    private Ui.Action resetAction() {
        boolean armed = System.currentTimeMillis() - tab.resetArmedAt < SettingsTab.RESET_WINDOW_MS;
        return new Ui.Action("refresh", L.t(armed ? "wynnlabs.rules.reset_confirm" : "wynnlabs.rules.reset"));
    }

    private String noticeText() {
        return L.t("wynnlabs.rules.for_character", SettingsText.character());
    }

    /** The notice's height at width {@code w}: its text on at most two lines. */
    int rulesNoticeHeight(int w) {
        int lines = Math.min(2, Type.BODY.wrap(font(), noticeText(), Ui.noticeTextRoom(font(), resetAction(), w)).size());
        return Ui.noticeHeight(lines);
    }

    /** "These rules are for Shaman, lv. 44" as a calm info notice, with the two-click reset at its end. */
    void rulesNotice(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int nh = rulesNoticeHeight(w);
        int[][] hit = new int[1][];
        Ui.notice(g, font(), Ui.Level.INFO, List.of(noticeText()), resetAction(), x, y, w, nh, mx, my, hit);
        if (hit[0] != null) {
            menu.toggles.add(new Object[] {hit[0], "#rulesreset"});
        }
    }

    // ---- a click on a row ----

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
        if (id.startsWith("#ssel:")) {
            boolean again = tab.pickCat == ref[0] && tab.pickRow == i;
            tab.pickCat = ref[0];
            tab.pickRow = again ? -1 : i;           // a second click closes what it does
            return;
        }
        tab.pickCat = ref[0];
        tab.pickRow = i;
        if (id.startsWith("#stog:")) {
            switch (r.kind()) {
                case "switch" -> {
                    Settings.toggle(r.id());
                    if ("webFont".equals(r.id())) {
                        menu.reinit();           // init() again: the screen and its boxes take the new font now
                    }
                }
                case "advice" -> adviceHere(!Rules.adviceHere());
                default -> {
                }
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
            case "multi" -> {
                for (String k : SettingsRows.MULTI.get(r.id()).keys()) {
                    Settings.reset(k);
                }
            }
            case "choice" -> Settings.resetNumber(r.id());
            case "step" -> Settings.resetNumber(r.id());
            case "advice" -> adviceHere(true);
            case "lang" -> {
                Settings.setNumber("langOverride", 0);
                menu.rebuildMap();
            }
            case "fill" -> Settings.resetNumber("menuFill");
            case "rule" -> RuleEditor.reset(r.r());
            case "rulemulti" -> {
                for (String k : SettingsRows.MULTI.get(r.id()).keys()) {
                    RuleEditor.reset(RuleEditor.byId(k));
                }
            }
            case "preset" -> RuleEditor.applyPreset(RuleEditor.NORMAL);
            case "slots" -> {
                MarketSlots.setLimited(true);
                RuleEditor.reset(RuleEditor.byId("market_slots"));
            }
            default -> {
                return;
            }
        }
        menu.flash(L.t("wynnlabs.settings.reset_done", SettingsText.rowTitle(r)));
    }

    /** A chip of a row: "#opt:kind:id:index". */
    void optionClicked(String id) {
        String[] p = id.split(":");
        String kind = p[1];
        String key = p[2];
        int i = Integer.parseInt(p[3]);
        switch (kind) {
            case "multi" -> Settings.toggle(SettingsRows.MULTI.get(key).keys()[i]);
            case "rulemulti" -> RuleEditor.step(RuleEditor.byId(SettingsRows.MULTI.get(key).keys()[i]), 1);
            case "choice" -> Settings.setChoice(key, i);
            case "lang" -> {
                Settings.setNumber("langOverride", i);
                menu.rebuildMap();              // its text is in the old language: rebuilt in the background
            }
            case "fill" -> {
                int v = SettingsRows.FILLS[i];
                if (v == 100) {
                    Settings.resetNumber("menuFill");
                } else {
                    Settings.setNumber("menuFill", v);
                }
            }
            case "preset" -> RuleEditor.applyPreset(i);
            default -> {
            }
        }
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
