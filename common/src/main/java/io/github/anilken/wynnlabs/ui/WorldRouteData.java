package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.RepoData;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The coarse map of the walkable world ({@link WorldRoute}) on disk: {@code config/wynnlabs/}
 * {@value #FILE}, fetched from our GitHub repository ({@value #URL}) the first time a far route
 * needs it, with a sidecar ({@value #META}) holding its SHA-256, when it was downloaded and when
 * the repository was last asked for a newer one. The hash is checked before every load and a
 * corrupt file deleted. At most once a day the small {@value #HASH_URL} is read and the map
 * fetched again only when the hash differs; offline, the copy on disk serves. One request at a
 * time, on the caller's thread (the guide's route thread, never the render thread), 20 seconds
 * each; any failure leaves the guide to its block-level route with one line in the log, and is
 * not tried again for {@link #RETRY_MILLIS}. Each hop stays over https to GitHub's own hosts, as
 * the update check does. The hand-made walls ({@value #GATES_URL}, {@link WorldGates}) come along
 * with every fetch of the map and every daily look, to {@value #GATES}; a failure keeps the old
 * file, and a malformed one is ignored. So do the roads ({@value #ROADS_URL}, {@link WorldRoads}),
 * to {@value #ROADS}, fetched only when the hash at {@value #ROADS_HASH_URL} is not the one on
 * disk ({@value #ROADS_SHA}).
 */
final class WorldRouteData {

    static final String URL = "https://raw.githubusercontent.com/WynnLabsMC/Wynnlabs/main/data/world-route.bin";
    static final String HASH_URL = "https://raw.githubusercontent.com/WynnLabsMC/Wynnlabs/main/data/world-route.sha256";
    static final String GATES_URL = "https://raw.githubusercontent.com/WynnLabsMC/Wynnlabs/main/data/world-gates.json";
    static final String ROADS_URL = "https://raw.githubusercontent.com/WynnLabsMC/Wynnlabs/main/data/world-roads.bin";
    static final String ROADS_HASH_URL = "https://raw.githubusercontent.com/WynnLabsMC/Wynnlabs/main/data/world-roads.sha256";
    static final String FILE = "world-route.bin";
    static final String ROADS = "world-roads.bin";
    static final String ROADS_SHA = "world-roads.sha256";
    static final String META = "world-route.bin.meta";
    static final String GATES = "world-gates.json";
    /** The repository is asked for a newer map at most this often, ... */
    static final long RECHECK_MILLIS = RepoData.RECHECK_MILLIS;
    /** ... and after a failed download the next try waits this long. */
    static final long RETRY_MILLIS = RepoData.RETRY_MILLIS;
    /** The map is at most this big; more is not the file we asked for. */
    static final long MAX_BYTES = 16L << 20;

    enum State { MISSING, DOWNLOADING, READY, FAILED }

    /** The sidecar: the map's SHA-256 (lower-case hex), when it was downloaded and when the repository was last asked, in epoch millis. */
    record Meta(String sha256, long downloaded, long checked) {

        /** Reads the sidecar; null when there is none or it does not parse. */
        static Meta read(Path file) {
            RepoData.Meta m = RepoData.Meta.read(file);
            return m == null ? null : new Meta(m.sha256(), m.downloaded(), m.checked());
        }

        void write(Path file) throws IOException {
            new RepoData.Meta(sha256, downloaded, checked).write(file);
        }
    }

    /** The data's state for /guide: the map's hash prefix and age in millis when there is one, and why it failed when it did. */
    record Status(State state, String hash, long age, String why) {}

    /** Fetches a URL's body (tests give their own). */
    @FunctionalInterface
    interface Fetcher {
        byte[] get(String url) throws IOException;
    }

    static Fetcher fetcher = WorldRouteData::fetch;
    static java.util.function.LongSupplier clock = System::currentTimeMillis;

    private static volatile WorldRoute map;
    private static volatile WorldRoads roads;
    private static volatile String roadsSha;
    private static volatile Meta loaded;
    private static volatile State state = State.MISSING;
    private static volatile String why;
    /** When the last fetch failed (epoch millis; 0: never). */
    private static long failedAt;
    private static volatile boolean busy;

    private WorldRouteData() {}

    static Path file(Path dir) {
        return dir.resolve(FILE);
    }

    static Path sidecar(Path dir) {
        return dir.resolve(META);
    }

    static Path gatesFile(Path dir) {
        return dir.resolve(GATES);
    }

    static Path roadsFile(Path dir) {
        return dir.resolve(ROADS);
    }

    static Path roadsShaFile(Path dir) {
        return dir.resolve(ROADS_SHA);
    }

    /** The roads in memory (null: none yet), for the block-level route and /guide. */
    static WorldRoads roads() {
        return roads;
    }

    /** The roads' hash prefix, for /guide (null: none). */
    static String roadsHash() {
        String s = roadsSha;
        return s == null ? null : s.substring(0, 8);
    }

    /**
     * The roads file: read from {@code dir} when it is there and matches its hash file; then,
     * with {@code look}, the repository's hash is read and the file fetched when it differs (a
     * failure keeps what is on disk). Never throws.
     */
    private static void roadsSync(Path dir, boolean look) {
        if (roads == null) {
            readRoads(dir);
        }
        if (!look) {
            return;
        }
        try {
            String fresh = new String(fetcher.get(ROADS_HASH_URL), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            if (!fresh.matches("[0-9a-f]{64}") || fresh.equals(roadsSha)) {
                return;
            }
            byte[] bytes = fetcher.get(ROADS_URL);
            String sha = sha256(bytes);
            if (!sha.equals(fresh)) {
                throw new IOException("the roads file does not match its hash");
            }
            WorldRoads r = WorldRoads.load(new java.io.ByteArrayInputStream(bytes));
            RepoData.write(roadsFile(dir), bytes);
            Files.writeString(roadsShaFile(dir), sha + "\n", StandardCharsets.UTF_8);
            roads = r;
            roadsSha = sha;
            System.out.println("[WynnLabs] guide: the world roads " + sha.substring(0, 8) + " are ready (" + bytes.length / 1024 + " KB, "
                    + r.tiles() + " tiles)");
        } catch (IOException | RuntimeException e) {
            System.out.println("[WynnLabs] guide: the world roads could not be fetched: " + e);
        }
    }

    /** The roads file on disk into memory when it matches its hash file; a mismatch deletes both. */
    private static void readRoads(Path dir) {
        Path f = roadsFile(dir);
        try {
            if (!Files.isRegularFile(f) || !Files.isRegularFile(roadsShaFile(dir))) {
                return;
            }
            String want = Files.readString(roadsShaFile(dir), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
            byte[] bytes = Files.readAllBytes(f);
            if (!sha256(bytes).equals(want)) {
                throw new IOException("hash mismatch");
            }
            roads = WorldRoads.load(new java.io.ByteArrayInputStream(bytes));
            roadsSha = want;
        } catch (IOException | RuntimeException e) {
            deleteQuietly(f);
            deleteQuietly(roadsShaFile(dir));
            System.out.println("[WynnLabs] guide: the world roads on disk could not be read, fetching them again: " + e);
        }
    }

    /** The map in memory (null: none yet), for /guide. */
    static WorldRoute current() {
        return map;
    }

    /** Fetches the walls file into {@code dir} (an old one stays on failure) and gives the map what it holds. */
    private static void walls(Path dir, WorldRoute g) {
        try {
            byte[] bytes = fetcher.get(GATES_URL);
            if (WorldGates.parse(new String(bytes, StandardCharsets.UTF_8)) != null) {
                Files.createDirectories(dir);
                Files.write(gatesFile(dir), bytes);
            }
        } catch (IOException | RuntimeException e) {
            // the file on disk, if any, serves
        }
        readWalls(dir, g);
    }

    /** The walls file on disk into the map; none when it is missing or malformed. */
    private static void readWalls(Path dir, WorldRoute g) {
        WorldGates w = null;
        try {
            if (Files.isRegularFile(gatesFile(dir))) {
                w = WorldGates.parse(Files.readString(gatesFile(dir), StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            w = null;
        }
        g.gates(w);
    }

    /** The data's state, for /guide. */
    static Status status() {
        Meta m = loaded;
        return new Status(state, m == null ? null : m.sha256().substring(0, 8), m == null ? 0 : clock.getAsLong() - m.downloaded(), why);
    }

    /**
     * The map, on the caller's thread (the route thread): from memory, else loaded from disk when
     * the file there matches its sidecar, else downloaded now; null when there is none yet or the
     * download failed ({@link #status} says). Once a day the repository is asked for a newer map.
     */
    static synchronized WorldRoute map() {
        if (busy) {
            return map;
        }
        busy = true;
        try {
            return obtain(Dirs.config());
        } finally {
            busy = false;
        }
    }

    /** {@link #map} over {@code dir}. */
    static WorldRoute obtain(Path dir) {
        long now = clock.getAsLong();
        Meta m = loaded;
        if (map == null) {
            m = verified(dir);
            if (m != null) {
                try {
                    WorldRoute g = load(file(dir));
                    readWalls(dir, g);
                    roadsSync(dir, false);
                    map = g;
                    loaded = m;
                    state = State.READY;
                } catch (IOException e) {
                    m = null;
                    deleteQuietly(file(dir));
                    deleteQuietly(sidecar(dir));
                    System.out.println("[WynnLabs] guide: the world route map on disk could not be read, fetching it again: " + e);
                }
            }
        }
        if (map != null && !due(m, now)) {
            return map;
        }
        if (failedAt != 0 && now - failedAt < RETRY_MILLIS) {
            return map;
        }
        if (map != null) {
            // once a day: the hash in the repository; only a different one is worth the download
            try {
                String fresh = new String(fetcher.get(HASH_URL), StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
                if (fresh.equals(m.sha256())) {
                    loaded = m = new Meta(m.sha256(), m.downloaded(), now);
                    m.write(sidecar(dir));
                    walls(dir, map);
                    roadsSync(dir, true);
                    return map;
                }
            } catch (IOException e) {
                failedAt = now;                 // offline: the copy on disk serves, asked again later
                return map;
            }
        }
        State before = state;
        state = State.DOWNLOADING;
        try {
            byte[] bytes = fetcher.get(URL);
            String sha = sha256(bytes);
            WorldRoute fresh = WorldRoute.load(new java.io.ByteArrayInputStream(bytes));
            Meta fm = new Meta(sha, now, now);
            store(dir, bytes, fm);
            walls(dir, fresh);
            roadsSync(dir, true);
            map = fresh;
            loaded = fm;
            state = State.READY;
            why = null;
            System.out.println("[WynnLabs] guide: the world route map " + sha.substring(0, 8) + " is ready (" + bytes.length / 1024 + " KB)");
        } catch (IOException | RuntimeException e) {
            failedAt = now;
            why = e.toString();
            state = map != null ? before : State.FAILED;
            System.out.println("[WynnLabs] guide: the world route map could not be fetched: " + e);
        }
        return map;
    }

    /** Whether the repository is due to be asked for a newer map. */
    static boolean due(Meta m, long now) {
        return RepoData.due(m == null ? 0 : m.checked(), m != null, now);
    }

    /**
     * The sidecar of the map in {@code dir} when the map is there and its SHA-256 matches; null
     * otherwise, a map that does not match (corrupt, or cut short) being deleted with its sidecar.
     */
    static Meta verified(Path dir) {
        RepoData.Meta m = RepoData.verified(file(dir), sidecar(dir));
        return m == null ? null : new Meta(m.sha256(), m.downloaded(), m.checked());
    }

    /** Writes the map and its sidecar, the map through a part file moved into place. */
    static void store(Path dir, byte[] bytes, Meta m) throws IOException {
        RepoData.write(file(dir), bytes);
        m.write(sidecar(dir));
    }

    private static WorldRoute load(Path f) throws IOException {
        try (InputStream in = Files.newInputStream(f)) {
            return WorldRoute.load(in);
        }
    }

    static String sha256(byte[] bytes) {
        return RepoData.sha256(bytes);
    }

    private static void deleteQuietly(Path p) {
        RepoData.deleteQuietly(p);
    }

    /** One GET over https to GitHub's own hosts ({@link RepoData#fetch}), at most {@link #MAX_BYTES}. */
    static byte[] fetch(String url) throws IOException {
        return RepoData.fetch(url, MAX_BYTES, "world route data");
    }

    /** Tests: as a fresh run. */
    static synchronized void reset() {
        map = null;
        roads = null;
        roadsSha = null;
        loaded = null;
        state = State.MISSING;
        why = null;
        failedAt = 0;
        busy = false;
    }
}
