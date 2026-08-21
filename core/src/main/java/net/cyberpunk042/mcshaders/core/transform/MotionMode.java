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
package net.cyberpunk042.mcshaders.core.transform;

/**
 * Motion modes for orbital/oscillating motion per axis.
 * 
 * <p>Used by {@link AxisMotionConfig} to define how a primitive
 * moves along each axis independently. Combining different modes
 * on X, Y, Z creates complex 3D motion patterns (e.g., atom-like orbits).</p>
 * 
 * <h2>Motion Types</h2>
 * <table>
 *   <tr><th>Mode</th><th>Description</th><th>Use Case</th></tr>
 *   <tr><td>NONE</td><td>No motion on this axis</td><td>Constrained motion</td></tr>
 *   <tr><td>OSCILLATION</td><td>Linear back-and-forth</td><td>Pendulum, bobbing</td></tr>
 *   <tr><td>WAVE</td><td>Smooth sinusoidal motion</td><td>Natural breathing</td></tr>
 *   <tr><td>CIRCULAR</td><td>Full circular motion</td><td>Traditional orbit</td></tr>
 *   <tr><td>FIGURE_8</td><td>Lissajous figure-8</td><td>Complex orbit</td></tr>
 *   <tr><td>BOUNCE</td><td>Bouncing motion</td><td>Ball bounce</td></tr>
 * </table>
 * 
 * <h2>Atom Analogy</h2>
 * <pre>
 * // Electron in XZ plane with vertical wobble:
 * x: { mode: CIRCULAR, amplitude: 2.0, frequency: 0.5 }
 * y: { mode: WAVE, amplitude: 0.3, frequency: 1.0 }
 * z: { mode: CIRCULAR, amplitude: 2.0, frequency: 0.5, phase: 0.25 }
 * </pre>
 * 
 * @see AxisMotionConfig
 * @see OrbitConfig3D
 */
public enum MotionMode {
    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 1: Single-axis motion (affects only the set axis)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** No motion - primitive stays at base position. */
    NONE("None", "No motion", Tier.SINGLE_AXIS),
    
    /** Smooth sinusoidal wave on this axis. Like gentle breathing. */
    WAVE("Wave", "Smooth sine oscillation", Tier.SINGLE_AXIS),
    
    /** Linear back-and-forth motion. Sharp direction changes. */
    LINEAR("Linear", "Triangle zigzag", Tier.SINGLE_AXIS),
    
    /** Bouncing motion - fast out, slow at peak, fast return. */
    BOUNCE("Bounce", "Elastic bounce", Tier.SINGLE_AXIS),
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 2: 2D Orbit (circular motion in a plane using two axes)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Full 360° circular orbit. */
    CIRCULAR("Circular", "Full circle orbit", Tier.ORBIT_2D),
    
    /** Elliptical orbit (stretched circle). */
    ELLIPTIC("Elliptic", "Oval orbit", Tier.ORBIT_2D),
    
    /** Figure-8 / Lissajous pattern. */
    FIGURE_8("Figure 8", "Lissajous ∞", Tier.ORBIT_2D),
    
    /** Partial arc that swings back and forth. Like a pendulum. */
    PENDULUM("Pendulum", "Arc swing", Tier.ORBIT_2D),
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 3: 3D Compound motion (orbit + perpendicular modulation)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Tilted orbit plane that rocks back and forth. Electron shell effect. */
    WOBBLE("Wobble", "Rocking orbit plane", Tier.ORBIT_3D),
    
    /** Circular orbit with sine wave on perpendicular axis. DNA helix. */
    HELIX("Helix", "Spiral / spring", Tier.ORBIT_3D),
    
    /** Circular orbit with bounce on perpendicular axis. */
    ORBIT_BOUNCE("Orbit+Bounce", "Bouncing orbit", Tier.ORBIT_3D),
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 4: Radial modulation (orbit with pulsing radius)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Orbit with oscillating radius. Creates flower/petal patterns. */
    FLOWER("Flower", "Petal pattern", Tier.RADIAL),
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TIER 5: Epicyclic (orbit of orbit - spirograph)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Small orbit around a point that orbits the center. Spirograph. */
    EPICYCLIC("Epicyclic", "Spirograph", Tier.EPICYCLIC);
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Tier Classification
    // ═══════════════════════════════════════════════════════════════════════════
    
    public enum Tier {
        SINGLE_AXIS(1, false),  // Only affects the set axis
        ORBIT_2D(2, true),      // Creates 2D circular motion
        ORBIT_3D(3, true),      // 2D orbit + perpendicular motion
        RADIAL(2, true),        // 2D orbit with radius modulation
        EPICYCLIC(2, true);     // Compound orbit
        
        private final int axesAffected;
        private final boolean requires2D;
        
