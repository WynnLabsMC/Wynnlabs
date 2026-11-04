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
 * The build window's Community view: the community build list ({@link CommunityList}) beside a
 * filter rail (class, level, roles, budget to finish, owned share, outdated, look-alikes,
 * crafted), with a search over build and item names, three builds picked for you, the chips of
 * the filters in force, and a table sorted by name, owned gear, a stat's change against what you
 * wear or the cost to finish. The build set as your goal stays pinned on top. A row opens in place
 * ({@link BuildCommunityRow}): its gear with where you have each piece or what it costs, the skill
 * points it needs, "Set as goal" and "Copy link". Nothing here acts in the game.
 */
final class BuildCommunityView {

    static final int ROW_H = 16;
    static final int ICON = 12;
    static final String[] STATS = {"dmg", "ehp", "mana", "speed"};
    /** Between the strip's pictures: a missing one's dashed square reaches 1 px out, so two stay 1 px apart. */
    static final int STRIP_GAP = 3;

    private final BuildWindow win;
    private final BuildControls ui;
    final BuildCommunityRow opened;

    BuildCommunityFilter filter = BuildCommunityFilter.defaults();
    boolean fold;
    /** The sort: 0 name, 1 owned gear, 2 the stat column, 3 cost; and its direction. */
    int sortCol = 1;
    boolean sortDesc = true;
    /** The stat column: an index in {@link #STATS}. */
    int stat;
    String openId;
    /** The opened build is to be scrolled into sight on the next frame. */
    boolean reveal;
    final Set<String> openFamilies = new HashSet<>();
    int scroll;
    private int maxScroll;
    final ScrollBar bar = new ScrollBar();
    int[] listBox;

    private CommunityBuilds.Owned owned = CommunityBuilds.Owned.NONE;
    private long ownedAt;
    private String cacheKey;
    private List<CommunityBuilds.Entry> matched = List.of();
    private Map<String, Integer> roleCounts = Map.of();
    private BuildCommunityFilter.Suggestion suggestion;

    BuildCommunityView(BuildWindow win, BuildControls ui) {
        this.win = win;
        this.ui = ui;
        this.opened = new BuildCommunityRow(this, ui);
    }

    /** The view is shown: the list is loaded or fetched in the background when it has to be. */
    void opened() {
        CommunityList.request();
    }

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

    /** A stat change of a comparison ({@link #STATS}); null when not known. */
    static Double statOf(CommunityBuilds.Compare c, int stat) {
        if (c == null || c.deltas() == null) {
            return null;
        }
        return switch (stat) {
            case 0 -> c.deltas().dmg();
            case 1 -> c.deltas().ehp();
            case 2 -> c.deltas().mana();
            default -> c.deltas().speed();
        };
    }

    /** A stat change as shown: "+18%", "−4%", "+3" for mana; null when not known. */
    static String statText(Double v, int stat) {
        if (v == null) {
            return null;
        }
        long r = Math.round(Math.abs(v));
        String s = (v >= 0 ? "+" : "−") + r;
        return stat == 2 ? s : L.pct(s);
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

    /** "2 to buy · 2.8LE" (with "+?" when a price is missing); "Owned" when nothing is. */
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
        CommunityBuilds.Doc d = CommunityList.doc();
        if (d == null) {
            drawState(g, x, y, w, h, mx, my);
            return;
        }
        refreshOwned();
        refresh();
        boolean compact = win.compact;
        int railW = compact ? 98 : 124;
        drawRail(g, host, x, y, railW, h, mx, my);
        int mxl = x + railW + Theme.SP_5;
        Kit.hair(g, mxl - Theme.SP_3, y, 1, h, Theme.LINE);
        drawMain(g, host, mxl, y, x + w - mxl, h, mx, my);
    }

    /** No list: loading, or it could not be loaded and when it is tried again. */
    private void drawState(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        CommunityList.State s = CommunityList.state();
        if (s == CommunityList.State.FAILED) {
            long min = Math.max(1, (CommunityList.retryIn() + 59_999) / 60_000);
            UiEmpty.draw(g, ui.font, x, y, w, h, win.compact, "world", L.t("wynnlabs.community.failed"),
                    List.of(L.n("wynnlabs.community.retry", (int) min, min)), null, null, mx, my);
        } else {
            UiEmpty.draw(g, ui.font, x, y, w, h, win.compact, "refresh", Ui.busy(L.t("wynnlabs.community.loading")),
                    List.of(), null, null, mx, my);
        }
    }

    // ---- the rail ----

    private int label(GuiGraphics g, String key, int x, int y) {
        Type.META.draw(g, ui.font, L.t(key), x, y, Theme.INK_3);
        return y + Type.META.lineHeight + Theme.SP_1;
    }

