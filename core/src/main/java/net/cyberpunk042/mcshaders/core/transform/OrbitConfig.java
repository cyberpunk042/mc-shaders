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

import net.cyberpunk042.mcshaders.core.animation.Axis;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Configuration for orbital/circular motion around an anchor point.
 * 
 * <p>When enabled, the primitive orbits around its anchor at the specified
 * radius and speed.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "orbit": {
 *   "enabled": true,
 *   "radius": 2.0,
 *   "speed": 0.5,
 *   "axis": "Y",
 *   "phase": 0.0
 * }
 * </pre>
 * 
 * @see Transform
 */
public record OrbitConfig(
    boolean enabled,
    @Range(ValueRange.RADIUS) float radius,
    @Range(ValueRange.UNBOUNDED) float speed,
    net.cyberpunk042.mcshaders.core.animation.Axis axis,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float phase
){
    /** Disabled orbit (static position). */
    public static final OrbitConfig NONE = new OrbitConfig(false, 0, 0, 
        net.cyberpunk042.mcshaders.core.animation.Axis.Y, 0);
    
    /** Default orbit (Y-axis, 1.0 radius, slow spin). */
    public static final OrbitConfig DEFAULT = new OrbitConfig(true, 1.0f, 0.02f,
        net.cyberpunk042.mcshaders.core.animation.Axis.Y, 0);
    
    /**
     * Creates a simple Y-axis orbit.
     * @param radius Distance from center
     * @param speed Rotation speed (radians per tick)
     */
    public static OrbitConfig yAxis(@Range(ValueRange.RADIUS) float radius, @Range(ValueRange.UNBOUNDED) float speed) {
        return new OrbitConfig(true, radius, speed, 
            net.cyberpunk042.mcshaders.core.animation.Axis.Y, 0);
    }
    
    /** Whether this orbit is active. */
    public boolean isActive() {
        return enabled && speed != 0;
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .enabled(enabled)
            .radius(radius)
            .speed(speed)
            .axis(axis)
            .phase(phase);
    }
    
    public static class Builder {
        private boolean enabled = true;
        private @Range(ValueRange.RADIUS) float radius = 1.0f;
        private @Range(ValueRange.UNBOUNDED) float speed = 0.02f;
        private net.cyberpunk042.mcshaders.core.animation.Axis axis = 
            net.cyberpunk042.mcshaders.core.animation.Axis.Y;
        private @Range(ValueRange.NORMALIZED) float phase = 0;
        
        public Builder enabled(boolean e) { this.enabled = e; return this; }
        public Builder radius(float r) { this.radius = r; return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder axis(net.cyberpunk042.mcshaders.core.animation.Axis a) { this.axis = a; return this; }
        public Builder phase(float p) { this.phase = p; return this; }
        
        public OrbitConfig build() {
            return new OrbitConfig(enabled, radius, speed, axis, phase);
        }
    }

    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    

}
