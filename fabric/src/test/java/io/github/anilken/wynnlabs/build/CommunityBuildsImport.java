package io.github.anilken.wynnlabs.build;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.RepoData;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Tier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The one-off import of the builds already collected into the community list
 * ({@code data/community-builds.json} and its {@code .sha256}): the local community chat list
 * (links only), the local list page's cards (their short labels; group names and headings other
 * than the XP set ones dropped), the forum guide's main-post builds and the forum leveling list's
 * level brackets (one link per class and bracket). Every link is decoded and written again in the
 * current form; builds with a piece no longer in the item data, without a readable weapon or the
 * same nine pieces as another are dropped. Nobody's name is published: every author, group and
 * heading name read is a deny word, and the import fails, writing nothing, when one would appear
 * in the output. Opt-in (-PcommunityImport), skipped when the local files are missing; writes a
 * report (counts only) to build/community-import/report.md. Never shipped.
 */
class CommunityBuildsImport {

    static final String DISCORD = "common/src/local/data/discord-builds.json";
    /** The local list page: the one other "*-builds.html" file in this folder. */
    static final String LOCAL = "common/src/local/data";
    static final String OUT = "data/community-builds.json";
    static final String OUT_SHA = "data/community-builds.sha256";
    private static final Pattern CARD = Pattern.compile("(?s)<a[^>]*data-group=\"([^\"]*)\"[^>]*href=\"([^\"]*)\"[^>]*>\\s*<h4>(.*?)</h4>\\s*<p>(.*?)</p>\\s*</a>");
    private static final Pattern XP_HEAD = Pattern.compile("(?i)^(?:(\\d+)-(\\d+) lv )?(G?XP) Bonus Set$");
    private static final Pattern RAID_WORDS = Pattern.compile("(?i)\\b(raids?|tna|tcc|nol|notg|nest of the grootslangs|orphion|canyon of the lost|nameless anomaly)\\b");
    private static final Pattern LOOTRUN_WORDS = Pattern.compile("(?i)\\b(lootrun(s|ning)?|lr)\\b");
    private static final Pattern TANK_WORDS = Pattern.compile("(?i)\\b(tank|tanky|tanking)\\b");

    @AfterEach
    void reset() {
        FixtureState.uninstall();
    }

    /** A build on its way into the list; {@code rank} orders sources for dedupe (higher wins). */
    private static final class Cand {
        String source;
        int rank;
        int order;
        String url;
        String name;
        String archetype;
        Set<String> tags = new java.util.LinkedHashSet<>();
        int levelMin;
        int levelMax = 106;
        String credit;
        Klass forced;
        WynnBuilderLink.Build build;
    }

    static final Map<String, io.github.anilken.wynnlabs.crafting.Ingredient> INGREDIENTS =
            io.github.anilken.wynnlabs.crafting.TestIngredients.byName();
    private final Map<String, Integer> dropped = new TreeMap<>();
    private int itemNamed;
    private final Map<String, Integer> versions = new TreeMap<>();
    private final Map<String, Map<String, Integer>> perSource = new TreeMap<>();
    private final List<String> undecodable = new ArrayList<>();
    private final Set<String> deny = new HashSet<>();

    private void count(String source, String what) {
        perSource.computeIfAbsent(source, k -> new TreeMap<>()).merge(what, 1, Integer::sum);
    }

    private void drop(Cand c, String why) {
        dropped.merge(c.source + ": " + why, 1, Integer::sum);
        count(c.source, "dropped");
    }

    static Path find(String rel) {
        Path dir = Path.of(System.getProperty("wynnlabs.projectDir", ".")).toAbsolutePath();
        for (; dir != null; dir = dir.getParent()) {
            Path f = dir.resolve(rel);
            if (Files.exists(f)) {
                return f;
            }
        }
        return null;
    }

