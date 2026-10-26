package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Wyn;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the quest map draws over its tiles: the markers (a dot, a leader, a number chip), the
 * route, the chips on the border for places out of view, place names that keep clear of all of
 * them, and you (an arrow on the map, or a pill on the border toward you).
 */
final class QuestMapMarks extends MenuPaint {

    private final QuestMap map;

    QuestMapMarks(RoadmapScreen menu, QuestMap map) {
        super(menu);
        this.map = map;
    }

    /** A marker's click area: its places in the tooltip, the compass on the first. */
    void hits(List<QuestMap.Place> places, List<String> nums, int x, int y, int w, int h) {
        List<String> n = sorted(nums);
        QuestMap.Place first = place(places, n.get(0));
        List<Component> tip = new ArrayList<>();
        for (String num : n) {
            tip.add(Component.literal(num + " · " + place(places, num).task())
                    .withStyle(num.equals(n.get(0)) ? ChatFormatting.WHITE : ChatFormatting.GRAY));
        }
        tip.add(L.c("wynnlabs.quests.point_click").withStyle(ChatFormatting.DARK_GRAY));
        menu.hits.add(new MenuHit(x, y, w, h, null, tip, "/compass at " + first.at()[0] + " " + first.at()[1] + " " + first.at()[2]));
    }

    /** The chips for places out of view, on the border toward them; places in one direction share a chip. */
    List<double[]> edgeChips(List<QuestMapLayout.Marker> outside, int w, int h, List<double[]> avoid) {
        List<double[]> out = new ArrayList<>();
        List<double[]> placed = new ArrayList<>(avoid);
        for (QuestMapLayout.Group gr : edgeGroups(outside)) {
            double cw = Type.MICRO.width(font(), gr.label()) + 2 * Theme.SP_2 + 8;
            double[] at = QuestMapLayout.edge(gr.x(), gr.y(), cw, QuestMap.CHIP_H, w, h, Theme.SP_2);
            at = QuestMapLayout.onBorder(at, cw, QuestMap.CHIP_H, w, h, Theme.SP_2, placed);
            double[] box = {at[0], at[1], cw, QuestMap.CHIP_H};
            out.add(box);
            placed.add(box);
        }
        return out;
    }

    /**
     * Places out of view that lie close together in the world (within 40 px of the view's scale)
     * share an edge chip; places of different stages far apart never do.
     */
    static List<QuestMapLayout.Group> edgeGroups(List<QuestMapLayout.Marker> outside) {
        List<QuestMapLayout.Group> out = new ArrayList<>();
        for (QuestMapLayout.Marker m : outside) {
            boolean joined = false;
            for (int i = 0; i < out.size() && !joined; i++) {
                QuestMapLayout.Group g = out.get(i);
                if (Math.hypot(g.x() - m.x(), g.y() - m.y()) < 40 && (g.state() == QuestMapLayout.DONE) == (m.state() == QuestMapLayout.DONE)) {
                    g.nums().add(m.num());
                    joined = true;
                }
            }
            if (!joined) {
                List<String> n = new ArrayList<>();
                n.add(m.num());
                out.add(new QuestMapLayout.Group(n, m.x(), m.y(), m.state()));
            }
        }
        return out;
    }

    static boolean free(double[] at, double cw, List<double[]> boxes) {
        for (double[] b : boxes) {
            if (at[0] < b[0] + b[2] + 2 && b[0] < at[0] + cw + 2 && at[1] < b[1] + b[3] + 2 && b[1] < at[1] + QuestMap.CHIP_H + 2) {
                return false;
            }
        }
        return true;
    }

