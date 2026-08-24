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
 * Configuration for scale pulsing animation.
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "pulse": {
 *   "scale": 0.1,
 *   "speed": 1.0,
 *   "waveform": "SINE",
 *   "min": 0.9,
 *   "max": 1.1
 * }
 * </pre>
 * 
 * <p>The scale oscillates between min and max using the specified waveform.</p>
 * 
 * @see Waveform
 */
public record PulseConfig(
    @Range(ValueRange.POSITIVE) float scale,
    @Range(ValueRange.POSITIVE) float speed,
    Waveform waveform,
    @Range(ValueRange.POSITIVE) float min,
    @Range(ValueRange.POSITIVE) float max,
    PulseMode mode
){
    /** No pulsing (static scale). */
    public static final PulseConfig NONE = new PulseConfig(0, 0, Waveform.SINE, 1, 1, PulseMode.SCALE);
    
    /** Default gentle pulse. */
    public static final PulseConfig DEFAULT = new PulseConfig(0.1f, 1.0f, Waveform.SINE, 0.9f, 1.1f, PulseMode.SCALE);
    
    /**
     * Creates a simple sine pulse.
     * @param amplitude How much to vary (0.1 = ±10%)
     * @param speed Pulse speed
     */
    public static PulseConfig sine(float amplitude, @Range(ValueRange.POSITIVE) float speed) {
        return new PulseConfig(amplitude, speed, Waveform.SINE, 1 - amplitude, 1 + amplitude, PulseMode.SCALE);
    }
    
    /**
     * Whether this pulse would actually change anything.
     *
     * <p>Asks what {@link #evaluate} reads, which is {@code speed}, {@code min} and
     * {@code max}. It used to ask {@code scale != 0 && speed != 0}, and disagreed with
     * its own method in both directions: a pulse with a real {@code min}/{@code max}
     * range and {@code scale == 0} called itself inactive and evaluated to a flat
     * {@code 1.0}, while one with {@code min == max} called itself active and evaluated
     * to a constant. {@code Primitive.isAnimated()} reaches this through
     * {@link Animation#isActive()}, so both answers were visible.
     *
     * <p>{@code scale} is the amplitude {@link #sine} was given, and {@code min} and
     * {@code max} are derived from it there — so for anything built that way the two
     * spellings agree and this is not a behaviour change. {@link #NONE} and
     * {@link #DEFAULT} are unaffected. Nothing reads {@code scale} to compute a value;
     * whether it should stay in the record at all is a separate question, and it is a
     * required JSON key, so it is left alone here.
     *
     * <p>{@link AlphaPulseConfig#isActive()} — the same idea for alpha — has always
     * asked it this way.
     */
    public boolean isActive() {
        return speed != 0 && min != max;
    }
    
    /**
     * Evaluates the pulse at a given time.
     * @param time Current time (ticks)
     * @return Scale multiplier between min and max
     */
    public float evaluate(float time) {
        if (!isActive()) return 1.0f;
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
            .scale(scale)
            .speed(speed)
            .waveform(waveform)
            .min(min)
            .max(max)
            .mode(mode);
    }
    
    public static class Builder {
        private @Range(ValueRange.POSITIVE) float scale = 0.1f;
        private @Range(ValueRange.POSITIVE) float speed = 1.0f;
        private Waveform waveform = Waveform.SINE;
        private @Range(ValueRange.POSITIVE) float min = 0.9f;
        private @Range(ValueRange.POSITIVE) float max = 1.1f;
        private PulseMode mode = PulseMode.SCALE;
        
        public Builder scale(float s) { this.scale = s; return this; }
        public Builder speed(float s) { this.speed = s; return this; }
        public Builder waveform(Waveform w) { this.waveform = w; return this; }
        public Builder min(float m) { this.min = m; return this; }
        public Builder max(float m) { this.max = m; return this; }
        public Builder mode(PulseMode m) { this.mode = m; return this; }
        
        public PulseConfig build() {
            return new PulseConfig(scale, speed, waveform, min, max, mode);
        }
    }

    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    

}
