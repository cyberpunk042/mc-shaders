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

/**
 * Configuration for alpha (transparency) pulsing animation.
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "alphaPulse": {
 *   "speed": 1.0,
 *   "min": 0.3,
 *   "max": 0.8,
 *   "waveform": "SINE"
 * }
 * </pre>
 * 
 * @see Waveform
 */
public record AlphaPulseConfig(
    @Range(ValueRange.POSITIVE) float speed,
    @Range(ValueRange.ALPHA) float min,
    @Range(ValueRange.ALPHA) float max,
    Waveform waveform
){
    /** No alpha pulsing (static alpha). */
    public static final AlphaPulseConfig NONE = new AlphaPulseConfig(0, 1, 1, Waveform.SINE);
    
    /** Default gentle alpha pulse. */
    public static final AlphaPulseConfig DEFAULT = new AlphaPulseConfig(1.0f, 0.5f, 1.0f, Waveform.SINE);
    
    /**
     * Creates a simple alpha pulse.
     * @param min Minimum alpha
     * @param max Maximum alpha
     * @param speed Pulse speed
     */
    public static AlphaPulseConfig between(@Range(ValueRange.ALPHA) float min, @Range(ValueRange.ALPHA) float max, @Range(ValueRange.POSITIVE) float speed) {
        return new AlphaPulseConfig(speed, min, max, Waveform.SINE);
    }
    
    /** Whether this pulse is active. */
    public boolean isActive() {
        return speed != 0 && min != max;
    }
    
    /**
     * Evaluates the alpha at a given time.
     * @param time Current time (ticks)
     * @return Alpha value between min and max
     */
    public float evaluate(float time) {
        if (!isActive()) return max;
        float t = time * speed;
        float wave = waveform.evaluate(t);
        return min + (max - min) * wave;
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .speed(speed)
            .min(min)
            .max(max)
            .waveform(waveform);
    }
    
    public static class Builder {
        private @Range(ValueRange.POSITIVE) float speed = 1.0f;
        private @Range(ValueRange.ALPHA) float min = 0.5f;
        private @Range(ValueRange.ALPHA) float max = 1.0f;
        private Waveform waveform = Waveform.SINE;
        
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder min(float m) { this.min = m; return this; }
        public Builder max(float m) { this.max = m; return this; }
        public Builder waveform(Waveform w) { this.waveform = w; return this; }
        
        public AlphaPulseConfig build() {
            return new AlphaPulseConfig(speed, min, max, waveform);
        }
    }

    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    

}
