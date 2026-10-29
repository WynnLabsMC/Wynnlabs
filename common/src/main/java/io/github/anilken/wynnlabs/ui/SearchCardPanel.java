package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.SourceHint;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.quests.Waypoints;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.ItemWiki;
import io.github.anilken.wynnlabs.search.MapMark;
import io.github.anilken.wynnlabs.search.KeptMarks;
import io.github.anilken.wynnlabs.search.MarkSet;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.SourceText;
import io.github.anilken.wynnlabs.search.Sources;
import io.github.anilken.wynnlabs.search.Things;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * The search tab's answer card for the chosen entry, on a soft card at the right (or in the
 * drawer on a small window). The head: the item's icon in an HD slot (a thing's badge), its name
 * in its rarity's colour with a copy button and the build gain, kind and level, and what it asks
 * of the player (✔/✘). The actions: "Compass ›" to the easiest place, "Open on map", "Compass to
 * all" for a thing of many places, "Market" and "Wiki" (pinned at the card's foot on the large
 * window). Then "How do I get it" / "Where" ({@link SearchCardRows}) and the sections under it
 * ({@link SearchCardSections}, a thing's {@link SearchThingCard}). Sources are worked out once per
 * choice; the wiki page loads in the background and fills in a merchant's cost when it arrives.
 * The map marks follow {@link MarkSet}: the picked row while the card shows, and the marks the
 * player asked for, which stay ({@link KeptMarks}) after the menu closes and a restart.
 */
final class SearchCardPanel extends MenuPaint {

    final SearchTab tab;
    final SearchCardSections sections;
    final SearchThingCard thing;
    final SearchCardRows sources;
    Entry entry;
    List<Source> rows = List.of();
    /** The card each row links to (a mob, a town, a dungeon), null where none; as long as {@link #rows}. */
    List<Entry> rowLinks = List.of();
    Sources.Owned have = Sources.Owned.NONE;
    /** The row the map and "open on map" follow (0: the easiest). */
    int picked;
    private CompletableFuture<ItemWiki.Info> wikiLoad;
    ItemWiki.Info wiki;
    private Double gain;
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
    private int[] view = new int[4];
    /** The large card's box, for the wheel. */
    private int[] box = new int[4];

    SearchCardPanel(SearchTab tab) {
        super(tab.menu);
        this.tab = tab;
        this.sections = new SearchCardSections(this, tab);
        this.thing = new SearchThingCard(this, tab);
        this.sources = new SearchCardRows(this);
    }

    /** The shown entry's {@link Entry#key}, or null. */
    String key() {
        return entry == null ? null : entry.key();
    }

    /** Shows this entry: its sources from where the player stands, its wiki page asked for, the map's marks replaced. */
    void select(Entry e) {
        entry = e;
        picked = 0;
        scroll = 0;
        marks = new MarkSet(KeptMarks.get());
        if (!e.item()) {
            have = Sources.Owned.NONE;
            rows = Things.rows(e, SearchTab.position());
            rowLinks = java.util.Collections.nCopies(rows.size(), null);
            wikiLoad = null;
            wiki = null;
            gain = null;
            marks.reset(rows, label());
            marks();
            return;
        }
        have = SearchCardSections.owned(e.name());
        Prices.Price price = e.tradable() ? Prices.of(e.name()) : null;
        rows = Sources.of(e, ItemIndex.get().places(), have, price, SearchTab.position());
        rowLinks = rows.stream().map(SearchThingCard::linked).toList();
        wikiLoad = ItemWiki.load(e.name());
        wiki = null;
        Shopping.Pick pick = e.kind() == io.github.anilken.wynnlabs.search.Kind.GEAR ? Shopping.find(e.name()) : null;
        gain = pick == null || pick.gain() <= 0 ? null : pick.gain();
        sections.select(e);
        marks.reset(rows, label());
        marks();
    }

    /** No entry to show (nothing listed): the card goes, and its marks unless the player kept them. */
    void clear() {
        if (entry == null) {
            return;
        }
        entry = null;
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
        if (entry == null) {
            return;
        }
        marks.label(label());
        shown = marks.marks(picked);
        Wyn.showMarks(shown);
    }

    /** How a row's marks are named: the wiki's words once it arrived. */
    private java.util.function.Function<Source, String> label() {
        ItemWiki.Info w = wiki;
        Entry e = entry;
        return s -> e.item() ? SourceText.row(s, w) : SearchThingCard.markText(e, s);
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
        if (entry != null) {
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
        if (entry == null) {
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
        view = new int[] {x + 1, top, w - 2, Math.max(0, footY - Theme.SP_4 - top)};
        int cw = w - 2 * pad - ScrollBar.W - Theme.SP_2;
        g.enableScissor(view[0], view[1], view[0] + view[2], view[1] + view[3]);
        int start = y + pad - scroll;
        int end = content(g, x + pad, start, cw, false, mx, my);
        g.disableScissor();
        int total = end - start + pad;
        scrollMax = Math.max(0, total - view[3]);
        scroll = Math.min(scroll, scrollMax);
        if (scrollMax > 0) {
            bar.place(x + w - ScrollBar.W - Theme.SP_2, view[1], view[3], view[3], total, scroll);
            bar.draw(g, mx, my);
        }
        view = new int[] {x, footY, w, actionsH};
        actions(g, x + pad, footY, w - 2 * pad, mx, my);
    }

    /** The drawer's content (a small window): everything in one column, the actions under the head. */
    int flow(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        if (entry == null) {
            return 0;
        }
        loadWiki();
        view = new int[] {x - Theme.SP_2, top, w + 2 * Theme.SP_2, bottom - top};
        return content(g, x, y, w, true, mx, my) - y;
    }

    private void loadWiki() {
        if (wiki == null && wikiLoad != null && wikiLoad.isDone()) {
            wiki = wikiLoad.getNow(ItemWiki.Info.EMPTY);
            SourceHint.forget(entry.name());        // the tooltip's line may now name a cost
            marks();
        }
    }

    private int content(GuiGraphics g, int x, int y, int w, boolean withActions, int mx, int my) {
        int cy = header(g, x, y, w, mx, my);
        if (withActions) {
            cy = actions(g, x, cy, w, mx, my) + Theme.SP_6;
        }
        cy = sources.draw(g, x, cy, w, mx, my) + Theme.SP_5;
        return entry.item() ? sections.draw(g, x, cy, w, mx, my) : thing.draw(g, entry, x, cy, w, mx, my);
    }

    /** A click area clipped to the card's visible part. */
    void click(int x, int y, int w, int h, Runnable run) {
        int top = Math.max(y, view[1]);
        int bottom = Math.min(y + h, view[1] + view[3]);
        if (bottom > top) {
            tab.click(x, top, w, bottom - top, run);
        }
    }

    /** Whether (mx, my) is on the card's visible part. */
    boolean over(int mx, int my) {
        return mx >= view[0] && mx < view[0] + view[2] && my >= view[1] && my < view[1] + view[3];
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
        if (entry.item()) {
            Kit.slot(g, x, y, slot, 0);
            var stack = tab.icons.of(entry);
            g.renderItem(stack, x + 4, y + 4);
            if (over(mx, my) && mx >= x && mx < x + slot && my >= y && my < y + slot) {
                menu.hits.add(new MenuHit(x, y, slot, slot, stack, null, null));
            }
        } else {
            SearchThingCard.picture(g, entry, x, y, slot);
        }
        int tx = x + slot + Theme.SP_4;
        int tw = w - slot - Theme.SP_4;
        // the name, a copy button (to paste into the Trade Market's search or chat), the build gain
        String gainText = gain == null ? null : L.t("wynnlabs.search.gain", dec(gain, 1));
        int gainW = gainText == null ? 0 : Type.META.width(font(), gainText) + 2 * Theme.SP_2 + Theme.SP_3;
        int copyW = Ui.CHIP_H + 2;
        String name = Type.HEADING.fit(font(), entry.name(), tw - copyW - Theme.SP_3 - gainW);
        Type.HEADING.draw(g, font(), name, tx, y, SearchResultsPanel.colour(entry));
        int cx = tx + Type.HEADING.width(font(), name) + Theme.SP_3;
        int cyb = Type.HEADING.onCaps(y, Ui.CHIP_H);
        boolean justCopied = entry.name().equals(copiedName) && System.currentTimeMillis() - copiedAt < 1200;
        Ui.iconButton(g, justCopied ? "check" : "copy", cx, cyb, false, over(mx, my) ? mx : -1, my);
        String copied = entry.name();
        click(cx, cyb, copyW, Ui.CHIP_H, () -> {
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(copied);
            copiedName = copied;
            copiedAt = System.currentTimeMillis();
        });
        tip(cx, cyb, copyW, Ui.CHIP_H, mx, my, L.t("wynnlabs.search.copy_name"));
        if (gainText != null) {
            Ui.seal(g, font(), gainText, cx + copyW + Theme.SP_3, Type.HEADING.onCaps(y, Ui.SEAL_H), Theme.SEAL_2);
        }
        int ly = y + Type.HEADING.lineHeight + Theme.SP_1;
        String tier = entry.tier() == null ? "" : SearchResultsPanel.words(entry.tier()) + " · ";
        String lv = entry.level() > 0 ? " · Lv " + entry.level() : "";
        String kind = entry.item() ? SearchResultsPanel.kindText(entry) : SearchThingCard.typeText(entry);
        Type.META.draw(g, font(), Type.META.fit(font(), tier + kind + lv, tw), tx, ly, Theme.INK_3);
        ly += Type.META.lineHeight;
        // the needs (a thing's status), on a line of their own
        Component needs = entry.item() ? sections.needs(entry) : SearchThingCard.status(entry);
        List<net.minecraft.util.FormattedCharSequence> need = font().split(needs, tw);
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
        boolean mapOk = easiest != null && GameState.current().running() && Wyn.up(Wyn.Feature.WAYPOINTS);
        List<Area> every = entry.item() ? List.of() : entry.places();
        String url = entry.item() || SearchThingCard.wiki(entry) ? ItemWiki.url(entry.name()) : null;
        boolean mob = entry.kind() == io.github.anilken.wynnlabs.search.Kind.MOB;
        boolean tracked = mob && io.github.anilken.wynnlabs.search.TrackedMobs.get().has(entry.name());
        String mobName = entry.name();
        Object[][] chips = {
            {tracked ? "check" : "target", L.t(tracked ? "wynnlabs.search.tracking" : "wynnlabs.search.track"), mob, (Runnable) () -> {
                boolean now = io.github.anilken.wynnlabs.search.TrackedMobs.get().toggle(mobName);
                menu.flash(L.t(now ? "wynnlabs.search.track_on" : "wynnlabs.search.track_off", mobName));
            }, tracked},
            {"map-2", L.t("wynnlabs.search.open_map"), mapOk, (Runnable) this::openMap},
            {"compass", L.t("wynnlabs.search.pin_all"), every.size() > 1,
                (Runnable) () -> pinAll(io.github.anilken.wynnlabs.search.SourceRank.nearest(every, SearchTab.position(), every.size()))},
            {"shopping-cart", L.t("wynnlabs.search.market"), entry.item() && entry.tradable(), (Runnable) this::market},
            {"arrow-up-right", L.t("wynnlabs.search.wiki"), url != null,
                (Runnable) () -> net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(menu, java.net.URI.create(url), true)},
        };
        int chipY = cy + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        for (Object[] c : chips) {
            if (!(boolean) c[2]) {
                continue;
            }
            String label = (String) c[1];
            int cw = Ui.iconChipWidth(font(), (String) c[0], label, false);
            if (bx + cw > x + w && bx > x) {
                bx = x;
                cy += Ui.BUTTON_H + Theme.SP_2;
                chipY = cy + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
            }
            if (g != null) {
                boolean on = c.length > 4 && (boolean) c[4];
                Ui.iconChip(g, font(), (String) c[0], label, false, bx, chipY, cw, on, over(mx, my) ? mx : -1, my);
                click(bx, chipY, cw, Ui.CHIP_H, (Runnable) c[3]);
            }
            bx += cw + Theme.SP_2;
        }
        return cy + Ui.BUTTON_H;
    }

    /** The Trade Market window the T key opens over a container, for this item: its listings and rolls. */
    private void market() {
        Entry e = entry;
        io.github.anilken.wynnlabs.model.Tier rarity;
        try {
            rarity = io.github.anilken.wynnlabs.model.Tier.valueOf(e.tier().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException ex) {
            rarity = io.github.anilken.wynnlabs.model.Tier.NORMAL;
        }
        var item = new io.github.anilken.wynnlabs.search.BoxMarket.Item(e.name(),
                io.github.anilken.wynnlabs.model.Slot.UNKNOWN, rarity, e.level(), e.klass(), 0, true);
        net.minecraft.client.Minecraft.getInstance().setScreen(new BoxScreen(new BoxScreen.Single(item, null, false), tab.icons.of(e), menu));
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

    /** A row's words: the item card's source text, a thing's place label. */
    String rowText(Source s) {
        return entry.item() ? SourceText.row(s, wiki) : SearchThingCard.rowText(entry, s);
    }

    void pin(Area a, String label) {
        String what = entry.name().length() > 32 ? entry.name().substring(0, 32) : entry.name();
        if (Waypoints.go(a.x(), a.y(), a.z(), what)) {
            menu.flash(L.t("wynnlabs.screen.guiding", what));
        } else {
            String at = "/compass at " + a.x() + " " + a.y() + " " + a.z();
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(at);
            menu.flash(L.t("wynnlabs.screen.copied", at));
        }
    }

    void pinAll(List<Area> places) {
        String what = entry.name().length() > 32 ? entry.name().substring(0, 32) : entry.name();
        List<int[]> points = new java.util.ArrayList<>();
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
        if (entry == null || tab.menu.layout.compact() || !in(x, y, box)) {
            return false;
        }
        scroll = Math.max(0, Math.min(scrollMax, scroll - 20 * (int) Math.signum(v)));
        return true;
    }
}
