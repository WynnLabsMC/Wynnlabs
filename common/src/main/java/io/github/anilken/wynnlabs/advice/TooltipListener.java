package io.github.anilken.wynnlabs.advice;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Stamp;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.game.WynntilsEvents;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.MenuKind;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.ui.Art;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds the advice lines to tooltips that Wynntils builds itself.
 *
 * <p>For identified gear Wynntils replaces the vanilla tooltip with its own, so lines added
 * through Fabric's {@code ItemTooltipCallback} are lost there. Wynntils' tooltip event carries the
 * final list ({@link WynntilsEvents} hands it here), so appending at the lowest priority puts the
 * advice under whatever Wynntils (or another mod) drew. Boxes and other items keep the Fabric
 * lines, so it checks for them first.
 */
public final class TooltipListener {

    /** The head of every advice line ("✦ Öneri: " / "✦ Advice: "), in the game's language. */
    public static String marker() {
        return L.t("wynnlabs.tooltip.marker");
    }

    /**
     * A pulsing glow behind ability tree nodes and menu buttons that have advice, so the
     * recommended ones stand out in the tree, and a gold one behind shopping list gear in shops,
     * the Trade Market and chests. Your own gear is left alone: Wynntils already tints it.
     */
    /**
     * What a slot draws for one stack, worked out once and reused: the red "cannot wear" frame,
     * the glow colour, the tree step number, and the badge (a word for boxes, a price for
     * identified gear). Rebuilt when the rules, settings or advice change, when the stack moves
     * between the bag and a menu, and about every half second (staggered per stack) for what
     * is not tracked (level, prices, worn gear). Client thread only.
     */
    private record SlotFacts(long until, int rules, int advice, boolean inBag, boolean cannot, int glow,
                             int step, int stepColour) {}

    /** The badge part; also drawn by the J menu preview without a slot, and on the hotbar. */
    public record BadgeFacts(long until, int rules, int advice, Component word, int wordWidth, float wordScale,
                              int colour, String where, Component price, int priceWidth, boolean priceTop) {}

    private static final java.util.WeakHashMap<net.minecraft.world.item.ItemStack, SlotFacts> SLOT_FACTS = new java.util.WeakHashMap<>();
    private static final java.util.WeakHashMap<net.minecraft.world.item.ItemStack, BadgeFacts> BADGE_FACTS = new java.util.WeakHashMap<>();
    private static final int[][] OUTLINE = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

    /** How long a stack's drawing facts live: 500 ms plus up to 250 ms, different per stack. */
    private static long lifetime(Object stack) {
        return 500 + (System.identityHashCode(stack) & 255);
    }

    /** Nothing known yet: drawn plain until its turn comes. */
    private static final SlotFacts NO_FACTS = new SlotFacts(0, 0, 0, false, false, -1, 0, 0);
    private static long workWindow;
    private static int workedNow;

    /** How many slots are worked out in one frame (8 ms) at most. A method: hot swaps keep no field values. */
    static int slotsPerFrame() {
        return 8;
    }

    private static SlotFacts slotFacts(Slot slot) {
        var stack = slot.getItem();
        var mc = Minecraft.getInstance();
        boolean inBag = mc.player != null && slot.container == mc.player.getInventory();
        long now = System.currentTimeMillis();
        SlotFacts f = SLOT_FACTS.get(stack);
        if (f != null && now < f.until() && f.rules() == Stamp.now() && f.inBag() == inBag) {
            return f;
        }
        // a menu just opened has every slot to work out: a few a frame, the rest the frames after
        // (an old one is drawn meanwhile, or none), so opening the bank or the market never stalls
        long nano = System.nanoTime();
        if (nano - workWindow > 8_000_000L) {
            workWindow = nano;
            workedNow = 0;
        }
        if (workedNow >= slotsPerFrame()) {
            return f != null ? f : NO_FACTS;
        }
        workedNow++;
        int rules = Stamp.now();
        int advice = Advice.version;
        long t0 = io.github.anilken.wynnlabs.core.Perf.start();
        boolean cannot = Settings.on("cannotWear") && cannotWear(stack);
        io.github.anilken.wynnlabs.core.Perf.end("slot.cannot", t0);
        long t1 = io.github.anilken.wynnlabs.core.Perf.start();
        int glow = glowOf(stack, inBag);
        io.github.anilken.wynnlabs.core.Perf.end("slot.glow", t1);
        f = new SlotFacts(now + lifetime(stack), rules, advice, inBag, cannot, glow, stepOf(stack), stepColourOf(stack));
        SLOT_FACTS.put(stack, f);
        return f;
    }

    /** Gear you cannot wear (level, class, skill points, a quest). */
    private static boolean cannotWear(net.minecraft.world.item.ItemStack reqStack) {
        var reqGear = reqStack.isEmpty() ? java.util.Optional.<ItemView>empty()
                : WynntilsItems.view(reqStack).filter(ItemView::isGear);
        if (reqGear.isEmpty() || !Settings.on("itemAdvice")) {
            return false;
        }
        var info = reqGear.get().data();
        var req = info.requirements();
        boolean cannot = req.level() > Wyn.level();
        var needCls = req.klass().orElse(info.slot().klass());
        if (!cannot && needCls != null && needCls != Klass.NONE
                && needCls != Wyn.classType()) {
            cannot = true;
        }
        // the game's own checkmarks under the item (Wynntils reads them): the truth for skill points
        var inst = reqGear.get().rolled();
        var marks = inst != null ? inst.marks() : null;
        if (marks != null && marks.known()) {
            cannot = !marks.all();
        }
        if (!cannot && req.quest().isPresent()) {
            for (QuestBook.Entry q : QuestBook.all()) {
                if (q.name().equalsIgnoreCase(req.quest().get()) && !"COMPLETED".equals(q.status())) {
                    cannot = true;
                    break;
                }
            }
        }
        return cannot;
    }

