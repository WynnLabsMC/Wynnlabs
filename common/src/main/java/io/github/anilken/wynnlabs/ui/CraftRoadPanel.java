package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.LevelRoad;
import io.github.anilken.wynnlabs.crafting.ProfessionPlan;
import io.github.anilken.wynnlabs.crafting.ProfessionXp;
import io.github.anilken.wynnlabs.crafting.RoadChoices;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.model.ProfBomb;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import io.github.anilken.wynnlabs.ui.CraftingTab.Drop;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static io.github.anilken.wynnlabs.ui.CraftFormat.typeIcon;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * Meslek: the professions on the left (on a small window a chip with a menu), on the right the
 * picked one's level road: its item type, the target level, a bar to it with what each band
 * unlocks, the bombs on this world (and the other worlds Wynntils heard of), the material
 * preference, and the steps one after another ({@link CraftRoadSteps}). A crafting profession at
 * the last recipe level shows a calm "tamamlandı"; a gathering one its nearest spots.
 */
final class CraftRoadPanel extends CraftPanel {

    /** The level every recipe is open from. */
    static final int ALL_RECIPES = 103;
    private static final int LIST_W = 140;

    final CraftRoadData data;
    private final CraftRoadSteps steps;
    /** The picked profession ("WEAPONSMITHING"), null until first drawn. */
    String profession;
    String note;

    CraftRoadPanel(CraftingTab tab) {
        super(tab);
        data = new CraftRoadData(tab);
        steps = new CraftRoadSteps(tab, this);
    }

    LevelRoad.Source sourceOf(String material) {
        return data.source(material);
    }

    int level(String p) {
        return Math.max(1, tab.knownLevel(p.toLowerCase(Locale.ROOT)));
    }

    int target(String p, int level) {
        Integer t = RoadChoices.get().targets.get(p);
        return ProfessionPlan.clampTarget(level, t == null ? level + 10 : t);
    }

    String type(String p, int level, int target) {
        String t = RoadChoices.get().types.get(p);
        if (t != null) {
            return t;
        }
        List<ProfessionPlan.CraftOption> o = data.options(p, level, target);
        return o.isEmpty() ? null : o.get(0).type();
    }

    /** Draws the page from y (already scrolled) across w; returns its height. */
    int draw(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        if (profession == null) {
            String p = tab.profession();
            profession = p != null && !Professions.gathering(p) ? p : "WEAPONSMITHING";
        }
        boolean wide = w >= CraftingTab.TWO_COLUMNS_MIN;
        int rx = x;
        int cy = y;
        if (wide) {
            list(g, x, y, mx, my);
            Kit.hair(g, x + LIST_W + Theme.SP_6, y, 1, Math.max(0, bottom - y - Theme.SP_2), Theme.LINE);
            rx = x + LIST_W + 2 * Theme.SP_6 + 1;
        } else {
            int lv = level(profession);
            String label = Professions.name(profession) + " " + lv;
            int cw = Ui.iconChipWidth(tab.font, profIcon(profession), label, true);
            UiMenu.chip(g, tab.font, profIcon(profession), label, x, cy, tab.open == Drop.ROAD_PROF, mx, my);
            int[] anchor = {x, cy, cw, Ui.CHIP_H};
            tab.acts.add(new Act(x, cy, cw, Ui.CHIP_H, () -> tab.toggle(Drop.ROAD_PROF, anchor), null, null));
            cy += Ui.CHIP_H + Theme.SP_4;
        }
        int rw = x + w - rx;
        int used = Professions.gathering(profession) ? gathering(g, rx, cy, rw, mx, my) : crafting(g, rx, cy, rw, top, bottom, mx, my);
        return Math.max(used + (cy - y), wide ? 13 * (Type.BODY.lineHeight + 2 * Theme.SP_1 + Theme.SP_2) + 2 * Theme.SP_3 : 0);
    }

    // ---- the list ----

