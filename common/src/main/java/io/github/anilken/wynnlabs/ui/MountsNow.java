package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.mounts.Breeding;
import io.github.anilken.wynnlabs.mounts.FeedPlan;
import io.github.anilken.wynnlabs.mounts.FeedStep;
import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.Mount;
import io.github.anilken.wynnlabs.mounts.MountStat;
import io.github.anilken.wynnlabs.mounts.MountType;
import io.github.anilken.wynnlabs.mounts.RoadMap;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The road map's "Now": the steps for the mount you work on (what is in its queue, each food with
 * where it comes from, the rides that train a stat, then the breeding) with the shopping list
 * beside them. The first step is lit; a click on it opens its sentence and, for a food, the green
 * gain it gives each stat. The feed plan is exact; the planner says when it is only the best found.
 */
final class MountsNow {

    /** One row: an icon or an item, its name, level, a note and its colour, the time, what a click opens. */
    record Step(String icon, String item, String name, String level, String note, int noteInk, long sec, String sentence,
                int[] gains) {}

    private static final int ICON = 16;
    private static final int SHOP_W = 190;

    private final MountsTab tab;
    private final MountsScroll scroll = new MountsScroll();
    boolean open;

    MountsNow(MountsTab tab) {
        this.tab = tab;
    }

    private net.minecraft.client.gui.Font font() {
        return tab.font();
    }

