package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.quests.QuestBook;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Home's ways back into the screen: the build window's host and the stand's host, both
 * adding to this frame's click and hover areas, and the one way an item's hover is made.
 */
final class HomeHosts {

    private final RoadmapScreen menu;
    private final HomePage page;

    HomeHosts(RoadmapScreen menu, HomePage page) {
        this.menu = menu;
        this.page = page;
    }

    /**
     * The one way an item's hover is made on the Home tab and in the build window: its full
     * tooltip (tier-coloured name, type and level, requirements, base stats, identification
     * ranges) and then, after a blank line, {@code extra} (plan, source, gain, story). A worn
     * piece shows its own stack's tooltip with your rolls; another piece Wynntils' tooltip of its
     * guide stack, or without one ({@link BuildWindow#itemTooltip}) the item data's. {@code front}:
     * ahead of the other hits (an icon over its card).
     */
    void itemHit(boolean front, int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> extra) {
        itemHit(front, x, y, w, h, info, worn, List.of(), extra);
    }

    /** {@link #itemHit}, with {@code lead} lines over the item's tooltip (a blank line between). */
    void itemHit(boolean front, int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> lead,
            List<Component> extra) {
        List<Component> ex = extra == null ? List.of() : extra;
        List<Component> ld = lead == null || lead.isEmpty() ? null : lead;
        MenuHit hit;
        if (worn != null && !worn.isEmpty()) {
            hit = new MenuHit(x, y, w, h, worn, ex.isEmpty() ? null : ex, null, null, ld);
        } else {
            ItemStack st = info == null ? ItemStack.EMPTY : menu.icon(info);
            if (menu.iconInfo.containsKey(st)) {
                hit = new MenuHit(x, y, w, h, st, ex.isEmpty() ? null : ex, null, null, ld);
            } else {
                List<Component> tip = new ArrayList<>();
                if (ld != null) {
                    tip.addAll(ld);
                    tip.add(Component.empty());
                }
                tip.addAll(info == null ? List.of() : BuildWindow.itemTooltip(info));
                if (!ex.isEmpty()) {
                    tip.add(Component.empty());
                    tip.addAll(ex);
                }
                hit = new MenuHit(x, y, w, h, null, tip, null);
            }
        }
        if (front) {
            menu.hits.add(0, hit);
        } else {
            menu.hits.add(hit);
        }
    }

    /** The build window's way back into the screen. */
    final BuildWindow.Host window = new BuildWindow.Host() {
        @Override
        public void tip(int x, int y, int w, int h, List<Component> tip) {
            menu.hits.add(new MenuHit(x, y, w, h, null, tip, null));
        }

        @Override
        public void item(int x, int y, int w, int h, ItemData info, ItemStack stack, List<Component> extra) {
            // first in the list: an icon lies over its card, and the first hit under the mouse shows
            itemHit(true, x, y, w, h, info, ItemStack.EMPTY, extra);
        }

        @Override
        public ItemStack icon(ItemData info) {
            return menu.icon(info);
        }

        @Override
        public void flash(String text) {
            menu.flash(text);
        }

        @Override
        public void applied() {
            HomeLevels.planPills.pickPlan();
            menu.flash(L.t("wynnlabs.homev.plan_applied"));
        }

        @Override
        public void detailed(Roadmap.Candidate c, int level, int rank) {
            page.stats().pick(c, level, rank);
        }
    };

    /** The home's way back into the screen: its click and hover areas join this frame's hits. */
    final HomeTab.Host home = new HomeTab.Host() {
        @Override
        public void area(int x, int y, int w, int h, ItemStack stack, List<Component> tip, String action) {
            menu.hits.add(new MenuHit(x, y, w, h, stack, tip, action));
        }

        @Override
        public void quest(int x, int y, int w, int h, List<Component> tip, QuestBook.Entry q) {
            menu.hits.add(new MenuHit(x, y, w, h, null, tip, null, q));
        }

        @Override
        public void item(int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> extra) {
            itemHit(false, x, y, w, h, info, worn, extra);
        }

        @Override
        public void item(int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> lead, List<Component> extra) {
            itemHit(false, x, y, w, h, info, worn, lead, extra);
        }

        @Override
        public void corner(int[] rect, String action) {
            page.cornerHits.add(new Object[] {rect, action});
        }

        @Override
        public void choicesChip(int[] rect) {
            page.choicesChip(rect);
        }

        @Override
        public io.github.anilken.wynnlabs.build.TomeAdvice.Advice shownTomes() {
            return page.alts().shownTomes();
        }

        @Override
        public void tomesChip(int[] rect) {
            page.tomesChip(rect);
        }

        @Override
        public void aspectsChip(int[] rect) {
            page.aspectsChip(rect);
        }

        @Override
        public void compare(int[] rect, HomeTab.SlotView s) {
            if (s.gear() != null && s.gear().options() != null && !s.gear().options().isEmpty()) {
                page.slotHits.add(new Object[] {rect, s.gear(), s.label(), s.worn()});
            }
        }

        @Override
        public int levelPills(GuiGraphics g, int y, int right, int room) {
            return page.levels().draw(g, y, right, room, page.levelRows);
        }

        @Override
        public Boolean shownSaved() {
            return page.alts().shownSaved();
        }

        @Override
        public int alternatives(GuiGraphics g, int y, int right, boolean compact, int mx, int my) {
            return page.alts().chip(g, UiFont.get(), this, y, right, compact, mx, my);
        }

        @Override
        public int refresh(GuiGraphics g, int x, int y, int mx, int my, String staleTip) {
            if (Roadmap.isBuilding()) {
                if (menu.steps.isEmpty()) {
                    return 0;                       // the first map: its progress fills the panel instead
                }
                // a new map over the last one: a small bar and Cancel (the last map stays)
                HdBar.draw(g, x, y + 5, 24, 4, Roadmap.progress(), HdBar.TEAL);
                menu.hits.add(new MenuHit(x, y, 24, 14, null, List.of(L.c("wynnlabs.home.refreshing").withStyle(ChatFormatting.GRAY)), null));
                Ui.iconButton(g, "x", x + 27, y, false, mx, my);
                menu.hits.add(new MenuHit(x + 27, y, Ui.CHIP_H + 2, Ui.CHIP_H, null, List.of(L.c("wynnlabs.pick.cancel").withStyle(ChatFormatting.WHITE),
                        L.c("wynnlabs.home.build_cancel_tip").withStyle(ChatFormatting.GRAY)), "#qcancelbuild:"));
                return 27 + Ui.CHIP_H + 2 + Theme.SP_2;
            }
            // the road map built again now: only ever on a click
            Ui.iconButton(g, "refresh", x, y, false, mx, my);
            List<Component> tip = new ArrayList<>();
            if (staleTip != null) {
                // out of date where no strip fits: an amber badge on the button's corner
                Kit.seal(g, x + 11, y - 2, 7, 7, 0xFFF0B040);
                tip.add(Component.literal(staleTip).withStyle(ChatFormatting.GOLD));
                tip.add(L.c("wynnlabs.home.stale_click").withStyle(ChatFormatting.GRAY));
            } else {
                tip.add(L.c("wynnlabs.home.refresh_tip").withStyle(ChatFormatting.GRAY));
            }
            menu.hits.add(new MenuHit(x, y, Ui.CHIP_H + 2, Ui.CHIP_H, null, tip, "#qrefresh:"));
            return Ui.CHIP_H + 2 + Theme.SP_2;
        }
    };
}