    private void list(GuiGraphics g, int x, int y, int mx, int my) {
        int cy = y + Theme.SP_1;                    // the picked row's wash keeps SP_4 from the tab bar's hairline
        List<String> order = new ArrayList<>(Professions.ALL.subList(4, Professions.ALL.size()));
        order.add(null);
        order.addAll(Professions.ALL.subList(0, 4));
        int rowH = Type.BODY.lineHeight + 2 * Theme.SP_1;
        int in = Theme.SP_2;
        for (String p : order) {
            if (p == null) {
                cy += Theme.SP_3 - Theme.SP_2;      // with the row gap: SP_3 above and below the divider
                Kit.hair(g, x, cy, LIST_W, 1, Theme.LINE);
                cy += 1 + Theme.SP_3;
                continue;
            }
            boolean on = p.equals(profession);
            if (on) {
                Kit.rowLit(g, x, cy, LIST_W, rowH);
            }
            int ty = Type.BODY.yIn(cy, rowH);
            Art.icon(g, profIcon(p), x + in, Type.BODY.onCaps(ty, 8), 8, Theme.INK_2);
            Type role = on ? Type.LABEL : Type.BODY;
            role.draw(g, tab.font, Professions.name(p), x + in + 8 + Theme.SP_2, ty, on ? Theme.INK : Theme.INK_2);
            int known = tab.knownLevel(p.toLowerCase(Locale.ROOT));
            if (!Professions.gathering(p) && known >= ALL_RECIPES + 2) {
                Art.icon(g, "check", x + LIST_W - in - 8, Type.BODY.onCaps(ty, 8), 8, Theme.GOOD);
            } else {
                Type.META.drawRight(g, tab.font, known > 0 ? String.valueOf(known) : "?", x + LIST_W - in, Type.META.yIn(cy, rowH), Theme.INK_3);
            }
            tab.acts.add(new Act(x, cy, LIST_W, rowH, () -> pickProf(p), null, null));
            cy += rowH + Theme.SP_2;
        }
    }

    static String profIcon(String p) {
        return switch (p) {
            case "MINING" -> "pick";
            case "WOODCUTTING" -> "leaf";
            case "FARMING" -> "sun";
            case "FISHING" -> "droplet";
            case "ALCHEMISM" -> "flask-2";
            case "ARMOURING" -> "shield";
            case "COOKING" -> "meat";
            case "JEWELING" -> "diamond";
            case "SCRIBING" -> "script";
            case "TAILORING" -> "shirt";
            case "WEAPONSMITHING" -> "sword";
            default -> "bow";
        };
    }

    void pickProf(String p) {
        profession = p;
        note = null;
        tab.scroll = 0;
    }

    /** The professions as menu rows (the small window's chip). */
    List<UiMenu.Item> profItems() {
        UiMenu.Items items = new UiMenu.Items();
        for (int i = 0; i < Professions.ALL.size(); i++) {
            String p = Professions.ALL.get((i + 4) % Professions.ALL.size());
            if (i == Professions.ALL.size() - 4) {
                items.divide();
            }
            int known = tab.knownLevel(p.toLowerCase(Locale.ROOT));
            items.add(UiMenu.Item.of(p, profIcon(p), Professions.name(p), known > 0 ? String.valueOf(known) : "?", p.equals(profession)));
        }
        return items.list;
    }

    /** The road's item types as menu rows: crafts to the target on the right. */
    List<UiMenu.Item> typeItems() {
        int lv = level(profession);
        int target = target(profession, lv);
        String now = type(profession, lv, target);
        List<UiMenu.Item> out = new ArrayList<>();
        for (ProfessionPlan.CraftOption o : data.options(profession, lv, target)) {
            String meta = o.crafts() < 0 ? null : L.n("wynnlabs.road.crafts_short", o.crafts());
            out.add(UiMenu.Item.of(o.type(), typeIcon(o.type()), typeName(o.type()), meta, o.type().equals(now)));
        }
        return out;
    }

    void pickType(String type) {
        RoadChoices ch = RoadChoices.get();
        ch.types.put(profession, type);
        ch.save();
    }

    // ---- a crafting profession ----

