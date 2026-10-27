package io.github.anilken.wynnlabs.game;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

/** Every item's verdict and note on the recorded fixtures, in English (Turkish with -PwithTurkish). */
class FixtureVerdictTest {

    private static void run(String language) throws Exception {
        FixtureRun.each("verdicts (" + language + ")", List.of(language), (fx, dir, lang) ->
                Expected.check(dir, "verdicts." + lang + ".json", FixtureRun.verdicts(fx), Expected::verdicts));
    }

    @Test
    void english() throws Exception {
        run("en");
    }

    @Test
    @EnabledIfSystemProperty(named = Turkish.PROPERTY, matches = "true")
    void turkish() throws Exception {
        run("tr");
    }
}
