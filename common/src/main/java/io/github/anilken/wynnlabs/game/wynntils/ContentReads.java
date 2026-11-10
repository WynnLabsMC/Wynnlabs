package io.github.anilken.wynnlabs.game.wynntils;

import com.wynntils.core.components.Models;
import com.wynntils.models.activities.quests.QuestInfo;
import com.wynntils.models.activities.type.ActivitySortOrder;
import com.wynntils.models.activities.type.ActivityType;
import com.wynntils.models.activities.type.DiscoveryType;
import com.wynntils.models.objectives.WynnObjective;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn.BookPage;
import io.github.anilken.wynnlabs.game.Wyn.BookSlot;
import io.github.anilken.wynnlabs.game.Wyn.ContentLine;
import io.github.anilken.wynnlabs.game.Wyn.Feature;
import io.github.anilken.wynnlabs.game.Wyn.TypedEntry;
import io.github.anilken.wynnlabs.game.WynntilsItems;
import io.github.anilken.wynnlabs.model.Goal;
import io.github.anilken.wynnlabs.model.Quest;
import io.github.anilken.wynnlabs.model.QuestKind;
import io.github.anilken.wynnlabs.quests.QuestBook;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static io.github.anilken.wynnlabs.game.wynntils.Bridge.guard;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.up;
import static io.github.anilken.wynnlabs.game.wynntils.CharacterReads.hasCharacter;
import static io.github.anilken.wynnlabs.game.wynntils.WorldReads.plain;

/**
 * Wynntils reads of tracking, quests, the content book, objectives and the party.
 */
public final class ContentReads {

    private ContentReads() {}

    // ---- quests and activities ----

    public static boolean tracking() {
        return guard(Feature.QUESTS, () -> Models.Activity.isTracking(), false);
    }

    public static String trackedName() {
        return guard(Feature.QUESTS, () -> Models.Activity.getTrackedName(), "");
    }

    /** The tracked task as plain text ("" when none). */
    public static String trackedTask() {
        return guard(Feature.QUESTS, () -> {
            var t = Models.Activity.getTrackedTask();
            return t == null ? "" : t.getStringWithoutFormatting();
        }, "");
    }

    public static QuestKind trackedType() {
        return guard(Feature.QUESTS, () -> {
            ActivityType t = Models.Activity.getTrackedType();
            return t == null ? null : QuestKind.valueOf(t.name());
        }, null);
    }

    /** Tracks a content book entry. A kind Wynntils does not have throws here, outside the guard. */
    public static boolean startTracking(String name, QuestKind kind) {
        ActivityType type = ActivityType.valueOf(kind.name());
        return guard(Feature.QUESTS, () -> {
            Models.Activity.startTracking(name, type);
            return true;
        }, false);
    }

    /**
     * The secret discoveries Wynntils lists as found for this character, by name. Empty until its
     * content book scan read them (and whenever the call fails).
     */
    public static java.util.Set<String> completedDiscoveries() {
        return guard(Feature.QUESTS, () -> {
            java.util.Set<String> out = new java.util.HashSet<>();
            Models.Discovery.getAllDiscoveriesForType(DiscoveryType.SECRET).forEach(d -> {
                if (d.discovered()) {
                    out.add(d.name());
                }
            });
            return out;
        }, java.util.Set.of());
    }

    /** Quests as Wynntils last read them from the content book. */
    public static List<Quest> quests() {
        return guard(Feature.QUESTS, () -> {
            List<Quest> out = new ArrayList<>();
            for (QuestInfo q : Models.Quest.getQuestsRaw()) {
                out.add(quest(q));
            }
            return out;
        }, List.of());
    }

    /** Quests and mini-quests as Wynntils last read them from the content book, by level. */
    public static List<Quest> questsAndMiniQuests() {
        return guard(Feature.QUESTS, () -> {
            List<Quest> out = new ArrayList<>();
            for (QuestInfo q : Models.Quest.getSortedQuests(ActivitySortOrder.LEVEL, true, true)) {
                out.add(quest(q));
            }
            return out;
        }, List.of());
    }

