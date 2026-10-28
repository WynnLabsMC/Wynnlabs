package io.github.anilken.wynnlabs.core;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.game.Turkish;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** L without the game: the bundled language files, formatted like I18n does (Turkish with -PwithTurkish). */
class LangTest {

    private static void language(int pick) {
        JsonObject s = new JsonObject();
        s.addProperty("langOverride", pick);
        Settings.use(s);
    }

    @AfterEach
    void reset() {
        Settings.use(new JsonObject());
    }

    @Test
    void englishWithArguments() {
        language(1);
        assertEquals(" (build +12%)", L.t("wynnlabs.auto.build_gain", 12));
        assertEquals("Equippable at Lv. 44", L.t("wynnlabs.auto.wear_at_level", 44));
    }

    @Test
    @EnabledIfSystemProperty(named = Turkish.PROPERTY, matches = "true")
    void turkishWithArguments() {
        language(2);
        assertEquals(" (build +%12)", L.t("wynnlabs.auto.build_gain", 12));
        assertEquals("Lv. 44'te giyebilirsin", L.t("wynnlabs.auto.wear_at_level", 44));
    }

    @Test
    void followTheGameIsEnglishWithoutOne() {
        language(0);
        assertEquals("5 min", L.t("wynnlabs.price.minutes", 5));
        assertEquals("5 min", L.c("wynnlabs.price.minutes", 5).getString());
    }

    @Test
    void englishCountsPickOneOrOther() {
        language(1);
        assertEquals("1 day ago", L.n("wynnlabs.screen.ago_days", 1));
        assertEquals("3 days ago", L.n("wynnlabs.screen.ago_days", 3));
        assertEquals("0 deaths", L.n("wynnlabs.deaths.tile_count", 0));
        assertEquals("Hunt over: Wybel Fluff (1 kill)", L.n("wynnlabs.coach.hunt_ended", 1, "Wybel Fluff", 1));
        assertEquals("Area Lv. 34 · You: Lv. 60", L.t("wynnlabs.deaths.chip_zone", 34, 60));
    }

    @Test
    @EnabledIfSystemProperty(named = Turkish.PROPERTY, matches = "true")
    void turkishCountsHaveOneForm() {
        language(2);
        assertEquals("1 gün önce", L.n("wynnlabs.screen.ago_days", 1));
        assertEquals("3 gün önce", L.n("wynnlabs.screen.ago_days", 3));
    }

    @Test
    void unknownKeyStaysAKey() {
        language(1);
        assertEquals("wynnlabs.no.such.key", L.t("wynnlabs.no.such.key"));
    }

    @Test
    void capUpperCasesOnlyTheFirstLetter() {
        assertEquals("Dropped by mobs", L.cap("dropped by mobs", "en_us"));
        assertEquals("• Already started", L.cap("• already started", "en_us"));
        assertEquals("(Slot empty)", L.cap("(slot empty)", "en_us"));
        assertEquals("Lootrun", L.cap("Lootrun", "en_us"));
        assertEquals("3 days ago", L.cap("3 days ago", "en_us"));
        assertEquals("+10 Strength", L.cap("+10 Strength", "en_us"));
        assertEquals("", L.cap("", "en_us"));
        assertEquals("Initial", L.cap("initial", "en_us"));
    }

    @Test
    @EnabledIfSystemProperty(named = Turkish.PROPERTY, matches = "true")
    void capIsTurkishInTurkish() {
        assertEquals("İlk raid'den sonra", L.cap("ilk raid'den sonra", "tr_tr"));
        assertEquals("Ilık", L.cap("ılık", "tr_tr"));
        assertEquals("Mob'lardan düşer", L.cap("mob'lardan düşer", "tr_tr"));
        assertEquals("Initial", L.cap("initial", "en_us"));
        language(2);
        assertEquals("İçerik", L.cap("içerik"));
    }

    @Test
    void percentSignFollowsTheNumberInEnglish() {
        assertEquals("15%", L.pct(15, "en_us"));
        assertEquals("+10107%", L.pct("+10107", "en_us"));
        assertEquals("−3%", L.pct("−3", "en_us"));
        language(1);
        assertEquals("+12%", L.pct("+12"));
        assertEquals("+12/5s", L.unit("+12", "/5s"));
        assertEquals("−4%", L.unit("−4", "%"));
        assertEquals("Walk Speed: +5% → +12%", L.t("wynnlabs.road.walk_speed", L.pct("+5"), L.pct("+12")));
    }

    @Test
    void percentSignLeadsTheNumberInTurkish() {
        assertEquals("%15", L.pct(15, "tr_tr"));
        assertEquals("+%10107", L.pct("+10107", "tr_tr"));
        assertEquals("−%3", L.pct("−3", "tr_tr"));
        assertEquals("-%3", L.pct("-3", "tr_tr"));
        assertEquals("%", L.pct("", "tr_tr"));
    }

    @Test
    @EnabledIfSystemProperty(named = Turkish.PROPERTY, matches = "true")
    void turkishPercentLines() {
        language(2);
        assertEquals("−%4", L.unit("−4", "%"));
        assertEquals("Walk Speed: +%5 → +%12", L.t("wynnlabs.road.walk_speed", L.pct("+5"), L.pct("+12")));
    }
}
