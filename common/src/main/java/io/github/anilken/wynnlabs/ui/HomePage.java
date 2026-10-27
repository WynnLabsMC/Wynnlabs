package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.advice.Advice;
import io.github.anilken.wynnlabs.advice.AutoAdvice;
import io.github.anilken.wynnlabs.advice.Cautions;
import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.build.TreePlanner;
import io.github.anilken.wynnlabs.build.roadmap.PanelOptions;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Slot;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.Waypoints;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
        cardHits.clear();
        backRect = null;
        filterRects = null;
        listRect = null;
    }

    /** The build window covers the home: its slots and level pills take no click. */
    void underWindow() {
        slotHits.clear();
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
    };

    @Override
    Popup popup() {
        return popups;
    }

    private boolean popupOpenImpl() {
        return goalOpen || stats != null && stats.picker.open();
    }

    private void drawPopupImpl(GuiGraphics g, int mx, int my, List<int[]> boxes) {
        int[] b = drawGoalList(g, mx, my);
        if (b != null) {
            boxes.add(b);
        }
        b = stats != null ? stats.picker.drawList(g, homeHost, mx, my) : null;
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
    }

    private boolean popupClickedImpl(MouseButtonEvent event) {
        if (stats != null && stats.picker.open()) {
            stats.picker.close();               // a row's own click area still picks
            return false;
        }
        return goalClicked(event.x(), event.y());
    }

    void pickGoal(int i) {
        goalOpen = false;
        String goal = Setup.GOALS.get(i);
        if (!goal.equals(Setup.goal())) {
            Setup.setGoal(goal);
            menu.flash(L.t("wynnlabs.home.goal_set", Setup.goalName(goal)));
            menu.rebuildMap();                 // rebuilt in the background, picked up when ready
        }
    }

    /** A click while the goal list is open: it closes, and a row picks that goal. True when the click is used up. */
    private boolean goalClicked(double ex, double ey) {
        if (!goalOpen) {
            return false;
        }
        goalOpen = false;
        for (int[] r : goalRows) {
            if (in(ex, ey, r)) {
                pickGoal(r[4]);
                return true;
            }
        }
        return goalAnchor != null && in(ex, ey, goalAnchor);    // the button again: closed
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
            if (in(ex, ey, r) && r[4] == PLAN_PILL) {
                planPills.pickPlan();
                return true;
            }
            if (in(ex, ey, r)) {
                planPills.pickLevel();
                // ‹ and › step the selection; the window follows it
                List<Integer> shown = shownSteps();
                int pos = shown.indexOf(menu.selected);
                menu.selected = r[4] == -1 ? shown.get(Math.max(0, Math.min(pos, levelOff) - 1))
                        : r[4] == -2 ? shown.get(Math.min(shown.size() - 1, Math.max(pos, levelOff + levelWindow - 1) + 1))
                        : r[4];
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
        planPills.pickLevel();
        // the wheel walks the tabs: şimdi, then the shown milestones
        List<Integer> order = new ArrayList<>();
        order.add(0);
        order.addAll(shownSteps());
        int pos = Math.max(0, order.indexOf(menu.selected));
        menu.selected = order.get(Math.max(0, Math.min(order.size() - 1, pos - (int) Math.signum(v))));
    }

    /** A home {@code #q} action (the build line, the plan, the goal, the road map, powders, archetype, a place to mark). */
    void action(String c, int button) {
        if (c.startsWith("#qadvice:")) {
            Rules.setAdviceHere(true);
            menu.rebuildMap();
        } else if (c.startsWith("#qpick:")) {
            if (button == 1) {
                // right-click: the plan removed, as its pill's ✕ does (Undo on the build line);
                // with no plan, the chosen items cleared
                if (!removePlan()) {
                    Locks.clear();
                    Around.forget();
                    menu.flash(L.t("wynnlabs.homev.pick_cleared"));
                }
            } else {
                BuildWindow.INSTANCE.openPick();
            }
        } else if (c.startsWith("#qplanremove:")) {
            removePlan();
        } else if (c.startsWith("#qplansave:")) {
            boolean was = Plans.isSaved(Plans.current());
            Plans.Build kept = Plans.savePlan(System.currentTimeMillis());
            if (kept != null) {
                menu.flash(L.t(was ? "wynnlabs.homev.plan_updated" : "wynnlabs.homev.plan_saved_flash", kept.name()));
            }
        } else if (c.startsWith("#qplanedit:")) {
            BuildWindow.INSTANCE.editPlan(Plans.current());
        } else if (c.startsWith("#qplanundo:")) {
            Plans.undoRemove(System.currentTimeMillis());
        } else if (c.startsWith("#qsaved:")) {
            BuildWindow.INSTANCE.openSaved();
        } else if (c.startsWith("#qhint:")) {
            if (button == 1) {
                Plans.HINT.dismiss();
            } else {
                BuildWindow.INSTANCE.openResults(Around.checkResult());
            }
        } else if (c.startsWith("#qstatsclear:")) {
            stats().clearPick();               // the stats list's ✕: the applied plan again
        } else if (c.startsWith("#qstatssrc:")) {
            stats().picker.toggle();           // the compare button: its list
        } else if (c.startsWith("#qstatsset:")) {
            stats().source.choose(c.substring(11));
        } else if (c.startsWith("#qstatstab:")) {
            stats().spellsTab = c.endsWith("spells");     // the compact column's tabs
        } else if (c.startsWith("#qstatsflip:")) {
            stats().source.flip();             // your numbers, or the other build's
        } else if (c.startsWith("#qgoal:")) {
            goalOpen = true;                    // the list opens; a row picks the goal
            goalMenu.reset();
        } else if (c.startsWith("#qrefresh:") || c.startsWith("#qbuild:")) {
            Roadmap.forceRefresh();
            menu.refreshAsked = true;
        } else if (c.startsWith("#qcancelbuild:")) {
            Roadmap.cancelBuild();
            menu.refreshAsked = false;
        } else if (c.startsWith("#qxp:")) {
            boolean on = !Setup.countsXp();
            Setup.setCountsXp(on);
            menu.flash(L.t(on ? "wynnlabs.home.count_xp_on" : "wynnlabs.home.count_xp_off"));
            menu.rebuildMap();                     // rebuilt in the background, picked up when ready
        } else if (c.startsWith("#qpowder:")) {
            boolean on = Rules.sub("model", "assumePowders", 1) > 0;
            JsonObject pm = new JsonObject();
            pm.addProperty("assumePowders", on ? 0 : 1);
            JsonObject pc = new JsonObject();
            pc.add("model", pm);
            Rules.update(pc);
            menu.flash(L.t(on ? "wynnlabs.home.powder_off" : "wynnlabs.home.powder_on"));
            menu.rebuildMap();                     // rebuilt in the background, picked up when ready
        } else if (c.startsWith("#qarch:")) {
            menu.archPicker.openAt(c);             // the list opens; a row picks the archetype
        } else if (c.startsWith("#qnear:")) {
            String[] f = c.substring(7).split(" ", 4);
            boolean ok = Waypoints.go(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3]);
            menu.flash(ok ? L.t("wynnlabs.home.marked", f[3]) : L.t("wynnlabs.home.mark_failed"));
        }
    }

    // ---- drawing ----

    private record Box(String label, Slot type, int ring) {}

    /** Box labels are language keys; show with {@code L.t(box.label())}. */
    private static final Box[] LEFT = {new Box("wynnlabs.screen.slot_helmet", Slot.HELMET, 0),
        new Box("wynnlabs.screen.slot_chestplate", Slot.CHESTPLATE, 0),
        new Box("wynnlabs.screen.slot_leggings", Slot.LEGGINGS, 0), new Box("wynnlabs.screen.slot_boots", Slot.BOOTS, 0),
        new Box("wynnlabs.screen.slot_weapon", null, 0)};
    private static final Box[] RIGHT = {new Box("wynnlabs.screen.slot_ring", Slot.RING, 0),
        new Box("wynnlabs.screen.slot_ring", Slot.RING, 1),
        new Box("wynnlabs.screen.slot_bracelet", Slot.BRACELET, 0), new Box("wynnlabs.screen.slot_necklace", Slot.NECKLACE, 0)};
    /** First milestone shown after "now" in the level buttons, and how many fit. */
    private int levelOff = 0;            // the first shown milestone tab, as a position in shownSteps()
    private static final int LEVEL_WINDOW = 5;
    private final List<int[]> levelRows = new ArrayList<>();   // x, y, w, h, step index
    private final List<Object[]> slotHits = new ArrayList<>();  // {int[] rect, Roadmap.Gear, label, worn}
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
    private boolean goalOpen;
    private HomeStats stats;
    private int[] goalAnchor;
    /** The character card on top, made on first use (a field without an initializer: safe to hot swap in). */
    private HomeHero hero;

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
    private final List<int[]> goalRows = new ArrayList<>();     // x, y, w, h, index in Setup.GOALS

    /** One row per goal: its name and what it weighs, the current one marked, the hovered one lit (when the list is open). */
    private final UiMenu.State goalMenu = new UiMenu.State();

    private int[] drawGoalList(GuiGraphics g, int mx, int my) {
        if (!goalOpen || goalAnchor == null) {
            return null;
        }
        goalRows.clear();
        return UiMenu.draw(g, font(), UiMenu.Spec.under(goalAnchor, menu.width, menu.height), goalMenu, goalItems(), mx, my,
                r -> goalRows.add(new int[] {r.x(), r.y(), r.w(), r.h(), Integer.parseInt(r.item().id())}));
    }

    private boolean popupKeyImpl(int key) {
        if (!goalOpen && stats != null && stats.picker.open()) {
            UiMenu.Item it = stats.picker.list.key(key);
            if (it != null) {
                stats.picker.close();
                menu.runAction(it.id());
            }
            return true;
        }
        if (!goalOpen) {
            return false;
        }
        UiMenu.Item it = goalMenu.key(key);
        if (it != null) {
            pickGoal(Integer.parseInt(it.id()));
        }
        return true;
    }

    /** The goals as dropdown rows: the name, what it weighs under it, the current one chosen (the home and the settings). */
    static List<UiMenu.Item> goalItems() {
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

    /** Upgrades reached by the selected milestone, by slot type name ("RING", "RELIK", ...). */
    private Map<String, Roadmap.Gear> upgrades() {
        Map<String, Roadmap.Gear> out = new LinkedHashMap<>();
        for (int i = 0; i <= menu.selected && i < menu.steps.size(); i++) {
            for (Roadmap.Gear g : menu.steps.get(i).gear()) {
                out.put(g.slot(), g);
            }
        }
        if (!menu.steps.isEmpty()) {
            Roadmap.Step at = menu.steps.get(Math.min(menu.selected, menu.steps.size() - 1));
            Roadmap.keepWorn(out, at);
        }
        // what the best build you own puts on from the bag, where the road map has no pick: the
        // plan follows a piece taken off within seconds, the map only when built again
        Roadmap.bagRows().forEach(out::putIfAbsent);
        return out;
    }

    // ---- home (Ana) ----

    private record UpRow(Box box, Slot type, ItemStack worn, Roadmap.Gear gear) {}

    /**
     * The level a milestone's whole set is wearable at: its highest piece. A milestone at 120
     * often brings Lv 105 pieces, so its tab reads 105 or so, not 120.
     */
    private static int wearAt(Roadmap.Step s) {
        int hi = 0;
        for (Damage.Piece p : s.build().values()) {
            if (p != null) {
                hi = Math.max(hi, p.level());
            }
        }
        for (Roadmap.Gear g : s.gear()) {
            hi = Math.max(hi, g.info().requirements().level());
        }
        return hi == 0 ? s.level() : hi;
    }

    /**
     * The milestones with a tab: one is left out when its set is wearable already (the now tab
     * has it) or the next one's is wearable as early (its picks were there already, only better
     * chosen), so the tabs climb one level each.
     */
    List<Integer> shownSteps() {
        List<Integer> out = new ArrayList<>();
        int now = menu.steps.isEmpty() ? 0 : wearAt(menu.steps.get(0));
        for (int i = 1; i < menu.steps.size(); i++) {
            int at = wearAt(menu.steps.get(i));
            // wearable already: the now tab has it; a later set as early: that one is better
            if (at <= now || i + 1 < menu.steps.size() && wearAt(menu.steps.get(i + 1)) <= at) {
                continue;
            }
            out.add(i);
        }
        return out;
    }

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
    private static String boxLabel(Box box) {
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
        boolean levelPill = !planPills.planSelected(current) && menu.selected > 0 && menu.selected < menu.steps.size();
        stats().source.level(levelPill ? menu.steps.get(menu.selected) : null);
        stats().source.planPill(planPills.planSelected(current));
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
                    u == null ? Double.NaN : rowGain(u), u == null ? null : context(gearTip(u))));
        }
        // the build score now, and with the selected milestone's whole set (NaN when it has none)
        Build.Plan plan = Build.plan();
        double now = plan == null || plan.current() == null ? Double.NaN : plan.current().objective();
        Roadmap.Step s = menu.steps.isEmpty() ? null : menu.steps.get(Math.max(0, Math.min(menu.selected, menu.steps.size() - 1)));
        double after = s == null || s.set() == null ? Double.NaN : s.set().result().objective();
        Plans.Build current = Plans.current();
        int chosen = Locks.names().size();
        int saved = Plans.saved().size();
        boolean searching = Around.running();
        if (planPills.planSelected(current)) {
            return planIn(current, rows, now, after, chosen, saved, searching);
        }
        return new HomeTab.In(slots, now, after, menu.quests.isEmpty() ? null : menu.quests.get(0), places(), null, chosen,
                saved, searching, road());
    }

    /**
     * The one way an item's hover is made on the Home tab and in the build window: its full
     * tooltip (tier-coloured name, type and level, requirements, base stats, identification
     * ranges) and then, after a blank line, {@code extra} (plan, source, gain, story). A worn
     * piece shows its own stack's tooltip with your rolls; another piece Wynntils' tooltip of its
     * guide stack, or without one ({@link BuildWindow#itemTooltip}) the item data's. {@code front}:
     * ahead of the other hits (an icon over its card).
     */
    private void itemHit(boolean front, int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> extra) {
        itemHit(front, x, y, w, h, info, worn, List.of(), extra);
    }

    /** {@link #itemHit}, with {@code lead} lines over the item's tooltip (a blank line between). */
    private void itemHit(boolean front, int x, int y, int w, int h, ItemData info, ItemStack worn, List<Component> lead,
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
    private boolean removePlan() {
        if (!Plans.remove(System.currentTimeMillis())) {
            return false;
        }
        menu.selected = 0;
        return true;
    }

    /** The plan's pieces by name, looked up once per plan. */
    private final Map<String, ItemData> planInfo = new HashMap<>();
    private String planInfoFor = "";

    /**
     * The Plan pill's view: each slot the plan's piece, a tick where you wear it already (by
     * name), purple for the items you chose; the line under the stand counts what you wear.
     */
    private HomeTab.In planIn(Plans.Build plan, List<UpRow> rows, double now, double after, int chosen, int saved, boolean searching) {
        if (!planInfoFor.equals(plan.id() + plan.updated())) {
            planInfo.clear();
            java.util.Set<String> want = new java.util.HashSet<>(plan.items().values());
            GameState.current().allGear().filter(i -> want.contains(i.name())).forEach(i -> planInfo.putIfAbsent(i.name(), i));
            planInfoFor = plan.id() + plan.updated();
        }
        Map<String, String> wornNames = new LinkedHashMap<>();
        for (int i = 0; i < rows.size() && i < Plans.SLOTS.length; i++) {
            ItemStack w = rows.get(i).worn();
            if (!w.isEmpty()) {
                wornNames.put(Plans.SLOTS[i], ItemJson.clean(w.getHoverName().getString()).trim());
            }
        }
        // the plan's rings in the hands that wear them, so a ring worn in the other hand shows there
        Map<String, String> items = new LinkedHashMap<>(plan.items());
        String r1 = items.get("RING1");
        String r2 = items.get("RING2");
        if (r1 != null && r2 != null && !r1.equals(wornNames.get("RING1"))
                && (r2.equals(wornNames.get("RING1")) || r1.equals(wornNames.get("RING2")))) {
            items.put("RING1", r2);
            items.put("RING2", r1);
        }
        boolean[] on = Plans.wornSlots(items, wornNames);
        List<HomeTab.SlotView> slots = new ArrayList<>();
        int worn = 0;
        double left = 0;
        for (int i = 0; i < rows.size(); i++) {
            UpRow r = rows.get(i);
            String name = i < Plans.SLOTS.length ? items.get(Plans.SLOTS[i]) : null;
            ItemData info = name == null ? null : planInfo.get(name);
            if (name == null || info == null) {
                slots.add(new HomeTab.SlotView(i, boxLabel(r.box()), r.type(), r.worn(), null, ItemStack.EMPTY, Double.NaN, null));
                continue;
            }
            boolean locked = plan.locked(name);
            boolean has = on[i];
            worn += has ? 1 : 0;
            HomeTab.Mark mark = has ? locked ? HomeTab.Mark.DONE_CHOSEN : HomeTab.Mark.DONE : locked ? HomeTab.Mark.CHOSEN : HomeTab.Mark.GET;
            List<Component> tip = new ArrayList<>();
            List<Component> lead = new ArrayList<>();
            tip.add(L.c(locked ? "wynnlabs.homev.slot_chosen" : "wynnlabs.homev.slot_planned").withStyle(ChatFormatting.LIGHT_PURPLE));
            if (has) {
                tip.add(L.c("wynnlabs.homev.slot_worn").withStyle(ChatFormatting.GREEN));
            } else {
                // the pill's icon explained first: "Missing · dropped by mobs", then where, when known
                lead.add(L.c("wynnlabs.homev.slot_missing", io.github.anilken.wynnlabs.build.BuildSources.sourceText(name)).withStyle(ChatFormatting.GOLD));
                String detail = io.github.anilken.wynnlabs.build.BuildSources.sourceDetail(name);
                if (detail != null) {
                    lead.add(Component.literal(detail).withStyle(ChatFormatting.GRAY));
                }
                Prices.Price p = Prices.of(name);
                if (p != null && p.typical() != null && Shopping.tradable(info)) {
                    left += p.typical();
                }
            }
            slots.add(new HomeTab.SlotView(i, boxLabel(r.box()), r.type(), r.worn(), null, menu.icon(info), Double.NaN, tip, mark, info, lead));
        }
        int better = Plans.HINT.showing();
        HomeTab.PlanLine line = new HomeTab.PlanLine(plan.name(), worn, plan.items().size(), left, better,
                plan.level() > GameState.current().level() ? plan.level() : 0);
        return new HomeTab.In(slots, now, after, menu.quests.isEmpty() ? null : menu.quests.get(0), places(), line, chosen,
                saved, searching, road());
    }

    /** The build window's way back into the screen. */
    final BuildWindow.Host windowHost = new BuildWindow.Host() {
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
            planPills.pickPlan();
            menu.flash(L.t("wynnlabs.homev.plan_applied"));
        }

        @Override
        public void detailed(Roadmap.Candidate c, int level, int rank) {
            stats().pick(c, level, rank);
        }
    };

    /** The home's way back into the screen: its click and hover areas join this frame's hits. */
    private final HomeTab.Host homeHost = new HomeTab.Host() {
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
        public void compare(int[] rect, HomeTab.SlotView s) {
            if (s.gear() != null && s.gear().options() != null && !s.gear().options().isEmpty()) {
                slotHits.add(new Object[] {rect, s.gear(), s.label(), s.worn()});
            }
        }

        @Override
        public int levelPills(GuiGraphics g, int y, int right, int room) {
            return HomePage.this.levelPills(g, y, right, room);
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

    /** The stats list's scrollbar thumb follows the mouse while dragged; true while it is. */
    boolean statsDragged(double y) {
        if (detail != null && detailBar.mouseDragged(y)) {
            detailScroll = detailBar.offset();
            return true;
        }
        return stats != null && stats.barDragged(y);
    }

    void statsReleased() {
        detailBar.mouseReleased();
        if (stats != null) {
            stats.barReleased();
        }
    }

    /** The stats list, made on first use (a field without an initializer: safe to hot swap in). */
    private HomeStats stats() {
        if (stats == null) {
            stats = new HomeStats();
        }
        return stats;
    }

    /** A bar's or the gauge's tooltip: the value, and how it compares with the next milestone's build. */
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

    /** The first road map of a character is being built: a bar for how far it is. */

    /** How many milestone buttons the level row shows this frame (LEVEL_WINDOW where they fit). */
    private int levelWindow = LEVEL_WINDOW;

    /**
     * The level buttons (şimdi · ‹ · Lv 60 · 70 … · ›) ending at {@code right}, their text on
     * line {@code y}; each names the level its whole set is wearable at, the window follows the
     * selected one and shows as many milestones as {@code room} takes. Returns their left edge.
     */
    private int levelPills(GuiGraphics g, int y, int right, int room) {
        List<Integer> shown = shownSteps();
        int pos = shown.indexOf(menu.selected);
        if (menu.selected > 0 && pos < 0 && !shown.isEmpty()) {
            // a hidden milestone: the next shown one has a set as early and better
            int to = shown.get(shown.size() - 1);
            for (int i : shown) {
                if (i >= menu.selected) {
                    to = i;
                    break;
                }
            }
            menu.selected = to;
            pos = shown.indexOf(menu.selected);
        }
        levelWindow = LEVEL_WINDOW;
        // with a plan, its pill first: "Plan · Now · Lv 68 · 80 …"
        Plans.Build plan = Plans.current();
        boolean planOn = planPills.planSelected(plan);
        String planText = L.t("wynnlabs.homev.plan_pill");
        int planW = plan == null ? 0 : Type.BODY.width(font(), planText) + 14 + PLAN_X;
        room -= plan == null ? 0 : planW + 3;
        List<int[]> order = pillOrder(shown, pos, levelWindow);
        while (levelWindow > 1 && pillsWidth(order, shown) > room) {
            order = pillOrder(shown, pos, --levelWindow);
        }
        int firstShown = levelOff < shown.size() ? shown.get(levelOff) : -9;
        int bx = right;
        for (int k = order.size() - 1; k >= 0; k--) {
            int i = order.get(k)[0];
            String t = pillText(i, firstShown);
            int tw = i < 0 ? 16 : font().width(t) + 12;
            bx -= tw;
            boolean on = !planOn && i == menu.selected;
            if (i < 0) {
                Ui.iconButton(g, i == -1 ? "chevron-left" : "chevron-right", bx, y - 3, false, menu.frameMx, menu.frameMy);
            } else {
                Ui.chip(g, font(), t, bx, y - 3, tw, on, false, false, menu.frameMx, menu.frameMy);
            }
            levelRows.add(new int[] {bx, y - 3, tw, 14, i});
            if (i > 0) {
                menu.hits.add(new MenuHit(bx, y - 3, tw, 14, null, List.of(
                        L.c("wynnlabs.home.levels_tip", wearAt(menu.steps.get(i)), menu.steps.get(i).level())
                                .withStyle(ChatFormatting.GRAY)), null));
            }
            bx -= 3;
        }
        if (plan != null) {
            bx -= planW;
            // purple when selected, like the chosen items on the panel; a thin chip otherwise
            boolean ph = menu.frameMx >= bx && menu.frameMx < bx + planW && menu.frameMy >= y - 3 && menu.frameMy < y + 11;
            if (planOn) {
                Kit.pillPrimary(g, bx, y - 3, planW, Ui.CHIP_H, ph);
            } else {
                Kit.pill(g, bx, y - 3, planW, Ui.CHIP_H, false, ph);
            }
            Type.BODY.draw(g, font(), planText, bx + Theme.SP_4, Type.BODY.yIn(y - 3, Ui.CHIP_H), planOn ? Theme.CREAM : Theme.INK_2);
            levelRows.add(new int[] {bx, y - 3, planW - PLAN_X, 14, PLAN_PILL});
            menu.hits.add(new MenuHit(bx, y - 3, planW - PLAN_X, 14, null, List.of(Component.literal(plan.name()).withStyle(ChatFormatting.LIGHT_PURPLE),
                    L.c("wynnlabs.homev.plan_pill_tip").withStyle(ChatFormatting.GRAY)), null));
            // its ✕ at the right end: the plan removed (Undo on the build line for a few seconds)
            int xx = bx + planW - PLAN_X - 1;
            boolean xh = menu.frameMx >= xx && menu.frameMx < xx + 12 && menu.frameMy >= y - 2 && menu.frameMy < y + 10;
            int xink = planOn ? Theme.CREAM : Theme.INK_2;
            Art.icon(g, "x", xx + 2, y, 8, xh ? (planOn ? Theme.GOLD_EDGE : Theme.INK) : xink);
            List<Component> xtip = new ArrayList<>();
            xtip.add(L.c("wynnlabs.homev.plan_remove").withStyle(ChatFormatting.WHITE));
            xtip.add(L.c("wynnlabs.homev.plan_remove_tip").withStyle(ChatFormatting.GRAY));
            if (!Plans.isSaved(plan)) {
                xtip.add(L.c("wynnlabs.homev.plan_unsaved").withStyle(ChatFormatting.GOLD));
            }
            menu.hits.add(new MenuHit(xx, y - 2, 12, 12, null, xtip, "#qplanremove:"));
            bx -= 3;
        }
        return bx + 3;
    }

    /** The Plan pill's place in levelRows (the milestones are 0 and up, ‹ and › -1 and -2). */
    private static final int PLAN_PILL = -3;
    /** The room the Plan pill's ✕ takes at its right end. */
    private static final int PLAN_X = 13;
    /** Whether the Plan pill or a level pill is selected (kept across menu openings). */
    private static final Plans.Pills planPills = new Plans.Pills();

    /** The level row's buttons for a window of {@code window} milestones: {step index, or -1 ‹ / -2 ›}. */
    private List<int[]> pillOrder(List<Integer> shown, int pos, int window) {
        if (pos >= 0 && pos < levelOff) {
            levelOff = pos;
        } else if (pos >= levelOff + window) {
            levelOff = pos - window + 1;
        }
        levelOff = Math.max(0, Math.min(levelOff, Math.max(0, shown.size() - window)));
        List<int[]> order = new ArrayList<>();
        order.add(new int[] {0});
        if (levelOff > 0) {
            order.add(new int[] {-1});
        }
        for (int k = levelOff; k < shown.size() && k < levelOff + window; k++) {
            order.add(new int[] {shown.get(k)});
        }
        if (levelOff + window < shown.size()) {
            order.add(new int[] {-2});
        }
        return order;
    }

    private String pillText(int i, int firstShown) {
        return i < 0 ? "" : i == 0 ? L.t("wynnlabs.home.now_short") : (i == firstShown ? "Lv " : "") + wearAt(menu.steps.get(i));
    }

    private int pillsWidth(List<int[]> order, List<Integer> shown) {
        int firstShown = levelOff < shown.size() ? shown.get(levelOff) : -9;
        int n = 0;
        for (int[] o : order) {
            n += (o[0] < 0 ? 16 : font().width(pillText(o[0], firstShown)) + 12) + 3;
        }
        return n - 3;
    }

    /** The full story of an upgrade, for its hover. */
    /**
     * The gain a row shows: what the selected milestone's build gains over what you wear now in
     * that slot (Roadmap.Step.vsWorn), or the step over the previous milestone's pick when what
     * you wear there is far below it (Roadmap.shownGain).
     */
    private double rowGain(Roadmap.Gear u) {
        return Roadmap.shownGain(menu.steps, menu.selected, u);
    }

    private List<Component> gearTip(Roadmap.Gear u) {
        List<Component> tip = new ArrayList<>();
        ChatFormatting tc = u.info().tier().colour();
        tip.add(Component.literal(u.info().name()).withStyle(tc).withStyle(UiFont::bold));
        // a powder row is a piece you have: its source line names the powders, and there is no other source
        boolean powder = Roadmap.isPowder(u);
        tip.add(powder ? Component.literal(u.source()).withStyle(ChatFormatting.AQUA)
                : L.c("wynnlabs.home.tip_from", u.source()).withStyle(ChatFormatting.DARK_AQUA));
        String also = Roadmap.fromBag(u) || powder ? null : Shopping.alsoFrom(u.info());   // the Trade Market first, the world source under it
        if (also != null && !also.equalsIgnoreCase(u.source())) {
            tip.add(Component.literal("   " + L.t("wynnlabs.homev.source_also", L.cap(also))).withStyle(ChatFormatting.DARK_GRAY));
        }
        Object[] pw = powder ? null : Damage.powderHint(u.info());
        if (pw != null) {
            String[] roman = {"I", "II", "III", "IV", "V", "VI", "VII"};
            String el = switch ((String) pw[0]) {
                case "thunder" -> L.t("wynnlabs.damage.el_thunder");
                case "water" -> L.t("wynnlabs.damage.el_water");
                case "fire" -> L.t("wynnlabs.damage.el_fire");
                case "air" -> L.t("wynnlabs.damage.el_air");
                default -> L.t("wynnlabs.damage.el_earth");
            };
            tip.add(L.c("wynnlabs.home.tip_powder", pw[1], el,
                    roman[Math.max(0, Math.min(6, (int) pw[2] - 1))]).withStyle(ChatFormatting.AQUA));
        }
        if (u.price() != null) {
            tip.add(L.c("wynnlabs.home.tip_past_price", u.price()).withStyle(ChatFormatting.GOLD));
            Prices.Price pp = Prices.of(u.info().name());
            String doubt = pp == null ? null : pp.doubt();
            tip.add(Component.literal(doubt != null ? L.t("wynnlabs.home.tip_doubt", doubt)
                    : L.t("wynnlabs.home.tip_seen")).withStyle(doubt != null ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        }
        double vsWorn = rowGain(u);
        boolean step = !Double.isNaN(vsWorn) && Roadmap.showsStep(menu.steps, menu.selected, u);
        tip.add(Double.isNaN(vsWorn)
                ? L.c(Roadmap.isTarget(u) ? "wynnlabs.home.tip_target" : "wynnlabs.home.tip_new").withStyle(ChatFormatting.AQUA)
                : step ? (vsWorn < 0 ? L.c("wynnlabs.home.tip_step_loss", Math.round(-vsWorn * 10) / 10.0).withStyle(ChatFormatting.RED)
                        : L.c("wynnlabs.home.tip_step", Math.round(vsWorn * 10) / 10.0).withStyle(ChatFormatting.GREEN))
                : vsWorn < 0 ? L.c("wynnlabs.home.tip_loss", Math.round(-vsWorn * 10) / 10.0).withStyle(ChatFormatting.RED)
                : L.c("wynnlabs.home.tip_gain", Math.round(vsWorn * 10) / 10.0).withStyle(ChatFormatting.GREEN));
        if (!step && !Double.isNaN(u.gain()) && Math.abs(vsWorn - u.gain()) >= 0.5) {
            // at a later milestone the pick also builds on the picks before it
            tip.add(L.c("wynnlabs.home.tip_step", Math.round(u.gain() * 10) / 10.0).withStyle(ChatFormatting.DARK_GRAY));
        }
        // why this pick: what changes against what you wear, biggest first (else the step's own reasons)
        List<String> why = menu.steps.isEmpty() ? null
                : menu.steps.get(Math.max(0, Math.min(menu.selected, menu.steps.size() - 1))).whyVsWorn().get(u.slot());
        if (why == null || why.isEmpty()) {
            why = u.detail();
        }
        if (why != null && !why.isEmpty()) {
            tip.add(L.c("wynnlabs.home.tip_why").withStyle(ChatFormatting.GOLD).withStyle(UiFont::bold));
            for (String d : why) {
                tip.add(Component.literal("  • " + d).withStyle(ChatFormatting.AQUA));
            }
        }
        // what it asks for: its level and skill points
        List<String> needs = new ArrayList<>();
        needs.add("Lv " + u.info().requirements().level());
        for (var r : u.info().requirements().skills()) {
            if (r.points() > 0) {
                needs.add(r.points() + " " + r.skill().name().substring(0, 3));
            }
        }
        tip.add(L.c("wynnlabs.home.tip_needs", String.join(" · ", needs)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(Shopping.tradable(u.info()) ? L.t("wynnlabs.home.tip_tradable") : L.t("wynnlabs.home.tip_untradable"))
                .withStyle(ChatFormatting.GRAY));
        Integer seenAt = Rules.communityLevel(u.info().name());
        if (seenAt != null) {
            tip.add(L.c("wynnlabs.home.tip_community", seenAt)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        String caution = Cautions.line(u.info());
        if (caution != null) {
            for (String l : Advice.wrap(caution, 46)) {
                tip.add(Component.literal(l).withStyle(ChatFormatting.RED));
            }
        }
        boolean panel = u.options() != null && !u.options().isEmpty();
        tip.add(Component.literal(panel ? L.t("wynnlabs.home.tip_click_compare") : L.t("wynnlabs.screen.click_copy_name")).withStyle(ChatFormatting.DARK_GRAY));
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
