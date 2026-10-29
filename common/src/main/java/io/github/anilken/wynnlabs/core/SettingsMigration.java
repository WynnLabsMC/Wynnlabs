package io.github.anilken.wynnlabs.core;

import java.util.Map;
import java.util.Set;

/**
 * The one-time move of an old settings.json to the regrouped settings (2026-10): families of
 * switches became one choice each. Runs once per file: the file then carries
 * {@link #VERSION_KEY} and the old keys are dropped ({@link Settings#REMOVED}).
 *
 * <p>Each choice keeps what the player saw: a family where any switch was on becomes the choice
 * that includes it (on if any was on), a family all off becomes Off. A choice that comes out at
 * its default is not written, so it does not count as changed.
 */
final class SettingsMigration {

    /** The number in settings.json that says which form the file is in. */
    static final String VERSION_KEY = "settingsVersion";
    static final int VERSION = 2;

    /** Old switches that were off before the player touched them (the rest were on). */
    private static final Set<String> OLD_OFF = Set.of("groundGlowNormal", "groundGlowOther", "hotbarBadges", "autoUpdate");

    private SettingsMigration() {}

    /** The old switches in {@code bools} as choices in {@code nums}; the old keys stay for the caller to drop. */
    static void apply(Map<String, Boolean> bools, Map<String, Integer> nums) {
        // Drop glow: off when drops did not glow; "All drops" when Normal or other drops glowed too
        int drop = !was(bools, "groundGlow") ? 0 : was(bools, "groundGlowNormal") || was(bools, "groundGlowOther") ? 2 : 1;
        // Slot badges: "+ Hotbar" when the hotbar had them (even without the inventory's), else the inventory's
        int badges = was(bools, "hotbarBadges") ? 2 : was(bools, "verdictBadges") ? 1 : 0;
        // Tree help: the glow always came with its tips now; tips alone stay tips
        int tree = was(bools, "glowTree") ? 2 : was(bools, "menuAdvice") ? 1 : 0;
        // New versions: installing by itself wins, else the reminder
        int updates = was(bools, "autoUpdate") ? 2 : was(bools, "updateCheck") ? 1 : 0;
        put(nums, "dropGlow", drop);
        put(nums, "slotBadges", badges);
        put(nums, "treeHelp", tree);
        put(nums, "updates", updates);
    }

    /** What an old switch was: saved, or its old default. */
    private static boolean was(Map<String, Boolean> bools, String key) {
        Boolean v = bools.get(key);
        return v != null ? v : !OLD_OFF.contains(key);
    }

    private static void put(Map<String, Integer> nums, String key, int value) {
        if (nums.containsKey(key)) {
            return;                             // already set by a newer build: kept
        }
        if (value != Settings.CHOICES.getOrDefault(key, 0)) {
            nums.put(key, value);
        }
    }
}
