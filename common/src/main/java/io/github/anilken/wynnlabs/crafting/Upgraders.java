package io.github.anilken.wynnlabs.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.anilken.wynnlabs.search.Area;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * The Item Upgrader NPCs by town, shipped in {@code assets/wynnlabs/crafting/item-upgraders.json}
 * (the item index lists none): where the compass points for a tool upgrade. The wiki gives x and z
 * only, so the height is a plain ground level.
 */
public final class Upgraders {

    private static final int GROUND = 70;
    private static volatile List<Area> all;

    private Upgraders() {}

    /** Every Item Upgrader, named by its town. */
    public static List<Area> all() {
        List<Area> a = all;
        if (a == null) {
            a = load();
            all = a;
        }
        return a;
    }

    private static List<Area> load() {
        List<Area> out = new ArrayList<>();
        try (InputStream in = Upgraders.class.getResourceAsStream("/assets/wynnlabs/crafting/item-upgraders.json")) {
            if (in == null) {
                return out;
            }
            JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : doc.getAsJsonArray("upgraders")) {
                JsonObject o = e.getAsJsonObject();
                out.add(new Area(o.get("x").getAsInt(), GROUND, o.get("z").getAsInt(), 0, -1, 0,
                        "Item Upgrader, " + o.get("place").getAsString()));
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read the shipped Item Upgraders: " + e);
        }
        return List.copyOf(out);
    }
}
