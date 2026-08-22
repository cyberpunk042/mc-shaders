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
package net.cyberpunk042.mcshaders.core.mesh.ray.tessellation;

import net.cyberpunk042.mcshaders.core.shape.EdgeTransitionMode;

/**
 * Factory for computing edge transition effects based on EdgeTransitionMode.
 * 
 * <h2>EdgeTransitionMode</h2>
 * <p>Controls how the ray segment appears at its edges during animation:</p>
 * <ul>
 *   <li><b>CLIP:</b> Portions of the shape are geometrically clipped at edges</li>
 *   <li><b>SCALE:</b> The shape uniformly shrinks as it approaches edges</li>
 *   <li><b>FADE:</b> The shape fades with gradient alpha based on edge proximity</li>
 * </ul>
 * 
 * <h2>Edge Intensity</h2>
 * <p>The intensity parameter (0-1) controls how strong the edge effect is:</p>
 * <ul>
 *   <li><b>0:</b> No edge effect - shape is fully visible regardless of position</li>
 *   <li><b>1:</b> Full edge effect - maximum clipping/scaling/fading at edges</li>
 * </ul>
 * 
 * <h2>Usage with RadiativeInteraction</h2>
 * <p>RadiativeInteraction determines WHERE the segment is positioned on the ray (clipStart/clipEnd).
 * EdgeTransitionMode determines HOW the shape looks at boundaries as it moves.</p>
 * 
 * <h2>Key Insight</h2>
 * <p>SCALE and FADE are based on <b>shape center proximity to edges</b>, NOT on segment size.
 * When the shape center (midpoint of clipRange) is at t=0.5, effects are minimal.
 * When the shape center approaches t=0 or t=1, effects are maximal.</p>
 * 
 * @see EdgeTransitionMode
 * @see RadiativeInteractionFactory
 */
public final class TessEdgeModeFactory {
    
    private TessEdgeModeFactory() {} // Utility class
    
    /**
     * Compute edge effect based on EdgeTransitionMode, clip range, and intensity.
     * 
     * <p>EdgeMode controls what happens at ray trajectory boundaries (phase ~0 or ~1):
     * <ul>
     *   <li><b>CLIP:</b> Geometrically clips the segment - invisible outside boundaries</li>
     *   <li><b>SCALE:</b> Shrinks the line width as it approaches edges</li>
     *   <li><b>FADE:</b> Alpha gradient across the visible segment near edges</li>
     * </ul>
     * 
     * @param edgeMode The edge transition mode (CLIP/SCALE/FADE)
     * @param clipStart Start of visible segment (0-1), from RadiativeInteraction
     * @param clipEnd End of visible segment (0-1), from RadiativeInteraction
     * @param intensity Edge effect intensity (0=no effect, 1=full effect)
     * @return Edge result with appropriate effect applied
     */
    public static TessEdgeResult compute(EdgeTransitionMode edgeMode, float clipStart, float clipEnd, float intensity) {
        if (edgeMode == null) {
            edgeMode = EdgeTransitionMode.CLIP;
        }
        
        // Clamp intensity
        intensity = Math.clamp(intensity, 0f, 1f);
        
        // Compute the segment center position (where the shape is on the ray)
        float segmentCenter = (clipStart + clipEnd) * 0.5f;
        
        // Compute edge proximity: how close is the segment center to either edge?
        // At t=0 or t=1: proximity = 0 (at edge)
        // At t=0.5: proximity = 1 (center of ray, farthest from edges)
        float distanceToStart = segmentCenter;           // Distance from t=0
        float distanceToEnd = 1.0f - segmentCenter;      // Distance from t=1
        float edgeProximity = Math.min(distanceToStart, distanceToEnd) * 2.0f; // Normalize to [0,1]
        edgeProximity = Math.clamp(edgeProximity, 0f, 1f);
        
        return switch (edgeMode) {
            case CLIP -> {
                // CLIP mode: use the segment position directly
                // No need to modify - the segment is already positioned by RadiativeInteraction
                // Just return the clip bounds (no alpha/scale effects)
                yield new TessEdgeResult(clipStart, clipEnd, 1f, 1f, distanceToStart, distanceToEnd);
            }
            case SCALE -> {
                // SCALE mode: full ray visibility, but scale (line width) varies by edge proximity
                // effectiveScale = 1 when far from edges, scales down near edges
                // Intensity controls how much scaling occurs
                float effectiveScale = 1.0f - (1.0f - edgeProximity) * intensity;
                yield new TessEdgeResult(0f, 1f, effectiveScale, 1f, distanceToStart, distanceToEnd);
            }
            case FADE -> {
                // FADE mode: full ray visibility, full scale
                // Alpha gradient is computed per-vertex based on position
                // Pass edge distances modified by intensity - this controls the fade range
                // Low edgeDistance = more fade; intensity=0 means edgeDistance=1 (no fade)
                float fadeDistStart = 1.0f - (1.0f - distanceToStart) * intensity;
                float fadeDistEnd = 1.0f - (1.0f - distanceToEnd) * intensity;
                // Also set base alpha based on overall proximity
                float baseAlpha = 1.0f - (1.0f - edgeProximity) * intensity * 0.5f;
                yield new TessEdgeResult(0f, 1f, 1f, baseAlpha, fadeDistStart, fadeDistEnd);
            }
        };
    }
    
    /**
     * Compute edge effect with default intensity of 1.0 (full effect).
     */
    public static TessEdgeResult compute(EdgeTransitionMode edgeMode, float clipStart, float clipEnd) {
        return compute(edgeMode, clipStart, clipEnd, 1.0f);
    }
    
    /**
     * Compute edge effect from ClipRange (from RadiativeInteractionFactory).
     * 
     * @param edgeMode The edge transition mode
     * @param clipRange The clip range from RadiativeInteractionFactory
     * @param intensity Edge effect intensity (0=no effect, 1=full effect)
     * @return Edge result with appropriate effect applied
     */
    public static TessEdgeResult compute(EdgeTransitionMode edgeMode, RadiativeInteractionFactory.ClipRange clipRange, float intensity) {
        if (clipRange == null) {
            return TessEdgeResult.FULL;
        }
        return compute(edgeMode, clipRange.start(), clipRange.end(), intensity);
    }
    
    /**
     * Compute edge effect from ClipRange with default intensity of 1.0.
     */
    public static TessEdgeResult compute(EdgeTransitionMode edgeMode, RadiativeInteractionFactory.ClipRange clipRange) {
        return compute(edgeMode, clipRange, 1.0f);
    }
}
