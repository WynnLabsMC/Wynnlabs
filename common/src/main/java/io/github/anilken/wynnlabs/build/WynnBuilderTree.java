package io.github.anilken.wynnlabs.build;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.model.Klass;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The aspects and the ability tree at the end of a WynnBuilder build link ({@link
 * WynnBuilderLink.Build#rest}), read and written by the mod's own code from the published format
 * alone.
 *
 * <p>The aspects: a flag, then per aspect slot a used flag, the aspect's id and its tier − 1.
 * The tree: one bit per node, in the order a walk from the tree's first node (the one with no
 * parents) meets them: each node's children (the nodes naming it a parent, in the tree's order)
 * not met before get a bit, and a 1 is taken and walked into at once. Bits the link stops short
 * of are 0. Both are read with the class of the link's weapon and the tree of the link's data
 * version, shipped in {@code wynnbuilder-trees.json} with the names as the game writes them.
 */
public final class WynnBuilderTree {

    /** One aspect: its name and tier (1-4). */
    public record Aspect(String name, int tier) {}

    /**
     * A link's tree: the class, the nodes taken by name in the link's order (the first node
     * included), the aspects and the nodes taken per archetype (only the archetypes with any).
     */
    public record Tree(Klass klass, List<String> nodes, List<Aspect> aspects, Map<String, Integer> archetypes) {}

    /** One class's nodes in WynnBuilder's order: name, archetype ("" for none), parents by place. */
    record Node(String name, String archetype, int[] parents) {}

    /** One data version: the aspect field widths and, per class, its tree and aspect names by id. */
    record Version(String name, int aspectBits, int tierBits, int slots, Map<Klass, List<Node>> trees, Map<Klass, String[]> aspects) {}

    private static volatile Map<String, Version> table;

    private WynnBuilderTree() {}

