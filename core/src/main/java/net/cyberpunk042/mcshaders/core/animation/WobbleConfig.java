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

import org.joml.Vector3f;
import net.cyberpunk042.mcshaders.core.serial.JsonField;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;

/**
 * Configuration for random jitter/wobble animation (FUTURE).
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "wobble": {
 *   "amplitude": [0.1, 0.05, 0.1],
 *   "speed": 1.0,
 *   "randomize": true
 * }
 * </pre>
 * 
 * @see Animation
 */
public record WobbleConfig(
    @JsonField(skipIfNull = true) Vector3f amplitude,
    @Range(ValueRange.POSITIVE) float speed,
    @JsonField(skipIfDefault = true, defaultValue = "true") boolean randomize
) {
    
    
    public static final WobbleConfig NONE = new WobbleConfig(null, 0, false);
    
    /** Default gentle wobble. */
    public static final WobbleConfig DEFAULT = new WobbleConfig(
        new Vector3f(0.1f, 0.05f, 0.1f), 1.0f, true);
    
    /** Strong wobble. */
    public static final WobbleConfig STRONG = new WobbleConfig(
        new Vector3f(0.3f, 0.15f, 0.3f), 1.5f, true);
    
    /**
     * Creates a uniform wobble.
     * @param amplitude Wobble amplitude (same on all axes)
     * @param speed Wobble speed
     */
    public static WobbleConfig uniform(float amplitude, @Range(ValueRange.POSITIVE) float speed) {
        return new WobbleConfig(new Vector3f(amplitude), speed, true);
    }
    
    /** Whether wobble is active. */
    public boolean isActive() {
        return amplitude != null && speed > 0;
    }
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .amplitude(amplitude)
            .amplitude(amplitude)
            .speed(speed)
            .randomize(randomize);
    }
    
    public static class Builder {
        private Vector3f amplitude = new Vector3f(0.1f, 0.05f, 0.1f);
        private @Range(ValueRange.POSITIVE) float speed = 1.0f;
        private boolean randomize = true;
        
        public Builder amplitude(Vector3f a) { this.amplitude = a; return this; }
        public Builder amplitude(float x, float y, float z) { this.amplitude = new Vector3f(x, y, z); return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder randomize(boolean r) { this.randomize = r; return this; }
        
        public WobbleConfig build() {
            return new WobbleConfig(amplitude, speed, randomize);
        }
    }
}
