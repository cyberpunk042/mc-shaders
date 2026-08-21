package net.cyberpunk042.mcshaders.core.pattern;

/**
 * Defines the fundamental cell types produced by shape tessellation.
 * 
 * <p>Each shape produces cells of a specific type, which determines
 * what vertex patterns can be applied to it.</p>
 * 
 * <h3>Usage by Shape</h3>
 * <ul>
 *   <li>QUAD - Sphere (lat/lon), Prism sides, Cylinder sides</li>
 *   <li>SEGMENT - Ring surface</li>
 *   <li>SECTOR - Disc surface, Cylinder caps, Cone base</li>
 *   <li>EDGE - Any shape in wireframe/cage mode</li>
 *   <li>TRIANGLE - Polyhedron (some), Icosphere, Sphere poles</li>
 * </ul>
 * 
 * @see VertexPattern
 * @see QuadPattern
 * @see SegmentPattern
 * @see SectorPattern
 * @see EdgePattern
 * @see TrianglePattern
 */
public enum CellType {
    /** 4-corner cells (sphere lat/lon, prism sides, cylinder sides) */
    QUAD,
    
    /** Arc segments around a circle (rings) */
    SEGMENT,
    
    /** Radial pie slices (discs, cylinder caps) */
    SECTOR,
    
    /** Line segments (wireframe/cage mode) */
    EDGE,
    
    /** 3-corner cells (icosphere, some polyhedra, sphere poles) */
    TRIANGLE;
    
    /**
     * Parse from string (case-insensitive).
     * @param id The string identifier
     * @return Matching CellType, or QUAD if not found
     */
    public static CellType fromId(String id) {
        if (id == null || id.isEmpty()) return QUAD;
        try {
            return valueOf(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return QUAD;
        }
    }
}
