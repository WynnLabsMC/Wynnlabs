package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.BuildEdits;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;

import java.util.ArrayList;

/**
 * The EDIT view's buttons ("e." actions): the name box, keep / swap / remove on a slot, Make my
 * plan, Recompute (the results overwrite this build), Save as copy, Delete, and Undo of an
 * overwrite. Every change is kept at once.
 */
final class BuildEditActions {

    private final BuildWindow win;

    BuildEditActions(BuildWindow win) {
        this.win = win;
    }

    void run(String act, Object arg, long now) {
        Plans.Build b = win.editId == null ? null : Plans.byId(win.editId);
        if (act.equals("e.open")) {
            win.openEditor((String) arg);
            return;
        }
        if (b == null) {
            return;
        }
        switch (act) {
            case "e.name" -> win.rename.setFocused(true);
            case "e.keep" -> Plans.update(BuildEdits.toggleKeep(b, (String) arg), now);
            case "e.remove" -> Plans.update(BuildEdits.remove(b, (String) arg), now);
            case "e.swap" -> {
                // the items, filtered to that slot: a click there puts the piece in and comes back
                int i = (int) arg;
                win.pick.type = BuildEditView.PICK_TYPE[i];
                win.pick.listScroll = 0;
                win.showView(BuildWindow.View.PICK);
                win.swapSlot = i;
            }
            case "e.plan" -> {
                Plans.apply(b);
                win.hostApplied();
            }
            case "e.recompute" -> {
                // a search around the kept items with the build's strip; Apply in the results overwrites it
                win.chosen = new ArrayList<>(Locks.resolve(GameState.current(), b.locks()));
                win.tuning = b.tuning();
                Locks.set(BuildWindow.names(win.chosen));
                Locks.setTuning(win.tuning);
                win.overwriteId = b.id();
                Around.forget();
                win.result = null;
                win.awaitGen = Around.request(BuildWindow.names(win.chosen), win.tuning, false);
                win.showView(BuildWindow.View.PICK);
            }
            case "e.copy" -> win.openEditor(Plans.save(BuildEdits.copyOf(b), now).id());
            case "e.delete" -> {
                Plans.delete(b.id(), now);                  // Undo in My builds for a few seconds
                win.editId = null;
                win.showView(BuildWindow.View.SAVED);
            }
            case "e.undo" -> {
                if (Plans.undoOverwrite(now)) {
                    win.openEditor(b.id());
                }
            }
            default -> { }
        }
    }

    /** A piece picked for the slot being swapped: into the build, back to it. */
    void swapIn(ItemData info, long now) {
        Plans.Build b = Plans.byId(win.editId);
        Integer i = win.swapSlot;
        win.swapSlot = null;
        if (b != null && i != null) {
            Plans.update(BuildEdits.swap(b, Plans.SLOTS[i], info.name()), now);
        }
        win.showView(BuildWindow.View.EDIT);
    }
}
