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

/**
 * Configuration for ray TWIST animation (axial rotation).
 * 
 * <p>Twist animation rotates the ray around its own lengthwise axis.
 * This is most visible when the ray has a 3D shape like CORKSCREW or SPRING
 * (from {@link net.cyberpunk042.mcshaders.core.shape.RayLineShape}).</p>
 * 
 * <h2>Twist Modes</h2>
 * <ul>
 *   <li><b>TWIST</b>: Continuous rotation around ray axis</li>
 *   <li><b>OSCILLATE_TWIST</b>: Twist back and forth</li>
 *   <li><b>WIND_UP</b>: Progressive twist increasing over time</li>
 *   <li><b>UNWIND</b>: Progressive untwist decreasing over time</li>
 *   <li><b>SPIRAL_TWIST</b>: Different parts twist at different rates</li>
 * </ul>
 * 
 * <h2>Visual Effect</h2>
 * <p>For a corkscrew-shaped ray, twist animation makes it look like it's drilling
 * or spinning. For rays with visual markers or textures, it creates rotation.</p>
 * 
 * <h2>Stacking</h2>
 * <p>Twist STACKS with Motion and Wiggle. A ray can oscillate, wiggle,
 * AND twist simultaneously for complex effects.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "rayTwist": {
 *   "mode": "TWIST",
 *   "speed": 1.0,
 *   "amount": 360.0,
 *   "phaseOffset": 0.0
 * }
 * </pre>
 * 
 * @see TwistMode
 * @see RayMotionConfig
 * @see RayWiggleConfig
 * @see Animation
 */
public record RayTwistConfig(
    @JsonField(skipIfDefault = true) TwistMode mode,
    @Range(ValueRange.UNBOUNDED) @JsonField(skipIfDefault = true) float speed,
    @Range(ValueRange.DEGREES) @JsonField(skipIfDefault = true, defaultValue = "360.0") float amount,
    @Range(ValueRange.DEGREES) @JsonField(skipIfDefault = true) float phaseOffset
) {
    // =========================================================================
    // Static Constants
    // =========================================================================
    
    /** No twist animation. */
    public static final RayTwistConfig NONE = new RayTwistConfig(
        TwistMode.NONE, 0f, 360f, 0f
    );
    
    /** Default continuous twist (one full rotation per cycle). */
    public static final RayTwistConfig TWIST = new RayTwistConfig(
        TwistMode.TWIST, 1f, 360f, 0f
    );
    
    /** Slow drilling effect. */
    public static final RayTwistConfig DRILL = new RayTwistConfig(
        TwistMode.TWIST, 0.5f, 720f, 0f
    );
    
    /** Oscillating twist - back and forth. */
    public static final RayTwistConfig OSCILLATE = new RayTwistConfig(
        TwistMode.OSCILLATE_TWIST, 1f, 90f, 0f
    );
    
    /** Winding up effect (increasing twist). */
    public static final RayTwistConfig WIND_UP = new RayTwistConfig(
        TwistMode.WIND_UP, 0.3f, 720f, 0f
    );
    
    /** Unwinding effect (decreasing twist). */
    public static final RayTwistConfig UNWIND = new RayTwistConfig(
        TwistMode.UNWIND, 0.3f, 720f, 0f
    );
    
    /** Spiral twist - different rates along length. */
    public static final RayTwistConfig SPIRAL = new RayTwistConfig(
        TwistMode.SPIRAL_TWIST, 1f, 180f, 0f
    );
    
    // =========================================================================
    // Queries
    // =========================================================================
    
    /** Whether twist animation is active. */
    public boolean isActive() {
        return mode != null && mode != TwistMode.NONE && speed != 0;
    }
    
    /** Whether this effect requires a visible ray shape (like CORKSCREW). */
    public boolean requiresVisibleShape() {
        return mode != null && mode.requiresVisibleShape();
    }
    
    /** Gets the twist amount in radians. */
    public float amountRadians() {
        return (float) Math.toRadians(amount);
    }
    
    /** Gets the phase offset in radians. */
    public float phaseOffsetRadians() {
        return (float) Math.toRadians(phaseOffset);
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
            .amount(amount).phaseOffset(phaseOffset);
    }
    
    public static class Builder {
        private TwistMode mode = TwistMode.NONE;
        private float speed = 1f;
        private float amount = 360f;
        private float phaseOffset = 0f;
        
        public Builder mode(TwistMode m) { this.mode = m; return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder amount(float a) { this.amount = a; return this; }
        public Builder amountRadians(float r) { this.amount = (float) Math.toDegrees(r); return this; }
        public Builder phaseOffset(float p) { this.phaseOffset = p; return this; }
        
        public RayTwistConfig build() {
            return new RayTwistConfig(mode, speed, amount, phaseOffset);
        }
    }
}
