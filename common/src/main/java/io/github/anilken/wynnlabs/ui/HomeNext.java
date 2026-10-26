package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestGuides;
import io.github.anilken.wynnlabs.ui.HomeTab.Host;
import io.github.anilken.wynnlabs.ui.HomeTab.In;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The home's "Next" line on top: the one thing to do now, a slim row with a compass button.
 * It scores nothing itself: it takes the first of, in the Today list's order, a piece the
 * build plan wears from your bag, a better piece in your bank, the quest advisor's first
 * quest and the safest place to level; with none of them (the endgame) it is not drawn.
 */
final class HomeNext extends HomePart {

    /**
     * The row's content: its icon, the name and its colour, the line after it, the row's click
     * (a menu action, or null), the quest it tracks (null: none), the compass command (null: no
     * button) and the tooltip.
     */
    record Pick(String icon, String title, int colour, String sub, String action, QuestBook.Entry quest, String compass,
            List<Component> tip) {}

    private List<Vault.Find> bank = List.of();
    private long bankAt;

    /** The card's height: a chip-tall button with SP_1 around it (compact), a button with SP_2 (else). */
    int height(boolean compact) {
        return compact ? Ui.CHIP_H + 2 * Theme.SP_1 : Ui.BUTTON_H + 2 * Theme.SP_2;
    }

    /** The first thing to do, or null. */
    Pick pick(In in) {
        long now = System.currentTimeMillis();
        if (now - bankAt > 3000) {             // the bank's pieces are tried against the build: not every frame
            bankAt = now;
            try {
                bank = Vault.forMe();
            } catch (Throwable t) {
                bank = List.of();
            }
        }
        Roadmap.Gear bag = null;
        try {
            for (Roadmap.Gear g : Roadmap.bagRows().values()) {
                if (g.info() != null && !Double.isNaN(g.gain()) && g.gain() > 0 && (bag == null || g.gain() > bag.gain())) {
                    bag = g;
                }
            }
        } catch (Throwable ignored) {
            // no plan yet
        }
        return choose(bag, bank.isEmpty() ? null : bank.get(0), in.quest(), safest(in.places()));
    }

