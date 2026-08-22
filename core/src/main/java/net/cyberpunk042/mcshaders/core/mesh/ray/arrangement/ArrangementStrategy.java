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
package net.cyberpunk042.mcshaders.core.mesh.ray.arrangement;

import net.cyberpunk042.mcshaders.core.mesh.ray.distribution.DistributionResult;
import net.cyberpunk042.mcshaders.core.mesh.ray.layer.LayerOffset;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;

/**
 * Strategy for computing ray start/end positions based on arrangement mode.
 * 
 * <p>Arrangement strategies determine the spatial pattern of rays:</p>
 * <ul>
 *   <li><b>RADIAL:</b> Rays emanate outward on XZ plane (2D star pattern)</li>
 *   <li><b>SPHERICAL/DIVERGING:</b> Rays point outward from center in 3D</li>
 *   <li><b>CONVERGING:</b> Rays point inward toward center in 3D</li>
 *   <li><b>PARALLEL:</b> All rays point the same direction (grid pattern)</li>
 * </ul>
 * 
 * <p>Each arrangement receives:</p>
 * <ul>
 *   <li>{@link LayerOffset} - computed by {@link net.cyberpunk042.mcshaders.core.mesh.ray.layer.LayerModeFactory}</li>
 *   <li>{@link DistributionResult} - computed by {@link net.cyberpunk042.mcshaders.core.mesh.ray.distribution.DistributionFactory}</li>
 * </ul>
 * 
 * @see ArrangementFactory
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner
 */
public interface ArrangementStrategy {
    
    /**
     * Compute the start and end positions for a ray.
     * 
     * @param shape The rays shape configuration
     * @param index Ray index (0 to count-1)
     * @param count Total ray count
     * @param layerOffset Pre-computed layer offset (from LayerModeFactory)
     * @param dist Pre-computed distribution jitter (from DistributionFactory)
     * @param outStart Output array for start position [x, y, z]
     * @param outEnd Output array for end position [x, y, z]
     */
    void compute(
        RaysShape shape,
        int index,
        int count,
        LayerOffset layerOffset,
        DistributionResult dist,
        float[] outStart,
        float[] outEnd
    );
    
    /**
     * Name for debugging/logging.
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
