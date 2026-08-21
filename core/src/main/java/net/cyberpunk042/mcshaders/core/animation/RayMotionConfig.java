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

import net.cyberpunk042.mcshaders.core.serial.JsonField;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import org.joml.Vector3f;

/**
 * Configuration for ray MOTION animation (geometry transformation).
 * 
 * <p>Motion animation transforms ray vertex positions to create physical
 * movement in 3D space. Unlike Flow which affects visibility, Motion
 * changes where the rays actually are.</p>
 * 
 * <h2>Motion Modes</h2>
 * <ul>
 *   <li><b>LINEAR</b>: Rays translate in a direction (drifting)</li>
 *   <li><b>OSCILLATE</b>: Rays move back-and-forth (pendulum)</li>
 *   <li><b>SPIRAL</b>: Rays rotate around an axis (helical motion)</li>
 *   <li><b>RIPPLE</b>: Radial wave propagation (shockwave)</li>
 * </ul>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "rayMotion": {
 *   "mode": "SPIRAL",
 *   "speed": 1.0,
 *   "direction": [0, 1, 0],
 *   "amplitude": 0.5,
 *   "frequency": 2.0
 * }
 * </pre>
 * 
 * @see MotionMode
 * @see Animation
 */
public record RayMotionConfig(
    @JsonField(skipIfDefault = true) MotionMode mode,
    @Range(ValueRange.UNBOUNDED) @JsonField(skipIfDefault = true) float speed,
    @JsonField(skipIfDefault = true) float directionX,
    @JsonField(skipIfDefault = true) float directionY,
    @JsonField(skipIfDefault = true) float directionZ,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float amplitude,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "1.0") float frequency
) {
    // =========================================================================
    // Static Constants
    // =========================================================================
    
    /** No motion animation. */
    public static final RayMotionConfig NONE = new RayMotionConfig(
        MotionMode.NONE, 0f, 0f, 1f, 0f, 0f, 1f
    );
    
    /** Orbit rotation around center. */
    public static final RayMotionConfig SPIRAL = new RayMotionConfig(
        MotionMode.ORBIT, 45f, 0f, 1f, 0f, 0f, 1f
    );
    
    /** Oscillating radial motion. */
    public static final RayMotionConfig OSCILLATE = new RayMotionConfig(
        MotionMode.RADIAL_OSCILLATE, 1f, 0f, 1f, 0f, 0.3f, 2f
    );
    
    /** Radial ripple effect. */
    public static final RayMotionConfig RIPPLE = new RayMotionConfig(
        MotionMode.RIPPLE, 1f, 0f, 0f, 0f, 0.2f, 3f
    );
    
    /** Float up/down motion. */
    public static final RayMotionConfig LINEAR_UP = new RayMotionConfig(
        MotionMode.FLOAT, 0.5f, 0f, 1f, 0f, 0f, 1f
    );
    
    // =========================================================================
    // Queries
    // =========================================================================
    
    /** Whether motion animation is active. */
    public boolean isActive() {
        return mode != null && mode != MotionMode.NONE && speed != 0;
    }
    
    /** Gets direction as a Vector3f. */
    public Vector3f direction() {
        return new Vector3f(directionX, directionY, directionZ);
    }
    
    /** Gets normalized direction. */
    public Vector3f normalizedDirection() {
        Vector3f dir = direction();
        float len = dir.length();
        if (len > 0) dir.div(len);
        return dir;
    }
    
    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            .mode(mode).speed(speed)
            .direction(directionX, directionY, directionZ)
            .amplitude(amplitude).frequency(frequency);
    }
    
    public static class Builder {
        private MotionMode mode = MotionMode.NONE;
        private float speed = 1f;
        private float dirX = 0f, dirY = 1f, dirZ = 0f;
        private float amplitude = 0.3f;
        private float frequency = 1f;
        
        public Builder mode(MotionMode m) { this.mode = m; return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder direction(float x, float y, float z) {
            this.dirX = x; this.dirY = y; this.dirZ = z;
            return this;
        }
        public Builder direction(Vector3f v) {
            return direction(v.x, v.y, v.z);
        }
        public Builder amplitude(float a) { this.amplitude = a; return this; }
        public Builder frequency(float f) { this.frequency = f; return this; }
        
        public RayMotionConfig build() {
            return new RayMotionConfig(mode, speed, dirX, dirY, dirZ, amplitude, frequency);
        }
    }
}
