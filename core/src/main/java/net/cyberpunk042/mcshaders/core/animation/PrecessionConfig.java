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

import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Configuration for axis precession animation - the "lighthouse" wobbling effect.
 * 
 * <h2>Concept</h2>
 * <p>Precession causes the shape's axis to trace a cone around the world's Y axis.
 * This is separate from SpinConfig which rotates the shape around its OWN axis.
 * Combined, you can have a spinning shape whose axis also wobbles.</p>
 * 
 * <h2>Visual Effect</h2>
 * <pre>
 *                    World Y Axis
 *                         │
 *                         │   ╱ Shape Axis
 *                         │  ╱  tilted at 'tiltAngle'
 *                         │ ╱
 *                         │╱
 *            ─────────────┴───────────── ← Precession circle
 *                        ╱│
 *                       ╱ │
 *                      ╱  │
 *                     ╱   │
 *              Shape axis rotates around Y
 *              at 'speed' revolutions per second
 * </pre>
 * 
 * <h2>Use Cases</h2>
 * <ul>
 *   <li>Pulsar beams sweeping through space</li>
 *   <li>Lighthouse effects</li>
 *   <li>Gyroscopic wobble on any shape</li>
 *   <li>Orbital axis tilt animations</li>
 * </ul>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "precession": {
 *   "enabled": true,
 *   "tiltAngle": 15.0,
 *   "speed": 0.1,
 *   "phase": 0.0
 * }
 * </pre>
 * 
 * @see SpinConfig
 * @see Animation
 */
public record PrecessionConfig(
    /** Is precession animation active? */
    @JsonField(skipIfDefault = true) 
    boolean enabled,
    
    /** 
     * Tilt angle in degrees - how far the axis tips from vertical.
     * Range: 0-90 (default: 15)
     */
    @Range(ValueRange.DEGREES) 
    @JsonField(skipIfDefault = true, defaultValue = "15")
    float tiltAngle,
    
    /** 
     * Rotation speed in revolutions per second. 
     * Positive = CCW when viewed from above, negative = CW.
     * Range: -10 to 10 (default: 0.1)
     */
    @Range(ValueRange.UNBOUNDED)
    @JsonField(skipIfDefault = true, defaultValue = "0.1")
    float speed,
    
    /** 
     * Starting phase offset from 0 to 1.
     * 0 = axis tipped toward +X initially
     * 0.25 = axis tipped toward +Z initially
     * etc.
     */
    @Range(ValueRange.NORMALIZED)
    @JsonField(skipIfDefault = true)
    float phase
) {
    // =========================================================================
    // Presets
    // =========================================================================
    
    /** No precession (default). */
    public static final PrecessionConfig NONE = new PrecessionConfig(false, 0, 0, 0);
    
    /** Default precession (15° tilt, slow wobble). */
    public static final PrecessionConfig DEFAULT = new PrecessionConfig(true, 15.0f, 0.1f, 0);
    
    /** Gentle wobble (subtle effect). */
    public static final PrecessionConfig GENTLE = new PrecessionConfig(true, 5.0f, 0.05f, 0);
    
    /** Fast precession (rapid lighthouse effect). */
    public static final PrecessionConfig FAST = new PrecessionConfig(true, 20.0f, 0.5f, 0);
    
    /** Wide precession (large cone sweep). */
    public static final PrecessionConfig WIDE = new PrecessionConfig(true, 45.0f, 0.08f, 0);
    
    /** Tight precession (subtle wobble). */
    public static final PrecessionConfig TIGHT = new PrecessionConfig(true, 5.0f, 0.2f, 0);
    
    /** Pulsar-style fast tight wobble. */
    public static final PrecessionConfig PULSAR = new PrecessionConfig(true, 10.0f, 1.0f, 0);
    
    public static PrecessionConfig defaults() { return NONE; }
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /**
     * Creates simple precession with tilt and speed.
     * @param tiltAngle Angle from vertical in degrees
     * @param speed Revolutions per second
     */
    public static PrecessionConfig of(float tiltAngle, float speed) {
        return new PrecessionConfig(true, tiltAngle, speed, 0);
    }
    
    /**
     * Creates precession with phase offset.
     * @param tiltAngle Angle from vertical in degrees
     * @param speed Revolutions per second
     * @param phase Starting phase (0-1)
     */
    public static PrecessionConfig withPhase(float tiltAngle, float speed, float phase) {
        return new PrecessionConfig(true, tiltAngle, speed, phase);
    }
    
    // =========================================================================
    // Utility Methods
    // =========================================================================
    
    /** Whether precession is active and has effect. */
    public boolean isActive() {
        return enabled && tiltAngle > 0 && speed != 0;
    }
    
    /** 
     * Calculates the current precession rotation angle around Y axis at a given time.
     * @param time Time in seconds
     * @return Angle in radians around Y axis
     */
    public float getCurrentAngle(float time) {
        if (!enabled || speed == 0) return phase * (float)(Math.PI * 2);
        return (float)((phase + time * speed) * Math.PI * 2);
    }
    
    /** Gets the tilt angle in radians. */
    public float tiltRadians() {
        return (float) Math.toRadians(tiltAngle);
    }
    
    // =========================================================================
    // JSON
    // =========================================================================
    
    
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            .enabled(enabled)
            .tiltAngle(tiltAngle)
            .speed(speed)
            .phase(phase);
    }
    
    public static class Builder {
        private boolean enabled = true;
        private float tiltAngle = 15.0f;
        private float speed = 0.1f;
        private float phase = 0;
        
        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder tiltAngle(float v) { this.tiltAngle = v; return this; }
        public Builder speed(float v) { this.speed = v; return this; }
        public Builder phase(float v) { this.phase = v; return this; }
        
        public PrecessionConfig build() {
            return new PrecessionConfig(enabled, tiltAngle, speed, phase);
        }
    }
}
