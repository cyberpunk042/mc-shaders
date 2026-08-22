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
package net.cyberpunk042.mcshaders.core.mesh.ray.geometry;

/**
 * Zigzag line shape - sharp triangular wave pattern.
 * 
 * <p>Based on RayGeometryUtils.computeLineShapeOffsetWithPhase:
 * phase = (t * frequency) % 1.0
 * triangle = phase &lt; 0.5 ? (phase * 4 - 1) : (3 - phase * 4)
 * Offset in right direction.</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayGeometryUtils#computeLineShapeOffsetWithPhase
 */
public final class ZigzagLineShape implements LineShapeStrategy {
    
    public static final ZigzagLineShape INSTANCE = new ZigzagLineShape();
    
    private ZigzagLineShape() {}
    
    @Override
    public float[] computeOffset(float t, float amplitude, float frequency, float phaseOffset) {
        // From RayGeometryUtils.computeLineShapeOffsetWithPhase lines 182-188:
        // phase = (t * frequency) % 1.0
        // triangle = phase < 0.5 ? (phase * 4 - 1) : (3 - phase * 4)
        // wave = amplitude * triangle
        
        // Adjust t by phase offset
        float adjustedT = t + phaseOffset / ((float) Math.PI * 2);
        float phase = (adjustedT * frequency) % 1.0f;
        if (phase < 0) phase += 1.0f;
        
        // Triangle wave: linear ascent then descent
        float triangle = phase < 0.5f ? (phase * 4 - 1) : (3 - phase * 4);
        float wave = amplitude * triangle;
        
        return new float[] { wave, 0, 0 };
    }
    
    @Override
    public int suggestedMinSegments() {
        return 8;  // Sharp corners need fewer segments
    }
}