    /** The edge chips: an arrow toward the places and their numbers, dark with a gold edge. */
    void drawEdges(GuiGraphics g, int x, int y, List<QuestMapLayout.Marker> outside, List<double[]> boxes, List<QuestMap.Place> places,
                           String lit, int mx, int my) {
        List<QuestMapLayout.Group> groups = edgeGroups(outside);
        for (int i = 0; i < groups.size() && i < boxes.size(); i++) {
            QuestMapLayout.Group gr = groups.get(i);
            double[] b = boxes.get(i);
            int bx = x + (int) Math.round(b[0]);
            int by = y + (int) Math.round(b[1]);
            int cw = (int) Math.round(b[2]);
            boolean over = mx >= bx && mx < bx + cw && my >= by && my < by + QuestMap.CHIP_H;
            boolean hot = over || lit != null && gr.nums().contains(lit);
            if (over) {
                map.hovered = gr.nums().get(0);
            }
            boolean done = gr.state() == QuestMapLayout.DONE;
            Art.box(g, bx, by, cw, QuestMap.CHIP_H, 5, 0xF01A130C, hot ? QuestMap.PALE : done ? QuestMap.FADED : QuestMap.GOLD);
            double ang = Math.atan2(gr.y() - (b[1] + QuestMap.CHIP_H / 2.0), gr.x() - (b[0] + cw / 2.0));
            arrow(g, bx + Theme.SP_2 + 3, by + QuestMap.CHIP_H / 2, (float) (ang + Math.PI / 2), 6, done ? QuestMap.FADED : QuestMap.PALE);
            Type.MICRO.draw(g, font(), gr.label(), bx + Theme.SP_2 + 8, Type.MICRO.yIn(by, QuestMap.CHIP_H), done ? 0xFFB8A988 : QuestMap.PALE);
            hits(places, gr.nums(), bx, by, cw, QuestMap.CHIP_H);
        }
    }

    static List<String> sorted(List<String> nums) {
        List<String> n = new ArrayList<>(nums);
        n.sort(null);
        return n;
    }

    static QuestMap.Place place(List<QuestMap.Place> places, String num) {
        for (QuestMap.Place p : places) {
            if (p.num().equals(num)) {
                return p;
            }
        }
        return places.get(0);
    }

    double chipWidth(QuestMapLayout.Group gr) {
        String t = gr.state() == QuestMapLayout.START ? L.t("wynnlabs.quests.map_start") : gr.label();
        return Type.MICRO.width(font(), t) + 2 * Theme.SP_2 + (gr.state() == QuestMapLayout.DONE ? 8 : 0);
    }

    /** A dot on the spot, a leader to its chip, the chip with its number; lit: a gold glow behind both. */
    void marker(GuiGraphics g, QuestMapLayout.Group gr, int dx, int dy, int bx, int by, int cw, boolean hot) {
        int st = gr.state();
        int dot = switch (st) {
            case QuestMapLayout.NOW -> QuestMap.GOLD;
            case QuestMapLayout.DONE -> QuestMap.FADED;
            case QuestMapLayout.START -> QuestMap.START;
            default -> QuestMap.CREAM;
        };
        // the leader ends on the chip's nearest edge
        int ex = Math.max(bx, Math.min(bx + cw, dx));
        int ey = Math.max(by, Math.min(by + QuestMap.CHIP_H, dy));
        line(g, dx, dy, ex, ey, hot ? QuestMap.PALE : 0xC8F3E6C4);
        if (hot) {
            Art.box(g, dx - 6, dy - 6, 13, 13, 6, 0x55FFE071, 0);
            Art.box(g, bx - 2, by - 2, cw + 4, QuestMap.CHIP_H + 4, 6, 0x55FFE071, 0);
        }
        int r = st == QuestMapLayout.DONE ? 2 : 3;
        Art.box(g, dx - r, dy - r, 2 * r + 1, 2 * r + 1, r, dot, QuestMap.DARK);
        int fill = switch (st) {
            case QuestMapLayout.NOW -> QuestMap.GOLD;
            case QuestMapLayout.START -> QuestMap.YOU;
            default -> 0xF01A130C;
        };
        int edge = hot ? QuestMap.PALE : st == QuestMapLayout.NOW || st == QuestMapLayout.START ? QuestMap.DARK : st == QuestMapLayout.DONE ? QuestMap.FADED : QuestMap.GOLD;
        int ink = switch (st) {
            case QuestMapLayout.NOW -> QuestMap.DARK;
            case QuestMapLayout.DONE -> 0xFFB8A988;
            case QuestMapLayout.START -> QuestMap.CREAM;
            default -> QuestMap.PALE;
        };
        Art.box(g, bx, by, cw, QuestMap.CHIP_H, 5, fill, edge);
        int tx = bx + Theme.SP_2;
        if (st == QuestMapLayout.DONE) {
            Art.icon(g, "check", tx, by + 2, 7, ink);
            tx += 8;
        }
        String t = st == QuestMapLayout.START ? L.t("wynnlabs.quests.map_start") : gr.label();
        Type.MICRO.draw(g, font(), t, tx, Type.MICRO.yIn(by, QuestMap.CHIP_H), ink);
    }

