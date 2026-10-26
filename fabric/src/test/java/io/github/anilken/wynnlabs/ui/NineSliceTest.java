package io.github.anilken.wynnlabs.ui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Every nine-sliced GUI sprite leaves a centre slice: the game drops one that doesn't and draws the missing texture. */
class NineSliceTest {

    @Test
    void everyNineSliceHasACentre() throws Exception {
        Path root = Path.of(NineSliceTest.class.getResource("/assets/wynnlabs/textures/gui/sprites/kit").toURI()).getParent();
        List<String> bad = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".png.mcmeta")).toList()) {
                JsonObject gui = JsonParser.parseString(Files.readString(f)).getAsJsonObject().getAsJsonObject("gui");
                JsonObject s = gui == null ? null : gui.getAsJsonObject("scaling");
                if (s == null || !"nine_slice".equals(s.get("type").getAsString())) {
                    continue;
                }
                int w = s.get("width").getAsInt();
                int h = s.get("height").getAsInt();
                int l;
                int t;
                int r;
                int b;
                if (s.get("border").isJsonPrimitive()) {
                    l = t = r = b = s.get("border").getAsInt();
                } else {
                    JsonObject o = s.getAsJsonObject("border");
                    l = o.get("left").getAsInt();
                    t = o.get("top").getAsInt();
                    r = o.get("right").getAsInt();
                    b = o.get("bottom").getAsInt();
                }
                if (l + r >= w || t + b >= h) {
                    bad.add(root.relativize(f) + ": " + l + "+" + r + " of " + w + ", " + t + "+" + b + " of " + h);
                }
            }
        }
        assertEquals(List.of(), bad);
    }
}
