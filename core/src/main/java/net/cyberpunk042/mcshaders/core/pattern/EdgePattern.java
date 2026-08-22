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
 * Patterns for edge-based rendering (cages, wireframes).
 * 
 * <p>Controls which edges are rendered and how.
 * Edges are line segments (2 vertices).
 * 
 * <h2>Available Patterns</h2>
 * <ul>
 *   <li><b>FULL</b>: All edges rendered (default)</li>
 *   <li><b>LATITUDE</b>: Horizontal lines only</li>
 *   <li><b>LONGITUDE</b>: Vertical lines only</li>
 *   <li><b>SPARSE</b>: Every other edge</li>
 *   <li><b>MINIMAL</b>: Every fourth edge</li>
 *   <li><b>DASHED</b>: Alternating segments</li>
 *   <li><b>GRID</b>: Both lat and lon</li>
 * </ul>
 * 
 * @see VertexPattern
 */
public enum EdgePattern implements VertexPattern {
    
    /** All edges rendered. */
    FULL("full", 1, true, true),
    
    /** Latitude lines only (horizontal). */
    LATITUDE("latitude", 1, true, false),
    
    /** Longitude lines only (vertical). */
    LONGITUDE("longitude", 1, false, true),
    
    /** Every other edge. */
    SPARSE("sparse", 2, true, true),
    
    /** Every fourth edge. */
    MINIMAL("minimal", 4, true, true),
    
    /** Alternating segments. */
    DASHED("dashed", 2, true, true),
    
    /** Both lat and lon (same as FULL). */
    GRID("grid", 1, true, true);
    
    /** Default pattern (alias for FULL). */
    public static final EdgePattern DEFAULT = FULL;
    
    private final String id;
    private final int skipInterval;
    private final boolean renderLatitude;
    private final boolean renderLongitude;
    
    EdgePattern(String id, int skipInterval, boolean renderLatitude, boolean renderLongitude) {
        this.id = id;
        this.skipInterval = skipInterval;
        this.renderLatitude = renderLatitude;
        this.renderLongitude = renderLongitude;
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
        return CellType.EDGE;
    }
    
    @Override
    public boolean shouldRender(int index, int total) {
        return (index % skipInterval) == 0;
    }
    
    @Override
    public int[][] getVertexOrder() {
        // Edges are lines: 2 vertices
        return new int[][]{{0, 1}};
    }
    
    // =========================================================================
    // Additional Edge-Specific Methods
    // =========================================================================
    
    /** Whether to render latitude (horizontal) lines. */
    public boolean renderLatitude() { return renderLatitude; }
    
    /** Whether to render longitude (vertical) lines. */
    public boolean renderLongitude() { return renderLongitude; }
    
    // =========================================================================
    // Static Utilities
    // =========================================================================
    
    /**
     * Parses a pattern from ID string.
     * @return The pattern, or null if not found
     */
    public static EdgePattern fromId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase().trim();
        for (EdgePattern p : values()) {
            if (p.id.equals(lower)) return p;
        }
        return null;
    }
    
    /**
     * Lists all pattern IDs.
     */
    public static String[] ids() {
        EdgePattern[] values = values();
        String[] ids = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            ids[i] = values[i].id;
        }
        return ids;
    }
}