    /** The glow colour behind the slot, or -1 for none. */
    private static int glowOf(net.minecraft.world.item.ItemStack stack, boolean inBag) {
        var mc = Minecraft.getInstance();
        int shopGlow = -1;
        Advice.Entry e = Advice.find(stack, k -> k.startsWith("ability:") || k.startsWith("name:"));
        if (e != null) {
            if (!(Settings.choice("treeHelp") == 2) || e.verdict() == Verdict.TAKEN) {
                return -1;                          // a taken step keeps only its number
            }
            return Advice.rgb(e.verdict());
        } else if (isSkillButton(stack)) {
            if (!(Settings.choice("treeHelp") == 2)) {
                return -1;
            }
            Advice.Entry auto = AutoAdvice.cached(stack);
            return auto == null ? -1 : Advice.rgb(auto.verdict());
        }
        long tq = io.github.anilken.wynnlabs.core.Perf.start();
        boolean sell = mc.player != null && inBag && Settings.on("sellGlow") && sellHere(stack) != 0;
        io.github.anilken.wynnlabs.core.Perf.end("glow.sell", tq);
        tq = io.github.anilken.wynnlabs.core.Perf.start();
        boolean up = !sell && mc.player != null && inBag && Settings.on("glowUpgrade") && upgrade(stack);
        io.github.anilken.wynnlabs.core.Perf.end("glow.upgrade", tq);
        tq = io.github.anilken.wynnlabs.core.Perf.start();
        boolean shop = !sell && !up && mc.player != null && !inBag && Settings.on("glowShop") && onShoppingList(stack);
        io.github.anilken.wynnlabs.core.Perf.end("glow.shop", tq);
        if (sell) {
            return sellHere(stack);               // at the Blacksmith / Trade Market: what to sell here
        } else if (up) {
            return 0x55FF55;                      // green: wear this, it beats what you have on
        } else if (shop && (shopGlow = marketGlow(stack)) >= 0) {
            return shopGlow;                      // gold: on the shopping list (in the Trade Market the best for you; teal the best value)
        }
        tq = io.github.anilken.wynnlabs.core.Perf.start();
        boolean quest = mc.player != null && !inBag && Settings.on("glowQuest") && QuestAdvisor.rankOf(stack) >= 0;
        io.github.anilken.wynnlabs.core.Perf.end("glow.quest", tq);
        return quest ? 0x55FFFF : -1;             // aqua: a recommended quest in the content book
    }

    /** The plan's step on an ability tree node, or 0. */
    private static int stepOf(net.minecraft.world.item.ItemStack stack) {
        Advice.Entry e = Advice.find(stack, k -> k.startsWith("ability:"));
        if (e == null || e.verdict() != Verdict.BUY && e.verdict() != Verdict.BUY_LATER && e.verdict() != Verdict.TAKEN) {
            return 0;
        }
        return Advice.planStep(e);
    }

    private static int stepColourOf(net.minecraft.world.item.ItemStack stack) {
        Advice.Entry e = Advice.find(stack, k -> k.startsWith("ability:"));
        if (e == null) {
            return 0;
        }
        return e.verdict() == Verdict.BUY ? 0xFFFFE071 : e.verdict() == Verdict.TAKEN ? 0xFFB4B2A9 : 0xFFF3E6C4;
    }

