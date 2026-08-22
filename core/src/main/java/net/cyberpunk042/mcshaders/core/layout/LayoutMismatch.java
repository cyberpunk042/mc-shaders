package net.cyberpunk042.mcshaders.core.layout;

import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * One way in which two declarations of the same uniform block disagree.
 *
 * <p>Not every disagreement is a defect, which is why these carry a
 * {@link Severity}. A validator that reports a matrix spelled as four vectors
 * with the same urgency as a genuine offset shift is a validator people turn
 * off, and then the real shift ships.
 *
 * @param kind     what sort of disagreement
 * @param severity how much it matters
 * @param offset   the byte offset it was found at
 * @param expected what the reference declaration has there, or null past its end
 * @param actual   what the compared declaration has there, or null past its end
 * @param detail   a sentence naming the consequence
 */
@Stable(since = "0.4.0")
public record LayoutMismatch(Kind kind, Severity severity, int offset,
                             String expected, String actual, String detail) {

    public enum Severity {
        /** The shader will read wrong values. */
        ERROR,
        /** Legal, but very likely not what anyone intended. */
        WARNING,
        /** Worth knowing; the bytes are fine. */
        INFO
    }

    public enum Kind {
        /** Different members occupy the same offset. Everything after this is suspect. */
        DIVERGENT_MEMBER,
        /** The shader reads at an offset the host writes nothing to. */
        UNWRITTEN,
        /** The host writes at an offset the shader reads nothing from — padding, usually. */
        IGNORED,
        /** Same offset and type, different name, and the rest still lines up. */
        RENAMED_MEMBER,
        /** The host fills a slot the shader has marked as reserved or padding. */
        RESERVED_SLOT_WRITTEN,
        /** Same member and offset, different type. */
        TYPE_MISMATCH,
        /** One declaration ends while the other still has members. */
        TRUNCATED,
        /** The blocks agree member for member but the total sizes differ. */
        SIZE_MISMATCH
    }

    /** Whether this would make the shader read a value it was not given. */
    public boolean isError() {
        return severity == Severity.ERROR;
    }

    @Override
    public String toString() {
        return severity + " " + kind + " at byte " + offset + ": " + detail;
    }
}
