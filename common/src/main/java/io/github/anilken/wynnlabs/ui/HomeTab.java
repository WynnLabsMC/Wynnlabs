package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * The home tab under the character band, drawn as pictures: the gear on an armour stand in the
 * middle (the armour slots on its left, the accessories on its right, the weapon in its hand),
 * every slot the selected milestone changes framed green with its gain and its body part tinted,
 * the build bar under it; the spells' damage on the left ({@link HomeSpells}) and the build's
 * stats as a list on the right ({@link HomeStats}). Compact: the stand on the left, the stats over
 * the spells beside it (the first steps' row on top while any is open). Above it all the "Next"
 * line ({@link HomeNext}); while a menu the mod reads is unopened, the "First steps" card
 * ({@link HomeSteps}) sits under the stats. The tiles that were here are on the Today tab
 * (the Today tab once had them).
 *
 * <p>{@link RoadmapScreen} keeps the band, the level buttons, the clicks and the tooltips: it
 * hands this frame's slots in ({@link In}) and takes the areas back through {@link Host}. Only
 * reads; nothing here acts in the game.
 *
 * <p>This class lays the page out; {@link HomeGearPanel} (with {@link HomeSlots} and
 * {@link HomeGearBars}) draws the gear panel, {@link HomeTiles} the tiles from {@link HomeData}
 * and {@link HomeStats} the stats list.
 */
public final class HomeTab {

    public static final HomeTab INSTANCE = new HomeTab();

    /**
     * One gear slot: its place in the screen's slot order (0-3 armour, 4 the weapon, 5-8 rings,
     * bracelet, necklace), its name, what is worn, the upgrade (null: keep) with its icon, gain
     * and tooltip; for a plan's missing piece, {@code lead}: the lines above the item's tooltip
     * that explain its pill ("Missing · dropped by mobs" and a detail).
     */
    record SlotView(int index, String label, Slot type, ItemStack worn, Roadmap.Gear gear, ItemStack icon, double gain,
            List<Component> tip, Mark mark, ItemData planned, List<Component> lead) {

        SlotView(int index, String label, Slot type, ItemStack worn, Roadmap.Gear gear, ItemStack icon, double gain,
                List<Component> tip) {
            this(index, label, type, worn, gear, icon, gain, tip, Mark.NONE, null, List.of());
        }

        /** A plan's piece to show (chosen or still to get): its icon framed, not what is worn. */
        boolean showsPlanned() {
            return mark == Mark.CHOSEN || mark == Mark.GET;
        }
    }

    /**
     * How a slot of the applied plan shows: NONE (the road map's view), CHOSEN (one of the items
     * you build around, not worn yet: purple), GET (the plan's piece, not worn yet: green), DONE
     * (worn as planned: a tick) and DONE_CHOSEN (a chosen item worn: a purple tick).
     */
    enum Mark { NONE, CHOSEN, GET, DONE, DONE_CHOSEN }

    /**
     * The applied plan's build line: its name, how many of its pieces you wear, what the rest
     * costs (NaN unknown), and the level a better build was found at (-1 none).
     */
    record PlanLine(String name, int worn, int total, double left, int better, int plannedFor) {}

    /**
     * The road map behind the panel: none yet (the panel offers to calculate it), being built and
     * how far, and why it is out of date (null when current).
     */
    record Road(boolean none, boolean building, double progress, Roadmap.Stale stale) {}

    /** The preview: this road map state instead of the real one; never set in the game. */
    static volatile Road previewRoad;

    /**
     * What the screen hands in each frame: the slots, the build score now and with the selected
     * milestone's whole set (NaN when unknown), the best quest (null for none) and the places to
     * level now; the plan's line when the Plan pill is selected (null otherwise); how many items
     * are chosen and builds saved, and whether a search for builds is running.
     */
    record In(List<SlotView> slots, double nowScore, double afterScore, QuestAdvisor.Suggestion quest, List<String> places,
            PlanLine plan, int chosen, int saved, boolean searching, Road road) {

        In(List<SlotView> slots, double nowScore, double afterScore, QuestAdvisor.Suggestion quest, List<String> places) {
            this(slots, nowScore, afterScore, quest, places, null, 0, 0, false, null);
        }
    }

    /** The screen's side: areas for this frame's tooltips and clicks, and its level buttons. */
    interface Host {
        /** A hover and click area: an item's tooltip, or lines, and the screen's action (null: none). */
        void area(int x, int y, int w, int h, ItemStack stack, List<Component> tip, String action);

        /** A quest's area: a click tracks it. */
        void quest(int x, int y, int w, int h, List<Component> tip, QuestBook.Entry q);

        /**
         * An item's hover: its full tooltip (the worn stack's own with your rolls when {@code worn}
         * is not empty, else the item database's) with {@code extra} context lines under it.
         */
        void item(int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> extra);

        /** {@link #item}, with {@code lead} lines above the item's tooltip (a blank line between). */
        void item(int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> lead, List<Component> extra);

        /** A slot whose click opens its comparison (when it has options). */
        void compare(int[] rect, SlotView s);

        /** The level buttons ending at {@code right} in {@code room} pixels; their left edge. */
        int levelPills(GuiGraphics g, int y, int right, int room);

        /** The small refresh button (or its bar while the map is rebuilt) at x; the width it took. */
        int refresh(GuiGraphics g, int x, int y, int mx, int my, String staleTip);
    }

    /** The items you chose: their frames, pills and the stand's part in the archetype's purple. */
    static final int CHOSEN = Theme.ACCENT;
    static final int CHOSEN_TEXT = 0xFFF1E9FF;

    private final HomeGearPanel gear = new HomeGearPanel(this);
    private final HomeTiles tiles = new HomeTiles();
    private final HomeNext next = new HomeNext();
    private final HomeSteps steps = new HomeSteps();

    private HomeTab() {}

    // ---- preview (LayoutMatrix): long names, to check they never move the figure ----

    /** Long slot names by slot index, for the preview. */
    static final String[] LONG = {"The King's Robe", "The King's Robe", "Darkiron Scrap", "Darkiron Scrap",
        "Enzan's Lucky Charm", "Enzan's Lucky Charm", "Darkiron Scrap", "Enzan's Lucky Charm", "The King's Robe"};
    /** For the preview: every slot shows a long name and the weapon a "+142%" pill; never set in the game. */
    static boolean previewLong;
    /**
     * The figure's box drawn last frame (x, y, w, h) and the panel's width and stand room, for the
     * preview's check that the names never move it.
     */
    final int[] standBox = new int[6];

    // =====================================================================================
    // drawing

    /**
     * Draws the home's body under the band.
     *
     * @param x       the page's left edge
     * @param y       the top, under the experience bar
     * @param w       the page's width
     * @param h       the height left
     * @param compact the compact menu (an icon rail, a short page)
     * @param large   the large menu
     * @param stats   the stats list of the right column, with the spell panel of the left
     */
    void draw(GuiGraphics g, In in, Host host, HomeStats stats, int x, int y, int w, int h, boolean compact, boolean large,
            int mx, int my) {
        Font font = UiFont.get();
        gear.frame(font, large);
        gear.slots.frame(font, large);
        gear.bars.frame(font, large);
        tiles.frame(font, large);
        next.frame(font, large);
        steps.frame(font, large);
        firstPill = null;
        long now = System.currentTimeMillis();
        tiles.refresh(now);
        // the columns have no frames: SP_7 between them (SP_5 compact) with a hairline in the middle
        int gap = compact ? Theme.SP_5 : Theme.SP_7;
        // the one thing to do now, a slim line on top (none: the endgame with nothing left)
        HomeNext.Pick pick = next.pick(in);
        if (pick != null) {
            int nh = next.height(compact);
            next.draw(g, pick, host, x, y, w, nh, compact, mx, my);
            y += nh + (compact ? Theme.SP_3 : Theme.SP_5);
            h -= nh + (compact ? Theme.SP_3 : Theme.SP_5);
        }
        boolean firstSteps = FirstSteps.show();
        if (compact) {
            int lw = Math.max(118, Math.min(160, w * 36 / 100));
            int pw = w - lw - gap;
            gear.panel(g, in, host, x, y, pw, h, true, false, mx, my);
            Kit.hair(g, x + pw + gap / 2, y, 1, h, Theme.LINE);
            int used = firstSteps ? tiles.rows(g, host, x + pw + gap, y, lw, h / 3, mx, my, steps) + 4 : 0;
            // the stats and the spells as two tabs in the column beside the stand
            stats.draw(g, host, x + pw + gap, y + used, lw, h - used, false, true, mx, my);
            return;
        }
        // the sides give the gear panel room for its slots' names; the stats' side a little more
        int side = large ? Math.max(170, Math.min(220, w * 24 / 100)) : Math.max(112, Math.min(140, (w - 2 * gap) * 24 / 100));
        // the small menu keeps the sides equal: the gear panel needs that room for its build bar
        int rside = large ? Math.max(190, Math.min(250, w * 27 / 100))
                : w < 520 ? side : Math.max(140, Math.min(175, (w - 2 * gap) * 28 / 100));
        int cw = w - side - rside - 2 * gap;
        stats.spells.draw(g, host, x, y, side, h, large, mx, my);
        Kit.hair(g, x + side + gap / 2, y, 1, h, Theme.LINE);
        gear.panel(g, in, host, x + side + gap, y, cw, h, false, large, mx, my);
        int rx = x + side + gap + cw + gap;
        Kit.hair(g, rx - gap / 2, y, 1, h, Theme.LINE);
        if (firstSteps) {
            // the menus still to open, under the stats until all are done
            int sh = (h - gap) * 2 / 5;
            stats.draw(g, host, rx, y, rside, h - sh - gap, large, mx, my);
            steps.draw(g, host, rx, y + h - sh, rside, sh, mx, my);
            return;
        }
        stats.draw(g, host, rx, y, rside, h, large, mx, my);
    }

    /**
     * Three dots, one lit in turn by the clock (a quarter second each), 2px squares 2px apart;
     * all lit and still in the preview's frozen state.
     */
    static void dots(GuiGraphics g, int x, int y, int colour) {
        int lit = Around.previewLoading != null ? -1 : (int) (System.currentTimeMillis() / 250 % 3);
        for (int i = 0; i < 3; i++) {
            int c = lit < 0 || i == lit ? colour : (colour & 0xFFFFFF) | 0x66000000;
            g.fill(x + i * 3, y - 1, x + i * 3 + 2, y + 1, c);
        }
    }

    /** The preview: the first slot's hover box this frame, for a tooltip shot. */
    public int[] firstSlot;
    /** The preview: the first missing piece's pill this frame (x, y, w, h, 1 when its square is on its left), for a tooltip shot. */
    public int[] firstPill;

    /** "from yesterday", "3 days ago", "5 h ago"; "" under an hour. */
    static String age(long ms) {
        long h = ms / 3_600_000L;
        if (h < 1) {
            return "";
        }
        if (h < 24) {
            return L.t("wynnlabs.home.stale_age_hours", h);
        }
        long d = h / 24;
        return d == 1 ? L.t("wynnlabs.home.stale_age_yesterday") : L.t("wynnlabs.home.stale_age_days", d);
    }
}