        Tier(int axes, boolean req2D) { 
            this.axesAffected = axes; 
            this.requires2D = req2D; 
        }
        
        public int axesAffected() { return axesAffected; }
        public boolean requires2D() { return requires2D; }
    }
    
    private final String displayName;
    private final String description;
    private final Tier tier;
    
    MotionMode(String displayName, String description, Tier tier) {
        this.displayName = displayName;
        this.description = description;
        this.tier = tier;
    }
    
    /** Display name for UI. */
    public String displayName() { return displayName; }
    
    /** Description for tooltips. */
    public String description() { return description; }
    
    /** The tier/category of this motion. */
    public Tier tier() { return tier; }
    
    /**
     * Evaluates the motion at a given time (sin component for 2D modes).
     * 
     * @param t Time value (normalized, typically 0-1 per cycle)
     * @return Displacement value between -1 and 1
     */
    public float evaluate(float t) {
        return switch (this) {
            // Tier 1: Single-axis
            case NONE -> 0f;
            case WAVE -> (float) Math.sin(t * Math.PI * 2);
            case LINEAR -> {
                // Triangle wave: linear back-and-forth
                float cycle = t - (float) Math.floor(t);
                yield cycle < 0.5f 
                    ? cycle * 4f - 1f       // -1 to 1
                    : 3f - cycle * 4f;      // 1 to -1
            }
            case BOUNCE -> {
                // Parabolic bounce: 0 → 1 → 0
                float cycle = t - (float) Math.floor(t);
                float x = cycle * 2f - 1f;
                yield 1f - x * x;
            }
            
            // Tier 2: 2D Orbit - sin component
            case CIRCULAR -> (float) Math.sin(t * Math.PI * 2);
            case ELLIPTIC -> (float) Math.sin(t * Math.PI * 2);
            case FIGURE_8 -> (float) Math.sin(t * Math.PI * 4); // Double frequency
            case PENDULUM -> {
                // Swing back and forth (sin of a triangle wave = partial arc swing)
                float swing = (float) Math.sin(t * Math.PI * 2);
                yield swing * 0.5f; // Reduced to partial arc
            }
            
            // Tier 3+: Complex modes - handled specially in OrbitConfig3D
            case WOBBLE, HELIX, ORBIT_BOUNCE, FLOWER, EPICYCLIC -> 
                (float) Math.sin(t * Math.PI * 2); // Base orbit sin
        };
    }
    
    /**
     * Gets the cosine component for 2D orbital modes.
     * For single-axis modes, returns 0.
     * 
     * @param t Time value
     * @return Cos displacement (0 for single-axis modes)
     */
    public float evaluateCos(float t) {
        return switch (this) {
            // Tier 1: Single-axis - no perpendicular motion
            case NONE, WAVE, LINEAR, BOUNCE -> 0f;
            
            // Tier 2: 2D Orbit - cos component
            case CIRCULAR -> (float) Math.cos(t * Math.PI * 2);
            case ELLIPTIC -> (float) Math.cos(t * Math.PI * 2) * 0.6f;
            case FIGURE_8 -> (float) Math.cos(t * Math.PI * 2);
            case PENDULUM -> {
                float swing = (float) Math.cos(t * Math.PI * 2);
                yield swing * 0.5f;
            }
            
            // Tier 3+: Complex modes - handled specially in OrbitConfig3D
            case WOBBLE, HELIX, ORBIT_BOUNCE, FLOWER, EPICYCLIC -> 
                (float) Math.cos(t * Math.PI * 2);
        };
    }
    
    /**
     * Whether this mode creates 2D orbital motion (affects 2 axes).
     */
    public boolean is2DOrbit() {
        return tier == Tier.ORBIT_2D || tier == Tier.RADIAL || tier == Tier.EPICYCLIC;
    }
    
    /**
     * Whether this mode creates 3D motion (affects 3 axes).
     * EPICYCLIC is included because tilt can produce perpendicular motion.
     */
    public boolean is3DOrbit() {
        return tier == Tier.ORBIT_3D || this == EPICYCLIC;
    }
    
    /**
     * Whether this mode needs secondary parameters (amplitude2, frequency2).
     */
    public boolean needsSecondaryParams() {
        return this == WOBBLE || this == HELIX || this == ORBIT_BOUNCE 
            || this == FLOWER || this == EPICYCLIC || this == PENDULUM
            || this == ELLIPTIC;
    }
    
    /**
     * Parse from string (case-insensitive).
     */
    public static MotionMode fromId(String id) {
        if (id == null || id.isEmpty()) return NONE;
        try {
            // Handle legacy OSCILLATION name
            if (id.equalsIgnoreCase("OSCILLATION")) return LINEAR;
            return valueOf(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
