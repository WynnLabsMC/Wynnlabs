package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.mixin.ContainerHoverAccessor;
import io.github.anilken.wynnlabs.mounts.FeedPlan;
import io.github.anilken.wynnlabs.mounts.FeedStep;
import io.github.anilken.wynnlabs.mounts.FeederRead;
import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.Mount;
import io.github.anilken.wynnlabs.mounts.MountBook;
import io.github.anilken.wynnlabs.mounts.MountMaterials;
import io.github.anilken.wynnlabs.mounts.RoadMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Mount Feeder, marked where the player looks (setting feederOverlay): under each mount its
 * potential (a heart when it can breed), in each trough's first free slot the next food faintly
 * with where it is, a thin bar under the trough with the time left on the food being eaten, one
 * chip over the menu (target, generation, mounts; a click opens the Mounts tab), a tag left of each
 * row (its potential), and the row's card beside the menu while the mouse is on the row or the card
 * (a click on the tag or the card's pin keeps it open). The plan's next foods in the bag get a soft
 * gold frame (setting feederHighlight). Draws only: the clicks it takes are on its own chip, card
 * and tags, which never lie over a slot; a click on a game slot always goes to the game.
 */
public final class FeederOverlay {

    private static MountsModel model;
    /** The row whose card stays open (0: none). */
    static int pinned;
    /** The row the mouse is on this frame (0: none), and the card's and the chip's boxes. */
    private static int hovered;
    private static int[] cardBox;
    private static int[] chipBox;
    private static int[] buttonBox;
    private static int[] pinBox;
    private static MountsModel.Owned cardMount;
    /** Each row's tag left of the menu (potential, a heart): {x, y, w, h, row}; a click on one pins its card. */
    private static final java.util.List<int[]> tags = new java.util.ArrayList<>();
    /** The game's slots this frame, in screen space: a click on one always goes to the game, never to us. */
    private static final java.util.List<int[]> slotBoxes = new java.util.ArrayList<>();
    /** The card shown last and when the mouse last was on its row or on it (it stays a moment while the mouse crosses over). */
    private static int lastShown;
    private static long lastSeenAt;
    private static final long HOLD_MS = 400;
    /** The next foods (clean names) of every row, for the bag's frames; worked out once a second. */
    private static Set<String> nextFoods = Set.of();
    private static long nextAt;
    /** Where each row's next food comes from, by row number, as the marks drew it last ({@link #allotted}). */
    private static Map<Integer, MountsShop.Source> sources;

    private FeederOverlay() {}

    static MountsModel model() {
        if (model == null) {
            model = new MountsModel();
        }
        return model;
    }

    /** Whether this screen is the Mount Feeder and the overlay is wanted. */
    static boolean active(AbstractContainerScreen<?> screen) {
        if (!Settings.on("feederOverlay") || MountBook.live().isEmpty()) {
            return false;
        }
        var c = io.github.anilken.wynnlabs.game.Wyn.container();
        return MountBook.feeder(c == null ? null : c.type(), screen.getTitle().getString());
    }

    /** The mount last seen with this key, in the planner's terms; null when not known. */
    /** The mount in this feeder row (its key and its row: two fresh mounts may share a key). */
    static MountsModel.Owned owned(MountBook.Live row) {
        if (row.mountKey() == null) {
            return null;
        }
        for (MountsModel.Owned o : model().owned()) {
            if (o.read().key().equals(row.mountKey()) && o.where() == MountBook.Where.ENCLOSURE && o.row() == row.row().number()) {
                return o;
            }
        }
        return owned(row.mountKey());
    }

    static MountsModel.Owned owned(String key) {
        if (key == null) {
            return null;
        }
        for (MountsModel.Owned o : model().owned()) {
            if (o.read().key().equals(key)) {
                return o;
            }
        }
        return null;
    }

    /** The next food the plan (cached, running or estimated) gives this mount, or null. */
    static Material nextFood(MountsModel.Owned o) {
        if (o == null) {
            return null;
        }
        FeedPlan p = model().feed(o).plan();
        if (p == null) {
            return null;
        }
        for (FeedStep s : p.steps()) {
            if (s.isFeed()) {
                return s.material();
            }
        }
        return null;
    }

    /** The plan's step to do now (a food or a ride), or null. */
    static FeedStep nextStep(MountsModel.Owned o) {
        return o == null ? null : firstStep(model().feed(o).plan());
    }

    /** The plan's first step: a ride comes before a food that needs its level. Pure, for the test. */
    static FeedStep firstStep(FeedPlan p) {
        return p == null || p.steps().isEmpty() ? null : p.steps().get(0);
    }

    /** The food a row eats now: none while a ride must come first. */
    private static Material eatsNow(MountsModel.Owned o) {
        FeedStep s = nextStep(o);
        return s != null && s.isFeed() ? s.material() : null;
    }

    // ---- drawing, after the menu ----

    public static void render(AbstractContainerScreen<?> screen, GuiGraphics g, int mx, int my) {
        if (!active(screen)) {
            pinned = 0;
            cardBox = null;
            chipBox = null;
            pinBox = null;
            tags.clear();
            slotBoxes.clear();
            return;
        }
        ContainerHoverAccessor acc = (ContainerHoverAccessor) screen;
        int left = acc.wynnlabs$leftPos();
        int top = acc.wynnlabs$topPos();
        Font font = Minecraft.getInstance().font;
        List<Slot> slots = screen.getMenu().slots;
        hovered = 0;
        long now = System.currentTimeMillis();
        tags.clear();
        slotBoxes.clear();
        for (Slot s : slots) {
            slotBoxes.add(new int[] {left + s.x - 1, top + s.y - 1, 18, 18});
        }
        g.nextStratum();
        Map<Integer, MountsShop.Source> from = allotted();
        for (MountBook.Live row : MountBook.live()) {
            FeederRead.Row r = row.row();
            if (r.mountSlot() >= slots.size()) {
                continue;
            }
            Slot ms = slots.get(r.mountSlot());
            int sx = left + ms.x;
            int sy = top + ms.y;
            int rowRight = sx + 18;
            for (int i : r.trough()) {
                if (i < slots.size()) {
                    rowRight = Math.max(rowRight, left + slots.get(i).x + 18);
                }
            }
            if (mx >= sx - 1 && mx < rowRight && my >= sy - 1 && my < sy + 17) {
                hovered = r.number();
            }
            MountsModel.Owned o = owned(row);
            if (o == null) {
                continue;
            }
            Mount m = o.mount();
            // the row's tag, left of the menu (never over a slot): its potential, a heart when it can
            // breed; gold while its card is kept open; a click on it keeps the card open
            // two mounts in the row breed: the tag says so instead of the potential
            boolean breeding = row.partnerKey() != null;
            String pot = breeding ? L.t("wynnlabs.mounts.breeding") : String.valueOf(o.read().potential());
            boolean heart = breeding || m.breedReady();
            int pw = Type.MICRO.width(font, pot) + 2 * Theme.SP_1 + (heart ? 7 + Theme.SP_1 : 0);
            int tx = left - Theme.SP_1 - pw;
            int ty = sy + 4;
            boolean tagOn = pinned == r.number();
            boolean tagHover = mx >= tx && mx < tx + pw && my >= ty && my < ty + 9;
            Art.box(g, tx, ty, pw, 9, 3, tagOn ? 0xFFE0AE3E : tagHover ? 0xEE2A1F14 : 0xCC1A130C, tagOn ? 0xFF8A6A1C : 0);
            int ix = tx + Theme.SP_1;
            if (heart) {
                Art.icon(g, "heart", ix, ty + 1, 7, tagOn ? 0xFF1A130C : breeding ? 0xFFF09595 : 0xFF8FD47E);
                ix += 7 + Theme.SP_1;
            }
            Type.MICRO.draw(g, font, pot, ix, Type.MICRO.yIn(ty, 9), tagOn ? 0xFF1A130C : 0xFFF3E6C4);
            tags.add(new int[] {tx, ty, pw, 9, r.number()});
            if (row.alert()) {
                // something to collect (a bred mount): a small "!" before the tag
                Art.box(g, tx - Theme.SP_1 - 8, ty, 8, 9, 3, 0xFFC0392B, 0);
                Type.MICRO.draw(g, font, "!", tx - Theme.SP_1 - 8 + (8 - Type.MICRO.width(font, "!")) / 2, Type.MICRO.yIn(ty, 9), 0xFFF3E6C4);
            }
            if (tagHover) {
                hovered = r.number();
            }
            // the step to do now, faint, in the first free trough slot: the food and where it is, or
            // a ride icon when the mount must ride first (its next food needs a stat it lacks)
            FeedStep step = breeding ? null : nextStep(o);
            if (step != null && row.freeSlot() >= 0 && row.freeSlot() < slots.size()) {
                Slot fs = slots.get(row.freeSlot());
                int fx = left + fs.x;
                int fy = top + fs.y;
                if (step.isFeed()) {
                    MountsText.item(g, step.material().name(), fx + 2, fy + 2, 12);
                    g.fill(fx + 2, fy + 2, fx + 14, fy + 14, 0x66404040);
                    mark(g, from.getOrDefault(r.number(), MountsShop.Source.BUY), fx + 10, fy + 9);
                } else {
                    Art.box(g, fx + 1, fy + 1, 14, 14, 4, 0x99F3E6C4, 0);
                    Art.icon(g, "trending-up", fx + 4, fy + 4, 8, Theme.INK_2);
                }
            }
            // the time left on the food being eaten (feeding rows only): a thin bar along the trough's
            // bottom, and the time at a fixed spot in the row, right-aligned just left of the first
            // locked slot and centred on the trough (never at the bar's end, never in another row)
            long left1 = row.remainingSec() < 0 ? -1 : Math.max(0, row.remainingSec());
            if (!breeding && left1 > 0 && !r.trough().isEmpty() && !m.queue().pending().isEmpty()) {
                int x0 = left + slots.get(r.trough().get(0)).x;
                int x1 = x0 + 18 * Math.max(1, r.open()) - 2;
                long whole = FeederRead.timers(m.stats(), m.queue().pending())[0];
                double frac = whole <= 0 ? 0 : Math.min(1, left1 / (double) whole);
                g.fill(x0, sy + 17, x1, sy + 18, 0x80000000);
                g.fill(x0, sy + 17, x0 + (int) Math.round((x1 - x0) * frac), sy + 18, 0xFFE0AE3E);
                int lock = r.open() < r.trough().size() && r.trough().get(r.open()) < slots.size()
                        ? left + slots.get(r.trough().get(r.open())).x - 1 : x1 + 2;
                String t = MountsText.durShort(left1);
                int tw = Type.MICRO.width(font, t) + 2 * Theme.SP_1;
                int th = 8;
                int ty0 = sy + (16 - th) / 2;
                Art.box(g, lock - Theme.SP_1 - tw, ty0, tw, th, 3, 0xCC1A130C, 0);
                Type.MICRO.draw(g, font, t, lock - Theme.SP_1 - tw + Theme.SP_1, Type.MICRO.yIn(ty0, th), 0xFFFFE071);
            }
        }
        chip(screen, g, font, top, mx, my);
        // the card: the kept one, else the row under the mouse; it stays a moment while the mouse
        // crosses from the row to it, and as long as the mouse is on it
        boolean onCard = cardBox != null && in(mx, my, cardBox);
        if (hovered != 0 || onCard) {
            lastSeenAt = now;
        }
        int show = pinned != 0 ? pinned : hovered != 0 ? hovered : onCard || now - lastSeenAt < HOLD_MS ? lastShown : 0;
        lastShown = show;
        cardBox = null;
        buttonBox = null;
        pinBox = null;
        cardMount = null;
        if (show != 0) {
            for (MountBook.Live row : MountBook.live()) {
                if (row.row().number() == show) {
                    MountsModel.Owned o = owned(row);
                    if (o != null) {
                        int[][] boxes = FeederCard.draw(g, font, screen, left, top, acc.wynnlabs$imageWidth(), row, o, model(),
                                pinned == show, mx, my);
                        cardBox = boxes[0];
                        buttonBox = boxes[1];
                        pinBox = boxes[2];
                        cardMount = o;
                    }
                }
            }
        }
    }

    /**
     * Where each row's next food comes from this frame, by row number: the bag's and the bank's
     * stock shared out in row order ({@link MountsShop#allot}), so two rows never count one item.
     */
    private static Map<Integer, MountsShop.Source> allotted() {
        List<MountBook.Live> rows = new ArrayList<>(MountBook.live());
        rows.sort(java.util.Comparator.comparingInt(l -> l.row().number()));
        List<Material> wants = new ArrayList<>();
        for (MountBook.Live row : rows) {
            MountsModel.Owned o = row.partnerKey() != null ? null : owned(row);
            wants.add(o == null ? null : eatsNow(o));
        }
        List<MountsShop.Source> src = MountsShop.allot(wants, FeederOverlay::bagCount, MountsModel::inBank);
        Map<Integer, MountsShop.Source> out = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            if (src.get(i) != null) {
                out.put(rows.get(i).row().number(), src.get(i));
            }
        }
        sources = out;
        return out;
    }

    /** Where this row's next food comes from (as its mark says), BUY when the overlay has not placed it. */
    static MountsShop.Source source(int row, Material next) {
        MountsShop.Source s = sources == null ? null : sources.get(row);
        if (s != null) {
            return s;
        }
        return bagCount(next) > 0 ? MountsShop.Source.BAG : MountsModel.inBank(next) > 0 ? MountsShop.Source.BANK : MountsShop.Source.BUY;
    }

    /** The mark's icon and colour: a backpack (green) for the bag, the bank (blue), a coin (gold) to buy. */
    private static void mark(GuiGraphics g, MountsShop.Source s, int x, int y) {
        String icon = s == MountsShop.Source.BAG ? "backpack" : s == MountsShop.Source.BANK ? "building-bank" : "coin";
        int ink = s == MountsShop.Source.BAG ? Theme.GOOD : s == MountsShop.Source.BANK ? Theme.LINK : Theme.PRICE;
        Art.box(g, x, y, 8, 8, 4, 0xFFF3E6C4, ink);
        Art.icon(g, icon, x + 1, y + 1, 6, ink);
    }

    /** How many items of this name the bag holds. */
    static int bagCount(Material m) {
        return m == null ? 0 : bagCount(m.name());
    }

    /** Previews: the bag's counts by name instead of the player's inventory (null: the inventory). */
    static Map<String, Integer> previewBag;

    static int bagCount(String name) {
        if (previewBag != null) {
            return previewBag.getOrDefault(name, 0);
        }
        var p = Minecraft.getInstance().player;
        if (p == null) {
            return 0;
        }
        Inventory inv = p.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var st = inv.getItem(i);
            if (!st.isEmpty() && FeederRead.clean(st.getHoverName().getString()).equalsIgnoreCase(name)) {
                n += st.getCount();
            }
        }
        return n;
    }

    /** One chip over the menu: the target, the generation now and the mounts to buy; opens the Mounts tab. */
    private static void chip(AbstractContainerScreen<?> screen, GuiGraphics g, Font font, int top, int mx, int my) {
        chipBox = null;
        RoadMap.Plan plan = model().road().value();
        String text = plan == null ? L.t("wynnlabs.mounts.sec_road")
                : L.t("wynnlabs.mounts.feeder_chip", MountsTab.targetLabel(plan.targetPotential()), generation(plan),
                        L.n("wynnlabs.mounts.mounts_n", plan.mountsToBuy(), plan.mountsToBuy()));
        int w = Ui.iconChipWidth(font, "route", text, false);
        int x = screen.width / 2 - w / 2;
        int y = top - Ui.CHIP_H - Theme.SP_2;
        if (y < Theme.SP_1) {
            return;                                        // no room over the menu
        }
        Kit.softCard(g, x, y, w, Ui.CHIP_H);               // parchment under it: the world behind is dark
        Ui.iconChip(g, font, "route", text, false, x, y, w, false, mx, my);
        chipBox = new int[] {x, y, w, Ui.CHIP_H};
    }

    /** The generation of the mount the road map works on now (1 with none). */
    private static int generation(RoadMap.Plan plan) {
        MountsModel.Owned cur = model().current();
        List<RoadMap.Generation> gens = plan.generations();
        List<MountsModel.Owned> mine = model().owned(plan.type());
        for (int i = 0; i < gens.size(); i++) {
            if (cur != null && MountsRoad.inGeneration(gens, i, mine).contains(cur)) {
                return gens.get(i).index();
            }
        }
        return 1;
    }

    // ---- input: only our chip and card take clicks ----

    /** What a click lands on: the game (a slot, or anything not ours), or one of our own drawings. */
    enum Hit { GAME, CHIP, PIN, BUTTON, CARD, TAG }

    /**
     * What a click at x, y lands on. A real slot always wins: anything of ours drawn over a slot
     * never takes its click. Pure, for the test.
     */
    static Hit hit(double x, double y, List<int[]> slots, int[] chip, int[] card, int[] pin, int[] button, List<int[]> tags) {
        for (int[] s : slots) {
            if (in(x, y, s)) {
                return Hit.GAME;
            }
        }
        if (chip != null && in(x, y, chip)) {
            return Hit.CHIP;
        }
        if (card != null && in(x, y, card)) {
            if (pin != null && in(x, y, pin)) {
                return Hit.PIN;
            }
            return button != null && in(x, y, button) ? Hit.BUTTON : Hit.CARD;
        }
        for (int[] t : tags) {
            if (in(x, y, t)) {
                return Hit.TAG;
            }
        }
        return Hit.GAME;
    }

    /**
     * A click in the feeder; true only when it was on our chip, card or a row's tag (then the game
     * never gets it). A click on a game slot is never taken and changes nothing of ours.
     */
    public static boolean click(AbstractContainerScreen<?> screen, double mx, double my, int button) {
        if (!active(screen)) {
            return false;
        }
        Hit h = hit(mx, my, slotBoxes, chipBox, cardBox, pinBox, buttonBox, tags);
        switch (h) {
            case CHIP -> {
                RoadmapScreen s = new RoadmapScreen();
                s.openTab(RoadmapScreen.TAB_MOUNT_ROAD);
                Minecraft.getInstance().setScreen(s);
                return true;
            }
            case PIN -> {
                pinned = pinned == lastShown ? 0 : lastShown;
                return true;
            }
            case BUTTON -> {
                if (cardMount != null) {
                    model().startFeed(cardMount);
                }
                return true;
            }
            case CARD -> {
                return true;
            }
            case TAG -> {
                for (int[] t : tags) {
                    if (in(mx, my, t)) {
                        pinned = pinned == t[4] ? 0 : t[4];
                    }
                }
                return true;
            }
            default -> {
                if (!onSlot(mx, my)) {
                    pinned = 0;                            // a click on nothing of ours and no slot: the card closes
                }
                return false;
            }
        }
    }

    private static boolean onSlot(double x, double y) {
        for (int[] s : slotBoxes) {
            if (in(x, y, s)) {
                return true;
            }
        }
        return false;
    }

    /** Esc while a card is kept open: it closes, the menu stays. */
    public static boolean key(int key) {
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE && pinned != 0) {
            pinned = 0;
            return true;
        }
        return false;
    }

    static boolean in(double x, double y, int[] r) {
        return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
    }

    // ---- the bag: the plan's next foods get a soft gold frame ----

    /** Before a slot is drawn: a gold frame on a bag slot holding one of the rows' next foods. */
    public static void onSlot(GuiGraphics g, Slot slot) {
        if (!Settings.on("feederHighlight") || MountBook.live().isEmpty() || !(slot.container instanceof Inventory)
                || slot.getItem().isEmpty()) {
            return;
        }
        if (System.currentTimeMillis() - nextAt > 1000) {
            Set<String> names = new HashSet<>();
            for (MountBook.Live row : MountBook.live()) {
                Material m = nextFood(owned(row));
                if (m != null) {
                    names.add(m.name().toLowerCase(java.util.Locale.ROOT));
                }
            }
            nextFoods = names;
            nextAt = System.currentTimeMillis();
        }
        String name = FeederRead.clean(slot.getItem().getHoverName().getString()).toLowerCase(java.util.Locale.ROOT);
        if (nextFoods.contains(name) && MountMaterials.byName(name) != null) {
            Kit.slotRing(g, slot.x, slot.y, 16);
        }
    }

    /** Previews and tests: forget the menu's state. */
    static void reset() {
        pinned = 0;
        hovered = 0;
        model = null;
    }
}
