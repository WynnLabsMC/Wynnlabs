package io.github.anilken.wynnlabs.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Feature switches, choices and numbers, set on the menu's settings tab and kept in
 * config/wynnlabs/settings.json for every character. Most switches are on by default.
 */
public final class Settings {

    /**
     * Every switch → its language key. The settings tab picks and orders them by page
     * ({@code ui/SettingsRows}); a few live on their own tab (feederOverlay and feederHighlight on
     * Mounts, dpsHud on the tree) or the AI tab (localApi, apiWrite) and are only listed here.
     */
    public static final Map<String, String> LABELS = new LinkedHashMap<>();
    /** Switch, choice or number → the language key of its one-line description. */
    public static final Map<String, String> DESC = new LinkedHashMap<>();

    static {
        for (String k : new String[] {"itemAdvice", "altAdvice", "wornHint", "powderHint", "tooltipPrice", "boxPrices",
            "boxCompactTooltip", "gearCompactTooltip", "glowUpgrade", "glowShop", "chatUpgrade", "chatSkills", "chatLevelUp", "groundBeam", "groundGlowShop", "chestBeams", "trackedDrops", "mobTrack",
            "mobTrackSound", "autoTree", "glowQuest", "priceApi", "dangerWarn", "sellSummary", "bankHint", "sourceHint",
            "survivalWarn", "healerAdvice", "deathCoach", "coachToasts", "dpsHud", "ridePanel", "powerupLabels", "rideRefresh", "sellGlow", "feederOverlay",
            "feederHighlight", "miniQuests", "questGuides", "caveMapDone", "localApi", "apiWrite", "debug", "webFont", "menuResume",
            "crowdMode"}) {
            LABELS.put(k, "wynnlabs.setting." + k);
            DESC.put(k, "wynnlabs.setting_desc." + k);
        }
    }

    /**
     * Settings with one of three values (a row of chips): key → its default. They replaced
     * families of switches ({@link SettingsMigration}): dropGlow 0 off, 1 gear, 2 every drop;
     * slotBadges 0 off, 1 inventory, 2 inventory and hotbar; treeHelp 0 off, 1 tips, 2 tips and
     * glow; updates 0 off, 1 notify, 2 install by itself.
     */
    public static final Map<String, Integer> CHOICES = new LinkedHashMap<>();

    static {
        CHOICES.put("dropGlow", 1);
        CHOICES.put("slotBadges", 1);
        CHOICES.put("treeHelp", 2);
        CHOICES.put("updates", 1);
        CHOICES.keySet().forEach(k -> DESC.put(k, "wynnlabs.setting_desc." + k));
    }

    /** A choice setting's value, 0 up to 2 (its default when unset). */
    public static int choice(String key) {
        int d = CHOICES.getOrDefault(key, 0);
        return Math.max(0, Math.min(2, number(key, d)));
    }

    /** A choice set; its default is not written to settings.json. */
    public static synchronized void setChoice(String key, int value) {
        load();
        if (value == CHOICES.getOrDefault(key, 0)) {
            NUMBERS.remove(key);
        } else {
            NUMBERS.put(key, value);
        }
        save();
    }

    /** Number settings with − and + on the settings tab: key → the values they step through, in order. */
    public static final Map<String, int[]> STEPS = new LinkedHashMap<>();
    /** Number setting → its language key. */
    public static final Map<String, String> STEP_LABELS = new LinkedHashMap<>();
    /** Number setting → the language key that writes its value ("85%"); plain digits when absent. */
    public static final Map<String, String> STEP_FORMAT = new LinkedHashMap<>();
    /** Number setting → the language key for its value 0 when that means something else ("All"). */
    public static final Map<String, String> STEP_ZERO = new LinkedHashMap<>();
    private static final Map<String, Integer> STEP_DEFAULT = new LinkedHashMap<>();

