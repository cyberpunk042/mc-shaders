package net.cyberpunk042.mcshaders.core.schema;

import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * Converts between a parameter's own range and the 0–1 a slider works in.
 *
 * <p>Slider widgets carry a normalised position and know nothing about what it means;
 * a {@link ParamSpec} carries a range and knows nothing about pixels. This is the
 * arithmetic between them, and it lives here rather than in the screen for one
 * reason: a screen cannot be run in a test, and every failure mode below is a wrong
 * number rather than a crash.
 *
 * <ul>
 *   <li>A zero-width range divides by zero. {@link Bounds#NONE} is exactly that, and
 *       it is the default for controls that have no range at all.</li>
 *   <li>A position slightly outside 0–1 — which a drag to the edge can produce —
 *       maps outside the range and gets written back as an out-of-bounds value.</li>
 *   <li>A stepped range has to snap, and snapping the position rather than the value
 *       puts the handle where the user let go instead of where the value now is.</li>
 * </ul>
 *
 * <p>None of those would throw. They would produce a control that looks like it works
 * and sets the wrong number.
 */
@Stable(since = "0.6.0")
public final class SliderScale {

    private SliderScale() {
    }

    /**
     * The 0–1 position representing {@code value} within {@code bounds}.
     *
     * @return a position in [0, 1]; 0 for a range with no width, since there is no
     *         position within it that means anything
     */
    public static double toPosition(Bounds bounds, double value) {
        double span = bounds.max() - bounds.min();
        if (span <= 0) {
            return 0;
        }
        return clamp01((value - bounds.min()) / span);
    }

    /**
     * The value {@code position} represents within {@code bounds}, snapped to the
     * bounds' step if it has one.
     *
     * @param position a slider position, clamped to [0, 1] before use
     * @return a value {@link Bounds#contains} accepts
     */
    public static double toValue(Bounds bounds, double position) {
        double span = bounds.max() - bounds.min();
        if (span <= 0) {
            return bounds.min();
        }
        double raw = bounds.min() + clamp01(position) * span;
        return bounds.coerce(snap(bounds, raw));
    }

    /**
     * {@code value} rounded to the nearest step, or unchanged on a continuous range.
     *
     * <p>Snapping is relative to {@code min} rather than to zero: a range of 1–10 in
     * steps of 2 offers 1, 3, 5, 7, 9 — not 2, 4, 6, 8, 10, which would leave the
     * minimum unreachable.
     */
    public static double snap(Bounds bounds, double value) {
        if (bounds.isContinuous() || bounds.step() <= 0) {
            return value;
        }
        double steps = Math.round((value - bounds.min()) / bounds.step());
        return bounds.min() + steps * bounds.step();
    }

    private static double clamp01(double v) {
        return v < 0 ? 0 : v > 1 ? 1 : v;
    }
}
