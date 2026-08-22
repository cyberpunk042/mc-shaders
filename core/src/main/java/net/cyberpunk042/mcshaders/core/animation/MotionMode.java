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
 * Animation mode controlling per-ray POSITION movement in space.
 * 
 * <p>These modes animate how each individual ray MOVES in 3D space.
 * This is different from field-level transforms (spin, wobble) which
 * affect the entire primitive as one unit.</p>
 * 
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>NONE</b>: No position motion</li>
 *   <li><b>RADIAL_OSCILLATE</b>: Ray oscillates in/out radially</li>
 *   <li><b>RADIAL_DRIFT</b>: Ray slowly drifts outward/inward</li>
 *   <li><b>ANGULAR_OSCILLATE</b>: Ray sways side-to-side around center</li>
 *   <li><b>ANGULAR_DRIFT</b>: Ray slowly rotates around center</li>
 *   <li><b>FLOAT</b>: Ray bobs up/down on Y axis</li>
 *   <li><b>SWAY</b>: Ray tip waves while base stays fixed</li>
 *   <li><b>ORBIT</b>: Ray revolves around center axis</li>
 *   <li><b>JITTER</b>: Ray has random position noise</li>
 *   <li><b>PRECESS</b>: Ray axis traces a cone pattern (gyroscopic wobble)</li>
 *   <li><b>RIPPLE</b>: Radial wave propagation (kept from old API)</li>
 * </ul>
 * 
 * <h2>Stacking</h2>
 * <p>MotionMode STACKS with WiggleMode. A ray can oscillate radially
 * (MotionMode) while also wiggling like a snake (WiggleMode).</p>
 * 
 * @see RayMotionConfig
 * @see WiggleMode
 */
public enum MotionMode {
    /** No position motion. Ray stays in original position. */
    NONE("None"),
    
    /** Ray oscillates in/out radially. Sine wave displacement toward/away from center. */
    RADIAL_OSCILLATE("Radial Oscillate"),
    
    /** Ray slowly drifts outward or inward. Constant radial velocity. */
    RADIAL_DRIFT("Radial Drift"),
    
    /** Ray sways side-to-side angularly around center. Sine wave angular displacement. */
    ANGULAR_OSCILLATE("Angular Oscillate"),
    
    /** Ray slowly rotates around center. Constant angular velocity. */
    ANGULAR_DRIFT("Angular Drift"),
    
    /** Ray bobs up and down on Y axis. Vertical sine wave. */
    FLOAT("Float"),
    
    /** Ray tip waves while base stays fixed. Pivot/pendulum animation. */
    SWAY("Sway"),
    
    /** Ray revolves around center axis continuously. Orbital rotation. */
    ORBIT("Orbit"),
    
    /** Ray position has random noise. Jittery, unstable movement. */
    JITTER("Jitter"),
    
    /** Ray axis precesses - traces a cone pattern around its base orientation. */
    PRECESS("Precess"),
    
    /** Radial wave propagation displacing rays outward/inward. */
    RIPPLE("Ripple");
    
    private final String displayName;
    
    MotionMode(String displayName) {
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
     * Whether this mode operates radially (toward/away from center).
     */
    public boolean isRadial() {
        return this == RADIAL_OSCILLATE || this == RADIAL_DRIFT || this == RIPPLE;
    }
    
    /**
     * Whether this mode operates angularly (around center).
     */
    public boolean isAngular() {
        return this == ANGULAR_OSCILLATE || this == ANGULAR_DRIFT || this == ORBIT;
    }
    
    /**
     * Parses from string, case-insensitive. Supports legacy names.
     * @param value String to parse
     * @return Matching mode or NONE if not found
     */
    public static MotionMode fromString(String value) {
        if (value == null) return NONE;
        String upper = value.toUpperCase().replace(" ", "_").replace("-", "_");
        
        // Legacy name mapping for backward compatibility
        switch (upper) {
            case "LINEAR": return RADIAL_DRIFT;      // LINEAR → RADIAL_DRIFT
            case "OSCILLATE": return RADIAL_OSCILLATE; // OSCILLATE → RADIAL_OSCILLATE
            case "SPIRAL": return ORBIT;              // SPIRAL → ORBIT
        }
        
        try {
            return valueOf(upper);
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}

