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


/**
 * Defines waveform shapes for animations.
 * 
 * <p>Used by PulseConfig and AlphaPulseConfig to control
 * the shape of the animation curve.</p>
 * 
 * <h3>Visual Characteristics</h3>
 * <ul>
 *   <li>SINE - Smooth oscillation (DEFAULT)</li>
 *   <li>SQUARE - Instant on/off</li>
 *   <li>TRIANGLE_WAVE - Linear up/down</li>
 *   <li>SAWTOOTH - Linear up, instant reset</li>
 * </ul>
 * 
 * <p>Uses java.lang.Math for sine. See the porting note on the SINE case.</p>
 * 
 * @see PulseConfig
 * @see AlphaPulseConfig
 */
public enum Waveform {
    /** Smooth sinusoidal oscillation (DEFAULT) */
    SINE,
    
    /** Instant on/off switching */
    SQUARE,
    
    /** Linear up and down - named to avoid confusion with TrianglePattern */
    TRIANGLE_WAVE,
    
    /** Linear increase, instant reset to minimum */
    SAWTOOTH;
    
    /** TWO_PI constant for waveform calculations */
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    /**
     * Evaluates the waveform at time t.
     * 
     * <p>Uses java.lang.Math.sin for the SINE waveform - see the note below on the
     * lookup table implementation from Minecraft, more performant than Math.sin().</p>
     * 
     * @param t Time value, typically 0-1 for one cycle
     * @return Value between 0 and 1
     */
    public float evaluate(float t) {
        // Normalise t into 0-1.
        t = t - (float) Math.floor(t);
        
        return switch (this) {
            // Ported from Minecraft's MathHelper.sin, which is a lookup-table
            // approximation. Math.sin is exact and slightly slower; for a waveform
            // driving visuals the difference is imperceptible, and core cannot
            // depend on Minecraft. If this ever shows up in a profile, the lookup
            // table is worth reintroducing here rather than reaching back out.
            case SINE -> (float) Math.sin(t * TWO_PI) * 0.5f + 0.5f;
            case SQUARE -> t < 0.5f ? 1.0f : 0.0f;
            case TRIANGLE_WAVE -> t < 0.5f ? t * 2 : 2 - t * 2;
            case SAWTOOTH -> t;
        };
    }

    
    /**
     * Parse from string (case-insensitive).
     * @param id The string identifier
     * @return Matching Waveform, or SINE if not found
     */
    public static Waveform fromId(String id) {
        if (id == null || id.isEmpty()) return SINE;
        try {
            return valueOf(id.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return SINE;
        }
    }
}
