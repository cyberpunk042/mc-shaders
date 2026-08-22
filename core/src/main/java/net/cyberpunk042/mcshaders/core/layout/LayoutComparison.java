package net.cyberpunk042.mcshaders.core.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;
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
        Map<Integer, Std140.Placement> want = byOffset(expected);
        Map<Integer, Std140.Placement> have = byOffset(actual);
        List<LayoutMismatch> out = new ArrayList<>();

        List<Integer> offsets = new ArrayList<>(new TreeSet<>(
                Stream.concat(want.keySet().stream(), have.keySet().stream()).toList()));

        for (int offset : offsets) {
            Std140.Placement w = want.get(offset);
            Std140.Placement h = have.get(offset);

            if (h == null) {
                // A host that simply stops early leaves every later member unwritten.
                // That is one event, and naming forty of them buries it.
                long remaining = offsets.stream().filter(o -> o >= offset).count();
                long unwritten = offsets.stream().filter(o -> o >= offset && !have.containsKey(o)).count();
                boolean toTheEnd = remaining == unwritten;
                out.add(new LayoutMismatch(LayoutMismatch.Kind.UNWRITTEN, LayoutMismatch.Severity.ERROR,
                        offset, describe(w), null,
                        toTheEnd && unwritten > 1
                                ? "the shader reads " + unwritten + " member(s) from here on and nothing"
                                        + " writes them, starting with '" + w.name() + "'; they hold"
                                        + " whatever the buffer happened to contain"
                                : "the shader reads '" + w.name() + "' here and nothing writes it; it"
                                        + " holds whatever the buffer happened to contain"));
                if (toTheEnd) {
                    break;
                }
                continue;
            }
            if (w == null) {
                out.add(new LayoutMismatch(LayoutMismatch.Kind.IGNORED, LayoutMismatch.Severity.INFO,
                        offset, null, describe(h),
                        "the host writes '" + h.name() + "' here and the shader reads nothing from it"));
                continue;
            }
            // Names first. Two differently-named members at one offset is the
            // declarations parting company; the same member declared with two types is
            // a local error that says nothing about what follows.
            if (namesAgree(w, h)) {
                if (w.type() != h.type()) {
                    out.add(mismatch(LayoutMismatch.Kind.TYPE_MISMATCH, LayoutMismatch.Severity.ERROR,
                            w, h, "same member at the same offset, declared with different types"));
                }
                continue;
            }
            if (isReserved(w.name())) {
                out.add(mismatch(LayoutMismatch.Kind.RESERVED_SLOT_WRITTEN, LayoutMismatch.Severity.INFO,
                        w, h, "the host writes here but the shader has reserved the slot, so the value"
                                + " is ignored"));
            } else if (tailAgrees(want, have, offsets, offset)) {
                out.add(mismatch(LayoutMismatch.Kind.RENAMED_MEMBER, LayoutMismatch.Severity.WARNING, w, h,
                        "same offset and type under a different name, and the rest still lines up —"
                                + " a rename on one side only"));
            } else {
                out.add(mismatch(LayoutMismatch.Kind.DIVERGENT_MEMBER, LayoutMismatch.Severity.ERROR, w, h,
                        "the declarations part company here, so this member and every one after it"
                                + " is read from the wrong place"));
                // Every later offset is a consequence of this one; listing them is noise.
                break;
            }
        }

        if (out.isEmpty() && expected.sizeInBytes() != actual.sizeInBytes()) {
            out.add(new LayoutMismatch(LayoutMismatch.Kind.SIZE_MISMATCH,
                    LayoutMismatch.Severity.WARNING, expected.sizeInBytes(),
                    expected.sizeInBytes() + " bytes", actual.sizeInBytes() + " bytes",
                    "every member agrees but the blocks are different sizes"));
        }
        return List.copyOf(out);
    }

    private static Map<Integer, Std140.Placement> byOffset(UniformBlock block) {
        Map<Integer, Std140.Placement> out = new LinkedHashMap<>();
        for (Std140.Placement p : Std140.place(Std140.expand(block.members()))) {
            out.put(p.offset(), p);
        }
        return out;
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

    /** Whether every offset after {@code from} holds the same thing on both sides. */
    private static boolean tailAgrees(Map<Integer, Std140.Placement> want,
                                      Map<Integer, Std140.Placement> have,
                                      List<Integer> offsets, int from) {
        for (int offset : offsets) {
            if (offset <= from) {
                continue;
            }
            Std140.Placement w = want.get(offset);
            Std140.Placement h = have.get(offset);
            if (w == null || h == null || w.type() != h.type() || !namesAgree(w, h)) {
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
