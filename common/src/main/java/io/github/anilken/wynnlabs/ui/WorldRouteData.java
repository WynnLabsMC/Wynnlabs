package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.UpdateCheck;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
 * file, and a malformed one is ignored.
 */
final class WorldRouteData {

    static final String URL = "https://raw.githubusercontent.com/WynnLabsMC/Wynnlabs/main/data/world-route.bin";
    static final String HASH_URL = "https://raw.githubusercontent.com/WynnLabsMC/Wynnlabs/main/data/world-route.sha256";
    static final String GATES_URL = "https://raw.githubusercontent.com/WynnLabsMC/Wynnlabs/main/data/world-gates.json";
    static final String FILE = "world-route.bin";
    static final String META = "world-route.bin.meta";
    static final String GATES = "world-gates.json";
    /** The repository is asked for a newer map at most this often, ... */
    static final long RECHECK_MILLIS = 24L * 60 * 60 * 1000;
    /** ... and after a failed download the next try waits this long. */
    static final long RETRY_MILLIS = 10 * 60 * 1000;
    static final Duration TIMEOUT = Duration.ofSeconds(20);
    /** The map is at most this big; more is not the file we asked for. */
    static final long MAX_BYTES = 16L << 20;
    private static final int MAX_HOPS = 5;
    private static final Set<String> HOSTS = Set.of("raw.githubusercontent.com", "github.com", "objects.githubusercontent.com");

    enum State { MISSING, DOWNLOADING, READY, FAILED }

    /** The sidecar: the map's SHA-256 (lower-case hex), when it was downloaded and when the repository was last asked, in epoch millis. */
    record Meta(String sha256, long downloaded, long checked) {

        /** Reads the sidecar; null when there is none or it does not parse. */
        static Meta read(Path file) {
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                if (lines.size() < 3 || !lines.get(0).matches("[0-9a-f]{64}")) {
                    return null;
                }
                return new Meta(lines.get(0), Long.parseLong(lines.get(1).trim()), Long.parseLong(lines.get(2).trim()));
            } catch (IOException | RuntimeException e) {
                return null;
            }
        }

        void write(Path file) throws IOException {
            Files.writeString(file, sha256 + "\n" + downloaded + "\n" + checked + "\n", StandardCharsets.UTF_8);
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
        return m == null || now - m.checked() >= RECHECK_MILLIS;
    }

    /**
     * The sidecar of the map in {@code dir} when the map is there and its SHA-256 matches; null
     * otherwise, a map that does not match (corrupt, or cut short) being deleted with its sidecar.
     */
    static Meta verified(Path dir) {
        Path f = file(dir);
        Meta m = Meta.read(sidecar(dir));
        if (m == null || !Files.isRegularFile(f)) {
            return null;
        }
        try {
            if (sha256(Files.readAllBytes(f)).equals(m.sha256())) {
                return m;
            }
        } catch (IOException ignored) {
            // treated as corrupt
        }
        deleteQuietly(f);
        deleteQuietly(sidecar(dir));
        return null;
    }

    /** Writes the map and its sidecar, the map through a part file moved into place. */
    static void store(Path dir, byte[] bytes, Meta m) throws IOException {
        Files.createDirectories(dir);
        Path part = dir.resolve(FILE + ".part");
        Files.write(part, bytes);
        try {
            Files.move(part, file(dir), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, file(dir), StandardCopyOption.REPLACE_EXISTING);
        }
        m.write(sidecar(dir));
    }

    private static WorldRoute load(Path f) throws IOException {
        try (InputStream in = Files.newInputStream(f)) {
            return WorldRoute.load(in);
        }
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // nothing more to do
        }
    }

    /** One GET, redirects followed by hand, each hop over https to GitHub's own hosts, at most {@link #MAX_BYTES}. */
    static byte[] fetch(String url) throws IOException {
        URI u = URI.create(url);
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(TIMEOUT).build();
        for (int hop = 0; hop <= MAX_HOPS; hop++) {
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(u.getScheme()) || !HOSTS.contains(host)) {
                throw new IOException("refused a hop to " + u.getScheme() + "://" + host);
            }
            HttpRequest req = HttpRequest.newBuilder(u)
                    .header("User-Agent", "WynnLabs/" + UpdateCheck.version.get()
                            + " (Minecraft client mod; world route data; https://github.com/WynnLabsMC/Wynnlabs)")
                    .timeout(TIMEOUT).GET().build();
            HttpResponse<InputStream> res;
            try {
                res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted");
            }
            int s = res.statusCode();
            try (InputStream body = res.body()) {
                if (s == 301 || s == 302 || s == 303 || s == 307 || s == 308) {
                    String location = res.headers().firstValue("location").orElse(null);
                    if (location == null) {
                        throw new IOException("redirect without a location");
                    }
                    u = u.resolve(location);
                    continue;
                }
                if (s != 200) {
                    throw new IOException("HTTP " + s);
                }
                byte[] bytes = body.readNBytes((int) MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    throw new IOException("more than " + MAX_BYTES + " bytes");
                }
                return bytes;
            }
        }
        throw new IOException("too many redirects");
    }

    /** Tests: as a fresh run. */
    static synchronized void reset() {
        map = null;
        loaded = null;
        state = State.MISSING;
        why = null;
        failedAt = 0;
        busy = false;
    }
}
