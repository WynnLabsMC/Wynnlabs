package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.MarketSlots;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.RuleEditor;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.ui.SettingsRows.SetRow;

import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * A settings row's control in the column at its right: a switch, − value +, chips (a choice,
 * several switches, the preset), a menu chip, the key's state with its "Key…" chip; and the
 * reset beside it when the row differs from its default. Chips that do not fit beside the name
 * go on their own line under it ({@link #drawChips}). Every piece is a Ui component.
 */
final class SettingsControls extends MenuPaint {

    /** A button square in a row (− + and the reset). */
    private static final int SQ = Ui.CHIP_H + 2;
    /** Over a control that does nothing now (the slot limit's number while the limit is off): the page, see-through. */
    static final int DIM = 0x99D8C595;

    private final SettingsTab tab;

    SettingsControls(RoadmapScreen menu, SettingsTab tab) {
        super(menu);
        this.tab = tab;
    }

    /** Rows whose control is a row of chips. */
    static boolean chips(SetRow r) {
        return switch (r.kind()) {
            case "multi", "rulemulti", "choice", "lang", "fill", "preset", "profiles" -> true;
            default -> false;
        };
    }

    /** The control's width (the reset included), with its chips beside the name. */
    int width(SetRow r, boolean picked) {
        int w = switch (r.kind()) {
            case "switch", "advice" -> UiToggle.W + (movable(r) ? moveWidth() + Theme.SP_2 : 0);
            case "rule" -> emeraldBox(r, picked) ? 2 * SQ + 2 * Theme.SP_1 + 40 + Theme.SP_1 + unitWidth()
                    : stepperWidth(SettingsText.shortValue(r));
            case "slots" -> stepperWidth(SettingsText.shortValue(r)) + Theme.SP_3 + UiToggle.W;
            case "step" -> stepperWidth(SettingsText.shortValue(r));
            case "arch", "goal" -> Ui.iconChipWidth(font(), null, SettingsText.shortValue(r), true);
            case "keynote" -> keyStateWidth(r) + Theme.SP_2 + keyChipWidth();
            default -> chips(r) ? chipsWidth(r) : 0;
        };
        return w + resetWidth(r);
    }

    /** The reset's room when the row differs from its default. */
    int resetWidth(SetRow r) {
        return SettingsRows.hasDefault(r) && SettingsRows.rowChanged(r) ? Theme.SP_2 + SQ : 0;
    }

    private int stepperWidth(String value) {
        return 2 * SQ + 2 * Theme.SP_1 + Math.max(36, Type.LABEL.width(font(), value) + 2 * Theme.SP_2);
    }

    /** An emerald rule picked: its amount typed in the box, the unit on a chip after it. */
    private static boolean emeraldBox(SetRow r, boolean picked) {
        return r.kind().equals("rule") && r.r().unit().equals("E") && picked;
    }

    private int unitWidth() {
        return Ui.chipWidth(font(), "EB");
    }

    /**
     * The row's control ending at {@code right}, centred on the name's capitals at {@code ty}, and
     * the reset left of it when the row differs from its default; returns the width it took.
     * {@code live} false: drawn, but nothing is clickable (a row whose parent is off).
     */
    int draw(GuiGraphics g, SetRow r, String ref, boolean picked, boolean live, int right, int ty, int mx, int my) {
        int cy = Type.BODY.onCaps(ty, Ui.CHIP_H);
        int t0 = menu.toggles.size();
        int w;
        switch (r.kind()) {
            case "switch", "advice" -> {
                UiToggle.draw(g, right - UiToggle.W, Type.BODY.onCaps(ty, UiToggle.H), SettingsRows.toggleOn(r), r.id(), mx, my);
                w = UiToggle.W;
                if (movable(r)) {
                    w += Theme.SP_2 + move(g, right - w - Theme.SP_2, ty, mx, my);
                }
            }
            case "rule" -> w = emeraldBox(r, picked) ? emerald(g, r, right, cy, mx, my)
                    : stepper(g, SettingsText.shortValue(r), SettingsRows.rowChanged(r), "#rule:" + r.id() + ":-1",
                    "#rule:" + r.id() + ":1", right, cy, mx, my);
            case "slots" -> w = slots(g, r, right, ty, cy, mx, my);
            case "step" -> w = stepper(g, SettingsText.shortValue(r), SettingsRows.rowChanged(r), "#step:" + r.id() + ":-1",
                    "#step:" + r.id() + ":1", right, cy, mx, my);
            case "arch", "goal" -> {
                String v = SettingsText.shortValue(r);
                w = Ui.iconChipWidth(font(), null, v, true);
                Ui.iconChip(g, font(), null, v, true, right - w, cy, w, false, mx, my);
                menu.toggles.add(new Object[] {new int[] {right - w, cy, w, Ui.CHIP_H}, r.kind().equals("arch") ? "#archetype" : "#goal"});
            }
            case "keynote" -> w = keynote(g, r, right, ty, cy, mx, my);
            default -> w = chips(r) ? chipLine(g, r, right - chipsWidth(r), cy, mx, my) : 0;
        }
        if (SettingsRows.hasDefault(r) && SettingsRows.rowChanged(r)) {
            w += reset(g, r, ref, right - w - Theme.SP_2 - SQ, cy, true, mx, my);
        }
        if (!live) {
            menu.toggles.subList(t0, menu.toggles.size()).clear();
        }
        return w;
    }

    /** The reset button at x; returns its width and the gap before it. */
    int reset(GuiGraphics g, SetRow r, String ref, int x, int cy, boolean live, int mx, int my) {
        Ui.iconButton(g, "refresh", x, cy, false, mx, my);
        if (!live) {
            return Theme.SP_2 + SQ;
        }
        menu.toggles.add(new Object[] {new int[] {x, cy, SQ, Ui.CHIP_H}, "#sreset:" + ref});
        if (mx >= x && mx < x + SQ && my >= cy && my < cy + Ui.CHIP_H) {
            rowTip(x, cy, SQ, Ui.CHIP_H, L.t("wynnlabs.settings.reset_one"), SettingsText.rowDefault(r));
        }
        return Theme.SP_2 + SQ;
    }

    // ---- chips ----

    private int chipWidth(SetRow r, int i, String label) {
        if (label.startsWith("@")) {
            return label.equals("@box") ? BOX_W : SQ;
        }
        return multi(r) ? Ui.iconChipWidth(font(), SettingsText.picked(r, i) ? "check" : null, label, false) : Ui.chipWidth(font(), label);
    }

    private static boolean multi(SetRow r) {
        return r.kind().equals("multi") || r.kind().equals("rulemulti");
    }

    /** The chips side by side, SP_2 apart (and the preset's Custom seal before them). */
    int chipsWidth(SetRow r) {
        List<String> opts = SettingsText.options(r);
        int w = custom(r) ? customWidth() + Theme.SP_2 : 0;
        for (int i = 0; i < opts.size(); i++) {
            w += chipWidth(r, i, opts.get(i)) + (i > 0 ? Theme.SP_2 : 0);
        }
        return w;
    }

    private static boolean custom(SetRow r) {
        return r.kind().equals("preset") && RuleEditor.preset() < 0;
    }

    private int customWidth() {
        return Type.META.width(font(), L.t("wynnlabs.rules.preset_custom")) + 2 * Theme.SP_2;
    }

    /** The chips on one line from x; returns their width. */
    private int chipLine(GuiGraphics g, SetRow r, int x, int y, int mx, int my) {
        int x0 = x;
        if (custom(r)) {
            x += Ui.seal(g, font(), L.t("wynnlabs.rules.preset_custom"), x, y + (Ui.CHIP_H - Ui.SEAL_H) / 2, Theme.ACCENT) + Theme.SP_2;
        }
        List<String> opts = SettingsText.options(r);
        for (int i = 0; i < opts.size(); i++) {
            x += chip(g, r, i, opts.get(i), x, y, mx, my) + Theme.SP_2;
        }
        return x - Theme.SP_2 - x0;
    }

    /** The preset name box's width. */
    private static final int BOX_W = 110;

    /** A preset row's button (or its name box) in the chip line; the tip names what it does. */
    private int tool(GuiGraphics g, String label, int i, int x, int y, int mx, int my) {
        if (label.equals("@box")) {
            tab.nameBoxRect = UiInput.input(g, font(), tab.nameBox, L.t("wynnlabs.settings.profile_box"), x, y - 1, BOX_W, true, false, mx, my);
            return BOX_W;
        }
        boolean armed = label.equals("@del") && tab.deleteArmed();
        String icon = switch (label) {
            case "@edit" -> "edit";
            case "@add" -> "plus";
            case "@del" -> "trash";
            case "@ok" -> "check";
            default -> "x";
        };
        String tip = L.t(switch (label) {
            case "@edit" -> "wynnlabs.settings.profile_edit";
            case "@add" -> "wynnlabs.settings.profile_add";
            case "@del" -> armed ? "wynnlabs.settings.profile_del_sure" : "wynnlabs.settings.profile_del";
            case "@ok" -> "wynnlabs.settings.profile_ok";
            default -> "wynnlabs.settings.profile_cancel";
        });
        Ui.iconButton(g, icon, x, y, armed, mx, my);
        menu.toggles.add(new Object[] {new int[] {x, y, SQ, Ui.CHIP_H}, "#opt:profiles:presets:" + i});
        if (mx >= x && mx < x + SQ && my >= y && my < y + Ui.CHIP_H) {
            rowTip(x, y, SQ, Ui.CHIP_H, tip, "");
        }
        return SQ;
    }

    private int chip(GuiGraphics g, SetRow r, int i, String label, int x, int y, int mx, int my) {
        if (label.startsWith("@")) {
            return tool(g, label, i, x, y, mx, my);
        }
        int cw = chipWidth(r, i, label);
        boolean on = SettingsText.picked(r, i);
        if (multi(r)) {
            Ui.iconChip(g, font(), on ? "check" : null, label, false, x, y, cw, on, mx, my);
        } else {
            Ui.chip(g, font(), label, x, y, cw, on, false, false, mx, my);
        }
        menu.toggles.add(new Object[] {new int[] {x, y, cw, Ui.CHIP_H}, "#opt:" + r.kind() + ":" + r.id() + ":" + i});
        return cw;
    }

    /** The height of the chips wrapped in {@code w} on lines of their own, SP_2 apart. */
    int chipsHeight(SetRow r, int w) {
        return lines(r, w) * (Ui.CHIP_H + Theme.SP_2) - Theme.SP_2;
    }

    private int lines(SetRow r, int w) {
        List<String> opts = SettingsText.options(r);
        int n = 1;
        int x = custom(r) ? customWidth() + Theme.SP_2 : 0;
        for (int i = 0; i < opts.size(); i++) {
            int cw = chipWidth(r, i, opts.get(i));
            if (x > 0 && x + cw > w) {
                n++;
                x = 0;
            }
            x += cw + Theme.SP_2;
        }
        return n;
    }

    /** The chips on lines of their own from x, y, wrapped in {@code w}; returns their height. */
    int drawChips(GuiGraphics g, SetRow r, boolean live, int x, int y, int w, int mx, int my) {
        int t0 = menu.toggles.size();
        List<String> opts = SettingsText.options(r);
        int cx = x;
        int cy = y;
        if (custom(r)) {
            cx += Ui.seal(g, font(), L.t("wynnlabs.rules.preset_custom"), cx, cy + (Ui.CHIP_H - Ui.SEAL_H) / 2, Theme.ACCENT)
                    + Theme.SP_2;
        }
        for (int i = 0; i < opts.size(); i++) {
            int cw = chipWidth(r, i, opts.get(i));
            if (cx > x && cx + cw > x + w) {
                cx = x;
                cy += Ui.CHIP_H + Theme.SP_2;
            }
            cx += chip(g, r, i, opts.get(i), cx, cy, mx, my) + Theme.SP_2;
        }
        if (!live) {
            menu.toggles.subList(t0, menu.toggles.size()).clear();
        }
        return cy + Ui.CHIP_H - y;
    }

    // ---- the other controls ----

    /** The slot limit: − number + (pale and still while the limit is off), then its switch. */
    private int slots(GuiGraphics g, SetRow r, int right, int ty, int cy, int mx, int my) {
        boolean on = MarketSlots.limited();
        UiToggle.draw(g, right - UiToggle.W, Type.BODY.onCaps(ty, UiToggle.H), on, "#slots", mx, my);
        menu.toggles.add(new Object[] {new int[] {right - UiToggle.W, Type.BODY.onCaps(ty, UiToggle.H), UiToggle.W, UiToggle.H}, "#slots"});
        int sr = right - UiToggle.W - Theme.SP_3;
        int t0 = menu.toggles.size();
        int sw = stepper(g, SettingsText.shortValue(r), RuleEditor.changed(RuleEditor.byId("market_slots")),
                "#rule:market_slots:-1", "#rule:market_slots:1", sr, cy, on ? mx : -1, on ? my : -1);
        if (!on) {
            menu.toggles.subList(t0, menu.toggles.size()).clear();
            g.fill(sr - sw, cy, sr, cy + Ui.CHIP_H, DIM);
        }
        return sw + Theme.SP_3 + UiToggle.W;
    }

    private int keyStateWidth(SetRow r) {
        return Type.META.width(font(), SettingsText.shortValue(r)) + 2 * Theme.SP_2;
    }

    private int keyChipWidth() {
        return Ui.iconChipWidth(font(), null, L.t("wynnlabs.settings.key_chip"), true);
    }

    /** The key's state as a seal, then "Key…" that opens the key's box under the row. */
    private int keynote(GuiGraphics g, SetRow r, int right, int ty, int cy, int mx, int my) {
        int kw = keyChipWidth();
        Ui.iconChip(g, font(), null, L.t("wynnlabs.settings.key_chip"), true, right - kw, cy, kw, tab.keyOpen, mx, my);
        menu.toggles.add(new Object[] {new int[] {right - kw, cy, kw, Ui.CHIP_H}, "#keyopen"});
        String v = SettingsText.shortValue(r);
        int colour = Prices.keyInvalid ? Theme.BAD : Prices.maskedKey() == null && !Prices.embeddedKeyInUse() ? Theme.INK_3
                : Theme.SEAL_2;
        int sw = keyStateWidth(r);
        Ui.seal(g, font(), v, right - kw - Theme.SP_2 - sw, Type.BODY.onCaps(ty, Ui.SEAL_H), colour);
        return sw + Theme.SP_2 + kw;
    }

    /** A switch for a movable HUD element (its place set in {@link HudEditScreen}). */
    private static boolean movable(SetRow r) {
        return r.kind().equals("switch") && r.id().equals("ridePanel");
    }

    private int moveWidth() {
        return Type.BODY.width(font(), L.t("wynnlabs.hud.move")) + 2 * Theme.SP_3;
    }

    /** "Move": opens the HUD's move screen on the mount panel; returns its width. */
    private int move(GuiGraphics g, int right, int ty, int mx, int my) {
        int w = moveWidth();
        int x = right - w;
        int y = Type.BODY.onCaps(ty, Ui.SMALL_H);
        Ui.small(g, font(), L.t("wynnlabs.hud.move"), x, y, w, mx, my);
        menu.toggles.add(new Object[] {new int[] {x, y, w, Ui.SMALL_H}, "#hudmove:" + RidePanel.ID});
        if (mx >= x && mx < x + w && my >= y && my < y + Ui.SMALL_H) {
            rowTip(x, y, w, Ui.SMALL_H, L.t("wynnlabs.hud.move"), L.t("wynnlabs.hud.move_tip"));
        }
        return w;
    }

    /** − value +: two icon buttons and the value in a well between them; the value bold when changed. */
    private int stepper(GuiGraphics g, String value, boolean changed, String minus, String plus, int right, int y, int mx, int my) {
        int vw = Math.max(36, Type.LABEL.width(font(), value) + 2 * Theme.SP_2);
        int w = 2 * SQ + 2 * Theme.SP_1 + vw;
        int x = right - w;
        Ui.iconButton(g, "minus", x, y, false, mx, my);
        menu.toggles.add(new Object[] {new int[] {x, y, SQ, Ui.CHIP_H}, minus});
        int wx = x + SQ + Theme.SP_1;
        g.fill(wx, y, wx + vw, y + Ui.CHIP_H, Theme.WELL);
        Type role = changed ? Type.LABEL : Type.BODY;
        String v = role.fit(font(), value, vw - 2);
        role.draw(g, font(), v, wx + (vw - role.width(font(), v)) / 2, role.yIn(y, Ui.CHIP_H), Theme.INK);
        Ui.iconButton(g, "plus", wx + vw + Theme.SP_1, y, false, mx, my);
        menu.toggles.add(new Object[] {new int[] {wx + vw + Theme.SP_1, y, SQ, Ui.CHIP_H}, plus});
        return w;
    }

    /** An emerald rule picked: − [amount] [unit] +; the amount is typed, the unit chip cycles E, EB, LE. */
    private int emerald(GuiGraphics g, SetRow r, int right, int y, int mx, int my) {
        int uw = unitWidth();
        int w = 2 * SQ + 2 * Theme.SP_1 + 40 + Theme.SP_1 + uw;
        int x = right - w;
        Ui.iconButton(g, "minus", x, y, false, mx, my);
        menu.toggles.add(new Object[] {new int[] {x, y, SQ, Ui.CHIP_H}, "#rule:" + r.id() + ":-1"});
        int bx = x + SQ + Theme.SP_1;
        emeraldBox(g, r, bx, y + 1, 40, mx, my);
        int ux = bx + 40 + Theme.SP_1;
        Ui.chip(g, font(), SettingsTab.UNIT_NAMES[tab.ruleUnit], ux, y, uw, false, false, false, mx, my);
        menu.toggles.add(new Object[] {new int[] {ux, y, uw, Ui.CHIP_H}, "#ruleunit"});
        Ui.iconButton(g, "plus", ux + uw + Theme.SP_1, y, false, mx, my);
        menu.toggles.add(new Object[] {new int[] {ux + uw + Theme.SP_1, y, SQ, Ui.CHIP_H}, "#rule:" + r.id() + ":1"});
        return w;
    }

    /** An emerald rule's amount in the box at x, y ({@code w} wide), in the unit its value reads best in. */
    private void emeraldBox(GuiGraphics g, SetRow r, int x, int y, int w, int mx, int my) {
        double v = RuleEditor.value(r.r());
        if (!r.id().equals(tab.ruleBoxFor)) {
            tab.ruleUnit = v >= 4096 && v % 4096 == 0 ? 2 : v >= 64 && v % 64 == 0 ? 1 : 0;
        }
        if (!tab.ruleBox.isFocused()) {
            double inUnit = v / SettingsTab.UNIT_MULT[tab.ruleUnit];
            tab.ruleBox.setValue(inUnit == Math.rint(inUnit) ? String.valueOf((long) inUnit)
                    : String.valueOf(Math.round(inUnit * 100) / 100.0));
        }
        tab.ruleBoxFor = r.id();
        String typed = tab.ruleBox.getValue().trim();
        boolean bad = !typed.isEmpty() && !typed.matches("\\d+([.,]\\d+)?\\s*[a-zA-Z²]*");
        tab.ruleBoxRect = UiInput.input(g, font(), tab.ruleBox, L.t("wynnlabs.rules.type_hint"), x, y - 1, w, true, bad, mx, my);
    }
}
