package io.github.anilken.wynnlabs.ui;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The guide line's look at the terrain ({@link GuideLine}): column heights, the blocks the route
 * may step on ({@link GuideTerrain}), and whether the player is riding or gliding. Reading only.
 */
final class GroundProbe {

    /** A block at most this tall (a carpet, a flower pot) is stepped over at the feet. */
    private static final double THIN = 0.375;

    private GroundProbe() {}

    /** The highest block of a column, {@link GuideLine#UNKNOWN} where its chunk is not loaded. */
    static GuideLine.Ground heights(ClientLevel level) {
        return (x, z) -> level.hasChunk(x >> 4, z >> 4)
                ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : GuideLine.UNKNOWN;
    }

    /** Columns the player can step to, read from loaded chunks only (an unloaded one has none). */
    static GuidePath.Walkable walkable(ClientLevel level) {
        WorldRoads r = WorldRouteData.roads();
        return new GuideTerrain(blocks(level), r == null ? null : r::at);
    }

    /** Every block's code ({@link GuideTerrain}), {@link GuideTerrain#UNKNOWN} where its chunk is not loaded. */
    static GuideTerrain.Blocks blocks(ClientLevel level) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // a block's code is asked for many times over in one search (every column from several
        // heights, every spot's neighbours for the step cost): worked out once and kept
        it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap seen = new it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap(1 << 16);
        return new GuideTerrain.Blocks() {
            private int cx = Integer.MIN_VALUE;
            private int cz;
            private ChunkAccess chunk;

            @Override
            public char at(int x, int y, int z) {
                long key = BlockPos.asLong(x, y, z);
                byte known = seen.get(key);
                if (known != 0) {
                    return (char) known;
                }
                if (x >> 4 != cx || z >> 4 != cz) {
                    cx = x >> 4;
                    cz = z >> 4;
                    chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
                }
                char c;
                if (chunk == null) {
                    c = GuideTerrain.UNKNOWN;
                } else {
                    pos.set(x, y, z);
                    c = code(chunk, pos, chunk.getBlockState(pos));
                }
                seen.put(key, (byte) c);
                return c;
            }
        };
    }

    /**
     * A block's code for the route: a barrier, lava, climbable, door, slab or stair first; then whatever the
     * player walks through (no collision, something thin like a carpet or snow layer, a chain, a
     * cobweb) is water or a non-solid; the rest is a fence or wall (taller than a block), a road
     * block or plain solid.
     */
    static char code(BlockGetter level, BlockPos pos, BlockState s) {
        if (s.isAir()) {
            return GuideTerrain.AIR;
        }
        if (s.is(Blocks.BARRIER)) {
            return GuideTerrain.BARRIER;
        }
        FluidState fluid = s.getFluidState();
        if (fluid.is(FluidTags.LAVA)) {
            return GuideTerrain.LAVA;
        }
        if (s.is(BlockTags.CLIMBABLE)) {
            return GuideTerrain.CLIMB;
        }
        if (door(s)) {
            return GuideTerrain.DOOR;
        }
        if (s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS)) {
            return GuideTerrain.HALF;
        }
        VoxelShape shape = s.getCollisionShape(level, pos);
        if (!s.blocksMotion() || shape.isEmpty() || shape.max(Direction.Axis.Y) <= THIN || s.is(BlockTags.CHAINS)) {
            return fluid.is(FluidTags.WATER) ? GuideTerrain.WATER : GuideTerrain.THIN;
        }
        if (shape.max(Direction.Axis.Y) > 1) {
            return GuideTerrain.TALL;
        }
        return road(s) ? GuideTerrain.ROAD : GuideTerrain.SOLID;
    }

    /** A door, fence gate or trapdoor: the player opens it. */
    private static boolean door(BlockState s) {
        return s.is(BlockTags.DOORS) || s.is(BlockTags.FENCE_GATES) || s.is(BlockTags.TRAPDOORS);
    }

    /** A road or stair block, which the route prefers. */
    private static boolean road(BlockState s) {
        return s.is(Blocks.DIRT_PATH) || s.is(Blocks.GRAVEL) || s.is(Blocks.COBBLESTONE)
                || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS)
                || s.is(Blocks.CRACKED_STONE_BRICKS) || s.is(Blocks.SMOOTH_STONE)
                || s.is(BlockTags.PLANKS) || s.is(BlockTags.SLABS) || s.is(BlockTags.STAIRS);
    }

    /** On a mount or gliding: the straight line through the air. */
    static boolean airborne(Player p) {
        return p.isPassenger() || p.isFallFlying();
    }
}
