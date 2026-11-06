package io.github.anilken.wynnlabs.game.wynntils;

import com.wynntils.core.components.Managers;
import com.wynntils.core.components.Models;
import com.wynntils.models.marker.MarkerModel;
import com.wynntils.models.marker.UserWaypointMarkerProvider;
import com.wynntils.models.territories.profile.TerritoryProfile;
import com.wynntils.utils.mc.type.Location;
import io.github.anilken.wynnlabs.game.GameState;
import io.github.anilken.wynnlabs.game.Wyn.Feature;
import io.github.anilken.wynnlabs.game.Wyn.MapLabel;
import io.github.anilken.wynnlabs.game.Wyn.Pins;
import io.github.anilken.wynnlabs.game.Wyn.PoiIcon;
import io.github.anilken.wynnlabs.model.Pin;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static io.github.anilken.wynnlabs.game.wynntils.Bridge.guard;
import static io.github.anilken.wynnlabs.game.wynntils.Bridge.run;
import static io.github.anilken.wynnlabs.game.wynntils.ContentReads.knownContent;

/**
 * Wynntils reads and drawing of the world: territories, waypoints, toasts, the map and its
 * points of interest.
 */
public final class WorldReads {

    private WorldReads() {}

    /** The name of the territory at this position, or null. */
    public static String territoryAt(Position pos) {
        return guard(Feature.WORLD, () -> {
            TerritoryProfile t = Models.Territory.getTerritoryProfileForPosition(pos);
            return t == null ? null : t.getName();
        }, null);
    }

    /** Text without formatting codes, trimmed; "" for null. */
    public static String plain(String s) {
        if (s == null) {
            return "";
        }
        String p = ChatFormatting.stripFormatting(s);
        return p == null ? "" : p.trim();
    }

    // ---- waypoints, notifications, events ----

    /** Wynntils' user waypoints (map, compass, beacon); null when unavailable. */
    public static Pins waypoints() {
        return guard(Feature.WAYPOINTS, () -> {
            UserWaypointMarkerProvider p = MarkerModel.USER_WAYPOINTS_PROVIDER;
            return p == null ? null : new Pins(p);
        }, null);
    }

    /** The pins of Wynntils' waypoint list behind {@link Pins}. */
    public static List<Pin> pins(UserWaypointMarkerProvider provider) {
        List<Pin> out = new ArrayList<>();
        provider.getMarkerInfos().forEach(m -> {
            Location l = m.location();
            // every user waypoint is named "Waypoint"; the label we gave is its additional text
            // (null when Wynntils' "show additional text in world" is off)
            String label = m.additionalText() != null ? m.additionalText() : m.name();
            out.add(new Pin(label, l.x(), l.y(), l.z()));
        });
        return out;
    }

    public static void removePin(UserWaypointMarkerProvider provider, Pin p) {
        provider.removeLocation(new Location(p.x(), p.y(), p.z()));
    }

    public static void addPin(UserWaypointMarkerProvider provider, int x, int y, int z, String label) {
        provider.addLocation(new Location(x, y, z), label);
    }

    /** A Wynntils toast; false when unavailable (the caller may use chat instead). */
    public static boolean toast(Component c) {
        return guard(Feature.NOTIFY, () -> {
            Managers.Notification.queueMessage(c);
            return true;
        }, false);
    }

    // ---- the quests tab's map: Wynntils' own map tiles and place names ----

    /**
     * Whether Wynntils has map tiles for the world box x0..x1, z0..z1. Wynntils downloads its map
     * at start-up into its cache; the player never has to open the map. False without Wynntils.
     */
    public static boolean hasMap(float x0, float z0, float x1, float z1) {
        return guard(Feature.WAYPOINTS, () -> !com.wynntils.core.components.Services.Map
                .getMapsForBoundingBox(new com.wynntils.utils.type.BoundingBox(x0, z0, x1, z1)).isEmpty(), false);
    }

