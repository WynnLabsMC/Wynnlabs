package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.CommunityBuilds;
import io.github.anilken.wynnlabs.build.CommunityList;
import io.github.anilken.wynnlabs.build.LinkImport;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.Tuning;
import io.github.anilken.wynnlabs.build.WynnBuilderLink;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Community tab's page: a filter rail (class, level, roles with counts, budget to finish,
 * owned share, outdated, look-alikes, crafted) beside the list: a search over build names, item
 * names and crafted ingredients with its count, three builds of your class picked as the closest
 * to finished, the filters in force as chips, and a table (build, its nine pieces owned or faded,
 * one of the build's own stats, the cost to finish, tags) sorted by any column; a build's stats
 * are its own, never set against what you wear. The build set as
 * your goal stays pinned on top; look-alikes fold into families. A row opens in place
 * ({@link CommunityRow}). Nothing here acts in the game: "Set as goal" only changes the plan.
 */
final class CommunityPage {

    static final String[] STATS = {"dps", "ehp", "mana", "speed"};
    static final int MENU_LEVEL = 1;
    static final int MENU_BUDGET = 2;
    static final int MENU_OWN = 3;
    static final int MENU_STAT = 4;
    /** A row's thin rule, a family's band, the goal row's face. */
    static final int RULE = 0x559A855C;
    static final int BAND = 0xFFDCCB9C;
    static final int GOAL_FACE = 0xFFF3E6BF;
    /** A small tag's face ("raid"). */
    static final int TAG_FACE = 0xFFDCC79C;

    private final CommunityTab tab;
    private final BuildControls ui;
    final CommunityRow opened;

    CommunityFilter filter = CommunityFilter.defaults();
    boolean fold;
    /** The sort: 0 name, 1 owned gear, 2 the stat column, 3 cost; and its direction. */
    int sortCol = 1;
    boolean sortDesc = true;
    /** The stat column: an index in {@link #STATS}. */
    int stat;
    String openId;
    /** The opened build shows its All stats section. */
    boolean allStats;
    /** The opened build is to be scrolled into sight on the next frame. */
    boolean reveal;
    final Set<String> openFamilies = new HashSet<>();
    int scroll;
    private int maxScroll;
    final ScrollBar bar = new ScrollBar();

    private CommunityBuilds.Owned owned = CommunityBuilds.Owned.NONE;
    private long ownedAt;
    private String cacheKey;
    private List<CommunityBuilds.Entry> matched = List.of();
    private Map<String, Integer> roleCounts = Map.of();
    private CommunityFilter.Suggestion suggestion;
    private String rowsKey;
    private List<Object> rows = List.of();

    CommunityTab tab() {
        return tab;
    }

    CommunityPage(CommunityTab tab, BuildControls ui) {
        this.tab = tab;
        this.ui = ui;
        this.opened = new CommunityRow(ui);
    }

    // =====================================================================================
    // sizes

    /** The strip's pictures: 16 px in the large menu, 12 in the small one. */
    int icon() {
        return tab.compact ? 10 : 16;
    }

    /** A table row: the pictures and 3 px above and below. */
    int rowH() {
        return icon() + 6;
    }

    /** Between the strip's pictures: a missing one's dashed square reaches 1 px out, so two stay 1 px apart. */
    static final int STRIP_GAP = 3;

    // =====================================================================================
    // data

    Map<String, CommunityBuilds.Compare> compared() {
        return CommunityList.compared();
    }

    static Klass klass() {
        GameState s = GameState.current();
        return s.inGame() ? s.classType() : Klass.NONE;
    }

    /** The owned snapshot at most once a second (it reads the bag, the gear and the bank), and the comparisons kept up. */
    private void refreshOwned() {
        long now = System.currentTimeMillis();
        if (now - ownedAt >= 1000 || owned == CommunityBuilds.Owned.NONE) {
            ownedAt = now;
            owned = CommunityBuilds.owned(GameState.current());
        }
        CommunityList.compareIfStale(GameState.current(), owned);
    }

    /** The filtered and sorted builds, made again only when something they rest on changed. */
    private void refresh() {
        CommunityBuilds.Doc d = CommunityList.doc();
        Map<String, CommunityBuilds.Compare> cmp = compared();
        Klass k = klass();
        int lv = GameState.current().level();
        String key = System.identityHashCode(d) + "|" + System.identityHashCode(cmp) + "|" + filter + "|" + sortCol + sortDesc + stat + "|" + k + lv;
        if (key.equals(cacheKey)) {
            return;
        }
        cacheKey = key;
        rowsKey = null;
        if (d == null) {
            matched = List.of();
            roleCounts = Map.of();
            suggestion = null;
            return;
        }
        List<CommunityBuilds.Entry> list = new ArrayList<>(filter.apply(d.builds(), cmp, k, lv));
        list.sort(order(cmp));
        matched = List.copyOf(list);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String role : CommunityBuilds.ROLES.keySet()) {
            counts.put(role, filter.withRole(role, true).count(d.builds(), cmp, k, lv));
        }
        roleCounts = counts;
        suggestion = matched.isEmpty() ? filter.suggest(d.builds(), cmp, k, lv) : null;
    }

    private Comparator<CommunityBuilds.Entry> order(Map<String, CommunityBuilds.Compare> cmp) {
        Comparator<CommunityBuilds.Entry> by = switch (sortCol) {
            case 0 -> Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT));
            case 1 -> Comparator.comparingInt(e -> {
                CommunityBuilds.Compare c = cmp.get(e.id());
                return c == null ? -1 : c.ownedPct();
            });
            case 2 -> Comparator.comparingDouble(e -> {
                Double v = statOf(cmp.get(e.id()), stat);
                return v == null ? (sortDesc ? -1e9 : 1e9) : v;
            });
            default -> Comparator.comparingLong(e -> {
                CommunityBuilds.Compare c = cmp.get(e.id());
                return c == null ? Long.MAX_VALUE / 2 : c.knownCost() + (c.unpriced() > 0 ? 1 : 0);
            });
        };
        if (sortDesc) {
            by = by.reversed();
        }
        return by.thenComparing(CommunityBuilds.Entry::levelMin).thenComparing(CommunityBuilds.Entry::id);
    }

    /** One of the build's own stats ({@link #STATS}); null when not known (no DPS without a weapon). */
    static Double statOf(CommunityBuilds.Compare c, int stat) {
        if (c == null || c.totals() == null) {
            return null;
        }
        CommunityBuilds.Totals t = c.totals();
        return switch (stat) {
            case 0 -> t.dps() > 0 ? t.dps() : null;
            case 1 -> t.ehp();
            case 2 -> t.manaRegen();
            default -> t.speed();
        };
    }

    /** A stat as the column shows it: "14.1k" for DPS and EHP, "+6/5s" for mana, "+32%" for speed; null when not known. */
    static String statText(Double v, int stat) {
        if (v == null) {
            return null;
        }
        return switch (stat) {
            case 0, 1 -> big(v);
            case 2 -> signed(v) + "/5s";
            default -> L.pct(signed(v));
        };
    }

    /** A stat as a header pill says it: "14.1k DPS", "29.1k EHP", "6 mana/5s", "+32% speed". */
    static String pillText(double v, int stat) {
        String n = switch (stat) {
            case 0, 1 -> big(v);
            case 2 -> String.valueOf(Math.round(v)).replace('-', '−');
            default -> L.pct(signed(v));
        };
        return L.t("wynnlabs.community.pill." + STATS[stat], n);
    }

    /** A whole number with its sign: "+6", "−4", "0". */
    static String signed(double v) {
        long r = Math.round(v);
        return r > 0 ? "+" + r : r < 0 ? "−" + -r : "0";
    }

    /** A large number short: "850", "14.1k", "1.2M". */
    static String big(double v) {
        double a = Math.abs(v);
        String sign = v < 0 ? "−" : "";
        if (a < 1000) {
            return sign + Math.round(a);
        }
        double d = a < 1e6 ? a / 1e3 : a / 1e6;
        String n = d >= 100 ? String.valueOf(Math.round(d)) : String.format(Locale.ROOT, "%.1f", d).replaceAll("\\.0$", "");
        return sign + n + (a < 1e6 ? "k" : "M");
    }

    /** "Lv 41-50", or "Lv 106" when the build is for one level. */
    static String levels(CommunityBuilds.Entry e) {
        return e.levelMin() == e.levelMax() ? "Lv " + e.levelMin() : L.t("wynnlabs.community.levels", e.levelMin(), e.levelMax());
    }

    /** The build set as the goal: the entry whose kept items are the items the road map builds around. */
    CommunityBuilds.Entry goal() {
        CommunityBuilds.Doc d = CommunityList.doc();
        List<String> locks = Locks.names();
        if (d == null || locks.isEmpty()) {
            return null;
        }
        Set<String> want = new HashSet<>(locks);
        Map<String, CommunityBuilds.Compare> cmp = compared();
        for (CommunityBuilds.Entry e : d.builds()) {
            CommunityBuilds.Compare c = cmp.get(e.id());
            if (c != null && !c.kept().isEmpty() && new HashSet<>(c.kept()).equals(want)) {
                return e;
            }
        }
        return null;
    }

    /** The source as shown: "Community" for the chat and list builds, "Forum guide", "Submitted". */
    static String source(CommunityBuilds.Entry e) {
        return L.t(switch (e.source()) {
            case "forum" -> "wynnlabs.community.source_forum";
            case "issue" -> "wynnlabs.community.source_issue";
            default -> "wynnlabs.community.source_community";
        });
    }

    static String price(long emeralds) {
        return Prices.slot(emeralds);
    }

    /** The cost to finish: "2.8LE" ("+" when a price is missing), "No price", "Owned". */
    static String cost(CommunityBuilds.Compare c) {
        if (c.missing() == 0) {
            return L.t("wynnlabs.community.owned_short");
        }
        return c.knownCost() > 0 ? price(c.knownCost()) + (c.unpriced() > 0 ? "+" : "") : L.t("wynnlabs.community.no_price_short");
    }

    /** "2 to buy · 2.8LE", or "All owned". */
    static String toBuy(CommunityBuilds.Compare c) {
        if (c.missing() == 0) {
            return L.t("wynnlabs.community.all_owned");
        }
        String cost = c.knownCost() > 0 ? price(c.knownCost()) + (c.unpriced() > 0 ? " +?" : "") : L.t("wynnlabs.community.no_price");
        return L.n("wynnlabs.community.to_buy", c.missing(), c.missing(), cost);
    }

    // =====================================================================================
    // drawing

    void draw(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        CommunityList.request();
        if (CommunityList.doc() == null) {
            drawState(g, x, y, w, h, mx, my);
            return;
        }
        refreshOwned();
        refresh();
        int railW = tab.compact ? 92 : Math.max(96, Math.min(140, w * 18 / 100));
        drawRail(g, host, x, y, railW, h, mx, my);
        int mx0 = x + railW + Theme.SP_4;
        Kit.hair(g, x + railW + Theme.SP_1, y, 1, h, Theme.LINE);
        drawMain(g, host, mx0, y, x + w - mx0, h, mx, my);
    }

    /** No list: loading, or it could not be loaded and when it is tried again. */
    private void drawState(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        if (CommunityList.state() == CommunityList.State.FAILED) {
            long min = Math.max(1, (CommunityList.retryIn() + 59_999) / 60_000);
            int[] act = UiEmpty.draw(g, ui.font, x, y, w, h, tab.compact, "world", L.t("wynnlabs.community.failed"),
                    List.of(L.n("wynnlabs.community.retry", (int) min, min)),
                    new UiEmpty.Action("refresh", L.t("wynnlabs.community.try_again"), "c.retry"), null, mx, my);
            if (act != null) {
                ui.btn(act[0], act[1], act[2], act[3], "c.retry", null);
            }
        } else {
            UiEmpty.draw(g, ui.font, x, y, w, h, tab.compact, "refresh", Ui.busy(L.t("wynnlabs.community.loading")),
                    List.of(), null, null, mx, my);
        }
    }

    // ---- the rail ----

    private int head(GuiGraphics g, String key, int x, int y) {
        Type.META.draw(g, ui.font, L.t(key), x, y, Theme.INK_3);
        return y + Type.META.lineHeight + Theme.SP_1;
    }

    /** A check row: an 8 px box, the label, a muted count; returns the y under it. */
    private int check(GuiGraphics g, String text, String count, boolean on, int x, int y, int w, String act, Object arg,
            List<Component> tip, BuildWindow.Host host, int mx, int my) {
        int rh = Type.BODY.lineHeight;
        boolean hov = BuildControls.in(mx, my, x, y, w, rh);
        int by = Type.BODY.onCaps(y, 8);
        g.fill(x, by, x + 8, by + 8, on ? Theme.WOOD : hov ? Theme.CARD_HOVER : Theme.CREAM);
        Kit.outline(g, x, by, 8, 8, on ? Theme.WOOD_LO : Theme.INK_2);
        if (on) {
            Art.icon(g, "check", x + 1, by + 1, 6, Theme.CREAM);
        }
        int tx = x + 8 + Theme.SP_2;
        int cw = count == null ? 0 : Type.META.width(ui.font, count) + Theme.SP_2;
        String t = Type.BODY.fit(ui.font, text, x + w - tx - cw);
        Type.BODY.draw(g, ui.font, t, tx, y, on ? Theme.INK : Theme.INK_2);
        if (count != null) {
            Type.META.draw(g, ui.font, count, tx + Type.BODY.width(ui.font, t) + Theme.SP_2, y + 1, Theme.INK_3);
        }
        ui.btn(x, y, w, rh, act, arg);
        if (tip != null) {
            host.tip(x, y, w, rh, tip);
        }
        return y + rh + (tab.compact ? 0 : Theme.SP_1);
    }

    /** A value that opens a small menu: its text and a chevron; returns the y under it. */
    private int value(GuiGraphics g, String text, boolean on, int x, int y, int w, int menuId, int mx, int my) {
        int rh = Type.BODY.lineHeight;
        String t = Type.BODY.fit(ui.font, text, w - 6 - Theme.SP_2);
        int tw = Type.BODY.width(ui.font, t);
        boolean hov = BuildControls.in(mx, my, x, y, tw + 6 + Theme.SP_2, rh) || tab.menuOpen == menuId;
        Type.BODY.draw(g, ui.font, t, x, y, hov ? Theme.LINK : on ? Theme.INK : Theme.INK_2);
        Art.icon(g, "chevron-down", x + tw + Theme.SP_1, Type.BODY.onCaps(y, 6), 6, hov ? Theme.LINK : Theme.INK_3);
        ui.btn(x, y, tw + 6 + Theme.SP_2, rh, "menu", menuId);
        if (tab.menuOpen == menuId) {
            tab.menuAt = new int[] {x, y, Math.max(tw + 8, 80), rh};
        }
        return y + rh + (tab.compact ? 0 : Theme.SP_1);
    }

    private void drawRail(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        int gap = tab.compact ? Theme.SP_1 : Theme.SP_3;
        int cy = y;
        if (!tab.compact) {
            Type.LABEL.draw(g, ui.font, L.t("wynnlabs.community.filters"), x, cy, Theme.INK);
            cy += Type.LABEL.lineHeight + gap;
        }
        Klass k = klass();
        String cls = k == Klass.NONE ? L.t("wynnlabs.community.your_class_none")
                : L.t("wynnlabs.community.your_class", k.name().charAt(0) + k.name().substring(1).toLowerCase(Locale.ROOT));
        cy = head(g, "wynnlabs.community.f_class", x, cy);
        cy = check(g, cls, null, !filter.allClasses(), x, cy, w, "c.classes", false, null, host, mx, my);
        cy = check(g, L.t("wynnlabs.community.all_classes"), null, filter.allClasses(), x, cy, w, "c.classes", true, null, host, mx, my) + gap;

        cy = head(g, "wynnlabs.community.f_level", x, cy);
        cy = value(g, levelText(filter.level()), filter.level() != 0, x, cy, w, MENU_LEVEL, mx, my) + gap;

        cy = head(g, "wynnlabs.community.f_role", x, cy);
        for (String role : CommunityBuilds.ROLES.keySet()) {
            cy = check(g, L.t("wynnlabs.community.role." + role), String.valueOf(roleCounts.getOrDefault(role, 0)),
                    filter.roles().contains(role), x, cy, w, "c.role", role, null, host, mx, my);
        }
        cy += gap;

        cy = head(g, "wynnlabs.community.f_budget", x, cy);
        cy = value(g, budgetText(filter.budget()), filter.budget() != 0, x, cy, w, MENU_BUDGET, mx, my) + gap;

        cy = head(g, "wynnlabs.community.f_own", x, cy);
        cy = value(g, ownText(filter.own()), filter.own() != 0, x, cy, w, MENU_OWN, mx, my) + gap;

        cy = head(g, "wynnlabs.community.f_other", x, cy);
        cy = check(g, L.t("wynnlabs.community.hide_outdated"), null, filter.hideOutdated(), x, cy, w, "c.outdated", null, null, host, mx, my);
        cy = check(g, L.t("wynnlabs.community.fold"), null, fold, x, cy, w, "c.fold", null,
                List.of(L.c("wynnlabs.community.fold_tip").withStyle(ChatFormatting.GRAY)), host, mx, my);
        check(g, L.t("wynnlabs.community.has_crafted"), null, filter.crafted(), x, cy, w, "c.crafted", null, null, host, mx, my);
    }

    static String levelText(int i) {
        if (i == 0) {
            int lv = GameState.current().level();
            return lv > 0 ? L.t("wynnlabs.community.level_yours", lv) : L.t("wynnlabs.community.level_any");
        }
        if (i == CommunityFilter.ANY_LEVEL) {
            return L.t("wynnlabs.community.level_any");
        }
        int[] r = CommunityFilter.LEVELS[i];
        return L.t("wynnlabs.community.level_range", r[0], r[1]);
    }

    static String budgetText(int i) {
        return i == 0 ? L.t("wynnlabs.community.budget_none") : L.t("wynnlabs.community.budget_at_most", Prices.fmt(Tuning.BUDGETS[i]));
    }

    static String ownText(int i) {
        return i == 0 ? L.t("wynnlabs.community.own_any") : L.pct(String.valueOf(CommunityFilter.OWN[i]));
    }

    // ---- the main column ----

    private void drawMain(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        CommunityBuilds.Doc d = CommunityList.doc();
        // the search and the count
        String count = L.t("wynnlabs.community.count", matched.size(), d.builds().size());
        int cw = Type.META.width(ui.font, count);
        int sw = w - cw - Theme.SP_3;
        tab.searchHit = UiInput.search(g, ui.font, tab.search, L.t("wynnlabs.community.search_hint"), true, x, y, sw, mx, my);
        Type.META.draw(g, ui.font, count, x + w - cw, Type.META.yIn(y, UiInput.H), Theme.INK_3);
        int cy = y + UiInput.H + Theme.SP_3;
        String q = tab.search.getValue();
        if (!q.equals(filter.query())) {
            filter = filter.withQuery(q);
            scroll = 0;
            refresh();
        }

        // picked for you: the builds of your class closest to finished
        List<CommunityBuilds.Entry> picks = picks();
        if (!picks.isEmpty()) {
            Type.META.draw(g, ui.font, L.t("wynnlabs.community.picked"), x, cy, Theme.INK_3);
            cy += Type.META.lineHeight + Theme.SP_2;
            int cardW = (w - 2 * Theme.SP_3) / 3;
            int cardH = Type.LABEL.lineHeight + Type.META.lineHeight + icon() + 2 * Theme.SP_2 + Theme.SP_3;
            for (int i = 0; i < picks.size(); i++) {
                drawPick(g, host, picks.get(i), x + i * (cardW + Theme.SP_3), cy, cardW, cardH, mx, my);
            }
            cy += cardH + 2 + Theme.SP_3;
        }

        // the filters in force: a chip each, its ✕ clearing it alone
        List<String[]> chips = activeChips();
        if (!chips.isEmpty()) {
            int cx = x;
            int chH = Type.META.lineHeight + 4;
            for (String[] c : chips) {
                int chw = Type.META.width(ui.font, c[1]) + 2 * Theme.SP_3 + Theme.SP_2 + 6;
                if (cx + chw > x + w) {
                    break;
                }
                boolean hov = BuildControls.in(mx, my, cx, cy, chw, chH);
                Kit.pill(g, cx, cy, chw, chH, false, hov);
                Type.META.draw(g, ui.font, c[1], cx + Theme.SP_3, Type.META.yIn(cy, chH), Theme.INK);
                Art.icon(g, "x", cx + chw - Theme.SP_3 - 6, cy + (chH - 6) / 2, 6, hov ? Theme.BAD : Theme.INK_3);
                ui.btn(cx, cy, chw, chH, "c.unchip", c[0]);
                cx += chw + Theme.SP_2;
            }
            cy += chH + Theme.SP_3;
        }

        // the table's header, then its rows in their own scrolled clip
        int[] cols = columns(w);
        drawHeader(g, host, x, cy, cols, mx, my);
        cy += Type.META.lineHeight + Theme.SP_2;
        Kit.hair(g, x, cy - 1, w, 1, Theme.LINE);
        int listH = y + h - cy;
        CommunityBuilds.Entry goal = goal();
        if (matched.isEmpty() && goal == null) {
            drawNone(g, x, cy, w, listH, mx, my);
            return;
        }
        int rw = maxScroll > 0 ? w - ScrollBar.W - Theme.SP_2 : w;
        int[] rc = rw == w ? cols : columns(rw);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        int top = cy;
        int bottom = cy + listH;
        boolean in = BuildControls.in(mx, my, x, top, w, listH);
        int rmx = in ? mx : -1;
        int rmy = in ? my : -1;
        BuildWindow.Host clip = clipped(host, top, bottom);
        int firstBtn = ui.btns.size();
        g.enableScissor(x, top, x + w, bottom);
        int ry = top - scroll;
        List<Runnable> over = new ArrayList<>();
        if (goal != null) {
            ry = drawEntry(g, clip, goal, x, ry, rw, rc, true, top, bottom, over, rmx, rmy);
        }
        for (Object row : rows()) {
            if (row instanceof Family f) {
                ry = drawFamily(g, clip, f, x, ry, rw, rc, top, bottom, rmx, rmy);
                if (openFamilies.contains(f.key())) {
                    int ind = Theme.SP_5;
                    for (CommunityBuilds.Entry e : f.members()) {
                        ry = drawEntry(g, clip, e, x + ind, ry, rw - ind, columns(rw - ind), false, top, bottom, over, rmx, rmy);
                    }
                }
            } else {
                CommunityBuilds.Entry e = (CommunityBuilds.Entry) row;
                if (goal != null && e.id().equals(goal.id())) {
                    continue;
                }
                ry = drawEntry(g, clip, e, x, ry, rw, rc, false, top, bottom, over, rmx, rmy);
            }
        }
        if (!over.isEmpty()) {
            g.nextStratum();                            // the fades, badges and prices over the item pictures
            over.forEach(Runnable::run);
        }
        g.disableScissor();
        // a row cut by the list's edges takes no click there (the table's header lies above it)
        for (int i = ui.btns.size() - 1; i >= firstBtn; i--) {
            BuildControls.Btn b = ui.btns.get(i);
            if (b.y() < top || b.y() + b.h() > bottom) {
                ui.btns.remove(i);
            }
        }
        int total = ry + scroll - top;
        maxScroll = Math.max(0, total - listH);
        bar.place(x + w - ScrollBar.W, top, listH, listH, total, scroll);
        bar.draw(g, mx, my);
    }

    /** {@code host} whose hovers count only between {@code top} and {@code bottom} (the list's clip). */
    private static BuildWindow.Host clipped(BuildWindow.Host host, int top, int bottom) {
        return new BuildWindow.Host() {
            @Override
            public void tip(int x, int y, int w, int h, List<Component> tip) {
                if (y >= top && y + h <= bottom) {
                    host.tip(x, y, w, h, tip);
                }
            }

            @Override
            public void item(int x, int y, int w, int h, ItemData info, ItemStack stack, List<Component> extra) {
                if (y >= top && y + h <= bottom) {
                    host.item(x, y, w, h, info, stack, extra);
                }
            }

            @Override
            public ItemStack icon(ItemData info) {
                return host.icon(info);
            }

            @Override
            public void flash(String text) {
                host.flash(text);
            }

            @Override
            public void applied() {
                host.applied();
            }

            @Override
            public void detailed(io.github.anilken.wynnlabs.build.roadmap.Roadmap.Candidate c, int level, int rank) {
                host.detailed(c, level, rank);
            }
        };
    }

    /**
     * The table's columns as x offsets {name, gear, stat, cost, tags, end}: the name the widest,
     * the strip of nine pictures, the stat, the cost, the tags (none in the small menu).
     */
    int[] columns(int w) {
        int gear = 9 * icon() + 8 * STRIP_GAP;
        int stat = tab.compact ? 32 : 46;
        int cost = tab.compact ? 40 : 52;
        int tags = tab.compact ? 0 : Math.max(0, Math.min(90, w / 7));
        int gap = Theme.SP_3;
        int name = w - gear - stat - cost - tags - (tags > 0 ? 4 : 3) * gap;
        int[] c = new int[6];
        c[1] = name + gap;
        c[2] = c[1] + gear + gap;
        c[3] = c[2] + stat + gap;
        c[4] = c[3] + cost + gap;
        c[5] = w;
        return c;
    }

    private void drawHeader(GuiGraphics g, BuildWindow.Host host, int x, int y, int[] c, int mx, int my) {
        String[] names = {L.t("wynnlabs.community.col_build"), L.t("wynnlabs.community.col_gear"),
            L.t("wynnlabs.community.stat." + STATS[stat]), L.t("wynnlabs.community.col_cost")};
        for (int i = 0; i < 4; i++) {
            int cx = x + c[i];
            int cw = c[i + 1] - c[i] - Theme.SP_3;
            boolean on = sortCol == i;
            String t = Type.META.fit(ui.font, names[i] + (on ? sortDesc ? " ▾" : " ▴" : ""), cw);
            int tw = Type.META.width(ui.font, t);
            boolean hov = BuildControls.in(mx, my, cx, y, tw, Type.META.lineHeight);
            Type.META.draw(g, ui.font, t, cx, y, on || hov ? Theme.INK : Theme.INK_3);
            ui.btn(cx, y, tw, Type.META.lineHeight, "c.sort", i);
            if (i == 2) {
                // the stat column's switch: DPS, EHP, Mana or Speed
                int ax = cx + tw + Theme.SP_1;
                boolean ah = BuildControls.in(mx, my, ax - 1, y - 1, 9, Type.META.lineHeight + 1) || tab.menuOpen == MENU_STAT;
                Art.icon(g, "chevron-down", ax, y + 1, 6, ah ? Theme.LINK : Theme.INK_3);
                ui.btn(ax - 1, y - 1, 9, Type.META.lineHeight + 1, "menu", MENU_STAT);
                if (tab.menuOpen == MENU_STAT) {
                    tab.menuAt = new int[] {cx, y, 80, Type.META.lineHeight};
                }
                host.tip(ax - 1, y - 1, 9, Type.META.lineHeight + 1, List.of(L.c("wynnlabs.community.stat_tip").withStyle(ChatFormatting.GRAY)));
            }
        }
        if (c[4] < c[5]) {
            Type.META.draw(g, ui.font, L.t("wynnlabs.community.col_tags"), x + c[4], y, Theme.INK_3);
        }
    }

    /** The strip of a build's nine pictures at x, y: owned ones whole, missing ones faded in a dashed square. */
    private void strip(GuiGraphics g, BuildWindow.Host host, CommunityBuilds.Compare cmp, int x, int y, List<Runnable> over) {
        int ic = icon();
        CommunityBuilds.Piece[] bySlot = CommunityRow.bySlot(cmp);
        for (int i = 0; i < 9; i++) {
            CommunityBuilds.Piece p = bySlot[i];
            int px = x + i * (ic + STRIP_GAP);
            if (p == null) {
                continue;
            }
            ItemStack st = CommunityRow.icon(host, p);
            if (!st.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(px, y);
                g.pose().scale(ic / 16f, ic / 16f);
                g.renderItem(st, 0, 0);
                g.pose().popMatrix();
            }
            if (!p.owned()) {
                over.add(() -> {
                    g.fill(px, y, px + ic, y + ic, 0xA0E7D3A8);
                    CommunityRow.dashed(g, px - 1, y - 1, ic + 2, ic + 2, 0xFFA0522D);
                });
            }
            if (p.info() != null) {
                host.item(px, y, ic, ic, p.info(), st, CommunityRow.pieceTip(p).subList(1, CommunityRow.pieceTip(p).size()));
            } else {
                host.tip(px, y, ic, ic, CommunityRow.pieceTip(p));
            }
        }
    }

    /** A small flat tag ("raid"). Returns its width. */
    int tag(GuiGraphics g, String text, int x, int y, int ink) {
        int w = Type.META.width(ui.font, text) + 2 * Theme.SP_2;
        int h = Type.META.lineHeight + 2;
        Kit.box(g, x, y, w, h, TAG_FACE, 0);
        Type.META.draw(g, ui.font, text, x + Theme.SP_2, Type.META.yIn(y, h), ink);
        return w;
    }

    /** One build's row (and, when it is the open one, its panel under it); returns the y after it. */
    private int drawEntry(GuiGraphics g, BuildWindow.Host host, CommunityBuilds.Entry e, int x, int y, int w, int[] c, boolean pinned,
            int top, int bottom, List<Runnable> over, int mx, int my) {
        int rh = rowH();
        boolean open = e.id().equals(openId);
        CommunityBuilds.Compare cmp = compared().get(e.id());
        if (y + rh >= top && y <= bottom) {
            boolean hov = BuildControls.in(mx, my, x, y, w, rh);
            if (pinned) {
                g.fill(x, y, x + w, y + rh, GOAL_FACE);
                Kit.outline(g, x, y, w, rh, Theme.WOOD_LO);
            } else if (open) {
                g.fill(x, y, x + w, y + rh, Theme.SELECTED);
            } else if (hov) {
                Kit.rowLit(g, x, y, w, rh);
            }
            if (!pinned) {
                Kit.hair(g, x, y + rh - 1, w, 1, RULE);
            }
            ui.btn(x, y, w, rh, "c.open", e.id());
            int ty = Type.BODY.yIn(y, rh);
            int nx = x + Theme.SP_1;
            // the chevron: right while closed, down while the row is open
            Type nameRole = pinned || !tab.compact ? Type.BODY : Type.META;
            Art.icon(g, open ? "chevron-down" : "chevron-right", nx, nameRole.onCaps(nameRole.yIn(y, rh), 6), 6, Theme.INK_3);
            nx += 6 + Theme.SP_1;
            if (pinned) {
                Art.icon(g, "target", nx, Type.BODY.onCaps(ty, 8), 8, Theme.ACCENT);
                nx += 8 + Theme.SP_1;
            }
            int nameW = x + c[1] - Theme.SP_3 - nx;
            String name = e.name();
            if (pinned) {
                // the target says it is your goal (and the row's tip): the name keeps the room
                Type.BODY.draw(g, ui.font, Type.BODY.fit(ui.font, name, nameW), nx, ty, Theme.INK);
            } else {
                Type role = tab.compact ? Type.META : Type.BODY;
                // the level range after the name, muted; the name gives way first only when both cannot fit
                String lvl = "  " + levels(e);
                int lw = Type.META.width(ui.font, lvl);
                if (tab.compact && role.width(ui.font, name) + lw > nameW) {
                    lvl = "";                   // the small menu keeps the name; the level is in its tip and panel
                    lw = 0;
                }
                String n = role.fit(ui.font, name, Math.max(nameW / 2, nameW - lw));
                role.draw(g, ui.font, n, nx, role.yIn(y, rh), Theme.INK);
                int after = nx + role.width(ui.font, n);
                Type.META.draw(g, ui.font, Type.META.fit(ui.font, lvl, nx + nameW - after), after, Type.META.yIn(y, rh), Theme.INK_3);
            }
            host.tip(x, y, c[1] - Theme.SP_3, rh, rowTip(e, pinned));
            int iy = y + (rh - icon()) / 2;
            if (cmp != null) {
                strip(g, host, cmp, x + c[1], iy, over);
            } else {
                Type.META.draw(g, ui.font, "…", x + c[1], Type.META.yIn(y, rh), Theme.INK_3);
            }
            String st = statText(statOf(cmp, stat), stat);
            Type.BODY.draw(g, ui.font, st == null ? "—" : st, x + c[2], ty, st == null ? Theme.INK_3 : Theme.INK_2);
            if (cmp != null) {
                boolean priced = cmp.knownCost() > 0 || cmp.missing() == 0;
                // the goal's "N to buy · cost" takes the cost and tags columns
                String cost = pinned ? toBuy(cmp) : cost(cmp);
                int room = (pinned ? c[5] : c[4]) - c[3] - Theme.SP_1;
                Type role = priced || pinned ? Type.BODY : Type.META;
                role.draw(g, ui.font, role.fit(ui.font, cost, room), x + c[3], role.yIn(y, rh),
                        cmp.missing() == 0 ? Theme.GOOD : priced ? Theme.PRICE : Theme.INK_3);
                host.tip(x + c[3], y, c[4] - c[3], rh, List.of(Component.literal(toBuy(cmp)).withStyle(ChatFormatting.GOLD)));
            }
            if (!pinned && c[4] < c[5]) {
                int tx = x + c[4];
                int sy = y + (rh - Type.META.lineHeight - 2) / 2;
                List<String> ts = new ArrayList<>();
                if (e.outdated()) {
                    ts.add("outdated");
                }
                ts.addAll(e.tags());
                for (String t : ts) {
                    String label = L.t(t.equals("outdated") ? "wynnlabs.community.outdated" : "wynnlabs.community.tag." + t);
                    int lw = Type.META.width(ui.font, label) + 2 * Theme.SP_2;
                    if (tx + lw > x + c[5]) {
                        break;
                    }
                    tag(g, label, tx, sy, t.equals("outdated") ? Theme.WARN : Theme.INK_2);
                    if (t.equals("outdated")) {
                        host.tip(tx, sy, lw, Type.META.lineHeight + 2,
                                List.of(L.c("wynnlabs.community.outdated_tip", e.checked()).withStyle(ChatFormatting.GRAY)));
                    }
                    tx += lw + Theme.SP_1;
                }
            }
        }
        y += rh;
        if (open && cmp != null) {
            boolean tags = c[4] >= c[5];
            int hgt = opened.height(e, cmp, w, tags, tab.compact, allStats);
            int py = y + Theme.SP_1;
            if (py + hgt >= top && py <= bottom) {
                opened.draw(g, host, this, e, cmp, x, py, w, tags, tab.compact, over, mx, my);
            }
            if (reveal) {
                // the opened build whole in sight: its row at the top at most, its last line at the bottom;
                // its All stats just opened, their last line at the bottom even when the row then leaves the top
                int need = py + hgt + Theme.SP_2 - bottom;
                int room = y - rh - top;
                if (need > 0) {
                    scroll += allStats ? need : Math.min(need, Math.max(0, room));
                }
                reveal = false;
            }
            y = py + hgt + Theme.SP_3;
        }
        return y;
    }

    private List<Component> rowTip(CommunityBuilds.Entry e, boolean pinned) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(e.name()).withStyle(ChatFormatting.WHITE));
        if (pinned) {
            tip.add(L.c("wynnlabs.community.your_goal").withStyle(ChatFormatting.GOLD));
        }
        String klass = e.any() ? L.t("wynnlabs.community.any_class") : e.klass().substring(0, 1).toUpperCase(Locale.ROOT) + e.klass().substring(1);
        tip.add(Component.literal(klass + " · " + L.t("wynnlabs.community.levels", e.levelMin(), e.levelMax()) + " · " + source(e))
                .withStyle(ChatFormatting.GRAY));
        if (!e.description().isBlank()) {
            tip.add(Component.literal(e.description()).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (e.outdated()) {
            tip.add(L.c("wynnlabs.community.outdated_tip", e.checked()).withStyle(ChatFormatting.GOLD));
        }
        if (pinned) {
            tip.add(L.c("wynnlabs.community.goal_tip").withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        if (e.credit() != null) {
            tip.add(L.c("wynnlabs.community.credit", e.credit()).withStyle(ChatFormatting.DARK_GRAY));
        }
        return tip;
    }

    // ---- look-alikes ----

    /** Near-identical builds folded: one class and weapon, at least six of the nine pieces the same. */
    record Family(String key, String name, List<CommunityBuilds.Entry> members) {}

    /** The rows: builds, or (with look-alikes folded) families of two or more and single builds. */
    List<Object> rows() {
        String key = cacheKey + "|" + fold;
        if (!key.equals(rowsKey)) {
            rows = foldRows();
            rowsKey = key;
        }
        return rows;
    }

    private List<Object> foldRows() {
        List<Object> out = new ArrayList<>();
        if (!fold) {
            out.addAll(matched);
            return out;
        }
        Map<String, CommunityBuilds.Compare> cmp = compared();
        List<List<CommunityBuilds.Entry>> fams = new ArrayList<>();
        List<Set<String>> sets = new ArrayList<>();
        List<String> heads = new ArrayList<>();
        for (CommunityBuilds.Entry e : matched) {
            CommunityBuilds.Compare c = cmp.get(e.id());
            Set<String> names = new HashSet<>();
            String weapon = "";
            if (c != null) {
                for (CommunityBuilds.Piece p : c.pieces()) {
                    names.add(p.slot() + ":" + p.name());
                    if (p.slot() == WynnBuilderLink.WEAPON) {
                        weapon = p.name();
                    }
                }
            }
            String head = e.klass() + "|" + weapon;
            int at = -1;
            for (int i = 0; i < fams.size() && c != null; i++) {
                if (heads.get(i).equals(head)) {
                    Set<String> both = new HashSet<>(sets.get(i));
                    both.retainAll(names);
                    if (both.size() >= 6) {
                        at = i;
                        break;
                    }
                }
            }
            if (at < 0) {
                fams.add(new ArrayList<>(List.of(e)));
                sets.add(names);
                heads.add(head);
            } else {
                fams.get(at).add(e);
            }
        }
        for (List<CommunityBuilds.Entry> f : fams) {
            if (f.size() == 1) {
                out.add(f.get(0));
            } else {
                out.add(new Family(f.get(0).id(), f.get(0).name(), List.copyOf(f)));
            }
        }
        return out;
    }

    private int drawFamily(GuiGraphics g, BuildWindow.Host host, Family f, int x, int y, int w, int[] c, int top, int bottom,
            int mx, int my) {
        int rh = rowH();
        if (y + rh >= top && y <= bottom) {
            boolean open = openFamilies.contains(f.key());
            g.fill(x, y, x + w, y + rh, BAND);
            if (BuildControls.in(mx, my, x, y, w, rh)) {
                Kit.rowLit(g, x, y, w, rh);
            }
            Kit.hair(g, x, y + rh - 1, w, 1, RULE);
            ui.btn(x, y, w, rh, "c.family", f.key());
            int ty = Type.BODY.yIn(y, rh);
            Art.icon(g, open ? "chevron-down" : "chevron-right", x + Theme.SP_1, Type.LABEL.onCaps(ty, 6), 6, Theme.INK_3);
            int nx = x + Theme.SP_1 + 6 + Theme.SP_1;
            String count = " · " + f.members().size();
            int cw = Type.META.width(ui.font, count);
            String name = Type.LABEL.fit(ui.font, L.t("wynnlabs.community.family", f.name()), c[1] - Theme.SP_3 - (nx - x) - cw);
            Type.LABEL.draw(g, ui.font, name, nx, ty, Theme.INK);
            Type.META.draw(g, ui.font, count, nx + Type.LABEL.width(ui.font, name), ty + 1, Theme.INK_3);
            // the ranges: the best owned share, the stat's span, the cost's span
            Map<String, CommunityBuilds.Compare> cmp = compared();
            int bestOwned = 0;
            int total = 9;
            double lo = Double.MAX_VALUE;
            double hi = -Double.MAX_VALUE;
            long clo = Long.MAX_VALUE;
            long chi = 0;
            for (CommunityBuilds.Entry e : f.members()) {
                CommunityBuilds.Compare cc = cmp.get(e.id());
                if (cc == null) {
                    continue;
                }
                if (cc.owned() > bestOwned) {
                    bestOwned = cc.owned();
                    total = cc.total();
                }
                Double v = statOf(cc, stat);
                if (v != null) {
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
                if (cc.knownCost() > 0) {
                    clo = Math.min(clo, cc.knownCost());
                    chi = Math.max(chi, cc.knownCost());
                }
            }
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, L.t("wynnlabs.community.best_owned", bestOwned, total), c[2] - c[1] - Theme.SP_3),
                    x + c[1], Type.META.yIn(y, rh), Theme.INK_3);
            String span = lo == Double.MAX_VALUE ? "—" : lo == hi ? statText(lo, stat) : statText(lo, stat) + "…" + statText(hi, stat);
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, span, c[3] - c[2] - Theme.SP_1), x + c[2], Type.META.yIn(y, rh),
                    lo == Double.MAX_VALUE ? Theme.INK_3 : Theme.INK_2);
            String cs = clo == Long.MAX_VALUE ? L.t("wynnlabs.community.no_price_short") : clo == chi ? price(clo) : price(clo) + "–" + price(chi);
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, cs, c[5] - c[3]), x + c[3], Type.META.yIn(y, rh),
                    clo == Long.MAX_VALUE ? Theme.INK_3 : Theme.PRICE);
        }
        return y + rh;
    }

    // ---- picked for you ----

    /**
     * Three builds of your class from the list as filtered, the closest to finished: the most
     * pieces owned first, then the cheapest to finish, then the build's own DPS. A build you
     * already own whole is left out; none when no build qualifies.
     */
    List<CommunityBuilds.Entry> picks() {
        Map<String, CommunityBuilds.Compare> cmp = compared();
        Klass k = klass();
        List<CommunityBuilds.Entry> mine = new ArrayList<>();
        for (CommunityBuilds.Entry e : matched) {
            CommunityBuilds.Compare c = cmp.get(e.id());
            if (c != null && !e.any() && (k == Klass.NONE || e.klassType() == k) && c.missing() > 0) {
                mine.add(e);
            }
        }
        mine.sort(Comparator.<CommunityBuilds.Entry>comparingInt(e -> cmp.get(e.id()).owned()).reversed()
                .thenComparingInt(e -> cmp.get(e.id()).unpriced())
                .thenComparingLong(e -> cmp.get(e.id()).knownCost())
                .thenComparing(Comparator.<CommunityBuilds.Entry>comparingDouble(e -> {
                    Double d = statOf(cmp.get(e.id()), 0);
                    return d == null ? 0 : d;
                }).reversed()));
        return mine.subList(0, Math.min(3, mine.size()));
    }

    /** A pick: a light flat card: the name, how many of its pieces you own, then "Buy", the missing pieces and the price. */
    private void drawPick(GuiGraphics g, BuildWindow.Host host, CommunityBuilds.Entry e, int x, int y, int w, int h, int mx, int my) {
        CommunityBuilds.Compare c = compared().get(e.id());
        Kit.softCard(g, x, y, w, h);
        if (BuildControls.in(mx, my, x, y, w, h)) {
            Kit.outline(g, x + 1, y + 1, w - 2, h - 1, Theme.GOLD_DEEP);
        }
        int pad = Theme.SP_3;
        int ix = x + pad;
        int cy = y + Theme.SP_2;
        Type.LABEL.draw(g, ui.font, Type.LABEL.fit(ui.font, e.name(), w - 2 * pad), ix, cy, Theme.INK);
        cy += Type.LABEL.lineHeight;
        Type.META.draw(g, ui.font, Type.META.fit(ui.font, L.t("wynnlabs.community.owned_count", c.owned(), c.total()), w - 2 * pad),
                ix, cy, Theme.INK_2);
        cy += Type.META.lineHeight + Theme.SP_1;
        int ic = icon();
        String buy = L.t("wynnlabs.community.buy");
        Type.META.draw(g, ui.font, buy, ix, Type.META.yIn(cy, ic), Theme.INK_3);
        int px = ix + Type.META.width(ui.font, buy) + Theme.SP_2;
        boolean priced = c.knownCost() > 0;
        String cost = priced ? price(c.knownCost()) + (c.unpriced() > 0 ? "+" : "") : L.t("wynnlabs.community.no_price_short");
        int costW = Type.META.width(ui.font, cost);
        for (CommunityBuilds.Piece p : c.pieces()) {
            if (p.owned()) {
                continue;
            }
            if (px + ic > x + w - pad - costW - Theme.SP_2) {
                break;
            }
            ItemStack st = CommunityRow.icon(host, p);
            if (!st.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(px, cy);
                g.pose().scale(ic / 16f, ic / 16f);
                g.renderItem(st, 0, 0);
                g.pose().popMatrix();
            }
            host.tip(px, cy, ic, ic, CommunityRow.pieceTip(p));
            px += ic + Theme.SP_1;
        }
        Type.META.draw(g, ui.font, cost, x + w - pad - costW, Type.META.yIn(cy, ic), priced ? Theme.PRICE : Theme.INK_3);
        ui.btn(x, y, w, h, "c.pick", e.id());
    }

    // ---- the filter chips and the empty list ----

    /** {id, label} per filter in force, its ✕ clearing it alone. */
    List<String[]> activeChips() {
        List<String[]> out = new ArrayList<>();
        if (!filter.words().isEmpty()) {
            String q = filter.query().strip();
            String shown = q.regionMatches(true, 0, "uses", 0, 4) ? q.substring(4).replaceFirst("^:?\\s*", "") : q;
            out.add(new String[] {"search", L.t("wynnlabs.community.chip_uses", shown)});
        }
        if (filter.allClasses()) {
            out.add(new String[] {"classes", L.t("wynnlabs.community.all_classes")});
        }
        if (filter.level() != 0) {
            out.add(new String[] {"level", levelText(filter.level())});
        }
        for (String r : filter.roles()) {
            out.add(new String[] {"role:" + r, L.t("wynnlabs.community.role." + r)});
        }
        if (filter.budget() != 0) {
            out.add(new String[] {"budget", "≤ " + Prices.fmt(Tuning.BUDGETS[filter.budget()])});
        }
        if (filter.own() != 0) {
            out.add(new String[] {"own", L.t("wynnlabs.community.chip_own", ownText(filter.own()))});
        }
        if (filter.hideOutdated()) {
            out.add(new String[] {"outdated", L.t("wynnlabs.community.hide_outdated")});
        }
        if (filter.crafted()) {
            out.add(new String[] {"crafted", L.t("wynnlabs.community.has_crafted")});
        }
        return out;
    }

    private void drawNone(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        int[] box = UiEmpty.draw(g, ui.font, x, y, w, h, true, "search", L.t("wynnlabs.community.none"), List.of(),
                new UiEmpty.Action("eraser", L.t("wynnlabs.community.clear_filters"), "c.clear"), null, mx, my);
        if (box != null) {
            ui.btn(box[0], box[1], box[2], box[3], "c.clear", null);
        }
        String sug = suggestionText(suggestion);
        if (sug != null && box != null) {
            int sy = box[1] + box[3] + Theme.SP_4;
            String t = Type.BODY.fit(ui.font, sug, w);
            int sw = Type.BODY.width(ui.font, t);
            int sx = x + (w - sw) / 2;
            boolean hov = BuildControls.in(mx, my, sx, sy, sw, Type.BODY.lineHeight);
            Type.BODY.draw(g, ui.font, t, sx, sy, hov ? Theme.ACCENT : Theme.LINK);
            ui.btn(sx, sy, sw, Type.BODY.lineHeight, "c.suggest", null);
        }
    }

    static String suggestionText(CommunityFilter.Suggestion s) {
        if (s == null) {
            return null;
        }
        return switch (s.kind()) {
            case "budget" -> L.n("wynnlabs.community.try_budget", s.count(), Prices.fmt(Tuning.BUDGETS[Integer.parseInt(s.arg())]), s.count());
            case "role" -> L.n("wynnlabs.community.try_role", s.count(), L.t("wynnlabs.community.role." + s.arg()), s.count());
            default -> L.n("wynnlabs.community.try." + s.kind(), s.count(), s.count());
        };
    }

    // =====================================================================================
    // clicks

    void click(String act, Object arg, long now) {
        switch (act) {
            case "c.classes" -> set(filter.withClasses((boolean) arg));
            case "c.role" -> {
                String r = (String) arg;
                set(filter.withRole(r, !filter.roles().contains(r)));
            }
            case "c.outdated" -> set(filter.withOutdatedHidden(!filter.hideOutdated()));
            case "c.crafted" -> set(filter.withCrafted(!filter.crafted()));
            case "c.fold" -> {
                fold = !fold;
                scroll = 0;
            }
            case "c.sort" -> {
                int col = (int) arg;
                if (sortCol == col) {
                    sortDesc = !sortDesc;
                } else {
                    sortCol = col;
                    sortDesc = col != 0 && col != 3;
                }
            }
            case "c.stats" -> {
                allStats = !allStats;
                reveal = allStats;
            }
            case "c.open" -> {
                String id = (String) arg;
                openId = id.equals(openId) ? null : id;
                allStats = false;
                if (openId != null) {
                    requestPrices(openId);
                    reveal = true;
                }
            }
            case "c.pick" -> {
                openId = (String) arg;
                allStats = false;
                requestPrices(openId);
                scrollTo(openId);
                reveal = true;
            }
            case "c.family" -> {
                String k = (String) arg;
                if (!openFamilies.remove(k)) {
                    openFamilies.add(k);
                }
            }
            case "c.unchip" -> unchip((String) arg);
            case "c.clear" -> {
                tab.search.setValue("");
                set(CommunityFilter.defaults());
            }
            case "c.suggest" -> {
                if (suggestion != null) {
                    if (suggestion.kind().equals("search")) {
                        tab.search.setValue("");
                    }
                    set(suggestion.to());
                }
            }
            case "c.goal" -> setGoal((String) arg, now);
            case "c.copy" -> copy((String) arg);
            case "c.retry" -> CommunityList.retryNow();
            case "c.craft" -> {
                if (arg instanceof io.github.anilken.wynnlabs.crafting.RecipeCode.Recipe r) {
                    tab.craft(r);
                }
            }
            default -> { }
        }
    }

    private void set(CommunityFilter f) {
        filter = f;
        scroll = 0;
        cacheKey = null;
    }

    private void unchip(String id) {
        if (id.startsWith("role:")) {
            set(filter.withRole(id.substring(5), false));
            return;
        }
        switch (id) {
            case "search" -> tab.search.setValue("");
            case "classes" -> set(filter.withClasses(false));
            case "level" -> set(filter.withLevel(0));
            case "budget" -> set(filter.withBudget(0));
            case "own" -> set(filter.withOwn(0));
            case "outdated" -> set(filter.withOutdatedHidden(false));
            case "crafted" -> set(filter.withCrafted(false));
            default -> { }
        }
    }

    /** A menu's rows: the level ranges, the budgets, the owned shares, the stats. */
    List<UiMenu.Item> menuItems(int menuId) {
        List<UiMenu.Item> items = new ArrayList<>();
        switch (menuId) {
            case MENU_LEVEL -> {
                for (int i = 0; i < CommunityFilter.LEVELS.length; i++) {
                    items.add(UiMenu.Item.of(String.valueOf(i), null, levelText(i), null, i == filter.level()));
                }
            }
            case MENU_BUDGET -> {
                for (int i = 0; i < Tuning.BUDGETS.length; i++) {
                    items.add(UiMenu.Item.of(String.valueOf(i), i > 0 ? "coin" : null, budgetText(i), null, i == filter.budget()));
                }
            }
            case MENU_OWN -> {
                for (int i = 0; i < CommunityFilter.OWN.length; i++) {
                    items.add(UiMenu.Item.of(String.valueOf(i), null, ownText(i), null, i == filter.own()));
                }
            }
            default -> {
                for (int i = 0; i < STATS.length; i++) {
                    items.add(UiMenu.Item.of(String.valueOf(i), null, L.t("wynnlabs.community.stat_long." + STATS[i]), null, i == stat));
                }
            }
        }
        return items;
    }

    void pickMenu(int menuId, int i) {
        switch (menuId) {
            case MENU_LEVEL -> set(filter.withLevel(i));
            case MENU_BUDGET -> set(filter.withBudget(i));
            case MENU_OWN -> set(filter.withOwn(i));
            default -> {
                stat = i;
                sortCol = 2;
                sortDesc = true;
            }
        }
    }

    void scroll(double v) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(v) * 3 * rowH()));
    }

    private void scrollTo(String id) {
        int y = goal() != null ? rowH() : 0;
        for (Object row : rows()) {
            if (row instanceof CommunityBuilds.Entry e && e.id().equals(id)) {
                scroll = y;
                return;
            }
            y += rowH();
        }
    }

    private void requestPrices(String id) {
        CommunityBuilds.Compare c = compared().get(id);
        if (c == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        for (CommunityBuilds.Piece p : c.pieces()) {
            if (!p.owned() && p.info() != null && p.price() == null) {
                names.add(p.name());
            }
        }
        if (!names.isEmpty()) {
            GameState.current().requestPrices(names);
        }
    }

    private CommunityBuilds.Entry byId(String id) {
        CommunityBuilds.Doc d = CommunityList.doc();
        if (d == null) {
            return null;
        }
        for (CommunityBuilds.Entry e : d.builds()) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    /** "Set as goal": what applying an imported link does, and its items the ones the road map builds around. */
    private void setGoal(String id, long now) {
        CommunityBuilds.Entry e = byId(id);
        WynnBuilderLink.Build b = e == null ? null : WynnBuilderLink.decode(e.link());
        if (b == null) {
            return;
        }
        LinkImport.Result r = LinkImport.of(GameState.current(), b);
        Plans.Build plan = LinkImport.asPlan(r.kept(), r.build().level(), r.build().skills(), now);
        if (plan == null) {
            tab.flash(L.t("wynnlabs.import.no_weapon"));
            return;
        }
        Plans.apply(plan.named(e.name().length() > Plans.MAX_NAME ? e.name().substring(0, Plans.MAX_NAME) : e.name()));
        Locks.set(r.kept().stream().map(ItemData::name).toList());
        tab.host().applied();
    }

    private void copy(String id) {
        CommunityBuilds.Entry e = byId(id);
        if (e == null) {
            return;
        }
        try {
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(e.link());
            tab.flash(L.t("wynnlabs.wb.copied"));
        } catch (Throwable t) {
            tab.flash(L.t("wynnlabs.wb.copy_failed"));
        }
    }

    /** Whether "Set as goal" is offered: the build is for your class or any class. */
    static boolean canGoal(CommunityBuilds.Entry e) {
        Klass k = klass();
        return e.any() || k == Klass.NONE || e.klassType() == k;
    }

    // =====================================================================================
    // the preview

    /** The preview: the list in a state, a row opened (null: none). */
    void preview(CommunityFilter f, boolean folded, String open) {
        filter = f;
        fold = folded;
        openId = open;
        allStats = false;
        scroll = 0;
        cacheKey = null;
        if (open != null) {
            scrollTo(open);
            scroll = Math.max(0, scroll - rowH());
            reveal = true;
        }
    }

    /** The preview: the matched builds after the filters (refreshed). */
    List<CommunityBuilds.Entry> matched() {
        refresh();
        return matched;
    }
}
