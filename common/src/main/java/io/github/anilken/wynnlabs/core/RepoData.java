package io.github.anilken.wynnlabs.core;

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
 * Data files kept in our GitHub repository's {@code data/} folder ({@value #BASE}) and fetched
 * into {@code config/wynnlabs/}: one GET over https to GitHub's own hosts (redirects followed by
 * hand, each hop checked, a size cap, 20 seconds), the SHA-256 sidecar ({@link Meta}) beside the
 * file, the daily look at the repository's small hash file ({@link #due}), the retry delay after a
 * failure and the write through a part file. The world route map and the community build list
 * share it.
 */
public final class RepoData {

    public static final String BASE = "https://raw.githubusercontent.com/WynnLabsMC/Wynnlabs/main/data/";
    /** The repository is asked for a newer file at most this often, ... */
    public static final long RECHECK_MILLIS = 24L * 60 * 60 * 1000;
    /** ... and after a failed download the next try waits this long. */
    public static final long RETRY_MILLIS = 10 * 60 * 1000;
    public static final Duration TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_HOPS = 5;
    private static final Set<String> HOSTS = Set.of("raw.githubusercontent.com", "github.com", "objects.githubusercontent.com");

    private RepoData() {}

    /** The sidecar: the file's SHA-256 (lower-case hex), when it was downloaded and when the repository was last asked, in epoch millis. */
    public record Meta(String sha256, long downloaded, long checked) {

        /** Reads the sidecar; null when there is none or it does not parse. */
        public static Meta read(Path file) {
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

        public void write(Path file) throws IOException {
            Files.writeString(file, sha256 + "\n" + downloaded + "\n" + checked + "\n", StandardCharsets.UTF_8);
        }
    }

    /** Whether the repository is due to be asked for a newer file. */
    public static boolean due(long checked, boolean known, long now) {
        return !known || now - checked >= RECHECK_MILLIS;
    }

    /**
     * The sidecar of {@code file} when the file is there and its SHA-256 matches; null otherwise,
     * a file that does not match (corrupt, or cut short) being deleted with its sidecar.
     */
    public static Meta verified(Path file, Path sidecar) {
        Meta m = Meta.read(sidecar);
        if (m == null || !Files.isRegularFile(file)) {
            return null;
        }
        try {
            if (sha256(Files.readAllBytes(file)).equals(m.sha256())) {
                return m;
            }
        } catch (IOException ignored) {
            // treated as corrupt
        }
        deleteQuietly(file);
        deleteQuietly(sidecar);
        return null;
    }

    /** Writes {@code bytes} to {@code file} through a part file moved into place. */
    public static void write(Path file, byte[] bytes) throws IOException {
        Files.createDirectories(file.getParent());
        Path part = file.resolveSibling(file.getFileName() + ".part");
        Files.write(part, bytes);
        try {
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(part, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** {@link #write} then the sidecar. */
    public static void store(Path file, Path sidecar, byte[] bytes, Meta m) throws IOException {
        write(file, bytes);
        m.write(sidecar);
    }

    /** The repository's published hash in {@code body} (a .sha256 file), lower-case; null when it is not one. */
    public static String hash(byte[] body) {
        String s = new String(body, StandardCharsets.UTF_8).trim().toLowerCase(Locale.ROOT);
        int space = s.indexOf(' ');
        if (space > 0) {
            s = s.substring(0, space);
        }
        return s.matches("[0-9a-f]{64}") ? s : null;
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void deleteQuietly(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // nothing more to do
        }
    }

    /**
     * One GET, redirects followed by hand, each hop over https to GitHub's own hosts, at most
     * {@code maxBytes}; {@code what} names the data in the User-Agent.
     */
    public static byte[] fetch(String url, long maxBytes, String what) throws IOException {
        URI u = URI.create(url);
        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(TIMEOUT).build();
        for (int hop = 0; hop <= MAX_HOPS; hop++) {
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(u.getScheme()) || !HOSTS.contains(host)) {
                throw new IOException("refused a hop to " + u.getScheme() + "://" + host);
            }
            HttpRequest req = HttpRequest.newBuilder(u)
                    .header("User-Agent", "WynnLabs/" + UpdateCheck.version.get()
                            + " (Minecraft client mod; " + what + "; https://github.com/WynnLabsMC/Wynnlabs)")
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
                byte[] bytes = body.readNBytes((int) maxBytes + 1);
                if (bytes.length > maxBytes) {
                    throw new IOException("more than " + maxBytes + " bytes");
                }
                return bytes;
            }
        }
        throw new IOException("too many redirects");
    }
}
