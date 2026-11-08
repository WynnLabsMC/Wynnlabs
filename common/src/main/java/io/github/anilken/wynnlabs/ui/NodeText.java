package io.github.anilken.wynnlabs.ui;

import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.mixin.PowerupLayerAccessor;
import io.github.anilken.wynnlabs.mixin.PowerupModelAccessor;
import io.github.anilken.wynnlabs.model.GatherNode;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.objects.AtlasSprite;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game's label over a gathering node, on this screen only (setting nodePrices): its
 * "Left-Click for Ingot" and "Right-Click for Gem" lines become the item's picture, its name and
 * its Trade Market price per tier ("T1 53E T2 2EB T3 17EB") in the mod's colours; the mouse
 * glyph and the lines above stay. The client's copy of the label is changed and nothing is sent;
 * shown only with a gathering tool in hand, and the original comes back without one or when the
 * setting is turned off.
 */
public final class NodeText {

    private static final Pattern CLICK = Pattern.compile("(Left|Right)-Click for (.+)$");

    /** Label entity id → the text we set, and the game's own text it replaced. */
    private static final Map<Integer, Component> OURS = new HashMap<>();
    private static final Map<Integer, Component> THEIRS = new HashMap<>();
    private static final Map<String, TextureAtlasSprite> SPRITES = new HashMap<>();
    private static int ticks;

    private NodeText() {}

    public static void onTick(Minecraft mc) {
        if (++ticks % 10 != 0 || mc.level == null) {
            return;
        }
        // only with a gathering tool in hand, as the nodes' outline shows
        boolean on = Settings.on("nodePrices") && Wyn.holdingGatherTool();
        if (!on || ticks % 600 == 0) {
            // off or no tool: the game's own text back; every half minute: rebuilt with fresh prices
            if (!OURS.isEmpty()) {
                restore(mc);
            }
            if (!on) {
                return;
            }
        }
        List<GatherNode> nodes;
        try {
            nodes = Wyn.gatherNodes();
        } catch (Throwable t) {
            return;
        }
        for (GatherNode n : nodes) {
            Entity e = mc.level.getEntity(n.entityId());
            if (!(e instanceof Display.TextDisplay td) || td.getText() == null) {
                continue;
            }
            Component now = td.getText();
            if (now == OURS.get(n.entityId())) {
                continue;                       // ours is still on
            }
            Component neo = rewrite(mc, now, n.resource());
            if (neo != null) {
                THEIRS.put(n.entityId(), now);
                OURS.put(n.entityId(), neo);
                ((io.github.anilken.wynnlabs.mixin.TextDisplayAccessor) td).wynnlabs$setText(neo);
            }
        }
    }

    public static void reset() {
        OURS.clear();
        THEIRS.clear();
    }

    private static void restore(Minecraft mc) {
        for (Map.Entry<Integer, Component> o : OURS.entrySet()) {
            Entity e = mc.level == null ? null : mc.level.getEntity(o.getKey());
            if (e instanceof Display.TextDisplay td && td.getText() == o.getValue()) {
                ((io.github.anilken.wynnlabs.mixin.TextDisplayAccessor) td).wynnlabs$setText(THEIRS.get(o.getKey()));
            }
        }
        reset();
    }

    /** One styled piece of the label's text. */
    private record Piece(Style style, String text) {}

