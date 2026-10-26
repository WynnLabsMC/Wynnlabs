package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.quests.GuideImages;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A guide picture as a texture: fetched one at a time in the background (one request at a time
 * keeps the wiki's load low), uploaded on the render thread, kept for the session.
 */
final class GuideImageCache {

    /** A loaded picture: its texture and its size in pixels. */
    record Tex(Identifier id, int w, int h) {}

    private static final Object LOADING = new Object();
    private static final Object FAILED = new Object();
    private static final Map<String, Object> STATE = new ConcurrentHashMap<>();
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wynnlabs-guide-images");
        t.setDaemon(true);
        return t;
    });

    private GuideImageCache() {}

    /** The picture's texture, or null while it loads (asked for once) or when it could not load. */
    static Tex get(String file) {
        Object s = STATE.putIfAbsent(file, LOADING);
        if (s == null) {
            ONE.execute(() -> load(file));
            return null;
        }
        return s instanceof Tex t ? t : null;
    }

    /** True when the picture could not be fetched or read: the strip leaves it out. */
    static boolean failed(String file) {
        return STATE.get(file) == FAILED;
    }

    private static void load(String file) {
        try {
            byte[] png = GuideImages.png(file);
            if (png == null) {
                STATE.put(file, FAILED);
                return;
            }
            Minecraft.getInstance().execute(() -> {
                try {
                    NativeImage img = NativeImage.read(png);
                    Identifier id = Identifier.fromNamespaceAndPath("wynnlabs",
                            "guide/" + Integer.toHexString(file.toLowerCase(Locale.ROOT).hashCode()));
                    Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "wynnlabs guide " + file, img));
                    STATE.put(file, new Tex(id, img.getWidth(), img.getHeight()));
                } catch (Exception e) {
                    STATE.put(file, FAILED);
                }
            });
        } catch (Exception e) {
            STATE.put(file, FAILED);
        }
    }
}
