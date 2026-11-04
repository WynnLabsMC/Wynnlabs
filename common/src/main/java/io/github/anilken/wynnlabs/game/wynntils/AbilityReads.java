package io.github.anilken.wynnlabs.game.wynntils;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.wynntils.core.components.Models;
import com.wynntils.models.abilitytree.AbilityTreeModel;
import com.wynntils.models.abilitytree.type.AbilityTreeConnectionType;
import com.wynntils.models.abilitytree.type.AbilityTreeInfo;
import com.wynntils.models.abilitytree.type.AbilityTreeNodeState;
import com.wynntils.models.abilitytree.type.AbilityTreeSkillNode;
import com.wynntils.models.character.type.ClassType;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.LiveGameState;
import io.github.anilken.wynnlabs.game.Wyn.Feature;
import io.github.anilken.wynnlabs.model.AbilityNode;
import io.github.anilken.wynnlabs.model.Klass;
import io.github.anilken.wynnlabs.ui.TreeConnectors;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.anilken.wynnlabs.game.wynntils.Bridge.call;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.guard;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.method;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.missing;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.type;

/**
 * Wynntils reads of the ability tree: nodes, connectors, icons and ability points.
 */
public final class AbilityReads {

    private AbilityReads() {}

    // AbilityTreeModel.getUnlockedAbilities(): 4.2.5 on. getNodeState(AbilityTreeSkillNode): until
    // 4.2.3, the state of a node in the tree Wynntils last parsed from the menu.
    private static final String GET_UNLOCKED_ABILITIES = "getUnlockedAbilities";
    private static final String GET_NODE_STATE = "getNodeState";

    @SuppressWarnings("unchecked")
    public static List<String> unlockedAbilities() {
        return guard(Feature.ABILITIES, () -> {
            Optional<Method> unlocked = method(AbilityTreeModel.class, GET_UNLOCKED_ABILITIES);
            if (unlocked.isPresent()) {
                return (List<String>) call(unlocked.get(), Models.AbilityTree);
            }
            Method state = method(AbilityTreeModel.class, GET_NODE_STATE, AbilityTreeSkillNode.class)
                    .orElseThrow(() -> missing("AbilityTreeModel." + GET_UNLOCKED_ABILITIES));
            ClassType c = Models.Character.getClassType();
            AbilityTreeInfo tree = c == null ? null : Models.AbilityTree.getAbilityTree(c);
            if (tree == null) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (AbilityTreeSkillNode n : tree.nodes()) {
                if (call(state, Models.AbilityTree, n) == AbilityTreeNodeState.UNLOCKED) {
                    out.add(n.name());
                }
            }
            return out;
        }, List.of());
    }

    private static AbilityTreeInfo lastTree;
    private static List<AbilityNode> lastNodes;

    /** The class's ability tree, or null when Wynntils has none for it. Converted once per tree. */
    public static List<AbilityNode> abilityTree(Klass cls) {
        GameState gs = GameState.current();
        if (!gs.running()) {
            // a recorded game: its own tree (the live one asks here, so not for it)
            return gs instanceof LiveGameState ? null : gs.abilityTree();
        }
        return guard(Feature.ABILITIES, () -> {
            ClassType c = cls == null ? null : ClassType.valueOf(cls.name());
            AbilityTreeInfo tree = Models.AbilityTree.getAbilityTree(c);
            if (tree == null) {
                return null;
            }
            synchronized (AbilityReads.class) {
                if (tree != lastTree) {
                    List<AbilityNode> nodes = new ArrayList<>();
                    for (AbilityTreeSkillNode n : tree.nodes()) {
                        nodes.add(node(n, c));
                    }
                    lastNodes = java.util.Collections.unmodifiableList(nodes);
                    lastTree = tree;
                }
                return lastNodes;
            }
        }, null);
    }

    // AbilityTreeSkillNode, by release:
    //   abilityTreeNodeType(), willBlock(), requiredLevel(): 4.1.22 on. Until 4.1.21 the node has
    //     no type (its spells are the nodes with a click combo), blocks() for willBlock(), and its
    //     data has no level requirement (0, as for most nodes now).
    //   archetypeInfo().archetype(): 4.2.5 on; until 4.2.3 archetype(), the same name.
    //   description(): component JSON lines (List<JsonArray>) from 4.2.5, before that formatted
    //     text lines (List<String>).
    private static final String NODE_TYPE = "abilityTreeNodeType";
    private static final String WILL_BLOCK = "willBlock";
    private static final String BLOCKS = "blocks";
    private static final String REQUIRED_LEVEL = "requiredLevel";
    private static final String ARCHETYPE_INFO = "archetypeInfo";
    private static final String ARCHETYPE = "archetype";
    private static final String ARCHETYPE_COLOUR = "color";

