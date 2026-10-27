package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.build.TreePlanner;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.Wyn;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/**
 * The Home's top: the character on a soft card (the class's name, the archetype as a chip, the
 * level; effective health, casts in a row and spell damage as thin bars against the next
 * milestone's build; the build score on its gauge), then a row of chips for what the build is
 * scored by (the goal, powders, whether experience counts) with the nearest bank and Trade Market
 * on its right, and the experience bar. Compact: the three settings as icons under the gauge, the
 * nearest places beside the experience bar. Only reads; the chips' clicks are the page's actions.
 */
final class HomeHero extends MenuPaint {

    /** The bars' colours on paper: health, mana, spell damage; their track. */
    private static final int HP = 0xFFB8452F;
    private static final int MANA = 0xFF2F6FB0;
    private static final int SPELL = 0xFF6A4BA8;
    private static final int TRACK = 0xFFCDB98A;
    private static final int BAR_H = 4;
    /** A bar's row: a body line and SP_1 under it. */
    private static final int ROW = 13;
    private static final int GAUGE_W = 58;
    /** Under this menu height the card keeps its name row only. */
    private static final int TIGHT_H = 300;

    private final HomePage page;

    HomeHero(RoadmapScreen menu, HomePage page) {
        super(menu);
        this.page = page;
    }

