package io.github.anilken.wynnlabs;

import io.github.anilken.wynnlabs.advice.Advice;
import io.github.anilken.wynnlabs.advice.SellSummary;
import io.github.anilken.wynnlabs.advice.WearCheck;
import io.github.anilken.wynnlabs.api.WynnLabs;
import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.build.TreePlanner;
import io.github.anilken.wynnlabs.coach.Coach;
import io.github.anilken.wynnlabs.coach.Danger;
import io.github.anilken.wynnlabs.coach.DeathCoach;
import io.github.anilken.wynnlabs.coach.DpsMeter;
import io.github.anilken.wynnlabs.coach.Today;
import io.github.anilken.wynnlabs.coach.XpMeter;
import io.github.anilken.wynnlabs.core.DevHooks;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.Module;
import io.github.anilken.wynnlabs.core.Perf;
import io.github.anilken.wynnlabs.core.Platform;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.core.Saver;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Stamp;
import io.github.anilken.wynnlabs.core.UpdateCheck;
import io.github.anilken.wynnlabs.endgame.AspectBook;
import io.github.anilken.wynnlabs.endgame.LootrunLog;
import io.github.anilken.wynnlabs.endgame.RaidLog;
import io.github.anilken.wynnlabs.endgame.RaidPool;
import io.github.anilken.wynnlabs.game.MenuWatcher;
import io.github.anilken.wynnlabs.game.WornGear;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.game.WynntilsEvents;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.quests.DiscoveryBook;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.ui.DpsHud;
import io.github.anilken.wynnlabs.ui.GroundMarkers;
import io.github.anilken.wynnlabs.ui.HotbarBadges;
import io.github.anilken.wynnlabs.ui.MobTracker;
import io.github.anilken.wynnlabs.ui.RoadmapScreen;

import java.util.List;

/**
 * The mod's start-up, the same on every loader: {@link WynnLabs#init} hands it the loader's
 * {@link Platform}, and every listener is registered through that. Each feature's ticks, chat
 * listeners, HUD and world layers, disconnect part and event listeners are one row of
 * {@link #MODULES}; within each kind they are added in the table's order.
 */
public final class Startup {

