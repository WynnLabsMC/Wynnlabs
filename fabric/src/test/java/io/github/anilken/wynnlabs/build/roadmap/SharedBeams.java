package io.github.anilken.wynnlabs.build.roadmap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The road map's set searches ({@link BeamCache}) kept on disk for every test JVM: the eight
 * side by side and the next run search each set once. A file per search key in the folder
 * Gradle names (-Dwynnlabs.beamCache: under fabric/build, named by a hash of the mod's classes
 * and the fixtures, so a changed model or recording starts empty). A JVM that is about to search
 * a key holds its lock file; another one that wants the same key waits for its result instead of
 * searching it too. On for every test (SharedBeamsExtension); a test that calls
 * {@link BeamCache#clear} searches anew.
 */
public final class SharedBeams implements BeamCache.Shared {

    private static final Gson GSON = new Gson();
    /** How long a JVM waits for another's search before it searches the key itself. */
    private static final long WAIT_MS = 90_000;

    private static SharedBeams instance;

    private final Path dir;
    /** The keys this JVM is searching: their locks, released when the result is kept. */
    private final Map<String, Held> held = new ConcurrentHashMap<>();

    private record Held(FileChannel channel, FileLock lock) {}

    private SharedBeams(Path dir) {
        this.dir = dir;
    }

    /** The store for this JVM, or null when Gradle named no folder. */
    static synchronized SharedBeams get() {
        if (instance == null) {
            String d = System.getProperty("wynnlabs.beamCache", "");
            if (d.isEmpty()) {
                return null;
            }
            try {
                instance = new SharedBeams(Files.createDirectories(Path.of(d)));
            } catch (IOException e) {
                return null;
            }
        }
        return instance;
    }

    /** Shares the searches for the next test (a clear in the last one turned it off). */
    public static void attach() {
        SharedBeams s = get();
        if (s != null) {
            s.releaseAll();
        }
        BeamCache.share(s);
    }

    @Override
    public Map<String, String> load(String key) {
        Map<String, String> e = read(key);
        if (e != null || held.containsKey(key)) {
            return e;
        }
        // nobody's result yet: take the key, or wait for the JVM that has it
        long until = System.currentTimeMillis() + WAIT_MS;
        try {
            FileChannel ch = FileChannel.open(dir.resolve(key + ".lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            while (true) {
                FileLock lock;
                try {
                    lock = ch.tryLock();
                } catch (OverlappingFileLockException ex) {
                    ch.close();
                    return null;                     // another thread here searches it: search too
                }
                if (lock != null) {
                    e = read(key);                   // kept while we waited
                    if (e != null) {
                        lock.release();
                        ch.close();
                        return e;
                    }
                    held.put(key, new Held(ch, lock));
                    return null;                     // ours to search; keep() lets go
                }
                if (System.currentTimeMillis() > until) {
                    ch.close();
                    return null;
                }
                Thread.sleep(50);
                e = read(key);
                if (e != null) {
                    ch.close();
                    return e;
                }
            }
        } catch (IOException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void keep(String key, Map<String, String> entry) {
        try {
            Path tmp = Files.createTempFile(dir, key, ".tmp");
            Files.writeString(tmp, GSON.toJson(entry), StandardCharsets.UTF_8);
            Files.move(tmp, dir.resolve(key + ".json"), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            // another JVM wrote it first (Windows refuses the replace while it is read): the same set
        } finally {
            release(key);
        }
    }

    private Map<String, String> read(String key) {
        Path f = dir.resolve(key + ".json");
        if (!Files.exists(f)) {
            return null;
        }
        try {
            return GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8),
                    new TypeToken<Map<String, String>>() {}.getType());
        } catch (Exception ex) {
            return null;                             // being replaced: searched anew
        }
    }

    private void release(String key) {
        Held h = held.remove(key);
        if (h != null) {
            try {
                h.lock().release();
                h.channel().close();
            } catch (IOException ex) {
                // the JVM's exit lets it go
            }
        }
    }

    /** Lets go of the keys a test took and never searched (it failed or was cancelled). */
    private void releaseAll() {
        held.keySet().forEach(this::release);
    }
}