    @SuppressWarnings("unchecked")
    private static AbilityNode node(AbilityTreeSkillNode n, ClassType c) {
        var ra = n.requiredArchetype();
        var loc = n.location();
        List<JsonArray> description = description(n.description());
        String type;
        Optional<Method> nodeType = method(AbilityTreeSkillNode.class, NODE_TYPE);
        if (nodeType.isPresent()) {
            Object t = call(nodeType.get(), n);
            type = t == null ? null : ((Enum<?>) t).name();
        } else {
            type = c != null && isSpell(description) ? c.name() + "_ABILITY_LOCKED" : null;
        }
        Optional<Method> willBlock = method(AbilityTreeSkillNode.class, WILL_BLOCK);
        List<String> blocks = (List<String>) call(willBlock.isPresent() ? willBlock.get()
                : method(AbilityTreeSkillNode.class, BLOCKS).orElseThrow(() -> missing("AbilityTreeSkillNode." + WILL_BLOCK)), n);
        Optional<Method> level = method(AbilityTreeSkillNode.class, REQUIRED_LEVEL);
        int requiredLevel = level.isPresent() ? (Integer) call(level.get(), n) : 0;
        String archetype;
        String colour = null;
        Optional<Method> info = method(AbilityTreeSkillNode.class, ARCHETYPE_INFO);
        if (info.isPresent()) {
            Object ai = call(info.get(), n);
            archetype = ai == null ? null : (String) call(ai, ARCHETYPE);
            colour = ai == null ? null : (String) call(ai, ARCHETYPE_COLOUR);
        } else {
            archetype = (String) call(n, ARCHETYPE);
        }
        return new AbilityNode(n.id(), n.name(), type, description, n.cost(), blocks, n.requiredAbility(),
                ra == null ? null : ra.name(), ra == null ? 0 : ra.required(), requiredLevel, archetype, colour,
                loc == null ? null : new AbilityNode.Place(loc.page(), loc.row(), loc.col()), n.connections());
    }

    // AbilityTreeConnectionType.fromItemStack: 4.2 on (the piece by its model data); before that
    // the pieces were told apart by damage, which no item carries now.
    private static final String CONNECTOR_FROM_STACK = "fromItemStack";

    /**
     * A connector piece for an item of the tree menu: its type's name and its arms (up, right,
     * down, left), or null when the item is no piece.
     */
    public static TreeConnectors.Piece connector(ItemStack stack) {
        return guard(Feature.ABILITIES, () -> {
            Method m = method(AbilityTreeConnectionType.class, CONNECTOR_FROM_STACK, ItemStack.class).orElse(null);
            if (m == null || stack == null || stack.isEmpty()) {
                return null;
            }
            Object t = call(m, null, stack);
            if (!(t instanceof AbilityTreeConnectionType type)) {
                return null;
            }
            boolean[] d = type.getPossibleDirections();
            return d == null || d.length < 4 ? null : new TreeConnectors.Piece(type.name(), d.clone());
        }, null);
    }

    private static final Map<String, ItemStack> CONNECTOR_ICONS = new ConcurrentHashMap<>();

    /**
     * The game's own picture of a connector piece with these arms (up, right, down, left),
     * the given ones lit, as the tree menu draws it; null without the pack's model data or
     * when Wynntils has no piece of that shape.
     */
    public static ItemStack connectorIcon(boolean[] arms, boolean[] lit) {
        if (arms == null || arms.length < 4) {
            return null;
        }
        ItemStack s = CONNECTOR_ICONS.computeIfAbsent(Arrays.toString(arms) + Arrays.toString(lit), k -> guard(Feature.ABILITIES, () -> {
            AbilityTreeConnectionType t = null;
            for (AbilityTreeConnectionType c : AbilityTreeConnectionType.values()) {
                if (Arrays.equals(c.getPossibleDirections(), arms)) {
                    t = c;
                    break;
                }
            }
            if (t == null) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = t.getItemStack(lit);
            if (stack == null || stack.isEmpty()
                    || !stack.has(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA)) {
                return ItemStack.EMPTY;
            }
            var model = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
            if (model == null || model.floats().isEmpty() || model.floats().get(0) < 0) {
                return ItemStack.EMPTY;         // -1: the pack has no model for it
            }
            return stack.copy();
        }, ItemStack.EMPTY));
        return s.isEmpty() ? null : s;
    }

