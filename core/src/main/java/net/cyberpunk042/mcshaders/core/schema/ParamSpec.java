package net.cyberpunk042.mcshaders.core.schema;

import java.util.List;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;

/**
 * How one parameter should be presented for editing.
 *
 * <p>The engine already knows what a parameter <em>is</em>
 * ({@link net.cyberpunk042.mcshaders.core.param.ParamValue}) and what an effect
 * holds ({@link net.cyberpunk042.mcshaders.core.param.EffectParams}). This adds
 * what an editor needs on top: a name a person recognises, a control that suits
 * the value, limits, a group to sit in, and something to say on hover.
 *
 * <p>It carries no toolkit types, so the same schema drives a screen in the game,
 * a web page, or a command line. That is the point: the description of what is
 * tunable belongs with the effect, not with one front end.
 *
 * @param key      the key this edits in an {@code EffectParams}
 * @param label    what to call it in front of a person
 * @param control  the kind of control to offer
 * @param bounds   numeric limits, or {@link Bounds#NONE} for unbounded controls
 * @param fallback the value to start from, and to reset to
 * @param group    the section it belongs in
 * @param tooltip  a sentence of explanation, or null
 * @param choices  the options, for {@link ControlKind#CHOICE}; empty otherwise
 */
@Stable(since = "0.5.0")
public record ParamSpec(String key, String label, ControlKind control, Bounds bounds,
                        ParamValue fallback, String group, String tooltip, List<String> choices) {

    public ParamSpec {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("a parameter spec needs a key");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("parameter '" + key + "' needs a label");
        }
        if (control == null) {
            throw new IllegalArgumentException("parameter '" + key + "' needs a control kind");
        }
        if (bounds == null) {
            throw new IllegalArgumentException("parameter '" + key + "' needs bounds; use Bounds.NONE");
        }
        choices = List.copyOf(choices);

        // A default the control cannot hold gives an editor that looks correct and
        // cannot round-trip its own starting value.
        if (fallback != null && !control.accepts(fallback)) {
            throw new IllegalArgumentException("parameter '" + key + "' is a " + control
                    + " but its default is a " + fallback.getClass().getSimpleName());
        }
        if (control == ControlKind.CHOICE && choices.isEmpty()) {
            throw new IllegalArgumentException("parameter '" + key + "' is a CHOICE with no options");
        }
        if (control == ControlKind.CHOICE && fallback instanceof ParamValue.Text text
                && !choices.contains(text.value())) {
            throw new IllegalArgumentException("parameter '" + key + "' defaults to '" + text.value()
                    + "', which is not one of its options " + choices);
        }
    }

    // ── factories, one per control kind, so a spec cannot be half-built ──────────

    /** A continuous slider over one of the project's named ranges. */
    public static ParamSpec slider(String key, String label, ValueRange range, double fallback, String group) {
        return new ParamSpec(key, label, ControlKind.SLIDER, Bounds.of(range),
                new ParamValue.Scalar(fallback), group, null, List.of());
    }

    /** A continuous slider over explicit bounds. */
    public static ParamSpec slider(String key, String label, double min, double max,
                                   double fallback, String group) {
        return new ParamSpec(key, label, ControlKind.SLIDER, Bounds.between(min, max),
                new ParamValue.Scalar(fallback), group, null, List.of());
    }

    public static ParamSpec intSlider(String key, String label, int min, int max, int fallback, String group) {
        return new ParamSpec(key, label, ControlKind.INT_SLIDER, Bounds.stepped(min, max, 1),
                new ParamValue.Scalar(fallback), group, null, List.of());
    }

    public static ParamSpec toggle(String key, String label, boolean fallback, String group) {
        return new ParamSpec(key, label, ControlKind.TOGGLE, Bounds.NONE,
                new ParamValue.Flag(fallback), group, null, List.of());
    }

    /** A colour, edited as one control rather than as its components. */
    public static ParamSpec color(String key, String label, ParamValue.Rgba fallback, String group) {
        return new ParamSpec(key, label, ControlKind.COLOR, Bounds.NONE, fallback, group, null, List.of());
    }

    public static ParamSpec vector(String key, String label, ParamValue.Vec3 fallback, String group) {
        return new ParamSpec(key, label, ControlKind.VECTOR, Bounds.NONE, fallback, group, null, List.of());
    }

    public static ParamSpec choice(String key, String label, List<String> options,
                                   String fallback, String group) {
        return new ParamSpec(key, label, ControlKind.CHOICE, Bounds.NONE,
                new ParamValue.Text(fallback), group, null, options);
    }

    // ── derivations ─────────────────────────────────────────────────────────────

    /** The same parameter under a different name — the same knob means different things per effect. */
    public ParamSpec withLabel(String newLabel) {
        return new ParamSpec(key, newLabel, control, bounds, fallback, group, tooltip, choices);
    }

    public ParamSpec withGroup(String newGroup) {
        return new ParamSpec(key, label, control, bounds, fallback, newGroup, tooltip, choices);
    }

    public ParamSpec withTooltip(String newTooltip) {
        return new ParamSpec(key, label, control, bounds, fallback, group, newTooltip, choices);
    }

    public ParamSpec withBounds(Bounds newBounds) {
        return new ParamSpec(key, label, control, newBounds, fallback, group, tooltip, choices);
    }

    public ParamSpec withFallback(ParamValue newFallback) {
        return new ParamSpec(key, label, control, bounds, newFallback, group, tooltip, choices);
    }

    public Optional<String> tooltipIfAny() {
        return Optional.ofNullable(tooltip);
    }

    /**
     * Brings a value within what this spec permits.
     *
     * <p>Numeric values are clamped and snapped; a choice outside its options falls
     * back to the default; anything of the wrong shape for the control is replaced
     * by the default rather than passed through.
     */
    public ParamValue coerce(ParamValue value) {
        if (value == null || !control.accepts(value)) {
            return fallback;
        }
        if (control.isBounded() && value instanceof ParamValue.Scalar scalar) {
            return new ParamValue.Scalar(bounds.coerce(scalar.value()));
        }
        if (control == ControlKind.CHOICE && value instanceof ParamValue.Text text
                && !choices.contains(text.value())) {
            return fallback;
        }
        return value;
    }
}
