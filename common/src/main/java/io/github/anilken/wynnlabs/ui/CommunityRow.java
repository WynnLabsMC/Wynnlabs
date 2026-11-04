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
 * An opened community build: a bordered panel right under its row. A header (name · levels ·
 * source · note) with the build's own DPS, EHP, mana and speed as pills; its nine pieces in three
 * labelled groups (Armour, Accessories, Weapon), each a tile ringed in the tier's colour with the
 * item's picture and name; an owned piece carries a round badge at the picture's corner
 * (equipped, in the bag, its bank page), a missing one sits in a dashed, faded tile with its
 * Trade Market price on the picture, or for a crafted piece its cost to craft and a hammer (a
 * click opens the recipe in Crafting). Then a key to the badges and the bottom line: the skill
 * points the pieces need, what is left to buy, "Set as goal" and "Copy link"; under it the "All
 * stats" switch, which opens the build's own totals in three columns (Offence, Defence, Utility).
 * Nothing here is set against what you wear.
 */
final class CommunityRow {

    private static final int EDGE = 0xFF6B4F2A;
    private static final int FACE = 0xFFF5EAD0;
    private static final int TILE = 0xFFEFE1BD;
    private static final int TILE_MISSING = 0xFFF3E6C4;
    /** A stat pill's face (14.1k DPS). */
    static final int PILL = 0xFFECDCB6;
    private static final int PRICE_INK = 0xFFFFE9A8;
    /** The badges: equipped, in the bag, on a bank page. */
    private static final int EQUIPPED = 0xFF6F8A3E;
    private static final int BAG = 0xFF8A6A3E;
    private static final int BANK = 0xFF4F6F8A;
    /** The skills' element colours: earth, thunder, water, fire, air. */
    private static final int[] ELEMENT = {0xFF6F8A3E, 0xFFA08C1E, 0xFF4F86AD, 0xFFB8573A, 0xFF8A8A8A};
    private static final String[] GROUPS = {"wynnlabs.community.armour", "wynnlabs.community.accessories", "wynnlabs.community.weapon"};
    /** The elements as the game names them, earth to air (the defence chips). */
    private static final String[] ELEMENT_NAMES = {"Earth", "Thunder", "Water", "Fire", "Air"};
    /** The attack speed tiers as the game names them, slowest first. */
    private static final String[] ATTACK_SPEEDS = {"Super Slow", "Very Slow", "Slow", "Normal", "Fast", "Very Fast", "Super Fast"};
    /** An All stats column's face and the rule between its rows. */
    private static final int STATS_FACE = 0xFFEFE1BD;
    private static final int STATS_RULE = 0xFFDCC79C;
    private static int pad(boolean compact) {
        return compact ? Theme.SP_2 : Theme.SP_4;
    }

    private final BuildControls ui;

    CommunityRow(BuildControls ui) {
        this.ui = ui;
    }

    // ---- shared with the list ----

    /** The pieces by link place (0 helmet … 8 weapon); null where the link holds none. */
    static CommunityBuilds.Piece[] bySlot(CommunityBuilds.Compare c) {
        CommunityBuilds.Piece[] out = new CommunityBuilds.Piece[9];
        for (CommunityBuilds.Piece p : c.pieces()) {
            if (p.slot() >= 0 && p.slot() < 9) {
                out[p.slot()] = p;
            }
        }
        return out;
    }

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

