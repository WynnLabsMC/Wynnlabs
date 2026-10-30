package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The community leveling list (reference-builds/leveling.json) names items as the item data spells
 * them, each in the slot it lists it for, so {@link LevelingMetaBenchmark} compares like with like.
 */
class LevelingListTest {

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    @Test
    void everyListedItemIsInTheItemData() throws Exception {
        JsonObject doc = ReferenceBuilds.resource("leveling.json");
        ReferenceBuilds.installPlain();
        Map<String, ItemData> db = new HashMap<>();
        GameState.current().allGear().forEach(i -> db.putIfAbsent(i.name(), i));
        List<String> bad = new ArrayList<>();
        int n = 0;
        for (String part : List.of("weapons", "armour")) {
            for (JsonElement el : doc.getAsJsonArray(part)) {
                JsonObject e = el.getAsJsonObject();
                if (e.get("item").isJsonNull()) {
                    bad.add(part + ": " + e.get("listed").getAsString() + " unresolved");
                    continue;
                }
                n++;
                ItemData info = db.get(e.get("item").getAsString());
                if (info == null) {
                    bad.add(part + ": " + e.get("item").getAsString() + " not in the item data");
                    continue;
                }
                String slot = e.get("slot").getAsString();
                boolean ok = part.equals("weapons") ? info.slot().isWeapon()
                        : info.slot().name().toLowerCase(Locale.ROOT).equals(slot);
                if (!ok) {
                    bad.add(part + ": " + info.name() + " is a " + info.slot() + ", listed as " + slot);
                }
            }
        }
        assertTrue(n > 500, "only " + n + " items parsed");
        assertTrue(bad.isEmpty(), String.join("\n", bad));
        assertTrue(doc.getAsJsonArray("unresolved").isEmpty(), "unresolved: " + doc.getAsJsonArray("unresolved"));
    }
}
