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
 * S-curve line shape - smooth S-shaped curve.
 * 
 * <p>Based on RayGeometryUtils.computeLineShapeOffsetWithPhase:
 * curve = amplitude * sin(t * 2π)
 * Offset in right direction.</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayGeometryUtils#computeLineShapeOffsetWithPhase
 */
public final class SCurveLineShape implements LineShapeStrategy {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    public static final SCurveLineShape INSTANCE = new SCurveLineShape();
    
    private SCurveLineShape() {}
    
    @Override
    public float[] computeOffset(float t, float amplitude, float frequency, float phaseOffset) {
        // From RayGeometryUtils.computeLineShapeOffsetWithPhase lines 213-217:
        // curve = amplitude * sin(t * TWO_PI)
        // offset in right direction
        
        float curve = amplitude * (float) Math.sin(t * TWO_PI);
        
        return new float[] { curve, 0, 0 };
    }
    
    @Override
    public int suggestedMinSegments() {
        return 16;
    }
}
