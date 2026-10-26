package io.github.anilken.wynnlabs.quests;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A guide stage laid out for reading: its hints without dialogue or leftovers, split into the
 * parts the wiki marks "▸ Voidroot" and the options it writes as "Mechanic Outfit =" (options with
 * the same steps shown once, a lone option as a plain choice), choices ("If you talk to Ava
 * first:") with their steps under them, items gained, places as a numbered list, and a long
 * paragraph cut into sentences numbered on with the rest of its list.
 */
public final class GuideLayout {

    public enum Kind { PART, CHOICE, ITEM, STEP, BULLET, PLACE }

    /**
     * One row: {@code number} counts a list's steps or places (1, 2, …; 0 otherwise), {@code under}
     * sits beneath a choice or a line leading into a list, {@code at} the place it names or null;
     * a place's {@code place} is its coordinates as written and {@code note} the line about it.
     */
    public record Row(Kind kind, String text, int number, boolean under, int[] at, String place, String note) {

        Row(Kind kind, String text, int number, boolean under, int[] at) {
            this(kind, text, number, under, at, null, null);
        }
    }

    /** The options sharing these rows ("Mechanic Outfit", "Azure Outfit"). */
    public record Option(List<String> labels, List<Row> rows) {}

    /** A part ("Voidroot"): what it is for ("Brie", from "Used for: Brie"), its rows and its options. */
    public record Part(String name, String usedFor, List<Row> common, List<Option> options) {}

    /**
     * The rows before any option or part, then the options when the stage starts with them
     * (parts are rows inside them), or the parts when it starts with those (options sit in a part).
     */
    public record View(List<Row> common, List<Option> options, List<Part> parts) {}

    /** A paragraph longer than this is cut into its sentences. */
    static final int LONG = 170;

    private static final Pattern ITEM = Pattern.compile("^\\[?([+-]\\d[\\d,]*) ([^\\[\\]|]+?)]?$");
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+(?=[\\p{Lu}(\\[])");
    private static final Pattern OWN_NUMBER = Pattern.compile("^(\\d{1,2})\\. (.+)$");
    private static final Pattern COORDS_ONLY = Pattern.compile("^[\\[(]?-?\\d{1,5},? -?\\d{1,3},? -?\\d{1,5}[\\])]?\\.?$");
    private static final Pattern NAMED = Pattern.compile("^([^:]{1,40}):\\s*([\\[(]?-?\\d{1,5}, -?\\d{1,3}, -?\\d{1,5}[\\])]?)\\.?$");
    /** "Granite cave (-1797 -2120) - for getting the L-I-G item" */
    private static final Pattern NAMED_XZ = Pattern.compile("^(.{2,40}?) \\((-?\\d{2,5}),? (-?\\d{2,5})\\)\\s*[-–]\\s*(.+)$");
    private static final Pattern USED_FOR = Pattern.compile("^Used for: (.+)$");
    private static final Pattern NOT_A_NAME = Pattern.compile("^(?:If|When|Once|Should|After|Before|Told|Gave|Said) ");

    private GuideLayout() {}

    private record Line(String text, String en) {}

    public static View of(QuestWiki.Stage st, QuestWiki.Stage english) {
        return of(st, english, null);
    }

    /**
     * {@code questText}: the whole guide's text in its language, lower case; an option's or part's
     * English name is used when the translation keeps it elsewhere (an item: "Emerald-Encrusted
     * Outfit"), the translated one otherwise ("Toprak" for "Earth"). Null: the stage's own text.
     */
    public static View of(QuestWiki.Stage st, QuestWiki.Stage english, String questText) {
        List<Line> lines = new ArrayList<>();
        for (int j : QuestWiki.shownHints(st, english)) {
            String en = english != null && j < english.hints().size() ? QuestWiki.tidy(english.hints().get(j)) : null;
            lines.add(new Line(QuestWiki.tidy(st.hints().get(j)), en));
        }
        String quest = questText != null ? questText : (st.task() + " " + String.join(" ", st.hints())).toLowerCase(Locale.ROOT);
        int firstOption = -1;
        int firstPart = -1;
        for (int i = 0; i < lines.size(); i++) {
            String h = lines.get(i).text();
            if (firstOption < 0 && h.endsWith("=")) {
                firstOption = i;
            }
            if (firstPart < 0 && h.startsWith("▸ ")) {
                firstPart = i;
            }
        }
        if (firstPart >= 0 && (firstOption < 0 || firstPart < firstOption)) {
            // parts first: each part holds its own options
            List<Part> parts = new ArrayList<>();
            List<Line> pre = new ArrayList<>(lines.subList(0, firstPart));
            List<Line> cur = null;
            String name = null;
            for (Line l : lines.subList(firstPart, lines.size())) {
                if (l.text().startsWith("▸ ")) {
                    if (cur != null) {
                        parts.add(part(name, cur, quest));
                    }
                    name = label(l.text().substring(2).trim(), l.en() == null ? null : l.en().replaceFirst("^▸ ", ""), quest);
                    cur = new ArrayList<>();
                } else {
                    cur.add(l);
                }
            }
            parts.add(part(name, cur, quest));
            return new View(rows(pre), List.of(), List.copyOf(parts));
        }
        List<Line> common = new ArrayList<>();
        List<Option> options = sections(lines, common, quest);
        return new View(rows(common), options, List.of());
    }

    private static Part part(String name, List<Line> lines, String quest) {
        String usedFor = null;
        List<Line> rest = new ArrayList<>();
        for (Line l : lines) {
            Matcher m = USED_FOR.matcher(l.en() != null ? l.en() : l.text());
            if (m.matches()) {
                usedFor = m.group(1).trim();
            } else {
                rest.add(l);
            }
        }
        List<Line> common = new ArrayList<>();
        List<Option> options = sections(rest, common, quest);
        return new Part(name, usedFor, rows(common), options);
    }

    /**
     * Lines split at their "… =" headings: what comes before goes into {@code common}, the rest into
     * options; options whose steps read the same are one. A lone option is a plain choice in
     * {@code common} ("Zafer:" over its lines).
     */
    private static List<Option> sections(List<Line> lines, List<Line> common, String quest) {
        List<String> labels = new ArrayList<>();
        List<List<Line>> sections = new ArrayList<>();
        for (Line l : lines) {
            if (l.text().endsWith("=")) {
                String en = l.en() == null ? null : l.en().replaceAll("\\s*=$", "");
                labels.add(label(l.text().substring(0, l.text().length() - 1).trim(), en, quest));
                sections.add(new ArrayList<>());
            } else if (sections.isEmpty()) {
                common.add(l);
            } else {
                sections.get(sections.size() - 1).add(l);
            }
        }
        if (sections.size() == 1) {
            common.add(new Line(labels.get(0) + ":", null));
            common.addAll(sections.get(0));
            return List.of();
        }
        List<List<String>> groupLabels = new ArrayList<>();
        List<List<Line>> groupLines = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            int same = -1;
            for (int k = 0; k < groupLines.size() && same < 0; k++) {
                if (alike(groupLines.get(k), sections.get(i))) {
                    same = k;
                }
            }
            if (same >= 0) {
                groupLabels.get(same).add(labels.get(i));
            } else {
                groupLabels.add(new ArrayList<>(List.of(labels.get(i))));
                groupLines.add(sections.get(i));
            }
        }
        List<Option> options = new ArrayList<>();
        for (int i = 0; i < groupLines.size(); i++) {
            options.add(new Option(List.copyOf(groupLabels.get(i)), rows(groupLines.get(i))));
        }
        return List.copyOf(options);
    }

    /**
     * An option's or part's name: the English one when it is a name the translation keeps in its
     * text (an item), the translated one otherwise (a sentence, a common word).
     */
    static String label(String shown, String en, String quest) {
        if (en == null || en.isBlank() || en.equalsIgnoreCase(shown) || NOT_A_NAME.matcher(en).find()
                || en.split("\\s+").length > 4 || !quest.contains(en.toLowerCase(Locale.ROOT))) {
            return shown;
        }
        return en;
    }

    /**
     * Two options' lines read the same: as many lines, headings where headings are, and nine in ten
     * of their words shared (in English when the guide has it: translations of the same line vary
     * more than the wiki's wording does).
     */
    static boolean alike(List<Line> a, List<Line> b) {
        if (a.size() != b.size()) {
            return false;
        }
        Set<String> x = new HashSet<>();
        Set<String> y = new HashSet<>();
        for (int i = 0; i < a.size(); i++) {
            Line p = a.get(i);
            Line q = b.get(i);
            if (p.text().endsWith(":") != q.text().endsWith(":")) {
                return false;
            }
            x.addAll(words(p.en() != null ? p.en() : p.text()));
            y.addAll(words(q.en() != null ? q.en() : q.text()));
        }
        return alike(x, y, 0.85);
    }

    static boolean alike(Set<String> x, Set<String> y, double share) {
        Set<String> both = new HashSet<>(x);
        both.retainAll(y);
        Set<String> any = new HashSet<>(x);
        any.addAll(y);
        return any.isEmpty() || both.size() >= share * any.size();
    }

    private static Set<String> words(String s) {
        Set<String> out = new HashSet<>();
        for (String w : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (!w.isEmpty()) {
                out.add(w);
            }
        }
        return out;
    }

    /** A row being built: what it is, and whether it is prose (and a cut paragraph) for the numbering. */
    private static final class R {
        Kind kind;
        String text;
        int number;
        boolean under;
        int[] at;
        String place;
        String note;
        boolean prose;
        boolean cut;

        R(Kind kind, String text, boolean under, int[] at) {
            this.kind = kind;
            this.text = text;
            this.under = under;
            this.at = at;
        }

        Row row() {
            return new Row(kind, text, number, under, at, place, note);
        }
    }

    /** Lines as rows. */
    static List<Row> rows(List<Line> lines) {
        List<R> out = new ArrayList<>();
        boolean choice = false;              // under a choice heading
        boolean lead = false;                // under a long line leading into a list
        for (Line l : lines) {
            String h = l.text();
            R last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (h.startsWith("▸ ")) {
                out.add(new R(Kind.PART, h.substring(2).trim(), false, null));
                choice = lead = false;
                continue;
            }
            if (l.en() != null && QuestWiki.QUOTED_IF.matcher(l.en()).matches()) {
                out.add(new R(Kind.CHOICE, h.replaceAll("^\"|\"$", ""), false, null));
                choice = true;
                lead = false;
                continue;
            }
            R place = QuestWiki.condition(h, l.en()) ? null : place(h, choice || lead);
            if (place != null) {
                if (h.endsWith(":")) {
                    lead = true;           // "[x, y, z] noktasındaki delik arenaya çıkar:" heads its note
                }
                out.add(place);
                continue;
            }
            if (h.endsWith(":") && QuestWiki.choice(h, l.en())) {
                out.add(new R(Kind.CHOICE, h.substring(0, h.length() - 1).trim(), false, QuestWiki.placeIn(h)));
                choice = true;
                lead = false;
                continue;
            }
            Matcher m = ITEM.matcher(h);
            if (m.matches() && m.group(2).split("\\s+").length <= 6) {
                out.add(new R(Kind.ITEM, m.group(1) + " " + m.group(2).trim(), choice, null));
                continue;
            }
            Matcher own = OWN_NUMBER.matcher(h);
            if (own.matches() && (lead || choice)) {
                R r = new R(Kind.STEP, own.group(2), true, QuestWiki.placeIn(own.group(2)));
                r.number = Integer.parseInt(own.group(1));
                out.add(r);
                continue;
            }
            if (last != null && last.kind == Kind.PLACE && last.text != null && !last.text.isEmpty() && last.note == null
                    && GuideCoords.find(h, List.of()).isEmpty()) {
                last.note = h;             // the line about the place just named
                continue;
            }
            if (h.endsWith(":")) {
                // a long sentence leading into the list under it
                R r = new R(Kind.BULLET, h, choice, QuestWiki.placeIn(h));
                r.prose = true;
                out.add(r);
                lead = true;
                continue;
            }
            lead = false;
            String[] sentences = h.length() > LONG ? SENTENCE.split(h) : new String[] {h};
            for (String s : sentences) {
                R r = new R(sentences.length > 1 ? Kind.STEP : Kind.BULLET, s, choice, QuestWiki.placeIn(s));
                r.prose = true;
                r.cut = sentences.length > 1;
                out.add(r);
            }
        }
        number(out);
        List<Row> rows = new ArrayList<>();
        for (R r : out) {
            rows.add(r.row());
        }
        return List.copyOf(rows);
    }

    /**
     * A place line, or null: "Cargo: 2027, 74, -4631", "1995, 69, -4650", "Cave (-1797 -2120) - …",
     * "[x, y, z] noktasındaki delik …:". A condition naming a place ("Kıyafetleri (x, y, z) noktasındaki
     * odada yıkadıktan sonra:") is not one: it stays a choice, its coordinates clickable in its text.
     */
    private static R place(String h, boolean under) {
        if (COORDS_ONLY.matcher(h).matches()) {
            R r = new R(Kind.PLACE, "", under, QuestWiki.placeIn(h));
            r.place = h.replaceAll("[\\[\\]().]", "").trim();
            return r.at == null ? null : r;
        }
        Matcher m = NAMED.matcher(h);
        if (m.matches()) {
            R r = new R(Kind.PLACE, m.group(1).trim(), under, QuestWiki.placeIn(m.group(2)));
            r.place = m.group(2).replaceAll("[\\[\\]()]", "");
            return r.at == null ? null : r;
        }
        m = NAMED_XZ.matcher(h);
        if (m.matches()) {
            R r = new R(Kind.PLACE, m.group(1).trim(), under, null);
            r.place = m.group(2) + ", " + m.group(3);
            r.note = m.group(4).trim();
            return r;
        }
        if (h.endsWith(":") && h.length() <= 120) {
            int[] at = QuestWiki.placeIn(h);
            if (at != null) {
                String name = QuestWiki.placeText(h, at);
                if (name.length() >= 4) {
                    R r = new R(Kind.PLACE, Character.toUpperCase(name.charAt(0)) + name.substring(1), under, at);
                    r.place = at[0] + ", " + at[1] + ", " + at[2];
                    return r;
                }
            }
        }
        return null;
    }

    /**
     * Numbers the lists: a run of prose under one heading where a paragraph was cut into sentences
     * counts on through the run (1–8 over two paragraphs); a run of places counts 1, 2, 3 (a place
     * alone has no number).
     */
    private static void number(List<R> rows) {
        int i = 0;
        while (i < rows.size()) {
            R r = rows.get(i);
            int j = i + 1;
            if (r.prose) {
                boolean cut = r.cut;
                while (j < rows.size() && rows.get(j).prose && rows.get(j).under == r.under && !rows.get(j).text.endsWith(":")) {
                    cut |= rows.get(j).cut;
                    j++;
                }
                if (cut) {
                    for (int k = i, n = 1; k < j; k++, n++) {
                        rows.get(k).kind = Kind.STEP;
                        rows.get(k).number = n;
                    }
                }
            } else if (r.kind == Kind.PLACE) {
                while (j < rows.size() && rows.get(j).kind == Kind.PLACE) {
                    j++;
                }
                for (int k = i, n = 1; k < j && j - i > 1; k++, n++) {
                    rows.get(k).number = n;
                }
            }
            i = j;
        }
    }
}