    @Test
    @EnabledIfSystemProperty(named = "wynnlabs.communityImport", matches = "true")
    void importCollectedBuilds() throws Exception {
        Path discord = find(DISCORD);
        Path listPage = listPage();
        Assumptions.assumeTrue(discord != null && listPage != null, "no local " + DISCORD + " or list page");
        Path root = Path.of(System.getProperty("wynnlabs.projectDir", ".")).toAbsolutePath();
        ReferenceBuilds.installPlain();
        List<Cand> cands = new ArrayList<>();
        int order = 0;

        // the community chat list: the link only (and a few play style words of the note as tags);
        // the author is a deny word and never read further
        JsonObject d = JsonParser.parseString(Files.readString(discord, StandardCharsets.UTF_8)).getAsJsonObject();
        for (JsonElement el : d.getAsJsonArray("builds")) {
            JsonObject e = el.getAsJsonObject();
            denyName(text(e, "author"));
            Cand c = new Cand();
            c.source = "discord";
            c.rank = 0;
            c.order = order++;
            c.url = text(e, "url");
            String note = text(e, "note");
            if (note != null) {
                if (RAID_WORDS.matcher(note).find()) {
                    c.tags.add("raid");
                }
                if (LOOTRUN_WORDS.matcher(note).find()) {
                    c.tags.add("lootrun");
                }
            }
            cands.add(c);
        }

        // the list page's cards: the label as the name; group names and other headings are deny words
        String html = Files.readString(listPage, StandardCharsets.UTF_8);
        denyName(listPage.getFileName().toString().replace("-builds.html", ""));       // the list's author, never a source value
        Matcher m = CARD.matcher(html);
        while (m.find()) {
            String group = unhtml(m.group(1));
            String head = unhtml(m.group(3));
            String label = unhtml(m.group(4).replaceAll("(?i)<br\\s*/?>", ", "));
            denyName(group);
            Cand c = new Cand();
            c.source = "community";
            c.rank = 1;
            c.order = order++;
            c.url = unhtml(m.group(2));
            Matcher xp = XP_HEAD.matcher(head);
            if (xp.matches()) {
                boolean gather = xp.group(3).equalsIgnoreCase("GXP");
                c.tags.add(gather ? "gathering-xp" : "xp-bonus");
                if (!gather) {
                    c.tags.add("leveling");
                }
                if (xp.group(1) != null) {
                    c.levelMin = Integer.parseInt(xp.group(1));
                    c.levelMax = Math.min(106, Integer.parseInt(xp.group(2)));
                }
                c.name = gather ? "Gathering XP bonus set" : "XP bonus set" + (xp.group(1) == null ? "" : " " + xp.group(1) + "-" + xp.group(2));
            } else {
                denyName(head);
                c.name = label;
                if (RAID_WORDS.matcher(label).find()) {
                    c.tags.add("raid");
                }
                if (LOOTRUN_WORDS.matcher(label).find()) {
                    c.tags.add("lootrun");
                }
                if (TANK_WORDS.matcher(label).find()) {
                    c.tags.add("tank");
                }
            }
            cands.add(c);
        }

        // the forum guide: its main post's builds only, the thread as the credit line
        JsonObject guide = ReferenceBuilds.resource("guide.json");
        String guideThread = guide.get("source").getAsString();
        for (JsonElement el : guide.getAsJsonArray("builds")) {
            JsonObject e = el.getAsJsonObject();
            denyName(text(e, "credit"));
            denyName(text(e, "poster"));
            if (!"guide".equals(text(e, "post"))) {
                dropped.merge("forum: a reply, not the main post", 1, Integer::sum);
                count("forum", "dropped");
                continue;
            }
            Cand c = new Cand();
            c.source = "forum";
            c.rank = 2;
            c.order = order++;
            c.url = text(e, "url");
            c.archetype = text(e, "archetype");
            c.credit = guideThread;
            for (JsonElement t : e.getAsJsonArray("tags")) {
                String s = t.getAsString();
                if (Set.of("TNA", "TCC", "NOL", "NOTG").contains(s)) {
                    c.tags.add("raid");
                } else if (s.equals("Lootrun")) {
                    c.tags.add("lootrun");
                } else if (s.equals("Mob Grinding")) {
                    c.tags.add("leveling");
                }
            }
            cands.add(c);
        }

        // the forum leveling list: per track and level bracket its armour, and per class the
        // highest listed weapon of the track's kind (spell or melee) the bracket's level allows
        JsonObject lev = ReferenceBuilds.resource("leveling.json");
        String levThread = lev.get("source").getAsString();
        denyName(text(lev, "author"));
        Map<String, List<Integer>> trackLevels = new LinkedHashMap<>();
        for (JsonElement el : lev.getAsJsonArray("builds")) {
            JsonObject b = el.getAsJsonObject();
            trackLevels.computeIfAbsent(text(b, "track"), k -> new ArrayList<>()).add(b.get("level").getAsInt());
        }
        for (JsonElement el : lev.getAsJsonArray("builds")) {
            JsonObject b = el.getAsJsonObject();
            String track = text(b, "track");
            int level = b.get("level").getAsInt();
            int next = trackLevels.get(track).stream().filter(v -> v > level).min(Integer::compare).orElse(107);
            WynnBuilderLink.Build armour = WynnBuilderLink.decode(text(b, "url"));
            if (armour == null) {
                undecodable.add("forum leveling " + track + " " + level);
                continue;
            }
            boolean melee = track.contains("melee");
            for (Klass k : List.of(Klass.WARRIOR, Klass.MAGE, Klass.ARCHER, Klass.ASSASSIN, Klass.SHAMAN)) {
                String weapon = null;
                int best = -1;
                for (JsonElement we : lev.getAsJsonArray("weapons")) {
                    JsonObject w = we.getAsJsonObject();
                    int wl = w.has("itemLevel") && !w.get("itemLevel").isJsonNull() ? w.get("itemLevel").getAsInt() : w.get("level").getAsInt();
                    boolean wMelee = w.getAsJsonArray("tags").toString().contains("Melee");
                    if (w.get("class").getAsString().equals(k.name()) && wMelee == melee && wl <= Math.max(level, 1) && wl > best
                            && ReferenceBuilds.byName(text(w, "item")) != null) {
                        best = wl;
                        weapon = text(w, "item");
                    }
                }
                Cand c = new Cand();
                c.source = "forum";
                c.rank = 2;
                c.order = order++;
                c.forced = k;
                c.credit = levThread;
                c.tags.add("leveling");
                c.levelMin = Math.max(1, level);
                c.levelMax = Math.min(106, next - 1);
                if (weapon == null) {
                    drop(c, "leveling bracket without a listed weapon for the class");
                    continue;
                }
                List<WynnBuilderLink.Piece> pieces = new ArrayList<>();
                for (WynnBuilderLink.Piece p : armour.pieces()) {
                    if (p.slot() != WynnBuilderLink.WEAPON) {
                        pieces.add(p);
                    }
                }
                pieces.add(new WynnBuilderLink.Piece(WynnBuilderLink.WEAPON, WynnBuilderLink.Kind.ITEM, -1, weapon, null, ""));
                c.build = new WynnBuilderLink.Build(null, false, pieces, List.of(), null, 0, "");
                c.url = WynnBuilderExport.link(c.build, null);
                c.build = null;                     // decoded again below, as every other link
                cands.add(c);
            }
        }

        // decode, check, derive
        Map<String, Cand> kept = new LinkedHashMap<>();
        Map<String, JsonObject> entries = new LinkedHashMap<>();
        int outdated = 0;
        int deduped = 0;
        WynnBuilderLink.Table table = WynnBuilderLink.table();
        String newest = table.versions().get(table.versions().size() - 1).name();
        for (Cand c : cands) {
            count(c.source, "read");
            if (c.url == null) {
                drop(c, "no link");
                continue;
            }
            WynnBuilderLink.Build b = WynnBuilderLink.decode(c.url);
            if (b == null) {
                undecodable.add(c.source + " #" + c.order);
                drop(c, "link does not decode");
                continue;
            }
            List<WynnBuilderLink.Piece> real = b.pieces().stream()
                    .filter(p -> !(p.kind() == WynnBuilderLink.Kind.ITEM && (p.name() == null && p.id() < 0 || p.name() != null && p.name().startsWith("No "))))
                    .toList();
            if (real.isEmpty()) {
                drop(c, "empty build");
                continue;
            }
            LinkImport.Result r = LinkImport.of(GameState.current(), b, INGREDIENTS::get);
            boolean unknown = r.rows().stream().anyMatch(row -> row.piece().kind() == WynnBuilderLink.Kind.ITEM && row.info() == null
                    && !(row.piece().name() != null && row.piece().name().startsWith("No ")) && row.piece().id() >= 0);
            if (unknown) {
                drop(c, "a piece no longer in the item data");
                continue;
            }
            WynnBuilderLink.Piece weapon = real.stream().filter(p -> p.slot() == WynnBuilderLink.WEAPON).findFirst().orElse(null);
            if (weapon != null && weapon.kind() == WynnBuilderLink.Kind.CUSTOM) {
                drop(c, "custom weapon");
                continue;
            }
            if (real.stream().allMatch(p -> p.kind() == WynnBuilderLink.Kind.CUSTOM)) {
                drop(c, "custom pieces only");
                continue;
            }
            if (real.stream().anyMatch(p -> p.kind() == WynnBuilderLink.Kind.CRAFTED && p.recipe() == null)) {
                drop(c, "a crafted piece whose recipe does not read");
                continue;
            }
            // a crafted piece is valid when its recipe reads; one whose ingredient is gone marks the build outdated
            boolean craftGone = r.rows().stream().anyMatch(row -> row.piece().kind() == WynnBuilderLink.Kind.CRAFTED && row.made() == null);
            Klass k = weapon == null ? Klass.NONE : weapon.kind() == WynnBuilderLink.Kind.CRAFTED ? craftedClass(weapon) : ReferenceBuilds.classOf(b);
            if (k == null) {
                drop(c, "weapon class not known");
                continue;
            }
            List<ItemData> items = new ArrayList<>();
            List<Damage.Piece> model = new ArrayList<>();
            boolean alias = false;
            for (LinkImport.Row row : r.rows()) {
                if (row.info() != null) {
                    items.add(row.info());
                    model.add(Damage.piece(row.info(), row.piece().powders() == null ? "" : row.piece().powders()));
                    alias |= row.piece().alias() != null && !row.info().name().equals(row.piece().name());
                } else if (row.made() != null) {
                    model.add(row.made());
                }
            }
            // one entry per set of pieces (powders aside): the forum's over the list's over the chat's, else the later one
            List<String> names = new ArrayList<>();
            for (WynnBuilderLink.Piece p : real) {
                names.add(p.kind() == WynnBuilderLink.Kind.ITEM ? p.name() : p.code());
            }
            names.sort(null);
            String key = k + "|" + String.join(",", names);
            Cand had = kept.get(key);
            if (had != null) {
                deduped++;
                if (had.rank > c.rank) {
                    drop(c, "same pieces as another build");
                    continue;
                }
                drop(had, "same pieces as another build");
                entries.remove(key);
            }
            c.build = b;
            int pieceLevel = items.stream().mapToInt(ItemData::level).max().orElse(1);
            c.levelMin = Math.min(106, Math.max(c.levelMin, Math.max(1, pieceLevel)));
            if (c.levelMin > c.levelMax) {
                c.levelMax = 106;
            }
            // the archetype: the forum's, else the link's tree's strongest one the class has
            String arch = c.archetype == null ? "" : c.archetype;
            if (arch.isEmpty() && k != Klass.NONE) {
                WynnBuilderTree.Tree tree = WynnBuilderTree.of(b, k);
                if (tree != null) {
                    List<String> known = ReferenceBuilds.archetypes(k);
                    int best = 0;
                    for (Map.Entry<String, Integer> en : tree.archetypes().entrySet()) {
                        if (known.contains(en.getKey()) && en.getValue() > best) {
                            best = en.getValue();
                            arch = en.getKey();
                        }
                    }
                }
            }
            String style = style(model);
            if (k != Klass.NONE && style != null) {
                c.tags.add(style);
            }
            BuildRoles.Call role = BuildRoles.of(arch, null, model, -1, Double.NaN);
            if (k != Klass.NONE) {
                c.tags.add(role.goal().equals("HEALER") ? "healer" : role.goal().equals("TANK") ? "tank" : "damage");
            }
            String mythic = items.stream().filter(i -> i.tier() == Tier.MYTHIC).map(ItemData::name).findFirst().orElse(null);
            if (mythic == null && k != Klass.NONE) {
                c.tags.add("budget");
            }
            if (c.tags.isEmpty()) {
                c.tags.add("leveling");
            }
            // outdated: older item data than the mod's, a renamed piece, or points that no longer cover the pieces
            // item data more than one release behind the mod's (the release before the newest is the
            // one most links were made with, and its items still read the same)
            boolean oldData = versionIndex(table, b.version()) < table.versions().size() - 2;
            boolean old = oldData || alias || craftGone;
            if (oldData) {
                count(c.source, "outdated-version");
            }
            versions.merge(String.valueOf(b.version()), 1, Integer::sum);
            if (alias) {
                count(c.source, "outdated-alias");
            }
            if (craftGone) {
                count(c.source, "outdated-ingredient");
            }
            if (real.stream().anyMatch(p -> p.kind() == WynnBuilderLink.Kind.CRAFTED)) {
                count(c.source, "with-crafted");
            }
            if (!old && b.skills() != null) {
                int[] need = Build.minPoints(model);
                for (int i = 0; i < need.length && i < b.skills().size(); i++) {
                    Integer have = b.skills().get(i);
                    if (have != null && have < need[i]) {
                        old = true;
                        count(c.source, "outdated-points");
                    }
                }
            }
            String link = WynnBuilderExport.link(b, k == Klass.NONE ? null : WynnBuilderTree.of(b, k));
            WynnBuilderLink.Build back = link == null ? null : WynnBuilderLink.decode(link);
            if (back == null || !samePieces(b, back)) {
                drop(c, "could not be written in the current link form");
                continue;
            }
            String klass = k == Klass.NONE ? "any" : k.name().toLowerCase(Locale.ROOT);
            String cls = k == Klass.NONE ? "set" : klass;
            // the build's mythic, else its weapon, then its archetype or style and class: "Warp Riftwalker mage"
            String lead = mythic != null ? mythic : weapon != null && weapon.kind() == WynnBuilderLink.Kind.ITEM ? weapon.name()
                    : weapon != null ? "Crafted " + weapon.recipe().type().toLowerCase(Locale.ROOT) : null;
            String what = arch.isEmpty() ? style == null ? "" : style : arch;
            String name = c.name != null ? c.name : ((lead == null ? "" : lead + " ") + (what.isEmpty() ? "" : what + " ") + cls).trim();
            name = cap(name);
            JsonObject o = new JsonObject();
            String slug = (klass + "-" + (arch.isEmpty() ? style == null ? "set" : style : arch) + "-" + c.levelMin)
                    .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
            o.addProperty("id", slug + "-" + RepoData.sha256(link.getBytes(StandardCharsets.UTF_8)).substring(0, 6));
            o.addProperty("name", name);
            o.addProperty("link", link);
            o.addProperty("class", klass);
            o.addProperty("levelMin", c.levelMin);
            o.addProperty("levelMax", c.levelMax);
            JsonArray tags = new JsonArray();
            CommunityBuilds.TAGS.stream().filter(c.tags::contains).forEach(tags::add);
            o.add("tags", tags);
            o.addProperty("description", description(k, arch, style, role.goal(), c, mythic, model));
            o.addProperty("source", c.source);
            o.addProperty("checked", java.time.LocalDate.now().toString());
            o.addProperty("outdated", old);
            if (c.credit != null && c.source.equals("forum")) {
                o.addProperty("credit", c.credit);
            }
            kept.put(key, c);
            entries.put(key, o);
        }
        List<JsonObject> list = new ArrayList<>(entries.values());
        Map<String, Integer> classOrder = Map.of("warrior", 0, "mage", 1, "archer", 2, "assassin", 3, "shaman", 4, "any", 5);
        list.sort(Comparator.<JsonObject>comparingInt(o -> classOrder.get(o.get("class").getAsString()))
                .thenComparingInt(o -> o.get("levelMin").getAsInt()).thenComparing(o -> o.get("name").getAsString())
                .thenComparing(o -> o.get("id").getAsString()));
        // ids unique: a clash gets a longer hash
        Set<String> ids = new HashSet<>();
        for (JsonObject o : list) {
            if (!ids.add(o.get("id").getAsString())) {
                fail("two entries share the id " + o.get("id").getAsString());
            }
            if (o.get("outdated").getAsBoolean()) {
                outdated++;
            }
            count(o.get("source").getAsString(), "kept");
        }
        JsonObject doc = new JsonObject();
        doc.addProperty("version", 1);
        doc.addProperty("updated", java.time.LocalDate.now().toString());
        JsonArray tagList = new JsonArray();
        CommunityBuilds.TAGS.forEach(tagList::add);
        doc.add("tags", tagList);
        JsonArray builds = new JsonArray();
        list.forEach(builds::add);
        doc.add("builds", builds);
        String json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(doc) + "\n";

        // the anonymity guard: no name read from a source may appear anywhere in the output
        List<String> leaks = new ArrayList<>();
        StringBuilder local = new StringBuilder();
        String low = json.toLowerCase(Locale.ROOT);
        // a group named after a mythic is the item's name, not a person's: item names stay in the list
        Set<String> items = new HashSet<>();
        GameState.current().allGear().forEach(i -> {
            String n = i.name().toLowerCase(Locale.ROOT);
            items.add(n);
            items.addAll(List.of(n.split("[^\\p{L}\\p{N}_]+")));   // "maple" of Masterwork Maple Relik
        });
        for (String n : deny) {
            if (items.contains(n)) {
                itemNamed++;
                continue;
            }
            Pattern p = Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(n) + "(?![\\p{L}\\p{N}_])");
            Matcher hit = p.matcher(low);
            if (hit.find()) {
                leaks.add("a source name (" + n.length() + " letters)");
                int at = low.lastIndexOf("\"", low.lastIndexOf("\":", hit.start()) - 1);
                local.append(n).append(" in ").append(low, Math.max(0, at), Math.min(low.length(), hit.end() + 20)).append("\n");
            }
        }
        StringBuilder md = new StringBuilder("# Community builds import\n\n");
        perSource.forEach((s, c) -> md.append("- ").append(s).append(": ").append(c).append('\n'));
        md.append("\nDeduped: ").append(deduped).append(", outdated: ").append(outdated).append(", written: ").append(list.size())
                .append(", deny words: ").append(deny.size()).append(" (").append(itemNamed).append(" are item names or words of one)").append("\n\n## Dropped\n\n");
        dropped.forEach((w, n) -> md.append("- ").append(w).append(": ").append(n).append('\n'));
        md.append("\nLink data versions (newest ").append(newest).append("): ").append(versions).append('\n');
        md.append("\n## Undecodable\n\n");
        undecodable.forEach(u -> md.append("- ").append(u).append('\n'));
        Path report = root.resolve("build/community-import/report.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, md, StandardCharsets.UTF_8);
        System.out.println(md);
        Files.writeString(report.resolveSibling("leaks-local.txt"), local, StandardCharsets.UTF_8);
        if (!leaks.isEmpty()) {
            fail("the import would publish names; nothing written: " + leaks);
        }
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        Files.write(root.resolve(OUT), bytes);
        Files.writeString(root.resolve(OUT_SHA), RepoData.sha256(bytes) + "\n", StandardCharsets.UTF_8);
        assertTrue(list.size() > 0, "nothing imported");
    }

