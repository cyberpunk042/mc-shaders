/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 *
 * JSON binding was removed on the way across: the model stays free of
 * serialisation so it can be loaded from external content. The @JsonField
 * metadata is retained for a codec layer above core.
 */
package net.cyberpunk042.mcshaders.core.pattern;

/**
 * Patterns for quad-based tessellation (spheres, prism sides).
 * 
 * <p>Controls how each 4-vertex quad cell is divided into triangles.
 * Uses semantic {@link Corner} enum for readability.
 * 
 * <h2>Vertex Layout</h2>
 * <pre>
 * TOP_LEFT (0) ──── TOP_RIGHT (1)
 *      │                 │
 *      │                 │
 * BOTTOM_LEFT (2) ─ BOTTOM_RIGHT (3)
 * </pre>
 * 
 * <h2>Available Patterns (16 user-curated from shuffle exploration)</h2>
 * <ul>
 *   <li><b>FILLED_1</b>: Standard filled quad (default)</li>
 *   <li><b>TRIANGLE_1-4</b>: Various triangle arrangements</li>
 *   <li><b>TRIANGLE_MESHED_1-2</b>: Meshed triangle patterns</li>
 *   <li><b>PARALLELOGRAM_1-2</b>: Parallelogram shapes</li>
 *   <li><b>STRIPE_1</b>: Stripe effect</li>
 *   <li><b>WAVE_1</b>: Wave pattern</li>
 *   <li><b>TOOTH_1</b>: Tooth/sawtooth pattern</li>
 * </ul>
 * 
 * @see VertexPattern
 * @see Corner
 */
public enum QuadPattern implements VertexPattern {
    
    // =========================================================================
    // User-Curated Patterns (from shuffle exploration)
    // Uses Corner enum for readability: TL=0, TR=1, BL=2, BR=3
    // =========================================================================
    
    /** Filled pattern variant 1 (#37) - Standard filled quad. */
    FILLED_1("filled_1",
        new Corner[]{Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_LEFT, Corner.TOP_RIGHT}
    ),
    
