package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.search.Area;
import io.github.anilken.wynnlabs.search.Entry;
import io.github.anilken.wynnlabs.search.Source;
import io.github.anilken.wynnlabs.search.Things;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Map;

/**
 * The badge of a kind of thing (a town, a merchant, a station, a dungeon, a mob, a loot chest ...)
 * wherever a source or a thing is shown: the search list, the card's head and its source rows,
 * the box's sources, Home's gear. One family: a light tile in the kind's colour
 * ({@link Theme#sourceFill}) with a soft top light and a one-screen-pixel ring of that colour; on
 * it Wynntils' own map icon for that kind when Wynntils has one (from 16 px; drawn as it is, never
 * copied), else our glyph in the kind's colour, one per kind and service. At least
 * {@link #MIN} px: below that a glyph is mush.
 *
 * @param fill  the icon whose colour the badge takes (a source's badge)
 * @param glyph our glyph for it
 * @param wyn   the key of Wynntils' icon ({@link Wyn#poiIcon}), or null
 */
record KindBadge(String fill, String glyph, String wyn) {

    static final int MIN = 12;
    /** From this size Wynntils' pixel icons read; below it the glyph does. */
    private static final int POI_MIN = 16;
    private static final int PAPER = 0xFFF4E9C8;

    /** A service's own glyph, by its map id ("blacksmith", "merchant:potion", "profession:cooking"). */
    private static final Map<String, String> SERVICE = Map.ofEntries(
            Map.entry("blacksmith", "hammer"), Map.entry("trade-market", "scale"), Map.entry("booth-shop", "shopping-cart"),
            Map.entry("fast-travel", "route"), Map.entry("seaskipper", "route"), Map.entry("identifier", "eye"),
            Map.entry("item-upgrader", "sparkles"), Map.entry("party-finder", "user"), Map.entry("housing-balloon", "home"),
            Map.entry("mount-enclosure", "tag"), Map.entry("merchant:potion", "flask-2"), Map.entry("merchant:emerald", "coin"),
            Map.entry("merchant:liquid-emerald", "droplet"), Map.entry("merchant:scroll", "script"),
            Map.entry("merchant:dungeon-scroll", "script"), Map.entry("merchant:dungeon", "trophy"), Map.entry("merchant:weapon", "sword"),
            Map.entry("merchant:armor", "shield"), Map.entry("merchant:tool", "pick"), Map.entry("merchant:exchange", "arrows-horizontal"),
            Map.entry("merchant:mount", "tag"), Map.entry("profession:alchemism", "flask-2"), Map.entry("profession:armoring", "helmet"),
            Map.entry("profession:cooking", "meat"), Map.entry("profession:jeweling", "diamond"), Map.entry("profession:scribing", "pencil"),
            Map.entry("profession:tailoring", "shirt"), Map.entry("profession:weaponsmithing", "sword"),
            Map.entry("profession:woodworking", "bow"));
    /** A content's own glyph and Wynntils' map id for it. */
    private static final Map<String, String> CONTENT = Map.of("dungeon", "skull", "corrupted-dungeon", "skull", "raid", "trophy",
            "cave", "pick", "boss-altar", "flame", "grind-spot", "sword", "lootrun-camp", "flame", "shrine", "sparkles",
            "time-trial", "clock");

    /** A thing of the search (a town, a service, a dungeon, a quest, a mob ...). */
    static KindBadge of(Entry e) {
        String fill = Things.badge(e);
        String type = e.type() == null ? "" : e.type();
        return switch (e.kind()) {
            case SERVICE -> new KindBadge(fill, SERVICE.getOrDefault(type, fill), "service:" + type);
            case CONTENT -> new KindBadge(fill, CONTENT.getOrDefault(type, fill),
                    CONTENT.containsKey(type) && !type.startsWith("corrupted") && !type.equals("time-trial") ? "combat:" + type : null);
            case PLACE -> new KindBadge(fill, "home", "city".equals(type) || "town".equals(type) ? "texture:FLAG" : null);
            case QUEST -> new KindBadge(fill, fill, "texture:QUEST_ICON");
            case DISCOVERY -> new KindBadge(fill, fill, "texture:DISCOVERY_ICON");
            // a mob: Wynntils' grind-spot target; a miniboss: its boss crown
            case MOB -> new KindBadge(fill, fill, "miniboss".equals(type) ? "texture:BOSS_ALTAR" : "texture:GRIND_SPOT");
            default -> new KindBadge(fill, fill, null);
        };
    }

