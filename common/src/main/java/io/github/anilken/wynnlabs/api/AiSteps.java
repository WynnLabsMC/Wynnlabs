package io.github.anilken.wynnlabs.api;

/**
 * The AI page's state and its three setup steps, worked out from plain facts (pure, so tested
 * headless): whether the API switch is on, the server's state ("off", "listening", "busy"), when
 * the last request came, and whether the chosen tool's own step was done (the skill installed,
 * the prompt or the address copied).
 */
public final class AiSteps {

    /** What the status card says. */
    public enum State { OFF, NEW, CONNECTED, BUSY }

    /** The assistant the player sets up: each has its own second step. */
    public enum Tool { CLAUDE_CODE, CHAT, OTHER }

    private AiSteps() {}

    public static State state(boolean apiOn, String server, long lastAt) {
        if (!apiOn) {
            return State.OFF;
        }
        if ("busy".equals(server)) {
            return State.BUSY;
        }
        if (!"listening".equals(server)) {
            return State.OFF;
        }
        return lastAt > 0 ? State.CONNECTED : State.NEW;
    }

    /** Which steps are done: 1 the API listens, 2 the tool's step, 3 a request came. */
    public static boolean[] done(State s, boolean toolDone, long lastAt) {
        boolean one = s == State.NEW || s == State.CONNECTED;
        boolean three = one && lastAt > 0;
        // a request already came: the tool is clearly set up, whatever this session did
        boolean two = one && (toolDone || three);
        return new boolean[] {one, two, three};
    }

    /** The step to do next (0, 1, 2), or -1 when all are done. */
    public static int next(boolean[] done) {
        for (int i = 0; i < done.length; i++) {
            if (!done[i]) {
                return i;
            }
        }
        return -1;
    }
}
