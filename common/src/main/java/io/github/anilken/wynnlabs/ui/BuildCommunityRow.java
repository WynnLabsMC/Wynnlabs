package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.TooltipListener;
import io.github.anilken.wynnlabs.build.CommunityBuilds;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Tier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * An opened community build, in place under its row: its name, levels, source and note, the
 * changes against what you wear, its nine pieces in three groups (armour, accessories, weapon),
 * each the item's picture in a slot ringed in its tier's colour with its name under it; an owned
 * piece carries a badge (equipped, in the bag, on bank page N), a missing one is faded in a dashed
 * square with its Trade Market price on it, or for a crafted piece its cost to craft and a hammer
 * (a click opens the recipe in Crafting). Then a key to the badges, the skill points the pieces
 * need, what is left to buy, "Set as goal" and "Copy link".
 */
final class BuildCommunityRow {

    private static final int SLOT = 20;
    private static final int BADGE = 8;
    /** The skills' element colours: earth, thunder, water, fire, air. */
    private static final int[] ELEMENT = {0xFF3F7A2A, 0xFF9A7A0A, 0xFF2A6E9A, 0xFFA33A22, 0xFF66767E};
    private static final String[] GROUPS = {"wynnlabs.community.armour", "wynnlabs.community.accessories", "wynnlabs.community.weapon"};

    private final BuildCommunityView view;
    private final BuildControls ui;

    BuildCommunityRow(BuildCommunityView view, BuildControls ui) {
        this.view = view;
        this.ui = ui;
    }

    // ---- shared with the list ----

    /** A piece's picture: the item's, a crafted piece's type, else none. */
    static ItemStack icon(BuildWindow.Host host, CommunityBuilds.Piece p) {
        if (p.info() != null) {
            return host.icon(p.info());
        }
        if (p.recipe() != null) {
            return CraftIcons.type(p.recipe().type());
        }
        return ItemStack.EMPTY;
    }

    /** Where a piece is, as the badges say it ("Equipped", "In bag", "Bank page 3", "Missing"). */
    static String where(CommunityBuilds.Piece p) {
        return switch (p.place()) {
            case EQUIPPED -> L.t("wynnlabs.community.equipped");
            case BAG -> L.t("wynnlabs.community.in_bag");
            case BANK -> L.t("wynnlabs.community.bank_page", p.bankPage());
            case MISSING -> L.t("wynnlabs.community.missing");
        };
    }

