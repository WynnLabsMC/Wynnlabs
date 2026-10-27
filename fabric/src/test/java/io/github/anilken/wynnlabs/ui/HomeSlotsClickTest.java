package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.quests.QuestBook;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Home slot's click does what its tooltip says: a piece with options opens the comparison
 * (and copies nothing there), one without copies its name (an Absolution row said "Click: copy
 * its name" and the click did nothing).
 */
class HomeSlotsClickTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    /** The calls a row makes: compare rects, and the copy actions of its areas. */
    private static final class Calls implements HomeTab.Host {
        final List<int[]> compared = new ArrayList<>();
        final List<String> actions = new ArrayList<>();

        @Override
        public void area(int x, int y, int w, int h, ItemStack stack, List<Component> tip, String action) {
            if (action != null) {
                actions.add(action);
            }
        }

        @Override
        public void quest(int x, int y, int w, int h, List<Component> tip, QuestBook.Entry q) { }

        @Override
        public void item(int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> extra) { }

        @Override
        public void item(int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> lead, List<Component> extra) { }

        @Override
        public void compare(int[] rect, HomeTab.SlotView s) {
            if (HomeSlots.opens(s)) {
                compared.add(rect);
            }
        }

        @Override
        public int levelPills(GuiGraphics g, int y, int right, int room) {
            return right;
        }

        @Override
        public int refresh(GuiGraphics g, int x, int y, int mx, int my, String staleTip) {
            return 0;
        }
    }

    @Test
    void theClickDoesWhatTheTooltipSays() throws Exception {
        FixtureState.load(Fixtures.root().resolve("shaman-44")).install("en");
        ItemData info = GameState.current().allGear().filter(i -> i.name().equals("Absolution")).findFirst().orElseThrow();
        Roadmap.Option opt = new Roadmap.Option(info, 10, null, "?", List.of(), "", false, new double[0], null, null);
        Roadmap.Gear with = new Roadmap.Gear("RELIK", info, 10, "?", null, null, null, List.of(), List.of(), List.of(opt));
        Roadmap.Gear without = new Roadmap.Gear("RELIK", info, 10, "?", null, null, null, List.of(), List.of(), List.of());
        HomeSlots slots = new HomeSlots(HomeTab.INSTANCE);
        int[] rect = {10, 20, 150, 24};

        Calls a = new Calls();
        slots.areas(a, new HomeTab.SlotView(1, "Weapon", Slot.RELIK, ItemStack.EMPTY, with, ItemStack.EMPTY, 10, List.of()), rect);
        assertEquals(1, a.compared.size(), "options: the click opens them");
        assertTrue(a.actions.isEmpty(), "and copies nothing");

        Calls b = new Calls();
        slots.areas(b, new HomeTab.SlotView(1, "Weapon", Slot.RELIK, ItemStack.EMPTY, without, ItemStack.EMPTY, 10, List.of()), rect);
        assertTrue(b.compared.isEmpty(), "no options: nothing to open");
        assertEquals(List.of("Absolution"), b.actions, "the click copies the name");
    }
}
