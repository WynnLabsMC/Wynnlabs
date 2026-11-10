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
 * menu; then an item with what it is used for (Koi Oil's own row and card, its crafts with the
 * ring chosen, its mount feed, an ingredient's professions, gear with no use), on an index with
 * the materials and a station added and a stock made up for it. On a small window ({@code -PpreviewSize=1380x960}) the card is the drawer.
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
        uses(ctx, out);
    }

    /**
     * What an item is used for. The index is read again with the files of {@code search/uses}
     * over the trimmed cache (Koi Oil, the materials it is crafted with and a Jeweling station;
     * only from here on, so the shots before it keep their counts), the stock is made up (24 Koi
     * Oil in the bag and the bank, 26 Cobalt Gem in the bank, 12 Dark Planks) and two prices are
     * seeded by hand. Each shot's texts and click areas are checked for overlaps.
     */
    private static void uses(ClientGameTestContext ctx, Path out) {
        if (System.getProperty("wynnlabs.previewWynntils") == null) {
            try {
                Path game = Files.createTempDirectory("wynnlabs-uses-preview");
                Path cache = Files.createDirectories(game.resolve("wynntils/cache"));
                for (String dir : new String[] {"search/cache", "search/uses"}) {
                    try (Stream<Path> files = Files.list(Fixtures.root().getParent().resolve(dir))) {
                        for (Path f : files.toList()) {
                            Files.copy(f, cache.resolve(f.getFileName().toString()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
                ItemIndex index = ItemIndex.read(cache, java.util.List.of());
                ctx.runOnClient(mc -> {
                    try {
                        set(ItemIndex.class, "current", index);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        ctx.runOnClient(mc -> {
            java.util.Map<String, int[]> stock = java.util.Map.of("Koi Oil", new int[] {16, 8}, "Cobalt Gem", new int[] {0, 26},
                    "Dark Planks", new int[] {12, 0}, "Lucky Rabbit's Foot", new int[] {3, 0});
            UseStock.preview = stock::get;
            try {
                Class<?> c = io.github.anilken.wynnlabs.market.Prices.class;
                set(c, "installed", Boolean.TRUE);
                set(c, "loaded", true);
                set(c, "unavailable", true);            // onTick stays away: no reads, no requests
                java.lang.reflect.Field f = c.getDeclaredField("PRICES");
                f.setAccessible(true);
                @SuppressWarnings("unchecked")
                var prices = (java.util.Map<String, io.github.anilken.wynnlabs.market.Prices.Price>) f.get(null);
                long now = System.currentTimeMillis();
                prices.put("Koi Oil T1", new io.github.anilken.wynnlabs.market.Prices.Price(38, null, null, null, null, now, null, null));
                prices.put("Cobalt Gem T1", new io.github.anilken.wynnlabs.market.Prices.Price(1_250, null, null, null, null, now, null,
                        null));
                Object[][] more = {{"Cobalt Rod T8", 9_800}, {"Silver Rod T7", 4_200}, {"Blue Ward", 310_000}, {"Yellow Ward", 262_000},
                    {"Red Ward", 240_000}, {"Nii Rune", 2_100}, {"Broken Decrepit Sewers Key", 600}, {"Corrupted Decrepit Sewers Key", 11_000}};
                for (Object[] p : more) {
                    prices.put((String) p[0], new io.github.anilken.wynnlabs.market.Prices.Price((Integer) p[1], null, null, null, null, now,
                            null, null));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        boolean compact = ctx.computeOnClient(mc -> ((RoadmapScreen) mc.screen).layout.compact());
        tab(ctx, t -> t.showUses("Koi Oil"));
        if (compact) {
            usesShot(ctx, out, "search-uses-list", true);   // a small window: the list, then a row's card in the drawer
            use(ctx, "Koi Oil", true);
        }
        // the item's own row first, chosen: its card says how to get it
        // (the drawer lies over the page, so only the shots without it are checked)
        usesShot(ctx, out, "search-uses", !compact);
        use(ctx, "use:Koi Oil:ring", compact);
        usesShot(ctx, out, "search-uses-ring", !compact);
        use(ctx, "use:Koi Oil:relik", compact);
        usesShot(ctx, out, "search-uses-relik", !compact);
        use(ctx, "use:Koi Oil:feed", compact);
        usesShot(ctx, out, "search-uses-mount", !compact);
        tab(ctx, t -> t.showUses("Lucky Rabbit's Foot"));
        if (compact) {
            usesShot(ctx, out, "search-uses-ingredient-list", true);
        }
        use(ctx, "use:Lucky Rabbit's Foot:prof-armouring", compact);
        usesShot(ctx, out, "search-uses-ingredient", !compact);
        // gear nothing is crafted from: the item alone, with its card
        tab(ctx, t -> t.showUses("Bear Mask"));
        if (compact) {
            usesShot(ctx, out, "search-uses-gear-list", true);
            use(ctx, "Bear Mask", true);
        }
        usesShot(ctx, out, "search-uses-gear", !compact);
        known(ctx, out, compact);
        if (!compact) {
            // an item's own card: its "Uses" section and "All uses", at the card's end
            tab(ctx, t -> t.show("Lucky Rabbit's Foot"));
            tab(ctx, t -> t.card.scroll = 10_000);
            usesShot(ctx, out, "search-uses-section", true);
        }
        ctx.runOnClient(mc -> UseStock.preview = null);
    }

    /**
     * Items the index does not list: a gathering tool (the game's tools made up as the Crafting
     * tab's preview does, a Silver Rod T7 in the bag), a ward, a raid rune, and a crafted scroll
     * whose making is searched for as in the box window's preview.
     */
    private static void known(ClientGameTestContext ctx, Path out, boolean compact) {
        ctx.runOnClient(mc -> {
            String[] mats = {"Stone", "Copper", "Granite", "Gold", "Sandstone", "Iron", "Silver", "Cobalt", "Kanderstone", "Diamond",
                "Molten", "Voidstone", "Dernic"};
            Object[][] kinds = {{"mining", "Pickaxe", net.minecraft.world.item.Items.IRON_PICKAXE},
                {"fishing", "Rod", net.minecraft.world.item.Items.FISHING_ROD}};
            java.util.List<io.github.anilken.wynnlabs.model.GatherTool> all = new java.util.ArrayList<>();
            for (Object[] k : kinds) {
                for (int t = 1; t <= 13; t++) {
                    all.add(new io.github.anilken.wynnlabs.model.GatherTool(mats[t - 1] + " " + k[1] + " T" + t, (String) k[0], t,
                            t == 1 ? 1 : (t - 1) * 10, 1 + t, -1, 100 + 50 * t,
                            new net.minecraft.world.item.ItemStack((net.minecraft.world.item.Item) k[2])));
                }
            }
            CraftTools.previewTools = all;
            CraftTools.previewHeld = java.util.List.of(new io.github.anilken.wynnlabs.model.GatherTool("Silver Rod T7", "fishing", 7, 60,
                    8, 210, 450, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FISHING_ROD)));
        });
        for (String[] item : new String[][] {{"Cobalt Rod T8", "tool"}, {"Blue Ward", "ward"}, {"Nii Rune", "rune"}}) {
            tab(ctx, t -> t.showUses(item[0]));
            if (compact) {
                usesShot(ctx, out, "search-uses-" + item[1] + "-list", true);
                use(ctx, io.github.anilken.wynnlabs.search.InfoItems.key(item[0]), true);
            }
            usesShot(ctx, out, "search-uses-" + item[1], !compact);
        }
        // a crafted scroll, as the box window's preview makes one
        ctx.runOnClient(mc -> CraftingTab.previewIngredients = CraftPreview.ingredients());
        java.util.Map<String, io.github.anilken.wynnlabs.crafting.Ingredient> by = new java.util.HashMap<>();
        CraftPreview.ingredients().forEach(i -> by.put(i.name(), i));
        io.github.anilken.wynnlabs.crafting.HowMade.Target scroll = BoxMadePreview.target("Scroll of Dusk", "scroll", "Scroll80-83", 2, 1, by,
                "Essence Of Dusk", "Essence Of Dusk", "Haleva Plant", "Altered Ash");
        tab(ctx, t -> t.showMade(scroll, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER)));
        ctx.waitFor(mc -> {
            SearchTab t = ((RoadmapScreen) mc.screen).search;
            return t.made == null || t.made.done();
        }, 20 * 20);
        ctx.waitTicks(5);
        if (compact) {
            usesShot(ctx, out, "search-uses-crafted-list", true);
            use(ctx, MadeCard.key(scroll), true);
        }
        usesShot(ctx, out, "search-uses-crafted", !compact);
        tab(ctx, SearchTab::clearUses);
        ctx.runOnClient(mc -> {
            CraftTools.previewTools = null;
            CraftTools.previewHeld = null;
        });
    }

    /** Chooses a use's row: its card beside the list, or in the drawer on a small window. */
    private static void use(ClientGameTestContext ctx, String key, boolean compact) {
        tab(ctx, t -> {
            t.selName = key;
            if (compact) {
                t.openDrawer(key);
            }
        });
        ctx.waitTicks(compact ? 15 : 5);                // the drawer slides in
    }

    /** A shot, and that frame's layout: no text over another, no two click areas that cross without one holding the other. */
    private static void usesShot(ClientGameTestContext ctx, Path out, String name, boolean check) {
        if (!check) {
            shot(ctx, out, name);
            return;
        }
        ctx.runOnClient(mc -> LayoutCheck.start());
        ctx.waitTicks(1);
        java.util.List<LayoutCheck.Drawn> drawn = ctx.computeOnClient(mc -> LayoutCheck.stop());
        java.util.List<String> found = new java.util.ArrayList<>(LayoutCheck.textOverlaps(drawn));
        found.removeIf(s -> s.contains("\"_\""));         // the focused box's caret blinks over its hint
        java.util.List<int[]> clicks = ctx.computeOnClient(mc -> {
            java.util.List<int[]> rects = new java.util.ArrayList<>();
            for (SearchTab.Click c : ((RoadmapScreen) mc.screen).search.clicks) {
                rects.add(c.rect());
            }
            return rects;
        });
        for (int i = 0; i < clicks.size(); i++) {
            for (int j = i + 1; j < clicks.size(); j++) {
                int[] a = clicks.get(i);
                int[] b = clicks.get(j);
                boolean cross = a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
                if (cross && !holds(a, b) && !holds(b, a)) {
                    found.add("click areas cross: " + java.util.Arrays.toString(a) + " and " + java.util.Arrays.toString(b));
                }
            }
        }
        System.out.println("[wynnlabs-layout] " + name + " at " + PreviewSize.WIDTH + "x" + PreviewSize.HEIGHT + ": "
                + (found.isEmpty() ? "no overlaps (" + clicks.size() + " click areas)" : found));
        shot(ctx, out, name);
    }

    /** Whether {@code a} holds all of {@code b} (a row and the button on it: the button, added later, takes its own click). */
    private static boolean holds(int[] a, int[] b) {
        return b[0] >= a[0] && b[1] >= a[1] && b[0] + b[2] <= a[0] + a[2] && b[1] + b[3] <= a[1] + a[3];
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
