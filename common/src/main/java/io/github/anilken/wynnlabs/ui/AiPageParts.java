package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.api.AiLink;
import io.github.anilken.wynnlabs.api.AiSteps;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The AI page's parts ({@link AiTab}): the status card with the API switch, the port-busy notice,
 * "Connect your assistant" (tool chips, three steps, the connection row), "What it can read" by
 * group and "What it can write" with the write switch. Each draws from a y and returns the y under it.
 */
final class AiPageParts extends MenuPaint {

    /** How many endpoints only read, as the title's count. */
    private static final String READS = String.valueOf(AiLink.ENDPOINTS.stream().filter(e -> !e.write()).count());
    private static final String[] GROUP_ICON = {"user", "shield", "map-2", "building-bank", "settings"};
    private final AiTab tab;

    AiPageParts(AiTab tab) {
        super(tab.menu);
        this.tab = tab;
    }

    /** The whole page from y; returns the y under it. */
    int page(GuiGraphics g, int x, int y, int w, int bottom, boolean compact, int mx, int my) {
        int cy = y + Theme.SP_4;                // clear of the section chips above
        if (tab.state == AiSteps.State.BUSY) {
            int[][] hit = new int[1][];
            cy += Ui.notice(g, font(), Ui.Level.ERROR, List.of(L.t("wynnlabs.ai.busy_strip", AiLink.port)),
                    new Ui.Action("arrow-up-right", L.t("wynnlabs.ai.next_port")), x, cy, w, 60, mx, my, hit) + Theme.SP_4;
            if (hit[0] != null) {
                tab.click(hit[0][0], hit[0][1], hit[0][2], hit[0][3], () -> tab.act("free"));
            }
        }
        if (tab.failure != null) {
            cy += Ui.notice(g, font(), Ui.Level.ERROR, List.of(tab.failure), null, x, cy, w, 60, mx, my, new int[1][]) + Theme.SP_4;
        }
        cy = status(g, x, cy, w, mx, my) + Theme.SP_6;
        if (compact) {
            cy = connect(g, x, cy, w, compact, mx, my) + Theme.SP_6;
            cy = connection(g, x, cy - Theme.SP_6 + Theme.SP_5, w, mx, my) + Theme.SP_6;
            cy = reads(g, x, cy, w, true, mx, my) + Theme.SP_6;
            return writes(g, x, cy, w, mx, my) + Theme.SP_4;
        }
        int lw = (w - Theme.SP_7) / 2;
        int ly = connect(g, x, cy, lw, false, mx, my) + Theme.SP_6;
        int rx = x + lw + Theme.SP_7;
        int ry = reads(g, rx, cy, w - lw - Theme.SP_7, false, mx, my) + Theme.SP_6;
        ry = writes(g, rx, ry, w - lw - Theme.SP_7, mx, my);
        // the connection row right under the steps
        ly = connection(g, x, ly - Theme.SP_6 + Theme.SP_5, lw, mx, my);
        return Math.max(ly, ry) + Theme.SP_4;
    }

    // ---- the status card ----

    private int status(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int pad = Theme.SP_5;
        int h = 2 * pad + Type.HEADING.lineHeight + Type.META.lineHeight;
        Kit.softCard(g, x, y, w, h);
        AiSteps.State s = tab.state;
        String title = switch (s) {
            case OFF -> L.t("wynnlabs.ai.state_off");
            case NEW -> L.t("wynnlabs.ai.state_new");
            case CONNECTED -> L.t("wynnlabs.ai.state_on");
            case BUSY -> L.t("wynnlabs.ai.state_busy", AiLink.port);
        };
        int dot = switch (s) {
            case OFF -> Theme.INK_3;
            case NEW -> Theme.GOLD_DEEP;
            case CONNECTED -> Theme.GOOD;
            case BUSY -> Theme.BAD;
        };
        int tx = x + pad;
        int ty = y + pad;
        Art.box(g, tx, Type.HEADING.onCaps(ty, 8), 8, 8, 4, dot, 0);
        // the switch on the right: the API itself
        String api = L.t("wynnlabs.ai.api");
        int sx = x + w - pad - UiToggle.W;
        int sy = Type.HEADING.onCaps(ty, UiToggle.H);
        UiToggle.draw(g, sx, sy, Settings.on("localApi"), "localApi", mx, my);
        int aw = Type.BODY.width(font(), api);
        Type.BODY.draw(g, font(), api, sx - Theme.SP_3 - aw, Type.BODY.yIn(sy, UiToggle.H), Theme.INK_2);
        int ax = sx - Theme.SP_3 - aw;
        tab.click(ax, sy - Theme.SP_2, x + w - pad - ax, UiToggle.H + 2 * Theme.SP_2, () -> tab.flip("localApi"));
        tab.tip(ax, sy - Theme.SP_2, x + w - pad - ax, UiToggle.H + 2 * Theme.SP_2, setting("localApi"));
        int room = ax - Theme.SP_4 - (tx + 8 + Theme.SP_3);
        Type.HEADING.draw(g, font(), Type.HEADING.fit(font(), title, room), tx + 8 + Theme.SP_3, ty, Theme.INK);
        Type.META.draw(g, font(), Type.META.fit(font(), tab.statusSub, w - 2 * pad - 8 - Theme.SP_3), tx + 8 + Theme.SP_3,
                ty + Type.HEADING.lineHeight, Theme.INK_3);
        return y + h;
    }

