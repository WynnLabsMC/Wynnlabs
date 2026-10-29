package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.mixin.ContainerHoverAccessor;
import io.github.anilken.wynnlabs.model.MountRead;
import io.github.anilken.wynnlabs.mounts.FeederRead;
import io.github.anilken.wynnlabs.mounts.MountBook;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * -PpreviewOnly=feeder: the Mount Feeder marks over a stand-in feeder (a six-row chest titled
 * "Mount Feeder": column 0 the mounts, column 1 a spacer, columns 2-8 the queues, 7-8 locked), with three of the
 * Mounts tab's seeded mounts in rows 1, 2 and 4 and row 2 eating a Copper Ingot: the marks alone,
 * the mouse on row 2 (its card), and row 4's card kept open. The bag's gold frames need Wynntils'
 * slot event, which the preview does not have.
 */
final class FeederPreview {

    private FeederPreview() {}

    private static ItemStack named(net.minecraft.world.item.Item item, String name) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        return s;
    }

    static void shots(ClientGameTestContext ctx, Path out) {
        ctx.runOnClient(mc -> {
            List<MountBook.Seen> seeded = MountsPreview.mounts();
            long now = System.currentTimeMillis();
            MountRead azure = seeded.get(0).mount();
            MountRead crimson = seeded.get(1).mount();
            MountRead ivory = seeded.get(2).mount();
            List<MountBook.Seen> book = new ArrayList<>();
            book.add(new MountBook.Seen(azure, MountBook.Where.ENCLOSURE, now, new MountBook.Fed(1, List.of(), -1, now, 5)));
            book.add(new MountBook.Seen(crimson, MountBook.Where.ENCLOSURE, now,
                    new MountBook.Fed(2, List.of("Copper Ingot", "Oak Paper"), 8040, now, 5)));
            book.add(new MountBook.Seen(ivory, MountBook.Where.ENCLOSURE, now, new MountBook.Fed(4, List.of(), -1, now, 5)));
            book.add(seeded.get(3));
            MountBook.preview(book);
            MountRead[] byRow = {azure, crimson, null, ivory, null};
            List<MountBook.Live> live = new ArrayList<>();
            SimpleContainer chest = new SimpleContainer(54);
            for (int r = 0; r < 5; r++) {
                List<Integer> trough = new ArrayList<>();
                for (int c = 2; c <= 8; c++) {
                    trough.add(r * 9 + c);
                }

                chest.setItem(r * 9 + 7, named(Items.IRON_BARS, "Locked Slot"));
                chest.setItem(r * 9 + 8, named(Items.IRON_BARS, "Locked Slot"));
                if (byRow[r] != null) {
                    chest.setItem(r * 9, named(Items.SADDLE, byRow[r].name()));
                }
                List<String> pending = r == 1 ? List.of("Copper Ingot", "Oak Paper") : List.of();
                if (r == 1) {
                    chest.setItem(r * 9 + 2, named(Items.IRON_INGOT, "Copper Ingot"));
                    chest.setItem(r * 9 + 3, named(Items.PAPER, "Oak Paper"));
                }
                FeederRead.Row row = new FeederRead.Row(r + 1, r * 9, List.copyOf(trough), 5);
                live.add(new MountBook.Live(row, byRow[r] == null ? null : byRow[r].key(), pending, r == 1 ? 8040 : -1,
                        r * 9 + 2 + pending.size()));
            }
            MountBook.previewLive(live);
            FeederOverlay.reset();
            var inv = mc.player.getInventory();
            // the preview has no Wynntils (no render event): the stand-in draws the overlay after itself
            mc.setScreen(new ContainerScreen(ChestMenu.sixRows(0, inv, chest), inv, Component.literal("Mount Feeder")) {
                @Override
                public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float pt) {
                    super.render(g, mx, my, pt);
                    FeederOverlay.render(this, g, mx, my);
                }
            });
        });
        ctx.waitTicks(40);
        // one of row 1's next food in the bag (a stand-in count: real items in the inventory make the
        // tick read them through Wynntils, which the preview does not have): row 1 marks it as in the
        // bag, later rows wanting it buy it
        ctx.runOnClient(mc -> {
            FeederOverlay.previewBag = new java.util.HashMap<>();
            for (MountBook.Live row : MountBook.live()) {
                var next = row.row().number() == 1 ? FeederOverlay.nextFood(FeederOverlay.owned(row)) : null;
                if (next != null) {
                    FeederOverlay.previewBag.put(next.name(), 1);
                }
            }
        });
        ctx.waitTicks(5);
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(10);
        shot(ctx, out, "feeder-marks");
        // the mouse on row 2's mount: its card beside the menu
        double[] at = ctx.computeOnClient(mc -> {
            var screen = (ContainerScreen) mc.screen;
            var acc = (ContainerHoverAccessor) screen;
            var slot = screen.getMenu().slots.get(9);
            double scale = mc.getWindow().getGuiScale();
            return new double[] {(acc.wynnlabs$leftPos() + slot.x + 8) * scale, (acc.wynnlabs$topPos() + slot.y + 8) * scale};
        });
        ctx.getInput().setCursorPos(at[0], at[1]);
        ctx.waitTicks(10);
        shot(ctx, out, "feeder-hover");
        // row 4's card kept open, the mouse away
        ctx.runOnClient(mc -> FeederOverlay.pinned = 4);
        ctx.getInput().setCursorPos(2, 2);
        ctx.waitTicks(10);
        shot(ctx, out, "feeder-pinned");
        ctx.runOnClient(mc -> {
            FeederOverlay.reset();
            FeederOverlay.previewBag = null;
            MountBook.previewLive(null);
            MountBook.preview(null);
            mc.setScreen(null);
        });
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }
}