    private int crafting(GuiGraphics g, int x, int y, int w, int top, int bottom, int mx, int my) {
        int level = level(profession);
        RoadChoices ch = RoadChoices.get();
        int known = tab.knownLevel(profession.toLowerCase(Locale.ROOT));
        boolean doneAll = known >= ALL_RECIPES + 2 && !ch.targets.containsKey(profession);
        if (doneAll) {
            return finished(g, x, y, w, mx, my);
        }
        int target = target(profession, level);
        String type = type(profession, level, target);
        int cy = head(g, x, y, w, level, target, type, mx, my);
        if (type == null) {
            Type.MUTED.draw(g, tab.font, L.t("wynnlabs.road.no_recipes"), x, cy, Theme.INK_3);
            return cy + Type.MUTED.lineHeight - y;
        }
        LevelRoad.Road road = data.road(profession, type, level, target);
        cy = bar(g, x, cy, w, level, target, road) + Theme.SP_4;
        cy = bombs(g, x, cy, w, mx, my);
        cy = prefRow(g, x, cy, w, road, mx, my) + Theme.SP_4;
        cy = steps.draw(g, x + Theme.SP_3, cy + Theme.SP_3, w - 2 * Theme.SP_3, road, level, top, bottom, mx, my);
        if (note != null) {
            cy += Theme.SP_3;
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, note, w), x, cy, Theme.GOOD);
            cy += Type.META.lineHeight;
        }
        return cy - y;
    }

    /** The name and level, the type chip and the target stepper; returns the y under them. */
    private int head(GuiGraphics g, int x, int y, int w, int level, int target, String type, int mx, int my) {
        String name = Professions.name(profession);
        Type.HEADING.draw(g, tab.font, name, x, y);
        int[] xp = CraftRoadData.xp(profession);
        String sub = "Lv " + level + (xp[1] > 0 ? " · %" + (100L * xp[0] / xp[1]) : "");
        Type.META.draw(g, tab.font, sub, x + Type.HEADING.width(tab.font, name) + Theme.SP_3, y + 3, Theme.INK_3);
        int r = x + w;
        int sq = Ui.CHIP_H + 2;
        // − Lv 40 +
        r -= sq;
        Ui.iconButton(g, "plus", r, y, false, mx, my);
        tab.acts.add(new Act(r, y, sq, Ui.CHIP_H, () -> setTarget(level, target + 1), null, null));
        String t = "Lv " + target;
        int tw = Math.max(Type.BODY.width(tab.font, "Lv 132"), Type.BODY.width(tab.font, t)) + 2 * Theme.SP_3;
        r -= Theme.SP_1 + tw;
        Kit.pill(g, r, y, tw, Ui.CHIP_H, false, false);
        Type.BODY.draw(g, tab.font, t, r + (tw - Type.BODY.width(tab.font, t)) / 2, Type.BODY.yIn(y, Ui.CHIP_H));
        r -= Theme.SP_1 + sq;
        Ui.iconButton(g, "minus", r, y, false, mx, my);
        tab.acts.add(new Act(r, y, sq, Ui.CHIP_H, () -> setTarget(level, target - 1), null, null));
        if (type != null) {
            String label = typeName(type);
            int cw = Ui.iconChipWidth(tab.font, typeIcon(type), label, true);
            r -= Theme.SP_4 + cw;
            UiMenu.chip(g, tab.font, typeIcon(type), label, r, y, tab.open == Drop.ROAD_TYPE, mx, my);
            int[] anchor = {r, y, cw, Ui.CHIP_H};
            tab.acts.add(new Act(r, y, cw, Ui.CHIP_H, () -> tab.toggle(Drop.ROAD_TYPE, anchor), null, List.of(L.c("wynnlabs.road.type_tip"))));
        }
        return y + Math.max(Type.HEADING.lineHeight, Ui.CHIP_H) + Theme.SP_4;
    }

    private void setTarget(int level, int target) {
        RoadChoices ch = RoadChoices.get();
        ch.targets.put(profession, ProfessionPlan.clampTarget(level, target));
        ch.save();
    }

    /** The bar from the level to the target, with each later band's start and what it unlocks under it. */
    private int bar(GuiGraphics g, int x, int y, int w, int level, int target, LevelRoad.Road road) {
        int[] xp = CraftRoadData.xp(profession);
        double into = xp[1] > 0 ? xp[0] / (double) xp[1] : 0;
        double span = Math.max(1, target - level);
        progress(g, x, y, w, 6, into / span);
        int ly = y + 6 + Theme.SP_2;
        int lastRight = x - 1;
        boolean any = false;
        for (LevelRoad.Band b : road.bands()) {
            if (b.from() <= level) {
                continue;
            }
            int tx = x + (int) Math.round(w * (b.from() - level) / span);
            g.fill(tx, y - 2, tx + 1, y + 8, Theme.INK_3);
            String label = L.t("wynnlabs.road.unlock", b.from(), b.materials().get(0).shown());
            int lw = Type.META.width(tab.font, label);
            int lx = Math.min(x + w - lw, Math.max(x, tx - lw / 2));
            if (lx > lastRight + Theme.SP_5) {
                Type.META.draw(g, tab.font, label, lx, ly, Theme.INK_3);
                lastRight = lx + lw;
                any = true;
            }
        }
        String end = "Lv " + target;
        int ew = Type.META.width(tab.font, end);
        if (x + w - ew > lastRight + Theme.SP_5) {
            Type.META.draw(g, tab.font, end, x + w - ew, ly, Theme.INK_3);
            any = true;
        }
        return any ? ly + Type.META.lineHeight : y + 6;
    }

    /** The Profession XP or Speed bomb on this world as a seal; the other worlds Wynntils heard of as one line. */
    private int bombs(GuiGraphics g, int x, int y, int w, int mx, int my) {
        List<ProfBomb> all = data.bombs();
        if (all.isEmpty()) {
            return y;
        }
        int cx = x;
        boolean drew = false;
        for (ProfBomb b : all) {
            if (!b.here()) {
                continue;
            }
            String text = L.t(b.speed() ? "wynnlabs.road.bomb_speed" : "wynnlabs.road.bomb_xp")
                    + (b.remainingMs() >= 0 ? " · " + minutes(b.remainingMs()) : "");
            int sw = Ui.seal(g, tab.font, text, cx, y, b.speed() ? Theme.LINK : Theme.GOOD);
            tab.acts.add(new Act(cx, y, sw, Ui.SEAL_H, null, null,
                    List.of(L.c(b.speed() ? "wynnlabs.road.bomb_speed_tip" : "wynnlabs.road.bomb_xp_tip"))));
            cx += sw + Theme.SP_2;
            drew = true;
        }
        // other worlds: only what Wynntils was told (some ranks hear of every world's bombs); never a full list
        List<String> others = new ArrayList<>();
        List<Component> tip = new ArrayList<>();
        for (ProfBomb b : all) {
            if (!b.here() && !b.speed()) {
                others.add(b.world() + " (" + minutes(b.remainingMs()) + ")");
                tip.add(Component.literal(b.world() + " · " + minutes(b.remainingMs())).withStyle(ChatFormatting.WHITE));
            }
        }
        if (!others.isEmpty()) {
            int lx = drew ? cx + Theme.SP_2 : x;
            String line = L.t("wynnlabs.road.bomb_elsewhere", String.join(", ", others));
            String shown = Type.META.fit(tab.font, line, x + w - lx);
            Type.META.draw(g, tab.font, shown, lx, Type.META.yIn(y, Ui.SEAL_H), Theme.INK_2);
            tip.add(L.c("wynnlabs.road.bomb_elsewhere_tip").withStyle(ChatFormatting.GRAY));
            tab.acts.add(new Act(lx, y, Type.META.width(tab.font, shown), Ui.SEAL_H, null, null, tip));
            drew = true;
        }
        return drew ? y + Ui.SEAL_H + Theme.SP_4 : y;
    }

    /** A progress bar on paper: the light well as its track, the fill as long as the share (0..1). */
    static void progress(GuiGraphics g, int x, int y, int w, int h, double frac) {
        g.fill(x, y, x + w, y + h, Theme.WELL);
        Kit.outline(g, x, y, w, h, 0x669A855C);
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
        if (fw > 0) {
            g.fill(x, y, x + fw, y + h, Theme.GREEN);
        }
    }

    static String minutes(long ms) {
        return ms < 0 ? "?" : Math.max(1, Math.round(ms / 60000.0)) + " dk";
    }

    /** "Malzeme: Topla / Al / Karışık" and the totals; returns the y under them. */
    private int prefRow(GuiGraphics g, int x, int y, int w, LevelRoad.Road road, int mx, int my) {
        RoadChoices ch = RoadChoices.get();
        String label = L.t("wynnlabs.road.materials");
        Type.META.draw(g, tab.font, label, x, Type.META.yIn(y, Ui.CHIP_H), Theme.INK_2);
        int cx = x + Type.META.width(tab.font, label) + Theme.SP_3;
        LevelRoad.Pref[] prefs = LevelRoad.Pref.values();
        for (LevelRoad.Pref p : prefs) {
            String t = L.t("wynnlabs.road.pref." + p.name().toLowerCase(Locale.ROOT));
            int cw = Ui.chipWidth(tab.font, t);
            Ui.chip(g, tab.font, t, cx, y, cw, ch.pref == p, false, false, mx, my);
            tab.acts.add(new Act(cx, y, cw, Ui.CHIP_H, () -> {
                ch.pref = p;
                ch.routes.clear();                  // the preference fills every step again
                ch.save();
            }, null, List.of(L.c("wynnlabs.road.pref_tip." + p.name().toLowerCase(Locale.ROOT)))));
            cx += cw + Theme.SP_1;
        }
        String sum = summary(road);
        int sx = cx + Theme.SP_5;
        if (sx + Type.META.width(tab.font, sum) > x + w) {
            y += Ui.CHIP_H + Theme.SP_2;
            sx = x;
        }
        String shown = Type.META.fit(tab.font, sum, x + w - sx);
        Type.META.draw(g, tab.font, shown, sx, Type.META.yIn(y, Ui.CHIP_H), Theme.INK_2);
        tab.acts.add(new Act(sx, y, Type.META.width(tab.font, shown), Ui.CHIP_H, null, null,
                List.of(L.c("wynnlabs.road.estimate_tip").withStyle(ChatFormatting.GRAY))));
        return y + Ui.CHIP_H;
    }

    /** "~4.260 XP · ~41 üretim · ~1,4 LE · 96 toplanacak". */
    private String summary(LevelRoad.Road r) {
        List<String> parts = new ArrayList<>();
        parts.add(r.xp() < 0 ? L.t("wynnlabs.road.xp_unknown") : "~" + CraftFormat.thousands((int) Math.min(Integer.MAX_VALUE, r.xp())) + " XP");
        if (r.crafts() >= 0) {
            parts.add(L.n("wynnlabs.road.crafts", r.crafts(), CraftFormat.thousands((int) Math.min(Integer.MAX_VALUE, r.crafts()))));
        }
        if (r.cost() == null) {
            parts.add(L.t("wynnlabs.road.cost_unknown"));
        } else if (r.cost() > 0) {
            parts.add("~" + io.github.anilken.wynnlabs.market.Prices.fmt(r.cost()));
        }
        if (r.toGather() > 0) {
            parts.add(L.t("wynnlabs.road.to_gather", CraftFormat.thousands((int) Math.min(Integer.MAX_VALUE, r.toGather()))));
        }
        return String.join(" · ", parts);
    }

    /** Every recipe is open: a calm page, and the way on to the cap. */
    private int finished(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int cx = x + w / 2;
        int cy = y + Theme.SP_7 * 2;
        Art.icon(g, "check", cx - 12, cy, 24, Theme.GOOD);
        cy += 24 + Theme.SP_4;
        String t = L.t("wynnlabs.road.done", Professions.name(profession));
        Type.TITLE.draw(g, tab.font, t, cx - Type.TITLE.width(tab.font, t) / 2, cy, Theme.INK);
        cy += Type.TITLE.lineHeight + Theme.SP_3;
        String s = L.t("wynnlabs.road.done_sub", tab.knownLevel(profession.toLowerCase(Locale.ROOT)));
        Type.MUTED.draw(g, tab.font, s, cx - Type.MUTED.width(tab.font, s) / 2, cy, Theme.INK_2);
        cy += Type.MUTED.lineHeight + Theme.SP_5;
        String link = L.t("wynnlabs.road.to_cap", ProfessionPlan.MAX_LEVEL);
        int lw = Type.LINK.width(tab.font, link);
        int lx = cx - lw / 2;
        boolean hover = in(mx, my, lx, cy - 2, lw, Type.LINK.lineHeight + 2);
        Type.LINK.draw(g, tab.font, link, lx, cy, hover ? Theme.INK : Theme.LINK);
        int level = level(profession);
        tab.acts.add(new Act(lx, cy - 2, lw, Type.LINK.lineHeight + 2, () -> setTarget(level, ProfessionPlan.MAX_LEVEL), null, null));
        return cy + Type.LINK.lineHeight - y;
    }

    // ---- a gathering profession ----

    private int gathering(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int level = level(profession);
        int cy = y;
        Type.HEADING.draw(g, tab.font, Professions.name(profession), x, cy);
        Type.META.draw(g, tab.font, "Lv " + level, x + Type.HEADING.width(tab.font, Professions.name(profession)) + Theme.SP_3, cy + 3, Theme.INK_3);
        cy += Type.HEADING.lineHeight + Theme.SP_4;
        double each = ProfessionXp.last(profession);
        String line = each <= 0 ? L.t("wynnlabs.plan.gather_xp_none") : L.t("wynnlabs.plan.gather_xp_only", CraftFormat.dec(each, 1));
        Type.MUTED.draw(g, tab.font, Type.MUTED.fit(tab.font, line, w), x, cy, Theme.INK_2);
        cy += Type.MUTED.lineHeight + Theme.SP_4;
        cy = bombs(g, x, cy, w, mx, my);
        cy = tab.recipePanel.head(g, L.t("wynnlabs.plan.spots"), null, x, cy, w);
        List<ProfessionPlan.Spot> spots = data.nearestSpots(profession, level);
        if (spots.isEmpty()) {
            Type.MUTED.draw(g, tab.font, Type.MUTED.fit(tab.font, L.t("wynnlabs.plan.no_spots"), w), x, cy, Theme.INK_3);
            return cy + Type.MUTED.lineHeight - y;
        }
        int rowH = Type.BODY.lineHeight + Type.META.lineHeight;
        for (ProfessionPlan.Spot s : spots) {
            cy = steps.spotRow(g, s, profession, x, cy, w, rowH, mx, my) + Theme.SP_3;
        }
        return cy - y;
    }
}
