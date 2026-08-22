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
package net.cyberpunk042.mcshaders.core.visibility;

/**
 * Defines visibility mask patterns for primitives.
 * 
 * <p>Masks control which cells of a shape are visible, enabling
 * effects like bands, stripes, and checkerboard patterns.</p>
 * 
 * <h3>Phase 1 Parameters</h3>
 * <ul>
 *   <li>mask - The MaskType</li>
 *   <li>count - Number of divisions</li>
 *   <li>thickness - Band/stripe thickness (0-1)</li>
 * </ul>
 * 
 * <h3>Phase 2 Parameters (Future)</h3>
 * <ul>
 *   <li>offset - Pattern offset/phase</li>
 *   <li>invert - Invert visibility</li>
 *   <li>feather - Edge softness</li>
 *   <li>animate - Animate pattern</li>
 *   <li>animSpeed - Animation speed</li>
 * </ul>
 * 
 * @see VisibilityMask
 */
public enum MaskType {
    /** All cells visible (DEFAULT) */
    FULL("Full (No Mask)"),
    
    /** Horizontal stripes (latitude-based) */
    BANDS("Horizontal Bands"),
    
    /** Vertical stripes (longitude-based) */
    STRIPES("Vertical Stripes"),
    
    /** Checkerboard pattern */
    CHECKER("Checkerboard"),
    
    /** Radial gradient visibility (Phase 2) */
    RADIAL("Radial Gradient"),
    
    /** Linear gradient visibility (Phase 2) */
    GRADIENT("Linear Gradient"),
    
    /** Custom mask function (Future) */
    CUSTOM("Custom");
    
    private final String label;
    
    MaskType(String label) {
        this.label = label;
    }
    
    /** Display label for GUI */
    public String label() {
        return label;
    }
    
    @Override
    public String toString() {
        return label;
    }
    
    /**
     * Parse from string (case-insensitive).
     * @param id The string identifier
     * @return Matching MaskType, or FULL if not found
     */
    public static MaskType fromId(String id) {
        if (id == null || id.isEmpty()) return FULL;
        try {
            return valueOf(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return FULL;
        }
    }
}
