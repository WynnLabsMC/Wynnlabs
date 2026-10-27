package io.github.anilken.wynnlabs.ui;

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
 * The quest guide window without the game ({@code -PpreviewOnly=guide}): a shipped guide (Jungle
 * Fever) on the recorded Shaman, over the world. Nothing is fetched.
 */
public class GuidePreview implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext ctx) {
        if (!"guide".equals(System.getProperty("wynnlabs.previewOnly"))) {
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
            ctx.getInput().setCursorPos(2, 2);
            ctx.setScreen(() -> new GuideScreen("Jungle Fever", ""));
            ctx.waitTicks(20);
            ctx.takeScreenshot(TestScreenshotOptions.of("guide-window").disableCounterPrefix().withDestinationDir(out));
            ctx.runOnClient(mc -> mc.setScreen(null));
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            FixtureState.uninstall();
        }
    }
}
