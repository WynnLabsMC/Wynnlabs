package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The guide of a quest with no shipped guide: the wiki page's stage headings, task lines (») and
 * hidden solutions in one scrolling list, dialogue and tables left out, the line closest to the
 * tracked task in green. Each line with coordinates has its "Yol ›".
 */
final class GuideWikiList {

    private record Line(String text, int colour, int[] at, boolean stage) {}

    private static final Pattern COORDS = Pattern.compile("\\[\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*,\\s*(-?\\d+)\\s*]");
    /** NPC dialogue lines ("* {{Dialogue|…}}"): the guide shows tasks, not conversations. */
    private static final Pattern DIALOGUE = Pattern.compile("[*#:]*\\s*\\{\\{Dialogue\\|", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOUR = Pattern.compile("\\{\\{c\\|[^|{}]*\\|([^{}]*)}}");
    private static final Pattern TEMPLATE = Pattern.compile("\\{\\{[^{}]*}}");

    private final GuideScreen screen;
    private volatile List<Line> lines;
    private volatile String error;
    private int current = -1;
    private int scroll;
    private boolean first = true;
    /** The walkthrough's bar (offsets in rows). */
    final ScrollBar bar = new ScrollBar();

    GuideWikiList(GuideScreen screen) {
        this.screen = screen;
    }

    boolean current() {
        return current >= 0;
    }

    void scroll(int rows) {
        scroll = Math.max(0, scroll + rows);
    }

    void fetch() {
        if (!QuestWiki.on()) {
            error = L.t("wynnlabs.guide.off");
            return;
        }
        try {
            // the same page QuestWiki keeps on disk for a week: one request per quest per week
            String text = QuestWiki.page(screen.quest);
            if (text == null) {
                error = L.t("wynnlabs.guide.no_page");
                return;
            }
            lines = parse(text);
        } catch (Exception e) {
            error = L.t("wynnlabs.guide.unreachable", e.getClass().getSimpleName());
        }
    }

    /**
     * Templates in a line, innermost first so nested ones come apart cleanly: coloured text
     * ({{c|colour|text}}) keeps its text, any other template is dropped.
     */
    static String templates(String l) {
        String prev;
        do {
            prev = l;
            l = COLOUR.matcher(l).replaceAll(r -> Matcher.quoteReplacement(r.group(1)));
            l = TEMPLATE.matcher(l).replaceAll("");
        } while (!l.equals(prev));
        return l.replace("{{", "").replace("}}", "");
    }

    /** Wikitext → stage headings, the task lines (») and the hidden solutions; no dialogue or tables. */
    private List<Line> parse(String text) {
        List<Line> out = new ArrayList<>();
        text = text.replaceAll("(?s)<ref.*?</ref>", "").replaceAll("(?s)<!--.*?-->", "");
        // spoilers: keep their content, drop the wrapper and its label
        text = text.replaceAll("(?s)\\{\\{HideSpoiler\\|(.*?)\\|[^|{}]*}}", "$1");
        text = QuestWiki.dropBlockTemplates(text);  // {{Infobox/Items … }} and the like
        boolean inTable = false;
        boolean skip = false;
        String taskLow = screen.task.toLowerCase(Locale.ROOT);
        int bestScore = 0;
        for (String raw : text.split("\n")) {
            String l = raw.trim();
            if (l.startsWith("{|") || l.startsWith("{{MobTable") || l.startsWith("{{Infobox")) {
                inTable = true;
                continue;
            }
            if (inTable) {
                if (l.startsWith("|}") || l.equals("}}")) {
                    inTable = false;
                }
                continue;
            }
            if (l.startsWith("==")) {
                String h = l.replaceAll("=", "").trim();
                skip = h.equalsIgnoreCase("Navigation") || h.equalsIgnoreCase("Trivia") || h.equalsIgnoreCase("Rewards");
                if (!skip) {
                    out.add(new Line(QuestWiki.stageTitle(h), Theme.ACCENT, null, true));
                }
                continue;
            }
            if (skip || l.isEmpty() || l.startsWith("[[File:") || l.startsWith("[[Category") || l.startsWith("'''Dialogue")
                    || l.startsWith("*'''") || l.startsWith("{{") || l.startsWith("|") || DIALOGUE.matcher(l).lookingAt()) {
                continue;
            }
            String clean = templates(l.replaceAll("\\[\\[(?:[^|\\]]*\\|)?([^\\]]*)]]", "$1").replaceAll("'''?", ""))
                    .replaceAll("<[^>]+>", "").replaceFirst("^[*#:]+\\s*", "• ").trim();
            if (clean.isEmpty() || clean.equals("•")) {
                continue;
            }
            int[] at = null;
            Matcher m = COORDS.matcher(clean);
            if (m.find()) {
                at = new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
            }
            boolean taskLine = clean.startsWith("»");
            out.add(new Line(clean, taskLine ? Theme.INK : Theme.INK_2, at, false));
            // the stage you are on: the task line that shares the most words with the tracked task
            if (taskLine && !taskLow.isEmpty()) {
                int score = 0;
                for (String word : clean.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                    if (word.length() > 3 && taskLow.contains(word)) {
                        score++;
                    }
                }
                if (score > bestScore) {
                    bestScore = score;
                    current = out.size() - 1;
                }
            }
        }
        return out;
    }

    /** A press on the bar; true when it took it. */
    boolean press(double mx, double my) {
        if (bar.mouseClicked(mx, my)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    /** A drag with the bar held; true while it is. */
    boolean drag(double my) {
        if (bar.mouseDragged(my)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    void draw(GuiGraphics g, int mx, int my, int left, int top, int right, int bottom) {
        var font = screen.getFont();
        bar.place(0, 0, 0, 1, 1, 0);
        int barX = right - ScrollBar.W;
        right -= ScrollBar.W + Theme.SP_2;                   // the text and the go buttons leave the bar its room
        if (lines == null) {
            Type.BODY.draw(g, font, error != null ? error : L.t("wynnlabs.guide.loading"), left, top, error != null ? Theme.BAD : Theme.INK_2);
            return;
        }
        String go = L.t("wynnlabs.guide.go");
        int goW = Type.BODY.width(font, go) + 2 * Theme.SP_3;
        // wrap everything once per frame width; scroll to the current line the first time
        List<Object[]> rows = new ArrayList<>();             // {text, colour, at, isCurrent, isStage}
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            boolean cur = i == current;
            List<FormattedCharSequence> wrapped = Type.BODY.wrap(font, l.text(), right - left - goW - Theme.SP_4);
            for (int k = 0; k < wrapped.size(); k++) {
                rows.add(new Object[] {wrapped.get(k), l.stage() ? Theme.ACCENT : cur ? Theme.GOOD : l.colour(), k == 0 ? l.at() : null, cur, l.stage()});
            }
        }
        int lineH = Type.BODY.lineHeight;
        int fit = (bottom - top) / lineH;
        if (current >= 0 && first) {
            for (int i = 0; i < rows.size(); i++) {
                if ((boolean) rows.get(i)[3]) {
                    scroll = Math.max(0, i - 3);
                    break;
                }
            }
        }
        first = false;
        scroll = Math.min(scroll, Math.max(0, rows.size() - fit));
        bar.place(barX, top, fit * lineH, fit, rows.size(), scroll);
        bar.draw(g, mx, my);
        int ry = top;
        for (int i = scroll; i < rows.size() && i < scroll + fit; i++, ry += lineH) {
            Object[] r = rows.get(i);
            if ((boolean) r[3]) {
                g.fill(left - Theme.SP_2, ry, left - Theme.SP_2 + 2, ry + lineH - 2, Theme.GOOD);
            }
            g.drawString(font, Kit.ink((FormattedCharSequence) r[0]), left, ry, (int) r[1], false);
            int[] at = (int[]) r[2];
            if (at != null) {
                GuidePane.goButton(screen, g, mx, my, right - goW, ry, () -> screen.compass(at, true));
            }
        }
    }
}
