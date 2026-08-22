package net.cyberpunk042.mcshaders.core.schema;

import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.param.ParamValue;

/**
 * The kind of control an editor should offer for a parameter.
 *
 * <p>This says what the value <em>is</em> to a person editing it, not how any
 * particular toolkit draws it. A colour is one control here even though every UI
 * ends up drawing it as several widgets, because presenting it as four unrelated
 * sliders is the thing that makes a panel unusable.
 */
@Stable(since = "0.5.0")
public enum ControlKind {

    /** Continuous value between bounds. */
    SLIDER,

    /** Whole-numbered value between bounds. */
    INT_SLIDER,

    /** On or off. */
    TOGGLE,

    /** A colour, edited as one thing. */
    COLOR,

    /** Three components edited together — a position, a direction, a scale. */
    VECTOR,

    /** One of a fixed set of named options. */
    CHOICE,

    /** Shown, never edited. */
    LABEL;

    /** Whether this control is driven by numeric bounds. */
    public boolean isBounded() {
        return this == SLIDER || this == INT_SLIDER;
    }

    /**
     * Whether {@code value} is a sensible thing for this control to hold.
     *
     * <p>Used to reject a spec whose default cannot be what its control edits — a
     * {@code COLOR} defaulting to a scalar, say. Left unchecked that produces an
     * editor that looks right and cannot round-trip its own default.
     */
    public boolean accepts(ParamValue value) {
        return switch (this) {
            case SLIDER, INT_SLIDER -> value instanceof ParamValue.Scalar;
            case TOGGLE -> value instanceof ParamValue.Flag;
            case COLOR -> value instanceof ParamValue.Rgba;
            case VECTOR -> value instanceof ParamValue.Vec3;
            case CHOICE, LABEL -> value instanceof ParamValue.Text;
        };
    }
}
