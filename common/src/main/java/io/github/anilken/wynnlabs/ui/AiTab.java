package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.api.AiLink;
import io.github.anilken.wynnlabs.api.AiSteps;
import io.github.anilken.wynnlabs.api.WynnLabs;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The AI assistant page (Settings › AI). A live status card (connected, last request and today's
 * count; on and never asked; off; the port busy) with the local API's switch; "Connect your
 * assistant": the tool, then three numbered steps, each with its one action, the next one lit and
 * the done ones ticked (the third ticks itself on the first request), and the address and port
 * under them; "What it can read", grouped, and "What it can write", with the write switch. The
 * status reads the server's own counters, at most twice a second. Writes still need the switch and
 * the X-Wynninv header; the server listens on 127.0.0.1 only.
 */
final class AiTab extends MenuTab {

    /** A click area of this frame and what it does. */
    record Click(int[] rect, Runnable run) {}

    private final List<Click> clicks = new ArrayList<>();
    private final AiPageParts parts = new AiPageParts(this);
    private final UiDrawer drawer;
    private final ScrollBar bar = new ScrollBar();
    AiSteps.Tool tool = AiSteps.Tool.CLAUDE_CODE;
    boolean promptCopied;
    boolean addressCopied;
    private boolean claudeInstalled;
    private long checkedAt;
    /** The status card's words, worked out at most twice a second. */
    AiSteps.State state = AiSteps.State.OFF;
    String statusSub = "";
    private long statusAt;
    /** The last action's failure, shown as an error notice until the next action. */
    String failure;
    private int scroll;
    private int scrollMax;
    private int[] view = new int[4];
    private int popupFrom;
    private boolean inPopup;
    int mouseX;
    int mouseY;

    AiTab(RoadmapScreen menu) {
        super(menu);
        drawer = new UiDrawer(menu);
    }

    @Override
    void beginFrame() {
        clicks.clear();
    }

    /** A click area: on the page, clipped to its visible part; in the drawer, as it is (the drawer clips). */
    void click(int x, int y, int w, int h, Runnable run) {
        if (inPopup) {
            clicks.add(new Click(new int[] {x, y, w, h}, run));
            return;
        }
        int top = Math.max(y, view[1]);
        int bottom = Math.min(y + h, view[1] + view[3]);
        if (bottom > top) {
            clicks.add(new Click(new int[] {x, top, w, bottom - top}, run));
        }
    }

    /** A tooltip while the mouse is on the box. */
    void tip(int x, int y, int w, int h, List<Component> lines) {
        boolean seen = inPopup || mouseY >= view[1] && mouseY < view[1] + view[3];
        if (seen && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
            menu.hits.add(new MenuHit(x, y, w, h, null, lines, null));
        }
    }

    // ---- the facts, cached ----

    private void refresh() {
        long now = System.currentTimeMillis();
        if (now - statusAt < 500) {
            return;
        }
        statusAt = now;
        long last = AiLink.lastAt();
        state = AiSteps.state(Settings.on("localApi"), AiLink.state, last);
        statusSub = switch (state) {
            case OFF -> L.t("wynnlabs.ai.sub_off");
            case NEW -> L.t("wynnlabs.ai.sub_new");
            case BUSY -> L.t("wynnlabs.ai.busy_hint");
            case CONNECTED -> L.t("wynnlabs.ai.last_request", ago(last), AiLink.lastPath(), AiLink.today());
        };
        if (now - checkedAt > 2000) {
            checkedAt = now;
            try {
                claudeInstalled = Files.isRegularFile(AiLink.claudeSkill());
            } catch (RuntimeException e) {
                claudeInstalled = false;
            }
        }
    }

    /** Whether the chosen tool's own step was done. */
    boolean toolDone() {
        return switch (tool) {
            case CLAUDE_CODE -> claudeInstalled;
            case CHAT -> promptCopied;
            case OTHER -> addressCopied;
        };
    }

    // ---- drawing ----

