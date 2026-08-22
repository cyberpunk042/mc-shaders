package net.cyberpunk042.mcshaders.core.util;

import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * The handful of maths primitives Minecraft's {@code MathHelper} was providing.
 *
 * <p>The ported visual model leaned on Minecraft for clamping, interpolation and
 * spline evaluation. None of that is Minecraft-specific, and {@code core} cannot
 * depend on Minecraft, so it lives here instead.
 *
 * <p>These are reimplementations, not copies, which is why they are tested rather
 * than trusted — {@link #catmullRom} in particular is real maths that has to match
 * the curve the original produced, or animation paths change shape.
 */
@Stable(since = "0.3.0")
public final class MathSupport {

    private MathSupport() {
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }

    public static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    public static int clamp(int value, int min, int max) {
        return value < min ? min : Math.min(value, max);
    }

    /** Linear interpolation. {@code t} is not clamped; callers that need that clamp first. */
    public static float lerp(float t, float from, float to) {
        return from + t * (to - from);
    }

    public static double lerp(double t, double from, double to) {
        return from + t * (to - from);
    }

    /**
     * Maps a value from one range onto another, clamped to the destination range.
     *
     * <p>Unclamped mapping is the more common primitive, but the ported model relies
     * on the clamped form: a parameter driven past its authored range should hold at
     * the limit rather than continue extrapolating into nonsense.
     */
    public static float clampedMap(float value, float fromStart, float fromEnd,
                                   float toStart, float toEnd) {
        if (fromEnd == fromStart) {
            // A zero-width source range has no meaningful position within it.
            return toStart;
        }
        float t = (value - fromStart) / (fromEnd - fromStart);
        return clamp(lerp(t, toStart, toEnd), Math.min(toStart, toEnd), Math.max(toStart, toEnd));
    }

    /** Double-precision {@link #clampedMap(float, float, float, float, float)}. */
    public static double clampedMap(double value, double fromStart, double fromEnd,
                                    double toStart, double toEnd) {
        if (fromEnd == fromStart) {
            return toStart;
        }
        double t = (value - fromStart) / (fromEnd - fromStart);
        return clamp(lerp(t, toStart, toEnd), Math.min(toStart, toEnd), Math.max(toStart, toEnd));
    }

    /** Whether two floats are equal within the tolerance the original used. */
    public static boolean approximatelyEquals(float a, float b) {
        return Math.abs(b - a) < 1.0E-5f;
    }

    /**
     * Evaluates a Catmull-Rom spline segment between {@code p1} and {@code p2}.
     *
     * <p>{@code p0} and {@code p3} are the neighbouring control points that set the
     * tangents, which is what makes a path through a series of points smooth rather
     * than kinked at each one.
     *
     * <p>This is the standard uniform (alpha = 0) formulation, matching what the
     * original used:
     * <pre>
     * 0.5 * ( 2p1 + (p2 - p0)t + (2p0 - 5p1 + 4p2 - p3)t² + (3p1 - 3p2 + p3 - p0)t³ )
     * </pre>
     *
     * @param t progress within the segment, normally in {@code [0, 1]}
     */
    public static float catmullRom(float t, float p0, float p1, float p2, float p3) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5f * (
                (2.0f * p1)
                        + (p2 - p0) * t
                        + (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2
                        + (3.0f * p1 - 3.0f * p2 + p3 - p0) * t3);
    }
}
