package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.SourceHint;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.quests.Waypoints;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemWiki;
import io.github.anilken.wynnlabs.search.MapMark;
import io.github.anilken.wynnlabs.search.KeptMarks;
import io.github.anilken.wynnlabs.search.MarkSet;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.SourceRank;
import io.github.anilken.wynnlabs.search.TrackedMobs;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * The search tab's answer card for the chosen row, on a soft card at the right (or in the
 * drawer on a small window). What it shows is the row's {@link CardView} (an {@link ItemCard}, a
 * {@link ThingCard}); this is its frame. The head: the view's picture (an item's icon in an HD
 * slot, a thing's badge), its name in its ink with a copy button and its seal, its kind and its
 * status line. The actions: "Compass ›" to the easiest place, then the view's chips ("Open on
 * map", "Compass to all", "Market", "Wiki"; pinned at the card's foot on the large window). Then
 * the view's rows ({@link SearchCardRows}) and its sections under them. Rows are worked out once
 * per choice; the wiki page loads in the background and fills in a merchant's cost when it arrives.
 * The map marks follow {@link MarkSet}: the picked row while the card shows, and the marks the
 * player asked for, which stay ({@link KeptMarks}) after the menu closes and a restart.
 */
final class SearchCardPanel extends MenuPaint {

    /** A chip of the card: its icon and words, whether it shows, whether it is lit, and what a click does. */
    record Action(String icon, String label, boolean enabled, boolean on, Runnable run) {}

    final SearchTab tab;
    final SearchCardRows sources;
    /** What the card shows, null with nothing chosen. */
    CardView view;
    List<Source> rows = List.of();
    /** The card each row links to (a mob, a town, a dungeon), null where none; as long as {@link #rows}. */
    List<Entry> rowLinks = List.of();
    /** The row the map and "open on map" follow (0: the easiest). */
    int picked;
    private CompletableFuture<ItemWiki.Info> wikiLoad;
    ItemWiki.Info wiki;
    MarkSet marks = new MarkSet(KeptMarks.get());
    /** The marks on the map now (as last handed to Wynntils). */
    private List<MapMark> shown = List.of();
    int scroll;
    private int scrollMax;
    /** The name last copied with the name button, and when: its icon shows a tick for a moment. */
    private String copiedName;
    private long copiedAt;
    /** The card's bar, kept between frames so its thumb can be dragged. */
    private final ScrollBar bar = new ScrollBar();
    /** The card's visible part this frame: clicks and hovers outside it do not count. */
    private int[] clip = new int[4];
    /** The large card's box, for the wheel. */
    private int[] box = new int[4];

    SearchCardPanel(SearchTab tab) {
        super(tab.menu);
        this.tab = tab;
        this.sources = new SearchCardRows(this);
    }

    /** The shown view's {@link CardView#key}, or null. */
    String key() {
        return view == null ? null : view.key();
    }

    /** Shows this view: its rows from where the player stands, its wiki page asked for, the map's marks replaced. */
    void select(CardView v) {
        view = v;
        picked = 0;
        scroll = 0;
        marks = new MarkSet(KeptMarks.get());
        v.select();
        rebuildSources();
        String page = v.wikiPage();
        wikiLoad = page == null ? null : ItemWiki.load(page);
        wiki = null;
        marks.reset(rows, label());
        marks();
    }

    /** The view's rows worked out again, from where the player stands. */
    private void rebuildSources() {
        rows = view.rows(SearchTab.position());
        rowLinks = rows.stream().map(view::link).toList();
    }

    /** The view's rows are others now (the chest chip's stars): worked out again; a route on the old set ends. */
    void rowsChanged() {
        if (view == null) {
            return;
        }
        if (!Waypoints.route().isEmpty()) {
            Waypoints.clearRoute();
        }
        rebuildSources();
        marks.reset(rows, label());
        marks();
    }

