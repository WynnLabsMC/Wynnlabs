package io.github.anilken.wynnlabs.guild;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.core.Workers;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * Guilds from Wynncraft's own API ({@code /v3/guild/prefix/<prefix>}): level, members, wars, and
 * who is online on which world. Asked only while the guild map is open, one guild at a time on a
 * low-priority worker, each guild at most once a minute (the one you picked) or once in five
 * (the list's online counts), and never more than 20 asks a minute; the API allows 50.
 */
public final class GuildApi {

    private GuildApi() {}

    /** A member online now: name, rank ("Chief") and world ("NA2", null when hidden). */
    public record Member(String name, String rank, String world) {}

    /** A guild as the API answered, {@code at} when. */
    public record Info(String name, String prefix, int level, int xpPercent, int territories, int wars, int members,
            int online, String created, List<Member> onlineMembers, long at) {}

    private static final String[] RANKS = {"owner", "chief", "strategist", "captain", "recruiter", "recruit"};
    private static final long PICKED_EVERY = 60_000L;
    private static final long LIST_EVERY = 5 * 60_000L;
    private static final int PER_MINUTE = 20;

    private static final Map<String, Info> INFO = new ConcurrentHashMap<>();
    private static final Map<String, Long> ASKED = new ConcurrentHashMap<>();
    private static final Set<String> QUEUED = ConcurrentHashMap.newKeySet();
    private static final ExecutorService WORKER = Workers.singleLow("guild-api");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final long[] SENT = new long[PER_MINUTE];
    private static int sentAt;
    /** Until when the API told us to wait (a 429). */
    private static volatile long quietUntil;

    /** The picked guild: what is known, asked again when a minute old. */
    public static Info picked(String prefix) {
        return get(prefix, PICKED_EVERY);
    }

    /** True while this guild is being asked for. */
    public static boolean asking(String prefix) {
        return prefix != null && QUEUED.contains(prefix);
    }

    /** When the newest answer of any guild came (0: none yet), for the list's "updated" line. */
    public static long newest() {
        long at = 0;
        for (Info i : INFO.values()) {
            at = Math.max(at, i.at());
        }
        return at;
    }

    /** A guild in the list: what is known, asked again when five minutes old. */
    public static Info listed(String prefix) {
        return get(prefix, LIST_EVERY);
    }

    private static Info get(String prefix, long every) {
        if (prefix == null || prefix.isBlank()) {
            return null;
        }
        Info have = INFO.get(prefix);
        if (!Settings.on("guildApi")) {
            return have;
        }
        long now = System.currentTimeMillis();
        Long asked = ASKED.get(prefix);
        if ((asked == null || now - asked >= every) && now >= quietUntil && QUEUED.add(prefix)) {
            ASKED.put(prefix, now);
            WORKER.execute(() -> {
                try {
                    if (!slot()) {
                        ASKED.remove(prefix);           // over this minute's share: asked again later
                        return;
                    }
                    Info got = fetch(prefix);
                    if (got != null) {
                        INFO.put(prefix, got);
                    }
                } catch (Exception e) {
                    // offline or a changed answer: what we had stays
                } finally {
                    QUEUED.remove(prefix);
                }
            });
        }
        return have;
    }

    /** True when one more ask fits in the last minute's share (and counts it). */
    private static synchronized boolean slot() {
        long now = System.currentTimeMillis();
        if (now - SENT[sentAt] < 60_000L) {
            return false;
        }
        SENT[sentAt] = now;
        sentAt = (sentAt + 1) % SENT.length;
        return true;
    }

    private static Info fetch(String prefix) throws Exception {
        String url = "https://api.wynncraft.com/v3/guild/prefix/" + URLEncoder.encode(prefix, StandardCharsets.UTF_8).replace("+", "%20");
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
                .header("User-Agent", "WynnLabs (Minecraft client mod; guild map; https://github.com/WynnLabsMC/Wynnlabs)")
                .GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 429) {
            quietUntil = System.currentTimeMillis() + 60_000L;
            return null;
        }
        if (r.statusCode() != 200) {
            return null;
        }
        return parse(JsonParser.parseString(r.body()).getAsJsonObject(), System.currentTimeMillis());
    }

    /** A guild from the API's answer. */
    static Info parse(JsonObject o, long at) {
        List<Member> online = new ArrayList<>();
        int members = 0;
        JsonObject m = o.has("members") && o.get("members").isJsonObject() ? o.getAsJsonObject("members") : new JsonObject();
        for (String rank : RANKS) {
            if (!m.has(rank) || !m.get(rank).isJsonObject()) {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : m.getAsJsonObject(rank).entrySet()) {
                members++;
                if (!e.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject p = e.getValue().getAsJsonObject();
                if (p.has("online") && p.get("online").isJsonPrimitive() && p.get("online").getAsBoolean()) {
                    String world = p.has("server") && p.get("server").isJsonPrimitive() ? p.get("server").getAsString() : null;
                    online.add(new Member(e.getKey(), Character.toUpperCase(rank.charAt(0)) + rank.substring(1), world));
                }
            }
        }
        if (m.has("total") && m.get("total").isJsonPrimitive()) {
            members = m.get("total").getAsInt();
        }
        return new Info(str(o, "name"), str(o, "prefix"), num(o, "level"), num(o, "xpPercent"), num(o, "territories"),
                num(o, "wars"), members, Math.max(num(o, "online"), online.size()), str(o, "created"), List.copyOf(online), at);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    private static int num(JsonObject o, String k) {
        try {
            return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }
}
