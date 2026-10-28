package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.build.ArchetypeMix;
import io.github.anilken.wynnlabs.build.TreePlanner;
import io.github.anilken.wynnlabs.core.L;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The archetype list under an archetype button (the home's HUD, the tree's chip, the setting):
 * the class's archetypes, each with a line of what it plays like, and Automatic (no tree plan, the
 * build follows the one you lean to). The one you have the most nodes in is marked Suggested.
 * Under "Combined" the class's pairs of archetypes (known hybrids first), half each; the picked
 * pair offers its other splits (70/30, 30/70) right under it. A row sets the choice
 * ({@link TreePlanner#choose}).
 */
final class ArchetypePicker extends MenuPaint {

    private boolean open;
    private int[] anchor;
    private int[] box;
    private final List<Object[]> rows = new ArrayList<>();     // {int[] rect, choice (ArchetypeMix#spec, "" automatic)}
    /** The choice of each row last drawn, by its id (null for a heading): the keys pick from it. */
    private final List<String> specs = new ArrayList<>();

    ArchetypePicker(RoadmapScreen menu) {
        super(menu);
    }

    /** Opens under {@code anchor} (x, y, w, h: the button clicked). */
    void open(int[] anchor) {
        this.anchor = anchor;
        open = true;
        state.reset();
    }

    /** Opens under this frame's click area with that action (the button just clicked). */
    void openAt(String action) {
        int[] at = {menu.frameMx, menu.frameMy, 1, 1};
        for (MenuHit h : menu.hits) {
            if (action.equals(h.copy())) {
                at = new int[] {h.x(), h.y(), h.w(), h.h()};
            }
        }
        open(at);
    }

    void close() {
        open = false;
        box = null;
    }

    /** The language key of an archetype's one-line description: "Battle Monk" → wynnlabs.arch.desc.battlemonk. */
    static String descKey(String archetype) {
        return "wynnlabs.arch.desc." + archetype.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    /**
     * The archetype to mark: the one you have the most nodes in ({@link TreePlanner#mostUnlocked}),
     * "" (Automatic) while you have none of any, null when the tree is unknown.
     */
    static String suggested(List<String> archetypes, String most, int mostNodes) {
        if (archetypes.isEmpty()) {
            return null;
        }
        return !most.isEmpty() && mostNodes > 0 ? most : "";
    }

    private final UiMenu.State state = new UiMenu.State();

    boolean isOpen() {
        return open && anchor != null;
    }

    /**
     * The shared dropdown under the button: one row per archetype with what it plays like under its
     * name, "Suggested" on the right of the one you lean to (why, in its tooltip), Automatic last
     * after a hairline. Drawn over everything with the real mouse; returns its box.
     */
    int[] draw(GuiGraphics g, int mx, int my) {
        if (!isOpen()) {
            return null;
        }
        rows.clear();
        specs.clear();
        List<String> archs = TreePlanner.archetypes();
        String most = TreePlanner.mostUnlocked();
        int mostNodes = most.isEmpty() ? 0 : TreePlanner.unlockedIn(most);
        String sugg = suggested(archs, most, mostNodes);
        ArchetypeMix chosen = TreePlanner.chosenMix();
        String why = sugg == null ? L.t("wynnlabs.arch.no_tree")
                : sugg.isEmpty() ? L.t("wynnlabs.arch.why_auto") : L.t("wynnlabs.arch.why_nodes", sugg, mostNodes);
        List<UiMenu.Item> items = new ArrayList<>();
        for (String a : archs) {
            items.add(single(a, a.equals(sugg), why, !chosen.hybrid() && a.equalsIgnoreCase(chosen.first())));
        }
        // two combined: the pairs, the picked one with its other splits under it
        List<ArchetypeMix> pairs = ArchetypeMix.pairs(archs);
        if (!pairs.isEmpty()) {
            items.add(UiMenu.Item.heading(L.t("wynnlabs.arch.combined")).group());
            specs.add(null);
            for (ArchetypeMix pair : pairs) {
                boolean on = pair.samePair(chosen);
                // the pair in its row's order: the picked split as the first's share
                int split = on ? (int) Math.round(chosen.weight(pair.first()) * 100) : 50;
                ArchetypeMix shown = ArchetypeMix.of(pair.first(), pair.second(), split);
                items.add(row(shown.spec(), pair.first() + " + " + pair.second(), split + "/" + (100 - split), on,
                        L.t("wynnlabs.arch.combo_tip", pair.first(), pair.second(), split + "/" + (100 - split))));
                if (on) {
                    for (int sp : ArchetypeMix.SPLITS) {
                        if (sp != split) {
                            String more = sp == 50 ? L.t("wynnlabs.arch.split_even") : L.t("wynnlabs.arch.split_more", sp > 50 ? pair.first() : pair.second());
                            items.add(row(ArchetypeMix.of(pair.first(), pair.second(), sp).spec(),
                                    L.t("wynnlabs.arch.split", sp + "/" + (100 - sp)), more, false,
                                    L.t("wynnlabs.arch.split_tip", sp / 10, pair.first(), pair.second())));
                        }
                    }
                }
            }
        }
        items.add(single("", "".equals(sugg), why, chosen.auto()).group());
        box = UiMenu.draw(g, font(), UiMenu.Spec.under(anchor, menu.width, menu.height).width(170).rows(14), state, items,
                mx, my, r -> {
                    rows.add(new Object[] {new int[] {r.x(), r.y(), r.w(), r.h()}, specs.get(Integer.parseInt(r.item().id()))});
                    menu.hits.add(new MenuHit(r.x(), r.y(), r.w(), r.h(), null, r.item().tip(), null));
                });
        return box;
    }

    /** One archetype's row (or Automatic's, ""): what it plays like under it, Suggested on the one you lean to. */
    private UiMenu.Item single(String a, boolean tag, String why, boolean on) {
        List<Component> tip = new ArrayList<>();
        tip.add(Component.literal(label(a)).withStyle(net.minecraft.ChatFormatting.AQUA));
        if (!desc(a).isEmpty()) {
            tip.add(Component.literal(desc(a)).withStyle(net.minecraft.ChatFormatting.GRAY));
        }
        if (tag) {
            tip.add(Component.literal(why).withStyle(net.minecraft.ChatFormatting.GOLD));
        }
        UiMenu.Item it = UiMenu.Item.of(String.valueOf(specs.size()), null, label(a), tag ? L.t("wynnlabs.arch.suggested") : null, on)
                .tip(tip);
        specs.add(a);
        return desc(a).isEmpty() ? it : it.sub(desc(a));
    }

    /** A combined choice's row: its label, the split or what it leans to on the right, one line of tooltip. */
    private UiMenu.Item row(String spec, String label, String meta, boolean on, String tipLine) {
        List<Component> tip = List.of(Component.literal(label).withStyle(net.minecraft.ChatFormatting.AQUA),
                Component.literal(tipLine).withStyle(net.minecraft.ChatFormatting.GRAY));
        UiMenu.Item it = UiMenu.Item.of(String.valueOf(specs.size()), null, label, meta, on).tip(tip);
        specs.add(spec);
        return it;
    }

    /** A click while the list is open: a row sets that archetype; anywhere, the list closes. Always used up. */
    boolean clicked(double ex, double ey) {
        if (!open) {
            return false;
        }
        if (box != null && in(ex, ey, box) && state.press(ex, ey)) {
            return true;
        }
        close();
        for (Object[] r : rows) {
            if (in(ex, ey, (int[]) r[0])) {
                pick((String) r[1]);
                return true;
            }
        }
        return true;
    }

    private void pick(String a) {
        close();
        ArchetypeMix next = ArchetypeMix.parse(a);
        if (!next.same(TreePlanner.chosenMix())) {
            TreePlanner.choose(a);
            menu.flash(L.t("wynnlabs.home.archetype_set", next.auto() ? label("") : next.label()));
            menu.rebuildMap();              // the build follows the archetype: rebuilt in the background
        }
    }

    int[] box() {
        return box;
    }

    /** A drag with the list's scroll bar held. */
    boolean drag(double y) {
        return open && state.drag(y);
    }

    void release() {
        state.release();
    }

    /** The wheel over the list scrolls it. */
    void wheel(double v) {
        state.wheel(v);
    }

    /** Esc closes the list, not the menu. */
    boolean keyPressed(int key) {
        if (!open) {
            return false;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        UiMenu.Item it = state.key(key);
        if (it != null && it.id() != null) {
            int i = Integer.parseInt(it.id());
            if (i < specs.size() && specs.get(i) != null) {
                pick(specs.get(i));
            }
        }
        return true;
    }

    private static String label(String archetype) {
        return archetype.isEmpty() ? L.t("wynnlabs.settings.archetype_off") : archetype;
    }

    /** What the archetype plays like; "" for one the language files do not know. */
    private static String desc(String archetype) {
        if (archetype.isEmpty()) {
            return L.t("wynnlabs.arch.auto_desc");
        }
        String key = descKey(archetype);
        String d = L.t(key);
        return d.equals(key) ? "" : d;
    }
}