    /** The label with its click lines replaced, or null when it has none. */
    private static Component rewrite(Minecraft mc, Component label, String resource) {
        // the label's text in styled pieces, cut into lines
        List<List<Piece>> lines = new ArrayList<>();
        lines.add(new ArrayList<>());
        label.visit((style, text) -> {
            String[] parts = text.split("\n", -1);
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    lines.add(new ArrayList<>());
                }
                if (!parts[i].isEmpty()) {
                    lines.get(lines.size() - 1).add(new Piece(style, parts[i]));
                }
            }
            return Optional.empty();
        }, Style.EMPTY);
        MutableComponent out = Component.empty();
        boolean changed = false;
        for (int l = 0; l < lines.size(); l++) {
            List<Piece> line = lines.get(l);
            StringBuilder plain = new StringBuilder();
            for (Piece p : line) {
                plain.append(p.text());
            }
            Matcher m = CLICK.matcher(plain.toString().replaceAll("§.", "").strip());
            MutableComponent row = Component.empty();
            if (m.find()) {
                // keep what comes before "Left-Click" (the mouse glyph), then ours
                for (Piece p : line) {
                    int at = p.text().indexOf(m.group(1) + "-Click");
                    if (at >= 0) {
                        row.append(Component.literal(p.text().substring(0, at)).withStyle(p.style()));
                        break;
                    }
                    row.append(Component.literal(p.text()).withStyle(p.style()));
                }
                row.append(ours(mc, resource + " " + m.group(2).strip(), m.group(2).strip()));
                changed = true;
            } else {
                for (Piece p : line) {
                    row.append(Component.literal(p.text()).withStyle(p.style()));
                }
            }
            if (l > 0) {
                out.append("\n");
            }
            out.append(row);
        }
        return changed ? out : null;
    }

    /** The item's picture, its short name and its price per tier: "[icon] Ingot T1 53E T2 2EB T3 17EB". */
    private static MutableComponent ours(Minecraft mc, String item, String word) {
        MutableComponent c = Component.empty();
        TextureAtlasSprite sprite = sprite(mc, item);
        if (sprite != null) {
            // the atlas by its id ("minecraft:items"), not its texture ("textures/atlas/items.png")
            net.minecraft.resources.Identifier atlas = sprite.atlasLocation();
            String path = atlas.getPath();
            if (path.startsWith("textures/atlas/") && path.endsWith(".png")) {
                atlas = net.minecraft.resources.Identifier.fromNamespaceAndPath(atlas.getNamespace(),
                        path.substring("textures/atlas/".length(), path.length() - 4));
            }
            c.append(Component.object(new AtlasSprite(atlas, sprite.contents().name()))).append(" ");
        }
        c.append(Component.literal(word).withStyle(ChatFormatting.WHITE));
        for (int t = 1; t <= 1; t++) {   // T1 only: three tiers made the line too long
            Prices.Price p = Prices.of(item + " T" + t);
            Double v = p == null ? null : p.typical();
            String price = "?";
            if (v != null) {
                // its largest unit, one decimal at most: "53E", "17EB", "1.2LE"
                if (v < 64) {
                    price = Math.round(v) + "E";
                } else {
                    double u = v < 4096 ? v / 64 : v / 4096;
                    String n = u >= 10 ? String.valueOf(Math.round(u)) : String.valueOf(Math.round(u * 10) / 10.0);
                    price = (n.endsWith(".0") ? n.substring(0, n.length() - 2) : n) + (v < 4096 ? "EB" : "LE");
                }
            }
            c.append(Component.literal("  "));   // the T1 price, no tier label
            c.append(Component.literal(price).withStyle(ChatFormatting.GOLD));
        }
        return c;
    }

    /** The item's main sprite as its model draws it, or null (read once per item). */
    private static TextureAtlasSprite sprite(Minecraft mc, String item) {
        if (SPRITES.containsKey(item)) {
            return SPRITES.get(item);
        }
        TextureAtlasSprite main = null;
        try {
            ItemStack stack = CraftIcons.of(item);
            if (stack != null && !stack.isEmpty()) {
                ItemStackRenderState state = new ItemStackRenderState();
                mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GUI, mc.level, null, 0);
                PowerupModelAccessor acc = (PowerupModelAccessor) state;
                ItemStackRenderState.LayerRenderState[] layers = acc.wynnlabs$layers();
                for (int i = 0; i < acc.wynnlabs$layerCount() && main == null; i++) {
                    for (BakedQuad q : ((PowerupLayerAccessor) layers[i]).wynnlabs$quads()) {
                        if (!q.sprite().contents().name().getPath().endsWith("empty")) {
                            main = q.sprite();
                            break;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            main = null;
        }
        SPRITES.put(item, main);
        return main;
    }
}