    /**
     * The route: a dotted pale-gold line through the places in their stage order, each where it
     * really is in the view now (panned or not), every leg clipped to the frame; a leg toward a
     * place out of view runs to the border on its true way, never to that place's edge chip.
     */
    void route(GuiGraphics g, int x, int y, int w, int h, QuestMapLayout.Fit f, List<QuestMap.Place> places) {
        List<double[]> xz = new ArrayList<>();
        for (QuestMap.Place p : places) {
            xz.add(new double[] {p.at()[0], p.at()[2]});
        }
        for (double[] leg : QuestMapLayout.route(map.route, f, xz, w, h)) {
            int n = (int) (Math.hypot(leg[2] - leg[0], leg[3] - leg[1]) / 3);
            for (int k = 1; k < n; k++) {
                double t = k / (double) n;
                dotAt(g, x + leg[0] + (leg[2] - leg[0]) * t, y + leg[1] + (leg[3] - leg[1]) * t, 0xC8FFE071);
            }
        }
    }

    /** A 2-screen-pixel dot at a fractional position. */
    static void dotAt(GuiGraphics g, double px, double py, int colour) {
        float s = screenScale(g);
        g.pose().pushMatrix();
        g.pose().translate((float) px, (float) py);
        g.pose().scale(1f / s, 1f / s);
        g.fill(-1, -1, 1, 1, colour);
        g.pose().popMatrix();
    }

    /** A one-screen-pixel line from (ax, ay) to (bx, by). */
    static void line(GuiGraphics g, int ax, int ay, int bx, int by, int colour) {
        double len = Math.hypot(bx - ax, by - ay);
        if (len < 1) {
            return;
        }
        float s = screenScale(g);
        g.pose().pushMatrix();
        g.pose().translate(ax, ay);
        g.pose().rotate((float) Math.atan2(by - ay, bx - ax));
        g.pose().scale(1f / s, 1f / s);
        g.fill(0, 0, Math.round((float) len * s), 1, colour);
        g.pose().popMatrix();
    }

    static float screenScale(GuiGraphics g) {
        try {
            return Math.max(1f, (float) Minecraft.getInstance().getWindow().getGuiScale() * g.pose().m00());
        } catch (Throwable t) {
            return 1f;
        }
    }

    /**
     * QuestMap.Place names (Wynntils' labels): below, above, left or right of the spot, the first that stays
     * inside the frame and off every dot and chip; shortened with "…"; one on a small map, two on a large.
     */
    void names(GuiGraphics g, int x, int y, int w, int h, float x0, float z0, float x1, float z1, QuestMapLayout.Fit f,
                       List<double[]> dots, List<double[]> taken0) {
        int max = w < 200 ? 1 : 2;
        int inset = Theme.SP_2;
        int shown = 0;
        List<double[]> taken = new ArrayList<>(taken0);
        for (Wyn.MapLabel l : QuestsCache.labels()) {
            if (l.x() < x0 + 12 || l.x() > x1 - 12 || l.z() < z0 + 8 || l.z() > z1 - 8) {
                continue;
            }
            if (shown >= max) {
                break;
            }
            double nx = f.px(l.x());
            double ny = f.py(l.z());
            for (String side : new String[] {"below", "above", "left", "right"}) {
                int room = side.equals("left") ? (int) nx - inset - 6 : side.equals("right") ? w - inset - (int) nx - 6 : w - 2 * inset;
                String text = Type.META.fit(font(), l.name(), Math.max(0, room));
                int nw = Type.META.width(font(), text);
                if (nw < 24) {
                    continue;
                }
                double tx = switch (side) {
                    case "left" -> nx - 6 - nw;
                    case "right" -> nx + 6;
                    default -> nx - nw / 2.0;
                };
                double ty = switch (side) {
                    case "below" -> ny + 4;
                    case "above" -> ny - 4 - Type.META.lineHeight;
                    default -> ny - Type.META.lineHeight / 2.0;
                };
                tx = Math.max(inset, Math.min(w - inset - nw, tx));
                if (!QuestMapLayout.inside(tx, ty, nw, Type.META.lineHeight, w, h, inset)) {
                    continue;
                }
                double[] box = {tx - 2, ty, nw + 4, Type.META.lineHeight};
                boolean clear = true;
                for (double[] d : dots) {
                    clear &= !(d[0] > box[0] - 3 && d[0] < box[0] + box[2] + 3 && d[1] > box[1] - 3 && d[1] < box[1] + box[3] + 3);
                }
                for (double[] b : taken) {
                    clear &= !(box[0] < b[0] + b[2] + 2 && b[0] < box[0] + box[2] + 2 && box[1] < b[1] + b[3] + 2 && b[1] < box[1] + box[3] + 2);
                }
                if (!clear) {
                    continue;                       // pins win over place names
                }
                halo(g, text, x + (int) Math.round(tx), y + (int) Math.round(ty));
                taken.add(box);
                shown++;
                break;
            }
        }
    }