    /** Nothing to show (nothing listed): the card goes, and its marks unless the player kept them. */
    void clear() {
        if (view == null) {
            return;
        }
        view = null;
        rows = List.of();
        shown = marks.closed();
        Wyn.showMarks(shown);
    }

    /** The menu closes or the tab is left: our marks leave the map unless the player asked to keep them. */
    void closed() {
        shown = marks.closed();
        Wyn.showMarks(shown);
    }

    // ---- the map marks ----

    void marks() {
        if (view == null) {
            return;
        }
        marks.label(label());
        shown = marks.marks(picked);
        Wyn.showMarks(shown);
    }

    /** How a row's marks are named: the wiki's words once it arrived. */
    private Function<Source, String> label() {
        ItemWiki.Info w = wiki;
        CardView v = view;
        return s -> v.markText(s, w);
    }

    /** The marks the player kept (the title's "Marked N"), from every card, kept across closing and restarts. */
    List<MapMark> keptMarks() {
        return KeptMarks.get().all();
    }

    void removeMark(MapMark m) {
        marks.remove(m);
        refreshMarks();
    }

    /** "Remove all": everything off the map; returns what was kept, for Undo. */
    List<MapMark> clearMarks() {
        List<MapMark> was = marks.clear();
        refreshMarks();
        return was;
    }

    /** Undo after "Remove all": the marks back. */
    void restoreMarks(List<MapMark> was) {
        KeptMarks.get().addAll(was);
        marks.picked();
        refreshMarks();
    }

    private void refreshMarks() {
        if (view != null) {
            marks();
        } else {
            shown = marks.closed();
            Wyn.showMarks(shown);
        }
    }

    /** "All on the map": every row's places, kept after the menu closes; again: this card's marks off. */
    void toggleAll() {
        int added = marks.toggleAll();
        marks();
        if (marks.all()) {
            menu.flash(L.n("wynnlabs.search.marks_added", added));
        }
    }

    /** One row on or off the map. */
    void toggleRow(int i) {
        marks.toggle(i);
        marks();
        if (marks.marked(i)) {
            menu.flash(L.t("wynnlabs.search.marked"));
        }
    }

    /** A row picked: the map's focus follows it. */
    void pickRow(int i) {
        picked = i;
        marks.picked();
        marks();
    }

    // ---- drawing ----

