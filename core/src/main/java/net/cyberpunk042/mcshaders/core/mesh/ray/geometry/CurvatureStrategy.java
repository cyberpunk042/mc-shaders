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
 * Strategy for applying curvature to ray paths.
 * 
 * Different curvature modes (NONE, VORTEX, SPIRAL, GRAVITATIONAL)
 * implement this interface.
 */
public interface CurvatureStrategy {
    
    /**
     * Apply curvature to a position.
     * 
     * @param position Input position [x, y, z]
     * @param t Parameter along ray (0-1)
     * @param intensity Curvature intensity
     * @param center Field center position [x, y, z]
     * @return Curved position [x, y, z] (may be same array, modified)
     */
    float[] apply(float[] position, float t, float intensity, float[] center);
    
    /**
     * Name for debugging.
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
