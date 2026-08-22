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
 * Patterns for radial/fan tessellation (discs, pie charts).
 * 
 * <p>Controls which sectors (pie slices) are rendered.
 * Each sector is a triangle from center to arc edge.
 * 
 * <h2>Available Patterns</h2>
 * <ul>
 *   <li><b>FULL</b>: All sectors rendered (default)</li>
 *   <li><b>HALF</b>: Every other sector (half coverage)</li>
 *   <li><b>QUARTERS</b>: Every fourth sector</li>
 *   <li><b>PINWHEEL</b>: Alternating sectors</li>
 *   <li><b>TRISECTOR</b>: Every third sector</li>
 *   <li><b>SPIRAL</b>: Alternating with offset</li>
 *   <li><b>CROSSHAIR</b>: Only cardinal directions</li>
 * </ul>
 * 
 * @see VertexPattern
 */
public enum SectorPattern implements VertexPattern {
    
    /** All sectors rendered. */
    FULL("full", 1, 0),
    
    /** Every other sector (pinwheel). */
    HALF("half", 2, 0),
    
    /** Every fourth sector. */
    QUARTERS("quarters", 4, 0),
    
    /** Same as half, different name. */
    PINWHEEL("pinwheel", 2, 0),
    
    /** Every third sector. */
    TRISECTOR("trisector", 3, 0),
    
    /** Alternating with offset. */
    SPIRAL("spiral", 2, 1),
    
    /** Only cardinal directions (every 4th, starting at 0). */
    CROSSHAIR("crosshair", 4, 0);
    
    /** Default pattern (alias for FULL). */
    public static final SectorPattern DEFAULT = FULL;
    
    private final String id;
    private final int skipInterval;
    private final int phaseOffset;
    
    SectorPattern(String id, int skipInterval, int phaseOffset) {
        this.id = id;
        this.skipInterval = skipInterval;
        this.phaseOffset = phaseOffset;
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
        return CellType.SECTOR;
    }
    
    @Override
    public boolean shouldRender(int index, int total) {
        if (skipInterval <= 1) return true;
        int adjusted = (index + phaseOffset) % total;
        return (adjusted % skipInterval) == 0;
    }
    
    @Override
    public int[][] getVertexOrder() {
        // Sectors are triangles: center, edge0, edge1
        return new int[][]{{0, 1, 2}};
    }
    
    // =========================================================================
    // Static Utilities
    // =========================================================================
    
    /**
     * Parses a pattern from ID string.
     * @return The pattern, or null if not found
     */
    public static SectorPattern fromId(String id) {
        if (id == null) return null;
        String lower = id.toLowerCase().trim();
        for (SectorPattern p : values()) {
            if (p.id.equals(lower)) return p;
        }
        return null;
    }
    
    /**
     * Lists all pattern IDs.
     */
    public static String[] ids() {
        SectorPattern[] values = values();
        String[] ids = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            ids[i] = values[i].id;
        }
        return ids;
    }
}
