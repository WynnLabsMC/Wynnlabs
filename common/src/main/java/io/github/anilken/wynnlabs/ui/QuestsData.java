package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.quests.QuestBook;
import io.github.anilken.wynnlabs.quests.QuestTrail;
import io.github.anilken.wynnlabs.quests.QuestWiki;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** What the quests tab's parts read about a quest: its book entry, the stage you are on, its places. */
final class QuestsData {

    private QuestsData() {}

    static QuestBook.Entry entry(String name) {
        return QuestsCache.entry(name);
    }

    /** The tracked quest's name, or null. */
    static String tracked() {
        try {
            return Wyn.tracking() ? Wyn.trackedName() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** The task line the game shows for this quest (the tracked one's, else the book's). */
    static String task(String name) {
        try {
            if (name.equals(tracked())) {
                return Wyn.trackedTask();
            }
        } catch (Throwable ignored) {
        }
        QuestBook.Entry q = entry(name);
        return q != null && q.task() != null ? q.task() : "";
    }

    /** The stage you are on (0-based), or -1 when the quest is not started. */
    static int stage(String name, QuestWiki.Info info) {
        if (info == null) {
            return -1;
        }
        return QuestsCache.stage(name, info, task(name), () -> stageNow(name, info));
    }

    private static int stageNow(String name, QuestWiki.Info info) {
        if (info == null) {
            return -1;
        }
        QuestBook.Entry q = entry(name);
        boolean on = name.equals(tracked()) || q != null && "STARTED".equals(q.status());
        return on ? QuestWiki.currentStage(info, task(name)) : -1;
    }

    /** "1.1", "5.2": each place's stage and its order in it. */
    static List<String> pointNumbers(List<QuestWiki.Place> places) {
        List<String> out = new ArrayList<>();
        int stage = -1;
        int k = 0;
        for (QuestWiki.Place p : places) {
            k = p.stage() == stage ? k + 1 : 1;
            stage = p.stage();
            out.add((p.stage() + 1) + "." + k);
        }
        return out;
    }

    /** The quest's places for the map: passed, on (or the start), next. */
    static List<QuestMap.Place> mapPlaces(String name, QuestWiki.Info info, boolean all) {
        if (info == null) {
            return new ArrayList<>();
        }
        return QuestsCache.places(name, info, all, stage(name, info), () -> mapPlacesNow(name, info, all));
    }

    private static List<QuestMap.Place> mapPlacesNow(String name, QuestWiki.Info info, boolean all) {
        List<QuestMap.Place> out = new ArrayList<>();
        if (info == null) {
            return out;
        }
        int cur = stage(name, info);
        List<QuestWiki.Place> places = QuestTrail.places(name, info, 0);
        List<String> nums = pointNumbers(places);
        for (int i = 0; i < places.size(); i++) {
            int st = places.get(i).stage();
            int state = cur < 0 ? (st == 0 ? QuestMapLayout.START : QuestMapLayout.NEXT)
                    : st < cur ? QuestMapLayout.DONE : st == cur ? QuestMapLayout.NOW : QuestMapLayout.NEXT;
            if (all || state != QuestMapLayout.DONE) {
                out.add(new QuestMap.Place(nums.get(i), places.get(i).at(), L.cap(places.get(i).what()), state));
            }
        }
        return out;
    }

    /** "~2.2 km" to a place, or null without a player. */
    static String distance(int[] at) {
        var p = Minecraft.getInstance().player;
        if (p == null || at == null) {
            return null;
        }
        double d = Math.hypot(p.getX() - at[0], p.getZ() - at[2]);
        return d >= 1000 ? String.format(Locale.ROOT, "%.1f km", d / 1000) : Math.round(d) + " m";
    }
}
