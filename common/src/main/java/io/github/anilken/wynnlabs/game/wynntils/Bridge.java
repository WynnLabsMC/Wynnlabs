package io.github.anilken.wynnlabs.game.wynntils;

import com.wynntils.core.components.Models;
import com.wynntils.models.profession.type.ProfessionType;
import io.github.anilken.wynnlabs.core.Platform;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn.Feature;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.SkillKind;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static io.github.anilken.wynnlabs.game.wynntils.AbilityReads.abilityTree;
import static io.github.anilken.wynnlabs.game.wynntils.AbilityReads.unlockedAbilities;
import static io.github.anilken.wynnlabs.game.wynntils.CharacterReads.assignedPoints;
import static io.github.anilken.wynnlabs.game.wynntils.CharacterReads.classType;
import static io.github.anilken.wynnlabs.game.wynntils.CharacterReads.hasCharacter;
import static io.github.anilken.wynnlabs.game.wynntils.CharacterReads.level;
import static io.github.anilken.wynnlabs.game.wynntils.CharacterReads.xp;
import static io.github.anilken.wynnlabs.game.wynntils.ContentReads.objectives;
import static io.github.anilken.wynnlabs.game.wynntils.ContentReads.quests;
import static io.github.anilken.wynnlabs.game.wynntils.ContentReads.tracking;
import static io.github.anilken.wynnlabs.game.wynntils.EndgameReads.classAspects;
import static io.github.anilken.wynnlabs.game.wynntils.EndgameReads.lootrunState;
import static io.github.anilken.wynnlabs.game.wynntils.EndgameReads.raidDry;
import static io.github.anilken.wynnlabs.game.wynntils.ItemReads.container;
import static io.github.anilken.wynnlabs.game.wynntils.ItemReads.item;
import static io.github.anilken.wynnlabs.game.wynntils.ProfessionReads.professionLevel;
import static io.github.anilken.wynnlabs.game.wynntils.WorldReads.waypoints;

/**
 * The guard every Wynntils call runs through, the features it marks down, the lookups of
 * members that not every supported Wynntils release has, and the self-test. Wyn hands them
 * out.
 */
public final class Bridge {

    private static final Map<Feature, String> DOWN = new EnumMap<>(Feature.class);

    private Bridge() {}

    /**
     * Runs one Wynntils call: its value, or {@code fallback} when Wynntils no longer has what
     * we call (or throws from inside); the first failure of a feature is logged with its trace.
     */
    public static <T> T guard(Feature f, Supplier<T> call, T fallback) {
        if (!GameState.current().running()) {
            return fallback;                    // a recorded game (tests, the J menu preview): no Wynntils
        }
        try {
            return call.get();
        } catch (LinkageError e) {
            // Wynntils no longer has what we call: this feature is off for the session
            boolean first;
            synchronized (DOWN) {
                first = !DOWN.containsKey(f);
                DOWN.putIfAbsent(f, e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage()));
            }
            if (first) {
                System.out.println("[WynnLabs] Wynntils " + version() + ": " + f + " is off");
                e.printStackTrace(System.out);
            }
            return fallback;
        } catch (RuntimeException e) {
            // an ordinary failure inside Wynntils (an odd item, a menu not loaded yet): this call
            // only; logged once per feature so a real problem is still visible
            synchronized (ODD) {
                if (ODD.add(f)) {
                    System.out.println("[WynnLabs] Wynntils " + f + " call threw (feature stays on): " + e);
                }
            }
            return fallback;
        }
    }

    private static final java.util.Set<Feature> ODD = java.util.EnumSet.noneOf(Feature.class);

    public static void run(Feature f, Runnable call) {
        guard(f, () -> {
            call.run();
            return null;
        }, null);
    }

    public static boolean up(Feature f) {
        synchronized (DOWN) {
            return !DOWN.containsKey(f);
        }
    }

    /** The features that failed this session, with why. */
    public static Map<Feature, String> down() {
        synchronized (DOWN) {
            return new EnumMap<>(DOWN);
        }
    }

    /** For the J menu preview: forget what was marked down before the recorded game was in. */
    public static void clearDown() {
        synchronized (DOWN) {
            DOWN.clear();
        }
    }

    /** For the debug toggle and tests: mark a feature down by hand. */
    public static void markDown(Feature f, String why) {
        synchronized (DOWN) {
            DOWN.putIfAbsent(f, why);
        }
    }

    public static String version() {
        try {
            return Platform.get().modVersion("wynntils").orElse("?");
        } catch (Throwable t) {
            return "?";                             // never let the guard itself fail
        }
    }

    // ---- members that not every supported Wynntils release has ----
    //
    // Wynn Labs compiles against the newest Wynntils but runs on every release since 4.1.0, the
    // first with Wynncraft 2.2.0 (Fruma). A member that some of those releases lack is looked up
    // here by name, once; the caller then uses that release's own equivalent. Each caller names
    // the member and the releases that have it. Where no equivalent exists, the caller throws
    // {@link #missing}, which its guard turns into "feature off" like any other API change.

    private static final Map<String, Optional<Class<?>>> TYPES = new ConcurrentHashMap<>();
    private static final Map<String, Optional<Method>> METHODS = new ConcurrentHashMap<>();

    /** A Wynntils class by its full name, or empty when this release has none. */
    public static Optional<Class<?>> type(String name) {
        return TYPES.computeIfAbsent(name, n -> {
            try {
                return Optional.of(Class.forName(n, false, Models.class.getClassLoader()));
            } catch (ClassNotFoundException | LinkageError e) {
                return Optional.empty();
            }
        });
    }

    /** A public method of {@code owner}, or empty when this release lacks it. */
    public static Optional<Method> method(Class<?> owner, String name, Class<?>... params) {
        return METHODS.computeIfAbsent(owner.getName() + '#' + name + Arrays.toString(params), k -> {
            try {
                return Optional.of(owner.getMethod(name, params));
            } catch (NoSuchMethodException | LinkageError e) {
                return Optional.empty();
            }
        });
    }

    /** Calls a method from {@link #method}; what it throws comes out as from a direct call. */
    public static Object call(Method m, Object target, Object... args) {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) {
                throw r;
            }
            if (e.getCause() instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(e.getCause());
        } catch (IllegalAccessException e) {
            throw new IncompatibleClassChangeError(m + ": " + e);
        }
    }

    /** Calls a no-argument method of {@code target}'s own class, or throws {@link #missing}. */
    public static Object call(Object target, String name) {
        return call(method(target.getClass(), name).orElseThrow(() -> missing(target.getClass().getSimpleName() + "." + name)),
                target);
    }

    /** Neither the newest member nor an older equivalent is in this Wynntils. */
    public static NoSuchMethodError missing(String what) {
        return new NoSuchMethodError(what + " is not in this Wynntils");
    }

    // ---- self-test ----

    /** Once per world join: one cheap read of every Wynntils feature the mod uses. */
    public static void selfTest() {
        level();
        xp();
        Klass cls = classType();
        hasCharacter();
        item(ItemStack.EMPTY);
        guard(Feature.GEAR_DB, () -> Models.Gear.getAllGearInfos().findAny().isPresent(), false);
        tracking();
        quests();
        abilityTree(cls);
        unlockedAbilities();
        assignedPoints(SkillKind.STRENGTH);
        waypoints();
        objectives();
        professionLevel(ProfessionType.values()[0]);
        container();
        raidDry();
        lootrunState();
        guard(Feature.ASPECTS, () -> Models.Aspect.getAllAspectInfos().findAny().isPresent(), false);
        classAspects(cls);
    }
}
