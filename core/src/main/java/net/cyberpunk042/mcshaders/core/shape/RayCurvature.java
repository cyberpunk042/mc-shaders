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
 * Defines how rays curve around the field center (global curvature pattern).
 * 
 * <p>This controls the ARRANGEMENT pattern of rays relative to the center point,
 * creating effects like vortex/whirlpool, spiral galaxy arms, or pinwheel patterns.</p>
 * 
 * <p>This is separate from {@link RayLineShape} which controls individual ray geometry.
 * A ray can be STRAIGHT but follow a VORTEX curvature around the center.</p>
 * 
 * <h2>Visual Examples</h2>
 * <ul>
 *   <li><b>NONE/RADIAL</b>: Rays point straight outward like sun rays</li>
 *   <li><b>VORTEX</b>: Rays curve into a whirlpool/accretion disk pattern</li>
 *   <li><b>SPIRAL_ARM</b>: Rays form spiral galaxy arm patterns</li>
 *   <li><b>PINWHEEL</b>: Rays curve like windmill/pinwheel blades</li>
 * </ul>
 * 
 * @see RaysShape
 * @see RayArrangement
 */
public enum RayCurvature {
    
    /** No curvature - rays point straight outward (radial). Default. */
    NONE("None"),
    
    /** Rays curve into a vortex/whirlpool pattern (like accretion disk). */
    VORTEX("Vortex"),
    
    /** Rays form spiral galaxy arm patterns. */
    SPIRAL_ARM("Spiral Arm"),
    
    /** Rays are tangent to circles around the center (perpendicular to radial). */
    TANGENTIAL("Tangential"),
    
    /** Rays follow logarithmic/golden spiral curves (nautilus shell pattern). */
    LOGARITHMIC("Logarithmic"),
    
    /** Rays curve like windmill/pinwheel blades. */
    PINWHEEL("Pinwheel"),
    
    /** Rays follow circular orbital paths around the center. */
    ORBITAL("Orbital");
    
    private final String displayName;
    
    RayCurvature(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Human-readable display name for UI.
     */
    public String displayName() {
        return displayName;
    }
    
    /**
     * Whether this curvature is active (not NONE).
     */
    public boolean isActive() {
        return this != NONE;
    }
    
    /**
     * Parses from string, case-insensitive.
     * @param value String to parse
     * @return Matching curvature or NONE if not found
     */
    public static RayCurvature fromString(String value) {
        if (value == null || value.isEmpty()) return NONE;
        try {
            return valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
