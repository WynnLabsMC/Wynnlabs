package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.quests.GuideCoords;
import io.github.anilken.wynnlabs.quests.GuideImages;
import io.github.anilken.wynnlabs.quests.GuideLayout;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The stage shown in the guide: its title and the "Buradasın" seal, the task on a soft card (with
 * the compass button), the part and option chips, then its rows in a scrolling box, the pictures
 * in a strip kept above the footer, and after the rows the next stage (a short stage also the next
 * three and the quest's facts). Spacing comes from Theme.SP_*, text from the Type roles only.
 */
final class GuidePane {

    /** The mark column: a number or bullet ends SP_1 before its text, wrapped lines hang under the text. */
    static final int MARK_W = Theme.SP_6;
    /** The reading width: about 80 characters of body text; the rest of the pane is the "Yol ›" column. */
    static final int MEASURE = 360;

    private GuidePane() {}

    /** The "Yol ›" button's width: one size for all, so the column lines up. */
    static int goW(Font font) {
        return Type.BODY.width(font, L.t("wynnlabs.guide.go")) + 2 * Theme.SP_3;
    }

    /** "Yol ›" ({@link Ui#small}) centred on the capitals of the body line drawn at {@code lineY}. */
    static void goButton(GuideScreen s, GuiGraphics g, int mx, int my, int x, int lineY, Runnable action) {
        int y = Type.BODY.onCaps(lineY, Ui.SMALL_H);
        int w = goW(s.getFont());
        Ui.small(g, s.getFont(), L.t("wynnlabs.guide.go"), x, y, w, mx, my);
        s.tap(x, y, w, Ui.SMALL_H, action);
    }

    /** The brass diamond before a choice (kit/diamond, 5px), its top-left at x, y. */
    static void diamond(GuiGraphics g, int x, int y) {
        g.blitSprite(RenderPipelines.GUI_TEXTURED, net.minecraft.resources.Identifier.fromNamespaceAndPath("wynnlabs", "kit/diamond"), x, y, 5, 5);
    }

    static void draw(GuideScreen s, GuiGraphics g, int mx, int my, int x, int top, int w, int bottom, boolean large) {
        Font font = s.getFont();
        int n = s.stages.size();
        QuestWiki.Stage st = s.stages.get(s.sel);
        GuideLayout.View v = s.views.get(s.sel);
        List<int[]> points = new ArrayList<>(st.points());
        if (st.at() != null) {
            points.add(st.at());
        }
        // the stage's title, and the seal when it is the one you are on
        int y = top;
        String head = QuestWiki.stageTitle(st.title()) + " / " + n;
        Type.LABEL.draw(g, font, head, x, y, Theme.ACCENT);
        if (s.sel == s.current) {
            Ui.seal(g, font, L.t("wynnlabs.guide.you_are_here"), x + Type.LABEL.width(font, head) + Theme.SP_3,
                    Type.LABEL.onCaps(y, Ui.SEAL_H), Theme.SEAL_2);
        }
        y += Type.LABEL.lineHeight + Theme.SP_3;

        // what the stage shows: the common rows, the part's, the option's
        List<List<GuideLayout.Row>> groups = new ArrayList<>();
        groups.add(v.common());
        GuideLayout.Part part = v.parts().isEmpty() ? null : v.parts().get(Math.min(s.part, v.parts().size() - 1));
        if (part != null) {
            groups.add(part.common());
        }
        List<GuideLayout.Option> opts = s.options();
        if (!opts.isEmpty()) {
            groups.add(opts.get(Math.min(s.option, opts.size() - 1)).rows());
        }
        boolean empty = groups.stream().allMatch(List::isEmpty);

        y = task(s, g, font, mx, my, x, y, w, st, points, empty);
        y = chips(s, g, font, mx, my, x, y, w, v, part, opts);

        // the pictures: their strip's height kept first, above the footer; the rows get the rest
        List<GuideImages.Image> pics = s.images.getOrDefault(st.title(), List.of()).stream()
                .filter(p -> !GuideImageCache.failed(p.file())).toList();
        int th = large ? 44 : 30;
        int strip = pics.isEmpty() ? 0 : Theme.SP_5 + Type.META.lineHeight + Theme.SP_2 + th;
        if (strip > 0 && bottom - strip - y < 4 * Type.BODY.lineHeight) {
            th = 24;                                       // a short window: smaller pictures
            strip = Theme.SP_5 + Type.META.lineHeight + Theme.SP_2 + th;
        }
        int areaTop = y;
        int areaBottom = bottom - strip;

        List<GuideRows.Block> blocks = new ArrayList<>();
        if (part != null && part.usedFor() != null) {
            String used = L.t("wynnlabs.guide.used_for", part.usedFor());
            blocks.add(new GuideRows.Block(0, Type.LABEL.lineHeight, (gg, yy, mmx, mmy) -> {
                Type.LABEL.draw(gg, font, part.name(), x, yy, Theme.ACCENT);
                int cx = x + Type.LABEL.width(font, part.name()) + Theme.SP_3;
                Type.META.draw(gg, font, used, cx, Type.META.yIn(Type.LABEL.onCaps(yy, Ui.SEAL_H), Ui.SEAL_H));
                cx += Type.META.width(font, used) + Theme.SP_3;
                Kit.hair(gg, cx, yy + 6, x + w - cx, 1, Theme.LINE);
            }));
        }
        blocks.addAll(GuideRows.build(s, font, groups, points, x, w));
        if (empty) {
            blocks.add(new GuideRows.Block(0, Type.MUTED.lineHeight,
                    (gg, yy, mmx, mmy) -> Type.MUTED.draw(gg, font, L.t("wynnlabs.guide.no_hints"), x, yy)));
        }
        int rowsH = height(blocks);
        boolean short_ = rowsH < (areaBottom - areaTop) * 55 / 100;
        GuideTail.add(s, font, blocks, x, w, short_);

        // the rows, scrolled inside their box
        int total = height(blocks);
        int box = areaBottom - areaTop;
        s.scroll = Math.max(0, Math.min(s.scroll, total - box));
        g.enableScissor(x - Theme.SP_4, areaTop - Theme.SP_1, x + w + Theme.SP_4, areaBottom);
        s.clip = new int[] {x - Theme.SP_4, areaTop - Theme.SP_1, w + 2 * Theme.SP_4, box + Theme.SP_1};
        int by = areaTop - s.scroll;
        for (int i = 0; i < blocks.size(); i++) {
            GuideRows.Block b = blocks.get(i);
            by += i == 0 ? 0 : b.gap();
            if (by + b.h() > areaTop && by < areaBottom) {
                b.paint().draw(g, by, mx, my);
            }
            by += b.h();
        }
        s.clip = null;
        g.disableScissor();
        // where in the stage you are: a thin bar on the right edge, dragged or clicked
        s.paneBar.place(x + w + Theme.SP_2, areaTop, box, box, total, s.scroll);
        s.paneBar.drawThin(g, mx, my);
        if (!pics.isEmpty()) {
            pictures(s, g, font, mx, my, x, areaBottom + Theme.SP_5, w, th, pics);
        }
    }

    private static int height(List<GuideRows.Block> blocks) {
        int h = 0;
        for (int i = 0; i < blocks.size(); i++) {
            h += (i == 0 ? 0 : blocks.get(i).gap()) + blocks.get(i).h();
        }
        return h;
    }

    /**
     * The task on a soft card, its coordinates with their pins. Its place's button: "Yol ›" when the
     * task names no coordinates inline; on a stage with no steps a larger "Show on compass ›".
     */
    private static int task(GuideScreen s, GuiGraphics g, Font font, int mx, int my, int x, int y, int w,
                            QuestWiki.Stage st, List<int[]> points, boolean empty) {
        String text = QuestWiki.taskText(st.task());
        if (st.task().isBlank()) {
            return y;
        }
        int[] at = st.at() != null ? st.at() : !st.points().isEmpty() ? st.points().get(0) : QuestWiki.placeIn(st.task());
        boolean inline = !GuideCoords.find(text, points).isEmpty();
        boolean button = at != null && (empty || !inline);
        String label = L.t(empty ? "wynnlabs.guide.compass" : "wynnlabs.guide.go");
        int bw = empty ? Ui.buttonWidth(font, label) : goW(font);
        int pad = Theme.SP_4;
        int tw = Math.min(MEASURE + goW(font), w - 2 * pad - (button ? bw + Theme.SP_5 : 0));
        List<List<GuideText.Tok>> lines = GuideText.wrap(font, Type.LEAD, text, points, tw);
        String dist = button ? GuideScreen.distance(at) : null;
        int bh = empty ? Ui.BUTTON_H : Ui.SMALL_H;
        int side = button ? bh + (dist != null ? Theme.SP_1 + Type.META.lineHeight : 0) : 0;
        int h = Math.max(GuideText.height(Type.LEAD, lines), side) + 2 * pad;
        Kit.softCard(g, x, y, w, h);
        GuideText.draw(g, font, Type.LEAD, lines, x + pad, y + pad, Type.LEAD.colour, mx, my, s, true);
        if (button) {
            int bx = x + w - pad - bw;
            int by = y + pad;
            if (empty) {
                Ui.button(g, font, label, bx, by, bw, mx, my);
                s.tap(bx, by, bw, Ui.BUTTON_H, () -> s.compass(at, true));
            } else {
                Ui.small(g, font, label, bx, by, bw, mx, my);
                s.tap(bx, by, bw, Ui.SMALL_H, () -> s.compass(at, true));
            }
            if (dist != null) {
                Type.META.draw(g, font, dist, bx + (bw - Type.META.width(font, dist)) / 2, by + bh + Theme.SP_1);
            }
        }
        return y + h + 2 + Theme.SP_4;
    }

    /**
     * The part chips (a dot on the parts the picked option needs: "Used for: Brie"), then the option
     * chips, one per name, the ones with the same steps as the picked one underlined. Options that
     * all read the same: one line naming them.
     */
    private static int chips(GuideScreen s, GuiGraphics g, Font font, int mx, int my, int x, int y, int w,
                             GuideLayout.View v, GuideLayout.Part part, List<GuideLayout.Option> opts) {
        if (v.parts().size() > 1) {
            List<Chip> cs = new ArrayList<>();
            String picked = s.picked();
            for (int i = 0; i < v.parts().size(); i++) {
                GuideLayout.Part p = v.parts().get(i);
                boolean dot = picked != null && p.usedFor() != null && List.of(p.usedFor().split(",\\s*")).contains(picked);
                int k = i;
                cs.add(new Chip(p.name(), i == s.part, false, dot, () -> s.pickPart(k)));
            }
            y = row(s, g, font, mx, my, x, y, w, cs) + Theme.SP_4;
        }
        if (opts.size() > 1) {
            String picked = s.pickedLabel();
            List<String> group = opts.get(Math.min(s.option, opts.size() - 1)).labels();
            List<Chip> cs = new ArrayList<>();
            for (GuideLayout.Option o : opts) {
                for (String label : o.labels()) {
                    cs.add(new Chip(label, label.equals(picked), group.contains(label) && !label.equals(picked), false,
                            () -> s.pickOption(label)));
                }
            }
            y = row(s, g, font, mx, my, x, y, w, cs) + Theme.SP_4;
        } else if (opts.size() == 1 && opts.get(0).labels().size() > 1) {
            String same = L.t("wynnlabs.guide.same", String.join(", ", opts.get(0).labels()));
            Type.META.draw(g, font, Type.META.fit(font, same, w), x, y);
            y += Type.META.lineHeight + Theme.SP_4;
        }
        return y;
    }

    private record Chip(String label, boolean on, boolean twin, boolean dot, Runnable pick) {}

    /** Chips as pills, wrapping to more rows when they do not fit; returns the y under them. */
    private static int row(GuideScreen s, GuiGraphics g, Font font, int mx, int my, int x, int y, int w, List<Chip> chips) {
        int cx = x;
        for (Chip c : chips) {
            int cw = Ui.chipWidth(font, c.label()) + (c.dot() ? Theme.SP_3 : 0);
            if (cx > x && cx + cw > x + w) {
                cx = x;
                y += Ui.CHIP_H + Theme.SP_2;
            }
            Ui.chip(g, font, c.label(), cx, y, cw, c.on(), c.twin(), c.dot(), mx, my);
            s.tap(cx, y, cw, Ui.CHIP_H, c.pick());
            cx += cw + Theme.SP_2;
        }
        return y + Ui.CHIP_H;
    }

    /** The stage's pictures in a strip at {@code y}: a click shows one large; what does not fit is "+N". */
    private static void pictures(GuideScreen s, GuiGraphics g, Font font, int mx, int my, int x, int y, int w, int th,
                                 List<GuideImages.Image> pics) {
        Type.META.draw(g, font, L.t("wynnlabs.guide.images", pics.size()), x, y);
        int py = y + Type.META.lineHeight + Theme.SP_2;
        int px = x;
        String more = null;
        for (int i = 0; i < pics.size(); i++) {
            GuideImages.Image p = pics.get(i);
            GuideImageCache.Tex t = GuideImageCache.get(p.file());
            int tw = t == null ? th : Math.max(12, Math.min(th * 5 / 2, t.w() * th / Math.max(1, t.h())));
            int left = pics.size() - i;
            String plus = "+" + left;
            int plusW = Type.META.width(font, plus) + 2 * Theme.SP_3;
            if (px + tw > x + w || left > 1 && px + tw + Theme.SP_3 + plusW > x + w) {
                more = plus;
                break;
            }
            boolean hover = t != null && mx >= px && mx < px + tw && my >= py && my < py + th;
            if (t == null) {
                g.fill(px, py, px + tw, py + th, Theme.WELL);
                Type.META.draw(g, font, "…", px + tw / 2 - 3, py + th / 2 - 5);
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, t.id(), px, py, 0, 0, tw, th, t.w(), t.h(), t.w(), t.h());
                s.tap(px, py, tw, th, () -> s.zoom = p);
            }
            int edge = hover ? Theme.GOLD_EDGE : Theme.LINE;
            Kit.hair(g, px, py - 1, tw, 1, edge);
            Kit.hair(g, px, py + th, tw, 1, edge);
            Kit.hair(g, px - 1, py, 1, th, edge);
            Kit.hair(g, px + tw, py, 1, th, edge);
            px += tw + Theme.SP_3;
        }
        if (more != null) {
            int mw = Type.META.width(font, more) + 2 * Theme.SP_3;
            Ui.chip(g, font, more, px, py + (th - Ui.CHIP_H) / 2, Math.max(mw, Ui.chipWidth(font, more)), false, false, false, mx, my);
        }
    }

    /** A large number short: 4,200,000 → "4.2M", 242,500 → "242.5k". */
    static String compact(long v) {
        if (v >= 1_000_000) {
            return String.format(Locale.ROOT, "%.1fM", v / 1e6).replace(".0M", "M");
        }
        if (v >= 10_000) {
            return String.format(Locale.ROOT, "%.1fk", v / 1e3).replace(".0k", "k");
        }
        return String.valueOf(v);
    }
}
