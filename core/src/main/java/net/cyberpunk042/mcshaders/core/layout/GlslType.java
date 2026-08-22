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
