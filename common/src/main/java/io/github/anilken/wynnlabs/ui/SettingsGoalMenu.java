package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** The settings' goal row as a dropdown ({@link UiMenu}): the same rows as the home's goal list. */
final class SettingsGoalMenu extends MenuPaint implements MenuTab.Popup {

    /** The chip it opens under; null while closed. */
    int[] at;
    final UiMenu.State state = new UiMenu.State();
    private final List<int[]> rows = new ArrayList<>();

    SettingsGoalMenu(RoadmapScreen menu) {
        super(menu);
    }

    void open(int[] chip) {
        at = chip;
        state.reset();
    }

    @Override
    public boolean open() {
        return at != null;
    }

    @Override
    public void close() {
        at = null;
    }

    @Override
    public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
        rows.clear();
        boxes.add(UiMenu.draw(g, font(), UiMenu.Spec.under(at, menu.width, menu.height), state, HomePage.goalItems(), mx, my,
                r -> rows.add(new int[] {r.x(), r.y(), r.w(), r.h(), Integer.parseInt(r.item().id())})));
    }

    @Override
    public void scrolled(double x, double y, double v) {
        state.wheel(v);
    }

    /** A click inside: the scroll bar, or a row picks that goal. Always used up. */
    @Override
    public boolean clicked(net.minecraft.client.input.MouseButtonEvent event) {
        double x = event.x();
        double y = event.y();
        if (state.press(x, y)) {
            return true;
        }
        for (int[] r : rows) {
            if (in(x, y, r)) {
                pick(r[4]);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean key(int key) {
        UiMenu.Item it = state.key(key);
        if (it != null) {
            pick(Integer.parseInt(it.id()));
        }
        return true;
    }

    private void pick(int i) {
        at = null;
        menu.home.pickGoal(i);
    }
}