    /** A source row (an item's way to get it, a thing's place). */
    static KindBadge of(Source s) {
        String b = s.badge();
        String wyn = switch (s.type()) {
            case DUNGEON -> "combat:dungeon";
            case RAID -> "combat:raid";
            case CAVE -> "combat:cave";
            case ALTAR -> "combat:boss-altar";
            case LOOTRUN -> "combat:lootrun-camp";
            case QUEST -> "texture:QUEST_ICON";
            case MARKET -> "service:trade-market";
            case BANK -> "texture:TREASURY";        // Wynntils has no banker icon: its gold chest
            case DUNGEON_MERCHANT -> "service:merchant:dungeon";
            case MOB, MOB_BAND -> "texture:GRIND_SPOT";
            case MINIBOSS -> "texture:BOSS_ALTAR";
            case LOOT_CHEST -> chest(s);
            default -> null;
        };
        return new KindBadge(b, b, wyn);
    }

    /** Only an icon is known (Home's gear sources): its colour and glyph, Wynntils' icon where the icon names one. */
    static KindBadge of(String icon) {
        String wyn = switch (icon) {
            case "coin" -> "service:trade-market";
            case "building-bank" -> "texture:TREASURY";
            default -> null;
        };
        return new KindBadge(icon, icon, wyn);
    }

    /** A loot chest row: Wynntils' chest of its tier (the nearest chest's), T3 when unknown. */
    private static String chest(Source s) {
        Area a = s.main();
        int tier = a == null ? 0 : a.stars();
        return "texture:CHEST_T" + (tier >= 1 && tier <= 4 ? tier : 3);
    }

    /** Draws it at x, y, {@code size} square (at least {@link #MIN}); returns the size drawn. */
    static int draw(GuiGraphics g, int x, int y, int size, KindBadge b) {
        int s = Math.max(MIN, size);
        int colour = Theme.sourceFill(b.fill());
        Kit.tile(g, x, y, s, light(colour), colour);
        Wyn.PoiIcon poi = s >= POI_MIN ? Wyn.poiIcon(b.wyn()) : null;
        if (poi != null && poi.w() > 0 && poi.h() > 0) {
            // Wynntils' own icon at a whole multiple of its size (never a fraction: its pixels stay
            // sharp), as large as leaves a pixel of tile around it; one that does not fit at 1x is not drawn
            int big = Math.max(poi.w(), poi.h());
            int k = Math.max(1, (s - 2) / big);
            if (big * k <= s) {
                int w = poi.w() * k;
                int h = poi.h() * k;
                g.blit(RenderPipelines.GUI_TEXTURED, poi.id(), x + (s - w) / 2, y + (s - h) / 2, 0, 0, w, h, poi.w(), poi.h(),
                        poi.w(), poi.h());
                return s;
            }
        }
        int glyph = (s - 4) & ~1;
        Art.icon(g, b.glyph(), x + Ui.iconIn(s, glyph), y + Ui.iconIn(s, glyph), glyph, colour);
        return s;
    }

    /** The kind's colour a fifth of the way from the page's light paper. */
    static int light(int colour) {
        int r = (colour >> 16) & 255;
        int gr = (colour >> 8) & 255;
        int bl = colour & 255;
        int pr = (PAPER >> 16) & 255;
        int pg = (PAPER >> 8) & 255;
        int pb = PAPER & 255;
        return 0xFF000000 | (pr + (r - pr) / 5) << 16 | (pg + (gr - pg) / 5) << 8 | (pb + (bl - pb) / 5);
    }
}