    private static List<Component> setting(String key) {
        return List.of(L.c(Settings.LABELS.get(key)).withStyle(ChatFormatting.WHITE), L.c(Settings.DESC.get(key)).withStyle(ChatFormatting.GRAY));
    }

    // ---- connect your assistant ----

    private int title(GuiGraphics g, String text, String meta, int x, int y, int w) {
        Type.LABEL.draw(g, font(), text, x, y, Theme.INK);
        if (meta != null) {
            Type.META.draw(g, font(), meta, x + Type.LABEL.width(font(), text) + Theme.SP_3, y + 1, Theme.INK_3);
        }
        int hy = y + Type.LABEL.lineHeight + Theme.SP_3;
        Kit.hair(g, x, hy, w, 1, Theme.LINE);
        return hy + 1 + Theme.SP_3;
    }

    private int connect(GuiGraphics g, int x, int y, int w, boolean compact, int mx, int my) {
        int cy = title(g, L.t("wynnlabs.ai.connect"), null, x, y, w);
        int cx = x;
        for (AiSteps.Tool t : AiSteps.Tool.values()) {
            String label = L.t("wynnlabs.ai.tool." + t.name().toLowerCase(java.util.Locale.ROOT));
            String icon = t == AiSteps.Tool.CLAUDE_CODE ? "hierarchy" : t == AiSteps.Tool.CHAT ? "script" : "settings";
            int cw = Ui.iconChipWidth(font(), icon, label, false);
            if (cx + cw > x + w && cx > x) {
                cx = x;
                cy += Ui.CHIP_H + Theme.SP_2;
            }
            Ui.iconChip(g, font(), icon, label, false, cx, cy, cw, tab.tool == t, mx, my);
            tab.click(cx, cy, cw, Ui.CHIP_H, () -> tab.tool = t);
            cx += cw + Theme.SP_2;
        }
        cy += Ui.CHIP_H + Theme.SP_4;
        boolean[] done = AiSteps.done(tab.state, tab.toolDone(), AiLink.lastAt());
        int next = AiSteps.next(done);
        String[] titles = {L.t("wynnlabs.ai.step1"), L.t("wynnlabs.ai.step2." + toolKey()), L.t("wynnlabs.ai.step3")};
        String[] metas = {L.t("wynnlabs.ai.step1_meta"), stepTwoMeta(), L.t("wynnlabs.ai.step3_meta")};
        for (int i = 0; i < 3; i++) {
            cy = step(g, i, titles[i], metas[i], done[i], i == next, x, cy, w, mx, my);
        }
        return cy - Theme.SP_2;
    }

    private String toolKey() {
        return tab.tool.name().toLowerCase(java.util.Locale.ROOT);
    }

    private String stepTwoMeta() {
        return switch (tab.tool) {
            case CLAUDE_CODE -> "~/.claude/skills/wynnlabs";
            case CHAT -> L.t("wynnlabs.ai.step2_meta.chat");
            case OTHER -> AiLink.url();
        };
    }