    /**
     * Draws Wynntils' map tiles for the world box x0..x1, z0..z1 with its top left at (sx, sy) in the
     * current pose, {@code scale} gui units a block, through Wynntils' own renderer; false when none.
     */
    public static boolean drawMap(net.minecraft.client.gui.GuiGraphics g, float x0, float z0, float x1, float z1,
            float sx, float sy, float scale, int tint) {
        return guard(Feature.WAYPOINTS, () -> {
            var tiles = tilesFor(x0, z0, x1, z1);
            var colour = new com.wynntils.utils.colors.CustomColor((tint >> 16) & 255, (tint >> 8) & 255, tint & 255, 255);
            for (var t : tiles) {
                // the part of the tile inside the box, drawn tinted (the map's dark wash on the tiles themselves)
                float tx0 = Math.max(x0, t.getX1());
                float tz0 = Math.max(z0, t.getZ1());
                float tx1 = Math.min(x1, t.getX2() + 1f);
                float tz1 = Math.min(z1, t.getZ2() + 1f);
                if (tx0 >= tx1 || tz0 >= tz1) {
                    continue;
                }
                com.wynntils.utils.render.RenderUtils.drawTexturedRect(g, t.identifier(), colour,
                        sx + (tx0 - x0) * scale, sy + (tz0 - z0) * scale, (tx1 - tx0) * scale, (tz1 - tz0) * scale,
                        tx0 - t.getX1(), tz0 - t.getZ1(), tx1 - tx0, tz1 - tz0, t.getTextureWidth(), t.getTextureHeight());
            }
            return !tiles.isEmpty();
        }, false);
    }

    /** The view box the tile list below was asked for, and its tiles (only those crossing it). */
    private static float[] tilesBox;
    private static List<com.wynntils.services.map.MapTexture> tilesCache = List.of();

    /** The tiles crossing the box, asked of Wynntils again only when the view moves. */
    private static List<com.wynntils.services.map.MapTexture> tilesFor(float x0, float z0, float x1, float z1) {
        float[] box = {x0, z0, x1, z1};
        if (!java.util.Arrays.equals(box, tilesBox)) {
            tilesCache = List.copyOf(com.wynntils.core.components.Services.Map
                    .getMapsForBoundingBox(new com.wynntils.utils.type.BoundingBox(x0, z0, x1, z1)));
            tilesBox = box;
        }
        return tilesCache;
    }

    /** The world box all of Wynntils' map tiles cover {x0, z0, x1, z1}, or null without them. */
    public static float[] mapCoverage() {
        return guard(Feature.WAYPOINTS, () -> {
            var all = com.wynntils.core.components.Services.Map
                    .getMapsForBoundingBox(new com.wynntils.utils.type.BoundingBox(-100_000, -100_000, 100_000, 100_000));
            if (all.isEmpty()) {
                return null;
            }
            float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
            for (var t : all) {
                b[0] = Math.min(b[0], t.getX1());
                b[1] = Math.min(b[1], t.getZ1());
                b[2] = Math.max(b[2], t.getX2() + 1f);
                b[3] = Math.max(b[3], t.getZ2() + 1f);
            }
            return b;
        }, null);
    }

    /** Wynntils' place names inside the world box (provinces, cities, towns), largest first. */
    public static List<MapLabel> mapLabels(float x0, float z0, float x1, float z1) {
        return guard(Feature.WAYPOINTS, () -> com.wynntils.core.components.Services.Poi.getLabelPois()
                .map(p -> p.getLabel())
                .filter(l -> l.getX() >= x0 && l.getX() <= x1 && l.getZ() >= z0 && l.getZ() <= z1)
                .map(l -> new MapLabel(l.getName(), l.getX(), l.getZ(), l.getLayer().ordinal()))
                .sorted(java.util.Comparator.comparingInt(MapLabel::layer))
                .toList(), List.of());
    }

    // ---- the search tab: Wynntils' map and guide screens ----

    /** Opens Wynntils' full map centred on (x, z), in place of the open screen; false when unavailable. */
    public static boolean openMap(float x, float z) {
        return guard(Feature.WAYPOINTS, () -> {
            net.minecraft.client.Minecraft.getInstance().setScreen(com.wynntils.screens.maps.MainMapScreen.create(x, z));
            return true;
        }, false);
    }

    /**
     * Puts these places on Wynntils' map in place of the ones shown before (empty clears them):
     * our own marker provider, registered once, with one point of interest a mark. Map only: no
     * waypoint, beacon or compass. False when unavailable.
     */
    public static boolean showMarks(List<io.github.anilken.wynnlabs.search.MapMark> marks) {
        return guard(Feature.WAYPOINTS, () -> SearchMarks.show(marks), false);
    }

