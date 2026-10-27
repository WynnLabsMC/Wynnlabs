package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.core.L;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** The Home's goal list: the dropdown under the goal button, a row picks the goal and builds the road map again. */
final class HomeGoals extends MenuPaint {

    private final HomePage page;

    HomeGoals(RoadmapScreen menu, HomePage page) {
        super(menu);
        this.page = page;
    }

    void pick(int i) {
        page.goalOpen = false;
        String goal = Setup.GOALS.get(i);
        if (!goal.equals(Setup.goal())) {
            Setup.setGoal(goal);
            menu.flash(L.t("wynnlabs.home.goal_set", Setup.goalName(goal)));
            menu.rebuildMap();                 // rebuilt in the background, picked up when ready
        }
    }

    /** A click while the goal list is open: it closes, and a row picks that goal. True when the click is used up. */
    boolean clicked(double ex, double ey) {
        if (!page.goalOpen) {
            return false;
        }
        page.goalOpen = false;
        for (int[] r : goalRows) {
            if (in(ex, ey, r)) {
                pick(r[4]);
                return true;
            }
        }
        return page.goalAnchor != null && in(ex, ey, page.goalAnchor);    // the button again: closed
    }

    private final List<int[]> goalRows = new ArrayList<>();     // x, y, w, h, index in Setup.GOALS

    /** One row per goal: its name and what it weighs, the current one marked, the hovered one lit (when the list is open). */
    final UiMenu.State goalMenu = new UiMenu.State();

    int[] draw(GuiGraphics g, int mx, int my) {
        if (!page.goalOpen || page.goalAnchor == null) {
            return null;
        }
        goalRows.clear();
        return UiMenu.draw(g, font(), UiMenu.Spec.under(page.goalAnchor, menu.width, menu.height), goalMenu, items(), mx, my,
                r -> goalRows.add(new int[] {r.x(), r.y(), r.w(), r.h(), Integer.parseInt(r.item().id())}));
    }

    /** A key while the goal list is open: a row picked by the keyboard. */
    boolean key(int key) {
        UiMenu.Item it = goalMenu.key(key);
        if (it != null) {
            pick(Integer.parseInt(it.id()));
        }
        return true;
    }

    /** The goals as dropdown rows: the name, what it weighs under it, the current one chosen (the home and the settings). */
    static List<UiMenu.Item> items() {
        String now = Setup.goal();
        List<UiMenu.Item> items = new ArrayList<>();
        for (int i = 0; i < Setup.GOALS.size(); i++) {
            String goal = Setup.GOALS.get(i);
            items.add(UiMenu.Item.of(String.valueOf(i), null, Setup.goalName(goal), null, goal.equals(now)).sub(L.t(goalDesc(goal))));
        }
        return items;
    }

    private static String goalDesc(String goal) {
        return switch (goal) {
            case "BALANCED" -> "wynnlabs.setup.goal_balanced_desc";
            case "SAFE" -> "wynnlabs.setup.goal_safe_desc";
            case "TANK" -> "wynnlabs.setup.goal_tank_desc";
            case "HEALER" -> "wynnlabs.setup.goal_healer_desc";
            case "BOSS" -> "wynnlabs.setup.goal_boss_desc";
            case "MOBBING" -> "wynnlabs.setup.goal_mobbing_desc";
            case "XP" -> "wynnlabs.setup.goal_xp_desc";
            case "LOOT" -> "wynnlabs.setup.goal_loot_desc";
            default -> "wynnlabs.setup.goal_damage_desc";
        };
    }
}
