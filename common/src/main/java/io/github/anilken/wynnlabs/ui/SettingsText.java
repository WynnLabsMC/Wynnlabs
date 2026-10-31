package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.MarketSlots;
import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.build.TreePlanner;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.RuleEditor;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.ui.SettingsRows.SetRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What a settings row says: its name, its description and example (shown on the picked row),
 * its default, its value in a few characters, its chips' labels, and whether it matches a search.
 */
final class SettingsText {

    private SettingsText() {}

    static String rowTitle(SetRow r) {
        return switch (r.kind()) {
            case "switch" -> L.t(Settings.LABELS.get(r.id()));
            case "multi", "choice", "rulemulti" -> L.t("wynnlabs.setting." + r.id());
            case "step" -> L.t(Settings.STEP_LABELS.get(r.id()));
            case "advice" -> L.t("wynnlabs.settings.advice_here");
            case "arch" -> L.t("wynnlabs.settings.arch_title");
            case "goal" -> L.t("wynnlabs.settings.goal_title");
            case "lang" -> L.t("wynnlabs.settings.language");
            case "fill" -> L.t("wynnlabs.settings.menu_fill");
            case "keynote" -> L.t("wynnlabs.settings.key_title");
            case "rule" -> L.t(r.r().title());
            case "preset" -> L.t("wynnlabs.rules.preset");
            case "profiles" -> L.t("wynnlabs.settings.profiles");
            case "slots" -> L.t("wynnlabs.rules.market_slots");
            case "notice" -> L.t("wynnlabs.rules.per_character");
            default -> L.t(r.id());
        };
    }

    static String rowDesc(SetRow r) {
        return switch (r.kind()) {
            case "switch", "step", "choice" -> L.t(Settings.DESC.get(r.id()));
            case "multi", "rulemulti" -> L.t("wynnlabs.setting_desc." + r.id());
            case "advice" -> L.t("wynnlabs.settings.advice_here_desc");
            case "arch" -> L.t("wynnlabs.settings.arch_desc");
            case "goal" -> L.t("wynnlabs.settings.goal_desc");
            case "lang" -> L.t("wynnlabs.settings.language_desc");
            case "fill" -> L.t("wynnlabs.settings.menu_fill_desc");
            case "keynote" -> L.t("wynnlabs.settings.key_hint");
            case "rule" -> L.t(r.r().desc());
            case "preset" -> L.t("wynnlabs.rules.preset_desc");
            case "profiles" -> L.t("wynnlabs.settings.profiles_desc");
            case "slots" -> L.t("wynnlabs.rules.market_slots_desc");
            default -> "";
        };
    }

    /** A short note after the row's name while it is off ("Turn on for exact values"); null for none. */
    static String rowHint(SetRow r) {
        return r.kind().equals("switch") && r.id().equals("rideRefresh") && !Settings.on("rideRefresh")
                ? L.t("wynnlabs.settings.hint.rideRefresh") : null;
    }

    /** The chips of a choice, multi or preset row, in order. */
    static List<String> options(SetRow r) {
        List<String> out = new ArrayList<>();
        switch (r.kind()) {
            case "multi", "rulemulti" -> {
                for (String k : SettingsRows.MULTI.get(r.id()).labels()) {
                    out.add(L.t(k));
                }
            }
            case "choice" -> {
                for (int i = 0; i < 3; i++) {
                    out.add(L.t("wynnlabs.settings.opt." + r.id() + "." + i));
                }
            }
            case "lang" -> out.addAll(List.of(langNames()));
            case "fill" -> {
                out.add(L.t("wynnlabs.settings.lang_auto"));
                out.add(RoadmapScreen.pct(SettingsRows.FILLS[1]));
                out.add(RoadmapScreen.pct(SettingsRows.FILLS[2]));
            }
            case "preset" -> {
                for (int i = 0; i < 3; i++) {
                    out.add(L.t("wynnlabs.rules.preset." + i));
                }
            }
            case "profiles" -> {
                // "@..." are the buttons and the name box between the chips, not words
                if (SettingsRows.profileEdit != 0) {
                    out.addAll(List.of("@box", "@ok", "@cancel"));
                } else {
                    out.add(L.t("wynnlabs.settings.profile_shared"));
                    out.addAll(Settings.presetNames());
                    if (Settings.activePreset() != null) {
                        out.add("@edit");
                    }
                    out.add("@add");
                    if (Settings.activePreset() != null) {
                        out.add("@del");
                    }
                }
            }
            default -> {
            }
        }
        return out;
    }

    /** Which chips are picked. */
    static boolean picked(SetRow r, int i) {
        return switch (r.kind()) {
            case "multi", "rulemulti" -> SettingsRows.multiOn(r, i);
            case "choice" -> Settings.choice(r.id()) == i;
            case "lang" -> Math.max(0, Math.min(2, Settings.number("langOverride", 0))) == i;
            case "fill" -> SettingsRows.menuFill() == SettingsRows.FILLS[i];
            case "preset" -> RuleEditor.preset() == i;
            case "profiles" -> {
                String active = Settings.activePreset();
                yield active == null ? i == 0 : i > 0 && i <= Settings.presetNames().size()
                        && Settings.presetNames().get(i - 1).equals(active);
            }
            default -> false;
        };
    }

