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
 * Scale oscillation animation.
 * 
 * <p>Per ARCHITECTURE.md (line 87):
 * <pre>
 * visual/animation/
 * ├── Pulse.java   # scale oscillation
 * </pre>
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Gentle pulse between 0.95 and 1.05 scale
 * Pulse pulse = Pulse.gentle();
 * 
 * // Custom pulse
 * Pulse pulse = new Pulse(0.1f, 1.0f, 0.0f);  // 10% amplitude, 1 cycle/sec
 * </pre>
 * 
 * @param amplitude scale variation amount (0.1 = ±10% scale)
 * @param frequency oscillation speed (cycles per second)
 * @param phase     phase offset (0-1)
 * 
 * @see net.cyberpunk042.mcshaders.core.animation.Animation
 */
public record Pulse(float amplitude, float frequency, float phase) {
    
    /**
     * No pulsing.
     */
    public static final Pulse NONE = new Pulse(0, 0, 0);
    
    /**
     * Gentle breathing pulse.
     */
    public static final Pulse GENTLE = new Pulse(0.05f, 0.5f, 0);
    
    /**
     * Medium pulse.
     */
    public static final Pulse MEDIUM = new Pulse(0.1f, 1.0f, 0);
    
    /**
     * Strong pulse.
     */
    public static final Pulse STRONG = new Pulse(0.2f, 2.0f, 0);
    
    /**
     * Creates a gentle pulse.
     */
    public static Pulse gentle() {
        return GENTLE;
    }
    
    /**
     * Creates a pulse with the given amplitude.
     */
    public static Pulse withAmplitude(float amplitude) {
        return new Pulse(amplitude, 1.0f, 0);
    }
    
    /**
     * Calculates the scale factor at a given time.
     * 
     * @param time world time in ticks
     * @return scale factor (1.0 ± amplitude)
     */
    public float scaleAt(long time) {
        if (amplitude == 0 || frequency == 0) {
            return 1.0f;
        }
        // Convert ticks to seconds (20 ticks/sec)
        float seconds = time / 20.0f;
        float cycle = (float) Math.sin((seconds * frequency + phase) * Math.PI * 2);
        return 1.0f + (cycle * amplitude);
    }
    
    /**
     * Returns whether this pulse is active.
     */
    public boolean isActive() {
        return amplitude != 0 && frequency != 0;
    }
    
    /**
     * Creates a new pulse with a different phase.
     */
    public Pulse withPhase(float newPhase) {
        return new Pulse(amplitude, frequency, newPhase);
    }
}

