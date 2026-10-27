package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.LinkImport;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.Tuning;
import io.github.anilken.wynnlabs.build.WynnBuilderLink;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.DevHooks;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The gear panel's window over the menu: a soft card with three tabs. Choose items (search, the
 * type chips and level range over the list of what the character can use, the strip, the chosen
 * items and "Suggest build"; a loading view while the search runs; WynnBuilder's import); Results
 * (the ranked builds and the picked one's details, "Apply build N"; kept while you look back at
 * the items); Saved (Apply, Edit, Rename, Delete). Esc closes a menu, a rename, then the window,
 * never the menu. Nothing here acts in the game: Apply only changes the plan the panel shows.
 * This class holds the state; the views draw in {@link BuildPickView}, {@link BuildResultsView}
 * and {@link BuildSavedView}, the header and menus in {@link BuildFrame}, the clicks in
 * {@link BuildActions}.
 */
public final class BuildWindow {

    public static final BuildWindow INSTANCE = new BuildWindow();

    /** The screen's side: tooltips, item icons, a flash line, and the plan applied. */
    interface Host {
        void tip(int x, int y, int w, int h, List<Component> tip);

        /**
         * An item icon's hover: the item's own tooltip (Wynntils' for its guide stack, else
         * {@link #itemTooltip}) with {@code extra} lines under it.
         */
        void item(int x, int y, int w, int h, ItemData info, ItemStack stack, List<Component> extra);

        ItemStack icon(ItemData info);

        void flash(String text);

        /** A plan was applied (or changed): the panel selects its pill. */
        void applied();

        /** "Detailed" on a found build: the Home's stats view with it as the plan ({@code rank} from 1). */
        void detailed(Roadmap.Candidate c, int level, int rank);
    }

    public enum View { PICK, RESULTS, SAVED }

    boolean open;
    /** The compact menu this frame: the result view in one column. */
    boolean compact;
    /** The preview: the first chosen item's icon drawn this frame (x, y, w, h), for a hover shot. */
    int[] firstIcon;
    View view = View.PICK;
    EditBox search;
    EditBox rename;

    final BuildControls ui = new BuildControls();
    final BuildPickView pick = new BuildPickView(this, ui);
    final BuildResultsView results = new BuildResultsView(this, ui);
    final BuildSavedView saved = new BuildSavedView(this, ui);
    private final BuildActions actions = new BuildActions(this);
    private final BuildFrame frame = new BuildFrame(this);
    /** The open menu: 0 none, else {@link #MENU_RANGE}, {@link #MENU_BUDGET}, {@link #MENU_EDIT_BUDGET}; its anchor this frame. */
    int menu;
    int[] menuAt;
    final UiMenu.State menuState = new UiMenu.State();
    static final int MENU_RANGE = 1;
    static final int MENU_BUDGET = 2;
    static final int MENU_EDIT_BUDGET = 3;
    /** "Import from WynnBuilder", made on first use. */
    BuildImportView imports;

    // ---- PICK ----
    List<ItemData> chosen = new ArrayList<>();
    Tuning.Choice tuning = Tuning.Choice.PLAIN;
    /** The generation of the search the window waits for (-1 none). */
    int awaitGen = -1;

    // ---- RESULTS ----
    Around.Result result;
    int card;
    /** A saved build the picked result overwrites (Recompute), or null. */
    String overwriteId;

    // ---- SAVED ----
    String renameId;
    String editId;
    List<String> editLocks = List.of();
    Tuning.Choice editTuning = Tuning.Choice.PLAIN;

    private BuildWindow() {}

    // =====================================================================================
    // the screen's calls

    /** The window's text boxes, for the screen to add as widgets (init: again after a resize). */
    List<EditBox> init(Font f) {
        ui.font = f;
        search = UiInput.box(f, "wynnlabs.pick.search_hint", search, v -> pick.listScroll = 0);
        String renamed = rename == null ? "" : rename.getValue();
        rename = new EditBox(f, 0, 0, 100, 12, L.c("wynnlabs.saved.rename"));
        rename.setMaxLength(Plans.MAX_NAME);
        rename.setValue(renamed);
        paperField(rename);
        return List.of(search, rename);
    }

    private static void paperField(EditBox box) {
        box.setBordered(false);
        box.setTextColor(Theme.INK);
        box.setTextShadow(false);
    }

    public boolean isOpen() {
        return open;
    }

    public View view() {
        return view;
    }

    /**
     * "Choose items": the picker with the chosen items and the strip as they were. The results
     * only when a search finished while the window was closed and they were not seen yet; a
     * result list once seen and cancelled is gone (Suggest build again shows new ones).
     */
    public void openPick() {
        editingPlan = null;
        loadChoice();
        open = true;
        overwriteId = null;
        Around.Result r = Around.result();
        if (unseenGen >= 0 && r != null && r.gen() == unseenGen) {
            result = r;
            card = 0;
            fromHint = false;
            view = View.RESULTS;
        } else {
            view = View.PICK;
        }
        unseenGen = -1;
        focusSearch(view == View.PICK);
    }

