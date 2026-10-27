package io.github.anilken.wynnlabs.core;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * The mod's chat lines in one style: "Wynn Labs ▸ " in gold, then the line. Every sender goes
 * through here, so the death coach, the updater, the menus and the level-up note read alike.
 */
public final class Chat {

    private Chat() {}

    /** The prefix and {@code body}, for a line built further (a click event, more parts). */
    public static MutableComponent prefixed(Component body) {
        return L.c("wynnlabs.wear.prefix").withStyle(ChatFormatting.GOLD).append(body);
    }

    /** The prefix and a lang line in one colour. */
    public static MutableComponent line(String key, ChatFormatting colour, Object... args) {
        return prefixed(L.c(key, args).withStyle(colour));
    }

    /** Says {@code body} after the prefix. */
    public static void say(Component body) {
        show(prefixed(body));
    }

    /** Shows a line as it is: one made with {@link #prefixed}, or a follow-on line under one. */
    public static void show(Component line) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.getChat().addMessage(line);
        }
    }
}
