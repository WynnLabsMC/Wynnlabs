package io.github.anilken.wynnlabs.dev;

import io.github.anilken.wynnlabs.core.Dirs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dev builds only: records Wynncraft's invisible walls. One loaded chunk a tick, cycling over the
 * {@value #RADIUS} chunks each way around the player, is scanned for barrier blocks below the
 * world's barrier roof ({@value #ROOF}); every column that has one is appended to
 * {@code config/wynnlabs/dev/barriers.jsonl} as {@code {"x","z","lo","hi","s"}} (the lowest and
 * highest barrier y, and the feet height of the walkable surface under the lowest one), once per
 * column (a column already in the file is written again only when its span grows). Chunk
 * sections without a barrier in their palette are skipped, so a scan costs little. The rows are
 * folded into the world route data offline; the mod never reads this file.
 */
final class BarrierScan {

    static final int RADIUS = 6;
    /** Wynncraft roofs the world with barriers from here up; those are not walls. */
    static final int ROOF = 250;
    private static final String FILE = "barriers.jsonl";

    /** A column's barrier span {lo, hi}. */
    private static final Map<Long, int[]> SEEN = new HashMap<>();
    private static boolean loaded;
    private static int step;

    private BarrierScan() {}

    static void onTick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (mc.player == null || level == null) {
            return;
        }
        try {
            if (!loaded) {
                load();
                loaded = true;
            }
            int side = 2 * RADIUS + 1;
            int i = step++ % (side * side);
            int cx = (((int) Math.floor(mc.player.getX())) >> 4) - RADIUS + i % side;
            int cz = (((int) Math.floor(mc.player.getZ())) >> 4) - RADIUS + i / side;
            ChunkAccess chunk = level.getChunk(cx, cz, ChunkStatus.FULL, false);
            if (chunk != null) {
                scan(chunk, cx, cz);
            }
        } catch (Throwable e) {
            System.out.println("[WynnLabs] barrier scan failed: " + e);
        }
    }

    private static void scan(ChunkAccess chunk, int cx, int cz) throws IOException {
        LevelChunkSection[] sections = chunk.getSections();
        int minY = chunk.getMinY();
        int[][] lo = new int[16][16];
        int[][] hi = new int[16][16];
        for (int[] row : lo) {
            java.util.Arrays.fill(row, Integer.MAX_VALUE);
        }
        for (int[] row : hi) {
            java.util.Arrays.fill(row, Integer.MIN_VALUE);
        }
        boolean any = false;
        for (int s = 0; s < sections.length; s++) {
            LevelChunkSection sec = sections[s];
            int bottom = minY + s * 16;
            if (bottom >= ROOF || sec.hasOnlyAir() || !sec.maybeHas(st -> st.is(Blocks.BARRIER))) {
                continue;
            }
            for (int y = 0; y < 16 && bottom + y < ROOF; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        if (sec.getBlockState(x, y, z).is(Blocks.BARRIER)) {
                            lo[x][z] = Math.min(lo[x][z], bottom + y);
                            hi[x][z] = Math.max(hi[x][z], bottom + y);
                            any = true;
                        }
                    }
                }
            }
        }
        if (!any) {
            return;
        }
        StringBuilder out = new StringBuilder();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (hi[x][z] == Integer.MIN_VALUE) {
                    continue;
                }
                int bx = cx * 16 + x;
                int bz = cz * 16 + z;
                long key = ((long) bx << 32) | (bz & 0xFFFFFFFFL);
                int[] was = SEEN.get(key);
                if (was != null && was[0] <= lo[x][z] && was[1] >= hi[x][z]) {
                    continue;
                }
                SEEN.put(key, new int[] {lo[x][z], hi[x][z]});
                out.append("{\"x\":").append(bx).append(",\"z\":").append(bz).append(",\"lo\":").append(lo[x][z])
                        .append(",\"hi\":").append(hi[x][z]).append(",\"s\":").append(surface(chunk, x, z, lo[x][z], minY))
                        .append("}\n");
            }
        }
        if (out.length() > 0) {
            Path dir = Dirs.config().resolve("dev");
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(FILE), out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    /** The feet height on the first solid block under {@code from} in the column (not a barrier); {@code minY} with none. */
    private static int surface(ChunkAccess chunk, int x, int z, int from, int minY) {
        var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int y = from - 1; y >= minY; y--) {
            BlockState st = chunk.getBlockState(pos.set(x, y, z));
            if (st.blocksMotion() && !st.is(Blocks.BARRIER)) {
                return y + 1;
            }
        }
        return minY;
    }

    /** The columns already in the file, so a run after a restart does not write them again. */
    private static void load() throws IOException {
        Path f = Dirs.config().resolve("dev").resolve(FILE);
        if (!Files.isRegularFile(f)) {
            return;
        }
        List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
        for (String line : lines) {
            try {
                var o = com.google.gson.JsonParser.parseString(line).getAsJsonObject();
                long key = ((long) o.get("x").getAsInt() << 32) | (o.get("z").getAsInt() & 0xFFFFFFFFL);
                int[] span = {o.get("lo").getAsInt(), o.get("hi").getAsInt()};
                SEEN.merge(key, span, (a, b) -> new int[] {Math.min(a[0], b[0]), Math.max(a[1], b[1])});
            } catch (RuntimeException ignored) {
                // a line cut short: scanned again
            }
        }
    }
}
