package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.core.Dirs;
import io.github.anilken.wynnlabs.core.RepoData;
import io.github.anilken.wynnlabs.core.Workers;
import io.github.anilken.wynnlabs.crafting.CraftData;
import io.github.anilken.wynnlabs.crafting.CraftSearch;
import io.github.anilken.wynnlabs.crafting.Crafting;
import io.github.anilken.wynnlabs.crafting.Ingredient;
import io.github.anilken.wynnlabs.crafting.RecipeCode;
import io.github.anilken.wynnlabs.crafting.Shopping;
import io.github.anilken.wynnlabs.crafting.WhatCanICraft;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.LongSupplier;

/**
 * The community build list on disk ({@code config/wynnlabs/}{@value #FILE}) and in memory: fetched
 * from our repository ({@value #URL}) through {@link RepoData} only once the player opens the
 * community view, on its own worker thread, never the game or render thread. A copy on disk whose
 * sidecar hash matches serves (offline too); at most once a day the small {@value #HASH_URL} is
 * read and the list fetched again only when it differs; a failure waits
 * {@link RepoData#RETRY_MILLIS}. The same worker compares the entries with what the player has
 * ({@link CommunityBuilds#compare}), again whenever the bank, the prices, the class, the level or
 * the owned items change. The local API only reads what is here: it never starts a fetch.
 */
public final class CommunityList {

    static final String FILE = "community-builds.json";
    static final String META = "community-builds.json.meta";
    static final String URL = RepoData.BASE + FILE;
    static final String HASH_URL = RepoData.BASE + "community-builds.sha256";
    /** The list is at most this big; more is not the file we asked for. */
    static final long MAX_BYTES = 2L << 20;

    public enum State { MISSING, LOADING, READY, FAILED }

    /** Fetches a URL's body (tests give their own). */
    @FunctionalInterface
    interface Fetcher {
        byte[] get(String url) throws IOException;
    }

    static Fetcher fetcher;
    static LongSupplier clock;

    private static volatile CommunityBuilds.Doc doc;
    private static volatile RepoData.Meta loaded;
    private static volatile State state;
    private static volatile String why;
    private static volatile long failedAt;
    private static volatile boolean busy;
    private static ExecutorService worker;

    /** The comparisons for {@link #compareStamp}; replaced whole by the worker. */
    private static volatile Map<String, CommunityBuilds.Compare> compared;
    private static volatile String compareStamp;
    private static volatile String wantStamp;
    private static volatile boolean comparing;

    private CommunityList() {}

    private static Fetcher fetcher() {
        Fetcher f = fetcher;
        return f != null ? f : url -> RepoData.fetch(url, MAX_BYTES, "community builds");
    }

    private static long now() {
        LongSupplier c = clock;
        return c != null ? c.getAsLong() : System.currentTimeMillis();
    }

    private static synchronized ExecutorService worker() {
        if (worker == null) {
            worker = Workers.single("community");
        }
        return worker;
    }

    public static State state() {
        State s = state;
        return s == null ? State.MISSING : s;
    }

    /** The list in memory, or null. */
    public static CommunityBuilds.Doc doc() {
        return doc;
    }

    /** Millis since the copy in memory was downloaded (0: none). */
    public static long age() {
        RepoData.Meta m = loaded;
        return m == null ? 0 : now() - m.downloaded();
    }

    /** Millis until the next try after a failure (0: none waiting). */
    public static long retryIn() {
        long f = failedAt;
        return f == 0 ? 0 : Math.max(0, RepoData.RETRY_MILLIS - (now() - f));
    }

    /** Why the last fetch failed, or null. */
    public static String why() {
        return why;
    }

