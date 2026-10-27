package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.Tuning;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the build window's click areas do ({@link BuildControls.Btn#act}): the tabs, the picker's
 * filters, strip and chosen items, the menus (level range, budget), Suggest, the WynnBuilder
 * import, the results (pick, apply, details), and the saved builds (apply, open in the editor,
 * undo, the edit card). Only the mod's plan changes; nothing happens in the game.
 */
final class BuildActions {

    private final BuildWindow win;
    private final BuildEditActions editActs;

    BuildActions(BuildWindow win) {
        this.win = win;
        this.editActs = new BuildEditActions(win);
    }

    boolean click(double mx, double my, int button) {
        BuildWindow.View view = win.view;
        BuildPickView pick = win.pick;
        BuildSavedView saved = win.saved;
        // the scrollbars first: a press there never reaches the rows
        ScrollBar bar = view == BuildWindow.View.PICK ? pick.listBar : view == BuildWindow.View.SAVED ? saved.savedBar
                : view == BuildWindow.View.RESULTS ? win.results.detailBar : null;
        if (win.menu == 0 && bar != null && button == 0 && bar.mouseClicked(mx, my)) {
            sync();
            return true;
        }
        BuildControls.Btn hit = null;
        for (int i = win.ui.btns.size() - 1; i >= 0; i--) {
            BuildControls.Btn b = win.ui.btns.get(i);
            if (BuildControls.in(mx, my, b.x(), b.y(), b.w(), b.h())) {
                hit = b;
                break;
            }
        }
        if (hit == null) {
            win.menu = 0;                               // the page or the dimmed menu: nothing under it reacts
            return true;
        }
        if (win.menu != 0 && !hit.act().equals("menuPick") && !hit.act().equals("menu")) {
            win.menu = 0;                                   // a click outside the open menu only closes it
            return true;
        }
        run(hit, button);
        return true;
    }

    /** A drag: a scrollbar's thumb (or the menu's) follows it; true when one took it. */
    boolean drag(double my) {
        if (win.menu != 0) {
            return win.menuState.drag(my);
        }
        boolean took = win.pick.listBar.mouseDragged(my) || win.saved.savedBar.mouseDragged(my) || win.results.detailBar.mouseDragged(my);
        if (took) {
            sync();
        }
        return took;
    }

    /** The lists follow their bars. */
    private void sync() {
        if (win.view == BuildWindow.View.PICK) {
            win.pick.listScroll = win.pick.listBar.offset();
        } else if (win.view == BuildWindow.View.SAVED) {
            win.saved.savedScroll = win.saved.savedBar.offset();
        } else {
            win.results.detailScroll = win.results.detailBar.offset();
        }
    }

    /** The wheel: the open menu, else the list (three rows a notch), the saved builds (one), or the result cards. */
    boolean scroll(double v) {
        return scroll(-1, -1, v);
    }

    /** The wheel at (mx, my): over the results' details it scrolls them. */
    boolean scroll(double mx, double my, double v) {
        int[] d = win.results.detailBox;
        if (win.view == BuildWindow.View.RESULTS && d != null && BuildControls.in(mx, my, d[0], d[1], d[2], d[3])) {
            win.results.detailScroll = Math.max(0, win.results.detailScroll - (int) Math.signum(v) * 3 * Theme.SP_4);
            return true;
        }
        int step = (int) -Math.signum(v);
        if (win.menu != 0) {
            win.menuState.wheel(v);
        } else if (win.view == BuildWindow.View.PICK) {
            win.pick.listScroll = Math.max(0, win.pick.listScroll + step * 3);
        } else if (win.view == BuildWindow.View.SAVED) {
            win.saved.savedScroll = Math.max(0, Math.min(win.saved.savedScroll, Math.max(0, Plans.saved().size() - 1)) + step);
        } else if (win.result != null && !win.result.builds().isEmpty()) {
            win.card = Math.max(0, Math.min(win.result.builds().size() - 1, win.card + step));
        }
        return true;
    }

