package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemWiki;
import io.github.anilken.wynnlabs.search.Source;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * What the search card shows for one chosen row: an item ({@link ItemCard}) or a thing of the
 * world ({@link ThingCard}). {@link SearchCardPanel} keeps the frame, the scroll, the map marks,
 * the compass pins and the wiki load, and asks the view for the rest: the head (picture, name,
 * kind, status), the rows ("How do I get it" / "Where") with their words, the action chips and the
 * sections under the rows. Nothing here needs an {@link Entry}: a view of something that is not
 * in the index (a recipe an item goes into) implements it the same way. {@link SearchViews#card}
 * makes the view of a key.
 */
interface CardView {

    /**
     * The head's words: the META line under the name, the status line under that (empty: none)
     * and the seal beside the name (null: none).
     */
    record Head(String kind, Component status, String seal) {}

    /** A small button of one row, left of its map mark (the loot chest row's stars): its words, its tooltip, its click. */
    record RowChip(String label, String tip, Runnable run) {}

    /** The key of the list row it is the card of: what {@code SearchTab.selName} holds while it shows. */
    String key();

    /** Its name: the head's, the copy button's, a compass pin's, and the search box's when it is opened by key. */
    String name();

    /** The drawer's title on a small window: its kind in a word or two. */
    String title();

    /** Chosen: what is worked out once per choice (what the player holds of it, its related items). */
    void select();

    // ---- the head ----

    /** Its picture in a square of {@code size} at x, y; {@code hover}: the mouse is on it (an item adds its tooltip). */
    void picture(GuiGraphics g, int x, int y, int size, boolean hover);

    /** The name's ink (an item's rarity). */
    int ink();

    Head head();

    // ---- the rows ----

    /** The rows' title: "How do I get it", "Where". */
    String rowsTitle();

    /**
     * Its rows from where the player stands ({@code pos}: x and z, null out of the world), easiest
     * first; asked again after {@link SearchCardPanel#rowsChanged}.
     */
    List<Source> rows(double[] pos);

    /** A row's words; {@code wiki}: the page asked for with {@link #wikiPage}, null until it arrived. */
    String rowText(Source s, ItemWiki.Info wiki);

    /** What a row's map marks say. */
    default String markText(Source s, ItemWiki.Info wiki) {
        return rowText(s, wiki);
    }

    /** The entry whose card a row opens (the mob, town or dungeon it names), or null. */
    default Entry link(Source s) {
        return null;
    }

    /** A row's own small button, or null. */
    default RowChip rowChip(Source s) {
        return null;
    }

    /** The wiki page to load in the background for the rows' words, or null. */
    default String wikiPage() {
        return null;
    }

    // ---- under the head ----

    /** The chips after "Compass ›", in order; the panel's own ({@link SearchCardPanel#mapChip} and the like) among them. */
    List<SearchCardPanel.Action> actions();

    /** Draws the sections under the rows; returns the y under them. */
    int sections(GuiGraphics g, int x, int y, int w, int mx, int my);

    /** "Legendary · Chestplate · Lv 80": an entry's rarity, its kind's words and its level, as the head's META line. */
    static String kindLine(Entry e, String kind) {
        String tier = e.tier() == null ? "" : SearchResultsPanel.words(e.tier()) + " · ";
        String lv = e.level() > 0 ? " · Lv " + e.level() : "";
        return tier + kind + lv;
    }
}
