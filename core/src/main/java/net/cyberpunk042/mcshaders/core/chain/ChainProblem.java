package net.cyberpunk042.mcshaders.core.chain;

import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.layout.LayoutMismatch;

/**
 * Something a {@link ChainValidator} found wrong with a chain.
 *
 * @param kind     what sort of problem
 * @param severity how much it matters
 * @param where    which pass, or null for a chain-wide finding
 * @param detail   what is wrong, in a sentence
 */
@Stable(since = "0.4.0")
public record ChainProblem(Kind kind, LayoutMismatch.Severity severity, String where, String detail) {

    public enum Kind {
        /** A shader the chain names cannot be found. */
        MISSING_SHADER,
        /** A shader was found but its includes did not resolve. */
        UNRESOLVED_INCLUDE,
        /** An input reads a target nothing declares or produces. */
        UNKNOWN_TARGET,
        /** A pass writes a target nothing declares. */
        UNKNOWN_OUTPUT,
        /** A target is read before any pass has written it. */
        READ_BEFORE_WRITE,
        /** A declared target is never used. */
        UNUSED_TARGET,
        /** An input has no matching sampler in the shader, or the reverse. */
        SAMPLER_MISMATCH,
        /** A uniform block is declared differently by the host and the shader. */
        LAYOUT_MISMATCH
    }

    public boolean isError() {
        return severity == LayoutMismatch.Severity.ERROR;
    }

    @Override
    public String toString() {
        return severity + " " + kind + (where == null ? "" : " [" + where + "]") + ": " + detail;
    }
}