    /**
     * Whether the quests tab's "read quests with Wynntils" can run: the quest calls work and a
     * character is selected. The J menu preview (no Wynntils) draws it as in a game.
     */
    public static boolean canRescanQuests() {
        if (!up(Feature.QUESTS)) {
            return false;
        }
        if (!GameState.current().running()) {
            return true;
        }
        return hasCharacter();
    }

    /**
     * Wynntils' own quest book refresh: it opens the content book for a moment, reads every
     * quest and mini-quest page, then posts the new lists (taken in by {@link QuestBook#fromWynntils}).
     * False when unavailable. Same signature in every supported release (4.1.0 to 4.2.14).
     * <p>
     * The one place the mod asks Wynntils to open a menu to read it, and only on the player's
     * press of the quests tab's button or "Yenile" chip ({@link QuestBook#askWynntils}). Never call
     * it automatically, on a timer or when a tab opens. (Tracking a quest, {@link #startTracking},
     * also has Wynntils open the book, likewise only on the player's click.)
     */
    public static boolean rescanQuests() {
        return guard(Feature.QUESTS, () -> {
            Models.Quest.rescanQuestBook(true, true);
            return true;
        }, false);
    }

    /**
     * Wynntils reads one kind of the content book ({@code kind}: an {@code ActivityType} name such as
     * "CAVE" or "DUNGEON") and hands the lines to {@code done} on the game thread. False when the kind
     * is unknown to this Wynntils or the call fails. Like {@link #rescanQuests}, only ever on the
     * player's press of the quests tab's refresh: never automatically, on a timer or in a loop.
     */
    public static boolean scanContent(String kind, java.util.function.Consumer<List<ContentLine>> done) {
        ActivityType type = activityType(kind);
        if (type == null) {
            return false;
        }
        return guard(Feature.QUESTS, () -> {
            Models.Activity.scanContentBook(type, (list, progress) -> {
                try {
                    List<ContentLine> out = new ArrayList<>();
                    for (com.wynntils.models.activities.type.ActivityInfo a : list) {
                        int lv = a.requirements() == null || a.requirements().level() == null ? 0 : a.requirements().level().a();
                        String d = a.description().map(t -> t.getStringWithoutFormatting()).orElse("");
                        out.add(new ContentLine(a.name(), a.status() == null ? "UNKNOWN" : a.status().name(), lv, d));
                    }
                    done.accept(out);
                } catch (Throwable t) {
                    System.out.println("[WynnLabs] content book lines unreadable: " + t);
                }
            });
            return true;
        }, false);
    }

    /**
     * The content book's world events, read by Wynntils' hidden content book read: name, status,
     * and as description the entry's region line before its text (the read keeps no countdown).
     * The one read that runs on its own: every few minutes while the main map is open, behind
     * the world events setting (approved for this feature only).
     */
    public static boolean scanWorldEvents(java.util.function.Consumer<List<ContentLine>> done) {
        return guard(Feature.QUESTS, () -> {
            Models.Activity.scanContentBook(ActivityType.WORLD_EVENT, (list, progress) -> {
                try {
                    List<ContentLine> out = new ArrayList<>();
                    for (com.wynntils.models.activities.type.ActivityInfo a : list) {
                        String info = a.specialInfo().orElse("");
                        String d = a.description().map(t -> t.getStringWithoutFormatting()).orElse("");
                        out.add(new ContentLine(a.name(), a.status() == null ? "UNKNOWN" : a.status().name(), 0,
                                info.isEmpty() ? d : info + "\n" + d));
                    }
                    done.accept(out);
                } catch (Throwable t) {
                    System.out.println("[WynnLabs] world events in the content book unreadable: " + t);
                }
            });
            return true;
        }, false);
    }

