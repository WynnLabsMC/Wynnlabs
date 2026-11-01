package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.endgame.AspectBook;
import io.github.anilken.wynnlabs.endgame.ItemIcons;
import io.github.anilken.wynnlabs.model.AspectEntry;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.PoolItem;
import io.github.anilken.wynnlabs.model.RankedAspect;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The aspects that suit the character's abilities, in one popup over the Gear panel's Aspects chip:
 * the Endgame tab's ranking, those it ties to an ability or that are equipped, each with its picture
 * and the tier owned. A short list, so it does not scroll. Nothing here changes the build. A class of
 * its own made on first use (safe to hot swap in).
 */
final class HomeAspects {

    private static final int W = 230;
    private static final int ICON = 12;
    private static final int ROW_H = ICON + Theme.SP_2;
    private static final int TIER_W = 20;
    private static final int TICK = 10;
    private static final int MAX = 8;

    private boolean open;
    private int[] at;

    boolean open() {
        return open;
    }

    void openMenu(int[] chip) {
        at = chip;
        open = true;
    }

    void close() {
        open = false;
    }

    /** The rows, best first; empty for a class whose aspects are not known yet. */
    static List<RankedAspect> shown() {
        return AspectBook.ranked().stream().filter(r -> !r.matches().isEmpty() || r.equipped()).limit(MAX).toList();
    }

    int[] drawList(GuiGraphics g, HomeTab.Host host, int mx, int my) {
        List<RankedAspect> rows = shown();
        if (!open || at == null || rows.isEmpty()) {
            open = false;
            return null;
        }
        Font font = UiFont.get();
        var mc = net.minecraft.client.Minecraft.getInstance();
        int sw = mc.screen == null ? 10_000 : mc.screen.width;
        int sh = mc.screen == null ? 10_000 : mc.screen.height;
        int[] bounds = UiMenu.bounds(UiMenu.Spec.under(at, sw, sh));
        int room = Math.max(bounds[3] - (at[1] + at[3]), at[1] - bounds[1]) - Theme.SP_1 - 2 * Theme.SP_2;
        int head = Type.BODY.lineHeight + Theme.SP_2;
        int fits = Math.min(rows.size(), Math.max(1, (room - head) / ROW_H));
        int w = Math.min(W, bounds[2] - bounds[0] - 2 * Theme.SP_2);
        int[] b = UiMenu.place(at, w, head + fits * ROW_H + 2 * Theme.SP_2, bounds);
        Kit.softCard(g, b[0], b[1], w, b[3]);
        int x = b[0] + Theme.SP_3;
        int rw = w - 2 * Theme.SP_3;
        int y = b[1] + Theme.SP_2;
        Type.BODY.draw(g, font, L.t("wynnlabs.aspects.title"), x, y, Theme.INK);
        y += head;
        for (RankedAspect r : rows.subList(0, fits)) {
            row(g, font, host, r, x, y, rw);
            y += ROW_H;
        }
        return b;
    }

    private static void row(GuiGraphics g, Font font, HomeTab.Host host, RankedAspect r, int x, int y, int w) {
        AspectEntry a = r.aspect();
        Klass k = a.klass() == null ? Klass.NONE : a.klass();
        String subtype = k.name().charAt(0) + k.name().substring(1).toLowerCase(Locale.ROOT) + "Aspect";
        ItemIcons.draw(g, new PoolItem(a.name(), "AspectItem", a.rarity(), subtype, 1), k, x, y, ICON, false);
        int textY = y + (ICON - Type.BODY.lineHeight) / 2 + 1;
        int nameX = x + ICON + Theme.SP_2;
        int tierX = x + w - TIER_W;
        int room = tierX - Theme.SP_2 - nameX - (r.equipped() ? TICK : 0);
        Type.BODY.draw(g, font, Type.BODY.fit(font, a.name(), room), nameX, textY, Theme.INK);
        if (r.equipped()) {
            Art.icon(g, "check", tierX - TICK, y + 2, 8, Theme.GOOD);
        }
        if (r.ownedTier() >= 0) {
            String tier = r.ownedTier() + "/" + a.tiers();
            Type.META.draw(g, font, tier, x + w - font.width(tier), textY, r.maxed() ? Theme.GOOD : Theme.INK_2);
        }
        host.area(x, y, w, ROW_H, null, tip(r), null);
    }

    private static List<Component> tip(RankedAspect r) {
        List<Component> out = new ArrayList<>();
        out.add(Component.literal(r.aspect().name()).withStyle(ChatFormatting.WHITE));
        if (!r.reason().isEmpty()) {
            out.add(Component.literal(r.reason()).withStyle(ChatFormatting.GRAY));
        }
        if (r.equipped()) {
            out.add(L.c("wynnlabs.endgame.tip_equipped").withStyle(ChatFormatting.GOLD));
        }
        return out;
    }
}
