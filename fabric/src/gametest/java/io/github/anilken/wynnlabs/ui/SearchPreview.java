package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.search.ItemIndex;
import io.github.anilken.wynnlabs.search.SearchCategory;
import io.github.anilken.wynnlabs.search.TrackedMobs;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The search tab in the J menu preview: the item index read from a game folder's Wynntils cache
 * ({@code -PpreviewWynntils=<folder>}, read only) or, without one, from the trimmed test copies.
 * Shots: the catalogue before anything is typed, an item ("lucky rab"), its card with every row on
 * the map and the "Marked" list open, a tracked mob and the "Tracked" list, the "Tracked items"
 * list, a place, the Stats category and a stat, nothing found with "did you mean", and the filter
 * menu. On a small window ({@code -PpreviewSize=1380x960}) the card is the drawer.
 */
final class SearchPreview {

    private SearchPreview() {}

    /** Starts reading the index before the menu does (the index is read once a session). */
    static void loadIndex() throws IOException {
        String dir = System.getProperty("wynnlabs.previewWynntils");
        Path game;
        if (dir != null) {
            game = Path.of(dir);
        } else {
            game = Files.createTempDirectory("wynnlabs-search-preview");
            Path cache = Files.createDirectories(game.resolve("wynntils/cache"));
            try (Stream<Path> files = Files.list(Fixtures.root().getParent().resolve("search/cache"))) {
                for (Path f : files.toList()) {
                    Files.copy(f, cache.resolve(f.getFileName().toString()));
                }
            }
        }
        ItemIndex.startLoading(game, null);
    }

