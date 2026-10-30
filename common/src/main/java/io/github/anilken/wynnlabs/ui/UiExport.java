package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * "Export to WynnBuilder": a chip (or a 16×14 icon button when small) that puts a WynnBuilder
 * link on the clipboard. Its own face says how it went for two seconds: a green check and "Link
 * copied", or a red "Couldn't copy the link" when there was nothing to write. One per place
 * ({@link #HOME}, {@link #WINDOW}); only the clipboard is touched, never the game.
 */
final class UiExport {

    static final String HOME = "home";
    static final String WINDOW = "window";
    /** How long the chip shows how the copy went. */
    static final long SHOWN_MS = 2000;
    static final String ICON = "arrow-up-right";

    /** The place that copied last, whether it worked and when. */
    private static String place;
    private static boolean ok;
    private static long at;
    /** Where each was drawn last {x, y, w, h} (the previews hover it). */
    private static int[] homeBox;
    private static int[] windowBox;

    private UiExport() {}

    /** The previews: {@code where} shows copied (true) or failed, without touching the clipboard; null place: at rest. */
    static void preview(String where, boolean worked) {
        place = where;
        ok = worked;
        at = System.currentTimeMillis() + 3_600_000L;       // held for the shots
    }

    static int[] drawnAt(String where) {
        return HOME.equals(where) ? homeBox : windowBox;
    }

    /** Puts {@code link} on the clipboard for {@code where}; null shows the failure instead. */
    static void copy(String where, String link) {
        ok = link != null;
        if (ok) {
            Minecraft.getInstance().keyboardHandler.setClipboard(link);
        }
        place = where;
        at = System.currentTimeMillis();
    }

    /** 1 copied, -1 failed, 0 at rest, for {@code where} at {@code now}. */
    static int state(String where, long now) {
        if (place == null || !place.equals(where) || now - at >= SHOWN_MS) {
            return 0;
        }
        return ok ? 1 : -1;
    }

    private static String label(int state) {
        return L.t(state > 0 ? "wynnlabs.wb.copied" : state < 0 ? "wynnlabs.wb.copy_failed" : "wynnlabs.wb.export");
    }

    private static String icon(int state) {
        return state > 0 ? "check" : state < 0 ? "alert-triangle" : ICON;
    }

    /** The chip's width: with its words, else the icon button's. */
    static int width(Font font, String where, boolean words) {
        int s = state(where, System.currentTimeMillis());
        return words ? Ui.iconChipWidth(font, icon(s), label(s), false) : Ui.CHIP_H + 2;
    }

    /**
     * Draws it at {@code x, y}: with words or as an icon; {@code enabled} false draws it faded
     * (nothing to export). Returns its width.
     */
    static int draw(GuiGraphics g, Font font, String where, int x, int y, boolean words, boolean enabled, int mx, int my) {
        int s = state(where, System.currentTimeMillis());
        int w = width(font, where, words);
        int[] box = {x, y, w, Ui.CHIP_H};
        if (HOME.equals(where)) {
            homeBox = box;
        } else {
            windowBox = box;
        }
        boolean hover = enabled && mx >= x && mx < x + w && my >= y && my < y + Ui.CHIP_H;
        if (enabled) {
            Kit.pill(g, x, y, w, Ui.CHIP_H, false, hover);
        } else {
            Kit.hairPill(g, x, y, w, Ui.CHIP_H);
        }
        int ink = !enabled ? Theme.LINE : s > 0 ? Theme.GOOD : s < 0 ? Theme.BAD : Theme.INK_2;
        if (words) {
            String text = label(s);
            int ty = Type.BODY.yIn(y, Ui.CHIP_H);
            int cx = x + (w - (8 + Theme.SP_1 + Type.BODY.width(font, text))) / 2;
            Art.icon(g, icon(s), cx, Type.BODY.onCaps(ty, 8), 8, ink);
            Type.BODY.draw(g, font, text, cx + 8 + Theme.SP_1, ty, ink);
        } else {
            Art.icon(g, icon(s), x + Ui.iconIn(w, 8), y + Ui.iconIn(Ui.CHIP_H, 8), 8, ink);
        }
        return w;
    }

    /** The tooltip for what you wear now: what the link holds, then what was not read yet. */
    static List<Component> wornTip(boolean words) {
        List<Component> tip = new ArrayList<>();
        int s = state(HOME, System.currentTimeMillis());
        if (!words || s == 0) {
            tip.add(L.c("wynnlabs.wb.export").withStyle(ChatFormatting.WHITE));
        }
        if (s != 0) {
            tip.add(Component.literal(label(s)).withStyle(s > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
        }
        tip.add(L.c("wynnlabs.wb.export_tip").withStyle(ChatFormatting.GRAY));
        for (String key : new String[] {"wynnlabs.wb.part_gear", "wynnlabs.wb.part_skills", "wynnlabs.wb.part_tomes", "wynnlabs.wb.part_tree"}) {
            tip.add(Component.literal("  ").append(L.c(key)).withStyle(ChatFormatting.AQUA));
        }
        GameState state = GameState.current();
        if (state.inGame() && !state.skillsKnown()) {
            tip.add(L.c("wynnlabs.wb.skills_unread").withStyle(ChatFormatting.GOLD));
        }
        if (state.inGame() && state.tomes().isEmpty()) {
            tip.add(L.c("wynnlabs.wb.tomes_unread").withStyle(ChatFormatting.GOLD));
        }
        return tip;
    }
}