    /** A check box row (8 px box, label after); returns the y under it. */
    private int check(GuiGraphics g, BuildWindow.Host host, String text, boolean on, int x, int y, int w, String act, Object arg,
            List<Component> tip, int mx, int my) {
        int rh = Type.BODY.lineHeight;
        boolean hov = BuildControls.in(mx, my, x, y, w, rh);
        int by = Type.BODY.onCaps(y, 8);
        g.fill(x, by, x + 8, by + 8, on ? Theme.ACCENT : hov ? Theme.CARD_HOVER : Theme.WELL);
        Kit.outline(g, x, by, 8, 8, on ? Theme.ACCENT : Theme.LINE);
        if (on) {
            Art.icon(g, "check", x + 1, by + 1, 6, Theme.CREAM);
        }
        Type.BODY.draw(g, ui.font, Type.BODY.fit(ui.font, text, w - 8 - Theme.SP_2), x + 8 + Theme.SP_2, y, on ? Theme.INK : Theme.INK_2);
        ui.btn(x, y, w, rh, act, arg);
        if (tip != null) {
            host.tip(x, y, w, rh, tip);
        }
        return y + rh;
    }

    /** A menu chip: the value and a chevron; returns the y under it. */
    private int menuChip(GuiGraphics g, String text, boolean on, int x, int y, int w, int menu, int mx, int my) {
        String t = Type.BODY.fit(ui.font, text, w - 2 * Theme.SP_4 - Theme.SP_1 - 6);
        Ui.iconChip(g, ui.font, null, t, true, x, y, w, on, mx, my);
        if (win.menu == menu) {
            win.menuAt = new int[] {x, y, w, Ui.CHIP_H};
        }
        ui.btn(x, y, w, Ui.CHIP_H, "menu", menu);
        return y + Ui.CHIP_H;
    }

    private void drawRail(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        int gap = win.compact ? Theme.SP_1 : Theme.SP_4;
        int step = win.compact ? 0 : Theme.SP_1;          // between check rows: none in the compact menu, so the rail fits
        int cy = y;
        Klass k = klass();
        String cls = k == Klass.NONE ? L.t("wynnlabs.community.your_class_none")
                : L.t("wynnlabs.community.your_class", k.name().charAt(0) + k.name().substring(1).toLowerCase(Locale.ROOT));
        cy = label(g, "wynnlabs.community.f_class", x, cy);
        cy = check(g, host, cls, !filter.allClasses(), x, cy, w, "c.classes", false, null, mx, my) + step;
        cy = check(g, host, L.t("wynnlabs.community.all_classes"), filter.allClasses(), x, cy, w, "c.classes", true, null, mx, my) + gap;

        cy = label(g, "wynnlabs.community.f_level", x, cy);
        cy = menuChip(g, levelText(filter.level()), filter.level() != 0, x, cy, w, BuildWindow.MENU_C_LEVEL, mx, my) + gap;

        cy = label(g, "wynnlabs.community.f_role", x, cy);
        for (String role : CommunityBuilds.ROLES.keySet()) {
            String t = L.t("wynnlabs.community.role." + role) + " (" + roleCounts.getOrDefault(role, 0) + ")";
            cy = check(g, host, t, filter.roles().contains(role), x, cy, w, "c.role", role, null, mx, my) + step;
        }
        cy += gap - step;

        cy = label(g, "wynnlabs.community.f_budget", x, cy);
        cy = menuChip(g, budgetText(filter.budget()), filter.budget() != 0, x, cy, w, BuildWindow.MENU_C_BUDGET, mx, my) + gap;

        cy = label(g, "wynnlabs.community.f_own", x, cy);
        cy = menuChip(g, ownText(filter.own()), filter.own() != 0, x, cy, w, BuildWindow.MENU_C_OWN, mx, my) + gap;

        cy = label(g, "wynnlabs.community.f_other", x, cy);
        cy = check(g, host, L.t("wynnlabs.community.hide_outdated"), filter.hideOutdated(), x, cy, w, "c.outdated", null, null, mx, my) + step;
        cy = check(g, host, L.t("wynnlabs.community.fold"), fold, x, cy, w, "c.fold", null,
                List.of(L.c("wynnlabs.community.fold_tip").withStyle(ChatFormatting.GRAY)), mx, my) + step;
        check(g, host, L.t("wynnlabs.community.has_crafted"), filter.crafted(), x, cy, w, "c.crafted", null, null, mx, my);
    }

    static String levelText(int i) {
        if (i == 0) {
            int lv = GameState.current().level();
            return lv > 0 ? L.t("wynnlabs.community.level_yours", lv) : L.t("wynnlabs.community.level_any");
        }
        if (i == BuildCommunityFilter.ANY_LEVEL) {
            return L.t("wynnlabs.community.level_any");
        }
        int[] r = BuildCommunityFilter.LEVELS[i];
        return L.t("wynnlabs.community.level_range", r[0], r[1]);
    }

