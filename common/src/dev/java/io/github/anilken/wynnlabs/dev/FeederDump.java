package io.github.anilken.wynnlabs.dev;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.mounts.MountBook;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.time.LocalDateTime;

/**
 * Dev builds only: while a Mount Feeder is open, every slot of the menu (index, position, item,
 * name, count, lore, whether it reads as a mount) is written to config/wynnlabs/dev/feeder-dump.txt,
 * a fresh dump whenever the slots change, to confirm the feeder's layout and its time-left text.
 */
final class FeederDump {

    private static String last = "";
    private static int tick;

    private FeederDump() {}

    static void onTick(Minecraft mc) {
        if (++tick % 20 != 0 || mc.player == null || mc.screen == null) {
            return;
        }
        var menu = mc.player.containerMenu;
        if (menu == null || menu == mc.player.inventoryMenu) {
            return;
        }
        var c = Wyn.container();
        if (!MountBook.feeder(c == null ? null : c.type(), mc.screen.getTitle().getString())) {
            return;
        }
        StringBuilder b = new StringBuilder();
        b.append("title: ").append(mc.screen.getTitle().getString()).append('\n');
        b.append("container: ").append(c == null ? "?" : c.type()).append('\n');
        b.append("slots: ").append(menu.slots.size()).append('\n');
        for (int i = 0; i < menu.slots.size(); i++) {
            var slot = menu.slots.get(i);
            ItemStack st = slot.getItem();
            b.append(i).append(" @").append(slot.x).append(',').append(slot.y).append(' ');
            if (st.isEmpty()) {
                b.append("-\n");
                continue;
            }
            b.append(st.getItem()).append(" x").append(st.getCount()).append(" | ").append(st.getHoverName().getString());
            try {
                if (WynntilsItems.mountRead(st) != null) {
                    b.append(" | MOUNT");
                }
            } catch (Throwable ignored) {
                // not a mount
            }
            b.append('\n');
            var lore = st.get(DataComponents.LORE);
            if (lore != null) {
                for (var line : lore.lines()) {
                    b.append("    ").append(line.getString()).append('\n');
                }
            }
        }
        String text = b.toString();
        if (text.equals(last)) {
            return;
        }
        last = text;
        try {
            var dir = Dirs.config().resolve("dev");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("feeder-dump.txt"), "# " + LocalDateTime.now() + "\n" + text);
            System.out.println("[WynnLabs] feeder dump written (" + menu.slots.size() + " slots)");
        } catch (Exception e) {
            System.out.println("[WynnLabs] feeder dump failed: " + e);
        }
    }
}
