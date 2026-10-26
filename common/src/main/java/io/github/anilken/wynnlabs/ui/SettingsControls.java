package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.RuleEditor;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.ui.SettingsRows.SetRow;

import net.minecraft.client.gui.GuiGraphics;

/**
 * A settings row's control in the fixed column at its right: a switch, − value + (an emerald rule
 * picked: its amount typed and its unit), a segmented choice, a menu chip, the key's state; and
 * the reset beside it when the row differs from its default. Every piece is a Ui component.
 */
final class SettingsControls extends MenuPaint {

    /** A button square in a row (− + and the reset). */
    private static final int SQ = Ui.CHIP_H + 2;

    private final SettingsTab tab;

    SettingsControls(RoadmapScreen menu, SettingsTab tab) {
        super(menu);
        this.tab = tab;
    }

    /** The control's width (the reset included), as {@link #control} draws it. */
    int width(SetRow r, boolean picked) {
        int w = switch (r.kind()) {
            case "switch", "advice" -> 19;
            case "rule" -> r.r().isSwitch() ? 19 : emeraldBox(r, picked) ? 2 * SQ + 2 * Theme.SP_1 + 40 + Theme.SP_1 + unitWidth()
                    : stepperWidth(SettingsRows.shortValue(r));
            case "step" -> stepperWidth(SettingsRows.shortValue(r));
            case "arch", "goal" -> Ui.iconChipWidth(font(), null, SettingsRows.shortValue(r), true);
            case "scale", "fill", "lang" -> segmentedWidth(segments(r));
            case "key" -> Type.META.width(font(), SettingsRows.shortValue(r)) + 2 * Theme.SP_2;
            default -> 0;
        };
        if (SettingsRows.hasDefault(r) && SettingsRows.rowChanged(r)) {
            w += Theme.SP_2 + SQ;
        }
        return w;
    }

    /** An emerald rule picked: its amount typed in the box, the unit on a chip after it. */
    private static boolean emeraldBox(SetRow r, boolean picked) {
        return r.kind().equals("rule") && r.r().unit().equals("E") && picked;
    }

    private int unitWidth() {
        return Ui.chipWidth(font(), "EB");
    }

    private int stepperWidth(String value) {
        return 2 * SQ + 2 * Theme.SP_1 + Math.max(36, Type.LABEL.width(font(), value) + 2 * Theme.SP_2);
    }

