package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.ItemJson;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Shopping;
import io.github.anilken.wynnlabs.model.ItemData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Home with the Plan pill selected: each slot the plan's piece, what you wear of it, what is left to buy. */
final class HomePlanView {

    private final RoadmapScreen menu;

    HomePlanView(RoadmapScreen menu) {
        this.menu = menu;
    }

    /** The plan's pieces by name, looked up once per plan. */
    private final Map<String, ItemData> planInfo = new HashMap<>();
    private String planInfoFor = "";

    /**
     * The Plan pill's view: each slot the plan's piece, a tick where you wear it already (by
     * name), purple for the items you chose; the line under the stand counts what you wear.
     */
    HomeTab.In view(Plans.Build plan, List<HomePage.UpRow> rows, double now, double after, int chosen, int saved, boolean searching,
            List<String> places, HomeTab.Road road) {
        if (!planInfoFor.equals(plan.id() + plan.updated())) {
            planInfo.clear();
            java.util.Set<String> want = new java.util.HashSet<>(plan.items().values());
            GameState.current().allGear().filter(i -> want.contains(i.name())).forEach(i -> planInfo.putIfAbsent(i.name(), i));
            planInfoFor = plan.id() + plan.updated();
        }
        Map<String, String> wornNames = new LinkedHashMap<>();
        for (int i = 0; i < rows.size() && i < Plans.SLOTS.length; i++) {
            ItemStack w = rows.get(i).worn();
            if (!w.isEmpty()) {
                wornNames.put(Plans.SLOTS[i], ItemJson.clean(w.getHoverName().getString()).trim());
            }
        }
        // the plan's rings in the hands that wear them, so a ring worn in the other hand shows there
        Map<String, String> items = new LinkedHashMap<>(plan.items());
        String r1 = items.get("RING1");
        String r2 = items.get("RING2");
        if (r1 != null && r2 != null && !r1.equals(wornNames.get("RING1"))
                && (r2.equals(wornNames.get("RING1")) || r1.equals(wornNames.get("RING2")))) {
            items.put("RING1", r2);
            items.put("RING2", r1);
        }
        boolean[] on = Plans.wornSlots(items, wornNames);
        List<HomeTab.SlotView> slots = new ArrayList<>();
        int worn = 0;
        double left = 0;
        for (int i = 0; i < rows.size(); i++) {
            HomePage.UpRow r = rows.get(i);
            String name = i < Plans.SLOTS.length ? items.get(Plans.SLOTS[i]) : null;
            ItemData info = name == null ? null : planInfo.get(name);
            if (name == null || info == null) {
                slots.add(new HomeTab.SlotView(i, HomePage.boxLabel(r.box()), r.type(), r.worn(), null, ItemStack.EMPTY, Double.NaN, null));
                continue;
            }
            boolean locked = plan.locked(name);
            boolean has = on[i];
            worn += has ? 1 : 0;
            HomeTab.Mark mark = has ? locked ? HomeTab.Mark.DONE_CHOSEN : HomeTab.Mark.DONE : locked ? HomeTab.Mark.CHOSEN : HomeTab.Mark.GET;
            List<Component> tip = new ArrayList<>();
            List<Component> lead = new ArrayList<>();
            tip.add(L.c(locked ? "wynnlabs.homev.slot_chosen" : "wynnlabs.homev.slot_planned").withStyle(ChatFormatting.LIGHT_PURPLE));
            if (has) {
                tip.add(L.c("wynnlabs.homev.slot_worn").withStyle(ChatFormatting.GREEN));
            } else {
                // the pill's icon explained first: "Missing · dropped by mobs", then where, when known
                lead.add(L.c("wynnlabs.homev.slot_missing", io.github.anilken.wynnlabs.build.BuildSources.sourceText(name)).withStyle(ChatFormatting.GOLD));
                String detail = io.github.anilken.wynnlabs.build.BuildSources.sourceDetail(name);
                if (detail != null) {
                    lead.add(Component.literal(detail).withStyle(ChatFormatting.GRAY));
                }
                Prices.Price p = Prices.of(name);
                if (p != null && p.typical() != null && Shopping.tradable(info)) {
                    left += p.typical();
                }
            }
            slots.add(new HomeTab.SlotView(i, HomePage.boxLabel(r.box()), r.type(), r.worn(), null, menu.icon(info), Double.NaN, tip, mark, info, lead));
        }
        int better = Plans.HINT.showing();
        HomeTab.PlanLine line = new HomeTab.PlanLine(plan.name(), worn, plan.items().size(), left, better,
                plan.level() > GameState.current().level() ? plan.level() : 0);
        return new HomeTab.In(slots, now, after, menu.quests.isEmpty() ? null : menu.quests.get(0), places, line, chosen,
                saved, searching, road);
    }
}
