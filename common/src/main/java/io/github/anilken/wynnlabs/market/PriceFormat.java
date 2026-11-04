package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.game.GameState;

/**
 * Prices written as text for {@link Prices}: emeralds in coins, with letters or the game's
 * glyphs ({@link Prices#coins}), and the road map's price to buy.
 */
final class PriceFormat {

    private PriceFormat() {}

    /**
     * The price of a piece to buy, for the road map: {@link Prices#text} while it is listed now; an
     * old price as what buying it is likely to cost ({@link Prices.Price#buyPrice}) marked as a past
     * price ("~5 EB · past price"). Never holds " (", so cutting at it keeps the mark.
     */
    static String buyText(String name) {
        Prices.Price p = GameState.current().price(name);
        if (p == null || !p.uncertain()) {
            return Prices.text(name, false);
        }
        Double v = p.buyPrice();
        return v == null ? null : Prices.past("~" + fmt(v));
    }

    /** A price written with letters: a number right before its coin ("2LE", "52EB", "3stx", "12.5LE"). */
    private static final java.util.regex.Pattern COIN = java.util.regex.Pattern.compile("(\\d)(LE|EB|E)\\b");

    /** Text with prices' coins written as the game's glyphs ("2¼² 52²½"), for the game's own font. */
    static String glyphs(String text) {
        if (!text.contains("E")) {
            return text;
        }
        java.util.regex.Matcher m = COIN.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String coin = m.group(2).equals("LE") ? GLYPHS[1] : m.group(2).equals("EB") ? GLYPHS[2] : GLYPHS[3];
            m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(m.group(1) + coin));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static final String[] GLYPHS = {"stx", "¼²", "²½", "²"};
    private static final String[] LETTERS = {"stx", "LE", "EB", "E"};

    /** Text with the coin glyphs written as letters ("2LE 52EB 30E"), for readers without the game's font. */
    static String letters(String text) {
        return text.replace(GLYPHS[1], LETTERS[1]).replace(GLYPHS[2], LETTERS[2]).replace(GLYPHS[3], LETTERS[3]);
    }

    /**
     * Emeralds as Wynnventory writes them: from a stack up, stacks and liquid emeralds to a tenth
     * ("3stx 12.5LE"); below, the two largest coins, the rest rounded ("12LE 34EB", "5EB 30E",
     * "40E"). 1 stx = 64 LE, 1 LE = 64 EB = 4096 E.
     */
    static String fmt(double emeralds) {
        long e = Math.round(emeralds);
        if (e < 0) {
            return "-" + fmt(-e);
        }
        if (e >= 262_144) {
            long rest = e % 262_144;
            String stx = e / 262_144 + "stx";
            return rest == 0 ? stx : stx + String.format(java.util.Locale.ROOT, " %.1f", rest / 4096.0)
                    + LETTERS[1];
        }
        return exact(e >= 4096 ? Math.round(e / 64.0) * 64 : e);
    }

    /**
     * Emeralds in one coin, for the corner of an item slot: "40E", "14EB", "2.3LE", "1.2stx" (a
     * tenth below ten, whole above). {@link #fmt} has the rest.
     */
    static String slot(double emeralds) {
        long e = Math.round(emeralds);
        String[] names = Prices.coins ? GLYPHS : LETTERS;
        long[] sizes = {262_144, 4096, 64, 1};
        for (int k = 0; k < sizes.length - 1; k++) {
            if (e >= sizes[k]) {
                double v = (double) e / sizes[k];
                return (v < 10 ? String.format(java.util.Locale.ROOT, "%.1f", v) : String.valueOf(Math.round(v))) + names[k];
            }
        }
        return e + names[3];
    }

    /** Emeralds to the emerald, every coin that is not 0: "1stx 12LE 34EB 1E", "40E", "0E". */
    static String exact(long emeralds) {
        if (emeralds < 0) {
            return "-" + exact(-emeralds);
        }
        long[] sizes = {262_144, 4096, 64, 1};
        String[] names = LETTERS;
        StringBuilder b = new StringBuilder();
        long left = emeralds;
        for (int k = 0; k < sizes.length; k++) {
            long n = left / sizes[k];
            left %= sizes[k];
            if (n > 0) {
                b.append(b.length() > 0 ? " " : "").append(n).append(names[k]);
            }
        }
        return b.length() == 0 ? "0" + names[3] : b.toString();
    }
}
