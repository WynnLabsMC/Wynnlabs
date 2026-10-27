package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.Tuning;
import io.github.anilken.wynnlabs.game.GameState;

import java.util.ArrayList;
import java.util.List;

/** The build window held still for the previews and tests: a view with its state set. */
final class BuildPreviews {

    private BuildPreviews() {}

    /** The preview: the PICK view with a search typed and these items chosen. */
    static void pick(BuildWindow w, String typed, List<String> items, Tuning.Choice t) {
        w.open = true;
        w.view = BuildWindow.View.PICK;
        w.pick.type = 0;
        w.pick.listScroll = 0;
        w.search.setValue(typed);
        w.search.setFocused(false);
        w.chosen = new ArrayList<>(Locks.resolve(GameState.current(), items));
        w.tuning = t;
        w.awaitGen = -1;
        if (w.imports != null) {
            w.imports.shown = null;
        }
    }

    /** The preview: the RESULTS view of {@code r}, card {@code pick}. */
    static void results(BuildWindow w, Around.Result r, int pick) {
        w.openResults(r);
        w.card = pick;
    }

    /** The preview: the SAVED view, scrolled {@code scroll} rows, renaming or editing a build (null: neither). */
    static void saved(BuildWindow w, int scroll, String renaming, String editing) {
        w.open = true;
        w.view = BuildWindow.View.SAVED;
        w.saved.savedScroll = scroll;
        w.renameId = null;
        String id = renaming != null ? renaming : editing;
        if (id != null) {
            // the editor on that build; renaming: its name being typed
            w.openEditor(id);
            if (renaming != null) {
                Plans.Build b = Plans.byId(renaming);
                w.rename.setValue(b == null ? "" : b.name() + " 2");
                w.rename.setFocused(true);
            }
        }
    }
}