    /** Where a piece is ("Equipped", "In bag", "Bank page 3", "Missing"). */
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
                        ? L.c("wynnlabs.community.craft_cost", CommunityPage.price(p.craftCost())).withStyle(ChatFormatting.GOLD)
                        : L.c("wynnlabs.community.craft_cost_none").withStyle(ChatFormatting.GRAY));
                tip.add(L.c("wynnlabs.community.craft_tip").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (!p.owned()) {
            tip.add(p.price() != null
                    ? L.c("wynnlabs.community.market_price", CommunityPage.price(Math.round(p.price()))).withStyle(ChatFormatting.GOLD)
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

    /** A piece's tier ink on paper (crafted pieces in the crafted colour). */
    static int tierInk(CommunityBuilds.Piece p) {
        if (p.crafted()) {
            return Theme.TIER_CRAFTED;
        }
        return p.info() == null ? Theme.INK_3 : BankMenus.tierInk(p.info().tier() == null ? Tier.NORMAL : p.info().tier());
    }

    // ---- sizes ----

    private static int slot(boolean compact) {
        return compact ? 20 : 24;
    }

    /** A tile: the slot and the name under it, inside its ring. */
    private static int tileH(boolean compact) {
        return 2 + Theme.SP_2 + slot(compact) + Theme.SP_1 + Type.META.lineHeight + Theme.SP_2;
    }

    private int headerW(CommunityBuilds.Compare c) {
        int w = 0;
        for (int i = 0; i < CommunityPage.STATS.length; i++) {
            Double v = CommunityPage.statOf(c, i);
            if (v != null) {
                w += pillW(i, v) + Theme.SP_2;
            }
        }
        return w;
    }

    private int pillW(int i, double v) {
        return Type.META.width(ui.font, CommunityPage.pillText(v, i)) + 2 * Theme.SP_3;
    }

    /** The pills on their own line when they do not fit beside the header's text. */
    private boolean pillsBelow(CommunityBuilds.Entry e, CommunityBuilds.Compare c, int inner) {
        return headerW(c) + Type.LABEL.width(ui.font, e.name()) + 60 > inner;
    }

    private int chipsW(CommunityBuilds.Compare c) {
        int cw = Type.META.width(ui.font, L.t("wynnlabs.community.skill_points")) + Theme.SP_3;
        for (SkillKind k : SkillKind.values()) {
            cw += Type.META.width(ui.font, skill(k, c)) + 2 * Theme.SP_2 + Theme.SP_1;
        }
        return cw;
    }

    private String skill(SkillKind k, CommunityBuilds.Compare c) {
        int v = k.ordinal() < c.need().length ? c.need()[k.ordinal()] : 0;
        return k.displayName().substring(0, 3) + " " + v;
    }

    private int buttonsW(CommunityBuilds.Compare c) {
        int w = Type.META.width(ui.font, CommunityPage.toBuy(c)) + Theme.SP_3;
        w += Ui.buttonWidth(ui.font, L.t("wynnlabs.community.set_goal")) + Theme.SP_2;
        w += copyW(L.t("wynnlabs.community.copy_link"));
        if (craftable(c) != null) {
            w += Theme.SP_2 + copyW(L.t("wynnlabs.community.craft"));
        }
        return w;
    }

    private int copyW(String text) {
        return Type.BODY.width(ui.font, text) + 2 * Theme.SP_4;
    }

    private boolean twoLines(CommunityBuilds.Compare c, int inner) {
        return chipsW(c) + buttonsW(c) + Theme.SP_5 > inner;
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

    private static String problemText(CommunityBuilds.Compare c) {
        int lv = GameState.current().level();
        if (lv > 0 && c.needLevel() > lv) {
            return L.t("wynnlabs.community.needs_level", c.needLevel());
        }
        return c.problem() == null ? null : L.t("wynnlabs.community.cannot_wear", c.problem().text());
    }

    /** Whether the tags (no tags column) go on a line of their own, not after the pills. */
    private boolean tagLine(CommunityBuilds.Entry e, CommunityBuilds.Compare c, int inner) {
        return !pillsBelow(e, c, inner) || headerW(c) + tagsW(e) > inner;
    }

    private int tagsW(CommunityBuilds.Entry e) {
        int w = 0;
        for (String t : tagList(e)) {
            w += Type.META.width(ui.font, tagLabel(t)) + 2 * Theme.SP_2 + Theme.SP_1;
        }
        return w;
    }

    private static List<String> tagList(CommunityBuilds.Entry e) {
        List<String> ts = new ArrayList<>();
        if (e.outdated()) {
            ts.add("outdated");
        }
        ts.addAll(e.tags());
        return ts;
    }

    private static String tagLabel(String t) {
        return L.t(t.equals("outdated") ? "wynnlabs.community.outdated" : "wynnlabs.community.tag." + t);
    }

    int height(CommunityBuilds.Entry e, CommunityBuilds.Compare c, int w, boolean tags, boolean compact, boolean stats) {
        int pad = pad(compact);
        int inner = w - 2 * pad;
        int h = 2 + pad + Type.LABEL.lineHeight;
        if (pillsBelow(e, c, inner)) {
            h += Theme.SP_2 + Type.META.lineHeight + 4;
        }
        if (tags && tagLine(e, c, inner)) {
            h += Theme.SP_2 + Type.META.lineHeight + 2;
        }
        h += (compact ? Theme.SP_3 : Theme.SP_4) + Type.META.lineHeight + Theme.SP_1 + tileH(compact) + Theme.SP_3;   // the groups
        h += Type.META.lineHeight + Theme.SP_3;                                                // the key
        if (problemText(c) != null) {
            h += Type.META.lineHeight + Theme.SP_2;
        }
        h += Ui.BUTTON_H;
        if (twoLines(c, inner)) {
            h += Ui.SEAL_H + Theme.SP_2;
        }
        if (c.totals() != null) {
            h += Theme.SP_3 + toggleH();
            if (stats) {
                h += Theme.SP_2 + stats(null, c.totals(), 0, 0, inner, compact);
            }
        }
        return h + pad + 2;
    }

    void draw(GuiGraphics g, BuildWindow.Host host, CommunityPage page, CommunityBuilds.Entry e, CommunityBuilds.Compare c,
            int x, int y, int w, boolean tags, boolean compact, List<Runnable> over, int mx, int my) {
        int h = height(e, c, w, tags, compact, page.allStats);
        // the panel: a rounded light face with a dark ring
        Kit.box(g, x, y, w, h, FACE, EDGE);
        int pad = pad(compact);
        int ix = x + pad;
        int inner = w - 2 * pad;
        int cy = y + pad;

        // the header: name · levels · source · note, the pills at its right (or under it)
        boolean below = pillsBelow(e, c, inner);
        int pillsW = headerW(c);
        int textRoom = below ? inner : inner - pillsW - Theme.SP_3;
        String name = Type.LABEL.fit(ui.font, e.name(), textRoom / 2 + textRoom / 4);
        Type.LABEL.draw(g, ui.font, name, ix, cy, Theme.INK);
        int nw = Type.LABEL.width(ui.font, name);
        String rest = " · " + L.t("wynnlabs.community.levels", e.levelMin(), e.levelMax()) + " · " + CommunityPage.source(e)
                + (e.description().isBlank() ? "" : " · " + e.description());
        Type.META.draw(g, ui.font, Type.META.fit(ui.font, rest, textRoom - nw), ix + nw, cy + 1, Theme.INK_3);
        if (!e.description().isBlank()) {
            host.tip(ix + nw, cy, textRoom - nw, Type.LABEL.lineHeight, List.of(Component.literal(e.description()).withStyle(ChatFormatting.GRAY)));
        }
        int py = cy;
        int px = ix + inner - pillsW;
        if (below) {
            cy += Type.LABEL.lineHeight + Theme.SP_2;
            py = cy;
            px = ix;
        }
        for (int i = 0; i < CommunityPage.STATS.length; i++) {
            Double v = CommunityPage.statOf(c, i);
            if (v == null) {
                continue;
            }
            int pw = pillW(i, v);
            int ph = Type.META.lineHeight + 4;
            Kit.box(g, px, py, pw, ph, PILL, 0);
            Type.META.draw(g, ui.font, CommunityPage.pillText(v, i), px + Theme.SP_3, Type.META.yIn(py, ph), Theme.INK_2);
            px += pw + Theme.SP_2;
        }
        cy += below ? Type.META.lineHeight + 4 : Type.LABEL.lineHeight;
        if (pillsW == 0 && !below) {
            String why = L.t("wynnlabs.community.no_totals");
            int ww = Type.META.width(ui.font, why);
            Type.META.draw(g, ui.font, why, ix + inner - ww, y + pad + 1, Theme.INK_3);
        }
        if (tags) {
            // the small menu has no tags column: the play styles and the outdated mark here, after the pills when they fit
            boolean own = tagLine(e, c, inner);
            int tx = own ? ix : px;
            int ty = own ? cy + Theme.SP_2 : py + 1;
            for (String t : tagList(e)) {
                String label = tagLabel(t);
                int lw = Type.META.width(ui.font, label) + 2 * Theme.SP_2;
                if (tx + lw > ix + inner) {
                    break;
                }
                page.tag(g, label, tx, ty, t.equals("outdated") ? Theme.WARN : Theme.INK_2);
                tx += lw + Theme.SP_1;
            }
            if (own) {
                cy += Theme.SP_2 + Type.META.lineHeight + 2;
            }
        }
        cy += compact ? Theme.SP_3 : Theme.SP_4;

        // the tiles in three labelled groups
        int gap = Theme.SP_4;
        int pitch = Math.min(compact ? 40 : 58, (inner - 2 * gap) / 9);
        int tw = pitch - Theme.SP_1;
        int[] colX = new int[9];
        for (int i = 0; i < 9; i++) {
            colX[i] = ix + i * pitch + (i >= 4 ? gap : 0) + (i >= 8 ? gap : 0);
        }
        int[] groupAt = {0, 4, 8};
        for (int gi = 0; gi < 3; gi++) {
            int room = gi == 2 ? tw + 2 * gap : 4 * pitch;
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, L.t(GROUPS[gi]), room), colX[groupAt[gi]], cy, Theme.INK_3);
        }
        cy += Type.META.lineHeight + Theme.SP_1;
        CommunityBuilds.Piece[] by = bySlot(c);
        for (int i = 0; i < 9; i++) {
            tile(g, host, page, by[i], colX[i], cy, tw, compact, over, mx, my);
        }
        cy += tileH(compact) + Theme.SP_3;

        // the key to the badges
        int kx = ix;
        kx = keyItem(g, "check", null, EQUIPPED, L.t("wynnlabs.community.equipped"), kx, cy);
        kx = keyItem(g, "backpack", null, BAG, L.t("wynnlabs.community.in_bag"), kx, cy);
        kx = keyItem(g, null, "3", BANK, L.t("wynnlabs.community.bank_page_key"), kx, cy);
        String miss = L.t("wynnlabs.community.key_missing");
        Type.META.draw(g, ui.font, Type.META.fit(ui.font, miss, ix + inner - kx), kx, cy, Theme.INK_3);
        cy += Type.META.lineHeight + Theme.SP_3;
        String prob = problemText(c);
        if (prob != null) {
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, prob, inner), ix, cy, Theme.WARN);
            cy += Type.META.lineHeight + Theme.SP_2;
        }

        // the bottom line: skill points, then what is left to buy and the actions
        boolean two = twoLines(c, inner);
        int sy = two ? cy : cy + (Ui.BUTTON_H - Ui.SEAL_H) / 2;
        String sp = L.t("wynnlabs.community.skill_points");
        Type.META.draw(g, ui.font, sp, ix, Type.META.yIn(sy, Ui.SEAL_H), Theme.INK_3);
        int sx = ix + Type.META.width(ui.font, sp) + Theme.SP_3;
        for (SkillKind k : SkillKind.values()) {
            sx += Ui.seal(g, ui.font, skill(k, c), sx, sy, ELEMENT[k.ordinal()]) + Theme.SP_1;
        }
        List<Component> ptip = new ArrayList<>(List.of(L.c("wynnlabs.community.points_tip").withStyle(ChatFormatting.GRAY)));
        if (c.linkPoints() != null) {
            List<String> parts = new ArrayList<>();
            for (int i = 0; i < c.linkPoints().size() && i < 5; i++) {
                Integer v = c.linkPoints().get(i);
                parts.add(SkillKind.values()[i].displayName().substring(0, 3) + " " + (v == null ? "—" : v));
            }
            ptip.add(L.c("wynnlabs.community.link_points", String.join(" · ", parts)).withStyle(ChatFormatting.DARK_GRAY));
        }
        host.tip(ix, sy, sx - ix, Ui.SEAL_H, ptip);
        int by0 = two ? cy + Ui.SEAL_H + Theme.SP_2 : cy;
        int bx = ix + inner;
        String copy = L.t("wynnlabs.community.copy_link");
        bx -= copyW(copy);
        flatButton(g, host, copy, bx, by0, "c.copy", e.id(), L.t("wynnlabs.community.copy_tip"), mx, my);
        RecipeCode.Recipe craft = craftable(c);
        if (craft != null) {
            String ct = L.t("wynnlabs.community.craft");
            bx -= copyW(ct) + Theme.SP_2;
            flatButton(g, host, ct, bx, by0, "c.craft", craft, L.t("wynnlabs.community.craft_tip"), mx, my);
        }
        String goal = L.t("wynnlabs.community.set_goal");
        int gw = Ui.buttonWidth(ui.font, goal);
        bx -= gw + Theme.SP_2;
        boolean can = CommunityPage.canGoal(e);
        if (can) {
            // the page's one primary action: purple, its label cream
            Kit.pillPrimary(g, bx, by0, gw, Ui.BUTTON_H, BuildControls.in(mx, my, bx, by0, gw, Ui.BUTTON_H));
            Type.LABEL.draw(g, ui.font, goal, bx + (gw - Type.LABEL.width(ui.font, goal)) / 2, Type.LABEL.yIn(by0, Ui.BUTTON_H),
                    Theme.CREAM);
            ui.btn(bx, by0, gw, Ui.BUTTON_H, "c.goal", e.id());
        } else {
            flatButton(g, host, goal, bx, by0, null, null, null, -1, -1);
        }
        host.tip(bx, by0, gw, Ui.BUTTON_H,
                List.of(L.c(can ? "wynnlabs.community.set_goal_tip" : "wynnlabs.community.set_goal_class").withStyle(ChatFormatting.GRAY)));
        String left = CommunityPage.toBuy(c);
        int lw = Type.META.width(ui.font, left);
        bx -= lw + Theme.SP_3;
        Type.META.draw(g, ui.font, left, bx, Type.META.yIn(by0, Ui.BUTTON_H),
                c.missing() == 0 ? Theme.GOOD : c.knownCost() > 0 ? Theme.PRICE : Theme.INK_3);

        // All stats: a thin switch, then the build's own totals in three columns
        CommunityBuilds.Totals t = c.totals();
        if (t != null) {
            int ty = by0 + Ui.BUTTON_H + Theme.SP_3;
            String label = L.t("wynnlabs.community.all_stats");
            int sw = toggleW(label);
            int th = toggleH();
            boolean hov = BuildControls.in(mx, my, ix, ty, sw, th);
            Kit.pill(g, ix, ty, sw, th, page.allStats, hov);
            Art.icon(g, page.allStats ? "chevron-down" : "chevron-right", ix + Theme.SP_3, ty + (th - 6) / 2, 6, Theme.INK_3);
            Type.META.draw(g, ui.font, label, ix + Theme.SP_3 + 6 + Theme.SP_1, Type.META.yIn(ty, th), Theme.INK);
            ui.btn(ix, ty, sw, th, "c.stats", null);
            host.tip(ix, ty, sw, th, List.of(L.c("wynnlabs.community.all_stats_tip", t.level()).withStyle(ChatFormatting.GRAY)));
            if (page.allStats) {
                stats(g, t, ix, ty + th + Theme.SP_2, inner, compact);
            }
        }
    }

    private int toggleH() {
        return Type.META.lineHeight + 4;
    }

    private int toggleW(String label) {
        return 2 * Theme.SP_3 + 6 + Theme.SP_1 + Type.META.width(ui.font, label);
    }

    /** One line of an All stats column: a label and its value; a null value marks the defence chips. */
    private record Line(String label, String value) {}

    private static List<List<Line>> columns(CommunityBuilds.Totals t) {
        List<Line> off = List.of(
                new Line(L.t("wynnlabs.community.st.spell_damage"), L.pct(CommunityPage.signed(t.id("spellDamage")))),
                new Line(L.t("wynnlabs.community.st.raw_spell_damage"), CommunityPage.signed(t.id("rawSpellDamage"))),
                new Line(L.t("wynnlabs.community.st.melee_damage"), L.pct(CommunityPage.signed(t.id("mainAttackDamage")))),
                new Line(L.t("wynnlabs.community.st.attack_speed"), ATTACK_SPEEDS[Math.max(0, Math.min(6, t.attackTier()))]));
        List<Line> def = List.of(
                new Line(L.t("wynnlabs.community.st.health"), CommunityPage.big(t.health())),
                new Line(L.t("wynnlabs.community.st.ehp"), CommunityPage.big(t.ehp())),
                new Line(L.t("wynnlabs.community.st.health_regen"), CommunityPage.signed(t.id("healthRegenRaw"))
                        + (t.id("healthRegen") == 0 ? "" : " (" + L.pct(CommunityPage.signed(t.id("healthRegen"))) + ")")),
                new Line(L.t("wynnlabs.community.st.life_steal"), CommunityPage.signed(t.id("lifeSteal")) + "/3s"),
                new Line(L.t("wynnlabs.community.st.defences"), null));
        List<Line> util = List.of(
                new Line(L.t("wynnlabs.community.st.mana_regen"), CommunityPage.signed(t.manaRegen()) + "/5s"),
                new Line(L.t("wynnlabs.community.st.mana_steal"), CommunityPage.signed(t.id("manaSteal")) + "/3s"),
                new Line(L.t("wynnlabs.community.st.walk_speed"), L.pct(CommunityPage.signed(t.id("walkSpeed")))),
                new Line(L.t("wynnlabs.community.st.loot_bonus"), L.pct(CommunityPage.signed(t.id("lootBonus")))),
                new Line(L.t("wynnlabs.community.st.xp_bonus"), L.pct(CommunityPage.signed(t.id("xpBonus")))));
        return List.of(off, def, util);
    }

    private static final String[] STAT_HEADS = {"wynnlabs.community.st.offence", "wynnlabs.community.st.defence", "wynnlabs.community.st.utility"};

    /** The defence chips' texts: "Earth +60". */
    private static List<String> chips(CommunityBuilds.Totals t) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < ELEMENT_NAMES.length; i++) {
            out.add(ELEMENT_NAMES[i] + " " + CommunityPage.signed(t.defence(i)));
        }
        return out;
    }

    private int chipW(String text) {
        return Type.META.width(ui.font, text) + 2 * Theme.SP_2;
    }

    /** The lines of chips {@code room} wide holds. */
    private int chipLines(List<String> chips, int room) {
        int lines = 1;
        int x = 0;
        for (String c : chips) {
            int w = chipW(c);
            if (x > 0 && x + w > room) {
                lines++;
                x = 0;
            }
            x += w + Theme.SP_1;
        }
        return lines;
    }

    /**
     * The All stats columns {@code inner} wide at x, y: three side by side, or two and one under
     * them when three do not fit (one a row when two do not); draws when {@code g} is not null.
     * Returns their height.
     */
    private int stats(GuiGraphics g, CommunityBuilds.Totals t, int x, int y, int inner, boolean compact) {
        List<List<Line>> cols = columns(t);
        List<String> chips = chips(t);
        int cp = compact ? Theme.SP_2 : Theme.SP_3;
        int gap = compact ? Theme.SP_2 : Theme.SP_3;
        int need = 0;
        for (int i = 0; i < 3; i++) {
            need = Math.max(need, Type.META.width(ui.font, L.t(STAT_HEADS[i])));
            for (Line l : cols.get(i)) {
                need = Math.max(need, Type.META.width(ui.font, l.label())
                        + (l.value() == null ? 0 : Theme.SP_3 + Type.LABEL.width(ui.font, l.value())));
            }
        }
        for (String c : chips) {
            need = Math.max(need, chipW(c));
        }
        need += 2 * cp;
        int per = 3 * need + 2 * gap <= inner ? 3 : 2 * need + gap <= inner ? 2 : 1;
        int colW = (inner - (per - 1) * gap) / per;
        int lineH = Type.META.lineHeight + Theme.SP_2;
        int[] hs = new int[3];
        for (int i = 0; i < 3; i++) {
            int h = cp + Type.META.lineHeight + Theme.SP_1;
            for (Line l : cols.get(i)) {
                h += l.value() == null
                        ? Type.META.lineHeight + Theme.SP_1 + chipLines(chips, colW - 2 * cp) * (Ui.SEAL_H + Theme.SP_1)
                        : lineH;
            }
            hs[i] = h + cp - Theme.SP_1;
        }
        int total = 0;
        for (int row = 0; row * per < 3; row++) {
            int rowH = 0;
            for (int i = row * per; i < Math.min(3, row * per + per); i++) {
                rowH = Math.max(rowH, hs[i]);
            }
            if (g != null) {
                for (int i = row * per; i < Math.min(3, row * per + per); i++) {
                    statColumn(g, L.t(STAT_HEADS[i]), cols.get(i), chips, x + (i - row * per) * (colW + gap), y + total, colW, rowH, cp);
                }
            }
            total += rowH + gap;
        }
        return total - gap;
    }

    private void statColumn(GuiGraphics g, String head, List<Line> lines, List<String> chips, int x, int y, int w, int h, int cp) {
        Kit.box(g, x, y, w, h, STATS_FACE, 0);
        int ix = x + cp;
        int room = w - 2 * cp;
        int cy = y + cp;
        Type.META.draw(g, ui.font, Type.META.fit(ui.font, head, room), ix, cy, Theme.INK_3);
        cy += Type.META.lineHeight + Theme.SP_1;
        int lineH = Type.META.lineHeight + Theme.SP_2;
        for (int n = 0; n < lines.size(); n++) {
            Line l = lines.get(n);
            if (l.value() == null) {
                Type.META.draw(g, ui.font, Type.META.fit(ui.font, l.label(), room), ix, cy, Theme.INK_3);
                cy += Type.META.lineHeight + Theme.SP_1;
                int cx = ix;
                for (int i = 0; i < chips.size(); i++) {
                    int cw = chipW(chips.get(i));
                    if (cx > ix && cx + cw > ix + room) {
                        cx = ix;
                        cy += Ui.SEAL_H + Theme.SP_1;
                    }
                    Ui.seal(g, ui.font, chips.get(i), cx, cy, ELEMENT[i]);
                    cx += cw + Theme.SP_1;
                }
                cy += Ui.SEAL_H + Theme.SP_1;
                continue;
            }
            int vw = Type.LABEL.width(ui.font, l.value());
            Type.META.draw(g, ui.font, Type.META.fit(ui.font, l.label(), room - vw - Theme.SP_3), ix, cy, Theme.INK_2);
            Type.LABEL.draw(g, ui.font, l.value(), ix + room - Type.LABEL.width(ui.font, l.value()), cy, Theme.INK);
            if (n + 1 < lines.size()) {
                Kit.hair(g, ix, cy + Type.META.lineHeight + Theme.SP_1 - 1, room, 1, STATS_RULE);
            }
            cy += lineH;
        }
    }

    /** A light button with a thin edge ("Copy link"). */
    private void flatButton(GuiGraphics g, BuildWindow.Host host, String text, int x, int y, String act, Object arg, String tip,
            int mx, int my) {
        int w = copyW(text);
        boolean hov = BuildControls.in(mx, my, x, y, w, Ui.BUTTON_H);
        Kit.pill(g, x, y, w, Ui.BUTTON_H, false, hov);
        Type.BODY.draw(g, ui.font, text, x + Theme.SP_4, Type.BODY.yIn(y, Ui.BUTTON_H), act == null ? Theme.INK_3 : Theme.INK);
        if (act != null) {
            ui.btn(x, y, w, Ui.BUTTON_H, act, arg);
        }
        if (tip != null) {
            host.tip(x, y, w, Ui.BUTTON_H, List.of(Component.literal(tip).withStyle(ChatFormatting.GRAY)));
        }
    }

    private int keyItem(GuiGraphics g, String icon, String text, int colour, String label, int x, int y) {
        int d = 9;
        int iy = Type.META.onCaps(y, d);
        badge(g, icon, text, colour, x, iy, d);
        Type.META.draw(g, ui.font, label, x + d + Theme.SP_1, y, Theme.INK_3);
        return x + d + Theme.SP_1 + Type.META.width(ui.font, label) + Theme.SP_4;
    }

    /** A round badge {@code d} across at x, y: an icon or a short text in cream on {@code colour}. */
    private static void badge(GuiGraphics g, String icon, String text, int colour, int x, int y, int d) {
        Kit.seal(g, x, y, d, d, colour);
        if (icon != null) {
            Art.icon(g, icon, x + 1, y + 1, d - 2, Theme.CREAM);
        } else if (text != null) {
            var font = net.minecraft.client.Minecraft.getInstance().font;
            Component c = Component.literal(text);
            int tw = font.width(c);
            float s = Math.min(0.6f, (d - 2f) / Math.max(1, tw));
            TooltipListener.outlined(g, font, c, tw, x + (d - tw * s) / 2f, y + (d - 8 * s) / 2f, s, Theme.CREAM, false);
        }
    }

    /** One piece's tile: ringed in its tier's colour, the picture in a slot, the name under it; a badge or a price. */
    private void tile(GuiGraphics g, BuildWindow.Host host, CommunityPage page, CommunityBuilds.Piece p, int x, int y, int tw,
            boolean compact, List<Runnable> over, int mx, int my) {
        int th = tileH(compact);
        int sl = slot(compact);
        int sx = x + (tw - sl) / 2;
        int sy = y + 2 + Theme.SP_2;
        if (p == null) {
            Kit.box(g, x, y, tw, th, TILE, CommunityPage.RULE);
            Kit.slot(g, sx, sy, sl, 0);
            return;
        }
        int ink = tierInk(p);
        // owned: the tier's ring; missing: a paler face and the ring at half strength
        Kit.box(g, x, y, tw, th, p.owned() ? TILE : TILE_MISSING, p.owned() ? ink : (ink & 0xFFFFFF) | 0x80000000);
        Kit.slot(g, sx, sy, sl, 0);
        ItemStack st = icon(host, p);
        int off = (sl - 16) / 2;
        if (!st.isEmpty()) {
            g.renderItem(st, sx + off, sy + off);
        }
        String n = Type.META.fit(ui.font, p.name(), tw - 2);
        Type.META.draw(g, ui.font, n, x + (tw - Type.META.width(ui.font, n)) / 2, sy + sl + Theme.SP_1, ink);
        if (p.info() != null) {
            List<Component> tip = pieceTip(p);
            host.item(x, y, tw, th, p.info(), st, tip.subList(1, tip.size()));
        } else {
            host.tip(x, y, tw, th, pieceTip(p));
        }
        if (p.crafted() && !p.owned()) {
            page.tab().ui.btn(x, y, tw, th, "c.craft", p.recipe());
            if (BuildControls.in(mx, my, x, y, tw, th)) {
                Kit.outline(g, x - 1, y - 1, tw + 2, th + 2, Theme.ACCENT);
            }
        }
        var font = net.minecraft.client.Minecraft.getInstance().font;
        over.add(() -> {
            if (!p.owned()) {
                g.fill(sx + 1, sy + 1, sx + sl - 1, sy + sl - 1, 0x80F3E6C4);
                if (p.crafted()) {
                    Art.icon(g, "hammer", sx + 1, sy + 1, 7, Theme.TIER_CRAFTED);
                }
                String price = p.crafted() ? p.craftCost() == null ? null : "~" + CommunityPage.price(p.craftCost())
                        : p.price() == null ? null : CommunityPage.price(Math.round(p.price()));
                if (price != null) {
                    Component pc = Component.literal(price);
                    int pw = font.width(pc);
                    float s = Math.min(0.6f, (sl + 6f) / Math.max(1, pw));
                    TooltipListener.outlined(g, font, pc, pw, sx + (sl - pw * s) / 2f, sy + sl - 8 * s + 1, s, PRICE_INK, false);
                }
                return;
            }
            int d = compact ? 9 : 10;
            int bx = sx + sl - d + 3;
            int by = sy - 3;
            switch (p.place()) {
                case EQUIPPED -> badge(g, "check", null, EQUIPPED, bx, by, d);
                case BAG -> badge(g, "backpack", null, BAG, bx, by, d);
                default -> badge(g, null, String.valueOf(p.bankPage()), BANK, bx, by, d);
            }
        });
    }
}
