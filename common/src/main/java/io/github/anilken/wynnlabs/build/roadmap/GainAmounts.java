package io.github.anilken.wynnlabs.build.roadmap;

import io.github.anilken.wynnlabs.build.Build;
import io.github.anilken.wynnlabs.build.Damage;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.model.SkillKind;

import java.util.ArrayList;
import java.util.List;

/** A row's reasons as plain amounts, where a per cent of what you wear would say nothing. */
final class GainAmounts {

    private GainAmounts() {}

    /**
     * What build {@code b} adds over {@code a} as plain amounts, biggest share first ("Main Attack:
     * +1662", "Effective HP: +9399"), then the skill points: for a slot that had nothing, or a
     * piece's share of its set, where a per cent of what you wear would say nothing.
     */
    static List<String> amounts(Build.Eval a, Build.Eval b) {
        Damage.Result x = a.result();
        Damage.Result y = b.result();
        List<Object[]> lines = new ArrayList<>();
        amount(lines, L.t("wynnlabs.road.spell_damage"), x.spellDps(), y.spellDps());
        amount(lines, L.t("wynnlabs.road.main_attack"), x.mainDps(), y.mainDps());
        amount(lines, L.t("wynnlabs.road.effective_health"), x.ehp(), y.ehp());
        if (Math.round(y.speed()) != Math.round(x.speed())) {
            lines.add(new Object[] {Math.abs(y.speed() - x.speed()) / 100.0,
                    L.t("wynnlabs.road.walk_speed", L.pct(signed(x.speed())), L.pct(signed(y.speed())))});
        }
        lines.sort((p, q) -> Double.compare((double) q[0], (double) p[0]));
        List<String> skills = new ArrayList<>();
        boolean down = false;
        for (SkillKind s : SkillKind.values()) {
            int i = s.ordinal();
            if (a.allocation()[i] != b.allocation()[i]) {
                skills.add(s.displayName().substring(0, 3) + " " + a.allocation()[i] + "→" + b.allocation()[i]);
                down |= b.allocation()[i] < a.allocation()[i];
            }
        }
        List<String> out = new ArrayList<>();
        int room = skills.isEmpty() ? Roadmap.WHY_LINES : Roadmap.WHY_LINES - 1;
        for (int i = 0; i < lines.size() && out.size() < room; i++) {
            out.add((String) lines.get(i)[1]);
        }
        if (!skills.isEmpty()) {
            out.add(down ? L.t("wynnlabs.road.skills_respec", String.join(", ", skills))
                    : L.t("wynnlabs.road.skills", String.join(", ", skills)));
        }
        return out;
    }

    /** A "name: +n" line weighted by its share of the larger value; none under 2 % of it. */
    private static void amount(List<Object[]> lines, String name, double from, double to) {
        double top = Math.max(Math.abs(from), Math.abs(to));
        if (top <= 0 || Math.abs(to - from) < 0.02 * top) {
            return;
        }
        long d = Math.round(to - from);
        lines.add(new Object[] {Math.abs(to - from) / top, L.t("wynnlabs.road.amount", name, d > 0 ? "+" + d : "−" + Math.abs(d))});
    }

    private static String signed(double v) {
        long n = Math.round(v);
        return n > 0 ? "+" + n : String.valueOf(n);
    }
}
