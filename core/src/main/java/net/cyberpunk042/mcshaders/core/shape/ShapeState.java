/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 */
package net.cyberpunk042.mcshaders.core.shape;


/**
 * Immutable state of an animatable shape.
 * 
 * <h2>User Controls</h2>
 * The user sets all values in UI:
 * <ul>
 *   <li><b>Stage:</b> Which lifecycle stage (e.g., SPAWNING, ACTIVE)</li>
 *   <li><b>Phase:</b> 0-1 progress within the stage</li>
 *   <li><b>EdgeMode:</b> How edges appear during transitions (CLIP, SCALE, FADE)</li>
 *   <li><b>EdgeIntensity:</b> How strong the edge effect is (0=none, 1=full)</li>
 * </ul>
 * 
 * <h2>Animation Behavior</h2>
 * Animation "plays with" phase (modifies it over time) while respecting 
 * stage, edgeMode, and edgeIntensity (reads them but doesn't change them).
 * 
 * <h2>Preview Capability</h2>
 * User can set stage=SPAWNING, phase=0.5, edgeMode=CLIP and see exactly 
 * what the shape looks like at 50% spawn with clip effect - WITHOUT 
 * running any animation.
 * 
 * @param stage The current stage (user controlled)
 * @param phase Progress within the stage (user controlled, animation modifies)
 * @param edgeMode How edges appear during transitional stages (user controlled)
 * @param edgeIntensity Intensity of edge effect: 0=no effect, 1=full effect (user controlled)
 */

public record ShapeState<S extends ShapeStage>(
    S stage,
    float phase,
    EdgeTransitionMode edgeMode,
    float edgeIntensity
) {
    
    /**
     * Compact constructor with validation.
     */
    public ShapeState {
        if (edgeMode == null) edgeMode = EdgeTransitionMode.CLIP;
        phase = Math.clamp(phase, 0f, 1f);
        edgeIntensity = Math.clamp(edgeIntensity, 0f, 5f);
    }
    
    /**
     * Backward-compatible constructor (defaults edgeIntensity to 1.0).
     */
    public ShapeState(S stage, float phase, EdgeTransitionMode edgeMode) {
        this(stage, phase, edgeMode, 1.0f);
    }
    
    // ─────────────────── Immutable "setters" ───────────────────
    
    public ShapeState<S> withStage(S newStage) {
        return new ShapeState<>(newStage, phase, edgeMode, edgeIntensity);
    }
    
    public ShapeState<S> withPhase(float newPhase) {
        return new ShapeState<>(stage, newPhase, edgeMode, edgeIntensity);
    }
    
    public ShapeState<S> withEdgeMode(EdgeTransitionMode newMode) {
        return new ShapeState<>(stage, phase, newMode, edgeIntensity);
    }
    
    public ShapeState<S> withEdgeIntensity(float newIntensity) {
        return new ShapeState<>(stage, phase, edgeMode, newIntensity);
    }
    
    // ─────────────────── Convenience queries ───────────────────
    
    /**
     * Returns true if the shape should be visible in this state.
     */
    public boolean isVisible() {
        return stage != null && stage.isVisible();
    }
    
    /**
     * Returns true if this is a transitional state.
     */
    public boolean isTransitional() {
        return stage != null && stage.isTransitional();
    }
}

