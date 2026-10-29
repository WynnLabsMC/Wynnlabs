package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.MarketSlots;
import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.build.TreePlanner;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.RuleEditor;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.market.Prices;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The settings' model, without drawing: the categories, each one's rows (section titles, switches,
 * numbers, rules, choices, the key) and what a row says (its name, description, value, default and
 * example) and whether it differs from its default. {@link SettingsList} draws them.
 */
final class SettingsRows {

    private SettingsRows() {}

    static final String[] SETTING_CATS = {"about", "look", "tips", "glow", "coach", "quests", "build", "rules", "data"};

    static final String[] SETTING_CAT_NAMES = {"wynnlabs.settings.cat_about", "wynnlabs.settings.cat_look",
        "wynnlabs.settings.cat_tips", "wynnlabs.settings.cat_glow", "wynnlabs.settings.cat_coach", "wynnlabs.settings.cat_quests",
        "wynnlabs.settings.cat_build", "wynnlabs.settings.cat_rules", "wynnlabs.settings.cat_data"};

    static final String[] SETTING_CAT_DESC = {"wynnlabs.settings.catdesc_about", "wynnlabs.settings.catdesc_look",
        "wynnlabs.settings.catdesc_tips", "wynnlabs.settings.catdesc_glow", "wynnlabs.settings.catdesc_coach",
        "wynnlabs.settings.catdesc_quests", "wynnlabs.settings.catdesc_build", "wynnlabs.settings.catdesc_rules",
        "wynnlabs.settings.catdesc_data"};

    static final String[] SETTING_CAT_ICONS = {"info-circle", "eye", "tag", "sparkles", "user", "map-2", "shield",
        "filter", "world"};

    /** One row of a settings category: kind switch, step, advice, arch, goal, scale, rule or key; "head" is a section title. */
    record SetRow(String kind, String id, int rule) {

        boolean head() {
            return kind.equals("head");
        }

        RuleEditor.Rule r() {
            return RuleEditor.RULES.get(rule);
        }
    }

    /** A category's rows in the order the list shows them. */
    static List<SetRow> settingRows(String cat) {
        List<SetRow> rows = new ArrayList<>();
        if (cat.equals("about")) {
            return rows;
        }
        if (cat.equals("rules")) {
            for (RuleEditor.Section s : RuleEditor.SECTIONS) {
                rows.add(new SetRow("head", s.title(), -1));
                for (int i : s.rules()) {
                    rows.add(new SetRow("rule", RuleEditor.RULES.get(i).key(), i));
                }
            }
            return rows;
        }
        if (cat.equals("build")) {
            rows.add(new SetRow("head", "wynnlabs.settings.sec_build_goal", -1));
            rows.add(new SetRow("advice", "advice_here", -1));
            rows.add(new SetRow("arch", "archetype", -1));
            rows.add(new SetRow("goal", "goal", -1));
        }
        java.util.Set<String> done = new java.util.HashSet<>();
        for (String[] sec : SETTING_SECTIONS.getOrDefault(cat, List.of())) {
            rows.add(new SetRow("head", sec[0], -1));
            for (int j = 1; j < sec.length; j++) {
                if (sec[j].equals("menu_scale")) {
                    rows.add(new SetRow("scale", "menu_scale", -1));
                } else if (sec[j].equals("menu_fill")) {
                    rows.add(new SetRow("fill", "menu_fill", -1));
                } else if (sec[j].equals("language")) {
                    rows.add(new SetRow("lang", "language", -1));
                } else if (Settings.LABELS.containsKey(sec[j]) && cat.equals(Settings.category(sec[j]))) {
                    addSwitch(rows, sec[j]);
                    done.add(sec[j]);
                }
            }
        }
        for (String k : Settings.LABELS.keySet()) {
            if (cat.equals(Settings.category(k)) && !done.contains(k)) {
                addSwitch(rows, k);                     // one no section names yet: at the end
            }
        }
        return rows;
    }

