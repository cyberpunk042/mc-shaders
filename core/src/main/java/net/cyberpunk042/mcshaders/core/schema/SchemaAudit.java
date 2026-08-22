package net.cyberpunk042.mcshaders.core.schema;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.layout.LayoutMismatch;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;

/**
 * Checks a schema against the parameters an effect actually carries.
 *
 * <p>These are two declarations of one thing — what an effect is tunable by — and
 * nothing keeps them in step. The failures are quiet in the way this project keeps
 * finding: a parameter the effect reads but no control reaches is simply
 * un-editable, and a control bound to a key the effect never reads does nothing
 * when dragged. Neither raises anything.
 *
 * <p>The third finding is the sharpest. An effect whose shipped default sits
 * outside the range its own schema declares will have that value silently changed
 * the first time someone opens the panel and closes it again — a fresh install and
 * an edited one then render differently, from a control nobody touched.
 */
@Stable(since = "0.5.0")
public final class SchemaAudit {

    private SchemaAudit() {
    }

    /**
     * Compares {@code schema} against the parameters {@code effect} carries.
     *
     * @param schema the description of what should be tunable
     * @param effect the parameters the effect actually holds — normally its defaults
     * @return the disagreements, schema order first, then whatever the effect has
     *         left over
     */
    public static List<SchemaProblem> audit(EffectSchema schema, EffectParams effect) {
        List<SchemaProblem> out = new ArrayList<>();

        for (ParamSpec spec : schema.parameters()) {
            Optional<ParamValue> held = effect.get(spec.key());
            if (held.isEmpty()) {
                out.add(new SchemaProblem(SchemaProblem.Kind.UNBACKED, LayoutMismatch.Severity.WARNING,
                        spec.key(), "the schema offers a control for '" + spec.label()
                                + "' but the effect carries no such parameter, so editing it does nothing"));
                continue;
            }
            ParamValue value = held.get();
            if (!spec.control().accepts(value)) {
                out.add(new SchemaProblem(SchemaProblem.Kind.SHAPE_MISMATCH, LayoutMismatch.Severity.ERROR,
                        spec.key(), "the effect holds a " + describe(value) + " but the schema edits it as a "
                                + spec.control()));
                continue;
            }
            if (!spec.coerce(value).equals(value)) {
                out.add(new SchemaProblem(SchemaProblem.Kind.DEFAULT_OUT_OF_RANGE,
                        LayoutMismatch.Severity.WARNING, spec.key(),
                        "the effect ships " + describe(value) + ", which its own editor would change to "
                                + describe(spec.coerce(value)) + " on first open"));
            }
        }

        for (String key : effect.keys()) {
            if (!schema.describes(key)) {
                out.add(new SchemaProblem(SchemaProblem.Kind.UNREACHABLE, LayoutMismatch.Severity.INFO,
                        key, "the effect carries this parameter but no control reaches it"));
            }
        }
        return List.copyOf(out);
    }

    /** Whether the schema and the effect describe the same set of knobs. */
    public static boolean agree(EffectSchema schema, EffectParams effect) {
        return audit(schema, effect).stream()
                .noneMatch(p -> p.severity() != LayoutMismatch.Severity.INFO);
    }

    private static String describe(ParamValue value) {
        return switch (value) {
            case ParamValue.Scalar s -> String.valueOf(s.value());
            case ParamValue.Flag f -> String.valueOf(f.value());
            case ParamValue.Text t -> "'" + t.value() + "'";
            case ParamValue.Vec3 v -> "(" + v.x() + ", " + v.y() + ", " + v.z() + ")";
            case ParamValue.Rgba c -> "colour";
        };
    }
}
