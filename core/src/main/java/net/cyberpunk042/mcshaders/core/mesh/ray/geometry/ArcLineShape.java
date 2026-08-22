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
 * Arc line shape - single smooth curve.
 * 
 * <p>Based on RayGeometryUtils.computeLineShapeOffsetWithPhase:
 * curve = amplitude * sin(t * π)
 * Offset in up direction (bowing effect).</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayGeometryUtils#computeLineShapeOffsetWithPhase
 */
public final class ArcLineShape implements LineShapeStrategy {
    
    private static final float PI = (float) Math.PI;
    
    public static final ArcLineShape INSTANCE = new ArcLineShape();
    
    private ArcLineShape() {}
    
    @Override
    public float[] computeOffset(float t, float amplitude, float frequency, float phaseOffset) {
        // From RayGeometryUtils.computeLineShapeOffsetWithPhase lines 206-210:
        // curve = amplitude * sin(t * PI)
        // offset in up direction
        
        float curve = amplitude * (float) Math.sin(t * PI);
        
        // Return [right_amount, up_amount, 0]
        // Arc goes in UP direction (Y), not right
        return new float[] { 0, curve, 0 };
    }
    
    @Override
    public int suggestedMinSegments() {
        return 12;
    }
}