    /** A switch, its number right under it, and the key right under live prices. */
    static void addSwitch(List<SetRow> rows, String k) {
        rows.add(new SetRow("switch", k, -1));
        for (var st : Settings.STEP_PARENT.entrySet()) {
            if (st.getValue().equals(k)) {
                rows.add(new SetRow("step", st.getKey(), -1));
            }
        }
        if (k.equals("priceApi")) {
            rows.add(new SetRow("key", "key", -1));
        }
    }

    /**
     * Each page's settings under section titles, as the Rules page has them: the title's key, then
     * the settings in order ("menu_scale" is the menu size row). A key a build does not have is
     * skipped; a setting no section names comes at the end.
     */
    static final Map<String, List<String[]>> SETTING_SECTIONS = Map.of(
            "look", List.<String[]>of(
                    new String[] {"wynnlabs.settings.sec_text", "webFont", "menuResume", "menu_scale", "menu_fill", "language"}),
            "tips", List.of(
                    new String[] {"wynnlabs.settings.sec_advice", "itemAdvice", "altAdvice", "wornHint"},
                    new String[] {"wynnlabs.settings.sec_tooltip", "powderHint", "tooltipPrice", "boxPrices", "bankHint",
                            "sourceHint"}),
            "glow", List.of(
                    new String[] {"wynnlabs.settings.sec_bag", "glowUpgrade", "glowShop", "sellGlow", "verdictBadges",
                            "hotbarBadges"},
                    new String[] {"wynnlabs.settings.sec_ground", "groundGlow", "groundBeam", "groundGlowShop",
                            "groundGlowNormal", "groundGlowOther"},
                    new String[] {"wynnlabs.settings.sec_tracked", "mobTrack", "mobTrackSound"},
                    new String[] {"wynnlabs.settings.sec_menus", "glowTree", "feederOverlay", "feederHighlight"}),
            "coach", List.of(
                    new String[] {"wynnlabs.settings.sec_chat", "chatUpgrade", "chatSkills", "chatLevelUp"},
                    new String[] {"wynnlabs.settings.sec_warnings", "dangerWarn", "survivalWarn", "coachToasts"},
                    new String[] {"wynnlabs.settings.sec_screen", "dpsHud", "sellSummary"}),
            "quests", List.<String[]>of(
                    new String[] {"wynnlabs.settings.sec_quests", "glowQuest", "miniQuests"}),
            "build", List.<String[]>of(
                    new String[] {"wynnlabs.settings.sec_tree", "menuAdvice", "autoTree",
                            "treeBonuses"}),
            "data", List.of(
                    new String[] {"wynnlabs.settings.sec_online", "priceApi", "questGuides", "updateCheck", "autoUpdate"},
                    new String[] {"wynnlabs.settings.sec_dev", "debug"}));

    static boolean isToggle(SetRow r) {
        return r.kind().equals("switch") || r.kind().equals("advice") || r.kind().equals("rule") && r.r().isSwitch();
    }

    static boolean toggleOn(SetRow r) {
        return switch (r.kind()) {
            case "switch" -> Settings.on(r.id());
            case "advice" -> Rules.adviceHere();
            default -> RuleEditor.value(r.r()) > 0;
        };
    }

    static String rowTitle(SetRow r) {
        return switch (r.kind()) {
            case "switch" -> L.t(Settings.LABELS.get(r.id()));
            case "step" -> L.t(Settings.STEP_LABELS.get(r.id()));
            case "advice" -> L.t("wynnlabs.settings.advice_here");
            case "arch" -> L.t("wynnlabs.settings.arch_title");
            case "goal" -> L.t("wynnlabs.settings.goal_title");
            case "scale" -> L.t("wynnlabs.settings.menu_scale");
            case "lang" -> L.t("wynnlabs.settings.language");
            case "fill" -> L.t("wynnlabs.settings.menu_fill");
            case "key" -> L.t("wynnlabs.settings.key_title");
            case "rule" -> L.t(r.r().title());
            default -> L.t(r.id());
        };
    }

