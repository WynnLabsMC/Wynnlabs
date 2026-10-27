package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.Advice;
import io.github.anilken.wynnlabs.advice.AutoAdvice;
import io.github.anilken.wynnlabs.advice.TooltipListener;
import io.github.anilken.wynnlabs.advice.Verdict;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A bank slot's tooltip: the item's own (drawn by Wynntils as in the bank), then the mod's lines
 * handed over through {@link Vault#setTipExtra}: where it is, what its corner mark means, the
 * rules' verdict and why, a box's outcomes, quests, professions and worth. Built only for the
 * hovered slot.
 */
final class BankTips {

    private BankTips() {}

    static void addSlotTip(MenuTab tab, int x, int y, int w, int h, BankIndex.Item it, int mx, int my) {
        if (h <= 0) {
            return;
        }
        ItemStack stack = it.stack();
        tab.menu.hits.add(new MenuHit(x, y, w, h, stack, null, null));
        if (!(mx >= x && mx < x + w && my >= y && my < y + h)) {
            return;
        }
        Vault.Stored st = it.st();
        Vault.Group gr = it.group();
        List<Component> tip = new ArrayList<>();
        tip.add(Component.empty());
        // where first: every place it is kept
        tip.add(Component.literal("▣ " + (gr != null && gr.total() > 1 ? "×" + gr.total() + " · " : "")
                + (gr != null ? Vault.where(gr) : Vault.label(st.page()))).withStyle(ChatFormatting.DARK_AQUA));
        if (gr != null && !gr.reachable()) {
            tip.add(L.c("wynnlabs.bank.tip_other_char").withStyle(ChatFormatting.DARK_GRAY));
        }
        String mark = BankIndex.markKey(it.mark());
        if (mark != null) {
            ChatFormatting c = it.mark() == Theme.MARK_SELL ? ChatFormatting.RED : it.mark() == Theme.MARK_QUEST ? ChatFormatting.AQUA
                    : it.mark() == Theme.MARK_IDENTIFY ? ChatFormatting.GOLD : ChatFormatting.GREEN;
            tip.add(Component.literal("◥ " + L.t(mark)).withStyle(c));
        }
        if (st.verdict() != null) {
            boolean sell = it.mark() == Theme.MARK_SELL;
            String where = sell && gr != null ? Vault.place(gr) : null;
            tip.add(Component.literal(TooltipListener.marker()).withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(Verdict.of(st.verdict()).label() + (where == null ? "" : " → " + where))
                            .withStyle(sell ? ChatFormatting.RED : ChatFormatting.GREEN).withStyle(UiFont::bold))
                    .append(Component.literal(st.by() == null ? "" : L.t("wynnlabs.bank.tip_checked_by", AutoAdvice.nice(st.by())))
                            .withStyle(ChatFormatting.DARK_GRAY)));
            // what the word means, then why this item got it (the note)
            for (String l : Advice.wrap(Verdict.of(st.verdict()).meaning(), 40)) {
                tip.add(Component.literal(l).withStyle(ChatFormatting.DARK_GRAY));
            }
            String note = Vault.noteNow(st);
            if (note != null) {
                for (String l : Advice.wrap(note, 40)) {
                    tip.add(Component.literal(l).withStyle(ChatFormatting.GRAY));
                }
            }
        }
        // a box Wynntils could not read: its levels and outcomes from what was saved
        if (Vault.boxOf(st) != null) {
            boxLines(Vault.boxOf(st), tip, !WynntilsItems.known(stack));
        }
        if (gr != null) {
            for (String qn : Vault.questsFor(gr)) {
                tip.add(L.c("wynnlabs.bank.tip_quest", qn).withStyle(ChatFormatting.AQUA));
            }
        }
        String prof = Vault.professions(st);
        if (prof != null) {
            tip.add(L.c("wynnlabs.bank.tip_prof", prof).withStyle(ChatFormatting.GREEN));
        }
        Double v = gr == null ? null : Vault.value(gr);
        if (v != null && gr.total() > 1) {
            tip.add(L.c("wynnlabs.bank.tip_all_worth", Prices.fmt(v)).withStyle(ChatFormatting.GOLD));
        }
        if (it.unknown() && Vault.boxOf(st) == null) {
            tip.add(L.c("wynnlabs.bank.unpriced_one").withStyle(ChatFormatting.GOLD));
        }
        tip.add(L.c("wynnlabs.bank.click_open").withStyle(ChatFormatting.DARK_GRAY));
        Vault.setTipExtra(stack, tip);
    }

    /**
     * A box: its levels and tier, which of your characters it suits, and the best that can come
     * out for them (by the rules' score) with market prices, so you know whether to identify it.
     */
    static void boxLines(ItemView.Box box, List<Component> tip, boolean outcomesToo) {
        if (box == null) {
            return;
        }
        int low = box.low();
        int high = box.high();
        tip.add(L.c("wynnlabs.bank.box_head", MenuPaint.nice(box.slot().name()), MenuPaint.nice(box.tier().name()), low, high)
                .withStyle(box.tier().colour(), ChatFormatting.BOLD));
        // your characters this box is for: right class, levels from 15 below to 5 above them
        java.util.Set<String> mine = new java.util.LinkedHashSet<>();
        List<String> forWho = new ArrayList<>();
        try {
            for (var e : Profile.list().getAsJsonArray("characters")) {
                var o = e.getAsJsonObject();
                String cls = o.get("class").getAsString();
                int lv = o.get("level").getAsInt();
                boolean classFits = false;
                for (var info : WynntilsItems.possibleGears(box)) {
                    var c = info.requirements().klass().orElse(info.slot().klass());
                    if (c == null || c.name().equals("NONE") || c.name().equals(cls)) {
                        classFits = true;
                        break;
                    }
                }
                if (classFits && high >= lv - 15 && low <= lv + 5) {
                    mine.add(cls);
                    forWho.add(MenuPaint.nice(cls) + " Lv. " + lv);
                }
            }
        } catch (Throwable ignored) {
        }
        tip.add(forWho.isEmpty()
                ? L.c("wynnlabs.bank.box_fits_none").withStyle(ChatFormatting.RED)
                : L.c("wynnlabs.bank.box_fits", String.join(", ", forWho)).withStyle(ChatFormatting.GREEN));
        // every outcome by level, as Wynntils shows it in the bank: identify cost, names, best price
        List<ItemData> outcomes = new ArrayList<>(WynntilsItems.possibleGears(box));
        if (!outcomesToo || outcomes.isEmpty()) {
            tip.add(Component.empty());
            return;
        }
        java.util.TreeMap<Integer, List<ItemData>> byLevel = new java.util.TreeMap<>();
        outcomes.forEach(i -> byLevel.computeIfAbsent(i.requirements().level(), k -> new ArrayList<>()).add(i));
        tip.add(L.c("wynnlabs.bank.box_outcomes", outcomes.size()).withStyle(ChatFormatting.GREEN));
        int lines = 0;
        for (var e : byLevel.entrySet()) {
            if (lines++ == 10) {
                tip.add(L.cn("wynnlabs.bank.box_more_levels", byLevel.size() - 10).withStyle(ChatFormatting.DARK_GRAY));
                break;
            }
            int cost = box.tier().identificationCost(e.getKey());
            Double best = null;
            List<String> names = new ArrayList<>();
            for (ItemData info : e.getValue()) {
                var c = info.requirements().klass().orElse(info.slot().klass());
                boolean forMe = mine.isEmpty() || c == null || c.name().equals("NONE") || mine.contains(c.name());
                names.add((forMe ? "" : "·") + info.name());
                Double v = BankIndex.unitPrice(info.name());
                if (v != null && (best == null || v > best)) {
                    best = v;
                }
            }
            String all = String.join(", ", names);
            tip.add(Component.literal("  – Lv. " + e.getKey() + " ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal("[" + cost + " E]").withStyle(ChatFormatting.DARK_GREEN))
                    .append(Component.literal(": " + (all.length() > 60 ? all.substring(0, 59) + "…" : all)).withStyle(box.tier().colour()))
                    .append(Component.literal(best == null ? "" : " (" + Prices.fmt(best) + ")").withStyle(ChatFormatting.GOLD)));
        }
        // the three dearest outcomes: what identifying could be worth
        List<String> dear = outcomes.stream()
                .filter(i -> BankIndex.unitPrice(i.name()) != null)
                .sorted((a, b) -> Double.compare(BankIndex.unitPrice(b.name()), BankIndex.unitPrice(a.name())))
                .limit(3).map(i -> i.name() + " ~" + Prices.fmt(BankIndex.unitPrice(i.name()))).toList();
        if (!dear.isEmpty()) {
            tip.add(L.c("wynnlabs.bank.box_market", String.join(", ", dear)).withStyle(ChatFormatting.GOLD));
        }
        tip.add(Component.empty());
    }
}
