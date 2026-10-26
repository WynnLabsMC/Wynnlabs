package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The guide pane's two fact lines, what was the old tab's Rewards and Enemies tabs: one META line
 * each, the details (each reward's gain for your build, each enemy's health and weakness) in the
 * line's tooltip.
 */
final class QuestsGuideFacts {

    private QuestsGuideFacts() {}

    /** "Ödül: 34.000 XP · 128 zümrüt · Gernald's Amulet"; returns the y under it. */
    static int rewards(QuestsGuidePane pane, GuiGraphics g, int x, int y, int w, QuestWiki.Info info, QuestBook.Entry q, QuestsTab tab) {
        Font font = pane.paneFont();
        long xp = info.xp() > 0 ? info.xp() : q == null ? 0 : q.xp();
        List<String> parts = new ArrayList<>();
        List<Component> tip = new ArrayList<>();
        if (xp > 0) {
            parts.add(QuestAdvisor.fmt(xp) + " XP");
            tip.add(Component.literal(QuestAdvisor.fmt(xp) + " XP · " + tab.share(xp)).withStyle(ChatFormatting.GOLD));
        }
        if (info.emeralds() > 0) {
            parts.add(L.t("wynnlabs.quests.reward_emeralds", info.emeralds()));
            tip.add(L.c("wynnlabs.quests.reward_emeralds", info.emeralds()).withStyle(ChatFormatting.GREEN));
        }
        for (String r : info.rewards()) {
            parts.add(r);
            tip.add(Component.literal(r).withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(gain(r)).withStyle(ChatFormatting.GRAY)));
        }
        if (parts.isEmpty()) {
            return y;
        }
        String line = L.t("wynnlabs.quests.reward_line", String.join(" · ", parts));
        int lines = wrapped(g, font, line, x, y, w, Theme.INK_2);
        pane.tip(x, y, w, lines * Type.META.lineHeight, tip);
        return y + lines * Type.META.lineHeight + Theme.SP_1;
    }

    /** Each reward's use to you, kept a few seconds: the build is tried once, not every frame. */
    private static final java.util.Map<String, String> GAIN = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<String, Long> GAIN_AT = new java.util.concurrent.ConcurrentHashMap<>();

    private static String gain(String reward) {
        Long at = GAIN_AT.get(reward);
        if (at != null && System.currentTimeMillis() - at < 5000) {
            return GAIN.getOrDefault(reward, "");
        }
        String v;
        try {
            v = tryGain(reward);
        } catch (Throwable t) {
            v = "";                                 // no Wynntils item data (the preview): no gain line
        }
        GAIN.put(reward, v);
        GAIN_AT.put(reward, System.currentTimeMillis());
        return v;
    }

    /** A gear reward's use to you: "Lv 55'te", "→ build +%4", "(no gain)"; "" for other rewards. */
    private static String tryGain(String reward) {
        var gi = WynntilsItems.gearByName(reward.replaceAll("^\\d+\\s*", "").trim());
        Build.Plan plan = Build.plan();
        if (gi == null || plan == null || plan.result().objective() <= 0) {
            return "";
        }
        int lvNow = Wyn.level();
        if (gi.requirements().level() > lvNow) {
            return L.t("wynnlabs.quests.reward_at_level", gi.requirements().level());
        }
        String slot = Build.slotOf(gi.slot(), 0);
        double v = slot == null ? Double.NaN : Build.trySlot(plan.pieces(), plan.allocation(), slot, Damage.piece(gi), lvNow, plan.earned());
        if (Double.isNaN(v)) {
            return "";
        }
        double gp = (v / plan.result().objective() - 1) * 100;
        return gp > 0.5 ? L.t("wynnlabs.quests.reward_gain", Math.round(gp)) : L.t("wynnlabs.quests.reward_no_gain");
    }

    /** "Düşmanlar: Frost Wraith, Ice Golem" with health and weaknesses in the tooltip; returns the y under it. */
    static int enemies(QuestsGuidePane pane, GuiGraphics g, int x, int y, int w, QuestWiki.Info info) {
        if (info.mobs().isEmpty()) {
            return y;
        }
        Font font = pane.paneFont();
        List<String> mine = myElements();
        List<String> names = new ArrayList<>();
        List<Component> tip = new ArrayList<>();
        int lv = Wyn.level();
        for (QuestWiki.Mob m : info.mobs()) {
            names.add(m.name());
            List<String> weak = new ArrayList<>();
            boolean yours = false;
            for (String e : m.weak()) {
                weak.add(element(e));
                yours |= mine.contains(e);
            }
            tip.add(Component.literal(m.name() + " · Lv " + m.level()).withStyle(m.level() > lv + 3 ? ChatFormatting.RED : ChatFormatting.WHITE));
            tip.add(Component.literal("  " + L.t("wynnlabs.quests.mob_health", m.health())
                    + (weak.isEmpty() ? "" : L.t("wynnlabs.quests.mob_weak", String.join(", ", weak)))).withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(yours ? L.t("wynnlabs.quests.mob_your_build") : "").withStyle(ChatFormatting.GREEN)));
        }
        String line = L.t("wynnlabs.quests.enemies_line", String.join(", ", names));
        int lines = wrapped(g, font, line, x, y, w, Theme.INK_2);
        pane.tip(x, y, w, lines * Type.META.lineHeight, tip);
        return y + lines * Type.META.lineHeight + Theme.SP_1;
    }

    /**
     * A META line wrapped inside {@code w}, two lines at most, the second ending in "…" when more
     * is left (the tooltip has it all); returns the lines drawn.
     */
    static int wrapped(GuiGraphics g, Font font, String text, int x, int y, int w, int ink) {
        List<net.minecraft.util.FormattedCharSequence> lines = QuestsCache.kept("wrap|meta|" + w + "|" + text, 30_000, () -> Type.META.wrap(font, text, w));
        if (lines.size() <= 2) {
            Type.META.drawWrapped(g, font, text, x, y, w, 2, ink);
            return Math.max(1, lines.size());
        }
        g.drawString(font, Kit.ink(lines.get(0)), x, y, ink, false);
        StringBuilder first = new StringBuilder();
        lines.get(0).accept((i, style, cp) -> {
            first.appendCodePoint(cp);
            return true;
        });
        String rest = text.substring(Math.min(text.length(), first.length())).stripLeading();
        Type.META.draw(g, font, Type.META.fit(font, rest, w), x, y + Type.META.lineHeight, ink);
        return 2;
    }

    /** The elements your build hits with (from the skills it leans on). */
    private static List<String> myElements() {
        List<String> out = new ArrayList<>();
        for (String sk : Rules.list("model", "skillPriority")) {
            switch (sk) {
                case "STRENGTH" -> out.add("earth");
                case "DEXTERITY" -> out.add("thunder");
                case "INTELLIGENCE" -> out.add("water");
                case "DEFENCE" -> out.add("fire");
                case "AGILITY" -> out.add("air");
                default -> {
                }
            }
        }
        return out;
    }

    private static String element(String e) {
        return switch (e) {
            case "earth" -> L.t("wynnlabs.quests.element_earth");
            case "water" -> L.t("wynnlabs.quests.element_water");
            case "fire" -> L.t("wynnlabs.quests.element_fire");
            case "air" -> L.t("wynnlabs.quests.element_air");
            case "thunder" -> L.t("wynnlabs.quests.element_thunder");
            default -> e;
        };
    }
}
