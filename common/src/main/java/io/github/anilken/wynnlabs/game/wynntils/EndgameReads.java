package io.github.anilken.wynnlabs.game.wynntils;

import com.wynntils.core.components.Models;
import com.wynntils.models.containers.containers.MasteryTomesContainer;
import com.wynntils.models.containers.containers.RaidRewardPreviewContainer;
import com.wynntils.models.items.items.game.AspectItem;
import com.wynntils.models.items.items.game.TomeItem;
import com.wynntils.models.lootrun.type.LootrunningState;
import com.wynntils.models.lootrun.type.MissionType;
import com.wynntils.models.raid.RaidModel;
import com.wynntils.models.stats.type.SkillStatType;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.TomeMemory;
import io.github.anilken.wynnlabs.game.Wyn.Feature;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.model.AspectEntry;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.model.LootrunRun;
import io.github.anilken.wynnlabs.model.PoolAspect;
import io.github.anilken.wynnlabs.model.RaidDry;
import io.github.anilken.wynnlabs.model.SkillKind;
import io.github.anilken.wynnlabs.model.Tier;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.anilken.wynnlabs.game.wynntils.Bridge.call;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.guard;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.method;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.missing;
import static io.github.anilken.wynnlabs.game.wynntils.WorldReads.plain;

/**
 * Wynntils reads of raids, lootruns, aspects and tomes (the Endgame tab).
 */
public final class EndgameReads {

    private EndgameReads() {}

    // ---- raids, lootruns and aspects (the Endgame tab) ----
    //
    // RaidModel's four dry counters: every release since 4.1.0. Each is looked up by name, so a
    // release that drops one shows that counter as unknown and keeps the others; only when all
    // four are gone is raid tracking off. trackRaids (a public Config field): 4.2.12 on; before
    // that Wynntils always tracks raids.
    private static final String[] RAID_DRY = {"getRaidsWithoutMythicAspect", "getAspectPullsWithoutMythicAspect",
            "getRaidsWithoutMythicTome", "getRewardPullsWithoutMythicTome"};
    private static final String TRACK_RAIDS = "trackRaids";
    private static final String CONFIG_GET = "get";
    private static final int LOOTRUN_MISSIONS = 4;
    private static final int ASPECT_SLOTS = 5;
    private static volatile Optional<java.lang.reflect.Field> trackRaidsField;

    /** Wynntils' raid dry counters (whole account); {@link RaidDry#UNKNOWN} when unavailable. */
    public static RaidDry raidDry() {
        return guard(Feature.RAIDS, () -> {
            int[] v = new int[RAID_DRY.length];
            boolean any = false;
            for (int i = 0; i < RAID_DRY.length; i++) {
                Optional<Method> m = method(RaidModel.class, RAID_DRY[i]);
                v[i] = m.isPresent() ? ((Number) call(m.get(), Models.Raid)).intValue() : -1;
                any |= m.isPresent();
            }
            if (!any) {
                throw missing("RaidModel." + RAID_DRY[0]);
            }
            return new RaidDry(v[0], v[1], v[2], v[3]);
        }, RaidDry.UNKNOWN);
    }

    /** Wynntils' best time for a raid (full name or abbreviation), milliseconds; -1 when none or unknown. */
    public static long raidBestTime(String raid) {
        if (raid == null || raid.isBlank()) {
            return -1L;
        }
        return guard(Feature.RAIDS, () -> Models.Raid.getRaidBestTime(raid), -1L);
    }

    /** Whether the player is in a raid right now (Wynntils' current raid); false when unknown. */
    public static boolean inRaid() {
        return guard(Feature.RAIDS, () -> Models.Raid.getCurrentRaid() != null, false);
    }

    /**
     * Whether Wynntils' own raid tracking ("trackRaids") is on. True when this release has no
     * such switch, and when it cannot be read (the feature's self-test says whether raids work).
     */
    public static boolean raidTrackingOn() {
        return guard(Feature.RAIDS, () -> {
            Optional<java.lang.reflect.Field> field = trackRaidsField;
            if (field == null) {
                try {
                    field = Optional.of(RaidModel.class.getField(TRACK_RAIDS));
                } catch (NoSuchFieldException e) {
                    field = Optional.empty();
                }
                trackRaidsField = field;
            }
            if (field.isEmpty()) {
                return true;
            }
            Object config;
            try {
                config = field.get().get(Models.Raid);
            } catch (IllegalAccessException e) {
                throw new IncompatibleClassChangeError("RaidModel." + TRACK_RAIDS + ": " + e);
            }
            return config == null || !Boolean.FALSE.equals(call(config, CONFIG_GET));
        }, true);
    }

