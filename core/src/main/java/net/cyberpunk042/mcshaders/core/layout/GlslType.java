package net.cyberpunk042.mcshaders.core.layout;

import java.util.Locale;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * The GLSL types a uniform block member can have, with their std140 footprint.
 *
 * <p>Only the types that actually appear in uniform blocks are modelled. Opaque
 * types (samplers, images) cannot appear inside a block, so they are not here.
 *
 * @see Std140
 */
@Stable(since = "0.4.0")
public enum GlslType {

    BOOL("bool", 4, 4),
    INT("int", 4, 4),
    UINT("uint", 4, 4),
    FLOAT("float", 4, 4),
    VEC2("vec2", 8, 8),
    IVEC2("ivec2", 8, 8),
    /** Sized 12 but aligned 16 — the classic std140 trap. */
    VEC3("vec3", 12, 16),
    IVEC3("ivec3", 12, 16),
    VEC4("vec4", 16, 16),
    IVEC4("ivec4", 16, 16),
    MAT2("mat2", 32, 16),
    MAT3("mat3", 48, 16),
    MAT4("mat4", 64, 16);

    private final String glsl;
    private final int size;
    private final int alignment;

    GlslType(String glsl, int size, int alignment) {
        this.glsl = glsl;
        this.size = size;
        this.alignment = alignment;
    }

    /** The name as written in GLSL. */
    public String glsl() {
        return glsl;
    }

    /** Size in bytes of a single, non-array occurrence. */
    public int size() {
        return size;
    }

    /** Required base alignment in bytes under std140. */
    public int alignment() {
        return alignment;
    }

    /**
     * How many {@code vec4}-shaped columns this type occupies, or 0 if it is not a
     * matrix.
     *
     * <p>This is what lets a matrix be compared against the four vectors some
     * content formats have to spell it as — see {@link Std140#expand}.
     */
    public int matrixColumns() {
        return switch (this) {
            case MAT2 -> 2;
            case MAT3 -> 3;
            case MAT4 -> 4;
            default -> 0;
        };
    }

    /**
     * The scalar this type is made of, and how many of them it holds.
     *
     * <p>std140 binds by byte offset and a shader ultimately reads scalars, so this is
     * what lets two declarations of the same bytes be recognised as the same bytes
     * however each side chose to spell them. A {@code mat4} and sixteen {@code float}s
     * at the same offset are one thing seen twice.
     *
     * @return the component type and its count, e.g. {@code VEC3 -> (FLOAT, 3)}
     */
    public Components components() {
        return switch (this) {
            case BOOL -> new Components(BOOL, 1);
            case INT -> new Components(INT, 1);
            case UINT -> new Components(UINT, 1);
            case FLOAT -> new Components(FLOAT, 1);
            case VEC2 -> new Components(FLOAT, 2);
            case VEC3 -> new Components(FLOAT, 3);
            case VEC4 -> new Components(FLOAT, 4);
            case IVEC2 -> new Components(INT, 2);
            case IVEC3 -> new Components(INT, 3);
            case IVEC4 -> new Components(INT, 4);
            case MAT2 -> new Components(FLOAT, 4);
            case MAT3 -> new Components(FLOAT, 9);
            case MAT4 -> new Components(FLOAT, 16);
        };
    }

    /**
     * A type broken into the scalars it is made of.
     *
     * @param scalar the component type — always one of {@code BOOL}, {@code INT},
     *               {@code UINT} or {@code FLOAT}
     * @param count  how many components, e.g. 3 for a {@code vec3}
     */
    public record Components(GlslType scalar, int count) {
    }

    public static Optional<GlslType> parse(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String lower = name.toLowerCase(Locale.ROOT).trim();
        for (GlslType t : values()) {
            if (t.glsl.equals(lower)) {
                return Optional.of(t);
            }
        }
        return Optional.empty();
    }
}
