package io.github.anilken.wynnlabs.api;

import com.google.gson.JsonArray;

import io.github.anilken.wynnlabs.advice.AutoAdvice;
import io.github.anilken.wynnlabs.core.Perf;
import io.github.anilken.wynnlabs.core.Platform;
import io.github.anilken.wynnlabs.market.Prices;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Start-up registers every listener through {@link Platform} in a fixed order: per kind (tick,
 * chat, HUD ...) the loader runs them in the order they were added, so that order must never
 * change by accident. A recording platform runs {@link WynnLabs#init} and lists each registration
 * with its timing section; the developer tools' own hooks (absent from a release jar) are left out.
 */
class StartupSequenceTest {

    /**
     * Per kind (by name), in registration order: the timing section, or the key, command or HUD id.
     * Only the order within a kind matters to the loaders, so kinds are not interleaved here.
     */
    private static final String EXPECTED = """
            chat -
            chat -
            chat -
            chat -
            chat -
            command av
            command wynnlabs-update
            command wynnlabs-prices
            disconnect -
            hud wynnlabs:coach hud.Coach !
            hud wynnlabs:dps hud.DpsHud
            hud wynnlabs:ride hud.RidePanel
            hud wynnlabs:hotbar_badges hud.HotbarBadges
            hud wynnlabs:mob_track hud.MobTracker
            key key.wynnlabs.roadmap
            key key.wynnlabs.search
            key key.wynnlabs.box_guide
            keyCategory wynnlabs:main
            started -
            stopping -
            tick -
            tick tick.Stamp
            tick tick.MenuWatcher
            tick tick.Profile
            tick tick.Crowd
            tick tick.Setup
            tick tick.Wyn
            tick tick.Prices
            tick tick.Build
            tick tick.Roadmap
            tick tick.TreePlanner
            tick tick.GroundMarkers
            tick tick.MobTracker
            tick tick.DropTracker
            tick tick.ChestBeams
            tick tick.GuideLine
            tick tick.KeptMarks
            tick tick.QuestBook
            tick tick.AutoAdvice
            tick tick.DeathCoach
            tick tick.Today
            tick tick.WearCheck
            tick tick.UpdateCheck
            tick tick.PricePrompt
            tick tick.DiscoveryBook
            tick tick.Danger
            tick tick.SellSummary
            tick tick.XpMeter
            tick tick.Vault
            tick tick.Bags
            tick tick.MountBook
            tick tick.RideWatch
            tick tick.Powerups
            tick tick.Coach
            tick tick.RaidLog
            tick tick.RaidPool
            tick tick.LootrunLog
            tick tick.AspectBook
            tick -
            tick -
            tick -
            tick -
            tooltip -
            world world.MobTracker
            world world.ChestBeams
            world world.GuideLine
            world world.DropTracker
            world world.PowerupLabels
            """;

    @Test
    void initRegistersTheSameHooksInTheSameOrder() {
        Platform before = Platform.get();
        boolean budgeted = AutoAdvice.budgeted;
        boolean coins = Prices.coins;
        Recorder rec = new Recorder(before);
        try {
            WynnLabs.init(rec);
        } finally {
            Platform.install(before);
            AutoAdvice.budgeted = budgeted;
            Prices.coins = coins;
        }
        assertEquals(EXPECTED.strip(), rec.sequence().strip());
    }

    /** Records each registration, and passes the paths and mod queries to the real platform. */
    private static final class Recorder implements Platform {
        private final Platform real;
        private final Map<String, List<String>> byKind = new TreeMap<>();

        Recorder(Platform real) {
            this.real = real;
        }

        private void add(String kind, String what) {
            // the developer tools register their own hooks; a release jar has none
            boolean dev = StackWalker.getInstance().walk(s -> s.anyMatch(
                    f -> f.getClassName().startsWith("io.github.anilken.wynnlabs.dev.")));
            if (!dev) {
                byKind.computeIfAbsent(kind, k -> new ArrayList<>()).add(what);
            }
        }

        String sequence() {
            StringBuilder b = new StringBuilder();
            byKind.forEach((kind, list) -> list.forEach(w -> b.append(kind).append(' ').append(w).append('\n')));
            return b.toString();
        }

        @Override
        public String loaderName() {
            return real.loaderName();
        }

        @Override
        public Path configDir() {
            return real.configDir();
        }

        @Override
        public Path gameDir() {
            return real.gameDir();
        }

        @Override
        public boolean isModLoaded(String id) {
            return real.isModLoaded(id);
        }

        @Override
        public Optional<String> modVersion(String id) {
            return real.modVersion(id);
        }

        @Override
        public Optional<Path> modFile(String id) {
            return real.modFile(id);
        }

        @Override
        public void onEndTick(Consumer<Minecraft> listener) {
            add("tick", capturedLabel(listener));
        }

        @Override
        public void onGameMessage(BiConsumer<Component, Boolean> listener) {
            add("chat", "-");
        }

        @Override
        public void onTooltip(BiConsumer<ItemStack, List<Component>> listener) {
            add("tooltip", "-");
        }

        @Override
        public void addHud(Identifier id, HudLayer layer) {
            add("hud", id + " " + timed(() -> layer.render(null, null)));
        }

        @Override
        public void onWorldRender(WorldLayer layer) {
            add("world", timed(() -> layer.render(null, null)));
        }

        @Override
        public void onDisconnect(Runnable listener) {
            add("disconnect", "-");
        }

        @Override
        public KeyMapping.Category keyCategory(Identifier id) {
            add("keyCategory", id.toString());
            return new KeyMapping.Category(id);
        }

        @Override
        public KeyMapping registerKey(KeyMapping key) {
            add("key", key.getName());
            return key;
        }

        @Override
        public void registerClientCommand(ClientCommand command) {
            add("command", command.<Object>build((source, text) -> { }).getLiteral());
        }

        @Override
        public void onClientStarted(Runnable listener) {
            add("started", "-");
        }

        @Override
        public void onClientStopping(Runnable listener) {
            add("stopping", "-");
        }
    }

    /**
     * The section a tick listener is timed as: the label {@link Perf#tick} captured, or "-" for a
     * listener that is not timed. Read from the listener, so no feature's tick runs here.
     */
    private static String capturedLabel(Object listener) {
        if (!listener.getClass().getName().startsWith(Perf.class.getName() + "$$Lambda")) {
            return "-";
        }
        for (Field f : listener.getClass().getDeclaredFields()) {
            if (f.getType() == String.class) {
                try {
                    f.setAccessible(true);
                    return (String) f.get(listener);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    return "?";
                }
            }
        }
        return "-";
    }

    /**
     * Draws a layer once without a game (it returns early or fails at once) while timing is on:
     * the section it was timed as, and "!" when its exception reached the caller (no guard).
     */
    private static String timed(Runnable draw) {
        boolean was = Perf.on;
        Perf.reset();
        Perf.on = true;
        String thrown = "";
        try {
            draw.run();
        } catch (Throwable t) {
            thrown = " !";
        } finally {
            Perf.on = was;
        }
        JsonArray sections = Perf.json().getAsJsonArray("sections");
        Perf.reset();
        String label = sections.isEmpty() ? "-" : sections.get(0).getAsJsonObject().get("section").getAsString();
        return label + thrown;
    }
}
