package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.HowMade;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.Types;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.Wyn;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The box window on a crafted item ({@code -PpreviewOnly=boxmade}): how a scroll, a helmet (with a
 * position-effect ingredient) and a scroll no layout can make were most likely made, from the
 * preview's sample ingredients; each at the large window (GUI scale 3) and the small one (a 1000x600
 * window, one pane at a time). No prices: costs show "?".
 */
public class BoxMadePreview implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!"boxmade".equals(System.getProperty("wynnlabs.previewOnly"))) {
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
                    FixtureState.load(Fixtures.root().resolve("shaman-44")).install(System.getProperty("wynnlabs.previewLang", "en"));
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                Wyn.clearDown();
                CraftingTab.previewIngredients = CraftPreview.ingredients();
            });
            Map<String, Ingredient> by = new HashMap<>();
            CraftPreview.ingredients().forEach(i -> by.put(i.name(), i));
            HowMade.Target scroll = target("Scroll of Dusk", "scroll", "Scroll80-83", 2, 1, by,
                    "Essence Of Dusk", "Essence Of Dusk", "Haleva Plant", "Altered Ash");
            HowMade.Target helmet = target("Crystal Helm", "helmet", "Helmet97-99", 3, 2, by,
                    "Major's Badge", "Elemental Crystal", "Chitin Plate", "Chitin Plate", "Enhanced Potential Microchip");
            HowMade.Target odd = new HowMade.Target("Odd Scroll", "scroll", 83,
                    List.of(new HowMade.Stat("rawSpellDamage", "Spell Damage", "", 400),
                            new HowMade.Stat("spellDamage", "Spell Damage", "%", 9)), 3, 700, -1, -1, null);
            for (Object[] c : new Object[][] {{"scroll", scroll, Items.PAPER}, {"helmet", helmet, Items.LEATHER_HELMET},
                    {"closest", odd, Items.PAPER}}) {
                HowMade.Target t = (HowMade.Target) c[1];
                ItemStack icon = new ItemStack((net.minecraft.world.item.Item) c[2]);
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(3);
                    mc.resizeDisplay();
                    mc.setScreen(new BoxScreen(t, icon, new InventoryScreen(mc.player)));
                });
                ctx.getInput().setCursorPos(2, 2);
                ctx.waitTicks(3);
                shot(ctx, out, "boxmade-" + c[0] + "-searching");
                ctx.waitTicks(20 * 6);
                shot(ctx, out, "boxmade-" + c[0]);
                ctx.getInput().resizeWindow(1000, 600);
                ctx.waitTicks(5);
                ctx.runOnClient(mc -> {
                    mc.options.guiScale().set(4);
                    mc.resizeDisplay();
                });
                ctx.waitTicks(5);
                shot(ctx, out, "boxmade-" + c[0] + "-small");
                ctx.runOnClient(mc -> ((BoxScreen) mc.screen).made.tab = 1);
                ctx.waitTicks(3);
                shot(ctx, out, "boxmade-" + c[0] + "-small-match");
                ctx.getInput().resizeWindow(PreviewSize.WIDTH, PreviewSize.HEIGHT);
                ctx.waitTicks(3);
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** What a crafted item made from this layout shows: each identification at its range's middle. */
    private static HowMade.Target target(String name, String type, String recipeId, int t1, int t2, Map<String, Ingredient> by,
                                         String... names) {
        CraftData.Recipe recipe = CraftData.byId(recipeId);
        Ingredient[] placed = new Ingredient[names.length];
        for (int i = 0; i < names.length; i++) {
            placed[i] = by.get(names[i]);
        }
        Craft.Result r = Craft.craft(recipe, t1, t2, Craft.Speed.NORMAL, Craft.slots(placed));
        List<HowMade.Stat> ids = new ArrayList<>();
        r.ids().forEach((k, g) -> {
            int v = (g.min() + g.max()) / 2;
            if (v != 0) {
                Ingredient.Stat s = null;
                for (Ingredient i : placed) {
                    s = s != null ? s : i.ids().get(k);
                }
                ids.add(new HowMade.Stat(k, s == null ? k : s.shown(), s == null ? "" : s.unit(), v));
            }
        });
        boolean consumable = Types.consumable(type);
        int[] req = null;
        if (!consumable) {
            req = new int[5];
            for (int s = 0; s < 5; s++) {
                req[s] = Math.max(0, r.requirements()[s]);
            }
        }
        return new HowMade.Target(name, type, recipe.maxLevel(), ids, consumable ? r.charges() : -1,
                consumable ? (r.duration().min() + r.duration().max()) / 2 : -1,
                consumable ? -1 : (r.durability().min() + r.durability().max()) / 2,
                consumable || r.health() == null ? -1 : (r.health().min() + r.health().max()) / 2, req);
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }
}
