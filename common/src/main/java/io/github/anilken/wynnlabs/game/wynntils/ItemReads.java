package io.github.anilken.wynnlabs.game.wynntils;

import com.wynntils.core.components.Handlers;
import com.wynntils.core.components.Models;
import com.wynntils.models.containers.Container;
import com.wynntils.models.containers.containers.personal.AccountBankContainer;
import com.wynntils.models.containers.containers.personal.CharacterBankContainer;
import com.wynntils.models.containers.containers.personal.PersonalStorageContainer;
import com.wynntils.models.containers.containers.trademarket.TradeMarketSellContainer;
import com.wynntils.models.gear.type.GearInfo;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.models.wynnitem.type.ItemObtainInfo;
import io.github.anilken.wynnlabs.game.Wyn.Feature;
import io.github.anilken.wynnlabs.model.MenuKind;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static io.github.anilken.wynnlabs.game.wynntils.Bridge.guard;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.type;

/**
 * Wynntils reads of items, the gear database and the open menu.
 */
public final class ItemReads {

    private ItemReads() {}

    // ---- items and gear (the rest of the mod reads them through WynntilsItems) ----

    public static Optional<WynnItem> item(ItemStack stack) {
        return guard(Feature.ITEMS, () -> Models.Item.getWynnItem(stack), Optional.empty());
    }

    public static <T extends WynnItem> Optional<T> asItem(ItemStack stack, Class<T> type) {
        return guard(Feature.ITEMS, () -> Models.Item.asWynnItem(stack, type), Optional.empty());
    }

    private static java.lang.reflect.Method annotate;

    /**
     * Has Wynntils parse a stack it has not seen (a bank page stored as JSON, a copied stack):
     * its private ItemHandler.annotate, by reflection. False when that is gone.
     */
    public static boolean annotate(ItemStack stack) {
        return guard(Feature.ITEMS, () -> {
            try {
                if (annotate == null) {
                    annotate = com.wynntils.handlers.item.ItemHandler.class.getDeclaredMethod("annotate", ItemStack.class);
                    annotate.setAccessible(true);
                }
                annotate.invoke(Handlers.Item, stack);
                return true;
            } catch (ReflectiveOperationException e) {
                // the private method is gone or changed: an API change like any other
                throw new IncompatibleClassChangeError("ItemHandler.annotate unavailable: " + e);
            }
        }, false);
    }

    public static List<ItemStack> accessories(Player player) {
        return guard(Feature.ITEMS, () -> com.wynntils.utils.wynn.InventoryUtils.getAccessories(player), List.of());
    }

    public static Stream<GearInfo> allGear() {
        return guard(Feature.GEAR_DB, () -> Models.Gear.getAllGearInfos(), Stream.empty());
    }

    public static GearInfo gearByName(String name) {
        return guard(Feature.GEAR_DB, () -> Models.Gear.getGearInfoFromDisplayName(name), null);
    }

    /** An ingredient's star count (0-3) by its name, or null when it is not one. */
    public static Integer ingredientTier(String name) {
        return guard(Feature.GEAR_DB, () -> {
            var info = Models.Ingredient.getIngredientInfoFromName(name);
            return info == null ? null : info.tier();
        }, null);
    }

    /**
     * A piece's picture as Wynntils' gear data has it now (its item with today's model number),
     * or null when unknown. Wynntils' own stack: read it, never change it.
     */
    public static ItemStack gearPicture(String name) {
        return guard(Feature.GEAR_DB, () -> {
            GearInfo info = Models.Gear.getGearInfoFromDisplayName(name);
            return info == null ? null : info.metaInfo().material().itemStack();
        }, null);
    }

    public static List<GearInfo> possibleGears(GearBoxItem box) {
        return guard(Feature.GEAR_DB, () -> Models.Gear.getPossibleGears(box), List.of());
    }

    /**
     * Where a piece comes from. The crowd-sourced sources go by the item's API name, so a piece
     * renamed in game (Ornate Shadow Cloak, "Ornate Shadow Garb" in the API) is looked up by
     * that name too when its display name finds none.
     */
    public static List<ItemObtainInfo> obtainInfo(GearInfo info) {
        return guard(Feature.GEAR_DB, () -> {
            List<ItemObtainInfo> out = Models.Gear.getObtainInfo(info);
            String api = info.metaInfo().apiName().orElse(null);
            if (out.isEmpty() && api != null && !api.equals(info.name())) {
                return Models.WynnItem.getObtainInfo(api);
            }
            return out;
        }, List.of());
    }

    // The Blacksmith's sell menu: BlacksmithSellContainer from 4.1.16. Until 4.1.15 the same menu
    // (same title) is BlacksmithContainer; repair got its own class when sell was renamed.
    private static final String BLACKSMITH_SELL = "com.wynntils.models.containers.containers.BlacksmithSellContainer";
    private static final String BLACKSMITH_OLD = "com.wynntils.models.containers.containers.BlacksmithContainer";

    /** The menu open now, or null when Wynntils does not know it. */
    /** The last container {@link #container} described, and what it said: one container, one answer. */
    private static Object lastContainer;
    private static MenuKind lastKind;

    /**
     * The unit price of a Trade Market listing being looked at (its "Price" block), or 0 for
     * anything else. Only in the Trade Market's buy screens: merchants use the same block.
     */
    public static int marketListingPrice(ItemStack stack) {
        MenuKind kind = container();
        if (kind == null || kind.marketSell() || !kind.type().startsWith("TradeMarket")) {
            return 0;
        }
        // only a listing (its lore has a "Price" line): Wynntils warns in the log on every other item
        var lore = stack == null || stack.isEmpty() ? null : stack.get(net.minecraft.core.component.DataComponents.LORE);
        boolean priced = false;
        if (lore != null) {
            for (var l : lore.lines()) {
                if (io.github.anilken.wynnlabs.game.ItemJson.clean(l.getString()).strip().equals("Price")) {
                    priced = true;
                    break;
                }
            }
        }
        if (!priced) {
            return 0;
        }
        return guard(Feature.CONTAINERS, () -> Math.max(0, Models.TradeMarket.calculateItemPriceInfo(stack).price()), 0);
    }

    public static MenuKind container() {
        return guard(Feature.CONTAINERS, () -> {
            Container c = Models.Container.getCurrentContainer();
            if (c == null) {
                return null;
            }
            if (c == lastContainer) {
                return lastKind;
            }
            Class<?> blacksmith = type(BLACKSMITH_SELL).or(() -> type(BLACKSMITH_OLD))
                    .orElseThrow(() -> new NoClassDefFoundError(BLACKSMITH_SELL + " is not in this Wynntils"));
            PersonalStorageContainer ps = c instanceof PersonalStorageContainer p ? p : null;
            MenuKind kind = new MenuKind(c.getClass().getSimpleName(), c instanceof CharacterBankContainer,
                    c instanceof AccountBankContainer, ps != null, blacksmith.isInstance(c),
                    c instanceof TradeMarketSellContainer, ps != null ? List.copyOf(ps.getBounds().getSlots()) : List.of(),
                    ps != null ? ps.getPreviousItemSlot() : -1, ps != null ? ps.getNextItemSlot() : -1);
            lastKind = kind;
            lastContainer = c;
            return kind;
        }, null);
    }
}
