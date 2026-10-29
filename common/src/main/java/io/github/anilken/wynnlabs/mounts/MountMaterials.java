package io.github.anilken.wynnlabs.mounts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The shipped material table ({@code assets/wynnlabs/mounts/materials.json}). */
public final class MountMaterials {
    private static volatile List<Material> all;

    private MountMaterials() {
    }

    /** Every material, by level then kind order. */
    public static List<Material> all() {
        List<Material> a = all;
        if (a == null) {
            a = load();
            all = a;
        }
        return a;
    }

    /** The material of this kind and level, or a synthetic "Kind L" entry when the table lacks it. */
    public static Material of(MaterialKind kind, int level) {
        for (Material m : all()) {
            if (m.kind() == kind && m.level() == level) {
                return m;
            }
        }
        return new Material(kind.display() + " " + level, level, kind);
    }

    /** By English item name (case-insensitive), or null. */
    public static Material byName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim();
        for (Material m : all()) {
            if (m.name().equalsIgnoreCase(n)) {
                return m;
            }
        }
        return null;
    }

    private static List<Material> load() {
        try (InputStream in = MountMaterials.class.getResourceAsStream("/assets/wynnlabs/mounts/materials.json")) {
            if (in == null) {
                return List.of();
            }
            JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Material> out = new ArrayList<>();
            for (JsonElement e : doc.getAsJsonArray("materials")) {
                JsonObject o = e.getAsJsonObject();
                MaterialKind k = MaterialKind.parse(o.get("kind").getAsString());
                if (k != null) {
                    out.add(new Material(o.get("name").getAsString(), o.get("level").getAsInt(), k));
                }
            }
            return Collections.unmodifiableList(out);
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read the shipped mount materials: " + e);
            return List.of();
        }
    }
}
