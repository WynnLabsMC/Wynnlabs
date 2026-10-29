package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.mounts.MountType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mounts tab: three sections in the page's tab bar (Mounts, Road map, Places; the road map
 * opens first). Mounts: the mounts read so far and the picked one's card ({@link MountsCard});
 * Road map: the way to a LOW (~700) or HIGH (1000+) mount and what to do now ({@link MountsRoad});
 * Places: the enclosures on the map and the unlocks ({@link MountsPlaces}). The type menu and the
 * options menu are popups ({@link UiMenu}). Shows only; it never feeds, rides or buys anything.
 */
final class MountsTab extends MenuTab {

    /** A click area this frame and what it does. */
    record Click(int[] rect, Runnable run) {}

    static final int NONE = 0;
    static final int TYPE_MENU = 1;
    static final int OPTIONS_MENU = 2;
    static final int TARGET_MENU = 3;

    final MountsModel model = new MountsModel();
    final MountsCard card = new MountsCard(this);
    final MountsRoad road = new MountsRoad(this);
    final MountsPlaces places = new MountsPlaces(this);
    final List<Click> clicks = new ArrayList<>();
    final UiMenu.State menuState = new UiMenu.State();
    int open = NONE;
    private int popupFrom;
    /** The chips the menus open under, this frame. */
    int[] typeAt;
    int[] optionsAt;
    int[] targetAt;

    MountsTab(RoadmapScreen menu) {
        super(menu);
    }

    /** 0 Mounts, 1 Road map, 2 Places: the section open now. */
    int section() {
        return switch (menu.tab()) {
            case RoadmapScreen.TAB_MOUNTS -> 0;
            case RoadmapScreen.TAB_MOUNT_PLACES -> 2;
            default -> 1;
        };
    }

    @Override
    void beginFrame() {
        clicks.clear();             // the menus' anchors stay: an open menu is asked for before the chips are drawn
    }

    void click(int x, int y, int w, int h, Runnable run) {
        clicks.add(new Click(new int[] {x, y, w, h}, run));
    }

    /** A tooltip over this box. */
    void tip(int x, int y, int w, int h, List<Component> lines) {
        menu.hits.add(new MenuHit(x, y, w, h, null, lines, null));
    }

    /** The compass to {@code at} on a click, the place's name its tooltip. */
    void compass(int x, int y, int w, int h, String name, int[] at) {
        menu.hits.add(new MenuHit(x, y, w, h, null, List.of(Component.literal(name)),
                "/compass at " + at[0] + " " + at[1] + " " + at[2]));
    }

    boolean compact() {
        return menu.layout.compact();
    }

    // ---- the title row: the type, the standard and the options ----

    /** Whether the title row had no room for the road map's chips (the page draws them in its first row). */
    boolean chipsInPage;

    @Override
    int titleRight(GuiGraphics g, int right, int y, int room, int mx, int my) {
        typeAt = null;
        optionsAt = null;
        targetAt = null;
        int by = y - 3;
        int sec = section();
        if (sec == 2) {
            chipsInPage = false;
            return 0;
        }
        String opt = L.t("wynnlabs.mounts.options");
        int ow = Ui.iconChipWidth(font(), "settings", opt, true);
        if (sec == 0) {
            chipsInPage = false;
            if (ow > room) {
                return 0;
            }
            optionsChip(g, right - ow, by, ow, opt, mx, my);
            return ow;
        }
        int need = typeChipWidth() + Theme.SP_3 + targetChipWidth() + Theme.SP_2 + ow;
        chipsInPage = compact() || need > room;
        if (chipsInPage) {
            return 0;
        }
        int x = right - ow;
        optionsChip(g, x, by, ow, opt, mx, my);
        x -= Theme.SP_2 + targetChipWidth();
        targetChip(g, x, by, mx, my);
        x -= Theme.SP_3;
        typeChip(g, x - typeChipWidth(), by, mx, my);
        return need;
    }

    /** The page's own chip row when the title row has no room (small windows); returns its height. */
    int chipRow(GuiGraphics g, int x, int y, int w, int mx, int my) {
        typeChip(g, x, y, mx, my);
        int r = x + w - (Ui.CHIP_H + 2);
        boolean on = open == OPTIONS_MENU;
        Ui.iconButton(g, "settings", r, y, on, mx, my);
        optionsAt = new int[] {r, y, Ui.CHIP_H + 2, Ui.CHIP_H};
        click(r, y, Ui.CHIP_H + 2, Ui.CHIP_H, () -> toggle(OPTIONS_MENU));
        tip(r, y, Ui.CHIP_H + 2, Ui.CHIP_H, List.of(Component.literal(L.t("wynnlabs.mounts.options"))));
        targetChip(g, r - Theme.SP_2 - targetChipWidth(), y, mx, my);
        return Ui.CHIP_H;
    }

    /** "~700", "1200+": a target as the picker names it. */
    static String targetLabel(int target) {
        int[] all = io.github.anilken.wynnlabs.mounts.RoadMap.TARGETS;
        return target == all[all.length - 1] ? target + "+" : "~" + target;
    }

