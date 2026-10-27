package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.Advice;
import io.github.anilken.wynnlabs.advice.Cautions;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.market.ShoppingSources;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** A Home slot's upgrade as its hover tells it, and the gain its row shows. */
final class HomeGearTip {

    private HomeGearTip() {}

    /**
     * The gain a row shows: what the selected milestone's build gains over what you wear now in
     * that slot (Roadmap.Step.vsWorn), or the step over the previous milestone's pick when what
     * you wear there is far below it (Roadmap.shownGain).
     */
    static double rowGain(RoadmapScreen menu, Roadmap.Gear u) {
        return Roadmap.shownGain(menu.steps, menu.selected, u);
    }

    /** The full story of an upgrade, for its hover. */
    static List<Component> gearTip(RoadmapScreen menu, Roadmap.Gear u) {
        List<Component> tip = new ArrayList<>();
        ChatFormatting tc = u.info().tier().colour();
        tip.add(Component.literal(u.info().name()).withStyle(tc).withStyle(UiFont::bold));
        // a powder row is a piece you have: its source line names the powders, and there is no other source
        boolean powder = Roadmap.isPowder(u);
        tip.add(powder ? Component.literal(u.source()).withStyle(ChatFormatting.AQUA)
                : L.c("wynnlabs.home.tip_from", u.source()).withStyle(ChatFormatting.DARK_AQUA));
        String also = Roadmap.fromBag(u) || powder ? null : ShoppingSources.alsoFrom(u.info());   // the Trade Market first, the world source under it
        if (also != null && !also.equalsIgnoreCase(u.source())) {
            tip.add(Component.literal("   " + L.t("wynnlabs.homev.source_also", L.cap(also))).withStyle(ChatFormatting.DARK_GRAY));
        }
        Object[] pw = powder ? null : Damage.powderHint(u.info());
        if (pw != null) {
            String[] roman = {"I", "II", "III", "IV", "V", "VI", "VII"};
            String el = switch ((String) pw[0]) {
                case "thunder" -> L.t("wynnlabs.damage.el_thunder");
                case "water" -> L.t("wynnlabs.damage.el_water");
                case "fire" -> L.t("wynnlabs.damage.el_fire");
                case "air" -> L.t("wynnlabs.damage.el_air");
                default -> L.t("wynnlabs.damage.el_earth");
            };
            tip.add(L.c("wynnlabs.home.tip_powder", pw[1], el,
                    roman[Math.max(0, Math.min(6, (int) pw[2] - 1))]).withStyle(ChatFormatting.AQUA));
        }
        if (u.price() != null) {
            tip.add(L.c("wynnlabs.home.tip_past_price", u.price()).withStyle(ChatFormatting.GOLD));
            Prices.Price pp = Prices.of(u.info().name());
            String doubt = pp == null ? null : pp.doubt();
            tip.add(Component.literal(doubt != null ? L.t("wynnlabs.home.tip_doubt", doubt)
                    : L.t("wynnlabs.home.tip_seen")).withStyle(doubt != null ? ChatFormatting.RED : ChatFormatting.DARK_GRAY));
        }
        double vsWorn = rowGain(menu, u);
        boolean step = !Double.isNaN(vsWorn) && Roadmap.showsStep(menu.steps, menu.selected, u);
        tip.add(Double.isNaN(vsWorn)
                ? L.c(Roadmap.isTarget(u) ? "wynnlabs.home.tip_target" : "wynnlabs.home.tip_new").withStyle(ChatFormatting.AQUA)
                : step ? (vsWorn < 0 ? L.c("wynnlabs.home.tip_step_loss", Math.round(-vsWorn * 10) / 10.0).withStyle(ChatFormatting.RED)
                        : L.c("wynnlabs.home.tip_step", Math.round(vsWorn * 10) / 10.0).withStyle(ChatFormatting.GREEN))
                : vsWorn < 0 ? L.c("wynnlabs.home.tip_loss", Math.round(-vsWorn * 10) / 10.0).withStyle(ChatFormatting.RED)
                : L.c("wynnlabs.home.tip_gain", Math.round(vsWorn * 10) / 10.0).withStyle(ChatFormatting.GREEN));
        if (!step && !Double.isNaN(u.gain()) && Math.abs(vsWorn - u.gain()) >= 0.5) {
            // at a later milestone the pick also builds on the picks before it
            tip.add(L.c("wynnlabs.home.tip_step", Math.round(u.gain() * 10) / 10.0).withStyle(ChatFormatting.DARK_GRAY));
        }
        // why this pick: what changes against what you wear, biggest first (else the step's own reasons)
        List<String> why = menu.steps.isEmpty() ? null
                : menu.steps.get(Math.max(0, Math.min(menu.selected, menu.steps.size() - 1))).whyVsWorn().get(u.slot());
        if (why == null || why.isEmpty()) {
            why = u.detail();
        }
        if (why != null && !why.isEmpty()) {
            tip.add(L.c("wynnlabs.home.tip_why").withStyle(ChatFormatting.GOLD).withStyle(UiFont::bold));
            for (String d : why) {
                tip.add(Component.literal("  • " + d).withStyle(ChatFormatting.AQUA));
            }
        }
        // what it asks for: its level and skill points
        List<String> needs = new ArrayList<>();
        needs.add("Lv " + u.info().requirements().level());
        for (var r : u.info().requirements().skills()) {
            if (r.points() > 0) {
                needs.add(r.points() + " " + r.skill().name().substring(0, 3));
            }
        }
        tip.add(L.c("wynnlabs.home.tip_needs", String.join(" · ", needs)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.literal(Shopping.tradable(u.info()) ? L.t("wynnlabs.home.tip_tradable") : L.t("wynnlabs.home.tip_untradable"))
                .withStyle(ChatFormatting.GRAY));
        Integer seenAt = Rules.communityLevel(u.info().name());
        if (seenAt != null) {
            tip.add(L.c("wynnlabs.home.tip_community", seenAt)
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        String caution = Cautions.line(u.info());
        if (caution != null) {
            for (String l : Advice.wrap(caution, 46)) {
                tip.add(Component.literal(l).withStyle(ChatFormatting.RED));
            }
        }
        boolean panel = u.options() != null && !u.options().isEmpty();
        tip.add(Component.literal(panel ? L.t("wynnlabs.home.tip_click_compare") : L.t("wynnlabs.screen.click_copy_name")).withStyle(ChatFormatting.DARK_GRAY));
        return tip;
    }
}
