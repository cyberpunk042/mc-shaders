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
 * Modifies shape parameters over the duration of a stage.
 * 
 * <p>Used to animate scale, length, and radius during stage transitions.
 * Each parameter has start/end values and an easing function.</p>
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Beam extends from 10% to 100% length during "fire" stage
 * ShapeModifier fireModifier = ShapeModifier.builder()
 *     .lengthStart(0.1f)
 *     .lengthEnd(1.0f)
 *     .lengthEase(EaseFunction.EASE_OUT_EXPO)
 *     .build();
 * 
 * // Get length at 50% through stage
 * float length = fireModifier.computeLength(0.5f); // ~0.97 (expo ease)
 * </pre>
 * 
 * @see StageConfig
 * @see EaseFunction
 */
public record ShapeModifier(
    // Scale modifiers
    float scaleStart,
    float scaleEnd,
    EaseFunction scaleEase,
    
    // Length modifiers (for cylinders, jets, beams)
    float lengthStart,
    float lengthEnd,
    EaseFunction lengthEase,
    
    // Radius modifiers
    float radiusStart,
    float radiusEnd,
    EaseFunction radiusEase,
    
    // Alpha modifiers (for fade effects)
    float alphaStart,
    float alphaEnd,
    EaseFunction alphaEase
) {
    /** No modification (all values stay at 1.0). */
    public static final ShapeModifier IDENTITY = new ShapeModifier(
        1f, 1f, EaseFunction.LINEAR,
        1f, 1f, EaseFunction.LINEAR,
        1f, 1f, EaseFunction.LINEAR,
        1f, 1f, EaseFunction.LINEAR
    );
    
    /** Fade in from 0 to 1. */
    public static final ShapeModifier FADE_IN = builder()
        .alphaStart(0f).alphaEnd(1f).alphaEase(EaseFunction.EASE_OUT_QUAD)
        .build();
    
    /** Fade out from 1 to 0. */
    public static final ShapeModifier FADE_OUT = builder()
        .alphaStart(1f).alphaEnd(0f).alphaEase(EaseFunction.EASE_IN_QUAD)
        .build();
    
    /** Scale up from 0 to 1. */
    public static final ShapeModifier SCALE_IN = builder()
        .scaleStart(0f).scaleEnd(1f).scaleEase(EaseFunction.EASE_OUT_BACK)
        .build();
    
    /** Scale down from 1 to 0. */
    public static final ShapeModifier SCALE_OUT = builder()
        .scaleStart(1f).scaleEnd(0f).scaleEase(EaseFunction.EASE_IN_QUAD)
        .build();
    
    /** Extend length from 0 to 1 (for beams). */
    public static final ShapeModifier EXTEND = builder()
        .lengthStart(0f).lengthEnd(1f).lengthEase(EaseFunction.EASE_OUT_EXPO)
        .build();
    
    /** Retract length from 1 to 0 (for beams). */
    public static final ShapeModifier RETRACT = builder()
        .lengthStart(1f).lengthEnd(0f).lengthEase(EaseFunction.EASE_IN_QUAD)
        .build();
    
    // =========================================================================
    // Compute Methods
    // =========================================================================
    
    /**
     * Compute scale at given stage progress.
     * @param t Stage progress (0-1)
     * @return Scale multiplier
     */
    public float computeScale(float t) {
        return scaleEase.lerp(scaleStart, scaleEnd, clamp(t));
    }
    
    /**
     * Compute length at given stage progress.
     * @param t Stage progress (0-1)
     * @return Length multiplier
     */
    public float computeLength(float t) {
        return lengthEase.lerp(lengthStart, lengthEnd, clamp(t));
    }
    
    /**
     * Compute radius at given stage progress.
     * @param t Stage progress (0-1)
     * @return Radius multiplier
     */
    public float computeRadius(float t) {
        return radiusEase.lerp(radiusStart, radiusEnd, clamp(t));
    }
    
    /**
     * Compute alpha at given stage progress.
     * @param t Stage progress (0-1)
     * @return Alpha multiplier (0-1)
     */
    public float computeAlpha(float t) {
        return alphaEase.lerp(alphaStart, alphaEnd, clamp(t));
    }
    
    /**
     * Whether scale is animated (start != end).
     */
    public boolean hasScaleAnimation() {
        return Math.abs(scaleStart - scaleEnd) > 0.001f;
    }
    
    /**
     * Whether length is animated.
     */
    public boolean hasLengthAnimation() {
        return Math.abs(lengthStart - lengthEnd) > 0.001f;
    }
    
    /**
     * Whether radius is animated.
     */
    public boolean hasRadiusAnimation() {
        return Math.abs(radiusStart - radiusEnd) > 0.001f;
    }
    
    /**
     * Whether alpha is animated.
     */
    public boolean hasAlphaAnimation() {
        return Math.abs(alphaStart - alphaEnd) > 0.001f;
    }
    
    /**
     * Whether any animation is active.
     */
    public boolean hasAnyAnimation() {
        return hasScaleAnimation() || hasLengthAnimation() || 
               hasRadiusAnimation() || hasAlphaAnimation();
    }
    
    private static float clamp(float t) {
        return Math.max(0f, Math.min(1f, t));
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            .scaleStart(scaleStart).scaleEnd(scaleEnd).scaleEase(scaleEase)
            .lengthStart(lengthStart).lengthEnd(lengthEnd).lengthEase(lengthEase)
            .radiusStart(radiusStart).radiusEnd(radiusEnd).radiusEase(radiusEase)
            .alphaStart(alphaStart).alphaEnd(alphaEnd).alphaEase(alphaEase);
    }
    
    
    
    
    
    public static class Builder {
        private float scaleStart = 1f, scaleEnd = 1f;
        private EaseFunction scaleEase = EaseFunction.LINEAR;
        
        private float lengthStart = 1f, lengthEnd = 1f;
        private EaseFunction lengthEase = EaseFunction.LINEAR;
        
        private float radiusStart = 1f, radiusEnd = 1f;
        private EaseFunction radiusEase = EaseFunction.LINEAR;
        
        private float alphaStart = 1f, alphaEnd = 1f;
        private EaseFunction alphaEase = EaseFunction.LINEAR;
        
        public Builder scaleStart(float v) { this.scaleStart = v; return this; }
        public Builder scaleEnd(float v) { this.scaleEnd = v; return this; }
        public Builder scaleEase(EaseFunction e) { this.scaleEase = e != null ? e : EaseFunction.LINEAR; return this; }
        public Builder scale(float start, float end) { return scaleStart(start).scaleEnd(end); }
        public Builder scale(float start, float end, EaseFunction ease) { return scale(start, end).scaleEase(ease); }
        
        public Builder lengthStart(float v) { this.lengthStart = v; return this; }
        public Builder lengthEnd(float v) { this.lengthEnd = v; return this; }
        public Builder lengthEase(EaseFunction e) { this.lengthEase = e != null ? e : EaseFunction.LINEAR; return this; }
        public Builder length(float start, float end) { return lengthStart(start).lengthEnd(end); }
        public Builder length(float start, float end, EaseFunction ease) { return length(start, end).lengthEase(ease); }
        
        public Builder radiusStart(float v) { this.radiusStart = v; return this; }
        public Builder radiusEnd(float v) { this.radiusEnd = v; return this; }
        public Builder radiusEase(EaseFunction e) { this.radiusEase = e != null ? e : EaseFunction.LINEAR; return this; }
        public Builder radius(float start, float end) { return radiusStart(start).radiusEnd(end); }
        public Builder radius(float start, float end, EaseFunction ease) { return radius(start, end).radiusEase(ease); }
        
        public Builder alphaStart(float v) { this.alphaStart = v; return this; }
        public Builder alphaEnd(float v) { this.alphaEnd = v; return this; }
        public Builder alphaEase(EaseFunction e) { this.alphaEase = e != null ? e : EaseFunction.LINEAR; return this; }
        public Builder alpha(float start, float end) { return alphaStart(start).alphaEnd(end); }
        public Builder alpha(float start, float end, EaseFunction ease) { return alpha(start, end).alphaEase(ease); }
        
        public ShapeModifier build() {
            return new ShapeModifier(
                scaleStart, scaleEnd, scaleEase,
                lengthStart, lengthEnd, lengthEase,
                radiusStart, radiusEnd, radiusEase,
                alphaStart, alphaEnd, alphaEase
            );
        }
    }
}