    /** Standard quad fill using TL-BR diagonal split - geometrically correct. */
    STANDARD_QUAD("standard_quad",
        new Corner[]{Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT},  // Upper-right triangle
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT}  // Lower-left triangle
    ),
    
    /** Triangle pattern 1 (#43). */
    TRIANGLE_1("triangle_1",
        new Corner[]{Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT},
        new Corner[]{Corner.BOTTOM_RIGHT, Corner.TOP_LEFT, Corner.TOP_RIGHT}
    ),
    
    /** Triangle pattern 2 (#53). */
    TRIANGLE_2("triangle_2",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT}
    ),
    
    /** Tooth pattern 1 (#54). */
    TOOTH_1("tooth_1",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT}
    ),
    
    /** Triangle meshed pattern 1 (#62). */
    TRIANGLE_MESHED_1("triangle_meshed_1",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_LEFT, Corner.BOTTOM_RIGHT}
    ),
    
    /** Triangle slim pattern 1 (#63). */
    TRIANGLE_SLIM_1("triangle_slim_1",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.TOP_LEFT}
    ),
    
    /** Parallelogram pattern 1 (#66). */
    PARALLELOGRAM_1("parallelogram_1",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT}
    ),
    
    /** Three quarter slim pattern 1 (#69). */
    THREE_QUARTER_SLIM_1("three_quarter_slim_1",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT, Corner.TOP_LEFT}
    ),
    
    /** Filled with triangle pattern 1 (#71). */
    FILLED_WITH_TRIANGLE_1("filled_with_triangle_1",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT, Corner.TOP_LEFT}
    ),
    
    /** Parallelogram pattern 2 (#74). */
    PARALLELOGRAM_2("parallelogram_2",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT},
        new Corner[]{Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT}
    ),
    
    /** Triangle meshed pattern 2 (#81). */
    TRIANGLE_MESHED_2("triangle_meshed_2",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT},
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, Corner.TOP_LEFT}
    ),
    
    /** Stripe pattern 1 (#83). */
    STRIPE_1("stripe_1",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT},
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT, Corner.TOP_LEFT}
    ),
    
    /** Square overlap pattern 1 (#99). */
    SQUARE_OVERLAP_1("square_overlap_1",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT},
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT}
    ),
    
    /** Wave pattern 1 (#114). */
    WAVE_1("wave_1",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT}
    ),
    
    /** Triangle pattern 3 (#147). */
    TRIANGLE_3("triangle_3",
        new Corner[]{Corner.TOP_RIGHT, Corner.TOP_LEFT, Corner.BOTTOM_LEFT},
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT}
    ),
    
    /** Triangle pattern 4 (#156). */
    TRIANGLE_4("triangle_4",
        new Corner[]{Corner.TOP_RIGHT, Corner.TOP_LEFT, Corner.BOTTOM_LEFT},
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT}
    ),
    
    // =========================================================================
    // Patterns from shield_triangles folder
    // =========================================================================
    
    /** Meshed pattern 1. */
    MESHED_1("meshed_1",
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, Corner.TOP_LEFT},
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT}
    ),
    
    /** Meshed pattern 2. */
    MESHED_2("meshed_2",
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, Corner.TOP_LEFT},
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT}
    ),
    
    /** Meshed pattern 3. */
    MESHED_3("meshed_3",
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, Corner.TOP_LEFT},
        new Corner[]{Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT}
    ),
    
    /** Meshed pattern 4. */
    MESHED_4("meshed_4",
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, Corner.TOP_LEFT},
        new Corner[]{Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT, Corner.BOTTOM_LEFT}
    ),
    
    /** Meshed pattern 5. */
    MESHED_5("meshed_5",
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.TOP_LEFT},
        new Corner[]{Corner.TOP_RIGHT, Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT}
    ),
    
    /** Meshed pattern 6. */
    MESHED_6("meshed_6",
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.TOP_LEFT},
        new Corner[]{Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT}
    ),
    
    /** Meshed pattern 7. */
    MESHED_7("meshed_7",
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.TOP_LEFT},
        new Corner[]{Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT, Corner.BOTTOM_LEFT}
    ),
    
    /** Parallelogram pattern (standard). */
    PARALLELOGRAM("parallelogram",
        new Corner[]{Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_LEFT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT}
    ),
    
    /** Triangle arrow pattern. */
    TRIANGLE_ARROW("triangle_arrow",
        new Corner[]{Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT},
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT}
    ),
    
    /** Triangle default pattern. */
    TRIANGLE_DEFAULT("triangle_default",
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT}
    ),
    
    /** Triangle facing pattern. */
    TRIANGLE_FACING("triangle_facing",
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.TOP_LEFT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT}
    ),
    
    /** Triangle facing spaced pattern. */
    TRIANGLE_FACING_SPACED("triangle_facing_spaced",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT, Corner.BOTTOM_LEFT}
    ),
    
    /** Triangle hole pattern. */
    TRIANGLE_HOLE("triangle_hole",
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.TOP_LEFT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT}
    ),
    
    /** Triangle line pattern. */
    TRIANGLE_LINE("triangle_line",
        new Corner[]{Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_LEFT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT}
    ),
    
    /** Triangle rectangle pattern. */
    TRIANGLE_RECTANGLE("triangle_rectangle",
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_RIGHT, Corner.TOP_RIGHT, Corner.BOTTOM_LEFT}
    ),
    
    /** Triangle spaced pattern. */
    TRIANGLE_SPACED("triangle_spaced",
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT}
    ),
    
    /** Triangle square pattern. */
    TRIANGLE_SQUARE("triangle_square",
        new Corner[]{Corner.TOP_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT},
        new Corner[]{Corner.TOP_LEFT, Corner.BOTTOM_RIGHT, Corner.BOTTOM_LEFT}
    ),
    
    /** Triangle triangle pattern. */
    TRIANGLE_TRIANGLE("triangle_triangle",
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_LEFT, Corner.TOP_RIGHT},
        new Corner[]{Corner.BOTTOM_LEFT, Corner.TOP_RIGHT, Corner.BOTTOM_RIGHT}
    );
    
    // =========================================================================
    // Default alias
    // =========================================================================
    
    /** Default pattern (alias for FILLED_1). */
    public static final QuadPattern DEFAULT = FILLED_1;
    
    // =========================================================================
    // Fields and Constructor
    // =========================================================================
    
    private final String id;
    private final Corner[] triangle1;
    private final Corner[] triangle2;
    private final int[][] cachedVertexOrder;
    
    QuadPattern(String id, Corner[] triangle1, Corner[] triangle2) {
        this.id = id;
        this.triangle1 = triangle1;
        this.triangle2 = triangle2;
        // Pre-compute for renderer efficiency
        this.cachedVertexOrder = computeVertexOrder();
    }
    
    private int[][] computeVertexOrder() {
        int[] t1 = new int[]{triangle1[0].index, triangle1[1].index, triangle1[2].index};
        int[] t2 = new int[]{triangle2[0].index, triangle2[1].index, triangle2[2].index};
        return new int[][]{t1, t2};
    }
    
    // =========================================================================
    // VertexPattern Implementation
    // =========================================================================
    
    @Override
    public String id() {
        return id;
    }
    
    @Override
    public CellType cellType() {
        return CellType.QUAD;
    }
    
    @Override
    public boolean shouldRender(int index, int total) {
        // Quad patterns always render - they just change vertex arrangement
        return true;
    }
    
    /**
     * Gets the number of triangles this pattern produces.
     */
    public int triangleCount() { return cachedVertexOrder.length; }
    
    @Override
    public int[][] getVertexOrder() {
        return cachedVertexOrder;
    }
    
    // =========================================================================
    // Semantic Access (for debugging/logging)
    // =========================================================================
    
    /**
     * Gets the first triangle's corners (semantic).
     */
    public Corner[] triangle1() {
        return triangle1;
    }
    
    /**
     * Gets the second triangle's corners (semantic).
     */
    public Corner[] triangle2() {
        return triangle2;
    }
    
    /**
     * Gets all triangles as an array of Corner arrays.
     * Used by MeshBuilder for iteration.
     */
    public Corner[][] triangles() {
        return new Corner[][]{triangle1, triangle2};
    }
    
    /**
     * Returns human-readable description of the pattern.
     */
    public String describe() {
        return String.format("%s→%s→%s | %s→%s→%s",
            triangle1[0].shortName(), triangle1[1].shortName(), triangle1[2].shortName(),
            triangle2[0].shortName(), triangle2[1].shortName(), triangle2[2].shortName()
        );
    }
    
    // =========================================================================
    // Static Utilities
    // =========================================================================
    
    /**
     * Parses a pattern from ID string.
     * @return The pattern, or null if not found
     */
    public static QuadPattern fromId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase().trim();
        for (QuadPattern p : values()) {
            if (p.id.equals(lower)) return p;
        }
        return null;
    }
    
    /**
     * Lists all pattern IDs.
     */
    public static String[] ids() {
        QuadPattern[] values = values();
        String[] ids = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            ids[i] = values[i].id;
        }
        return ids;
    }
    
    // =========================================================================
    // Corner Enum (Semantic Vertex Names)
    // =========================================================================
    
    /**
     * Semantic names for quad vertices.
     * 
     * <pre>
     * TOP_LEFT (0) ──── TOP_RIGHT (1)
     *      │                 │
     * BOTTOM_LEFT (2) ─ BOTTOM_RIGHT (3)
     * </pre>
     */
    public enum Corner {
        TOP_LEFT(0, "TL"),
        TOP_RIGHT(1, "TR"),
        BOTTOM_LEFT(2, "BL"),
        BOTTOM_RIGHT(3, "BR");
        
        /** Index used in vertex arrays */
        public final int index;
        private final String shortName;
        
        Corner(int index, String shortName) {
            this.index = index;
            this.shortName = shortName;
        }
        
        /** Short name for logging (TL, TR, BL, BR) */
        public String shortName() { return shortName; }
    }
}
