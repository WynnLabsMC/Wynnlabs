package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.model.AbilityNode;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The tree page's right column, its top: the next node of the plan on a soft card (its own game
 * picture, its name, when it can be taken, what it does), the plan's next nodes, and the
 * archetypes' points now and after the plan.
 */
final class TreeSide extends MenuPaint {

    private final TreeTab tab;
    /** The mouse this frame (set by the tab before it draws), for the title rows' chips. */
    int mx;
    int my;

    TreeSide(RoadmapScreen menu, TreeTab tab) {
        super(menu);
        this.tab = tab;
    }

    /** The game's picture of a node, drawn {@code size} wide at x, y; false when the pack has none. */
    static boolean nodeIcon(GuiGraphics g, TreeView.Node n, int x, int y, int size) {
        ItemStack icon = Wyn.nodeIcon(n.node().type(), n.iconState());
        if (icon == null || icon.isEmpty()) {
            Art.icon(g, n.ultimate() ? "tree-diamond" : "tree-circle", x, y, size, n.colour());
            return false;
        }
        float sc = size / 16f;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(sc, sc);
        g.renderItem(icon, 0, 0);
        g.pose().popMatrix();
        return true;
    }

    /** "Sıradaki": the plan's next node on a soft card; returns its height (0: no plan). */
    int next(GuiGraphics g, int x, int y, int w, TreeView.View v) {
        if (v.next() == null) {
            return 0;
        }
        TreeView.Node n = v.byName(v.next().name());
        int pad = Theme.SP_4;
        int ic = 22;
        int tw0 = w - pad - ic - pad - pad;
        List<Component> what = whatLines(v.next(), tw0, 3);
        int h = pad + Type.META.lineHeight + Type.LEAD.lineHeight + Type.META.lineHeight
                + (what.isEmpty() ? 0 : Theme.SP_1 + what.size() * Type.BODY.lineHeight) + pad;
        Kit.softCard(g, x, y, w, h);
        if (n != null) {
            nodeIcon(g, n, x + pad, y + pad, ic);
        }
        int tx = x + pad + ic + pad;
        int tw = x + w - pad - tx;
        int cy = y + pad;
        Type.META.draw(g, font(), L.t("wynnlabs.tree.state_next"), tx, cy, Theme.INK_3);
        cy += Type.META.lineHeight;
        Type.LEAD.draw(g, font(), Type.LEAD.fit(font(), v.next().name(), tw), tx, cy);
        cy += Type.LEAD.lineHeight;
        String when = v.nextLevel() <= Wyn.level() ? L.t("wynnlabs.tree.next_now") : L.t("wynnlabs.tree.tip_level", v.nextLevel());
        String meta = when + " · " + L.n("wynnlabs.tree.tip_cost", v.next().cost()).replace("✔ ", "")
                + (v.next().archetype() == null || v.next().archetype().isBlank() ? "" : " · " + v.next().archetype());
        Type.META.draw(g, font(), Type.META.fit(font(), meta, tw), tx, cy, Theme.INK_2);
        cy += Type.META.lineHeight;
        if (!what.isEmpty()) {
            cy += Theme.SP_1;
            for (Component l : what) {
                Kit.text(g, font(), Type.BODY.of(l), tx, cy, Type.BODY.colour);
                cy += Type.BODY.lineHeight;
            }
        }
        if (n != null) {
            menu.hits.add(new MenuHit(x, y, w, h, null, tab.board.treeTip(n, v), null));
        }
        return h;
    }

    /**
     * What the node does: its first paragraph (the combo line and the stat lines left out), kept
     * styled, wrapped to {@code width} in at most {@code most} lines, the last cut with "…" when
     * more follows (the whole text is in the card's tooltip).
     */
    private List<Component> whatLines(AbilityNode a, int width, int most) {
        if (a.description() == null) {
            return List.of();
        }
        net.minecraft.network.chat.MutableComponent para = Component.empty();
        boolean any = false;
        for (var line : a.description()) {
            String s = TreeView.plain(line).strip();
            if (s.isEmpty() || s.startsWith("Click Combo") || s.contains(":")) {
                if (any) {
                    break;
                }
                continue;
            }
            if (any) {
                para.append(" ");
            }
            para.append(UiStyled.line(line));
            any = true;
        }
        if (!any) {
            return List.of();
        }
        UiStyled.Width px = (t, st) -> font().width(Type.BODY.of(Component.literal(t).withStyle(st)));
        List<net.minecraft.network.chat.MutableComponent> lines = UiStyled.wrap(para, width, px);
        List<Component> out = new ArrayList<>();
        for (int i = 0; i < lines.size() && i < most; i++) {
            boolean last = i == most - 1 && lines.size() > most;
            out.add(last ? UiStyled.fit(lines.get(i).copy().append(" ").append(lines.get(i + 1)), width, px) : lines.get(i));
        }
        return out;
    }

