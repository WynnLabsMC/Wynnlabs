package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.crafting.Craft;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.ui.CraftingTab.Act;
import io.github.anilken.wynnlabs.ui.CraftingTab.Drop;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.github.anilken.wynnlabs.ui.CraftFormat.typeIcon;
import static io.github.anilken.wynnlabs.ui.CraftFormat.typeName;

/**
 * The crafting tab's dropdowns, all the shared {@link UiMenu}: the item type, the level, the goal
 * (identifications to raise, ticked), the conditions, the attack speed, a material's tier (each
 * with its multiplier) and the road's item type. A choice closes the menu; a tick keeps it open.
 */
final class CraftDropdowns extends CraftPanel {

    private static final List<String> TYPE_ORDER = List.of("#armour", "helmet", "chestplate", "leggings", "boots",
            "#accessory", "ring", "bracelet", "necklace", "#weapon", "spear", "dagger", "bow", "wand", "relik",
            "#consumable", "potion", "scroll", "food");
    /** Tier 1, 2, 3 multiply a recipe's identifications by these (the game's weights). */
    static final String[] TIER_MULT = {"×1,00", "×1,25", "×1,40"};

    CraftDropdowns(CraftingTab tab) {
        super(tab);
    }

    void drawDrop(GuiGraphics g, int mx, int my, float pt) {
        if (tab.open == Drop.NONE || tab.openAnchor == null) {
            return;
        }
        List<UiMenu.Item> items = items();
        if (items.isEmpty()) {
            tab.open = Drop.NONE;
            return;
        }
        UiMenu.State st = tab.dropMenu;
        int[] box = UiMenu.draw(g, UiFont.get(), UiMenu.Spec.under(tab.openAnchor, tab.screen.width, tab.screen.height).rows(12), st,
                items, mx, my, r -> tab.overlay.add(new Act(r.x(), r.y(), r.w(), r.h(), () -> pick(r.item()), null, r.item().tip())));
        tab.overlayBox = box;
    }

    /** The open menu's rows. */
    private List<UiMenu.Item> items() {
        UiMenu.Items items = new UiMenu.Items();
        switch (tab.open) {
            case TYPE -> {
                for (String t : TYPE_ORDER) {
                    if (t.startsWith("#")) {
                        items.divide();
                        continue;
                    }
                    items.add(UiMenu.Item.of(t, typeIcon(t), typeName(t), null, t.equals(tab.type)));
                }
            }
            case LEVEL -> {
                List<CraftData.Recipe> rs = CraftData.ofType(tab.type);
                int lv = tab.level(rs.get(0).profession());
                for (CraftData.Recipe r : rs) {
                    boolean locked = r.minLevel() > lv;
                    UiMenu.Item it = UiMenu.Item.of(String.valueOf(r.minLevel()), locked ? "lock" : null, "Lv " + r.bracket().replace('-', '–'),
                            null, r.minLevel() == tab.minLevel);
                    if (locked) {
                        it = it.asDim().tip(List.of(L.c("wynnlabs.craft.locked_recipe",
                                Professions.name(r.profession().toUpperCase(Locale.ROOT)), r.minLevel(), lv)));
                    }
                    items.add(it);
                }
            }
            case GOAL -> {
                for (String id : idsForProfession()) {
                    items.add(UiMenu.Item.of(id, null, tab.statName(id), null, tab.weights.containsKey(id)));
                }
            }
            case CONDITIONS -> {
                for (int i = 0; i < CraftingTab.FLOORS.length; i++) {
                    items.add(UiMenu.Item.of("floor:" + i, null, L.t("wynnlabs.craft.cond_floor", CraftingTab.FLOORS[i]), null,
                            tab.floorIndex == i));
                }
                items.divide();
                items.add(UiMenu.Item.of("cap", null, L.t("wynnlabs.craft.cond_points"), null, tab.capToPoints));
                items.divide();
                for (String id : idsForProfession()) {
                    items.add(UiMenu.Item.of("neg:" + id, null, L.t("wynnlabs.craft.cond_no_negative_id", tab.statName(id)), null,
                            tab.noNegative.contains(id)));
                }
            }
            case SPEED -> {
                for (Craft.Speed s : Craft.Speed.values()) {
                    items.add(UiMenu.Item.of(s.name(), null, L.t("wynnlabs.craft.speed." + s.name().toLowerCase(Locale.ROOT)), null,
                            s == tab.speed));
                }
            }
            case TIER1, TIER2 -> {
                int now = tab.open == Drop.TIER1 ? tab.tier1 : tab.tier2;
                CraftData.Material m = tab.recipe().materials().get(tab.open == Drop.TIER1 ? 0 : 1);
                for (int t = 1; t <= 3; t++) {
                    items.add(UiMenu.Item.of(String.valueOf(t), "star-filled", L.t("wynnlabs.craft.tier_n", t), TIER_MULT[t - 1], t == now)
                            .sub(L.t("wynnlabs.craft.tier_have", tab.stock.material(m.shown(), t))));
                }
            }
            case ROAD_TYPE -> items.list.addAll(tab.roadPanel.typeItems());
            case ROAD_PROF -> items.list.addAll(tab.roadPanel.profItems());
            default -> { }
        }
        return items.list;
    }

    /** A row picked with the keyboard (Enter): as if clicked. */
    void pickByKey(UiMenu.Item it) {
        pick(it);
    }

    private void pick(UiMenu.Item it) {
        String id = it.id();
        switch (tab.open) {
            case TYPE -> {
                if (!id.equals(tab.type)) {
                    tab.type = id;
                    tab.minLevel = tab.bestBracket(id).minLevel();
                    Arrays.fill(tab.slots, null);
                }
                tab.open = Drop.NONE;
            }
            case LEVEL -> {
                tab.minLevel = Integer.parseInt(id);
                tab.open = Drop.NONE;
            }
            case GOAL -> {
                if (tab.weights.remove(id) == null) {
                    tab.weights.put(id, 1.0);
                }
            }
            case CONDITIONS -> {
                if (id.startsWith("floor:")) {
                    tab.floorIndex = Integer.parseInt(id.substring(6));
                } else if (id.equals("cap")) {
                    tab.capToPoints = !tab.capToPoints;
                } else if (!tab.noNegative.remove(id.substring(4))) {
                    tab.noNegative.add(id.substring(4));
                }
            }
            case SPEED -> {
                tab.speed = Craft.Speed.valueOf(id);
                tab.open = Drop.NONE;
            }
            case TIER1 -> {
                tab.tier1 = Integer.parseInt(id);
                tab.open = Drop.NONE;
            }
            case TIER2 -> {
                tab.tier2 = Integer.parseInt(id);
                tab.open = Drop.NONE;
            }
            case ROAD_TYPE -> {
                tab.roadPanel.pickType(id);
                tab.open = Drop.NONE;
            }
            case ROAD_PROF -> {
                tab.roadPanel.pickProf(id);
                tab.open = Drop.NONE;
            }
            default -> { }
        }
    }

    /** Identifications the recipe's profession can get, for the goal and the conditions. */
    private List<String> idsForProfession() {
        CraftData.Recipe r = tab.recipe();
        java.util.TreeMap<String, String> byShown = new java.util.TreeMap<>();
        for (Ingredient i : tab.ingredients) {
            if (i.usableBy(r.profession()) && i.level() <= r.maxLevel()) {
                for (String id : i.ids().keySet()) {
                    byShown.put(tab.statName(id) + "|" + id, id);
                }
            }
        }
        return new ArrayList<>(byShown.values());
    }
}