    /**
     * The features' hooks, in order. A feature takes two rows when its tick runs early but its
     * layers draw after others' (MobTracker, DropTracker, ChestBeams, GuideLine).
     */
    private static final List<Module> MODULES = List.of(
            // what the player carries, as one number caches compare against (before anything reads it)
            Module.of("Stamp").tick(Stamp::onTick),
            Module.of("MenuWatcher").tick(MenuWatcher::onTick),
            Module.of("Profile").tick(Profile::onTick),
            Module.of("Crowd").tick(io.github.anilken.wynnlabs.ui.CrowdMode::onTick),
            Module.of("WorldEvents").chat(io.github.anilken.wynnlabs.quests.WorldEvents::onChat),
            Module.of("Setup").tick(Setup::onTick),
            Module.of("Wyn").tick(Wyn::onTick),
            Module.of("Prices").tick(Prices::onTick),
            Module.of("Build").tick(Build::onTick),
            // the road map is built only when asked (the J menu's button, its refresh, a setting you
            // change there); in the background only the applied plan's level-up check runs
            Module.of("Roadmap").tick(client -> {
                if (client.player != null && client.player.tickCount % 40 == 0) {
                    Around.watch();                     // only with a plan: the better-build check after a level-up
                }
            }),
            Module.of("TreePlanner").tick(TreePlanner::onTick),
            Module.of("GroundMarkers").tick(GroundMarkers::onTick),
            Module.of("MobTracker").tick(MobTracker::onTick),
            Module.of("DropTracker").tick(io.github.anilken.wynnlabs.ui.DropTracker::onTick),
            Module.of("ChestBeams").tick(io.github.anilken.wynnlabs.ui.ChestBeams::onTick),
            Module.of("GuideLine").tick(io.github.anilken.wynnlabs.ui.GuideLine::onTick),
            Module.of("KeptMarks").tick(io.github.anilken.wynnlabs.ui.KeptMarksSync::onTick),
            Module.of("QuestBook").tick(QuestBook::onTick).chat(QuestBook::onChat),
            Module.of("AutoAdvice").tick(WornGear::snapshotArmour),
            Module.of("CaveMap").chat(io.github.anilken.wynnlabs.quests.CaveMap::onChat),
            Module.of("DeathCoach").tick(DeathCoach::onTick).chat(DeathCoach::onChat),
            Module.of("Today").tick(Today::onTick),
            Module.of("WearCheck").tick(WearCheck::onTick),
            // once per game run, a few seconds into a world: a chat line (or the auto update) when GitHub has a newer release
            Module.of("UpdateCheck").tick(UpdateCheck::onTick),
            // once ever, a little later: whether to turn live prices on ([Yes] [No] in chat)
            Module.of("PricePrompt").tick(io.github.anilken.wynnlabs.market.PricePrompt::onTick),
            Module.of("DiscoveryBook").tick(DiscoveryBook::onTick),
            Module.of("Danger").tick(Danger::onTick),
            Module.of("SellSummary").tick(SellSummary::onTick),
            Module.of("XpMeter").tick(XpMeter::onTick),
            Module.of("Vault").tick(Vault::onTick),
            Module.of("Bags").tick(io.github.anilken.wynnlabs.market.Bags::onTick),
            Module.of("MountBook").tick(io.github.anilken.wynnlabs.mounts.MountBook::onTick),
            // the mount panel's ridden mount, and its refresh (only with the panel and its refresh on)
            Module.of("RideWatch").tick(io.github.anilken.wynnlabs.mounts.RideWatch::onTick)
                    .chat((text, overlay) -> {
                        if (overlay) {
                            io.github.anilken.wynnlabs.mounts.RideWatch.onBar(text);
                        }
                    }),
            // the powerups around a rider, for their labels (setting powerupLabels); reads only
            Module.of("Powerups").tick(io.github.anilken.wynnlabs.mounts.Powerups::onTick),
            // the coach's HUD: the death warning and the hunt line; leaving the server closes the session report
            Module.of("Coach").tick(Coach::onTick).hud("coach", Coach::renderHud, false)
                    .disconnect(() -> Coach.saveSession(System.currentTimeMillis())),
            // the live DPS meter in a corner (setting dpsHud, off by default)
            Module.of("DpsHud").hud("dps", DpsHud::render, false).disconnect(() -> DpsMeter.LIVE.reset()),
            // the mount panel while riding (setting ridePanel, off by default)
            Module.of("RidePanel").hud("ride", io.github.anilken.wynnlabs.ui.RidePanel::render, true),
            // the verdict badges on the hotbar's items (setting hotbarBadges), after the hotbar is drawn
            Module.of("HotbarBadges").hud("hotbar_badges", HotbarBadges::render, true),
            // tracked mobs: the notice on the HUD, the markers in the world (only in plain sight)
            Module.of("MobTracker").hud("mob_track", MobTracker::renderHud, true)
                    .world(MobTracker::renderWorld, true).disconnect(MobTracker::reset),
            // the beams above known loot chests (setting chestBeams)
            Module.of("ChestBeams").world(io.github.anilken.wynnlabs.ui.ChestBeams::renderWorld, true),
            // the line to the compass target (setting guideLine)
            Module.of("GuideLine").world(io.github.anilken.wynnlabs.ui.GuideLine::renderWorld, true),
            // the beams and names over tracked items on the ground (setting trackedDrops)
            Module.of("DropTracker").world(io.github.anilken.wynnlabs.ui.DropTracker::renderWorld, true)
                    .disconnect(io.github.anilken.wynnlabs.ui.DropTracker::reset),
            // the labels over mount powerups while riding
            Module.of("PowerupLabels").world(io.github.anilken.wynnlabs.ui.PowerupLabels::renderWorld, true),
            // what a gathering node gives on each click, with its picture and price
            Module.of("NodePrices").world(io.github.anilken.wynnlabs.ui.NodePrices::renderWorld, true),
            // the Endgame tab: raid and lootrun history, the reward pool seen, the aspect list
            Module.of("RaidLog").tick(RaidLog::onTick).disconnect(RaidLog::disconnected),
            Module.of("RaidPool").tick(RaidPool::onTick),
            Module.of("LootrunLog").tick(LootrunLog::onTick).disconnect(LootrunLog::disconnected),
            Module.of("AspectBook").tick(AspectBook::onTick),
            // Wynntils' event bus only exists once the client has started: these are added then
            Module.of("tooltip hook").listener(Wyn.Feature.EVENTS, () -> new WynntilsEvents()),
            // tooltips drawn smaller while a menu is open (MenuTooltips)
            Module.of("menu tooltip size hook").listener(Wyn.Feature.EVENTS, () -> new WynntilsEvents.Tooltips()),
            // the mount panel: the mount Wynntils saw summoned or mounted
            Module.of("mount events").listener(Wyn.Feature.EVENTS, () -> new WynntilsEvents.Mounts()),
            // the Mount Feeder's marks and row card
            Module.of("mount feeder overlay").listener(Wyn.Feature.EVENTS, () -> new WynntilsEvents.Feeder()),
            // the quests tab's "read quests with Wynntils": the lists Wynntils posts after the press
            Module.of("quest list hook").listener(Wyn.Feature.QUESTS, () -> new WynntilsEvents.Quests()),
            // the Endgame tab's events, each under its own feature
            Module.of("raid and lootrun hooks").listener(Wyn.Feature.RAIDS, () -> new WynntilsEvents.Raids())
                    .listener(Wyn.Feature.LOOTRUNS, () -> new WynntilsEvents.Lootruns())
                    .listener(Wyn.Feature.LOOTRUNS, () -> new WynntilsEvents.Valuables()),
            // the crafting planner's XP per gather: the gains the game shows
            Module.of("profession xp hook").listener(Wyn.Feature.PROFESSIONS, () -> new WynntilsEvents.ProfessionGains()));

    private static int serverTick;

    private Startup() {}

