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
 * Field deformation mode for 3D ray shapes.
 * 
 * <p>Controls how ray shapes (droplets, etc.) are deformed based on their
 * distance from the field center. Creates gravitational lensing effects
 * like matter near a black hole.</p>
 * 
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>NONE</b>: No field deformation - shapes maintain their base form</li>
 *   <li><b>GRAVITATIONAL</b>: Shapes stretch toward center (spaghettification)</li>
 *   <li><b>REPULSION</b>: Shapes stretch away from center (expelled)</li>
 *   <li><b>TIDAL</b>: Shapes stretch both toward and away (tidal forces)</li>
 * </ul>
 * 
 * <h2>Distance Behavior</h2>
 * <p>The deformation intensity scales with distance:</p>
 * <ul>
 *   <li>At center (distance=0): Maximum deformation</li>
 *   <li>At outer edge (distance=outerRadius): Minimal deformation</li>
 * </ul>
 * 
 * <h2>Integration with RayFlowConfig</h2>
 * <p>When combined with RADIATE/ABSORB flow animation, the deformation
 * dynamically changes as shapes travel toward/away from center.</p>
 * 
 * @see RaysShape
 * @see net.cyberpunk042.mcshaders.core.animation.RayFlowConfig
 */
public enum FieldDeformationMode {
    /** No field deformation - shapes maintain their base form. */
    NONE("None"),
    
    /** Shapes stretch toward center (gravitational pull / spaghettification). */
    GRAVITATIONAL("Gravitational"),
    
    /** Shapes stretch away from center (repelled / expanding). */
    REPULSION("Repulsion"),
    
    /** Shapes stretch both toward and away (tidal forces). */
    TIDAL("Tidal");
    
    private final String displayName;
    
    FieldDeformationMode(String displayName) {
        this.displayName = displayName;
    }
    
    public String displayName() {
        return displayName;
    }
    
    /**
     * Whether this mode is active (not NONE).
     */
    public boolean isActive() {
        return this != NONE;
    }
    
    /**
     * Computes the axial stretch factor based on distance from center.
     * 
     * @param normalizedDistance Distance from center (0=at center, 1=at outer edge)
     * @param intensity Deformation intensity multiplier
     * @return Axial stretch factor (&gt;1 = elongated, &lt;1 = compressed)
     */
    public float computeStretch(float normalizedDistance, float intensity) {
        if (this == NONE || intensity <= 0.001f) {
            return 1.0f;
        }
        
        // Clamp distance to valid range
        float d = Math.max(0.01f, Math.min(1.0f, normalizedDistance));
        
        // Inverse distance for gravitational effect (stronger near center)
        float invDist = 1.0f / d;
        
        // Apply intensity scaling
        float factor = 1.0f + (invDist - 1.0f) * intensity;
        
        switch (this) {
            case GRAVITATIONAL -> {
                // Stretch along radial direction (toward center)
                return factor;
            }
            case REPULSION -> {
                // Compress along radial direction (pushed away)
                return 1.0f / factor;
            }
            case TIDAL -> {
                // Both stretch and compress (tidal elongation)
                // Strong stretch near center, mild compression far away
                if (d < 0.5f) {
                    return factor;
                } else {
                    return 1.0f / (1.0f + (factor - 1.0f) * 0.3f);
                }
            }
            default -> {
                return 1.0f;
            }
        }
    }
    
    /**
     * Parses from string, case-insensitive.
     * @param value String to parse
     * @return Matching mode or NONE if not found
     */
    public static FieldDeformationMode fromString(String value) {
        if (value == null) return NONE;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
