package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.MarketSlots;
import io.github.anilken.wynnlabs.core.RuleEditor;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.core.Settings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The settings' model, without drawing: the pages (one per feature, Basics first), each page's
 * rows under their section titles, and whether a row differs from its default. What a row says
 * is {@link SettingsText}; {@link SettingsList} draws them.
 *
 * <p>Basics repeats the most used rows of the other pages (the same settings, not copies); the
 * changed count and the search count every setting once.
 */
final class SettingsRows {

    private SettingsRows() {}

    static final String[] SETTING_CATS = {"basics", "items", "world", "progress", "mounts", "trading", "online", "advanced"};

    static final String[] SETTING_CAT_ICONS = {"home", "backpack", "world", "trending-up", "horse", "coin", "refresh",
        "settings"};

    private static final String[] SETTING_CAT_NAMES = {"wynnlabs.settings.page_basics", "wynnlabs.settings.page_items",
        "wynnlabs.settings.page_world", "wynnlabs.settings.page_progress", "wynnlabs.settings.page_mounts",
        "wynnlabs.settings.page_trading", "wynnlabs.settings.page_online", "wynnlabs.settings.page_advanced"};

    /** The page's name's language key. */
    static String pageKey(int cat) {
        return SETTING_CAT_NAMES[cat];
    }

    /**
     * One row of a page. {@code kind}: head (a section title: {@code id} its language key),
     * switch, multi (several switches as chips), choice, lang, fill, step, rule, rulemulti, preset,
     * slots, advice, arch, goal, keynote, notice. {@code parent}: the switch or choice the row
     * belongs to (dimmed while that is off; "a|b" while both are off). {@code fold}: the folding
     * section it is in, null for none; a head with a fold folds the rows under it.
     */
    record SetRow(String kind, String id, String parent, String fold) {

        boolean head() {
            return kind.equals("head");
        }

        RuleEditor.Rule r() {
            return RuleEditor.byId(id);
        }

        /** What the row sets, for counting it once though Basics repeats it. */
        String key() {
            return kind + ":" + id;
        }
    }

    /** A multi row's switches (or, for rulemulti, rule ids) and their chips' language keys. */
    record Multi(String[] keys, String[] labels) {}

    static final Map<String, Multi> MULTI = Map.of(
            "warnings", multi("dangerWarn", "survivalWarn", "healerAdvice"),
            "chat", multi("chatUpgrade", "chatSkills", "chatLevelUp"),
            "extras", multi("wornHint", "sourceHint", "bankHint", "powderHint"),
            "slotGlow", multi("glowUpgrade", "glowShop", "sellGlow"),
            "hide", multi("no_mythic", "no_event", "no_raid"));

    private static Multi multi(String... keys) {
        String[] labels = new String[keys.length];
        for (int i = 0; i < keys.length; i++) {
            labels[i] = "wynnlabs.settings.opt." + keys[i];
        }
        return new Multi(keys, labels);
    }

    /** The preset row's name box: 0 closed, 1 naming a new preset, 2 renaming the one in use. */
    static int profileEdit;

