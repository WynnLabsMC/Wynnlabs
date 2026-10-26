package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.RuleEditor;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.UpdateCheck;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.ui.SettingsRows.SetRow;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings tab: the categories (a list on the left, compact a filter row), a page of rows on
 * one grid ({@link SettingsList}), the search box in the title row, the changed settings, the
 * About page and the Wynnventory key. The model is {@link SettingsRows}.
 */
final class SettingsTab extends MenuTab {

    SettingsTab(RoadmapScreen menu) {
        super(menu);
        list = new SettingsList(menu, this);
        pages = new SettingsPages(menu, this);
    }

    private final SettingsList list;
    private final SettingsPages pages;

    // ---- the tab ----

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        drawSettings(g, x, y, w, h, mx, my, pt);
    }

    @Override
    List<EditBox> init() {
        // the Wynnventory key, pasted here instead of a file; shown as dots while typed
        keyBox = new EditBox(font(), 0, 0, 150, 12, Component.literal("Wynnventory API key"));
        keyBox.setMaxLength(128);
        keyBox.addFormatter((text, pos) -> net.minecraft.util.FormattedCharSequence.forward("•".repeat(text.length()),
                net.minecraft.network.chat.Style.EMPTY));
        paperField(keyBox);
        ruleBox = new EditBox(font(), 0, 0, 60, 12, L.c("wynnlabs.rules.type_hint"));
        ruleBox.setMaxLength(12);
        paperField(ruleBox);
        ruleBoxFor = -1;
        String typed = searchBox == null ? "" : searchBox.getValue();
        searchBox = new EditBox(font(), 0, 0, 100, 10, L.c("wynnlabs.settings.search_hint"));
        searchBox.setMaxLength(40);
        paperField(searchBox);
        searchBox.setValue(typed);
        searchBox.setResponder(s -> settingsScroll = 0);
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

    /** A click on a menu scale choice; the next frame draws at the new scale. */
    boolean scaleClicked(double ex, double ey) {
        for (int[] r : scaleRects) {
            if (in(ex, ey, r)) {
                Settings.setNumber("menuScale", r[4]);
                return true;
            }
        }
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

    /**
     * A click on the settings, AI or damage tab: the boxes, then the switches, chips and buttons
     * this frame drew ({@link RoadmapScreen#toggles}). True when one took it.
     */
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
        if (here && boxClicked(searchBox, searchRect, event, doubleClick)) {
            return true;
        }
        searchBox.setFocused(false);
        if (ruleBoxFor >= 0 && boxClicked(ruleBox, ruleBoxRect, event, doubleClick)) {
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
        if (id.startsWith("#ai")) {
            menu.ai.aiClick(id);
        } else if (id.equals("#ruleunit")) {
            // the next unit; the amount typed stays, so "100" E becomes 100 EB
            ruleUnit = (ruleUnit + 1) % UNIT_NAMES.length;
            ruleBox.setFocused(true);       // saved with the new unit
            saveRuleBox();
        } else if (id.startsWith("#fill:")) {
            int v = Integer.parseInt(id.substring(6));
            if (v == 100) {
                Settings.resetNumber("menuFill");
            } else {
                Settings.setNumber("menuFill", v);
            }
        } else if (id.startsWith("#lang:")) {
            Settings.setNumber("langOverride", Integer.parseInt(id.substring(6)));
            menu.rebuildMap();              // its text is in the old language: rebuilt in the background
        } else if (id.startsWith("#cat:")) {
            settingsCat = Integer.parseInt(id.substring(5));
            clearView();
        } else if (id.startsWith("#sfirst:")) {
            filterFirst = Math.max(0, filterFirst + Integer.parseInt(id.substring(8)));
        } else if (id.equals("#sshowchanged")) {
            showChanged = true;
            searchBox.setValue("");
            settingsScroll = 0;
        } else if (id.equals("#sclearview")) {
            clearView();
        } else if (id.equals("#searchopen")) {
            searchOpen = !searchOpen;
            if (searchOpen) {
                focusSearch();
            } else {
                searchBox.setValue("");
            }
        } else if (id.startsWith("#sall:")) {
            int[] ref = SettingsPages.ref(id);
            SectionSwitches.flip(sectionKeys(ref[0], ref[1]));
        } else if (id.startsWith("#ssel:") || id.startsWith("#stog:") || id.startsWith("#sreset:")) {
            pages.settingsAction(id);
        } else if (id.startsWith("#upd:") || id.startsWith("#link:")) {
            pages.aboutClick(id);
        } else if (id.startsWith("#rule:")) {
            String[] p = id.split(":");
            RuleEditor.step(RuleEditor.RULES.get(Integer.parseInt(p[1])), Integer.parseInt(p[2]));
        } else if (id.startsWith("#step:")) {
            String[] p = id.split(":");
            Settings.step(p[1], Integer.parseInt(p[2]));
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
        } else if ("#archetype".equals(id)) {
            menu.archPicker.open(rect);
        } else if ("#goal".equals(id)) {
            goalMenu.open(rect);           // the goal list opens under its chip; a row picks it
        } else {
            Settings.toggle(id);
            if ("webFont".equals(id)) {
                menu.reinit();             // init() again: the screen and its boxes take the new font now
            }
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
        if (searchBox != null && searchBox.isFocused() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !searchBox.getValue().isEmpty()) {
            searchBox.setValue("");
            return true;
        }
        return false;
    }

    /** "/" while nothing is being typed: the search box (its "/" never lands in it). */
    boolean slashTyped() {
        if (menu.layout.compact()) {
            searchOpen = true;
        }
        focusSearch();
        return true;
    }

    private void focusSearch() {
        searchBox.setFocused(true);
        menu.setFocused(searchBox);
    }

    EditBox keyBox;
    /** False while the key built into the mod is in use: the settings row then asks for nothing. */
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

    /** The categories, by name (the preview finds its pages here). */
    private static final String[] SETTING_CATS = SettingsRows.SETTING_CATS;

    int settingsCat;
    int settingsScroll;
    int settingsScrollMax;
    /** The list's box this frame, for the wheel. */
    int[] settingsListRect;
    /** The picked row (its example shows): a category and an index in its rows; -1 for none. */
    int pickCat = -1;
    int pickRow = -1;
    /** The list of every changed setting instead of a category. */
    boolean showChanged;
    /** Compact: the search box open under the title row. */
    private boolean searchOpen;
    /** Compact: the categories' filter row's first chip. */
    int filterFirst;
    EditBox searchBox;
    int[] searchRect;
    /** When the rules' reset was asked for: a second click within {@link #RESET_WINDOW_MS} resets. */
    long resetArmedAt;
    static final long RESET_WINDOW_MS = 4000;
    final List<int[]> scaleRects = new ArrayList<>();   // {x, y, w, h, scale}

    /** Opens the About page (the rail's Help). */
    void showAbout() {
        settingsCat = 0;                            // SETTING_CATS[0] is "about"
        clearView();
    }

    /** Back to the picked category: no search, not the changed list. */
    void clearView() {
        showChanged = false;
        if (searchBox != null) {
            searchBox.setValue("");
        }
        settingsScroll = 0;
        keyBox.setFocused(false);
        menu.setFocused(null);
    }

    /** Whether the page lists search results or the changed settings instead of a category. */
    boolean listView() {
        return showChanged || searchBox != null && !searchBox.getValue().isBlank();
    }

    @Override
    MenuRail.Badge badge() {
        return UpdateCheck.state().status() == UpdateCheck.Status.NEWER
                ? new MenuRail.Badge(MenuRail.Badge.Kind.GOOD_DOT, "", L.t("wynnlabs.screen.rail_badge_update"))
                : null;
    }

    static final int SET_ROW = 32;

    // ---- the page ----

    /**
     * The search box in the title row, right of the section tabs ("/" focuses it); compact, a
     * search button that opens it under the title row.
     */
    @Override
    int titleRight(GuiGraphics g, int right, int y, int room, int mx, int my) {
        searchRect = null;
        int by = y - 3;
        if (menu.layout.compact() || room < 90) {
            int bw = Ui.CHIP_H + 2;
            Ui.iconButton(g, "search", right - bw, by, searchOpen, mx, my);
            menu.toggles.add(new Object[] {new int[] {right - bw, by, bw, Ui.CHIP_H}, "#searchopen"});
            return bw;
        }
        int w = Math.min(168, room);
        pages.searchField(g, right - w, by, w, mx, my);
        return w;
    }

    /**
     * The settings: the categories on the left (compact: a filter row on top), then the page: a
     * category's rows under its title, or the search results, or every changed setting. Rules end
     * in the reset as an error notice; About shows the version, the update check and the links.
     */
    private void drawSettings(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        scaleRects.clear();
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
        if (compact) {
            top += list.filter(g, x, y, w, mx, my) + Theme.SP_4;
            if (searchOpen) {
                pages.searchField(g, x, top, w, mx, my);
                top += Ui.CHIP_H + Theme.SP_4;
            }
            cx += Theme.SP_3;                  // room for the picked row's wash and the changed dot
            cw -= Theme.SP_3;
        } else {
            int nw = SettingsList.NAV_W;
            list.nav(g, x + Theme.SP_2, y, nw - Theme.SP_2, h, mx, my);
            Kit.hair(g, x + nw + Theme.SP_5, y, 1, h, Theme.LINE);
            cx = x + nw + Theme.SP_7;
            cw = w - nw - Theme.SP_7;
        }
        int bottom = y + h;
        String query = searchBox.getValue().strip();
        if (!query.isEmpty() || showChanged) {
            String title = showChanged ? L.t("wynnlabs.settings.changed_title") : L.t("wynnlabs.settings.search_title", query);
            if (!compact) {
                top += list.pageHead(g, cx, top, cw, title, null, null);
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
        String cat = SETTING_CATS[settingsCat];
        if (!compact) {
            top += list.pageHead(g, cx, top, cw, L.t(SettingsRows.SETTING_CAT_NAMES[settingsCat]),
                    L.t(SettingsRows.SETTING_CAT_DESC[settingsCat]), cat.equals("about") ? null : L.t("wynnlabs.settings.applies_now"));
        }
        if (cat.equals("about")) {
            pages.drawAboutPage(g, cx, top, cw, bottom - top, mx, my);
            return;
        }
        if (cat.equals("rules")) {
            bottom -= pages.rulesNotice(g, cx, bottom, cw, mx, my) + Theme.SP_4;
        }
        list.list(g, SettingsList.catItems(settingsCat), cx, top, cw, bottom - top, compact, mx, my);
    }

    /** The switches under the section title at {@code head} of category {@code cat}, when it gets the "all off / all on" link. */
    List<String> sectionKeys(int cat, int head) {
        List<SetRow> rows = SettingsRows.settingRows(SETTING_CATS[cat]);
        List<String> keys = new ArrayList<>();
        for (int j = head + 1; j < rows.size() && !rows.get(j).head(); j++) {
            if (rows.get(j).kind().equals("switch")) {
                keys.add(rows.get(j).id());
            }
        }
        return SectionSwitches.keys(SETTING_CATS[cat], keys);
    }

    /** The preview's shots: this category with this row picked ({@code detail} is no longer a separate page). */
    void previewSettings(int cat, int row, boolean detail) {
        settingsCat = cat;
        pickCat = cat;
        pickRow = row;
        settingsScroll = 0;
        if (searchBox != null) {
            searchBox.setValue("");
        }
        showChanged = false;
    }

    // ---- the rule box (an emerald rule's amount) ----

    EditBox ruleBox;
    int ruleBoxFor = -1;                    // the rule the box shows, an index in RuleEditor.RULES
    int[] ruleBoxRect;
    int ruleUnit;                           // 0 E, 1 EB, 2 LE
    static final String[] UNIT_NAMES = {"E", "EB", "LE"};
    static final double[] UNIT_MULT = {1, 64, 4096};

    /** Saves what was typed in the rule box, in the chip's unit; a value that is no value is left as it was. */
    private void saveRuleBox() {
        if (ruleBoxFor >= 0 && ruleBoxFor < RuleEditor.RULES.size()) {
            RuleEditor.Rule r = RuleEditor.RULES.get(ruleBoxFor);
            String typed = ruleBox.getValue().trim();
            // a unit typed in the box ("1 le") wins over the chip
            boolean ok = RuleEditor.set(r, typed.matches(".*[a-zA-Z].*") ? typed : typed + " " + UNIT_NAMES[ruleUnit]);
            menu.flash(ok ? L.t("wynnlabs.rules.typed_ok", L.t(r.title()), RuleEditor.display(r)) : L.t("wynnlabs.rules.typed_bad"));
        }
        ruleBox.setFocused(false);
        menu.setFocused(null);
    }

    // ---- the Wynnventory key (drawn by SettingsPages) ----

    static int keyDetailHeight() {
        return SettingsPages.keyDetailHeight();
    }

    void drawKeyDetail(GuiGraphics g, int x, int y, int w, int mx, int my) {
        pages.drawKeyDetail(g, x, y, w, mx, my);
    }
}
