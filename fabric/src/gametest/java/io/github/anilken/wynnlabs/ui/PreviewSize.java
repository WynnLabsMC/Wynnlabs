package io.github.anilken.wynnlabs.ui;

/**
 * The preview window's size: by default what the user plays at (3440x1440 on GUI scale 3, about
 * 1146x480 logical, the menu's large layout); {@code -PpreviewSize=1920x1080} for another (that one
 * shows the small layout).
 */
final class PreviewSize {

    static final int WIDTH;
    static final int HEIGHT;

    static {
        String s = System.getProperty("wynnlabs.previewSize", "3440x1440");
        int w = 3440;
        int h = 1440;
        try {
            String[] p = s.toLowerCase(java.util.Locale.ROOT).split("x");
            w = Integer.parseInt(p[0].trim());
            h = Integer.parseInt(p[1].trim());
        } catch (RuntimeException e) {
            System.out.println("[preview] bad previewSize '" + s + "', using 3440x1440");
        }
        WIDTH = w;
        HEIGHT = h;
    }

    private PreviewSize() {}
}