    /**
     * The row's control ending at {@code right}, centred on the name's capitals at {@code ty}, and
     * the reset left of it when the row differs from its default; returns the width it took.
     */
    int draw(GuiGraphics g, SetRow r, String ref, boolean picked, int right, int ty, int mx, int my) {
        int cy = Type.BODY.onCaps(ty, Ui.CHIP_H);
        int w;
        switch (r.kind()) {
            case "switch", "advice" -> {
                Kit.toggle(g, right - 19, Type.BODY.onCaps(ty, 11), SettingsRows.toggleOn(r));
                w = 19;
            }
            case "rule" -> {
                if (r.r().isSwitch()) {
                    Kit.toggle(g, right - 19, Type.BODY.onCaps(ty, 11), SettingsRows.toggleOn(r));
                    w = 19;
                } else if (emeraldBox(r, picked)) {
                    w = emerald(g, r, right, cy, mx, my);
                } else {
                    w = stepper(g, SettingsRows.shortValue(r), SettingsRows.rowChanged(r), "#rule:" + r.rule() + ":-1",
                            "#rule:" + r.rule() + ":1", right, cy, mx, my);
                }
            }
            case "step" -> w = stepper(g, SettingsRows.shortValue(r), SettingsRows.rowChanged(r), "#step:" + r.id() + ":-1",
                    "#step:" + r.id() + ":1", right, cy, mx, my);
            case "arch", "goal" -> {
                String v = SettingsRows.shortValue(r);
                w = Ui.iconChipWidth(font(), null, v, true);
                Ui.iconChip(g, font(), null, v, true, right - w, cy, w, false, mx, my);
                menu.toggles.add(new Object[] {new int[] {right - w, cy, w, Ui.CHIP_H}, r.kind().equals("arch") ? "#archetype" : "#goal"});
            }
            case "scale", "fill", "lang" -> w = segmented(g, r, right, cy, mx, my);
            case "key" -> {
                String v = SettingsRows.shortValue(r);
                int colour = Prices.keyInvalid ? Theme.BAD : Prices.maskedKey() == null && !Prices.embeddedKeyInUse() ? Theme.INK_3
                        : Theme.SEAL_2;
                w = Ui.seal(g, font(), v, right - Type.META.width(font(), v) - 2 * Theme.SP_2, Type.BODY.onCaps(ty, Ui.SEAL_H), colour);
            }
            default -> w = 0;
        }
        if (SettingsRows.hasDefault(r) && SettingsRows.rowChanged(r)) {
            int rx = right - w - Theme.SP_2 - SQ;
            Ui.iconButton(g, "refresh", rx, cy, false, mx, my);
            menu.toggles.add(new Object[] {new int[] {rx, cy, SQ, Ui.CHIP_H}, "#sreset:" + ref});
            if (mx >= rx && mx < rx + SQ && my >= cy && my < cy + Ui.CHIP_H) {
                rowTip(rx, cy, SQ, Ui.CHIP_H, L.t("wynnlabs.settings.reset_one"), SettingsRows.rowDefault(r));
            }
            w += Theme.SP_2 + SQ;
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
        menu.toggles.add(new Object[] {new int[] {x, y, SQ, Ui.CHIP_H}, "#rule:" + r.rule() + ":-1"});
        int bx = x + SQ + Theme.SP_1;
        emeraldBox(g, r, bx, y + 1, 40, mx, my);
        int ux = bx + 40 + Theme.SP_1;
        Ui.chip(g, font(), unitName(), ux, y, uw, false, false, false, mx, my);
        menu.toggles.add(new Object[] {new int[] {ux, y, uw, Ui.CHIP_H}, "#ruleunit"});
        Ui.iconButton(g, "plus", ux + uw + Theme.SP_1, y, false, mx, my);
        menu.toggles.add(new Object[] {new int[] {ux + uw + Theme.SP_1, y, SQ, Ui.CHIP_H}, "#rule:" + r.rule() + ":1"});
        return w;
    }

    /** A segmented choice's options: {label, click id or scale value, picked}. */
    private Object[][] segments(SetRow r) {
        switch (r.kind()) {
            case "scale" -> {
                int pick = MenuScale.setting();
                return new Object[][] {{L.t("wynnlabs.settings.scale_game"), 0, pick == 0}, {"2", 2, pick == 2}, {"3", 3, pick == 3},
                    {"4", 4, pick == 4}};
            }
            case "fill" -> {
                int pick = SettingsRows.menuFill();
                Object[][] out = new Object[SettingsRows.FILLS.length][];
                for (int i = 0; i < out.length; i++) {
                    int v = SettingsRows.FILLS[i];
                    out[i] = new Object[] {RoadmapScreen.pct(v), "#fill:" + v, pick == v};
                }
                return out;
            }
            default -> {
                int pick = Settings.number("langOverride", 0);
                String[] names = SettingsRows.langNames();
                Object[][] out = new Object[names.length][];
                for (int i = 0; i < names.length; i++) {
                    out[i] = new Object[] {names[i], "#lang:" + i, pick == i};
                }
                return out;
            }
        }
    }

    private int segmentWidth(String label) {
        return Math.max(22, Type.BODY.width(font(), label) + 2 * Theme.SP_3);
    }

    private int segmentedWidth(Object[][] segs) {
        int w = 0;
        for (Object[] s : segs) {
            w += segmentWidth((String) s[0]) + Theme.SP_1;
        }
        return w - Theme.SP_1;
    }

    /** The choice as chips side by side, the picked one gold. */
    private int segmented(GuiGraphics g, SetRow r, int right, int y, int mx, int my) {
        Object[][] segs = segments(r);
        int w = segmentedWidth(segs);
        int x = right - w;
        for (Object[] s : segs) {
            String label = (String) s[0];
            int sw = segmentWidth(label);
            Ui.chip(g, font(), label, x, y, sw, (Boolean) s[2], false, false, mx, my);
            if (s[1] instanceof Integer scale) {
                tab.scaleRects.add(new int[] {x, y, sw, Ui.CHIP_H, scale});
            } else {
                menu.toggles.add(new Object[] {new int[] {x, y, sw, Ui.CHIP_H}, s[1]});
            }
            x += sw + Theme.SP_1;
        }
        return w;
    }

    /** The unit the rule box's amount is in. */
    private String unitName() {
        return SettingsTab.UNIT_NAMES[tab.ruleUnit];
    }

    /** An emerald rule's amount in the box at x, y ({@code w} wide), in the unit its value reads best in. */
    private void emeraldBox(GuiGraphics g, SetRow r, int x, int y, int w, int mx, int my) {
        double v = RuleEditor.value(r.r());
        if (tab.ruleBoxFor != r.rule()) {
            tab.ruleUnit = v >= 4096 && v % 4096 == 0 ? 2 : v >= 64 && v % 64 == 0 ? 1 : 0;
        }
        if (!tab.ruleBox.isFocused()) {
            double inUnit = v / SettingsTab.UNIT_MULT[tab.ruleUnit];
            tab.ruleBox.setValue(inUnit == Math.rint(inUnit) ? String.valueOf((long) inUnit)
                    : String.valueOf(Math.round(inUnit * 100) / 100.0));
        }
        tab.ruleBoxFor = r.rule();
        textBox(g, tab.ruleBox, x, y, w, "wynnlabs.rules.type_hint", mx, my, 0f);
        tab.ruleBoxRect = new int[] {x, y, w, 12};
    }
}
