package net.cyberpunk042.mcshaders.core.layout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * Compares two declarations of the same uniform block and reports where, in bytes,
 * they stop agreeing.
 *
 * <p>A uniform block has to be declared twice: once in the shader that reads it,
 * and once by whatever writes it — a host record, a pipeline definition, a
 * generator. Nothing checks that those two agree. They are bound by offset, so if
 * one side gains a member the other does not, every member after it silently
 * shifts and the shader reads real numbers from the wrong places. The picture is
 * wrong and nothing anywhere reports an error.
 *
 * <p>The first {@link LayoutMismatch.Kind#DIVERGENT_MEMBER} is the interesting one:
 * everything before it is fine and everything after it is suspect, so its offset
 * is the boundary between the part of the block that works and the part that does
 * not.
 *
 * <h2>What is deliberately not reported as a defect</h2>
 *
 * <p>Two declarations can differ in spelling while describing identical bytes, and
 * reporting those as problems buries the ones that matter:
 *
 * <ul>
 *   <li><b>A matrix or array written as its elements.</b> Content formats routinely
 *       have neither type — Minecraft's post-effect JSON, for one — so a {@code mat4}
 *       is spelled as four {@code vec4}s under whatever names the author picked. Both
 *       sides are expanded to elements before comparing, and an element's name is not
 *       treated as meaningful.</li>
 *   <li><b>A slot the shader reserves.</b> If the shader calls a member
 *       {@code Reserved3_0} and the host writes {@code CameraX} there, the bytes
 *       line up and the shader simply ignores them. That is worth knowing, not
 *       worth failing.</li>
 * </ul>
 */
@Stable(since = "0.4.0")
public final class LayoutComparison {

    private LayoutComparison() {
    }

    /**
     * Compares {@code actual} against {@code expected}.
     *
     * @param expected the declaration to treat as correct — normally the shader's,
     *                 since that is the one that decides what the bytes mean
     * @param actual   the declaration being checked
     * @return the disagreements, in offset order; empty if the two describe the
     *         same bytes in the same way
     */
    public static List<LayoutMismatch> compare(UniformBlock expected, UniformBlock actual) {
        List<Std140.Placement> want = Std140.place(Std140.expand(expected.members()));
        List<Std140.Placement> have = Std140.place(Std140.expand(actual.members()));
        List<LayoutMismatch> out = new ArrayList<>();

        int common = Math.min(want.size(), have.size());
        boolean diverged = false;
        for (int i = 0; i < common && !diverged; i++) {
            Std140.Placement w = want.get(i);
            Std140.Placement h = have.get(i);

            if (w.offset() != h.offset() || w.type() != h.type()) {
                out.add(mismatch(w.offset() != h.offset()
                                ? LayoutMismatch.Kind.DIVERGENT_MEMBER
                                : LayoutMismatch.Kind.TYPE_MISMATCH,
                        LayoutMismatch.Severity.ERROR, w, h,
                        w.offset() != h.offset()
                                ? "the declarations part company here, so this member and every one"
                                        + " after it is read from the wrong place"
                                : "same member at the same offset, declared with different types"));
                diverged = w.offset() != h.offset();
                continue;
            }

            if (namesAgree(w, h)) {
                continue;
            }

            if (isReserved(w.name())) {
                out.add(mismatch(LayoutMismatch.Kind.RESERVED_SLOT_WRITTEN,
                        LayoutMismatch.Severity.INFO, w, h,
                        "the host writes here but the shader has reserved the slot, so the value"
                                + " is ignored"));
            } else if (tailMatches(want, have, i + 1)) {
                out.add(mismatch(LayoutMismatch.Kind.RENAMED_MEMBER,
                        LayoutMismatch.Severity.WARNING, w, h,
                        "same offset and type under a different name, and the rest still lines up —"
                                + " a rename on one side only"));
            } else {
                out.add(mismatch(LayoutMismatch.Kind.DIVERGENT_MEMBER,
                        LayoutMismatch.Severity.ERROR, w, h,
                        "the declarations part company here, so this member and every one after it"
                                + " is read from the wrong place"));
                diverged = true;
            }
        }

        // Once the two have parted company, listing every later member as its own
        // mismatch is noise: they are all consequences of the one divergence.
        if (!diverged && want.size() != have.size()) {
            boolean shortfall = have.size() < want.size();
            Std140.Placement first = (shortfall ? want : have).get(common);
            out.add(new LayoutMismatch(LayoutMismatch.Kind.TRUNCATED,
                    shortfall ? LayoutMismatch.Severity.ERROR : LayoutMismatch.Severity.WARNING,
                    first.offset(),
                    shortfall ? describe(first) : null,
                    shortfall ? null : describe(first),
                    shortfall
                            ? "the shader reads " + (want.size() - common) + " more member(s) than are"
                                    + " written, starting here; they hold whatever the buffer happened"
                                    + " to contain"
                            : (have.size() - common) + " member(s) are written past the end of what the"
                                    + " shader declares, starting here"));
        }

        if (out.isEmpty() && expected.sizeInBytes() != actual.sizeInBytes()) {
            out.add(new LayoutMismatch(LayoutMismatch.Kind.SIZE_MISMATCH,
                    LayoutMismatch.Severity.WARNING, expected.sizeInBytes(),
                    expected.sizeInBytes() + " bytes", actual.sizeInBytes() + " bytes",
                    "every member agrees but the blocks are different sizes"));
        }
        return List.copyOf(out);
    }

    /** Whether the two declarations describe the same bytes, ignoring INFO notes. */
    public static boolean agree(UniformBlock expected, UniformBlock actual) {
        return compare(expected, actual).stream()
                .noneMatch(m -> m.severity() != LayoutMismatch.Severity.INFO);
    }

    /** The disagreements that would make the shader read a value it was not given. */
    public static List<LayoutMismatch> errors(UniformBlock expected, UniformBlock actual) {
        return compare(expected, actual).stream().filter(LayoutMismatch::isError).toList();
    }

    /**
     * Whether two members' names can be meaningfully compared at all.
     *
     * <p>A name {@link Std140#expand} generated for a matrix column or an array
     * element carries no information — the author of the other side never chose it —
     * so two elements at the same offset and type are the same thing whatever they
     * are called.
     */
    private static boolean namesAgree(Std140.Placement a, Std140.Placement b) {
        return a.name().equals(b.name()) || isExpandedElement(a.name()) || isExpandedElement(b.name());
    }

    private static boolean isExpandedElement(String name) {
        return name.endsWith("]") && name.lastIndexOf('[') > 0;
    }

    private static boolean isReserved(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("reserved") || lower.startsWith("padding")
                || lower.startsWith("unused") || lower.startsWith("_pad");
    }

    /** Whether the two lists are identical from {@code from} onwards. */
    private static boolean tailMatches(List<Std140.Placement> want, List<Std140.Placement> have, int from) {
        if (want.size() != have.size()) {
            return false;
        }
        for (int i = from; i < want.size(); i++) {
            Std140.Placement w = want.get(i);
            Std140.Placement h = have.get(i);
            if (w.offset() != h.offset() || w.type() != h.type() || !namesAgree(w, h)) {
                return false;
            }
        }
        return true;
    }

    private static LayoutMismatch mismatch(LayoutMismatch.Kind kind, LayoutMismatch.Severity severity,
                                           Std140.Placement w, Std140.Placement h, String detail) {
        return new LayoutMismatch(kind, severity, w.offset(), describe(w), describe(h), detail);
    }

    private static String describe(Std140.Placement p) {
        return p.name() + ":" + p.type().glsl() + "@" + p.offset();
    }
}
