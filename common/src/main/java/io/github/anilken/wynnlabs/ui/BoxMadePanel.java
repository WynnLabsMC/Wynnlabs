package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.HowMade;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.crafting.SearchRunner;
import io.github.anilken.wynnlabs.crafting.Shopping;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * The box window for a crafted item: how it was most likely made. The game keeps no ingredients
 * on a crafted item, so the crafting calculator is searched backwards ({@link HowMade}) on the
 * crafting search thread while the window shows "Searching…" and fills in. The title row (the
 * item, "Crafted scroll · Lv 82", the verdict), the cost card (recipe, ingredients, materials,
 * total at market prices), then two panes: the layout (the 2×3 grid, the materials and their
 * tiers, "Open in Recipe ›") and whether it matches (one row per value the item shows, then the
 * other layouts that fit, cheapest first; a click shows one). A small window shows one pane at a
 * time. It only reads the hovered item; nothing is sent to the game.
 */
final class BoxMadePanel {

    /** The search's time: long enough for three position-effect ingredients, short enough to wait for. */
    static final long MILLIS = 4_000;

    private static final SearchRunner RUNNER = new SearchRunner();

    private static final int PITCH = Type.BODY.lineHeight + Theme.SP_2;
    private static final int CELL = 16;

    private final BoxScreen s;
    final HowMade.Target target;
    private final CraftData.Recipe recipe;
    private volatile HowMade.Answer answer;
    private volatile boolean done;
    /** The layout shown: its index in the answer. */
    private int picked;
    /** A small window's pane: 0 the layout, 1 the match. */
    int tab;
    private final ScrollBar leftBar = new ScrollBar();
    private final ScrollBar rightBar = new ScrollBar();
    private int leftScroll;
    private int rightScroll;
    private int[] leftArea = new int[4];
    private int[] rightArea = new int[4];
    /** Prices read for what is shown, again when the market's prices move. */
    private Map<String, CraftSearch.Price> prices = Map.of();
    private int pricesFor = -1;
    private Object pricedLayout;
    private final Set<String> asked = new HashSet<>();

    BoxMadePanel(BoxScreen s, HowMade.Target target) {
        this.s = s;
        this.target = target;
        this.recipe = HowMade.recipe(target.type(), target.level());
        start();
    }

    /** Reads the ingredients and the prices it knows now (game thread) and starts the search. */
    private void start() {
        if (recipe == null) {
            answer = HowMade.Answer.EMPTY;
            done = true;
            return;
        }
        List<Ingredient> all = Wyn.craftIngredients();
        if (all.isEmpty()) {
            all = CraftingTab.previewIngredients;
        }
        List<Ingredient> usable = new ArrayList<>();
        for (Ingredient i : all) {
            if (i.usableBy(recipe.profession()) && i.level() <= recipe.maxLevel()) {
                usable.add(i);
            }
        }
        Map<String, CraftSearch.Price> known = Crafting.prices(usable, recipe);
        Function<String, Long> price = n -> {
            CraftSearch.Price p = known.get(n);
            return p == null ? null : p.emeralds();
        };
        List<Ingredient> list = usable;
        RUNNER.run(MILLIS, b -> {
            HowMade.Answer a = HowMade.solve(target, list, price, MILLIS, b, part -> answer = part);
            if (!b.cancelled()) {
                answer = a;
                done = true;
            }
        });
    }

    /** The window closed: a search still running stops. */
    void closed() {
        if (!done) {
            RUNNER.cancel();
        }
    }

    private HowMade.Found shown() {
        HowMade.Answer a = answer;
        if (a == null || a.layouts().isEmpty()) {
            return null;
        }
        return a.layouts().get(Math.max(0, Math.min(picked, a.layouts().size() - 1)));
    }

    /** A price by name or material key from what was read, null when unknown. */
    private Long price(String name) {
        CraftSearch.Price p = prices.get(name);
        return p == null ? null : p.emeralds();
    }

