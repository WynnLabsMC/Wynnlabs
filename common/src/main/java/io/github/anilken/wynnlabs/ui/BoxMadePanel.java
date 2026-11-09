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
    /** The grid's inner padding, and a list row's height. */
    private static final int GRID_PAD = 4;
    private static final int ROW = 14;

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
        Map<String, CraftSearch.Price> known = new java.util.HashMap<>(Crafting.prices(usable, recipe));
        // a material tier with no listings gets its estimate, so the tiers that fit equally are
        // told apart by what they cost (the cheapest is kept) rather than by which have listings
        for (CraftData.Material m : recipe.materials()) {
            for (int t = 1; t <= 3; t++) {
                String key = Shopping.materialKey(m.shown(), t);
                Long e = known.containsKey(key) ? null : estimate(key);
                if (e != null) {
                    known.put(key, new CraftSearch.Price(e, true));
                }
            }
        }
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
        if (p != null) {
            return p.emeralds();
        }
        Long e = estimate(name);
        if (e != null) {
            estimated.add(name);
        }
        return e;
    }

    /** Names whose price above is an estimate, shown with "≈". */
    private final java.util.Set<String> estimated = new java.util.HashSet<>();
    private static final java.util.regex.Pattern TIER_KEY = java.util.regex.Pattern.compile("^(.+) T([1-3])$");

    /**
     * A price for something with no listing now: a material tier from the tier next to it (a tier
     * up about half as much again), else the material's price over all tiers; anything else its
     * last median. Null when nothing is known.
     */
    static Long estimate(String name) {
        java.util.regex.Matcher m = TIER_KEY.matcher(name);
        if (m.matches()) {
            String base = m.group(1);
            int t = Integer.parseInt(m.group(2));
            Double t1 = typical(base + " T1");
            Double t2 = typical(base + " T2");
            Double t3 = typical(base + " T3");
            // from the tier next to it: a tier up costs about half as much again; a dear tier
            // further away (few listings, wild prices) would lift the estimate too far
            if (t == 2 && t1 != null) {
                return Math.round(t1 * 1.5);
            }
            if (t == 3 && t2 != null) {
                return Math.round(t2 * 1.5);
            }
            if (t == 1 && t2 != null) {
                return Math.round(t2 / 1.5);
            }
            if (t == 3 && t1 != null) {
                return Math.round(t1 * 2.25);
            }
            Double all = typical(base);
            if (all != null) {
                return Math.round(all);
            }
        }
        Prices.Price p = Prices.of(name);
        return p != null && p.median() != null ? Math.round(p.median()) : null;
    }

    private static Double typical(String name) {
        Prices.Price p = Prices.of(name);
        return p == null ? null : p.typical();
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
        // on the Trade Market: what the listing asks, and whether buying or crafting is cheaper
        Long listed = listed();
        int verdictInk = Theme.PRICE;
        if (listed != null) {
            cells.add(new String[] {L.t("wynnlabs.box.made.cell_listed"), Prices.fmt(listed)});
            if (ing != null && ing.unknown() == 0 && mat.unknown() == 0) {
                long craft = ing.plus(mat).emeralds();
                boolean buy = listed <= craft;
                verdictInk = buy ? Theme.GOOD : Theme.WARN;
                cells.add(new String[] {L.t(buy ? "wynnlabs.box.made.cell_buy" : "wynnlabs.box.made.cell_craft"),
                        L.t("wynnlabs.box.made.saves", Prices.fmt(Math.abs(craft - listed)))});
            }
        }
        int cx = x + Theme.SP_4;
        int gap = s.compact ? Theme.SP_5 : Theme.SP_6;
        for (int i = 0; i < cells.size(); i++) {
            String[] c = cells.get(i);
            int cw = Math.max(Type.META.width(s.font(), c[0]), Type.NUMBER.width(s.font(), c[1]));
            if (cx + cw > x + w - Theme.SP_3) {
                break;
            }
            Type.META.draw(g, s.font(), c[0], cx, y + Theme.SP_3, Theme.INK_3);
            int ink = i == 0 ? Theme.INK : listed != null && i == 5 ? verdictInk : Theme.PRICE;
            Type.NUMBER.draw(g, s.font(), c[1], cx, y + Theme.SP_3 + Type.META.lineHeight, ink);
            cx += cw + gap;
            if (i < cells.size() - 1) {
                Kit.hair(g, cx - gap / 2, y + Theme.SP_3 + 1, 1, top - 2 * Theme.SP_3 - 2, 0x40A08A5A);
            }
        }
        return top;
    }

    private Long listedValue;
    private boolean listedRead;

    /** The price the item's Trade Market listing asks (read once from its lore), or null off the market. */
    private Long listed() {
        if (!listedRead) {
            listedRead = true;
            List<String> lines = new ArrayList<>();
            var lore = s.singleIcon == null ? null : s.singleIcon.get(net.minecraft.core.component.DataComponents.LORE);
            if (lore != null) {
                for (var c : lore.lines()) {
                    lines.add(io.github.anilken.wynnlabs.game.ItemJson.clean(c.getString()));
                }
            }
            listedValue = listedPrice(lines);
        }
        return listedValue;
    }

    /**
     * A listing's price from its tooltip lines: the number on the line after "Price"
     * ("17,719 (4LE 20EB 55E)" → 17719), or null when there is no price block.
     */
    static Long listedPrice(List<String> lore) {
        boolean next = false;
        for (String raw : lore) {
            String line = raw.strip().replaceFirst("^[^\\p{L}\\p{N}]+", "");
            if (line.startsWith("Price") || line.startsWith("Fiyat")) {
                next = true;
                continue;
            }
            if (next) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([0-9][0-9,.]*)").matcher(line);
                if (m.find()) {
                    try {
                        return Long.parseLong(m.group(1).replaceAll("[,.]", ""));
                    } catch (NumberFormatException e) {
                        return null;
                    }
                }
                return null;
            }
        }
        return null;
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
            // the 2×3 grid of pictures on the left (where each one sits matters), the list beside it
            int gw = GRID_PAD * 2 + 2 * CELL + Theme.SP_1;
            int gh = GRID_PAD * 2 + 3 * CELL + 2 * Theme.SP_1;
            Kit.box(g, x, cy, gw, gh, Theme.WELL, Theme.LINE);
            for (int i = 0; i < Craft.SLOTS; i++) {
                Ingredient ing = i < f.slots().size() ? f.slots().get(i) : Ingredient.NONE;
                boolean empty = ing == null || ing.empty();
                int gx = x + GRID_PAD + (i % 2) * (CELL + Theme.SP_1);
                int gy = cy + GRID_PAD + (i / 2) * (CELL + Theme.SP_1);
                ItemStack icon = empty ? null : CraftIcons.of(ing.name());
                CraftIcons.slot(g, gx, gy, CELL, icon);
                if (!empty && icon != null && BoxChips.in(mx, my, gx, gy, CELL, CELL) && my >= top && my < top + listH) {
                    g.setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font, icon, mx, my);   // the game's font: its coin glyphs
                }
            }
            int lx = x + gw + Theme.SP_4;
            int lw = x + inner - lx;
            int ly = cy;
            // the ingredients, the same ones together
            Map<String, Integer> count = new LinkedHashMap<>();
            for (Ingredient i : f.slots()) {
                if (i != null && !i.empty()) {
                    count.merge(i.name(), 1, Integer::sum);
                }
            }
            Type.META.draw(g, s.font(), L.t("wynnlabs.box.made.cell_ingredients"), lx, ly, Theme.INK_3);
            ly += Type.META.lineHeight + Theme.SP_1;
            long sum = 0;
            boolean unknown = false;
            for (Map.Entry<String, Integer> e : count.entrySet()) {
                Long each = price(e.getKey());
                unknown |= each == null;
                sum += each == null ? 0 : each * e.getValue();
                ItemStack icon = CraftIcons.of(e.getKey());
                String from = sourceOf(e.getKey());
                int rowH = ROW;
                // one line each; where it drops shows on hover, and a click opens the item in Search
                boolean hover = BoxChips.in(mx, my, lx, ly, lw, rowH) && my >= top && my < top + listH;
                if (hover) {
                    Art.box(g, lx - Theme.SP_1, ly - 1, lw + Theme.SP_1 * 2, rowH + 2, 3, Theme.CARD_HOVER, 0);
                }
                listRow(g, icon, e.getKey(), e.getValue(), each == null ? null : each * e.getValue(), estimated.contains(e.getKey()), null,
                        lx, ly, lw, false);
                if (hover && icon != null && BoxChips.in(mx, my, lx, ly, 12 + Theme.SP_2, ROW)) {
                    g.setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font, icon, mx, my);
                } else if (hover) {
                    List<net.minecraft.network.chat.Component> tip = new ArrayList<>();
                    if (from != null) {
                        tip.add(net.minecraft.network.chat.Component.literal(from).withStyle(ChatFormatting.WHITE));
                    }
                    tip.add(L.c("wynnlabs.box.made.search_tip").withStyle(ChatFormatting.GRAY));
                    s.tip(tip);
                }
                searchHit(e.getKey(), lx, ly, lw, rowH, top, listH);
                ly += rowH + Theme.SP_1;
            }
            long ingSum = sum;
            boolean ingUnknown = unknown;
            // the materials at their tiers
            ly += Theme.SP_2;
            Type.META.draw(g, s.font(), L.t("wynnlabs.box.made.cell_materials"), lx, ly, Theme.INK_3);
            ly += Type.META.lineHeight + Theme.SP_1;
            for (int m = 0; m < 2 && m < f.recipe().materials().size(); m++) {
                CraftData.Material mat = f.recipe().materials().get(m);
                int tier = m == 0 ? f.tier1() : f.tier2();
                Long each = price(Shopping.materialKey(mat.shown(), tier));
                unknown |= each == null;
                sum += each == null ? 0 : each * mat.amount();
                boolean hover = BoxChips.in(mx, my, lx, ly, lw, ROW) && my >= top && my < top + listH;
                listRow(g, CraftIcons.of(mat.shown()), mat.shown(), mat.amount(), each == null ? null : each * mat.amount(),
                        estimated.contains(Shopping.materialKey(mat.shown(), tier)),
                        CraftFormat.stars(tier), lx, ly, lw, hover);
                if (hover) {
                    materialTip(g, mat, tier, each, estimated.contains(Shopping.materialKey(mat.shown(), tier)), mx, my);
                }
                searchHit(mat.shown(), lx, ly, lw, ROW, top, listH);   // a material in Search: where to gather it
                ly += ROW + Theme.SP_2;
            }
            // the total under a hairline
            Kit.hair(g, lx, ly, lw, 1, Theme.LINE);
            ly += 1 + Theme.SP_2;
            String totalText = sum == 0 && unknown ? "?" : Prices.fmt(sum) + (unknown ? " +?" : "");
            Type.LABEL.draw(g, s.font(), L.t("wynnlabs.box.made.cell_total"), lx, ly, Theme.INK);
            Type.LABEL.drawRight(g, s.font(), totalText, lx + lw, ly, Theme.PRICE);
            ly += Type.LABEL.lineHeight + Theme.SP_1;
            // the materials are gathered by most crafters: what is left to buy then
            String gathered = ingSum == 0 && ingUnknown ? "?" : Prices.fmt(ingSum) + (ingUnknown ? " +?" : "");
            Type.META.draw(g, s.font(), Type.META.fit(s.font(), L.t("wynnlabs.box.made.gathered"), lw - Type.META.width(s.font(), gathered) - Theme.SP_3),
                    lx, ly, Theme.INK_3);
            Type.META.drawRight(g, s.font(), gathered, lx + lw, ly, Theme.PRICE);
            ly += Type.META.lineHeight;
            cy = Math.max(cy + gh, ly) + Theme.SP_4;
            // the actions: open the layout in Recipe, save it to the saved recipes, copy its WynnBuilder link
            HowMade.Found pick = f;
            int buttonsTop = cy;
            boolean justSaved = "save".equals(doneWhat) && System.currentTimeMillis() - doneAt < 2000;
            boolean justCopied = "link".equals(doneWhat) && System.currentTimeMillis() - doneAt < 2000;
            boolean noLink = "nolink".equals(doneWhat) && System.currentTimeMillis() - doneAt < 2000;
            String[] labels = {L.t("wynnlabs.box.made.open_recipe"),
                    L.t(justSaved ? "wynnlabs.box.made.saved_done" : "wynnlabs.box.made.save_recipe"),
                    L.t(justCopied ? "wynnlabs.box.made.copied" : noLink ? "wynnlabs.box.made.no_link" : "wynnlabs.box.made.wb_link")};
            String[] tips = {"wynnlabs.box.made.open_tip", "wynnlabs.box.made.save_tip", "wynnlabs.box.made.wb_tip"};
            Runnable[] acts = {() -> openRecipe(pick), () -> saveRecipe(pick), () -> copyLink(pick)};
            int bx = x;
            for (int k = 0; k < labels.length; k++) {
                int bw = Ui.buttonWidth(s.font(), labels[k]);
                if (bx > x && bx + bw > x + inner) {
                    bx = x;                         // no room left on this line: the next one
                    cy += Ui.BUTTON_H + Theme.SP_2;
                }
                boolean hover = Ui.button(g, s.font(), labels[k], bx, cy, bw, mx, my) && my >= top && my < top + listH;
                if (hover) {
                    s.tip(List.of(L.c(tips[k]).withStyle(ChatFormatting.GRAY)));
                }
                int by = Math.max(cy, top);
                int bb = Math.min(cy + Ui.BUTTON_H, top + listH);
                if (bb > by) {
                    s.hit(bx, by, bw, bb - by, acts[k]);
                }
                bx += bw + Theme.SP_2;
            }
            buttonExtra = cy - buttonsTop;      // the lines the buttons wrapped onto
        }
        g.disableScissor();
        leftBar.place(x + w - ScrollBar.W, top, listH, listH, total, leftScroll);
        leftBar.draw(g, mx, my);
    }

    private int leftHeight(HowMade.Found f) {
        if (f == null) {
            return Type.BODY.lineHeight;
        }
        long kinds = f.slots().stream().filter(i -> i != null && !i.empty()).map(Ingredient::name).distinct().count();
        int list = 2 * (Type.META.lineHeight + Theme.SP_1) + (int) kinds * (ROW + Theme.SP_1) + 2 * (ROW + Theme.SP_2) + Theme.SP_2
                + 1 + Theme.SP_2 + Type.LABEL.lineHeight;
        int grid = GRID_PAD * 2 + 3 * CELL + 2 * Theme.SP_1;
        list += Theme.SP_1 + Type.META.lineHeight;      // the second total
        return Math.max(grid, list) + Theme.SP_4 + Ui.BUTTON_H + buttonExtra;
    }

    /** A click on a row opens its item in Search (Esc there comes back here); clipped to the pane. */
    private void searchHit(String name, int x, int y, int w, int h, int top, int listH) {
        int hy = Math.max(y - 1, top);
        int hb = Math.min(y + h + 1, top + listH);
        if (hb > hy) {
            s.hit(x - Theme.SP_1, hy, w + Theme.SP_1 * 2, hb - hy,
                    () -> net.minecraft.client.Minecraft.getInstance().setScreen(RoadmapScreen.search(name, s)));
        }
    }

    private final Map<String, String> sources = new java.util.HashMap<>();

    /**
     * Where an ingredient is found besides the market ("Drops from Spiders, Lv 40-60", "From
     * Nest of the Grootslangs"), read once per name; null when the item list knows none.
     */
    private String sourceOf(String name) {
        if (sources.containsKey(name)) {
            return sources.get(name);
        }
        String text = null;
        try {
            io.github.anilken.wynnlabs.search.Entry e = io.github.anilken.wynnlabs.search.ItemIndex.get().byName(name);
            if (e != null) {
                for (io.github.anilken.wynnlabs.search.Source src : io.github.anilken.wynnlabs.search.Sources.of(e,
                        io.github.anilken.wynnlabs.search.ItemIndex.get().places(), io.github.anilken.wynnlabs.search.Sources.Owned.NONE,
                        null, SearchTab.position())) {
                    if (src.type() != io.github.anilken.wynnlabs.search.Source.Type.MARKET) {
                        String row = io.github.anilken.wynnlabs.search.SourceText.row(src, null);
                        if (row != null && row.contains("Dummy")) {
                            continue;                   // a training dummy is no place to get it
                        }
                        text = row;
                        break;
                    }
                }
            }
        } catch (Throwable t) {
            text = null;                        // no item list yet: no source line
        }
        sources.put(name, text);
        return text;
    }

    /** How far the action buttons wrapped below their first line, from the last frame. */
    private int buttonExtra;
    /** The last action done ("save", "link") and when, for its button's short confirmation. */
    private String doneWhat;
    private long doneAt;

    private RecipeCode.Recipe recipeOf(HowMade.Found f) {
        return new RecipeCode.Recipe(f.recipe().type(), f.recipe().minLevel(), f.tier1(), f.tier2(), Craft.Speed.NORMAL, f.names());
    }

    /** Saves the layout to the saved recipes under the item's name ("Scroll of Dusk", "Scroll of Dusk 2" ...). */
    private void saveRecipe(HowMade.Found f) {
        RecipeCode.Recipe r = recipeOf(f);
        io.github.anilken.wynnlabs.crafting.SavedRecipes saved = io.github.anilken.wynnlabs.crafting.Crafting.saved();
        String name = saved.free(target.name() == null || target.name().isBlank() ? CraftFormat.typeName(r.type()) : target.name());
        saved.save(new io.github.anilken.wynnlabs.crafting.SavedRecipes.Saved(name, r.type(), r.minLevel(), r.tier1(), r.tier2(),
                r.speed().name(), java.util.Collections.unmodifiableList(new ArrayList<>(r.slots())), null, System.currentTimeMillis()), false);
        io.github.anilken.wynnlabs.crafting.Crafting.persist();
        doneWhat = "save";
        doneAt = System.currentTimeMillis();
    }

    /** Copies the layout's WynnBuilder crafter link. */
    private void copyLink(HowMade.Found f) {
        String link = io.github.anilken.wynnlabs.crafting.WynnBuilderCode.link(recipeOf(f));
        if (link != null) {
            net.minecraft.client.Minecraft.getInstance().keyboardHandler.setClipboard(link);
            doneWhat = "link";
        } else {
            doneWhat = "nolink";                // WynnBuilder does not know an ingredient in it
        }
        doneAt = System.currentTimeMillis();
    }

    /**
     * One row of the list beside the grid: the picture, the name (and a material's stars), "×5"
     * and the price for the amount on the right ("?" when unknown); lit while hovered.
     */
    private void listRow(GuiGraphics g, ItemStack icon, String name, int amount, Long price, boolean est, String stars, int x, int y, int w,
                         boolean hover) {
        if (hover) {
            Art.box(g, x - Theme.SP_1, y - 1, w + Theme.SP_1 * 2, ROW + 2, 3, Theme.CARD_HOVER, 0);
        }
        CraftIcons.slot(g, x, y + (ROW - 12) / 2, 12, icon);
        String priceText = price == null ? "?" : (est ? "\u2248" : "") + Prices.fmt(price);
        String times = "×" + amount;
        String tail = (stars == null ? "" : " " + stars);
        int fixed = 12 + Theme.SP_2 + Theme.SP_3 + Type.META.width(s.font(), " " + times) + Type.BODY.width(s.font(), tail);
        if (price != null && Type.BODY.width(s.font(), name) > w - fixed - Type.META.width(s.font(), priceText)) {
            // a long name keeps its words: the price in one coin ("3.3LE") instead of two
            priceText = (est ? "\u2248" : "") + Prices.slot(price);
        }
        int pw = Type.META.width(s.font(), priceText);
        Type.META.drawRight(g, s.font(), priceText, x + w, Type.META.yIn(y, ROW), price == null ? Theme.INK_3 : Theme.PRICE);
        int room = w - fixed - pw;
        String fit = Type.BODY.fit(s.font(), name, Math.max(0, room));
        int tx = x + 12 + Theme.SP_2;
        Type.BODY.draw(g, s.font(), fit, tx, Type.BODY.yIn(y, ROW), Theme.INK);
        tx += Type.BODY.width(s.font(), fit);
        if (!tail.isEmpty()) {
            Type.BODY.draw(g, s.font(), tail, tx, Type.BODY.yIn(y, ROW), Theme.GOLD_EDGE);
            tx += Type.BODY.width(s.font(), tail);
        }
        Type.META.draw(g, s.font(), " " + times, tx, Type.META.yIn(y, ROW), Theme.INK_3);
    }

    /**
     * A material's tooltip as the game draws an item's: its name and stars, then the market's
     * gold box with its price at that tier and, for the amount, the grey "X5" box with the total;
     * "No listings now" when the market has none at that tier.
     */
    private void materialTip(GuiGraphics g, CraftData.Material mat, int tier, Long each, boolean est, int mx, int my) {
        net.minecraft.network.chat.Style plain = net.minecraft.network.chat.Style.EMPTY
                .withFont(net.minecraft.network.chat.FontDescription.DEFAULT);
        List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        lines.add(net.minecraft.network.chat.Component.literal(mat.shown()).withStyle(ChatFormatting.WHITE)
                .append(net.minecraft.network.chat.Component.literal(" " + "\u2605".repeat(tier)).withStyle(ChatFormatting.YELLOW)));
        lines.add(net.minecraft.network.chat.Component.empty());
        String word = L.t("wynnlabs.tooltip.market_banner");
        net.minecraft.network.chat.MutableComponent price = net.minecraft.network.chat.Component.empty()
                .append(io.github.anilken.wynnlabs.advice.TooltipListener.banner(word, 0xFFAA00));
        if (each == null) {
            price.append(net.minecraft.network.chat.Component.literal(" " + L.t("wynnlabs.box.made.no_listings"))
                    .setStyle(plain.withColor(ChatFormatting.GRAY)));
        } else {
            price.append(net.minecraft.network.chat.Component.literal(" ~" + Prices.glyphs(Prices.fmt(each)))
                    .setStyle(plain.withColor(ChatFormatting.GOLD)));
        }
        lines.add(price);
        if (est) {
            lines.add(net.minecraft.network.chat.Component.literal(L.t("wynnlabs.box.made.estimate")).setStyle(plain.withColor(ChatFormatting.GRAY)));
        }
        if (each != null && mat.amount() > 1) {
            lines.add(net.minecraft.network.chat.Component.empty()
                    .append(io.github.anilken.wynnlabs.advice.TooltipListener.banner("X" + mat.amount(), 0xAAAAAA))
                    .append(net.minecraft.network.chat.Component.literal(" \u2248 " + Prices.glyphs(Prices.fmt(each * mat.amount())))
                            .setStyle(plain.withColor(ChatFormatting.GOLD))));
        }
        ItemStack st = CraftIcons.of(mat.shown());
        // the game's font, for the banner boxes and the coin glyphs; the item's own frame when it has one
        g.setTooltipForNextFrame(net.minecraft.client.Minecraft.getInstance().font, lines,
                st == null ? java.util.Optional.empty() : st.getTooltipImage(), mx, my,
                st == null ? null : st.get(net.minecraft.core.component.DataComponents.TOOLTIP_STYLE));
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
        // the other layouts, one per set of ingredients: the same ones in another order or with
        // other material tiers make the same item, so only the cheapest of them is listed
        List<Integer> uniq = new ArrayList<>();
        Map<String, Integer> variants = new LinkedHashMap<>();
        Map<String, Integer> firstOf = new LinkedHashMap<>();
        if (a != null) {
            for (int i = 0; i < a.layouts().size(); i++) {
                String key = setKey(a.layouts().get(i));
                if (variants.merge(key, 1, Integer::sum) == 1) {
                    firstOf.put(key, i);
                    uniq.add(i);
                }
            }
        }
        int others = uniq.size();
        // many values matching fold into one line (the misses always show), so the other layouts
        // stay in view; a click on the line unfolds them
        long matching = rows.stream().filter(HowMade.Row::ok).count();
        boolean fold = !rowsOpen && matching > 3 && rows.size() > 5;
        int shownRows = fold ? (int) (rows.size() - matching) + 1 : rows.size();
        int total = shownRows * PITCH + (others > 1 ? Theme.SP_3 + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3 + others * PITCH : 0);
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
        if (fold) {
            int hy = cy;
            Art.icon(g, "check", x, Type.BODY.onCaps(cy, 8), 8, Theme.GOOD);
            String line = L.t("wynnlabs.box.made.rows_folded", matching, rows.size());
            boolean hov = BoxChips.in(mx, my, x, cy, inner, PITCH) && my >= top && my < top + listH;
            Type.BODY.draw(g, s.font(), Type.BODY.fit(s.font(), line + " ›", inner - 8 - Theme.SP_2), x + 8 + Theme.SP_2, cy,
                    hov ? Theme.LINK : Theme.INK_2);
            if (hy + PITCH > top && hy < top + listH) {
                s.hit(x, Math.max(hy, top), inner, Math.min(hy + PITCH, top + listH) - Math.max(hy, top), () -> rowsOpen = true);
            }
            cy += PITCH;
        }
        for (HowMade.Row r : rows) {
            if (fold && r.ok()) {
                continue;
            }
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
            String pickedKey = setKey(a.layouts().get(Math.max(0, Math.min(picked, a.layouts().size() - 1))));
            for (int u = 0; u < others; u++) {
                int i = uniq.get(u);
                HowMade.Found o = a.layouts().get(i);
                boolean on = setKey(o).equals(pickedKey);
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
                    int same = variants.getOrDefault(setKey(o), 1);
                    if (same > 1) {
                        tip.add(L.c("wynnlabs.box.made.same_set", same).withStyle(ChatFormatting.GRAY));
                    }
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

    /** Whether the matching values are unfolded (a click on their folded line). */
    private boolean rowsOpen;

    /** A layout's set of ingredients, whatever their order: "Bright Petal*3|Unicorn Horn*2". */
    private static String setKey(HowMade.Found f) {
        java.util.TreeMap<String, Integer> count = new java.util.TreeMap<>();
        for (Ingredient i : f.slots()) {
            if (i != null && !i.empty()) {
                count.merge(i.name(), 1, Integer::sum);
            }
        }
        StringBuilder b = new StringBuilder();
        count.forEach((n, c) -> b.append(n).append('*').append(c).append('|'));
        return b.toString();
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