    private String targetChipText() {
        return L.t("wynnlabs.mounts.target_chip", targetLabel(model.target()));
    }

    private int targetChipWidth() {
        return Ui.iconChipWidth(font(), "target", targetChipText(), true);
    }

    /** "Target · ~700 ▾": opens the targets with what each one costs. */
    private void targetChip(GuiGraphics g, int x, int y, int mx, int my) {
        int w = UiMenu.chip(g, font(), "target", targetChipText(), x, y, open == TARGET_MENU, mx, my);
        targetAt = new int[] {x, y, w, Ui.CHIP_H};
        click(x, y, w, Ui.CHIP_H, () -> toggle(TARGET_MENU));
    }

    private int typeChipWidth() {
        return Ui.iconChipWidth(font(), "horse", model.type().display(), true);
    }

    private void typeChip(GuiGraphics g, int x, int y, int mx, int my) {
        int w = UiMenu.chip(g, font(), "horse", model.type().display(), x, y, open == TYPE_MENU, mx, my);
        typeAt = new int[] {x, y, w, Ui.CHIP_H};
        click(x, y, w, Ui.CHIP_H, () -> toggle(TYPE_MENU));
    }

    private void optionsChip(GuiGraphics g, int x, int y, int w, String label, int mx, int my) {
        Ui.iconChip(g, font(), "settings", label, true, x, y, w, open == OPTIONS_MENU, mx, my);
        optionsAt = new int[] {x, y, w, Ui.CHIP_H};
        click(x, y, w, Ui.CHIP_H, () -> toggle(OPTIONS_MENU));
    }

    void toggle(int which) {
        open = open == which ? NONE : which;
        menuState.reset();
    }