    /** A place name in cream with a dark halo, so it reads on any tile. */
    void halo(GuiGraphics g, String text, int tx, int ty) {
        float[][] offs = {{-0.6f, 0}, {0.6f, 0}, {0, -0.6f}, {0, 0.6f}};
        for (float[] o : offs) {
            g.pose().pushMatrix();
            g.pose().translate(o[0], o[1]);
            Type.META.draw(g, font(), text, tx, ty, 0xCC140F0A);
            g.pose().popMatrix();
        }
        Type.META.draw(g, font(), text, tx, ty, QuestMap.CREAM);
    }

    /** The far player's pill on the border toward them, {x, y, w, h} in the map; null when near or unknown. */
    double[] youBox(int w, int h, QuestMapLayout.Fit f, double[] me, List<QuestMap.Place> places) {
        if (me == null || f.near() || QuestMapLayout.offView(f, w, h, me) < 1) {
            return null;                            // in the view: the arrow, not the pill
        }
        String t = youText(f, w, h, me);
        double tw = Type.META.width(font(), t) + 2 * Theme.SP_2 + 9;
        double[] at = QuestMapLayout.edge(f.px(me[0]), f.py(me[1]), tw, 13, w, h, Theme.SP_2);
        return new double[] {at[0], at[1], tw, 13};
    }

    /** "Sen · 1.7 km": how far you are from what this view shows (the pane's "en yakını" is to the nearest place). */
    static String youText(QuestMapLayout.Fit f, int w, int h, double[] me) {
        double d = QuestMapLayout.offView(f, w, h, me);
        String t = d >= 1000 ? String.format(Locale.ROOT, "%.1f km", d / 1000) : Math.round(d) + " m";
        return L.t("wynnlabs.quests.map_you", t);
    }

    /** You: an arrow where you face when on the map, else the pill on the border pointing at you. */
    void you(GuiGraphics g, int x, int y, QuestMapLayout.Fit f, double[] me, float yaw, double[] box, List<QuestMap.Place> places) {
        if (me == null) {
            return;
        }
        if (f.near() || box == null && QuestMapLayout.onMap(f.px(me[0]), f.py(me[1]), map.rect[2], map.rect[3])) {
            int px = x + (int) Math.round(f.px(me[0]));
            int py = y + (int) Math.round(f.py(me[1]));
            Art.box(g, px - 5, py - 5, 11, 11, 5, QuestMap.CREAM, QuestMap.YOU);
            arrow(g, px, py, (float) Math.toRadians(yaw + 180), 7, QuestMap.YOU);
            return;
        }
        if (box == null) {
            return;
        }
        int bx = x + (int) Math.round(box[0]);
        int by = y + (int) Math.round(box[1]);
        int tw = (int) Math.round(box[2]);
        int ph = (int) Math.round(box[3]);
        double ang = Math.atan2(f.py(me[1]) - (box[1] + ph / 2.0), f.px(me[0]) - (box[0] + tw / 2.0));
        Art.box(g, bx, by, tw, ph, 6, 0xF0F3E6C4, QuestMap.YOU);
        arrow(g, bx + Theme.SP_2 + 4, by + ph / 2, (float) (ang + Math.PI / 2), 6, QuestMap.YOU);
        Type.META.draw(g, font(), youText(f, map.rect[2], map.rect[3], me), bx + Theme.SP_2 + 9, Type.META.yIn(by, ph), QuestMap.YOU);
    }

    /** The arrow icon turned to {@code rad} (0 = up), centred on (cx, cy). */
    static void arrow(GuiGraphics g, int cx, int cy, float rad, int size, int colour) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().rotate(rad);
        Art.icon(g, "arrow-up", -size / 2, -size / 2, size, colour);
        g.pose().popMatrix();
    }
}