    private static final Map<String, java.util.Optional<PoiIcon>> POI = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The icon Wynntils' map shows for a kind of place: "service:blacksmith", "service:merchant:potion"
     * (a service's map id), "combat:dungeon", "combat:cave" (a content's), "texture:QUEST_ICON",
     * "texture:CHEST_T3". Null when Wynntils has none or is not running. Looked up once a key.
     */
    public static PoiIcon poiIcon(String key) {
        if (key == null) {
            return null;
        }
        java.util.Optional<PoiIcon> known = POI.get(key);
        if (known != null) {
            return known.orElse(null);
        }
        if (!GameState.current().running()) {
            return null;                        // asked again once the game runs
        }
        PoiIcon icon = guard(Feature.WAYPOINTS, () -> findPoi(key), null);
        POI.put(key, java.util.Optional.ofNullable(icon));
        return icon;
    }

    private static PoiIcon findPoi(String key) {
        int c = key.indexOf(':');
        String kind = key.substring(0, c);
        String id = key.substring(c + 1);
        com.wynntils.utils.render.Texture t = null;
        switch (kind) {
            case "service" -> {
                for (com.wynntils.services.map.type.ServiceKind k : com.wynntils.services.map.type.ServiceKind.values()) {
                    if (k.getMapDataId().equals(id)) {
                        t = k.getIcon();
                    }
                }
            }
            case "combat" -> {
                for (com.wynntils.services.map.type.CombatKind k : com.wynntils.services.map.type.CombatKind.values()) {
                    if (k.getMapDataId().equals(id)) {
                        t = k.getIcon();
                    }
                }
            }
            case "texture" -> {
                try {
                    t = com.wynntils.utils.render.Texture.valueOf(id);
                } catch (IllegalArgumentException e) {
                    t = null;
                }
            }
            default -> {
                // not a kind of ours
            }
        }
        return t == null || t.atlas() != null ? null : new PoiIcon(t.identifier(), t.width(), t.height());
    }

    /** The name of the item under the mouse on one of Wynntils' guide screens, or null. */
    public static String hoveredGuideItem(net.minecraft.client.gui.screens.Screen screen) {
        return guard(Feature.ITEMS, () -> {
            for (var child : screen.children()) {
                if (child instanceof com.wynntils.screens.guides.widgets.GuideButton b && b.isHovered()) {
                    try {
                        java.lang.reflect.Field f = com.wynntils.screens.guides.widgets.GuideButton.class.getDeclaredField("itemName");
                        f.setAccessible(true);
                        Object name = f.get(b);
                        return name == null ? null : plain(name.toString());
                    } catch (ReflectiveOperationException e) {
                        return null;                // the field moved: the key does nothing there
                    }
                }
            }
            return null;
        }, null);
    }

    /** Our places on Wynntils' map: a marker provider with points of interest only (no world markers). */
    private static final class SearchMarks
            implements com.wynntils.models.marker.type.MarkerProvider<com.wynntils.services.map.pois.Poi> {
        private static SearchMarks registered;
        private static volatile boolean broken;
        private volatile List<com.wynntils.services.map.pois.Poi> pois = List.of();

        static boolean show(List<io.github.anilken.wynnlabs.search.MapMark> marks) {
            if (registered == null) {
                if (marks.isEmpty()) {
                    return true;                    // nothing shown yet, nothing to clear
                }
                registered = new SearchMarks();
                Models.Marker.registerMarkerProvider(registered);
            }
            List<com.wynntils.services.map.pois.Poi> out = new ArrayList<>();
            for (io.github.anilken.wynnlabs.search.MapMark m : marks) {
                out.add(new MarkPoi(m, new com.wynntils.utils.mc.type.PoiLocation(m.x(), m.y(), m.z())));
            }
            registered.pois = List.copyOf(out);
            return true;
        }

        @Override
        public java.util.stream.Stream<com.wynntils.models.marker.type.MarkerInfo> getMarkerInfos() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public java.util.stream.Stream<com.wynntils.services.map.pois.Poi> getPois() {
            return pois.stream();
        }

        @Override
        public boolean isEnabled() {
            return !pois.isEmpty();
        }
    }

