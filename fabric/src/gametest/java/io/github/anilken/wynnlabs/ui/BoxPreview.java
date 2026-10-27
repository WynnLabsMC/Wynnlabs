package io.github.anilken.wynnlabs.ui;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Listings;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.model.Tier;
import io.github.anilken.wynnlabs.search.BoxMarket;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The box contents window without the game ({@code -PpreviewOnly=box}): the recorded Shaman's
 * unique leggings box (Lv 66-70) over the inventory, with canned Wynnventory answers for some of
 * its items and no network; shots with live lookups on and off, a listing kept open, an item with
 * no listings and one still loading, the build filter, the second page, the unidentified filter,
 * the price sort, GUI scales 2 and 4; the window for one rolled item (with your own rolls, a
 * listing open, the stats view, too few listings), for one ingredient (no rolls: the one-line
 * table and where it comes from), and a box whose contents are not read yet. The item index is
 * read from the search fixtures before the first shot.
 */
public class BoxPreview implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!"box".equals(System.getProperty("wynnlabs.previewOnly"))) {
            return;
        }
        Path out = Path.of(System.getProperty("wynnlabs.preview", "preview"));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            Files.createDirectories(out);
            SearchPreview.loadIndex();
            ctx.getInput().resizeWindow(PreviewSize.WIDTH, PreviewSize.HEIGHT);
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                try {
                    FixtureState.load(Fixtures.root().resolve("shaman-44")).install(System.getProperty("wynnlabs.previewLang", "tr"));
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                Wyn.clearDown();
                canned("Atoll", 1, "listings-atoll-p1.json");
                canned("Canopy", 1, "listings-canopy.json");
                live(false);
            });
            ctx.waitFor(mc -> !"loading".equals(io.github.anilken.wynnlabs.search.ItemIndex.get().missing()), 20 * 60);
            ItemView.Box box = new ItemView.Box(Slot.LEGGINGS, Tier.UNIQUE, 66, 70);
            ctx.runOnClient(mc -> mc.setScreen(new BoxScreen(box, new InventoryScreen(mc.player))));
            ctx.getInput().setCursorPos(2, 2);
            ctx.waitTicks(5);
            ctx.runOnClient(mc -> ((BoxScreen) mc.screen).selected = "Atoll");
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-offline");
            ctx.runOnClient(mc -> live(true));
            ctx.waitTicks(3);
            shot(ctx, out, "box-window");
            hover(ctx, out, "box");
            ctx.runOnClient(mc -> {
                BoxScreen b = (BoxScreen) mc.screen;
                b.right.open = 1;
                b.right.sel = b.right.open;
            });
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-open");
            ctx.runOnClient(mc -> ((BoxScreen) mc.screen).right.reset());
            ctx.runOnClient(mc -> {
                cannedEmpty("Flame Beaters");
                ((BoxScreen) mc.screen).selected = "Flame Beaters";
            });
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-none");
            ctx.runOnClient(mc -> ((BoxScreen) mc.screen).selected = "Ironclad");
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-loading");
            ctx.runOnClient(mc -> {
                // no answer from the service (a rate limit, a timeout): not the same as "no listings"
                try {
                    Method f = Listings.class.getDeclaredMethod("finished", String.class, int.class, boolean.class);
                    f.setAccessible(true);
                    f.invoke(null, "Leg of the Scared", 1, false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                ((BoxScreen) mc.screen).selected = "Leg of the Scared";
            });
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-failed");
            ctx.runOnClient(mc -> {
                ((BoxScreen) mc.screen).selected = "Atoll";
                ((BoxScreen) mc.screen).buildOnly = true;
            });
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-build");
            ctx.runOnClient(mc -> ((BoxScreen) mc.screen).buildOnly = false);
            ctx.runOnClient(mc -> canned("Atoll", 2, "listings-atoll-p2.json"));     // "Daha fazla" fetched
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-page2");
            ctx.runOnClient(mc -> ((BoxScreen) mc.screen).filter = BoxMarket.Filter.UNID);
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-unid");
            ctx.runOnClient(mc -> {
                ((BoxScreen) mc.screen).filter = BoxMarket.Filter.ALL;
                ((BoxScreen) mc.screen).sort = BoxMarket.Sort.PRICE;
            });
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-price");
            for (int scale : new int[] {2, 4}) {
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(scale);
                    mc.resizeDisplay();
                });
                ctx.waitTicks(5);
                shot(ctx, out, "box-window-gui" + scale);
            }
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                BoxMarket.Item atoll = new BoxMarket.Item("Atoll", Slot.LEGGINGS, Tier.UNIQUE, 68, Klass.NONE, 0, true);
                List<BoxMarket.StatRoll> own = List.of(new BoxMarket.StatRoll("rawHealth", 104.0, 80.0),
                        new BoxMarket.StatRoll("waterDamage", 12.0, 55.0), new BoxMarket.StatRoll("manaRegen", 4.0, 60.0),
                        new BoxMarket.StatRoll("walkSpeed", 9.0, 35.0));
                mc.setScreen(new BoxScreen(new BoxScreen.Single(atoll, 72.4f, false, own), new ItemStack(Items.LEATHER_LEGGINGS),
                        new InventoryScreen(mc.player)));
            });
            ctx.waitTicks(5);
            shot(ctx, out, "box-window-item");
            hover(ctx, out, "item");
            ara(ctx, out);
            ctx.runOnClient(mc -> {
                BoxScreen b = (BoxScreen) mc.screen;
                b.right.open = 0;
                b.right.sel = b.right.open;
            });
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-item-open");
            ctx.runOnClient(mc -> ((BoxScreen) mc.screen).tab = 1);
            ctx.waitTicks(3);
            shot(ctx, out, "box-window-item-stats");
            ctx.runOnClient(mc -> {
                BoxMarket.Item canopy = new BoxMarket.Item("Canopy", Slot.LEGGINGS, Tier.UNIQUE, 69, Klass.NONE, 0, true);
                mc.setScreen(new BoxScreen(new BoxScreen.Single(canopy, 61f, false), new ItemStack(Items.LEATHER_LEGGINGS),
                        new InventoryScreen(mc.player)));
            });
            ctx.waitTicks(5);
            shot(ctx, out, "box-window-item-few");
            ctx.runOnClient(mc -> {
                canned("Accursed Effigy", 1, "listings-effigy.json");
                BoxMarket.Item effigy = new BoxMarket.Item("Accursed Effigy", Slot.UNKNOWN, Tier.NORMAL, 76, Klass.NONE, 0, true);
                mc.setScreen(new BoxScreen(new BoxScreen.Single(effigy, null, false), new ItemStack(Items.POTION),
                        new InventoryScreen(mc.player)));
            });
            ctx.waitTicks(5);
            shot(ctx, out, "box-window-material");
            for (int scale : new int[] {2, 4}) {
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(scale);
                    mc.resizeDisplay();
                });
                ctx.waitTicks(5);
                shot(ctx, out, "box-window-material-gui" + scale);
            }
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(3);
                mc.resizeDisplay();
                mc.setScreen(new BoxScreen(new ItemView.Box(Slot.RING, Tier.MYTHIC, 1, 2), new InventoryScreen(mc.player)));
            });
            ctx.waitTicks(5);
            shot(ctx, out, "box-window-unread");
            ctx.runOnClient(mc -> mc.setScreen(null));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** A canned answer through the store's own reader, as if Wynnventory had sent it. */
    private static void canned(String name, int page, String file) {
        try {
            String text = Files.readString(Fixtures.root().resolve("wynnventory").resolve(file), StandardCharsets.UTF_8);
            Method m = Listings.class.getDeclaredMethod("accept", String.class, int.class, JsonObject.class);
            m.setAccessible(true);
            JsonObject body = new Gson().fromJson(text, JsonObject.class);
            // the answers were recorded at 12:05 UTC: their times moved to now, so the ages read as minutes
            long shift = System.currentTimeMillis() - java.time.Instant.parse("2026-10-24T12:05:00Z").toEpochMilli();
            for (var el : body.getAsJsonArray("data")) {
                JsonObject l = el.getAsJsonObject();
                long at = java.time.Instant.parse(l.get("timestamp").getAsString()).toEpochMilli() + shift;
                l.addProperty("timestamp", java.time.Instant.ofEpochMilli(at).toString());
            }
            m.invoke(null, name, page, body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The mouse moved over the listings, a row a frame for 120 frames: the box window's mean and
     * worst drawing time, written to box-hover.txt (the hover check).
     */
    private static void hover(ClientGameTestContext ctx, Path out, String name) {
        double scale = ctx.computeOnClient(mc -> (double) MenuScale.effective() / mc.getWindow().getGuiScale()
                * mc.getWindow().getGuiScale());
        int[] at = ctx.computeOnClient(mc -> {
            BoxListingsPanel p = ((BoxScreen) mc.screen).right;
            return new int[] {p.area[0] + 30, p.rowsTop + 4};
        });
        long sum = 0;
        long worst = 0;
        int n = 120;
        for (int i = 0; i < n; i++) {
            ctx.getInput().setCursorPos((at[0] + (i % 7) * 3) * scale, (at[1] + (i % 40) * 4) * scale);
            ctx.waitTicks(1);
            long t = ctx.computeOnClient(mc -> BoxScreen.drawNanos);
            sum += t;
            worst = Math.max(worst, t);
        }
        String line = name + ": mean " + sum / n / 1000 + " us, worst " + worst / 1000 + " us" + System.lineSeparator();
        try {
            Files.writeString(out.resolve("box-hover.txt"), line, java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(2);
    }

    /** The title's magnifier clicked: the menu must open on Search with the item, and Esc come back here. */
    private static void ara(ClientGameTestContext ctx, Path out) {
        double scale = ctx.computeOnClient(mc -> (double) MenuScale.effective());
        int[] r = ctx.computeOnClient(mc -> ((BoxScreen) mc.screen).searchRect.clone());
        ctx.getInput().setCursorPos((r[0] + r[2] / 2.0) * scale, (r[1] + r[3] / 2.0) * scale);
        ctx.waitTicks(2);
        ctx.getInput().pressMouse(org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT);
        ctx.waitTicks(5);
        String opened = ctx.computeOnClient(mc -> mc.screen instanceof RoadmapScreen rs
                ? "search tab " + (rs.tab() == RoadmapScreen.TAB_SEARCH) + ", query '" + rs.search.box.getValue() + "'"
                : "screen " + (mc.screen == null ? "none" : mc.screen.getClass().getSimpleName()));
        shot(ctx, out, "box-window-ara");
        // Esc clears the search box first, then closes the menu
        for (int i = 0; i < 2 && ctx.computeOnClient(mc -> mc.screen instanceof RoadmapScreen); i++) {
            ctx.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE);
            ctx.waitTicks(5);
        }
        String back = ctx.computeOnClient(mc -> mc.screen == null ? "none" : mc.screen.getClass().getSimpleName());
        ctx.getInput().setCursorPos(2, 2);              // off the button again: no tooltip in the next shots
        ctx.waitTicks(2);
        try {
            Files.writeString(out.resolve("box-ara.txt"), "click: " + opened + System.lineSeparator() + "Esc: " + back + System.lineSeparator());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
        if (!opened.startsWith("search tab true, query 'Atoll'") || !"BoxScreen".equals(back)) {
            throw new AssertionError("Ara'da aç: " + opened + " / Esc: " + back);
        }
    }

    /** An answer with no listing in it: the item is not on the market now. */
    private static void cannedEmpty(String name) {
        try {
            Method m = Listings.class.getDeclaredMethod("accept", String.class, int.class, JsonObject.class);
            m.setAccessible(true);
            JsonObject body = new Gson().fromJson("{\"data\":[],\"page\":1,\"page_size\":0,\"total\":0,\"has_more\":false}",
                    JsonObject.class);
            m.invoke(null, name, 1, body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Live lookups on or off, with nothing ever sent: a sender that only says whether it is live. */
    private static void live(boolean on) {
        try {
            Field f = Listings.class.getDeclaredField("sender");
            f.setAccessible(true);
            Class<?> type = f.getType();
            Object sender = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "live" -> on;
                        case "queue" -> false;
                        case "hashCode" -> 0;
                        case "equals" -> proxy == args[0];
                        default -> "preview";
                    });
            f.set(null, sender);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }
}
