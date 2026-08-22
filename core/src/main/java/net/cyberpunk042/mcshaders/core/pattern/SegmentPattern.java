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
 * Patterns for segment-based tessellation (rings, arcs).
 * 
 * <p>Controls which segments are rendered around a ring.
 * Each segment is a quad connecting inner/outer radii.
 * 
 * <h2>Available Patterns</h2>
 * <ul>
 *   <li><b>FULL</b>: All segments rendered (default)</li>
 *   <li><b>ALTERNATING</b>: Every other segment</li>
 *   <li><b>SPARSE</b>: Every third segment</li>
 *   <li><b>QUARTER</b>: Every fourth segment</li>
 *   <li><b>REVERSED</b>: Reverse winding order</li>
 *   <li><b>ZIGZAG</b>: Alternating with reversed winding</li>
 *   <li><b>DASHED</b>: Short dashes with gaps</li>
 * </ul>
 * 
 * @see VertexPattern
 */
public enum SegmentPattern implements VertexPattern {
    
    /** All segments rendered normally. */
    FULL("full", 1, 0, false),
    
    /** Every other segment. */
    ALTERNATING("alternating", 2, 0, false),
    
    /** Every third segment. */
    SPARSE("sparse", 3, 0, false),
    
    /** Every fourth segment. */
    QUARTER("quarter", 4, 0, false),
    
    /** Reverse winding order (flip normals). */
    REVERSED("reversed", 1, 0, true),
    
    /** Alternating with reversed winding. */
    ZIGZAG("zigzag", 2, 0, true),
    
    /** Dashed pattern - 2 on, 2 off. */
    DASHED("dashed", 2, 1, false);
    
    /** Default pattern (alias for FULL). */
    public static final SegmentPattern DEFAULT = FULL;
    
    private final String id;
    private final int skipInterval;
    private final int phaseOffset;
    private final boolean reverseWinding;
    
    SegmentPattern(String id, int skipInterval, int phaseOffset, boolean reverseWinding) {
        this.id = id;
        this.skipInterval = skipInterval;
        this.phaseOffset = phaseOffset;
        this.reverseWinding = reverseWinding;
    }
    
    /**
     * Gets the skip interval for this pattern.
     * @return Skip interval (1 = every segment, 2 = every other, etc.)
     */
    public int skipInterval() { return skipInterval; }
    
    /**
     * Gets the phase offset for this pattern.
     */
    public int phaseOffset() { return phaseOffset; }
    
    /**
     * Whether this pattern reverses winding.
     */
    public boolean reverseWinding() { return reverseWinding; }
    
    // =========================================================================
    // VertexPattern Implementation
    // =========================================================================
    
    @Override
    public String id() {
        return id;
    }
    
    @Override
    public CellType cellType() {
        return CellType.SEGMENT;
    }
    
    @Override
    public boolean shouldRender(int index, int total) {
        int adjusted = (index + phaseOffset) % total;
        return (adjusted % skipInterval) == 0;
    }
    
    @Override
    public int[][] getVertexOrder() {
        // Segments are quads: inner0, inner1, outer0, outer1
        // Two triangles: {0,1,2}, {1,3,2}
        if (reverseWinding) {
            return new int[][]{{0, 2, 1}, {1, 2, 3}};  // Reversed
        }
        return new int[][]{{0, 1, 2}, {1, 3, 2}};  // Normal
    }
    
    // =========================================================================
    // Static Utilities
    // =========================================================================
    
    /**
     * Parses a pattern from ID string.
     * @return The pattern, or null if not found
     */
    public static SegmentPattern fromId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase().trim();
        for (SegmentPattern p : values()) {
            if (p.id.equals(lower)) return p;
        }
        return null;
    }
    
    /**
     * Lists all pattern IDs.
     */
    public static String[] ids() {
        SegmentPattern[] values = values();
        String[] ids = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            ids[i] = values[i].id;
        }
        return ids;
    }
}
