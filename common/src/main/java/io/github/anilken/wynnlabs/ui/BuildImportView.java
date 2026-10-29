package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.LinkImport;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.WynnBuilderLink;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.SkillKind;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The PICK view's "Import from WynnBuilder": the link on the clipboard read into its pieces, shown
 * under the picker's title in place of the rest (each piece's slot, name and powders, or why it is
 * left out; the tomes, the skill points set by hand and whether the kept ones fit together;
 * powders, tomes and points stay information only), then "Build around these"
 * makes the kept ones the items to build around. Nothing here acts in the game.
 */
final class BuildImportView {

    /** A piece's row: its name and slot lines beside the 18 px slot. */
    private static final int ROW_H = Type.BODY.lineHeight + Type.META.lineHeight;
    private static final String[] SLOTS = {"wynnlabs.road.slot_helmet", "wynnlabs.road.slot_chestplate", "wynnlabs.road.slot_leggings",
        "wynnlabs.road.slot_boots", "wynnlabs.road.slot_ring", "wynnlabs.road.slot_ring", "wynnlabs.road.slot_bracelet",
        "wynnlabs.road.slot_necklace", "wynnlabs.road.slot_weapon"};

    /** Why a piece is left out, by LinkImport.Why (KEPT shows the powders instead). */
    private static final String[] WHY = {null, "wynnlabs.import.why_crafted", "wynnlabs.import.why_custom", "wynnlabs.import.why_unknown",
        "wynnlabs.import.why_wrong_slot", "wynnlabs.import.why_class"};

    private final BuildControls ui;
    /** The link on show, or null when the list shows. */
    LinkImport.Result shown;

    BuildImportView(BuildControls ui) {
        this.ui = ui;
    }

    /** Reads the clipboard: true when it held a build link (now on show), else a flash says why not. */
    boolean read(BuildWindow.Host host) {
        String text;
        try {
            text = Minecraft.getInstance().keyboardHandler.getClipboard();
        } catch (Throwable t) {
            text = null;
        }
        WynnBuilderLink.Build b = WynnBuilderLink.decode(text);
        if (b == null) {
            if (host != null) {
                host.flash(L.t("wynnlabs.import.bad"));
            }
            return false;
        }
        shown = LinkImport.of(GameState.current(), b);
        return true;
    }

    /** The items to build around from the link on show; it closes. */
    List<ItemData> take() {
        List<ItemData> out = shown == null ? List.of() : shown.kept();
        shown = null;
        return out;
    }