    /**
     * One mark as Wynntils' map draws it: the map hands us its place on screen and its pixels per
     * block ({@code zoomRenderScale}); {@link io.github.anilken.wynnlabs.ui.MapMarkPaint} draws. A
     * failure in our drawing turns the drawing off and never reaches Wynntils' screen.
     */
    private record MarkPoi(io.github.anilken.wynnlabs.search.MapMark mark, com.wynntils.utils.mc.type.PoiLocation location)
            implements com.wynntils.services.map.pois.Poi {

        @Override
        public com.wynntils.utils.mc.type.PoiLocation getLocation() {
            return location;
        }

        @Override
        public com.wynntils.services.map.type.DisplayPriority getDisplayPriority() {
            return mark.focus() ? com.wynntils.services.map.type.DisplayPriority.HIGH
                    : com.wynntils.services.map.type.DisplayPriority.NORMAL;
        }

        @Override
        public boolean hasStaticLocation() {
            return true;
        }

        @Override
        public void renderAt(net.minecraft.client.gui.GuiGraphics g, float x, float z, boolean hovered, float scale,
                float zoomRenderScale, float zoomLevel, boolean showLabels) {
            if (SearchMarks.broken) {
                return;
            }
            try {
                io.github.anilken.wynnlabs.ui.MapMarkPaint.draw(g, mark, x, z, hovered, zoomRenderScale);
            } catch (Throwable t) {
                SearchMarks.broken = true;          // the pins and the rest of the map keep working
                System.out.println("[WynnLabs] search marks on the map are off: " + t);
            }
        }

        @Override
        public int getWidth(float zoomRenderScale, float scale) {
            return io.github.anilken.wynnlabs.search.MapMark.sizePx(mark, zoomRenderScale);
        }

        @Override
        public int getHeight(float zoomRenderScale, float scale) {
            return io.github.anilken.wynnlabs.search.MapMark.sizePx(mark, zoomRenderScale);
        }

        @Override
        public String getName() {
            return mark.label();
        }
    }

    // ---- world events on Wynntils' main map ----

    public static boolean mainMapOpen() {
        return guard(Feature.WAYPOINTS,
                () -> net.minecraft.client.Minecraft.getInstance().screen instanceof com.wynntils.screens.maps.MainMapScreen, false);
    }

    /** Once a second: lets the world events ask what is due and hands the map what to draw. */
    public static void worldEventSync() {
        run(Feature.WAYPOINTS, () -> {
            long now = System.currentTimeMillis();
            boolean open = net.minecraft.client.Minecraft.getInstance().screen instanceof com.wynntils.screens.maps.MainMapScreen;
            io.github.anilken.wynnlabs.quests.WorldEvents.tick(open, now);
            // a "… World Event starts in 3m 59s!" in chat gives Wynntils the exact start in this world;
            // Wynntils forgets it once the event starts, so the start is kept on our side, map open or not
            for (var e : io.github.anilken.wynnlabs.quests.WorldEvents.all()) {
                var heard = Models.WorldEvent.getWorldEvent(e.name());
                long at = heard == null || heard.getStartTime() == null ? 0 : heard.getStartTime().timestamp();
                if (at > 0) {
                    io.github.anilken.wynnlabs.quests.WorldEvents.heard(e.name(), at);
                }
            }
            EventMarks.show(open ? io.github.anilken.wynnlabs.quests.WorldEvents.shown(now) : List.of());
        });
    }

    /** The world events on Wynntils' map: points of interest only (no world markers or beacons). */
    private static final class EventMarks
            implements com.wynntils.models.marker.type.MarkerProvider<com.wynntils.services.map.pois.Poi> {
        private static EventMarks registered;
        private static volatile boolean broken;
        private volatile List<com.wynntils.services.map.pois.Poi> pois = List.of();

        static void show(List<io.github.anilken.wynnlabs.quests.WorldEvents.Shown> shown) {
            if (registered == null) {
                if (shown.isEmpty()) {
                    return;
                }
                registered = new EventMarks();
                Models.Marker.registerMarkerProvider(registered);
            }
            List<com.wynntils.services.map.pois.Poi> out = new ArrayList<>();
            for (io.github.anilken.wynnlabs.quests.WorldEvents.Shown s : shown) {
                var e = s.event();
                out.add(new EventPoi(s, new com.wynntils.utils.mc.type.PoiLocation(e.x(), e.y(), e.z())));
            }
            registered.pois = List.copyOf(out);
        }

        @Override
        public java.util.stream.Stream<com.wynntils.models.marker.type.MarkerInfo> getMarkerInfos() {
            return java.util.stream.Stream.empty();
        }

        @Override
        public java.util.stream.Stream<com.wynntils.services.map.pois.Poi> getPois() {
            return pois.stream();
        }

        @Override
        public boolean isEnabled() {
            return !pois.isEmpty();
        }
    }

