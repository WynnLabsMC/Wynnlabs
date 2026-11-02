package io.github.anilken.wynnlabs.game;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Distant Horizons' terrain, read through its public API by reflection, so the mod neither needs
 * nor names it: without Distant Horizons (or before its world has loaded) every column is unknown.
 * Players with WynnLODGrabber have Wynncraft's whole surface in it, far beyond the loaded chunks.
 *
 * <p>{@code IDhApiTerrainDataRepo.getColumnDataAtBlockPos} waits for the data to be read from
 * disk, so {@link #surface} is for a worker thread (the guide route's), never the render thread.
 * Not thread safe: one caller thread.
 */
public final class DistantHorizons {

    /** The surface of a column: feet height on it, whether it is water or lava, and the LOD detail level (0: a block). */
    public record Surface(int feet, boolean water, boolean lava, int detail) {}

    private static final String API = "com.seibel.distanthorizons.api.";

    /** 0: not looked for yet, 1: there, -1: missing or broken (off until restart). */
    private static int state;
    private static volatile String error;
    private static Field repoField;
    private static Field worldField;
    private static Method worldLoaded;
    private static Method levels;
    private static Method column;
    private static Method softCache;
    private static Field success;
    private static Field payload;
    private static Field detailField;
    private static Field topField;
    private static Field stateField;
    private static Method isAir;
    private static Method isSolid;
    private static Method isLiquid;
    private static Method serial;
    /** The level wrapper read from and DH's soft cache for it. */
    private static Object level;
    private static Object cache;

    private DistantHorizons() {}

    /** Why Distant Horizons cannot be read (null: it can, or it has not been tried yet). */
    public static String error() {
        return error;
    }

    /**
     * Whether Distant Horizons is there with a world loaded; picks its level (the client's one)
     * for {@link #surface}. Call once per round of reads.
     */
    public static boolean ready() {
        if (!find()) {
            return false;
        }
        try {
            Object world = worldField.get(null);
            if (world == null || repoField.get(null) == null || !(boolean) worldLoaded.invoke(world)) {
                level = null;
                return false;
            }
            Object first = null;
            for (Object l : (Iterable<?>) levels.invoke(world)) {
                first = l;
                break;
            }
            if (first != level) {
                level = first;
                cache = first == null ? null : softCache.invoke(repoField.get(null));
            }
            return level != null;
        } catch (Throwable e) {
            fail(e);
            return false;
        }
    }

    /**
     * The surface Distant Horizons has for column (x, z): the highest block that is not air, a
     * plant, leaves or a log; null with no data there.
     */
    public static Surface surface(int x, int z) {
        if (state != 1 || level == null) {
            return null;
        }
        try {
            Object result = column.invoke(repoField.get(null), level, x, z, cache);
            if (result == null || !(boolean) success.get(result) || !(payload.get(result) instanceof Object[] points)) {
                return null;
            }
            int top = Integer.MIN_VALUE;
            boolean water = false;
            boolean lava = false;
            int detail = 0;
            for (Object p : points) {
                if (p == null) {
                    continue;
                }
                Object block = stateField.get(p);
                if (block == null || (boolean) isAir.invoke(block)) {
                    continue;
                }
                boolean liquid = (boolean) isLiquid.invoke(block);
                String name = String.valueOf(serial.invoke(block)).toLowerCase(Locale.ROOT);
                if (!liquid && (!(boolean) isSolid.invoke(block) || foliage(name))) {
                    continue;                           // grass, flowers, a tree: the ground is under it
                }
                int y = topField.getInt(p);
                if (y > top) {
                    top = y;
                    water = liquid && !name.contains("lava");
                    lava = liquid && name.contains("lava");
                    detail = detailField.getByte(p);
                }
            }
            return top == Integer.MIN_VALUE ? null : new Surface(top, water, lava, detail);
        } catch (Throwable e) {
            fail(e);
            return null;
        }
    }

    private static boolean foliage(String name) {
        return name.contains("leaves") || name.contains("_log") || name.contains("_wood");
    }

    private static boolean find() {
        if (state != 0) {
            return state == 1;
        }
        try {
            Class<?> delayed = load(API + "DhApi$Delayed");
            Class<?> repo = load(API + "interfaces.data.IDhApiTerrainDataRepo");
            Class<?> world = load(API + "interfaces.world.IDhApiWorldProxy");
            Class<?> levelType = load(API + "interfaces.world.IDhApiLevelWrapper");
            Class<?> cacheType = load(API + "interfaces.data.IDhApiTerrainDataCache");
            Class<?> result = load(API + "objects.DhApiResult");
            Class<?> point = load(API + "objects.data.DhApiTerrainDataPoint");
            Class<?> block = load(API + "interfaces.block.IDhApiBlockStateWrapper");
            repoField = delayed.getField("terrainRepo");
            worldField = delayed.getField("worldProxy");
            worldLoaded = world.getMethod("worldLoaded");
            levels = world.getMethod("getAllLoadedLevelWrappers");
            column = repo.getMethod("getColumnDataAtBlockPos", levelType, int.class, int.class, cacheType);
            softCache = repo.getMethod("createSoftCache");
            success = result.getField("success");
            payload = result.getField("payload");
            detailField = point.getField("detailLevel");
            topField = point.getField("topYBlockPos");
            stateField = point.getField("blockStateWrapper");
            isAir = block.getMethod("isAir");
            isSolid = block.getMethod("isSolid");
            isLiquid = block.getMethod("isLiquid");
            serial = block.getMethod("getSerialString");
            state = 1;
            return true;
        } catch (ClassNotFoundException e) {
            state = -1;
            error = "Distant Horizons is not installed";
            return false;
        } catch (Throwable e) {
            fail(e);
            return false;
        }
    }

    private static Class<?> load(String name) throws ClassNotFoundException {
        try {
            return Class.forName(name, false, DistantHorizons.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            if (context == null) {
                throw e;
            }
            return Class.forName(name, false, context);
        }
    }

    private static void fail(Throwable e) {
        state = -1;
        level = null;
        cache = null;
        error = e.toString();
        System.out.println("[WynnLabs] Distant Horizons terrain off until restart: " + e);
    }
}
