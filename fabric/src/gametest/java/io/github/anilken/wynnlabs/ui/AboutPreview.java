package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.UpdateAsset;
import io.github.anilken.wynnlabs.core.UpdateCheck;
import io.github.anilken.wynnlabs.core.UpdateDownload;
import io.github.anilken.wynnlabs.core.UpdateInstall;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.Wyn;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The About page (the rail's "?") and the update window without the game
 * ({@code -PpreviewOnly=about}): About when up to date, with an update, offline, downloading,
 * ready to install on restart and after a failed install; the update window with its notes, the
 * full changelog and while downloading. Nothing is fetched: the states are given by hand.
 */
public class AboutPreview implements FabricClientGameTest {

    private static final String PAGE = "https://github.com/anilken/wynnlabs/releases/tag/";
    private static final String NOTES = "## [v0.4.0](https://github.com/anilken/Wynnlabs/compare/v0.3.0...v0.4.0) (2026-10-23)\n\n"
            + "### New Features\n\n"
            + "* **roadmap:** more options per slot, with a Trade Market filter ([#66](https://github.com/anilken/Wynnlabs/pull/66))\n"
            + "* **ui:** show where each gear slot's piece comes from ([#62](https://github.com/anilken/Wynnlabs/pull/62))\n\n"
            + "### Bug Fixes\n\n* **quests:** finished quests are recognised right away ([#63](https://github.com/anilken/Wynnlabs/pull/63))\n";

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!"about".equals(System.getProperty("wynnlabs.previewOnly"))) {
            return;
        }
        Path out = Path.of(System.getProperty("wynnlabs.preview", "preview"));
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            world.getClientWorld().waitForChunksRender();
            Files.createDirectories(out);
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
            });
            ctx.setScreen(() -> RoadmapScreen.on(RoadmapScreen.TAB_SETTINGS));
            ctx.waitForScreen(RoadmapScreen.class);
            ctx.getInput().setCursorPos(2, 2);
            for (String state : new String[] {"uptodate", "new", "offline", "downloading", "ready", "install-failed"}) {
                ctx.runOnClient(mc -> {
                    ((RoadmapScreen) mc.screen).settings.showAbout();
                    state(state);
                });
                ctx.waitTicks(4);
                shot(ctx, out, "about-" + state);
            }
            for (String state : new String[] {"new", "changelog", "downloading", "ready"}) {
                ctx.runOnClient(mc -> state(state.equals("changelog") ? "new" : state));
                ctx.setScreen(() -> new UpdateScreen(null, state.equals("changelog")));
                ctx.waitTicks(4);
                shot(ctx, out, "update-" + state);
            }
            ctx.runOnClient(mc -> {
                UpdateDownload.preview(UpdateDownload.Phase.IDLE, 0, UpdateDownload.Failure.NONE, null);
                UpdateCheck.reset();
                mc.setScreen(null);
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            FixtureState.uninstall();
        }
    }

    /** The update check and the updater as they would be in each state. */
    private static void state(String name) {
        long now = System.currentTimeMillis();
        UpdateDownload.preview(UpdateDownload.Phase.IDLE, 0, UpdateDownload.Failure.NONE, null);
        UpdateAsset jar = new UpdateAsset("wynnlabs-fabric-0.4.0.jar", 4_320_605, "0".repeat(64),
                "https://github.com/anilken/Wynnlabs/releases/download/v0.4.0/wynnlabs-fabric-0.4.0.jar");
        switch (name) {
            case "uptodate" -> UpdateCheck.preview(new UpdateCheck.State(UpdateCheck.Status.UP_TO_DATE, UpdateCheck.Reason.NONE, "0.3.0",
                    "v0.3.0", PAGE + "v0.3.0", now - 5 * 86_400_000L, now - 4 * 60_000L));
            case "offline" -> UpdateCheck.preview(new UpdateCheck.State(UpdateCheck.Status.FAILED, UpdateCheck.Reason.OFFLINE, "0.3.0",
                    null, null, 0, now - 60_000L));
            default -> {
                UpdateCheck.preview(new UpdateCheck.State(UpdateCheck.Status.NEWER, UpdateCheck.Reason.NONE, "0.3.0",
                        "v0.4.0", PAGE + "v0.4.0", now - 3 * 86_400_000L, now - 2 * 60_000L, NOTES, jar));
                switch (name) {
                    case "downloading" -> UpdateDownload.preview(UpdateDownload.Phase.DOWNLOADING, 0.4, UpdateDownload.Failure.NONE, "v0.4.0");
                    case "ready" -> UpdateDownload.preview(UpdateDownload.Phase.READY, 1, UpdateDownload.Failure.NONE, "v0.4.0");
                    case "install-failed" -> UpdateInstall.preview("v0.4.0");
                    default -> {
                    }
                }
            }
        }
    }

    private static void shot(ClientGameTestContext ctx, Path out, String name) {
        ctx.takeScreenshot(TestScreenshotOptions.of(name).disableCounterPrefix().withDestinationDir(out));
    }
}
