package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.Consumer;

/**
 * Every text field of the mod, one look (the approved component sheet): a thin pill the height of
 * a chip with SP_3 inside it, the typed text in BODY, the hint in faint META while it is empty (it
 * stays while the caret blinks), a gold ring while typed in, a red one on an invalid value, and
 * faded when it is off.
 *
 * <p>{@link #search} adds the magnifier, a "/" on the right while empty (the key that focuses it)
 * and an ✕ that clears it once there is text; where there is no room (a compact window) the
 * page draws {@link #icon} instead, an icon button that opens the field. {@link #input} is the
 * plain field (names, numbers, the API key).
 *
 * <p>The page owns its {@link EditBox} (made with {@link #box}); this draws it, takes its clicks and
 * its Esc, and returns this frame's areas as a {@link Hit}.
 */
final class UiInput {

    /** The field's height: a chip's. */
    static final int H = Ui.CHIP_H;
    /** Narrower than this, the page shows {@link #icon} instead of the search field. */
    static final int MIN_W = 90;

    /** This frame's areas: the whole field, and the ✕ (null while it is empty or for a plain field). */
    record Hit(int[] field, int[] clear) {
    }

    private UiInput() {
    }

    /** A page's box: borderless ink on paper, at most 40 characters, the typed text kept from {@code old}. */
    static EditBox box(Font font, String hintKey, EditBox old, Consumer<String> responder) {
        String typed = old == null ? "" : old.getValue();
        EditBox box = new EditBox(font, 0, 0, 100, 10, io.github.anilken.wynnlabs.core.L.c(hintKey));
        box.setMaxLength(40);
        MenuPaint.paperField(box);
        box.setValue(typed);
        box.setResponder(responder);
        return box;
    }

    /**
     * The search field at {@code x, y}, {@code w} wide: the pill, the magnifier, the box, then the
     * hint and "/" when empty, or the ✕ when not. {@code slash}: whether "/" focuses it on this page.
     */
    static Hit search(GuiGraphics g, Font font, EditBox box, String hint, boolean slash, int x, int y, int w, int mx, int my) {
        boolean focused = box.isFocused();
        pill(g, x, y, w, true, false, focused, mx, my);
        Art.icon(g, "search", x + Theme.SP_3, y + Ui.iconIn(H, 8), 8, focused ? Theme.INK : Theme.INK_3);
        int tx = x + Theme.SP_3 + 8 + Theme.SP_2;
        boolean empty = box.getValue().isEmpty();
        int end = x + w - Theme.SP_3 - (empty ? 0 : 6 + Theme.SP_2);
        text(g, font, box, empty ? hint : null, slash && !focused, tx, end, x + w, y, mx, my);
        int[] clear = null;
        if (!empty) {
            int cx = x + w - Theme.SP_3 - 6;
            clear = new int[] {cx - Theme.SP_1, y, 6 + Theme.SP_1 + Theme.SP_3, H};
            boolean on = mx >= clear[0] && mx < clear[0] + clear[2] && my >= y && my < y + H;
            Art.icon(g, "x", cx, y + Ui.iconIn(H, 6), 6, on ? Theme.INK : Theme.INK_3);
        }
        return new Hit(new int[] {x, y, w, H}, clear);
    }

    /**
     * A plain field (a name, a number, a key) at {@code x, y}, {@code w} wide: the pill, the box,
     * the hint while empty; {@code error} rings it red (the caller says why under it, META BAD),
     * {@code enabled} false fades it and draws no box. Returns its area (null when off).
     */
    static int[] input(GuiGraphics g, Font font, EditBox box, String hint, int x, int y, int w, boolean enabled, boolean error,
            int mx, int my) {
        pill(g, x, y, w, enabled, error, enabled && box.isFocused(), mx, my);
        int tx = x + Theme.SP_3;
        int end = x + w - Theme.SP_3;
        if (!enabled) {
            String shown = box.getValue().isEmpty() ? hint : box.getValue();
            Type.META.draw(g, font, Type.META.fit(font, shown, end - tx), tx, Type.META.yIn(y, H), Theme.LINE);
            return null;
        }
        box.setTextColor(error ? Theme.BAD : Theme.INK);
        text(g, font, box, box.getValue().isEmpty() ? hint : null, false, tx, end, x + w, y, mx, my);
        return new int[] {x, y, w, H};
    }

    /** The field's pill: hover lit, then the gold focus ring or the red error ring over it; faded when off. */
    private static void pill(GuiGraphics g, int x, int y, int w, boolean enabled, boolean error, boolean focused, int mx, int my) {
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + H;
        if (!enabled) {
            Kit.hairPill(g, x, y, w, H);
            return;
        }
        Kit.pill(g, x, y, w, H, false, hover || focused);
        if (error || focused) {
            Kit.ring(g, x, y, w, H, error);
        }
    }

    /** The box placed between {@code tx} and {@code end} and drawn, then the hint (and "/") while it is empty. */
    private static void text(GuiGraphics g, Font font, EditBox box, String hint, boolean slash, int tx, int end, int right, int y,
            int mx, int my) {
        box.setX(tx);
        box.setY(Type.BODY.yIn(y, H));
        box.setWidth(Math.max(10, end - tx));
        box.render(g, mx, my, 0f);
        if (hint == null) {
            return;
        }
        int slashW = slash ? Type.META.width(font, "/") + Theme.SP_2 : 0;
        Type.META.draw(g, font, Type.META.fit(font, hint, end - tx - slashW), tx, Type.META.yIn(y, H), Theme.INK_3);
        if (slash) {
            Type.META.drawRight(g, font, "/", right - Theme.SP_3, Type.META.yIn(y, H), Theme.LINE);
        }
    }

    /** The compact stand-in: the magnifier as an icon button, gold while the field is open or holds text; returns its width. */
    static int icon(GuiGraphics g, EditBox box, boolean open, int x, int y, int mx, int my) {
        Ui.iconButton(g, "search", x, y, open || box != null && !box.getValue().isBlank(), mx, my);
        return H + 2;
    }

    /**
     * A press: on the ✕ it clears the box, on the field it focuses it and places the caret; true
     * when it took the press. A press elsewhere is left to the page (which may unfocus the box).
     */
    static boolean clicked(Hit hit, EditBox box, MouseButtonEvent event, boolean doubleClick, Consumer<EditBox> focus) {
        if (box == null) {
            return false;
        }
        if (hit == null || !in(event.x(), event.y(), hit.field())) {
            return false;
        }
        if (hit.clear() != null && in(event.x(), event.y(), hit.clear())) {
            box.setValue("");
        } else {
            box.mouseClicked(event, doubleClick);
        }
        box.setFocused(true);
        focus.accept(box);
        return true;
    }

    /** "/" pressed on the page: the box takes the focus (it starts empty of the slash). */
    static boolean focus(EditBox box, Consumer<EditBox> focus) {
        if (box == null) {
            return false;
        }
        box.setFocused(true);
        focus.accept(box);
        return true;
    }

    /** Esc while typing in a box with text clears it (a second Esc closes the menu); true when it did. */
    static boolean escape(EditBox box, int key) {
        if (box != null && box.isFocused() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !box.getValue().isEmpty()) {
            box.setValue("");
            return true;
        }
        return false;
    }

    private static boolean in(double x, double y, int[] r) {
        return r != null && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }
}
