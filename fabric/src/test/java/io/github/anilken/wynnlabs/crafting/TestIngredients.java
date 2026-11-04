package io.github.anilken.wynnlabs.crafting;

import java.util.Map;

/** The game's ingredients from the test resource ({@link CraftGoldenTest#ingredients}) by name, for tests outside this package. */
public final class TestIngredients {

    private TestIngredients() {}

    public static Map<String, Ingredient> byName() {
        return CraftGoldenTest.byName();
    }
}
