package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestGuides;
import io.github.anilken.wynnlabs.quests.QuestTrail;
import io.github.anilken.wynnlabs.quests.QuestWiki;
import io.github.anilken.wynnlabs.quests.Waypoints;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The quests tab, laid out like the quest guide: the quests on the left as the guide's stage rail
 * ({@link QuestsRail}), the picked quest on the right as its guide ({@link QuestsGuidePane}) or its
 * places with Wynntils' map ({@link QuestsPointsPane}); the order, filters and session plan in one
 * "Önerilen ▾" menu; a search in the title row. Compact windows fold the list into a menu chip.
 */
final class QuestsTab extends MenuTab {

    QuestsTab(RoadmapScreen menu) {
        super(menu);
        rail = new QuestsRail(menu, this);
        guide = new QuestsGuidePane(menu, this);
        points = new QuestsPointsPane(menu, this);
        map = new QuestMap(menu);
    }

    final QuestsRail rail;
    final QuestsGuidePane guide;
    final QuestsPointsPane points;
    final QuestMap map;
    /** The list's and the places' scroll bars: dragged, clicked or wheeled. */
    final ScrollBar railBar = new ScrollBar();
    final ScrollBar pointsBar = new ScrollBar();

    // ---- state ----

    /** The order and filter ({@link QuestAdvisor#FILTERS}). */
    String questFilter = "RECOMMENDED";
    /** The picked quest (null: none yet). */
    String questSelected;
    /** The pane: 0 the guide, 3 the places ("Noktalar"); the numbers the preview and dev tools set. */
    int questDetailTab;
    int railScroll;
    int pointScroll;
    String pointScrollKey = "";
    /** The "Önerilen ▾" menu open; compact: the list open over the pane; compact: the search box shown. */
    boolean filterOpen;
    boolean listOpen;
    boolean searchOpen;
    /** The place whose row the mouse is on (its marker lights on the map). */
    String litPlace;
    int[] railRect;
    int[] pointsRect;
    private EditBox searchBox;
    private int[] searchRect;
    private Map<String, List<QuestAdvisor.Suggestion>> questLists = new HashMap<>();
    private long questListsAt;

    // ---- the tab ----

    @Override
    List<EditBox> init() {
        String typed = searchBox == null ? "" : searchBox.getValue();
        searchBox = new EditBox(font(), 0, 0, 100, 10, L.c("wynnlabs.quests.search_hint"));
        searchBox.setMaxLength(40);
        paperField(searchBox);
        searchBox.setValue(typed);
        searchBox.setResponder(s -> railScroll = 0);
        return List.of(searchBox);
    }