    /**
     * Puts the content book's filter back on {@code shown} (the name on its filter button) after
     * the world event read moved it: Wynntils' hidden read of that kind's first page, which sets
     * the filter on its way. False when Wynntils does not know the name or the call fails. Only
     * ever after {@link #scanWorldEvents}, once per read at most.
     */
    public static boolean showBookFilter(String shown) {
        ActivityType type = ActivityType.from(shown);
        if (type == null) {
            return false;
        }
        return guard(Feature.QUESTS, () -> {
            java.util.function.BiConsumer<List<com.wynntils.models.activities.type.ActivityInfo>,
                    List<com.wynntils.core.text.StyledText>> none = (list, progress) -> {};
            try {
                // the first page only and no message; the public call reads every page of the kind
                var field = Models.Activity.getClass().getDeclaredField("CONTAINER_QUERIES");
                field.setAccessible(true);
                Object queries = field.get(null);
                var query = queries.getClass().getDeclaredMethod("queryContentBook", ActivityType.class,
                        java.util.function.BiConsumer.class, boolean.class, boolean.class);
                query.setAccessible(true);
                query.invoke(queries, type, none, false, true);
            } catch (ReflectiveOperationException e) {
                Models.Activity.scanContentBook(type, none);
            }
            return true;
        }, false);
    }

    /**
     * What Wynntils itself keeps of one kind for this character (caves and the three discovery
     * kinds; empty for the rest and until Wynntils read them).
     */
    public static List<ContentLine> knownContent(String kind) {
        return guard(Feature.QUESTS, () -> {
            List<ContentLine> out = new ArrayList<>();
            switch (kind) {
                case "CAVE" -> {
                    for (var c : Models.Cave.getSortedCaves(ActivitySortOrder.LEVEL)) {
                        out.add(new ContentLine(c.name(), c.status() == null ? "UNKNOWN" : c.status().name(), c.recommendedLevel(),
                                c.description() == null ? "" : c.description()));
                    }
                }
                case "SECRET_DISCOVERY", "WORLD_DISCOVERY", "TERRITORIAL_DISCOVERY" -> {
                    DiscoveryType t = kind.startsWith("SECRET") ? DiscoveryType.SECRET : kind.startsWith("WORLD") ? DiscoveryType.WORLD
                            : DiscoveryType.TERRITORY;
                    Models.Discovery.getAllDiscoveriesForType(t).forEach(d -> out.add(new ContentLine(d.name(),
                            d.discovered() ? "COMPLETED" : "AVAILABLE", d.minLevel(), d.description() == null ? "" : d.description())));
                }
                default -> {
                }
            }
            return out;
        }, List.of());
    }

