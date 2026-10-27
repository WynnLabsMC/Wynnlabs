package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.market.Vault;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * -PpreviewOnly=bank: the bank tab on the fixture's bank (shaman-44): the whole bank, a search, a
 * kind chip, each layout (and a search in it), the menus, a slot's drawer, the list to sell, then a bank read in part (pages
 * missing between read ones) and one not read at all.
 */
final class BankPreview {

    private BankPreview() {}

    static void shots(ClientGameTestContext ctx, Path out) throws Exception {
        var fx = FixtureState.load(Fixtures.root().resolve(System.getProperty("wynnlabs.previewFixture", "shaman-44")));
        Map<String, List<ItemStack>> all = fx.bankStacks();
        ctx.runOnClient(mc -> {
            Vault.preview(all);
            ((RoadmapScreen) mc.screen).openTab(RoadmapScreen.TAB_BANK);
        });
        ctx.waitTicks(10);
        shot(ctx, out, "bank-tab");
        step(ctx, out, "bank-search", b -> {
            b.slashTyped();
            b.search.setValue("Champion");
        });
        step(ctx, out, "bank-list", b -> {
            b.previewReset();
            b.previewView(BankPages.VIEW_LIST);
        });
        step(ctx, out, "bank-list-search", b -> b.search.setValue("Powder"));
        step(ctx, out, "bank-all", b -> {
            b.previewReset();
            b.previewView(BankPages.VIEW_ALL);
        });
        step(ctx, out, "bank-all-search", b -> b.search.setValue("Powder"));
        step(ctx, out, "bank-kind", b -> {
            b.previewReset();
            b.previewView(BankPages.VIEW_PAGES);
            b.previewKind(BankIndex.Kind.ARMOUR);
        });
        step(ctx, out, "bank-menu-banks", b -> {
            b.previewReset();
            b.previewMenu(BankMenus.MENU_BANKS);
        });
        step(ctx, out, "bank-menu-sort", b -> {
            b.previewReset();
            b.previewView(BankPages.VIEW_LIST);
            b.previewMenu(BankMenus.MENU_SORT);
        });
        step(ctx, out, "bank-menu-view", b -> {
            b.previewReset();
            b.previewMenu(BankMenus.MENU_VIEW);
        });
        step(ctx, out, "bank-menu-tier", b -> {
            b.previewReset();
            b.previewView(BankPages.VIEW_PAGES);
            b.previewMenu(BankMenus.MENU_TIER);
        });
        step(ctx, out, "bank-drawer-item", b -> {
            b.previewReset();
            b.previewItem("Gleaming Titanium Boots");
        });
        step(ctx, out, "bank-drawer-sell", b -> {
            b.previewReset();
            b.previewSell();
        });
        // a bank read in part: the account bank's first two pages and its fifth
        Map<String, List<ItemStack>> part = new LinkedHashMap<>();
        all.forEach((k, v) -> {
            if (k.startsWith("acct:") && List.of(1, 2, 5).contains(Vault.pageNumber(k))) {
                part.put(k, v);
            }
        });
        step(ctx, out, "bank-partial", b -> {
            b.previewReset();
            Vault.preview(part);
        });
        step(ctx, out, "bank-empty", b -> Vault.preview(Map.of()));
        ctx.runOnClient(mc -> Vault.preview(all));
    }

    private static void step(ClientGameTestContext ctx, Path out, String name, java.util.function.Consumer<BankTab> act) {
        ctx.runOnClient(mc -> act.accept(((RoadmapScreen) mc.screen).bank));
        ctx.waitTicks(8);
        shot(ctx, out, name);
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }
}