    static String budgetText(int i) {
        return i == 0 ? L.t("wynnlabs.community.budget_none") : L.t("wynnlabs.community.budget_at_most", Prices.fmt(Tuning.BUDGETS[i]));
    }

    static String ownText(int i) {
        return i == 0 ? L.t("wynnlabs.community.own_any") : L.pct(String.valueOf(BuildCommunityFilter.OWN[i]));
    }

    // ---- the main column ----

    private void drawMain(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        CommunityBuilds.Doc d = CommunityList.doc();
        // the search and the count
        String count = L.t("wynnlabs.community.count", matched.size(), d.builds().size());
        int cw = Type.META.width(ui.font, count);
        int sw = w - cw - Theme.SP_3;
        UiInput.Hit sh = UiInput.search(g, ui.font, win.communitySearch, L.t("wynnlabs.community.search_hint"), false, x, y, sw, mx, my);
        ui.btn(x, y, sw, UiInput.H, "search", null);
        if (sh.clear() != null) {
            int[] c = sh.clear();
            ui.btn(c[0], c[1], c[2], c[3], "c.searchclear", null);
        }
        Type.META.draw(g, ui.font, count, x + w - cw, Type.META.yIn(y, UiInput.H), Theme.INK_3);
        int cy = y + UiInput.H + Theme.SP_3;
        String q = win.communitySearch.getValue();
        if (!q.equals(filter.query())) {
            filter = filter.withQuery(q);
            scroll = 0;
            refresh();
        }

        // picked for you
        List<CommunityBuilds.Entry> picks = picks();
        if (picks.isEmpty() && compared().values().stream().anyMatch(c -> c.deltas() != null)) {
            // compared with your gear, and nothing of your class here beats it
            String none = L.t("wynnlabs.community.picked") + ": " + L.t("wynnlabs.community.nothing_beats");
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, none, w), x, cy, Theme.INK_3);
            cy += Type.META.lineHeight + Theme.SP_3;
        } else if (!picks.isEmpty()) {
            Type.META.draw(g, ui.font, L.t("wynnlabs.community.picked"), x, cy, Theme.INK_3);
            cy += Type.META.lineHeight + Theme.SP_1;
            int cardW = (w - 2 * Theme.SP_3) / 3;
            int cardH = 2 * Type.BODY.lineHeight + ICON + Theme.SP_3;
            for (int i = 0; i < picks.size(); i++) {
                drawPick(g, host, picks.get(i), x + i * (cardW + Theme.SP_3), cy, cardW, cardH, mx, my);
            }
            cy += cardH + Theme.SP_3;
        }

        // the filters in force, each a chip with its ✕
        List<String[]> chips = activeChips();
        if (!chips.isEmpty()) {
            int cx = x;
            for (String[] c : chips) {
                int chw = Math.min(Type.BODY.width(ui.font, c[1]) + 2 * Theme.SP_3 + Theme.SP_2 + 6, w);
                if (cx + chw > x + w) {
                    break;
                }
                ui.chosenChip(g, host, cx, cy, c[1], w, "c.unchip", c[0], mx, my);
                cx += chw + Theme.SP_2;
            }
            cy += Ui.CHIP_H + Theme.SP_3;
        }

        // the table
        int[] cols = columns(w);
        drawHeader(g, host, x, cy, w, cols, mx, my);
        cy += Type.META.lineHeight + Theme.SP_2;
        Kit.hair(g, x, cy - Theme.SP_1, w, 1, Theme.LINE);
        int listH = y + h - cy;
        CommunityBuilds.Entry goal = goal();
        if (matched.isEmpty() && goal == null) {
            drawNone(g, x, cy, w, listH, mx, my);
            return;
        }
        listBox = new int[] {x, cy, w, listH};
        int rw = maxScroll > 0 ? w - ScrollBar.W - Theme.SP_2 : w;
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        boolean in = BuildControls.in(mx, my, x, cy, w, listH);
        int rmx = in ? mx : -1;
        int rmy = in ? my : -1;
        g.enableScissor(x, cy, x + w, cy + listH);
        int ry = cy - scroll;
        int firstBtn = ui.btns.size();
        host = clipped(host, cy, cy + listH);
        List<Runnable> over = new ArrayList<>();
        if (goal != null) {
            ry = drawEntry(g, host, goal, x, ry, rw, cols, true, cy, cy + listH, over, rmx, rmy);
        }
        for (Object row : rows()) {
            if (row instanceof Family f) {
                ry = drawFamily(g, host, f, x, ry, rw, cols, cy, cy + listH, rmx, rmy);
                if (openFamilies.contains(f.key())) {
                    for (CommunityBuilds.Entry e : f.members()) {
                        ry = drawEntry(g, host, e, x + Theme.SP_4, ry, rw - Theme.SP_4, columns(rw - Theme.SP_4), false, cy, cy + listH, over, rmx, rmy);
                    }
                }
            } else {
                CommunityBuilds.Entry e = (CommunityBuilds.Entry) row;
                if (goal != null && e.id().equals(goal.id())) {
                    continue;
                }
                ry = drawEntry(g, host, e, x, ry, rw, cols, false, cy, cy + listH, over, rmx, rmy);
            }
        }
        if (!over.isEmpty()) {
            g.nextStratum();                            // the fades and price marks over the item pictures
            over.forEach(Runnable::run);
        }
        g.disableScissor();
        // a row cut by the list's edges takes no click there (the table's header lies above it)
        int top = cy;
        int bottom = cy + listH;
        for (int i = ui.btns.size() - 1; i >= firstBtn; i--) {
            BuildControls.Btn b = ui.btns.get(i);
            if (b.y() < top || b.y() + b.h() > bottom) {
                ui.btns.remove(i);
            }
        }
        int total = ry + scroll - cy;
        maxScroll = Math.max(0, total - listH);
        bar.place(x + w - ScrollBar.W, cy, listH, listH, total, scroll);
        bar.draw(g, mx, my);
    }

    /** {@code host} whose hovers count only when they lie between {@code top} and {@code bottom} (the list's clip). */
    private static BuildWindow.Host clipped(BuildWindow.Host host, int top, int bottom) {
        return new BuildWindow.Host() {
            @Override
            public void tip(int x, int y, int w, int h, List<Component> tip) {
                if (y >= top && y + h <= bottom) {
                    host.tip(x, y, w, h, tip);
                }
            }

            @Override
            public void item(int x, int y, int w, int h, io.github.anilken.wynnlabs.model.ItemData info, ItemStack stack,
                    List<Component> extra) {
                if (y >= top && y + h <= bottom) {
                    host.item(x, y, w, h, info, stack, extra);
                }
            }

            @Override
            public ItemStack icon(io.github.anilken.wynnlabs.model.ItemData info) {
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

            @Override
            public void craft(io.github.anilken.wynnlabs.crafting.RecipeCode.Recipe recipe) {
                host.craft(recipe);
            }
        };
    }

    /** The table's columns: name, gear, stat, cost, tags (0 wide in the compact menu) as x offsets {name, gear, stat, cost, tags, end}. */
    int[] columns(int w) {
        int gear = 9 * ICON + 8 * STRIP_GAP;
        int stat = win.compact ? 32 : 40;
        int cost = win.compact ? 38 : 46;
        int tags = win.compact ? 0 : Math.max(0, Math.min(84, w / 5));
        int gap = Theme.SP_3;
        int name = w - gear - stat - cost - tags - (tags > 0 ? 4 : 3) * gap;
        int[] c = new int[6];
        c[0] = 0;
        c[1] = name + gap;
        c[2] = c[1] + gear + gap;
        c[3] = c[2] + stat + gap;
        c[4] = c[3] + cost + gap;
        c[5] = w;
        return c;
    }

    private void drawHeader(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int[] c, int mx, int my) {
        String[] names = {L.t("wynnlabs.community.col_build"), L.t("wynnlabs.community.col_gear"),
            L.t("wynnlabs.community.stat." + STATS[stat]), L.t("wynnlabs.community.col_cost")};
        for (int i = 0; i < 4; i++) {
            int cx = x + c[i];
            int cw = c[i + 1] - c[i] - Theme.SP_3;
            boolean on = sortCol == i;
            String t = names[i] + (on ? sortDesc ? " ▾" : " ▴" : "");
            int tw = Type.META.width(ui.font, t);
            boolean hov = BuildControls.in(mx, my, cx, y, Math.min(cw, tw), Type.META.lineHeight);
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, t, cw), cx, y, on || hov ? Theme.INK : Theme.INK_3);
            ui.btn(cx, y, Math.min(cw, tw), Type.META.lineHeight, "c.sort", i);
            if (i == 2) {
                // the stat column's switch: Dmg, EHP, Mana or Speed
                int ax = cx + Math.min(cw, tw) + Theme.SP_1;
                Art.icon(g, "chevron-down", ax, y + 1, 6, BuildControls.in(mx, my, ax - 1, y, 8, 9) ? Theme.INK : Theme.INK_3);
                ui.btn(ax - 1, y - 1, 9, Type.META.lineHeight + 1, "menu", BuildWindow.MENU_C_STAT);
                if (win.menu == BuildWindow.MENU_C_STAT) {
                    win.menuAt = new int[] {cx, y, Math.max(60, cw), Type.META.lineHeight};
                }
                host.tip(ax - 1, y - 1, 9, Type.META.lineHeight + 1, List.of(L.c("wynnlabs.community.stat_tip").withStyle(ChatFormatting.GRAY)));
            }
        }
        if (c[4] < c[5]) {
            Type.META.draw(g, ui.font, L.t("wynnlabs.community.col_tags"), x + c[4], y, Theme.INK_3);
        }
    }

    /** One build's row (and, when it is the open one, its details under it); returns the y after it. */
    private int drawEntry(GuiGraphics g, BuildWindow.Host host, CommunityBuilds.Entry e, int x, int y, int w, int[] c, boolean pinned,
            int top, int bottom, List<Runnable> over, int mx, int my) {
        boolean open = e.id().equals(openId);
        CommunityBuilds.Compare cmp = compared().get(e.id());
        if (y + ROW_H >= top && y <= bottom) {
            boolean hov = BuildControls.in(mx, my, x, y, w, ROW_H);
            if (pinned || open) {
                g.fill(x - Theme.SP_1, y, x + w, y + ROW_H, Theme.SELECTED);
            } else if (hov) {
                Kit.rowLit(g, x - Theme.SP_1, y, w + Theme.SP_1, ROW_H);
            }
            ui.btn(x, y, w, ROW_H, "c.open", e.id());
            int ty = Type.BODY.yIn(y, ROW_H);
            int nx = x;
            if (pinned) {
                Art.icon(g, "target", nx, Type.BODY.onCaps(ty, 8), 8, Theme.ACCENT);
                nx += 8 + Theme.SP_1;
            }
            int nameW = x + c[1] - Theme.SP_3 - nx;
            Type role = win.compact ? Type.META : Type.BODY;          // the compact menu: more of the name in the small type
            role.draw(g, ui.font, role.fit(ui.font, e.name(), nameW), nx, role.yIn(y, ROW_H), Theme.INK);
            host.tip(x, y, c[1] - Theme.SP_3, ROW_H, rowTip(e, pinned, cmp));
            // the gear: each piece's picture, missing ones faded in a dashed square
            int gx = x + c[1];
            int iy = y + (ROW_H - ICON) / 2;
            if (cmp != null) {
                List<CommunityBuilds.Piece> pieces = cmp.pieces();
                for (int i = 0; i < pieces.size() && i < 9; i++) {
                    CommunityBuilds.Piece p = pieces.get(i);
                    int px = gx + i * (ICON + STRIP_GAP);
                    ItemStack st = BuildCommunityRow.icon(host, p);
                    if (!st.isEmpty()) {
                        g.pose().pushMatrix();
                        g.pose().translate(px, iy);
                        g.pose().scale(ICON / 16f, ICON / 16f);
                        g.renderItem(st, 0, 0);
                        g.pose().popMatrix();
                    }
                    if (!p.owned()) {
                        over.add(() -> {
                            g.fill(px, iy, px + ICON, iy + ICON, 0x99EADCB4);
                            BuildCommunityRow.dashed(g, px - 1, iy - 1, ICON + 2, ICON + 2, Theme.INK_3);
                        });
                    }
                    host.tip(px, iy, ICON, ICON, BuildCommunityRow.pieceTip(p));
                }
            } else {
                Type.META.draw(g, ui.font, "…", gx, Type.META.yIn(y, ROW_H), Theme.INK_3);
            }
            // the stat, the cost
            String st = statText(statOf(cmp, stat), stat);
            Double sv = statOf(cmp, stat);
            Type.BODY.draw(g, ui.font, st == null ? "—" : st, x + c[2], ty,
                    st == null ? Theme.INK_3 : sv >= 0.5 ? Theme.GOOD : sv <= -0.5 ? Theme.BAD : Theme.INK_2);
            boolean priced = cmp != null && cmp.knownCost() > 0;
            String cost = cmp == null ? "…" : cmp.missing() == 0 ? L.t("wynnlabs.community.owned_short")
                    : priced ? price(cmp.knownCost()) + (cmp.unpriced() > 0 ? "+" : "") : L.t("wynnlabs.community.no_price_short");
            if (pinned && cmp != null && cmp.missing() > 0 && c[4] >= c[5]) {
                // the goal without a tags column: what is left to buy in the cost column (the price in its tooltip)
                cost = L.n("wynnlabs.community.n_to_buy", cmp.missing(), cmp.missing());
                priced = false;
            }
            Type cr = priced || cmp == null || cmp.missing() == 0 ? Type.BODY : Type.META;
            cr.draw(g, ui.font, cr.fit(ui.font, cost, c[4] - c[3] - Theme.SP_1), x + c[3], cr.yIn(y, ROW_H),
                    cmp != null && cmp.missing() == 0 ? Theme.GOOD : priced ? Theme.PRICE : Theme.INK_3);
            if (cmp != null) {
                host.tip(x + c[3], y, c[4] - c[3], ROW_H, List.of(Component.literal(toBuy(cmp)).withStyle(ChatFormatting.GOLD)));
            }
            // the tags (the large menu): the outdated mark first, then the play styles that fit
            if (c[4] < c[5]) {
                int tx = x + c[4];
                int end = x + c[5];
                List<String> ts = new ArrayList<>();
                if (pinned && cmp != null) {
                    ts.add("goal");
                }
                if (e.outdated()) {
                    ts.add("outdated");
                }
                ts.addAll(e.tags());
                int sy = y + (ROW_H - Ui.SEAL_H) / 2;
                for (String t : ts) {
                    String label = t.equals("goal") ? toBuy(cmp) : L.t(t.equals("outdated") ? "wynnlabs.community.outdated" : "wynnlabs.community.tag." + t);
                    int lw = Type.META.width(ui.font, label) + 2 * Theme.SP_2;
                    if (tx + lw > end) {
                        break;
                    }
                    int colour = t.equals("goal") ? Theme.ACCENT : t.equals("outdated") ? Theme.WARN : Theme.SEAL_2;
                    Ui.seal(g, ui.font, label, tx, sy, colour);
                    if (t.equals("outdated")) {
                        host.tip(tx, sy, lw, Ui.SEAL_H, List.of(L.c("wynnlabs.community.outdated_tip", e.checked()).withStyle(ChatFormatting.GRAY)));
                    }
                    tx += lw + Theme.SP_2;
                }
            }
        }
        y += ROW_H + Theme.SP_2;
        if (open && cmp != null) {
            boolean tags = c[4] >= c[5];            // no tags column: the tags under the header
            int hgt = opened.height(e, cmp, w, tags);
            if (y + hgt >= top && y <= bottom) {
                opened.draw(g, host, e, cmp, x, y, w, tags, over, mx, my);
            }
            if (reveal) {
                // the opened build whole in sight: its row at the top at most, its last line at the bottom
                int need = y + hgt - bottom;
                int room = y - ROW_H - Theme.SP_2 - top;
                if (need > 0) {
                    scroll += Math.min(need, Math.max(0, room));
                }
                reveal = false;
            }
            y += hgt + Theme.SP_3;
        }
        return y;
    }

    private List<Component> rowTip(CommunityBuilds.Entry e, boolean pinned, CommunityBuilds.Compare cmp) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(e.name()).withStyle(ChatFormatting.WHITE));
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
        if (key.equals(rowsKey)) {
            return rows;
        }
        rows = foldRows();
        rowsKey = key;
        return rows;
    }

    private String rowsKey;
    private List<Object> rows = List.of();

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
        if (y + ROW_H >= top && y <= bottom) {
            boolean open = openFamilies.contains(f.key());
            boolean hov = BuildControls.in(mx, my, x, y, w, ROW_H);
            if (hov) {
                Kit.rowLit(g, x - Theme.SP_1, y, w + Theme.SP_1, ROW_H);
            }
            ui.btn(x, y, w, ROW_H, "c.family", f.key());
            int ty = Type.BODY.yIn(y, ROW_H);
            Art.icon(g, open ? "chevron-down" : "chevron-right", x, Type.BODY.onCaps(ty, 6), 6, Theme.INK_2);
            String t = L.t("wynnlabs.community.family", f.name(), f.members().size());
            Type.LABEL.draw(g, ui.font, Type.LABEL.fit(ui.font, t, c[1] - Theme.SP_3 - 6 - Theme.SP_2 + (c[2] - c[1])), x + 6 + Theme.SP_2, ty,
                    Theme.INK);
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
                clo = Math.min(clo, cc.knownCost());
                chi = Math.max(chi, cc.knownCost());
            }
            List<Component> tip = List.of(Component.literal(L.t("wynnlabs.community.family_tip", bestOwned, total)).withStyle(ChatFormatting.GRAY));
            host.tip(x, y, c[2] - Theme.SP_3, ROW_H, tip);
            String span = lo == Double.MAX_VALUE ? "—" : statText(lo, stat) + "…" + statText(hi, stat);
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, span, c[3] - c[2] - Theme.SP_1), x + c[2], Type.META.yIn(y, ROW_H), Theme.INK_2);
            String cs = clo == Long.MAX_VALUE ? "—" : clo == chi ? price(clo) : price(clo) + "–" + price(chi);
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, cs, c[5] - c[3]), x + c[3], Type.META.yIn(y, ROW_H), Theme.PRICE);
        }
        return y + ROW_H + Theme.SP_2;
    }

    // ---- picked for you ----

    /**
     * Three builds of your class from the list as filtered that beat your gear: the biggest damage
     * gain first, then the most owned and the cheapest to finish. None when none beats it.
     */
    List<CommunityBuilds.Entry> picks() {
        Map<String, CommunityBuilds.Compare> cmp = compared();
        Klass k = klass();
        List<CommunityBuilds.Entry> mine = new ArrayList<>();
        for (CommunityBuilds.Entry e : matched) {
            CommunityBuilds.Compare c = cmp.get(e.id());
            Double dmg = statOf(c, 0);
            if (c != null && !e.any() && (k == Klass.NONE || e.klassType() == k) && c.missing() > 0 && dmg != null && dmg >= 0.5) {
                mine.add(e);
            }
        }
        mine.sort(Comparator.<CommunityBuilds.Entry>comparingDouble(e -> statOf(cmp.get(e.id()), 0)).reversed()
                .thenComparing(Comparator.<CommunityBuilds.Entry>comparingInt(e -> cmp.get(e.id()).ownedPct()).reversed())
                .thenComparingLong(e -> cmp.get(e.id()).knownCost()));
        return mine.subList(0, Math.min(3, mine.size()));
    }

    private void drawPick(GuiGraphics g, BuildWindow.Host host, CommunityBuilds.Entry e, int x, int y, int w, int h, int mx, int my) {
        CommunityBuilds.Compare c = compared().get(e.id());
        boolean hov = BuildControls.in(mx, my, x, y, w, h);
        Kit.card(g, x, y, w, h, Theme.ACCENT, hov);
        int pad = Theme.SP_2 + 1;
        int ix = x + pad;
        int cy = y + Theme.SP_2;
        Type.LABEL.draw(g, ui.font, Type.LABEL.fit(ui.font, e.name(), w - 2 * pad), ix, cy, Theme.INK);
        cy += Type.BODY.lineHeight;
        Double dmg = statOf(c, 0);
        String d = dmg == null ? L.t("wynnlabs.community.owned_of", c.owned(), c.total())
                : L.t("wynnlabs.community.dmg_delta", statText(dmg, 0));
        Type.META.draw(g, ui.font, d, ix, cy, dmg == null ? Theme.INK_2 : dmg >= 0 ? Theme.GOOD : Theme.BAD);
        cy += Type.BODY.lineHeight;
        String buy = L.t("wynnlabs.community.buy");
        Type.META.draw(g, ui.font, buy, ix, Type.META.yIn(cy, ICON), Theme.INK_3);
        int px = ix + Type.META.width(ui.font, buy) + Theme.SP_2;
        String cost = c.knownCost() > 0 ? price(c.knownCost()) + (c.unpriced() > 0 ? "+" : "") : L.t("wynnlabs.community.no_price_short");
        int costW = Type.META.width(ui.font, cost);
        for (CommunityBuilds.Piece p : c.pieces()) {
            if (p.owned()) {
                continue;
            }
            if (px + ICON > x + w - pad - costW - Theme.SP_2) {
                break;
            }
            ItemStack st = BuildCommunityRow.icon(host, p);
            if (!st.isEmpty()) {
                g.pose().pushMatrix();
                g.pose().translate(px, cy);
                g.pose().scale(ICON / 16f, ICON / 16f);
                g.renderItem(st, 0, 0);
                g.pose().popMatrix();
            }
            host.tip(px, cy, ICON, ICON, BuildCommunityRow.pieceTip(p));
            px += ICON + Theme.SP_1;
        }
        Type.META.draw(g, ui.font, cost, x + w - pad - costW, Type.META.yIn(cy, ICON), c.knownCost() > 0 ? Theme.PRICE : Theme.INK_3);
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
        String sug = suggestionText(suggestion);
        int[] box = UiEmpty.draw(g, ui.font, x, y, w, h, true, "search", L.t("wynnlabs.community.none"), List.of(),
                new UiEmpty.Action("eraser", L.t("wynnlabs.community.clear_filters"), "c.clear"), null, mx, my);
        if (box != null) {
            ui.btn(box[0], box[1], box[2], box[3], "c.clear", null);
        }
        if (sug != null && box != null) {
            int sy = box[1] + box[3] + Theme.SP_4;              // under "Clear filters"
            int sw = Type.BODY.width(ui.font, sug);
            int sx = x + (w - sw) / 2;
            boolean hov = BuildControls.in(mx, my, sx, sy, sw, Type.BODY.lineHeight);
            Type.BODY.draw(g, ui.font, Type.BODY.fit(ui.font, sug, w), Math.max(x, sx), sy, hov ? Theme.ACCENT : Theme.LINK);
            ui.btn(sx, sy, sw, Type.BODY.lineHeight, "c.suggest", null);
        }
    }

    static String suggestionText(BuildCommunityFilter.Suggestion s) {
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
            case "c.searchclear" -> {
                win.communitySearch.setValue("");
                win.communitySearch.setFocused(true);
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
            case "c.open" -> {
                String id = (String) arg;
                openId = id.equals(openId) ? null : id;
                if (openId != null) {
                    requestPrices(openId);
                    reveal = true;
                }
            }
            case "c.pick" -> {
                openId = (String) arg;
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
                win.communitySearch.setValue("");
                set(BuildCommunityFilter.defaults());
            }
            case "c.suggest" -> {
                if (suggestion != null) {
                    if (suggestion.kind().equals("search")) {
                        win.communitySearch.setValue("");
                    }
                    set(suggestion.to());
                }
            }
            case "c.goal" -> setGoal((String) arg, now);
            case "c.copy" -> copy((String) arg);
            case "c.craft" -> {
                if (win.host() != null && arg instanceof io.github.anilken.wynnlabs.crafting.RecipeCode.Recipe r) {
                    win.host().craft(r);
                    win.close();
                }
            }
            default -> { }
        }
    }

    private void set(BuildCommunityFilter f) {
        filter = f;
        scroll = 0;
        cacheKey = null;
        rowsKey = null;
    }

    private void unchip(String id) {
        if (id.startsWith("role:")) {
            set(filter.withRole(id.substring(5), false));
            return;
        }
        switch (id) {
            case "search" -> win.communitySearch.setValue("");
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
    List<UiMenu.Item> menuItems(int menu) {
        List<UiMenu.Item> items = new ArrayList<>();
        switch (menu) {
            case BuildWindow.MENU_C_LEVEL -> {
                for (int i = 0; i < BuildCommunityFilter.LEVELS.length; i++) {
                    items.add(UiMenu.Item.of(String.valueOf(i), null, levelText(i), null, i == filter.level()));
                }
            }
            case BuildWindow.MENU_C_BUDGET -> {
                for (int i = 0; i < Tuning.BUDGETS.length; i++) {
                    items.add(UiMenu.Item.of(String.valueOf(i), i > 0 ? "coin" : null, budgetText(i), null, i == filter.budget()));
                }
            }
            case BuildWindow.MENU_C_OWN -> {
                for (int i = 0; i < BuildCommunityFilter.OWN.length; i++) {
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

    void pickMenu(int menu, int i) {
        switch (menu) {
            case BuildWindow.MENU_C_LEVEL -> set(filter.withLevel(i));
            case BuildWindow.MENU_C_BUDGET -> set(filter.withBudget(i));
            case BuildWindow.MENU_C_OWN -> set(filter.withOwn(i));
            default -> {
                stat = i;
                sortCol = 2;
                sortDesc = true;
            }
        }
    }

    boolean isMenu(int menu) {
        return menu == BuildWindow.MENU_C_LEVEL || menu == BuildWindow.MENU_C_BUDGET || menu == BuildWindow.MENU_C_OWN
                || menu == BuildWindow.MENU_C_STAT;
    }

    void scroll(double v) {
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(v) * 3 * (ROW_H + Theme.SP_2)));
    }

    private void scrollTo(String id) {
        int y = goal() != null ? ROW_H + Theme.SP_2 : 0;
        for (Object row : rows()) {
            if (row instanceof CommunityBuilds.Entry e && e.id().equals(id)) {
                scroll = y;
                return;
            }
            y += ROW_H + Theme.SP_2;
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
            if (win.host() != null) {
                win.host().flash(L.t("wynnlabs.import.no_weapon"));
            }
            return;
        }
        Plans.apply(plan.named(e.name().length() > Plans.MAX_NAME ? e.name().substring(0, Plans.MAX_NAME) : e.name()));
        Locks.set(BuildWindow.names(r.kept()));
        win.hostApplied();
    }

    private void copy(String id) {
        CommunityBuilds.Entry e = byId(id);
        if (e == null) {
            return;
        }
        UiExport.copy(UiExport.WINDOW, e.link());
        if (win.host() != null) {
            win.host().flash(L.t("wynnlabs.wb.copied"));
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
    void preview(BuildCommunityFilter f, boolean folded, String open) {
        filter = f;
        fold = folded;
        openId = open;
        scroll = 0;
        cacheKey = null;
        if (open != null) {
            scrollTo(open);
            scroll = Math.max(0, scroll - (ROW_H + Theme.SP_2));
            reveal = true;
        }
    }

    /** The preview: the matched builds after the filters (refreshed). */
    List<CommunityBuilds.Entry> matched() {
        refresh();
        return matched;
    }

    Map<String, Integer> roleCounts() {
        return roleCounts;
    }
}