    /** Wynntils' lootrun state; UNKNOWN when it cannot be read. */
    public static LootrunRun.State lootrunState() {
        return guard(Feature.LOOTRUNS, () -> {
            LootrunningState s = Models.Lootrun.getState();
            if (s == null) {
                return LootrunRun.State.UNKNOWN;
            }
            // by name: a state a later release adds counts as running or idle, never fails
            return switch (s.name()) {
                case "NOT_RUNNING" -> LootrunRun.State.IDLE;
                case "CHOOSING_BEACON" -> LootrunRun.State.CHOOSING_BEACON;
                case "IN_TASK" -> LootrunRun.State.IN_TASK;
                default -> s.isRunning() ? LootrunRun.State.IN_TASK : LootrunRun.State.IDLE;
            };
        }, LootrunRun.State.UNKNOWN);
    }

    /**
     * The missions of the lootrun going on, by their English names ("Hoarder"), in order; the
     * slots Wynntils does not know yet are left out. Empty when none or unavailable.
     */
    public static List<String> lootrunMissions() {
        return guard(Feature.LOOTRUNS, () -> {
            String unknown = MissionType.UNKNOWN.getName();
            List<String> out = new ArrayList<>();
            for (int i = 0; i < LOOTRUN_MISSIONS; i++) {
                String m = plain(Models.Lootrun.getMissionStatus(i, false));
                if (!m.isEmpty() && !m.equalsIgnoreCase(unknown)) {
                    out.add(m);
                }
            }
            return out;
        }, List.of());
    }

