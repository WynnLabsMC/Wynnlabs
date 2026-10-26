package io.github.anilken.wynnlabs.quests;

import io.github.anilken.wynnlabs.core.L;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Mini-quests: they have no wiki page of their own, but the wiki's "Mini-Quest" page lists every
 * slaying and gathering post in tables (name, level, place, coordinates, province, items, XP).
 * That page is read once and kept a week like the quest pages ({@link QuestWiki#page}); each mini
 * then gets a one-stage guide: bring the items to the post, the post on the map.
 */
public final class MiniQuests {

    /** One post: {@code kind} COMBAT for slaying, else the profession (MINING, WOODCUTTING, FARMING, FISHING). */
    public record Mini(String name, String kind, int level, String place, int[] at, String province, String items, long xp,
            long combatXp) {}

    private static final String PAGE = "Mini-Quest";
    private static volatile Map<String, Mini> posts;
    private static volatile long failedAt;
    private static final AtomicBoolean LOADING = new AtomicBoolean();

    private MiniQuests() {}

    /** Whether the book lists {@code quest} as a mini-quest. */
    public static boolean is(String quest) {
        QuestBook.Entry e = QuestBook.get(quest);
        return e != null && "MINI_QUEST".equals(e.type());
    }

    /** The post of a mini, or null (unknown, or the page still loading: the first call starts it). */
    public static Mini post(String quest) {
        Map<String, Mini> p = posts;
        if (p == null) {
            load();
            return null;
        }
        return p.get(quest);
    }

    /** True while the page is being read (or about to be). */
    public static boolean loading() {
        return posts == null && QuestWiki.on() && (LOADING.get() || failedAt == 0);
    }

    /** The guides built, by quest, language, book line and page read: the same object while nothing changes. */
    private static final Map<String, QuestWiki.Info> BUILT = new java.util.concurrent.ConcurrentHashMap<>();

    /** As {@link #info(String, QuestBook.Entry)} for the book's own entry, kept while nothing changes. */
    public static QuestWiki.Info info(String quest) {
        QuestBook.Entry book = QuestBook.get(quest);
        post(quest);                                    // starts the page's read the first time
        String key = quest + "|" + L.code() + "|" + (book == null ? "" : book.task()) + "|" + System.identityHashCode(posts);
        QuestWiki.Info kept = BUILT.get(key);
        if (kept != null) {
            return kept;
        }
        QuestWiki.Info built = info(quest, book);
        if (built != null) {
            BUILT.put(key, built);
        }
        return built;
    }

    private static void load() {
        if (!QuestWiki.on() || System.currentTimeMillis() - failedAt < 5 * 60_000L || !LOADING.compareAndSet(false, true)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String text = QuestWiki.page(PAGE);
                if (text == null) {
                    failedAt = System.currentTimeMillis();
                } else {
                    posts = parse(text);
                }
            } catch (Exception e) {
                failedAt = System.currentTimeMillis();
            } finally {
                LOADING.set(false);
            }
        });
    }

    /** For tests and the preview: the page as if it had been read. */
    public static void preview(String wikitext) {
        posts = wikitext == null ? null : parse(wikitext);
    }

    /**
     * The guide of a mini: one stage, its task the book's line while the mini is open (it names
     * the post and its coordinates) or, from the wiki, "Bring 24 Perkish Potatoes to the post at
     * [x, y, z]"; the post as its place; the XP as its reward. Null when the wiki does not list it.
     */
    public static QuestWiki.Info info(String quest, QuestBook.Entry book) {
        Mini m = post(quest);
        String bookTask = book == null || book.task() == null || book.task().isBlank() ? null : book.task();
        int[] bookAt = book == null || book.x() == null ? null : new int[] {book.x(), book.y(), book.z()};
        if (m == null && bookTask == null) {
            return null;
        }
        int[] at = bookAt != null ? bookAt : m != null ? m.at() : null;
        String task = bookTask != null ? bookTask
                : L.t(m.kind().equals("COMBAT") ? "wynnlabs.quests.mini_task_slay" : "wynnlabs.quests.mini_task_gather",
                        m.items(), "[" + m.at()[0] + ", " + m.at()[1] + ", " + m.at()[2] + "]");
        List<String> hints = new ArrayList<>();
        if (m != null && !m.place().isEmpty()) {
            hints.add(L.t("wynnlabs.quests.mini_place", m.place(), m.province()));
        }
        List<int[]> points = at == null ? List.of() : List.of(at);
        QuestWiki.Stage stage = new QuestWiki.Stage("Stage 1", task, hints, at, points, List.of());
        List<String> rewards = new ArrayList<>();
        if (m != null && !m.kind().equals("COMBAT") && m.xp() > 0) {
            rewards.add(QuestAdvisor.fmt(m.xp()) + " " + profession(m.kind()) + " XP");
        }
        long combat = m == null ? 0 : m.kind().equals("COMBAT") ? m.xp() : m.combatXp();
        Map<String, Integer> profs = new LinkedHashMap<>();
        if (m != null && !m.kind().equals("COMBAT")) {
            profs.put(m.kind(), m.level());
        }
        return new QuestWiki.Info("", "", m == null ? "" : m.province(), m == null ? 0 : m.level(), combat, 0, rewards,
                List.of(stage), List.of(), profs);
    }

    private static String profession(String kind) {
        return kind.charAt(0) + kind.substring(1).toLowerCase(Locale.ROOT);
    }

    /** The page's mini-quest tables: every row by its name. */
    static Map<String, Mini> parse(String text) {
        Map<String, Mini> out = new LinkedHashMap<>();
        int start = text.indexOf("==Mini-Quests==");
        int end = text.indexOf("Removed Quests", Math.max(0, start));
        String sec = start < 0 ? text : text.substring(start, end < 0 ? text.length() : end);
        int at = 0;
        while ((at = sec.indexOf("{|", at)) >= 0) {
            int close = sec.indexOf("|}", at);
            String table = sec.substring(at, close < 0 ? sec.length() : close);
            at = close < 0 ? sec.length() : close + 2;
            table(table, out);
        }
        return out;
    }

    /** One table: its header names the columns (and, for gathering, the profession). */
    private static void table(String table, Map<String, Mini> out) {
        String[] rows = table.split("\n\\|-");
        List<String> head = new ArrayList<>();
        for (String line : rows[0].split("\n")) {
            if (line.startsWith("!")) {
                head.add(QuestWiki.clean(line.substring(1)).replace("'''", "").replace("&nbsp;", " ").toLowerCase(Locale.ROOT));
            }
        }
        String kind = "COMBAT";
        for (String h : head) {
            for (String p : new String[] {"mining", "woodcutting", "farming", "fishing"}) {
                if (h.startsWith(p)) {
                    kind = p.toUpperCase(Locale.ROOT);
                }
            }
        }
        int cName = find(head, "name");
        int cLevel = find(head, "level");
        int cPlace = find(head, "location");
        int cAt = find(head, "coordinates");
        int cProv = find(head, "province");
        int cItems = find(head, "items");
        int cXp = kind.equals("COMBAT") ? find(head, "combat xp") : find(head, kind.toLowerCase(Locale.ROOT) + " xp");
        int cCombat = find(head, "combat xp");
        for (int r = 1; r < rows.length; r++) {
            List<String> cells = new ArrayList<>();
            for (String line : rows[r].split("\n")) {
                if (line.startsWith("|") && !line.startsWith("|}")) {
                    String c = line.substring(1).replaceFirst("^\\s*align=\"[a-z]+\"\\s*\\|", "");
                    cells.add(QuestWiki.clean(c).trim());
                }
            }
            if (cName < 0 || cName >= cells.size() || cells.get(cName).isEmpty()) {
                continue;
            }
            int[] xyz = coords(get(cells, cAt));
            if (xyz == null) {
                continue;
            }
            String name = cells.get(cName);
            out.put(name, new Mini(name, kind, number(get(cells, cLevel)), get(cells, cPlace), xyz, get(cells, cProv),
                    get(cells, cItems), number(get(cells, cXp)), number(get(cells, cCombat))));
        }
    }

    private static int find(List<String> head, String what) {
        for (int i = 0; i < head.size(); i++) {
            if (head.get(i).contains(what)) {
                return i;
            }
        }
        return -1;
    }

    private static String get(List<String> cells, int i) {
        return i < 0 || i >= cells.size() ? "" : cells.get(i);
    }

    private static int number(String s) {
        String d = s.replaceAll("[^0-9]", "");
        if (d.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(d.length() > 9 ? d.substring(0, 9) : d);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int[] coords(String s) {
        String[] p = s.split(",");
        if (p.length != 3) {
            return null;
        }
        try {
            return new int[] {Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ---- what the pane shows ----

    /** One thing to bring: how many of which item. */
    public record Need(int count, String name) {}

    /**
     * What a mini asks for, as alternatives ("16 Gold Ingot or 16 Gold Gem": two). From the book's
     * line while open ("Bring [15 Snow Clumps] to …"), else the wiki's "Items Required".
     */
    public static List<Need> needs(String bookTask, Mini m) {
        String text = null;
        if (bookTask != null) {
            java.util.regex.Matcher b = BRACKET.matcher(bookTask);
            if (b.find()) {
                text = b.group(1);
            }
        }
        if (text == null && m != null) {
            text = m.items();
        }
        List<Need> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (String part : text.split("\\s+or\\s+")) {
            java.util.regex.Matcher n = AMOUNT.matcher(part.trim());
            if (n.matches()) {
                out.add(new Need(Integer.parseInt(n.group(1)), n.group(2).trim()));
            } else if (!part.isBlank()) {
                out.add(new Need(1, part.trim()));
            }
        }
        return out;
    }

    private static final java.util.regex.Pattern BRACKET = java.util.regex.Pattern.compile("\\[(\\d+ [^\\]]+)]");
    private static final java.util.regex.Pattern AMOUNT = java.util.regex.Pattern.compile("(\\d+)\\s+(.+)");

    /**
     * Whether you can start it, and what is missing: {@code skill} COMBAT or the profession,
     * {@code need} its level, {@code have} yours (-1 unknown).
     */
    public record Req(boolean met, String skill, int need, int have) {}

    /** The requirement of a mini for you: the profession's level for a gathering post, else the combat level. */
    public static Req requirement(Mini m, int level, java.util.function.ToIntFunction<String> profession, QuestBook.Entry book) {
        if (m == null) {
            int need = book == null ? 0 : book.level();
            return new Req(book == null || book.met() || level >= need, "COMBAT", need, level);
        }
        if (m.kind().equals("COMBAT")) {
            return new Req(level >= m.level(), "COMBAT", m.level(), level);
        }
        int have = profession.applyAsInt(m.kind());
        return new Req(have < 0 ? book == null || book.met() : have >= m.level(), m.kind(), m.level(), have);
    }

    /** The mobs a slaying mini names ("Slay Frosted Guards & Cryostone Golems" → Frosted Guard, Cryostone Golem). */
    public static List<String> mobNames(String quest) {
        List<String> out = new ArrayList<>();
        if (quest == null || !quest.startsWith("Slay ")) {
            return out;
        }
        for (String part : quest.substring(5).split("\\s*(?:&|,|\\band\\b)\\s*")) {
            String p = part.trim();
            if (p.isEmpty() || p.toLowerCase(Locale.ROOT).startsWith("creatures of")) {
                continue;
            }
            out.add(singular(p));
        }
        return out;
    }

    /** "Monkeys" → "Monkey", "Ailuropodas" → "Ailuropoda", "Pixies" → "Pixie", "Wolves" → "Wolf". */
    static String singular(String name) {
        if (name.endsWith("ves")) {
            return name.substring(0, name.length() - 3) + "f";
        }
        if (name.endsWith("ies") && name.length() > 4) {
            return name.substring(0, name.length() - 1);
        }
        if (name.endsWith("ses") || name.endsWith("xes") || name.endsWith("ches")) {
            return name.substring(0, name.length() - 2);
        }
        if (name.endsWith("s") && !name.endsWith("ss")) {
            return name.substring(0, name.length() - 1);
        }
        return name;
    }

    /** The mob a slaying mini sends you after, from the wiki's mob list: its name, level range and where. */
    public record MobInfo(String name, int min, int max, String where) {}

    /** The first named mob the list knows, its levels near the mini's (within 15), or null. */
    public static MobInfo mob(String quest, int level, List<io.github.anilken.wynnlabs.search.MobWiki.Mob> all) {
        for (String want : mobNames(quest)) {
            int min = Integer.MAX_VALUE;
            int max = 0;
            String where = "";
            String name = null;
            for (var m : all) {
                if (!m.name().equalsIgnoreCase(want) || m.level() > 0 && Math.abs(m.level() - level) > 15) {
                    continue;
                }
                name = m.name();
                if (m.level() > 0) {
                    min = Math.min(min, m.level());
                    max = Math.max(max, m.level());
                }
                if (where.isEmpty()) {
                    where = m.where() != null && !m.where().isBlank() ? m.where() : m.region() == null ? "" : m.region();
                }
            }
            if (name != null) {
                return new MobInfo(name, min == Integer.MAX_VALUE ? 0 : min, max, where);
            }
        }
        return null;
    }
}
