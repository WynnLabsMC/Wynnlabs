package io.github.anilken.wynnlabs.mounts;

import io.github.anilken.wynnlabs.core.DevHooks;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.model.MountRead;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The ridden mount for the mount panel: its read (the held mount item, else the hotbar slot last
 * used to summon one ({@link RideSlot}), or a fresher copy from a
 * {@link RideRefresh}), its "+N" flashes, and the game's side of the refresh (the action bar
 * watched, the packets sent, the server's answers swallowed). Everything here runs on the client
 * thread; nothing is sent unless the mount panel and "Refresh mount info" are both on.
 */
public final class RideWatch {

    /** Ticks between two reads of the held item. */
    private static final int READ_EVERY = 5;

    private static final RideRefresh REFRESH = new RideRefresh(new GameNet());
    private static final RideFlash FLASH = new RideFlash();
    private static final RideSlot SLOT = new RideSlot();

    private static long tick;
    private static MountRead current;
    private static MountRead lastHeld;
    /** The mount type last seen summoned or mounted ("WYVERN"), null when none. */
    private static String summoned;
    /** Where that came from ("our use hook", "wynntils summon event"), for the dev diagnostics. */
    private static String summonSource;
    /** The live energy pool {current, max} from Wynntils, null when unknown. */
    private static int[] liveEnergy;
    private static RideView view;
    private static long drawnAt;
    private static String drawnRect;
    /** Wynntils' bar frame when the item was last read, -1 when unknown. */
    private static int frameAtRead;
    /** The hotbar slot the mount is read from now, -1 when none. */
    private static int mountSlot = -1;
    private static RideSignals.Signal bar = RideSignals.Signal.NONE;
    private static String lastPill = "";
    /** Previews: this read shown without riding. */
    private static MountRead preview;

    private RideWatch() {}

    /** The mount panel's switch (off by default). */
    public static boolean panelOn() {
        return Settings.on("ridePanel");
    }

    /** The mount to show: the preview's, else the ridden one's last read; null when none is known. */
    public static MountRead current() {
        return preview != null ? preview : current;
    }

    /** Whether the panel shows now (a preview always). */
    public static boolean showing(Minecraft mc) {
        if (preview != null) {
            return true;
        }
        // while riding with the panel on it always shows: what is known, "stats not read yet" for the rest
        return panelOn() && mc.player != null && mc.player.isPassenger();
    }

    /** What the panel shows now (built once a tick, the same object while nothing changed); null when nothing rides. */
    public static RideView view() {
        if (preview != null) {
            RideView v = RideView.of(preview, null, null, null, -1);
            return v.equals(view) ? view : (view = v);
        }
        return view;
    }

    /** The panel drew at x, y, w, h (its dev diagnostics). */
    public static void drawn(int x, int y, int w, int h, int guiW, int guiH) {
        drawnAt = System.currentTimeMillis();
        drawnRect = x + "," + y + " " + w + "x" + h + " on " + guiW + "x" + guiH;
    }

    /** The energy pool to show: the item's points, its live current estimated from Wynntils' bar frame between reads. */
    public static RideEnergy.Shown energy() {
        MountRead m = current();
        if (m == null) {
            return null;
        }
        return RideEnergy.shown(m.energy(), m.energyMax(), preview == null ? liveEnergy : null, frameAtRead);
    }

    /** A stat's "+N / −N" at {@code nowMs}; 0 when none shows. */
    public static int delta(String statId, long nowMs) {
        return FLASH.delta(statId, nowMs);
    }

    /** Previews: show this mount (null ends it) with these flashes (stat id, change). */
    public static void preview(MountRead m, String[] ids, int[] changes) {
        preview = m;
        FLASH.clear();
        long now = System.currentTimeMillis();
        for (int i = 0; ids != null && i < ids.length; i++) {
            FLASH.force(ids[i], changes[i], now + 600_000);   // still showing while the shot is taken
        }
    }