    /** The data version's place in the mod's table; -1 for none or an unknown one (the older link form). */
    private static int versionIndex(WynnBuilderLink.Table t, String v) {
        for (int i = 0; i < t.versions().size(); i++) {
            if (t.versions().get(i).name().equals(v)) {
                return i;
            }
        }
        return -1;
    }

    /** The class a crafted weapon's recipe is for (its type: wand, spear, ...). */
    private static Klass craftedClass(WynnBuilderLink.Piece p) {
        io.github.anilken.wynnlabs.model.Slot t = io.github.anilken.wynnlabs.model.Slot.of(p.recipe().type().toUpperCase(Locale.ROOT));
        return t == null ? null : t.klass();
    }

    /** The local list page ("*-builds.html" beside the chat list), or null. */
    static Path listPage() {
        Path dir = find(LOCAL);
        if (dir == null) {
            return null;
        }
        try (var files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith("-builds.html")).findFirst().orElse(null);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    private void denyName(String name) {
        if (name == null) {
            return;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.length() >= 3) {
            deny.add(n);
        }
    }

    private static boolean samePieces(WynnBuilderLink.Build a, WynnBuilderLink.Build b) {
        Map<Integer, String> x = new TreeMap<>();
        Map<Integer, String> y = new TreeMap<>();
        a.pieces().stream().filter(p -> p.kind() == WynnBuilderLink.Kind.ITEM && p.name() != null && !p.name().startsWith("No "))
                .forEach(p -> x.put(p.slot(), p.name()));
        b.pieces().stream().filter(p -> p.kind() == WynnBuilderLink.Kind.ITEM && p.name() != null && !p.name().startsWith("No "))
                .forEach(p -> y.put(p.slot(), p.name()));
        return x.equals(y);
    }

    /** "spell", "melee" or "hybrid" from the pieces' spell and main attack damage bonuses; null with neither. */
    static String style(List<Damage.Piece> pieces) {
        double spell = 0;
        double melee = 0;
        for (Damage.Piece p : pieces) {
            for (Map.Entry<String, Double> s : p.stats().entrySet()) {
                String k = s.getKey().toLowerCase(Locale.ROOT);
                if (k.startsWith("raw")) {
                    continue;
                }
                if (k.contains("spelldamage")) {
                    spell += s.getValue();
                } else if (k.contains("mainattackdamage")) {
                    melee += s.getValue();
                }
            }
        }
        if (spell <= 0 && melee <= 0) {
            return null;
        }
        return spell > 1.5 * Math.max(0, melee) ? "spell" : melee > 1.5 * Math.max(0, spell) ? "melee" : "hybrid";
    }

    private static String description(Klass k, String arch, String style, String goal, Cand c, String mythic, List<Damage.Piece> model) {
        String range = c.levelMax >= 106 ? "level " + c.levelMin + "+" : "levels " + c.levelMin + "-" + c.levelMax;
        if (k == Klass.NONE) {
            String what = c.tags.contains("gathering-xp") ? "Gathering XP set" : c.tags.contains("xp-bonus") ? "Combat XP bonus set" : "Gear set";
            return what + " for " + range + "; wear it with any weapon.";
        }
        String role = goal.equals("HEALER") ? "healer" : goal.equals("TANK") ? "tank" : "damage";
        StringBuilder s = new StringBuilder(cap((style == null ? "" : style + " ") + role) + " " + (arch.isEmpty() ? "" : arch + " ")
                + k.name().toLowerCase(Locale.ROOT) + " build for " + range);
        if (mythic != null) {
            s.append(", built around ").append(mythic);
        }
        if (c.tags.contains("leveling")) {
            s.append(", for levelling");
        }
        int[] need = Build.minPoints(model);
        int top = 0;
        for (int i = 1; i < need.length; i++) {
            if (need[i] > need[top]) {
                top = i;
            }
        }
        if (need[top] >= 60) {
            s.append("; needs high ").append(io.github.anilken.wynnlabs.model.SkillKind.values()[top].displayName().toLowerCase(Locale.ROOT));
        }
        s.append('.');
        return s.length() > CommunityBuilds.MAX_DESCRIPTION ? s.substring(0, CommunityBuilds.MAX_DESCRIPTION - 1) + "." : s.toString();
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String unhtml(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("<[^>]+>", "").trim();
    }

    private static String text(JsonObject e, String k) {
        return e.has(k) && !e.get(k).isJsonNull() ? e.get(k).getAsString() : null;
    }
}
