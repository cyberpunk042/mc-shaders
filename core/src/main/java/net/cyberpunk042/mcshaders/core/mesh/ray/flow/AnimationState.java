/*
 * Ported from the-virus-block-mc (net.cyberpunk042.client.visual.mesh), where it
 * is tessellation code with no Minecraft or graphics-API dependency: the whole
 * subtree imported nothing from net.minecraft or com.mojang.
 * Relicensed to MIT here by the author, per the engine/content split recorded in
 * docs/PORTING.md.
 *
 * The mod's logging system was replaced with core's Diag, which routes to
 * java.lang.System.Logger. The call sites are unchanged apart from the name.
 */
package net.cyberpunk042.mcshaders.core.mesh.ray.flow;

/**
 * Output of the FlowPipeline - the computed animation values.
 * 
 * <h2>Animation's Role</h2>
 * Animation modifies phase over time while RESPECTING stage and edgeMode
 * (which are USER-controlled in ShapeState).
 * 
 * <h2>What This Contains</h2>
 * <ul>
 *   <li><b>phase:</b> The computed 0-1 progress (animation's output for length mode)</li>
 *   <li><b>travelPhase:</b> Travel animation phase (for per-vertex alpha effects)</li>
 *   <li><b>flickerAlpha:</b> Flicker effect overlay (1.0 = no flicker)</li>
 * </ul>
 * 
 * <h2>What This Does NOT Contain</h2>
 * <p>Stage and EdgeMode are NOT here - they are USER-controlled in ShapeState.
 * Animation only computes phase values, it never changes the stage.</p>
 * 
 * @param phase The computed phase value (0-1) for length animation
 * @param travelPhase Travel animation phase (0-1) for per-vertex effects
 * @param flickerAlpha Flicker overlay multiplier (0-1)
 */
public record AnimationState(
    float phase,
    float travelPhase,
    float flickerAlpha
) {
    /** Starting state - zero phase, no travel, full alpha. */
    public static final AnimationState ZERO = new AnimationState(0f, 0f, 1f);
    
    /** Full state - full phase, no travel, full alpha. */
    public static final AnimationState FULL = new AnimationState(1f, 0f, 1f);
    
    public AnimationState {
        phase = Math.clamp(phase, 0f, 1f);
        travelPhase = Math.clamp(travelPhase, 0f, 1f);
        flickerAlpha = Math.clamp(flickerAlpha, 0f, 1f);
    }
    
    public AnimationState withPhase(float p) {
        return new AnimationState(p, travelPhase, flickerAlpha);
    }
    
    public AnimationState withTravelPhase(float t) {
        return new AnimationState(phase, t, flickerAlpha);
    }
    
    public AnimationState withFlickerAlpha(float a) {
        return new AnimationState(phase, travelPhase, a);
    }
}
