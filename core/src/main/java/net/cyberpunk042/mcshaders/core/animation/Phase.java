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
 * Animation phase offset.
 * 
 * <p>Per ARCHITECTURE.md (line 88):
 * <pre>
 * visual/animation/
 * ├── Phase.java   # animation offset
 * </pre>
 * 
 * <p>Used to offset multiple primitives' animations to create
 * wave-like or staggered effects. Phase is applied by {@code AnimationApplier}
 * to create staggered or wave-like visual effects across multiple primitives.
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Offset by quarter cycle
 * Phase phase = Phase.of(0.25f);
 * 
 * // Stagger 5 rings
 * for (int i = 0; i < 5; i++) {
 *     Phase phase = Phase.stagger(i, 5);  // 0, 0.2, 0.4, 0.6, 0.8
 * }
 * </pre>
 * 
 * @param offset phase offset (0-1, wraps)
 * 
 * @see SpinConfig
 * @see PulseConfig
 * @see net.cyberpunk042.client.visual.animation.AnimationApplier
 */
public record Phase(float offset) {
    
    /**
     * No phase offset.
     */
    public static final Phase ZERO = new Phase(0);
    
    /**
     * Quarter cycle offset.
     */
    public static final Phase QUARTER = new Phase(0.25f);
    
    /**
     * Half cycle offset.
     */
    public static final Phase HALF = new Phase(0.5f);
    
    /**
     * Three-quarter cycle offset.
     */
    public static final Phase THREE_QUARTER = new Phase(0.75f);
    
    /**
     * Creates a phase with the given offset.
     */
    public static Phase of(float offset) {
        return new Phase(offset % 1.0f);
    }
    
    /**
     * Creates a staggered phase for multiple elements.
     * 
     * @param index element index (0-based)
     * @param total total number of elements
     * @return phase offset for this element
     */
    public static Phase stagger(int index, int total) {
        if (total <= 1) return ZERO;
        return new Phase((float) index / total);
    }
    
    /**
     * Creates a random phase.
     */
    public static Phase random() {
        return new Phase((float) Math.random());
    }
    
    /**
     * Applies this phase offset to a time value.
     * 
     * @param time world time in ticks
     * @param period period in ticks
     * @return phase-adjusted time
     */
    public float apply(long time, float period) {
        return (time + offset * period) % period;
    }
    
    /**
     * Combines this phase with another.
     */
    public Phase add(Phase other) {
        return new Phase((offset + other.offset) % 1.0f);
    }
    
    /**
     * Returns the offset in degrees.
     */
    public float degrees() {
        return offset * 360.0f;
    }
    
    /**
     * Returns the offset in radians.
     */
    public float radians() {
        return offset * (float) (Math.PI * 2);
    }
}

