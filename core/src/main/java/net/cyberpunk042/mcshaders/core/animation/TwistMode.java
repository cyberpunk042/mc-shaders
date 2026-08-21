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
package net.cyberpunk042.mcshaders.core.animation;

/**
 * Animation mode controlling ray ROTATION around its own axis.
 * 
 * <p>These modes animate how the ray twists/rotates around its lengthwise axis.
 * This is most visible when the ray has a 3D shape like {@code RayLineShape.CORKSCREW}
 * or {@code RayLineShape.SPRING}.</p>
 * 
 * <h2>Visual Effect</h2>
 * <p>For a corkscrew-shaped ray, twist animation makes it look like it's drilling
 * or spinning. For rays with visual markers, it creates a rotating effect.</p>
 * 
 * <h2>Stacking</h2>
 * <p>TwistMode animations STACK with other animations. A ray can wiggle, drift,
 * and twist simultaneously.</p>
 * 
 * @see RayTwistConfig
 * @see RayLineShape
 */
public enum TwistMode {
    
    /** No twist animation. Ray orientation is static. */
    NONE("None"),
    
    /** Ray rotates continuously around its own axis. Constant angular velocity. */
    TWIST("Twist"),
    
    /** Ray twists back and forth. Oscillating rotation. */
    OSCILLATE_TWIST("Oscillate"),
    
    /** Ray progressively twists more over time. Increasing rotation angle. */
    WIND_UP("Wind Up"),
    
    /** Ray progressively untwists over time. Decreasing rotation angle. */
    UNWIND("Unwind"),
    
    /** Different parts of the ray twist at different rates. Position-based twist. */
    SPIRAL_TWIST("Spiral Twist");
    
    private final String displayName;
    
    TwistMode(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Human-readable display name for UI.
     */
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
     * Whether this mode has a meaningful visual effect.
     * Twist is most visible on 3D ray shapes like CORKSCREW or SPRING.
     */
    public boolean requiresVisibleShape() {
        return this != NONE;
    }
    
    /**
     * Parses from string, case-insensitive.
     * @param value String to parse
     * @return Matching mode or NONE if not found
     */
    public static TwistMode fromString(String value) {
        if (value == null || value.isEmpty()) return NONE;
        try {
            return valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