    /**
     * The community view is open: the list is loaded from disk or fetched on the worker when it
     * is missing or due a daily look, unless a load runs or a failure is still waiting.
     */
    public static void request() {
        if (busy) {
            return;
        }
        RepoData.Meta m = loaded;
        if (doc != null && !RepoData.due(m == null ? 0 : m.checked(), m != null, now())) {
            return;
        }
        if (failedAt != 0 && now() - failedAt < RepoData.RETRY_MILLIS) {
            return;
        }
        busy = true;
        if (doc == null) {
            state = State.LOADING;
        }
        worker().execute(() -> {
            try {
                obtain(Dirs.config());
            } finally {
                busy = false;
            }
        });
    }

    /** {@link #request}'s work over {@code dir}, on the caller's thread (tests call it straight). */
    static synchronized CommunityBuilds.Doc obtain(Path dir) {
        long now = now();
        Path file = dir.resolve(FILE);
        Path side = dir.resolve(META);
        RepoData.Meta m = loaded;
        if (doc == null) {
            m = RepoData.verified(file, side);
            if (m != null) {
                CommunityBuilds.Doc d = read(file);
                if (d != null) {
                    doc = d;
                    loaded = m;
                    state = State.READY;
                } else {
                    m = null;
                    RepoData.deleteQuietly(file);
                    RepoData.deleteQuietly(side);
                }
            }
        }
        if (doc != null && !RepoData.due(m.checked(), true, now)) {
            return doc;
        }
        if (failedAt != 0 && now - failedAt < RepoData.RETRY_MILLIS) {
            if (doc == null) {
                state = State.FAILED;
            }
            return doc;
        }
        if (doc != null) {
            // once a day: the hash in the repository; only a different one is worth the download
            try {
                String fresh = RepoData.hash(fetcher().get(HASH_URL));
                if (m.sha256().equals(fresh)) {
                    loaded = new RepoData.Meta(m.sha256(), m.downloaded(), now);
                    loaded.write(side);
                    return doc;
                }
            } catch (IOException | RuntimeException e) {
                failedAt = now;                 // offline: the copy on disk serves, asked again later
                return doc;
            }
        }
        if (doc == null) {
            state = State.LOADING;
        }
        try {
            byte[] bytes = fetcher().get(URL);
            CommunityBuilds.Doc d = CommunityBuilds.parse(new String(bytes, StandardCharsets.UTF_8));
            if (d == null) {
                throw new IOException("not the community build list");
            }
            RepoData.Meta fm = new RepoData.Meta(RepoData.sha256(bytes), now, now);
            RepoData.store(file, side, bytes, fm);
            doc = d;
            loaded = fm;
            state = State.READY;
            why = null;
            failedAt = 0;
            compareStamp = null;
            System.out.println("[WynnLabs] community builds: " + d.builds().size() + " builds ready (" + bytes.length / 1024 + " KB)");
        } catch (IOException | RuntimeException e) {
            failedAt = now;
            why = e.toString();
            state = doc != null ? State.READY : State.FAILED;
            System.out.println("[WynnLabs] community builds: the list could not be fetched: " + e);
        }
        return doc;
    }

