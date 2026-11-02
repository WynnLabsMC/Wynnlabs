package io.github.anilken.wynnlabs.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The world route map on disk: its sidecar, the hash check, the daily look for a newer one, and failures. */
class WorldRouteDataTest {

    private static final long DAY = WorldRouteData.RECHECK_MILLIS;

    @TempDir
    Path dir;
    private long now = 1_700_000_000_000L;
    private final List<String> requests = new ArrayList<>();
    private byte[] served;
    private boolean offline;

    @BeforeEach
    void fresh() throws IOException {
        WorldRouteData.reset();
        served = WorldRouteTest.gridBytes("....", "....");
        WorldRouteData.clock = () -> now;
        WorldRouteData.fetcher = url -> {
            requests.add(url);
            if (offline) {
                throw new IOException("offline");
            }
            return url.equals(WorldRouteData.HASH_URL)
                    ? (WorldRouteData.sha256(served) + "\n").getBytes(StandardCharsets.UTF_8) : served;
        };
    }

    @AfterEach
    void restore() {
        WorldRouteData.reset();
        WorldRouteData.clock = System::currentTimeMillis;
        WorldRouteData.fetcher = WorldRouteData::fetch;
    }

    @Test
    void theSidecarIsWrittenAndReadBack() throws IOException {
        Path f = dir.resolve("m");
        WorldRouteData.Meta m = new WorldRouteData.Meta("ab".repeat(32), 5, 7);
        m.write(f);
        assertEquals(m, WorldRouteData.Meta.read(f));
        Files.writeString(f, "not a hash\n1\n2\n");
        assertNull(WorldRouteData.Meta.read(f));
        assertNull(WorldRouteData.Meta.read(dir.resolve("none")));
    }

    @Test
    void theFirstNeedDownloadsTheMapAndKeepsItWithItsHash() throws IOException {
        assertEquals(WorldRouteData.State.MISSING, WorldRouteData.status().state());
        WorldRoute g = WorldRouteData.obtain(dir);
        assertNotNull(g);
        assertEquals(List.of(WorldRouteData.URL), requests);
        assertTrue(Files.isRegularFile(WorldRouteData.file(dir)));
        WorldRouteData.Meta m = WorldRouteData.Meta.read(WorldRouteData.sidecar(dir));
        assertEquals(WorldRouteData.sha256(served), m.sha256());
        assertEquals(now, m.downloaded());
        assertEquals(now, m.checked());
        WorldRouteData.Status s = WorldRouteData.status();
        assertEquals(WorldRouteData.State.READY, s.state());
        assertEquals(m.sha256().substring(0, 8), s.hash());
        // the same hour: nothing is asked again
        now += 1000;
        assertSame(g, WorldRouteData.obtain(dir));
        assertEquals(1, requests.size());
        assertEquals(1000, WorldRouteData.status().age());
    }

    @Test
    void aDayLaterOnlyTheHashIsAskedForUnlessItDiffers() throws IOException {
        WorldRoute g = WorldRouteData.obtain(dir);
        now += DAY;
        assertSame(g, WorldRouteData.obtain(dir));
        assertEquals(List.of(WorldRouteData.URL, WorldRouteData.HASH_URL), requests);
        assertEquals(now, WorldRouteData.Meta.read(WorldRouteData.sidecar(dir)).checked(), "the look is noted");
        // a newer map in the repository: downloaded
        served = WorldRouteTest.gridBytes("...", "...", "...");
        now += DAY;
        WorldRoute fresh = WorldRouteData.obtain(dir);
        assertNotNull(fresh);
        assertFalse(fresh == g);
        assertEquals(List.of(WorldRouteData.URL, WorldRouteData.HASH_URL, WorldRouteData.HASH_URL, WorldRouteData.URL), requests);
        assertEquals(WorldRouteData.sha256(served), WorldRouteData.Meta.read(WorldRouteData.sidecar(dir)).sha256());
    }

    @Test
    void theCopyOnDiskServesOfflineAndIsLoadedOnTheNextRun() throws IOException {
        WorldRoute g = WorldRouteData.obtain(dir);
        offline = true;
        now += DAY;
        assertSame(g, WorldRouteData.obtain(dir), "offline: the copy on disk");
        assertEquals(WorldRouteData.State.READY, WorldRouteData.status().state());
        // a fresh run: loaded from disk, nothing fetched while it is not due
        WorldRouteData.reset();
        now -= DAY;
        requests.clear();
        assertNotNull(WorldRouteData.obtain(dir));
        assertTrue(requests.isEmpty());
        assertEquals(WorldRouteData.State.READY, WorldRouteData.status().state());
    }

    @Test
    void aFailedDownloadIsNotRetriedAtOnce() {
        offline = true;
        assertNull(WorldRouteData.obtain(dir));
        assertEquals(WorldRouteData.State.FAILED, WorldRouteData.status().state());
        assertTrue(WorldRouteData.status().why().contains("offline"));
        now += 1000;
        assertNull(WorldRouteData.obtain(dir));
        assertEquals(1, requests.size(), "not asked again within the retry wait");
        now += WorldRouteData.RETRY_MILLIS;
        offline = false;
        assertNotNull(WorldRouteData.obtain(dir));
        assertEquals(WorldRouteData.State.READY, WorldRouteData.status().state());
    }

    @Test
    void aCorruptFileIsDeletedAndFetchedAgain() throws IOException {
        WorldRouteData.obtain(dir);
        Files.write(WorldRouteData.file(dir), new byte[] {1, 2, 3});
        WorldRouteData.reset();
        requests.clear();
        assertNull(WorldRouteData.verified(dir));
        assertFalse(Files.exists(WorldRouteData.file(dir)));
        assertFalse(Files.exists(WorldRouteData.sidecar(dir)));
        assertNotNull(WorldRouteData.obtain(dir));
        assertEquals(List.of(WorldRouteData.URL), requests);
        // a file that is not a map at all, though its hash matches its sidecar: deleted, fetched again
        byte[] junk = {9, 9, 9};
        WorldRouteData.store(dir, junk, new WorldRouteData.Meta(WorldRouteData.sha256(junk), now, now));
        WorldRouteData.reset();
        requests.clear();
        assertNotNull(WorldRouteData.obtain(dir));
        assertEquals(List.of(WorldRouteData.URL), requests);
        assertFalse(WorldRouteData.due(new WorldRouteData.Meta("0".repeat(64), now, now), now + DAY - 1));
        assertTrue(WorldRouteData.due(new WorldRouteData.Meta("0".repeat(64), now, now), now + DAY));
        assertTrue(WorldRouteData.due(null, now));
    }
}
