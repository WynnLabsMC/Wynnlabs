package io.github.anilken.wynnlabs.ui;

/**
 * A screen built on another mod's screen class answers keys, typed characters and the scroll
 * wheel through these instead of overriding Minecraft's own methods: the build cannot rename an
 * override whose parent comes from a mod jar, so the game would never call it.
 * {@code mixin/RawInputMixin} calls them before the screen sees the event; true means handled.
 */
public interface RawInput {

    /** A key press or repeat (GLFW key code). */
    boolean rawKey(int key);

    /** A typed character. */
    boolean rawChar(int codepoint);

    /** The scroll wheel at (mx, my) in GUI coordinates. */
    boolean rawScroll(double mx, double my, double amount);
}