    /** One world event as Wynntils' map draws it: a blue area, the event icon, and its time above. */
    private record EventPoi(io.github.anilken.wynnlabs.quests.WorldEvents.Shown shown,
            com.wynntils.utils.mc.type.PoiLocation location) implements com.wynntils.services.map.pois.Poi {

        @Override
        public com.wynntils.utils.mc.type.PoiLocation getLocation() {
            return location;
        }

        @Override
        public com.wynntils.services.map.type.DisplayPriority getDisplayPriority() {
            return com.wynntils.services.map.type.DisplayPriority.HIGH;
        }

        @Override
        public boolean hasStaticLocation() {
            return true;
        }

        @Override
        public void renderAt(net.minecraft.client.gui.GuiGraphics g, float x, float z, boolean hovered, float scale,
                float zoomRenderScale, float zoomLevel, boolean showLabels) {
            if (EventMarks.broken) {
                return;
            }
            try {
                int r = io.github.anilken.wynnlabs.ui.WorldEventPaint.area(g, shown, x, z, hovered, zoomRenderScale);
                com.wynntils.utils.render.Texture icon = com.wynntils.utils.render.Texture.WORLD_EVENT_ICON;
                // the icon follows the zoom: at most its own size, at least 10 px
                float side = Math.max(10f, Math.min(Math.max(icon.width(), icon.height()), r * 1.6f));
                float k = side / Math.max(icon.width(), icon.height());
                float w = icon.width() * k;
                float h = icon.height() * k;
                com.wynntils.utils.render.RenderUtils.drawScalingTexturedRect(g, icon,
                        com.wynntils.utils.colors.CustomColor.fromARGBInt(0xFFFFFFFF), x - w / 2, z - h / 2, w, h);
                io.github.anilken.wynnlabs.ui.WorldEventPaint.labels(g, shown, x, z - Math.max(r, h / 2), hovered,
                        System.currentTimeMillis());
            } catch (Throwable t) {
                EventMarks.broken = true;           // the rest of the map keeps working
                System.out.println("[WynnLabs] world events on the map are off: " + t);
            }
        }

        @Override
        public int getWidth(float zoomRenderScale, float scale) {
            return Math.max(20, 2 * io.github.anilken.wynnlabs.ui.WorldEventPaint.radiusPx(shown, zoomRenderScale));
        }

        @Override
        public int getHeight(float zoomRenderScale, float scale) {
            return getWidth(zoomRenderScale, scale);
        }

        @Override
        public String getName() {
            return shown.event().name();
        }
    }

    // ---- finished caves red and dim on Wynntils' map ----

    /**
     * Brings Wynntils' map in line with the "fade finished caves" setting and the content book:
     * registers our attribute override for the cave category while the setting is on, drops it
     * when off, and re-reads which caves are done. Called once a second and when Wynntils rereads
     * the caves; cheap when nothing changed. Rendering only: no waypoint, marker or packet.
     */
    public static void caveMapSync() {
        boolean on = io.github.anilken.wynnlabs.core.Settings.on(io.github.anilken.wynnlabs.quests.CaveMap.SETTING);
        run(Feature.WAYPOINTS, () -> {
            try {
                DoneCaves.sync(on);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Wynntils' cave icons are not where expected", e);
            }
        });
    }

    /**
     * Wynntils' map draws caves from its own icon list ({@code PoiService}), not from the map-data
     * attributes, and paints the hover label a fixed green. So a finished cave's icon is swapped
     * for {@link Done}, a copy that draws a white faded icon and a white label; the list is put back
     * when the cave is no longer finished or the setting goes off. Rendering only: no waypoint,
     * marker or packet. Like {@link SearchMarks}, every line that hands Wynntils one of our
     * objects is in here, so this class is only loaded (and verified against Wynntils) in a
     * running game.
     */
    private static final class DoneCaves {
        private static java.lang.reflect.Field field;

