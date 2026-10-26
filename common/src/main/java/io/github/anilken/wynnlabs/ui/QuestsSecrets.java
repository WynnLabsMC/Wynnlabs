package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.quests.Content;
import io.github.anilken.wynnlabs.quests.ContentType;
import io.github.anilken.wynnlabs.quests.Discoveries;
import io.github.anilken.wynnlabs.quests.DiscoveryBook;
import io.github.anilken.wynnlabs.quests.StepPlaces;
import io.github.anilken.wynnlabs.quests.Waypoints;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The secret discoveries as a kind of the quests tab: the shipped guides ({@link Discoveries}) as
 * the rail's rows (what you have not found first, in the chosen order; then "Bulunan"), their
 * order menu (the sorts and "within my level"), how many you found, and their clicks: the order,
 * a step's way, "Buldum".
 */
final class QuestsSecrets extends MenuPaint {

    private final QuestsTab tab;

    QuestsSecrets(RoadmapScreen menu, QuestsTab tab) {
        super(menu);
        this.tab = tab;
    }

    // ---- what is found ----

    /** The names found: Wynntils' list, the open book's, the content book read, and the ones ticked by hand. */
    static Set<String> found() {
        return QuestsCache.kept("secrets-found", 1000, () -> {
            Set<String> s = new HashSet<>(Discoveries.foundInGame());
            for (Content.Line l : Content.lines(ContentType.SECRET_DISCOVERY)) {
                if (l.done()) {
                    s.add(Discoveries.key(l.name()));
                    s.add(l.name());
                }
            }
            return s;
        });
    }

    static boolean done(Discoveries.Entry e) {
        return Discoveries.done(e, found(), GameState.current().characterId());
    }

    /** The guide's entry for a row's id. */
    static Discoveries.Entry entry(String id) {
        return id == null ? null : Discoveries.get(id);
    }

    /** Whether the book has never been read: nothing found anywhere yet. */
    static boolean unread() {
        return found().isEmpty() && !DiscoveryBook.seen() && Content.readAt(ContentType.SECRET_DISCOVERY) == 0;
    }

    /** How many are left within your level (the kind menu's count). */
    static int left() {
        return QuestsCache.kept("secrets-left", 1000, () -> {
            int level = GameState.current().level();
            int n = 0;
            for (Discoveries.Entry e : Discoveries.all()) {
                if (e.minLevel() <= level && !done(e)) {
                    n++;
                }
            }
            return n;
        });
    }

    /** "3 / 121 bulundu". */
    static String progress() {
        int done = 0;
        List<Discoveries.Entry> all = Discoveries.all();
        for (Discoveries.Entry e : all) {
            done += done(e) ? 1 : 0;
        }
        return L.t("wynnlabs.discoveries.progress", done, all.size());
    }

    // ---- the rows ----

    /** A discovery as a rail line: its place is where it is found. */
    static Content.Line line(Discoveries.Entry e, boolean done) {
        Content.Place p = new Content.Place(ContentType.SECRET_DISCOVERY, e.id(), e.minLevel(), e.x(), e.y(), e.z(), e.lore(), "", "",
                List.of());
        return new Content.Line(ContentType.SECRET_DISCOVERY, e.id(), e.minLevel(), done ? "COMPLETED" : "AVAILABLE", e.lore(), p);
    }

