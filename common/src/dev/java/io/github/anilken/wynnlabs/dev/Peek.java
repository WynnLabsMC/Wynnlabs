package io.github.anilken.wynnlabs.dev;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Dev builds only: the few private fields of the menu the screenshot tool reads and sets by
 * reflection, at tick time and never in a frame, so the release code carries no accessor for them.
 * Everything else it reaches directly (ShotHands sits in the menu's own package). Each field is
 * a {@link Ref} kept in a static constant, so ShotNamesTest checks every name against the menu's
 * classes without the game: a renamed field fails the tests, not a run in the game.
 */
public final class Peek {

    private Peek() {}

    /** A field by its class and name, looked up on first use. */
    public record Ref(Class<?> owner, String name) {

        /** The field; throws when it is gone. */
        public Field field() {
            try {
                Field f = owner.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                throw new IllegalStateException("no field " + owner.getSimpleName() + "." + name, e);
            }
        }

        /** Its value on {@code target} (ignored for a static field). */
        public Object get(Object target) {
            try {
                Field f = field();
                return f.get(Modifier.isStatic(f.getModifiers()) ? null : target);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        public void set(Object target, Object value) {
            try {
                Field f = field();
                f.set(Modifier.isStatic(f.getModifiers()) ? null : target, value);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
