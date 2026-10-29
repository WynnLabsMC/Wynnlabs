package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.mounts.RideView;
import io.github.anilken.wynnlabs.mounts.RideWatch;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Moving the movable HUD elements (today the mount panel): the world dimmed, each element drawn
 * with its own data or a sample, the picked one with a gold edge. Drag one: it snaps to the
 * screen's margins and middle lines ({@link HudPlace#snap}) and is saved by its nearest corner when
 * let go. A card at the top: one chip per element, a reset to its default place, Done. Esc saves
 * and goes back too. Drawn at the game's GUI scale, as the HUD is.
 */
public final class HudEditScreen extends Screen {

    /** A movable element: its id, name, size on this screen, default place and how it draws. */
    interface Element {
        String id();

        String label();

        int[] size(Font font, int screenW, int screenH);

        int[] defaultAt(int screenW, int screenH, int w, int h);

        void draw(GuiGraphics g, Font font, int x, int y, int screenH);
    }

    /** The mount panel, with the mount ridden last or a sample. */
    static final Element RIDE = new Element() {
        @Override
        public String id() {
            return RidePanel.ID;
        }

        @Override
        public String label() {
            return L.t("wynnlabs.setting.ridePanel");
        }

        private RideView sample;

        /** The panel as it shows now while riding, else a sample. */
        private RideView shown() {
            RideView v = RideWatch.view();
            if (v != null && !v.stats().isEmpty()) {
                return v;
            }
            if (sample == null) {
                sample = RideView.of(RidePanel.sample(), null, null, null, -1);
            }
            return sample;
        }

        @Override
        public int[] size(Font font, int screenW, int screenH) {
            return RidePanel.size(font, shown(), screenH);
        }

        @Override
        public int[] defaultAt(int screenW, int screenH, int w, int h) {
            return RidePanel.defaultAt(screenW, screenH, w, h);
        }

        @Override
        public void draw(GuiGraphics g, Font font, int x, int y, int screenH) {
            RidePanel.draw(g, font, shown(), x, y, screenH < RidePanel.SMALL_SCREEN, 0);
        }
    };

    static final List<Element> ELEMENTS = List.of(RIDE);

    private static final int VEIL = 0x77000000;
    private static final int GUIDE = 0x40F0C24A;
    private static final int GUIDE_ON = 0xC0F0C24A;

    private final Screen parent;
    private int picked;
    /** Each element's top-left while this screen is open. */
    private final int[][] pos = new int[ELEMENTS.size()][];
    private boolean dragging;
    private double grabX;
    private double grabY;
    private final List<Object[]> hits = new ArrayList<>();

    public HudEditScreen(Screen parent, String focus) {
        super(Component.literal(""));
        this.parent = parent;
        for (int i = 0; i < ELEMENTS.size(); i++) {
            if (ELEMENTS.get(i).id().equals(focus)) {
                picked = i;
            }
        }
    }

