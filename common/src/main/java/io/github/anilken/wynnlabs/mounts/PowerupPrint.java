package io.github.anilken.wynnlabs.mounts;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * A powerup's fingerprint: the lowercase hex SHA-256 of its sprite's pixels, each an ARGB int
 * written big-endian, row by row from the top left. The table in {@code powerups.json} is keyed by
 * it. Pure.
 */
public final class PowerupPrint {

    /** The pixel at x, y as ARGB. */
    @FunctionalInterface
    public interface Pixels {
        int argb(int x, int y);
    }

    private PowerupPrint() {}

    public static String of(int width, int height, Pixels pixels) {
        MessageDigest sha;
        try {
            sha = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] row = new byte[width * 4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int p = pixels.argb(x, y);
                row[x * 4] = (byte) (p >>> 24);
                row[x * 4 + 1] = (byte) (p >>> 16);
                row[x * 4 + 2] = (byte) (p >>> 8);
                row[x * 4 + 3] = (byte) p;
            }
            sha.update(row);
        }
        return HexFormat.of().formatHex(sha.digest());
    }
}