    private static CommunityBuilds.Doc read(Path file) {
        try {
            return CommunityBuilds.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---- the comparisons ----

    /** The comparisons by entry id (empty until the worker made them). */
    public static Map<String, CommunityBuilds.Compare> compared() {
        Map<String, CommunityBuilds.Compare> c = compared;
        return c == null ? Map.of() : c;
    }

    /** Whether the comparisons on hand were made for what the player has now. */
    public static boolean comparing() {
        return comparing;
    }

    /**
     * The view draws (the game thread): when the list, the bank, the prices, the class, the level,
     * the plan or {@code owned} changed since the comparisons were made, the worker makes them
     * again. Never more than one run at a time.
     */
    public static void compareIfStale(GameState state, CommunityBuilds.Owned owned) {
        CommunityBuilds.Doc d = doc;
        if (d == null || "held".equals(compareStamp)) {
            return;                                 // none yet, or held still for the preview
        }
        Build.Plan plan = Build.plan();
        String stamp = System.identityHashCode(d) + "|" + Vault.version + "|" + Prices.version + "|" + state.classType() + "|"
                + state.level() + "|" + owned.stamp() + "|" + (plan == null ? 0 : System.identityHashCode(plan.current()));
        if (stamp.equals(compareStamp) || stamp.equals(wantStamp) || comparing) {
            return;
        }
        wantStamp = stamp;
        comparing = true;
        WhatCanICraft.Stock stock;
        try {
            stock = Crafting.stock();                // the bag's materials and ingredients, read here on the game thread
        } catch (RuntimeException e) {
            stock = new WhatCanICraft.Stock(Map.of(), Map.of(), Map.of());
        }
        WhatCanICraft.Stock have = stock;
        worker().execute(() -> {
            try {
                compared = compareAll(state, d.builds(), owned, have);
                compareStamp = stamp;
            } catch (Throwable t) {
                System.out.println("[WynnLabs] community builds: the comparison failed: " + t);
                compareStamp = stamp;
            } finally {
                comparing = false;
                wantStamp = null;
            }
        });
    }

    /** Every entry compared (the damage model only for the player's own class). */
    static Map<String, CommunityBuilds.Compare> compareAll(GameState state, List<CommunityBuilds.Entry> entries,
            CommunityBuilds.Owned owned, WhatCanICraft.Stock stock) {
        Map<String, Ingredient> ings = new HashMap<>();
        io.github.anilken.wynnlabs.game.Wyn.craftIngredients().forEach(i -> ings.put(i.name(), i));
        Map<String, CommunityBuilds.Compare> out = new HashMap<>();
        for (CommunityBuilds.Entry e : entries) {
            try {
                CommunityBuilds.Compare c = CommunityBuilds.compare(state, e, owned, Prices::of, ings::get,
                        r -> craftCost(r, ings::get, stock), true);
                if (c != null) {
                    out.put(e.id(), c);
                }
            } catch (RuntimeException ex) {
                // one entry that cannot be read is left out, the rest compared
            }
        }
        return Map.copyOf(out);
    }

    /**
     * What crafting {@code r} costs from what the player has: the missing materials and
     * ingredients at their cached prices ({@link Shopping#of}); null when the recipe or an
     * ingredient is not known or a price is missing.
     */
    public static Long craftCost(RecipeCode.Recipe r, java.util.function.Function<String, Ingredient> ings, WhatCanICraft.Stock stock) {
        CraftData.Recipe rec = CraftData.find(r.type(), r.minLevel());
        if (rec == null) {
            return null;
        }
        List<Ingredient> slots = new java.util.ArrayList<>();
        for (String n : r.slots()) {
            Ingredient i = n == null ? Ingredient.NONE : ings.apply(n);
            if (i == null) {
                return null;
            }
            slots.add(i);
        }
        Map<String, CraftSearch.Price> prices = Crafting.prices(slots, rec);
        Shopping.Needs need = Shopping.of(rec, r.tier1(), r.tier2(), slots, stock, prices::get);
        return need.unpriced().isEmpty() ? need.cost() : null;
    }

    /** Tests and the preview: a list in memory as if fetched. */
    public static synchronized void use(CommunityBuilds.Doc d, Map<String, CommunityBuilds.Compare> c) {
        doc = d;
        state = d == null ? State.MISSING : State.READY;
        loaded = d == null ? null : new RepoData.Meta("0".repeat(64), now(), now());
        compared = c;
        compareStamp = c == null ? null : "held";
        failedAt = 0;
    }

    /** Tests and the preview: the list failed {@code ago} millis back, none in memory. */
    public static synchronized void useFailed(long ago) {
        doc = null;
        loaded = null;
        compared = null;
        state = State.FAILED;
        failedAt = now() - ago;
    }

    /** Tests: as a fresh run. */
    static synchronized void reset() {
        doc = null;
        loaded = null;
        state = null;
        why = null;
        failedAt = 0;
        busy = false;
        compared = null;
        compareStamp = null;
        wantStamp = null;
        comparing = false;
    }
}
