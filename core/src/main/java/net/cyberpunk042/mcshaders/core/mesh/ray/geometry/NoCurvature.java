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
 * No curvature - identity transform.
 */
public final class NoCurvature implements CurvatureStrategy {
    
    public static final NoCurvature INSTANCE = new NoCurvature();
    
    private NoCurvature() {}
    
    @Override
    public float[] apply(float[] position, float t, float intensity, float[] center) {
        // No change - return as-is
        return position;
    }
}
