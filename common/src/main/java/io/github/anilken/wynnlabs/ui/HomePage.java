package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.AutoAdvice;
import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Slot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Home tab: the character HUD, the level pills, a slot's comparison panel and the goal list, around
 * the stand and tiles {@link HomeTab} draws.
 */
public final class HomePage extends MenuTab {

    HomePage(RoadmapScreen menu) {
        super(menu);
    }

    // ---- the tab ----

    @Override
    void beginFrame() {
        levelRows.clear();
        slotHits.clear();
        cornerHits.clear();
        cardHits.clear();
        backRect = null;
        filterRects = null;
        listRect = null;
    }

    /** The build window covers the home: its slots and level pills take no click. */
    void underWindow() {
        slotHits.clear();
        cornerHits.clear();
        levelRows.clear();
    }

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        if (detail != null) {
            options().draw(g, x, y, w, h, mx, my);
        } else {
            drawHome(g, x, y, w, h, mx, my);
        }
    }

    // ---- the popups: the goal list, the stats' compare list ----

    /** A key while a popup is open: the stats list's rows, else the goal list's (HomeGoals). */
    private boolean popupKeyImpl(int key) {
        if (!goalOpen && stats != null && stats.picker.open()) {
            UiMenu.Item it = stats.picker.list.key(key);
            if (it != null) {
                stats.picker.close();
                menu.runAction(it.id());
            }
            return true;
        }
        return goalOpen && goals().key(key);
    }

    /** The popups as the menu's one hook (the methods below). */
    private final Popup popups = new Popup() {
        @Override
        public boolean open() {
            return popupOpenImpl();
        }

        @Override
        public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
            drawPopupImpl(g, mx, my, boxes);
        }

        @Override
        public void close() {
            closePopupImpl();
        }

        @Override
        public boolean clicked(MouseButtonEvent event) {
            return popupClickedImpl(event);
        }

        @Override
        public boolean key(int key) {
            return popupKeyImpl(key);
        }

        @Override
        public void scrolled(double x, double y, double v) {
            if (alts != null && alts.open()) {
                alts.wheel(v);
            }
            if (choices != null && choices.open()) {
                choices.wheel(v);
            }
            if (tomes != null && tomes.open()) {
                tomes.wheel(v);
            }
        }
    };

    @Override
    Popup popup() {
        return popups;
    }

    private boolean popupOpenImpl() {
        return goalOpen || stats != null && stats.picker.open() || alts != null && alts.open()
                || choices != null && choices.open()
                || tomes != null && tomes.open();
    }

    private void drawPopupImpl(GuiGraphics g, int mx, int my, List<int[]> boxes) {
        int[] b = goals().draw(g, mx, my);
        if (b != null) {
            boxes.add(b);
        }
        b = stats != null ? stats.picker.drawList(g, homeHost, mx, my) : null;
        if (b != null) {
            boxes.add(b);
        }
        b = alts != null ? alts.drawList(g, homeHost, mx, my) : null;
        if (b != null) {
            boxes.add(b);
        }
        b = choices != null ? choices.drawList(g, homeHost, mx, my) : null;
        if (b != null) {
            boxes.add(b);
        }
        b = tomes != null && tomes.open() ? tomes.drawList(g, homeHost, alts().shownTomes(), mx, my) : null;
        if (b != null) {
            boxes.add(b);
        }
        if (boxes.isEmpty()) {
            closePopupImpl();
        }
    }

    private void closePopupImpl() {
        goalOpen = false;
        if (stats != null) {
            stats.picker.close();
        }
        if (alts != null) {
            alts.close();
        }
        if (choices != null) {
            choices.close();
        }
        if (tomes != null) {
            tomes.close();
        }
    }

    void pickGoal(int i) {
        goals().pick(i);
    }

    private boolean popupClickedImpl(MouseButtonEvent event) {
        if (choices != null && choices.open()) {
            return choices.press(event.x(), event.y());  // its scroll bar; a row's own click does the rest
        }
        if (tomes != null && tomes.open()) {
            return tomes.press(event.x(), event.y());
        }
        if (alts != null && alts.open()) {
            return alts.press(event.x(), event.y());     // its scroll bar; a card's own click picks it
        }
        if (stats != null && stats.picker.open()) {
            stats.picker.close();               // a row's own click area still picks
            return false;
        }
        return goals().clicked(event.x(), event.y());
    }

    @Override
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double ex = event.x();
        double ey = event.y();
        if (detail != null) {
            if (listRect != null && detailBar.mouseClicked(ex, ey)) {
                detailScroll = detailBar.offset();
                return true;
            }
            if (backRect != null && in(ex, ey, backRect)) {
                detail = null;
                return true;
            }
            for (int i = 0; filterRects != null && i < filterRects.length; i++) {
                if (in(ex, ey, filterRects[i])) {
                    detailMarket = i == 1;
                    detailScroll = 0;
                    return true;
                }
            }
            for (Object[] c : cardHits) {
                if (listRect != null && in(ex, ey, listRect) && in(ex, ey, (int[]) c[0])) {
                    String n = ((ItemData) c[1]).name();
                    Minecraft.getInstance().keyboardHandler.setClipboard(n);
                    menu.flash(L.t("wynnlabs.screen.copied", n));
                    return true;
                }
            }
            return false;
        }
        if (stats != null) {
            stats.picker.press();              // an open compare list closes; its row's own click still picks
        }
        if (stats != null && stats.barClicked(ex, ey)) {
            return true;                       // the stats list's scrollbar: a drag or a page
        }
        for (Object[] c : cornerHits) {
            if (in(ex, ey, (int[]) c[0])) {
                action((String) c[1], event.button());
                return true;
            }
        }
        for (Object[] sh : slotHits) {
            if (in(ex, ey, (int[]) sh[0])) {
                detail = (Roadmap.Gear) sh[1];
                detailLabel = (String) sh[2];
                detailWorn = (ItemStack) sh[3];
                detailScroll = 0;
                return true;
            }
        }
        for (int[] r : levelRows) {
            if (in(ex, ey, r) && r[4] == HomeLevels.PLAN_PILL) {
                HomeLevels.planPills.pickPlan();
                return true;
            }
            if (in(ex, ey, r)) {
                levels().clicked(r);            // ‹ and › step the selection; the window follows it
                return true;
            }
        }
        return false;
    }

    /** The comparison panel keeps its clicks; the home itself passes the rest on. */
    @Override
    boolean sharedClicks() {
        return detail == null;
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        if (detail != null) {
            // the comparison panel's cards scroll; the milestone stays
            detailScroll = Math.max(0, Math.min(detailScrollMax, detailScroll - (int) Math.signum(v) * HomeOptions.PITCH * 2));
            return;
        }
        if (stats != null && stats.over(x, y)) {
            stats.scroll(x, y, v);             // the stats list or the spell panel scrolls under the mouse
            return;
        }
        if (menu.steps.isEmpty()) {
            return;
        }
        levels().wheel(v);                     // the wheel walks the tabs: şimdi, then the shown milestones
    }

    /** A home {@code #q} action (HomeActions). */
    void action(String c, int button) {
        new HomeActions(menu, this).run(c, button);
    }

    // ---- drawing ----

    record Box(String label, Slot type, int ring) {}

    /** Box labels are language keys; show with {@code L.t(box.label())}. */
    private static final Box[] LEFT = {new Box("wynnlabs.screen.slot_helmet", Slot.HELMET, 0),
        new Box("wynnlabs.screen.slot_chestplate", Slot.CHESTPLATE, 0),
        new Box("wynnlabs.screen.slot_leggings", Slot.LEGGINGS, 0), new Box("wynnlabs.screen.slot_boots", Slot.BOOTS, 0),
        new Box("wynnlabs.screen.slot_weapon", null, 0)};
    private static final Box[] RIGHT = {new Box("wynnlabs.screen.slot_ring", Slot.RING, 0),
        new Box("wynnlabs.screen.slot_ring", Slot.RING, 1),
        new Box("wynnlabs.screen.slot_bracelet", Slot.BRACELET, 0), new Box("wynnlabs.screen.slot_necklace", Slot.NECKLACE, 0)};
    final List<int[]> levelRows = new ArrayList<>();   // x, y, w, h, step index
    /** The Gear slots' lock and ✕, which come before the slot's own click: {int[] rect, action}. */
    final List<Object[]> cornerHits = new ArrayList<>();
    final List<Object[]> slotHits = new ArrayList<>();  // {int[] rect, Roadmap.Gear, label, worn}
    final List<Object[]> cardHits = new ArrayList<>();  // {int[] rect, ItemData}
    int[] backRect;
    private HomeOptions options;
    /** The slot whose options the comparison panel shows (null: the home itself). */
    Roadmap.Gear detail;
    String detailLabel;
    ItemStack detailWorn = ItemStack.EMPTY;
    // the panel's filter (only what the Trade Market sells), kept for the session; its list's
    // scroll in pixels; this frame's filter buttons {all, market} and list area (no initializers:
    // fields a hot swap adds keep no value)
    boolean detailMarket;
    int detailScroll;
    int detailScrollMax;
    /** The comparison list's bar (offsets in pixels). */
    final ScrollBar detailBar = new ScrollBar();
    int[][] filterRects;
    int[] listRect;

    /** The goal list under the goal button, open after a click on it. */
    boolean goalOpen;
    private HomeStats stats;
    int[] goalAnchor;
    /** The parts made on first use (fields without an initializer: safe to hot swap in). */
    private HomeLevels levels;
    private HomeGoals goals;
    private HomePlanView planView;
    /** The character card on top, made on first use (a field without an initializer: safe to hot swap in). */
    private HomeHero hero;
    /** The endgame milestones' other builds, made on first use (no initializer: safe to hot swap in). */
    private HomeAlts alts;
    /** What you told the map, made on first use (no initializer: safe to hot swap in). */
    private HomeChoices choices;
    private int[] choicesChip;

    HomeChoices choices() {
        if (choices == null) {
            choices = new HomeChoices();
        }
        return choices;
    }

    void choicesChip(int[] rect) {
        choicesChip = rect;
    }

    void openChoices() {
        choices().openMenu(choicesChip);
    }

    /** The tomes for the build shown, made on first use (no initializer: safe to hot swap in). */
    private HomeTomes tomes;
    private int[] tomesChip;

    void tomesChip(int[] rect) {
        tomesChip = rect;
    }

    void openTomes() {
        if (tomes == null) {
            tomes = new HomeTomes();
        }
        tomes.openMenu(tomesChip);
    }

    HomeAlts alts() {
        if (alts == null) {
            alts = new HomeAlts(menu);
        }
        return alts;
    }

    /** The road map as the Home shows it: the selected milestone as the build picked in its menu. */
    List<Roadmap.Step> viewSteps() {
        return alts().view(menu.steps, menu.selected);
    }

    private HomeHero hero() {
        if (hero == null) {
            hero = new HomeHero(menu, this);
        }
        return hero;
    }

    boolean goalOpen() {
        return goalOpen;
    }

    void goalAnchor(int[] r) {
        goalAnchor = r;
    }
    /** Upgrades reached by the selected milestone, by slot type name ("RING", "RELIK", ...). */
    private Map<String, Roadmap.Gear> upgrades() {
        Map<String, Roadmap.Gear> out = new LinkedHashMap<>();
        List<Roadmap.Step> steps = viewSteps();
        for (int i = 0; i <= menu.selected && i < steps.size(); i++) {
            for (Roadmap.Gear g : steps.get(i).gear()) {
                out.put(g.slot(), g);
            }
        }
        if (!steps.isEmpty()) {
            Roadmap.Step at = steps.get(Math.min(menu.selected, steps.size() - 1));
            Roadmap.keepWorn(out, at);
        }
        // what the best build you own puts on from the bag, where the road map has no pick: the
        // plan follows a piece taken off within seconds, the map only when built again
        Roadmap.bagRows().forEach(out::putIfAbsent);
        return out;
    }

    // ---- home (Ana) ----

    record UpRow(Box box, Slot type, ItemStack worn, Roadmap.Gear gear) {}

    /**
     * Wynncraft's stand-in for an empty slot ("Leggings Slot", "Ring Slot"): nothing is worn
     * there, so the stand shows the slot empty, not a tick on it.
     */
    private static boolean emptySlot(ItemStack s) {
        if (s.isEmpty()) {
            return true;
        }
        var v = GameState.current().view(s);
        return (v.isEmpty() || !v.get().isGear() && !v.get().isCrafted())
                && ItemJson.clean(s.getHoverName().getString()).trim().endsWith(" Slot");
    }

    /** A slot's name on a row; the two rings as "Ring 1" and "Ring 2". */
    static String boxLabel(Box box) {
        return L.t(box.label()) + (box.type() == Slot.RING ? " " + (box.ring() + 1) : "");
    }

    /** Every slot with what you wear and the upgrade the selected level brings (null: keep). */
    private List<UpRow> upRows() {
        Map<String, Roadmap.Gear> up = upgrades();
        LocalPlayer player = Minecraft.getInstance().player;
        int weakRing = weakerRing(player);
        List<UpRow> out = new ArrayList<>();
        List<Box> boxes = new ArrayList<>(List.of(LEFT));
        boxes.addAll(List.of(RIGHT));
        for (Box box : boxes) {
            Slot type = box.type() == null ? Slot.weaponOf(Wyn.classType()) : box.type();
            ItemStack worn = ItemStack.EMPTY;
            if (player != null && type != null) {
                List<ItemStack> l = AutoAdvice.worn(type);
                if (box.ring() < l.size() && !emptySlot(l.get(box.ring()))) {
                    worn = l.get(box.ring());
                }
            }
            Roadmap.Gear u = type == null ? null : up.get(type.name());
            if (type == Slot.RING) {
                // the ring pick replaces the weaker ring, a pick for the other hand the other one
                Roadmap.Gear second = up.get(Roadmap.RING2);
                if (u == null) {
                    u = box.ring() == weakRing ? second : null;
                } else if (box.ring() != weakRing) {
                    u = second;
                }
            }
            if (u == null && type != null) {
                u = up.get("POWDER:" + Build.slotOf(type, box.ring()));     // no new piece there: its powders
            }
            out.add(new UpRow(box, type, worn, u));
        }
        return out;
    }

    private void drawHome(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        boolean compact = menu.layout != null && menu.layout.compact();
        if (!Wyn.up(Wyn.Feature.GEAR_DB) || !Wyn.up(Wyn.Feature.CHARACTER)) {
            UiEmpty.draw(g, font(), x, y, w, h, compact, "alert-triangle", L.t("wynnlabs.wyn.home_off", Wyn.version()), List.of(),
                    null, null, mx, my);
            return;
        }
        if (!Rules.adviceHere()) {
            // an alt with the advice off: say so, with the switch right here
            int[] box = UiEmpty.draw(g, font(), x, y, w, h, compact, "eye", L.t("wynnlabs.home.advice_off_title"),
                    List.of(L.t("wynnlabs.home.advice_off_1")), new UiEmpty.Action("check", L.t("wynnlabs.home.advice_on"), "#qadvice:"),
                    null, mx, my);
            if (box != null) {
                menu.hits.add(new MenuHit(box[0], box[1], box[2], box[3], null, null, "#qadvice:"));
            }
            return;
        }
        if (menu.error != null) {
            ink(g, Component.literal(menu.error).withStyle(ChatFormatting.RED), x, y + 4, 0xFFFFFFFF);
            return;
        }
        // with no road map yet the gear panel offers to calculate one: nothing is built on its own
        // the character card, the settings' chips and the experience bar (HomeHero)
        int by = hero().draw(g, x, y, w, mx, my) + Theme.SP_4;
        // 4px short of the page's foot: the parchment's corner curls sit there
        // a level pill other than "now": the stats compare with the road map's build for that level
        Plans.Build current = Plans.current();
        boolean levelPill = !HomeLevels.planPills.planSelected(current) && menu.selected > 0 && menu.selected < menu.steps.size();
        stats().source.level(levelPill ? viewSteps().get(menu.selected) : null);
        boolean planPill = HomeLevels.planPills.planSelected(current);
        stats().source.planPill(planPill);
        stats().source.pill(planPill ? "plan" : "at" + menu.selected);
        HomeTab.INSTANCE.draw(g, homeIn(), homeHost, stats(), x, by, w, y + h - by - 4, menu.layout.compact(), menu.layout.large(), mx, my);
    }

    /** This frame's slots for the home's stand: every gear slot with what you wear and the selected level's upgrade. */
    private HomeTab.In homeIn() {
        List<HomeTab.SlotView> slots = new ArrayList<>();
        List<UpRow> rows = upRows();
        for (int i = 0; i < rows.size(); i++) {
            UpRow r = rows.get(i);
            Roadmap.Gear u = r.gear();
            slots.add(new HomeTab.SlotView(i, boxLabel(r.box()), r.type(), r.worn(), u, u == null ? ItemStack.EMPTY : menu.icon(u.info()),
                    u == null ? Double.NaN : HomeGearTip.rowGain(menu, u), u == null ? null : context(HomeGearTip.gearTip(menu, u, r.worn().isEmpty()))));
        }
        // the build score now, and with the selected milestone's whole set (NaN when it has none)
        Build.Plan plan = Build.plan();
        double now = plan == null || plan.current() == null ? Double.NaN : plan.current().objective();
        List<Roadmap.Step> steps = viewSteps();
        Roadmap.Step s = steps.isEmpty() ? null : steps.get(Math.max(0, Math.min(menu.selected, steps.size() - 1)));
        double after = s == null || s.set() == null ? Double.NaN : s.set().result().objective();
        Plans.Build current = Plans.current();
        int chosen = Locks.names().size();
        int saved = Plans.saved().size();
        boolean searching = Around.running();
        if (HomeLevels.planPills.planSelected(current)) {
            return planView().view(current, rows, now, after, chosen, saved, searching, places(), road());
        }
        return new HomeTab.In(slots, now, after, menu.quests.isEmpty() ? null : menu.quests.get(0), places(), null, chosen,
                saved, searching, road());
    }

    /** A pick's story without its first line (the name): the item's own tooltip has the name. */
    private static List<Component> context(List<Component> tip) {
        return tip.size() <= 1 ? List.of() : List.copyOf(tip.subList(1, tip.size()));
    }

    /** The places to level now, from the road map (none without one). */
    private List<String> places() {
        return menu.steps.isEmpty() ? List.of() : menu.steps.get(0).places();
    }

    /** The road map's state for the gear panel: none yet, being built (and how far), out of date. */
    private HomeTab.Road road() {
        HomeTab.Road preview = HomeTab.previewRoad;
        return preview != null ? preview : new HomeTab.Road(menu.steps.isEmpty(), Roadmap.isBuilding(), Roadmap.progress(), Roadmap.stale());
    }

    /**
     * Removes the plan: the panel shows the suggestions with "Now" selected; the chosen items,
     * the strip and the saved builds stay; the build line offers Undo for a few seconds.
     */
    boolean removePlan() {
        if (!Plans.remove(System.currentTimeMillis())) {
            return false;
        }
        menu.selected = 0;
        return true;
    }

    /** The build window's and the stand's hosts, and the item hover (HomeHosts). */
    private final HomeHosts hosts = new HomeHosts(menu, this);
    final BuildWindow.Host windowHost = hosts.window;
    private final HomeTab.Host homeHost = hosts.home;

    /** The stats list's scrollbar thumb follows the mouse while dragged; true while it is. */
    boolean statsDragged(double y) {
        if (detail != null && detailBar.mouseDragged(y)) {
            detailScroll = detailBar.offset();
            return true;
        }
        if (alts != null && alts.drag(y)) {
            return true;
        }
        if (choices != null && choices.drag(y)) {
            return true;
        }
        if (tomes != null && tomes.drag(y)) {
            return true;
        }
        return stats != null && stats.barDragged(y);
    }

    void statsReleased() {
        detailBar.mouseReleased();
        if (alts != null) {
            alts.release();
        }
        if (choices != null) {
            choices.release();
        }
        if (tomes != null) {
            tomes.release();
        }
        if (stats != null) {
            stats.barReleased();
        }
    }

    /** The stats list, made on first use (a field without an initializer: safe to hot swap in). */
    HomeStats stats() {
        if (stats == null) {
            stats = new HomeStats();
        }
        return stats;
    }

    /** A bar's or the gauge's tooltip: the value, and how it compares with the next milestone's build. */
    HomeLevels levels() {
        if (levels == null) {
            levels = new HomeLevels(menu);
        }
        return levels;
    }

    HomeGoals goals() {
        if (goals == null) {
            goals = new HomeGoals(menu, this);
        }
        return goals;
    }

    private HomePlanView planView() {
        if (planView == null) {
            planView = new HomePlanView(menu);
        }
        return planView;
    }

    /** The milestones with a level pill (HomeLevels). */
    List<Integer> shownSteps() {
        return levels().shown();
    }

    List<Component> vsTip(String label, String value, double frac, int targetLevel) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(label + " · " + value).withStyle(ChatFormatting.WHITE));
        if (targetLevel > 0) {
            tip.add(L.c("wynnlabs.home.hud_vs", Math.round(frac * 100), targetLevel).withStyle(ChatFormatting.GRAY));
        } else {
            tip.add(L.c("wynnlabs.home.hud_vs_none").withStyle(ChatFormatting.DARK_GRAY));
        }
        return tip;
    }

    private int weakerRing(LocalPlayer player) {
        if (player == null) {
            return 0;
        }
        List<ItemStack> rings = AutoAdvice.worn(Slot.RING);
        if (rings.size() < 2) {
            return 0;
        }
        double a = AutoAdvice.scoreAny(rings.get(0));
        double b = AutoAdvice.scoreAny(rings.get(1));
        a = Double.isNaN(a) ? -1 : a;
        b = Double.isNaN(b) ? -1 : b;
        return b < a ? 1 : 0;
    }

    // ---- comparison panel (a slot's options): HomeOptions ----

    /** The options table, made on first use (a field without an initializer: safe to hot swap in). */
    private HomeOptions options() {
        if (options == null) {
            options = new HomeOptions(menu, this);
        }
        return options;
    }
}
