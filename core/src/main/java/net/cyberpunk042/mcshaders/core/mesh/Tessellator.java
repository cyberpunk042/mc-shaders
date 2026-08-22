/*
 * Ported from the-virus-block-mc (net.cyberpunk042.client.visual.mesh), where it
 * is tessellation code with no Minecraft or graphics-API dependency: the whole
 * subtree imported nothing from net.minecraft or com.mojang.
 * Relicensed to MIT here by the author, per the engine/content split recorded in
 * docs/PORTING.md.
 *
 * The mod's logging system was replaced with core's Diag, which routes to
 * java.lang.System.Logger. The call sites are unchanged apart from the name.
 */
package net.cyberpunk042.mcshaders.core.mesh;

import net.cyberpunk042.mcshaders.core.diag.Diag;
import net.cyberpunk042.mcshaders.core.shape.*;

/**
 * Turns a {@link Shape} — a description, such as "sphere of radius 1" — into a
 * {@link Mesh} of concrete vertices, normals, texture coordinates and indices.
 *
 * <p>A mesh is where a shape stops being parameters and becomes something drawable.
 * What happens after that is the consumer's business: core produces the geometry and
 * deliberately does not reach for a graphics API to submit it.
 *
 * <h2>Resolution comes from the shape, not from {@code detail}</h2>
 *
 * <p><b>The {@code detail} argument to {@link #tessellate(Shape, int)} is not read by
 * any tessellator</b>, and neither is the {@link DetailLevel} enum below. Every shape
 * record carries its own resolution — {@code segments} and {@code heightSegments} on
 * the swept shapes, {@code subdivisions} on {@link PolyhedronShape} — and that is what
 * the tessellators use. {@code PolyhedronTessellator.tessellate(int)} even accepts the
 * parameter and then ignores it in favour of the shape's own value.
 *
 * <p>So to change how fine a mesh is, change the shape:
 *
 * <pre>
 * CylinderShape coarse = CylinderShape.of(1.0f, 2.0f);          // 32 segments
 * CylinderShape fine   = new CylinderShape(1.0f, 2.0f, 128,     // 128 segments
 *         coarse.topRadius(), coarse.heightSegments(),
 *         coarse.capTop(), coarse.capBottom(), coarse.arc());
 * </pre>
 *
 * <p>This is a leftover from the code's origin rather than a design: the parameter and
 * its enum predate the move to shape-carried resolution and were never removed. They
 * are documented here rather than deleted because deleting them is a breaking change to
 * make on purpose, not in passing. {@code TessellationTest} pins the behaviour, so if a
 * tessellator ever starts honouring {@code detail} a test fails and this text gets
 * corrected with it.
 *
 * <h2>Shapes the dispatcher handles</h2>
 *
 * <table>
 *   <caption>What {@link #tessellate(Shape, int)} routes where</caption>
 *   <tr><th>Shape</th><th>Tessellator</th><th>Primitives</th></tr>
 *   <tr><td>{@link SphereShape}</td><td>{@code SphereTessellator}</td><td>triangles</td></tr>
 *   <tr><td>{@link RingShape}</td><td>{@code RingTessellator}</td><td>triangles</td></tr>
 *   <tr><td>{@link PrismShape}</td><td>{@code PrismTessellator}</td><td>triangles</td></tr>
 *   <tr><td>{@link CylinderShape}</td><td>{@code CylinderTessellator}</td><td>triangles</td></tr>
 *   <tr><td>{@link PolyhedronShape}</td><td>{@code PolyhedronTessellator}</td><td>triangles or quads</td></tr>
 *   <tr><td>{@link MoleculeShape}</td><td>{@code MoleculeTessellator}</td><td>triangles</td></tr>
 * </table>
 *
 * <p>Anything else yields {@link Mesh#empty()} and a warning, on the grounds that a
 * consumer passing a shape core does not recognise should get nothing drawn rather than
 * an exception in the middle of a frame. The other tessellators in this package —
 * {@code RaysTessellator}, {@code JetTessellator}, {@code KamehamehaTessellator},
 * {@code CapsuleTessellator}, {@code ConeTessellator}, {@code TorusTessellator} — are
 * called directly rather than through this dispatcher.
 *
 * @see Mesh
 * @see Shape
 */
public interface Tessellator {
    
    // =========================================================================
    // Detail Level Constants
    // =========================================================================
    
    /**
     * Named segment counts.
     *
     * <p>Passing one of these to {@link #tessellate(Shape, int)} has no effect — see the
     * class documentation. They remain useful as values to put in a shape's own
     * {@code segments} field, which is what actually decides resolution.
     */
    enum DetailLevel {
        /** Minimal detail - fast but blocky (4 segments) */
        MINIMAL(4),
        
        /** Low detail - visible facets but fast (8 segments) */
        LOW(8),
        
        /** Medium detail - good balance (16 segments) */
        MEDIUM(16),
        
        /** High detail - smooth appearance (32 segments) */
        HIGH(32),
        