    /** A page's rows in the order the list shows them. */
    static List<SetRow> settingRows(String cat) {
        Page p = new Page();
        switch (cat) {
            case "basics" -> {
                p.head("wynnlabs.settings.sec_presets");
                p.row("profiles", "presets");
                p.head("wynnlabs.settings.sec_menu");
                p.row("lang", "language");
                p.row("fill", "menu_fill");
                p.head("wynnlabs.settings.sec_in_game");
                p.row("switch", "itemAdvice");
                p.row("switch", "tooltipPrice");
                p.row("choice", "dropGlow");
                p.row("switch", "glowUpgrade");
                p.row("multi", "warnings");
                p.row("multi", "chat");
            }
            case "items" -> {
                p.head("wynnlabs.settings.sec_advice");
                p.row("switch", "itemAdvice");
                p.row("switch", "altAdvice");
                p.row("switch", "sellSummary");
                p.head("wynnlabs.settings.sec_tooltip");
                p.row("multi", "extras");
                p.row("step", "menuTooltipScale");
                p.head("wynnlabs.settings.sec_bag");
                p.row("multi", "slotGlow");
                p.row("choice", "slotBadges");
            }
            case "world" -> {
                p.head("wynnlabs.settings.sec_drops");
                p.row("choice", "dropGlow");
                p.child("switch", "groundGlowShop", "dropGlow");
                p.child("switch", "groundBeam", "dropGlow");
                p.row("switch", "trackedDrops");
                p.row("switch", "chestBeams");
                p.child("step", "chestBeamTier", "chestBeams");
                p.head("wynnlabs.settings.sec_tracked");
                p.row("switch", "mobTrack");
                p.child("switch", "mobTrackSound", "mobTrack");
                p.head("wynnlabs.settings.sec_warnings");
                p.row("multi", "warnings");
                // the low HP warning's and the death coach's words for healers and tanks
                p.child("switch", "healerAdvice", "survivalWarn|deathCoach");
                p.row("switch", "deathCoach");
            }
            case "progress" -> {
                p.head("wynnlabs.settings.sec_chat");
                p.row("multi", "chat");
                p.row("switch", "coachToasts");
                p.head("wynnlabs.settings.sec_build_goal");
                p.row("arch", "archetype");
                p.row("goal", "goal");
                p.head("wynnlabs.settings.sec_tree");
                p.row("choice", "treeHelp");
                p.row("switch", "autoTree");
                p.head("wynnlabs.settings.sec_quests");
                p.row("switch", "glowQuest");
                p.row("switch", "miniQuests");
                p.row("switch", "questGuides");
                p.row("switch", "caveMapDone");
            }
            case "mounts" -> {
                p.head("wynnlabs.settings.sec_riding");
                p.row("switch", "ridePanel");
                p.row("switch", "powerupLabels");
                p.child("step", "powerupLabelSize", "powerupLabels");
                // shared by the panel and the labels: dimmed only while both are off
                p.child("switch", "rideRefresh", "ridePanel|powerupLabels");
            }
            case "trading" -> {
                p.head("wynnlabs.rules.sec_trade");
                p.row("slots", "market_slots");
                p.row("rule", "tm_price");
                p.row("rule", "keep_roll");
                p.row("rule", "reveal_profit");
                p.row("rule", "market_fee");
                p.head("wynnlabs.settings.sec_in_tooltips");
                p.row("switch", "tooltipPrice");
                p.row("switch", "boxPrices");
                p.row("switch", "boxCompactTooltip");
                p.head("wynnlabs.settings.sec_live");
                p.row("switch", "priceApi");
                p.child("keynote", "key", "priceApi");
            }
            case "online" -> {
                p.head("wynnlabs.settings.sec_downloads");
                p.row("switch", "priceApi");
                p.row("switch", "questGuides");
                p.head("wynnlabs.settings.sec_updates");
                p.row("choice", "updates");
            }
            case "advanced" -> {
                p.head("wynnlabs.settings.sec_character");
                p.row("notice", "rules_notice");
                p.row("advice", "advice_here");
                p.head("wynnlabs.settings.sec_rules");
                p.row("preset", "preset");
                p.fold("wynnlabs.rules.sec_sell", "sell");
                p.rules("too_low", "too_high", "reroll");
                p.fold("wynnlabs.rules.sec_shopping", "shop");
                p.rules("old_gear", "min_gain", "effort", "cost_tie", "ahead", "off_skills", "keep_milestone");
                p.row("rulemulti", "hide");
                p.fold("wynnlabs.rules.sec_quests", "quest");
                p.rules("quest_margin", "grind_below", "grind_above");
                p.fold("wynnlabs.settings.sec_look", "look");
                p.row("switch", "webFont");
                p.row("switch", "menuResume");
                p.fold("wynnlabs.settings.sec_dev", "dev");
                p.row("switch", "debug");
            }
            default -> {
            }
        }
        return p.rows;
    }

    /** Builds a page: rows go into the section opened last (its fold, when it folds). */
    private static final class Page {
        final List<SetRow> rows = new ArrayList<>();
        String fold;

        void head(String title) {
            fold = null;
            rows.add(new SetRow("head", title, null, null));
        }

        void fold(String title, String id) {
            fold = id;
            rows.add(new SetRow("head", title, null, id));
        }

        void row(String kind, String id) {
            rows.add(new SetRow(kind, id, null, fold));
        }

        void child(String kind, String id, String parent) {
            rows.add(new SetRow(kind, id, parent, fold));
        }

        void rules(String... ids) {
            for (String id : ids) {
                row("rule", id);
            }
        }
    }

