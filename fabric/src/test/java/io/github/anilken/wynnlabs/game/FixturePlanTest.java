package io.github.anilken.wynnlabs.game;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;

/** The build plan, the road map's steps and the shopping list on the recorded fixtures. */
class FixturePlanTest {

    @Test
    void buildPlan() throws Exception {
        FixtureRun.each("build plan", List.of("en"), (fx, dir, lang) ->
                Expected.check(dir, "build.json", Build.json(), Expected::json));
    }

    @Test
    void roadmap() throws Exception {
        roadmap("en");
    }

    @Test
    @EnabledIfSystemProperty(named = Turkish.PROPERTY, matches = "true")
    void roadmapTurkish() throws Exception {
        roadmap("tr");
    }

    private static void roadmap(String language) throws Exception {
        FixtureRun.each("road map (" + language + ")", List.of(language), (fx, dir, lang) ->
                Expected.check(dir, "roadmap." + lang + ".json", Roadmap.json(), Expected::json));
    }

    @Test
    void shoppingList() throws Exception {
        FixtureRun.each("shopping list", List.of("en"), (fx, dir, lang) ->
                Expected.check(dir, "shopping.json", FixtureRun.shopping(), Expected::json));
    }
}