        /** Very high detail - very smooth (64 segments) */
        VERY_HIGH(64),
        
        /** Maximum detail - highest quality (128 segments) */
        MAXIMUM(128);
        
        /** The numeric detail value. */
        public final int value;
        
        DetailLevel(int value) {
            this.value = value;
        }
        
        /**
         * Gets the appropriate detail level for a given radius.
         * Larger objects need more detail to look smooth.
         * 
         * @param radius Object radius
         * @return Recommended detail level
         */
        public static DetailLevel forRadius(float radius) {
            if (radius < 0.5f) return LOW;
            if (radius < 1.0f) return MEDIUM;
            if (radius < 2.0f) return HIGH;
            if (radius < 5.0f) return VERY_HIGH;
            return MAXIMUM;
        }
    }
    
    // =========================================================================
    // Minimum Detail Constants
    // =========================================================================
    
    /** Minimum segments for circular shapes (prevents degenerate triangles) */
    int MIN_CIRCULAR_SEGMENTS = 8;
    
    /** Minimum latitude steps for spheres */
    int MIN_SPHERE_LAT_STEPS = 8;
    
    /** Minimum longitude steps for spheres */
    int MIN_SPHERE_LON_STEPS = 8;
    
    /** Minimum sides for prisms/cylinders */
    int MIN_PRISM_SIDES = 3;
    
    // =========================================================================
    // Static Factory Method (Primary API)
    // =========================================================================
    
    /**
     * Tessellates any shape into a mesh.
     * 
     * <p>This is the primary tessellation API. It automatically selects the
     * appropriate tessellator based on the shape's type.</p>
     * 
     * @param shape  the shape definition to tessellate
     * @param detail <b>ignored.</b> No tessellator reads it; resolution comes from the
     *               shape's own {@code segments} or {@code subdivisions}. See the class
     *               documentation for why the parameter is still here.
     * @return the generated mesh, or {@link Mesh#empty()} if the shape's type has no
     *         tessellator in this dispatcher
     * @throws IllegalArgumentException if {@code shape} is null
     */
    static Mesh tessellate(Shape shape, int detail) {
        if (shape == null) {
            throw new IllegalArgumentException("Shape cannot be null");
        }
        
        // Log tessellation request
        Diag.RENDER.topic("tessellate")
            .kv("shape", shape.getClass().getSimpleName())
            .kv("detail", detail)
            .debug("Tessellating shape");
        
        // Dispatch to appropriate tessellator based on shape type
        return switch (shape) {
            
            // === SPHERE ===
            case SphereShape sphere -> SphereTessellator.tessellate(sphere);
            
            // === RING ===
            case RingShape ring -> RingTessellator.tessellate(ring);
            
            // === PRISM ===
            case PrismShape prism -> PrismTessellator.tessellate(prism);
            
            // === CYLINDER ===
            case CylinderShape cylinder -> CylinderTessellator.tessellate(cylinder);
            
            // === POLYHEDRON ===
            case PolyhedronShape polyhedron -> PolyhedronTessellator
                .fromShape(polyhedron)
                .tessellate(detail);
            
            // === MOLECULE ===
            case MoleculeShape molecule -> MoleculeTessellator.tessellate(molecule);
            default -> {
                Diag.RENDER.topic("tessellate")
                    .reason("unknown shape type")
                    .warn("Cannot tessellate unknown shape type: {}", 
                        shape.getClass().getSimpleName());
                yield Mesh.empty();
            }
        };
    }
    
    /**
     * Tessellates with automatic detail level based on shape size.
     * 
     * @param shape The shape to tessellate
     * @return Generated mesh
     */
    static Mesh tessellateAuto(Shape shape) {
        if (shape == null) {
            throw new IllegalArgumentException("Shape cannot be null");
        }
        
        // Estimate radius from bounds
        float radius = shape.getBounds().length() / 2;
        DetailLevel level = DetailLevel.forRadius(radius);
        
        return tessellate(shape, level.value);
    }
    
    // =========================================================================
    // Instance Methods (for custom tessellators)
    // =========================================================================
    
    /**
     * Generates a mesh.
     *
     * @param detail nominally a level of detail, but no implementation in this package
     *               reads it — each takes its resolution from the shape it was built
     *               from. An implementation outside this package is free to use it.
     * @return the generated mesh
     */
    Mesh tessellate(int detail);
    
    /**
     * Returns a default detail level appropriate for this tessellator.
     */
    default int defaultDetail() {
        return DetailLevel.MEDIUM.value;
    }
    
    /**
     * Returns the minimum valid detail level.
     */
    default int minDetail() {
        return DetailLevel.MINIMAL.value;
    }
    
    /**
     * Returns the maximum valid detail level.
     */
    default int maxDetail() {
        return DetailLevel.MAXIMUM.value;
    }
    
    /**
     * Clamps detail to valid range.
     * 
     * @param detail Requested detail level
     * @return Clamped detail level
     */
    default int clampDetail(int detail) {
        return Math.max(minDetail(), Math.min(maxDetail(), detail));
    }
}