    /** Not found first in the chosen order (above your level last unless hidden), then "Bulunan · N". */
    List<QuestsRail.Row> rows(String query) {
        GameState gs = GameState.current();
        double[] at = gs.position();
        int level = gs.level();
        boolean within = Discoveries.withinLevel();
        String q = query.toLowerCase(Locale.ROOT);
        List<QuestsRail.Row> open = new ArrayList<>();
        List<QuestsRail.Row> later = new ArrayList<>();
        List<QuestsRail.Row> found = new ArrayList<>();
        for (Discoveries.Entry e : Discoveries.sorted(Discoveries.all(), Discoveries.sort(), at[0], at[1])) {
            if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q) && !e.region().toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            boolean done = done(e);
            QuestsRail.Row r = new QuestsRail.Row(e.id(), null, null, line(e, done));
            if (done) {
                found.add(r);
            } else if (e.minLevel() > level) {
                if (!within) {
                    later.add(r);
                }
            } else {
                open.add(r);
            }
        }
        List<QuestsRail.Row> out = new ArrayList<>(open);
        out.addAll(later);
        if (!found.isEmpty()) {
            out.add(new QuestsRail.Row(null, L.t("wynnlabs.content.found_head", found.size()), null, null));
            out.addAll(found);
        }
        return out;
    }

    /** A row's tooltip: the name, level and region, the lore. */
    static List<Component> tip(Discoveries.Entry e) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(e.name()).withStyle(ChatFormatting.AQUA).withStyle(UiFont::bold));
        tip.add(Component.literal(ContentType.SECRET_DISCOVERY.label() + " · Lv " + e.minLevel() + " · " + e.region()).withStyle(ChatFormatting.GRAY));
        for (String l : io.github.anilken.wynnlabs.advice.Advice.wrap(e.lore(), 48)) {
            tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }
        return tip;
    }

    // ---- the order menu ----

    /** The order chip's label: the sort in use. */
    static String sortName() {
        return L.t(Discoveries.SORT_KEYS[Discoveries.sort().ordinal()]);
    }

    /** "Önerilen ▾" on this kind: the sorts, then "within my level" as a choice that stays ticked. */
    static List<UiMenu.Item> menuItems() {
        UiMenu.Items items = new UiMenu.Items();
        for (int i = 0; i < Discoveries.SORT_KEYS.length; i++) {
            items.add(UiMenu.Item.of("#discsort:" + i, null, L.t(Discoveries.SORT_KEYS[i]), null, Discoveries.sort().ordinal() == i));
        }
        items.divide().add(UiMenu.Item.of("#disclevel", "check", L.t("wynnlabs.discoveries.within_level", GameState.current().level()),
                null, Discoveries.withinLevel()));
        return items.list;
    }

    // ---- the clicks ----

    /** A {@code #disc} click (the order, a way, "Buldum"); false when it is none. */
    boolean action(String c) {
        if (c.startsWith("#discsort:")) {
            Settings.setNumber("discoverySort", Integer.parseInt(c.substring(10)));
            tab.railScroll = 0;
            tab.filterOpen = false;
        } else if (c.equals("#disclevel")) {
            Settings.setNumber("discoveryWithinLevel", Discoveries.withinLevel() ? 0 : 1);
            tab.railScroll = 0;
            tab.filterOpen = false;
        } else if (c.startsWith("#disctick:")) {
            String id = c.substring(10);
            String character = GameState.current().characterId();
            boolean now = !Discoveries.ticked(character, id);
            Discoveries.tick(character, id, now);
            QuestsCache.forget();
            menu.flash(L.t(now ? "wynnlabs.discoveries.ticked" : "wynnlabs.discoveries.unticked"));
        } else if (c.equals("#discfound")) {
            menu.flash(L.t("wynnlabs.discoveries.found_by_book"));
        } else if (c.startsWith("#discpin:")) {
            String[] p = c.substring(9).split(":", 2);
            Discoveries.Entry e = p.length > 1 ? Discoveries.get(p[1]) : null;
            if (e != null) {
                // the guide's steps (the wiki's newer ones when it changed), from the clicked one on
                List<int[]> pts = new ArrayList<>();
                List<String> labels = new ArrayList<>();
                StepPlaces.from(marks(e), Integer.parseInt(p[0]), pts, labels, e.name());
                if (pts.isEmpty()) {
                    pts.add(new int[] {e.x(), e.y(), e.z()});
                    labels.add(e.name());
                }
                int n = Waypoints.all(pts, labels);
                menu.flash(n > 1 ? L.n("wynnlabs.quests.pins_added", n) : n == 1 ? L.t("wynnlabs.screen.guiding", labels.get(0))
                        : L.t("wynnlabs.home.mark_failed"));
            }
        } else {
            return false;
        }
        return true;
    }

    /** Every place of a discovery's steps (both of "here or there"), kept a while. */
    static List<List<StepPlaces.Mark>> marks(Discoveries.Entry e) {
        return QuestsCache.kept("disc-marks|" + e.id(), 5000,
                () -> StepPlaces.of(Discoveries.guide(e).steps(), new int[] {e.x(), e.y(), e.z()}));
    }

    /** Opens the tab on the secret discoveries, {@code id} picked (null: the first). */
    void show(String id) {
        tab.questType = ContentType.SECRET_DISCOVERY;
        tab.pickedType = id == null ? null : ContentType.SECRET_DISCOVERY;
        tab.questSelected = id;
        tab.railScroll = 0;
        tab.questDetailTab = 0;
    }
}
