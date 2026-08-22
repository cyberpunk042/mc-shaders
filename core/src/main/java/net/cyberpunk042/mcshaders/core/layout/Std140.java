package net.cyberpunk042.mcshaders.core.layout;

import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * The std140 layout rules, which decide where each member of a uniform block sits.
 *
 * <p>This matters because a uniform block is bound by <em>offset</em>, never by
 * name. Two declarations of the same block — the shader's, and whatever the host
 * writes into it — agree only if every member lands on the same byte. Insert one
 * float into one of them and everything after it silently shifts, so the shader
 * keeps reading values, just the wrong ones. Nothing errors; the picture is
 * merely wrong.
 *
 * <p>The rules implemented here:
 *
 * <ul>
 *   <li>a member starts at the next offset that is a multiple of its alignment;</li>
 *   <li>a {@code vec3} is 12 bytes but aligns to 16, so it leaves a hole;</li>
 *   <li>an array element's stride is rounded up to a multiple of 16;</li>
 *   <li>a matrix is an array of its columns, each padded to 16;</li>
 *   <li>the block's total size is rounded up to a multiple of 16.</li>
 * </ul>
 */
@Stable(since = "0.4.0")
public final class Std140 {

    /** Every std140 array element and matrix column is padded up to this. */
    public static final int ARRAY_STRIDE_ALIGNMENT = 16;

    private Std140() {
    }

    /**
     * Assigns a byte offset to every member of a block, in declaration order.
     *
     * @param members the block's members, in the order they are declared
     * @return one entry per member, in the same order
     */
    public static List<Placement> place(List<Member> members) {
        List<Placement> out = new ArrayList<>(members.size());
        int offset = 0;
        for (Member m : members) {
            int alignment = alignmentOf(m);
            offset = align(offset, alignment);
            out.add(new Placement(m, offset));
            offset += sizeOf(m);
        }
        return List.copyOf(out);
    }

    /** Total size of a block, rounded up as std140 requires. */
    public static int sizeOf(List<Member> members) {
        List<Placement> placed = place(members);
        if (placed.isEmpty()) {
            return 0;
        }
        Placement last = placed.get(placed.size() - 1);
        return align(last.offset() + sizeOf(last.member()), ARRAY_STRIDE_ALIGNMENT);
    }

    /**
     * Rewrites a member list so that every matrix and every array becomes its
     * individual elements, named {@code parent[i]}.
     *
     * <p>Content formats routinely cannot spell a matrix or an array — Minecraft's
     * post-effect JSON, for one, has neither — so a {@code mat4} is written as four
     * {@code vec4}s and an array as its elements one by one. Expanding both sides
     * before comparing them means that spelling difference stops looking like a
     * layout bug, which it is not: the bytes are identical either way.
     *
     * <p>It also makes the comparison count in slots rather than declarations. One
     * {@code vec4 x[32]} is a single member but thirty-two slots of data, and a host
     * that writes one of them has left thirty-one unwritten — which is a defect, not
     * a rounding difference in a total.
     *
     * <p>Elements are reported as {@code vec4} because std140 pads every array
     * element and every matrix column up to sixteen bytes regardless of its type.
     * That makes the offsets exact for any element type, and leaves only the padding
     * described loosely — on both sides equally.
     */
    public static List<Member> expand(List<Member> members) {
        List<Member> out = new ArrayList<>(members.size());
        for (Member m : members) {
            int columns = Math.max(m.type().matrixColumns(), 1);
            int elements = columns * m.arrayLength();
            if (elements == 1) {
                out.add(m);
                continue;
            }
            for (int i = 0; i < elements; i++) {
                out.add(new Member(m.name() + "[" + i + "]", GlslType.VEC4, 1));
            }
        }
        return List.copyOf(out);
    }

    /** Rounds {@code offset} up to the next multiple of {@code alignment}. */
    public static int align(int offset, int alignment) {
        int remainder = offset % alignment;
        return remainder == 0 ? offset : offset + alignment - remainder;
    }

    private static int alignmentOf(Member m) {
        // Arrays and matrices align to 16 whatever their element type.
        return m.arrayLength() > 1 || m.type().matrixColumns() > 0
                ? ARRAY_STRIDE_ALIGNMENT
                : m.type().alignment();
    }

    private static int sizeOf(Member m) {
        if (m.arrayLength() > 1) {
            return align(m.type().size(), ARRAY_STRIDE_ALIGNMENT) * m.arrayLength();
        }
        return m.type().size();
    }

    /**
     * One member of a uniform block.
     *
     * @param name        the member's name
     * @param type        its type
     * @param arrayLength element count; 1 for a plain, non-array member
     */
    public record Member(String name, GlslType type, int arrayLength) {

        public Member {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("member name is required");
            }
            if (type == null) {
                throw new IllegalArgumentException("member type is required for " + name);
            }
            if (arrayLength < 1) {
                throw new IllegalArgumentException(
                        "array length must be at least 1 for " + name + ", got " + arrayLength);
            }
        }

        public Member(String name, GlslType type) {
            this(name, type, 1);
        }
    }

    /** A member together with the byte offset std140 puts it at. */
    public record Placement(Member member, int offset) {

        public String name() {
            return member.name();
        }

        public GlslType type() {
            return member.type();
        }
    }
}
