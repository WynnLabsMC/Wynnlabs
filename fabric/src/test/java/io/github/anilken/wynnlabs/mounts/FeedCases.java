package io.github.anilken.wynnlabs.mounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** The 72 reference cases (mounts/feed72.json) and how a plan is compared with the reference output. */
final class FeedCases {
    record Case(String name, MountStats stats, FeedGoal goal, FeedOptions options, boolean sameSteps,
                JsonObject expected) {
    }

    private FeedCases() {
    }

    static List<Case> load() {
        try (InputStream in = FeedCases.class.getResourceAsStream("/mounts/feed72.json")) {
            JsonArray a = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonArray();
            List<Case> out = new ArrayList<>();
            for (JsonElement e : a) {
                out.add(parse(e.getAsJsonObject()));
            }
            return out;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Case parse(JsonObject c) {
        JsonObject o = c.getAsJsonObject("options");
        JsonObject st = o.getAsJsonObject("stats");
        int[] lv = new int[8], li = new int[8], mx = new int[8];
        for (MountStat s : MountStat.values()) {
            JsonObject x = st.getAsJsonObject(s.display());
            lv[s.ordinal()] = x.get("level").getAsInt();
            li[s.ordinal()] = x.get("limit").getAsInt();
            mx[s.ordinal()] = x.get("max").getAsInt();
        }
        FeedGoal goal;
        if ("custom_goal".equals(o.get("goalMode").getAsString())) {
            Map<MountStat, Integer> t = new EnumMap<>(MountStat.class);
            JsonObject tl = o.has("targetLimits") ? o.getAsJsonObject("targetLimits") : new JsonObject();
            if (o.has("targetStats")) {
                for (JsonElement s : o.getAsJsonArray("targetStats")) {
                    String n = s.getAsString();
                    t.put(MountStat.parse(n), tl.has(n) ? tl.get(n).getAsInt() : null);
                }
            }
            int min = o.has("requiredAverageLimit") ? o.get("requiredAverageLimit").getAsInt()
                    : o.has("minAvgLimit") ? o.get("minAvgLimit").getAsInt() : 0;
            goal = FeedGoal.custom(t, min);
        } else {
            goal = FeedGoal.full();
        }
        FeedOptions opt = FeedOptions.defaults()
                .withTrainSecPerLevel(o.has("trainSecPerLevel") ? o.get("trainSecPerLevel").getAsInt() : 60)
                .withTrainFocus(o.has("trainFocus") ? MountStat.parse(o.get("trainFocus").getAsString()) : null)
                .withTrainCap(o.has("trainCap") ? o.get("trainCap").getAsInt() : Integer.MAX_VALUE)
                .withQueueSwaps(o.has("allowQueueSwaps") && o.get("allowQueueSwaps").getAsBoolean())
                .withMaxTimeSec(o.has("maxTimeSec") ? o.get("maxTimeSec").getAsLong() : Long.MAX_VALUE)
                .withBudget(Long.MAX_VALUE, 600_000);
        return new Case(c.get("name").getAsString(), MountStats.of(lv, li, mx), goal, opt,
                c.get("sameStepsAsReference").getAsBoolean(), c.getAsJsonObject("expected"));
    }

    /** The plan's steps in the reference output's JSON shape. */
    static JsonArray actions(FeedPlan plan) {
        JsonArray a = new JsonArray();
        for (FeedStep s : plan.steps()) {
            JsonObject x = new JsonObject();
            if (s.isFeed()) {
                x.addProperty("kind", "FEED");
                x.addProperty("material", s.material().kind().display());
                x.addProperty("level", s.material().level());
                x.addProperty("durationSec", s.durationSec());
                if (s.swap()) {
                    x.addProperty("mustUseLowerLevel", true);
                    x.addProperty("swapStartLevel", 1);
                }
            } else {
                x.addProperty("kind", "TRAIN");
                x.addProperty("stat", s.stat().display());
                x.addProperty("toLevel", s.toLevel());
                x.addProperty("durationSec", s.durationSec());
                x.addProperty("coveredDurationSec", s.coveredSec());
            }
            a.add(x);
        }
        return a;
    }

    /** Differences from the reference (empty = match): totals always; every step too. */
    static List<String> diff(Case c, FeedPlan plan) {
        List<String> d = new ArrayList<>();
        JsonObject e = c.expected();
        check(d, "goalReached", e.get("goalReached").getAsBoolean(), plan.goalReached());
        check(d, "totalTimeSec", e.get("totalTimeSec").getAsLong(), plan.totalTimeSec());
        check(d, "feedTimeSec", e.get("totalFeedTimeSec").getAsLong(), plan.feedTimeSec());
        check(d, "trainingTimeSec", e.get("totalTrainingTimeSec").getAsLong(), plan.trainingTimeSec());
        check(d, "trainingInFullSec", e.get("trainingInFullSec").getAsLong(), plan.fullTrainingSec());
        check(d, "feeds", e.get("feeds").getAsInt(), plan.feeds());
        JsonArray want = e.getAsJsonArray("actions"), got = actions(plan);
        if (!canon(want).equals(canon(got))) {
            d.add("steps differ:\n  want " + canon(want) + "\n  got  " + canon(got));
        }
        return d;
    }

    private static String canon(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) {
            JsonObject o = e.getAsJsonObject();
            List<String> keys = new ArrayList<>(o.keySet());
            keys.sort(null);
            StringBuilder b = new StringBuilder("{");
            for (String k : keys) {
                b.append(k).append('=').append(o.get(k).getAsString()).append(' ');
            }
            out.add(b.append('}').toString());
        }
        return String.join(",", out);
    }

    private static void check(List<String> d, String what, Object want, Object got) {
        if (!want.equals(got)) {
            d.add(what + ": want " + want + ", got " + got);
        }
    }
}