    /** An element's top-left now: where it was dragged, else its saved or default place. */
    private int[] place(int i, Font font) {
        Element e = ELEMENTS.get(i);
        int[] wh = e.size(font, width, height);
        if (pos[i] == null) {
            HudPlace.Spot s = HudPlace.load(e.id());
            pos[i] = s == null ? e.defaultAt(width, height, wh[0], wh[1]) : HudPlace.resolve(s, width, height, wh[0], wh[1]);
        }
        pos[i] = HudPlace.clamp(pos[i][0], pos[i][1], width, height, wh[0], wh[1]);
        return pos[i];
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, width, height, VEIL);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Font font = Hud.font();
        hits.clear();
        Element cur = ELEMENTS.get(picked);
        int[] cwh = cur.size(font, width, height);
        int[] cp = place(picked, font);
        // the guides: the margins and the middle lines, lit while the picked element sits on one
        guide(g, true, HudPlace.MARGIN, dragging && cp[0] == HudPlace.MARGIN);
        guide(g, true, width - HudPlace.MARGIN, dragging && cp[0] + cwh[0] == width - HudPlace.MARGIN);
        guide(g, true, width / 2, dragging && HudPlace.centred(cp[0], cwh[0], width));
        guide(g, false, HudPlace.MARGIN, dragging && cp[1] == HudPlace.MARGIN);
        guide(g, false, height - HudPlace.MARGIN, dragging && cp[1] + cwh[1] == height - HudPlace.MARGIN);
        guide(g, false, height / 2, dragging && HudPlace.centred(cp[1], cwh[1], height));
        for (int i = 0; i < ELEMENTS.size(); i++) {
            int[] p = place(i, font);
            int[] wh = ELEMENTS.get(i).size(font, width, height);
            ELEMENTS.get(i).draw(g, font, p[0], p[1], height);
            if (i == picked) {
                Art.box(g, p[0] - 2, p[1] - 2, wh[0] + 4, wh[1] + 4, 6, 0, Hud.GOLD);
            }
        }
        toolbar(g, font, mx, my);
    }

    private void guide(GuiGraphics g, boolean vertical, int at, boolean on) {
        if (vertical) {
            Kit.hair(g, at, 0, 1, height, on ? GUIDE_ON : GUIDE);
        } else {
            Kit.hair(g, 0, at, width, 1, on ? GUIDE_ON : GUIDE);
        }
    }

    /** The top card: a chip per element, the reset, Done. */
    private void toolbar(GuiGraphics g, Font font, int mx, int my) {
        String done = L.t("wynnlabs.hud.done");
        int doneW = Ui.buttonWidth(font, done);
        int resetW = Ui.CHIP_H + 2;
        int w = 0;
        int[] chipW = new int[ELEMENTS.size()];
        for (int i = 0; i < ELEMENTS.size(); i++) {
            chipW[i] = Ui.chipWidth(font, ELEMENTS.get(i).label());
            w += chipW[i] + Theme.SP_2;
        }
        w += Theme.SP_2 + resetW + Theme.SP_2 + doneW;
        int cardW = w + 2 * Theme.SP_3;
        int cardH = Ui.BUTTON_H + 2 * Theme.SP_2;
        int x = (width - cardW) / 2;
        int y = Theme.SP_5;
        Kit.softCard(g, x, y, cardW, cardH);
        int cx = x + Theme.SP_3;
        int chipY = y + (cardH - Ui.CHIP_H) / 2;
        for (int i = 0; i < ELEMENTS.size(); i++) {
            Ui.chip(g, font, ELEMENTS.get(i).label(), cx, chipY, chipW[i], i == picked, false, false, mx, my);
            hits.add(new Object[] {new int[] {cx, chipY, chipW[i], Ui.CHIP_H}, "pick:" + i});
            cx += chipW[i] + Theme.SP_2;
        }
        cx += Theme.SP_2;
        Ui.iconButton(g, "refresh", cx, chipY, false, mx, my);
        hits.add(new Object[] {new int[] {cx, chipY, resetW, Ui.CHIP_H}, "reset"});
        if (mx >= cx && mx < cx + resetW && my >= chipY && my < chipY + Ui.CHIP_H) {
            g.setTooltipForNextFrame(font, Component.literal(L.t("wynnlabs.hud.reset")), mx, my);
        }
        cx += resetW + Theme.SP_2;
        int by = y + Theme.SP_2;
        Ui.button(g, font, done, cx, by, doneW, mx, my);
        hits.add(new Object[] {new int[] {cx, by, doneW, Ui.BUTTON_H}, "done"});
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double ex = event.x();
        double ey = event.y();
        for (Object[] h : hits) {
            int[] r = (int[]) h[0];
            if (ex >= r[0] && ex < r[0] + r[2] && ey >= r[1] && ey < r[1] + r[3]) {
                act((String) h[1]);
                return true;
            }
        }
        Font font = Hud.font();
        for (int i = ELEMENTS.size() - 1; i >= 0; i--) {
            int[] p = place(i, font);
            int[] wh = ELEMENTS.get(i).size(font, width, height);
            if (ex >= p[0] && ex < p[0] + wh[0] && ey >= p[1] && ey < p[1] + wh[1]) {
                picked = i;
                dragging = true;
                grabX = ex - p[0];
                grabY = ey - p[1];
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (!dragging) {
            return super.mouseDragged(event, dx, dy);
        }
        Font font = Hud.font();
        int[] wh = ELEMENTS.get(picked).size(font, width, height);
        int x = HudPlace.snap((int) Math.round(event.x() - grabX), wh[0], width);
        int y = HudPlace.snap((int) Math.round(event.y() - grabY), wh[1], height);
        pos[picked] = HudPlace.clamp(x, y, width, height, wh[0], wh[1]);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging) {
            dragging = false;
            save(picked);
        }
        return super.mouseReleased(event);
    }

    private void act(String id) {
        if (id.startsWith("pick:")) {
            picked = Integer.parseInt(id.substring(5));
        } else if (id.equals("reset")) {
            HudPlace.reset(ELEMENTS.get(picked).id());
            pos[picked] = null;
        } else if (id.equals("done")) {
            onClose();
        }
    }

    /** An element's place saved by its nearest corner. */
    private void save(int i) {
        if (pos[i] == null) {
            return;
        }
        int[] wh = ELEMENTS.get(i).size(Hud.font(), width, height);
        HudPlace.save(ELEMENTS.get(i).id(), HudPlace.of(pos[i][0], pos[i][1], wh[0], wh[1], width, height));
    }

    @Override
    public void onClose() {
        if (dragging) {
            dragging = false;
            save(picked);
        }
        minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
