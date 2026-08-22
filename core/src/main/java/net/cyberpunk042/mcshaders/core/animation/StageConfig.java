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
 * Configuration for a single animation stage.
 * 
 * <p>A stage represents a distinct phase in a primitive's lifecycle,
 * such as "charging", "firing", "sustaining", or "despawning".</p>
 * 
 * <h2>Stage Components</h2>
 * <ul>
 *   <li><b>id</b>: Unique identifier (e.g., "charge", "fire", "sustain")</li>
 *   <li><b>duration</b>: How long the stage lasts in ticks (0 = indefinite)</li>
 *   <li><b>transition</b>: How the stage ends (TIME, MANUAL, HOLD, etc.)</li>
 *   <li><b>shapeModifier</b>: Animates scale/length/radius during stage</li>
 *   <li><b>animation</b>: Stage-specific animation settings (optional override)</li>
 *   <li><b>visible</b>: Whether the primitive is visible during this stage</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Charging stage: scale up, pulsing
 * StageConfig charge = StageConfig.builder()
 *     .id("charge")
 *     .duration(60)
 *     .transition(StageTransition.TIME)
 *     .shapeModifier(ShapeModifier.SCALE_IN)
 *     .build();
 * 
 * // Fire stage: beam extends rapidly
 * StageConfig fire = StageConfig.builder()
 *     .id("fire")
 *     .duration(15)
 *     .shapeModifier(ShapeModifier.builder()
 *         .length(0.1f, 1.0f, EaseFunction.EASE_OUT_EXPO)
 *         .build())
 *     .build();
 * </pre>
 * 
 * @see StageTransition
 * @see ShapeModifier
 * @see LifecycleAnimator
 */
public record StageConfig(
    String id,
    int duration,
    StageTransition transition,
    ShapeModifier shapeModifier,
    Animation animationOverride,
    boolean visible,
    String nextStageId  // Explicit next stage (null = sequential order)
) {
    // =========================================================================
    // Presets
    // =========================================================================
    
    /** Hidden stage (not visible, no duration). */
    public static final StageConfig HIDDEN = builder()
        .id("hidden")
        .visible(false)
        .transition(StageTransition.MANUAL)
        .build();
    
    /** Spawn stage: scale and fade in over 20 ticks. */
    public static final StageConfig SPAWN = builder()
        .id("spawn")
        .duration(20)
        .transition(StageTransition.TIME)
        .shapeModifier(ShapeModifier.builder()
            .scale(0f, 1f, EaseFunction.EASE_OUT_BACK)
            .alpha(0f, 1f, EaseFunction.EASE_OUT_QUAD)
            .build())
        .build();
    
    /** Active stage: no modifications, hold indefinitely. */
    public static final StageConfig ACTIVE = builder()
        .id("active")
        .transition(StageTransition.HOLD)
        .build();
    
    /** Despawn stage: scale and fade out over 30 ticks. */
    public static final StageConfig DESPAWN = builder()
        .id("despawn")
        .duration(30)
        .transition(StageTransition.TIME)
        .shapeModifier(ShapeModifier.builder()
            .scale(1f, 0f, EaseFunction.EASE_IN_QUAD)
            .alpha(1f, 0f, EaseFunction.EASE_IN_QUAD)
            .build())
        .build();
    
    // =========================================================================
    // Kamehameha-specific presets
    // =========================================================================
    
    /** Charge stage for energy orb: grows with pulsing. */
    public static final StageConfig CHARGE = builder()
        .id("charge")
        .duration(60)
        .transition(StageTransition.MANUAL)  // Wait for user to release
        .shapeModifier(ShapeModifier.builder()
            .scale(0.1f, 1.0f, EaseFunction.EASE_OUT_BACK)
            .build())
        .build();
    
    /** Fire stage for beam: extends rapidly. */
    public static final StageConfig FIRE = builder()
        .id("fire")
        .duration(15)
        .transition(StageTransition.TIME)
        .shapeModifier(ShapeModifier.builder()
            .length(0f, 1f, EaseFunction.EASE_OUT_EXPO)
            .radius(0.5f, 1f, EaseFunction.EASE_OUT_QUAD)
            .build())
        .build();
    
    /** Sustain stage: continuous effect, hold until released. */
    public static final StageConfig SUSTAIN = builder()
        .id("sustain")
        .transition(StageTransition.HOLD)
        .build();
    
    /** Retract stage for beam: length shrinks back. */
    public static final StageConfig RETRACT = builder()
        .id("retract")
        .duration(20)
        .transition(StageTransition.TIME)
        .shapeModifier(ShapeModifier.RETRACT)
        .build();
    
    // =========================================================================
    // Query Methods
    // =========================================================================
    
    /**
     * Whether this stage has a fixed duration.
     */
    public boolean hasDuration() {
        return duration > 0;
    }
    
    /**
     * Whether this stage runs indefinitely (HOLD or MANUAL with no duration).
     */
    public boolean isIndefinite() {
        return transition.isIndefinite() || duration == 0;
    }
    
    /**
     * Whether this stage has shape modifications.
     */
    public boolean hasShapeModifier() {
        return shapeModifier != null && shapeModifier.hasAnyAnimation();
    }
    
    /**
     * Whether this stage overrides the base animation.
     */
    public boolean hasAnimationOverride() {
        return animationOverride != null;
    }
    
    /**
     * Get the shape modifier, or IDENTITY if none.
     */
    public ShapeModifier effectiveShapeModifier() {
        return shapeModifier != null ? shapeModifier : ShapeModifier.IDENTITY;
    }
    
    /**
     * Calculate stage progress based on elapsed ticks.
     * @param elapsedTicks Ticks since stage started
     * @return Progress (0-1), or 1 if indefinite
     */
    public float progress(long elapsedTicks) {
        if (!hasDuration()) return 1f;
        return Math.min(1f, (float) elapsedTicks / duration);
    }
    
    /**
     * Whether this stage is complete based on elapsed ticks.
     */
    public boolean isComplete(long elapsedTicks) {
        if (!transition.requiresDuration()) return false;
        return elapsedTicks >= duration;
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            .id(id)
            .duration(duration)
            .transition(transition)
            .shapeModifier(shapeModifier)
            .animationOverride(animationOverride)
            .visible(visible)
            .nextStageId(nextStageId);
    }
    
    
    
    
    
    public static class Builder {
        private String id = "stage";
        private int duration = 0;
        private StageTransition transition = StageTransition.MANUAL;
        private ShapeModifier shapeModifier = ShapeModifier.IDENTITY;
        private Animation animationOverride = null;
        private boolean visible = true;
        private String nextStageId = null;
        
        public Builder id(String id) { this.id = id; return this; }
        public Builder duration(int ticks) { this.duration = Math.max(0, ticks); return this; }
        public Builder transition(StageTransition t) { this.transition = t != null ? t : StageTransition.MANUAL; return this; }
        public Builder shapeModifier(ShapeModifier m) { this.shapeModifier = m; return this; }
        public Builder animationOverride(Animation a) { this.animationOverride = a; return this; }
        public Builder visible(boolean v) { this.visible = v; return this; }
        public Builder nextStageId(String id) { this.nextStageId = id; return this; }
        
        public StageConfig build() {
            return new StageConfig(id, duration, transition, shapeModifier, animationOverride, visible, nextStageId);
        }
    }
}
