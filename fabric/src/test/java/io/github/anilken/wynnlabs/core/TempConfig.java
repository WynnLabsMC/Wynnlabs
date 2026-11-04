package io.github.anilken.wynnlabs.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;

/** For tests: the mod's config folder in a temporary folder and a character in play, until closed. */
public final class TempConfig implements AutoCloseable {

    private final Platform before = Platform.get();

    public TempConfig(Path dir, String character) throws Exception {
        Platform.install((Platform) Proxy.newProxyInstance(Platform.class.getClassLoader(), new Class<?>[] {Platform.class},
                (p, m, args) -> m.getName().equals("configDir") ? dir : m.invoke(before, args)));
        playAs(character);
    }

    @Override
    public void close() throws Exception {
        Platform.install(before);
        playAs("");
    }

    /** Runs a class's private no-argument static method (a feature's own save). */
    public static void call(Class<?> type, String method) throws Exception {
        Method m = type.getDeclaredMethod(method);
        m.setAccessible(true);
        m.invoke(null);
    }

    private static void playAs(String id) throws Exception {
        Field f = Profile.class.getDeclaredField("current");
        f.setAccessible(true);
        f.set(null, id);
    }
}
