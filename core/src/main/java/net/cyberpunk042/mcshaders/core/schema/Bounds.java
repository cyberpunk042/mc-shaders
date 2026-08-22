package net.cyberpunk042.mcshaders.core.schema;

import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.util.MathSupport;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;

/**
 * The numeric limits of a bounded control, and the unit its value is in.
 *
 * <p>Prefer {@link #of(ValueRange)}: the project already has a vocabulary of named
 * ranges, and a schema that restates {@code 0f, 1f} by hand is one that drifts
 * from it silently.
 *
 * @param min  lowest permitted value
 * @param max  highest permitted value
 * @param step increment an editor should snap to; 0 for continuous
 * @param unit suffix to show after the number, or null
 */
@Stable(since = "0.5.0")
public record Bounds(double min, double max, double step, String unit) {

    /** For controls that are not numeric. */
    public static final Bounds NONE = new Bounds(0, 0, 0, null);

    public Bounds {
        if (max < min) {
            throw new IllegalArgumentException("max " + max + " is below min " + min);
        }
        if (step < 0) {
            throw new IllegalArgumentException("step cannot be negative: " + step);
        }
    }

    /** Bounds taken from one of the project's named ranges, unit included. */
    public static Bounds of(ValueRange range) {
        return new Bounds(range.min(), range.max(), 0, range.unit());
    }

    /** Continuous bounds with no unit. */
    public static Bounds between(double min, double max) {
        return new Bounds(min, max, 0, null);
    }

    /** Bounds an editor should snap to multiples of. */
    public static Bounds stepped(double min, double max, double step) {
        return new Bounds(min, max, step, null);
    }

    public Bounds withUnit(String newUnit) {
        return new Bounds(min, max, step, newUnit);
    }

    public Optional<String> unitIfAny() {
        return Optional.ofNullable(unit);
    }

    public boolean isContinuous() {
        return step == 0;
    }

    /** Whether {@code value} is inside these bounds. */
    public boolean contains(double value) {
        return value >= min && value <= max;
    }

    /**
     * Brings {@code value} inside the bounds, snapping to {@link #step} if there is
     * one.
     *
     * <p>Snapping happens before clamping so that a step which does not divide the
     * range evenly cannot push a value past {@link #max}.
     */
    public double coerce(double value) {
        double snapped = value;
        if (step > 0) {
            snapped = min + Math.round((value - min) / step) * step;
        }
        return MathSupport.clamp(snapped, min, max);
    }
}