    /** The first that applies: the bag's piece, the bank's, the quest, the place to level. */
    static Pick choose(Roadmap.Gear bag, Vault.Find bank, QuestAdvisor.Suggestion quest, String place) {
        if (bag != null) {
            String gain = "+" + RoadmapScreen.pct(Math.round(bag.gain()));
            return new Pick("shirt", bag.info().name(), Theme.GOOD, gain + " · " + L.t("wynnlabs.next.bag"), null, null, null,
                    List.of(Component.literal(bag.info().name()).withStyle(ChatFormatting.GREEN),
                            L.c("wynnlabs.next.gear_tip", gain).withStyle(ChatFormatting.GRAY)));
        }
        if (bank != null) {
            String gain = "+" + RoadmapScreen.pct(Math.round(bank.gain()));
            return new Pick("building-bank", bank.item().name(), Theme.GOOD, gain + " · " + L.t("wynnlabs.next.bank"),
                    "#qgo:bank", null, null, List.of(Component.literal(bank.item().name()).withStyle(ChatFormatting.GREEN),
                            L.c("wynnlabs.next.gear_tip", gain).withStyle(ChatFormatting.GRAY),
                            L.c("wynnlabs.homev.click_open", L.t("wynnlabs.screen.tab_bank")).withStyle(ChatFormatting.DARK_GRAY)));
        }
        if (quest != null) {
            QuestBook.Entry q = quest.quest();
            String name = QuestGuides.title(q.name());
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(name).withStyle(ChatFormatting.AQUA).withStyle(UiFont::bold));
            for (String r : quest.reasons()) {
                tip.add(Component.literal("• " + L.cap(r)).withStyle(ChatFormatting.GRAY));
            }
            tip.add(L.c("wynnlabs.home.click_track").withStyle(ChatFormatting.GREEN));
            String at = q.x() == null || q.z() == null ? null
                    : "/compass at " + q.x() + " " + (q.y() == null ? 64 : q.y()) + " " + q.z();
            return new Pick("map-2", name, Theme.LINK, (quest.distance() >= 0 ? RoadmapScreen.dist(quest.distance()) + " · " : "")
                    + "Lv. " + q.level(), null, q, at, tip);
        }
        if (place != null) {
            String shown = RoadmapScreen.shortPlace(place);
            String name = shown.contains(" (") ? shown.substring(0, shown.indexOf(" (")) : shown;
            String lv = shown.contains(" (") ? shown.substring(shown.indexOf(" (") + 2, shown.length() - 1) : "";
            int risk = RoadmapScreen.placeRisk(place);
            String safe = L.t(risk == 0 ? "wynnlabs.home.risk_safe" : risk == 1 ? "wynnlabs.home.risk_careful" : "wynnlabs.home.risk_risky");
            String cmd = RoadmapScreen.compassFor(place);
            return new Pick("sword", name, Theme.INK, safe + (lv.isEmpty() ? "" : " · " + lv), cmd == null ? name : null, null, cmd,
                    List.of(Component.literal(shown).withStyle(ChatFormatting.WHITE), Component.literal(L.t(cmd != null
                            ? "wynnlabs.screen.click_compass" : "wynnlabs.screen.click_copy_name")).withStyle(ChatFormatting.DARK_GRAY)));
        }
        return null;
    }

    /** The safest of the places to level (the first of the safest), as the grind tile shows it; null for none. */
    static String safest(List<String> places) {
        String best = null;
        for (String p : places) {
            if (best == null || RoadmapScreen.placeRisk(p) < RoadmapScreen.placeRisk(best)) {
                best = p;
            }
        }
        return best;
    }

    /**
     * The row on a soft card: "Next" as a label, the icon, the name in its colour and its line in
     * small type, and the compass as a real button on the right.
     */
    void draw(GuiGraphics g, Pick p, Host host, int x, int y, int w, int h, boolean compact, int mx, int my) {
        Kit.softCard(g, x, y, w, h);
        int ty = Type.BODY.yIn(y, h);
        int right = x + w - Theme.SP_3;
        if (p.compass() != null) {
            // the compass button first: its area is added before the row's, so its click wins
            String word = L.t("wynnlabs.next.compass") + " ›";
            int bw;
            int by;
            int bh;
            if (compact) {
                bw = Ui.CHIP_H + 2;
                bh = Ui.CHIP_H;
                by = y + (h - bh) / 2;
                Ui.iconButton(g, "compass", right - bw, by, true, mx, my);
            } else {
                bw = Ui.iconButtonLabelWidth(font, word);
                bh = Ui.BUTTON_H;
                by = y + (h - bh) / 2;
                Ui.iconButtonLabel(g, font, "compass", word, right - bw, by, bw, mx, my);
            }
            host.area(right - bw, by, bw, bh, null, List.of(Component.literal(p.title()).withStyle(ChatFormatting.WHITE),
                    L.c("wynnlabs.screen.click_compass").withStyle(ChatFormatting.DARK_GRAY)), p.compass());
            right -= bw + Theme.SP_3;
        }
        String label = L.t("wynnlabs.next.label");
        int tx = x + Theme.SP_4;
        Type.LABEL.draw(g, font, label, tx, ty, Theme.INK_2);
        tx += Type.LABEL.width(font, label) + Theme.SP_3;
        Art.icon(g, p.icon(), tx, Type.BODY.onCaps(ty, 8), 8, p.colour());
        tx += 8 + Theme.SP_1;
        int room = right - tx;
        String title = Type.LABEL.fit(font, p.title(), Math.max(20, room * 3 / 5));
        Type.LABEL.draw(g, font, title, tx, ty, p.colour());
        int sx = tx + Type.LABEL.width(font, title) + Theme.SP_3;
        if (right - sx > 20) {
            Type.META.draw(g, font, Type.META.fit(font, p.sub(), right - sx), sx, ty + 1);
        }
        // the row's area stops before the compass button, so the two never overlap
        int rw = right - x;
        if (p.quest() != null) {
            host.quest(x, y, rw, h, p.tip(), p.quest());
        } else {
            host.area(x, y, rw, h, null, p.tip(), p.action());
        }
    }
}
