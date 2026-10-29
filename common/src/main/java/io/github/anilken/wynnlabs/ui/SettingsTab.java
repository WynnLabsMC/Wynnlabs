package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.MarketSlots;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.RuleEditor;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.UpdateCheck;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.ui.SettingsRows.SetRow;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The settings tab: the pages by feature (a list on the left, compact a filter row; Basics
 * first), a page of rows on one grid ({@link SettingsList}), the search box in the title row, the
 * changed settings, About behind the "?" and the Wynnventory key. The model is {@link SettingsRows}.
 */
final class SettingsTab extends MenuTab {

    SettingsTab(RoadmapScreen menu) {
        super(menu);
        list = new SettingsList(menu, this);
        pages = new SettingsPages(menu, this);
        nav = new SettingsNav(menu, this);
    }

    private final SettingsList list;
    final SettingsPages pages;
    private final SettingsNav nav;

    // ---- the tab ----

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        drawSettings(g, x, y, w, h, mx, my);
    }

    @Override
    List<EditBox> init() {
        // the Wynnventory key, pasted here instead of a file; shown as dots while typed
        keyBox = UiInput.box(font(), "wynnlabs.settings.key_box", null, v -> { });
        keyBox.setMaxLength(128);
        keyBox.addFormatter((text, pos) -> net.minecraft.util.FormattedCharSequence.forward("•".repeat(text.length()),
                net.minecraft.network.chat.Style.EMPTY));
        ruleBox = UiInput.box(font(), "wynnlabs.rules.type_hint", null, v -> { });
        ruleBox.setMaxLength(12);
        ruleBoxFor = null;
        searchBox = UiInput.box(font(), "wynnlabs.settings.search_hint", searchBox, s -> settingsScroll = 0);
        return List.of(keyBox, ruleBox, searchBox);
    }

    @Override
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return toggleClicked(event, doubleClick);
    }

    /** The tab's clicks are its own: the rest go to the screen's widgets. */
    @Override
    boolean sharedClicks() {
        return false;
    }

    /** Focuses a box the click is in (it takes the click); false when it is in none. */
    private boolean boxClicked(EditBox box, int[] rect, MouseButtonEvent event, boolean doubleClick) {
        if (rect == null || !in(event.x(), event.y(), rect)) {
            return false;
        }
        box.mouseClicked(event, doubleClick);
        box.setFocused(true);
        menu.setFocused(box);
        return true;
    }

    /** The settings list's scroll bar: dragged, clicked or wheeled. */
    final ScrollBar settingsBar = new ScrollBar();

    @Override
    boolean barDragged(double x, double y) {
        if (settingsBar.mouseDragged(y)) {
            settingsScroll = settingsBar.offset();
            return true;
        }
        return false;
    }

    @Override
    void barReleased() {
        settingsBar.mouseReleased();
    }

    /**
     * A click on the settings tab: the boxes, then the switches, chips and buttons this frame drew
     * ({@link RoadmapScreen#toggles}). True when one took it.
     */
    boolean toggleClicked(MouseButtonEvent event, boolean doubleClick) {
        if (menu.tab() == RoadmapScreen.TAB_SETTINGS && settingsListRect != null && settingsBar.mouseClicked(event.x(), event.y())) {
            settingsScroll = settingsBar.offset();
            return true;
        }
        double ex = event.x();
        double ey = event.y();
        boolean here = menu.tab() == RoadmapScreen.TAB_SETTINGS;
        if (here && keyBoxShown && keyBox.mouseClicked(event, doubleClick)) {
            keyBox.setFocused(true);
            menu.setFocused(keyBox);
            return true;
        }
        keyBox.setFocused(false);
        if (here && UiInput.clicked(searchHit, searchBox, event, doubleClick, menu::setFocused)) {
            return true;
        }
        searchBox.setFocused(false);
        if (ruleBoxFor != null && boxClicked(ruleBox, ruleBoxRect, event, doubleClick)) {
            return true;
        }
        if (ruleBox.isFocused()) {
            saveRuleBox();                      // clicking away keeps what was typed
        }
        for (Object[] t : menu.toggles) {
            if (in(ex, ey, (int[]) t[0])) {
                click(String.valueOf(t[1]), (int[]) t[0]);
                return true;
            }
        }
        return false;
    }

    private void click(String id, int[] rect) {
        if (id.equals("#ruleunit")) {
            // the next unit; the amount typed stays, so "100" E becomes 100 EB
            ruleUnit = (ruleUnit + 1) % UNIT_NAMES.length;
            ruleBox.setFocused(true);       // saved with the new unit
            saveRuleBox();
        } else if (id.startsWith("#cat:")) {
            settingsCat = Integer.parseInt(id.substring(5));
            aboutOpen = false;
            clearView();
        } else if (id.startsWith("#sfirst:")) {
            filterFirst = Math.max(0, filterFirst + Integer.parseInt(id.substring(8)));
        } else if (id.equals("#sshowchanged")) {
            showChanged = true;
            aboutOpen = false;
            searchBox.setValue("");
            settingsScroll = 0;
        } else if (id.equals("#sclearview")) {
            clearView();
        } else if (id.equals("#about")) {
            aboutOpen = !aboutOpen;
            clearView();
        } else if (id.equals("#searchopen")) {
            searchOpen = !searchOpen;
            if (searchOpen) {
                focusSearch();
            } else {
                searchBox.setValue("");
            }
        } else if (id.startsWith("#fold:")) {
            String f = id.substring(6);
            if (!openFolds.remove(f)) {
                openFolds.add(f);
            }
        } else if (id.startsWith("#ssel:") || id.startsWith("#stog:") || id.startsWith("#sreset:")) {
            pages.settingsAction(id);
        } else if (id.startsWith("#opt:")) {
            pages.optionClicked(id);
        } else if (id.startsWith("#upd:") || id.startsWith("#link:")) {
            pages.aboutClick(id);
        } else if (id.startsWith("#rule:")) {
            String[] p = id.split(":");
            RuleEditor.step(RuleEditor.byId(p[1]), Integer.parseInt(p[2]));
        } else if (id.startsWith("#step:")) {
            String[] p = id.split(":");
            Settings.step(p[1], Integer.parseInt(p[2]));
        } else if (id.equals("#slots")) {
            MarketSlots.setLimited(!MarketSlots.limited());
        } else if (id.equals("#keyopen")) {
            keyOpen = !keyOpen;
        } else if ("#rulesreset".equals(id)) {
            // two steps: the first click asks, a second within a few seconds resets
            if (System.currentTimeMillis() - resetArmedAt < RESET_WINDOW_MS) {
                resetArmedAt = 0;
                RuleEditor.reset();
                menu.flash(L.t("wynnlabs.rules.reset_done"));
            } else {
                resetArmedAt = System.currentTimeMillis();
            }
        } else if ("#keysave".equals(id)) {
            saveKey();
        } else if ("#keydel".equals(id)) {
            Prices.setKey("");
            menu.flash(L.t("wynnlabs.settings.key_removed"));
        } else if (id.startsWith("#hudmove:")) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new HudEditScreen(menu, id.substring(9)));
        } else if ("#archetype".equals(id)) {
            menu.archPicker.open(rect);
        } else if ("#goal".equals(id)) {
            goalMenu.open(rect);           // the goal list opens under its chip; a row picks it
        } else if (!id.startsWith("#")) {
            Settings.toggle(id);
        }
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        if (settingsListRect != null && in(x, y, settingsListRect)) {
            settingsScroll = Math.max(0, Math.min(settingsScrollMax, settingsScroll - 28 * (int) Math.signum(v)));
        }
    }

    // ---- the goal's dropdown (SettingsGoalMenu): a popup that owns the mouse ----

    final SettingsGoalMenu goalMenu = new SettingsGoalMenu(menu);

    @Override
    Popup popup() {
        return goalMenu;
    }

    /** Enter in the rule box or the key box saves it; Esc in the search box empties it. */
    @Override
    boolean keyPressed(int key) {
        boolean enter = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
        if (ruleBox != null && ruleBox.isFocused() && enter) {
            saveRuleBox();
            return true;
        }
        if (keyBox != null && keyBox.isFocused() && enter) {
            saveKey();
            return true;
        }
        return UiInput.escape(searchBox, key);
    }

    /** "/" while nothing is being typed: the search box (its "/" never lands in it). */
    @Override
    boolean slashTyped() {
        if (menu.layout.compact()) {
            searchOpen = true;
        }
        focusSearch();
        return true;
    }

    private void focusSearch() {
        UiInput.focus(searchBox, menu::setFocused);
    }

    EditBox keyBox;
    /** False while the key built into the mod is in use: the key's box asks for nothing. */
    boolean keyBoxShown = true;

    /** Saves what is in the key box (live prices switch on with it) and empties the box. */
    private void saveKey() {
        String v = keyBox.getValue().strip();
        if (v.isEmpty()) {
            return;
        }
        Prices.setKey(v);
        keyBox.setValue("");
        keyBox.setFocused(false);
        menu.setFocused(null);
        if (!Settings.on("priceApi")) {
            Settings.toggle("priceApi");
        }
        menu.flash(L.t("wynnlabs.settings.key_saved"));
    }

    // ---- state ----

    /** The pages, by name (the preview finds its pages here). */
    private static final String[] SETTING_CATS = SettingsRows.SETTING_CATS;

    int settingsCat;
    int settingsScroll;
    int settingsScrollMax;
    /** The list's box this frame, for the wheel. */
    int[] settingsListRect;
    /** The picked row (what it does and its example show): a page and an index in its rows; -1 for none. */
    int pickCat = -1;
    int pickRow = -1;
    /** The list of every changed setting instead of a page. */
    boolean showChanged;
    /** About (the "?") instead of a page. */
    boolean aboutOpen;
    /** The live prices' key box open under its note ("Key…"). */
    boolean keyOpen;
    /** The folding sections that are open (Advanced: the rules' groups, the look, bug reports). */
    final Set<String> openFolds = new HashSet<>();
    /** Compact: the search box open under the title row. */
    private boolean searchOpen;
    /** Compact: the pages' filter row's first chip. */
    int filterFirst;
    EditBox searchBox;
    UiInput.Hit searchHit;
    /** When the rules' reset was asked for: a second click within {@link #RESET_WINDOW_MS} resets. */
    long resetArmedAt;
    static final long RESET_WINDOW_MS = 4000;

    /** Opens About (the rail's Help, or the "?" at the foot of the pages). */
    void showAbout() {
        aboutOpen = true;
        clearView();
    }

    /** Back to the picked page: no search, not the changed list. */
    void clearView() {
        showChanged = false;
        if (searchBox != null) {
            searchBox.setValue("");
        }
        settingsScroll = 0;
        if (keyBox != null) {
            keyBox.setFocused(false);
        }
        menu.setFocused(null);
    }

    /** Whether the page lists search results or the changed settings instead of a page. */
    boolean listView() {
        return showChanged || searchBox != null && !searchBox.getValue().isBlank();
    }

    @Override
    MenuRail.Badge badge() {
        return UpdateCheck.state().status() == UpdateCheck.Status.NEWER
                ? new MenuRail.Badge(MenuRail.Badge.Kind.GOOD_DOT, "", L.t("wynnlabs.screen.rail_badge_update"))
                : null;
    }

    // ---- the page ----

    /**
     * The search box in the title row, right of the section tabs ("/" focuses it); compact, a
     * search button that opens it under the title row.
     */
    @Override
    int titleRight(GuiGraphics g, int right, int y, int room, int mx, int my) {
        searchHit = null;
        int by = y - 3;
        if (menu.layout.compact() || room < UiInput.MIN_W) {
            int bw = UiInput.icon(g, searchBox, searchOpen, right - Ui.CHIP_H - 2, by, mx, my);
            menu.toggles.add(new Object[] {new int[] {right - bw, by, bw, Ui.CHIP_H}, "#searchopen"});
            return bw;
        }
        int w = Math.min(168, room);
        pages.searchField(g, right - w, by, w, mx, my);
        return w;
    }

    /**
     * The settings: the pages on the left (compact: a filter row on top and the foot under the
     * page), then a page's rows, or the search results, or every changed setting, or About.
     */
    private void drawSettings(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        keyBoxShown = false;                    // set again where the key box is drawn
        ruleBoxRect = null;
        settingsListRect = null;
        if (settingsCat < 0 || settingsCat >= SETTING_CATS.length) {
            settingsCat = 0;
        }
        boolean compact = menu.layout.compact();
        int cx = x;
        int cw = w;
        int top = y;
        int bottom = y + h;
        if (compact) {
            top += nav.filter(g, x, y, w, mx, my) + Theme.SP_4;
            if (searchOpen) {
                pages.searchField(g, x, top, w, mx, my);
                top += Ui.CHIP_H + Theme.SP_4;
            }
            bottom -= SettingsNav.FOOT_H + Theme.SP_3;
            nav.compactFoot(g, x, bottom + Theme.SP_3, w, mx, my);
            cx += Theme.SP_3;                  // room for the picked row's wash and the changed dot
            cw -= Theme.SP_3;
        } else {
            int nw = SettingsNav.NAV_W;
            nav.nav(g, x + Theme.SP_2, y, nw - Theme.SP_2, h, mx, my);
            Kit.hair(g, x + nw + Theme.SP_5, y, 1, h, Theme.LINE);
            cx = x + nw + Theme.SP_7;
            cw = w - nw - Theme.SP_7;
        }
        String query = searchBox.getValue().strip();
        if (!query.isEmpty() || showChanged) {
            String title = showChanged ? L.t("wynnlabs.settings.changed_title") : L.t("wynnlabs.settings.search_title", query);
            if (!compact) {
                top += list.pageHead(g, cx, top, cw, title);
            }
            List<SettingsList.Item> items = SettingsList.found(showChanged ? null : query);
            if (items.isEmpty()) {
                String none = showChanged ? L.t("wynnlabs.settings.all_default") : L.t("wynnlabs.settings.search_none", query);
                Type.MUTED.draw(g, font(), Type.MUTED.fit(font(), none, cw), cx, top);
                return;
            }
            list.list(g, items, cx, top, cw, bottom - top, compact, mx, my);
            return;
        }
        if (aboutOpen) {
            if (!compact) {
                top += list.pageHead(g, cx, top, cw, L.t("wynnlabs.settings.about_title"));
            }
            pages.drawAboutPage(g, cx, top, cw, bottom - top, mx, my);
            return;
        }
        list.list(g, SettingsList.catItems(settingsCat, openFolds), cx, top, cw, bottom - top, compact, mx, my);
    }

    /** Opened on one setting (the box window's "Turn on" for live prices, About's update button): its page, its row picked. */
    void showSetting(String id) {
        for (int k = 1; k <= SETTING_CATS.length; k++) {
            int c = k % SETTING_CATS.length;            // a feature page before Basics
            List<SetRow> rows = SettingsRows.settingRows(SETTING_CATS[c]);
            for (int r = 0; r < rows.size(); r++) {
                if (id.equals(rows.get(r).id()) && !rows.get(r).head()) {
                    previewSettings(c, r, false);
                    return;
                }
            }
        }
    }

    /** The preview's shots: this page with this row picked (its folding section opened). */
    void previewSettings(int cat, int row, boolean detail) {
        settingsCat = cat;
        pickCat = cat;
        pickRow = row;
        settingsScroll = 0;
        aboutOpen = false;
        if (searchBox != null) {
            searchBox.setValue("");
        }
        showChanged = false;
        List<SetRow> rows = SettingsRows.settingRows(SETTING_CATS[Math.max(0, Math.min(SETTING_CATS.length - 1, cat))]);
        if (row >= 0 && row < rows.size() && rows.get(row).fold() != null) {
            openFolds.add(rows.get(row).fold());
        }
    }

    // ---- the rule box (an emerald rule's amount) ----

    EditBox ruleBox;
    /** The rule the box shows, by its short id; null for none. */
    String ruleBoxFor;
    int[] ruleBoxRect;
    int ruleUnit;                           // 0 E, 1 EB, 2 LE
    static final String[] UNIT_NAMES = {"E", "EB", "LE"};
    static final double[] UNIT_MULT = {1, 64, 4096};

    /** Saves what was typed in the rule box, in the chip's unit; a value that is no value is left as it was. */
    private void saveRuleBox() {
        RuleEditor.Rule r = ruleBoxFor == null ? null : RuleEditor.byId(ruleBoxFor);
        if (r != null) {
            String typed = ruleBox.getValue().trim();
            // a unit typed in the box ("1 le") wins over the chip
            boolean ok = RuleEditor.set(r, typed.matches(".*[a-zA-Z].*") ? typed : typed + " " + UNIT_NAMES[ruleUnit]);
            menu.flash(ok ? L.t("wynnlabs.rules.typed_ok", L.t(r.title()), RuleEditor.display(r)) : L.t("wynnlabs.rules.typed_bad"));
        }
        ruleBox.setFocused(false);
        menu.setFocused(null);
    }
}
