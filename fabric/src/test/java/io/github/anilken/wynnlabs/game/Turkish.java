package io.github.anilken.wynnlabs.game;

/**
 * The tests run in English; the Turkish cases run only on request ({@code gradle test -PwithTurkish}).
 * Mark a Turkish case with {@code @EnabledIfSystemProperty(named = Turkish.PROPERTY, matches = "true")}.
 */
public final class Turkish {

    /** The system property the build sets with -PwithTurkish. */
    public static final String PROPERTY = "wynnlabs.testTurkish";

    private Turkish() {}
}