    /** A piece's tooltip lines (the item's own tooltip comes first in the game). */
    static List<Component> pieceTip(CommunityBuilds.Piece p) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(p.name()).withStyle(p.crafted() ? ChatFormatting.DARK_AQUA
                : p.info() != null ? p.info().tier().colour() : ChatFormatting.WHITE));
        tip.add(Component.literal(where(p)).withStyle(p.owned() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        if (p.recipe() != null) {
            RecipeCode.Recipe r = p.recipe();
            CraftData.Recipe rec = CraftData.find(r.type(), r.minLevel());
            if (rec != null) {
                tip.add(L.c("wynnlabs.community.crafted_levels", rec.minLevel(), rec.maxLevel()).withStyle(ChatFormatting.GRAY));
            }
            List<String> ings = r.slots().stream().filter(s -> s != null && !s.isBlank()).toList();
            if (!ings.isEmpty()) {
                tip.add(L.c("wynnlabs.community.ingredients").withStyle(ChatFormatting.GRAY));
                for (String s : ings) {
                    tip.add(Component.literal(" " + s).withStyle(ChatFormatting.WHITE));
                }
            }
            if (!p.owned()) {
                tip.add(p.craftCost() != null
                        ? L.c("wynnlabs.community.craft_cost", BuildCommunityView.price(p.craftCost())).withStyle(ChatFormatting.GOLD)
                        : L.c("wynnlabs.community.craft_cost_none").withStyle(ChatFormatting.GRAY));
                tip.add(L.c("wynnlabs.community.craft_tip").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (!p.owned()) {
            tip.add(p.price() != null
                    ? L.c("wynnlabs.community.market_price", BuildCommunityView.price(Math.round(p.price()))).withStyle(ChatFormatting.GOLD)
                    : L.c("wynnlabs.community.no_price").withStyle(ChatFormatting.GRAY));
        }
        return tip;
    }

    /** A dashed outline: every other 2 px of each side. */
    static void dashed(GuiGraphics g, int x, int y, int w, int h, int colour) {
        for (int i = 0; i < w; i += 4) {
            int e = Math.min(i + 2, w);
            g.fill(x + i, y, x + e, y + 1, colour);
            g.fill(x + i, y + h - 1, x + e, y + h, colour);
        }
        for (int i = 0; i < h; i += 4) {
            int e = Math.min(i + 2, h);
            g.fill(x, y + i, x + 1, y + e, colour);
            g.fill(x + w - 1, y + i, x + w, y + e, colour);
        }
    }

    /** A tier's ink on paper (crafted pieces in the crafted colour). */
    static int tierInk(CommunityBuilds.Piece p) {
        if (p.crafted()) {
            return Theme.TIER_CRAFTED;
        }
        return p.info() == null ? Theme.INK_3 : BankMenus.tierInk(p.info().tier() == null ? Tier.NORMAL : p.info().tier());
    }

    // ---- the opened row ----

    private boolean twoLines(CommunityBuilds.Entry e, CommunityBuilds.Compare c, int w) {
        return chipsW(c) + buttonsW(e, c) + Theme.SP_5 > w;
    }

    private int chipsW(CommunityBuilds.Compare c) {
        int cw = Type.META.width(ui.font, L.t("wynnlabs.community.skill_points")) + Theme.SP_3;
        for (SkillKind k : SkillKind.values()) {
            cw += Type.META.width(ui.font, skill(k, c)) + 2 * Theme.SP_2 + Theme.SP_2;
        }
        return cw;
    }

    private String skill(SkillKind k, CommunityBuilds.Compare c) {
        int v = k.ordinal() < c.need().length ? c.need()[k.ordinal()] : 0;
        return k.displayName().substring(0, 3) + " " + v;
    }

    private int buttonsW(CommunityBuilds.Entry e, CommunityBuilds.Compare c) {
        int w = Type.META.width(ui.font, BuildCommunityView.toBuy(c)) + Theme.SP_3;
        w += ui.controlW("target", L.t("wynnlabs.community.set_goal"), 200, BuildControls.PRIMARY) + Theme.SP_2;
        w += ui.controlW("copy", L.t("wynnlabs.community.copy_link"), 200);
        if (craftable(c) != null) {
            w += Theme.SP_2 + ui.controlW("hammer", L.t("wynnlabs.community.craft"), 200);
        }
        return w;
    }

    /** The first missing crafted piece's recipe, for "Craft"; null for none. */
    private static RecipeCode.Recipe craftable(CommunityBuilds.Compare c) {
        for (CommunityBuilds.Piece p : c.pieces()) {
            if (p.crafted() && !p.owned()) {
                return p.recipe();
            }
        }
        return null;
    }

    private boolean problem(CommunityBuilds.Compare c) {
        return problemText(c) != null;
    }

    private static String problemText(CommunityBuilds.Compare c) {
        int lv = GameState.current().level();
        if (lv > 0 && c.needLevel() > lv) {
            return L.t("wynnlabs.community.needs_level", c.needLevel());
        }
        return c.problem() == null ? null : L.t("wynnlabs.community.cannot_wear", c.problem().text());
    }

    int height(CommunityBuilds.Entry e, CommunityBuilds.Compare c, int w, boolean tags) {
        int h = Type.LABEL.lineHeight + Theme.SP_2;                 // header
        if (tags) {
            h += Ui.SEAL_H + Theme.SP_2;                              // the tags, without a tags column
        }
        h += Ui.SEAL_H + Theme.SP_3;                                  // the changes
        h += Type.META.lineHeight + Theme.SP_1 + SLOT + Theme.SP_1 + Type.META.lineHeight + Theme.SP_3;   // the tiles
        h += Type.META.lineHeight + Theme.SP_2;                       // the key
        if (problem(c)) {
            h += Type.META.lineHeight + Theme.SP_2;
        }
        h += Ui.BUTTON_H;
        if (twoLines(e, c, w)) {
            h += Ui.SEAL_H + Theme.SP_2;
        }
        return h;
    }

    void draw(GuiGraphics g, BuildWindow.Host host, CommunityBuilds.Entry e, CommunityBuilds.Compare c, int x, int y, int w,
            boolean tags, List<Runnable> over, int mx, int my) {
        int cy = y;
        // the header: name · levels · source · note
        String name = e.name();
        int nw = Math.min(Type.LABEL.width(ui.font, name), w / 2);
        Type.LABEL.draw(g, ui.font, Type.LABEL.fit(ui.font, name, nw), x, cy, Theme.INK);
        String rest = " · " + L.t("wynnlabs.community.levels", e.levelMin(), e.levelMax()) + " · " + BuildCommunityView.source(e)
                + (e.description().isBlank() ? "" : " · " + e.description());
        Type.META.draw(g, ui.font, Type.META.fit(ui.font, rest, w - nw), x + nw, cy + 1, Theme.INK_3);
        host.tip(x + nw, cy, w - nw, Type.LABEL.lineHeight, List.of(Component.literal(e.description()).withStyle(ChatFormatting.GRAY)));
        cy += Type.LABEL.lineHeight + Theme.SP_2;
        if (tags) {
            // the compact menu has no tags column: the play styles and the outdated mark here
            int tx = x;
            List<String> ts = new ArrayList<>();
            if (e.outdated()) {
                ts.add("outdated");
            }
            ts.addAll(e.tags());
            for (String t : ts) {
                String label = L.t(t.equals("outdated") ? "wynnlabs.community.outdated" : "wynnlabs.community.tag." + t);
                int lw = Type.META.width(ui.font, label) + 2 * Theme.SP_2;
                if (tx + lw > x + w) {
                    break;
                }
                Ui.seal(g, ui.font, label, tx, cy, t.equals("outdated") ? Theme.WARN : Theme.SEAL_2);
                if (t.equals("outdated")) {
                    host.tip(tx, cy, lw, Ui.SEAL_H, List.of(L.c("wynnlabs.community.outdated_tip", e.checked()).withStyle(ChatFormatting.GRAY)));
                }
                tx += lw + Theme.SP_2;
            }
            cy += Ui.SEAL_H + Theme.SP_2;
        }

        // the changes against what you wear
        int px = x;
        boolean any = false;
        for (int i = 0; i < BuildCommunityView.STATS.length; i++) {
            Double v = BuildCommunityView.statOf(c, i);
            if (v == null) {
                continue;
            }
            any = true;
            String t = L.t("wynnlabs.community.stat." + BuildCommunityView.STATS[i]) + " " + BuildCommunityView.statText(v, i);
            int colour = Math.abs(v) < 0.5 ? Theme.INK_3 : v > 0 ? Theme.SEAL_3 : Theme.BAD;
            px += Ui.seal(g, ui.font, t, px, cy, colour) + Theme.SP_2;
        }
        if (!any) {
            Type.META.draw(g, ui.font, L.t(BuildCommunityView.canGoal(e) ? "wynnlabs.community.no_compare" : "wynnlabs.community.other_class"),
                    x, Type.META.yIn(cy, Ui.SEAL_H), Theme.INK_3);
        }
        cy += Ui.SEAL_H + Theme.SP_3;

        // the tiles in three groups
        int gap = Theme.SP_4;
        int tw = (w - 2 * gap) / 9;
        int[] colX = new int[9];
        for (int i = 0; i < 9; i++) {
            colX[i] = x + i * tw + (i >= 4 ? gap : 0) + (i >= 8 ? gap : 0);
        }
        int[] groupAt = {0, 4, 8};
        for (int gi = 0; gi < 3; gi++) {
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, L.t(GROUPS[gi]), gi == 2 ? tw + Theme.SP_4 : 4 * tw), colX[groupAt[gi]], cy, Theme.INK_3);
        }
        cy += Type.META.lineHeight + Theme.SP_1;
        CommunityBuilds.Piece[] bySlot = new CommunityBuilds.Piece[9];
        for (CommunityBuilds.Piece p : c.pieces()) {
            if (p.slot() >= 0 && p.slot() < 9) {
                bySlot[p.slot()] = p;
            }
        }
        for (int i = 0; i < 9; i++) {
            tile(g, host, bySlot[i], colX[i], cy, tw, over, mx, my);
        }
        cy += SLOT + Theme.SP_1 + Type.META.lineHeight + Theme.SP_3;

        // the key to the badges
        int kx = x;
        kx = keyItem(g, "check", Theme.GOOD, L.t("wynnlabs.community.equipped"), kx, cy);
        kx = keyItem(g, "backpack", Theme.WOOD_LO, L.t("wynnlabs.community.in_bag"), kx, cy);
        kx = keyItem(g, "building-bank", Theme.LINK, L.t("wynnlabs.community.bank_page_key"), kx, cy);
        String miss = L.t("wynnlabs.community.key_missing");
        Type.META.draw(g, ui.font, Type.META.fit(ui.font, miss, x + w - kx), kx, cy, Theme.INK_3);
        cy += Type.META.lineHeight + Theme.SP_2;
        String prob = problemText(c);
        if (prob != null) {
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, prob, w), x, cy, Theme.WARN);
            cy += Type.META.lineHeight + Theme.SP_2;
        }

        // skill points, then what is left to buy and the actions
        boolean two = twoLines(e, c, w);
        int sy = two ? cy : cy + (Ui.BUTTON_H - Ui.SEAL_H) / 2;
        String sp = L.t("wynnlabs.community.skill_points");
        Type.META.draw(g, ui.font, sp, x, Type.META.yIn(sy, Ui.SEAL_H), Theme.INK_2);
        int sx = x + Type.META.width(ui.font, sp) + Theme.SP_3;
        for (SkillKind k : SkillKind.values()) {
            sx += Ui.seal(g, ui.font, skill(k, c), sx, sy, ELEMENT[k.ordinal()]) + Theme.SP_2;
        }
        if (c.linkPoints() != null) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < c.linkPoints().size() && i < 5; i++) {
                Integer v = c.linkPoints().get(i);
                parts.add(SkillKind.values()[i].displayName().substring(0, 3) + " " + (v == null ? "—" : v));
            }
            host.tip(x, sy, sx - x, Ui.SEAL_H, List.of(L.c("wynnlabs.community.points_tip").withStyle(ChatFormatting.GRAY),
                    L.c("wynnlabs.community.link_points", String.join(" · ", parts)).withStyle(ChatFormatting.DARK_GRAY)));
        } else {
            host.tip(x, sy, sx - x, Ui.SEAL_H, List.of(L.c("wynnlabs.community.points_tip").withStyle(ChatFormatting.GRAY)));
        }
        int by = two ? cy + Ui.SEAL_H + Theme.SP_2 : cy;
        int bx = x + w;
        int cy2 = by + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        String copy = L.t("wynnlabs.community.copy_link");
        int cw = ui.controlW("copy", copy, 200);
        bx -= cw;
        ui.control(g, host, bx, cy2, Ui.CHIP_H, "copy", copy, BuildControls.WOOD, false, 200, "c.copy", e.id(),
                List.of(L.c("wynnlabs.community.copy_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        RecipeCode.Recipe craft = craftable(c);
        if (craft != null) {
            String ct = L.t("wynnlabs.community.craft");
            int crw = ui.controlW("hammer", ct, 200);
            bx -= crw + Theme.SP_2;
            ui.control(g, host, bx, cy2, Ui.CHIP_H, "hammer", ct, BuildControls.WOOD, false, 200, "c.craft", craft,
                    List.of(L.c("wynnlabs.community.craft_tip").withStyle(ChatFormatting.GRAY)), mx, my);
        }
        String goal = L.t("wynnlabs.community.set_goal");
        int gw = ui.controlW("target", goal, 200, BuildControls.PRIMARY);
        bx -= gw + Theme.SP_2;
        boolean can = BuildCommunityView.canGoal(e);
        ui.control(g, host, bx, by, Ui.BUTTON_H, "target", goal, can ? BuildControls.PRIMARY : BuildControls.WOOD, false, 200,
                can ? "c.goal" : null, e.id(),
                List.of(L.c(can ? "wynnlabs.community.set_goal_tip" : "wynnlabs.community.set_goal_class").withStyle(ChatFormatting.GRAY)), mx, my);
        String left = BuildCommunityView.toBuy(c);
        int lw = Type.META.width(ui.font, left);
        bx -= lw + Theme.SP_3;
        Type.META.draw(g, ui.font, left, bx, Type.META.yIn(by, Ui.BUTTON_H),
                c.missing() == 0 ? Theme.GOOD : c.knownCost() > 0 ? Theme.PRICE : Theme.INK_3);
    }

    private int keyItem(GuiGraphics g, String icon, int colour, String text, int x, int y) {
        int d = BADGE;
        int iy = Type.META.onCaps(y, d);
        Kit.seal(g, x, iy, d, d, colour);
        Art.icon(g, icon, x + 1, iy + 1, d - 2, Theme.CREAM);
        Type.META.draw(g, ui.font, text, x + d + Theme.SP_1, y, Theme.INK_3);
        return x + d + Theme.SP_1 + Type.META.width(ui.font, text) + Theme.SP_4;
    }

    /** One piece's tile: the slot ringed in the tier's colour, the picture, the name under it, its badge or price. */
    private void tile(GuiGraphics g, BuildWindow.Host host, CommunityBuilds.Piece p, int x, int y, int tw, List<Runnable> over, int mx, int my) {
        int sx = x + (tw - Theme.SP_2 - SLOT) / 2;
        if (p == null) {
            Kit.slot(g, sx, y, SLOT, 0);
            return;
        }
        int ink = tierInk(p);
        Kit.slot(g, sx, y, SLOT, p.owned() ? ink : 0);
        ItemStack st = icon(host, p);
        if (!st.isEmpty()) {
            g.renderItem(st, sx + 2, y + 2);
        }
        String n = Type.META.fit(ui.font, p.name(), tw - Theme.SP_2);
        Type.META.draw(g, ui.font, n, x + (tw - Theme.SP_2 - Type.META.width(ui.font, n)) / 2, y + SLOT + Theme.SP_1, ink);
        if (p.info() != null) {
            host.item(sx, y, SLOT, SLOT, p.info(), st, pieceTip(p).subList(1, pieceTip(p).size()));
        } else {
            host.tip(sx, y, SLOT, SLOT, pieceTip(p));
        }
        if (p.crafted() && !p.owned()) {
            ui.btn(sx, y, SLOT, SLOT, "c.craft", p.recipe());
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        over.add(() -> {
            if (!p.owned()) {
                g.fill(sx + 1, y + 1, sx + SLOT - 1, y + SLOT - 1, 0x88EADCB4);
                dashed(g, sx, y, SLOT, SLOT, ink == Theme.INK_3 ? Theme.INK_3 : ink);
                String price = p.crafted() ? p.craftCost() == null ? null : "~" + BuildCommunityView.price(p.craftCost())
                        : p.price() == null ? null : BuildCommunityView.price(Math.round(p.price()));
                if (p.crafted()) {
                    Art.icon(g, "hammer", sx + 1, y + 1, 6, Theme.TIER_CRAFTED);
                }
                if (price != null) {
                    Component c = Component.literal(price);
                    TooltipListener.outlined(g, font, c, font.width(c), sx + SLOT - 1, y + SLOT - 1, 0.42f,
                            p.crafted() ? 0xFFFFC060 : 0xFF55FF55, true);
                }
                return;
            }
            // the badge at the picture's top-right corner
            int bx = sx + SLOT - BADGE + 2;
            int by = y - 2;
            switch (p.place()) {
                case EQUIPPED -> {
                    Kit.seal(g, bx, by, BADGE, BADGE, Theme.GOOD);
                    Art.icon(g, "check", bx + 1, by + 1, BADGE - 2, Theme.CREAM);
                }
                case BAG -> {
                    Kit.seal(g, bx, by, BADGE, BADGE, Theme.WOOD_LO);
                    Art.icon(g, "backpack", bx + 1, by + 1, BADGE - 2, Theme.CREAM);
                }
                default -> {
                    String num = String.valueOf(p.bankPage());
                    int nw = (int) Math.ceil(font.width(num) * 0.5f) + 1;
                    int bw = BADGE + nw;
                    bx = sx + SLOT - bw + 2;
                    Kit.seal(g, bx, by, bw, BADGE, Theme.LINK);
                    Art.icon(g, "building-bank", bx + 1, by + 1, BADGE - 2, Theme.CREAM);
                    TooltipListener.outlined(g, font, Component.literal(num), font.width(num), bx + BADGE - 1, by + 2, 0.5f,
                            0xFFF3E6C4, false);
                }
            }
        });
        if (BuildControls.in(mx, my, sx, y, SLOT, SLOT) && p.crafted() && !p.owned()) {
            Kit.outline(g, sx - 1, y - 1, SLOT + 2, SLOT + 2, Theme.ACCENT);
        }
    }
}
