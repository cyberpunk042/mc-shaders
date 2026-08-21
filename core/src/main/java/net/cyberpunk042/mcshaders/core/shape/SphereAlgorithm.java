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
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Defines sphere tessellation algorithms.
 * 
 * <p>Different algorithms produce different vertex distributions
 * and are suited for different use cases.</p>
 * 
 * <h3>Algorithm Comparison</h3>
 * <ul>
 *   <li><b>LAT_LON</b>: Traditional latitude/longitude grid. Best for patterns,
 *       partial spheres, and visibility masks. Has pole singularities.</li>
 *   <li><b>UV_SPHERE</b>: Standard UV-mapped sphere. Good general purpose.</li>
 *   <li><b>ICO_SPHERE</b>: Icosahedron subdivision. Uniform distribution,
 *       best for smooth appearance without pole artifacts.</li>
 *   <li><b>TYPE_A</b>: Overlapping axis-aligned cubes. Accurate, more elements.
 *       Best for close-up viewing.</li>
 *   <li><b>TYPE_E</b>: Rotated thin rectangles forming hexadecagon. Efficient,
 *       fewer elements. Best for distant objects or animations.</li>
 * </ul>
 * 
 * @see SphereShape
 */
public enum SphereAlgorithm {
    /** Latitude/longitude tessellation (DEFAULT) - best for patterns and partial spheres */
    LAT_LON("lat_lon", "Lat/Lon"),
    
    /** Standard UV-mapped sphere - good general purpose */
    UV_SPHERE("uv_sphere", "UV Sphere"),
    
    /** Icosahedron subdivision - uniform distribution, no pole artifacts */
    ICO_SPHERE("ico_sphere", "Icosphere"),
    
    /** Overlapping cubes projection - accurate, more elements, best for close-up */
    TYPE_A("type_a", "Type A (Accuracy)"),
    
    /** Rotated rectangles projection - efficient, fewer elements, best for distant */
    TYPE_E("type_e", "Type E (Efficiency)");
    
    private final String id;
    private final String label;
    
    SphereAlgorithm(String id, String label) {
        this.id = id;
        this.label = label;
    }
    
    /** String identifier for JSON/commands */
    public String id() { return id; }
    
    /** Display label for GUI */
    public String label() { return label; }
    
    @Override
    public String toString() { return label; }
    
    /** Returns true if this is the default algorithm */
    public boolean isDefault() { return this == LAT_LON; }
    
    /** Returns true if this algorithm supports partial spheres */
    public boolean supportsPartialSphere() {
        return this == LAT_LON || this == UV_SPHERE;
    }
    
    /** Returns true if this algorithm supports visibility patterns */
    public boolean supportsPatterns() {
        return this == LAT_LON;
    }
    
    /**
     * Parse from string (case-insensitive).
     * @param id Algorithm identifier
     * @return Matching algorithm, or LAT_LON as default
     */
    public static SphereAlgorithm fromId(String id) {
        if (id == null || id.isEmpty()) {
            return LAT_LON;
        }
        String normalized = id.toLowerCase().replace("-", "_").replace(" ", "_");
        
        // Support legacy "uv" alias
        if (normalized.equals("uv")) normalized = "uv_sphere";
        
        for (SphereAlgorithm algo : values()) {
            if (algo.id.equalsIgnoreCase(normalized) || algo.name().equalsIgnoreCase(normalized)) {
                return algo;
            }
        }
        return LAT_LON;
    }
}