    /** A finished search not shown yet (it finished while the window was closed); -1 none. */
    private int unseenGen = -1;
    /** The result list on show came from the better-build hint. */
    private boolean fromHint;

    /**
     * The result list is done with (Cancel, ✕, Esc, back, Apply): dropped, so the next
     * "Choose items" opens the picker. The hint's own list goes with its hint.
     */
    void discardResults() {
        if (result == null) {
            return;
        }
        if (fromHint) {
            Around.forgetCheck();
            Plans.HINT.dismiss();
        } else {
            Around.forget();
        }
        result = null;
        fromHint = false;
        unseenGen = -1;
    }

    BuildImportView imports() {
        if (imports == null) {
            imports = new BuildImportView(ui);
        }
        return imports;
    }

    /** Tests and the preview: whether a result list is held. */
    boolean hasResults() {
        return result != null;
    }

    /**
     * The plan's Edit: the picker with the plan's chosen items and strip. Suggest build, then
     * Apply replaces the plan, and updates its copy in My builds when it has one.
     */
    public void editPlan(Plans.Build plan) {
        if (plan == null) {
            return;
        }
        open = true;
        overwriteId = null;
        editingPlan = Plans.isSaved(plan) ? plan.id() : "";
        chosen = new ArrayList<>(Locks.resolve(GameState.current(), plan.locks()));
        tuning = plan.tuning();
        Locks.set(names(chosen));
        Locks.setTuning(tuning);
        Around.forget();
        result = null;
        unseenGen = -1;
        view = View.PICK;
        focusSearch(true);
    }

    /** Editing the plan: "" for an unsaved one, its saved copy's id, null when not editing it. */
    String editingPlan;

    /** Tests: the items and the strip the picker holds. */
    public List<String> chosenNames() {
        return names(chosen);
    }

    public Tuning.Choice tuning() {
        return tuning;
    }

    /** "My builds". */
    public void openSaved() {
        editingPlan = null;
        open = true;
        view = View.SAVED;
        renameId = null;
        focusSearch(false);
    }

    /** The better-build hint: its results. */
    public void openResults(Around.Result r) {
        if (r == null) {
            return;
        }
        open = true;
        result = r;
        card = 0;
        overwriteId = null;
        fromHint = r.check();
        editingPlan = null;
        chosen = new ArrayList<>(Locks.resolve(GameState.current(), r.around()));
        tuning = r.tuning();
        view = View.RESULTS;
        focusSearch(false);
    }

    public void close() {
        open = false;
        if (imports != null) {
            imports.shown = null;
        }
        renameId = null;
        focusSearch(false);
        if (rename != null) {
            rename.setFocused(false);
        }
    }

    private void focusSearch(boolean on) {
        if (search != null) {
            search.setFocused(on);
        }
    }

    /** The text box that should have the keyboard, or null. */
    EditBox focused() {
        if (!open) {
            return null;
        }
        if (renameId != null && view == View.SAVED) {
            return rename;
        }
        return view == View.PICK && search.isFocused() ? search : null;
    }

    private void loadChoice() {
        chosen = new ArrayList<>(Locks.resolve(GameState.current(), Locks.names()));
        tuning = Locks.tuning();
    }

    /** The level a set around these items is planned for: yours, or the highest chosen item's. */
    static int planLevel(List<ItemData> items) {
        return Math.max(GameState.current().level(), items.stream().mapToInt(ItemData::level).max().orElse(0));
    }

    /** An item's tooltip from the item data (BuildText). */
    static List<Component> itemTooltip(ItemData info) {
        return BuildText.itemTooltip(info);
    }

    static List<String> names(List<ItemData> items) {
        return items.stream().map(ItemData::name).toList();
    }

    /** The choice or the strip changed: saved for the character, the old results and a running search dropped. */
    void changed() {
        Locks.set(names(chosen));
        Locks.setTuning(tuning);
        Around.forget();
        result = null;                              // its tab goes: the results were for the old choice
        if (Around.running() && awaitGen >= 0) {
            Around.cancel();
        }
        awaitGen = -1;
    }

    /**
     * Each frame while the window is closed: a search it started has finished, so say so; the
     * results wait for the window to open again.
     */
    void poll(Host host) {
        Around.Result r = Around.result();
        if (!open && awaitGen >= 0 && r != null && r.gen() == awaitGen) {
            awaitGen = -1;
            unseenGen = r.gen();                    // shown once when the window opens again
            host.flash(L.n("wynnlabs.pick.ready", r.builds().size(), r.builds().size()));
        }
    }

    /** The click areas drawn this frame, for the preview's layout check. */
    public List<LayoutCheck.Area> clickAreas() {
        List<LayoutCheck.Area> out = new ArrayList<>();
        for (BuildControls.Btn b : ui.btns) {
            out.add(new LayoutCheck.Area(b.x(), b.y(), b.w(), b.h(), b.act() + (b.arg() == null ? "" : ":" + b.arg())));
        }
        return out;
    }

    // =====================================================================================
    // drawing