    /** One numbered step: its ring (a tick when done), its words, its action on the right; the next one lit. */
    private int step(GuiGraphics g, int i, String title, String meta, boolean done, boolean lit, int x, int y, int w, int mx, int my) {
        int textH = Type.BODY.lineHeight + Type.META.lineHeight;
        if (lit) {
            Kit.rowLit(g, x, y, w, textH + 2 * Theme.SP_2);     // inside the column; the row's content SP_2 in
        }
        x += Theme.SP_2;
        w -= 2 * Theme.SP_2;
        int ty = y + Theme.SP_2;
        int d = 12;
        int dy = ty + (textH - d) / 2;
        if (done) {
            Kit.seal(g, x, dy, d, d, Theme.GOOD);
            Art.icon(g, "check", x + Ui.iconIn(d, 8), dy + Ui.iconIn(d, 8), 8, Theme.CREAM);
        } else {
            Kit.pill(g, x, dy, d, d, false, false);
            String n = String.valueOf(i + 1);
            Type.META.draw(g, font(), n, x + (d - Type.META.width(font(), n) + 1) / 2, Type.META.yIn(dy, d), Theme.INK_2);
        }
        int r = x + w;
        if (i == 1 && tab.state != AiSteps.State.OFF && tab.state != AiSteps.State.BUSY) {
            String[][] acts = switch (tab.tool) {
                case CLAUDE_CODE -> new String[][] {{L.t("wynnlabs.ai.install_short"), "install"}};
                case CHAT -> new String[][] {{L.t("wynnlabs.ai.copy"), "prompt"}};
                case OTHER -> new String[][] {{L.t("wynnlabs.ai.save"), "export"}, {L.t("wynnlabs.ai.copy"), "copy"}};
            };
            for (String[] a : acts) {
                int bw = Ui.chipWidth(font(), a[0]);
                int by = Type.BODY.onCaps(ty, Ui.SMALL_H);
                Ui.small(g, font(), a[0], r - bw, by, bw, mx, my);
                String what = a[1];
                tab.click(r - bw, by, bw, Ui.SMALL_H, () -> tab.act(what));
                r -= bw + Theme.SP_2;
            }
        }
        int tx = x + d + Theme.SP_4;
        int room = r - Theme.SP_3 - tx;
        Type role = lit ? Type.LABEL : Type.BODY;
        String shown = role.fit(font(), title, room);
        role.draw(g, font(), shown, tx, ty, done ? Theme.INK_2 : Theme.INK);
        if (!shown.equals(title)) {
            tab.tip(tx, ty, room, Type.BODY.lineHeight, List.of(Component.literal(title)));
        }
        Type.META.draw(g, font(), Type.META.fit(font(), meta, room), tx, ty + Type.BODY.lineHeight, Theme.INK_3);
        return y + textH + 2 * Theme.SP_2 + Theme.SP_2;
    }

    /** "Connection": the address with a copy button, the port with − and +. */
    private int connection(GuiGraphics g, int x, int y, int w, int mx, int my) {
        Type.META.draw(g, font(), L.t("wynnlabs.ai.connection"), x, y, Theme.INK_3);
        int cy = y + Type.META.lineHeight + Theme.SP_2;
        String url = AiLink.url();
        int bw = Ui.CHIP_H + 2;
        int uw = Type.BODY.width(font(), url) + 2 * Theme.SP_3 + bw + Theme.SP_1;
        Kit.pill(g, x, cy, uw, Ui.CHIP_H, false, false);
        Type.BODY.draw(g, font(), url, x + Theme.SP_3, Type.BODY.yIn(cy, Ui.CHIP_H), Theme.LINK);
        Ui.iconButton(g, "copy", x + uw - bw, cy, false, mx, my);
        tab.click(x, cy, uw, Ui.CHIP_H, () -> tab.act("copy"));
        int px = x + uw + Theme.SP_4;
        Ui.iconButton(g, "minus", px, cy, false, mx, my);
        tab.click(px, cy, bw, Ui.CHIP_H, () -> tab.act("port-"));
        px += bw + Theme.SP_2;
        String port = L.t("wynnlabs.ai.port", AiLink.port);
        Type.BODY.draw(g, font(), port, px, Type.BODY.yIn(cy, Ui.CHIP_H), Theme.INK);
        px += Type.BODY.width(font(), port) + Theme.SP_2;
        Ui.iconButton(g, "plus", px, cy, false, mx, my);
        tab.click(px, cy, bw, Ui.CHIP_H, () -> tab.act("port+"));
        return cy + Ui.CHIP_H;
    }

    // ---- what it can read and write ----

