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
 * Corkscrew/Helix line shape - spiral around the ray axis.
 * 
 * <p>Based on RayGeometryUtils.computeLineShapeOffsetWithPhase:
 * cos = cos(theta) * amplitude
 * sin = sin(theta) * amplitude
 * Offset in both right and up directions for full helix.</p>
 * 
 * <p>Also used for DOUBLE_HELIX (called twice with 180° phase difference).</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayGeometryUtils#computeLineShapeOffsetWithPhase
 */
public final class CorkscrewLineShape implements LineShapeStrategy {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    public static final CorkscrewLineShape INSTANCE = new CorkscrewLineShape();
    
    private CorkscrewLineShape() {}
    
    @Override
    public float[] computeOffset(float t, float amplitude, float frequency, float phaseOffset) {
        // From RayGeometryUtils.computeLineShapeOffsetWithPhase lines 155, 165-170:
        // theta = t * frequency * TWO_PI + phaseOffset
        // cos = cos(theta) * amplitude
        // sin = sin(theta) * amplitude
        // offset = right * cos + up * sin
        float theta = t * frequency * TWO_PI + phaseOffset;
        float cos = (float) Math.cos(theta) * amplitude;
        float sin = (float) Math.sin(theta) * amplitude;
        
        // Return [right_amount, up_amount, 0]
        return new float[] { cos, sin, 0 };
    }
    
    @Override
    public int suggestedMinSegments() {
        return 24;
    }
}