    static {
        // tooltip size while a menu is open, in percent; 100 leaves tooltips as the game draws them
        STEPS.put("menuTooltipScale", new int[] {70, 75, 80, 85, 90, 95, 100});
        STEP_LABELS.put("menuTooltipScale", "wynnlabs.setting.menuTooltipScale");
        STEP_FORMAT.put("menuTooltipScale", "wynnlabs.settings.percent");
        STEP_DEFAULT.put("menuTooltipScale", 85);
        DESC.put("menuTooltipScale", "wynnlabs.setting_desc.menuTooltipScale");
        // crowd mode: how far from you spell effects and particles are still drawn; 0 draws none
        STEPS.put("crowdRadius", new int[] {0, 2, 3, 4, 6, 8, 12});
        STEP_LABELS.put("crowdRadius", "wynnlabs.setting.crowdRadius");
        STEP_FORMAT.put("crowdRadius", "wynnlabs.settings.blocks");
        STEP_ZERO.put("crowdRadius", "wynnlabs.settings.crowd_none");
        STEP_DEFAULT.put("crowdRadius", 0);
        DESC.put("crowdRadius", "wynnlabs.setting_desc.crowdRadius");
        STEPS.put("powerupLabelSize", new int[] {75, 100, 125, 150});
        STEP_LABELS.put("powerupLabelSize", "wynnlabs.setting.powerupLabelSize");
        STEP_FORMAT.put("powerupLabelSize", "wynnlabs.settings.percent");
        STEP_DEFAULT.put("powerupLabelSize", 100);
        DESC.put("powerupLabelSize", "wynnlabs.setting_desc.powerupLabelSize");
        // the fewest stars a loot chest needs for a beam; 0 is every chest, unknown tier too
        STEPS.put("chestBeamTier", new int[] {0, 1, 2, 3});
        STEP_LABELS.put("chestBeamTier", "wynnlabs.setting.chestBeamTier");
        STEP_FORMAT.put("chestBeamTier", "wynnlabs.settings.stars_up");
        STEP_ZERO.put("chestBeamTier", "wynnlabs.settings.stars_all");
        STEP_DEFAULT.put("chestBeamTier", 0);
        DESC.put("chestBeamTier", "wynnlabs.setting_desc.chestBeamTier");
    }

    /** A stepped number setting's value (its default when unset). */
    public static int stepValue(String key) {
        return number(key, stepDefault(key));
    }

    /** What a stepped number setting is before the player touches it. */
    public static int stepDefault(String key) {
        Integer d = STEP_DEFAULT.get(key);
        return d == null ? 0 : d;
    }

    /** One step down (−1) or up (+1) through a number setting's values, stopping at the ends. */
    public static synchronized void step(String key, int dir) {
        int[] options = STEPS.get(key);
        if (options == null) {
            return;
        }
        int v = stepValue(key);
        int at = 0;                             // the option nearest the stored value
        for (int i = 1; i < options.length; i++) {
            if (Math.abs(options[i] - v) < Math.abs(options[at] - v)) {
                at = i;
            }
        }
        if (options[at] == v || dir > 0 && options[at] < v || dir < 0 && options[at] > v) {
            at += dir;                          // on an option, or past it the way we step: move on
        }
        setNumber(key, options[Math.max(0, Math.min(options.length - 1, at))]);
    }

    /**
     * Keys of features that were removed (loot chest glow and labels, far chest markers, party and
     * nameless glow, the manual menu scale, the tree bonus switch) or folded into a choice
     * ({@link SettingsMigration}). An old settings.json may still hold them: they are dropped on
     * load, after the migration read them, so the next save no longer writes them.
     */
    static final java.util.Set<String> REMOVED = java.util.Set.of("chestGlow", "chestGlowUnknown", "chestLabel",
            "chestMinStars", "chestFar", "chestFarTier", "chestFarDistance", "partyGlow", "namelessGlow",
            "groundGlow", "groundGlowNormal", "groundGlowOther", "verdictBadges", "hotbarBadges", "glowTree", "menuAdvice",
            "updateCheck", "autoUpdate", "treeBonuses", "menuScale");

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, Boolean> VALUES = new LinkedHashMap<>();
    private static final Map<String, Integer> NUMBERS = new LinkedHashMap<>();
    private static volatile boolean loaded;
    /**
     * What {@link #on} and {@link #number} read: immutable copies of VALUES and NUMBERS, swapped
     * after every change. Readers never lock (they run per entity and per slot every frame);
     * writers change the maps under the class lock and publish new copies.
     */
    private static volatile Map<String, Boolean> valuesNow = Map.of();
    private static volatile Map<String, Integer> numbersNow = Map.of();