    public static void onTick(Minecraft mc) {
        tick++;
        var p = mc.player;
        if (p == null || !p.isPassenger() || !panelOn()) {
            current = null;
            lastHeld = null;
            mountSlot = -1;
            liveEnergy = null;
            view = null;
            REFRESH.tick(tick, new RideRefresh.Ctx(false, panelOn(), false, false, mc.screen != null, false, 0, false));
            if (DevHooks.ON && p != null && p.isPassenger()) {
                RideDiag.write(mc, WynntilsItems.currentMountSlot(), -1, null, false, "hidden: the mount panel is off");
            }
            return;
        }
        var inv = p.getInventory();
        int selected = inv.getSelectedSlot();
        if (tick % READ_EVERY == 0) {
            // the mount Wynntils saw summoned (its item's slot); else the held mount, the slot
            // last used to summon one, or the first mount carried
            MountRead[] seen = new MountRead[36];
            boolean[] done = new boolean[36];
            int wynntils = WynntilsItems.currentMountSlot();
            java.util.function.IntFunction<MountRead> at = i -> {
                if (i < 0 || i >= 36) {
                    return null;
                }
                if (!done[i]) {
                    done[i] = true;
                    seen[i] = read(inv.getItem(i));
                }
                return seen[i];
            };
            mountSlot = -1;
            if ((wynntils < 0 || at.apply(wynntils) == null) && summoned != null) {
                // Wynntils' model has no slot, but its event (or our hook) named the type
                for (int i = 0; i < 36 && mountSlot < 0; i++) {
                    MountRead r = at.apply(i);
                    if (r != null && summoned.equalsIgnoreCase(r.type())) {
                        mountSlot = i;
                    }
                }
            }
            if (mountSlot < 0) {
                mountSlot = SLOT.choose(wynntils, selected, at);
            }
            MountRead m = mountSlot < 0 ? null : seen[mountSlot];
            if (m != null && m.type().isEmpty() && summoned != null) {
                // a summon item named without its type: the type Wynntils saw summoned
                m = new MountRead(summoned, m.name(), m.potential(), m.energy(), m.energyMax(), m.stats(), m.estimated(),
                        m.primary(), m.secondary(), m.skin(), m.state());
            }
            if (m == null) {
                current = null;                     // no mount item: no data, the panel hides
                lastHeld = null;
            } else if (!Objects.equals(m, lastHeld)) {
                lastHeld = m;
                adopt(m);
            }
            if (DevHooks.ON) {
                String why = mc.options.hideGui ? "hidden: F1" : mc.screen != null ? "hidden: a screen is open"
                        : current == null ? "shown without stats (no carried item reads as a mount)" : "shown with stats";
                long ago = System.currentTimeMillis() - drawnAt;
                why += "; drawn this frame: " + (ago < 250 ? "yes" : "no (last " + (drawnAt == 0 ? "never" : ago + " ms ago") + ")")
                        + "; rect " + drawnRect;
                RideDiag.write(mc, wynntils, mountSlot, current, true, why + "; summon seen: "
                        + (summonSource == null ? "none" : summoned + " via " + summonSource));
            }
        }
        liveEnergy = WynntilsItems.currentMountEnergy();
        RideView v = RideView.of(current, WynntilsItems.currentMountTypeName(),
                p.getVehicle() == null ? null : p.getVehicle().getType().toString(), liveEnergy, frameAtRead);
        if (!v.equals(view)) {
            view = v;
        }
        boolean busy = mc.options.keyUse.isDown() || mc.options.keyAttack.isDown();
        REFRESH.tick(tick, new RideRefresh.Ctx(true, true, Settings.on("rideRefresh") && Wyn.hasCharacter(), current != null,
                mc.screen != null, busy, selected, mountSlot == selected));
    }

    /** The player used the held item (client-side, before it is sent): its slot is kept when it reads as a mount. */
    public static void onUse() {
        var p = Minecraft.getInstance().player;
        if (p == null) {
            return;
        }
        MountRead m = read(p.getMainHandItem());
        SLOT.used(p.getInventory().getSelectedSlot(), m != null);
        if (m != null) {
            onSummoned(m.type(), "our use hook");
        }
    }

    /** A mount of this type ("WYVERN") was summoned or mounted; {@code source} says who saw it. */
    public static void onSummoned(String type, String source) {
        if (type != null && !type.isBlank()) {
            summoned = type;
            summonSource = source;
        }
    }

    private static MountRead read(ItemStack stack) {
        try {
            return MountBook.readAny(stack);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void adopt(MountRead m) {
        current = m;
        int[] live = WynntilsItems.currentMountEnergy();
        frameAtRead = live != null && live.length > 1 && live[1] == RideEnergy.FRAMES ? live[0] : -1;
        FLASH.update(m, System.currentTimeMillis());
    }

    /** The action bar's text (a packet or an overlay message): its pill or energy glyph changing asks for a refresh. */
    public static void onBar(Component text) {
        if (text == null || current == null) {
            return;
        }
        List<RideSignals.Run> runs = new ArrayList<>();
        text.visit((Style style, String s) -> {
            FontDescription f = style.getFont();
            runs.add(new RideSignals.Run(f instanceof FontDescription.Resource r ? r.id().toString() : null, s));
            return Optional.empty();
        }, Style.EMPTY);
        RideSignals.Signal now = RideSignals.read(runs);
        if (liveEnergy != null) {
            // Wynntils reads the pool live: its glyph changing needs no refresh
            now = now.withoutEnergy();
        }
        if (DevHooks.ON && !now.pillRaw().isEmpty() && !now.pillRaw().equals(lastPill)) {
            System.out.println("[WynnLabs] ride pill raw: " + RideSignals.escaped(now.pillRaw()) + " -> " + now.pill());
        }
        if (!now.pillRaw().isEmpty()) {
            lastPill = now.pillRaw();
        }
        if (RideSignals.changed(bar, now)) {
            REFRESH.signal(tick);
        }
        if (!now.empty()) {
            bar = now;
        }
    }

    /** The server opened a menu: true when it answers our refresh (the client then shows nothing). */
    public static boolean onOpenScreen(int containerId) {
        return REFRESH.onOpenScreen(containerId);
    }

    /** A menu's contents: ours are read for the mount's hotbar slot (the player's hotbar ends the list) and not applied. */
    public static boolean onContent(int containerId, List<ItemStack> items) {
        return REFRESH.onContent(containerId, () -> {
            var p = Minecraft.getInstance().player;
            int at = RideSlot.inMenu(mountSlot, items.size());
            if (p == null || at < 0) {
                return;
            }
            MountRead fresh = read(items.get(at));
            if (fresh != null) {
                adopt(fresh);
            }
        });
    }

    /** The game's packets: the only ones the mod sends, and only from {@link RideRefresh}. */
    private static final class GameNet implements RideRefresh.Net {

        @Override
        public void selectSlot(int slot) {
            send(new ServerboundSetCarriedItemPacket(slot));
        }

        @Override
        public void openInventory() {
            var p = Minecraft.getInstance().player;
            if (p != null) {
                send(new ServerboundPlayerCommandPacket(p, ServerboundPlayerCommandPacket.Action.OPEN_INVENTORY));
            }
        }

        @Override
        public void close(int containerId) {
            send(new ServerboundContainerClosePacket(containerId));
        }

        private static void send(Packet<?> packet) {
            var c = Minecraft.getInstance().getConnection();
            if (c != null) {
                c.send(packet);
            }
        }
    }
}