    /**
     * Draws the window over the menu {@code menu} (x, y, w, h), {@code large} for the large menu.
     */
    void draw(GuiGraphics g, int[] area, boolean large, boolean compact, Host host, int mx, int my, float pt) {
        this.compact = compact;
        firstIcon = null;
        ui.btns.clear();
        if (!open) {
            return;
        }
        ui.font = UiFont.get();
        // a finished search for the window: its results
        Around.Result r = Around.result();
        if (awaitGen >= 0 && r != null && r.gen() == awaitGen) {
            awaitGen = -1;
            result = r;
            card = 0;
            view = View.RESULTS;
            focusSearch(false);
        }
        g.nextStratum();                                // over the menu's text and items
        LayoutCheck.cover(g.pose(), area[0] - 5, area[1] - 5, area[2] + 10, area[3] + 10);
        // (the dev screenshot tool, while it runs: the dim in the frame's own shape, its corners clear)
        if (!DevHooks.ON || !DevHooks.dim(g, area[0] - 5, area[1] - 5, area[2] + 10, area[3] + 10, 0x66201810)) {
            g.fill(area[0] - 5, area[1] - 5, area[0] + area[2] + 5, area[1] + area[3] + 5, 0x66201810);
        }
        int ww = Math.min(area[2] - 16, large ? 600 : 460);
        int wh = Math.min(area[3] - 14, large ? 420 : 320);
        int wx = area[0] + (area[2] - ww) / 2;
        int wy = area[1] + (area[3] - wh) / 2;
        // a soft card on an opaque body (the card's sprite is a touch translucent), its shadow on the dim
        Kit.windowShadow(g, wx, wy, ww, wh);
        g.fill(wx + 4, wy + 4, wx + ww - 4, wy + wh - 3, 0xFFEADCB4);
        Kit.softCard(g, wx, wy, ww, wh);
        int x = wx + Theme.SP_5;
        int y = wy + Theme.SP_5;
        int w = ww - 2 * Theme.SP_5;
        int h = wh - 2 * Theme.SP_5;
        int head = frame.tabs(g, host, x, y, w, mx, my);
        switch (view) {
            case PICK -> pick.drawPick(g, host, x, y + head, w, h - head, mx, my, pt);
            case RESULTS -> results.drawResults(g, host, x, y + head, w, h - head, mx, my);
            case SAVED -> saved.drawSaved(g, host, x, y + head, w, h - head, mx, my, pt);
        }
        if (menu != 0 && menuAt != null) {
            frame.menuList(g, mx, my);
        } else {
            menu = 0;
        }
    }

    // =====================================================================================
    // input

    public boolean mouseClicked(double mx, double my, int button) {
        return open && actions.click(mx, my, button);
    }

    private Host lastHost;

    void host(Host h) {
        lastHost = h;
    }

    Host host() {
        return lastHost;
    }

    /** A tab: the view, the search box focused on the items. */
    void showView(View v) {
        if (v == View.RESULTS && result == null) {
            return;
        }
        view = v;
        menu = 0;
        focusSearch(v == View.PICK && imports().shown == null);
    }

    void hostApplied() {
        if (lastHost != null) {
            lastHost.applied();
        }
        close();
    }

    /** A drag with the button held: a scrollbar's thumb follows it. True when a bar took it. */
    boolean mouseDragged(double mx, double my) {
        return open && actions.drag(my);
    }

    void mouseReleased() {
        pick.listBar.mouseReleased();
        saved.savedBar.mouseReleased();
        results.detailBar.mouseReleased();
        menuState.release();
    }

    /** The preview and tests: the Choose-items list's bar and where the list is. */
    ScrollBar listBar() {
        return pick.listBar;
    }

    int listScroll() {
        return pick.listScroll;
    }

    boolean mouseScrolled(double mx, double my, double v) {
        return open && actions.scroll(mx, my, v);
    }

    /** Esc closes a rename, else the window (never the menu); Enter saves a rename. True when the window took the key. */
    boolean keyPressed(int key) {
        if (!open) {
            return false;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (menu != 0) {
                menu = 0;
            } else if (renameId != null) {
                renameId = null;
                rename.setFocused(false);
            } else {
                if (view == View.RESULTS) {
                    discardResults();
                }
                close();
            }
            return true;
        }
        boolean enter = key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
        if (renameId != null && enter) {
            actions.saveRename(System.currentTimeMillis());
            return true;
        }
        if (view == View.RESULTS && result != null && !result.builds().isEmpty()) {
            // the results: up and down pick a card, Enter applies it
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                card = Math.max(0, Math.min(result.builds().size() - 1, card + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_UP ? -1 : 1)));
                return true;
            }
            if (enter) {
                actions.apply(System.currentTimeMillis());
                return true;
            }
        }
        return false;
    }

    // =====================================================================================
    // the preview

    /** The preview: the picker with WynnBuilder's link {@code link} read, as after "Import from WynnBuilder". */
    public void previewImport(String link) {
        openPick();
        BuildImportView v = imports();
        WynnBuilderLink.Build b = WynnBuilderLink.decode(link);
        v.shown = b == null ? null : LinkImport.of(GameState.current(), b);
    }
}