    @Override
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (doubleClick && map.press(event)) {
            map.release();
            map.refit();                            // a double-click: back to the view that fits the places
            return true;
        }
        if (map.press(event)) {
            return true;                            // held: a pan if it moves, else replayed as a click on release
        }
        if (railRect != null && railBar.mouseClicked(event.x(), event.y())) {
            railScroll = railBar.offset();
            return true;
        }
        if (pointsRect != null && questDetailTab == 3 && pointsBar.mouseClicked(event.x(), event.y())) {
            pointScroll = pointsBar.offset();
            return true;
        }
        if (searchRect != null && in(event.x(), event.y(), searchRect)) {
            searchBox.mouseClicked(event, doubleClick);
            searchBox.setFocused(true);
            menu.setFocused(searchBox);
            return true;
        }
        if (searchBox != null) {
            searchBox.setFocused(false);
        }
        return false;
    }

    @Override
    boolean barDragged(double x, double y) {
        if (map.drag(x, y)) {
            return true;
        }
        if (railBar.mouseDragged(y)) {
            railScroll = railBar.offset();
            return true;
        }
        if (pointsBar.mouseDragged(y)) {
            pointScroll = pointsBar.offset();
            return true;
        }
        return false;
    }

    @Override
    void barReleased() {
        MouseButtonEvent click = map.release();
        if (click != null) {
            menu.replayClick(click);
        }
        railBar.mouseReleased();
        pointsBar.mouseReleased();
    }

    /** "/" while nothing is being typed: the search box. */
    boolean slashTyped() {
        searchOpen = true;
        searchBox.setFocused(true);
        menu.setFocused(searchBox);
        return true;
    }

    @Override
    boolean keyPressed(int key) {
        if (searchBox != null && searchBox.isFocused() && key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && !searchBox.getValue().isEmpty()) {
            searchBox.setValue("");
            return true;
        }
        return false;
    }

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        long t0 = System.nanoTime();
        drawQuests(g, x, y, w, h, mx, my);
        QuestsCache.frame(System.nanoTime() - t0);
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        int dir = (int) Math.signum(v);
        if (map.rect != null && in(x, y, map.rect)) {
            map.zoomAt(x, y, dir);
        } else if (pointsRect != null && in(x, y, pointsRect)) {
            pointScroll = Math.max(0, pointScroll - dir);
        } else if (railRect != null && in(x, y, railRect)) {
            railScroll = Math.max(0, railScroll - dir);
        }
    }

    /** What was typed in the search box ("" for none). */
    String query() {
        return searchBox == null ? "" : searchBox.getValue().strip();
    }

    // ---- actions ----

    /**
     * A quest {@code #q} action (the list's rows and menu, the pane's switch and buttons, tracking,
     * the rescan, pins, the session plan, the map's zoom); false when {@code c} is not one of them.
     */
    boolean action(String c) {
        if (c.startsWith("#qfilter:")) {
            questFilter = c.substring(9);
            railScroll = 0;
            filterOpen = false;
            searchBox.setValue("");
        } else if (c.equals("#qmenu")) {
            filterOpen = !filterOpen;
        } else if (c.equals("#qlist")) {
            listOpen = !listOpen;
        } else if (c.equals("#qsearch")) {
            searchOpen = !searchOpen;
            if (!searchOpen) {
                searchBox.setValue("");
            }
        } else if (c.startsWith("#qsel:")) {
            questSelected = c.substring(6);
            pointScrollKey = "";
            listOpen = false;
            map.zoom = 1;
        } else if (c.startsWith("#qview:")) {
            questDetailTab = Integer.parseInt(c.substring(7));
            map.zoom = 1;
        } else if (c.equals("#qmapfit")) {
            map.refit();
        } else if (c.startsWith("#qzoom:")) {
            map.zoomBy(c.endsWith("+") ? 1 : -1);
        } else if (c.startsWith("#qtrack:")) {
            QuestBook.Entry q = QuestsData.entry(c.substring(8));
            if (q != null) {
                menu.onClose();
                if (QuestBook.track(q)) {
                    Minecraft.getInstance().gui.setOverlayMessage(
                            L.c("wynnlabs.quests.now_tracking", QuestGuides.title(q.name())).withStyle(ChatFormatting.GREEN), false);
                }
            }
        } else if (c.startsWith("#qrescan:")) {
            // the player's press, the only thing that starts Wynntils' quest book refresh
            QuestBook.askWynntils();
        } else if (c.equals("#qclear")) {
            int n = Waypoints.clearAll();
            menu.flash(L.n("wynnlabs.quests.pins_cleared", n));
        } else if (c.startsWith("#qpins:")) {
            pin(c.substring(7), false);
        } else if (c.startsWith("#qpinstage:")) {
            pin(c.substring(11), true);
        } else if (c.startsWith("#qplan:")) {
            filterOpen = false;
            List<int[]> pts = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            int i = 1;
            for (String name : c.substring(7).split("\n")) {
                QuestBook.Entry q = QuestsData.entry(name);
                if (q != null && q.x() != null) {
                    pts.add(new int[] {q.x(), q.y(), q.z()});
                    labels.add((i++) + ") " + name);
                }
            }
            int n = Waypoints.all(pts, labels);
            menu.flash(L.n("wynnlabs.quests.plan_marked", n));
        } else {
            return false;
        }
        return true;
    }

    /**
     * The quest's places on the map and compass, queued: every one from the stage you are on, or
     * only that stage's ({@code stageOnly}); the start when the wiki has none.
     */
    private void pin(String name, boolean stageOnly) {
        QuestWiki.Info info = QuestWiki.get(name);
        int cur = Math.max(0, QuestsData.stage(name, info));
        List<int[]> pts = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (info != null) {
            List<QuestWiki.Place> all = QuestTrail.places(name, info, 0);
            List<String> nums = QuestsData.pointNumbers(all);
            for (int i = 0; i < all.size(); i++) {
                int st = all.get(i).stage();
                if (stageOnly ? st == cur : st >= cur) {
                    pts.add(all.get(i).at());
                    labels.add(nums.get(i) + " " + all.get(i).what());
                }
            }
        }
        QuestBook.Entry q = QuestsData.entry(name);
        if (pts.isEmpty() && !stageOnly && q != null && q.x() != null) {
            pts.add(new int[] {q.x(), q.y(), q.z()});
            labels.add(L.t("wynnlabs.quests.pin_start", QuestGuides.title(name)));
        }
        int n = Waypoints.showAll(pts, labels);
        menu.flash(n > 0 ? L.n("wynnlabs.quests.pins_added", n) : L.t("wynnlabs.quests.no_coords"));
    }

    // ---- the data the parts share ----

    /** Every filter's list, refreshed every two seconds. */
    Map<String, List<QuestAdvisor.Suggestion>> lists() {
        if (System.currentTimeMillis() - questListsAt > 2000) {
            Map<String, List<QuestAdvisor.Suggestion>> m = new HashMap<>();
            for (String f : QuestAdvisor.FILTERS) {
                try {
                    m.put(f, QuestAdvisor.list(f));
                } catch (Throwable t) {
                    m.put(f, List.of());
                }
            }
            questLists = m;
            questListsAt = System.currentTimeMillis();
        }
        return questLists;
    }

    /** A quest's share of a level: "seviyenin %11'i", or "1+ seviye" from a whole level on. */
    String share(long xp) {
        long lv;
        try {
            lv = Math.max(1, Wyn.xp().max());
        } catch (Throwable t) {
            lv = 1;
        }
        if (xp <= 0 || lv <= 1) {
            return "";                              // the level's XP unknown: no share rather than a wrong one
        }
        long pct = xp * 100 / lv;
        return pct >= 100 ? L.t("wynnlabs.quests.share_over", xp / lv) : L.t("wynnlabs.quests.share", Math.max(1, pct));
    }

    /** "Read quests with Wynntils" or, while running, "Reading quests…". */
    String rescanLabel() {
        return L.t(QuestBook.rescanning() ? "wynnlabs.quests.rescanning" : "wynnlabs.quests.rescan");
    }

    /** The rescan's tooltip: what it does, that it runs only on a press, or why it cannot run. */
    List<Component> rescanTip() {
        if (!Wyn.canRescanQuests()) {
            return List.of(L.c("wynnlabs.quests.rescan_off", Wyn.version()).withStyle(ChatFormatting.GOLD));
        }
        return List.of(L.c(QuestBook.rescanning() ? "wynnlabs.quests.rescanning" : "wynnlabs.quests.rescan").withStyle(ChatFormatting.GOLD),
                L.c("wynnlabs.quests.rescan_tip").withStyle(ChatFormatting.GRAY),
                L.c("wynnlabs.quests.rescan_only_press").withStyle(ChatFormatting.DARK_GRAY),
                Component.literal(L.n("wynnlabs.quests.known", QuestAdvisor.known())).withStyle(ChatFormatting.DARK_GRAY));
    }

    // ---- the title row ----

    /** On the title row's right: when the book was read, its refresh, and the search ("/"). */
    @Override
    int titleRight(GuiGraphics g, int right, int y, int room, int mx, int my) {
        searchRect = null;
        if (!Wyn.up(Wyn.Feature.QUESTS) || QuestAdvisor.known() == 0) {
            return 0;
        }
        int by = y - 3;
        int sq = Ui.CHIP_H + 2;
        int r = right;
        boolean compact = menu.layout.compact() || room < 260;
        if (compact) {
            Ui.iconButton(g, "search", r - sq, by, searchOpen, mx, my);
            menu.hits.add(new MenuHit(r - sq, by, sq, Ui.CHIP_H, null, null, "#qsearch"));
            r -= sq + Theme.SP_2;
        } else {
            int sw = Math.min(168, room / 2);
            searchField(g, r - sw, by, sw, mx, my);
            r -= sw + Theme.SP_3;
        }
        Ui.iconButton(g, "refresh", r - sq, by, false, mx, my);
        menu.hits.add(new MenuHit(r - sq, by, sq, Ui.CHIP_H, null, rescanTip(), "#qrescan:"));
        r -= sq + Theme.SP_2;
        long at = QuestBook.readAt();
        if (at > 0 && !compact) {
            String read = System.currentTimeMillis() - at < 60_000 ? L.t("wynnlabs.quests.read_now") : L.t("wynnlabs.quests.read_ago", ago(at));
            int rw = Type.META.width(font(), read);
            if (right - r + rw + Theme.SP_2 <= room) {
                Type.META.draw(g, font(), read, r - rw, Type.META.yIn(by, Ui.CHIP_H), Theme.INK_3);
                r -= rw + Theme.SP_2;
            }
        }
        return right - r;
    }

    /** The search box: a thin pill, the magnifier, the typed text (or the hint and "/"). */
    void searchField(GuiGraphics g, int x, int y, int w, int mx, int my) {
        boolean focused = searchBox.isFocused();
        boolean hover = mx >= x && mx < x + w && my >= y && my < y + Ui.CHIP_H;
        Kit.pill(g, x, y, w, Ui.CHIP_H, false, hover || focused);
        Art.icon(g, "search", x + Theme.SP_3, y + (Ui.CHIP_H - 8) / 2, 8, focused ? Theme.INK : Theme.INK_3);
        int tx = x + Theme.SP_3 + 8 + Theme.SP_2;
        int tw = x + w - Theme.SP_3 - tx;
        searchBox.setX(tx);
        searchBox.setY(Type.BODY.yIn(y, Ui.CHIP_H));
        searchBox.setWidth(tw);
        searchBox.render(g, mx, my, 0f);
        if (searchBox.getValue().isEmpty() && !focused) {
            Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.quests.search_hint"), tw - 10), tx, Type.META.yIn(y, Ui.CHIP_H),
                    Theme.INK_3);
            Type.META.drawRight(g, font(), "/", x + w - Theme.SP_3, Type.META.yIn(y, Ui.CHIP_H), Theme.LINE);
        }
        searchRect = new int[] {x, y, w, Ui.CHIP_H};
    }

    // ---- the page ----

    private void drawQuests(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        railRect = null;
        pointsRect = null;
        map.rect = null;
        if (!Wyn.up(Wyn.Feature.QUESTS)) {
            Type.BODY.draw(g, font(), Type.BODY.fit(font(), L.t("wynnlabs.wyn.quests_off", Wyn.version()), w), x, y, Theme.WARN);
            return;
        }
        if (QuestAdvisor.known() == 0) {
            empty(g, x, y, w, h, mx, my);
            return;
        }
        List<QuestsRail.Row> rows = rail.rows();
        if (questSelected == null || rows.stream().noneMatch(r -> r.name() != null && r.name().equals(questSelected))
                && QuestsData.entry(questSelected) == null) {
            questSelected = rows.stream().filter(r -> r.name() != null).map(QuestsRail.Row::name).findFirst().orElse(null);
        }
        boolean compact = menu.layout.compact();
        int px = x;
        int pw = w;
        int py = y;
        if (compact) {
            py += rail.header(g, x, y, w, rows, mx, my) + Theme.SP_4;
        } else {
            int railW = Math.max(170, Math.min(240, w * 28 / 100));
            rail.draw(g, x, y, railW, h, rows, mx, my);
            Kit.hair(g, x + railW + Theme.SP_6, y, 1, h, Theme.LINE);
            px = x + railW + 2 * Theme.SP_6;
            pw = w - railW - 2 * Theme.SP_6;
        }
        if (questSelected != null) {
            if (questDetailTab == 3) {
                points.draw(g, px, py, pw, y + h - py, mx, my);
            } else {
                guide.draw(g, px, py, pw, y + h - py, mx, my);
            }
        }
        // overlays last: the list over the pane (compact), the order menu
        if (compact && listOpen) {
            rail.overlay(g, x, py, w, y + h - py, rows, mx, my);
        }
        if (filterOpen) {
            rail.menu(g, mx, my);
        }
    }

    /** Nothing read yet: an info notice with "Read with Wynntils" in it, the manual way under it. */
    private void empty(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int nw = Math.min(460, w - 2 * Theme.SP_7);
        int nx = x + (w - nw) / 2;
        Ui.Action act = new Ui.Action("refresh", rescanLabel());
        List<String> lines = List.of(L.t("wynnlabs.quests.empty_title") + ". " + L.t("wynnlabs.quests.empty_sub"));
        int nh = Ui.noticeHeight(3);
        int ny = y + Math.max(0, (h - nh) / 2 - Theme.SP_7);
        int[][] hit = new int[1][];
        nh = Ui.notice(g, font(), Ui.Level.INFO, lines, act, nx, ny, nw, nh, mx, my, hit);
        if (hit[0] != null) {
            menu.hits.add(new MenuHit(hit[0][0], hit[0][1], hit[0][2], hit[0][3], null, rescanTip(), "#qrescan:"));
        }
        Type.META.draw(g, font(), Type.META.fit(font(), L.t("wynnlabs.quests.empty_manual"), nw), nx, ny + nh + Theme.SP_3, Theme.INK_3);
    }

    /** The rail: a pin while a quest is tracked. */
    @Override
    MenuRail.Badge badge() {
        String t = QuestsData.tracked();
        return t == null ? null : new MenuRail.Badge(MenuRail.Badge.Kind.PIN, "", L.t("wynnlabs.screen.rail_badge_tracked", t));
    }
}
