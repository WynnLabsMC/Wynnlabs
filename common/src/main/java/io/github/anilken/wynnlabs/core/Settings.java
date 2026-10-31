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
            "glowUpgrade", "glowShop", "chatUpgrade", "chatSkills", "chatLevelUp", "groundBeam", "groundGlowShop", "chestBeams", "trackedDrops", "mobTrack",
            "mobTrackSound", "autoTree", "glowQuest", "priceApi", "dangerWarn", "sellSummary", "bankHint", "sourceHint",
            "survivalWarn", "healerAdvice", "deathCoach", "coachToasts", "dpsHud", "ridePanel", "powerupLabels", "rideRefresh", "sellGlow", "feederOverlay",
            "feederHighlight", "miniQuests", "questGuides", "caveMapDone", "localApi", "apiWrite", "debug", "webFont", "menuResume"}) {
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

    private static Path file() {
        return Dirs.config().resolve("settings.json");
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
            "chatUpgrade", "coachToasts", "mobTrackSound", "groundBeam", "powderHint", "chestBeams");

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
            JsonObject o = new JsonObject();
            VALUES.forEach(o::addProperty);
            NUMBERS.forEach(o::addProperty);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(o), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not save settings.json: " + e);
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
