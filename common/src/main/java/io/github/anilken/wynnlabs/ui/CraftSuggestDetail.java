package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.crafting.SavedRecipes;
import io.github.anilken.wynnlabs.crafting.SuggestionBoard;
import io.github.anilken.wynnlabs.crafting.Suggestions;
import io.github.anilken.wynnlabs.crafting.WynnBuilderCode;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import static io.github.anilken.wynnlabs.ui.CraftFormat.range;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * One suggestion in full, on a soft card (the suggestions page's right side, a set's piece, or the
 * small window's drawer): the item, the search tier to show it at, the materials, the 2×3 recipe
 * with what is owned or its price, the identifications, durability and requirements against the
 * player's points, one status, and its actions (to the recipe page, WynnBuilder link, save, code).
 */
final class CraftSuggestDetail extends CraftPanel {

    /** What is shown: a single suggestion or a set's piece. */
    record Shown(Suggestions.GoalSet goalSet, CraftData.Recipe recipe, int tier1, int tier2, CraftSearch.Layout layout) {

        String goal() {
            return goalSet.key();
        }

        Map<String, Double> weights() {
            return goalSet.goal().weights();
        }
    }

    /** A goal's name, or a combination's ("Raw spell + Mana + Spell cost"). */
    static String goalLabel(String key) {
        List<String> names = new ArrayList<>();
        for (String k : Suggestions.parts(key)) {
            names.add(L.t("wynnlabs.craft.goalset." + k));
        }
        return String.join(" + ", names);
    }

    CraftSuggestDetail(CraftingTab tab) {
        super(tab);
    }

    /** The status of a shown thing for this player. */
    SuggestionBoard.Status status(CraftData.Recipe r, CraftSearch.Layout l) {
        return SuggestionBoard.status(r, l, tab::level);
    }

    /** The status as words: "Ready", "Buy 2 · 1.4 LE", "Jeweling 105" ({@code needs} for a locked one). */
    String statusText(SuggestionBoard.Status st, CraftData.Recipe r, CraftSearch.Layout l, boolean needsWord) {
        return switch (st) {
            case READY -> L.t("wynnlabs.craft.status.ready");
            case BUY -> L.t("wynnlabs.craft.status.buy", l.bought().size()) + CraftSuggestionsPanel.costPart(tab.liveCost(l.bought()));
            case LEVEL -> {
                String prof = Professions.name(r.profession().toUpperCase(Locale.ROOT));
                int n = SuggestionBoard.needs(r, l.slots());
                yield needsWord ? L.t("wynnlabs.craft.suggest_locked", prof, n) : prof + " " + n;
            }
        };
    }

    static String statusIcon(SuggestionBoard.Status st) {
        return switch (st) {
            case READY -> "check";
            case BUY -> "shopping-cart";
            case LEVEL -> "lock";
        };
    }

    static int statusInk(SuggestionBoard.Status st) {
        return switch (st) {
            case READY -> Theme.GOOD;
            case BUY -> Theme.WARN;
            case LEVEL -> Theme.INK_3;
        };
    }

    /** The status line: its icon on the text's capitals, then the text cut to {@code w}. */
    void statusLine(GuiGraphics g, Type role, SuggestionBoard.Status st, String text, int x, int y, int w) {
        int ink = statusInk(st);
        Art.icon(g, statusIcon(st), x, role.onCaps(y, 6), 6, ink);
        role.draw(g, tab.font, role.fit(tab.font, text, w - 6 - Theme.SP_1), x + 6 + Theme.SP_1, y, ink);
    }

    /**
     * Draws the card in {x, y, w, h}. {@code tier} is the tier shown (null: no tier choice, a set's
     * piece) and {@code pickTier} takes a new one; {@code back} (null for none) is a chip at the top.
     * Its click areas go to {@code acts}.
     */
    void draw(GuiGraphics g, Shown s, int x, int y, int w, int h, CraftSearch.Tier tier, Consumer<CraftSearch.Tier> pickTier,
              String back, Runnable backAct, boolean framed, List<Act> acts, int mx, int my) {
        int p = framed ? Theme.SP_4 : 0;
        if (framed) {
            Kit.softCard(g, x, y, w, h);
        }
        int ix = x + p;
        int iw = w - 2 * p;
        int cy = y + p;
        int foot = y + h - p - Ui.BUTTON_H;
        g.enableScissor(x, y, x + w, y + h + 1);
        if (back != null) {
            int bw = Ui.iconChipWidth(tab.font, "chevron-left", back, false);
            Ui.iconChip(g, tab.font, "chevron-left", back, false, ix, cy, bw, false, mx, my);
            acts.add(new Act(ix, cy, bw, Ui.CHIP_H, backAct, null, null));
            cy += Ui.CHIP_H + Theme.SP_3;
        }
        CraftData.Recipe r = s.recipe();
        CraftSearch.Layout l = s.layout();
        String sub = "Lv " + r.bracket().replace('-', '–') + " · " + Professions.name(r.profession().toUpperCase(Locale.ROOT)) + " "
                + SuggestionBoard.needs(r, l.slots());
        if (framed) {
            CraftIcons.slot(g, ix, cy, 24, CraftIcons.type(r.type()));
            int tx = ix + 24 + Theme.SP_3;
            String name = L.t("wynnlabs.craft.suggest_name", goalLabel(s.goal()), typeName(r.type()));
            Type.LABEL.draw(g, tab.font, Type.LABEL.fit(tab.font, name, ix + iw - tx), tx, cy + 1, Theme.TIER_LEGENDARY);
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, sub, ix + iw - tx), tx, cy + 1 + Type.LABEL.lineHeight, Theme.INK_3);
            cy += 24 + Theme.SP_3;
        } else {
            // in a drawer: its title names it; the model beside the level line
            CraftIcons.slot(g, ix, cy, 18, CraftIcons.type(r.type()));
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, sub, iw - 18 - Theme.SP_3), ix + 18 + Theme.SP_3, Type.META.yIn(cy, 18),
                    Theme.INK_3);
            cy += 18 + Theme.SP_3;
        }
        if (tier != null) {
            int cx = ix;
            for (CraftSearch.Tier t : SuggestionBoard.TIERS) {
                String label = L.t("wynnlabs.craft.tier." + t.name().toLowerCase(Locale.ROOT));
                int cw = Ui.chipWidth(tab.font, label);
                if (cx + cw > ix + iw) {
                    break;
                }
                Ui.chip(g, tab.font, label, cx, cy, cw, t == tier, false, false, mx, my);
                acts.add(new Act(cx, cy, cw, Ui.CHIP_H, () -> pickTier.accept(t), null,
                        List.of(L.c("wynnlabs.craft.tier_tip." + t.name().toLowerCase(Locale.ROOT)))));
                cx += cw + Theme.SP_2;
            }
            cy += Ui.CHIP_H + Theme.SP_3;
        }
        cy = materials(g, r, s.tier1(), s.tier2(), ix, cy, iw) + Theme.SP_3;
        cy = grid(g, l, ix, cy, iw, acts) + Theme.SP_3;
        // the identifications, then durability and requirements, as far as the foot leaves room
        int sealsH = Ui.SEAL_H + Theme.SP_4;
        int room = foot - Theme.SP_3 - 1 - Theme.SP_3 - sealsH;
        if (cy + 1 + Theme.SP_3 + Type.BODY.lineHeight <= room) {
            Kit.hair(g, ix, cy, iw, 1, Theme.LINE);
            cy += 1 + Theme.SP_3;
            for (Map.Entry<String, Craft.Range> e : ids(l.result(), s.weights())) {
                if (cy + Type.BODY.lineHeight > room) {
                    break;
                }
                Craft.Range v = e.getValue();
                String val = range(v, tab.unit(e.getKey()));
                int vw = Type.BODY.width(tab.font, val);
                Type.BODY.draw(g, tab.font, Type.BODY.fit(tab.font, tab.statName(e.getKey()), iw - vw - Theme.SP_4), ix, cy, Theme.INK_2);
                Type.BODY.drawRight(g, tab.font, val, ix + iw, cy, v.max() < 0 ? Theme.BAD : Theme.GOOD);
                cy += Type.BODY.lineHeight + Theme.SP_1;
            }
            cy += Theme.SP_1;
        }
        if (cy + Ui.SEAL_H <= foot - Theme.SP_3 - 1 - Theme.SP_3) {
            seals(g, r, l.result(), ix, cy, iw);
        }
        // the foot: one status, the actions
        Kit.hair(g, ix, foot - Theme.SP_3 - 1, iw, 1, Theme.LINE);
        int right = ix + iw;
        String open = L.t("wynnlabs.craft.suggest_open");
        int ow = Ui.buttonWidth(tab.font, open);
        Ui.button(g, tab.font, open, right - ow, foot, ow, mx, my);
        acts.add(new Act(right - ow, foot, ow, Ui.BUTTON_H, () -> open(s), null, List.of(L.c("wynnlabs.craft.suggest_open_tip"))));
        right -= ow + Theme.SP_2;
        int by = foot + (Ui.BUTTON_H - Ui.CHIP_H) / 2;
        String[][] icons = {{"copy", "wynnlabs.craft.tip.code"}, {"bookmark", "wynnlabs.craft.tip.save_one"},
                {"world", "wynnlabs.craft.tip.wb_link"}};
        for (String[] ic : icons) {
            int bw = Ui.CHIP_H + 2;
            right -= bw;
            Ui.iconButton(g, ic[0], right, by, false, mx, my);
            Runnable act = switch (ic[0]) {
                case "copy" -> () -> copyCode(s);
                case "bookmark" -> () -> save(s);
                default -> () -> CraftSetsPanel.copyLink(tab, WynnBuilderCode.link(recipeOf(s)));
            };
            acts.add(new Act(right, by, bw, Ui.CHIP_H, act, null, List.of(L.c(ic[1]))));
            right -= Theme.SP_2;
        }
        SuggestionBoard.Status st = status(r, l);
        statusLine(g, Type.BODY, st, statusText(st, r, l, true), ix, Type.BODY.yIn(foot, Ui.BUTTON_H), right - Theme.SP_2 - ix);
        g.disableScissor();
    }

    /** The height a detail needs to show its rows (a drawer gives it at least this). */
    static final int MIN_H = 200;

    /** The small recipe slot in a card's 2×3 grid. */
    static final int MINI = 12;

    /**
     * A suggestion on a calm card (a ranked single, a set's piece): its model, its name with an
     * optional goal tag, "gain · Dur N", one quiet status, and its recipe as a small 2×3 grid of the
     * ingredients' icons (one to buy marked brown). The picked one on the gold wash.
     */
    void calmCard(GuiGraphics g, Suggestions.GoalSet set, String name, String tag, CraftData.Recipe r, CraftSearch.Layout l,
                  int x, int y, int w, boolean hover, boolean lit) {
        int h = CraftSuggestionsPanel.CARD_H;
        if (lit) {
            Kit.rowLit(g, x, y, w, h);
        } else {
            Kit.softCard(g, x, y, w, h);
        }
        int p = Theme.SP_3;
        CraftIcons.slot(g, x + p, y + (h - 24) / 2, 24, CraftIcons.type(r.type()));
        int gridW = 2 * MINI + Theme.SP_1;
        int gridH = 3 * MINI + 2 * Theme.SP_1;
        int gx = x + w - p - gridW;
        int tx = x + p + 24 + Theme.SP_4;
        int tw = (l == null ? x + w - p : gx - Theme.SP_4) - tx;
        int ty = y + Theme.SP_2;
        int ink = hover ? Theme.LINK : Theme.TIER_LEGENDARY;
        String n = Type.LABEL.fit(tab.font, name, tw);
        Type.LABEL.draw(g, tab.font, n, tx, ty, ink);
        if (tag != null) {
            int ax = tx + Type.LABEL.width(tab.font, n) + Theme.SP_3;
            int aw = Type.META.width(tab.font, tag) + 2 * Theme.SP_2;
            if (ax + aw <= tx + tw) {
                Kit.pill(g, ax, ty + (Type.LABEL.lineHeight - Ui.SEAL_H) / 2, aw, Ui.SEAL_H, false, false);
                Type.META.draw(g, tab.font, tag, ax + Theme.SP_2, Type.META.yIn(ty + (Type.LABEL.lineHeight - Ui.SEAL_H) / 2, Ui.SEAL_H),
                        Theme.INK_2);
            }
        }
        if (l == null) {
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, L.t("wynnlabs.craft.set_piece_none"), tw), tx, ty + Type.LABEL.lineHeight,
                    Theme.INK_3);
            return;
        }
        List<Map.Entry<String, Craft.Range>> ids = ids(l.result(), set.goal().weights());
        String gain = ids.isEmpty() ? "" : tab.statName(ids.get(0).getKey()) + " " + range(ids.get(0).getValue(), tab.unit(ids.get(0).getKey()));
        if (!r.consumable() && l.result().durability() != null) {
            gain += (gain.isEmpty() ? "" : " · ") + L.t("wynnlabs.craft.dur_short", l.result().durability().min());
        } else if (r.consumable()) {
            gain += (gain.isEmpty() ? "" : " · ") + L.n("wynnlabs.craft.charges_n", l.result().charges());
        }
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, gain, tw), tx, ty + Type.LABEL.lineHeight, Theme.INK_2);
        SuggestionBoard.Status st = status(r, l);
        String text = statusText(st, r, l, false);
        int sy = ty + Type.LABEL.lineHeight + Type.META.lineHeight;
        int sInk = statusInk(st);
        int sx = tx;
        if (st == SuggestionBoard.Status.LEVEL) {
            Art.icon(g, "lock", sx, Type.META.onCaps(sy, 6), 6, sInk);
            sx += 6 + Theme.SP_1;
        }
        Type.META.draw(g, tab.font, Type.META.fit(tab.font, text, tx + tw - sx), sx, sy, sInk);
        miniGrid(g, l, gx, y + (h - gridH) / 2);
    }

    /** The recipe as the game lays it out, 2 by 3, in small slots; an ingredient to buy has a brown mark. */
    void miniGrid(GuiGraphics g, CraftSearch.Layout l, int x, int y) {
        for (int k = 0; k < Craft.SLOTS; k++) {
            int sx = x + (k % 2) * (MINI + Theme.SP_1);
            int sy = y + (k / 2) * (MINI + Theme.SP_1);
            Ingredient in = k >= l.slots().size() ? Ingredient.NONE : l.slots().get(k);
            CraftIcons.slot(g, sx, sy, MINI, in.empty() ? null : CraftIcons.of(in.name()));
            if (!in.empty() && l.bought().contains(in.name())) {
                g.fill(sx + MINI - 3, sy, sx + MINI, sy + 3, Theme.WARN);
            }
        }
    }

    /** "12× Dernic Ingot ★★★  6× Dernic String ★★☆"; returns the y under it. */
    private int materials(GuiGraphics g, CraftData.Recipe r, int t1, int t2, int x, int y, int w) {
        int cx = x;
        for (int i = 0; i < r.materials().size() && i < 2; i++) {
            CraftData.Material m = r.materials().get(i);
            String t = m.amount() + "× " + m.shown();
            int tw = Type.META.width(tab.font, t);
            if (cx + tw + Theme.SP_1 + 3 * 7 > x + w) {
                break;
            }
            Type.META.draw(g, tab.font, t, cx, y, Theme.INK_2);
            cx += tw + Theme.SP_1;
            int tier = i == 0 ? t1 : t2;
            for (int k = 0; k < 3; k++) {
                Art.icon(g, k < tier ? "star-filled" : "star", cx, Type.META.onCaps(y, 6), 6, k < tier ? Theme.GOLD_DEEP : Theme.LINE);
                cx += 7;
            }
            cx += Theme.SP_4;
        }
        return y + Type.META.lineHeight;
    }

    /** The 2×3 recipe: each slot, the ingredient's name, and "×4" owned or its price; returns the y under it. */
    private int grid(GuiGraphics g, CraftSearch.Layout l, int x, int y, int w, List<Act> acts) {
        int cw = (w - Theme.SP_4) / 2;
        int rowH = 18 + Theme.SP_2;
        for (int i = 0; i < Craft.SLOTS && i < l.slots().size(); i++) {
            Ingredient in = l.slots().get(i);
            int gx = x + (i % 2) * (cw + Theme.SP_4);
            int gy = y + (i / 2) * rowH;
            CraftIcons.slot(g, gx, gy, 18, in.empty() ? null : CraftIcons.of(in.name()));
            if (in.empty()) {
                continue;
            }
            int tx = gx + 18 + Theme.SP_2;
            boolean buy = l.bought().contains(in.name());
            String mark;
            int ink;
            if (buy) {
                CraftSearch.Price pr = tab.prices.get(in.name());
                mark = pr == null ? "?" : Prices.fmt(pr.emeralds());
                ink = Theme.WARN;
                if (pr == null) {
                    tab.wantedPrices.add(in.name());
                }
            } else {
                mark = "×" + tab.owned(in.name());
                ink = Theme.GOOD;
            }
            int mw = Type.META.width(tab.font, mark);
            int ty = Type.META.yIn(gy, 18);
            Type.META.draw(g, tab.font, Type.META.fit(tab.font, in.name(), cw - 18 - Theme.SP_2 - mw - Theme.SP_2), tx, ty, Theme.INK);
            Type.META.drawRight(g, tab.font, mark, gx + cw, ty, ink);
            acts.add(new Act(gx, gy, cw, 18, null, null, card(in)));
        }
        return y + 3 * rowH - Theme.SP_2;
    }

    /** The identifications to list: the goal's first, then the other gains, then what it gives up. */
    static List<Map.Entry<String, Craft.Range>> ids(Craft.Result c, Map<String, Double> weights) {
        return ids(c.ids(), weights);
    }

    /** The same for identifications added up (a set's totals). */
    static List<Map.Entry<String, Craft.Range>> ids(Map<String, Craft.Range> all, Map<String, Double> weights) {
        List<Map.Entry<String, Craft.Range>> first = new ArrayList<>();
        List<Map.Entry<String, Craft.Range>> gains = new ArrayList<>();
        List<Map.Entry<String, Craft.Range>> losses = new ArrayList<>();
        for (Map.Entry<String, Craft.Range> e : all.entrySet()) {
            Craft.Range v = e.getValue();
            if (v.min() == 0 && v.max() == 0) {
                continue;
            }
            if (v.max() < 0) {
                losses.add(e);
            } else if (weights.containsKey(e.getKey())) {
                first.add(e);
            } else {
                gains.add(e);
            }
        }
        List<String> order = new ArrayList<>(weights.keySet());
        first.sort((a, b) -> Integer.compare(order.indexOf(a.getKey()), order.indexOf(b.getKey())));
        first.addAll(gains);
        first.addAll(losses);
        return first;
    }

    /** Durability (or charges) and each requirement against the player's points, as seals in one row. */
    private void seals(GuiGraphics g, CraftData.Recipe r, Craft.Result c, int x, int y, int w) {
        List<String> texts = new ArrayList<>();
        List<Integer> inks = new ArrayList<>();
        if (r.consumable()) {
            texts.add(L.n("wynnlabs.craft.charges_n", c.charges()));
            inks.add(Theme.INK_3);
        } else if (c.durability() != null) {
            texts.add(L.t("wynnlabs.craft.durability", range(c.durability(), "")));
            inks.add(c.durability().min() <= 0 ? Theme.BAD : Theme.INK_3);
        }
        reqSeals(c.requirements(), texts, inks);
        int cx = x;
        for (int i = 0; i < texts.size(); i++) {
            int sw = Type.META.width(tab.font, texts.get(i)) + 2 * Theme.SP_2;
            if (cx + sw > x + w) {
                break;
            }
            cx += Ui.seal(g, tab.font, texts.get(i), cx, y, inks.get(i)) + Theme.SP_2;
        }
    }

    /** "Int 64 ✔" (met) or "Agi 64 · you 60" (not), for each skill asked. */
    void reqSeals(int[] req, List<String> texts, List<Integer> inks) {
        for (int k = 0; k < 5 && k < req.length; k++) {
            if (req[k] <= 0) {
                continue;
            }
            String sk = L.t("wynnlabs.craft.skill_short." + io.github.anilken.wynnlabs.crafting.CraftChecks.SKILLS.get(k));
            boolean ok = tab.points[k] >= req[k];
            texts.add(ok ? L.t("wynnlabs.craft.req_ok", sk, req[k]) : L.t("wynnlabs.craft.req_short", sk, req[k], tab.points[k]));
            inks.add(ok ? Theme.GOOD : Theme.BAD);
        }
    }

    // ---- actions ----

    static RecipeCode.Recipe recipeOf(Shown s) {
        List<String> names = new ArrayList<>();
        for (Ingredient in : s.layout().slots()) {
            names.add(in.empty() ? null : in.name());
        }
        return new RecipeCode.Recipe(s.recipe().type(), s.recipe().minLevel(), s.tier1(), s.tier2(), Craft.Speed.NORMAL, names);
    }

    static String code(Shown s) {
        RecipeCode.Recipe r = recipeOf(s);
        String code = WynnBuilderCode.encode(r);
        return code != null ? code : RecipeCode.encode(r);
    }

    private void copyCode(Shown s) {
        Minecraft.getInstance().keyboardHandler.setClipboard(code(s));
        tab.flash.accept(L.t("wynnlabs.craft.code_copied"));
    }

    /** Puts it on the recipe page, with its goal. */
    void open(Shown s) {
        tab.type = s.recipe().type();
        tab.minLevel = s.recipe().minLevel();
        tab.tier1 = s.tier1();
        tab.tier2 = s.tier2();
        tab.speed = Craft.Speed.NORMAL;
        for (int i = 0; i < Craft.SLOTS; i++) {
            Ingredient in = s.layout().slots().get(i);
            tab.slots[i] = in.empty() ? null : in.name();
        }
        tab.weights.clear();
        tab.weights.putAll(s.weights());
        tab.drawers.close();
        tab.view = CraftingTab.VIEW_RECIPE;
        tab.scroll = 0;
        tab.flash.accept(L.t("wynnlabs.craft.applied"));
    }

    /** Saves it to the saved recipes as "Raw spell Leggings". */
    void save(Shown s) {
        SavedRecipes saved = Crafting.saved();
        String name = saved.free(L.t("wynnlabs.craft.suggest_name", goalLabel(s.goal()), typeName(s.recipe().type())));
        saveOne(saved, s, name);
        Crafting.persist();
        tab.flash.accept(L.t("wynnlabs.craft.saved_one", name));
    }

    static void saveOne(SavedRecipes saved, Shown s, String name) {
        RecipeCode.Recipe r = recipeOf(s);
        saved.save(new SavedRecipes.Saved(name, r.type(), r.minLevel(), r.tier1(), r.tier2(), r.speed().name(),
                java.util.Collections.unmodifiableList(new ArrayList<>(r.slots())),
                SavedRecipes.SavedGoal.of(s.goalSet().goal()), System.currentTimeMillis()), false);
    }

    /** Tooltip lines for a card: its ingredients. */
    static List<Component> ingredientTip(CraftSearch.Layout l) {
        List<Component> tip = new ArrayList<>();
        for (Ingredient in : l.slots()) {
            tip.add(Component.literal(in.empty() ? "—" : in.name()));
        }
        return tip;
    }
}
