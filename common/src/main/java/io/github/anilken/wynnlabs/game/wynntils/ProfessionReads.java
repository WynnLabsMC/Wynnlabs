package io.github.anilken.wynnlabs.game.wynntils;

import com.wynntils.core.components.Models;
import com.wynntils.models.profession.type.ProfessionType;
import com.wynntils.utils.mc.type.Location;
import io.github.anilken.wynnlabs.game.Wyn.Feature;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static io.github.anilken.wynnlabs.game.wynntils.Bridge.call;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.guard;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.method;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.missing;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.type;

/**
 * Wynntils reads of professions: crafting ingredients, levels, stations and bombs.
 */
public final class ProfessionReads {

    private ProfessionReads() {}

    /** A profession's level by its name ("COOKING"). An unknown name throws here, outside the guard. */
    /** Wynntils' ingredients as the calculator's plain records, read once; empty while Wynntils has none. */
    private static volatile java.util.List<io.github.anilken.wynnlabs.crafting.Ingredient> ingredients = java.util.List.of();

    public static java.util.List<io.github.anilken.wynnlabs.crafting.Ingredient> craftIngredients() {
        java.util.List<io.github.anilken.wynnlabs.crafting.Ingredient> have = ingredients;
        if (!have.isEmpty()) {
            return have;
        }
        java.util.List<io.github.anilken.wynnlabs.crafting.Ingredient> read = guard(Feature.GEAR_DB, () -> {
            java.util.List<io.github.anilken.wynnlabs.crafting.Ingredient> out = new java.util.ArrayList<>();
            Models.Ingredient.getAllIngredientInfos().forEach(info -> out.add(ingredient(info)));
            out.sort(java.util.Comparator.comparing(io.github.anilken.wynnlabs.crafting.Ingredient::name));
            return java.util.List.copyOf(out);
        }, java.util.List.<io.github.anilken.wynnlabs.crafting.Ingredient>of());
        ingredients = read;
        return read;
    }

    private static io.github.anilken.wynnlabs.crafting.Ingredient ingredient(
            com.wynntils.models.ingredients.type.IngredientInfo info) {
        java.util.List<String> profs = new java.util.ArrayList<>();
        info.professions().forEach(p -> profs.add(p.name().toLowerCase(java.util.Locale.ROOT)));
        java.util.Map<String, io.github.anilken.wynnlabs.crafting.Ingredient.Stat> ids = new java.util.LinkedHashMap<>();
        info.variableStats().forEach(pair -> {
            var st = pair.a();
            var r = pair.b();
            String unit = st.getUnit().getDisplayName();
            ids.put(st.getApiName(), new io.github.anilken.wynnlabs.crafting.Ingredient.Stat(
                    Math.min(r.low(), r.high()), Math.max(r.low(), r.high()), st.getDisplayName(), unit));
        });
        int[] pos = new int[6];
        info.positionModifiers().forEach((p, v) -> {
            int at = switch (p) {
                case LEFT -> io.github.anilken.wynnlabs.crafting.Ingredient.LEFT;
                case RIGHT -> io.github.anilken.wynnlabs.crafting.Ingredient.RIGHT;
                case ABOVE -> io.github.anilken.wynnlabs.crafting.Ingredient.ABOVE;
                case UNDER -> io.github.anilken.wynnlabs.crafting.Ingredient.UNDER;
                case TOUCHING -> io.github.anilken.wynnlabs.crafting.Ingredient.TOUCHING;
                case NOT_TOUCHING -> io.github.anilken.wynnlabs.crafting.Ingredient.NOT_TOUCHING;
            };
            pos[at] = v;
        });
        int[] reqs = new int[5];
        info.skillRequirements().forEach(pair -> reqs[pair.a().ordinal()] = pair.b());
        return new io.github.anilken.wynnlabs.crafting.Ingredient(info.name(), info.tier(), info.level(),
                java.util.List.copyOf(profs), java.util.Collections.unmodifiableMap(ids), pos, info.durabilityModifier(),
                info.charges(), info.duration(), reqs);
    }

    public static int professionLevel(String profession) {
        return professionLevel(ProfessionType.valueOf(profession));
    }

    public static int professionLevel(ProfessionType p) {
        return guard(Feature.PROFESSIONS, () -> Models.Profession.getLevel(p), 0);
    }

    /** A profession's XP into its level and the XP the level needs ({0, 0} when unknown). */
    public static int[] professionXp(String profession) {
        return guard(Feature.PROFESSIONS, () -> {
            var xp = Models.Profession.getXP(ProfessionType.valueOf(profession));
            return new int[] {xp.current(), xp.max()};
        }, new int[2]);
    }

    // ServiceLocation.ServiceKind, MapLocation and MapDataService.getFeaturesForCategory(String):
    // 4.1.4 on. Before that the map has no stations to read: the list is empty and WAYPOINTS
    // stays on (the compass pins do not need them).
    private static final String SERVICE_KIND = "com.wynntils.services.mapdata.features.builtin.ServiceLocation$ServiceKind";
    private static final String MAP_LOCATION = "com.wynntils.services.mapdata.features.type.MapLocation";
    private static final String FEATURES_FOR_CATEGORY = "getFeaturesForCategory";