    static void shots(ClientGameTestContext ctx, Path out) {
        ctx.waitFor(mc -> ItemIndex.get().missing() == null || !"loading".equals(ItemIndex.get().missing()), 20 * 60);
        ctx.getInput().setCursorPos(2, 2);
        ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).openTab(RoadmapScreen.TAB_SEARCH));
        ctx.waitTicks(20);
        // the empty page: before any search (the road map's missing pieces), after a few, and opened
        ctx.runOnClient(mc -> io.github.anilken.wynnlabs.search.RecentSearches.get().clear());
        tab(ctx, t -> t.box.setValue(""));
        shot(ctx, out, "search-empty-new");
        ctx.runOnClient(mc -> {
            var r = io.github.anilken.wynnlabs.search.RecentSearches.get();
            r.add("mob:Tumbleweed");
            r.add("Bear Mask");
            r.add("Lucky Rabbit's Foot");
        });
        tab(ctx, t -> { });
        shot(ctx, out, "search-empty");
        tab(ctx, t -> SearchTab.expanded = true);
        shot(ctx, out, "search-empty-more");
        tab(ctx, t -> SearchTab.expanded = false);
        search(ctx, "lucky rab");
        shot(ctx, out, "search-item");
        // every row of the card on the map, then the list of kept marks
        tab(ctx, t -> {
            t.card.toggleAll();
            t.menus.toggle(SearchMenus.MARKS);
        });
        shot(ctx, out, "search-marks");
        // "Remove all" from the list's header: the undo chip, then undone
        tab(ctx, t -> t.menus.pick("m:clear", false));
        shot(ctx, out, "search-marks-undo");
        tab(ctx, t -> t.menus.undoNow());
        // the menu closed and opened again: the kept marks are still listed
        ctx.setScreen(() -> RoadmapScreen.on(RoadmapScreen.TAB_SEARCH));
        ctx.waitForScreen(RoadmapScreen.class);
        ctx.waitTicks(10);
        shot(ctx, out, "search-marks-kept");
        tab(ctx, t -> {
            t.menus.open = SearchMenus.NONE;
            t.card.clearMarks();
        });
        // a mob tracked from its card, and the tracked list
        tab(ctx, t -> {
            if (!TrackedMobs.get().has("Tumbleweed")) {
                TrackedMobs.get().toggle("Tumbleweed");
            }
            t.show("mob:Tumbleweed");
        });
        ctx.waitTicks(10);
        shot(ctx, out, "search-mob-tracked");
        // two items tracked as well: their own chip and list, each row with the item's picture
        ctx.runOnClient(mc -> {
            for (String item : new String[] {"Lucky Rabbit's Foot", "Bear Mask"}) {
                if (!TrackedMobs.items().has(item)) {
                    TrackedMobs.items().toggle(item);
                }
            }
        });
        tab(ctx, t -> t.menus.toggle(SearchMenus.TRACKED));
        shot(ctx, out, "search-tracked");
        tab(ctx, t -> t.menus.toggle(SearchMenus.ITEMS));
        shot(ctx, out, "search-tracked-items");
        tab(ctx, t -> t.menus.open = SearchMenus.NONE);
        search(ctx, "cinfras");
        shot(ctx, out, "search-place");
        // the Stats category: its list, then one stat's items
        search(ctx, "");
        tab(ctx, t -> t.pickCategory(SearchCategory.STAT));
        shot(ctx, out, "search-stats");
        tab(ctx, t -> t.pickStat("manaSteal"));
        shot(ctx, out, "search-stat");
        // nothing found: the mobs category hides the ingredient ("did you mean"), then a typo
        tab(ctx, t -> t.pickCategory(SearchCategory.MOB));
        search(ctx, "lucky rab");
        shot(ctx, out, "search-none-filtered");
        tab(ctx, t -> t.pickCategory(SearchCategory.ALL));
        search(ctx, "luckyrabit");
        shot(ctx, out, "search-none-typo");
        // the filter menu on the items category
        search(ctx, "");
        tab(ctx, t -> {
            t.pickCategory(SearchCategory.ITEM);
            t.menus.toggle(SearchMenus.FILTER);
        });
        shot(ctx, out, "search-filter");
        tab(ctx, t -> {
            t.menus.open = SearchMenus.NONE;
            t.pickCategory(SearchCategory.ALL);
        });
        mobDrops(ctx, out);
    }

    /**
     * A mob with many actions and drops (Withered Quasinobility, only in a real Wynntils cache):
     * the wrapped action rows inside the card, and its drops with icons and prices seeded by hand
     * (every one but the last; Wynnventory is not in the preview, so nothing is requested), the second tracked.
     */
    private static void mobDrops(ClientGameTestContext ctx, Path out) {
        String key = "mob:Withered Quasinobility";
        if (ItemIndex.get().byKey(key) == null) {
            return;
        }
        ctx.runOnClient(mc -> {
            try {
                Class<?> c = io.github.anilken.wynnlabs.market.Prices.class;
                set(c, "installed", Boolean.TRUE);
                set(c, "loaded", true);
                set(c, "unavailable", true);            // onTick stays away: no reads, no requests
                java.lang.reflect.Field f = c.getDeclaredField("PRICES");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                var prices = (java.util.Map<String, io.github.anilken.wynnlabs.market.Prices.Price>) f.get(null);
                var drops = ItemIndex.get().byKey(key).links(io.github.anilken.wynnlabs.search.Entry.ITEM);
                long now = System.currentTimeMillis();
                int[] values = {38, 410, 2_900, 17_500, 96};
                for (int i = 0; i < drops.size() - 1; i++) {
                    prices.put(drops.get(i).name(), new io.github.anilken.wynnlabs.market.Prices.Price(values[i % values.length],
                            null, null, null, null, now, null, null));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
            if (!TrackedMobs.get().has("Withered Quasinobility")) {
                TrackedMobs.get().toggle("Withered Quasinobility");
            }
            // the second drop tracked: its row's toggle shows the check
            var drops = ItemIndex.get().byKey(key).links(io.github.anilken.wynnlabs.search.Entry.ITEM);
            if (drops.size() > 1 && !TrackedMobs.items().has(drops.get(1).name())) {
                TrackedMobs.items().toggle(drops.get(1).name());
            }
        });
        tab(ctx, t -> t.show(key));
        ctx.waitTicks(10);
        shot(ctx, out, "search-mob-drops");
        tab(ctx, t -> t.card.scroll = 10_000);
        shot(ctx, out, "search-mob-drops-end");
    }

    private static void set(Class<?> c, String name, Object value) throws ReflectiveOperationException {
        java.lang.reflect.Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void tab(ClientGameTestContext ctx, Consumer<SearchTab> run) {
        ctx.runOnClient(mc -> run.accept(((RoadmapScreen) mc.screen).search));
        ctx.waitTicks(10);
    }

    private static void search(ClientGameTestContext ctx, String text) {
        ctx.runOnClient(mc -> {
            SearchTab t = ((RoadmapScreen) mc.screen).search;
            t.box.setValue(text);
            t.selName = null;
            if ((text.equals("lucky rab") && t.category == SearchCategory.ALL || text.equals("cinfras"))
                    && mc.screen instanceof RoadmapScreen s && s.layout.compact()) {
                t.show(text.equals("lucky rab") ? "Lucky Rabbit's Foot" : text.equals("cinfras") ? "place:Cinfras" : text);
                t.box.setValue(text);
            }
        });
        ctx.waitTicks(25);                          // the worker answers, the card picks its sources
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }
}
