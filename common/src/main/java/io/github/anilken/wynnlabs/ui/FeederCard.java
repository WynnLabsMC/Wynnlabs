package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.mounts.FeedStep;
import io.github.anilken.wynnlabs.mounts.FeederRead;
import io.github.anilken.wynnlabs.mounts.Material;
import io.github.anilken.wynnlabs.mounts.Mount;
import io.github.anilken.wynnlabs.mounts.MountBook;
import io.github.anilken.wynnlabs.mounts.MountStat;
import io.github.anilken.wynnlabs.mounts.MountStats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * A Mount Feeder row's card beside the menu (FeederOverlay): the mount, its potential and average
 * limit, a ring for the time left on the food being eaten, its eight stats (level / limit / max,
 * the queue's gain in green), the next food and where it is, and the plan's one button.
 */
final class FeederCard {

    static final int MAX_W = 176;
    /** The narrowest the card reads at (the stat names, a short bar, the numbers). */
    static final int MIN_W = 120;
    private static final int BAR_H = 3;
    private static final int STAT_CAP = 115;
    private static final int NAME_COL = 46;

    private FeederCard() {}

    /** Draws it; returns {card box, button box (null for none), pin box}. */
    static int[][] draw(GuiGraphics g, Font font, AbstractContainerScreen<?> screen, int left, int top, int imageW,
                        MountBook.Live row, MountsModel.Owned o, MountsModel model, boolean pinned, int mx, int my) {
        Mount m = o.mount();
        MountStats now = m.stats();
        MountStats after = m.queue().applyTo(now);
        int statsH = MountStat.COUNT * (Type.MICRO.lineHeight + Theme.SP_1);
        // a ride first takes one more line: the food it opens, dimmer
        FeedStep first = row.partnerKey() != null ? null : FeederOverlay.nextStep(o);
        int thenH = first != null && !first.isFeed() && FeederOverlay.nextFood(o) != null ? Type.MICRO.lineHeight : 0;
        int h = thenH + Theme.SP_3 + Type.META.lineHeight + Type.MICRO.lineHeight + Theme.SP_3 + statsH + Theme.SP_2 + 1 + Theme.SP_2
                + Type.META.lineHeight + Type.MICRO.lineHeight + Theme.SP_3 + Ui.SMALL_H + Theme.SP_3;
        var slots = screen.getMenu().slots;
        int rowY = top + slots.get(row.row().mountSlot()).y + 8;
        // beside the menu, never over it: the right side first (the rows' tags are on the left),
        // as wide as the side allows; the left side only when the right one is too narrow
        int roomRight = screen.width - Theme.SP_2 - (left + imageW + Theme.SP_3);
        int roomLeft = left - Theme.SP_3 - Theme.SP_2 - 34;           // the tags take ~34 px left of the menu
        boolean right = roomRight >= MIN_W || roomRight >= roomLeft;
        int W = Math.max(Math.min(MAX_W, right ? roomRight : roomLeft), Math.min(MIN_W, Math.max(roomRight, roomLeft)));
        int x0 = right ? left + imageW + Theme.SP_3 : left - Theme.SP_3 - 34 - W;
        x0 = Math.max(Theme.SP_2, x0);
        int y0 = Math.max(Theme.SP_2, Math.min(screen.height - h - Theme.SP_2, rowY - 20));
        // a leader from the row's side of the menu to the card
        if (right) {
            g.fill(left + imageW, rowY, x0, rowY + 1, 0xCCE0AE3E);
        } else {
            g.fill(x0 + W, rowY, left - 34, rowY + 1, 0xCCE0AE3E);
        }
        Kit.softCard(g, x0, y0, W, h);
        int x = x0 + Theme.SP_4;
        int w = W - 2 * Theme.SP_4;
        int y = y0 + Theme.SP_3;
        // the title: the row, the mount; a ring with the time left on the right
        long leftSec = Math.max(-1, row.remainingSec());
        int ringR = 6;
        int pinS = 10;
        int textRight = x + w - (leftSec > 0 ? 2 * ringR + Theme.SP_2 : 0) - pinS - Theme.SP_1;
        String title = L.t("wynnlabs.mounts.card_title", row.row().number(), m.name());
        Type meta = Type.META;
        meta.draw(g, font, meta.fit(font, title, textRight - x), x, y, Theme.INK);
        // the pin: keeps the card open (gold while it does); a click on it never reaches the game
        int[] pin = {textRight + Theme.SP_1, y, pinS, pinS};
        boolean pinHover = mx >= pin[0] && mx < pin[0] + pinS && my >= pin[1] && my < pin[1] + pinS;
        if (pinned || pinHover) {
            Art.box(g, pin[0], pin[1], pinS, pinS, 3, pinned ? 0xFFF4E6BA : Theme.CARD_HOVER, pinned ? Theme.GOLD_EDGE : 0);
        }
        Art.icon(g, "bookmark", pin[0] + 1, pin[1] + 1, 8, pinned ? Theme.GOLD_EDGE : Theme.INK_3);
        String sub = L.t("wynnlabs.mounts.card_meta", o.read().potential(), MountsText.one(m.averageLimit()))
                + (o.read().state() == null ? "" : " · " + o.read().state());
        Type.MICRO.draw(g, font, Type.MICRO.fit(font, sub, textRight - x), x, y + Type.META.lineHeight, Theme.INK_2);
        if (leftSec > 0 && !m.queue().pending().isEmpty()) {
            long whole = FeederRead.timers(now, m.queue().pending())[0];
            double frac = whole <= 0 ? 0 : Math.min(1, leftSec / (double) whole);
            ring(g, x + w - ringR, y + ringR + 1, ringR, frac);
            String t = MountsText.durShort(leftSec);
            Type.MICRO.draw(g, font, t, x + w - 2 * ringR - Theme.SP_1 - Type.MICRO.width(font, t), y + Type.META.lineHeight,
                    Theme.WARN);
        }
        y += Type.META.lineHeight + Type.MICRO.lineHeight + Theme.SP_3;
        // the eight stats
        int numW = Type.MICRO.width(font, "115/115/115");
        int bx = x + NAME_COL;
        int bw = w - NAME_COL - numW - Theme.SP_2;
        for (MountStat s : MountStat.values()) {
            int i = s.ordinal();
            Type.MICRO.draw(g, font, Type.MICRO.fit(font, s.display(), NAME_COL - Theme.SP_1), x, y, Theme.INK);
            int by = Type.MICRO.onCaps(y, BAR_H);
            int track = Math.max(1, bw * now.max(i) / STAT_CAP);
            g.fill(bx, by, bx + track, by + BAR_H, 0x229A855C);
            int lim = bw * now.limit(i) / STAT_CAP;
            int gain = bw * after.limit(i) / STAT_CAP;
            g.fill(bx + lim, by, bx + gain, by + BAR_H, 0xFF6BAE5C);
            g.fill(bx, by, bx + lim, by + BAR_H, 0xFFB89A64);
            g.fill(bx, by, bx + bw * now.level(i) / STAT_CAP, by + BAR_H, Theme.INK_2);
            boolean up = after.limit(i) > now.limit(i);
            String nums = now.level(i) + "/" + after.limit(i) + "/" + now.max(i);
            Type.MICRO.drawRight(g, font, nums, x + w, y, up ? Theme.GOOD : Theme.PRICE);
            y += Type.MICRO.lineHeight + Theme.SP_1;
        }
        y += Theme.SP_2 - Theme.SP_1;
        Kit.hair(g, x, y, w, 1, Theme.LINE);
        y += 1 + Theme.SP_2;
        // the next food and where it is
        MountsModel.Owned partner = FeederOverlay.owned(row.partnerKey());
        Material next = row.partnerKey() != null ? null : FeederOverlay.nextFood(o);
        FeedStep step = first;
        int tx = x + 12 + Theme.SP_2;
        if (step != null && !step.isFeed() && step.stat() != null) {
            // a ride first: the stat's level up to the step's, then (dimmer) the food it opens
            String stat = step.stat().display();
            int lvl = now.level(step.stat().ordinal());
            Art.icon(g, "trending-up", x + 2, Type.META.onCaps(y, 8), 8, Theme.INK_2);
            long sec = Math.max(0, step.durationSec() - step.coveredSec());
            String time = sec > 0 ? "~" + MountsText.durShort(sec) : "";
            int timeW = time.isEmpty() ? 0 : Type.MICRO.width(font, time) + Theme.SP_2;
            Type.META.draw(g, font, Type.META.fit(font, L.t("wynnlabs.mounts.next_ride", stat, step.toLevel()), x + w - tx - timeW), tx, y,
                    Theme.INK);
            if (!time.isEmpty()) {
                Type.MICRO.drawRight(g, font, time, x + w, Type.META.yIn(y, Type.META.lineHeight), Theme.WARN);
            }
            int ly = y + Type.META.lineHeight;
            String prog = L.t("wynnlabs.mounts.ride_progress", stat, lvl, step.toLevel());
            int barW = 40;
            Type.MICRO.draw(g, font, Type.MICRO.fit(font, prog, x + w - tx - barW - Theme.SP_3), tx, ly, Theme.INK_2);
            int by = Type.MICRO.onCaps(ly, BAR_H);
            g.fill(x + w - barW, by, x + w, by + BAR_H, 0x229A855C);
            g.fill(x + w - barW, by, x + w - barW + barW * Math.min(lvl, step.toLevel()) / Math.max(1, step.toLevel()), by + BAR_H,
                    Theme.INK_2);
            if (next != null) {
                Type.MICRO.draw(g, font, Type.MICRO.fit(font, L.t("wynnlabs.mounts.then_food", next.name(), next.level()), x + w - tx), tx,
                        ly + Type.MICRO.lineHeight, Theme.INK_3);
            }
        } else if (row.partnerKey() != null) {
            // two mounts in this row: they breed; no food is planned meanwhile
            Art.icon(g, "heart", x + 2, y + 2, 8, 0xFFD9534F);
            String with = partner == null ? L.t("wynnlabs.mounts.breeding") : L.t("wynnlabs.mounts.breeding_with", partner.mount().name());
            Type.META.draw(g, font, Type.META.fit(font, with, x + w - tx), tx, y, Theme.INK);
            Type.MICRO.draw(g, font, L.t("wynnlabs.mounts.breeding_sub"), tx, y + Type.META.lineHeight, Theme.INK_2);
        } else if (next != null) {
            MountsText.item(g, next.name(), x, y + 1, 12);
            Type.META.draw(g, font, Type.META.fit(font, L.t("wynnlabs.mounts.next_food", next.name(), next.level()), x + w - tx), tx, y,
                    Theme.INK);
            String place;
            int ink = Theme.GOOD;
            // where this row's share of the stock comes from (the rows before it take theirs first)
            MountsShop.Source src = FeederOverlay.source(row.row().number(), next);
            if (src == MountsShop.Source.BAG) {
                place = L.t("wynnlabs.mounts.place_bag");
            } else if (src == MountsShop.Source.BANK) {
                place = L.t("wynnlabs.mounts.place_bank");
            } else {
                double p = MountsModel.price(next);
                place = p < 0 ? L.t("wynnlabs.mounts.place_buy") : L.t("wynnlabs.mounts.place_buy_at", MountsText.em(p));
                ink = Theme.PRICE;
            }
            Type.MICRO.draw(g, font, place, tx, y + Type.META.lineHeight, ink);
        } else {
            Type.META.draw(g, font, L.t("wynnlabs.mounts.next_none"), x, y, Theme.INK_3);
        }
        y += Type.META.lineHeight + Type.MICRO.lineHeight + thenH + Theme.SP_3;
        // the plan: where it stands, and its button (never started by itself)
        MountsModel.FeedView v = model.feed(o);
        String state = switch (v.state()) {
            case RUNNING -> L.t("wynnlabs.mounts.card_running");
            case CACHED -> L.t("wynnlabs.mounts.card_done", MenuPaint.ago(v.savedAt()));
            case STALE -> L.t("wynnlabs.mounts.card_stale");
            default -> L.t("wynnlabs.mounts.card_estimate");
        };
        int[] button = null;
        if (v.state() != MountsModel.FeedState.RUNNING) {
            String label = L.t(v.state() == MountsModel.FeedState.NONE ? "wynnlabs.mounts.calc_start" : "wynnlabs.mounts.calc_again");
            int btw = 2 * Theme.SP_2 + 8 + Theme.SP_1 + Type.META.width(font, label);
            int btx = x + w - btw;
            boolean hover = mx >= btx && mx < btx + btw && my >= y && my < y + Ui.SMALL_H;
            Kit.pill(g, btx, y, btw, Ui.SMALL_H, true, hover);
            Art.icon(g, v.state() == MountsModel.FeedState.NONE ? "player-play" : "refresh", btx + Theme.SP_2, y + Ui.iconIn(Ui.SMALL_H, 8),
                    8, Theme.INK);
            Type.META.draw(g, font, label, btx + Theme.SP_2 + 8 + Theme.SP_1, Type.META.yIn(y, Ui.SMALL_H), Theme.INK);
            button = new int[] {btx, y, btw, Ui.SMALL_H};
            Type.MICRO.draw(g, font, Type.MICRO.fit(font, state, btx - x - Theme.SP_2), x, Type.MICRO.yIn(y, Ui.SMALL_H),
                    v.state() == MountsModel.FeedState.STALE ? Theme.WARN : Theme.INK_2);
        } else {
            Type.MICRO.draw(g, font, state, x, Type.MICRO.yIn(y, Ui.SMALL_H), Theme.INK_2);
        }
        return new int[][] {{x0, y0, W, h}, button, pin};
    }

    /** A thin ring, the done part gold: one screen pixel thick at any scale. */
    private static void ring(GuiGraphics g, int cx, int cy, int r, double frac) {
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(1 / scale, 1 / scale);
        float rr = r * scale;
        int steps = Math.max(24, (int) (rr * 6));
        for (int k = 0; k < steps; k++) {
            double a = -Math.PI / 2 + 2 * Math.PI * k / steps;
            int px = (int) Math.round(Math.cos(a) * rr);
            int py = (int) Math.round(Math.sin(a) * rr);
            boolean done = k < frac * steps;
            g.fill(px, py, px + 1, py + 1, done ? 0xFFD9A93A : 0x809A855C);
        }
        g.pose().popMatrix();
    }
}
