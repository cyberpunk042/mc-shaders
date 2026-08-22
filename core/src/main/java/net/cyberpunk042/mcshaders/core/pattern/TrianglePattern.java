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
 * Patterns for triangle-based tessellation (icosphere, polyhedra).
 * 
 * <p>Controls which triangular cells are rendered and vertex winding.
 * Uses semantic {@link Vertex} enum for readability.
 * 
 * <h2>Vertex Layout</h2>
 * <pre>
 *        A (0)
 *       / \
 *      /   \
 *     /     \
 *   B (1)───C (2)
 * </pre>
 * 
 * <h2>Available Patterns</h2>
 * <ul>
 *   <li><b>FULL</b>: All triangles rendered (default)</li>
 *   <li><b>ALTERNATING</b>: Every other triangle</li>
 *   <li><b>INVERTED</b>: Flip winding order (normals)</li>
 *   <li><b>SPARSE</b>: Every third triangle</li>
 *   <li><b>QUARTER</b>: Every fourth triangle</li>
 *   <li><b>FAN</b>: Radial fan pattern</li>
 * </ul>
 * 
 * @see VertexPattern
 * @see Vertex
 */
public enum TrianglePattern implements VertexPattern {
    
    /** All triangles rendered normally: A → B → C */
    FULL("full", 1,
        new Vertex[]{Vertex.A, Vertex.B, Vertex.C}
    ),
    
    /** Every other triangle: A → B → C */
    ALTERNATING("alternating", 2,
        new Vertex[]{Vertex.A, Vertex.B, Vertex.C}
    ),
    
    /** Inverted winding (flip normals): A → C → B */
    INVERTED("inverted", 1,
        new Vertex[]{Vertex.A, Vertex.C, Vertex.B}
    ),
    
    /** Every third triangle: A → B → C */
    SPARSE("sparse", 3,
        new Vertex[]{Vertex.A, Vertex.B, Vertex.C}
    ),
    
    /** Every fourth triangle: A → B → C */
    QUARTER("quarter", 4,
        new Vertex[]{Vertex.A, Vertex.B, Vertex.C}
    ),
    
    /** Fan pattern (alternating skip): A → B → C */
    FAN("fan", 2,
        new Vertex[]{Vertex.A, Vertex.B, Vertex.C}
    ),
    
    /** Standard CCW winding for polyhedra: B → A → C */
    STANDARD("standard", 1,
        new Vertex[]{Vertex.B, Vertex.A, Vertex.C}
    );
    
    /** Default pattern - STANDARD works correctly for polyhedra faces. */
    public static final TrianglePattern DEFAULT = STANDARD;
    
    // =========================================================================
    // Fields and Constructor
    // =========================================================================
    
    private final String id;
    private final int skipInterval;
    private final Vertex[] vertices;
    private final int[][] cachedVertexOrder;
    
    TrianglePattern(String id, int skipInterval, Vertex[] vertices) {
        this.id = id;
        this.skipInterval = skipInterval;
        this.vertices = vertices;
        this.cachedVertexOrder = computeVertexOrder();
    }
    
    private int[][] computeVertexOrder() {
        return new int[][]{{vertices[0].index, vertices[1].index, vertices[2].index}};
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
        return CellType.TRIANGLE;
    }
    
    @Override
    public boolean shouldRender(int index, int total) {
        return (index % skipInterval) == 0;
    }
    
    @Override
    public int[][] getVertexOrder() {
        return cachedVertexOrder;
    }
    
    // =========================================================================
    // Semantic Access (for debugging/logging)
    // =========================================================================
    
    /**
     * Gets the vertex winding order (semantic).
     */
    public Vertex[] vertices() {
        return vertices;
    }
    
    /**
     * Whether this pattern inverts the default winding.
     */
    public boolean isInverted() {
        return vertices[1] == Vertex.C && vertices[2] == Vertex.B;
    }
    
    /**
     * Returns human-readable description of the pattern.
     */
    public String describe() {
        return String.format("%s→%s→%s (skip=%d)",
            vertices[0].name(), vertices[1].name(), vertices[2].name(),
            skipInterval
        );
    }
    
    // =========================================================================
    // Static Utilities
    // =========================================================================
    
    /**
     * Parses a pattern from ID string.
     * @return The pattern, or null if not found
     */
    public static TrianglePattern fromId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase().trim();
        for (TrianglePattern p : values()) {
            if (p.id.equals(lower)) return p;
        }
        return null;
    }
    
    /**
     * Lists all pattern IDs.
     */
    public static String[] ids() {
        TrianglePattern[] values = values();
        String[] ids = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            ids[i] = values[i].id;
        }
        return ids;
    }
    
    // =========================================================================
    // Vertex Enum (Semantic Vertex Names)
    // =========================================================================
    
    /**
     * Semantic names for triangle vertices.
     * 
     * <pre>
     *        A (0)
     *       / \
     *     B (1)─C (2)
     * </pre>
     */
    public enum Vertex {
        /** Top vertex (apex) */
        A(0),
        /** Bottom-left vertex */
        B(1),
        /** Bottom-right vertex */
        C(2);
        
        /** Index used in vertex arrays */
        public final int index;
        
        Vertex(int index) {
            this.index = index;
        }
    }
}
