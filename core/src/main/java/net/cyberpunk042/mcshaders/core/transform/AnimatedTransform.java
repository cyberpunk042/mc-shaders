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
package net.cyberpunk042.mcshaders.core.transform;

import org.joml.Vector3f;

/**
 * Represents a transform that interpolates between two states over time.
 * 
 * <p>Used for smooth transitions during lifecycle animations (spawn/despawn).</p>
 * 
 * @see Transform
 * @see net.cyberpunk042.field.influence.LifecycleConfig
 */
public class AnimatedTransform {
    
    private final Transform from;
    private final Transform to;
    private float progress;
    
    /**
     * Creates an animated transform between two states.
     * @param from Starting transform
     * @param to Ending transform
     */
    public AnimatedTransform(Transform from, Transform to) {
        this.from = from != null ? from : Transform.IDENTITY;
        this.to = to != null ? to : Transform.IDENTITY;
        this.progress = 0;
    }
    
    /**
     * Creates an animated transform that starts and ends at the same state.
     * @param transform The static transform
     */
    public AnimatedTransform(Transform transform) {
        this(transform, transform);
    }
    
    /**
     * Sets the animation progress.
     * @param progress Progress from 0 (from) to 1 (to)
     */
    public void setProgress(float progress) {
        this.progress = Math.max(0, Math.min(1, progress));
    }
    
    /** Gets the current progress. */
    public float getProgress() { return progress; }
    
    /** Gets the starting transform. */
    public Transform from() { return from; }
    
    /** Gets the ending transform. */
    public Transform to() { return to; }
    
    /**
     * Gets the current interpolated transform.
     * @return The interpolated transform at current progress
     */
    public Transform current() {
        if (progress <= 0) return from;
        if (progress >= 1) return to;
        return lerp(from, to, progress);
    }
    
    /**
     * Linearly interpolates between two transforms.
     * @param a Starting transform
     * @param b Ending transform
     * @param t Interpolation factor (0-1)
     * @return Interpolated transform
     */
    public static Transform lerp(Transform a, Transform b, float t) {
        if (t <= 0) return a;
        if (t >= 1) return b;
        
        // Lerp offset
        Vector3f offset = null;
        if (a.offset() != null || b.offset() != null) {
            Vector3f aOff = a.offset() != null ? a.offset() : new Vector3f();
            Vector3f bOff = b.offset() != null ? b.offset() : new Vector3f();
            offset = new Vector3f(aOff).lerp(bOff, t);
        }
        
        // Lerp rotation
        Vector3f rotation = null;
        if (a.rotation() != null || b.rotation() != null) {
            Vector3f aRot = a.rotation() != null ? a.rotation() : new Vector3f();
            Vector3f bRot = b.rotation() != null ? b.rotation() : new Vector3f();
            rotation = new Vector3f(aRot).lerp(bRot, t);
        }
        
        // Lerp scale
        float scale = a.scale() + (b.scale() - a.scale()) * t;
        
        // Lerp scaleXYZ
        Vector3f scaleXYZ = null;
        if (a.scaleXYZ() != null || b.scaleXYZ() != null) {
            Vector3f aScale = a.scaleXYZ() != null ? a.scaleXYZ() : new Vector3f(a.scale());
            Vector3f bScale = b.scaleXYZ() != null ? b.scaleXYZ() : new Vector3f(b.scale());
            scaleXYZ = new Vector3f(aScale).lerp(bScale, t);
        }
        
        // Use "to" values for non-interpolatable properties
        return new Transform(
            t < 0.5f ? a.anchor() : b.anchor(),
            offset,
            rotation,
            t < 0.5f ? a.inheritRotation() : b.inheritRotation(),
            scale,
            scaleXYZ,
            t < 0.5f ? a.scaleWithRadius() : b.scaleWithRadius(),
            t < 0.5f ? a.facing() : b.facing(),
            t < 0.5f ? a.up() : b.up(),
            t < 0.5f ? a.billboard() : b.billboard(),
            t < 0.5f ? a.orbit() : b.orbit(),
            t < 0.5f ? a.orbit3d() : b.orbit3d()
        );
    }
}