    /** Every crafting station of a profession ("WOODWORKING") on Wynntils' map, as {x, y, z}. */
    public static List<int[]> stations(String profession) {
        return guard(Feature.WAYPOINTS, () -> {
            Optional<Class<?>> kind = type(SERVICE_KIND);
            Optional<Class<?>> location = type(MAP_LOCATION);
            Optional<Method> features = method(com.wynntils.services.mapdata.MapDataService.class,
                    FEATURES_FOR_CATEGORY, String.class);
            if (kind.isEmpty() || location.isEmpty() || features.isEmpty()) {
                return List.<int[]>of();
            }
            Method valueOf = method(kind.get(), "valueOf", String.class).orElseThrow(() -> missing("ServiceKind.valueOf"));
            Method mapDataId = method(kind.get(), "getMapDataId").orElseThrow(() -> missing("ServiceKind.getMapDataId"));
            Method getLocation = method(location.get(), "getLocation").orElseThrow(() -> missing("MapLocation.getLocation"));
            String id = (String) call(mapDataId, call(valueOf, null, profession + "_STATION"));
            List<int[]> out = new ArrayList<>();
            ((Stream<?>) call(features.get(), com.wynntils.core.components.Services.MapData, "wynntils:service:" + id))
                    .forEach(f -> {
                        if (location.get().isInstance(f)) {
                            Location l = (Location) call(getLocation, f);
                            out.add(new int[] {l.x(), l.y(), l.z()});
                        }
                    });
            return out;
        }, List.of());
    }

    /**
     * The profession bombs Wynntils knows of and that still run (its bomb model, from the bomb
     * messages the client was shown): XP and Speed, with their world and time left. Empty without
     * Wynntils' bomb model.
     */
    public static List<io.github.anilken.wynnlabs.model.ProfBomb> professionBombs() {
        return guard(Feature.WORLD, () -> {
            String here = Models.WorldState.getCurrentWorldName();
            List<io.github.anilken.wynnlabs.model.ProfBomb> out = new ArrayList<>();
            for (com.wynntils.models.worlds.type.BombInfo b : Models.Bomb.getBombBells()) {
                var t = b.bomb();
                if (!b.isActive() || t != com.wynntils.models.worlds.type.BombType.PROFESSION_XP
                        && t != com.wynntils.models.worlds.type.BombType.PROFESSION_SPEED) {
                    continue;
                }
                out.add(new io.github.anilken.wynnlabs.model.ProfBomb(t == com.wynntils.models.worlds.type.BombType.PROFESSION_SPEED,
                        b.server(), b.getRemainingLong(), b.server() != null && b.server().equals(here)));
            }
            // this world's bomb from its boss bar, when no message named it (time left unknown: -1)
            for (var t : List.of(com.wynntils.models.worlds.type.BombType.PROFESSION_XP, com.wynntils.models.worlds.type.BombType.PROFESSION_SPEED)) {
                boolean speed = t == com.wynntils.models.worlds.type.BombType.PROFESSION_SPEED;
                if (Models.Bomb.isBombActive(t) && out.stream().noneMatch(x -> x.here() && x.speed() == speed)) {
                    out.add(new io.github.anilken.wynnlabs.model.ProfBomb(speed, here, -1, true));
                }
            }
            return out;
        }, List.of());
    }

    /** The gathering nodes the client sees, from their labels: the resource, the label's entity, where. */
    public static List<io.github.anilken.wynnlabs.model.GatherNode> gatherNodes() {
        return guard(Feature.PROFESSIONS, () -> {
            List<io.github.anilken.wynnlabs.model.GatherNode> out = new ArrayList<>();
            for (var n : Models.Profession.getNodesSet()) {
                var loc = n.getLocation();
                if (loc == null || n.getSourceMaterial() == null || n.getEntity() == null) {
                    continue;
                }
                out.add(new io.github.anilken.wynnlabs.model.GatherNode(n.getSourceMaterial().name(), n.getEntity().getId(),
                        loc.x(), loc.y(), loc.z()));
            }
            return out;
        }, List.of());
    }

    /** Every gathering tool the game has (Wynntils' tool list). */
    public static List<io.github.anilken.wynnlabs.model.GatherTool> gatherTools() {
        return guard(Feature.PROFESSIONS, () -> Models.Profession.getAllGatheringToolInfos()
                .map(i -> tool(i, -1, -1, null)).toList(), List.of());
    }

    /** The gathering tools in the player's bag, with what is left of them. */
    public static List<io.github.anilken.wynnlabs.model.GatherTool> heldTools() {
        return guard(Feature.PROFESSIONS, () -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            List<io.github.anilken.wynnlabs.model.GatherTool> out = new ArrayList<>();
            if (player == null) {
                return out;
            }
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack s = inv.getItem(i);
                if (s.isEmpty()) {
                    continue;
                }
                Models.Item.asWynnItem(s, com.wynntils.models.items.items.game.GatheringToolItem.class).ifPresent(t ->
                        out.add(tool(t.getToolInfo(), t.getDurability().current(), t.getDurability().max(), s.copy())));
            }
            return out;
        }, List.of());
    }

    private static io.github.anilken.wynnlabs.model.GatherTool tool(com.wynntils.models.profession.type.GatheringToolInfo i,
            int durability, int max, net.minecraft.world.item.ItemStack icon) {
        net.minecraft.world.item.ItemStack stack = icon != null ? icon
                : i.material() == null ? net.minecraft.world.item.ItemStack.EMPTY : i.material().itemStack();
        String prof = i.professionType() == null ? "" : i.professionType().name().toLowerCase(java.util.Locale.ROOT);
        return new io.github.anilken.wynnlabs.model.GatherTool(i.name(), prof, i.tier(), i.level(), i.gatheringSpeed(),
                durability, max < 0 ? i.durability() : max, stack);
    }
}
