package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.core.RepoData;
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

/**
 * The community list's fetch over the shared GitHub data path: the first fetch, a day later only
 * the hash when it is unchanged, offline with a copy on disk, a corrupt copy deleted and fetched
 * again, and the retry delay after a failure with no copy.
 */
class CommunityListTest {

    @TempDir
    Path dir;
    long now = 1_000_000_000L;
    boolean offline;
    byte[] served = list("a");
    final List<String> requests = new ArrayList<>();

    private static byte[] list(String id) {
        return ("{\"version\":1,\"builds\":[{\"id\":\"" + id + "\",\"name\":\"n\",\"link\":\"https://wynnbuilder.github.io/builder/#CZ0\","
                + "\"class\":\"mage\",\"levelMin\":1,\"levelMax\":106,\"tags\":[\"spell\"],\"description\":\"d\",\"source\":\"issue\","
                + "\"checked\":\"2026-11-04\",\"outdated\":false}]}").getBytes(StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        CommunityList.reset();
        CommunityList.clock = () -> now;
        CommunityList.fetcher = url -> {
            requests.add(url);
            if (offline) {
                throw new IOException("offline");
            }
            return url.equals(CommunityList.HASH_URL) ? (RepoData.sha256(served) + "\n").getBytes(StandardCharsets.UTF_8) : served;
        };
    }

    @AfterEach
    void tearDown() {
        CommunityList.reset();
        CommunityList.clock = null;
        CommunityList.fetcher = null;
    }

    @Test
    void firstFetchThenOnlyTheHash() {
        assertEquals(CommunityList.State.MISSING, CommunityList.state());
        CommunityBuilds.Doc d = CommunityList.obtain(dir);
        assertNotNull(d);
        assertEquals(List.of(CommunityList.URL), requests);
        assertEquals(CommunityList.State.READY, CommunityList.state());
        assertTrue(Files.isRegularFile(dir.resolve(CommunityList.FILE)));
        assertSame(d, CommunityList.obtain(dir), "the same day: nothing asked");
        assertEquals(1, requests.size());
        now += RepoData.RECHECK_MILLIS;
        assertSame(d, CommunityList.obtain(dir), "a day later, the hash unchanged: the list on disk");
        assertEquals(List.of(CommunityList.URL, CommunityList.HASH_URL), requests);
        now += RepoData.RECHECK_MILLIS;
        served = list("b");
        CommunityBuilds.Doc fresh = CommunityList.obtain(dir);
        assertEquals("b", fresh.builds().get(0).id(), "a new hash: the new list");
        assertEquals(List.of(CommunityList.URL, CommunityList.HASH_URL, CommunityList.HASH_URL, CommunityList.URL), requests);
    }

    @Test
    void offlineWithACopyAndCorruptCopy() throws Exception {
        CommunityList.obtain(dir);
        CommunityList.reset();
        CommunityList.clock = () -> now;
        offline = true;
        now += RepoData.RECHECK_MILLIS;
        assertNotNull(CommunityList.obtain(dir), "offline: the copy on disk");
        assertEquals(CommunityList.State.READY, CommunityList.state());
        assertNull(CommunityList.why(), "no error shown");

        CommunityList.reset();
        CommunityList.clock = () -> now;
        Files.write(dir.resolve(CommunityList.FILE), new byte[] {1, 2, 3});
        offline = false;
        requests.clear();
        assertNotNull(CommunityList.obtain(dir), "a corrupt copy is fetched again");
        assertEquals(List.of(CommunityList.URL), requests);
    }

    @Test
    void retryDelayAfterAFailure() {
        offline = true;
        assertNull(CommunityList.obtain(dir));
        assertEquals(CommunityList.State.FAILED, CommunityList.state());
        assertEquals(RepoData.RETRY_MILLIS, CommunityList.retryIn());
        offline = false;
        requests.clear();
        now += RepoData.RETRY_MILLIS - 1;
        assertNull(CommunityList.obtain(dir), "not before the delay");
        assertTrue(requests.isEmpty());
        now += 1;
        assertNotNull(CommunityList.obtain(dir));
        assertEquals(CommunityList.State.READY, CommunityList.state());
        assertFalse(requests.isEmpty());
    }
}
