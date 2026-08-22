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

import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Configuration for jet precession - the "lighthouse" wobbling effect.
 * 
 * <h2>Concept</h2>
 * <p>Precession makes the jet axis trace a cone around the field's main axis.
 * This is separate from SpinConfig - the jet can precess while the parent
 * primitive spins independently.</p>
 * 
 * <h2>Physics</h2>
 * <p>In real pulsars, the magnetic axis (jet) is tilted relative to the
 * rotation axis, causing the jets to sweep like a lighthouse beam.</p>
 * 
 * <pre>
 *                    Spin Axis
 *                       │
 *                       │   ╱ Magnetic Axis (Jet)
 *                       │  ╱  tilted at 'tiltAngle'
 *                       │ ╱
 *                       │╱
 *          ─────────────┴───────────── ← Precession circle
 *                      ╱│
 *                     ╱ │
 *                    ╱  │
 *                   ╱   │
 *            Jet axis rotates around spin axis
 *            at 'speed' revolutions per second
 * </pre>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "precession": {
 *   "enabled": true,
 *   "tiltAngle": 15.0,
 *   "speed": 0.1,
 *   "phase": 0.0,
 *   "syncWithParent": false
 * }
 * </pre>
 * 
 * @see JetShape
 */
public record JetPrecession(
    @JsonField(skipIfDefault = true, defaultValue = "true") boolean enabled,
    @Range(ValueRange.DEGREES) float tiltAngle,
    @Range(ValueRange.UNBOUNDED) float speed,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float phase,
    @JsonField(skipIfDefault = true) boolean syncWithParent
) {
    
    // =========================================================================
    // Presets
    // =========================================================================
    
    /** No precession (default). */
    public static final JetPrecession NONE = new JetPrecession(
        false, 0, 0, 0, false);
    
    /** Default precession (15° tilt, slow wobble). */
    public static final JetPrecession DEFAULT = new JetPrecession(
        true, 15.0f, 0.1f, 0, false);
    
    /** Fast precession (rapid lighthouse effect). */
    public static final JetPrecession FAST = new JetPrecession(
        true, 20.0f, 0.5f, 0, false);
    
    /** Wide precession (large cone sweep). */
    public static final JetPrecession WIDE = new JetPrecession(
        true, 45.0f, 0.08f, 0, false);
    
    /** Tight precession (subtle wobble). */
    public static final JetPrecession TIGHT = new JetPrecession(
        true, 5.0f, 0.2f, 0, false);
    
    /** Synchronized with parent spin. */
    public static final JetPrecession SYNCED = new JetPrecession(
        true, 15.0f, 0.1f, 0, true);
    
    public static JetPrecession defaults() { return NONE; }
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /**
     * Creates simple precession with tilt and speed.
     * @param tiltAngle Angle from vertical in degrees
     * @param speed Revolutions per second
     */
    public static JetPrecession of(float tiltAngle, float speed) {
        return new JetPrecession(true, tiltAngle, speed, 0, false);
    }
    
    /**
     * Creates precession with phase offset.
     * @param tiltAngle Angle from vertical in degrees
     * @param speed Revolutions per second
     * @param phase Starting phase (0-1)
     */
    public static JetPrecession withPhase(float tiltAngle, float speed, float phase) {
        return new JetPrecession(true, tiltAngle, speed, phase, false);
    }
    
    // =========================================================================
    // Utility Methods
    // =========================================================================
    
    /** Whether precession is active and has effect. */
    public boolean isActive() {
        return enabled && tiltAngle > 0 && speed != 0;
    }
    
    /** Calculates the current precession angle at a given time. */
    public float getCurrentAngle(float time) {
        if (!enabled || speed == 0) return 0;
        // Convert speed (rev/sec) to radians per tick (20 ticks = 1 sec)
        float radiansPerTick = (float) (speed * 2 * Math.PI / 20.0);
        return (phase * 2 * (float) Math.PI) + (time * radiansPerTick);
    }
    
    /** Gets the tilt angle in radians. */
    public float tiltRadians() {
        return (float) Math.toRadians(tiltAngle);
    }
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            .enabled(enabled)
            .tiltAngle(tiltAngle)
            .speed(speed)
            .phase(phase)
            .syncWithParent(syncWithParent);
    }
    
    public static class Builder {
        private boolean enabled = true;
        private float tiltAngle = 15.0f;
        private float speed = 0.1f;
        private float phase = 0;
        private boolean syncWithParent = false;
        
        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder tiltAngle(float v) { this.tiltAngle = v; return this; }
        public Builder speed(float v) { this.speed = v; return this; }
        public Builder phase(float v) { this.phase = v; return this; }
        public Builder syncWithParent(boolean v) { this.syncWithParent = v; return this; }
        
        public JetPrecession build() {
            return new JetPrecession(enabled, tiltAngle, speed, phase, syncWithParent);
        }
    }
}
