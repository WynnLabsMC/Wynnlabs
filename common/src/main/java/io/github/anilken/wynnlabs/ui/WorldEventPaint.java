package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.quests.WorldEvents;
import io.github.anilken.wynnlabs.search.MapMark;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Draws a world event on Wynntils' main map: a blue area of the event's radius (brighter while it
 * is on, fainter while it waits to start), and above it the time to its start or "Active"; the
 * name joins the time while the mouse is over it. The event icon between them is Wynntils' own.
 */
public final class WorldEventPaint {

    private WorldEventPaint() {}

    private static final int BLUE = 0x3FA9F5;

    /** The area's radius on screen. */
    public static int radiusPx(WorldEvents.Shown s, float pxPerBlock) {
        return MapMark.radiusPx(Math.max(1, s.event().radius()), pxPerBlock);
    }

    /** Draws the area around (x, z) and returns its radius in pixels. */
    public static int area(GuiGraphics g, WorldEvents.Shown s, float x, float z, boolean hovered, float pxPerBlock) {
        int r = radiusPx(s, pxPerBlock);
        int cx = Math.round(x);
        int cz = Math.round(z);
        int fill = (s.active() ? (hovered ? 0x70 : 0x55) : (hovered ? 0x45 : 0x2A)) << 24 | BLUE;
        int ring = (s.active() ? 0xFF : 0xB0) << 24 | BLUE;
        MapMarkPaint.disc(g, cx, cz, r + 1, 0, 0xA0FFFFFF, 1);
        MapMarkPaint.disc(g, cx, cz, r, fill, ring, s.active() ? 3 : 2);
        return r;
    }

    /** The time (and on hover the name) centred above {@code top}. */
    public static void labels(GuiGraphics g, WorldEvents.Shown s, float x, float top, boolean hovered, long now) {
        Font font = Minecraft.getInstance().font;
        String time = s.active() ? Component.translatable("wynnlabs.worldevent.active").getString()
                : s.start() <= 0 ? Component.translatable("wynnlabs.worldevent.soon").getString() : clock(s.start() - now);
        String text = hovered ? s.event().name() + "  " + time : time;
        int w = font.width(text);
        int lx = Math.round(x) - w / 2;
        int ly = Math.round(top) - 12;
        g.fill(lx - 3, ly - 2, lx + w + 3, ly + 10, 0xC0101010);
        g.drawString(font, text, lx, ly, s.active() ? 0xFF7FD0FF : 0xFFFFFFFF, true);
    }

    /** "3:54", or "1:02:10" an hour or more away; "0:00" once due. */
    static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        long h = s / 3600;
        long m = s / 60 % 60;
        long sec = s % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
