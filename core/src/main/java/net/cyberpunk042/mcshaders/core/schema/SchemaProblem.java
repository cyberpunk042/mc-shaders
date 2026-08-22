package net.cyberpunk042.mcshaders.core.schema;

import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.layout.LayoutMismatch;

/**
 * A disagreement between a schema and the parameters it claims to describe.
 *
 * @param kind     what sort of disagreement
 * @param severity how much it matters
 * @param key      the parameter it concerns
 * @param detail   what is wrong, in a sentence
 */
@Stable(since = "0.5.0")
public record SchemaProblem(Kind kind, LayoutMismatch.Severity severity, String key, String detail) {

    public enum Kind {
        /** The effect has a parameter no control reaches. */
        UNREACHABLE,
        /** The schema describes a parameter the effect does not carry. */
        UNBACKED,
        /** The effect ships a default its own editor would refuse. */
        DEFAULT_OUT_OF_RANGE,
        /** The effect's value is a different shape from the control meant to edit it. */
        SHAPE_MISMATCH
    }

    public boolean isError() {
        return severity == LayoutMismatch.Severity.ERROR;
    }

    @Override
    public String toString() {
        return severity + " " + kind + " [" + key + "]: " + detail;
    }
}
