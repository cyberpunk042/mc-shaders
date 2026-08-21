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
 * Animation mode controlling ray UNDULATION and DEFORMATION.
 * 
 * <p>These modes animate how the ray itself wiggles, wobbles, or deforms
 * as a wave traveling along its length. This is different from {@link MotionMode}
 * which moves the ray's position in space.</p>
 * 
 * <h2>Categories</h2>
 * <ul>
 *   <li><b>Oscillating</b>: WIGGLE, WOBBLE, SNAKE - smooth back-and-forth motion</li>
 *   <li><b>Traveling</b>: RIPPLE, PULSE_WAVE - waves moving along ray length</li>
 *   <li><b>Chaotic</b>: FLUTTER, SHIMMER, WRITHE - random/complex motion</li>
 *   <li><b>Impulse</b>: WHIP - single dramatic motion</li>
 * </ul>
 * 
 * <h2>Stacking</h2>
 * <p>WiggleMode animations STACK with MotionMode animations. A ray can
 * oscillate radially (MotionMode) while also wiggling like a snake (WiggleMode).</p>
 * 
 * @see RayWiggleConfig
 * @see MotionMode
 */
public enum WiggleMode {
    
    /** No wiggle animation. Ray shape is static. */
    NONE("None"),
    
    /** Snake-like side-to-side motion. Traveling sine wave displacement. */
    WIGGLE("Wiggle"),
    
    /** Ray tips/wobbles back and forth around its base. Like a bobblehead. */
    WOBBLE("Wobble"),
    
    /** 3D tentacle-like motion. Combination of multiple sine waves in 3D. */
    WRITHE("Writhe"),
    
    /** Rapid subtle vibration. High-frequency, small amplitude displacement. */
    SHIMMER("Shimmer"),
    
    /** Wave travels from ray base to tip. Position-phased sine wave. */
    RIPPLE("Ripple"),
    
    /** Ray cracks like a whip. Single dramatic motion (one-shot or looped). */
    WHIP("Whip"),
    
    /** Rapid chaotic motion. Random high-frequency displacement. */
    FLUTTER("Flutter"),
    
    /** Fluid slithering motion. Multi-frequency sine wave blend. */
    SNAKE("Snake"),
    
    /** Thickness pulsing traveling along ray. Amplitude varies with position. */
    PULSE_WAVE("Pulse Wave");
    
    private final String displayName;
    
    WiggleMode(String displayName) {
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
     * Whether this mode requires the ray to have multiple segments.
     * All wiggle modes except NONE need intermediate vertices to show the deformation.
     */
    public boolean requiresMultipleSegments() {
        return this != NONE;
    }
    
    /**
     * Parses from string, case-insensitive.
     * @param value String to parse
     * @return Matching mode or NONE if not found
     */
    public static WiggleMode fromString(String value) {
        if (value == null || value.isEmpty()) return NONE;
        try {
            return valueOf(value.toUpperCase().replace(" ", "_").replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
