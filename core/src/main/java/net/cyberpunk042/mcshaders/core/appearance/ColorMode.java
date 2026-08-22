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
package net.cyberpunk042.mcshaders.core.appearance;

/**
 * Color rendering mode for primitives.
 * 
 * <p>Determines how colors are applied to vertices:</p>
 * <ul>
 *   <li>{@link #GRADIENT} - Uniform blend from primary to secondary (via colorBlend)</li>
 *   <li>{@link #CYCLING} - Animate through a color set over time</li>
 *   <li>{@link #MESH_GRADIENT} - Per-vertex gradient from primary to secondary</li>
 *   <li>{@link #MESH_RAINBOW} - Per-vertex rainbow spectrum across mesh</li>
 *   <li>{@link #RANDOM} - Random color(s) from a color set</li>
 *   <li>{@link #HEAT_MAP} - Color based on position/distance (inner=hot, outer=cold)</li>
 *   <li>{@link #RANDOM_PULSE} - Random color pulses/flashes</li>
 *   <li>{@link #BREATHE} - Saturation/brightness pulsing (breathing effect)</li>
 *   <li>{@link #REACTIVE} - Color responds to motion/distance dynamically</li>
 * </ul>
 */
public enum ColorMode {
    /**
     * Uniform blend from primary to secondary color.
     * Blend ratio controlled by colorBlend slider.
     * colorBlend=0 → pure primary, colorBlend=1 → pure secondary.
     */
    GRADIENT,
    
    /**
     * Animated cycling through a color set.
     * Cycles through colors in the selected ColorSet over time.
     * Uses timePhase for animation offset.
     */
    CYCLING,
    
    /**
     * Per-vertex gradient from primary to secondary color.
     * Color varies across the mesh based on gradient direction.
     * Creates smooth color transitions across the geometry.
     */
    MESH_GRADIENT,
    
    /**
     * Per-vertex rainbow spectrum across the mesh.
     * Full hue range distributed along gradient direction.
     * Can animate with timePhase for scrolling rainbow effect.
     */
    MESH_RAINBOW,
    
    /**
     * Random color selection from a color set.
     * Distribution controls whether uniform (one random for all)
     * or per-cell (each cell gets its own random color).
     */
    RANDOM,
    
    /**
     * Color based on position/distance from center.
     * Inner positions = hot colors (red/orange).
     * Outer positions = cold colors (blue/purple).
     * Good for rays to show energy intensity gradients.
     */
    HEAT_MAP,
    
    /**
     * Random color pulses/flashes.
     * Occasional bursts of random colors from the color set.
     * Creates sparkle/flash effect.
     */
    RANDOM_PULSE,
    
    /**
     * Saturation/brightness pulsing (breathing effect).
     * Color intensity smoothly oscillates.
     * Uses timePhase for animation speed.
     */
    BREATHE,
    
    /**
     * Color responds to motion/distance dynamically.
     * Can change based on player distance, velocity, or other factors.
     * Good for interactive visual feedback.
     */
    REACTIVE;
    
    /**
     * Default color mode.
     */
    public static ColorMode defaultMode() {
        return GRADIENT;
    }
    
    /**
     * Parse from string (case-insensitive, handles legacy SOLID).
     */
    public static ColorMode fromString(String s) {
        if (s == null) return GRADIENT;
        String upper = s.toUpperCase().replace(" ", "_").replace("-", "_");
        // Backward compatibility: SOLID → GRADIENT
        if ("SOLID".equals(upper)) return GRADIENT;
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return GRADIENT;
        }
    }
    
    /**
     * Whether this mode requires per-vertex color calculation.
     */
    public boolean isPerVertex() {
        return this == MESH_GRADIENT || this == MESH_RAINBOW || 
               this == RANDOM || this == HEAT_MAP || this == REACTIVE ||
               this == RANDOM_PULSE || this == BREATHE;
    }
    
    /**
     * Whether this mode uses animation/time.
     */
    public boolean isAnimated() {
        return this == CYCLING || this == MESH_RAINBOW || 
               this == RANDOM_PULSE || this == BREATHE;
    }
    
    /**
     * Whether this mode uses a color set.
     */
    public boolean usesColorSet() {
        return this == CYCLING || this == RANDOM || this == MESH_RAINBOW ||
               this == RANDOM_PULSE;
    }
    
    /**
     * Whether this mode uses gradient direction.
     */
    public boolean usesDirection() {
        return this == MESH_GRADIENT || this == MESH_RAINBOW || this == HEAT_MAP;
    }
    
    /**
     * Whether this mode is position-dependent (uses vertex position).
     */
    public boolean isPositionBased() {
        return this == HEAT_MAP || this == REACTIVE;
    }
}