    static String rowDesc(SetRow r) {
        return switch (r.kind()) {
            case "switch", "step" -> L.t(Settings.DESC.get(r.id()));
            case "advice" -> L.t("wynnlabs.settings.advice_here_desc");
            case "arch" -> L.t("wynnlabs.settings.arch_desc");
            case "goal" -> L.t("wynnlabs.settings.goal_desc");
            case "scale" -> L.t("wynnlabs.settings.menu_scale_desc");
            case "lang" -> L.t("wynnlabs.settings.language_desc");
            case "fill" -> L.t("wynnlabs.settings.menu_fill_desc");
            case "key" -> L.t("wynnlabs.settings.key_hint");
            case "rule" -> L.t(r.r().desc());
            default -> "";
        };
    }

    /** What the row's value is in a few characters ("6 lv", "30 EB", "Boltslinger"); "" for a switch. */
    static String shortValue(SetRow r) {
        return switch (r.kind()) {
            case "rule" -> r.r().key().equals("marketSlots") && RuleEditor.value(r.r()) <= 0
                    ? MarketSlots.autoShort() : L.cap(RuleEditor.display(r.r()));
            case "step" -> stepText(r.id(), Settings.stepValue(r.id()));
            case "arch" -> TreePlanner.chosenLabel(L.t("wynnlabs.settings.archetype_off"));
            case "goal" -> Setup.goalName(Setup.goal());
            case "scale" -> MenuScale.setting() == 0 ? L.t("wynnlabs.settings.scale_game") : String.valueOf(MenuScale.setting());
            case "lang" -> langNames()[Math.max(0, Math.min(2, Settings.number("langOverride", 0)))];
            case "fill" -> RoadmapScreen.pct(menuFill());
            case "key" -> L.t(Prices.embeddedKeyInUse() && !Prices.keyInvalid ? "wynnlabs.settings.key_state_builtin"
                    : Prices.maskedKey() == null ? "wynnlabs.settings.key_state_none"
                    : Prices.keyInvalid ? "wynnlabs.settings.key_state_invalid" : "wynnlabs.settings.key_state_saved");
            default -> "";
        };
    }

    /** Whether the row has a default to go back to. */
    static boolean hasDefault(SetRow r) {
        return switch (r.kind()) {
            case "switch", "step", "advice", "scale", "fill", "lang", "rule" -> true;
            default -> false;
        };
    }

    /** Whether the row differs from its default: a gold dot in the list, a count on its category's chip. */
    static boolean rowChanged(SetRow r) {
        return switch (r.kind()) {
            case "switch" -> Settings.changed(r.id());
            case "step" -> Settings.stepValue(r.id()) != Settings.stepDefault(r.id());
            case "advice" -> !Rules.adviceHere();
            case "scale" -> MenuScale.setting() != 0;
            case "lang" -> Settings.number("langOverride", 0) != 0;
            case "fill" -> menuFill() != 100;
            case "rule" -> RuleEditor.changed(r.r());
            default -> false;
        };
    }

    static int changedIn(String cat) {
        int n = 0;
        for (SetRow r : settingRows(cat)) {
            if (!r.head() && rowChanged(r)) {
                n++;
            }
        }
        return n;
    }