    /** A title, its META, a chip that opens the drawer at the right, and its hairline; returns its height. */
    int head(GuiGraphics g, String title, String meta, String link, String action, int x, int y, int w) {
        Type.LABEL.draw(g, font(), title, x, y);
        if (meta != null) {
            Type.META.draw(g, font(), meta, x + Type.LABEL.width(font(), title) + Theme.SP_3, y + 1, Theme.INK_3);
        }
        if (link != null) {
            // the action that opens the drawer: a chip with an icon at the row's right
            int cw = Ui.iconChipWidth(font(), "list-check", link, false);
            int cy = Type.LABEL.onCaps(y, Ui.CHIP_H);
            Ui.iconChip(g, font(), "list-check", link, false, x + w - cw, cy, cw, false, mx, my);
            menu.hits.add(new MenuHit(x + w - cw, cy, cw, Ui.CHIP_H, null, null, action));
        }
        Kit.hair(g, x, y + Type.LABEL.lineHeight + Theme.SP_1, w, 1, Theme.LINE);
        return Type.LABEL.lineHeight + Theme.SP_1 + 1 + Theme.SP_3;
    }

    /** The plan's next nodes after the next one: the picture, the number, the name, the level; returns the y under. */
    int plan(GuiGraphics g, int x, int y, int w, TreeView.View v, int count) {
        List<TreeView.Node> steps = new ArrayList<>();
        TreeView.Node next = v.next() == null ? null : v.byName(v.next().name());
        int after = next == null ? 0 : next.step();
        for (TreeView.Node n : v.nodes()) {
            if (n.step() > after && n.state() != TreeView.State.UNLOCKED) {
                steps.add(n);
            }
        }
        steps.sort(Comparator.comparingInt(TreeView.Node::step));
        y += head(g, L.t("wynnlabs.tree.plan_head"), null, null, null, x, y, w);
        int line = Type.BODY.lineHeight + Theme.SP_2;
        for (int i = 0; i < steps.size() && i < count; i++) {
            TreeView.Node n = steps.get(i);
            nodeIcon(g, n, x, Type.BODY.onCaps(y, 10), 10);
            String num = n.step() + ".";
            Type.NUMBER.draw(g, font(), num, x + 10 + Theme.SP_2, y, Theme.PRICE);
            int nx = x + 10 + Theme.SP_2 + Type.NUMBER.width(font(), "00.") + Theme.SP_1;
            String lv = n.node().requiredLevel() > 0 ? "Lv " + n.node().requiredLevel() : "";
            int lw = Type.META.width(font(), lv);
            Type.BODY.draw(g, font(), Type.BODY.fit(font(), n.node().name(), x + w - lw - Theme.SP_3 - nx), nx, y);
            Type.META.drawRight(g, font(), lv, x + w, y + 1, Theme.INK_3);
            menu.hits.add(new MenuHit(x, y, w, Type.BODY.lineHeight, null, tab.board.treeTip(n, v), null));
            y += line;
        }
        if (steps.isEmpty()) {
            Type.MUTED.draw(g, font(), L.t("wynnlabs.tree.arch_off"), x, y);
            y += line;
        }
        return y;
    }

    /** Each archetype: its colour, name, a bar to the plan, and now → after the plan; returns the y under. */
    int archetypes(GuiGraphics g, int x, int y, int w, TreeView.View v) {
        y += head(g, L.t("wynnlabs.tree.legend_arch"), null, null, null, x, y, w);
        int line = Type.BODY.lineHeight + Theme.SP_2;
        int most = 1;
        for (TreeView.Archetype a : v.archetypes()) {
            most = Math.max(most, a.after());
        }
        for (TreeView.Archetype a : v.archetypes()) {
            g.fill(x, Type.BODY.onCaps(y, 6), x + 6, Type.BODY.onCaps(y, 6) + 6, a.colour());
            String t = a.after() > a.now() ? a.now() + " → " + a.after() : String.valueOf(a.now());
            int tw = Type.NUMBER.width(font(), t);
            Type.NUMBER.draw(g, font(), t, x + w - tw, y, Theme.INK_2);
            int nx = x + 6 + Theme.SP_2;
            int nameW = Math.min(Type.BODY.width(font(), a.name()), w / 3);
            Type.BODY.draw(g, font(), Type.BODY.fit(font(), a.name(), w / 3), nx, y);
            int bx = nx + Math.max(nameW, w / 4) + Theme.SP_3;
            int bw = x + w - tw - Theme.SP_4 - bx;
            if (bw > 20) {
                Art.pill(g, bx, Type.BODY.onCaps(y, 3), bw, 3, Theme.WELL, 0);
                Art.pill(g, bx, Type.BODY.onCaps(y, 3), Math.max(3, bw * a.after() / most), 3, a.colour(), 0);
            }
            y += line;
        }
        return y;
    }
}
