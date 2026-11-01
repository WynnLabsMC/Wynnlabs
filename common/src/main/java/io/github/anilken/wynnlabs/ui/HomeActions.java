package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;
import io.github.anilken.wynnlabs.build.Around;
import io.github.anilken.wynnlabs.build.Locks;
import io.github.anilken.wynnlabs.build.Plans;
import io.github.anilken.wynnlabs.build.Setup;
import io.github.anilken.wynnlabs.build.roadmap.Roadmap;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Rules;
import io.github.anilken.wynnlabs.quests.Waypoints;

/** The Home's {@code #q} actions: the build line, the plan, the goal, the road map, powders, archetype, a place to mark. */
final class HomeActions {

    private final RoadmapScreen menu;
    private final HomePage page;

    HomeActions(RoadmapScreen menu, HomePage page) {
        this.menu = menu;
        this.page = page;
    }

    /** A home {@code #q} action (the build line, the plan, the goal, the road map, powders, archetype, a place to mark). */
    void run(String c, int button) {
        if (c.startsWith("#qchoices:")) {
            page.openChoices();
        } else if (c.startsWith("#qkeep:")) {
            String name = c.substring("#qkeep:".length());
            io.github.anilken.wynnlabs.build.MapChoices.toggleKeep(name);
            menu.flash(L.t(io.github.anilken.wynnlabs.build.MapChoices.current().kept().contains(name)
                    ? "wynnlabs.choices.kept_on" : "wynnlabs.choices.kept_off", name));
            menu.rebuildMap();
        } else if (c.startsWith("#qban:")) {
            String name = c.substring("#qban:".length());
            io.github.anilken.wynnlabs.build.MapChoices.toggleBan(name);
            menu.flash(L.t(io.github.anilken.wynnlabs.build.MapChoices.current().banned().contains(name)
                    ? "wynnlabs.choices.ban_on" : "wynnlabs.choices.ban_off", name));
            menu.rebuildMap();
        } else if (c.startsWith("#qminlv:")) {
            io.github.anilken.wynnlabs.build.MapChoices.setMinLevel(Integer.parseInt(c.substring("#qminlv:".length())));
            menu.rebuildMap();
        } else if (c.startsWith("#qhist:")) {
            boolean back = io.github.anilken.wynnlabs.build.roadmap.Roadmap.restore(Long.parseLong(c.substring("#qhist:".length())));
            menu.flash(L.t(back ? "wynnlabs.choices.restored" : "wynnlabs.choices.restore_busy"));
        } else if (c.startsWith("#qadvice:")) {
            Rules.setAdviceHere(true);
            menu.rebuildMap();
        } else if (c.startsWith("#qpick:")) {
            if (button == 1) {
                // right-click: the plan removed, as its pill's ✕ does (Undo on the build line);
                // with no plan, the chosen items cleared
                if (!page.removePlan()) {
                    Locks.clear();
                    Around.forget();
                    menu.flash(L.t("wynnlabs.homev.pick_cleared"));
                }
            } else {
                BuildWindow.INSTANCE.openPick();
            }
        } else if (c.startsWith("#qplanremove:")) {
            page.removePlan();
        } else if (c.startsWith("#qplansave:")) {
            boolean was = Plans.isSaved(Plans.current());
            Plans.Build kept = Plans.savePlan(System.currentTimeMillis());
            if (kept != null) {
                menu.flash(L.t(was ? "wynnlabs.homev.plan_updated" : "wynnlabs.homev.plan_saved_flash", kept.name()));
            }
        } else if (c.startsWith("#qplanedit:")) {
            BuildWindow.INSTANCE.editPlan(Plans.current());
        } else if (c.startsWith("#qplanundo:")) {
            Plans.undoRemove(System.currentTimeMillis());
        } else if (c.startsWith("#qsaved:")) {
            BuildWindow.INSTANCE.openSaved();
        } else if (c.startsWith("#qhint:")) {
            if (button == 1) {
                Plans.HINT.dismiss();
            } else {
                BuildWindow.INSTANCE.openResults(Around.checkResult());
            }
        } else if (c.startsWith("#qstatsclear:")) {
            page.stats().clearPick();               // the stats list's ✕: the applied plan again
        } else if (c.startsWith("#qstatssrc:")) {
            page.stats().picker.toggle();           // the compare button: its list
        } else if (c.startsWith("#qstatsset:")) {
            page.stats().source.choose(c.substring(11));
        } else if (c.startsWith("#qstatstab:")) {
            page.stats().spellsTab = c.endsWith("spells");     // the compact column's tabs
        } else if (c.startsWith("#qstatsflip:")) {
            page.stats().source.flip();             // your numbers, or the other build's
        } else if (c.startsWith("#qgoal:")) {
            page.goalOpen = true;               // the list opens; a row picks the goal
            page.goals().goalMenu.reset();
        } else if (c.startsWith("#qrefresh:") || c.startsWith("#qbuild:")) {
            Roadmap.forceRefresh();
            menu.refreshAsked = true;
        } else if (c.startsWith("#qcancelbuild:")) {
            Roadmap.cancelBuild();
            menu.refreshAsked = false;
        } else if (c.startsWith("#qxp:")) {
            boolean on = !Setup.countsXp();
            Setup.setCountsXp(on);
            menu.flash(L.t(on ? "wynnlabs.home.count_xp_on" : "wynnlabs.home.count_xp_off"));
            menu.rebuildMap();                     // rebuilt in the background, picked up when ready
        } else if (c.startsWith("#qpowder:")) {
            boolean on = Rules.sub("model", "assumePowders", 1) > 0;
            JsonObject pm = new JsonObject();
            pm.addProperty("assumePowders", on ? 0 : 1);
            JsonObject pc = new JsonObject();
            pc.add("model", pm);
            Rules.update(pc);
            menu.flash(L.t(on ? "wynnlabs.home.powder_off" : "wynnlabs.home.powder_on"));
            menu.rebuildMap();                     // rebuilt in the background, picked up when ready
        } else if (c.startsWith("#qwbexport:")) {
            UiExport.copy(UiExport.HOME, io.github.anilken.wynnlabs.build.WynnBuilderExport.worn());
        } else if (c.startsWith("#qgearwb:")) {
            page.alts().exportShown();             // the Gear header's ↗: the set shown at the milestone
        } else if (c.startsWith("#qaltwb:")) {
            page.alts().export(Integer.parseInt(c.substring(8)));
        } else if (c.startsWith("#qaltpick:")) {
            page.alts().pick(Integer.parseInt(c.substring(10)));     // the menu stays open on the card
        } else if (c.startsWith("#qalt:")) {
            page.alts().openMenu();
        } else if (c.startsWith("#qarch:")) {
            menu.archPicker.openAt(c);             // the list opens; a row picks the archetype
        } else if (c.startsWith("#qnear:")) {
            String[] f = c.substring(7).split(" ", 4);
            boolean ok = Waypoints.go(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3]);
            menu.flash(ok ? L.t("wynnlabs.home.marked", f[3]) : L.t("wynnlabs.home.mark_failed"));
        }
    }
}
