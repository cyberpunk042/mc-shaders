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
 * Represents a UV slice of a texture frame for animated rendering.
 * 
 * <p>Used to define which portion of a texture to sample during animation,
 * enabling scrolling effects and partial frame displays.
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Full frame (no animation)
 * FrameSlice slice = FrameSlice.FULL;
 * 
 * // Top half of texture
 * FrameSlice topHalf = new FrameSlice(0.0f, 0.5f, 0.0f, false);
 * 
 * // Scrolling animation
 * float scroll = (worldTime % 20) / 20.0f;
 * FrameSlice scrolling = new FrameSlice(0.0f, 1.0f, scroll, true);
 * </pre>
 * 
 * @param minV         minimum V coordinate (0.0 = top of texture)
 * @param maxV         maximum V coordinate (1.0 = bottom of texture)
 * @param scrollOffset UV scroll offset for animation (0.0 - 1.0)
 * @param wrap         whether to wrap texture coordinates when scrolling
 * 
 * @see net.cyberpunk042.client.visual.animation.AnimationApplier
 */
public record FrameSlice(
        float minV,
        float maxV,
        float scrollOffset,
        boolean wrap
) {
    
    /**
     * Full frame with no animation or offset.
     */
    public static final FrameSlice FULL = new FrameSlice(0.0f, 1.0f, 0.0f, false);
    
    /**
     * Creates a slice for a portion of the texture height.
     * 
     * @param startFraction start position (0.0 = top)
     * @param endFraction   end position (1.0 = bottom)
     * @return new frame slice
     */
    public static FrameSlice portion(float startFraction, float endFraction) {
        return new FrameSlice(startFraction, endFraction, 0.0f, false);
    }
    
    /**
     * Creates a scrolling slice.
     * 
     * @param offset scroll offset (0.0 - 1.0)
     * @return new frame slice with wrapping enabled
     */
    public static FrameSlice scrolling(float offset) {
        return new FrameSlice(0.0f, 1.0f, offset, true);
    }
    
    /**
     * Returns the effective minV with scroll offset applied.
     */
    public float effectiveMinV() {
        float v = minV + scrollOffset;
        return wrap ? v % 1.0f : Math.min(v, 1.0f);
    }
    
    /**
     * Returns the effective maxV with scroll offset applied.
     */
    public float effectiveMaxV() {
        float v = maxV + scrollOffset;
        return wrap ? v % 1.0f : Math.min(v, 1.0f);
    }
    
    /**
     * Returns the height of this slice (0.0 - 1.0).
     */
    public float height() {
        return maxV - minV;
    }
    
    /**
     * Creates a new slice with a different scroll offset.
     */
    public FrameSlice withOffset(float newOffset) {
        return new FrameSlice(minV, maxV, newOffset, wrap);
    }
}

