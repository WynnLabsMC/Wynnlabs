package io.github.anilken.wynnlabs.build.roadmap;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Turns {@link SharedBeams} on before every test class and test (registered for all of them
 * in META-INF/services; Gradle turns the autodetection on): a test that cleared the beam cache
 * searched anew, the next one shares again. JUnit loads this class outside the game's class
 * loader, so it reaches SharedBeams through the test class's own (the one the mod runs in).
 */
public final class SharedBeamsExtension implements BeforeAllCallback, BeforeEachCallback {

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        attach(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        attach(context);
    }

    private static void attach(ExtensionContext context) throws Exception {
        ClassLoader game = context.getRequiredTestClass().getClassLoader();
        Class.forName(SharedBeams.class.getName(), true, game).getMethod("attach").invoke(null);
    }
}