    /** The section titles that carry the "saved for this character" mark. */
    static boolean perCharacter(String headId) {
        return headId.equals("wynnlabs.rules.sec_trade") || headId.equals("wynnlabs.settings.sec_rules")
                || headId.equals("wynnlabs.settings.sec_character") || headId.equals("wynnlabs.settings.sec_presets");
    }

    /** Whether the row works now: its parent switch on, its parent choice not Off (both parents off: dimmed). */
    static boolean active(SetRow r) {
        if (r.parent() == null) {
            return true;
        }
        for (String p : r.parent().split("\\|")) {
            if (Settings.CHOICES.containsKey(p) ? Settings.choice(p) > 0 : Settings.on(p)) {
                return true;
            }
        }
        return false;
    }

    /** A row that flips with a click anywhere on it. */
    static boolean isToggle(SetRow r) {
        return r.kind().equals("switch") || r.kind().equals("advice");
    }

    static boolean toggleOn(SetRow r) {
        return switch (r.kind()) {
            case "switch" -> Settings.on(r.id());
            case "advice" -> Rules.adviceHere();
            case "slots" -> MarketSlots.limited();
            default -> false;
        };
    }

    /** A multi row's option {@code i}: on or off. */
    static boolean multiOn(SetRow r, int i) {
        String k = MULTI.get(r.id()).keys()[i];
        return r.kind().equals("rulemulti") ? RuleEditor.value(RuleEditor.byId(k)) > 0 : Settings.on(k);
    }

    /** Whether the row has a default to go back to. */
    static boolean hasDefault(SetRow r) {
        return switch (r.kind()) {
            case "switch", "multi", "choice", "step", "advice", "fill", "lang", "rule", "rulemulti", "preset", "slots", "profiles" -> true;
            default -> false;
        };
    }

    /** Whether the row differs from its default: a gold dot in the list, a count on its page. */
    static boolean rowChanged(SetRow r) {
        return switch (r.kind()) {
            case "switch" -> Settings.changed(r.id());
            case "multi" -> {
                for (String k : MULTI.get(r.id()).keys()) {
                    if (Settings.changed(k)) {
                        yield true;
                    }
                }
                yield false;
            }
            case "rulemulti" -> {
                for (String k : MULTI.get(r.id()).keys()) {
                    if (RuleEditor.changed(RuleEditor.byId(k))) {
                        yield true;
                    }
                }
                yield false;
            }
            case "choice" -> Settings.choice(r.id()) != Settings.CHOICES.get(r.id());
            case "step" -> Settings.stepValue(r.id()) != Settings.stepDefault(r.id());
            case "advice" -> !Rules.adviceHere();
            case "lang" -> Settings.number("langOverride", 0) != 0;
            case "fill" -> menuFill() != 100;
            // a rule the preset sets counts once, on the preset's row, unless it was set by hand
            case "rule" -> RuleEditor.changed(r.r()) && (!RuleEditor.PRESET_RULES.contains(r.id()) || RuleEditor.preset() < 0);
            case "preset" -> RuleEditor.preset() != RuleEditor.NORMAL;
            case "profiles" -> Settings.activePreset() != null;
            case "slots" -> !MarketSlots.limited() || RuleEditor.changed(RuleEditor.byId("market_slots"));
            default -> false;
        };
    }

    /** The changed rows of a page, each setting once. */
    static int changedIn(String cat) {
        Set<String> seen = new LinkedHashSet<>();
        for (SetRow r : settingRows(cat)) {
            if (!r.head() && rowChanged(r)) {
                seen.add(r.key());
            }
        }
        return seen.size();
    }

    /** How many settings differ from their default, over every page, each once. */
    static int changedTotal() {
        Set<String> seen = new LinkedHashSet<>();
        for (String c : SETTING_CATS) {
            for (SetRow r : settingRows(c)) {
                if (!r.head() && rowChanged(r)) {
                    seen.add(r.key());
                }
            }
        }
        return seen.size();
    }

    /** Menu size: Auto (the whole screen, the scale picked for the window), 90% or 80% of it (MenuLayout). */
    static final int[] FILLS = {100, 90, 80};

    /** The menu size in use, one of {@link #FILLS}. */
    static int menuFill() {
        int v = Settings.number("menuFill", 100);
        return v >= 95 ? 100 : v >= 85 ? 90 : 80;
    }
}