    /** Called once by {@link WynnLabs#init}: every listener is registered through {@code platform}. */
    public static void init(Platform platform) {
        Platform.install(platform);
        Dirs.migrate();                         // before anything reads config
        try {
            var marker = Dirs.config().resolve("ai-write-migrated");
            if (!java.nio.file.Files.exists(marker)) {
                if (Settings.on("localApi")) {
                    Settings.set("apiWrite", true);  // the API was already in use: keep its tools writing
                }
                java.nio.file.Files.createDirectories(marker.getParent());
                java.nio.file.Files.writeString(marker, "done");
            }
        } catch (Exception ignored) {
        }
        Advice.load();
        Rules.load();
        // read in the background now, so tooltips never read these files
        Vault.preloadClasses();
        Prices.key();
        platform.onTooltip((stack, lines) -> {
            long t = Perf.start();
            try {
                long ta = Perf.start();
                lines.addAll(Advice.linesCached(stack));
                Perf.end("tooltip.advice", ta);
                // where the item comes from, one short line (setting sourceHint)
                long ts = Perf.start();
                var from = io.github.anilken.wynnlabs.advice.SourceHint.line(stack);
                Perf.end("tooltip.source", ts);
                if (from != null) {
                    lines.add(from);
                }
            } catch (Throwable ignored) {
                // a broken advice line must never break the tooltip
            } finally {
                Perf.end("tooltip.loader", t);
            }
        });
        // the timing sections follow the Debug setting (read once a tick)
        platform.onEndTick(Perf::onTick);
        io.github.anilken.wynnlabs.advice.AutoAdvice.budgeted = true;   // verdicts a few a frame, never a frozen chest
        io.github.anilken.wynnlabs.market.Prices.coins = true;          // prices in the game's emerald glyphs
        Module.register(platform, MODULES);
        // /av <item>: a hunt (client-side command, never sent to the server)
        platform.registerClientCommand(Startup::huntCommand);
        // /wynnlabs-update: the update window, from the chat line's [Update] (client-side, never sent to the server)
        platform.registerClientCommand(io.github.anilken.wynnlabs.ui.UpdateScreen::command);
        // /wynnlabs-prices yes|no: the answer to the live prices question (client-side, never sent to the server)
        platform.registerClientCommand(io.github.anilken.wynnlabs.market.PricePrompt::command);
        // dev builds only: the developer tools (the /wynnlabs shots command); nothing in a release jar
        if (DevHooks.ON) {
            DevHooks.register(platform);
        }
        // Y opens the road map (rebindable in Controls; J clashed with Wynntils)
        var category = platform.keyCategory(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("wynnlabs", "main"));
        var roadmapKey = platform.registerKey(
                new net.minecraft.client.KeyMapping("key.wynnlabs.roadmap",
                        com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, org.lwjgl.glfw.GLFW.GLFW_KEY_Y, category));
        platform.onEndTick(client -> {
            while (roadmapKey.consumeClick()) {
                if (client.player == null || client.screen != null) {
                    continue;
                }
                // Shift with the menu key: crowd mode on or off instead
                if (io.github.anilken.wynnlabs.ui.CrowdMode.shiftDown(client)) {
                    io.github.anilken.wynnlabs.ui.CrowdMode.flip(client);
                    continue;
                }
                client.setScreen(new RoadmapScreen());
            }
        });
        // the key under Esc opens the search tab, or the tab on the item under the mouse
        io.github.anilken.wynnlabs.ui.SearchKey.register(platform, category);
        // T over a gear box in a container: the box contents window over it
        io.github.anilken.wynnlabs.ui.BoxKey.register(platform, category);
        // files waiting in the background writer reach the disk before the game closes
        platform.onClientStopping(Saver::flush);
        // The local API only runs while "Local API for tools" is on; checked once a second, so
        // flipping the setting starts or stops it without a restart
        WynnLabs.syncServer();
        platform.onEndTick(client -> {
            if (++serverTick % 20 == 0) {
                WynnLabs.syncServer();
                // finished caves red and dim on Wynntils' map: follows its setting the same way
                if (client.player != null) {
                    Wyn.caveMapSync();
                    // world events on Wynntils' main map while it is open
                    Wyn.worldEventSync();
                }
            }
        });
    }

    /** {@code /av [item]}: the hunt line, shown on this client only (built once, for any loader's source). */
    private static <S> com.mojang.brigadier.builder.LiteralArgumentBuilder<S> huntCommand(
            java.util.function.BiConsumer<S, net.minecraft.network.chat.Component> feedback) {
        return com.mojang.brigadier.builder.LiteralArgumentBuilder.<S>literal("av")
                .executes(c -> {
                    feedback.accept(c.getSource(), net.minecraft.network.chat.Component.literal(Coach.hunt(null))
                            .withStyle(net.minecraft.ChatFormatting.GOLD));
                    return 1;
                })
                .then(com.mojang.brigadier.builder.RequiredArgumentBuilder.<S, String>argument("item",
                                com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                        .executes(c -> {
                            feedback.accept(c.getSource(), net.minecraft.network.chat.Component.literal(
                                    Coach.hunt(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "item")))
                                    .withStyle(net.minecraft.ChatFormatting.GOLD));
                            return 1;
                        }));
    }
}
