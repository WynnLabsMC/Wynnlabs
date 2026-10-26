package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Wynncraft's lore text kept styled: each span keeps its own font (the server pack's private-use
 * glyphs, the stat bullets and effect icons, live in a wynncraft: font; drawn in the default font
 * the same code points are rank badges), its colour and its weight. Lines are wrapped and cut
 * span by span, so nothing is flattened to a plain string and back.
 */
final class UiStyled {

    /** How wide a piece of text is in its style (a font's width; a test counts characters). */
    interface Width {
        int of(String text, Style style);
    }

    /** One styled piece of a line. */
    record Run(String text, Style style) {}

    private UiStyled() {
    }

    /** A line of component JSON (Wynntils' ability lore) as a component, its spans' fonts, colours and weights kept. */
    static MutableComponent line(JsonArray parts) {
        MutableComponent out = Component.empty();
        for (JsonElement e : parts) {
            if (e.isJsonPrimitive()) {
                out.append(Component.literal(e.getAsString()));
                continue;
            }
            if (!e.isJsonObject() || !e.getAsJsonObject().has("text")) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            Style st = Style.EMPTY;
            if (o.has("bold")) {
                st = st.withBold(true);
            }
            if (o.has("italic")) {
                st = st.withItalic(true);
            }
            if (o.has("underline")) {
                st = st.withUnderlined(true);
            }
            if (o.has("strikethrough")) {
                st = st.withStrikethrough(true);
            }
            if (o.has("font")) {
                Identifier id = Identifier.tryParse(o.get("font").getAsString());
                if (id != null) {
                    st = st.withFont(new FontDescription.Resource(id));
                }
            }
            if (o.has("color")) {
                TextColor c = TextColor.parseColor(o.get("color").getAsString()).result().orElse(null);
                if (c != null) {
                    st = st.withColor(c);
                }
            }
            out.append(Component.literal(o.get("text").getAsString()).withStyle(st));
        }
        return out;
    }

    /** The component's styled pieces, in order (empty pieces left out). */
    static List<Run> runs(Component c) {
        List<Run> out = new ArrayList<>();
        c.visit((style, text) -> {
            if (!text.isEmpty()) {
                out.add(new Run(text, style));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** The pieces back as one component. */
    static MutableComponent join(List<Run> runs) {
        MutableComponent out = Component.empty();
        for (Run r : runs) {
            out.append(Component.literal(r.text()).withStyle(r.style()));
        }
        return out;
    }

    /** Wrapped at spaces into lines at most {@code max} wide; every piece keeps its style. */
    static List<MutableComponent> wrap(Component c, int max, Width width) {
        List<MutableComponent> lines = new ArrayList<>();
        List<Run> line = new ArrayList<>();
        int used = 0;
        for (Run r : runs(c)) {
            // words with the space before them, so a line breaks at a space and loses it
            String t = r.text();
            int i = 0;
            while (i < t.length()) {
                int j = t.indexOf(' ', i + 1);
                String word = t.substring(i, j < 0 ? t.length() : j);
                int ww = width.of(word, r.style());
                if (used > 0 && used + ww > max) {
                    lines.add(join(line));
                    line = new ArrayList<>();
                    word = word.stripLeading();
                    ww = width.of(word, r.style());
                    used = 0;
                }
                add(line, word, r.style());
                used += ww;
                i = j < 0 ? t.length() : j;
            }
        }
        if (!line.isEmpty() || lines.isEmpty()) {
            lines.add(join(line));
        }
        return lines;
    }

    /** Cut to {@code max} wide with "…" at the end when it does not fit; the pieces keep their styles. */
    static MutableComponent fit(Component c, int max, Width width) {
        List<Run> runs = runs(c);
        int total = 0;
        for (Run r : runs) {
            total += width.of(r.text(), r.style());
        }
        if (total <= max) {
            return join(runs);
        }
        int room = max - width.of("…", Style.EMPTY);
        List<Run> out = new ArrayList<>();
        int used = 0;
        for (Run r : runs) {
            String t = r.text();
            int k = 0;
            while (k < t.length() && used + width.of(t.substring(k, k + 1), r.style()) <= room) {
                used += width.of(t.substring(k, k + 1), r.style());
                k++;
            }
            if (k > 0) {
                out.add(new Run(t.substring(0, k), r.style()));
            }
            if (k < t.length()) {
                break;
            }
        }
        out.add(new Run("…", Style.EMPTY));
        return join(out);
    }

    /** Appends a word to the line, merged into the last piece when it has the same style. */
    private static void add(List<Run> line, String word, Style style) {
        if (word.isEmpty()) {
            return;
        }
        if (!line.isEmpty() && line.get(line.size() - 1).style().equals(style)) {
            Run last = line.remove(line.size() - 1);
            line.add(new Run(last.text() + word, style));
        } else {
            line.add(new Run(word, style));
        }
    }
}
