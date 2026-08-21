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
 * Configuration for ray WIGGLE animation (undulation/deformation).
 * 
 * <p>Wiggle animation deforms the ray itself, making it wiggle, wobble, shimmer,
 * or writhe like a snake or tentacle. Unlike Motion which moves the ray's position,
 * Wiggle changes the ray's shape dynamically.</p>
 * 
 * <h2>Wiggle Modes</h2>
 * <ul>
 *   <li><b>WIGGLE</b>: Snake-like side-to-side motion</li>
 *   <li><b>WOBBLE</b>: Ray tips back and forth around base</li>
 *   <li><b>WRITHE</b>: 3D tentacle-like motion</li>
 *   <li><b>SHIMMER</b>: Rapid subtle vibration</li>
 *   <li><b>RIPPLE</b>: Wave travels from base to tip</li>
 *   <li><b>WHIP</b>: Ray cracks like a whip</li>
 *   <li><b>FLUTTER</b>: Rapid chaotic motion</li>
 *   <li><b>SNAKE</b>: Fluid slithering motion</li>
 *   <li><b>PULSE_WAVE</b>: Thickness pulsing along ray</li>
 * </ul>
 * 
 * <h2>Stacking</h2>
 * <p>Wiggle STACKS with Motion. A ray can oscillate radially (Motion)
 * while also wiggling like a snake (Wiggle).</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "rayWiggle": {
 *   "mode": "WIGGLE",
 *   "speed": 2.0,
 *   "amplitude": 0.15,
 *   "frequency": 3.0,
 *   "phaseOffset": 0.0
 * }
 * </pre>
 * 
 * @see WiggleMode
 * @see RayMotionConfig
 * @see Animation
 */
public record RayWiggleConfig(
    @JsonField(skipIfDefault = true) WiggleMode mode,
    @Range(ValueRange.UNBOUNDED) @JsonField(skipIfDefault = true) float speed,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "0.1") float amplitude,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "2.0") float frequency,
    @Range(ValueRange.DEGREES) @JsonField(skipIfDefault = true) float phaseOffset
) {
    // =========================================================================
    // Static Constants
    // =========================================================================
    
    /** No wiggle animation. */
    public static final RayWiggleConfig NONE = new RayWiggleConfig(
        WiggleMode.NONE, 0f, 0.1f, 2f, 0f
    );
    
    /** Default snake-like wiggle. */
    public static final RayWiggleConfig WIGGLE = new RayWiggleConfig(
        WiggleMode.WIGGLE, 1.5f, 0.15f, 3f, 0f
    );
    
    /** Wobble effect - ray tips back and forth. */
    public static final RayWiggleConfig WOBBLE = new RayWiggleConfig(
        WiggleMode.WOBBLE, 1f, 0.2f, 2f, 0f
    );
    
    /** Writhing tentacle motion. */
    public static final RayWiggleConfig WRITHE = new RayWiggleConfig(
        WiggleMode.WRITHE, 0.8f, 0.2f, 1.5f, 0f
    );
    
    /** Subtle shimmer/vibration. */
    public static final RayWiggleConfig SHIMMER = new RayWiggleConfig(
        WiggleMode.SHIMMER, 5f, 0.02f, 20f, 0f
    );
    
    /** Ripple wave from base to tip. */
    public static final RayWiggleConfig RIPPLE = new RayWiggleConfig(
        WiggleMode.RIPPLE, 2f, 0.1f, 4f, 0f
    );
    
    /** Whiplash crack motion. */
    public static final RayWiggleConfig WHIP = new RayWiggleConfig(
        WiggleMode.WHIP, 3f, 0.3f, 1f, 0f
    );
    
    /** Chaotic flutter. */
    public static final RayWiggleConfig FLUTTER = new RayWiggleConfig(
        WiggleMode.FLUTTER, 4f, 0.08f, 15f, 0f
    );
    
    /** Fluid slithering snake. */
    public static final RayWiggleConfig SNAKE = new RayWiggleConfig(
        WiggleMode.SNAKE, 1f, 0.12f, 2.5f, 0f
    );
    
    // =========================================================================
    // Queries
    // =========================================================================
    
    /** Whether wiggle animation is active. */
    public boolean isActive() {
        return mode != null && mode != WiggleMode.NONE && (speed != 0 || amplitude > 0);
    }
    
    /** Whether this mode requires multiple segments to look good. */
    public boolean requiresMultipleSegments() {
        return mode != null && mode.requiresMultipleSegments();
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
            .amplitude(amplitude).frequency(frequency)
            .phaseOffset(phaseOffset);
    }
    
    public static class Builder {
        private WiggleMode mode = WiggleMode.NONE;
        private float speed = 1f;
        private float amplitude = 0.1f;
        private float frequency = 2f;
        private float phaseOffset = 0f;
        
        public Builder mode(WiggleMode m) { this.mode = m; return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder amplitude(float a) { this.amplitude = a; return this; }
        public Builder frequency(float f) { this.frequency = f; return this; }
        public Builder phaseOffset(float p) { this.phaseOffset = p; return this; }
        
        public RayWiggleConfig build() {
            return new RayWiggleConfig(mode, speed, amplitude, frequency, phaseOffset);
        }
    }
}
