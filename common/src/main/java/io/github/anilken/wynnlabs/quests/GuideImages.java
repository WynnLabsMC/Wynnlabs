package io.github.anilken.wynnlabs.quests;

import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The pictures a quest's wiki page puts in its stages that help with the stage: a puzzle's
 * solution, a map, a place to find. NPC portraits (beside dialogue), mob and item icons are left
 * out. Each is fetched once from the wiki as a 360px PNG and kept on disk next to the pages.
 */
public final class GuideImages {

    /** A picture: its file on the wiki and the caption the page gives it ("" when none). */
    public record Image(String file, String caption) {}

    private static final Pattern FILE = Pattern.compile("\\[\\[File:([^|\\]]+)((?:\\|[^\\]]*)?)]]", Pattern.CASE_INSENSITIVE);
    private static final Pattern GALLERY_LINE = Pattern.compile("^\\s*(?:File:)?([^|]+\\.(?:png|jpe?g|gif|webp))\\s*(\\|.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern HEADING = Pattern.compile("^(=+)\\s*(.*?)\\s*\\1\\s*$");
    private static final Pattern STAGE = Pattern.compile("^(?:Stage|Part)\\s*\\d", Pattern.CASE_INSENSITIVE);
    private static final Pattern PX = Pattern.compile("(\\d+)(?:x\\d+)?px");
    private static final Pattern FRAMED = Pattern.compile("\\|\\s*(?:thumb|thumbnail|frame|frameless)\\s*(?=\\||$)");
    /** Names that tell a picture of the way: a puzzle, a map, a room; as a word ("FadedMap", not "MushroomMite"). */
    private static final Pattern USEFUL = Pattern.compile("(?:^|(?<=[^A-Za-z])|(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z]))"
            + "(?i:puzzle|map|location|solution|route|path|layout|maze|lever|stage|room|floor|code|grave|parkour)(?![a-z])");
    private static final Pattern OPTION = Pattern.compile("(?i)(?:thumb|thumbnail|frame|frameless|left|right|center|none|upright.*|border|\\d*x?\\d+px|(?:link|alt|class)=.*)");

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final String AGENT = "WynnLabs (Minecraft client mod; quest guide; https://github.com/WynnLabsMC/Wynnlabs)";

    private GuideImages() {}

    /** A page's useful pictures by the stage heading they sit under ("Stage 5"), each once per page. */
    public static Map<String, List<Image>> byStage(String quest, String wikitext) {
        Map<String, List<Image>> out = new LinkedHashMap<>();
        List<String> seen = new ArrayList<>();
        String stage = null;
        boolean gallery = false;
        for (String line : wikitext.split("\n")) {
            Matcher h = HEADING.matcher(line);
            if (h.matches()) {
                if (STAGE.matcher(h.group(2)).find()) {
                    stage = h.group(2);
                } else if (h.group(1).length() <= 2) {
                    stage = null;                       // Rewards, Trivia, …: past the stages
                }
                continue;
            }
            String low = line.toLowerCase(Locale.ROOT);
            if (low.contains("<gallery")) {
                gallery = true;
                continue;
            }
            if (low.contains("</gallery")) {
                gallery = false;
                continue;
            }
            if (stage == null) {
                continue;
            }
            if (gallery) {
                Matcher g = GALLERY_LINE.matcher(line);
                if (g.matches()) {
                    add(out, seen, stage, quest, g.group(1).trim(), "|thumb" + (g.group(2) == null ? "" : g.group(2)));
                }
                continue;
            }
            Matcher m = FILE.matcher(line);
            while (m.find()) {
                add(out, seen, stage, quest, m.group(1).trim(), m.group(2));
            }
        }
        return out;
    }

    private static void add(Map<String, List<Image>> out, List<String> seen, String stage, String quest, String file, String opts) {
        if (!useful(quest, file, opts) || seen.contains(file)) {
            return;
        }
        seen.add(file);
        String caption = "";
        for (String part : opts.split("\\|")) {
            String p = part.trim();
            if (!p.isEmpty() && !OPTION.matcher(p).matches()) {
                caption = QuestWiki.tidy(QuestWiki.clean(p));
            }
        }
        out.computeIfAbsent(stage, k -> new ArrayList<>()).add(new Image(file, caption));
    }

    /**
     * A picture of the way, not of someone: named after a puzzle, map or place, or a framed or
     * quest-named screenshot over 100px. An NPC ("Maxie(A Hunter's Calling).png") or a portrait
     * beside dialogue ("|200px|left") is not one.
     */
    static boolean useful(String quest, String file, String opts) {
        boolean framed = FRAMED.matcher(opts).find();
        if (file.contains("(") || !framed && opts.matches("(?i).*\\|\\s*left\\s*(\\|.*)?$")) {
            return false;
        }
        String name = file.replaceFirst("\\.[A-Za-z]+$", "");
        Matcher px = PX.matcher(opts);
        int size = px.find() ? Integer.parseInt(px.group(1)) : 0;
        if (USEFUL.matcher(name).find()) {
            return size == 0 || size > 64;              // not a token's icon
        }
        String q = letters(quest);
        boolean named = !q.isEmpty() && letters(name).contains(q.substring(0, Math.min(10, q.length())));
        return (framed || named) && (size == 0 || size > 100);
    }

    private static String letters(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * The picture as PNG bytes: from disk, else from the wiki (a 360px thumbnail, kept on disk).
     * Callers check {@link QuestWiki#on()} first; null when the wiki has no such file.
     */
    public static byte[] png(String file) throws Exception {
        Path dir = QuestWiki.cacheDir().resolve("images");
        Path f = dir.resolve(file.replaceAll("[^A-Za-z0-9._'-]", "_").replaceFirst("\\.[A-Za-z]+$", "") + ".png");
        if (Files.exists(f)) {
            return Files.readAllBytes(f);
        }
        String reply = QuestWiki.api("action=query&prop=imageinfo&iiprop=url&iiurlwidth=360&format=json&titles="
                + URLEncoder.encode("File:" + file.replace(' ', '_'), StandardCharsets.UTF_8));
        var pages = JsonParser.parseString(reply).getAsJsonObject().getAsJsonObject("query").getAsJsonObject("pages");
        var page = pages.entrySet().iterator().next().getValue().getAsJsonObject();
        if (!page.has("imageinfo")) {
            return null;
        }
        var info = page.getAsJsonArray("imageinfo").get(0).getAsJsonObject();
        String url = info.has("thumburl") ? info.get("thumburl").getAsString() : info.get("url").getAsString();
        byte[] body = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).header("User-Agent", AGENT)
                .header("Accept", "image/png,image/*").GET().build(), HttpResponse.BodyHandlers.ofByteArray()).body();
        byte[] png = isPng(body) ? body : toPng(body);
        if (png == null) {
            return null;
        }
        Files.createDirectories(dir);
        Files.write(f, png);
        return png;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    /** A JPEG or GIF thumbnail as PNG (the game reads PNG only); null when it can't be read. */
    private static byte[] toPng(byte[] b) throws Exception {
        var img = javax.imageio.ImageIO.read(new ByteArrayInputStream(b));
        if (img == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