    /** Draws the card, the chip row and the experience bar from {@code y}; returns the y under them. */
    int draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Font font = font();
        boolean compact = menu.layout.compact();
        String cls;
        int level;
        int xp;
        try {
            cls = nice(Wyn.classType().name());
            level = Wyn.level();
            xp = Wyn.xp().percentInt();
        } catch (Throwable t) {
            cls = "?";
            level = 0;
            xp = 0;
        }
        // a short menu (under 300 tall): the name row alone, the score and the settings at its right
        boolean tight = menu.layout.h() < TIGHT_H;
        int pad = tight ? Theme.SP_3 : Theme.SP_4;
        int h = tight ? 2 * pad + Type.TITLE.lineHeight : pad + Type.TITLE.lineHeight + Theme.SP_3 + 3 * ROW - Theme.SP_1 + pad;
        Kit.softCard(g, x, y, w, h);
        // the name line: the class, the archetype (click: its list), the level
        int tx = x + pad;
        Type.TITLE.draw(g, font, cls, tx, y + pad);
        int cy = Type.TITLE.onCaps(y + pad, Ui.CHIP_H);
        int cx = tx + Type.TITLE.width(font, cls) + Theme.SP_3;
        String arch = TreePlanner.chosen();
        String archLabel = arch.isEmpty() ? L.t("wynnlabs.settings.archetype_off") : arch;
        int aw = Ui.iconChipWidth(font, "sparkles", archLabel, true);
        Ui.iconChip(g, font, "sparkles", archLabel, true, cx, cy, aw, false, mx, my);
        menu.hits.add(new MenuHit(cx, cy, aw, Ui.CHIP_H, null, List.of(
                L.c("wynnlabs.home.archetype_tip", archLabel).withStyle(ChatFormatting.LIGHT_PURPLE),
                L.c("wynnlabs.home.archetype_click").withStyle(ChatFormatting.DARK_GRAY)), "#qarch:"));
        cx += aw + Theme.SP_3;
        Type.MUTED.draw(g, font, "Lv " + level, cx, Type.BODY.yIn(cy, Ui.CHIP_H));
        // the build score on its gauge at the card's right, against the next milestone's
        Build.Plan plan = Build.plan();
        Damage.Result now = plan == null ? null : plan.current();
        List<Integer> shown = page.shownSteps();
        Damage.Result target = null;
        int targetLevel = 0;
        if (!shown.isEmpty() && menu.steps.get(shown.get(0)).set() != null) {
            target = menu.steps.get(shown.get(0)).set().result();
            targetLevel = menu.steps.get(shown.get(0)).level();
        }
        int gx = x + w - pad - GAUGE_W;
        if (tight) {
            int iw = 3 * (Ui.CHIP_H + 2) + 2 * Theme.SP_1;
            int ix = x + w - pad - iw;
            settingsIcons(g, ix, cy, mx, my);
            if (now != null) {
                String score = String.valueOf(Math.round(now.objective()));
                String scoreShown = compact(Math.round(now.objective()));
                int sw = Type.LEAD.width(font, scoreShown);
                int sx = ix - Theme.SP_4 - sw;
                if (sx > cx + Type.MUTED.width(font, "Lv " + level) + Theme.SP_4) {
                    Type.LEAD.draw(g, font, scoreShown, sx, Type.LEAD.yIn(cy, Ui.CHIP_H));
                    menu.hits.add(new MenuHit(sx, cy, sw, Ui.CHIP_H, null,
                            page.vsTip(L.t("wynnlabs.home.stat_build"), score,
                            target == null || target.objective() <= 0 ? 1 : now.objective() / target.objective(), targetLevel), null));
                }
            }
        } else if (now != null) {
            double frac = target == null || target.objective() <= 0 ? 1 : now.objective() / target.objective();
            int gcx = gx + GAUGE_W / 2;
            int gcy = y + pad + 29;
            Kit.gauge(g, gcx, gcy, 29, 5, Math.max(0, Math.min(1, frac)), TRACK, Theme.GOLD_DEEP, Theme.GOLD_EDGE);
            String score = String.valueOf(Math.round(now.objective()));
            // the number inside the arc: compact ("756k"), a smaller role when even that is too wide, centred
            String scoreShown = compact(Math.round(now.objective()));
            int room = 2 * (29 - 5) - 2 * Theme.SP_2;
            Type role = Type.LEAD;
            for (Type t : new Type[] {Type.LEAD, Type.NUMBER, Type.META}) {
                role = t;
                if (t.width(font, scoreShown) <= room) {
                    break;
                }
            }
            role.draw(g, font, role.fit(font, scoreShown, room), gcx - Math.min(room, role.width(font, scoreShown)) / 2,
                    role.yIn(gcy - 17, Type.LEAD.lineHeight), Theme.INK);
            String lbl = L.t("wynnlabs.home.build_score");
            Type.META.draw(g, font, lbl, gcx - Type.META.width(font, lbl) / 2, gcy + Theme.SP_1);
            menu.hits.add(new MenuHit(gx, gcy - 28, GAUGE_W, 28 + Theme.SP_1 + Type.META.lineHeight, null,
                    page.vsTip(L.t("wynnlabs.home.stat_build"), score, frac, targetLevel), null));
        }
        if (compact && !tight) {
            settingsIcons(g, gx + (GAUGE_W - (3 * (Ui.CHIP_H + 2) + 2 * Theme.SP_1)) / 2, y + h - pad - Ui.CHIP_H, mx, my);
        }
        // the bars: the value and what it is, the track, the milestone's value at its end
        int by = y + pad + Type.TITLE.lineHeight + Theme.SP_3;
        int right = gx - Theme.SP_6;
        if (now != null && !tight && right - tx > 120) {
            double health = Wyn.health().map(c -> (double) c.max()).filter(m -> m > 0).orElse(now.health());
            Object[][] bars = {
                {String.valueOf(Math.round(health)), L.t("wynnlabs.home.hud_health_label"), health, target == null ? 0 : target.health(), HP,
                    "heart", L.t("wynnlabs.home.stat_health") + " · " + L.t("wynnlabs.home.stat_ehp") + " " + Math.round(now.ehp())},
                {String.valueOf(Math.round(now.casts())), L.t("wynnlabs.home.hud_casts_label"), now.casts(),
                    target == null ? 0 : target.casts(), MANA, "droplet", L.t("wynnlabs.home.hud_casts_tip")},
                {String.valueOf(Math.round(now.spellDps())), L.t("wynnlabs.home.hud_spell_label"), now.spellDps(),
                    target == null ? 0 : target.spellDps(), SPELL, "wand", L.t("wynnlabs.home.stat_spell")}};
            int labelCol = menu.layout.large() ? 128 : 100;
            int trackX = tx + labelCol;
            for (Object[] b : bars) {
                double val = (Double) b[2];
                double ref = (Double) b[3];
                double frac = ref <= 0 ? 1 : val / ref;
                Art.icon(g, (String) b[5], tx, Type.BODY.onCaps(by, 8), 8, (Integer) b[4]);
                int vx = tx + 8 + Theme.SP_2;
                Type.BODY.draw(g, font, (String) b[0], vx, by);
                int lx = vx + Type.BODY.width(font, (String) b[0]) + Theme.SP_2;
                Type.META.draw(g, font, fit((String) b[1], trackX - Theme.SP_3 - lx), lx, by + 1);
                String goal = ref <= 0 ? "" : (menu.layout.large() && targetLevel > 0 ? "Lv " + targetLevel + ": " : "") + Math.round(ref);
                int gw = goal.isEmpty() ? 0 : Type.META.width(font, goal);
                int trackW = Math.min(menu.layout.large() ? 260 : 200, right - trackX - gw - Theme.SP_3);
                if (trackW > 20) {
                    int ty = Type.BODY.onCaps(by, BAR_H);
                    g.fill(trackX, ty, trackX + trackW, ty + BAR_H, TRACK);
                    int fw = (int) Math.round(trackW * Math.max(0, Math.min(1, frac)));
                    g.fill(trackX, ty, trackX + fw, ty + BAR_H, (Integer) b[4]);
                    if (gw > 0) {
                        Type.META.draw(g, font, goal, trackX + trackW + Theme.SP_3, by + 1);
                    }
                }
                menu.hits.add(new MenuHit(tx, by, Math.max(1, right - tx), Type.BODY.lineHeight, null,
                        page.vsTip((String) b[6], (String) b[0], frac, targetLevel), null));
                by += ROW;
            }
        }
        int out = y + h + 2;                                  // the card's soft shadow
        if (!compact && !tight) {
            out += Theme.SP_3;
            settingsChips(g, x, out, w, mx, my);
            out += Ui.CHIP_H + Theme.SP_3;
            return experience(g, x, out, w, xp, 0);
        }
        out += Theme.SP_3;
        int nw = nearest(g, x + w, out - 3, mx, my, true);
        return experience(g, x, out, w, xp, nw > 0 ? nw + Theme.SP_3 : 0);
    }

    /** The experience bar: teal with its rim and notches, the % at its end; {@code reserve} px kept on the right. */
    private int experience(GuiGraphics g, int x, int y, int w, int xp, int reserve) {
        String pct = RoadmapScreen.pct(xp) + " XP";
        int pw = Type.META.width(font(), pct);
        int xw = w - reserve - pw - Theme.SP_3;
        Kit.tealBar(g, x, y + 1, xw, 4, Math.max(0, Math.min(100, xp)) / 100.0, true);
        Kit.notches(g, x, y + 1, xw, 4);
        Type.META.draw(g, font(), pct, x + xw + Theme.SP_3, Type.META.yIn(y - 2, 10));
        return y + 6;
    }

    /** The goal, powders and experience as chips, the nearest bank and Trade Market at the row's right. */
    private void settingsChips(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Font font = font();
        int cx = x;
        String goal = L.t("wynnlabs.home.goal_chip", Setup.goalName(Setup.goal()));
        int gw = Ui.iconChipWidth(font, "target", goal, true);
        Ui.iconChip(g, font, "target", goal, true, cx, y, gw, page.goalOpen(), mx, my);
        page.goalAnchor(new int[] {cx, y, gw, Ui.CHIP_H});
        menu.hits.add(new MenuHit(cx, y, gw, Ui.CHIP_H, null, goalTip(), "#qgoal:"));
        cx += gw + Theme.SP_2;
        boolean powdered = Rules.sub("model", "assumePowders", 1) > 0;
        String pow = L.t(powdered ? "wynnlabs.home.powder_on" : "wynnlabs.home.powder_off");
        int pw = Ui.iconChipWidth(font, "diamond", pow, false);
        Ui.iconChip(g, font, "diamond", pow, false, cx, y, pw, powdered, mx, my);
        menu.hits.add(new MenuHit(cx, y, pw, Ui.CHIP_H, null, powderTip(powdered), "#qpowder:"));
        cx += pw + Theme.SP_2;
        boolean xpOn = Setup.countsXp();
        String xpl = L.t(xpOn ? "wynnlabs.home.xp_counts" : "wynnlabs.home.xp_not_counted");
        int xw = Ui.iconChipWidth(font, "trending-up", xpl, false);
        Ui.iconChip(g, font, "trending-up", xpl, false, cx, y, xw, xpOn, mx, my);
        menu.hits.add(new MenuHit(cx, y, xw, Ui.CHIP_H, null, xpTip(xpOn), "#qxp:"));
        nearest(g, x + w, y, mx, my, false);
    }

    /** Compact: the goal, powders and experience as three icon buttons in a row from x. */
    private void settingsIcons(GuiGraphics g, int x, int y, int mx, int my) {
        int step = Ui.CHIP_H + 2 + Theme.SP_1;
        Ui.iconButton(g, "target", x, y, page.goalOpen(), mx, my);
        page.goalAnchor(new int[] {x, y, Ui.CHIP_H + 2, Ui.CHIP_H});
        menu.hits.add(new MenuHit(x, y, Ui.CHIP_H + 2, Ui.CHIP_H, null, goalTip(), "#qgoal:"));
        boolean powdered = Rules.sub("model", "assumePowders", 1) > 0;
        Ui.iconButton(g, "diamond", x + step, y, powdered, mx, my);
        menu.hits.add(new MenuHit(x + step, y, Ui.CHIP_H + 2, Ui.CHIP_H, null, powderTip(powdered), "#qpowder:"));
        boolean xpOn = Setup.countsXp();
        Ui.iconButton(g, "trending-up", x + 2 * step, y, xpOn, mx, my);
        menu.hits.add(new MenuHit(x + 2 * step, y, Ui.CHIP_H + 2, Ui.CHIP_H, null, xpTip(xpOn), "#qxp:"));
    }

    private List<Component> goalTip() {
        return page.goalOpen() ? List.of() : List.of(
                L.c("wynnlabs.home.goal_tip", Setup.goalName(Setup.goal())).withStyle(ChatFormatting.LIGHT_PURPLE),
                L.c("wynnlabs.home.goal_click").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static List<Component> powderTip(boolean powdered) {
        return List.of(L.c(powdered ? "wynnlabs.home.powder_on_tip" : "wynnlabs.home.powder_off_tip").withStyle(ChatFormatting.LIGHT_PURPLE),
                L.c("wynnlabs.home.powder_click").withStyle(ChatFormatting.DARK_GRAY));
    }

    private static List<Component> xpTip(boolean xpOn) {
        return List.of(L.c("wynnlabs.home.count_xp", L.t(xpOn ? "wynnlabs.rules.on" : "wynnlabs.rules.off")).withStyle(ChatFormatting.LIGHT_PURPLE),
                L.c(xpOn ? "wynnlabs.home.count_xp_on_tip" : "wynnlabs.home.count_xp_off_tip").withStyle(ChatFormatting.GRAY),
                L.c("wynnlabs.home.powder_click").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * The nearest bank (wiki's Bank page) and Trade Market (Wynntils' service list) as two chips
     * ending at {@code right}; one click puts a waypoint there. Returns the width they took.
     */
    private int nearest(GuiGraphics g, int right, int y, int mx, int my, boolean small) {
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null) {
            return 0;
        }
        String[][] kinds = {{"shopping-cart", "TMS"}, {"building-bank", "BANKS"}};
        int r = right;
        for (String[] k : kinds) {
            String bestLine = null;
            double bestD = Double.MAX_VALUE;
            for (String line : (k[1].equals("BANKS") ? HomeHeroData.BANKS : HomeHeroData.TMS).split("\\|")) {
                String[] f = line.split(" ", 4);
                double d = Math.hypot(Integer.parseInt(f[0]) - me.getX(), Integer.parseInt(f[2]) - me.getZ());
                if (d < bestD) {
                    bestD = d;
                    bestLine = line;
                }
            }
            if (bestLine == null) {
                continue;
            }
            String[] f = bestLine.split(" ", 4);
            String dist = bestD >= 1000 ? String.format(Locale.ROOT, "%.1f km", bestD / 1000) : Math.round(bestD) + " m";
            int cw = Ui.iconChipWidth(font(), k[0], dist, false);
            r -= cw;
            Ui.iconChip(g, font(), k[0], dist, false, r, y, cw, false, mx, my);
            String what = (k[1].equals("BANKS") ? L.t("wynnlabs.home.bank") : "Trade Market") + " · " + f[3];
            menu.hits.add(new MenuHit(r, y, cw, Ui.CHIP_H, null, List.of(
                    L.c("wynnlabs.home.nearest", what).withStyle(ChatFormatting.GREEN),
                    L.c("wynnlabs.home.coords_blocks", f[0] + " " + f[1] + " " + f[2], Math.round(bestD)).withStyle(ChatFormatting.GRAY),
                    L.c("wynnlabs.home.click_mark").withStyle(ChatFormatting.DARK_GRAY)),
                    "#qnear:" + f[0] + " " + f[1] + " " + f[2] + " " + what));
            r -= Theme.SP_2;
        }
        return right - r - Theme.SP_2;
    }

    /** A big number short enough for the gauge: 42, 9 800, 12.4k, 756k, 1.2M (the exact one is in the tooltip). */
    static String compact(long v) {
        if (v >= 1_000_000) {
            return Math.round(v / 100_000.0) / 10.0 + "M";
        }
        if (v >= 100_000) {
            return Math.round(v / 1000.0) + "k";
        }
        if (v >= 10_000) {
            return Math.round(v / 100.0) / 10.0 + "k";
        }
        return String.valueOf(v);
    }
}
