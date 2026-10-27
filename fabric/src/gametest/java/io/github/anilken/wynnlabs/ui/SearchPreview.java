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
 * the map and the "Marked" list open, a tracked mob and the "Tracked" list, a place, the Stats
 * category and a stat, nothing found with "did you mean", and the filter menu. On a small window
 * ({@code -PpreviewSize=1380x960}) the card is the drawer.
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
        shot(ctx, out, "search-empty");
        search(ctx, "lucky rab");
        shot(ctx, out, "search-item");
        // every row of the card on the map, then the list of kept marks
        tab(ctx, t -> {
            t.card.toggleAll();
            t.menus.toggle(SearchMenus.MARKS);
        });
        shot(ctx, out, "search-marks");
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
        tab(ctx, t -> t.menus.toggle(SearchMenus.TRACKED));
        shot(ctx, out, "search-tracked");
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
