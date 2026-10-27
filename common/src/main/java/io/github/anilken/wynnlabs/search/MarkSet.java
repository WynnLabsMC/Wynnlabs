package io.github.anilken.wynnlabs.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Which of the search card's places are on Wynntils' map. The picked row always shows (as today);
 * the player adds rows one by one ("Mark on the map"), or all of them ("All on the map"), and may
 * take single marks off again (the ✕ in the marked list). Marks the player asked for are kept
 * after the menu closes; the picked row alone is not. "Remove all" takes everything off, the
 * picked row too, until another row is picked. A new card starts over. Pure: the card hands the
 * list to {@code Wyn.showMarks}.
 */
public final class MarkSet {

    private boolean all;
    private final Set<Integer> rows = new HashSet<>();
    private final Set<String> removed = new HashSet<>();
    private boolean kept;
    private boolean off;

    /** A new card: nothing marked, nothing kept. */
    public void reset() {
        all = false;
        rows.clear();
        removed.clear();
        kept = false;
        off = false;
    }

    public boolean all() {
        return all;
    }

    /** Whether the marks stay on the map once the menu closes. */
    public boolean kept() {
        return kept;
    }

    /** Whether row {@code i} is on the map by the player's choice. */
    public boolean marked(int i) {
        return !off && (all || rows.contains(i));
    }

    /** Row {@code i} of {@code n} on or off the map. */
    public void toggle(int i, int n) {
        off = false;
        if (all) {
            all = false;
            for (int r = 0; r < n; r++) {
                rows.add(r);
            }
        }
        if (!rows.remove(i)) {
            rows.add(i);
        }
        kept = !rows.isEmpty();
    }

    /** Every row on the map, or (when they all are) back to the picked row only. */
    public void toggleAll() {
        off = false;
        if (all) {
            all = false;
            rows.clear();
            kept = false;
        } else {
            all = true;
            removed.clear();
            kept = true;
        }
    }

    /** The marks kept for later without adding rows ("Open on map"). */
    public void keep() {
        off = false;
        kept = true;
    }

    /** A row picked: the map follows it again after "Remove all". */
    public void picked() {
        off = false;
    }

    /** One mark taken off. */
    public void remove(MapMark m) {
        removed.add(m.spot());
    }

    /** Everything off the map, the picked row too, until a row is picked. */
    public void clear() {
        all = false;
        rows.clear();
        removed.clear();
        kept = false;
        off = true;
    }

    /** The marks for these rows: the picked row's and the marked rows', minus the ones taken off. */
    public List<MapMark> marks(List<Source> sources, int picked, Function<Source, String> label) {
        if (off) {
            return List.of();
        }
        List<MapMark> out = new ArrayList<>();
        for (MapMark m : MapMark.of(sources, picked, this::marked, label)) {
            if (!removed.contains(m.spot())) {
                out.add(m);
            }
        }
        return out;
    }
}
