package io.github.anilken.wynnlabs.mounts;

/**
 * Keeps the held mount's item fresh while riding, for the mount panel and the powerup labels. This
 * is the one place the mod sends anything to the server, behind "Refresh mount info" and the
 * feature asking for it. With the mount panel on: every {@link #SLOT_EVERY} ticks the selected
 * hotbar slot again when it holds the mount (the server sends the held item back; another slot is
 * never selected), and when the action bar's pill or energy glyph changed ({@link #signal}, settled
 * {@link #DEBOUNCE} ticks) the inventory opened and closed without the player seeing it. With the
 * powerup labels on: that same inventory refresh when an Energy Boost in sight needs the real
 * energy pool ({@link #ask}), at most once every {@link #ASK_GAP} ticks; nothing else.
 *
 * <p>The open is tracked: {@link #onOpenScreen} swallows the server's answer to our own request
 * (the client never shows a screen), {@link #onContent} reads its slots, then the close goes out.
 * A screen the player opens cancels a request still waiting and leaves that screen alone; with
 * any screen open nothing is sent. A request with no answer is dropped after {@link #TIMEOUT}
 * ticks. Pure state over a tick count and a {@link Net}, so the tests drive it without a game.
 */
public final class RideRefresh {

    /** What the refresh sends; the game's packets in play, a recorder in the tests. */
    public interface Net {
        void selectSlot(int slot);

        void openInventory();

        void close(int containerId);
    }

    /**
     * This tick's situation: riding, the panel and the refresh switched on, the mount item read, a
     * screen open, the player using or attacking (an NPC or chest answer could be theirs), the
     * selected hotbar slot, whether that slot holds the mount (only then is re-selecting it of use:
     * the selection itself is never changed), and the powerup labels switched on.
     */
    public record Ctx(boolean riding, boolean panelOn, boolean refreshOn, boolean hasMount, boolean screenOpen,
                      boolean playerBusy, int slot, boolean heldMount, boolean labelsOn) {

        /** Without the powerup labels. */
        public Ctx(boolean riding, boolean panelOn, boolean refreshOn, boolean hasMount, boolean screenOpen,
                   boolean playerBusy, int slot, boolean heldMount) {
            this(riding, panelOn, refreshOn, hasMount, screenOpen, playerBusy, slot, heldMount, false);
        }

        /** Whether the refresh may send anything now. */
        boolean active() {
            return riding && (panelOn || labelsOn) && refreshOn && hasMount && !screenOpen;
        }
    }

    public enum State { IDLE, PENDING, OPEN }

    static final int SLOT_EVERY = 20;
    static final int DEBOUNCE = 4;
    static final int TIMEOUT = 20;
    /** At least this long between two inventory refreshes, so a draining energy bar is not one a second. */
    static final int MIN_GAP = 40;
    /** At least this long (5 s) between two refreshes the powerup labels ask for. */
    static final int ASK_GAP = 100;

    private final Net net;
    private State state = State.IDLE;
    private long now;
    private long since;
    private int container = -1;
    private long fireAt = -1;
    private long lastOpen = Long.MIN_VALUE / 2;
    private long lastSlot = Long.MIN_VALUE / 2;
    private long lastAsk = Long.MIN_VALUE / 2;
    /** The waiting refresh was asked for by the powerup labels (it may go out with the panel off). */
    private boolean asked;

    public RideRefresh(Net net) {
        this.net = net;
    }

    public State state() {
        return state;
    }

    /** The action bar's mount parts changed: an inventory refresh once they settle. */
    public void signal(long tick) {
        fireAt = tick + DEBOUNCE;
    }

    /**
     * The powerup labels need the real energy pool: an inventory refresh at the next tick, unless
     * one was asked for less than {@link #ASK_GAP} ticks ago. True when this ask counts.
     */
    public boolean ask(long tick) {
        if (tick - lastAsk < ASK_GAP) {
            return false;
        }
        lastAsk = tick;
        asked = true;
        fireAt = tick;
        return true;
    }

    /** Once a tick: timeouts, the slot every second, the settled refresh. */
    public void tick(long tick, Ctx c) {
        now = tick;
        if (c.screenOpen()) {
            // the player's screen: a request still waiting is dropped, an open one forgotten (the
            // server has the player's menu now, a close from us could shut theirs)
            state = State.IDLE;
            container = -1;
            fireAt = -1;
            asked = false;
            return;
        }
        if (state == State.PENDING && (c.playerBusy() || tick - since > TIMEOUT)) {
            state = State.IDLE;                     // their answer may come next: never swallow it
        }
        if (state == State.OPEN && tick - since > TIMEOUT) {
            net.close(container);                   // opened, but its contents never came
            state = State.IDLE;
            container = -1;
        }
        if (!c.active()) {
            if (state == State.PENDING) {
                state = State.IDLE;
            }
            fireAt = -1;
            asked = false;
            return;
        }
        if (state != State.IDLE) {
            return;
        }
        // the action bar's signals serve the panel; with it off only the labels' ask goes out
        boolean wanted = c.panelOn() || asked && c.labelsOn();
        if (wanted && fireAt >= 0 && tick >= fireAt && tick - lastOpen >= MIN_GAP && !c.playerBusy()) {
            fireAt = -1;
            asked = false;
            lastOpen = tick;
            state = State.PENDING;
            since = tick;
            net.openInventory();
            return;
        }
        if (c.panelOn() && c.heldMount() && tick - lastSlot >= SLOT_EVERY) {
            lastSlot = tick;
            net.selectSlot(c.slot());
        }
    }

    /** The server opened a screen: true when it answers our request (then the client must not show it). */
    public boolean onOpenScreen(int containerId) {
        if (state != State.PENDING) {
            return false;
        }
        state = State.OPEN;
        container = containerId;
        since = now;
        return true;
    }

    /**
     * A container's contents came: when they are our menu's, {@code read} reads them, the close goes
     * out and true says the game should not apply them.
     */
    public boolean onContent(int containerId, Runnable read) {
        if (state != State.OPEN || containerId != container) {
            return false;
        }
        try {
            read.run();
        } finally {
            net.close(containerId);
            state = State.IDLE;
            container = -1;
        }
        return true;
    }

    /** Whether a slot change or close for this container is ours (so the game does not apply it). */
    public boolean ours(int containerId) {
        return state == State.OPEN && containerId == container;
    }
}
