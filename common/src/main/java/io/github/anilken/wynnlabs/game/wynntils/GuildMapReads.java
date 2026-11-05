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
            routes = info.getTradingRoutes() == null ? 0 : info.getTradingRoutes().size();
            hq = info.isHeadquarters();
        }
        return new GuildMapLook.Territory(p.getName(), guild, prefix, colour, acquired, hq, treasury, tl, defence, dl,
                res, routes, info != null);
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
