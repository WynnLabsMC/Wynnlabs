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
        boolean overPanel = panel.over(w(), h(), mx, my);
        int pmx = overPanel ? -10000 : mx;
        int pmy = overPanel ? -10000 : my;
        renderMap(g);
        RenderUtils.enableScissor(g, (int) (renderX + renderedBorderXOffset), (int) (renderY + renderedBorderYOffset),
                (int) mapWidth, (int) mapHeight);
        renderPois(new ArrayList<>(pois), g, BoundingBox.centered(mapCenterX, mapCenterZ, w() / zoomRenderScale,
                h() / zoomRenderScale), 1f, pmx, pmy);
        extras(g, pois, now);
        GuildMapFeature f = Managers.Feature.getFeatureInstance(GuildMapFeature.class);
        renderCursor(g, 1.5f, f.pointerColor.get(), f.pointerType.get());
        RenderUtils.disableScissor(g);
        renderMapBorder(g);
        renderCoordinates(g, pmx, pmy);
        renderZoomWidgets(g, mx, my, partialTick);
        if (hovered instanceof TerritoryPoi t) {
            GuildMapLook.card(g, GuildMapReads.of(t), w() - (panel.open ? GuildMapPanel.W + 4 : 0), now);
        }
        panel.draw(g, f(), w(), h(), looks, mx, my);
        renderTooltip(g, pmx, pmy);
    }

    /** The territories as Wynntils draws them, with their trade routes when the panel says so. */
    @Override
    protected void renderPois(List<Poi> pois, GuiGraphics g, BoundingBox box, float scale, int mx, int my) {
        hovered = null;
        List<Poi> shown = getRenderedPois(pois, box, scale, mx, my);
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
    }

    /** Held times and produce under the tags, and the picked guild's territories outlined. */
    private void extras(GuiGraphics g, List<TerritoryPoi> pois, long now) {
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
            if (panel.timers) {
                float cx = MapRenderer.getRenderX(p, mapCenterX, centerX, zoomRenderScale);
                float cz = MapRenderer.getRenderZ(p, mapCenterZ, centerZ, zoomRenderScale);
                GuildMapLook.label(g, t, cx, cz, Math.abs(x1 - x0), Math.max(z0, z1), now);
            }
        }
    }

    @Override
    public boolean doMouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (panel.over(w(), h(), event.x(), event.y())) {
            GuildMapPanel.Click c = panel.click(event.x(), event.y());
            if (c != null && c.what().equals("territory")) {
                for (TerritoryPoi p : GuildMapReads.pois()) {
                    if (p.getName().equals(c.arg())) {
                        updateMapCenter(p.getLocation().getX(), p.getLocation().getZ());
                        break;
                    }
                }
            }
            return true;
        }
        panel.typing = false;
        return super.doMouseClicked(event, doubleClick);
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
        if (panel.over(w(), h(), mx, my)) {
            panel.scroll(amount);
            return true;
        }
        return false;
    }

    // Minecraft's own fields through Screen: the build renames them only when the owner it sees
    // is Minecraft's class, not this one (whose parent comes from Wynntils' jar)
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
