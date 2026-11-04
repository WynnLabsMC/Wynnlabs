package io.github.anilken.wynnlabs.market;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.EmbeddedKey;
import io.github.anilken.wynnlabs.core.Words;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The Wynnventory key for {@link Prices}' live lookups: the player's own key from the config
 * file, else the one built into the jar. Never logged or shown.
 */
final class PriceKey {

    private PriceKey() {}

    /**
     * The key as last read, so tooltips and price lookups never read the file: read in the
     * background at most once a minute (the player may edit the file by hand), and set at once
     * by {@link #setKey}.
     */
    private static volatile String cachedKey;
    private static volatile long keyReadAt;
    private static final java.util.concurrent.atomic.AtomicBoolean KEY_READING = new java.util.concurrent.atomic.AtomicBoolean();

    /** A pool endpoint answered 401 or 403: the key is refused, as the listings call does. */
    static void refuseKey(int status) {
        Prices.keyInvalid = true;               // stop asking until the player saves another key
        Prices.problem = Words.of("wynnlabs.price.problem_key", status);
        Prices.keyProblem = true;
    }

    private static Path keyFile() {
        return Dirs.config().resolve("wynnventory-key.txt");
    }

    /** Saves the player's key (blank removes it), typed in the settings tab; never logged. */
    static void setKey(String k) {
        try {
            String v = k == null ? "" : k.strip();
            if (v.isEmpty()) {
                Files.deleteIfExists(keyFile());
            } else {
                Files.createDirectories(keyFile().getParent());
                Files.writeString(keyFile(), v, StandardCharsets.UTF_8);
            }
            if (!embeddedRead) {
                embeddedKey = EmbeddedKey.load();   // removing the saved key falls back to it at once
                embeddedRead = true;
            }
            cachedKey = v.isEmpty() ? null : v;
            keyReadAt = System.currentTimeMillis();
            Prices.keyInvalid = false;
            Prices.MISSES.clear();
            if (Prices.keyProblem) {
                Prices.keyProblem = false;
                Prices.problem = null;
            }
        } catch (Exception e) {
            System.out.println("[WynnLabs] could not save the Wynnventory key: " + e.getClass().getSimpleName());
        }
    }

    /**
     * The saved key as "••••1a2b"; "••••" alone for the key built into the jar (so the settings
     * tab does not ask for one, and shows none of its characters); null when there is neither.
     */
    static String maskedKey() {
        String k = cachedKey;
        if (k == null) {
            return key() == null ? null : "••••";
        }
        return "••••" + (k.length() > 4 ? k.substring(k.length() - 4) : "");
    }

    /**
     * The key built into the jar at build time ({@link EmbeddedKey}, task 7.3.6), read once off
     * the game thread with the key file; null when this jar has none. Never logged or shown.
     */
    private static volatile String embeddedKey;
    private static volatile boolean embeddedRead;

    /** Whether the key in use is the one built into the jar (no key saved in config). */
    static boolean embeddedKeyInUse() {
        return cachedKey == null && embeddedKey != null;
    }

    /**
     * The key to send: the player's own key from config/wynnlabs/wynnventory-key.txt when one is
     * saved, else the key built into the jar; null when there is neither.
     */
    static String key() {
        if (System.currentTimeMillis() - keyReadAt > 60_000 && KEY_READING.compareAndSet(false, true)) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    if (!embeddedRead) {
                        embeddedKey = EmbeddedKey.load();
                        embeddedRead = true;
                    }
                    cachedKey = readKeyFile();
                    keyReadAt = System.currentTimeMillis();
                } finally {
                    KEY_READING.set(false);
                }
            });
        }
        String saved = cachedKey;
        return saved != null ? saved : embeddedKey;
    }

    /** Reads the key file now; null when there is none. Off the game thread. */
    static String readKeyFile() {
        try {
            Path f = keyFile();
            if (Files.exists(f)) {
                Prices.KEY_FILE_READS.incrementAndGet();
                String k = Files.readString(f, StandardCharsets.UTF_8).strip();
                return k.isEmpty() ? null : k;
            }
        } catch (Exception ignored) {
            // no key
        }
        return null;
    }
}
