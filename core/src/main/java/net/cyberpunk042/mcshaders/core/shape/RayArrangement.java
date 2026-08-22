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
 * Arrangement pattern for rays in a RaysShape.
 * 
 * <p>Determines how rays are positioned and oriented in 3D space:</p>
 * <ul>
 *   <li>{@link #RADIAL} - 2D star pattern on XZ plane, pointing outward</li>
 *   <li>{@link #SPHERICAL} - 3D distribution pointing outward from center</li>
 *   <li>{@link #PARALLEL} - All rays pointing same direction</li>
 *   <li>{@link #CONVERGING} - All rays pointing toward center</li>
 *   <li>{@link #DIVERGING} - All rays pointing away from center</li>
 * </ul>
 */
public enum RayArrangement {
    /**
     * Rays emanating from center outward on the XZ plane (2D star pattern).
     * Origin at center, rays point outward radially.
     */
    RADIAL,
    
    /**
     * Rays emanating in all 3D directions from center (like hedgehog spines).
     * Uniform spherical distribution using fibonacci lattice.
     */
    SPHERICAL,
    
    /**
     * All rays pointing the same direction (parallel beams).
     * Rays are distributed on a grid perpendicular to their direction.
     */
    PARALLEL,
    
    /**
     * All rays pointing toward center (absorption effect).
     * Rays start at outer radius and converge to inner radius.
     */
    CONVERGING,
    
    /**
     * All rays pointing away from center (emission effect).
     * Rays start at inner radius and diverge outward.
     */
    DIVERGING;
    
    /**
     * Default arrangement.
     */
    public static RayArrangement defaultArrangement() {
        return RADIAL;
    }
    
    /**
     * Parse from string (case-insensitive).
     */
    public static RayArrangement fromString(String s) {
        if (s == null) return RADIAL;
        try {
            return valueOf(s.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return RADIAL;
        }
    }
}
