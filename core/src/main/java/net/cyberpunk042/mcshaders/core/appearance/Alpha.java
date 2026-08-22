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

import net.cyberpunk042.mcshaders.core.util.MathSupport;

import net.cyberpunk042.mcshaders.core.util.FieldMath;

/**
 * Alpha (transparency) configuration supporting static or pulsing values.
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Static alpha
 * Alpha solid = Alpha.fixed(0.8f);
 * 
 * // Pulsing alpha
 * Alpha pulse = Alpha.pulsing(0.4f, 0.9f, 2.0f);
 * float current = pulse.getValue(worldTime);
 * </pre>
 * 
 * @param min Minimum alpha (0.0 - 1.0)
 * @param max Maximum alpha (0.0 - 1.0)
 * @param pulseSpeed Pulse speed (0 = static, higher = faster)
 */
public record Alpha(
    float min,
    float max,
    float pulseSpeed
) {
    
    // ─────────────────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────────────────
    
    public Alpha {
        min = MathSupport.clamp(min, 0.0f, 1.0f);
        max = MathSupport.clamp(max, min, 1.0f);
        pulseSpeed = Math.max(0, pulseSpeed);
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Factory Methods
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Creates a fixed (non-pulsing) alpha.
     */
    public static Alpha fixed(float value) {
        float clamped = MathSupport.clamp(value, 0.0f, 1.0f);
        return new Alpha(clamped, clamped, 0);
    }
    
    /**
     * Creates a pulsing alpha that oscillates between min and max.
     * 
     * @param min Minimum alpha
     * @param max Maximum alpha
     * @param speed Oscillation speed (1.0 = one cycle per second)
     */
    public static Alpha pulsing(float min, float max, float speed) {

        return new Alpha(min, max, speed);
    }
    
    /**
     * Creates a subtle pulse (±10% around base).
     */
    public static Alpha subtle(float base) {
        float min = MathSupport.clamp(base - 0.1f, 0, 1);
        float max = MathSupport.clamp(base + 0.1f, 0, 1);
        return new Alpha(min, max, 1.5f);
    }
    
    /**
     * Creates an aggressive pulse (wide range, fast).
     */
    public static Alpha aggressive(float min, float max) {
        return new Alpha(min, max, 4.0f);
    }
    
    /**
     * Fully opaque, no pulsing.
     */
    public static Alpha opaque() {
        return fixed(1.0f);
    }
    
    /**
     * Default alpha (80% opacity, no pulse).
     */
    public static Alpha defaults() {
        return fixed(0.8f);
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Value Calculation
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Gets the alpha value at the given time.
     * 
     * @param time World time (ticks or seconds depending on pulseSpeed units)
     * @return Current alpha value (0.0 - 1.0)
     */
    public float getValue(float time) {
        if (pulseSpeed <= 0 || min >= max) {
            return max; // Static
        }
        
        // Sinusoidal oscillation between min and max
        float phase = time * pulseSpeed * 0.1f; // Scale for reasonable speed
        float t = ((float) Math.sin(phase) + 1.0f) * 0.5f; // 0.0 to 1.0
        return MathSupport.lerp(t, min, max);
    }
    
    /**
     * Gets the alpha value with a phase offset.
     */
    public float getValue(float time, float phaseOffset) {
        return getValue(time + phaseOffset);
    }
    
    /**
     * Checks if this alpha pulses.
     */
    public boolean isPulsing() {
        return pulseSpeed > 0 && min < max;
    }
    
    /**
     * Gets the average alpha value.
     */
    public float average() {
        return (min + max) / 2.0f;
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // Modifiers
    // ─────────────────────────────────────────────────────────────────────────
    
    /**
     * Returns a copy with adjusted speed.
     */
    public Alpha withSpeed(float newSpeed) {
        return new Alpha(min, max, newSpeed);
    }
    
    /**
     * Returns a copy with scaled alpha range.
     */
    public Alpha scaled(float factor) {
        return new Alpha(min * factor, max * factor, pulseSpeed);
    }
}
