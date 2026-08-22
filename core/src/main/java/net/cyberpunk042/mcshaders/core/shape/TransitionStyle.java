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
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Transition style for Kamehameha component appearance/disappearance.
 * 
 * <p>Controls how the orb or beam visually transitions during lifecycle stages
 * (CHARGE, FIRE, SUSTAIN, RETRACT).</p>
 * 
 * @see KamehamehaShape
 * @see net.cyberpunk042.mcshaders.core.animation.LifecycleAnimator
 */
public enum TransitionStyle {
    /** Only alpha/opacity changes (fade in/out). */
    FADE("Fade", "Opacity transition only"),
    
    /** Only size/scale changes (grow/shrink). */
    SCALE("Scale", "Size transition only"),
    
    /** Both alpha and scale change together. */
    FADE_AND_SCALE("Fade & Scale", "Combined opacity and size transition");
    
    private final String displayName;
    private final String description;
    
    TransitionStyle(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String displayName() { return displayName; }
    public String description() { return description; }
    
    /**
     * Whether this transition affects alpha.
     */
    public boolean affectsAlpha() {
        return this == FADE || this == FADE_AND_SCALE;
    }
    
    /**
     * Whether this transition affects scale.
     */
    public boolean affectsScale() {
        return this == SCALE || this == FADE_AND_SCALE;
    }
    
    /**
     * Calculate effective alpha for given progress (0-1).
     */
    public float calculateAlpha(float progress, float baseAlpha) {
        if (!affectsAlpha()) return baseAlpha;
        return baseAlpha * progress;
    }
    
    /**
     * Calculate effective scale for given progress (0-1).
     */
    public float calculateScale(float progress, float baseScale) {
        if (!affectsScale()) return baseScale;
        return baseScale * progress;
    }
}
