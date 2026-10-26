package io.github.anilken.wynnlabs.quests;

/**
 * The content book's kinds, in the game's order and with its English names (they stay English in
 * both languages, as in the game). World Event and Mount Enclosure are left out: Wynntils keeps
 * no list of events and the game's filter has no enclosures.
 */
public enum ContentType {
    RECOMMENDED("Recommended", "sparkles", null),
    QUEST("Quest", "script", null),
    MINI_QUEST("Mini-Quest", "clipboard", null),
    SECRET_DISCOVERY("Secret Discovery", "crystal-ball", null),
    WORLD_DISCOVERY("World Discovery", "world", null),
    TERRITORIAL_DISCOVERY("Territorial Discovery", "map-2", null),
    CAVE("Cave", "pick", "wynntils:content:cave"),
    DUNGEON("Dungeon", "skull", "wynntils:content:dungeon"),
    RAID("Raid", "shield", "wynntils:content:raid"),
    BOSS_ALTAR("Boss Altar", "target", "wynntils:content:boss-altar"),
    LOOTRUN_CAMP("Lootrun Camp", "route", "wynntils:content:lootrun-camp"),
    TIME_TRIAL("Time Trial", "clock", "wynntils:content:time-trial");

    /** The game's name, shown as it is in both languages. */
    public final String shown;
    /** The icon in the menu and the rail. */
    public final String icon;
    /** Its category in Wynntils' map features ({@code dataStaticCombatMapFeatures}); null: no place. */
    public final String feature;

    ContentType(String shown, String icon, String feature) {
        this.shown = shown;
        this.icon = icon;
        this.feature = feature;
    }

    /** The quest book's own kinds: the guide pane, stages and the order menu. */
    public boolean quest() {
        return this == RECOMMENDED || this == QUEST || this == MINI_QUEST;
    }

    /** One place on the map (a cave, a dungeon, …): the stageless pane with a map. */
    public boolean placed() {
        return feature != null;
    }

    /** A content book kind that is not a quest: read on the refresh press, kept per character. */
    public boolean book() {
        return !quest();
    }

    /** By name, RECOMMENDED for anything unknown. */
    public static ContentType of(String name) {
        for (ContentType t : values()) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return RECOMMENDED;
    }
}