    /**
     * A pulsing glow behind ability tree nodes and menu buttons that have advice, so the
     * recommended ones stand out in the tree, and a gold one behind shopping list gear in shops,
     * the Trade Market and chests. Your own gear is left alone: Wynntils already tints it.
     */
    public static void onSlot(GuiGraphics graphics, Slot slot) {
        if (!Rules.adviceHere() || slot.getItem().isEmpty()) {
            return;
        }
        try {
            SlotFacts f = slotFacts(slot);
            // gear you cannot wear (level, class, skill points, a quest): a red slot with a red frame,
            // everywhere (bag, shops, Trade Market, chests), so it is not bought or kept by mistake
            if (f.cannot()) {
                GuiGraphics rg = graphics;
                int rx = slot.x;
                int ry = slot.y;
                rg.fill(rx, ry, rx + 16, ry + 16, 0x55E24B4A);
                rg.fill(rx - 1, ry - 1, rx + 17, ry, 0xFFE24B4A);
                rg.fill(rx - 1, ry + 16, rx + 17, ry + 17, 0xFFE24B4A);
                rg.fill(rx - 1, ry, rx, ry + 16, 0xFFE24B4A);
                rg.fill(rx + 16, ry, rx + 17, ry + 16, 0xFFE24B4A);
            }
            int rgb = f.glow();
            if (rgb < 0) {
                return;
            }
            double t = (System.currentTimeMillis() % 1600L) / 1600.0;
            float pulse = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2));
            GuiGraphics g = graphics;
            int x = slot.x;
            int y = slot.y;
            // soft halo: a wider faint ring, then the brighter slot fill
            int halo = ((int) (40 + 60 * pulse) << 24) | rgb;
            int fill = ((int) (90 + 110 * pulse) << 24) | rgb;
            g.fill(x - 2, y - 2, x + 18, y - 1, halo);
            g.fill(x - 2, y + 17, x + 18, y + 18, halo);
            g.fill(x - 2, y - 1, x - 1, y + 17, halo);
            g.fill(x + 17, y - 1, x + 18, y + 17, halo);
            g.fill(x - 1, y - 1, x + 17, y + 17, fill);
        } catch (Throwable ignored) {
            // drawing a glow must never break the menu
        }
    }

    /**
     * After the item is drawn, on your own items (bag and an open bank): the verdict's word
     * ("SAT", "SAKLA", "TANIMLA" …) in small type on a dark strip along the slot's bottom, in
     * the verdict's colour, so it reads at a glance without hovering.
     */
    private static void verdictBadge(GuiGraphics g, Slot slot) {
        var stack = slot.getItem();
        var mc = Minecraft.getInstance();
        if (stack.isEmpty() || mc.player == null) {
            return;
        }
        // other players' Trade Market listings are things to buy, not yours to sell: no verdict on them
        MenuKind kind = Wyn.container();
        if (kind != null && kind.type().startsWith("TradeMarket") && !kind.marketSell()
                && slot.container != mc.player.getInventory()) {
            return;
        }
        // every menu: the bag, the bank, chests, daily rewards, loot chests, shops
        drawBadge(g, slot.x, slot.y, stack);
    }

    public static BadgeFacts badgeFacts(net.minecraft.world.item.ItemStack stack) {
        long now = System.currentTimeMillis();
        BadgeFacts f = BADGE_FACTS.get(stack);
        if (f != null && now < f.until() && f.rules() == Stamp.now()) {
            return f;
        }
        int rules = Stamp.now();
        int advice = Advice.version;
        f = buildBadge(stack, now + lifetime(stack), rules, advice);
        BADGE_FACTS.put(stack, f);
        return f;
    }

    private static BadgeFacts buildBadge(net.minecraft.world.item.ItemStack stack, long until, int rules, int advice) {
        BadgeFacts none = new BadgeFacts(until, rules, advice, null, 0, 0, 0, null, null, 0, false);
        if (!Settings.on("itemAdvice")) {           // verdictBadges is the menus' own switch: drawBadge
            return none;
        }
        var view = GameState.current().view(stack);
        if (view.isEmpty()) {
            return none;
        }
        var font = Minecraft.getInstance().font;
        boolean box = view.get().isBox() || view.get().isGear() && view.get().unidentified();
        if (!box) {
            // an identified piece: its Trade Market price only, small and green, bottom-right
            if (view.get().isGear()) {
                Prices.Price pr = GameState.current().price(view.get().data().name());
                Double typical = pr == null ? null : pr.typical();
                if (typical != null) {
                    Component c = styled(Prices.slot(typical));
                    return new BadgeFacts(until, rules, advice, null, 0, 0, 0, null, c, font.width(c), false);
                }
            }
            // an ingredient: its price each, top-left (the stack count has the bottom-right, the tier stars the top-right)
            if (view.get().kind() == ItemView.Kind.INGREDIENT && view.get().name() != null && Settings.on("tooltipPrice")) {
                String name = view.get().name();
                Prices.Price pr = GameState.current().price(name);
                if (pr == null) {
                    GameState.current().requestPrices(List.of(name));
                } else if (pr.typical() != null) {
                    Component c = styled(Prices.slot(pr.typical()));
                    return new BadgeFacts(until, rules, advice, null, 0, 0, 0, null, c, font.width(c), true);
                }
            }
            return none;
        }
        // a box or unidentified piece: what to do with it, and where to sell it
        Advice.Entry e = Advice.find(stack, k -> !k.startsWith("ability:") && !k.startsWith("name:"));
        if (e == null) {
            e = AutoAdvice.cached(stack);
        }
        if (e == null || e.verdict() == null || e.verdict() == Verdict.NOTE) {
            return none;
        }
        // a short word that fits the slot at two-thirds size: SAT, TUT, TANI ...
        String word = L.t("wynnlabs.badge." + e.verdict().name().toLowerCase(java.util.Locale.ROOT));
        // a sale says where: the Trade Market in gold, the Blacksmith in red, and the same
        // colour behind a small icon bottom-left (cart / anvil)
        String where = null;
        if (e.verdict() == Verdict.SELL && e.note() != null) {
            String place = Advice.sellPlace(e);
            where = "Blacksmith".equals(place) ? "hammer" : "Trade Market".equals(place) ? "shopping-cart" : null;
            // the tooltip already says SELL, so the slot names the place instead: BS / TM
            if (where != null) {
                word = L.t("hammer".equals(where) ? "wynnlabs.badge.sell_blacksmith" : "wynnlabs.badge.sell_market");
            }
        }
        int colour = 0xFF000000 | ("shopping-cart".equals(where) ? 0xFFC23A : e.verdict().rgb());
        int tw = font.width(word);
        Component c = styled(word);
        // an unidentified piece also shows what it sells for unidentified, bottom-right
        Component price = null;
        if (view.get().isGear() && view.get().data() != null) {
            Prices.Price pr = GameState.current().price(view.get().data().name());
            Double unid = pr == null ? null : pr.unidAvg80() != null ? pr.unidAvg80()
                    : pr.unidLowest() == null ? null : (double) pr.unidLowest();
            if (unid != null && unid > 0) {
                price = styled(Prices.slot(unid));
            }
        }
        return new BadgeFacts(until, rules, advice, c, font.width(c), tw * 2f / 3f <= 18 ? 2f / 3f : 16f / tw, colour, where,
                price, price == null ? 0 : font.width(price), false);
    }

    private static Component styled(String text) {
        return Component.literal(text).withStyle(st -> st.withBold(false));
    }

    /** The word and the where-to-sell icon on a slot at x, y (the J menu preview draws them too). */
    public static void drawBadge(GuiGraphics g, int x, int y, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || !(Settings.choice("slotBadges") >= 1)) {
            return;
        }
        BadgeFacts f = badgeFacts(stack);
        var font = Minecraft.getInstance().font;
        if (f.price() != null) {
            g.nextStratum();
            if (f.priceTop()) {
                outlined(g, font, f.price(), f.priceWidth(), x - 1, y - 1, Math.min(0.42f, 17f / Math.max(1, f.priceWidth())),
                        0xFF55FF55, false);
            } else {
                outlined(g, font, f.price(), f.priceWidth(), x + 17, y + 16, 0.42f, 0xFF55FF55, true);
            }
            if (f.word() == null) {
                return;                         // a price alone (identified gear, an ingredient)
            }
        }
        if (f.word() == null) {
            return;
        }
        // items are drawn at the end of their layer, over anything drawn with them: a layer of
        // our own on top, so the item picture (and the pack's overlay on it) stays under the word
        g.nextStratum();
        // top-left, like Wynntils' tier numerals: the game's own font, outlined,
        // two-thirds size (sharp at GUI scale 3)
        outlined(g, font, f.word(), f.wordWidth(), x - 1, y - 1, f.wordScale(), f.colour(), false);
        if (f.where() != null) {
            // a dark disc with the icon in the word's colour: reads on any item
            Art.box(g, x - 1, y + 8, 9, 9, 4, 0xE0101010, f.colour());
            Art.icon(g, f.where(), x, y + 9, 7, f.colour());
        }
    }

    /**
     * Text at a scale with a dark outline all round (a drop shadow sits off the letters when
     * scaled). {@code rightBottom}: x, y are the bottom-right corner, else the top-left.
     */
    public static void outlined(GuiGraphics g, net.minecraft.client.gui.Font font, Component c, int w, float x, float y,
            float scale, int colour, boolean rightBottom) {
        float s = rightBottom ? Math.min(scale, 17f / Math.max(1, w)) : scale;
        g.pose().pushMatrix();
        g.pose().translate(rightBottom ? x - w * s : x, rightBottom ? y - 8 * s : y);
        g.pose().scale(s, s);
        for (int[] d : OUTLINE) {
            g.drawString(font, c, d[0], d[1], 0xFF1A0A0A, false);
        }
        g.drawString(font, c, 0, 0, colour, false);
        g.pose().popMatrix();
    }

    public static void onSlotAfter(GuiGraphics g, Slot slot) {
        if (slot.getItem().isEmpty()) {
            return;
        }
        if (Rules.adviceHere()) {
            try {
                verdictBadge(g, slot);
            } catch (Throwable ignored) {
                // a badge must never break the menu
            }
        }
        // the tree's nodes fill the menu's first 6 rows (9 x 6 = 54 slots); the archetype and
        // point icons below carry node names too, and get no number
        if (!Rules.adviceHere() || !(Settings.choice("treeHelp") == 2) || slot.index >= 54
                || Minecraft.getInstance().player != null && slot.container == Minecraft.getInstance().player.getInventory()) {
            return;
        }
        SlotFacts f = slotFacts(slot);
        int step = f.step();
        if (step <= 0) {
            return;
        }
        g.nextStratum();                            // over the node's picture
        var font = Minecraft.getInstance().font;
        String s = String.valueOf(step);
        int w = font.width(s);
        int x = slot.x + 17 - w;
        int y = slot.y - 2;
        // the HUD's dark pill, round, under the number
        Art.box(g, x - 2, y - 1, w + 3, 10, 3, io.github.anilken.wynnlabs.ui.Hud.PANEL, 0);
        g.drawString(font, s, x, y, f.stepColour(), true);
    }

    private static boolean isSkillButton(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var p = WynntilsItems.view(stack);
        return p.isPresent() && p.get().kind() == ItemView.Kind.SKILL_POINT;
    }

    private static boolean upgrade(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Advice.Entry e = AutoAdvice.cached(stack);
        return e != null && (e.verdict() == Verdict.WEAR || e.verdict() == Verdict.SPEND);
    }

    /**
     * In the Blacksmith's sell menu: red for what the rules sell to the Blacksmith. In the Trade
     * Market's sell menu: gold for what they sell there. 0 elsewhere or for anything else.
     */
    private static int sellHere(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        MenuKind c = Wyn.container();
        boolean smith = c != null && c.blacksmithSell();
        boolean market = c != null && c.marketSell();
        if (!smith && !market) {
            return 0;
        }
        Advice.Entry e = AutoAdvice.cached(stack);
        String where = Advice.sellPlace(e);
        if (smith && "Blacksmith".equals(where)) {
            return 0xFF5555;
        }
        if (market && "Trade Market".equals(where)) {
            return 0xFFAA00;
        }
        return 0;
    }

    /** How many of the cheapest and how many of the best rolled listings glow. A method: hot swaps keep no field values. */
    static int marketPicks() {
        return 3;
    }

    /** The glow of a listing that is the best for you (gold) or the best value (teal). */
    static final int GOLD = 0xFFAA00;
    static final int TEAL = 0x5FC9B0;

    /** A listing's price over the market's usual price above which it never glows. A method: hot swaps keep no field values. */
    static double tooDear() {
        return 1.3;
    }

    /** Whether the screen open is one of the Trade Market's buy screens. */
    static boolean inMarket() {
        MenuKind kind = Wyn.container();
        return kind != null && !kind.marketSell() && kind.type().startsWith("TradeMarket");
    }

    /** Set when the score's class is missing (a hot swap left it out): the rolls alone then. */
    private static boolean noWeights;

    /** A listing's change to your build score with it in its slot, in %; NaN unknown. */
    static double forYou(ItemView v, net.minecraft.world.item.ItemStack st) {
        if (v == null || v.rolled() == null || noWeights) {
            return Double.NaN;
        }
        try {
            var sc = io.github.anilken.wynnlabs.build.StatWeights.of(v, st);
            return sc == null ? Double.NaN : sc.gain();
        } catch (LinkageError e) {
            noWeights = true;                       // not in this game's jar (a hot swap): never tried again
        } catch (Throwable ignored) {
            // the model failed: no rank
        }
        return Double.NaN;
    }

    /**
     * Outside the Trade Market a shopping list item glows gold. In it, of the listings on screen of
     * one item, the {@link #marketPicks} best for you glow gold and the {@link #marketPicks} with
     * the most of it per emerald teal; a listing far above the usual price ({@link #tooDear})
     * never. -1: no glow.
     */
    private static int marketGlow(net.minecraft.world.item.ItemStack stack) {
        // the ranking only with its switch on (setting marketFit): a plain gold glow otherwise
        if (!inMarket() || !Settings.on("marketFit") || !(Minecraft.getInstance().screen
                instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
            return GOLD;
        }
        long now = System.currentTimeMillis();
        if (screen != rankedScreen || now - rankedAt > 500) {
            long t = io.github.anilken.wynnlabs.core.Perf.start();
            rank(screen);                               // the page once every half second, not once per slot
            io.github.anilken.wynnlabs.core.Perf.end("slot.rank", t);
            rankedScreen = screen;
            rankedAt = now;
        }
        Integer c = RANKED.get(stack);
        return c == null ? -1 : c;
    }

    /** The page's listings ranked: each listing's glow colour, worked out once for the whole page. */
    private static final java.util.Map<net.minecraft.world.item.ItemStack, Integer> RANKED = new java.util.IdentityHashMap<>();
    private static Object rankedScreen;
    private static long rankedAt;

    private static void rank(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
        RANKED.clear();
        var mine = Minecraft.getInstance().player.getInventory();
        // listings by item: {stack, price, score}
        java.util.Map<String, List<Object[]>> byItem = new java.util.HashMap<>();
        for (Slot sl : screen.getMenu().slots) {
            var st = sl.getItem();
            if (sl.container == mine || st.isEmpty()) {
                continue;
            }
            var v = WynntilsItems.view(st).orElse(null);
            String name = v == null ? null : v.gearData().map(ItemData::name).orElse(null);
            if (name == null) {
                continue;
            }
            byItem.computeIfAbsent(name, k -> new ArrayList<>()).add(new Object[] {st, Wyn.marketListingPrice(st), forYou(v, st)});
        }
        int n = marketPicks();
        for (var e : byItem.entrySet()) {
            Prices.Price known = Prices.of(e.getKey());
            Double usual = known == null ? null : known.typical();
            List<Object[]> same = e.getValue().stream()
                    .filter(o -> !((int) o[1] > 0 && usual != null && usual > 0 && (int) o[1] > usual * tooDear()))
                    .toList();
            same.stream().filter(o -> !Double.isNaN((double) o[2]) && (double) o[2] > 0)
                    .sorted(java.util.Comparator.comparingDouble(o -> -(double) o[2])).limit(n)
                    .forEach(o -> RANKED.put((net.minecraft.world.item.ItemStack) o[0], GOLD));
            same.stream().filter(o -> (double) o[2] > 0 && (int) o[1] > 0 && !RANKED.containsKey(o[0]))
                    .sorted(java.util.Comparator.comparingDouble(o -> -(double) o[2] / (int) o[1])).limit(n)
                    .forEach(o -> RANKED.put((net.minecraft.world.item.ItemStack) o[0], TEAL));
        }
    }

    /**
     * A Trade Market listing's worth for you, under the price, in the game's own banner: "[FIT]
     * +12% · Ritualist" (your build score with it in its slot), then the two stats with the
     * biggest part of that ("Aura Cost +4.1 · Air +3.2").
     */
    private static void forYouLines(ItemView g, net.minecraft.world.item.ItemStack stack, List<Component> out, int at) {
        if (noWeights) {
            return;
        }
        io.github.anilken.wynnlabs.build.StatWeights.Score sc;
        try {
            sc = io.github.anilken.wynnlabs.build.StatWeights.of(g, stack);
        } catch (LinkageError e) {
            noWeights = true;
            return;
        } catch (Throwable t) {
            return;
        }
        // nothing to say when it gains you nothing (0 %) or less: no line at all
        if (sc == null || Double.isNaN(sc.gain()) || Math.round(sc.gain()) <= 0) {
            return;
        }
        String gain = (sc.gain() >= 0 ? "+" : "") + String.format(java.util.Locale.ROOT, "%.0f%%", sc.gain());
        String arch = io.github.anilken.wynnlabs.build.TreePlanner.chosenLabel(io.github.anilken.wynnlabs.build.TreePlanner.mostUnlocked());
        List<Component> lines = new ArrayList<>();
        String word = L.t("wynnlabs.tooltip.for_you_banner");
        lines.add(Component.empty().append(banner(word.startsWith("wynnlabs.") ? "FIT" : word, 0xF2C94C))
                .append(Component.literal(" " + L.t("wynnlabs.tooltip.for_you", gain, arch == null || arch.isBlank() ? "-" : arch))
                        .setStyle(net.minecraft.network.chat.Style.EMPTY
                                .withColor(sc.gain() > 0 ? net.minecraft.ChatFormatting.YELLOW : net.minecraft.ChatFormatting.GRAY)
                                .withFont(net.minecraft.network.chat.FontDescription.DEFAULT))));
        List<String> parts = new ArrayList<>();
        sc.stats().stream().filter(st -> st.part() >= 0.5).limit(2)
                .forEach(st -> parts.add(shortStat(st.name()) + " +" + String.format(java.util.Locale.ROOT, "%.1f", st.part())));
        if (!parts.isEmpty()) {
            lines.add(Component.literal(String.join(" · ", parts)).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        lines.add(Component.empty());
        out.addAll(Math.max(0, Math.min(at, out.size())), lines);
    }

    /** A stat's name kept short for the tooltip: "Fire Damage" is "Fire", "Water Defence" "Water Def". */
    static String shortStat(String name) {
        // "3rd Spell Cost" as the game words it on the item: the class's own spell ("Aura Cost")
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(1st|2nd|3rd|4th) Spell Cost$").matcher(name);
        if (m.find()) {
            int n = "1st2nd3rd4th".indexOf(m.group(1)) / 3 + 1;
            var model = io.github.anilken.wynnlabs.core.Rules.get().getAsJsonObject("model");
            if (model != null && model.has("spells")) {
                for (var e : model.getAsJsonArray("spells")) {
                    var o = e.getAsJsonObject();
                    if (o.has("index") && o.get("index").getAsInt() == n && o.has("name")) {
                        return o.get("name").getAsString() + " Cost";
                    }
                }
            }
        }
        return name.replace(" Damage", "").replace(" Defence", " Def").replace("Spell Cost", "Cost");
    }

    private static boolean onShoppingList(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        var p = WynntilsItems.view(stack);
        return p.flatMap(ItemView::gearData).map(d -> Shopping.find(d.name()) != null).orElse(false);
    }

    /** The tooltip with the mod's lines added, or null to leave it as it is. */
    /**
     * The final tooltip for a stack, kept a quarter second: Wynntils asks every frame, and adding
     * the price lines reads every line's text. Rebuilt at once when the stamp (rules, prices,
     * advice, what you carry) or Shift changes, or when Wynntils hands a different number of lines.
     */
    private record Tip(long until, int stamp, boolean shift, int inSize, List<Component> out) {}

    private static final java.util.WeakHashMap<net.minecraft.world.item.ItemStack, Tip> TIPS = new java.util.WeakHashMap<>();

    public static List<Component> onTooltip(net.minecraft.world.item.ItemStack stack, List<Component> lines) {
        long now = System.currentTimeMillis();
        int stamp = Stamp.now();
        boolean shift = Minecraft.getInstance().hasShiftDown();
        Tip t = TIPS.get(stack);
        if (t != null && now < t.until() && t.stamp() == stamp && t.shift() == shift && t.inSize() == lines.size()) {
            return t.out() == null ? null : new ArrayList<>(t.out());
        }
        List<Component> out = buildTooltip(stack, lines);
        TIPS.put(stack, new Tip(now + 250 + (System.identityHashCode(stack) & 127), stamp, shift, lines.size(),
                out == null ? null : List.copyOf(out)));
        return out;
    }

    private static List<Component> buildTooltip(net.minecraft.world.item.ItemStack stack, List<Component> lines) {
        try {
            debugDump(stack, lines);
            List<Component> out = new ArrayList<>(lines);
            boolean changed = withPrices(stack, out);
            boolean present = false;
            String marker = marker();
            for (Component line : lines) {
                String text = line.getString();
                if (text.contains(marker) || text.startsWith("◆ ") || text.startsWith("▣ ")) {
                    present = true;               // already there via ItemTooltipCallback
                    break;
                }
            }
            if (!present) {
                // only when the Fabric hook's lines are not there already: Wynntils' own tooltips
                List<Component> advice = Advice.linesCached(stack);
                if (!advice.isEmpty()) {
                    out.addAll(advice);
                    changed = true;
                }
            }
            // a feed material: what it would give the mounts last seen in the Mount Feeder
            List<Component> mounts = io.github.anilken.wynnlabs.ui.MountTips.lines(stack);
            if (!mounts.isEmpty()) {
                out.addAll(mounts);
                changed = true;
            }
            changed |= bannerHeads(out);
            if (Prices.coins) {
                // prices are written with letters; the tooltip's font has the game's coin glyphs
                for (int i = 0; i < out.size(); i++) {
                    Component line = out.get(i);
                    String text = line.getString();
                    if (!Prices.glyphs(text).equals(text)) {
                        out.set(i, glyphs(line));
                        changed = true;
                    }
                }
            }
            return changed ? out : null;
        } catch (Throwable ignored) {
            // a broken advice line must never break the tooltip
            return null;
        }
    }

    private static final String PRICE_TAG = "◇ ";

    /**
     * A word in a coloured box, as Wynncraft draws "LE" and "CURRENCY" under an item's name: in
     * its banner font, a left cap, a cell per letter, a right cap, a step back over them, then the
     * letters on top in black. Letters A-Z only (Turkish ones are written plain).
     */
    public static net.minecraft.network.chat.MutableComponent banner(String word, int box) {
        String plain = word.toUpperCase(java.util.Locale.ROOT).replace('Ç', 'C').replace('Ğ', 'G').replace('İ', 'I')
                .replace('Ö', 'O').replace('Ş', 'S').replace('Ü', 'U');
        StringBuilder back = new StringBuilder().append('');
        StringBuilder front = new StringBuilder();
        int span = 0;                               // the cells' width: 6 a letter or digit, 4 for the narrow I and 1
        for (char c : plain.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                back.appendCodePoint(0xcffff).append((char) (0xe030 + c - 'A'));
                front.append((char) (0xe000 + c - 'A'));
                span += c == 'I' ? 4 : 6;
            } else if (c >= '0' && c <= '9') {
                // the digits sit in the font's third row, their cells in the seventh
                back.appendCodePoint(0xcffff).append((char) (0xe050 + c - '0'));
                front.append((char) (0xe020 + c - '0'));
                span += c == '1' ? 4 : 6;
            }
        }
        back.appendCodePoint(0xcffff).append('').appendCodePoint(0xd0000 - (span + 2));
        front.appendCodePoint(0xd0002);
        return Component.literal(back.toString())
                .setStyle(net.minecraft.network.chat.Style.EMPTY.withFont(new net.minecraft.network.chat.FontDescription.Resource(
                        net.minecraft.resources.Identifier.withDefaultNamespace("banner/box"))).withColor(box).withItalic(false))
                .append(Component.literal(front.toString())
                        .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(0x000000).withShadowColor(0xFFFFFF)));
    }

    /** The component with its prices' coins as glyphs ({@link Prices#glyphs}), styles kept. */
    private static Component glyphs(Component c) {
        net.minecraft.network.chat.MutableComponent out =
                c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents.LiteralContents lit
                        ? Component.literal(Prices.glyphs(lit.text())).setStyle(c.getStyle())
                        : c.plainCopy().setStyle(c.getStyle());
        for (Component sib : c.getSiblings()) {
            out.append(glyphs(sib));
        }
        return out;
    }

    /**
     * The advice head lines ("✦ Advice: KEEP → Trade Market") as banners: a pink "ADVICE" box,
     * the verdict in a box of its colour, the rest as it was. Other lines are left alone; true
     * when one changed.
     */
    private static boolean bannerHeads(List<Component> out) {
        String marker = marker();
        boolean changed = false;
        for (int i = 0; i < out.size(); i++) {
            Component c = out.get(i);
            if (c.getSiblings().isEmpty() || !(c.getContents() instanceof net.minecraft.network.chat.contents.PlainTextContents.LiteralContents lit)
                    || !lit.text().equals(marker)) {
                continue;
            }
            Component verdict = c.getSiblings().get(0);
            var colour = verdict.getStyle().getColor();
            String word = L.t("wynnlabs.tooltip.advice_banner");
            var line = Component.empty().append(banner(word.startsWith("wynnlabs.") ? "ADVICE" : word, 0xFF55FF));
            for (String part : verdict.getString().strip().split("\s+")) {   // "BUY LATER": a box a word
                line.append(Component.literal(" ")).append(banner(part, colour == null ? 0xFFFFFF : colour.getValue()));
            }
            for (int k = 1; k < c.getSiblings().size(); k++) {
                line.append(c.getSiblings().get(k));
            }
            out.set(i, line);
            changed = true;
        }
        return changed;
    }
    private static long debugCheckedAt;
    private static boolean debugOn;
    private static String lastDumped = "";
    private static final java.util.concurrent.ExecutorService DUMPER =
            io.github.anilken.wynnlabs.core.Workers.singleLow("tooltip-dump");

    /**
     * While the Debug setting is on, writes the lines of the last hovered item
     * (text, font and the component JSON) to tooltip-debug.txt, to see how a tooltip is built.
     */
    private static void debugDump(net.minecraft.world.item.ItemStack stack, List<Component> lines) {
        try {
            var dir = Dirs.config();
            long now = System.currentTimeMillis();
            if (now - debugCheckedAt > 5000) {
                debugCheckedAt = now;
                debugOn = Settings.on("debug");
            }
            if (!debugOn) {
                return;
            }
            String name = stack.getHoverName().getString();
            if (name.equals(lastDumped)) {
                return;
            }
            lastDumped = name;
            // the JSON and the file on a worker: a frame never waits for the disk
            List<Component> copy = List.copyOf(lines);
            var ops = Minecraft.getInstance().level == null ? null
                    : Minecraft.getInstance().level.registryAccess().createSerializationContext(
                            com.mojang.serialization.JsonOps.INSTANCE);
            DUMPER.execute(() -> {
                try {
                    StringBuilder sb = new StringBuilder("item: " + name + "\n");
                    for (int i = 0; i < copy.size(); i++) {
                        Component c = copy.get(i);
                        sb.append(i).append(" | ").append(c.getString().replace("\n", "\\n")).append('\n');
                        if (ops != null) {
                            var json = net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(ops, c);
                            json.result().ifPresent(j -> sb.append("    ").append(j.toString(), 0,
                                    Math.min(400, j.toString().length())).append('\n'));
                        }
                    }
                    java.nio.file.Files.writeString(dir.resolve("tooltip-debug.txt"), sb.toString(),
                            java.nio.charset.StandardCharsets.UTF_8);
                } catch (Throwable ignored) {
                    // debugging only
                }
            });
        } catch (Throwable ignored) {
            // debugging only
        }
    }

    /**
     * Trade Market price right under the item name, and for a box "(15 EB)" after each item in
     * the possibilities list Wynntils draws. Returns whether it changed anything.
     */
    /**
     * A material's market depth on its price line: a red "ONLY 9" box when only a few sit at the
     * lowest price before a wall of dearer ones, and, while Shift is held, up to three lines of how
     * many are listed at each price. The listings are asked for only when the price hints at a
     * wall (the median at least twice the lowest) or Shift is held, at most once per few minutes.
     */
    private static List<Component> depth(net.minecraft.network.chat.MutableComponent line, String name, Prices.Price known) {
        boolean shift = Minecraft.getInstance().hasShiftDown();
        boolean hint = known != null && known.lowest() != null && known.median() != null && known.median() >= known.lowest() * 2.0;
        if (!shift && !hint) {
            return List.of();
        }
        io.github.anilken.wynnlabs.search.BoxMarket.Held held = io.github.anilken.wynnlabs.market.Listings.held(name);
        if (held == null || held.listings().isEmpty()) {
            io.github.anilken.wynnlabs.market.Listings.request(List.of(name));
            return List.of();
        }
        List<io.github.anilken.wynnlabs.market.Depth.Step> steps = io.github.anilken.wynnlabs.market.Depth.ladder(held.listings());
        int few = io.github.anilken.wynnlabs.market.Depth.fewCheap(steps);
        net.minecraft.network.chat.Style plain = net.minecraft.network.chat.Style.EMPTY
                .withFont(net.minecraft.network.chat.FontDescription.DEFAULT);
        if (few > 0) {
            line.append(Component.literal(" ").setStyle(plain)).append(banner(L.t("wynnlabs.tooltip.only_banner"), 0xFF5555))
                    .append(banner(String.valueOf(few), 0xFF5555));
        }
        List<Component> out = new ArrayList<>();
        if (shift) {
            for (int i = 0; i < Math.min(3, steps.size()); i++) {
                var st = steps.get(i);
                out.add(Component.literal("  " + st.amount() + " × ").setStyle(plain.withColor(net.minecraft.ChatFormatting.GRAY))
                        .append(Component.literal(Prices.fmt(st.price())).setStyle(plain.withColor(net.minecraft.ChatFormatting.GOLD))));
            }
        }
        return out;
    }

    /** The hidden mark on our MARKET line, so a tooltip built twice gets one. */
    private static final String PRICE_MARK = "wynnlabs:price";

    private static boolean withPrices(net.minecraft.world.item.ItemStack stack, List<Component> out) {
        if (stack.isEmpty() || out.isEmpty()) {
            return false;
        }
        for (Component c : out) {
            // done already: our own MARKET line carries a hidden mark (the game's banners start with the same glyph)
            if (c.getString().contains(PRICE_TAG) || PRICE_MARK.equals(c.getStyle().getInsertion())) {
                return false;
            }
        }
        var parsed = WynntilsItems.view(stack);
        if (parsed.isEmpty()) {
            return false;
        }
        // gear, and anything else with a name the market prices (ingredients, materials, wards, runes…)
        // a Trade Market listing the item reader gave no name: the name it shows
        String listed = !parsed.get().isGear() && !parsed.get().isBox() && (parsed.get().name() == null || parsed.get().name().isBlank())
                && Wyn.marketListingPrice(stack) > 0 ? ItemJson.clean(stack.getHoverName().getString()) : null;
        boolean ingredient = !parsed.get().isGear() && !parsed.get().isBox()
                && (parsed.get().name() != null && !parsed.get().name().isBlank() || listed != null && !listed.isBlank());
        if (parsed.get().isGear() || ingredient) {
            ItemView g = parsed.get();
            if (!Settings.on("tooltipPrice")) {
                return false;
            }
            String shown = ingredient ? (g.name() != null && !g.name().isBlank() ? g.name() : listed) : g.data().name();
            String name = ingredient ? Prices.key(g, shown) : shown;
            String p = Prices.text(name, g.unidentified());
            if (p == null) {
                return false;
            }
            p = p.contains(" (") ? p.substring(0, p.indexOf(" (")) : p;   // just the price, short
            Prices.Price known = GameState.current().price(name);
            String bare = p;
            int stackOf = 0;
            String stackWorth = null;
            if (ingredient && stack.getCount() > 1 && known != null && known.typical() != null) {
                // a stack of ingredients: what the whole stack is worth too
                stackOf = stack.getCount();
                stackWorth = Prices.fmt(known.typical() * stackOf);
                p += " ×" + stackOf + " ≈ " + stackWorth;
            }
            boolean old = known != null && known.uncertain();
            if (old) {
                p = Prices.past(p);                 // nothing listed now, or only old listings
            }
            // Line 0 is a hidden copy of the name; the visible header ("Dusk Painter [57.9%]",
            // with the emblem) is the next line holding it. Under it come the tier and powder
            // banners, then an empty line before the stats: the price fills that line, so the
            // header keeps its width. Without such a line it goes at the header's end.
            int header = headerLine(out, shown);
            int blank = -1;
            for (int i = header + 1; header >= 0 && i < Math.min(out.size(), header + 5); i++) {
                if (out.get(i).getString().isBlank()) {
                    blank = i;
                    break;
                }
            }
            // Wynncraft's own font has no "◇": the price says its font, the default one
            Component price = Component.literal((blank >= 0 ? "" : "  ") + PRICE_TAG + p).withStyle(net.minecraft.ChatFormatting.GOLD)
                    .withStyle(s -> s.withFont(net.minecraft.network.chat.FontDescription.DEFAULT));
            if (!out.isEmpty()) {
                // on its own line: a gold "MARKET" box like the game's own banners ("LE", "CURRENCY"
                // in Wynncraft's banner font: a left cap, a cell per letter A-Z, a right cap, a step
                // back over them, the letters on top), the price, and a grey "PAST" box when no
                // listing backs it now
                String[] words = {L.t("wynnlabs.tooltip.market_banner"), old ? L.t("wynnlabs.tooltip.past_banner") : null};
                String[] fallback = {"MARKET", "PAST"};
                int[] boxes = {0xFFAA00, 0xAAAAAA};
                Component[] tags = new Component[2];
                for (int k = 0; k < 2; k++) {
                    if (words[k] == null) {
                        continue;
                    }
                    String word = words[k].startsWith("wynnlabs.") ? fallback[k] : words[k];
                    tags[k] = banner(word, boxes[k]);
                }
                net.minecraft.network.chat.MutableComponent line = Component.empty()
                        .withStyle(st -> st.withInsertion(PRICE_MARK)).append(tags[0]);
                java.util.regex.Matcher tierOf = java.util.regex.Pattern.compile(" (T[0-9])$").matcher(name);
                if (ingredient && !name.equals(shown) && tierOf.find()) {
                    // a material's price is its tier's ("Silver Ingot T3"): the tier in a box after MARKET
                    line.append(Component.literal(" ").setStyle(net.minecraft.network.chat.Style.EMPTY
                            .withFont(net.minecraft.network.chat.FontDescription.DEFAULT)))
                            .append(banner(tierOf.group(1), 0xAAAAAA));
                }
                line
                        .append(Component.literal(" " + bare + (stackOf > 1 || tags[1] != null ? " " : ""))
                                .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.ChatFormatting.GOLD)
                                        .withFont(net.minecraft.network.chat.FontDescription.DEFAULT)));
                if (stackOf > 1) {
                    // the stack's size in a grey box ("X28"), then what the whole stack is worth
                    line.append(banner("X" + stackOf, 0xAAAAAA))
                            .append(Component.literal(" ≈ " + stackWorth + (tags[1] != null ? " " : ""))
                                    .setStyle(net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.ChatFormatting.GOLD)
                                            .withFont(net.minecraft.network.chat.FontDescription.DEFAULT)));
                }
                if (tags[1] != null) {
                    line.append(tags[1]);
                }
                List<Component> ladder = ingredient ? depth(line, name, known) : List.of();
                if (blank >= 0) {
                    out.set(blank, line);
                    out.add(blank + 1, Component.empty());  // the stats keep their gap under it
                    out.add(blank, Component.empty());      // and the banners one over it
                } else if (header >= 0) {
                    // no empty line under the header (a material, a ward): its own line under the
                    // tier and kind banners, as close to the name as the game's own lines allow
                    // one empty line first, so it sits under the item's picture box
                    int at = Math.min(out.size(), header + 2);
                    out.add(at, line);
                    out.add(at, Component.empty());
                } else {
                    // no header line found (a powder's plain tooltip): right under the name
                    out.add(1, line);
                }
                int under = out.indexOf(line) + 1;
                if (under > 0) {
                    out.addAll(under, ladder);
                }
            } else {
                out.add(Math.min(1, out.size()), price);
            }
            if (!ingredient && inMarket() && Settings.on("marketFit")) {
                forYouLines(g, stack, out, blank >= 0 ? blank + 3 : Math.max(1, header + 1));
            }
            return true;
        }
        if (!Settings.on("boxPrices")
                || !parsed.get().isBox()) {
            return false;
        }
        boolean changed = false;
        var possible = WynntilsItems.possibleGears(parsed.get().box());
        Prices.request(possible.stream().map(ItemData::name).toList());
        // One summary line as well: names can be translated, so matching lines may fail
        List<String> worth = new ArrayList<>();
        possible.stream()
                .map(i -> new Object[] {i.name(), Prices.of(i.name()) == null ? null : Prices.of(i.name()).typical()})
                .filter(o -> o[1] != null)
                .sorted((x, y) -> Double.compare((Double) y[1], (Double) x[1]))
                .limit(3)
                .forEach(o -> worth.add(o[0] + " ~" + Prices.fmt((Double) o[1])));
        String marketHead = PRICE_TAG + L.t("wynnlabs.tooltip.box_market", "").strip();
        // each name in the list gets its price right after it, before the comma of the next one:
        // "Coral Charm (12 EB), Crash (18 EB)"
        java.util.Map<String, String> priceOf = new java.util.LinkedHashMap<>();
        for (var info : possible) {
            String p = Prices.text(info.name(), true);
            if (p != null) {
                String shortP = p.contains(" (") ? p.substring(0, p.indexOf(" (")) : p;
                priceOf.put(info.name(), " (" + shortP.replace("~", "") + ")");
            }
        }
        int priced = 0;
        for (int i = 0; i < out.size() && !priceOf.isEmpty(); i++) {
            String text = out.get(i).getString();
            boolean has = false;
            boolean done = false;
            for (var pe : priceOf.entrySet()) {
                if (text.contains(pe.getKey())) {
                    has = true;
                    done |= text.contains(pe.getKey() + pe.getValue());
                }
            }
            if (!has) {
                continue;
            }
            priced++;
            if (done) {
                continue;                           // priced on an earlier pass
            }
            out.set(i, withPricesAfterNames(out.get(i), priceOf));
            changed = true;
        }
        // one summary line only when the list's lines could not be matched (a translated name):
        // otherwise the prices are on the lines already, and the tooltip stays shorter
        if (priced == 0 && !worth.isEmpty() && out.stream().noneMatch(c -> c.getString().startsWith(marketHead))) {
            out.add(Component.literal(PRICE_TAG + L.t("wynnlabs.tooltip.box_market", String.join(", ", worth)))
                    .withStyle(net.minecraft.ChatFormatting.GOLD));
            changed = true;
        }
        return changed;
    }

    /**
     * The line again, its styles kept, with a gold "(price)" right after every name it shows.
     * Names are matched longest first, so "Charm of the Vampire" is not taken for "Charm".
     */
    private static Component withPricesAfterNames(Component line, java.util.Map<String, String> priceOf) {
        List<String> names = new ArrayList<>(priceOf.keySet());
        names.sort((x, y) -> y.length() - x.length());
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        line.visit((style, text) -> {
            int at = 0;
            while (at < text.length()) {
                int best = -1;
                String hit = null;
                for (String n : names) {
                    int k = text.indexOf(n, at);
                    if (k >= 0 && (best < 0 || k < best || k == best && n.length() > hit.length())) {
                        best = k;
                        hit = n;
                    }
                }
                if (hit == null) {
                    out.append(Component.literal(text.substring(at)).withStyle(style));
                    break;
                }
                int end = best + hit.length();
                out.append(Component.literal(text.substring(at, end)).withStyle(style));
                out.append(Component.literal(priceOf.get(hit)).withStyle(net.minecraft.ChatFormatting.GOLD));
                at = end;
            }
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out;
    }

    /** The visible header: the last of the first four lines that shows the name, or -1. */
    private static int headerLine(List<Component> lines, String name) {
        int found = -1;
        for (int i = 0; i < Math.min(4, lines.size()); i++) {
            if (ItemJson.clean(lines.get(i).getString()).contains(name)) {
                found = i;
            }
        }
        return found;
    }

    /** The index just under the first line that shows this name; 1 if none does. */
    private static int afterName(List<Component> lines, String name) {
        for (int i = 0; i < lines.size(); i++) {
            if (!name.isEmpty() && ItemJson.clean(lines.get(i).getString()).contains(name)) {
                return i + 1;
            }
        }
        return Math.min(1, lines.size());
    }
}
