package io.github.anilken.wynnlabs.build.roadmap;

import com.google.gson.JsonObject;

import io.github.anilken.wynnlabs.build.Tuning;
import io.github.anilken.wynnlabs.game.FixtureState;
import io.github.anilken.wynnlabs.game.Fixtures;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The recorded Shaman's plain road map, built once per test JVM: many tests only read it, and
 * each build of it takes seconds. Two of them, made the two ways the game makes it: the J menu's
 * {@link Roadmap#build} and the engine's {@link Roadmap#compute} with nothing chosen and no
 * tuning (the tests that say both are the same compare these two). The same inputs always give
 * the same map (RoadmapGainTest.sameInputsSamePicks builds it anew to check), so a map built
 * for an earlier test is the one a later test would build.
 */
public final class SharedRoadmaps {

    private static List<Roadmap.Step> built;
    private static Roadmap.Computed computed;

    private SharedRoadmaps() {}

    public static Path shamanDir() {
        Path dir = Fixtures.root().resolve("shaman-44");
        assumeTrue(Files.exists(dir.resolve("state.json")), "no shaman-44 fixture");
        return dir;
    }

    /**
     * Installs the recorded Shaman in English (as {@code FixtureState.load(..).install("en")})
     * and returns the map {@link Roadmap#build} makes for it; the tests read it and never change it.
     */
    public static synchronized List<Roadmap.Step> shaman() throws Exception {
        FixtureState.load(shamanDir()).install("en");
        if (built == null) {
            built = List.copyOf(Roadmap.build());
        }
        return built;
    }

    /** {@link #shaman}'s map as {@link Roadmap#json} gives it (the local API's /roadmap and the fixture's roadmap.en.json). */
    public static JsonObject shamanJson() throws Exception {
        return RoadmapJson.steps(shaman());
    }

    /**
     * Installs the recorded Shaman in English and returns the engine's map for it with nothing
     * chosen, no tuning and every milestone ({@code Roadmap.compute}), worked out once.
     */
    public static synchronized Roadmap.Computed shamanComputed() throws Exception {
        FixtureState.load(shamanDir()).install("en");
        if (computed == null) {
            computed = Roadmap.compute(new Roadmap.Inputs(List.of(), Tuning.Choice.PLAIN, false, 0), null);
        }
        return computed;
    }
}
