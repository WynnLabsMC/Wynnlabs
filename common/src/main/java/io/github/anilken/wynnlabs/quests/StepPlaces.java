package io.github.anilken.wynnlabs.quests;

import java.util.ArrayList;
import java.util.List;

/**
 * Every place a discovery's steps name: each coordinate group in a step's text ("-195, 80, -642 ya
 * da -187, 76, -731" is two) and the step's own place when the text does not show it. Alternatives
 * in one step share its number with a letter ("1a", "1b"); a step with one place keeps its number.
 */
public final class StepPlaces {

    /** A place of a step: its label, where ({@code at[1]} the discovery's height when unknown), and whether the text shows it. */
    public record Mark(String label, int[] at, boolean inText, int step) {}

    private StepPlaces() {}

    /** Each step's places, in step order; {@code home} is the discovery's own block (for heights the text leaves out). */
    public static List<List<Mark>> of(List<Discoveries.Step> steps, int[] home) {
        List<List<Mark>> out = new ArrayList<>();
        for (int i = 0; i < steps.size(); i++) {
            Discoveries.Step s = steps.get(i);
            int[] data = s.at() == null ? null
                    : new int[] {s.at()[0], s.at()[1] == DiscoveryWiki.NO_Y ? home[1] : s.at()[1], s.at()[2]};
            List<int[]> found = new ArrayList<>();
            for (GuideCoords.Spot sp : GuideCoords.find(s.text(), data == null ? List.of() : List.of(data))) {
                found.add(new int[] {sp.at()[0], sp.knowsY() ? sp.at()[1] : data != null ? data[1] : home[1], sp.at()[2], 1});
            }
            if (data != null && found.stream().noneMatch(f -> Math.abs(f[0] - data[0]) <= 2 && Math.abs(f[2] - data[2]) <= 2)) {
                found.add(new int[] {data[0], data[1], data[2], 0});
            }
            List<Mark> marks = new ArrayList<>();
            for (int k = 0; k < found.size(); k++) {
                int[] f = found.get(k);
                String label = (i + 1) + (found.size() > 1 ? String.valueOf((char) ('a' + k)) : "");
                marks.add(new Mark(label, new int[] {f[0], f[1], f[2]}, f[3] == 1, i));
            }
            out.add(marks);
        }
        return out;
    }

    /** The places from step {@code from} on, in order (for the compass queue), with their labels. */
    public static void from(List<List<Mark>> marks, int from, List<int[]> points, List<String> labels, String name) {
        for (int i = Math.max(0, from); i < marks.size(); i++) {
            for (Mark m : marks.get(i)) {
                points.add(m.at());
                labels.add(name + " " + m.label());
            }
        }
    }

    /** How many places all the steps name. */
    public static int count(List<List<Mark>> marks) {
        int n = 0;
        for (List<Mark> m : marks) {
            n += m.size();
        }
        return n;
    }
}
