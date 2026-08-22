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

        int resumeAt = Integer.MIN_VALUE;
        for (int offset : offsets) {
            if (offset < resumeAt) {
                // Inside a divergent run already reported. Its members are one event.
                continue;
            }
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
                                ? "the shader reads " + unwritten + " scalar slot(s) from here on and nothing"
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
                        "same offset and type under a different name, and the rest still lines up:"
                                + " a rename on one side only"));
            } else {
                int realign = realignsAt(want, have, offsets, offset);
                out.add(mismatch(LayoutMismatch.Kind.DIVERGENT_MEMBER, LayoutMismatch.Severity.ERROR, w, h,
                        realign < 0
                                ? "the declarations part company here and never line up again, so this"
                                        + " member and every one after it is read from the wrong place"
                                : "the declarations disagree from here until byte " + realign
                                        + " (" + countBetween(offsets, offset, realign) + " scalar slot(s))"
                                        + " and line up again from there, so the damage is bounded to"
                                        + " those"));
                if (realign < 0) {
                    // One side gained a member the other has not, so every later offset is
                    // shifted and reporting each is noise. Nothing further can be trusted.
                    break;
                }
                // A bounded run is a local swap, not a shift. Skip its members — they are
                // one event — and carry on, because what follows is independent and used
                // to be lost entirely: in the shaders this was written against, breaking
                // here hid a second two-slot swap 112 bytes later and 26 reserved slots
                // the host legitimately writes camera data into.
                resumeAt = realign;
                continue;
            }
        }

        // A size difference is only worth its own finding when nothing above already
        // described it. UNWRITTEN and TRUNCATED both mean "the host stopped early", and
        // saying that twice is not more evidence.
        //
        // What this must not do is stay silent after a DIVERGENT_MEMBER. That branch
        // stops reading members at the first disagreement, so without this the ends are
        // never compared — and a shader whose block is 928 bytes against a host writing
        // 672 reports one divergence in the middle and nothing at all about the 256
        // bytes past the end. That was the behaviour before: out.isEmpty() meant any
        // error at all suppressed the check that mattered most.
        boolean tailAlreadyReported = out.stream().anyMatch(m ->
                m.kind() == LayoutMismatch.Kind.UNWRITTEN
                        || m.kind() == LayoutMismatch.Kind.TRUNCATED);
        if (!tailAlreadyReported && expected.sizeInBytes() != actual.sizeInBytes()) {
            int shortfall = expected.sizeInBytes() - actual.sizeInBytes();
            out.add(new LayoutMismatch(LayoutMismatch.Kind.SIZE_MISMATCH,
                    shortfall > 0 ? LayoutMismatch.Severity.ERROR : LayoutMismatch.Severity.WARNING,
                    Math.min(expected.sizeInBytes(), actual.sizeInBytes()),
                    expected.sizeInBytes() + " bytes", actual.sizeInBytes() + " bytes",
                    shortfall > 0
                            ? "the shader's block is " + expected.sizeInBytes() + " bytes and the host"
                                    + " writes " + actual.sizeInBytes() + "; the last " + shortfall
                                    + " bytes hold whatever the buffer contained"
                            : "the host writes " + actual.sizeInBytes() + " bytes into a block the"
                                    + " shader declares as " + expected.sizeInBytes() + "; the extra "
                                    + (-shortfall) + " are never read"));
        }
        return List.copyOf(out);
    }

    /**
     * Every scalar the block occupies, keyed by the byte it starts at.
     *
     * <p>Comparing declarations member-for-member only works while both sides spell
     * things the same way, and content formats give authors no choice about that.
     * Minecraft's post-effect JSON has no matrix type and no vector type: a {@code mat4}
     * has to be written as sixteen consecutive floats. Against a shader declaring
     * {@code vec4[4]}, a member-level comparison sees {@code vec4} against {@code float}
     * at the same offset and calls it a type error, then calls the twelve floats sitting
     * inside those vectors writes nobody reads. Both sides are describing the same
     * sixty-four bytes.
     *
     * <p>So the comparison happens at the level std140 actually binds at. A {@code vec4}
     * at 528 becomes four floats at 528, 532, 536 and 540, and sixteen floats written by
     * the host land on exactly those, and the two agree. A genuine disagreement —
     * {@code int} where the shader reads {@code float}, or a member the other side does
     * not have — still lands on a scalar and is still reported.
     *
     * <p>Each scalar keeps the name of the member it came from, unsuffixed, so findings
     * still say {@code CameraX} rather than an offset alone — and, more importantly, so
     * that a name which means something goes on meaning something. Suffixing them was
     * tried and was wrong: {@link #isExpandedElement} treats a bracketed name as carrying
     * no information, because the author of the other side never chose it, and marking
     * every scalar that way made {@code Radius} and {@code Inserted} agree at byte 0.
     * A genuine shift went unreported. The only names that should be ignorable are the
     * ones {@link Std140#expand} generated for array and matrix elements, which is what
     * it already did.
     */
    private static Map<Integer, Std140.Placement> byOffset(UniformBlock block) {
        Map<Integer, Std140.Placement> out = new LinkedHashMap<>();
        for (Std140.Placement p : Std140.place(Std140.expand(block.members()))) {
            GlslType.Components parts = p.type().components();
            if (parts.count() == 1) {
                out.put(p.offset(), p);
                continue;
            }
            int stride = parts.scalar().size();
            for (int i = 0; i < parts.count(); i++) {
                out.put(p.offset() + i * stride,
                        new Std140.Placement(
                                new Std140.Member(p.name(), parts.scalar(), 1),
                                p.offset() + i * stride));
            }
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

    /**
     * The offset from which the two declarations agree again, or -1 if they never do.
     *
     * <p>A divergence is usually reported as poisoning everything after it, and often it
     * does — one side gaining a member shifts every later one. But two declarations can
     * also disagree over a couple of slots and then line up again, which is what happens
     * when someone replaces two fields with two others of the same size. Saying "every
     * member after this is wrong" there sends the reader to audit forty members when two
     * are at fault.
     *
     * <p>This answers where <em>this</em> run ends, not where all disagreement ends. A
     * block can hold several independent swaps, and treating the first as running to the
     * last would merge them into one finding and hide everything between. Later runs get
     * their own findings when the comparison resumes.
     *
     * <p>Offsets only one side has do not end a run: one declaration outliving the other
     * is what {@link LayoutMismatch.Kind#UNWRITTEN} and
     * {@link LayoutMismatch.Kind#SIZE_MISMATCH} are for. Without that, a block that both
     * diverges in the middle and is truncated at the end reports the divergence as
     * unbounded, which is the shape the real shaders turned out to have.
     */
    private static int realignsAt(Map<Integer, Std140.Placement> want,
                                  Map<Integer, Std140.Placement> have,
                                  List<Integer> offsets, int from) {
        for (int offset : offsets) {
            if (offset <= from) {
                continue;
            }
            if (agreeAt(want, have, offset)) {
                return offset;
            }
        }
        return -1;
    }

    /** Whether both sides describe the same member at {@code offset}. */
    private static boolean agreeAt(Map<Integer, Std140.Placement> want,
                                   Map<Integer, Std140.Placement> have, int offset) {
        Std140.Placement w = want.get(offset);
        Std140.Placement h = have.get(offset);
        return w != null && h != null && w.type() == h.type() && namesAgree(w, h);
    }

    /** How many offsets fall in {@code [from, to)}. */
    private static long countBetween(List<Integer> offsets, int from, int to) {
        return offsets.stream().filter(o -> o >= from && o < to).count();
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
