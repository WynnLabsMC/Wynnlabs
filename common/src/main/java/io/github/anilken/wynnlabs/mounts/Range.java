package io.github.anilken.wynnlabs.mounts;

/** An inclusive low..high estimate. Breeding results are always ranges, never one number. */
public record Range(int low, int high) {
    public Range {
        if (high < low) {
            int t = low;
            low = high;
            high = t;
        }
    }

    public static Range of(int v) {
        return new Range(v, v);
    }

    public int mid() {
        return Math.floorDiv(low + high, 2);
    }

    public Range plus(Range o) {
        return new Range(low + o.low, high + o.high);
    }

    public Range times(int k) {
        return new Range(low * k, high * k);
    }

    public Range clamp(int lo, int hi) {
        return new Range(Math.max(lo, Math.min(hi, low)), Math.max(lo, Math.min(hi, high)));
    }
}
