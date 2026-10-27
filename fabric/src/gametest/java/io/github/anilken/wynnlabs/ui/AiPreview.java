package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.api.AiLink;
import io.github.anilken.wynnlabs.api.WynnLabs;
import io.github.anilken.wynnlabs.core.Settings;

import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * The AI page in the J menu preview, in its four states, through the real server: off; on and never
 * asked (writing off); connected (a real GET to the local API) with writing on; the port busy (a
 * socket of ours holds it). The port is put back after.
 */
final class AiPreview {

    private AiPreview() {}

    static void shots(ClientGameTestContext ctx, Path out, Consumer<String> shot) {
        ctx.runOnClient(mc -> ((RoadmapScreen) mc.screen).openTab(RoadmapScreen.TAB_AI));
        set(ctx, () -> {
            Settings.set("localApi", false);
            Settings.set("apiWrite", false);
        });
        shot.accept("ai-off");
        set(ctx, () -> Settings.set("localApi", true));
        shot.accept("ai-new");
        try {
            HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(AiLink.url() + "equipped")).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.out.println("[preview] the local API did not answer: " + e);
        }
        set(ctx, () -> Settings.set("apiWrite", true));
        shot.accept("ai-connected");
        int port = Settings.number("localApiPort", 47591);
        try (ServerSocket taken = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            set(ctx, () -> Settings.setNumber("localApiPort", taken.getLocalPort()));
            shot.accept("ai-busy");
        } catch (Exception e) {
            System.out.println("[preview] no port to hold: " + e);
        }
        set(ctx, () -> Settings.setNumber("localApiPort", port));
    }

    private static void set(ClientGameTestContext ctx, Runnable change) {
        ctx.runOnClient(mc -> {
            change.run();
            WynnLabs.syncServer();
        });
        ctx.waitTicks(30);                      // the status card reads the server twice a second
    }
}