    private Settings() {}

    /** Where the files live instead of the config folder (tests only). */
    static Path dirOverride;

    private static Path dir() {
        return dirOverride != null ? dirOverride : Dirs.config();
    }

    private static Path file() {
        return dir().resolve("settings.json");
    }

    private static void load() {
        if (!loaded) {
            loadLocked();
        }
    }

    private static synchronized void loadLocked() {
        if (loaded) {
            return;
        }
        boolean migrated = false;
        try {
            if (Files.exists(file())) {
                migrated = read(GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), JsonObject.class));
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not read settings.json: " + e);
        }
        NUMBERS.put(SettingsMigration.VERSION_KEY, SettingsMigration.VERSION);
        publish();
        loaded = true;
        if (migrated) {
            save();                             // once: the merged switches are written as their choices
        }
    }

    /**
     * A settings file's switches and numbers into VALUES and NUMBERS: a file from before the
     * settings' regrouping is migrated first, then removed features are skipped. Returns whether
     * it was migrated (the file is then saved once in its new form).
     */
    private static boolean read(JsonObject o) {
        if (o == null) {
            return false;
        }
        Map<String, Boolean> bools = new LinkedHashMap<>();
        Map<String, Integer> nums = new LinkedHashMap<>();
        o.entrySet().forEach(e -> {
            if (!e.getValue().isJsonPrimitive()) {
                return;                         // a value we never wrote
            }
            var p = e.getValue().getAsJsonPrimitive();
            if (p.isBoolean()) {
                bools.put(e.getKey(), p.getAsBoolean());
            } else if (p.isNumber()) {
                nums.put(e.getKey(), p.getAsInt());
            }
        });
        boolean migrate = nums.getOrDefault(SettingsMigration.VERSION_KEY, 0) < SettingsMigration.VERSION;
        if (migrate) {
            SettingsMigration.apply(bools, nums);
        }
        bools.forEach((k, v) -> {
            if (!REMOVED.contains(k)) {
                VALUES.put(k, v);
            }
        });
        nums.forEach((k, v) -> {
            if (!REMOVED.contains(k)) {
                NUMBERS.put(k, v);
            }
        });
        NUMBERS.put(SettingsMigration.VERSION_KEY, SettingsMigration.VERSION);
        return migrate;
    }

    /** New read copies after a change; call with the class lock held. */
    private static void publish() {
        valuesNow = Map.copyOf(VALUES);
        numbersNow = Map.copyOf(NUMBERS);
    }

    /** Tests only: these values instead of settings.json (migrated as a file would be), never saved. */
    public static synchronized void use(JsonObject o) {
        VALUES.clear();
        NUMBERS.clear();
        preset = null;
        read(o);
        publish();
        loaded = true;
        Rules.version++;
    }

    /** A small number setting (e.g. the language picked or the API port). */
    public static int number(String key, int fallback) {
        load();
        Integer v = numbersNow.get(key);
        return v == null ? fallback : v;
    }

    /**
     * Switches that start off; everything else starts on. The chattier and busier extras, and the
     * one that asks the server for something (rideRefresh), so a first run shows the mod's main
     * work and the rest is a switch away.
     */
    static final java.util.Set<String> OFF_BY_DEFAULT = java.util.Set.of("altAdvice", "priceApi",
            "localApi", "apiWrite", "debug", "dpsHud", "ridePanel", "rideRefresh",
            "chatUpgrade", "coachToasts", "mobTrackSound", "groundBeam", "powderHint", "chestBeams", "gearCompactTooltip",
            "crowdMode");

    public static boolean on(String key) {
        load();
        Boolean v = valuesNow.get(key);
        return v != null ? v : byDefault(key);
    }

    /** Whether the player set this switch (it is in settings.json), on or off. */
    public static boolean saved(String key) {
        load();
        return valuesNow.containsKey(key);
    }

    /** What a switch is before the player touches it. */
    public static boolean byDefault(String key) {
        return !OFF_BY_DEFAULT.contains(key);
    }

    /** Whether a switch differs from its default (the settings tab marks it with a gold dot). */
    public static boolean changed(String key) {
        return on(key) != byDefault(key);
    }

    /** A switch back to its default: it is no longer written to settings.json. */
    public static synchronized void reset(String key) {
        load();
        VALUES.remove(key);
        save();
    }

    /** A number setting back to its default (the fallback its readers give). */
    public static synchronized void resetNumber(String key) {
        load();
        NUMBERS.remove(key);
        save();
    }

    public static synchronized void setNumber(String key, int value) {
        load();
        NUMBERS.put(key, value);
        save();
    }

    public static synchronized void set(String key, boolean value) {
        load();
        VALUES.put(key, value);
        save();
    }

    public static synchronized void toggle(String key) {
        load();
        VALUES.put(key, !on(key));
        save();
    }

    private static void save() {
        publish();
        Rules.version++;                        // cached verdicts and glows rebuild
        try {
            Files.createDirectories(file().getParent());
            if (preset == null) {
                write(file(), VALUES, NUMBERS);
                return;
            }
            // a preset is on: the changes go into it, settings.json keeps the shared settings
            // (and follows the few that belong to no preset)
            Files.createDirectories(presetFile(preset).getParent());
            write(presetFile(preset), withoutPersonal(VALUES), withoutPersonal(NUMBERS));
            for (String k : PERSONAL) {
                moveKey(k, VALUES, baseValues);
                moveKey(k, NUMBERS, baseNumbers);
            }
            write(file(), baseValues, baseNumbers);
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not save settings.json: " + e);
        }
    }

    private static <V> void moveKey(String key, Map<String, V> from, Map<String, V> to) {
        if (from.containsKey(key)) {
            to.put(key, from.get(key));
        } else {
            to.remove(key);
        }
    }

    private static void write(Path path, Map<String, Boolean> bools, Map<String, Integer> nums) throws java.io.IOException {
        JsonObject o = new JsonObject();
        bools.forEach(o::addProperty);
        nums.forEach(o::addProperty);
        Files.writeString(path, GSON.toJson(o), StandardCharsets.UTF_8);
    }

    // ---- presets: named copies of the settings, one chosen per character (SettingsPresets) ----

    /** Kept out of presets: they belong to the player, not to a way of playing (the API switches are a security matter). */
    private static final java.util.Set<String> PERSONAL = java.util.Set.of("localApi", "apiWrite", "langOverride", "menuFill",
            SettingsMigration.VERSION_KEY);

    /** The preset in use, null for the shared settings (settings.json). */
    private static String preset;
    /** What settings.json holds while a preset is in use. */
    private static Map<String, Boolean> baseValues = new LinkedHashMap<>();
    private static Map<String, Integer> baseNumbers = new LinkedHashMap<>();

    private static <V> Map<String, V> withoutPersonal(Map<String, V> m) {
        Map<String, V> out = new LinkedHashMap<>(m);
        out.keySet().removeAll(PERSONAL);
        return out;
    }

    static Path presetsDir() {
        return dir().resolve("presets");
    }

    private static Path presetFile(String name) {
        return presetsDir().resolve(name + ".json");
    }

    /** The preset in use, null for the shared settings. */
    public static synchronized String activePreset() {
        load();
        return preset;
    }

    /** A name a preset can be saved under: letters, digits, space, - and _, 16 at most; "" when nothing is left. */
    public static String presetName(String raw) {
        String n = raw == null ? "" : raw.replaceAll("[^\\p{L}\\p{N} _-]", "").strip();
        return n.length() > 16 ? n.substring(0, 16).strip() : n;
    }

    /** The saved presets, by name. */
    public static synchronized java.util.List<String> presetNames() {
        java.util.List<String> out = new java.util.ArrayList<>();
        try (var files = Files.isDirectory(presetsDir()) ? Files.list(presetsDir()) : java.util.stream.Stream.<Path>empty()) {
            files.map(f -> f.getFileName().toString()).filter(f -> f.endsWith(".json"))
                    .map(f -> f.substring(0, f.length() - 5)).sorted(String.CASE_INSENSITIVE_ORDER).forEach(out::add);
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not list presets: " + e);
        }
        return out;
    }

    /**
     * Uses a preset (its settings replace the live ones, but for the personal few), or, with
     * null or a name that is gone, the shared settings again. Nothing is written: a preset only
     * changes when its settings do.
     */
    public static synchronized void usePreset(String name) {
        load();
        JsonObject o = null;
        name = name == null || presetName(name).isEmpty() ? null : presetName(name);
        if (name != null) {
            try {
                if (Files.exists(presetFile(name))) {
                    o = GSON.fromJson(Files.readString(presetFile(name), StandardCharsets.UTF_8), JsonObject.class);
                }
            } catch (Exception e) {
                System.out.println("[WynnLabs] could not read preset " + name + ": " + e);
            }
        }
        Map<String, Boolean> personalV = new LinkedHashMap<>(VALUES);
        Map<String, Integer> personalN = new LinkedHashMap<>(NUMBERS);
        personalV.keySet().retainAll(PERSONAL);
        personalN.keySet().retainAll(PERSONAL);
        if (preset == null) {
            baseValues = new LinkedHashMap<>(VALUES);
            baseNumbers = new LinkedHashMap<>(NUMBERS);
        }
        VALUES.clear();
        NUMBERS.clear();
        if (o == null) {
            VALUES.putAll(baseValues);
            NUMBERS.putAll(baseNumbers);
            preset = null;
        } else {
            JsonObject mine = o;
            mine.entrySet().forEach(e -> {
                if (!e.getValue().isJsonPrimitive() || PERSONAL.contains(e.getKey()) || REMOVED.contains(e.getKey())) {
                    return;
                }
                var p = e.getValue().getAsJsonPrimitive();
                if (p.isBoolean()) {
                    VALUES.put(e.getKey(), p.getAsBoolean());
                } else if (p.isNumber()) {
                    NUMBERS.put(e.getKey(), p.getAsInt());
                }
            });
            VALUES.putAll(personalV);
            NUMBERS.putAll(personalN);
            preset = name;
        }
        publish();
        Rules.version++;
    }

    /** The settings as they are now, saved as a preset and in use; false for a name with nothing left in it. */
    public static synchronized boolean savePreset(String raw) {
        String name = presetName(raw);
        if (name.isEmpty()) {
            return false;
        }
        load();
        if (preset == null) {
            baseValues = new LinkedHashMap<>(VALUES);
            baseNumbers = new LinkedHashMap<>(NUMBERS);
        }
        preset = name;
        save();
        return true;
    }

    /** A preset renamed; the name it got, null when the new name is empty or another preset has it. */
    public static synchronized String renamePreset(String old, String raw) {
        String name = presetName(raw);
        if (name.isEmpty() || !presetNames().contains(old)) {
            return null;
        }
        if (!name.equalsIgnoreCase(old) && presetNames().stream().anyMatch(name::equalsIgnoreCase)) {
            return null;
        }
        try {
            Files.move(presetFile(old), presetFile(name), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not rename preset " + old + ": " + e);
            return null;
        }
        if (old.equals(preset)) {
            preset = name;
        }
        return name;
    }

    /** A preset deleted; the shared settings come back when it was the one in use. */
    public static synchronized void deletePreset(String name) {
        load();
        if (name.equals(preset)) {
            usePreset(null);
        }
        try {
            Files.deleteIfExists(presetFile(name));
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not delete preset " + name + ": " + e);
        }
    }

    /** GET /settings: every switch, on or off, and every choice as its number. */
    public static JsonObject json() {
        JsonObject o = new JsonObject();
        LABELS.keySet().forEach(k -> o.addProperty(k, on(k)));
        CHOICES.keySet().forEach(k -> o.addProperty(k, choice(k)));
        return o;
    }
}
