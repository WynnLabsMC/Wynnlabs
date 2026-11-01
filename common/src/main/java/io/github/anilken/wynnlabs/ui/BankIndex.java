package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.advice.Verdict;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.core.Profile;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.market.Vault;
import io.github.anilken.wynnlabs.model.ItemData;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Tier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The bank as the bank tab draws it, built once per change of the saved pages (and again every
 * few seconds for prices and verdicts): the banks in the game's order, each page's slots by their
 * place in the game's grid, what kind and tier each item is, its mark and a text to search in.
 * Pages missing between read ones (and this character's bank when only the account bank is read)
 * are listed as not read.
 */
final class BankIndex {

    /** The kind chips: what an item is for. OTHER has no chip; it shows under "All". */
    enum Kind {
        WEAPON("wynnlabs.bank.kind_weapon"), ARMOUR("wynnlabs.bank.kind_armour"), ACCESSORY("wynnlabs.bank.kind_accessory"),
        MATERIAL("wynnlabs.bank.kind_material"), POTION("wynnlabs.bank.kind_potion"), QUEST("wynnlabs.bank.kind_quest"), OTHER(null);

        final String key;

        Kind(String key) {
            this.key = key;
        }

        String label() {
            return key == null ? "" : L.t(key);
        }
    }

    /** Which bank a page is in: the account's, this character's, another character's, or another character's bag. */
    enum Bank { ACCOUNT, MINE, OTHER, BAG }

    /** One stack in a page: what to draw, its mark (0: none) and whether it shows "?". */
    record Item(Vault.Stored st, Vault.Group group, ItemStack stack, Kind kind, Tier tier, int mark, boolean unknown, String hay) {}

    /** One page: its slots by the game's index (row × 9 + column), how many are used; {@code read} false for a ghost. */
    record Page(String key, Bank bank, String bankId, int number, Map<Integer, Item> slots, int used, boolean read) {

        String name() {
            if (bank == Bank.BAG) {
                List<Vault.Stored> stacks = Vault.page(key);
                return stacks.isEmpty() ? "" : L.t("wynnlabs.bank.page_seen", MenuPaint.ago(stacks.get(0).seen()));
            }
            return L.t(bank == Bank.ACCOUNT ? "wynnlabs.bank.page_account" : "wynnlabs.bank.page_char", number);
        }
    }

    /** One bank's pages under its title. */
    record Group(Bank bank, String title, List<Page> pages) {}

    final List<Group> groups;
    final int cols;
    final int rows;
    /** Read pages, ghost pages not counted. */
    final int read;
    /** What the rules say to sell, and its Trade Market worth (0 when unknown). */
    final List<Vault.Group> junk;
    final double marketWorth;
    /** The tiers that occur, in the tier order. */
    final List<Tier> tiers;
    /** Every kind of item once (its first place stands for it; {@code group()} holds the total), in page order. */
    final List<Item> stacks;

    private BankIndex(List<Group> groups, int cols, int rows, int read, List<Vault.Group> junk, double marketWorth, List<Tier> tiers,
                      List<Item> stacks) {
        this.groups = groups;
        this.cols = cols;
        this.rows = rows;
        this.read = read;
        this.junk = junk;
        this.marketWorth = marketWorth;
        this.tiers = tiers;
        this.stacks = stacks;
    }

    private static BankIndex last;
    private static int lastVersion = -1;
    private static long lastAt;
    /** Prices and verdicts change without the pages: built again this often. */
    private static final long REFRESH_MS = 3000;

    /** The bank now (cached until the pages change or a few seconds pass). */
    static BankIndex get() {
        long now = System.currentTimeMillis();
        if (last == null || lastVersion != Vault.version || now - lastAt > REFRESH_MS) {
            lastVersion = Vault.version;
            lastAt = now;
            last = build();
        }
        return last;
    }

    private static BankIndex build() {
        List<Vault.Group> junk = Vault.junk();
        Set<String> sell = new HashSet<>();
        junk.forEach(j -> sell.add(j.key()));
        double worth = 0;
        for (Vault.Group j : junk) {
            Double v = "Trade Market".equals(Vault.place(j)) ? Vault.value(j) : null;
            worth += v == null ? 0 : v;
        }
        Map<String, Vault.Group> byKey = new HashMap<>();
        Vault.groups().forEach(gr -> byKey.put(gr.key(), gr));
        int[] size = Vault.gridSize();
        Set<Tier> seenTiers = new HashSet<>();
        // the pages by bank, in the game's order
        Map<String, List<Page>> banks = new LinkedHashMap<>();
        Map<String, Bank> kinds = new HashMap<>();
        int read = 0;
        List<String> keys = new ArrayList<>(Vault.pageKeys());
        keys.addAll(Vault.bagKeys());
        for (String key : keys) {
            String id = bankId(key);
            Bank bank = key.startsWith("acct:") ? Bank.ACCOUNT : Vault.isBag(key) ? Bank.BAG : Vault.reachable(key) ? Bank.MINE : Bank.OTHER;
            kinds.put(id, bank);
            Map<Integer, Item> slots = new HashMap<>();
            List<Vault.Stored> stacks = Vault.page(key);
            for (int i = 0; i < stacks.size(); i++) {
                Vault.Stored st = stacks.get(i);
                Item it = item(st, byKey.get(st.key()), sell);
                if (it.tier() != null) {
                    seenTiers.add(it.tier());
                }
                // pages saved before slots were kept: in order
                slots.put(st.slot() >= 0 ? st.slot() : (i / size[0]) * 9 + i % size[0], it);
            }
            banks.computeIfAbsent(id, k -> new ArrayList<>()).add(new Page(key, bank, id, Vault.pageNumber(key), slots, stacks.size(), true));
            read++;
        }
        // this character's bank, when only the others are read: a page to open
        if (!banks.isEmpty() && kinds.values().stream().noneMatch(b -> b == Bank.MINE)) {
            String id = "char:" + Profile.id();
            kinds.put(id, Bank.MINE);
            banks.put(id, new ArrayList<>());
        }
        List<Group> groups = new ArrayList<>();
        for (Bank want : Bank.values()) {
            for (Map.Entry<String, List<Page>> e : banks.entrySet()) {
                if (kinds.get(e.getKey()) != want) {
                    continue;
                }
                groups.add(new Group(want, title(want, e.getKey(), e.getValue()), withGhosts(e.getKey(), want, e.getValue())));
            }
        }
        List<Tier> tiers = new ArrayList<>();
        for (Tier t : Tier.values()) {
            if (seenTiers.contains(t)) {
                tiers.add(t);
            }
        }
        Map<String, Item> once = new LinkedHashMap<>();
        for (Group gr : groups) {
            for (Page p : gr.pages()) {
                List<Integer> order = new ArrayList<>(p.slots().keySet());
                java.util.Collections.sort(order);
                for (int i : order) {
                    Item it = p.slots().get(i);
                    once.putIfAbsent(it.st().key(), it);
                }
            }
        }
        return new BankIndex(List.copyOf(groups), size[0], size[1], read, junk, worth, List.copyOf(tiers), List.copyOf(once.values()));
    }

    /** The pages with the ones missing between them (from page 1) as ghosts; one ghost for a bank never read. */
    private static List<Page> withGhosts(String id, Bank bank, List<Page> pages) {
        Map<Integer, Page> byNumber = new HashMap<>();
        int max = 1;
        for (Page p : pages) {
            byNumber.put(p.number(), p);
            max = Math.max(max, p.number());
        }
        List<Page> out = new ArrayList<>();
        for (int n = 1; n <= max; n++) {
            Page p = byNumber.get(n);
            out.add(p != null ? p : new Page(id + "#" + n, bank, id, n, Map.of(), 0, false));
        }
        return List.copyOf(out);
    }

    private static String title(Bank bank, String id, List<Page> pages) {
        return switch (bank) {
            case ACCOUNT -> L.t("wynnlabs.bank.group_account");
            case MINE -> L.t("wynnlabs.bank.group_mine");
            case OTHER, BAG -> pages.isEmpty() ? L.t("wynnlabs.vault.bank_other_character") : Vault.bankName(pages.get(0).key());
        };
    }

    /** "acct", or "char:" or "bag:" and the character's id. */
    static String bankId(String key) {
        int id = key.indexOf(':') + 1;
        return !key.startsWith("acct:") && key.indexOf(':', id) > 0 ? key.substring(0, key.indexOf(':', id)) : "acct";
    }

    private static Item item(Vault.Stored st, Vault.Group gr, Set<String> sell) {
        ItemStack stack = Vault.stackOf(st);
        ItemView.Box box = Vault.boxOf(st);
        Tier tier = null;
        io.github.anilken.wynnlabs.model.Slot slot = null;
        String k = st.kind();
        if (k.equals("gear")) {
            ItemData d = WynntilsItems.gearByName(st.name());
            if (d != null) {
                tier = d.tier();
                slot = d.slot();
            }
        } else if (box != null) {
            tier = box.tier();
            slot = box.slot();
        } else if (k.startsWith("crafted")) {
            ItemView v = stack == null ? null : WynntilsItems.view(stack).orElse(null);
            if (v != null && v.crafted() != null) {
                tier = Tier.CRAFTED;
                slot = v.crafted().slot();
            }
        }
        if (slot == null && (k.equals("gear") || k.equals("unknown"))) {
            slot = slotByName(st.name());          // gear Wynntils does not know (yet): its name says what it is
        }
        boolean quest = gr != null && !Vault.questsFor(gr).isEmpty();
        Kind kind = quest || k.contains("key") ? Kind.QUEST
                : slot != null && slot.isWeapon() ? Kind.WEAPON
                : slot != null && slot.isArmour() ? Kind.ARMOUR
                : slot != null && slot.isAccessory() ? Kind.ACCESSORY
                : k.equals("ingredient") || k.equals("material") || k.equals("powder") ? Kind.MATERIAL
                : k.contains("potion") || k.contains("consumable") ? Kind.POTION : Kind.OTHER;
        int mark = mark(gr, sell, quest, st);
        boolean priced = k.equals("gear") || k.equals("gearbox") || k.equals("ingredient") || k.equals("material") || k.equals("powder");
        boolean unknown = box != null || gr != null && gr.reachable() && priced && Vault.value(gr) == null;
        String hay = String.join(" ", st.name(), kind.label(), tier == null ? "" : tier.name(), st.prof(),
                st.verdict() == null ? "" : Verdict.of(st.verdict()).label(), Vault.label(st.page())).toLowerCase(Locale.ROOT);
        return new Item(st, gr, stack, kind, tier, mark, unknown, hay);
    }

    private static final String[] ARMOUR = {"helmet", "cap", "mask", "hood", "crown", "chestplate", "tunic", "robe", "leggings",
        "pants", "greaves", "chausses", "boots", "shoes", "sandals", "snowshoes"};
    private static final String[] ACCESSORY = {"ring", "bracelet", "necklace", "amulet", "pendant"};

    /** Armour or an accessory by a word of its name ("Champion Helmet"); null when the name does not say. */
    static io.github.anilken.wynnlabs.model.Slot slotByName(String name) {
        for (String w : name.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
            for (String a : ARMOUR) {
                if (w.equals(a)) {
                    return io.github.anilken.wynnlabs.model.Slot.HELMET;
                }
            }
            for (String a : ACCESSORY) {
                if (w.equals(a)) {
                    return io.github.anilken.wynnlabs.model.Slot.RING;
                }
            }
        }
        return null;
    }

    private static int mark(Vault.Group gr, Set<String> sell, boolean quest, Vault.Stored st) {
        if (gr != null && sell.contains(gr.key())) {
            return Theme.MARK_SELL;
        }
        if (quest) {
            return Theme.MARK_QUEST;
        }
        if (st.verdict() == null) {
            return 0;
        }
        Verdict v = Verdict.of(st.verdict());
        return v == Verdict.IDENTIFY ? Theme.MARK_IDENTIFY : v == Verdict.WEAR || v == Verdict.USE ? Theme.MARK_USE : 0;
    }

    /** What the mark means, for the tooltip; null for none. */
    static String markKey(int mark) {
        return mark == Theme.MARK_SELL ? "wynnlabs.bank.legend_sell" : mark == Theme.MARK_QUEST ? "wynnlabs.bank.legend_quest"
                : mark == Theme.MARK_IDENTIFY ? "wynnlabs.bank.legend_identify" : mark == Theme.MARK_USE ? "wynnlabs.bank.legend_use" : null;
    }

    /** The Trade Market price of one piece, or null. */
    static Double unitPrice(String name) {
        Prices.Price p = Prices.of(name);
        return p == null ? null : p.typical();
    }
}
