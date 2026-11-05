package io.github.anilken.wynnlabs.game.wynntils;

import com.wynntils.core.components.Models;
import com.wynntils.models.territories.TerritoryInfo;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.models.territories.type.GuildResource;
import com.wynntils.models.territories.type.GuildResourceValues;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.utils.type.CappedValue;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.ui.GuildMapLook;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * The guild map's Wynntils side: territories as {@link GuildMapLook.Territory} values, and the
 * swap that opens the mod's guild map wherever Wynntils would open its own.
 */
public final class GuildMapReads {

    private GuildMapReads() {}

    private static final String WYNNTILS_GUILD_MAP = "com.wynntils.screens.maps.GuildMapScreen";

    /** The screen to open instead of {@code s}: the mod's guild map in place of Wynntils' (setting guildMap on). */
    public static Screen swap(Screen s) {
        if (s == null || !WYNNTILS_GUILD_MAP.equals(s.getClass().getName()) || !Settings.on("guildMap")) {
            return s;
        }
        try {
            return new GuildMapScreen();
        } catch (Throwable t) {
            return s;                       // Wynntils changed under us: its own map
        }
    }

    /** The territories the game told us about (its advancements: holder, resources, defence). */
    static List<TerritoryPoi> pois() {
        try {
            return Models.Territory.getTerritoryPoisFromAdvancement();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** A territory as the map's extras draw it. */
    static GuildMapLook.Territory of(TerritoryPoi p) {
        TerritoryProfile prof = p.getTerritoryProfile();
        TerritoryInfo info = p.isFakeTerritoryInfo() ? null : p.getTerritoryInfo();
        String guild = info != null ? info.getGuildName() : prof == null ? "" : prof.getGuild();
        String prefix = info != null ? info.getGuildPrefix() : prof == null ? "" : prof.getGuildPrefix();
        int colour = 0xFFFFFF;
        if (prof != null && prof.getGuildInfo() != null) {
            colour = prof.getGuildInfo().color().map(c -> c.asInt() & 0xFFFFFF).orElse(colour);
        }
        long acquired = prof == null || prof.getAcquired() == null ? 0 : prof.getAcquired().toEpochMilli();
        List<GuildMapLook.Res> res = new ArrayList<>();
        String treasury = "";
        String defence = "";
        int tl = 0;
        int dl = 0;
        int routes = 0;
        boolean hq = false;
        if (info != null) {
            for (GuildResource r : GuildResource.values()) {
                CappedValue st = info.getStorage(r);
                Integer gen = info.getGeneration(r);
                res.add(new GuildMapLook.Res(r.name(), ink(r), st == null ? 0 : st.current(), st == null ? 0 : st.max(),
                        gen == null ? 0 : gen));
            }
            GuildResourceValues t = info.getTreasury();
            GuildResourceValues d = info.getDefences();
            treasury = t == null ? "" : t.getAsString();
            defence = d == null ? "" : d.getAsString();
            tl = t == null ? 0 : t.getLevel();
            dl = d == null ? 0 : d.getLevel();
            // links: neighbours on a trade route held by the same guild (each adds 30% to the
            // tower's damage and health in a war)
            if (info.getTradingRoutes() != null) {
                for (String n : info.getTradingRoutes()) {
                    TerritoryPoi o = neighbour(n);
                    if (o != null && o.getTerritoryInfo() != null && guild != null && guild.equals(o.getTerritoryInfo().getGuildName())) {
                        routes++;
                    }
                }
            }
            hq = info.isHeadquarters();
        }
        int externals = hq && guild != null ? externals(p.getName(), guild) : 0;
        return new GuildMapLook.Territory(p.getName(), guild, prefix, colour, acquired, hq, treasury, tl, defence, dl,
                res, routes, externals, info != null);
    }

    /**
     * An HQ's externals: territories of its guild within three trade routes of it, whoever holds
     * the ones in between (each adds 25% to the HQ's tower on top of its 50%).
     */
    static int externals(String hq, String guild) {
        java.util.Set<String> seen = new java.util.HashSet<>(List.of(hq));
        List<String> ring = List.of(hq);
        int count = 0;
        for (int step = 0; step < 3; step++) {
            List<String> next = new ArrayList<>();
            for (String name : ring) {
                TerritoryPoi poi = neighbour(name);
                if (poi == null || poi.getTerritoryInfo() == null || poi.getTerritoryInfo().getTradingRoutes() == null) {
                    continue;
                }
                for (String n : poi.getTerritoryInfo().getTradingRoutes()) {
                    if (seen.add(n)) {
                        next.add(n);
                        TerritoryPoi o = neighbour(n);
                        if (o != null && o.getTerritoryInfo() != null && guild.equals(o.getTerritoryInfo().getGuildName())) {
                            count++;
                        }
                    }
                }
            }
            ring = next;
        }
        return count;
    }

    private static TerritoryPoi neighbour(String name) {
        try {
            return Models.Territory.getTerritoryPoiFromAdvancement(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** A resource's ink, as the game's own colour for it. */
    private static int ink(GuildResource r) {
        return switch (r) {
            case EMERALDS -> 0x57D163;
            case ORE -> 0xC9C9C9;
            case WOOD -> 0xE0913E;
            case FISH -> 0x56C8D8;
            case CROPS -> 0xE3C93B;
        };
    }
}
