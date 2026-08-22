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
package net.cyberpunk042.mcshaders.core.appearance;

import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;

/**
 * Represents a range of alpha (transparency) values.
 * 
 * <p>When min equals max, the alpha is constant.
 * When they differ, the alpha varies within the range.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * // Constant alpha
 * "alpha": 0.5
 * 
 * // Range (for gradient or animation)
 * "alpha": { "min": 0.3, "max": 0.8 }
 * </pre>
 * 
 * @see Appearance
 */
public record AlphaRange(
    @Range(ValueRange.ALPHA) float min,
    @Range(ValueRange.ALPHA) float max
){
    public static final AlphaRange FULL = new AlphaRange(1f, 1f);

    
    /** Fully opaque. */
    public static final AlphaRange OPAQUE = new AlphaRange(1.0f, 1.0f);
    
    /** Semi-transparent (50%). */
    public static final AlphaRange HALF = new AlphaRange(0.5f, 0.5f);
    
    /** Default field alpha. */
    public static final AlphaRange DEFAULT = new AlphaRange(1.0f, 1.0f);
    
    /**
     * Creates a range between two alphas.
     * @param min Minimum alpha (0-1)
     * @param max Maximum alpha (0-1)
     */
    public static AlphaRange of(float min, float max) { return new AlphaRange(min, max); }

    /**
     * Creates a constant alpha.
     * @param alpha The alpha value (0-1)
     */
    public static AlphaRange of(float alpha) {
        return new AlphaRange(alpha, alpha);
    }
    
    /**
     * Creates an alpha range.
     * @param min Minimum alpha
     * @param max Maximum alpha
     */
    public static AlphaRange range(float min, float max) {
        return new AlphaRange(min, max);
    }
    
    
    
    /** Whether this is a constant (min == max). */
    public boolean isConstant() {
        return min == max;
    }
    
    /** Gets the average alpha. */
    public float average() {
        return (min + max) / 2;
    }
    
    /**
     * Interpolates within the range.
     * @param t Interpolation factor (0-1)
     * @return Alpha value between min and max
     */
    public float lerp(float t) {
        return min + (max - min) * t;
    }
}
