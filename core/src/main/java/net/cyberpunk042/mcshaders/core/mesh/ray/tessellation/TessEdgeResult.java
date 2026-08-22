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

/**
 * Result of tessellation edge computation.
 * 
 * <h2>What Each Value Means</h2>
 * <ul>
 *   <li><b>clipStart:</b> Parametric start of visible region (0-1). 
 *       Geometry before this is clipped/hidden. Used by CLIP mode.</li>
 *   <li><b>clipEnd:</b> Parametric end of visible region (0-1).
 *       Geometry after this is clipped/hidden. Used by CLIP mode.</li>
 *   <li><b>scale:</b> Uniform size multiplier (0-1).
 *       Used by SCALE mode - shape shrinks uniformly as it approaches edges.</li>
 *   <li><b>alpha:</b> Base alpha multiplier (0-1).
 *       Used by FADE mode - shape fades as it approaches edges.</li>
 *   <li><b>edgeDistanceStart:</b> Distance from shape center to t=0 boundary (0-1).
 *       Used for gradient fading - 0=at start edge, 1=far from start.</li>
 *   <li><b>edgeDistanceEnd:</b> Distance from shape center to t=1 boundary (0-1).
 *       Used for gradient fading - 0=at end edge, 1=far from end.</li>
 * </ul>
 * 
 * @param clipStart Start of visible t-range (0-1)
 * @param clipEnd End of visible t-range (0-1)
 * @param scale Uniform size multiplier (0-1)
 * @param alpha Base alpha multiplier (0-1)
 * @param edgeDistanceStart Distance from shape to start edge (for fade gradient)
 * @param edgeDistanceEnd Distance from shape to end edge (for fade gradient)
 */
public record TessEdgeResult(
    float clipStart,
    float clipEnd,
    float scale,
    float alpha,
    float edgeDistanceStart,
    float edgeDistanceEnd
) {
    /**
     * Fully visible, no modifications.
     */
    public static final TessEdgeResult FULL = new TessEdgeResult(0f, 1f, 1f, 1f, 0.5f, 0.5f);
    
    /**
     * Completely hidden.
     */
    public static final TessEdgeResult HIDDEN = new TessEdgeResult(0f, 0f, 0f, 0f, 0f, 0f);
    
    public TessEdgeResult {
        clipStart = Math.clamp(clipStart, 0f, 1f);
        clipEnd = Math.clamp(clipEnd, 0f, 1f);
        scale = Math.clamp(scale, 0f, 1f);
        alpha = Math.clamp(alpha, 0f, 1f);
        edgeDistanceStart = Math.clamp(edgeDistanceStart, 0f, 1f);
        edgeDistanceEnd = Math.clamp(edgeDistanceEnd, 0f, 1f);
    }
    
    /**
     * Backward compatibility constructor (without edge distances).
     */
    public TessEdgeResult(float clipStart, float clipEnd, float scale, float alpha) {
        this(clipStart, clipEnd, scale, alpha, 
             (clipStart + clipEnd) * 0.5f,  // Distance to start = center position
             1.0f - (clipStart + clipEnd) * 0.5f);  // Distance to end
    }
    
    /**
     * Whether anything is visible.
     */
    public boolean isVisible() {
        return clipEnd > clipStart && (scale > 0.001f || alpha > 0.001f);
    }
    
    /**
     * Whether this uses geometric clipping.
     */
    public boolean hasClipping() {
        return clipStart > 0.001f || clipEnd < 0.999f;
    }
    
    /**
     * Whether this uses size scaling.
     */
    public boolean hasScaling() {
        return scale < 0.999f;
    }
    
    /**
     * Whether this uses alpha fading.
     */
    public boolean hasFading() {
        return alpha < 0.999f;
    }
    
    /**
     * Visible t-range (for iteration).
     */
    public float visibleRange() {
        return clipEnd - clipStart;
    }
    
    /**
     * Computes alpha for a vertex at position t based on edge proximity.
     * For FADE mode, creates a progressive gradient across the segment.
     * 
     * <p>The edgeDistanceStart/End values (modified by intensity in TessEdgeModeFactory)
     * control how wide the fade zone is:
     * - edgeDistance = 0: segment is AT the edge, wide fade zone
     * - edgeDistance = 1: segment is far from edge, no fade
     * 
     * The intensity slider controls this via the Factory.</p>
     * 
     * @param t Vertex position along the ray trajectory (0=inner radius, 1=outer radius)
     * @param baseAlpha Base alpha to multiply
     * @return Computed alpha with edge fade applied
     */
    public float computeFadeAlpha(float t, float baseAlpha) {
        // Fade width is derived from edge distance:
        // edgeDistance = 0 -> wide fade (0.4 = 40% of ray)
        // edgeDistance = 0.5 -> medium fade (0.2 = 20%)
        // edgeDistance = 1 -> no fade (0%)
        
        float fade = 1.0f;
        
        // FADE AT START EDGE (t approaching 0):
        float fadeWidthStart = (1.0f - edgeDistanceStart) * 0.4f;
        if (fadeWidthStart > 0.01f && t < fadeWidthStart) {
            // Linear fade: 0 at t=0, 1 at t=fadeWidthStart
            float startFade = t / fadeWidthStart;
            fade = Math.min(fade, startFade);
        }
        
        // FADE AT END EDGE (t approaching 1):
        float fadeWidthEnd = (1.0f - edgeDistanceEnd) * 0.4f;
        if (fadeWidthEnd > 0.01f && t > (1.0f - fadeWidthEnd)) {
            // Linear fade: 1 at t=(1-fadeWidthEnd), 0 at t=1
            float endFade = (1.0f - t) / fadeWidthEnd;
            fade = Math.min(fade, endFade);
        }
        
        return baseAlpha * Math.max(0f, fade);
    }
}
