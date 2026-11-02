package io.github.anilken.wynnlabs.build;

import io.github.anilken.wynnlabs.model.BaseStats;
import io.github.anilken.wynnlabs.model.ItemView;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.Roll;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * WynnBuilder's custom items ("CI-…"), written and read by the mod's own code from the format
 * alone: how a crafted piece you wear goes into a build link, since the game shows its numbers
 * but never its recipe. A bit string, lowest bit first, six bits per letter: a 0 bit, a 7-bit
 * version (2), a fixed-ids flag (0: every id one value), then per field its 10-bit index in the
 * site's save order and its value: type, tier, attack speed and class as 4-bit indexes, text
 * (the name, damage ranges "100-120") as a 16-bit letter count and the letters (Bootstring, the
 * site's parameters), numbers as a 5-bit width minus one and the value signed in that width;
 * then 1 to 6 zero bits to end on a whole letter. Nothing here acts in the game.
 *
 * <p>A crafted weapon's neutral damage is one number on the site: it reads the text up to its
 * dash as the base and shows 0.9 to 1.1 of it. So the neutral range goes out as its middle
 * ("110-110" for 100-120), the site's damage then averages what the game's does, and a crafted
 * range comes back the way the site reads it.
 */
public final class WynnBuilderCustom {

    public static final String PREFIX = "CI-";
    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+-";
    private static final int VERSION = 2;

    /** The site's field order: a field is written as its index here (entries are never removed). */
    private static final List<String> ORDER = List.of("name", "lore", "tier", "set", "slots", "type",
            "material", "drop", "quest",
            "nDam", "fDam", "wDam", "aDam", "tDam", "eDam",
            "atkSpd", "hp",
            "fDef", "wDef", "aDef", "tDef", "eDef",
            "lvl", "classReq",
            "strReq", "dexReq", "intReq", "defReq", "agiReq",
            "str", "dex", "int", "agi", "def", "id",
            "skillpoints", "reqs",
            "nDam_", "fDam_", "wDam_", "aDam_", "tDam_", "eDam_",
            "majorIds", "hprPct", "mr",
            "sdPct", "mdPct",
            "ls", "ms", "xpb", "lb",
            "ref", "thorns", "expd", "spd", "atkTier", "poison", "hpBonus", "spRegen", "eSteal", "hprRaw",
            "sdRaw", "mdRaw",
            "fDamPct", "wDamPct", "aDamPct", "tDamPct", "eDamPct",
            "fDefPct", "wDefPct", "aDefPct", "tDefPct", "eDefPct",
            "spPct1", "spRaw1", "spPct2", "spRaw2", "spPct3", "spRaw3", "spPct4", "spRaw4",
            "rSdRaw",
            "sprint", "sprintReg", "jh", "lq", "gXp", "gSpd", "durability", "duration", "charges", "maxMana", "critDamPct",
            "nSdRaw", "eSdRaw", "tSdRaw", "wSdRaw", "fSdRaw", "aSdRaw",
            "rSdPct", "nSdPct", "eSdPct", "tSdPct", "wSdPct", "fSdPct", "aSdPct",
            "rMdRaw", "nMdRaw", "eMdRaw", "tMdRaw", "wMdRaw", "fMdRaw", "aMdRaw",
            "rMdPct", "nMdPct", "eMdPct", "tMdPct", "wMdPct", "fMdPct", "aMdPct",
            "damRaw", "rDamRaw", "nDamRaw", "eDamRaw", "tDamRaw", "wDamRaw", "fDamRaw", "aDamRaw",
            "damPct", "rDamPct", "nDamPct",
            "healPct",
            "mainAttackRange", "kb", "weakenEnemy", "slowEnemy",
            "rDefPct");
    /** Fields that are text or a list index rather than a number. */
    private static final List<String> TEXT = List.of("name", "lore", "tier", "set", "type", "material", "drop", "quest", "majorIds",
            "classReq", "atkSpd", "displayName", "nDam", "fDam", "wDam", "aDam", "tDam", "eDam", "nDam_", "fDam_", "wDam_", "aDam_",
            "tDam_", "eDam_", "durability", "duration");
    private static final List<String> TYPES = List.of("Helmet", "Chestplate", "Leggings", "Boots", "Ring", "Bracelet", "Necklace",
            "Wand", "Spear", "Bow", "Dagger", "Relik", "Potion", "Scroll", "Food", "WeaponTome", "ArmorTome", "GuildTome",
            "LootrunTome", "GatherXpTome", "DungeonXpTome", "MobXpTome");
    private static final List<String> TIERS = List.of("Normal", "Unique", "Rare", "Legendary", "Fabled", "Mythic", "Set", "Crafted");
    private static final List<String> SPEEDS = List.of("SUPER_SLOW", "VERY_SLOW", "SLOW", "NORMAL", "FAST", "VERY_FAST", "SUPER_FAST");
    private static final List<String> CLASSES = List.of("Warrior", "Assassin", "Mage", "Archer", "Shaman");
    private static final Klass[] CLASS_OF = {Klass.WARRIOR, Klass.ASSASSIN, Klass.MAGE, Klass.ARCHER, Klass.SHAMAN};
    /** Damage fields by element, in {@link Damage#ELEMENTS} order. */
    private static final String[] DAMAGE = {"nDam", "eDam", "tDam", "wDam", "fDam", "aDam"};
    private static final String[] REQS = {"strReq", "dexReq", "intReq", "defReq", "agiReq"};

    /** The game's identification names (as the mod reads them) and the site's for the same id. */
    private static final Map<String, String> SITE_ID = new LinkedHashMap<>();
    private static final Map<String, String> GAME_ID = new HashMap<>();

    static {
        String[] pairs = {"healthRegen", "hprPct", "manaRegen", "mr", "spellDamage", "sdPct", "elementalSpellDamage", "rSdPct",
            "neutralSpellDamage", "nSdPct", "fireSpellDamage", "fSdPct", "waterSpellDamage", "wSdPct", "airSpellDamage", "aSdPct",
            "thunderSpellDamage", "tSdPct", "earthSpellDamage", "eSdPct", "mainAttackDamage", "mdPct",
            "elementalMainAttackDamage", "rMdPct", "neutralMainAttackDamage", "nMdPct", "fireMainAttackDamage", "fMdPct",
            "waterMainAttackDamage", "wMdPct", "airMainAttackDamage", "aMdPct", "thunderMainAttackDamage", "tMdPct",
            "earthMainAttackDamage", "eMdPct", "lifeSteal", "ls", "manaSteal", "ms", "xpBonus", "xpb", "lootBonus", "lb",
            "reflection", "ref", "rawStrength", "str", "rawDexterity", "dex", "rawIntelligence", "int", "rawDefence", "def",
            "rawAgility", "agi", "thorns", "thorns", "poison", "poison", "exploding", "expd", "walkSpeed", "spd",
            "rawAttackSpeed", "atkTier", "rawHealth", "hpBonus", "soulPointRegen", "spRegen", "stealing", "eSteal",
            "healthRegenRaw", "hprRaw", "rawSpellDamage", "sdRaw", "rawElementalSpellDamage", "rSdRaw",
            "rawNeutralSpellDamage", "nSdRaw", "rawFireSpellDamage", "fSdRaw", "rawWaterSpellDamage", "wSdRaw",
            "rawAirSpellDamage", "aSdRaw", "rawThunderSpellDamage", "tSdRaw", "rawEarthSpellDamage", "eSdRaw",
            "rawMainAttackDamage", "mdRaw", "rawElementalMainAttackDamage", "rMdRaw", "rawNeutralMainAttackDamage", "nMdRaw",
            "rawFireMainAttackDamage", "fMdRaw", "rawWaterMainAttackDamage", "wMdRaw", "rawAirMainAttackDamage", "aMdRaw",
            "rawThunderMainAttackDamage", "tMdRaw", "rawEarthMainAttackDamage", "eMdRaw", "damage", "damPct",
            "neutralDamage", "nDamPct", "fireDamage", "fDamPct", "waterDamage", "wDamPct", "airDamage", "aDamPct",
            "thunderDamage", "tDamPct", "earthDamage", "eDamPct", "elementalDamage", "rDamPct", "rawDamage", "damRaw",
            "rawNeutralDamage", "nDamRaw", "rawFireDamage", "fDamRaw", "rawWaterDamage", "wDamRaw", "rawAirDamage", "aDamRaw",
            "rawThunderDamage", "tDamRaw", "rawEarthDamage", "eDamRaw", "rawElementalDamage", "rDamRaw",
            "criticalDamageBonus", "critDamPct", "fireDefence", "fDefPct", "waterDefence", "wDefPct", "airDefence", "aDefPct",
            "thunderDefence", "tDefPct", "earthDefence", "eDefPct", "elementalDefence", "rDefPct", "1stSpellCost", "spPct1",
            "raw1stSpellCost", "spRaw1", "2ndSpellCost", "spPct2", "raw2ndSpellCost", "spRaw2", "3rdSpellCost", "spPct3",
            "raw3rdSpellCost", "spRaw3", "4thSpellCost", "spPct4", "raw4thSpellCost", "spRaw4", "sprint", "sprint",
            "sprintRegen", "sprintReg", "jumpHeight", "jh", "lootQuality", "lq", "gatherXpBonus", "gXp",
            "gatherSpeed", "gSpd", "healingEfficiency", "healPct", "knockback", "kb", "weakenEnemy", "weakenEnemy",
            "slowEnemy", "slowEnemy", "rawMaxMana", "maxMana", "mainAttackRange", "mainAttackRange"};
        for (int i = 0; i < pairs.length; i += 2) {
            SITE_ID.put(pairs[i], pairs[i + 1]);
            GAME_ID.putIfAbsent(pairs[i + 1], pairs[i]);
        }
    }

    /**
     * What a custom item holds, as far as the build model goes: its name, slot, level, whether it
     * is crafted (the site's tier), the class it needs (null: any), skill requirements (Strength …
     * Agility, 0 for none), health, damage by element ({@link Damage#ELEMENTS}, "low-high"; empty
     * for no weapon), attack speed (the game's name, null when none), powder slots and each
     * identification by the game's name with the value it shows.
     */
    public record Item(String name, Slot slot, int level, boolean crafted, Klass klass, List<Integer> reqs, int health,
                       Map<String, String> damages, String speed, int powderSlots, Map<String, Integer> ids) {}

    private WynnBuilderCustom() {}

    // ---- a crafted piece you wear ----

    /**
     * A worn crafted piece as a custom item with the numbers it shows: its rolls, requirements,
     * damage and health. The game does not show a crafted weapon's attack speed: it is the one
     * whose hits a second turn the damage into the DPS the item shows.
     */
    public static Item of(ItemView.Crafted c) {
        List<Integer> reqs = new ArrayList<>(Collections.nCopies(SkillKind.values().length, 0));
        c.requirements().skills().forEach(r -> reqs.set(r.skill().ordinal(), r.points()));
        Map<String, String> damages = new LinkedHashMap<>();
        double perHit = 0;
        for (BaseStats.Damage d : c.damages()) {
            String el = d.type().toLowerCase(Locale.ROOT);
            if (List.of(Damage.ELEMENTS).contains(el) && (d.low() != 0 || d.high() != 0)) {
                damages.put(el, d.low() + "-" + d.high());
                perHit += (d.low() + d.high()) / 2.0;
            }
        }
        String speed = null;
        if (c.slot() != null && c.slot().isWeapon()) {
            int best = 3;
            for (int i = 0; i < Damage.HITS.length && perHit > 0; i++) {
                if (Math.abs(perHit * Damage.HITS[i] - c.dps()) < Math.abs(perHit * Damage.HITS[best] - c.dps())) {
                    best = i;
                }
            }
            speed = SPEEDS.get(best);
        }
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (Roll r : c.rolls()) {
            if (SITE_ID.containsKey(r.key()) && r.value() != 0) {
                ids.merge(r.key(), r.value(), Integer::sum);
            }
        }
        Klass k = c.requiredClass() != null && c.requiredClass() != Klass.NONE ? c.requiredClass() : null;
        return new Item(c.name(), c.slot(), c.level(), true, k, List.copyOf(reqs), c.health(), Collections.unmodifiableMap(damages),
                speed, c.powderSlots(), Collections.unmodifiableMap(ids));
    }

    /**
     * The model's piece for a custom item: its identifications, requirements and health as they
     * are, and for a weapon its damage at its attack speed's hits a second. Crafted when the site
     * names it so (it then helps no other piece's requirements).
     */
    public static Damage.Piece piece(Item it, String key) {
        Map<String, Double> stats = new HashMap<>();
        it.ids().forEach((k, v) -> stats.merge(k, (double) v, Double::sum));
        Map<SkillKind, Integer> reqs = new java.util.EnumMap<>(SkillKind.class);
        for (SkillKind s : SkillKind.values()) {
            int v = s.ordinal() < it.reqs().size() ? it.reqs().get(s.ordinal()) : 0;
            if (v > 0) {
                reqs.put(s, v);
            }
        }
        Map<String, Double> parts = new HashMap<>();
        double perHit = 0;
        for (Map.Entry<String, String> e : it.damages().entrySet()) {
            int[] r = range(e.getValue());
            double mid = (r[0] + r[1]) / 2.0;
            parts.merge(e.getKey(), mid, Double::sum);
            perHit += mid;
        }
        double dps = 0;
        if (it.speed() != null) {
            int tier = Math.max(0, SPEEDS.indexOf(it.speed()));
            stats.put(Damage.FEEL, Damage.feel(it.speed()));
            stats.put(Damage.TIER, (double) tier);
            dps = perHit * Damage.HITS[tier];
        }
        Map<String, Double> shares = new HashMap<>();
        if (perHit > 0) {
            double total = perHit;
            parts.forEach((k, v) -> shares.put(k, v / total));
        } else {
            shares.put("neutral", 1.0);
        }
        return new Damage.Piece(null, key, it.name(), it.slot(), it.level(), stats, reqs, dps, shares, it.health(), it.crafted());
    }

    // ---- the code ----

    /** The code for an item ("CI-…"); null when its slot is not one the site has. */
    public static String encode(Item it) {
        String type = typeName(it.slot());
        if (type == null) {
            return null;
        }
        Bits b = new Bits();
        b.put(0, 1);
        b.put(VERSION, 7);
        b.put(0, 1);                                    // fixed ids: one value each
        Map<String, Object> fields = new HashMap<>();
        fields.put("name", it.name());
        fields.put("tier", it.crafted() ? "Crafted" : "Normal");
        fields.put("slots", it.powderSlots());
        fields.put("type", type);
        for (int i = 0; i < DAMAGE.length; i++) {
            String d = it.damages().get(Damage.ELEMENTS[i]);
            if (d != null && i == 0 && it.crafted()) {
                d = base(d);
            }
            if (d != null && !d.equals("0-0")) {
                fields.put(DAMAGE[i], d);
            }
        }
        if (it.speed() != null && SPEEDS.contains(it.speed())) {
            fields.put("atkSpd", it.speed());
        }
        fields.put("hp", it.health());
        fields.put("lvl", it.level());
        if (it.klass() != null) {
            for (int i = 0; i < CLASS_OF.length; i++) {
                if (CLASS_OF[i] == it.klass()) {
                    fields.put("classReq", CLASSES.get(i));
                }
            }
        }
        for (int i = 0; i < REQS.length && i < it.reqs().size(); i++) {
            fields.put(REQS[i], it.reqs().get(i));
        }
        it.ids().forEach((k, v) -> fields.put(SITE_ID.get(k), v));
        for (int i = 0; i < ORDER.size(); i++) {
            Object v = fields.get(ORDER.get(i));
            if (v instanceof String s && !s.isEmpty()) {
                b.put(i, 10);
                switch (ORDER.get(i)) {
                    case "type" -> b.put(TYPES.indexOf(s), 4);
                    case "tier" -> b.put(TIERS.indexOf(s), 4);
                    case "atkSpd" -> b.put(SPEEDS.indexOf(s), 4);
                    case "classReq" -> b.put(CLASSES.indexOf(s), 4);
                    default -> {
                        String text = Bootstring.encode(s);
                        b.put(text.length() & 0xffff, 16);
                        for (char c : text.toCharArray()) {
                            b.put(DIGITS.indexOf(c), 6);
                        }
                    }
                }
            } else if (v instanceof Integer n && n != 0) {
                b.put(i, 10);
                int width = Math.min(32, 33 - Integer.numberOfLeadingZeros(Math.abs(n)));
                b.put(width - 1, 5);
                b.put(n, width);
            }
        }
        return PREFIX + b.text();
    }

    /**
     * An item from a code ("CI-" optional), or null when it is not one, is in the site's older
     * text form or names a slot the model does not know.
     */
    public static Item decode(String text) {
        if (text == null) {
            return null;
        }
        String s = text.strip();
        if (s.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            s = s.substring(PREFIX.length());
        }
        if (s.isEmpty() || s.chars().anyMatch(c -> DIGITS.indexOf(c) < 0)) {
            return null;
        }
        try {
            return read(new Bits(s));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Item read(Bits b) {
        if (b.take(1) != 0) {
            return null;                                // the older form
        }
        b.take(7);                                      // the version: 2 is the only one so far
        boolean fixed = b.take(1) == 0;
        Map<String, Object> fields = new HashMap<>();
        while (b.at + 10 <= b.size()) {
            int i = b.take(10);
            String id = i < ORDER.size() ? ORDER.get(i) : "";
            if (TEXT.contains(id)) {
                switch (id) {
                    case "type" -> fields.put(id, at(TYPES, b.take(4)));
                    case "tier" -> fields.put(id, at(TIERS, b.take(4)));
                    case "atkSpd" -> fields.put(id, at(SPEEDS, b.take(4)));
                    case "classReq" -> fields.put(id, at(CLASSES, b.take(4)));
                    default -> {
                        int n = b.take(16);
                        StringBuilder t = new StringBuilder(n);
                        for (int k = 0; k < n; k++) {
                            t.append(DIGITS.charAt(b.take(6)));
                        }
                        fields.put(id, Bootstring.decode(t.toString()));
                    }
                }
            } else {
                int width = b.take(5) + 1;
                int v = b.signed(width);
                if (!fixed && GAME_ID.containsKey(id) && !id.equals("str") && !id.equals("dex") && !id.equals("int")
                        && !id.equals("def") && !id.equals("agi")) {
                    v = (v + b.signed(width)) / 2;      // a range: its middle
                }
                fields.put(id, v);
            }
        }
        Slot slot = slotOf((String) fields.get("type"));
        if (slot == null) {
            return null;
        }
        List<Integer> reqs = new ArrayList<>();
        for (String r : REQS) {
            reqs.add(number(fields, r));
        }
        Map<String, String> damages = new LinkedHashMap<>();
        for (int i = 0; i < DAMAGE.length; i++) {
            Object d = fields.get(DAMAGE[i]);
            if (d instanceof String s && !s.equals("0-0")) {
                damages.put(Damage.ELEMENTS[i], i == 0 && "Crafted".equals(fields.get("tier")) ? siteBase(s) : s);
            }
        }
        Map<String, Integer> ids = new LinkedHashMap<>();
        for (String site : ORDER) {
            String game = GAME_ID.get(site);
            if (game != null && fields.get(site) instanceof Integer v && v != 0) {
                ids.put(game, v);
            }
        }
        Klass k = null;
        Object cls = fields.get("classReq");
        if (cls != null && CLASSES.indexOf(cls) >= 0) {
            k = CLASS_OF[CLASSES.indexOf(cls)];
        }
        Object name = fields.get("name");
        Object speed = fields.get("atkSpd");
        return new Item(name == null ? "Custom " + fields.get("type") : (String) name, slot, number(fields, "lvl"),
                "Crafted".equals(fields.get("tier")), k, List.copyOf(reqs), number(fields, "hp"), Collections.unmodifiableMap(damages),
                slot.isWeapon() && speed != null ? (String) speed : null, number(fields, "slots"), Collections.unmodifiableMap(ids));
    }

    /** The site's type name for a slot ("Spear"); null for one it has no custom type for. */
    private static String typeName(Slot slot) {
        if (slot == null || slot == Slot.WEAPON || slot == Slot.ACCESSORY || slot == Slot.UNKNOWN) {
            return null;
        }
        String n = slot.name().charAt(0) + slot.name().substring(1).toLowerCase(Locale.ROOT);
        return TYPES.contains(n) ? n : null;
    }

    private static Slot slotOf(String type) {
        if (type == null) {
            return null;
        }
        Slot s = Slot.of(type.toUpperCase(Locale.ROOT));
        return s == Slot.UNKNOWN ? null : s;
    }

    private static String at(List<String> l, int i) {
        return i < l.size() ? l.get(i) : null;
    }

    private static int number(Map<String, Object> fields, String id) {
        return fields.get(id) instanceof Integer v ? v : 0;
    }

    /** A crafted weapon's neutral range as the one number the site reads: its middle, "110-110". */
    private static String base(String range) {
        int[] r = range(range);
        int mid = Math.floorDiv(r[0] + r[1], 2);
        return mid + "-" + mid;
    }

    /** A crafted neutral range as the site reads it: the number before the dash, both ends ("100-120" is "100-100"). */
    private static String siteBase(String range) {
        int v = range(range)[0];
        return v + "-" + v;
    }

    /** "100-120" (a negative end may lead: "-5-3") as {low, high}. */
    private static int[] range(String s) {
        int dash = s.indexOf('-', 1);
        if (dash < 0) {
            int v = Integer.parseInt(s.strip());
            return new int[] {v, v};
        }
        return new int[] {Integer.parseInt(s.substring(0, dash).strip()), Integer.parseInt(s.substring(dash + 1).strip())};
    }

    /** A bit string, lowest bit first, read and written six bits per letter. */
    private static final class Bits {
        private final List<Boolean> bits = new ArrayList<>();
        int at;

        Bits() {}

        Bits(String text) {
            for (int i = 0; i < text.length(); i++) {
                put(DIGITS.indexOf(text.charAt(i)), 6);
            }
        }

        int size() {
            return bits.size();
        }

        void put(int value, int n) {
            for (int i = 0; i < n; i++) {
                bits.add(((value >> i) & 1) == 1);
            }
        }

        int take(int n) {
            if (at + n > bits.size()) {
                throw new IllegalArgumentException("code too short");
            }
            int v = 0;
            for (int i = 0; i < n; i++) {
                if (bits.get(at + i)) {
                    v |= 1 << i;
                }
            }
            at += n;
            return v;
        }

        /** {@code n} bits as a signed number. */
        int signed(int n) {
            int v = take(n);
            return n >= 32 ? v : v << (32 - n) >> (32 - n);
        }

        String text() {
            put(0, 6 - bits.size() % 6);       // like the site: a whole letter of zeros when it already fits
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < bits.size(); i += 6) {
                int v = 0;
                for (int j = 0; j < 6; j++) {
                    if (bits.get(i + j)) {
                        v |= 1 << j;
                    }
                }
                sb.append(DIGITS.charAt(v));
            }
            return sb.toString();
        }
    }

    /**
     * The site's text form: Bootstring (the scheme behind Punycode) with its own parameters. The
     * link's letters pass as they are, then a '-' and the other characters as variable-length
     * numbers in 62 letters (no '+' or '-'). Its counting differs a little from Punycode's: the
     * link's own letters always count as passed, and decoding does not step past an inserted one.
     */
    static final class Bootstring {
        private static final String BASE = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        private static final int B = 62;
        private static final int TMIN = 1;
        private static final int TMAX = 52;
        private static final int BIAS = 104;
        private static final int DAMP = 700;
        private static final int SKEW = 38;
        private static final char DELIM = '-';

        private Bootstring() {}

        private static boolean basic(int cp) {
            return cp < 128 && DIGITS.indexOf(cp) >= 0;
        }

        private static int threshold(int i, int bias) {
            return Math.max(TMIN, Math.min(TMAX, B * (i + 1) - bias));
        }

        private static int adapt(long delta, int length, boolean first) {
            delta = first ? delta / DAMP : delta >> 1;
            delta = delta / length;
            int k = 0;
            while (delta > ((B - TMIN) * TMAX) >> 1) {
                delta = delta / (B - TMIN);
                k += B;
            }
            return (int) (k + (B - TMIN + 1) * delta / (delta + SKEW));
        }

        static String encode(String raw) {
            StringBuilder out = new StringBuilder();
            TreeSet<Integer> extended = new TreeSet<>();
            raw.codePoints().forEach(cp -> {
                if (basic(cp)) {
                    out.appendCodePoint(cp);
                } else {
                    extended.add(cp);
                }
            });
            int count = out.length();
            if (count > 0) {
                out.append(DELIM);
            }
            if (extended.isEmpty()) {
                return out.toString();
            }
            long delta = 0;
            int n = 0;
            int bias = BIAS;
            boolean first = true;
            int[] cps = raw.codePoints().toArray();
            for (int cp : extended) {
                delta += (long) (cp - n) * (count + 1);
                n = cp;
                for (int c : cps) {
                    if (c < n || basic(c)) {
                        delta++;
                    }
                    if (c == n) {
                        number(out, delta, bias);
                        count++;
                        bias = adapt(delta, count, first);
                        delta = 0;
                        first = false;
                    }
                }
                delta++;
                n++;
            }
            return out.toString();
        }

        private static void number(StringBuilder out, long value, int bias) {
            for (int i = 0; ; i++) {
                int t = threshold(i, bias);
                if (value < t) {
                    out.append(BASE.charAt((int) value));
                    return;
                }
                out.append(BASE.charAt((int) (t + (value - t) % (B - t))));
                value = (value - t) / (B - t);
            }
        }

        static String decode(String s) {
            int delim = s.lastIndexOf(DELIM);
            List<Integer> cps = new ArrayList<>();
            (delim < 0 ? "" : s.substring(0, delim)).codePoints().forEach(cps::add);
            String rest = s.substring(delim + 1);
            int bias = BIAS;
            int n = 0;
            int i = 0;
            boolean first = true;
            int at = 0;
            while (at < rest.length()) {
                long delta = 0;
                long w = 1;
                for (int k = 0; at < rest.length(); k++) {
                    int d = BASE.indexOf(rest.charAt(at++));
                    if (d < 0) {
                        throw new IllegalArgumentException("not a Bootstring letter");
                    }
                    delta += d * w;
                    int t = threshold(k, bias);
                    if (d < t) {
                        break;
                    }
                    w *= B - t;
                }
                long steps = delta + (first ? 0 : 1);
                n += (int) ((i + steps) / (cps.size() + 1));
                i = (int) ((i + steps) % (cps.size() + 1));
                cps.add(i, n);
                bias = adapt(delta, cps.size(), first);
                first = false;
            }
            StringBuilder out = new StringBuilder();
            cps.forEach(out::appendCodePoint);
            return out.toString();
        }
    }
}
