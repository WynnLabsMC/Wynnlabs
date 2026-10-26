package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.quests.GuideImages;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A quest's wiki pictures by stage, for the quests tab's guide pane: read once in the background
 * from the page the guide keeps on disk (fetched from the wiki only when the guides are on), so the
 * tab never waits on it. Empty until read, and when the guides are off.
 */
final class QuestPictures {

    private static final Map<String, Map<String, List<GuideImages.Image>>> READ = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> ASKED = new ConcurrentHashMap<>();
    private static final ExecutorService ONE = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "WynnLabs quest pictures");
        t.setDaemon(true);
        return t;
    });

    private QuestPictures() {}

    /** The pictures under the stage heading {@code stage} ("Stage 5") of the quest's page. */
    static List<GuideImages.Image> of(String quest, String stage) {
        Map<String, List<GuideImages.Image>> m = READ.get(quest);
        if (m == null) {
            if (QuestWiki.on() && ASKED.putIfAbsent(quest, true) == null) {
                ONE.submit(() -> {
                    try {
                        String page = QuestWiki.page(quest);
                        READ.put(quest, page == null ? Map.of() : GuideImages.byStage(quest, page));
                    } catch (Exception e) {
                        READ.put(quest, Map.of());
                    }
                });
            }
            return List.of();
        }
        return m.getOrDefault(stage, List.of());
    }
}
