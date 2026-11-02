package io.github.anilken.wynnlabs.market;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.model.ItemView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A material's price is kept per quality tier: "Cinnabar Gem T3", never the three tiers mixed under one name. */
class MaterialTierPriceTest {

    private static ItemView material(String name, Integer tier) {
        return new ItemView(ItemView.Kind.MATERIAL, "MaterialItem", false, name, 100, List.of("mining"), null, null, null, null,
                tier, null, null, null, null, null, null);
    }

    @Test
    void aMaterialsKeyCarriesItsTier() {
        assertEquals("Cinnabar Gem T3", Prices.key(material("Cinnabar Gem", 3), null));
        assertEquals("Cinnabar Gem T1", Prices.key(material("Cinnabar Gem", 1), null));
        assertEquals("Cinnabar Gem", Prices.key(material("Cinnabar Gem", null), null));
        assertEquals("Cinnabar Gem T3", Prices.key(material("Cinnabar Gem T3", 3), null), "not doubled");
        ItemView ingredient = new ItemView(ItemView.Kind.INGREDIENT, "IngredientItem", false, "Runner's Bandages", 1, List.of(),
                null, null, null, null, 1, null, null, null, null, null, null);
        assertEquals("Runner's Bandages", Prices.key(ingredient, null), "an ingredient has one tier: its plain name");
        assertEquals("Fallback", Prices.key(null, "Fallback"));
    }

    @Test
    void aTieredKeySplitsIntoNameAndTier() {
        Object[] t = Prices.tiered("Cinnabar Gem T3");
        assertEquals("Cinnabar Gem", t[0]);
        assertEquals(3, t[1]);
        assertNull(Prices.tiered("Cinnabar Gem"));
        assertNull(Prices.tiered("Teleport Scroll [3/3]"));
    }

    @Test
    void aListingsTierIsReadAsANumber() {
        assertEquals(3, Prices.listingTier(obj("{\"tier\":3}")));
        assertEquals(2, Prices.listingTier(obj("{\"tier\":\"T2\"}")));
        assertEquals(1, Prices.listingTier(obj("{\"tier\":\"Tier 1\"}")));
        assertNull(Prices.listingTier(obj("{\"tier\":null}")));
        assertNull(Prices.listingTier(obj("{}")));
    }

    private static JsonObject obj(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