    /** Wynntils' own lootrun pulls without a mythic (whole account); -1 when unavailable. */
    public static int lootrunDryPulls() {
        return guard(Feature.LOOTRUNS, () -> {
            // Wynntils 4.2.16 moved the count to Models.LootrunReward; older ones keep it on Models.Lootrun
            try {
                Object owner;
                try {
                    owner = Models.class.getField("LootrunReward").get(null);
                } catch (NoSuchFieldException e) {
                    owner = Models.Lootrun;
                }
                Object storage = owner.getClass().getField("dryPulls").get(owner);
                Object v = storage.getClass().getMethod("get").invoke(storage);
                return v instanceof Integer i ? i : -1;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }, -1);
    }

    /**
     * Every aspect of a class from Wynntils' downloaded data, in its order, with rarity, tier
     * count and thresholds; ability names are left empty (the aspect book adds them). Empty for
     * NONE, while the data is not downloaded, or when unavailable.
     */
    public static List<AspectEntry> classAspects(Klass cls) {
        if (cls == null || cls == Klass.NONE) {
            return List.of();
        }
        return guard(Feature.ASPECTS, () -> {
            String c = cls.name();
            List<AspectEntry> out = new ArrayList<>();
            Models.Aspect.getAllAspectInfos().forEach(a -> {
                if (a.classType() == null || !a.classType().name().equals(c)) {
                    return;
                }
                List<Integer> thresholds = new ArrayList<>();
                int tiers = a.effects() == null ? 0 : a.effects().size();
                if (a.effects() != null) {
                    for (var e : a.effects()) {
                        thresholds.add(e.a() == null ? 0 : e.a());
                    }
                }
                Tier rarity = a.gearTier() == null ? Tier.NORMAL : Tier.of(a.gearTier().name());
                out.add(new AspectEntry(plain(a.name()), cls, rarity, tiers, thresholds, List.of()));
            });
            return out;
        }, List.of());
    }

    /**
     * The tier of an aspect the player owns (1 on), as Wynntils last read it from the Aspects
     * menu or a raid reward chest; 0 when Wynntils lists it as not owned; -1 when unavailable.
     * Owned aspects are kept per Minecraft account, not per character.
     */
    public static int aspectTier(String name) {
        if (name == null || name.isBlank()) {
            return -1;
        }
        return guard(Feature.ASPECTS, () -> Models.Aspect.getAspectTierByName(name).orElse(0), -1);
    }

    /**
     * The tomes this character has equipped, by name: as Wynntils last read them with its own
     * menu query, else as the Mastery Tomes menu showed them when the player opened it; empty
     * when none is known or unavailable.
     */
    public static List<String> equippedTomes() {
        List<String> own = guard(Feature.CHARACTER, () -> {
            List<String> out = new ArrayList<>();
            var set = Models.Character.getCurrentTomeSet();
            if (set != null) {
                set.getAllTomes().stream().map(t -> plain(t.itemName())).filter(n -> !n.isEmpty()).forEach(out::add);
            }
            return List.copyOf(out);
        }, List.of());
        return GameState.current().running() ? TomeMemory.names(own) : own;
    }

    /**
     * The tomes in the Mastery Tomes menu with their skill points, while the player has it
     * open; null when another screen is open. Only reads the open menu.
     */
    public static List<TomeMemory.Seen> masteryTomes() {
        return guard(Feature.CHARACTER, () -> {
            if (!(Models.Container.getCurrentContainer() instanceof MasteryTomesContainer)
                    || !(net.minecraft.client.Minecraft.getInstance().screen
                            instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
                return null;
            }
            var player = net.minecraft.client.Minecraft.getInstance().player;
            List<TomeMemory.Seen> out = new ArrayList<>();
            for (var slot : screen.getMenu().slots) {
                ItemStack stack = slot.getItem();
                if ((player != null && slot.container == player.getInventory()) || stack.isEmpty()) {
                    continue;
                }
                Models.Item.asWynnItem(stack, TomeItem.class).ifPresent(t -> {
                    if (t.getItemInfo() == null || t.getItemInfo().type() == null) {
                        return;                 // not an equipped tome (Wynntils skips these too)
                    }
                    Map<SkillKind, Integer> skills = new EnumMap<>(SkillKind.class);
                    for (var id : t.getIdentifications()) {
                        if (id.statType() instanceof SkillStatType st) {
                            skills.merge(SkillKind.valueOf(st.getSkill().name()), id.value(), Integer::sum);
                        }
                    }
                    out.add(new TomeMemory.Seen(plain(t.getName()), skills));
                });
            }
            return out;
        }, null);
    }

    /**
     * The aspects this character has equipped (up to five), without formatting, as Wynntils
     * stores them: a name may carry a prefix, so match it with {@code endsWith}. Empty when
     * none is known or unavailable.
     */
    public static List<String> equippedAspects() {
        return guard(Feature.ASPECTS, () -> {
            List<String> out = new ArrayList<>();
            for (int i = 0; i < ASPECT_SLOTS; i++) {
                Models.Aspect.getEquippedAspect(i).map(WorldReads::plain).filter(s -> !s.isEmpty()).ifPresent(out::add);
            }
            return out;
        }, List.of());
    }

    /**
     * The aspects in the reward slots of the raid reward preview menu, when the player has it
     * open and its items have loaded; empty otherwise. Only reads the open menu.
     */
    public static List<PoolAspect> poolAspects() {
        return guard(Feature.ASPECTS, () -> {
            if (!(Models.Container.getCurrentContainer() instanceof RaidRewardPreviewContainer preview)
                    || !(net.minecraft.client.Minecraft.getInstance().screen
                            instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen)) {
                return List.<PoolAspect>of();
            }
            var slots = screen.getMenu().slots;
            List<PoolAspect> out = new ArrayList<>();
            for (int i : preview.getRewardBounds().getSlots()) {
                if (i < 0 || i >= slots.size()) {
                    continue;
                }
                ItemStack stack = slots.get(i).getItem();
                if (stack.isEmpty()) {
                    continue;
                }
                Models.Item.asWynnItem(stack, AspectItem.class).ifPresent(a -> out.add(new PoolAspect(plain(a.getName()),
                        a.getRequiredClass() == null ? Klass.NONE : WynntilsItems.klass(a.getRequiredClass().name()),
                        a.getGearTier() == null ? Tier.NORMAL : Tier.of(a.getGearTier().name()), Math.max(1, a.getTier()))));
            }
            return out;
        }, List.of());
    }
}
