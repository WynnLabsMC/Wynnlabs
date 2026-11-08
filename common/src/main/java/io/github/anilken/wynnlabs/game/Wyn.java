package io.github.anilken.wynnlabs.game;

import com.wynntils.models.gear.type.GearInfo;
import com.wynntils.models.items.WynnItem;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.models.marker.UserWaypointMarkerProvider;
import com.wynntils.models.wynnitem.type.ItemObtainInfo;
import io.github.anilken.wynnlabs.core.L;
import io.github.anilken.wynnlabs.game.wynntils.AbilityReads;
import io.github.anilken.wynnlabs.game.wynntils.Bridge;
import io.github.anilken.wynnlabs.game.wynntils.CharacterReads;
import io.github.anilken.wynnlabs.game.wynntils.ContentReads;
import io.github.anilken.wynnlabs.game.wynntils.EndgameReads;
import io.github.anilken.wynnlabs.game.wynntils.ItemReads;
import io.github.anilken.wynnlabs.game.wynntils.ProfessionReads;
import io.github.anilken.wynnlabs.game.wynntils.WorldReads;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.AspectEntry;
import io.github.anilken.wynnlabs.model.Capped;
import io.github.anilken.wynnlabs.model.Goal;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.LootrunRun;
import io.github.anilken.wynnlabs.model.MenuKind;
import io.github.anilken.wynnlabs.model.Pin;
import io.github.anilken.wynnlabs.model.PoolAspect;
import io.github.anilken.wynnlabs.model.Quest;
import io.github.anilken.wynnlabs.model.QuestKind;
import io.github.anilken.wynnlabs.model.RaidDry;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.ui.TreeConnectors;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Every call Wynn Labs makes into Wynntils goes through here. Wynntils' internals are not a public
 * API: when an update renames or removes something, the call fails with a LinkageError at run
 * time. Each method below runs its call through {@link #guard}, which returns a safe default
 * instead, marks the feature that depends on it as down, and logs the first failure, so a
 * Wynntils change costs one feature and never crashes the game. What it hands back is in the
 * mod's own types (the model package); items are converted by {@link WynntilsItems}. The calls
 * themselves live in one class per area under {@code game/wynntils} ({@link Bridge} holds the
 * guard); this class hands them out. Only these files and the event hooks in
 * {@link WynntilsEvents} see Wynntils' types (the build checks).
 */
public final class Wyn {

    /** What a Wynntils call is for; a down feature is shown to the player. */
    public enum Feature {
        CHARACTER, ITEMS, GEAR_DB, QUESTS, ABILITIES, SKILLS, WAYPOINTS, EVENTS, OBJECTIVES,
        PROFESSIONS, CONTAINERS, STATS, WORLD, NOTIFY, RAIDS, LOOTRUNS, ASPECTS
    }

    private Wyn() {}

    // ---- the guard, the features marked down and the self-test: Bridge ----

    static <T> T guard(Feature f, Supplier<T> call, T fallback) {
        return Bridge.guard(f, call, fallback);
    }

    /** The screen to open instead: the mod's guild map in place of Wynntils' own (setting guildMap). */
    public static net.minecraft.client.gui.screens.Screen guildMap(net.minecraft.client.gui.screens.Screen s) {
        if (s == null || !s.getClass().getName().endsWith(".GuildMapScreen") || !Bridge.up(Feature.WORLD)) {
            return s;
        }
        return io.github.anilken.wynnlabs.game.wynntils.GuildMapReads.swap(s);
    }

    public static boolean up(Feature f) {
        return Bridge.up(f);
    }

    public static Map<Feature, String> down() {
        return Bridge.down();
    }

    public static void clearDown() {
        Bridge.clearDown();
    }

    public static void markDown(Feature f, String why) {
        Bridge.markDown(f, why);
    }

    public static String version() {
        return Bridge.version();
    }

    static Optional<Class<?>> type(String name) {
        return Bridge.type(name);
    }

    static Optional<Method> method(Class<?> owner, String name, Class<?>... params) {
        return Bridge.method(owner, name, params);
    }

    static Object call(Method m, Object target, Object... args) {
        return Bridge.call(m, target, args);
    }

    static Object call(Object target, String name) {
        return Bridge.call(target, name);
    }

    static NoSuchMethodError missing(String what) {
        return Bridge.missing(what);
    }

    public static void selfTest() {
        Bridge.selfTest();
    }

    // ---- telling the player ----

    private static final java.util.Set<Feature> ANNOUNCED = java.util.EnumSet.noneOf(Feature.class);
    private static int tick;

    /** Every few seconds: one chat line per session for features that went down. */
    public static void onTick(net.minecraft.client.Minecraft mc) {
        if (++tick % 100 != 0 || mc.player == null) {
            return;
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        for (Feature f : down().keySet()) {
            if (ANNOUNCED.add(f)) {
                names.add(name(f));
            }
        }
        if (!names.isEmpty()) {
            io.github.anilken.wynnlabs.core.Chat.say(L.c("wynnlabs.wyn.down", String.join(", ", names), version())
                    .withStyle(net.minecraft.ChatFormatting.YELLOW));
        }
    }

    /** A feature's name in the player's language. */
    public static String name(Feature f) {
        return switch (f) {
            case CHARACTER -> L.t("wynnlabs.wyn.f_character");
            case ITEMS -> L.t("wynnlabs.wyn.f_items");
            case GEAR_DB -> L.t("wynnlabs.wyn.f_gear_db");
            case QUESTS -> L.t("wynnlabs.wyn.f_quests");
            case ABILITIES -> L.t("wynnlabs.wyn.f_abilities");
            case SKILLS -> L.t("wynnlabs.wyn.f_skills");
            case WAYPOINTS -> L.t("wynnlabs.wyn.f_waypoints");
            case EVENTS -> L.t("wynnlabs.wyn.f_events");
            case OBJECTIVES -> L.t("wynnlabs.wyn.f_objectives");
            case PROFESSIONS -> L.t("wynnlabs.wyn.f_professions");
            case CONTAINERS -> L.t("wynnlabs.wyn.f_containers");
            case STATS -> L.t("wynnlabs.wyn.f_stats");
            case WORLD -> L.t("wynnlabs.wyn.f_world");
            case NOTIFY -> L.t("wynnlabs.wyn.f_notify");
            case RAIDS -> L.t("wynnlabs.wyn.f_raids");
            case LOOTRUNS -> L.t("wynnlabs.wyn.f_lootruns");
            case ASPECTS -> L.t("wynnlabs.wyn.f_aspects");
        };
    }

    // ---- character and skill points: CharacterReads ----

    public static int level() {
        return CharacterReads.level();
    }

    public static Capped xp() {
        return CharacterReads.xp();
    }

    public static Klass classType() {
        return CharacterReads.classType();
    }

    public static boolean hasCharacter() {
        return CharacterReads.hasCharacter();
    }

    public static String characterId() {
        return CharacterReads.characterId();
    }

    public static String supporterRank() {
        return CharacterReads.supporterRank();
    }

    public static boolean reskinned() {
        return CharacterReads.reskinned();
    }

    public static Optional<Capped> health() {
        return CharacterReads.health();
    }

    public static Optional<Capped> mana() {
        return CharacterReads.mana();
    }

    public static int bloodPool() {
        return CharacterReads.bloodPool();
    }

    public static long focusedMobMaxHealth() {
        return CharacterReads.focusedMobMaxHealth();
    }

    public static int emeralds() {
        return CharacterReads.emeralds();
    }

    public static int assignedPoints(SkillKind s) {
        return CharacterReads.assignedPoints(s);
    }

    public static int gearPoints(SkillKind s) {
        return CharacterReads.gearPoints(s);
    }

    public static int tomePoints(SkillKind s) {
        return CharacterReads.tomePoints(s);
    }

    public static int setBonusPoints(SkillKind s) {
        return CharacterReads.setBonusPoints(s);
    }

    static int totalPoints(SkillKind s) {
        return CharacterReads.totalPoints(s);
    }

    // ---- items, gear and the open menu: ItemReads (the rest of the mod reads items through WynntilsItems) ----

    static Optional<WynnItem> item(ItemStack stack) {
        return ItemReads.item(stack);
    }

    static <T extends WynnItem> Optional<T> asItem(ItemStack stack, Class<T> type) {
        return ItemReads.asItem(stack, type);
    }

    public static boolean annotate(ItemStack stack) {
        return ItemReads.annotate(stack);
    }

    static List<ItemStack> accessories(Player player) {
        return ItemReads.accessories(player);
    }

    static Stream<GearInfo> allGear() {
        return ItemReads.allGear();
    }

    static GearInfo gearByName(String name) {
        return ItemReads.gearByName(name);
    }

    static Integer ingredientTier(String name) {
        return ItemReads.ingredientTier(name);
    }

    public static ItemStack gearPicture(String name) {
        return ItemReads.gearPicture(name);
    }

    static List<GearInfo> possibleGears(GearBoxItem box) {
        return ItemReads.possibleGears(box);
    }

    static List<ItemObtainInfo> obtainInfo(GearInfo info) {
        return ItemReads.obtainInfo(info);
    }

    public static int marketListingPrice(ItemStack stack) {
        return ItemReads.marketListingPrice(stack);
    }

    public static MenuKind container() {
        return ItemReads.container();
    }

    // ---- tracking, quests and the content book: ContentReads ----

    public static boolean tracking() {
        return ContentReads.tracking();
    }

    public static String trackedName() {
        return ContentReads.trackedName();
    }

    public static String trackedTask() {
        return ContentReads.trackedTask();
    }

    public static QuestKind trackedType() {
        return ContentReads.trackedType();
    }

    public static boolean startTracking(String name, QuestKind kind) {
        return ContentReads.startTracking(name, kind);
    }

    static java.util.Set<String> completedDiscoveries() {
        return ContentReads.completedDiscoveries();
    }

    public static List<Quest> quests() {
        return ContentReads.quests();
    }

    public static List<Quest> questsAndMiniQuests() {
        return ContentReads.questsAndMiniQuests();
    }

    public static boolean canRescanQuests() {
        return ContentReads.canRescanQuests();
    }

    public static boolean rescanQuests() {
        return ContentReads.rescanQuests();
    }

    public static boolean scanContent(String kind, java.util.function.Consumer<List<ContentLine>> done) {
        return ContentReads.scanContent(kind, done);
    }

    public static boolean scanWorldEvents(java.util.function.Consumer<List<ContentLine>> done) {
        return ContentReads.scanWorldEvents(done);
    }

    public static List<ContentLine> knownContent(String kind) {
        return ContentReads.knownContent(kind);
    }

    public static BookPage contentBookPage() {
        return ContentReads.contentBookPage();
    }

    public static boolean knowsKind(String kind) {
        return ContentReads.knowsKind(kind);
    }

    public static boolean startTracking(String name, String kind) {
        return ContentReads.startTracking(name, kind);
    }

    public static List<Goal> objectives() {
        return ContentReads.objectives();
    }

    /**
     * One content book line as Wynntils read it: the name, AVAILABLE / STARTED / UNAVAILABLE /
     * COMPLETED, the level it asks for (0 when none) and its description, plain text.
     */
    public record ContentLine(String name, String status, int level, String description) {}

    /** One item of the open Content Book: its name and its lore lines with their colour codes. */
    public record BookSlot(String name, List<String> lore) {}

    /** The open Content Book: its filter button (null when not found) and the entries on the page shown. */
    public record BookPage(BookSlot filter, List<BookSlot> entries, List<TypedEntry> typed) {}

    /** One entry as Wynntils read it: its kind (an ActivityType name), name, status name and level, with its lore. */
    public record TypedEntry(String type, String name, String status, int level, String description, List<String> lore) {}

    // ---- ability tree: AbilityReads ----

    public static List<String> unlockedAbilities() {
        return AbilityReads.unlockedAbilities();
    }

    public static List<AbilityNode> abilityTree(Klass cls) {
        return AbilityReads.abilityTree(cls);
    }

    public static TreeConnectors.Piece connector(ItemStack stack) {
        return AbilityReads.connector(stack);
    }

    public static ItemStack connectorIcon(boolean[] arms, boolean[] lit) {
        return AbilityReads.connectorIcon(arms, lit);
    }

    public static ItemStack nodeIcon(String type, String state) {
        return AbilityReads.nodeIcon(type, state);
    }

    public static int abilityPointsAt(int level) {
        return AbilityReads.abilityPointsAt(level);
    }

    // ---- professions: ProfessionReads ----

    public static java.util.List<io.github.anilken.wynnlabs.crafting.Ingredient> craftIngredients() {
        return ProfessionReads.craftIngredients();
    }

    public static int professionLevel(String profession) {
        return ProfessionReads.professionLevel(profession);
    }

    public static int[] professionXp(String profession) {
        return ProfessionReads.professionXp(profession);
    }

    public static List<int[]> stations(String profession) {
        return ProfessionReads.stations(profession);
    }

    public static List<io.github.anilken.wynnlabs.model.ProfBomb> professionBombs() {
        return ProfessionReads.professionBombs();
    }

    public static List<io.github.anilken.wynnlabs.model.GatherNode> gatherNodes() {
        return ProfessionReads.gatherNodes();
    }

    public static boolean holdingGatherTool() {
        return ProfessionReads.holdingGatherTool();
    }

    public static List<io.github.anilken.wynnlabs.model.GatherTool> gatherTools() {
        return ProfessionReads.gatherTools();
    }

    public static List<io.github.anilken.wynnlabs.model.GatherTool> heldTools() {
        return ProfessionReads.heldTools();
    }

    // ---- raids, lootruns, aspects and tomes: EndgameReads ----

    public static RaidDry raidDry() {
        return EndgameReads.raidDry();
    }

    public static boolean inRaid() {
        return EndgameReads.inRaid();
    }

    public static boolean raidTrackingOn() {
        return EndgameReads.raidTrackingOn();
    }

    public static LootrunRun.State lootrunState() {
        return EndgameReads.lootrunState();
    }

    public static List<String> lootrunMissions() {
        return EndgameReads.lootrunMissions();
    }

    public static int lootrunDryPulls() {
        return EndgameReads.lootrunDryPulls();
    }

    public static List<AspectEntry> classAspects(Klass cls) {
        return EndgameReads.classAspects(cls);
    }

    public static int aspectTier(String name) {
        return EndgameReads.aspectTier(name);
    }

    public static List<String> equippedTomes() {
        return EndgameReads.equippedTomes();
    }

    static List<TomeMemory.Seen> masteryTomes() {
        return EndgameReads.masteryTomes();
    }

    public static List<String> equippedAspects() {
        return EndgameReads.equippedAspects();
    }

    public static List<PoolAspect> poolAspects() {
        return EndgameReads.poolAspects();
    }

    // ---- territories, waypoints, toasts and the map: WorldReads ----

    public static String territoryAt(Position pos) {
        return WorldReads.territoryAt(pos);
    }

    public static Pins waypoints() {
        return WorldReads.waypoints();
    }

    public static boolean toast(Component c) {
        return WorldReads.toast(c);
    }

    public static boolean hasMap(float x0, float z0, float x1, float z1) {
        return WorldReads.hasMap(x0, z0, x1, z1);
    }

    public static boolean drawMap(net.minecraft.client.gui.GuiGraphics g, float x0, float z0, float x1, float z1,
            float sx, float sy, float scale, int tint) {
        return WorldReads.drawMap(g, x0, z0, x1, z1, sx, sy, scale, tint);
    }

    public static float[] mapCoverage() {
        return WorldReads.mapCoverage();
    }

    public static List<MapLabel> mapLabels(float x0, float z0, float x1, float z1) {
        return WorldReads.mapLabels(x0, z0, x1, z1);
    }

    public static boolean openMap(float x, float z) {
        return WorldReads.openMap(x, z);
    }

    public static boolean showMarks(List<io.github.anilken.wynnlabs.search.MapMark> marks) {
        return WorldReads.showMarks(marks);
    }

    public static PoiIcon poiIcon(String key) {
        return WorldReads.poiIcon(key);
    }

    public static String hoveredGuideItem(net.minecraft.client.gui.screens.Screen screen) {
        return WorldReads.hoveredGuideItem(screen);
    }

    /** The scale Wynntils' map draws one of its icons at: smaller for loot chests and caves. */
    public static float chestIconScale(Object poi, float scale) {
        return WorldReads.chestIconScale(poi, scale);
    }

    public static void caveMapSync() {
        WorldReads.caveMapSync();
    }

    /** True while Wynntils' main map is the open screen. */
    public static boolean mainMapOpen() {
        return WorldReads.mainMapOpen();
    }

    /** World events on Wynntils' main map: reads and asks what is due, then hands the map the events to draw. */
    public static void worldEventSync() {
        WorldReads.worldEventSync();
    }

    /** The waypoint list: its pins, and adding or removing one by position. */
    public static final class Pins {
        private final UserWaypointMarkerProvider provider;

        public Pins(UserWaypointMarkerProvider provider) {
            this.provider = provider;
        }

        public List<Pin> all() {
            return WorldReads.pins(provider);
        }

        public void remove(Pin p) {
            WorldReads.removePin(provider, p);
        }

        public void add(int x, int y, int z, String label) {
            WorldReads.addPin(provider, x, y, z, label);
        }
    }

    public static boolean listen(Object listener) {
        return listen(Feature.EVENTS, listener);
    }

    /** Registers a listener on Wynntils' bus; when its events are gone, {@code f} is off. */
    public static boolean listen(Feature f, Object listener) {
        return guard(f, () -> {
            com.wynntils.core.WynntilsMod.registerEventListener(listener);
            return true;
        }, false);
    }

    /** A place name on Wynntils' map: its name, where, and its layer (0 province, 1 city, 2 town or place). */
    public record MapLabel(String name, int x, int z, int layer) {}

    /** A Wynntils map icon: its texture and size, to draw as it is (nothing copied). */
    public record PoiIcon(net.minecraft.resources.Identifier id, int w, int h) {}
}