    /** The prices of what is shown, read again when the market's change; unknown ones asked for once. */
    private void readPrices(HowMade.Answer a) {
        if (a == null || recipe == null || pricesFor == Prices.version && pricedLayout == a) {
            return;
        }
        pricesFor = Prices.version;
        pricedLayout = a;
        Map<String, Ingredient> shownIngs = new LinkedHashMap<>();
        for (HowMade.Found f : a.layouts()) {
            for (Ingredient i : f.slots()) {
                if (i != null && !i.empty()) {
                    shownIngs.put(i.name(), i);
                }
            }
        }
        prices = Crafting.prices(shownIngs.values(), recipe);
        HowMade.Found best = a.best();
        if (best != null) {                // asked as soon as a layout is shown, not when the search ends
            List<String> ask = new ArrayList<>();
            for (Ingredient i : best.slots()) {
                if (i != null && !i.empty() && !prices.containsKey(i.name()) && asked.add(i.name())) {
                    ask.add(i.name());
                }
            }
            for (int m = 0; m < 2 && m < recipe.materials().size(); m++) {
                String key = Shopping.materialKey(recipe.materials().get(m).shown(), m == 0 ? best.tier1() : best.tier2());
                if (!prices.containsKey(key) && asked.add(key)) {
                    ask.add(key);
                }
            }
            if (!ask.isEmpty()) {
                GameState.current().requestPrices(ask);
            }
        }
    }

    // ---- drawing ----