    void draw(GuiGraphics g, BuildWindow.Host host, int x, int y, int w, int h, int mx, int my) {
        LinkImport.Result r = shown;
        if (r == null) {
            return;
        }
        WynnBuilderLink.Build b = r.build();
        // the title: WynnBuilder's build, its level and data version
        String title = L.t("wynnlabs.import.title", b.level());
        Type.LABEL.draw(g, ui.font, title, x, y, Theme.INK);
        Type.META.draw(g, ui.font, b.version(), x + Type.LABEL.width(ui.font, title) + Theme.SP_3, y + 1, Theme.INK_3);
        int cy = y + Type.LABEL.lineHeight + Theme.SP_3;
        Kit.hair(g, x, cy, w, 1, Theme.LINE);
        cy += 1 + Theme.SP_3;
        int bottom = y + h - Ui.BUTTON_H - Theme.SP_4;     // the buttons' row under everything
        Locks.Problem problem = r.kept().isEmpty() ? null : Locks.problem(r.kept(), BuildWindow.planLevel(r.kept()));
        // a plan to look at needs a weapon (the link's or what you wear); a wear problem is only a warning
        boolean planOk = asPlan(r, 0) != null;
        List<String[]> notes = new ArrayList<>();          // {level, text}
        if (b.newer()) {
            notes.add(new String[] {"W", L.t("wynnlabs.import.newer")});
        }
        if (problem != null) {
            notes.add(new String[] {"W", problemLine(problem)});
        }
        if (!planOk && !r.kept().isEmpty()) {
            notes.add(new String[] {"E", L.t("wynnlabs.import.no_weapon")});
        }
        int notesH = notes.size() * (Ui.noticeHeight(1) + Theme.SP_3);
        List<String> info = info(r);
        int infoH = info.isEmpty() ? 0 : info.size() * (Type.META.lineHeight + Theme.SP_1) + Theme.SP_3;
        int rowsEnd = bottom - notesH - infoH;
        int i = 0;
        for (LinkImport.Row row : r.rows()) {
            if (cy + ROW_H > rowsEnd) {
                break;
            }
            drawRow(g, host, row, x, cy, w, i++, mx, my);
            cy += ROW_H + Theme.SP_2;
        }
        if (r.rows().isEmpty()) {
            UiEmpty.draw(g, ui.font, x, cy, w, rowsEnd - cy, true, "clipboard", L.t("wynnlabs.import.empty"), List.of(), null, null, mx, my);
        }
        int iy = rowsEnd + Theme.SP_3;
        for (String line : info) {
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, line, w), x, iy, Theme.INK_3);
            iy += Type.META.lineHeight + Theme.SP_1;
        }
        iy = bottom - notesH + Theme.SP_3;
        for (String[] n : notes) {
            iy += Ui.notice(g, ui.font, n[0].equals("E") ? Ui.Level.ERROR : Ui.Level.WARNING, List.of(n[1]), null, x, iy, w, 40, mx, my,
                    new int[1][]) + Theme.SP_3;
        }
        // the buttons, right: Cancel, Build around these (purple), the link as the plan
        int by = y + h - Ui.BUTTON_H;
        int cy2 = by + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        String cancel = L.t("wynnlabs.import.cancel");
        String ok = L.t("wynnlabs.import.confirm");
        int cw = ui.controlW("x", cancel, w / 3);
        int ow = ui.controlW("target", ok, w / 2, BuildControls.PRIMARY);
        int bx = x + w - cw;
        ui.control(g, host, bx, cy2, Ui.CHIP_H, "x", cancel, BuildControls.WOOD, false, w / 3, "wbCancel", null, null, mx, my);
        boolean can = !r.kept().isEmpty();
        List<Component> tip = List.of(L.c(can ? "wynnlabs.import.confirm_tip" : "wynnlabs.import.none").withStyle(ChatFormatting.GRAY),
                L.c("wynnlabs.import.note").withStyle(ChatFormatting.DARK_GRAY));
        ui.control(g, host, bx - Theme.SP_2 - ow, by, Ui.BUTTON_H, "target", ok, can ? BuildControls.PRIMARY : BuildControls.WOOD, false, w / 2,
                can ? "wbConfirm" : null, null, tip, mx, my);
        // or the link as it is, as the plan: to look at its stats even before you can wear it
        String plan = L.t("wynnlabs.import.as_plan");
        int pw = ui.controlW("check", plan, w / 3);
        ui.control(g, host, bx - 2 * Theme.SP_2 - ow - pw, cy2, Ui.CHIP_H, "check", plan, BuildControls.WOOD, false, w / 3,
                planOk ? "wbPlan" : null, null,
                List.of(L.c(planOk ? "wynnlabs.import.as_plan_tip" : "wynnlabs.import.no_weapon").withStyle(ChatFormatting.GRAY)), mx, my);
    }

    /** The link on show as a plan at its own level with its skill points (null: no weapon). */
    static io.github.anilken.wynnlabs.build.Plans.Build asPlan(LinkImport.Result r, long now) {
        return r == null ? null : LinkImport.asPlan(r.kept(), r.build().level(), r.build().skills(), now);
    }

    /** One piece: its item in an HD slot, its name in its tier's colour (else what it is) and slot; a tick, or why it is left out. */
    private void drawRow(GuiGraphics g, BuildWindow.Host host, LinkImport.Row row, int x, int y, int w, int i, int mx, int my) {
        WynnBuilderLink.Piece p = row.piece();
        boolean kept = row.why() == LinkImport.Why.KEPT;
        Kit.slot(g, x, y + 2, 18, 0);
        ItemStack icon = row.info() == null ? ItemStack.EMPTY : host.icon(row.info());
        if (!icon.isEmpty()) {
            g.renderItem(icon, x + 1, y + 3);
        }
        int right = x + w;
        if (kept) {
            int d = 12;
            Kit.seal(g, right - d, y + 5, d, d, Theme.GOOD);
            Art.icon(g, "check", right - d + Ui.iconIn(d, 8), y + 5 + Ui.iconIn(d, 8), 8, Theme.CREAM);
            right -= d + Theme.SP_3;
            if (!p.powders().isEmpty()) {
                int pw = Type.META.width(ui.font, p.powders());
                Type.META.draw(g, ui.font, p.powders(), right - pw, y + 6, Theme.INK_3);
                right -= pw + Theme.SP_3;
            }
        } else {
            String why = L.t(WHY[row.why().ordinal()]);
            int sw = Type.META.width(ui.font, why) + 2 * Theme.SP_2;
            Ui.seal(g, ui.font, why, right - sw, y + 5, Theme.BAD);
            right -= sw + Theme.SP_3;
        }
        int nx = x + 18 + Theme.SP_2;
        int room = right - nx;
        String name = p.kind() == WynnBuilderLink.Kind.ITEM ? (p.name() == null ? "#" + p.id() : p.name())
                : L.t(p.kind() == WynnBuilderLink.Kind.CRAFTED ? "wynnlabs.import.crafted" : "wynnlabs.import.custom");
        Component nc = row.info() != null ? Component.literal(Type.BODY.fit(ui.font, row.info().name(), room)).withStyle(row.info().tier().colour())
                : Component.literal(Type.BODY.fit(ui.font, name, room));
        Kit.text(g, ui.font, nc, nx, y, row.info() != null && kept ? Theme.INK : Theme.INK_3);
        Type.META.draw(g, ui.font, L.t(SLOTS[Math.min(p.slot(), SLOTS.length - 1)]), nx, y + Type.BODY.lineHeight, Theme.INK_3);
        if (row.info() != null) {
            host.item(x, y, w, ROW_H, row.info(), icon, List.of());
        }
    }

    /** The lines under the pieces: the tree's archetype points, the aspects, the tomes and the skill points set by hand. */
    private static List<String> info(LinkImport.Result r) {
        List<String> out = new ArrayList<>();
        WynnBuilderLink.Build b = r.build();
        if (r.tree() != null && r.tree().nodes().size() > 1) {
            List<String> arch = new ArrayList<>();
            r.tree().archetypes().forEach((a, n) -> arch.add(a + " " + n));
            out.add(L.t("wynnlabs.import.tree", r.tree().nodes().size(), String.join(" · ", arch)));
            if (!r.tree().aspects().isEmpty()) {
                out.add(L.t("wynnlabs.import.aspects", String.join(", ",
                        r.tree().aspects().stream().map(a -> a.name() + " " + a.tier()).toList())));
            }
        }
        if (!b.tomes().isEmpty()) {
            out.add(L.t("wynnlabs.import.tomes", String.join(", ", b.tomes())));
        }
        if (b.skills() != null) {
            List<String> parts = new ArrayList<>();
            for (SkillKind k : SkillKind.values()) {
                Integer v = k.ordinal() < b.skills().size() ? b.skills().get(k.ordinal()) : null;
                if (v != null) {
                    parts.add(v + " " + k.displayName().substring(0, 3));
                }
            }
            if (!parts.isEmpty()) {
                out.add(L.t("wynnlabs.import.skills", String.join(" · ", parts)));
            }
        }
        return out;
    }

    private static String problemLine(Locks.Problem p) {
        return L.t("wynnlabs.pick.not_wearable", p.text());
    }
}