    void run(BuildControls.Btn hit, int button) {
        long now = System.currentTimeMillis();
        GameState state = GameState.current();
        BuildPickView pick = win.pick;
        if (hit.act().startsWith("e.")) {
            editActs.run(hit.act(), hit.arg(), now);  // the EDIT view's buttons
            return;
        }
        switch (hit.act()) {
            case "close" -> {
                win.discardResults();               // ✕ drops the list: the picker next time
                win.close();
            }
            case "tab" -> win.showView((BuildWindow.View) hit.arg());
            case "menu" -> {
                int m = (int) hit.arg();
                win.menu = win.menu == m ? 0 : m;
                win.menuState.reset();
            }
            case "menuPick" -> pickMenu((int) hit.arg());
            case "search" -> win.search.setFocused(true);
            case "searchclear", "clearSearch" -> {
                win.search.setValue("");
                pick.type = 0;
                pick.range = 0;
                win.search.setFocused(true);
            }
            case "type" -> {
                pick.type = (int) hit.arg();
                pick.listScroll = 0;
            }
            case "item" -> {
                if (win.swapSlot != null && win.editId != null) {
                    editActs.swapIn((ItemData) hit.arg(), now);       // the edited build's slot gets it
                    return;
                }
                win.chosen = Locks.toggle(win.chosen, (ItemData) hit.arg());
                win.changed();
            }
            case "unchoose" -> {
                ItemData it = (ItemData) hit.arg();
                win.chosen.removeIf(c -> c.name().equals(it.name()));
                win.changed();
            }
            case "clear" -> {
                win.chosen.clear();
                win.changed();
            }
            case "focus" -> {
                win.tuning = win.tuning.withFocus((int) hit.arg());
                win.changed();
            }
            case "budgetNone" -> {
                // "No build fits the budget": the budget off, back to the items
                win.tuning = win.tuning.withBudget(0);
                win.changed();
                win.showView(BuildWindow.View.PICK);
            }
            case "suggest" -> {
                Locks.set(BuildWindow.names(win.chosen));
                Locks.setTuning(win.tuning);
                win.awaitGen = Around.request(BuildWindow.names(win.chosen), win.tuning, false);
                win.search.setFocused(false);
            }
            case "wbImport" -> {
                win.search.setFocused(false);
                win.imports().read(win.host());
            }
            case "wbCancel" -> win.imports().shown = null;
            case "wbPlan", "pickPlan" -> {
                // the items as the plan even when they cannot be worn yet: for its stats on the Home
                Plans.Build b = hit.act().equals("wbPlan") ? BuildImportView.asPlan(win.imports().shown, now)
                        : io.github.anilken.wynnlabs.build.LinkImport.asPlan(win.chosen, BuildWindow.planLevel(win.chosen), null, now);
                if (b == null) {
                    flash(L.t("wynnlabs.import.no_weapon"));
                } else {
                    win.imports().shown = null;
                    Plans.apply(b.named(Plans.defaultName(b)));
                    win.hostApplied();
                }
            }
            case "wbConfirm" -> {
                win.chosen = new ArrayList<>(win.imports().take());
                win.changed();
                flash(L.n("wynnlabs.import.done", win.chosen.size(), win.chosen.size()));
            }
            case "cancelRun" -> {
                Around.cancel();
                win.awaitGen = -1;
                if (win.overwriteId != null) {
                    win.showView(BuildWindow.View.EDIT);
                    win.overwriteId = null;
                }
            }
            case "back" -> {
                // to the items: the results stay (their tab) until the choice changes
                win.showView(win.overwriteId != null ? BuildWindow.View.EDIT : BuildWindow.View.PICK);
            }
            case "card" -> win.card = (int) hit.arg();
            case "apply" -> apply(now);
            case "detailed" -> {
                // the build's every stat on the Home beside what you wear; the list stays for next time
                Around.Result r = win.result;
                if (r != null && !r.builds().isEmpty() && win.host() != null) {
                    int i = Math.min(win.card, r.builds().size() - 1);
                    win.host().detailed(r.builds().get(i), r.level(), i + 1);
                    win.close();
                }
            }
            case "applySaved" -> {
                Plans.Build b = Plans.byId((String) hit.arg());
                if (b != null) {
                    Plans.apply(b);
                    win.hostApplied();
                }
            }
            case "undo" -> Plans.undo(now);
            case "addCurrent" -> {
                Plans.Build cur = Plans.current();
                if (cur != null) {
                    Plans.Build kept = Plans.save(cur.named(""), now);
                    Plans.apply(cur.withId(kept.id()).named(kept.name()));
                    win.saved.savedScroll = Integer.MAX_VALUE;            // scrolled to it (clamped when drawn)
                }
            }
            case "editFocus" -> win.editTuning = win.editTuning.withFocus((int) hit.arg());
            default -> { }
        }
    }

