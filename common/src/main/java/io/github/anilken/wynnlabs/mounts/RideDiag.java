package io.github.anilken.wynnlabs.mounts;

import io.github.anilken.wynnlabs.core.DevHooks;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.model.MountRead;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Dev builds only ({@link DevHooks#ON}): while riding, at most once a second, what the mount panel
 * sees and decides, written over config/wynnlabs/dev/ride.txt: the vehicle, Wynntils' mount
 * model's answers, the items in its slot and in the selected one (name and lore as text), how each
 * reads, and whether the panel shows and why. Nothing here is in a release's behaviour.
 */
final class RideDiag {

    private static long lastAt;

    private RideDiag() {}

    static void write(Minecraft mc, int wynntilsSlot, int chosen, MountRead shown, boolean panelOn, String decision) {
        if (!DevHooks.ON) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAt < 1000) {
            return;
        }
        lastAt = now;
        try {
            var p = mc.player;
            if (p == null) {
                return;
            }
            var inv = p.getInventory();
            int selected = inv.getSelectedSlot();
            StringBuilder b = new StringBuilder();
            b.append("time ").append(java.time.LocalTime.now()).append('\n');
            b.append("isPassenger ").append(p.isPassenger()).append('\n');
            b.append("vehicle ").append(p.getVehicle() == null ? "none" : p.getVehicle().getType().toString()).append('\n');
            b.append("ridePanel ").append(panelOn).append('\n');
            b.append("wynntils currentMountType ").append(WynntilsItems.currentMountTypeName()).append('\n');
            b.append("wynntils currentMountSlot ").append(wynntilsSlot).append('\n');
            int[] e = WynntilsItems.currentMountEnergy();
            b.append("wynntils currentMountEnergy ").append(e == null ? "none" : Arrays.toString(e)).append('\n');
            b.append("selected slot ").append(selected).append('\n');
            b.append("chosen slot ").append(chosen).append('\n');
            if (wynntilsSlot >= 0 && wynntilsSlot < inv.getContainerSize()) {
                item(b, "wynntils slot " + wynntilsSlot, inv.getItem(wynntilsSlot));
            }
            item(b, "selected slot " + selected, inv.getItem(selected));
            if (chosen >= 0 && chosen != selected && chosen != wynntilsSlot) {
                item(b, "chosen slot " + chosen, inv.getItem(chosen));
            }
            b.append("shown mount ").append(shown == null ? "none" : shown.type() + " " + shown.name() + " stats "
                    + shown.stats().size() + " energy " + shown.energy() + "/" + shown.energyMax()).append('\n');
            b.append("panel ").append(decision).append('\n');
            Path f = Dirs.config().resolve("dev").resolve("ride.txt");
            Files.createDirectories(f.getParent());
            Files.writeString(f, b.toString(), StandardCharsets.UTF_8);
        } catch (Throwable t) {
            System.out.println("[WynnLabs] ride diagnostics: " + t);
        }
    }

    private static void item(StringBuilder b, String label, ItemStack stack) {
        b.append("-- ").append(label).append(": ");
        if (stack == null || stack.isEmpty()) {
            b.append("empty\n");
            return;
        }
        b.append(RideSignals.escaped(stack.getHoverName().getString())).append('\n');
        for (String l : MountBook.loreText(stack)) {
            b.append("   | ").append(l).append('\n');
        }
        b.append("   readAny: ").append(MountBook.explain(stack)).append('\n');
        b.append("   home: ").append(RideLore.home(MountBook.loreText(stack))).append('\n');
    }
}
