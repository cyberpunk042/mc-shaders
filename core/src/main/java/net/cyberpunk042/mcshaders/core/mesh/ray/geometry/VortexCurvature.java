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
 * Vortex curvature - rays curve progressively around Y axis.
 * 
 * <p>Based on RayGeometryUtils.computeCurvatureAngle:
 * angle = t * π * 0.5 * intensity
 * Creates a spinning/whirlpool effect.</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayGeometryUtils#computeCurvedPosition
 */
public final class VortexCurvature implements CurvatureStrategy {
    
    private static final float PI = (float) Math.PI;
    
    public static final VortexCurvature INSTANCE = new VortexCurvature();
    
    private VortexCurvature() {}
    
    @Override
    public float[] apply(float[] position, float t, float intensity, float[] center) {
        if (intensity < 0.001f) {
            return position;
        }
        
        // From RayGeometryUtils.computeCurvatureAngle line 308:
        // VORTEX -> t * PI * 0.5f
        float angle = t * PI * 0.5f * intensity;
        
        // From RayGeometryUtils.computeCurvedPosition lines 249-256:
        // Rotate around Y axis (simplified curvature)
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        
        float x = position[0] * cos - position[2] * sin;
        float z = position[0] * sin + position[2] * cos;
        
        position[0] = x;
        position[2] = z;
        
        return position;
    }
}