        /** Draws the finished caves faded (or all as Wynntils does when {@code on} is false). */
        static void sync(boolean on) throws ReflectiveOperationException {
            java.util.Set<String> done = on
                    ? io.github.anilken.wynnlabs.quests.CaveMap.doneNames(
                            io.github.anilken.wynnlabs.quests.Content.bookLines(io.github.anilken.wynnlabs.quests.ContentType.CAVE),
                            knownContent("CAVE"))
                    : java.util.Set.of();
            if (field == null) {
                field = com.wynntils.services.map.PoiService.class.getDeclaredField("cavePois");
                field.setAccessible(true);
            }
            @SuppressWarnings("unchecked")
            java.util.Set<com.wynntils.services.map.pois.CombatPoi> now =
                    (java.util.Set<com.wynntils.services.map.pois.CombatPoi>) field.get(com.wynntils.core.components.Services.Poi);
            java.util.Set<com.wynntils.services.map.pois.CombatPoi> next = new java.util.LinkedHashSet<>();
            int changed = 0;
            int faded = 0;
            for (com.wynntils.services.map.pois.CombatPoi p : now) {
                Done d = p instanceof Done x ? x : null;
                com.wynntils.services.map.pois.CombatPoi base = d != null ? d.original : p;
                boolean hit = io.github.anilken.wynnlabs.quests.CaveMap.isDone(base.getName(), done);
                if (hit && d == null) {
                    next.add(new Done(p));
                    changed++;
                } else if (!hit && d != null) {
                    next.add(base);
                    changed++;
                } else {
                    next.add(p);
                }
                faded += hit ? 1 : 0;
            }
            if (changed > 0) {
                field.set(com.wynntils.core.components.Services.Poi, java.util.Collections.unmodifiableSet(next));
                System.out.println("[WynnLabs] done caves: " + faded + " of " + next.size() + " cave icons drawn faded");
            }
        }

        /** A finished cave's icon: Wynntils' own, tinted white and faded, with a white hover label. */
        private static final class Done extends com.wynntils.services.map.pois.CombatPoi {
            final com.wynntils.services.map.pois.CombatPoi original;

            Done(com.wynntils.services.map.pois.CombatPoi original) {
                super(original.getLocation(), original.getName(), original.getKind());
                this.original = original;
            }

            private static final float FADE = 0.6f;
            private static java.lang.reflect.Method alpha;

            /** Wynntils' own zoom alpha for an icon (1 when it needs no fading). */
            private float zoomAlpha(float zoomLevel) {
                try {
                    if (alpha == null) {
                        alpha = com.wynntils.services.map.pois.IconPoi.class.getDeclaredMethod("getIconAlpha", float.class);
                        alpha.setAccessible(true);
                    }
                    return (Float) alpha.invoke(this, zoomLevel);
                } catch (ReflectiveOperationException e) {
                    return 1f;
                }
            }

            @Override
            protected com.wynntils.utils.colors.CustomColor getIconColor() {
                return com.wynntils.utils.colors.CustomColor.fromARGBInt(io.github.anilken.wynnlabs.quests.CaveMap.ICON_ARGB);
            }

            @Override
            public void renderAt(net.minecraft.client.gui.GuiGraphics graphics, float x, float y, boolean hovered,
                    float scale, float zoom, float zoomLevel, boolean flag) {
                // Wynntils sets the icon's alpha from the zoom on every frame, so the fade is drawn
                // here: its own alpha (the private getIconAlpha) times FADE
                float s = hovered ? scale * 1.05f : scale;
                com.wynntils.utils.render.Texture icon = getIcon();
                float w = icon.width() * s;
                float h = icon.height() * s;
                com.wynntils.utils.render.RenderUtils.drawScalingTexturedRect(graphics, icon,
                        getIconColor().withAlpha(zoomAlpha(zoomLevel) * FADE), x - w / 2, y - h / 2, w, h);
                if (hovered) {
                    com.wynntils.utils.render.FontRenderer.getInstance().renderText(graphics,
                            com.wynntils.core.text.StyledText.fromString(getName()), x, 15.0f + y,
                            com.wynntils.utils.colors.CustomColor.fromARGBInt(io.github.anilken.wynnlabs.quests.CaveMap.LABEL_ARGB),
                            com.wynntils.utils.render.type.HorizontalAlignment.CENTER,
                            com.wynntils.utils.render.type.VerticalAlignment.MIDDLE,
                            com.wynntils.utils.render.type.TextShadow.OUTLINE);
                }
            }
        }
    }
}
