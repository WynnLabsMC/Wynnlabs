package io.github.anilken.wynnlabs.core;

import java.util.function.Supplier;

/**
 * A value loaded for the character being played: {@link #get} runs the loader again when
 * {@link Profile#id()} is not the id it last loaded for, the first call included. Not locked:
 * a caller shared between threads holds its own lock around it.
 */
public final class PerCharacter<T> {

    private final Supplier<T> loader;
    private String loadedFor;
    private T value;

    public PerCharacter(Supplier<T> loader) {
        this.loader = loader;
    }

    /** The value for the current character, loaded when the character changed. */
    public T get() {
        String id = Profile.id();
        if (!id.equals(loadedFor)) {
            loadedFor = id;
            value = loader.get();
        }
        return value;
    }

    /** The value last loaded, without checking the character; null before the first load. */
    public T loaded() {
        return value;
    }

    /** Forgets the value: the next {@link #get} loads again. */
    public void clear() {
        loadedFor = null;
        value = null;
    }
}