    void draw(GuiGraphics g, int x, int y, int w, int h, RoadMap.Plan plan, int mx, int my) {
        MountType type = tab.model.type();
        MountsModel.Owned cur = tab.model.current();
        Mount m = cur != null ? cur.mount() : Mount.fresh(type);
        // with a mount: its plan as the cache, the running job or the estimate has it; without one,
        // the first generation's estimate from the road map
        MountsModel.FeedView view = cur != null ? tab.model.feed(cur) : null;
        FeedPlan fp = view != null ? view.plan()
                : plan != null && !plan.generations().isEmpty() ? plan.generations().get(0).feedPlan() : null;
        boolean full = cur != null && tab.model.goalFor(cur).fullLimits();
        List<Step> steps = steps(cur, m, fp, plan, full);
        boolean shop = !tab.compact() && w >= 460;
        int sw = shop ? SHOP_W : 0;
        int lw = shop ? w - sw - 2 * Theme.SP_5 - 1 : w;
        long total = steps.stream().mapToLong(Step::sec).sum();
        String head = L.t("wynnlabs.mounts.now") + " · " + headline(cur, m);
        String time = total > 0 ? "~" + MountsText.dur(total) : "";
        int tw = Type.META.width(font(), time);
        Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), head, lw - tw - Theme.SP_3), x, y, Theme.INK);
        Type.META.drawRight(g, font(), time, x + lw, y, Theme.WARN);
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, lw, 1, Theme.LINE);
        int top = y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
        if (view != null) {
            top += status(g, x, top, lw, cur, view, mx, my) + Theme.SP_3;
        }
        list(g, x, top, lw, y + h - top, steps, fp, mx, my);
        if (shop) {
            int sx = x + lw + Theme.SP_5;
            Kit.hair(g, sx, y, 1, h, Theme.LINE);
            shopping(g, sx + 1 + Theme.SP_5, y, sw, h, cur, fp, plan, mx, my);
        }
    }

    /**
     * Where the exact plan stands, one line with its one action: an estimate with "Calculate plan";
     * running with the states searched and "Cancel"; calculated, with when; the mount changed
     * since, with "Recalculate". The exact plan never starts by itself. Returns its height.
     */
    private int status(GuiGraphics g, int x, int y, int w, MountsModel.Owned cur, MountsModel.FeedView v, int mx, int my) {
        String text;
        int ink = Theme.INK_2;
        String action = null;
        String icon = null;
        Runnable run = null;
        switch (v.state()) {
            case RUNNING -> {
                text = L.t("wynnlabs.mounts.calc_running", String.format(java.util.Locale.ROOT, "%,d", v.explored()));
                action = L.t("wynnlabs.mounts.calc_cancel");
                icon = "x";
                run = MountsModel::cancelFeed;
            }
            case STALE -> {
                text = L.t("wynnlabs.mounts.calc_stale", MenuPaint.ago(v.savedAt()));
                ink = Theme.WARN;
                action = L.t("wynnlabs.mounts.calc_again");
                icon = "refresh";
                run = () -> tab.model.startFeed(cur);
            }
            case CACHED -> text = L.t(v.plan() != null && v.plan().proven() ? "wynnlabs.mounts.calc_done" : "wynnlabs.mounts.calc_partial",
                    MenuPaint.ago(v.savedAt()));
            default -> {
                text = L.t("wynnlabs.mounts.calc_estimate");
                action = L.t("wynnlabs.mounts.calc_start");
                icon = "player-play";
                run = () -> tab.model.startFeed(cur);
            }
        }
        int bw = action == null ? 0 : 2 * Theme.SP_3 + 8 + Theme.SP_1 + Type.BODY.width(font(), action);
        int ty = Type.META.yIn(y, Ui.SMALL_H);
        if (v.state() == MountsModel.FeedState.RUNNING) {
            Art.icon(g, "clock", x, Type.META.onCaps(ty, 8), 8, Theme.GOLD_EDGE);
        } else {
            Art.icon(g, v.state() == MountsModel.FeedState.STALE ? "alert-triangle" : "info-circle", x, Type.META.onCaps(ty, 8), 8, ink);
        }
        int tx = x + 8 + Theme.SP_2;
        Type.META.draw(g, font(), Type.META.fit(font(), text, w - (tx - x) - (bw > 0 ? bw + Theme.SP_3 : 0)), tx, ty, ink);
        if (action != null) {
            int bx = x + w - bw;
            boolean hover = mx >= bx && mx < bx + bw && my >= y && my < y + Ui.SMALL_H;
            Kit.pill(g, bx, y, bw, Ui.SMALL_H, v.state() != MountsModel.FeedState.RUNNING, hover);
            Art.icon(g, icon, bx + Theme.SP_3, y + Ui.iconIn(Ui.SMALL_H, 8), 8, Theme.INK);
            Type.BODY.draw(g, font(), action, bx + Theme.SP_3 + 8 + Theme.SP_1, Type.BODY.yIn(y, Ui.SMALL_H), Theme.INK);
            tab.click(bx, y, bw, Ui.SMALL_H, run);
            if (hover) {
                tab.tip(bx, y, bw, Ui.SMALL_H, List.of(Component.literal(L.t("wynnlabs.mounts.calc_tip")).withStyle(ChatFormatting.GRAY)));
            }
        }
        return Ui.SMALL_H;
    }

    private String headline(MountsModel.Owned cur, Mount m) {
        if (cur == null) {
            return L.t("wynnlabs.mounts.head_buy", m.type().display());
        }
        var goal = tab.model.goalFor(cur);
        if (goal.reachedBy(m.queue().applyTo(m.stats()))) {
            return L.t("wynnlabs.mounts.head_breed", m.name());
        }
        return L.t(goal.fullLimits() ? "wynnlabs.mounts.head_full" : "wynnlabs.mounts.head_ready", m.name());
    }

    // ---- the steps ----

    private List<Step> steps(MountsModel.Owned cur, Mount m, FeedPlan fp, RoadMap.Plan plan, boolean full) {
        List<Step> out = new ArrayList<>();
        MountsModel.Options opt = MountsModel.options();
        if (cur == null) {
            int n = plan == null ? 2 : Math.max(1, plan.mountsToBuy());
            out.add(new Step("shopping-cart", null, L.t("wynnlabs.mounts.step_buy", n, m.type().display()), null,
                    opt.buyTm ? L.t("wynnlabs.mounts.at_tm") : L.t("wynnlabs.mounts.at_merchant"), Theme.INK_2, 0,
                    L.t("wynnlabs.mounts.say_buy", n, m.type().display()), null));
        }
        long left = m.queue().secondsLeft();
        for (Material q : m.queue().pending()) {
            out.add(new Step(null, q.name(), q.name(), "L" + q.level(), L.t("wynnlabs.mounts.queued", MountsText.dur(left)), Theme.INK_2,
                    0, L.t("wynnlabs.mounts.say_queued", q.name()), m.stats().gainsFrom(q)));
        }
        if (fp != null) {
            var stats = m.queue().applyTo(m.stats());
            for (FeedStep s : fp.steps()) {
                if (s.isFeed()) {
                    Material mat = s.material();
                    int bank = opt.useBank ? MountsModel.inBank(mat) : 0;
                    double price = MountsModel.price(mat);
                    String note = bank > 0 ? L.t("wynnlabs.mounts.in_bank") : price >= 0 ? L.t("wynnlabs.mounts.buy_at", MountsText.em(price)) : "";
                    String say = L.t(s.swap() ? "wynnlabs.mounts.say_swap" : "wynnlabs.mounts.say_feed", mat.name(), m.name());
                    out.add(new Step(null, mat.name(), mat.name(), "L" + mat.level(), note, bank > 0 ? Theme.GOOD : Theme.INK_2,
                            s.durationSec(), say, stats.gainsFrom(mat)));
                    stats = stats.fed(mat);
                } else {
                    String stat = s.stat() == null ? "" : s.stat().display();
                    String note = s.coveredSec() > 0 ? L.t("wynnlabs.mounts.covered") : "";
                    out.add(new Step("trending-up", null, L.t("wynnlabs.mounts.step_ride", stat, s.toLevel()), null, note, Theme.INK_2,
                            s.durationSec() - s.coveredSec(), L.t("wynnlabs.mounts.say_ride", m.name(), stat, s.toLevel()), null));
                }
            }
        }
        if (cur != null && (fp != null || tab.model.goalFor(cur).reachedBy(m.queue().applyTo(m.stats())))) {
            String partner = partner(cur);
            String when = full ? L.t("wynnlabs.mounts.full_limits") : L.t("wynnlabs.mounts.when_ready");
            out.add(new Step("heart", null, partner == null ? L.t("wynnlabs.mounts.step_breed_any", m.type().display())
                    : L.t("wynnlabs.mounts.step_breed", partner), null, when, Theme.INK_2, Breeding.constants().breedSec(),
                    L.t("wynnlabs.mounts.say_breed"), null));
        }
        return out;
    }

    /** The other mount of the type with the most potential, or null. */
    private String partner(MountsModel.Owned cur) {
        for (MountsModel.Owned o : tab.model.owned(cur.mount().type())) {
            if (o != cur) {
                return o.mount().name();
            }
        }
        return null;
    }

    private void list(GuiGraphics g, int x, int y, int w, int h, List<Step> steps, FeedPlan fp, int mx, int my) {
        int cy = scroll.begin(g, x, y, w, h);
        int top = cy;
        int cw = scroll.width(w);
        int rowH = Theme.SP_2 + Math.max(ICON, Type.BODY.lineHeight) + Theme.SP_2;
        int timeW = 0;
        for (Step s : steps) {
            timeW = Math.max(timeW, Type.NUMBER.width(font(), MountsText.dur(s.sec())));
        }
        for (int i = 0; i < steps.size(); i++) {
            Step s = steps.get(i);
            boolean lit = i == 0;
            boolean hover = mx >= x && mx < x + cw && my >= cy && my < cy + rowH && scroll.shows(my);
            if (lit) {
                Kit.rowLit(g, x, cy, cw, rowH);
            } else if (hover) {
                g.fill(x, cy, x + cw, cy + rowH, Theme.CARD_HOVER);
            }
            int ty = cy + (rowH - Type.BODY.lineHeight) / 2;
            int ix = x + Theme.SP_3;
            Art.icon(g, lit ? "clock" : "circle-dot", ix, Type.BODY.onCaps(ty, 8), 8, lit ? Theme.GOLD_EDGE : Theme.INK_3);
            int px = ix + 8 + Theme.SP_3;
            if (s.item() != null) {
                MountsText.item(g, s.item(), px, Type.BODY.onCaps(ty, ICON), ICON);
            } else if (s.icon() != null) {
                Art.icon(g, s.icon(), px + (ICON - 8) / 2, Type.BODY.onCaps(ty, 8), 8, Theme.INK_2);
            }
            int nx = px + ICON + Theme.SP_3;
            int right = x + cw - Theme.SP_3;
            String t = s.sec() > 0 ? MountsText.dur(s.sec()) : "";
            Type.NUMBER.drawRight(g, font(), t, right, ty, Theme.PRICE);
            right -= timeW + Theme.SP_5;
            int noteW = s.note() == null || s.note().isEmpty() ? 0 : Math.min(Type.META.width(font(), s.note()), (right - nx) / 3);
            if (noteW > 0) {
                Type.META.draw(g, font(), Type.META.fit(font(), s.note(), noteW), right - noteW, ty, s.noteInk());
                right -= noteW + Theme.SP_4;
            }
            Type role = lit ? Type.LABEL : Type.BODY;
            String name = role.fit(font(), s.name(), right - nx);
            role.draw(g, font(), name, nx, ty, Theme.INK);
            if (s.level() != null) {
                int lx = nx + role.width(font(), name) + Theme.SP_3;
                if (lx + Type.META.width(font(), s.level()) <= right) {
                    Type.META.draw(g, font(), s.level(), lx, ty, Theme.INK_2);
                }
            }
            if (lit && scroll.shows(cy)) {
                tab.click(x, cy, cw, rowH, () -> open = !open);
            }
            cy += rowH + Theme.SP_2;
            if (lit && open) {
                cy += detail(g, nx, cy, x + cw - nx, s) + Theme.SP_3;
            }
        }
        if (fp != null && !fp.proven() && !fp.steps().isEmpty()) {
            Type.META.draw(g, font(), L.t("wynnlabs.mounts.best_found"), x + Theme.SP_3, cy, Theme.INK_3);
            cy += Type.META.lineHeight;
        }
        scroll.end(g, cy - top, mx, my);
    }

    /** The lit step opened: its sentence, then a food's gain on each stat in green. */
    private int detail(GuiGraphics g, int x, int y, int w, Step s) {
        int cy = Type.BODY.drawWrapped(g, font(), s.sentence(), x, y, w, 3, Theme.INK);
        if (s.gains() == null) {
            return cy - y;
        }
        cy += Theme.SP_2;
        int sx = x;
        for (MountStat st : MountStat.values()) {
            int gain = s.gains()[st.ordinal()];
            if (gain <= 0) {
                continue;
            }
            String text = st.display() + " +" + gain;
            int tw = Type.META.width(font(), text) + 2 * Theme.SP_2;
            if (sx + tw > x + w) {
                sx = x;
                cy += Ui.SEAL_H + Theme.SP_2;
            }
            Ui.seal(g, font(), text, sx, cy, Theme.SEAL_2);
            sx += tw + Theme.SP_2;
        }
        return cy + Ui.SEAL_H - y;
    }

    // ---- the shopping list ----

    /** The emeralds these cost at the lowest market price (only the ones with a price), 0 for none. */
    static double cost(Map<Material, Integer> materials) {
        double sum = 0;
        for (var e : materials.entrySet()) {
            double p = MountsModel.price(e.getKey());
            if (p > 0) {
                sum += p * e.getValue();
            }
        }
        return sum;
    }

    private void shopping(GuiGraphics g, int x, int y, int w, int h, MountsModel.Owned cur, FeedPlan fp, RoadMap.Plan plan, int mx, int my) {
        MountsModel.Options opt = MountsModel.options();
        Map<Material, Integer> need = new LinkedHashMap<>();
        Map<Material, Integer> banked = new LinkedHashMap<>();
        if (fp != null) {
            for (var e : fp.materials().entrySet()) {
                int bank = opt.useBank ? Math.min(e.getValue(), MountsModel.inBank(e.getKey())) : 0;
                if (bank > 0) {
                    banked.put(e.getKey(), bank);
                }
                if (e.getValue() - bank > 0) {
                    need.put(e.getKey(), e.getValue() - bank);
                }
            }
        }
        int buy = plan == null ? 0 : Math.max(0, plan.mountsToBuy() - tab.model.owned(tab.model.type()).size());
        double each = MountsModel.mountPrice(tab.model.type());
        double total = cost(need) + (each > 0 ? each * buy : 0);
        String t = total > 0 ? "~" + MountsText.em(total) : "";
        Type.LABEL.draw(g, font(), L.t("wynnlabs.mounts.shopping"), x, y, Theme.INK);
        Type.META.drawRight(g, font(), t, x + w, y, Theme.WARN);
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        int cy = y + Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
        int rowH = Math.max(ICON, Type.BODY.lineHeight) + Theme.SP_2;
        int end = y + h;
        if (buy > 0 && cy + rowH <= end) {
            MountsText.icon(g, tab.model.type(), x, cy, ICON);
            String name = tab.model.type().display() + " ×" + buy;
            int ty = cy + (ICON - Type.BODY.lineHeight) / 2;
            String price = each > 0 ? MountsText.em(each * buy) : "—";
            int pw = Type.NUMBER.width(font(), price);
            Type.NUMBER.drawRight(g, font(), price, x + w, ty, Theme.PRICE);
            String via = opt.buyTm ? "TM" : "";
            int vw = via.isEmpty() ? 0 : Type.META.width(font(), via) + Theme.SP_3;
            if (!via.isEmpty()) {
                Type.META.draw(g, font(), via, x + w - pw - vw, ty, Theme.INK_2);
            }
            Type.BODY.draw(g, font(), Type.BODY.fit(font(), name, w - ICON - Theme.SP_3 - pw - vw - Theme.SP_3), x + ICON + Theme.SP_3,
                    ty, Theme.INK);
            if (each > 0 && my >= cy && my < cy + ICON && mx >= x && mx < x + w) {
                tab.tip(x, cy, w, ICON, List.of(Component.literal(L.t("wynnlabs.mounts.mount_price_tip", MountsText.em(each)))
                        .withStyle(ChatFormatting.GRAY)));
            }
            cy += rowH + Theme.SP_2;
        }
        if (need.isEmpty() && buy == 0 && fp != null) {
            Type.MUTED.draw(g, font(), L.t("wynnlabs.mounts.shop_none"), x, cy, Theme.INK_3);
            cy += Type.BODY.lineHeight + Theme.SP_2;
        }
        for (var e : need.entrySet()) {
            if (cy + rowH > end - Type.META.lineHeight) {
                break;
            }
            Material mat = e.getKey();
            MountsText.item(g, mat.name(), x, cy, ICON);
            double p = MountsModel.price(mat);
            String price = p < 0 ? "—" : MountsText.em(p * e.getValue());
            int pw = Type.NUMBER.width(font(), price);
            int ty = cy + (ICON - Type.BODY.lineHeight) / 2;
            Type.NUMBER.drawRight(g, font(), price, x + w, ty, Theme.PRICE);
            String name = mat.name() + " ×" + e.getValue();
            Type.BODY.draw(g, font(), Type.BODY.fit(font(), name, w - ICON - Theme.SP_3 - pw - Theme.SP_3), x + ICON + Theme.SP_3, ty, Theme.INK);
            if (my >= cy && my < cy + ICON && mx >= x && mx < x + w) {
                tab.tip(x, cy, w, ICON, List.of(Component.literal(mat.name() + " · L" + mat.level()).withStyle(ChatFormatting.GOLD),
                        Component.literal(p < 0 ? L.t("wynnlabs.mounts.no_price") : L.t("wynnlabs.mounts.price_each", MountsText.em(p)))
                                .withStyle(ChatFormatting.GRAY)));
            }
            cy += rowH + Theme.SP_2;
        }
        if (!banked.isEmpty() && cy + Type.META.lineHeight <= end) {
            List<String> names = new ArrayList<>();
            banked.forEach((k, v) -> names.add(k.name() + " ×" + v));
            String line = L.t("wynnlabs.mounts.from_bank", String.join(", ", names));
            Type.META.draw(g, font(), Type.META.fit(font(), line, w), x, cy + Theme.SP_1, Theme.GOOD);
        }
    }

    // ---- input ----

    boolean barPress(double x, double y) {
        return scroll.press(x, y);
    }

    boolean barDrag(double y) {
        return scroll.drag(y);
    }

    void barRelease() {
        scroll.release();
    }

    void scrolled(double x, double y, double v) {
        scroll.wheel(x, y, v);
    }
}
