package io.github.anilken.wynnlabs.api;

import io.github.anilken.wynnlabs.coach.Today;
import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.L;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * The link to an AI assistant, for the settings' "AI assistant" panel: whether the local API
 * listens and is being used (last request, today's count), the endpoint table the API's index
 * also serves, and the skill file the mod ships, exported or installed for Claude Code only when
 * the player clicks.
 */
public final class AiLink {

    /**
     * One endpoint: path, its English line for the API index, its language key for the panel, and
     * its group on the AI page ("char", "gear", "world", "bank", "mod", or "write" for the ones that
     * change something and need writing on).
     */
    public record Endpoint(String path, String en, String key, String group) {
        public boolean write() {
            return "write".equals(group);
        }
    }

    public static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint("/status", "in game?, class, combat level", "wynnlabs.ai.ep_status", "char"),
            new Endpoint("/character", "class, level, skill points per skill (assigned / gear / total)", "wynnlabs.ai.ep_character", "char"),
            new Endpoint("/abilities", "ability points, unlocked abilities, the class's whole tree", "wynnlabs.ai.ep_abilities", "char"),
            new Endpoint("/treeplan", "the chosen ability tree plan, node by node", "wynnlabs.ai.ep_treeplan", "char"),
            new Endpoint("/profile", "the class profile's rules behind the build scores", "wynnlabs.ai.ep_profile", "char"),
            new Endpoint("/characters", "every character seen, with class, level and goal", "wynnlabs.ai.ep_characters", "char"),
            new Endpoint("/equipped", "armour, accessories, weapon in hand", "wynnlabs.ai.ep_equipped", "gear"),
            new Endpoint("/inventory", "all 36 inventory slots", "wynnlabs.ai.ep_inventory", "gear"),
            new Endpoint("/build", "best build from worn and carried gear with skill points", "wynnlabs.ai.ep_build", "gear"),
            new Endpoint("/damage", "model vs measured damage per cast, where the DPS comes from, what-ifs, live DPS meter",
                    "wynnlabs.ai.ep_damage", "gear"),
            new Endpoint("/roadmap", "milestones ahead: gear to get, ability nodes, places to level", "wynnlabs.ai.ep_roadmap", "gear"),
            new Endpoint("/roadmap/trace", "why each candidate for a slot was kept or dropped (?level=120&slot=RING)",
                    "wynnlabs.ai.ep_trace", "gear"),
            new Endpoint("/shopping", "best gear per slot to buy or hunt for your level and build", "wynnlabs.ai.ep_shopping", "gear"),
            new Endpoint("/crafting/ingredients", "every crafting ingredient with its stats and where it drops",
                    "wynnlabs.ai.ep_craft", "gear"),
            new Endpoint("/quests", "tracked quest and every quest from the quest book", "wynnlabs.ai.ep_quests", "world"),
            new Endpoint("/questplan", "which quests to do next and why", "wynnlabs.ai.ep_questplan", "world"),
            new Endpoint("/grind", "caves, dungeons, boss altars and grind spots near your level", "wynnlabs.ai.ep_grind", "world"),
            new Endpoint("/discoveries", "secret discoveries in the chosen order: done, level, distance, requirements",
                    "wynnlabs.ai.ep_discoveries", "world"),
            new Endpoint("/deaths", "recent deaths and what killed you", "wynnlabs.ai.ep_deaths", "world"),
            new Endpoint("/ground", "items on the ground around you (?r=12)", "wynnlabs.ai.ep_ground", "world"),
            new Endpoint("/vault", "every bank page you opened, with verdicts and what to sell", "wynnlabs.ai.ep_vault", "bank"),
            new Endpoint("/prices", "Trade Market prices the mod knows", "wynnlabs.ai.ep_prices", "bank"),
            new Endpoint("/", "this list", "wynnlabs.ai.ep_index", "mod"),
            new Endpoint("/settings", "the mod's settings", "wynnlabs.ai.ep_settings", "mod"),
            new Endpoint("/perf", "what each part of the mod costs a frame (?reset=1 starts over)", "wynnlabs.ai.ep_perf", "mod"),
            new Endpoint("/screenshot", "the game's screen as a PNG", "wynnlabs.ai.ep_screenshot", "mod"),
            new Endpoint("/compass", "point the compass and map pin: POST {x,y,z,label} or {points:[{x,y,z,label}]} (one at a time; with keep:true all at once and lasting), DELETE clears (?keep=1 the lasting ones); needs writing on",
                    "wynnlabs.ai.ep_compass", "write"),
            new Endpoint("/advice", "your own verdicts (POST {items:[{key,verdict,note}]}; needs writing on)", "wynnlabs.ai.ep_advice", "write"),
            new Endpoint("/rules", "the rules behind the verdicts (POST merges, DELETE resets; needs writing on)", "wynnlabs.ai.ep_rules", "write"));

    /** The page's groups, in order. */
    public static final List<String> GROUPS = List.of("char", "gear", "world", "bank", "mod");

    private AiLink() {}

    // ---- the server and its traffic ----

    /** "off", "listening" or "busy" (the port could not be opened; see {@link #error}). */
    public static volatile String state = "off";
    static volatile String error;
    public static volatile int port = 47591;
    private static volatile long lastAt;
    private static volatile String lastPath = "";
    private static volatile int today;
    private static volatile String day = "";

    /** Called for every API request. */
    static void seen(String path) {
        String d = Today.utcDate();
        if (!d.equals(day)) {
            day = d;
            today = 0;
        }
        today++;
        lastAt = System.currentTimeMillis();
        lastPath = path;
    }

    public static long lastAt() {
        return lastAt;
    }

    public static String lastPath() {
        return lastPath;
    }

    public static int today() {
        return Today.utcDate().equals(day) ? today : 0;
    }

    public static String url() {
        return "http://127.0.0.1:" + port + "/";
    }

    /**
     * The first port after {@code from} that this computer has free on 127.0.0.1 (tried by opening
     * and closing it at once), up to 50 on; {@code from + 1} when none answers.
     */
    public static int nextFreePort(int from) {
        for (int p = from + 1; p <= Math.min(65535, from + 50); p++) {
            try (java.net.ServerSocket s = new java.net.ServerSocket(p, 1, java.net.InetAddress.getLoopbackAddress())) {
                return p;
            } catch (java.io.IOException taken) {
                // in use: the next one
            }
        }
        return Math.min(65535, from + 1);
    }

    // ---- the skill file ----

    /**
     * The shipped skill (assets/wynnlabs/ai/SKILL.md, copied from ai/skills/wynnlabs at build),
     * with its address on the port in use: the file is written for the default 47591.
     */
    static String skill() throws Exception {
        try (InputStream in = AiLink.class.getResourceAsStream("/assets/wynnlabs/ai/SKILL.md")) {
            if (in == null) {
                throw new IllegalStateException("SKILL.md missing from the mod");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("127.0.0.1:47591", "127.0.0.1:" + port);
        }
    }

    /** Writes the skill under config/wynnlabs/ai/wynnlabs/; returns the file. */
    public static Path export() throws Exception {
        Path f = Dirs.config().resolve("ai").resolve("wynnlabs").resolve("SKILL.md");
        Files.createDirectories(f.getParent());
        Files.writeString(f, skill(), StandardCharsets.UTF_8);
        return f;
    }

    /** Where Claude Code keeps the skill (its own wynnlabs folder). */
    public static Path claudeSkill() {
        return Path.of(System.getProperty("user.home"), ".claude", "skills", "wynnlabs", "SKILL.md");
    }

    /** Copies the skill into Claude Code's skills folder (only its own wynnlabs folder); returns the file. */
    public static Path installClaude() throws Exception {
        Path f = claudeSkill();
        Files.createDirectories(f.getParent());
        Files.writeString(f, skill(), StandardCharsets.UTF_8);
        return f;
    }

    /** A short instruction block for any chat assistant that can call a local URL. */
    public static String prompt() {
        StringBuilder eps = new StringBuilder();
        for (Endpoint e : ENDPOINTS) {
            eps.append("- GET ").append(e.path()).append(": ").append(e.en()).append('\n');
        }
        return L.t("wynnlabs.ai.prompt_text", url(), eps.toString().strip());
    }
}
