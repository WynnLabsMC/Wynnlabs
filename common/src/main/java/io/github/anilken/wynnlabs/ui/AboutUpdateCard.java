package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.UpdateCheck;
import io.github.anilken.wynnlabs.core.UpdateDownload;
import io.github.anilken.wynnlabs.core.UpdateInstall;
import io.github.anilken.wynnlabs.core.UpdatePrefs;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * The About page's update card, one layout for every state: the state's tile, its title (LABEL)
 * and line (META), the one main action as a gold button on the right, the rest as chips under
 * the text, and on the title row the small icon buttons Check again and GitHub. States: up to
 * date, checking, not checked yet, the check failed (offline, off, a dev build), a newer release
 * (Update, Changes, Skip this version), downloading (its bar), ready on exit (Quit now), a failed
 * install (Open the folder). Its buttons click through {@code #upd:} ids that {@link SettingsTab}
 * hands to {@link #click}.
 */
final class AboutUpdateCard extends MenuPaint {

    private static final int TILE = 22;

    AboutUpdateCard(RoadmapScreen menu) {
        super(menu);
    }

    /** What the card shows: icon, its ink and wash, the texts, the main action {label, icon, id}, the chips {label, id}. */
    private record Look(String icon, int ink, int fill, String title, String sub, int subInk, String[] main, List<String[]> chips,
                        boolean bar, boolean check, boolean github) {}

    private Look look(UpdateCheck.State s) {
        String mine = UpdateScreen.vName(s.own().isEmpty() ? UpdateCheck.version.get() : s.own());
        String loader = UpdateScreen.loaderLabel();
        boolean newer = s.status() == UpdateCheck.Status.NEWER;
        boolean mineDl = newer && s.latest() != null && s.latest().equals(UpdateDownload.tag());
        UpdateDownload.Phase phase = mineDl ? UpdateDownload.phase() : UpdateDownload.Phase.IDLE;
        String failedInstall = UpdateInstall.failedTag();
        List<String[]> chips = new ArrayList<>();
        if (failedInstall != null && phase != UpdateDownload.Phase.DOWNLOADING && phase != UpdateDownload.Phase.READY) {
            if (newer && s.asset() != null) {
                chips.add(new String[] {L.t("wynnlabs.update.update"), "#upd:get"});
            }
            return new Look("alert-triangle", Theme.BAD, 0xFFF3DCD6, L.t("wynnlabs.about.install_failed_title", failedInstall),
                    L.t("wynnlabs.about.install_failed_sub"), Theme.INK_3, new String[] {L.t("wynnlabs.about.open_folder"), "box", "#upd:folder"},
                    chips, false, false, false);
        }
        if (phase == UpdateDownload.Phase.DOWNLOADING) {
            double f = UpdateDownload.progress();
            String sub = L.t("wynnlabs.box.pct", (int) Math.round(f * 100)) + (s.asset() == null ? ""
                    : " · " + String.format(java.util.Locale.ROOT, "%.1f / %.1f MB", s.asset().size() * f / 1048576.0,
                    s.asset().size() / 1048576.0));
            return new Look("refresh", TEAL, 0xFFDCEBEA, L.t("wynnlabs.about.downloading_title", s.latest()), sub, Theme.INK_3, null,
                    chips, true, false, true);
        }
        if (phase == UpdateDownload.Phase.READY) {
            return new Look("check", Theme.GOOD, 0xFFE3EED8, L.t("wynnlabs.about.ready_title", s.latest()), L.t("wynnlabs.about.ready_sub", mine),
                    Theme.INK_3, new String[] {L.t("wynnlabs.about.quit_now"), "player-play", "#upd:quit"}, chips, false, false, true);
        }
        if (newer) {
            boolean ignored = UpdatePrefs.ignoredVersion(s.latest());
            String sub = phase == UpdateDownload.Phase.FAILED ? UpdateCheck.failText(UpdateDownload.failure())
                    : ignored ? L.t("wynnlabs.about.ignored_sub", mine)
                    : s.asset() == null ? L.t("wynnlabs.about.no_fit_sub", mine)
                    : s.published() > 0 ? L.t("wynnlabs.about.newer_sub", mine, ago(s.published()))
                    : L.t("wynnlabs.about.newer_sub_nodate", mine);
            String[] main = s.asset() != null ? new String[] {L.t("wynnlabs.update.update"), "arrow-down", "#upd:get"}
                    : new String[] {L.t("wynnlabs.about.open_github"), "arrow-up-right", "#upd:open"};
            chips.add(new String[] {L.t("wynnlabs.update.changelog"), "#upd:notes"});
            if (!ignored) {
                chips.add(new String[] {L.t("wynnlabs.update.ignore_version"), "#upd:ignore"});
            }
            return new Look("arrow-up", Theme.GOLD_DEEP, 0xFFF6E7B8, L.t("wynnlabs.about.newer_title", s.latest()), sub,
                    phase == UpdateDownload.Phase.FAILED ? Theme.BAD : Theme.INK_3, main, chips, false, false, s.asset() != null);
        }
        return switch (s.status()) {
            case UP_TO_DATE -> new Look("check", Theme.GOOD, 0xFFE3EED8, L.t("wynnlabs.about.uptodate_title"),
                    L.t("wynnlabs.about.uptodate_sub", mine, loader, ago(s.checkedAt())), Theme.INK_3, null, chips, false, true, true);
            case FAILED -> {
                String[] main = s.reason() == UpdateCheck.Reason.OFF
                        ? new String[] {L.t("wynnlabs.about.go_setting"), "settings", "#upd:setting"} : null;
                yield new Look("alert-triangle", Theme.WARN, 0xFFF3E1B8, L.t("wynnlabs.about.failed_title"), L.t(switch (s.reason()) {
                    case OFFLINE -> "wynnlabs.about.reason_offline";
                    case OFF -> "wynnlabs.about.reason_off";
                    case DEV -> "wynnlabs.about.reason_dev";
                    default -> "wynnlabs.about.reason_no_answer";
                }), Theme.INK_3, main, chips, false, s.reason() != UpdateCheck.Reason.OFF && s.reason() != UpdateCheck.Reason.DEV, false);
            }
            case CHECKING -> new Look("refresh", Theme.INK_3, 0xFFEFE5CC, L.t("wynnlabs.about.checking_title"),
                    L.t("wynnlabs.about.idle_sub", mine, loader), Theme.INK_3, null, chips, false, false, false);
            default -> new Look("clock", Theme.INK_3, 0xFFEFE5CC, L.t("wynnlabs.about.idle_title"), L.t("wynnlabs.about.idle_sub", mine, loader),
                    Theme.INK_3, null, chips, false, true, false);
        };
    }

    static final int TEAL = 0xFF2C7A7E;

    /** Draws the card at {@code x, y}, {@code w} wide; returns its height. */
    int draw(GuiGraphics g, int x, int y, int w, int mx, int my) {
        UpdateCheck.State s = UpdateCheck.state();
        Look l = look(s);
        int pad = Theme.SP_4;
        boolean narrow = w < 360;
        // a narrow card puts its main action in the row under the text, before the chips
        boolean underRow = l.bar() || !l.chips().isEmpty() || narrow && l.main() != null;
        int h = 2 * pad + Math.max(TILE, Type.LABEL.lineHeight + Type.META.lineHeight)
                + (underRow ? Theme.SP_3 + Math.max(Ui.CHIP_H, narrow && l.main() != null ? Ui.BUTTON_H : 0) : 0);
        Art.box(g, x, y, w, h, 6, 0xFFF4EAD0, 0x40A08A5A);
        Art.box(g, x + pad, y + pad, TILE, TILE, 6, l.fill(), l.ink());
        Art.icon(g, l.icon(), x + pad + (TILE - 10) / 2, y + pad + (TILE - 10) / 2, 10, l.ink());
        // the title row's right end: GitHub, Check again (icon buttons), then the main action
        int right = x + w - pad;
        int by = y + pad;
        if (l.github() && s.url() != null) {
            right -= Ui.CHIP_H + 2;
            iconAction(g, "arrow-up-right", right, by, "#upd:open", L.t("wynnlabs.about.link_tip", s.url()), mx, my);
            right -= Theme.SP_2;
        }
        if (l.check()) {
            right -= Ui.CHIP_H + 2;
            iconAction(g, "refresh", right, by, "#upd:check", L.t("wynnlabs.about.check_again"), mx, my);
            right -= Theme.SP_2;
        }
        if (l.main() != null && !narrow) {
            int bw = Ui.iconButtonLabelWidth(font(), l.main()[0]);
            right -= bw;
            Ui.iconButtonLabel(g, font(), l.main()[1], l.main()[0], right, by - 1, bw, mx, my);
            menu.toggles.add(new Object[] {new int[] {right, by - 1, bw, Ui.BUTTON_H}, l.main()[2]});
            right -= Theme.SP_3;
        }
        int tx = x + pad + TILE + Theme.SP_4;
        Type.LABEL.draw(g, font(), Type.LABEL.fit(font(), l.title(), right - tx), tx, y + pad);
        Type.META.draw(g, font(), Type.META.fit(font(), l.sub(), right - tx), tx, y + pad + Type.LABEL.lineHeight, l.subInk());
        if (!underRow) {
            return h;
        }
        int ry = y + pad + Math.max(TILE, Type.LABEL.lineHeight + Type.META.lineHeight) + Theme.SP_3;
        if (l.bar()) {
            bar(g, tx, ry + (Ui.CHIP_H - 6) / 2, x + w - pad - tx, UpdateDownload.progress());
            return h;
        }
        int cx = tx;
        if (narrow && l.main() != null) {
            int bw = Ui.iconButtonLabelWidth(font(), l.main()[0]);
            Ui.iconButtonLabel(g, font(), l.main()[1], l.main()[0], cx, ry, bw, mx, my);
            menu.toggles.add(new Object[] {new int[] {cx, ry, bw, Ui.BUTTON_H}, l.main()[2]});
            cx += bw + Theme.SP_2;
        }
        int cy = ry + (narrow && l.main() != null ? (Ui.BUTTON_H - Ui.CHIP_H) / 2 : 0);
        for (String[] c : l.chips()) {
            int cw = Ui.iconChipWidth(font(), null, c[0], false);
            if (cx + cw > x + w - pad) {
                break;                          // no room: the rest stay in the update window
            }
            Ui.iconChip(g, font(), null, c[0], false, cx, cy, cw, false, mx, my);
            menu.toggles.add(new Object[] {new int[] {cx, cy, cw, Ui.CHIP_H}, c[1]});
            cx += cw + Theme.SP_2;
        }
        return h;
    }

    private void iconAction(GuiGraphics g, String icon, int x, int y, String id, String tipText, int mx, int my) {
        boolean hover = Ui.iconButton(g, icon, x, y, false, mx, my);
        menu.toggles.add(new Object[] {new int[] {x, y, Ui.CHIP_H + 2, Ui.CHIP_H}, id});
        if (hover) {
            menu.hits.add(new MenuHit(x, y, Ui.CHIP_H + 2, Ui.CHIP_H, null, List.of(net.minecraft.network.chat.Component.literal(tipText)), null));
        }
    }

    /** The HD progress bar: a light track, the teal fill up to its share. */
    static void bar(GuiGraphics g, int x, int y, int w, double f) {
        Art.box(g, x, y, w, 6, 3, 0xFFE4D6B0, 0x55A08A5A);
        Art.box(g, x, y, Math.max(6, (int) Math.round(w * Math.max(0, Math.min(1, f)))), 6, 3, TEAL, 0);
    }

    /** A click on one of the card's buttons; false for ids it does not own ({@code #upd:setting}). */
    boolean click(String id) {
        UpdateCheck.State s = UpdateCheck.state();
        switch (id) {
            case "#upd:check" -> {
                if (!UpdateCheck.checkNow() && UpdateCheck.state().status() != UpdateCheck.Status.FAILED) {
                    menu.flash(L.t("wynnlabs.about.wait"));
                }
            }
            case "#upd:open" -> openLink(s.url());
            case "#upd:get" -> {
                if (s.asset() != null && !UpdateDownload.start(s.asset(), s.latest(), false)
                        && UpdateDownload.phase() == UpdateDownload.Phase.FAILED) {
                    menu.flash(UpdateCheck.failText(UpdateDownload.failure()));
                }
            }
            case "#upd:notes" -> net.minecraft.client.Minecraft.getInstance().setScreen(new UpdateScreen(menu, true));
            case "#upd:ignore" -> UpdatePrefs.ignore(s.latest());
            case "#upd:quit" -> net.minecraft.client.Minecraft.getInstance().stop();
            case "#upd:folder" -> UpdateInstall.openFolder();
            default -> {
                return false;
            }
        }
        return true;
    }

    /** Opens a GitHub page in the browser after the game's "open this link?" prompt; anything else is refused. */
    void openLink(String url) {
        if (url == null || !url.startsWith("https://github.com/")) {
            return;
        }
        net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(menu, java.net.URI.create(url), true);
    }
}