    /** What the row's value is in a few characters ("6 lv", "30 EB", "Boltslinger"); "" for a switch. */
    static String shortValue(SetRow r) {
        return switch (r.kind()) {
            case "rule" -> L.cap(RuleEditor.display(r.r()));
            case "slots" -> RuleEditor.value(RuleEditor.byId("market_slots")) <= 0 ? MarketSlots.autoShort()
                    : String.valueOf(MarketSlots.limit());
            case "step" -> stepText(r.id(), Settings.stepValue(r.id()));
            case "arch" -> TreePlanner.chosenLabel(L.t("wynnlabs.settings.archetype_off"));
            case "goal" -> Setup.goalName(Setup.goal());
            case "keynote" -> L.t(Prices.embeddedKeyInUse() && !Prices.keyInvalid ? "wynnlabs.settings.key_state_builtin"
                    : Prices.maskedKey() == null ? "wynnlabs.settings.key_state_none"
                    : Prices.keyInvalid ? "wynnlabs.settings.key_state_invalid" : "wynnlabs.settings.key_state_saved");
            default -> "";
        };
    }

    /** "Default: on", "Default 4 lv · From 1 to 20"; null for a row without a default. */
    static String rowDefault(SetRow r) {
        String on = L.t("wynnlabs.settings.state_on");
        String off = L.t("wynnlabs.settings.state_off");
        return switch (r.kind()) {
            case "switch" -> L.t("wynnlabs.settings.default", Settings.byDefault(r.id()) ? on : off);
            case "advice" -> L.t("wynnlabs.settings.default", on);
            case "multi" -> {
                List<String> names = new ArrayList<>();
                SettingsRows.Multi m = SettingsRows.MULTI.get(r.id());
                for (int i = 0; i < m.keys().length; i++) {
                    if (Settings.byDefault(m.keys()[i])) {
                        names.add(L.t(m.labels()[i]));
                    }
                }
                yield L.t("wynnlabs.settings.default", names.isEmpty() ? off
                        : names.size() == m.keys().length ? L.t("wynnlabs.settings.all_on") : String.join(", ", names));
            }
            case "rulemulti" -> L.t("wynnlabs.settings.default", L.t("wynnlabs.settings.all_on"));
            case "choice" -> L.t("wynnlabs.settings.default", L.t("wynnlabs.settings.opt." + r.id() + "." + Settings.CHOICES.get(r.id())));
            case "step" -> {
                int[] options = Settings.STEPS.get(r.id());
                yield L.t("wynnlabs.settings.default_range", stepText(r.id(), Settings.stepDefault(r.id())),
                        stepText(r.id(), options[0]), stepText(r.id(), options[options.length - 1]));
            }
            case "lang", "fill" -> L.t("wynnlabs.settings.default", L.t("wynnlabs.settings.lang_auto"));
            case "preset" -> L.t("wynnlabs.settings.default", L.t("wynnlabs.rules.preset.1"));
            case "profiles" -> L.t("wynnlabs.settings.default", L.t("wynnlabs.settings.profile_shared"));
            case "slots" -> L.t("wynnlabs.rules.market_slots_default", MarketSlots.MAX);
            case "rule" -> {
                RuleEditor.Rule rule = r.r();
                double[] range = RuleEditor.range(rule);
                yield L.t("wynnlabs.settings.default_range", RuleEditor.format(rule, rule.def()),
                        RuleEditor.format(rule, range[0]), RuleEditor.format(rule, range[1]));
            }
            default -> null;
        };
    }

    /** The row's example ("wynnlabs.settings.example." + its id), filled with what the value means now. */
    static String rowExample(SetRow r) {
        String id = r.id();
        Object[] args = new Object[0];
        switch (r.kind()) {
            case "step" -> args = new Object[] {Settings.stepValue(id)};
            case "slots" -> {
                args = new Object[] {MarketSlots.limit()};
                id = "market_slots";
            }
            case "rule" -> {
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
                    default -> new Object[] {RuleEditor.format(rule, v)};
                };
            }
            case "notice" -> {
                return "";
            }
            case "keynote" -> id = "key";
            case "advice" -> id = "advice_here";
            default -> {
            }
        }
        return L.t("wynnlabs.settings.example." + id, args);
    }

    /** "Shaman, lv. 44": whose rules these are. */
    static String character() {
        GameState s = GameState.current();
        Klass k = s.classType();
        if (k == null || k == Klass.NONE) {
            return L.t("wynnlabs.rules.this_character");
        }
        return L.t("wynnlabs.rules.character", L.cap(k.name().toLowerCase(Locale.ROOT)), s.level());
    }

    /** The language choices: Auto (follows the game), EN, TR. */
    static String[] langNames() {
        return new String[] {L.t("wynnlabs.settings.lang_auto"), "EN", "TR"};
    }

    /** A number setting's value as its row writes it ("85%"). */
    static String stepText(String key, int v) {
        String zero = Settings.STEP_ZERO.get(key);
        if (v == 0 && zero != null) {
            return L.t(zero);
        }
        String fmt = Settings.STEP_FORMAT.get(key);
        return fmt == null ? String.valueOf(v) : L.t(fmt, v);
    }

    /** False while the key built into the mod works: the key row then asks for nothing. */
    static boolean keyBoxNeeded() {
        return !(Prices.embeddedKeyInUse() && !Prices.keyInvalid);
    }

    /** Whether the row matches what was typed in the search box (its name, chips, description or example). */
    static boolean matches(SetRow r, String query) {
        if (r.head() || r.kind().equals("notice") || query.isBlank()) {
            return false;
        }
        String q = query.strip().toLowerCase(Locale.ROOT);
        String text = rowTitle(r) + "\n" + options(r).stream().filter(o -> !o.startsWith("@")).collect(java.util.stream.Collectors.joining("\n")) + "\n" + rowDesc(r) + "\n" + rowExample(r);
        return text.toLowerCase(Locale.ROOT).contains(q);
    }
}
