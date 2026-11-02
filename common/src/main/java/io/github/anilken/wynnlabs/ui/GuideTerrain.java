package io.github.anilken.wynnlabs.ui;

/**
 * The guide route's walking rules, read from a block code per block ({@link Blocks}): the game
 * fills the codes from the loaded chunks, a terrain dump ({@link GuideDump}) from a file, so a dead
 * end seen in the game is replayed in a unit test under the very same rules. Pure: no Minecraft types.
 *
 * <p>A spot is feet height {@code y} in a column: the feet and head blocks let the player through
 * and the block under the feet holds them (or the feet are in water: swimming). Lava is never
 * walked into. A step goes at most one block up (not onto a fence or wall) and three down, or down
 * an open column as deep as {@link GuidePath#DROP} (no fall damage in Wynncraft), into water too.
 */
final class GuideTerrain implements GuidePath.Walkable {

    /** Block codes. */
    static final char AIR = '.';
    static final char SOLID = '#';
    /** A barrier: solid like any other, coded apart so a dump also records where they are. */
    static final char BARRIER = 'b';
    /** A solid block a road is paved with (cheaper to walk on). */
    static final char ROAD = 'r';
    static final char WATER = 'w';
    static final char LAVA = 'l';
    /** A ladder, vines, scaffolding: climbed. */
    static final char CLIMB = 'h';
    /** A door, fence gate or trapdoor: opened and walked through. */
    static final char DOOR = 'd';
    /** A slab or stair: stood on, a half step up. */
    static final char HALF = 's';
    /** A fence or wall: stood on from above, never jumped onto. */
    static final char TALL = 'f';
    /** Something walked through: a carpet, pressure plate, flower, vine, cobweb, snow layer, chain. */
    static final char THIN = 'n';
    /** Not loaded. */
    static final char UNKNOWN = '?';

    /** Feet in water cost this much a step (swimming). */
    static final double SWIM = 3;
    /** A step onto a road costs this much of a plain one. */
    static final double ROAD_COST = GuidePath.ROAD;
    /** A step through a door or gate costs this much of a plain one: to be opened, so an open way wins. */
    static final double DOOR_COST = 1.5;
    /** A step up onto a slab or stair: half a block, no jump. */
    static final double HALF_RISE = 0.25;

    /** The code of every block, {@link #UNKNOWN} where its chunk is not loaded. */
    @FunctionalInterface
    interface Blocks {
        char at(int x, int y, int z);
    }

    private final Blocks b;

    GuideTerrain(Blocks b) {
        this.b = b;
    }

    /** Whether the player gets through a block with this code. */
    static boolean passable(char c) {
        return c == AIR || c == THIN || c == DOOR || c == CLIMB || c == WATER;
    }

    /** Whether a block with this code holds the player up. */
    static boolean ground(char c) {
        return c == SOLID || c == BARRIER || c == ROAD || c == HALF || c == DOOR || c == TALL;
    }

    /** Feet at {@code y}: the feet and head let the player through, and the block below holds them or the feet swim. */
    boolean standable(int x, int y, int z) {
        char feet = b.at(x, y, z);
        if (!passable(feet) || !passable(b.at(x, y + 1, z))) {
            return false;
        }
        char below = b.at(x, y - 1, z);
        return ground(below) || feet == WATER && below == WATER;
    }

    /** Feet at {@code y} in a ladder, vines or scaffolding, with room for the head. */
    boolean onLadder(int x, int y, int z) {
        if (b.at(x, y, z) != CLIMB) {
            return false;
        }
        char head = b.at(x, y + 1, z);
        return passable(head);
    }

    @Override
    public int stand(int x, int z, int fromY) {
        if (b.at(x, fromY, z) == UNKNOWN) {
            return GuidePath.NONE;
        }
        for (int y = fromY + 1; y >= fromY - 3; y--) {
            if (y == fromY + 1 && b.at(x, y - 1, z) == TALL) {
                continue;                               // a fence or wall is not jumped onto
            }
            if (standable(x, y, z) || onLadder(x, y, z)) {
                return y;
            }
        }
        // no fall damage in Wynncraft: off a ledge, down an open column to the ground or into water
        if (!passable(b.at(x, fromY + 1, z))) {
            return GuidePath.NONE;
        }
        for (int y = fromY; y >= fromY - GuidePath.DROP; y--) {
            char feet = b.at(x, y, z);
            if (!passable(feet)) {
                return GuidePath.NONE;
            }
            if (y < fromY - 3 && (standable(x, y, z) || feet == WATER)) {
                return y;
            }
        }
        return GuidePath.NONE;
    }

    @Override
    public int climb(int x, int z, int y, int dy) {
        if (onLadder(x, y, z) && onLadder(x, y + dy, z)) {
            return y + dy;
        }
        // swimming up or down a water column
        return b.at(x, y, z) == WATER && b.at(x, y + dy, z) == WATER && standable(x, y + dy, z)
                ? y + dy : GuidePath.NONE;
    }

    @Override
    public double cost(int x, int y, int z) {
        if (b.at(x, y, z) == WATER) {
            return SWIM;
        }
        if (b.at(x, y, z) == DOOR || b.at(x, y + 1, z) == DOOR) {
            return DOOR_COST;
        }
        char below = b.at(x, y - 1, z);
        return below == ROAD || below == HALF ? ROAD_COST : 1;
    }

    @Override
    public double rise(int x, int y, int z) {
        return b.at(x, y - 1, z) == HALF ? HALF_RISE : GuidePath.RISE;
    }

    @Override
    public boolean unknown(int x, int z, int fromY) {
        return b.at(x, fromY, z) == UNKNOWN;
    }

    @Override
    public String why(int x, int z, int fromY) {
        if (b.at(x, fromY, z) == UNKNOWN) {
            return "unloaded";
        }
        boolean lava = false;
        boolean head = false;
        boolean fence = false;
        for (int y = fromY + 1; y >= fromY - 3; y--) {
            char feet = b.at(x, y, z);
            char up = b.at(x, y + 1, z);
            char below = b.at(x, y - 1, z);
            lava |= feet == LAVA || up == LAVA && passable(feet);
            if (passable(feet) && (ground(below) || feet == WATER && below == WATER) && !passable(up) && up != LAVA) {
                head = true;
            }
            fence |= y == fromY + 1 && below == TALL && passable(feet);
        }
        if (lava) {
            return "lava";
        }
        if (fence) {
            return "fence";
        }
        if (head) {
            return "head blocked";
        }
        if (!passable(b.at(x, fromY, z)) && !passable(b.at(x, fromY + 1, z))) {
            return "wall";
        }
        if (passable(b.at(x, fromY + 1, z))) {
            // an open column: how deep does it go?
            for (int y = fromY; y >= fromY - GuidePath.DROP; y--) {
                char feet = b.at(x, y, z);
                if (feet == LAVA) {
                    return "lava";
                }
                if (feet == UNKNOWN) {
                    return "unloaded";
                }
                if (!passable(feet)) {
                    return "no ground";
                }
            }
            return "drop too far";
        }
        return "no ground";
    }
}