    // AbilityTreeNodeType (4.1.22 on): every node type in every state, each with the model data
    // of the game's own icon when the Wynncraft pack is loaded.
    private static final String NODE_TYPE_CLASS = "com.wynntils.models.abilitytree.type.AbilityTreeNodeType";
    private static final String GET_CUSTOM_MODEL_DATA = "getCustomModelData";
    private static final String[] NODE_STATES = {"_LOCKED", "_UNLOCKABLE", "_UNLOCKED", "_BLOCKED"};
    private static final Map<String, ItemStack> NODE_ICONS = new ConcurrentHashMap<>();

    /**
     * The game's own icon for a node type ({@link AbilityNode#type}) in a state ("LOCKED",
     * "UNLOCKABLE", "UNLOCKED", "BLOCKED"): a stack the pack repaints, as the tree menu shows it.
     * Null without the pack's model data, or when Wynntils has no such type (a state the type
     * lacks falls back to LOCKED).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static ItemStack nodeIcon(String type, String state) {
        if (type == null) {
            return null;
        }
        ItemStack s = NODE_ICONS.computeIfAbsent(type + "|" + state, k -> guard(Feature.ABILITIES, () -> {
            String base = type;
            for (String suffix : NODE_STATES) {
                if (base.endsWith(suffix)) {
                    base = base.substring(0, base.length() - suffix.length());
                }
            }
            Class<?> t = type(NODE_TYPE_CLASS).orElse(null);
            if (t == null || !t.isEnum()) {
                return ItemStack.EMPTY;
            }
            Object v;
            try {
                v = Enum.valueOf((Class<Enum>) t, base + "_" + state);
            } catch (IllegalArgumentException e) {
                try {
                    v = Enum.valueOf((Class<Enum>) t, base + "_LOCKED");
                } catch (IllegalArgumentException e2) {
                    return ItemStack.EMPTY;
                }
            }
            Optional<Float> model = (Optional<Float>) call(v, GET_CUSTOM_MODEL_DATA);
            if (model == null || model.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = new ItemStack(net.minecraft.world.item.Items.POTION);
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new net.minecraft.world.item.component.CustomModelData(List.of(model.get()), List.of(), List.of(), List.of()));
            return stack;
        }, ItemStack.EMPTY));
        return s.isEmpty() ? null : s;
    }

    /** A node's description as component JSON lines; a formatted text line becomes one plain part. */
    @SuppressWarnings("unchecked")
    private static List<JsonArray> description(List<?> lines) {
        if (lines == null || lines.stream().allMatch(l -> l instanceof JsonArray)) {
            return (List<JsonArray>) lines;          // 4.2.5 on: already component JSON
        }
        List<JsonArray> out = new ArrayList<>(lines.size());
        for (Object line : lines) {
            if (line instanceof JsonArray a) {
                out.add(a);
            } else if (line != null) {
                JsonObject part = new JsonObject();
                String plain = ChatFormatting.stripFormatting(line.toString());
                part.addProperty("text", plain == null ? "" : plain);
                JsonArray a = new JsonArray();
                a.add(part);
                out.add(a);
            }
        }
        return out;
    }

    /** Whether a node is a spell: its description shows the click combo that casts it. */
    private static boolean isSpell(List<JsonArray> description) {
        if (description != null) {
            for (JsonArray line : description) {
                for (var e : line) {
                    if (e.isJsonObject() && e.getAsJsonObject().has("text")
                            && e.getAsJsonObject().get("text").getAsString().contains("Click Combo")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // AbilityPointProgression.getPointsAtLevel(int): 4.2.0 on. Releases before have no table of
    // ability points per level, so there this call is off.
    private static final String POINT_PROGRESSION = "com.wynntils.models.abilitytree.type.AbilityPointProgression";
    private static final String GET_POINTS_AT_LEVEL = "getPointsAtLevel";

    /** Ability points a character has at this level. */
    public static int abilityPointsAt(int level) {
        return guard(Feature.ABILITIES, () -> {
            Method m = type(POINT_PROGRESSION).flatMap(t -> method(t, GET_POINTS_AT_LEVEL, int.class))
                    .orElseThrow(() -> missing("AbilityPointProgression." + GET_POINTS_AT_LEVEL));
            return (Integer) call(m, null, level);
        }, 0);
    }
}
