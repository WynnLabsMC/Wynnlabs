package io.github.anilken.wynnlabs.ui;

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
 * A row sets the same choice the button used to cycle through ({@link TreePlanner#choose}).
 */
final class ArchetypePicker extends MenuPaint {

    private boolean open;
    private int[] anchor;
    private int[] box;
    private final List<Object[]> rows = new ArrayList<>();     // {int[] rect, archetype ("" automatic)}

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
        List<String> archs = TreePlanner.archetypes();
        String most = TreePlanner.mostUnlocked();
        int mostNodes = most.isEmpty() ? 0 : TreePlanner.unlockedIn(most);
        String sugg = suggested(archs, most, mostNodes);
        String chosen = TreePlanner.chosen();
        List<String> names = new ArrayList<>(archs);
        names.add("");
        String why = sugg == null ? L.t("wynnlabs.arch.no_tree")
                : sugg.isEmpty() ? L.t("wynnlabs.arch.why_auto") : L.t("wynnlabs.arch.why_nodes", sugg, mostNodes);
        List<UiMenu.Item> items = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String a = names.get(i);
            boolean tag = a.equals(sugg);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.literal(label(a)).withStyle(net.minecraft.ChatFormatting.AQUA));
            if (!desc(a).isEmpty()) {
                tip.add(Component.literal(desc(a)).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            if (tag) {
                tip.add(Component.literal(why).withStyle(net.minecraft.ChatFormatting.GOLD));
            }
            UiMenu.Item it = UiMenu.Item.of(String.valueOf(i), null, label(a), tag ? L.t("wynnlabs.arch.suggested") : null,
                    a.equalsIgnoreCase(chosen)).tip(tip);
            if (!desc(a).isEmpty()) {
                it = it.sub(desc(a));
            }
            items.add(a.isEmpty() && i > 0 ? it.group() : it);
        }
        box = UiMenu.draw(g, font(), UiMenu.Spec.under(anchor, menu.width, menu.height).width(170), state, items, mx, my, r -> {
            rows.add(new Object[] {new int[] {r.x(), r.y(), r.w(), r.h()}, names.get(Integer.parseInt(r.item().id()))});
            menu.hits.add(new MenuHit(r.x(), r.y(), r.w(), r.h(), null, r.item().tip(), null));
        });
        return box;
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
        if (!a.equalsIgnoreCase(TreePlanner.chosen())) {
            TreePlanner.choose(a);
            menu.flash(L.t("wynnlabs.home.archetype_set", label(a)));
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
        if (it != null) {
            List<String> names = new ArrayList<>(TreePlanner.archetypes());
            names.add("");
            int i = Integer.parseInt(it.id());
            if (i < names.size()) {
                pick(names.get(i));
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
