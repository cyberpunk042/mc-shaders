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
package net.cyberpunk042.mcshaders.core.fill;

/**
 * Defines how a primitive's surface is rendered.
 * 
 * <p>Fill modes control whether the shape is solid, wireframe,
 * cage (structured grid), or points only.</p>
 * 
 * <h3>Visual Differences</h3>
 * <ul>
 *   <li>SOLID - Filled triangles, opaque or translucent</li>
 *   <li>WIREFRAME - All edges of tessellated mesh</li>
 *   <li>CAGE - Structured lines (lat/lon for sphere, edges for polyhedra)</li>
 *   <li>POINTS - Vertices only (future)</li>
 * </ul>
 * 
 * @see FillConfig
 */
public enum FillMode {
    /** Filled triangles - standard solid rendering */
    SOLID,
    
    /** All edges of the tessellated mesh visible */
    WIREFRAME,
    
    /** Structured grid lines (lat/lon for sphere, natural edges for others) */
    CAGE,
    
    /** Render vertices only as points (FUTURE) */
    POINTS;
    
    /**
     * Parse from string (case-insensitive).
     * @param id The string identifier
     * @return Matching FillMode, or SOLID if not found
     */
    public static FillMode fromId(String id) {
        if (id == null || id.isEmpty()) return SOLID;
        try {
            return valueOf(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return SOLID;
        }
    }
}