    /**
     * The Content Book the player has open, as shown: the filter button and this page's entries
     * (an entry's name as Wynntils reads it when it can, else the plain item name). Null when
     * another screen, or none, is open. Only reads the open menu: never clicks or turns a page.
     */
    public static BookPage contentBookPage() {
        return guard(Feature.QUESTS, () -> {
            // Wynntils shows the book in its own screen, not a container screen: the items still
            // sit in the player's open container menu
            if (!(Models.Container.getCurrentContainer() instanceof com.wynntils.models.containers.containers.ContentBookContainer book)) {
                return null;
            }
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null || player.containerMenu == null || player.containerMenu == player.inventoryMenu) {
                return null;
            }
            java.util.Set<Integer> page = new java.util.HashSet<>(book.getBounds().getSlots());
            BookSlot filter = null;
            List<BookSlot> entries = new ArrayList<>();
            List<TypedEntry> typed = new ArrayList<>();
            var slots = player.containerMenu.slots;
            for (int i = 0; i < slots.size(); i++) {
                var slot = slots.get(i);
                ItemStack stack = slot.getItem();
                if ((player != null && slot.container == player.getInventory()) || stack.isEmpty()) {
                    continue;
                }
                List<String> lore = new ArrayList<>();
                for (var line : com.wynntils.utils.mc.LoreUtils.getLore(stack)) {
                    lore.add(line.getString());
                }
                String name = plain(stack.getHoverName().getString());
                if (page.contains(i)) {
                    String read = Models.Item.asWynnItem(stack, com.wynntils.models.items.items.gui.ActivityItem.class)
                            .map(a -> a.getActivityInfo() == null ? null : a.getActivityInfo().name()).orElse(null);
                    entries.add(new BookSlot(read == null || read.isBlank() ? name : read, lore));
                    Models.Item.asWynnItem(stack, com.wynntils.models.items.items.gui.ActivityItem.class).ifPresent(a -> {
                        var info = a.getActivityInfo();
                        if (info != null && info.type() != null && info.name() != null) {
                            int lv = info.requirements() == null || info.requirements().level() == null ? 0 : info.requirements().level().a();
                            typed.add(new TypedEntry(info.type().name(), info.name(), info.status() == null ? "UNKNOWN" : info.status().name(),
                                    lv, "", lore));
                        }
                    });
                } else if (filter == null && "Filter".equals(name)) {
                    filter = new BookSlot(name, lore);
                }
            }
            return new BookPage(filter, entries, typed);
        }, null);
    }

    /** Whether this Wynntils knows the content book kind (Time Trial is missing in 4.2.x). */
    public static boolean knowsKind(String kind) {
        return activityType(kind) != null;
    }

    private static ActivityType activityType(String kind) {
        try {
            return ActivityType.valueOf(kind);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Tracks a content book entry of any kind Wynntils knows; on the player's click only. */
    public static boolean startTracking(String name, String kind) {
        ActivityType type = activityType(kind);
        if (type == null) {
            return false;
        }
        return guard(Feature.QUESTS, () -> {
            Models.Activity.startTracking(name, type);
            return true;
        }, false);
    }

    private static Quest quest(QuestInfo q) {
        var req = q.additionalRequirements();
        Quest.Requirements r = null;
        if (req != null) {
            Integer lv = req.level() == null ? null : req.level().a();
            Boolean met = req.level() == null ? null : req.level().b();
            List<Quest.Profession> profs = new ArrayList<>();
            for (var p : req.professionLevels()) {
                profs.add(new Quest.Profession(p.a().a().name(), p.a().b(), p.b()));
            }
            List<Quest.Need> qs = new ArrayList<>();
            for (var p : req.quests()) {
                qs.add(new Quest.Need(p.a(), p.b()));
            }
            r = new Quest.Requirements(lv, met, profs, qs);
        }
        Map<String, List<String>> rewards = null;
        if (q.rewards() != null) {
            Map<String, List<String>> m = new LinkedHashMap<>();
            q.rewards().forEach((type, list) -> {
                List<String> l = new ArrayList<>();
                list.forEach(t -> l.add(t.getStringWithoutFormatting()));
                m.put(type.name(), l);
            });
            rewards = m;
        }
        String next = q.nextTask() != null && !q.nextTask().isBlank() ? q.nextTask().getStringWithoutFormatting() : null;
        return new Quest(q.name(), q.isMiniQuest(), q.status() == null ? null : q.status().name(), q.level(),
                q.difficulty() == null ? null : q.difficulty().name(), q.length() == null ? null : q.length().name(),
                next, r, rewards);
    }

    public static List<Goal> objectives() {
        return guard(Feature.OBJECTIVES, () -> {
            List<Goal> out = new ArrayList<>();
            for (WynnObjective o : Models.Objectives.getPersonalObjectives()) {
                out.add(new Goal(o.getGoal(), WynntilsItems.capped(o.getScore())));
            }
            return out;
        }, List.of());
    }

    /** The names in your party (Wynntils' party list), you included; empty when not in one. */
    public static List<String> partyMembers() {
        return guard(Feature.CHARACTER, () -> Models.Party.isInParty() ? List.copyOf(Models.Party.getPartyMembers()) : List.<String>of(),
                List.of());
    }
}
