package net.cyberpunk042.mcshaders.core.param;

/** Shared numeric helpers for parameter blending. */
public final class Interpolation {

    private Interpolation() {
    }

    /** Clamps {@code t} into {@code [0, 1]}, mapping NaN to 0. */
    public static double clamp01(double t) {
        if (Double.isNaN(t)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, t));
    }

    /** Linear blend of two numbers with {@code t} clamped to {@code [0, 1]}. */
    public static double mix(double from, double to, double t) {
        double c = clamp01(t);
        return from + (to - from) * c;
    }

    /**
     * Fallback for values that cannot be meaningfully blended: hold the source
     * until halfway, then switch to the destination.
     */
    public static ParamValue step(ParamValue from, ParamValue to, double t) {
        return clamp01(t) < 0.5 ? from : to;
    }
}
