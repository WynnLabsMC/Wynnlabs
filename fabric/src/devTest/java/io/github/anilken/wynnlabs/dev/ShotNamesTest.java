package io.github.anilken.wynnlabs.dev;

import io.github.anilken.wynnlabs.game.Professions;
import io.github.anilken.wynnlabs.quests.ContentType;
import io.github.anilken.wynnlabs.quests.QuestAdvisor;
import io.github.anilken.wynnlabs.ui.ShotHands;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The screenshot tool against the menu as it is now, without the game: every private field it
 * reaches by reflection still exists, nothing in the tool reflects by name past those constants,
 * and the lists it plans views from (quest filters and kinds, settings pages, professions) are the
 * menu's own. A redesign that renames one fails here instead of stopping a run in the game.
 */
class ShotNamesTest {

    /** The tool's sources, found from the folder the tests run in (somewhere under the repository). */
    private static Path devSources() {
        for (Path d = Path.of("").toAbsolutePath(); d != null; d = d.getParent()) {
            Path p = d.resolve("common/src/dev/java");
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        throw new AssertionError("common/src/dev/java not found from " + Path.of("").toAbsolutePath());
    }

    @Test
    void everyReflectedFieldExists() {
        assertTrue(!ShotHands.refs().isEmpty());
        for (Peek.Ref r : ShotHands.refs()) {
            assertDoesNotThrow(r::field, r.owner().getSimpleName() + "." + r.name());
        }
    }

    @Test
    void everyReflectedNameIsAConstantTheTestChecks() throws IOException, IllegalAccessException {
        // the Ref constants of ShotHands are exactly the ones it lists
        List<Peek.Ref> constants = new ArrayList<>();
        for (Field f : ShotHands.class.getDeclaredFields()) {
            if (f.getType() == Peek.Ref.class && Modifier.isStatic(f.getModifiers())) {
                f.setAccessible(true);
                constants.add((Peek.Ref) f.get(null));
            }
        }
        assertEquals(constants.size(), ShotHands.refs().size(), "a Ref constant missing from ShotHands.refs()");
        assertTrue(ShotHands.refs().containsAll(constants));
        // in the tool's sources: a Ref made anywhere but ShotHands, or a field or method looked up by
        // name past Peek (DevTools' optional local tools aside), would go unchecked
        Pattern ref = Pattern.compile("new Peek\\.Ref\\(");
        Pattern byName = Pattern.compile("getDeclared(Field|Method)s?\\(|\\.getMethod\\(|\\.getField\\(");
        int refs = 0;
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(devSources())) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String name = f.getFileName().toString();
                String src = Files.readString(f);
                Matcher m = ref.matcher(src);
                while (m.find()) {
                    if (name.equals("ShotHands.java")) {
                        refs++;
                    } else {
                        bad.add(name + " makes a Peek.Ref");
                    }
                }
                if (!name.equals("Peek.java") && !name.equals("DevTools.java") && byName.matcher(src).find()) {
                    bad.add(name + " looks a member up by name");
                }
            }
        }
        assertEquals(List.of(), bad);
        assertEquals(constants.size(), refs, "a Peek.Ref in ShotHands that is not a static constant");
    }

    @Test
    void theRepresentativesAreTheMenus() {
        assertTrue(Arrays.asList(QuestAdvisor.FILTERS).contains(ShotRun.QUEST_FILTER));
        List<String> kinds = Arrays.stream(ContentType.values()).map(Enum::name).toList();
        assertTrue(kinds.containsAll(List.of(ShotRun.QUEST_KINDS)), kinds.toString());
        List<String> pages = Arrays.asList(ShotHands.settingPages());
        assertTrue(pages.containsAll(List.of(ShotRun.SETTING_PAGES)), pages.toString());
        assertTrue(Professions.ALL.contains(ShotRun.ROAD_GATHER));
    }

    @Test
    void everyPageStartsOnATabOfTheMenu() {
        List<String> tabs = List.of(ShotDriver.tabNames());
        for (String tab : ShotRun.PAGES.values()) {
            assertTrue(tabs.contains(tab), "no menu tab " + tab + " in " + tabs);
        }
        // every tab of the rail has a page
        for (int t : ShotDriver.railOrder()) {
            assertTrue(ShotRun.PAGES.containsValue(ShotDriver.tabName(t)), ShotDriver.tabName(t) + " has no page");
        }
        List<ShotRun.Shot> plan = ShotRun.plan(ShotDriver.tabNames(), "all");
        for (ShotRun.Shot shot : plan) {
            assertEquals(ShotRun.PAGES.get(shot.page()), ShotDriver.tabName(shot.tab()));
        }
    }
}