    // ---- the page ----

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        switch (section()) {
            case 0 -> card.draw(g, x, y, w, h, mx, my);
            case 2 -> places.draw(g, x, y, w, h, mx, my);
            default -> road.draw(g, x, y, w, h, mx, my);
        }
    }

    // ---- the menus ----

    private List<UiMenu.Item> items() {
        UiMenu.Items out = new UiMenu.Items();
        MountsModel.Options o = MountsModel.options();
        if (open == TARGET_MENU) {
            for (int t : io.github.anilken.wynnlabs.mounts.RoadMap.TARGETS) {
                MountsModel.Pending<io.github.anilken.wynnlabs.mounts.RoadMap.Plan> p = model.road(t);
                var plan = p.value();
                String meta = plan == null ? (p.running() ? "…" : null)
                        : L.n("wynnlabs.mounts.mounts_n", plan.mountsToBuy()) + " · " + MountsText.dur(plan.wallClockSec());
                UiMenu.Item it = UiMenu.Item.of("g:" + t, null, targetLabel(t), meta, t == model.target());
                if (plan != null && !plan.reached()) {
                    it = it.sub(L.t("wynnlabs.mounts.not_reachable")).asDim()
                            .tip(List.of(Component.literal(L.t("wynnlabs.mounts.not_reachable_tip", MountsText.range(plan.finalPotential())))
                                    .withStyle(ChatFormatting.GRAY)));
                } else if (plan != null) {
                    it = it.sub(L.n("wynnlabs.mounts.target_sub_menu", plan.generations().size(), MountsText.range(plan.finalPotential()),
                            plan.generations().size()));
                }
                out.add(it);
            }
            return out.list;
        }
        if (open == TYPE_MENU) {
            for (MountType t : MountType.values()) {
                int n = model.owned(t).size();
                out.add(UiMenu.Item.of("t:" + t.name(), null, t.display(), n == 0 ? null : String.valueOf(n), t == model.type()));
            }
            return out.list;
        }
        out.add(UiMenu.Item.of("o:train", "clock", L.t("wynnlabs.mounts.opt_train"), L.t("wynnlabs.mounts.min", o.trainMin), false)
                .sub(L.t("wynnlabs.mounts.opt_train_sub")).tip(tip("wynnlabs.mounts.opt_train_tip")));
        out.add(UiMenu.Item.of("o:cap", "trending-up", L.t("wynnlabs.mounts.opt_cap"),
                o.trainCap <= 0 ? L.t("wynnlabs.mounts.none") : String.valueOf(o.trainCap), false).sub(L.t("wynnlabs.mounts.opt_cap_sub")));
        out.add(UiMenu.Item.of("o:swap", o.queueSwap ? "check" : null, L.t("wynnlabs.mounts.opt_swap"), null, false)
                .sub(L.t("wynnlabs.mounts.opt_swap_sub")));
        out.add(UiMenu.Item.of("o:silver", o.silverbull ? "check" : null, "Silverbull", null, false)
                .sub(L.t("wynnlabs.mounts.opt_silver_sub")));
        out.divide();
        out.add(UiMenu.Item.of("o:bank", o.useBank ? "check" : null, L.t("wynnlabs.mounts.opt_bank"), null, false)
                .sub(L.t("wynnlabs.mounts.opt_bank_sub")));
        out.add(UiMenu.Item.of("o:tm", o.buyTm ? "check" : null, L.t("wynnlabs.mounts.opt_tm"), null, false)
                .sub(L.t("wynnlabs.mounts.opt_tm_sub")));
        out.divide();
        boolean overlay = io.github.anilken.wynnlabs.core.Settings.on("feederOverlay");
        boolean highlight = io.github.anilken.wynnlabs.core.Settings.on("feederHighlight");
        out.add(UiMenu.Item.of("o:overlay", overlay ? "check" : null, L.t("wynnlabs.setting.feederOverlay"), null, false)
                .sub(L.t("wynnlabs.mounts.opt_overlay_sub")));
        out.add(UiMenu.Item.of("o:highlight", highlight ? "check" : null, L.t("wynnlabs.setting.feederHighlight"), null, false)
                .sub(L.t("wynnlabs.mounts.opt_highlight_sub")));
        return out.list;
    }

    private static List<Component> tip(String key) {
        return List.of(Component.literal(L.t(key)).withStyle(ChatFormatting.GRAY));
    }

    /** A row picked: the type, or an option changed (numbers step to the next value). */
    private void pick(String id) {
        MountsModel.Options o = MountsModel.options();
        switch (id) {
            case "o:train" -> o.trainMin = next(MountsModel.TRAIN_MINS, o.trainMin);
            case "o:cap" -> o.trainCap = next(MountsModel.TRAIN_CAPS, o.trainCap);
            case "o:swap" -> o.queueSwap = !o.queueSwap;
            case "o:silver" -> o.silverbull = !o.silverbull;
            case "o:bank" -> o.useBank = !o.useBank;
            case "o:tm" -> o.buyTm = !o.buyTm;
            case "o:overlay" -> io.github.anilken.wynnlabs.core.Settings.toggle("feederOverlay");
            case "o:highlight" -> io.github.anilken.wynnlabs.core.Settings.toggle("feederHighlight");
            default -> {
                if (id.startsWith("t:")) {
                    o.type = id.substring(2);
                    open = NONE;
                } else if (id.startsWith("g:")) {
                    o.target = Integer.parseInt(id.substring(2));
                    open = NONE;
                }
            }
        }
        MountsModel.saveOptions();
    }

    private static int next(int[] values, int now) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == now) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    private int[] anchor() {
        return open == TYPE_MENU ? typeAt : open == OPTIONS_MENU ? optionsAt : open == TARGET_MENU ? targetAt : null;
    }

    private final Popup popups = new Popup() {
        @Override
        public boolean open() {
            return open != NONE && anchor() != null;
        }

        @Override
        public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
            int[] at = anchor();
            if (at == null) {
                open = NONE;
                return;
            }
            popupFrom = clicks.size();
            int[] b = UiMenu.draw(g, font(), UiMenu.Spec.under(at, menu.width, menu.height), menuState, items(), mx, my,
                    r -> {
                        String id = r.item().id();
                        click(r.x(), r.y(), r.w(), r.h(), () -> pick(id));
                        if (r.item().tip() != null && mx >= r.x() && mx < r.x() + r.w() && my >= r.y() && my < r.y() + r.h()) {
                            tip(r.x(), r.y(), r.w(), r.h(), r.item().tip());
                        }
                    });
            if (b != null) {
                boxes.add(b);
            }
        }

        @Override
        public void close() {
            open = NONE;
        }

        @Override
        public boolean clicked(MouseButtonEvent event, boolean doubleClick) {
            if (menuState.press(event.x(), event.y())) {
                return true;
            }
            for (int i = clicks.size() - 1; i >= popupFrom; i--) {
                Click c = clicks.get(i);
                if (in(event.x(), event.y(), c.rect())) {
                    c.run().run();
                    return true;
                }
            }
            return true;
        }

        @Override
        public void scrolled(double x, double y, double v) {
            menuState.wheel(v);
        }

        @Override
        public boolean key(int key) {
            UiMenu.Item it = menuState.key(key);
            if (it != null) {
                pick(it.id());
            }
            return true;
        }
    };

    @Override
    Popup popup() {
        return popups;
    }

    // ---- input ----

    @Override
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double ex = event.x();
        double ey = event.y();
        if (section() == 2 && places.clicked(event, doubleClick)) {
            return true;
        }
        if (card.barPress(ex, ey) || road.barPress(ex, ey)) {
            return true;
        }
        for (int i = clicks.size() - 1; i >= 0; i--) {
            Click c = clicks.get(i);
            if (in(ex, ey, c.rect())) {
                c.run().run();
                return true;
            }
        }
        return false;
    }

    @Override
    boolean barDragged(double x, double y) {
        return menuState.drag(y) || section() == 2 && places.dragged(x, y) || card.barDrag(y) || road.barDrag(y);
    }

    @Override
    void barReleased() {
        menuState.release();
        card.barRelease();
        road.barRelease();
        if (section() == 2) {
            places.released();
        }
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        switch (section()) {
            case 0 -> card.scrolled(x, y, v);
            case 2 -> places.scrolled(x, y, v);
            default -> road.scrolled(x, y, v);
        }
    }
}
