package io.github.anilken.wynnlabs.search;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Which places are on Wynntils' map from the search card. Two kinds: the marks the player asked
 * for ("Mark on the map" on a row, "All on the map", "Open on map"), which live in
 * {@link KeptMarks} and stay after the menu closes and after a restart until taken off; and the
 * picked row's own mark, which follows the card and goes with it (and with the menu). A mark's ✕
 * takes it off; "Remove all" clears the kept ones and hides the picked row's until another row
 * is picked. A new card keeps every kept mark. Pure apart from the store: the card hands
 * {@link #marks} to {@code Wyn.showMarks}.
 */
public final class MarkSet {

    private final KeptMarks kept;
    /** This card's rows, their places as spots (worked out once per card, read every frame). */
    private List<Source> rows = List.of();
    private Function<Source, String> label = Source::name;
    private List<Set<String>> rowSpots = List.of();
    private Set<String> allSpots = Set.of();
    /** The picked row's places the player took off while this card shows. */
    private final Set<String> removed = new HashSet<>();
    private boolean off;

    public MarkSet(KeptMarks kept) {
        this.kept = kept;
    }

    /** A new card: its rows; every kept mark stays. */
    public void reset(List<Source> rows, Function<Source, String> label) {
        this.rows = rows;
        this.label = label;
        List<Set<String>> spots = new ArrayList<>();
        Set<String> every = new LinkedHashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            int row = i;
            Set<String> s = new LinkedHashSet<>();
            for (MapMark m : MapMark.of(rows, -1, j -> j == row, label)) {
                s.add(m.spot());
            }
            spots.add(s);
            every.addAll(s);
        }
        rowSpots = spots;
        allSpots = every;
        removed.clear();
        off = false;
    }

    /** The row texts changed (the wiki arrived): the marks added from now on are named with them. */
    public void label(Function<Source, String> label) {
        this.label = label;
    }

    /** Whether every row with a place is on the map by the player's choice. */
    public boolean all() {
        return kept.hasAll(allSpots);
    }

    /** Whether the player has any mark kept (the title's "Marked N"). */
    public boolean kept() {
        return !kept.all().isEmpty();
    }

    /** Whether row {@code i} is on the map by the player's choice. */
    public boolean marked(int i) {
        return i >= 0 && i < rowSpots.size() && kept.hasAll(rowSpots.get(i));
    }

    /** Row {@code i} on the map, or off it when it is. */
    public void toggle(int i) {
        off = false;
        if (marked(i)) {
            kept.removeAll(rowSpots.get(i));
        } else {
            int row = i;
            kept.addAll(MapMark.of(rows, -1, j -> j == row, label));
        }
    }

    /** Every row on the map, or (when they all are) this card's marks off again; returns how many were added. */
    public int toggleAll() {
        off = false;
        if (all()) {
            kept.removeAll(allSpots);
            return 0;
        }
        return kept.addAll(MapMark.of(rows, -1, j -> true, label));
    }

    /** "Open on map": the picked row's places kept. */
    public void keep(int picked) {
        off = false;
        kept.addAll(MapMark.of(rows, -1, j -> j == picked, label));
    }

    /** A row picked: the map follows it again after "Remove all". */
    public void picked() {
        off = false;
    }

    /** One mark taken off: a kept one for good, the picked row's while this card shows. */
    public void remove(MapMark m) {
        if (kept.has(m.spot())) {
            kept.remove(m);
        } else {
            removed.add(m.spot());
        }
    }

    /** Everything off the map, the picked row too until a row is picked; returns what was kept (for Undo). */
    public List<MapMark> clear() {
        off = true;
        return kept.clear();
    }

    /** The marks on the map: every kept one, and the picked row's unless kept already, taken off or hidden. */
    public List<MapMark> marks(int picked) {
        if (off) {
            return kept.all();
        }
        List<MapMark> out = new ArrayList<>(kept.all());
        for (MapMark m : MapMark.of(rows, picked, j -> false, label)) {
            if (!kept.has(m.spot()) && !removed.contains(m.spot())) {
                out.add(m);
            }
        }
        return out;
    }

    /** With the menu closed: the kept marks alone. */
    public List<MapMark> closed() {
        return kept.all();
    }
}