    /** The large window's card: a soft card, the content scrolling, the actions pinned at its foot. */
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        box = new int[] {x, y, w, h};
        Kit.softCard(g, x, y, w, h);
        if (view == null) {
            UiEmpty.draw(g, font(), x, y, w, h, true, "search", L.t("wynnlabs.search.card_empty_title"),
                    List.of(L.t("wynnlabs.search.card_empty"), L.t("wynnlabs.search.card_empty_2")), null, null, mx, my);
            return;
        }
        loadWiki();
        int pad = Theme.SP_5;
        // the actions may wrap onto more rows: measured first, so every row sits inside the card
        int actionsH = actions(null, x + pad, 0, w - 2 * pad, mx, my);
        int footY = y + h - pad - actionsH;
        int top = y + Theme.SP_2;
        clip = new int[] {x + 1, top, w - 2, Math.max(0, footY - Theme.SP_4 - top)};
        int cw = w - 2 * pad - ScrollBar.W - Theme.SP_2;
        g.enableScissor(clip[0], clip[1], clip[0] + clip[2], clip[1] + clip[3]);
        int start = y + pad - scroll;
        int end = content(g, x + pad, start, cw, false, mx, my);
        g.disableScissor();
        int total = end - start + pad;
        scrollMax = Math.max(0, total - clip[3]);
        scroll = Math.min(scroll, scrollMax);
        if (scrollMax > 0) {
            bar.place(x + w - ScrollBar.W - Theme.SP_2, clip[1], clip[3], clip[3], total, scroll);
            bar.draw(g, mx, my);
        }
        clip = new int[] {x, footY, w, actionsH};
        actions(g, x + pad, footY, w - 2 * pad, mx, my);
    }

    /** The drawer's content (a small window): everything in one column, the actions under the head. */
    int flow(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        if (view == null) {
            return 0;
        }
        loadWiki();
        clip = new int[] {x - Theme.SP_2, top, w + 2 * Theme.SP_2, bottom - top};
        return content(g, x, y, w, true, mx, my) - y;
    }

    private void loadWiki() {
        if (wiki == null && wikiLoad != null && wikiLoad.isDone()) {
            wiki = wikiLoad.getNow(ItemWiki.Info.EMPTY);
            SourceHint.forget(view.wikiPage());     // the tooltip's line may now name a cost
            marks();
        }
    }

    private int content(GuiGraphics g, int x, int y, int w, boolean withActions, int mx, int my) {
        int cy = header(g, x, y, w, mx, my);
        if (withActions && (firstPlace() != null || view.actions().stream().anyMatch(Action::enabled))) {
            cy = actions(g, x, cy, w, mx, my) + Theme.SP_6;         // a view with nothing to do leaves no empty row
        }
        cy = view.lead(g, x, cy, w, mx, my);
        if (view.rowsTitle() != null) {
            cy = sources.draw(g, x, cy, w, mx, my) + Theme.SP_5;
        }
        return view.sections(g, x, cy, w, mx, my);
    }

    /** A click area clipped to the card's visible part. */
    void click(int x, int y, int w, int h, Runnable run) {
        int top = Math.max(y, clip[1]);
        int bottom = Math.min(y + h, clip[1] + clip[3]);
        if (bottom > top) {
            tab.click(x, top, w, bottom - top, run);
        }
    }

    /** Whether (mx, my) is on the card's visible part. */
    boolean over(int mx, int my) {
        return mx >= clip[0] && mx < clip[0] + clip[2] && my >= clip[1] && my < clip[1] + clip[3];
    }

    /** A tooltip for a box of the card, while the mouse is on it. */
    void tip(int x, int y, int w, int h, int mx, int my, String text) {
        if (over(mx, my) && mx >= x && mx < x + w && my >= y && my < y + h) {
            menu.hits.add(new MenuHit(x, y, w, h, null, List.of(Component.literal(text)), null));
        }
    }

    // ---- the head: picture, name, needs ----

    private int header(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int slot = 24;
        view.picture(g, x, y, slot, over(mx, my) && mx >= x && mx < x + slot && my >= y && my < y + slot);
        CardView.Head head = view.head();
        int tx = x + slot + Theme.SP_4;
        int tw = w - slot - Theme.SP_4;
        // the name, a copy button (to paste into the Trade Market's search or chat), the seal (an item's build gain)
        String gainText = head.seal();
        int gainW = gainText == null ? 0 : Type.META.width(font(), gainText) + 2 * Theme.SP_2 + Theme.SP_3;
        int copyW = view.copyable() ? Ui.CHIP_H + 2 : 0;
        String name = Type.HEADING.fit(font(), view.name(), tw - copyW - Theme.SP_3 - gainW);
        Type.HEADING.draw(g, font(), name, tx, y, view.ink());
        int cx = tx + Type.HEADING.width(font(), name) + Theme.SP_3;
        int cyb = Type.HEADING.onCaps(y, Ui.CHIP_H);
        if (view.copyable()) {
            boolean justCopied = view.name().equals(copiedName) && System.currentTimeMillis() - copiedAt < 1200;
            Ui.iconButton(g, justCopied ? "check" : "copy", cx, cyb, false, over(mx, my) ? mx : -1, my);
            String copied = view.name();
            click(cx, cyb, copyW, Ui.CHIP_H, () -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(copied);
                copiedName = copied;
                copiedAt = System.currentTimeMillis();
            });
            tip(cx, cyb, copyW, Ui.CHIP_H, mx, my, L.t("wynnlabs.search.copy_name"));
        }
        if (gainText != null) {
            Ui.seal(g, font(), gainText, cx + copyW + Theme.SP_3, Type.HEADING.onCaps(y, Ui.SEAL_H), Theme.SEAL_2);
        }
        int ly = y + Type.HEADING.lineHeight + Theme.SP_1;
        Type.META.draw(g, font(), Type.META.fit(font(), head.kind(), tw), tx, ly, Theme.INK_3);
        ly += Type.META.lineHeight;
        // the status (an item's needs), on a line of its own
        Component needs = head.status();
        List<FormattedCharSequence> need = font().split(needs, tw);
        if (!need.isEmpty() && !needs.getString().isEmpty()) {
            ink(g, need.get(0), tx, ly + 1, Theme.INK);
            ly += Type.BODY.lineHeight;
        }
        return Math.max(y + slot, ly) + Theme.SP_5;
    }

    // ---- the actions ----

    /**
     * "Compass ›" and the chips beside it, wrapping; returns the y under them. With {@code g} null
     * it only measures (nothing drawn, no clicks), by the same wrapping, so the two never disagree.
     */
    private int actions(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Area easiest = firstPlace();
        int bx = x;
        int cy = y;
        if (easiest != null) {
            String pin = L.t("wynnlabs.search.pin");
            int bw = Ui.iconButtonLabelWidth(font(), pin);
            if (g != null) {
                Ui.iconButtonLabel(g, font(), "compass", pin, bx, cy, bw, over(mx, my) ? mx : -1, my);
                click(bx, cy, bw, Ui.BUTTON_H, this::pinEasiest);
            }
            bx += bw + Theme.SP_3;
        }
        int chipY = cy + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        for (Action c : view.actions()) {
            if (!c.enabled()) {
                continue;
            }
            String label = c.label();
            int cw = Ui.iconChipWidth(font(), c.icon(), label, false);
            if (bx + cw > x + w && bx > x) {
                bx = x;
                cy += Ui.BUTTON_H + Theme.SP_2;
                chipY = cy + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
            }
            if (g != null) {
                Ui.iconChip(g, font(), c.icon(), label, false, bx, chipY, cw, c.on(), over(mx, my) ? mx : -1, my);
                click(bx, chipY, cw, Ui.CHIP_H, c.run());
            }
            bx += cw + Theme.SP_2;
        }
        return cy + Ui.BUTTON_H;
    }

    // ---- the chips a view lists among its actions ----

    /**
     * "Track" / "Tracking": the name on {@code list} (a mob is tracked by its floating name, an item
     * by its drops on the ground); null: not a thing to track, the chip does not show.
     */
    Action trackChip(TrackedMobs list, String name) {
        boolean tracked = list != null && list.has(name);
        return new Action(tracked ? "check" : "target", L.t(tracked ? "wynnlabs.search.tracking" : "wynnlabs.search.track"),
                list != null && TrackedMobs.watched(),
                tracked, () -> toggleTrack(list, name));
    }

    /** "Open on map", while a row has a place and the map can show it. */
    Action mapChip() {
        boolean mapOk = firstPlace() != null && GameState.current().running() && Wyn.up(Wyn.Feature.WAYPOINTS);
        return new Action("map-2", L.t("wynnlabs.search.open_map"), mapOk, false, this::openMap);
    }

    /** "Compass to all": a route over every one of {@code every}, nearest first; only with more than one. */
    Action pinAllChip(List<Area> every) {
        return new Action("compass", L.t("wynnlabs.search.pin_all"), every.size() > 1, false,
                () -> pinAll(SourceRank.nearest(every, SearchTab.position(), every.size())));
    }

    /** "Wiki": the page at {@code url}, asked first; null: no page, the chip does not show. */
    Action wikiChip(String url) {
        return new Action("arrow-up-right", L.t("wynnlabs.search.wiki"), url != null, false,
                () -> ConfirmLinkScreen.confirmLinkNow(menu, URI.create(url), true));
    }

    /** A Track toggle clicked: the name on or off that list (tracked mobs or tracked items), and the flash that says which. */
    void toggleTrack(TrackedMobs list, String name) {
        boolean now = list.toggle(name);
        menu.flash(L.t(now ? "wynnlabs.search.track_on" : "wynnlabs.search.track_off", name));
    }


    /** The first place of the rows, or null. */
    private Area firstPlace() {
        for (Source s : rows) {
            if (s.hasPlace()) {
                return s.main();
            }
        }
        return null;
    }

    /** Enter and "Compass ›": the compass on the easiest place that has one. */
    void pinEasiest() {
        for (Source s : rows) {
            if (s.hasPlace()) {
                pin(s.main(), rowText(s));
                return;
            }
        }
    }

    /** A row's words, as the view says them: the item card's source text, a thing's place label. */
    String rowText(Source s) {
        return view.rowText(s, wiki);
    }

    /** The view's name as a compass pin carries it: its first 32 characters. */
    private String shortName() {
        String name = view.pinName();
        return name.length() > 32 ? name.substring(0, 32) : name;
    }

    void pin(Area a, String label) {
        String what = shortName();
        if (Waypoints.go(a.x(), a.y(), a.z(), what)) {
            menu.flash(L.t("wynnlabs.screen.guiding", what));
        } else {
            String at = "/compass at " + a.x() + " " + a.y() + " " + a.z();
            Minecraft.getInstance().keyboardHandler.setClipboard(at);
            menu.flash(L.t("wynnlabs.screen.copied", at));
        }
    }

    /** Whether the route set now is this row's places (the same spots). */
    boolean routeOf(List<Area> places) {
        List<Waypoints.Mine> route = Waypoints.route();
        if (route.isEmpty() || route.size() > places.size()) {
            return false;
        }
        for (Waypoints.Mine m : route) {
            if (places.stream().noneMatch(p -> p.x() == m.x() && p.y() == m.y() && p.z() == m.z())) {
                return false;
            }
        }
        return true;
    }


    /** "Route ✔" again: the route is over, its pins leave the map. */
    void endRoute() {
        Waypoints.clearRoute();
        menu.flash(L.t("wynnlabs.search.route_off"));
    }

    void pinAll(List<Area> places) {
        String what = shortName();
        List<int[]> points = new ArrayList<>();
        for (Area p : places) {
            points.add(new int[] {p.x(), p.y(), p.z()});
        }
        int n = Waypoints.goAll(points, what);
        if (n > 0) {
            menu.flash(L.n("wynnlabs.search.pinned_many", n));
        } else {
            pin(places.get(0), what);
        }
    }

    /** "Open on map": the marks kept, Wynntils' map centred on the chosen row's place. */
    private void openMap() {
        if (picked >= rows.size() || !rows.get(picked).hasPlace()) {
            for (int i = 0; i < rows.size(); i++) {
                if (rows.get(i).hasPlace()) {
                    picked = i;
                    break;
                }
            }
        }
        if (picked >= rows.size() || !rows.get(picked).hasPlace()) {
            return;
        }
        Area a = rows.get(picked).main();
        marks.keep(picked);
        marks();
        Wyn.openMap(a.x(), a.z());
    }

    // ---- the wheel ----

    /** A press on the card's bar; true when it took it. */
    boolean barPress(double mx, double my) {
        if (scrollMax > 0 && bar.mouseClicked(mx, my)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    /** The bar's thumb dragged; true while it is. */
    boolean barDrag(double my) {
        if (bar.mouseDragged(my)) {
            scroll = Math.max(0, Math.min(scrollMax, bar.offset()));
            return true;
        }
        return false;
    }

    void barRelease() {
        bar.mouseReleased();
    }

    /** The wheel over the card scrolls it; true when it was over the card. */
    boolean scrolled(double x, double y, double v) {
        if (view == null || tab.menu.layout.compact() || !in(x, y, box)) {
            return false;
        }
        scroll = Math.max(0, Math.min(scrollMax, scroll - 20 * (int) Math.signum(v)));
        return true;
    }
}