    /** A row of the open menu: a level range, a budget, or a leaning (that menu stays open). */
    private void pickMenu(int i) {
        if (win.menu == BuildWindow.MENU_LEAN || win.menu == BuildWindow.MENU_EDIT_LEAN) {
            boolean picker = win.menu == BuildWindow.MENU_LEAN;
            Tuning.Choice t = picker ? win.tuning : win.editTuning;
            if (i == BuildFrame.LEAN_NONE) {
                t = t.withoutLeans();
                win.menu = 0;
            } else {
                int bit = Tuning.LEANS[i];
                t = t.withLean(bit, !t.leans(bit));
            }
            if (picker) {
                win.tuning = t;
                win.changed();
            } else {
                win.editTuning = t;
            }
            return;
        }
        switch (win.menu) {
            case BuildWindow.MENU_RANGE -> {
                win.pick.range = i;
                win.pick.listScroll = 0;
            }
            case BuildWindow.MENU_BUDGET -> {
                win.tuning = win.tuning.withBudget(Tuning.BUDGETS[i]);
                win.changed();
            }
            case BuildWindow.MENU_EDIT_BUDGET -> win.editTuning = win.editTuning.withBudget(Tuning.BUDGETS[i]);
            default -> { }
        }
        win.menu = 0;
    }

    private void flash(String text) {
        if (win.host() != null) {
            win.host().flash(text);
        }
    }

    /** "Apply build N": the plan becomes that build (or, from a saved build's Recompute, overwrites it). */
    void apply(long now) {
        Around.Result r = win.result;
        if (r == null || r.builds().isEmpty()) {
            return;
        }
        Roadmap.Candidate c = r.builds().get(Math.min(win.card, r.builds().size() - 1));
        Map<String, String> items = new LinkedHashMap<>();
        for (String slot : Plans.SLOTS) {
            Damage.Piece p = c.set().get(slot);
            if (p != null) {
                items.put(slot, p.name());
            }
        }
        Plans.Build fresh = new Plans.Build("p" + now, "", r.around(), r.tuning(), items, c.score(), r.level(), now, now);
        if (win.overwriteId != null) {
            Plans.overwrite(win.overwriteId, fresh, now);       // Undo on the edit page for a few seconds
            win.openEditor(win.overwriteId);
            win.overwriteId = null;
            win.discardResults();
            if (win.host() != null) {
                win.host().applied();
            }
            return;
        }
        if (win.editingPlan != null && !win.editingPlan.isEmpty() && Plans.byId(win.editingPlan) != null) {
            // the plan's Edit: its saved copy follows (name kept), and it stays the plan
            Plans.Build kept = Plans.overwrite(win.editingPlan, fresh, now);
            Plans.apply(kept);
        } else {
            Plans.apply(fresh.named(Plans.defaultName(fresh)));
        }
        win.editingPlan = null;
        win.discardResults();                           // applied: the plan holds it now
        win.hostApplied();
    }
}
