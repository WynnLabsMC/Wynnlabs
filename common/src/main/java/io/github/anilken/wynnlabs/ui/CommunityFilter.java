package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.CommunityBuilds;
import io.github.anilken.wynnlabs.build.Tuning;
import io.github.anilken.wynnlabs.model.Klass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Community tab's filters as values: class (yours or every class), level range, the play
 * style roles (each picked one must be on the build), the budget to finish, the owned share, and
 * outdated / crafted; the search over build names, item names and crafted ingredients; the sort;
 * and, when nothing matches, the one filter change that brings back the most builds.
 */
record CommunityFilter(boolean allClasses, int level, Set<String> roles, int budget, int own, boolean hideOutdated,
        boolean crafted, String query) {

    /** Level ranges: 0 your level, then fixed ranges, the last any level. */
    static final int[][] LEVELS = {{0, 0}, {1, 20}, {21, 40}, {41, 60}, {61, 80}, {81, 99}, {100, 121}, {1, 121}};
    static final int ANY_LEVEL = LEVELS.length - 1;
    static final int[] OWN = {0, 25, 50, 75, 100};

    static CommunityFilter defaults() {
        return new CommunityFilter(false, 0, Set.of(), 0, 0, false, false, "");
    }

    /** The search words without a leading "uses ". */
    String words() {
        String q = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        return q.startsWith("uses ") ? q.substring(5).strip() : q.startsWith("uses:") ? q.substring(5).strip() : q;
    }

    boolean matches(CommunityBuilds.Entry e, CommunityBuilds.Compare c, Klass k, int lv) {
        if (!allClasses && k != null && k != Klass.NONE && !e.forClass(k)) {
            return false;
        }
        if (level == 0) {
            // above the list's highest builds (gear stops before the level cap) the top builds are yours
            int at = Math.min(lv, io.github.anilken.wynnlabs.build.CommunityList.topLevel());
            if (lv > 0 && (at < e.levelMin() || at > e.levelMax())) {
                return false;
            }
        } else if (level != ANY_LEVEL) {
            int[] r = LEVELS[level];
            if (e.levelMax() < r[0] || e.levelMin() > r[1]) {
                return false;
            }
        }
        for (String role : roles) {
            if (!e.hasRole(role)) {
                return false;
            }
        }
        if (hideOutdated && e.outdated()) {
            return false;
        }
        if (budget > 0 && (c == null || c.knownCost() > Tuning.BUDGETS[budget])) {
            return false;
        }
        if (own > 0 && (c == null || c.ownedPct() < OWN[own])) {
            return false;
        }
        if (crafted && (c == null || c.pieces().stream().noneMatch(CommunityBuilds.Piece::crafted))) {
            return false;
        }
        String q = words();
        if (!q.isEmpty() && !e.name().toLowerCase(Locale.ROOT).contains(q)) {
            if (c == null) {
                return false;
            }
            boolean hit = false;
            for (CommunityBuilds.Piece p : c.pieces()) {
                if (p.name().toLowerCase(Locale.ROOT).contains(q)
                        || p.recipe() != null && p.recipe().slots().stream().anyMatch(s -> s != null && s.toLowerCase(Locale.ROOT).contains(q))) {
                    hit = true;
                    break;
                }
            }
            return hit;
        }
        return true;
    }

    List<CommunityBuilds.Entry> apply(List<CommunityBuilds.Entry> all, Map<String, CommunityBuilds.Compare> cmp, Klass k, int lv) {
        List<CommunityBuilds.Entry> out = new ArrayList<>();
        for (CommunityBuilds.Entry e : all) {
            if (matches(e, cmp.get(e.id()), k, lv)) {
                out.add(e);
            }
        }
        return out;
    }

    int count(List<CommunityBuilds.Entry> all, Map<String, CommunityBuilds.Compare> cmp, Klass k, int lv) {
        int n = 0;
        for (CommunityBuilds.Entry e : all) {
            if (matches(e, cmp.get(e.id()), k, lv)) {
                n++;
            }
        }
        return n;
    }

    CommunityFilter withClasses(boolean all) {
        return new CommunityFilter(all, level, roles, budget, own, hideOutdated, crafted, query);
    }

    CommunityFilter withLevel(int l) {
        return new CommunityFilter(allClasses, l, roles, budget, own, hideOutdated, crafted, query);
    }

    CommunityFilter withRole(String role, boolean on) {
        Set<String> r = new LinkedHashSet<>(roles);
        if (on) {
            r.add(role);
        } else {
            r.remove(role);
        }
        return new CommunityFilter(allClasses, level, Set.copyOf(r), budget, own, hideOutdated, crafted, query);
    }

    CommunityFilter withBudget(int b) {
        return new CommunityFilter(allClasses, level, roles, b, own, hideOutdated, crafted, query);
    }

    CommunityFilter withOwn(int o) {
        return new CommunityFilter(allClasses, level, roles, budget, o, hideOutdated, crafted, query);
    }

    CommunityFilter withOutdatedHidden(boolean on) {
        return new CommunityFilter(allClasses, level, roles, budget, own, on, crafted, query);
    }

    CommunityFilter withCrafted(boolean on) {
        return new CommunityFilter(allClasses, level, roles, budget, own, hideOutdated, on, query);
    }

    CommunityFilter withQuery(String q) {
        return new CommunityFilter(allClasses, level, roles, budget, own, hideOutdated, crafted, q);
    }

    /** Whether nothing narrows the list beyond the defaults (your class, your level). */
    boolean plain() {
        return equals(defaults().withQuery(query)) && words().isEmpty();
    }

    /** One way out of an empty list: what to change, the filter it gives and how many builds then show. */
    record Suggestion(String kind, String arg, CommunityFilter to, int count) {}

    /** The single change that brings back the most builds; null when none brings any. */
    Suggestion suggest(List<CommunityBuilds.Entry> all, Map<String, CommunityBuilds.Compare> cmp, Klass k, int lv) {
        List<Suggestion> tries = new ArrayList<>();
        if (!words().isEmpty()) {
            tries.add(new Suggestion("search", null, withQuery(""), 0));
        }
        if (!allClasses) {
            tries.add(new Suggestion("classes", null, withClasses(true), 0));
        }
        if (level != ANY_LEVEL) {
            tries.add(new Suggestion("level", null, withLevel(ANY_LEVEL), 0));
        }
        for (String r : roles) {
            tries.add(new Suggestion("role", r, withRole(r, false), 0));
        }
        if (budget > 0) {
            // the smallest raise that brings builds back, else no limit
            for (int b = budget + 1; b <= Tuning.BUDGETS.length; b++) {
                int to = b == Tuning.BUDGETS.length ? 0 : b;
                if (withBudget(to).count(all, cmp, k, lv) > 0 || to == 0) {
                    tries.add(new Suggestion(to == 0 ? "budget_none" : "budget", to == 0 ? null : Integer.toString(to), withBudget(to), 0));
                    break;
                }
            }
        }
        if (own > 0) {
            tries.add(new Suggestion("own", null, withOwn(0), 0));
        }
        if (hideOutdated) {
            tries.add(new Suggestion("outdated", null, withOutdatedHidden(false), 0));
        }
        if (crafted) {
            tries.add(new Suggestion("crafted", null, withCrafted(false), 0));
        }
        return tries.stream().map(s -> new Suggestion(s.kind(), s.arg(), s.to(), s.to().count(all, cmp, k, lv)))
                .filter(s -> s.count() > 0)
                .max(Comparator.comparingInt(Suggestion::count)).orElse(null);
    }
}