    /** "Default: on", "Default 4 lv · from 1 to 20"; null for a row without a default. */
    static String rowDefault(SetRow r) {
        String on = L.t("wynnlabs.settings.state_on");
        String off = L.t("wynnlabs.settings.state_off");
        return switch (r.kind()) {
            case "switch" -> L.t("wynnlabs.settings.default", Settings.byDefault(r.id()) ? on : off);
            case "advice" -> L.t("wynnlabs.settings.default", on);
            case "step" -> {
                int[] options = Settings.STEPS.get(r.id());
                yield L.t("wynnlabs.settings.default_range", stepText(r.id(), Settings.stepDefault(r.id())),
                        stepText(r.id(), options[0]), stepText(r.id(), options[options.length - 1]));
            }
            case "scale" -> {
                int pick = MenuScale.setting();
                int used = MenuScale.effective();
                String d = L.t("wynnlabs.settings.default", L.t("wynnlabs.settings.menu_scale_game"));
                yield pick > 0 && used != pick ? d + " · " + L.t("wynnlabs.settings.menu_scale_used", used) : d;
            }
            case "lang" -> L.t("wynnlabs.settings.default", langNames()[0]);
            case "fill" -> L.t("wynnlabs.settings.default", RoadmapScreen.pct(100));
            case "rule" -> {
                RuleEditor.Rule rule = r.r();
                if (rule.isSwitch()) {
                    yield L.t("wynnlabs.settings.default", on);
                }
                double[] range = RuleEditor.range(rule);
                yield L.t("wynnlabs.settings.default_range", RuleEditor.format(rule, rule.def()),
                        RuleEditor.format(rule, range[0]), RuleEditor.format(rule, range[1]));
            }
            default -> null;
        };
    }

    /** The row's example ("wynnlabs.settings.example." + its id), filled with what the value means now. */
    static String rowExample(SetRow r) {
        String id = r.kind().equals("rule") ? RuleEditor.id(r.r()) : r.id();
        Object[] args = new Object[0];
        if (r.kind().equals("step")) {
            args = new Object[] {Settings.stepValue(id)};
        }
        if (r.kind().equals("rule") && !r.r().isSwitch()) {
            RuleEditor.Rule rule = r.r();
            long v = Math.round(RuleEditor.value(rule));
            int lv = 80;                                    // the examples' character is level 80
            args = switch (id) {
                case "too_low" -> new Object[] {lv - v - 1, lv - v};
                case "too_high" -> new Object[] {lv + v + 1};
                case "old_gear", "quest_margin", "grind_below" -> new Object[] {lv - v};
                case "ahead", "grind_above" -> new Object[] {lv + v};
                case "min_gain" -> new Object[] {RuleEditor.format(rule, v), RuleEditor.format(rule, Math.max(0, v - 1))};
                case "reroll" -> new Object[] {RuleEditor.format(rule, Math.max(0, v - 1)), RuleEditor.format(rule, v)};
                case "market_slots" -> new Object[] {MarketSlots.slots()};
                default -> new Object[] {RuleEditor.format(rule, v)};
            };
        }
        return L.t("wynnlabs.settings.example." + id, args);
    }

    /** Appearance → Menu size: the menu at 100%, 90% or 80% of the screen (MenuLayout). */
    static final int[] FILLS = {100, 90, 80};

    /** The menu size in use, one of {@link #FILLS}. */
    static int menuFill() {
        int v = Settings.number("menuFill", 100);
        return v >= 95 ? 100 : v >= 85 ? 90 : 80;
    }

    /** The language choices: Auto (follows the game), EN, TR. */
    static String[] langNames() {
        return new String[] {L.t("wynnlabs.settings.lang_auto"), "EN", "TR"};
    }

    /** A number setting's value as its row writes it ("85%"). */
    static String stepText(String key, int v) {
        String fmt = Settings.STEP_FORMAT.get(key);
        return fmt == null ? String.valueOf(v) : L.t(fmt, v);
    }

    /** False while the key built into the mod works: the key row then asks for nothing. */
    static boolean keyBoxNeeded() {
        return !(Prices.embeddedKeyInUse() && !Prices.keyInvalid);
    }

    /** Whether the row matches what was typed in the search box (its name, description or example). */
    static boolean matches(SetRow r, String query) {
        if (r.head() || query.isBlank()) {
            return false;
        }
        String q = query.strip().toLowerCase(Locale.ROOT);
        return (rowTitle(r) + "\n" + rowDesc(r) + "\n" + rowExample(r)).toLowerCase(Locale.ROOT).contains(q);
    }

    /** How many settings differ from their default, over every category. */
    static int changedTotal() {
        int n = 0;
        for (String c : SETTING_CATS) {
            n += changedIn(c);
        }
        return n;
    }
}