    /**
     * The tree and aspects of {@code b} for a {@code klass} weapon; null for the older form, an
     * unknown class, or a data version newer than the table (its tree may be laid out otherwise).
     */
    public static Tree of(WynnBuilderLink.Build b, Klass klass) {
        if (b == null || b.newer() || b.rest() == null || b.rest().isEmpty() || klass == null || klass == Klass.NONE) {
            return null;
        }
        Map<String, Version> t = table();
        Version v = t.get(b.version());
        List<Node> nodes = v == null ? null : v.trees().get(klass);
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        Bits bits = new Bits(b.rest());
        List<Aspect> aspects = new ArrayList<>();
        if (bits.take(1) == 1) {
            String[] names = v.aspects().getOrDefault(klass, new String[0]);
            for (int i = 0; i < v.slots(); i++) {
                if (bits.take(1) == 1) {
                    int id = bits.take(v.aspectBits());
                    int tier = bits.take(v.tierBits()) + 1;
                    if (id < names.length && names[id] != null) {
                        aspects.add(new Aspect(names[id], tier));
                    }
                }
            }
        }
        List<List<Integer>> children = new ArrayList<>();
        int root = -1;
        for (int i = 0; i < nodes.size(); i++) {
            children.add(new ArrayList<>());
        }
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).parents().length == 0 && root < 0) {
                root = i;
            }
            for (int p : nodes.get(i).parents()) {
                children.get(p).add(i);
            }
        }
        List<Integer> taken = new ArrayList<>();
        taken.add(root);
        walk(root, children, new boolean[nodes.size()], bits, taken);
        List<String> names = new ArrayList<>();
        Map<String, Integer> archetypes = new LinkedHashMap<>();
        for (int i : taken) {
            Node n = nodes.get(i);
            names.add(n.name());
            if (!n.archetype().isEmpty()) {
                archetypes.merge(n.archetype(), 1, Integer::sum);
            }
        }
        return new Tree(klass, List.copyOf(names), List.copyOf(aspects), java.util.Collections.unmodifiableMap(archetypes));
    }

    private static void walk(int head, List<List<Integer>> children, boolean[] met, Bits bits, List<Integer> taken) {
        for (int c : children.get(head)) {
            if (met[c]) {
                continue;
            }
            met[c] = true;
            if (bits.take(1) == 1) {
                taken.add(c);
                walk(c, children, met, bits, taken);
            }
        }
    }

    /**
     * What {@link #of} reads back, as '0'/'1': the aspects (a flag, then per slot a used flag, the
     * id and tier − 1; only those the class's list names, by name or a name ending in it), then
     * the walk with the nodes in {@code nodes} taken, up to its last 1 as the site writes it. A node
     * no taken parent reaches is left out.
     * Null when data version {@code version} has no tree for the class.
     */
    static String bits(String version, Klass klass, java.util.Collection<String> nodes, List<Aspect> aspects) {
        Version v = klass == null ? null : table().get(version);
        List<Node> tree = v == null ? null : v.trees().get(klass);
        if (tree == null || tree.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        List<int[]> found = new ArrayList<>();
        String[] names = v.aspects().getOrDefault(klass, new String[0]);
        for (Aspect a : aspects == null ? List.<Aspect>of() : aspects) {
            int id = aspectId(names, a.name());
            if (id >= 0 && found.size() < v.slots()) {
                found.add(new int[] {id, Math.max(1, a.tier())});
            }
        }
        put(out, found.isEmpty() ? 0 : 1, 1);
        if (!found.isEmpty()) {
            for (int i = 0; i < v.slots(); i++) {
                put(out, i < found.size() ? 1 : 0, 1);
                if (i < found.size()) {
                    put(out, found.get(i)[0], v.aspectBits());
                    put(out, found.get(i)[1] - 1, v.tierBits());
                }
            }
        }
        List<List<Integer>> children = new ArrayList<>();
        int root = -1;
        for (int i = 0; i < tree.size(); i++) {
            children.add(new ArrayList<>());
        }
        for (int i = 0; i < tree.size(); i++) {
            if (tree.get(i).parents().length == 0 && root < 0) {
                root = i;
            }
            for (int p : tree.get(i).parents()) {
                children.get(p).add(i);
            }
        }
        java.util.Set<String> taken = new java.util.HashSet<>(nodes == null ? List.of() : nodes);
        int walk = out.length();
        write(root, tree, children, new boolean[tree.size()], taken, out);
        int end = out.length();
        while (end > walk && out.charAt(end - 1) == '0') {
            end--;                              // the site ends the walk on its last 1: the reader takes the rest as 0
        }
        return out.substring(0, end);
    }

    private static void write(int head, List<Node> tree, List<List<Integer>> children, boolean[] met, java.util.Set<String> taken,
                              StringBuilder out) {
        for (int c : children.get(head)) {
            if (met[c]) {
                continue;
            }
            met[c] = true;
            boolean in = taken.contains(tree.get(c).name());
            out.append(in ? '1' : '0');
            if (in) {
                write(c, tree, children, met, taken, out);
            }
        }
    }

    private static int aspectId(String[] names, String name) {
        if (name == null) {
            return -1;
        }
        for (int i = 0; i < names.length; i++) {
            if (name.equals(names[i])) {
                return i;
            }
        }
        for (int i = 0; i < names.length; i++) {
            if (names[i] != null && name.endsWith(names[i])) {
                return i;                       // Wynntils may keep a prefix on the name
            }
        }
        return -1;
    }

    private static void put(StringBuilder out, int value, int n) {
        for (int i = 0; i < n; i++) {
            out.append((value >> i & 1) == 1 ? '1' : '0');
        }
    }

    /** '0'/'1' text read lowest bit first; past its end every bit is 0. */
    private static final class Bits {
        private final String s;
        private int at;

        Bits(String s) {
            this.s = s;
        }

        int take(int n) {
            int v = 0;
            for (int i = 0; i < n; i++, at++) {
                if (at < s.length() && s.charAt(at) == '1') {
                    v |= 1 << i;
                }
            }
            return v;
        }
    }

    // ---- the table ----

    static Map<String, Version> table() {
        Map<String, Version> t = table;
        if (t == null) {
            t = load();
            table = t;
        }
        return t;
    }

    private static Map<String, Version> load() {
        Map<String, Version> out = new HashMap<>();
        try (InputStream in = WynnBuilderTree.class.getResourceAsStream("/assets/wynnlabs/build/wynnbuilder-trees.json")) {
            if (in == null) {
                return Map.of();
            }
            JsonObject doc = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject trees = doc.getAsJsonObject("trees");
            JsonObject aspects = doc.getAsJsonObject("aspects");
            Map<String, List<List<Node>>> treeLists = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : trees.entrySet()) {
                JsonObject c = e.getValue().getAsJsonObject();
                JsonArray names = c.getAsJsonArray("names");
                JsonArray archetypes = c.getAsJsonArray("archetypes");
                List<List<Node>> lists = new ArrayList<>();
                for (JsonElement tree : c.getAsJsonArray("trees")) {
                    List<Node> nodes = new ArrayList<>();
                    for (JsonElement n : tree.getAsJsonArray()) {
                        JsonArray a = n.getAsJsonArray();
                        int[] parents = new int[a.size() - 2];
                        for (int i = 2; i < a.size(); i++) {
                            parents[i - 2] = a.get(i).getAsInt();
                        }
                        nodes.add(new Node(names.get(a.get(0).getAsInt()).getAsString(),
                                archetypes.get(a.get(1).getAsInt()).getAsString(), parents));
                    }
                    lists.add(List.copyOf(nodes));
                }
                treeLists.put(e.getKey(), lists);
            }
            for (JsonElement e : doc.getAsJsonArray("versions")) {
                JsonObject o = e.getAsJsonObject();
                Map<Klass, List<Node>> byClass = new HashMap<>();
                Map<Klass, String[]> aspectsByClass = new HashMap<>();
                for (Map.Entry<String, JsonElement> c : o.getAsJsonObject("tree").entrySet()) {
                    Klass k = Klass.valueOf(c.getKey().toUpperCase(Locale.ROOT));
                    byClass.put(k, treeLists.get(c.getKey()).get(c.getValue().getAsInt()));
                    JsonArray names = aspects.getAsJsonArray(c.getKey()).get(o.getAsJsonObject("aspects").get(c.getKey()).getAsInt()).getAsJsonArray();
                    String[] list = new String[names.size()];
                    for (int i = 0; i < list.length; i++) {
                        list[i] = names.get(i).isJsonNull() ? null : names.get(i).getAsString();
                    }
                    aspectsByClass.put(k, list);
                }
                String name = o.get("name").getAsString();
                out.put(name, new Version(name, o.get("aspectBits").getAsInt(), o.get("aspectTierBits").getAsInt(),
                        o.get("aspectSlots").getAsInt(), Map.copyOf(byClass), Map.copyOf(aspectsByClass)));
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read WynnBuilder's ability trees: " + e);
        }
        return Map.copyOf(out);
    }
}
