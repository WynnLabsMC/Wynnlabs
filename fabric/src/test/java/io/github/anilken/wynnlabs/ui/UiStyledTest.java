package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wynncraft's lore spans keep their font through our wrap and fit (a stat bullet must not become a rank badge). */
class UiStyledTest {

    private static final String LORE = "[{\"text\":\"\\uE01E\",\"font\":\"wynncraft:common\"},"
            + "{\"text\":\" Effect: +20% Damage Bonus to Summons and more words here\",\"color\":\"#AAAAAA\"}]";

    private static FontDescription fontOf(Component c, String text) {
        for (UiStyled.Run r : UiStyled.runs(c)) {
            if (r.text().contains(text)) {
                return r.style().getFont();
            }
        }
        return null;
    }

    @Test
    void aSpansFontSurvivesTheLineTheWrapAndTheFit() {
        JsonArray line = JsonParser.parseString(LORE).getAsJsonArray();
        Component c = UiStyled.line(line);
        FontDescription wynn = fontOf(c, "\uE01E");
        assertTrue(wynn instanceof FontDescription.Resource r && r.id().toString().equals("wynncraft:common"), String.valueOf(wynn));
        UiStyled.Width chars = (t, st) -> t.length();
        List<? extends Component> lines = UiStyled.wrap(c, 20, chars);
        assertTrue(lines.size() > 1);
        assertEquals(wynn, fontOf(lines.get(0), "\uE01E"));
        for (Component l : lines) {
            assertTrue(l.getString().length() <= 20, l.getString());
        }
        Component cut = UiStyled.fit(c, 12, chars);
        assertEquals(wynn, fontOf(cut, "\uE01E"));
        assertTrue(cut.getString().endsWith("…"));
        assertTrue(cut.getString().length() <= 12);
        // the plain words keep their colour, and the glyph is not in the default font
        assertTrue(fontOf(lines.get(0), "Effect") == null || fontOf(lines.get(0), "Effect").equals(Style.EMPTY.getFont()));
    }
}
