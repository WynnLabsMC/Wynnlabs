package io.github.anilken.wynnlabs.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.anilken.wynnlabs.core.Settings;
import io.github.anilken.wynnlabs.game.Wyn;
import io.github.anilken.wynnlabs.market.Prices;
import io.github.anilken.wynnlabs.mixin.PowerupLayerAccessor;
import io.github.anilken.wynnlabs.mixin.PowerupModelAccessor;
import io.github.anilken.wynnlabs.model.GatherNode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Over each gathering node near the player (setting nodePrices), on one line: the left click's
 * item and the right click's, each as its picture and its Trade Market price (the range of its
 * three tiers), facing the camera and seen through walls like the node's outline. Only draws: the
 * nodes come from the game's own labels.
 */
public final class NodePrices {

    /** Nodes further than this get no label. */
    private static final double RANGE = 40;
    /** Blocks above the game's label, so ours sits over its lines. */
    private static final double LIFT = 1.4;
    /** Behind the text, like a name tag's backing. */
    private static final int BACKING = 0x80000000;
    /** The picture's size in font pixels, and the gap after it. */
    private static final int ICON = 9;
    private static final int GAP = 2;
    /** Between the left click's item and the right click's. */
    private static final int SPACE = 8;

    private static List<GatherNode> nodes = List.of();
    private static long nodesAt;
    private static final Map<String, TextureAtlasSprite> SPRITES = new HashMap<>();
    private static final Map<String, String> PRICES = new HashMap<>();
    private static long pricesAt;

    private NodePrices() {}

    public static void renderWorld(PoseStack pose, MultiBufferSource buffers) {
        if (!Settings.on("nodePrices")) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - nodesAt > 500) {
            nodesAt = now;
            try {
                nodes = Wyn.gatherNodes();
            } catch (Throwable t) {
                nodes = List.of();
            }
        }
        if (now - pricesAt > 30_000) {
            pricesAt = now;
            PRICES.clear();                     // prices change; pictures do not
        }
        if (nodes.isEmpty()) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        List<double[]> near = new ArrayList<>();
        List<GatherNode> which = new ArrayList<>();
        for (GatherNode n : nodes) {
            double x = n.x() + 0.5 - cam.x;
            double y = n.y() + LIFT - cam.y;
            double z = n.z() + 0.5 - cam.z;
            double d = Math.sqrt(x * x + y * y + z * z);
            if (d <= RANGE && (n.left() != null || n.right() != null)) {
                near.add(new double[] {x, y, z, d, which.size()});
                which.add(n);
            }
        }
        // far to near, so each backing blends over the ones behind it
        near.sort(Comparator.comparingDouble((double[] a) -> -a[3]));
        Font font = mc.font;
        for (double[] a : near) {
            GatherNode n = which.get((int) a[4]);
            float k = PowerupLabels.scale(a[3], 100, 0.9f);
            // the left click's item on the left, the right click's on the right, as the mouse
            List<TextureAtlasSprite> sprites = new ArrayList<>();
            List<String> texts = new ArrayList<>();
            for (String word : new String[] {n.left(), n.right()}) {
                if (word != null) {
                    String item = n.resource() + " " + word;
                    String price = price(item);
                    sprites.add(sprite(mc, item));
                    texts.add(price == null ? "?" : price);
                }
            }
            drawLine(pose, buffers, font, camera, a[0], a[1], a[2], k, sprites, texts);
        }
        if (buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch();
        }
    }

    /** One line of pictures, each followed by its price on a backing, centred. Seen through blocks. */
    private static void drawLine(PoseStack pose, MultiBufferSource buffers, Font font, Camera camera, double x, double y, double z,
                                 float k, List<TextureAtlasSprite> sprites, List<String> texts) {
        int total = 0;
        for (int i = 0; i < texts.size(); i++) {
            total += (i > 0 ? SPACE : 0) + (sprites.get(i) == null ? 0 : ICON + GAP) + font.width(texts.get(i));
        }
        float x0 = -total / 2f;
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(camera.rotation());
        pose.scale(k, -k, k);
        Matrix4f m = pose.last().pose();
        for (int i = 0; i < texts.size(); i++) {
            x0 += i > 0 ? SPACE : 0;
            x0 = segment(m, buffers, font, x0, sprites.get(i), texts.get(i));
        }
        pose.popPose();
    }

    /** One picture and its price from {@code x0}; returns where the next one starts. */
    private static float segment(Matrix4f m, MultiBufferSource buffers, Font font, float x0, TextureAtlasSprite sprite, String text) {
        float ly = 0;
        if (sprite != null) {
            VertexConsumer vc = buffers.getBuffer(RenderTypes.textSeeThrough(sprite.atlasLocation()));
            float top = ly - 1;
            int light = LightTexture.FULL_BRIGHT;
            vc.addVertex(m, x0, top, 0).setColor(0xFFFFFFFF).setUv(sprite.getU0(), sprite.getV0()).setLight(light);
            vc.addVertex(m, x0, top + ICON, 0).setColor(0xFFFFFFFF).setUv(sprite.getU0(), sprite.getV1()).setLight(light);
            vc.addVertex(m, x0 + ICON, top + ICON, 0).setColor(0xFFFFFFFF).setUv(sprite.getU1(), sprite.getV1()).setLight(light);
            vc.addVertex(m, x0 + ICON, top, 0).setColor(0xFFFFFFFF).setUv(sprite.getU1(), sprite.getV0()).setLight(light);
            x0 += ICON + GAP;
        }
        font.drawInBatch(text, x0, ly, 0xFFFFFFFF, false, m, buffers, Font.DisplayMode.SEE_THROUGH, BACKING, LightTexture.FULL_BRIGHT);
        return x0 + font.width(text);
    }

    /** The item's Trade Market price over its three tiers ("12EB-1LE 4EB"), or null when none is known. */
    private static String price(String item) {
        if (PRICES.containsKey(item)) {
            return PRICES.get(item);
        }
        Double low = null;
        Double high = null;
        for (int t = 1; t <= 3; t++) {
            Prices.Price p = Prices.of(item + " T" + t);
            Double v = p == null ? null : p.typical();
            if (v == null) {
                continue;
            }
            low = low == null ? v : Math.min(low, v);
            high = high == null ? v : Math.max(high, v);
        }
        String out = low == null ? null
                : Prices.fmt(low).equals(Prices.fmt(high)) ? Prices.fmt(low) : Prices.fmt(low) + "-" + Prices.fmt(high);
        PRICES.put(item, out);
        return out;
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