    @Override
    void draw(GuiGraphics g, int x, int y, int w, int h, int mx, int my, float pt) {
        mouseX = mx;
        mouseY = my;
        drawer.page(x, y, w, h);
        refresh();
        boolean compact = menu.layout.compact();
        view = new int[] {x - Theme.SP_2, y, w + 2 * Theme.SP_2, h};
        g.enableScissor(view[0], view[1], view[0] + view[2], view[1] + view[3]);
        int cw = scrollMax > 0 ? w - ScrollBar.W - Theme.SP_3 : w;
        int top = y - scroll;
        int end = parts.page(g, x, top, cw, top + h, compact, mx, my);
        g.disableScissor();
        int total = end - top;
        scrollMax = Math.max(0, total - h);
        scroll = Math.max(0, Math.min(scroll, scrollMax));
        bar.place(x + w - ScrollBar.W, y, h, h, total, scroll);
        bar.draw(g, mx, my);
    }

    /** "All ›" on a small window: every read endpoint in the drawer. */
    void openReads() {
        drawer.show(L.t("wynnlabs.ai.reads"), (g, x, y, w, top, bottom, mx, my) -> parts.allReads(g, x, y, w, mx, my) - y);
    }

    // ---- the popups: the drawer ----

    private final Popup popups = new Popup() {
        @Override
        public boolean open() {
            return drawer.open();
        }

        @Override
        public void draw(GuiGraphics g, int mx, int my, List<int[]> boxes) {
            mouseX = mx;
            mouseY = my;
            popupFrom = clicks.size();
            inPopup = true;
            drawer.draw(g, mx, my, boxes);
            inPopup = false;
        }

        @Override
        public void close() {
            drawer.close();
        }

        @Override
        public boolean clicked(MouseButtonEvent event, boolean doubleClick) {
            if (drawer.clicked(event, doubleClick)) {
                return true;
            }
            for (int i = clicks.size() - 1; i >= popupFrom; i--) {
                if (in(event.x(), event.y(), clicks.get(i).rect())) {
                    clicks.get(i).run().run();
                    return true;
                }
            }
            return false;
        }

        @Override
        public void scrolled(double x, double y, double v) {
            drawer.scrolled(x, y, v);
        }
    };

    @Override
    Popup popup() {
        return popups;
    }

    // ---- input ----

    @Override
    boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (bar.mouseClicked(event.x(), event.y())) {
            scroll = bar.offset();
            return true;
        }
        for (int i = clicks.size() - 1; i >= 0; i--) {
            if (in(event.x(), event.y(), clicks.get(i).rect())) {
                clicks.get(i).run().run();
                return true;
            }
        }
        return false;
    }

    @Override
    boolean sharedClicks() {
        return false;
    }

    @Override
    boolean barDragged(double x, double y) {
        if (drawer.drag(x, y)) {
            return true;
        }
        if (bar.mouseDragged(y)) {
            scroll = bar.offset();
            return true;
        }
        return false;
    }

    @Override
    void barReleased() {
        drawer.release();
        bar.mouseReleased();
    }

    @Override
    void mouseScrolled(double x, double y, double v) {
        scroll = Math.max(0, Math.min(scrollMax, scroll - (int) Math.signum(v) * Theme.SP_7));
    }

    // ---- the actions ----

    void flip(String key) {
        Settings.set(key, !Settings.on(key));
        if (key.equals("localApi")) {
            WynnLabs.syncServer();
            statusAt = 0;
        }
    }

    /** Runs an action: a short note in the menu's flash line, or the failure as an error notice. */
    void act(String what) {
        failure = null;
        try {
            switch (what) {
                case "copy" -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(AiLink.url());
                    addressCopied = true;
                    menu.flash(L.t("wynnlabs.ai.copied_url"));
                }
                case "export" -> menu.flash(L.t("wynnlabs.ai.exported", AiLink.export()));
                case "install" -> {
                    menu.flash(L.t("wynnlabs.ai.installed", AiLink.installClaude()));
                    checkedAt = 0;
                    statusAt = 0;
                }
                case "prompt" -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(AiLink.prompt());
                    promptCopied = true;
                    menu.flash(L.t("wynnlabs.ai.copied_prompt"));
                }
                case "port-", "port+" -> port(Settings.number("localApiPort", 47591) + (what.equals("port+") ? 1 : -1));
                case "free" -> port(AiLink.nextFreePort(AiLink.port));
                default -> {
                    // not an action of ours
                }
            }
        } catch (Exception e) {
            failure = L.t("wynnlabs.ai.failed", e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
        }
    }

    private void port(int p) {
        Settings.setNumber("localApiPort", Math.max(1024, Math.min(65535, p)));
        WynnLabs.syncServer();
        statusAt = 0;
    }
}
