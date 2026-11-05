package io.github.anilken.wynnlabs.game.wynntils;

import com.wynntils.core.components.Managers;
import com.wynntils.features.map.GuildMapFeature;
import com.wynntils.screens.maps.AbstractMapScreen;
import com.wynntils.services.map.pois.Poi;
import com.wynntils.services.map.pois.TerritoryPoi;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.utils.colors.CommonColors;
import com.wynntils.utils.render.MapRenderer;
import com.wynntils.utils.render.RenderUtils;
import com.wynntils.utils.type.BoundingBox;
import io.github.anilken.wynnlabs.ui.GuildMapLook;
import io.github.anilken.wynnlabs.ui.GuildMapPanel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * The mod's guild map, opened wherever Wynntils would open its own: the same map and territory
 * tags (it is built on Wynntils' map screen), with each territory's held time and produce under
 * its tag, a fuller card for the hovered territory, and a side panel with the guilds by
 * territories held.
 */
public final class GuildMapScreen extends AbstractMapScreen implements io.github.anilken.wynnlabs.ui.RawInput {

    /** The panel outlives the screen, so the map opens as it was left. */
    private static GuildMapPanel panel;
    private List<GuildMapLook.Territory> looks = List.of();
    private long lookedAt;
    /** The picked guild's routes to its HQ: each territory's distance, worked out once a second. */
    private java.util.Map<String, Integer> hqDist;
    private String hqDistFor;
    private long hqDistAt;

    public GuildMapScreen() {
        super();
        if (panel == null) {
            panel = new GuildMapPanel();
        }
    }

    @Override
    public void doRender(GuiGraphics g, int mx, int my, float partialTick) {
        long now = System.currentTimeMillis();
        List<TerritoryPoi> pois = GuildMapReads.pois();
        if (now - lookedAt > 1000 || looks.size() != pois.size()) {
            List<GuildMapLook.Territory> out = new ArrayList<>(pois.size());
            for (TerritoryPoi p : pois) {
                out.add(GuildMapReads.of(p));
            }
            looks = out;
            lookedAt = now;
        }
        // a guild picked in the panel: only its territories on the map
        List<TerritoryPoi> shown = pois;
        List<GuildMapLook.Territory> shownLooks = looks;
        if (panel.guild != null && looks.size() == pois.size()) {
            shown = new ArrayList<>();
            shownLooks = new ArrayList<>();
            for (int i = 0; i < pois.size(); i++) {
                if (panel.guild.equals(looks.get(i).guild())) {
                    shown.add(pois.get(i));
                    shownLooks.add(looks.get(i));
                }
            }
        }
        boolean overPanel = panel.over(mapRight(), mapTop(), mapBottom(), mx, my);
        int pmx = overPanel ? -10000 : mx;
        int pmy = overPanel ? -10000 : my;
        renderMap(g);
        RenderUtils.enableScissor(g, (int) (renderX + renderedBorderXOffset), (int) (renderY + renderedBorderYOffset),
                (int) mapWidth, (int) mapHeight);
        renderPois(new ArrayList<>(shown), g, BoundingBox.centered(mapCenterX, mapCenterZ, w() / zoomRenderScale,
                h() / zoomRenderScale), 1f, pmx, pmy);
        extras(g, shown, shownLooks, now);
        GuildMapFeature f = Managers.Feature.getFeatureInstance(GuildMapFeature.class);
        renderCursor(g, 1.5f, f.pointerColor.get(), f.pointerType.get());
        RenderUtils.disableScissor(g);
        renderMapBorder(g);
        renderCoordinates(g, pmx, pmy);
        renderZoomWidgets(g, mx, my, partialTick);
        if (hovered instanceof TerritoryPoi t) {
            GuildMapLook.card(g, GuildMapReads.of(t), mapRight() - panel.taken(), mapTop(), now);
        }
        // the panel's scroll bar follows the mouse while its button is held
        if (panel.dragging) {
            long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
            if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
                panel.drag(my);
            } else {
                panel.dragging = false;
            }
        }
        panel.draw(g, f(), mapRight(), mapTop(), mapBottom(), looks, mx, my);
        renderTooltip(g, pmx, pmy);
    }

    /** The territories as Wynntils draws them, with their trade routes when the panel says so. */
    @Override
    protected void renderPois(List<Poi> pois, GuiGraphics g, BoundingBox box, float scale, int mx, int my) {
        hovered = null;
        List<Poi> shown = getRenderedPois(pois, box, scale, mx, my);
        java.util.Map<String, Integer> dist = flowFor();
        List<float[]> flows = new ArrayList<>();
        if (panel.routes) {
            for (Poi p : shown) {
                if (!(p instanceof TerritoryPoi t) || t.isFakeTerritoryInfo() || t.getTerritoryInfo() == null) {
                    continue;
                }
                float x = MapRenderer.getRenderX(p, mapCenterX, centerX, zoomRenderScale);
                float z = MapRenderer.getRenderZ(p, mapCenterZ, centerZ, zoomRenderScale);
                for (String route : t.getTerritoryInfo().getTradingRoutes()) {
                    for (Poi o : shown) {
                        if (o.getName().equals(route)) {
                            float ox = MapRenderer.getRenderX(o, mapCenterX, centerX, zoomRenderScale);
                            float oz = MapRenderer.getRenderZ(o, mapCenterZ, centerZ, zoomRenderScale);
                            RenderUtils.drawLine(g, CommonColors.DARK_GRAY, x, z, ox, oz, 1f);
                            // the picked guild's resources flow along it toward the HQ
                            Integer dp = dist == null ? null : dist.get(p.getName());
                            Integer dq = dist == null ? null : dist.get(o.getName());
                            if (dp != null && dq != null && dp > dq) {
                                flows.add(new float[] {x, z, ox, oz, Math.abs(p.getName().hashCode() % 1000) / 1000f});
                            }
                            break;
                        }
                    }
                }
            }
        }
        for (int i = shown.size() - 1; i >= 0; i--) {
            Poi p = shown.get(i);
            float x = MapRenderer.getRenderX(p, mapCenterX, centerX, zoomRenderScale);
            float z = MapRenderer.getRenderZ(p, mapCenterZ, centerZ, zoomRenderScale);
            p.renderAt(g, x, z, hovered == p, scale, zoomRenderScale, zoomLevel, true);
        }
        // flowing dots over the boxes: two on each route, from the farther territory to the nearer
        double t = (System.currentTimeMillis() % 6000L) / 6000.0;   // a route crossed in six seconds
        // the dots grow and shrink with the map (half a GUI pixel far out, two close in) and glide
        // between pixels; a route too short on screen gets none
        float size = Math.max(0.5f, Math.min(2f, zoomRenderScale * 1.5f));
        for (float[] f : flows) {
            float len = (float) Math.hypot(f[2] - f[0], f[3] - f[1]);
            if (len < size * 12) {
                continue;
            }
            for (int k = 0; k < 2; k++) {
                double s = (t + k * 0.5 + f[4]) % 1.0;
                g.pose().pushMatrix();
                g.pose().translate((float) (f[0] + (f[2] - f[0]) * s), (float) (f[1] + (f[3] - f[1]) * s));
                g.pose().scale(size, size);
                g.fill(-2, -2, 2, 2, 0xFF3A2A00);
                g.fill(-1, -1, 1, 1, 0xFFFFD24A);
                g.pose().popMatrix();
            }
        }
    }

    /** Distances to the picked guild's HQ, or null without a picked guild (or its HQ unknown). */
    private java.util.Map<String, Integer> flowFor() {
        if (panel.guild == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (panel.guild.equals(hqDistFor) && now - hqDistAt < 1000) {
            return hqDist;
        }
        String hq = null;
        for (GuildMapLook.Territory t : looks) {
            if (t.hq() && panel.guild.equals(t.guild())) {
                hq = t.name();
                break;
            }
        }
        hqDist = hq == null ? null : GuildMapReads.distances(hq);
        hqDistFor = panel.guild;
        hqDistAt = now;
        return hqDist;
    }

    /** Held times and produce under the tags, and the picked guild's territories outlined. */
    private void extras(GuiGraphics g, List<TerritoryPoi> pois, List<GuildMapLook.Territory> looks, long now) {
        float left = renderX + renderedBorderXOffset;
        float top = renderY + renderedBorderYOffset;
        for (int i = 0; i < pois.size() && i < looks.size(); i++) {
            TerritoryPoi p = pois.get(i);
            TerritoryProfile prof = p.getTerritoryProfile();
            if (prof == null) {
                continue;
            }
            float x0 = MapRenderer.getRenderX(prof.getStartX(), mapCenterX, centerX, zoomRenderScale);
            float x1 = MapRenderer.getRenderX(prof.getEndX(), mapCenterX, centerX, zoomRenderScale);
            float z0 = MapRenderer.getRenderZ(prof.getStartZ(), mapCenterZ, centerZ, zoomRenderScale);
            float z1 = MapRenderer.getRenderZ(prof.getEndZ(), mapCenterZ, centerZ, zoomRenderScale);
            if (Math.max(x0, x1) < left || Math.min(x0, x1) > left + mapWidth || Math.max(z0, z1) < top
                    || Math.min(z0, z1) > top + mapHeight) {
                continue;
            }
            GuildMapLook.Territory t = looks.get(i);
            if (panel.guild != null && panel.guild.equals(t.guild())) {
                int ax = (int) Math.min(x0, x1);
                int az = (int) Math.min(z0, z1);
                g.renderOutline(ax, az, (int) Math.abs(x1 - x0), (int) Math.abs(z1 - z0), 0xFFFFFFFF);
            }
            if (t.acquired() > 0 && now - t.acquired() < 10 * 60_000L) {
                // just taken, still on cooldown: a red frame that pulses
                int a = (int) (90 + 120 * (0.5 + 0.5 * Math.sin(now / 250.0)));
                int ax = (int) Math.min(x0, x1);
                int az = (int) Math.min(z0, z1);
                g.renderOutline(ax, az, (int) Math.abs(x1 - x0), (int) Math.abs(z1 - z0), a << 24 | 0xFF5555);
                g.renderOutline(ax + 1, az + 1, (int) Math.abs(x1 - x0) - 2, (int) Math.abs(z1 - z0) - 2, a << 24 | 0xFF5555);
            }
            if (panel.timers) {
                float cx = MapRenderer.getRenderX(p, mapCenterX, centerX, zoomRenderScale);
                float cz = MapRenderer.getRenderZ(p, mapCenterZ, centerZ, zoomRenderScale);
                GuildMapLook.label(g, t, cx, cz, Math.abs(x1 - x0), Math.max(z0, z1), now);
            }
        }
    }

    @Override
    public boolean doMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (panel.over(mapRight(), mapTop(), mapBottom(), event.x(), event.y())) {
            GuildMapPanel.Click c = panel.click(event.x(), event.y());
            if (c != null && c.what().equals("territory")) {
                for (TerritoryPoi p : GuildMapReads.pois()) {
                    if (p.getName().equals(c.arg())) {
                        focus(p);
                        break;
                    }
                }
            }
            return true;
        }
        panel.typing = false;
        // a right click lets the picked guild go: every territory comes back
        if (event.button() == 1 && panel.guild != null) {
            panel.guild = null;
            return true;
        }
        // a left click on a territory opens its guild in the panel
        if (event.button() == 0 && hovered instanceof TerritoryPoi t) {
            String guild = GuildMapReads.of(t).guild();
            if (guild != null && !guild.isBlank()) {
                panel.show(guild);
                return true;
            }
        }
        return super.doMouseClicked(event, doubleClick);
    }

    /**
     * Centres the map on a territory and zooms so it fills about a sixth of the map's width (or
     * height), the most Wynntils' zoom allows at either end.
     */
    private void focus(TerritoryPoi p) {
        TerritoryProfile prof = p.getTerritoryProfile();
        if (prof != null) {
            float tw = Math.max(1, Math.abs(prof.getEndX() - prof.getStartX()));
            float th = Math.max(1, Math.abs(prof.getEndZ() - prof.getStartZ()));
            float want = Math.min(mapWidth / 6f / tw, mapHeight / 6f / th);
            int level = 1;
            for (int l = 1; l <= 100; l++) {
                if (MapRenderer.getZoomRenderScaleFromLevel(l) <= want) {
                    level = l;
                }
            }
            setZoomLevel(level);
        }
        updateMapCenter(p.getLocation().getX(), p.getLocation().getZ());
    }

    /** While the search box has the keyboard, letters go to it and never reach the map's shortcuts. */
    @Override
    public boolean rawChar(int codepoint) {
        if (panel.typing) {
            panel.type(codepoint);
            return true;
        }
        return false;
    }

    @Override
    public boolean rawKey(int key) {
        if (!panel.typing) {
            return false;
        }
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE) {
            panel.erase();
        } else if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE || key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
            panel.typing = false;
        }
        return true;
    }

    /** The wheel over the panel scrolls it; over the map it zooms as before. */
    @Override
    public boolean rawScroll(double mx, double my, double amount) {
        if (panel.over(mapRight(), mapTop(), mapBottom(), mx, my)) {
            panel.scroll(amount);
            return true;
        }
        return false;
    }

    // Minecraft's own fields through Screen: the build renames them only when the owner it sees
    // is Minecraft's class, not this one (whose parent comes from Wynntils' jar)
    /** The map's drawn area inside its frame: the panel and the card stay within it. */
    private int mapTop() {
        return (int) (renderY + renderedBorderYOffset);
    }

    private int mapBottom() {
        return (int) (renderY + renderedBorderYOffset + mapHeight);
    }

    private int mapRight() {
        return (int) (renderX + renderedBorderXOffset + mapWidth);
    }

    private int w() {
        return ((Screen) this).width;
    }

    private int h() {
        return ((Screen) this).height;
    }

    private Font f() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }
}