    private int reads(GuiGraphics g, int x, int y, int w, boolean compact, int mx, int my) {
        String n = READS;
        int cy;
        if (compact) {
            String all = L.t("wynnlabs.ai.all");
            int aw = Ui.iconChipWidth(font(), "list-check", all, false);
            Ui.iconChip(g, font(), "list-check", all, false, x + w - aw, y - 1, aw, false, mx, my);
            tab.click(x + w - aw, y - 1, aw, Ui.CHIP_H, tab::openReads);
            cy = title(g, L.t("wynnlabs.ai.reads"), n, x, y, w - aw - Theme.SP_3);
        } else {
            cy = title(g, L.t("wynnlabs.ai.reads"), n, x, y, w);
        }
        int groups = compact ? 3 : AiLink.GROUPS.size();
        for (int gi = 0; gi < groups; gi++) {
            String group = AiLink.GROUPS.get(gi);
            Art.icon(g, GROUP_ICON[gi], x, Type.META.onCaps(cy, 8), 8, Theme.INK_2);
            String name = L.t("wynnlabs.ai.group." + group);
            int nx = x + 8 + Theme.SP_3;
            Type.META.draw(g, font(), name, nx, cy, Theme.INK_2);
            int px0 = nx + Math.max(Type.META.width(font(), name) + Theme.SP_3, 64);
            int px = px0;
            for (AiLink.Endpoint e : AiLink.ENDPOINTS) {
                if (!e.group().equals(group)) {
                    continue;
                }
                int pw = Type.META.width(font(), e.path());
                if (px + pw > x + w && px > px0) {
                    px = px0;
                    cy += Type.META.lineHeight + Theme.SP_1;
                }
                Type.META.draw(g, font(), e.path(), px, cy, Theme.LINK);
                tab.tip(px, cy, pw, Type.META.lineHeight, List.of(Component.literal(e.path()).withStyle(ChatFormatting.AQUA),
                        L.c(e.key()).withStyle(ChatFormatting.GRAY)));
                px += pw + Theme.SP_4;
            }
            cy += Type.META.lineHeight + Theme.SP_3;
        }
        return cy - Theme.SP_3;
    }

    private int writes(GuiGraphics g, int x, int y, int w, int mx, int my) {
        boolean on = Settings.on("apiWrite");
        String label = L.t("wynnlabs.ai.write_toggle");
        int sx = x + w - UiToggle.W;
        int sy = Type.LABEL.onCaps(y, UiToggle.H);
        UiToggle.draw(g, sx, sy, on, "apiWrite", mx, my);
        int lw = Type.META.width(font(), label);
        Type.META.draw(g, font(), label, sx - Theme.SP_3 - lw, Type.META.yIn(sy, UiToggle.H), Theme.INK_2);
        int ax = sx - Theme.SP_3 - lw;
        tab.click(ax, sy - Theme.SP_2, x + w - ax, UiToggle.H + 2 * Theme.SP_2, () -> tab.flip("apiWrite"));
        tab.tip(ax, sy - Theme.SP_2, x + w - ax, UiToggle.H + 2 * Theme.SP_2, setting("apiWrite"));
        int cy = title(g, L.t("wynnlabs.ai.writes"), null, x, y, w);
        String state = L.t(on ? "wynnlabs.ai.on" : "wynnlabs.ai.off");
        int stw = Type.META.width(font(), state);
        for (AiLink.Endpoint e : AiLink.ENDPOINTS) {
            if (!e.write()) {
                continue;
            }
            Type.META.draw(g, font(), e.path(), x, cy, on ? Theme.LINK : Theme.INK_3);
            int dx = x + 56;
            Type.META.draw(g, font(), Type.META.fit(font(), L.t(e.key()), x + w - stw - Theme.SP_4 - dx), dx, cy, on ? Theme.INK_2 : Theme.INK_3);
            Type.META.draw(g, font(), state, x + w - stw, cy, on ? Theme.GOOD : Theme.INK_3);
            cy += Type.META.lineHeight + Theme.SP_2;
        }
        return cy - Theme.SP_2;
    }

    /** The drawer's list: every read endpoint by group, the path and what it gives. */
    int allReads(GuiGraphics g, int x, int y, int w, int mx, int my) {
        int cy = y;
        for (int gi = 0; gi < AiLink.GROUPS.size(); gi++) {
            String group = AiLink.GROUPS.get(gi);
            cy = title(g, L.t("wynnlabs.ai.group." + group), null, x, cy, w);
            for (AiLink.Endpoint e : AiLink.ENDPOINTS) {
                if (!e.group().equals(group)) {
                    continue;
                }
                Type.BODY.draw(g, font(), e.path(), x, cy, Theme.LINK);
                int dx = x + Math.min(w / 2, 104);
                Type.META.draw(g, font(), Type.META.fit(font(), L.t(e.key()), x + w - dx), dx, cy + 1, Theme.INK_2);
                cy += Type.BODY.lineHeight + Theme.SP_2;
            }
            cy += Theme.SP_4;
        }
        return cy;
    }
}