    void draw(GuiGraphics g, int x, int y, int w, int bottom, int mx, int my) {
        HowMade.Answer a = answer;
        readPrices(a);
        y = title(g, a, x, y, w, mx, my);
        y += costs(g, x, y, w) + Theme.SP_4;
        if (recipe == null || done && (a == null || a.layouts().isEmpty())) {
            String why = L.t(recipe == null ? "wynnlabs.box.made.no_recipe" : "wynnlabs.box.made.nothing");
            Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), why, w), x, y, Theme.INK_3);
            return;
        }
        if (s.compact) {
            y = tabs(g, x, y, w, mx, my);
            if (tab == 0) {
                left(g, x, y, w, bottom - y, mx, my);
                rightArea = new int[4];
            } else {
                right(g, x, y, w, bottom - y, mx, my);
                leftArea = new int[4];
            }
            return;
        }
        int gap = Theme.SP_5;
        int lw = Math.max(170, Math.min(230, w * 45 / 100));
        left(g, x, y, lw - Theme.SP_2, bottom - y, mx, my);
        Kit.hair(g, x + lw + gap / 2 - 1, y, 1, bottom - y, 0x40A08A5A);
        right(g, x + lw + gap, y, w - lw - gap, bottom - y, mx, my);
    }

    /** The title row: the item, its kind and level, the verdict seal; then a hairline. */
    private int title(GuiGraphics g, HowMade.Answer a, int x, int y, int w, int mx, int my) {
        int h = Ui.CHIP_H;
        String seal;
        int sealInk;
        if (!done) {
            seal = Ui.busy(L.t("wynnlabs.box.made.searching"));
            sealInk = Theme.INK_3;
        } else {
            HowMade.Verdict v = a == null ? HowMade.Verdict.NONE : a.verdict();
            seal = switch (v) {
                case EXACT -> L.t("wynnlabs.box.made.exact");
                case POSSIBLE -> L.t("wynnlabs.box.made.possible");
                case CLOSEST, NONE -> L.t("wynnlabs.box.made.closest");
            };
            sealInk = v == HowMade.Verdict.EXACT ? Theme.SEAL_3 : v == HowMade.Verdict.POSSIBLE ? Theme.GOLD_EDGE : Theme.BAD;
        }
        // the seal's width with the most dots, so it does not jump while they come and go
        int sw = Type.META.width(s.font(), done ? seal : L.t("wynnlabs.box.made.searching").replace("…", "...")) + 2 * Theme.SP_2;
        int sx = x + w - sw;
        Kit.seal(g, sx, y + Ui.iconIn(h, Ui.SEAL_H), sw, Ui.SEAL_H, sealInk);
        Type.META.draw(g, s.font(), seal, sx + Theme.SP_2, Type.META.yIn(y + Ui.iconIn(h, Ui.SEAL_H), Ui.SEAL_H), Theme.SEAL_TEXT);
        if (done && a != null && BoxChips.in(mx, my, sx, y, sw, h)) {
            s.tip(List.of(L.c("wynnlabs.box.made.verdict_tip").withStyle(ChatFormatting.GRAY)));
        }
        int right = sx - Theme.SP_3;
        int sy = y + (h - 18) / 2;
        Kit.slot(g, x, sy, 18, 0);
        if (s.singleIcon != null) {
            g.renderItem(s.singleIcon, x + 1, sy + 1);
        }
        int nx = x + 18 + Theme.SP_3;
        Type role = s.compact ? Type.LABEL : Type.HEADING;
        String name = role.fit(s.font(), target.name() == null ? "" : target.name(), Math.max(0, right - nx));
        role.draw(g, s.font(), name, nx, role.yIn(y, h), Theme.TIER_CRAFTED);
        nx += role.width(s.font(), name) + Theme.SP_3;
        String kind = L.t("wynnlabs.box.made.kind", CraftFormat.typeName(target.type()).toLowerCase(Locale.ROOT), target.level());
        Type.META.draw(g, s.font(), Type.META.fit(s.font(), kind, Math.max(0, right - nx)), nx, Type.META.yIn(y, h), Theme.INK_3);
        int hy = y + h + Theme.SP_3;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_4;
    }

    /** The cost card: recipe, ingredients, materials and total at market prices; returns its height. */
    private int costs(GuiGraphics g, int x, int y, int w) {
        int top = Type.META.lineHeight + Type.NUMBER.lineHeight + 2 * Theme.SP_3;
        Art.box(g, x, y, w, top, 5, 0xFFF4EAD0, 0x40A08A5A);
        HowMade.Found f = shown();
        List<String[]> cells = new ArrayList<>();
        cells.add(new String[] {L.t("wynnlabs.craft.recipe"), recipe == null ? "?"
                : L.t("wynnlabs.box.made.recipe_value", CraftFormat.typeName(recipe.type()), recipe.minLevel() + "–" + recipe.maxLevel())});
        HowMade.Cost ing = f == null ? null : HowMade.ingredientsCost(f.slots(), this::price);
        HowMade.Cost mat = f == null ? null : HowMade.materialsCost(recipe, f.tier1(), f.tier2(), this::price);
        cells.add(new String[] {L.t("wynnlabs.box.made.cell_ingredients"), cost(ing)});
        cells.add(new String[] {L.t("wynnlabs.box.made.cell_materials"), cost(mat)});
        cells.add(new String[] {L.t("wynnlabs.box.made.cell_total"), ing == null ? "?" : cost(ing.plus(mat))});
        int cx = x + Theme.SP_4;
        int gap = s.compact ? Theme.SP_5 : Theme.SP_6;
        for (int i = 0; i < cells.size(); i++) {
            String[] c = cells.get(i);
            int cw = Math.max(Type.META.width(s.font(), c[0]), Type.NUMBER.width(s.font(), c[1]));
            if (cx + cw > x + w - Theme.SP_3) {
                break;
            }
            Type.META.draw(g, s.font(), c[0], cx, y + Theme.SP_3, Theme.INK_3);
            Type.NUMBER.draw(g, s.font(), c[1], cx, y + Theme.SP_3 + Type.META.lineHeight, i == 0 ? Theme.INK : Theme.PRICE);
            cx += cw + gap;
            if (i < cells.size() - 1) {
                Kit.hair(g, cx - gap / 2, y + Theme.SP_3 + 1, 1, top - 2 * Theme.SP_3 - 2, 0x40A08A5A);
            }
        }
        return top;
    }

    /** "1.2k", "1.2k +?" (some prices unknown), or "?" (none known; never blocks). */
    private static String cost(HowMade.Cost c) {
        if (c == null) {
            return "?";
        }
        if (c.priced() == 0 && c.unknown() == 0) {
            return Prices.fmt(0);
        }
        return CraftFormat.costText(c.emeralds(), c.priced() > 0, c.unknown() > 0);
    }

    /** A small window's switch between the two panes; returns where the pane starts. */
    private int tabs(GuiGraphics g, int x, int y, int w, int mx, int my) {
        String[] labels = {L.t("wynnlabs.box.made.made_title"), L.t("wynnlabs.box.made.match_title")};
        int cx = x;
        for (int i = 0; i < 2; i++) {
            int cw = Ui.chipWidth(s.font(), labels[i]);
            Ui.chip(g, s.font(), labels[i], cx, y, cw, tab == i, false, false, mx, my);
            int pick = i;
            s.hit(cx, y, cw, Ui.CHIP_H, () -> tab = pick);
            cx += cw + Theme.SP_2;
        }
        return y + Ui.CHIP_H + Theme.SP_4;
    }

    // ---- the layout pane ----

    private void left(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        leftArea = new int[] {x, y, w, h};
        HowMade.Found f = shown();
        int top = head(g, L.t("wynnlabs.box.made.made_title"), x, y, w);
        int listH = y + h - top;
        int inner = w - ScrollBar.W - Theme.SP_2;
        int total = leftHeight(f);
        leftScroll = Math.max(0, Math.min(ScrollBar.max(listH, total), leftScroll));
        g.enableScissor(x, top, x + w, top + listH);
        int cy = top - leftScroll;
        if (f == null) {
            Type.BODY.draw(g, s.font(), Ui.busy(L.t("wynnlabs.box.made.searching")), x, cy, Theme.INK_3);
        } else {
            // the 2×3 grid: slot n at row n / 2, column n % 2
            int cw = (inner - Theme.SP_2) / 2;
            for (int i = 0; i < Craft.SLOTS; i++) {
                Ingredient ing = i < f.slots().size() ? f.slots().get(i) : Ingredient.NONE;
                int gx = x + (i % 2) * (cw + Theme.SP_2);
                int gy = cy + (i / 2) * (CELL + Theme.SP_2);
                boolean empty = ing == null || ing.empty();
                ItemStack icon = empty ? null : CraftIcons.of(ing.name());
                CraftIcons.slot(g, gx, gy, CELL, icon);
                String label = empty ? L.t("wynnlabs.box.made.empty") : ing.name();
                int tx = gx + CELL + Theme.SP_2;
                Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), label, cw - CELL - Theme.SP_2), tx,
                        Type.BODY.yIn(gy, CELL), empty ? Theme.INK_3 : Theme.INK);
                if (!empty && icon != null && BoxChips.in(mx, my, gx, gy, cw, CELL) && my >= top && my < top + listH) {
                    g.setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font, icon, mx, my);   // the game's font: its coin glyphs
                }
            }
            cy += 3 * (CELL + Theme.SP_2) + Theme.SP_3;
            Type.META.draw(g, s.font(), L.t("wynnlabs.box.made.cell_materials"), x, cy, Theme.INK_3);
            cy += Type.META.lineHeight + Theme.SP_1;
            for (int m = 0; m < 2 && m < f.recipe().materials().size(); m++) {
                CraftData.Material mat = f.recipe().materials().get(m);
                int tier = m == 0 ? f.tier1() : f.tier2();
                CraftIcons.slot(g, x, cy, 12, CraftIcons.of(mat.shown()));
                String line = L.t("wynnlabs.box.made.material_line", mat.amount(), mat.shown());
                int tx = x + 12 + Theme.SP_2;
                String fitted = Type.BODY.fit(s.font(), line, inner - 12 - Theme.SP_2 - 24);
                Type.BODY.draw(g, s.font(), fitted, tx, Type.BODY.yIn(cy, 12), Theme.INK);
                int lx = tx + Type.BODY.width(s.font(), fitted);
                Type.BODY.draw(g, s.font(), CraftFormat.stars(tier), lx + Theme.SP_2, Type.BODY.yIn(cy, 12), Theme.GOLD_EDGE);
                if (BoxChips.in(mx, my, x, cy, inner, 12) && my >= top && my < top + listH) {
                    // the material at its tier: its price each and for the amount
                    Long each = price(Shopping.materialKey(mat.shown(), tier));
                    List<net.minecraft.network.chat.Component> tip = new java.util.ArrayList<>();
                    tip.add(net.minecraft.network.chat.Component.literal(mat.shown() + " " + CraftFormat.stars(tier)).withStyle(ChatFormatting.WHITE));
                    tip.add(L.c("wynnlabs.box.made.material_tip", each == null ? "?" : Prices.fmt(each),
                            each == null ? "?" : Prices.fmt(each * mat.amount()), mat.amount()).withStyle(ChatFormatting.GOLD));
                    s.tip(tip);
                }
                cy += 12 + Theme.SP_2;
            }
            cy += Theme.SP_3;
            String open = L.t("wynnlabs.box.made.open_recipe");
            int bw = Ui.buttonWidth(s.font(), open);
            boolean hover = Ui.button(g, s.font(), open, x, cy, bw, mx, my) && my >= top && my < top + listH;
            if (hover) {
                s.tip(List.of(L.c("wynnlabs.box.made.open_tip").withStyle(ChatFormatting.GRAY)));
            }
            int by = Math.max(cy, top);
            int bb = Math.min(cy + Ui.BUTTON_H, top + listH);
            if (bb > by) {
                HowMade.Found pick = f;
                s.hit(x, by, bw, bb - by, () -> openRecipe(pick));
            }
        }
        g.disableScissor();
        leftBar.place(x + w - ScrollBar.W, top, listH, listH, total, leftScroll);
        leftBar.draw(g, mx, my);
    }

    private int leftHeight(HowMade.Found f) {
        if (f == null) {
            return Type.BODY.lineHeight;
        }
        return 3 * (CELL + Theme.SP_2) + Theme.SP_3 + Type.META.lineHeight + Theme.SP_1 + 2 * (12 + Theme.SP_2)
                + Theme.SP_3 + Ui.BUTTON_H;
    }

    /** The layout on the Crafting tab's Recipe view; Esc there comes back here. */
    private void openRecipe(HowMade.Found f) {
        CraftingTab.INSTANCE.load(new RecipeCode.Recipe(f.recipe().type(), f.recipe().minLevel(), f.tier1(), f.tier2(),
                Craft.Speed.NORMAL, f.names()));
        net.minecraft.client.Minecraft.getInstance().setScreen(RoadmapScreen.craft(s));
    }

    /** A pane's heading over a hairline; returns where its content starts. */
    private int head(GuiGraphics g, String text, int x, int y, int w) {
        Type.LABEL.draw(g, s.font(), Type.LABEL.fit(s.font(), text, w), x, y, Theme.INK);
        int hy = y + Type.LABEL.lineHeight + Theme.SP_1;
        Kit.hair(g, x, hy, w - ScrollBar.W - Theme.SP_2, 1, Theme.LINE);
        return hy + 1 + Theme.SP_3;
    }

    // ---- the match pane ----

    private void right(GuiGraphics g, int x, int y, int w, int h, int mx, int my) {
        rightArea = new int[] {x, y, w, h};
        HowMade.Answer a = answer;
        HowMade.Found f = shown();
        int top = head(g, L.t("wynnlabs.box.made.match_title"), x, y, w);
        int listH = y + h - top;
        int inner = w - ScrollBar.W - Theme.SP_2;
        List<HowMade.Row> rows = f == null ? List.of() : f.rows();
        int others = a == null ? 0 : a.layouts().size();
        int total = rows.size() * PITCH + (others > 1 ? Theme.SP_3 + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3 + others * PITCH : 0);
        rightScroll = Math.max(0, Math.min(ScrollBar.max(listH, total), rightScroll));
        g.enableScissor(x - Theme.SP_1, top, x + w, top + listH);
        int cy = top - rightScroll;
        if (f == null) {
            Type.BODY.draw(g, s.font(), Ui.busy(L.t("wynnlabs.box.made.searching")), x, cy, Theme.INK_3);
        }
        int rangeW = 0;
        for (HowMade.Row r : rows) {
            rangeW = Math.max(rangeW, Type.META.width(s.font(), range(r)));
        }
        rangeW = Math.min(rangeW, inner / 3);
        int valueX = x + 8 + Theme.SP_2 + Math.max(60, (inner - 8 - Theme.SP_2 - rangeW) * 3 / 5);
        for (HowMade.Row r : rows) {
            int ink = r.ok() ? Theme.GOOD : Theme.BAD;
            Art.icon(g, r.ok() ? "check" : "x", x, Type.BODY.onCaps(cy, 8), 8, ink);
            int lx = x + 8 + Theme.SP_2;
            Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), label(r), valueX - Theme.SP_2 - lx), lx, cy, r.ok() ? Theme.INK : Theme.BAD);
            String value = value(r);
            Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), value, x + inner - rangeW - Theme.SP_2 - valueX), valueX, cy,
                    r.ok() ? Theme.INK_2 : Theme.BAD);
            Type.META.drawRight(g, s.font(), Type.META.fit(s.font(), range(r), rangeW), x + inner, cy + 1,
                    r.ok() ? Theme.INK_3 : Theme.BAD);
            cy += PITCH;
        }
        if (others > 1) {
            cy += Theme.SP_3;
            boolean fit = a.layouts().get(0).exact();
            Type.LABEL.draw(g, s.font(), L.t(fit ? "wynnlabs.box.made.others_title" : "wynnlabs.box.made.others_close"), x, cy, Theme.INK);
            cy += Type.LABEL.lineHeight + Theme.SP_1;
            Kit.hair(g, x, cy, inner, 1, Theme.LINE);
            cy += 1 + Theme.SP_3;
            for (int i = 0; i < others; i++) {
                HowMade.Found o = a.layouts().get(i);
                boolean on = i == Math.min(picked, others - 1);
                boolean hover = BoxChips.in(mx, my, x - Theme.SP_1, cy - Theme.SP_1, inner + Theme.SP_1, PITCH)
                        && my >= top && my < top + listH;
                if (on) {
                    Kit.rowLit(g, x - Theme.SP_1, cy - Theme.SP_1, inner + Theme.SP_1, Type.BODY.lineHeight + Theme.SP_1);
                } else if (hover) {
                    Art.box(g, x - Theme.SP_1, cy - Theme.SP_1, inner + Theme.SP_1, Type.BODY.lineHeight + Theme.SP_1, 3, Theme.CARD_HOVER, 0);
                }
                HowMade.Cost c = HowMade.ingredientsCost(o.slots(), this::price)
                        .plus(HowMade.materialsCost(o.recipe(), o.tier1(), o.tier2(), this::price));
                String price = cost(c);
                int pw = Type.BODY.width(s.font(), price);
                Art.icon(g, o.exact() ? "check" : "x", x, Type.BODY.onCaps(cy, 8), 8, o.exact() ? Theme.GOOD : Theme.BAD);
                int lx = x + 8 + Theme.SP_2;
                Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), names(o), x + inner - pw - Theme.SP_3 - lx), lx, cy,
                        on ? Theme.INK : Theme.INK_2);
                Type.BODY.drawRight(g, s.font(), price, x + inner, cy, Theme.PRICE);
                if (hover) {
                    // the whole layout, and what "+?" leaves out: the parts with no market price yet
                    List<net.minecraft.network.chat.Component> tip = new java.util.ArrayList<>();
                    tip.add(net.minecraft.network.chat.Component.literal(names(o)).withStyle(ChatFormatting.WHITE));
                    java.util.Set<String> missing = new java.util.LinkedHashSet<>();
                    for (Ingredient ing : o.slots()) {
                        if (ing != null && !ing.empty() && price(ing.name()) == null) {
                            missing.add(ing.name());
                        }
                    }
                    for (int m = 0; m < o.recipe().materials().size() && m < 2; m++) {
                        String key = Shopping.materialKey(o.recipe().materials().get(m).shown(), m == 0 ? o.tier1() : o.tier2());
                        if (price(key) == null) {
                            missing.add(key);
                        }
                    }
                    if (!missing.isEmpty()) {
                        tip.add(L.c("wynnlabs.box.made.no_price", String.join(", ", missing)).withStyle(ChatFormatting.GRAY));
                    }
                    s.tip(tip);
                }
                int hy = Math.max(cy - Theme.SP_1, top);
                int hb = Math.min(cy + PITCH - Theme.SP_1, top + listH);
                if (hb > hy) {
                    int pick = i;
                    s.hit(x - Theme.SP_1, hy, inner + Theme.SP_1, hb - hy, () -> picked = pick);
                }
                cy += PITCH;
            }
        }
        g.disableScissor();
        rightBar.place(x + w - ScrollBar.W, top, listH, listH, total, rightScroll);
        rightBar.draw(g, mx, my);
    }

    /** "2× Essence Of Dusk, Fairy Dust", or "Empty" for none. */
    private static String names(HowMade.Found f) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (Ingredient i : f.slots()) {
            if (i != null && !i.empty()) {
                count.merge(i.name(), 1, Integer::sum);
            }
        }
        if (count.isEmpty()) {
            return L.t("wynnlabs.box.made.empty");
        }
        List<String> parts = new ArrayList<>();
        count.forEach((n, c) -> parts.add(c > 1 ? c + "× " + n : n));
        return String.join(", ", parts);
    }

    private static String label(HowMade.Row r) {
        return switch (r.kind()) {
            case LEVEL -> L.t("wynnlabs.box.made.row_level");
            case ID -> r.shown();
            case EXTRA -> L.t("wynnlabs.box.made.row_extra", r.shown());
            case CHARGES -> L.t("wynnlabs.box.made.row_charges");
            case DURATION -> L.t("wynnlabs.box.made.row_duration");
            case DURABILITY -> L.t("wynnlabs.box.made.row_durability");
            case HEALTH -> L.t("wynnlabs.box.made.row_health");
            case REQUIREMENT -> L.t("wynnlabs.box.made.row_requirement", CraftFormat.skill(Integer.parseInt(r.key())));
        };
    }

    private static String value(HowMade.Row r) {
        return switch (r.kind()) {
            case ID -> L.unit(CraftFormat.signed(r.value()), r.unit());
            case EXTRA -> L.t("wynnlabs.box.made.none");
            case DURATION -> L.unit(r.value(), "s");
            case REQUIREMENT -> r.value() <= 0 ? L.t("wynnlabs.box.made.none") : String.valueOf(r.value());
            default -> String.valueOf(r.value());
        };
    }

    private static String range(HowMade.Row r) {
        Craft.Range g = r.range();
        if (g == null) {
            return L.t("wynnlabs.box.made.none");
        }
        return switch (r.kind()) {
            case ID, EXTRA -> CraftFormat.range(g, r.unit());
            case LEVEL -> g.min() + "–" + g.max();
            case DURATION -> L.unit(g.min() == g.max() ? String.valueOf(g.min()) : g.min() + "–" + g.max(), "s");
            case REQUIREMENT -> g.min() <= 0 ? L.t("wynnlabs.box.made.none") : String.valueOf(g.min());
            default -> g.min() == g.max() ? String.valueOf(g.min()) : g.min() + "–" + g.max();
        };
    }

    // ---- input ----

    boolean mouseClicked(double mx, double my) {
        if (leftBar.mouseClicked(mx, my)) {
            leftScroll = leftBar.offset();
            return true;
        }
        if (rightBar.mouseClicked(mx, my)) {
            rightScroll = rightBar.offset();
            return true;
        }
        return false;
    }

    boolean mouseDragged(double my) {
        if (leftBar.mouseDragged(my)) {
            leftScroll = leftBar.offset();
            return true;
        }
        if (rightBar.mouseDragged(my)) {
            rightScroll = rightBar.offset();
            return true;
        }
        return false;
    }

    void mouseReleased() {
        leftBar.mouseReleased();
        rightBar.mouseReleased();
    }

    void mouseScrolled(double mx, double my, double v) {
        int step = (int) Math.signum(v) * PITCH;
        if (BoxChips.in(mx, my, leftArea[0], leftArea[1], leftArea[2], leftArea[3])) {
            leftScroll -= step;
        } else if (BoxChips.in(mx, my, rightArea[0], rightArea[1], rightArea[2], rightArea[3])) {
            rightScroll -= step;
        }
    }
}
