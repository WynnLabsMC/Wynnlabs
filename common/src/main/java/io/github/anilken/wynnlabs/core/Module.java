package io.github.anilken.wynnlabs.core;

import io.github.anilken.wynnlabs.game.Wyn;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * One row of the start-up table: a feature's name and the hooks it adds. The ticks, HUD layers and
 * world layers are timed as {@code tick.<name>}, {@code hud.<name>} and {@code world.<name>} on
 * /perf. {@link #register} adds every row's hooks in the table's order, so within each kind the
 * loader runs them in that order.
 */
public final class Module {

    private record Hud(String id, Platform.HudLayer renderer, boolean guarded) {}

    private record World(Platform.WorldLayer renderer, boolean guarded) {}

    private record Listener(Wyn.Feature feature, Supplier<Object> listener) {}

    private final String name;
    private Consumer<Minecraft> tick;
    private BiConsumer<Component, Boolean> chat;
    private Hud hud;
    private World world;
    private Runnable disconnect;
    private final List<Listener> listeners = new ArrayList<>();

    private Module(String name) {
        this.name = name;
    }

    /**
     * A row named {@code name}: the timing sections' name, or for a row of event listeners only,
     * what the log line calls them when they cannot be added ("[WynnLabs] name unavailable").
     */
    public static Module of(String name) {
        return new Module(name);
    }

    /** Runs at the end of every client tick. */
    public Module tick(Consumer<Minecraft> tick) {
        this.tick = tick;
        return this;
    }

    /** A game message reached the chat or the action bar ({@code true} for the action bar). */
    public Module chat(BiConsumer<Component, Boolean> chat) {
        this.chat = chat;
        return this;
    }

    /**
     * A HUD layer {@code wynnlabs:<id>}; {@code guarded} swallows what it throws, so it never
     * breaks the HUD.
     */
    public Module hud(String id, Platform.HudLayer renderer, boolean guarded) {
        this.hud = new Hud(id, renderer, guarded);
        return this;
    }

    /** Drawn into the world once a frame; {@code guarded} swallows what it throws. */
    public Module world(Platform.WorldLayer renderer, boolean guarded) {
        this.world = new World(renderer, guarded);
        return this;
    }

    /** The client left a server: every row's part runs in one listener, in the table's order. */
    public Module disconnect(Runnable disconnect) {
        this.disconnect = disconnect;
        return this;
    }

    /**
     * An event listener for {@code feature}, made and added once the client has started. A row's
     * listeners are added together: when one fails, the rest of the row is skipped and one line
     * is logged.
     */
    public Module listener(Wyn.Feature feature, Supplier<Object> listener) {
        listeners.add(new Listener(feature, listener));
        return this;
    }

    /** Adds the hooks of {@code modules}, in order, through {@code platform}. */
    public static void register(Platform platform, List<Module> modules) {
        for (Module m : modules) {
            if (m.tick != null) {
                platform.onEndTick(Perf.tick("tick." + m.name, m.tick));
            }
            if (m.chat != null) {
                platform.onGameMessage(m.chat);
            }
            if (m.hud != null) {
                platform.addHud(Identifier.fromNamespaceAndPath("wynnlabs", m.hud.id()), hudLayer(m.name, m.hud));
            }
            if (m.world != null) {
                platform.onWorldRender(worldLayer(m.name, m.world));
            }
        }
        platform.onDisconnect(() -> {
            for (Module m : modules) {
                if (m.disconnect != null) {
                    m.disconnect.run();
                }
            }
        });
        platform.onClientStarted(() -> {
            for (Module m : modules) {
                if (m.listeners.isEmpty()) {
                    continue;
                }
                try {
                    for (Listener l : m.listeners) {
                        Wyn.listen(l.feature(), l.listener().get());
                    }
                } catch (Throwable t) {
                    System.out.println("[WynnLabs] " + m.name + " unavailable: " + t);
                }
            }
        });
    }

    private static Platform.HudLayer hudLayer(String name, Hud hud) {
        String section = "hud." + name;
        return (g, dt) -> {
            long t = Perf.start();
            try {
                hud.renderer().render(g, dt);
            } catch (Throwable e) {
                if (!hud.guarded()) {
                    throw e;
                }
            } finally {
                Perf.end(section, t);
            }
        };
    }

    private static Platform.WorldLayer worldLayer(String name, World world) {
        String section = "world." + name;
        return (pose, buffers) -> {
            long t = Perf.start();
            try {
                world.renderer().render(pose, buffers);
            } catch (Throwable e) {
                if (!world.guarded()) {
                    throw e;
                }
            } finally {
                Perf.end(section, t);
            }
        };
    }
}
