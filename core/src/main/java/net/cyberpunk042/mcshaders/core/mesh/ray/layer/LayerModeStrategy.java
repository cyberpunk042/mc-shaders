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
package net.cyberpunk042.mcshaders.core.mesh.ray.layer;

import net.cyberpunk042.mcshaders.core.shape.RaysShape;

/**
 * Strategy for computing layer offsets.
 * 
 * <p>Different layer modes produce different offset patterns:</p>
 * <ul>
 *   <li><b>VERTICAL:</b> Layers stacked along Y axis</li>
 *   <li><b>RADIAL:</b> Layers extend further outward (each starts where previous ends)</li>
 *   <li><b>SHELL:</b> Concentric shells at increasing radii</li>
 *   <li><b>SPIRAL:</b> Both angular and radial offset (spiral pattern)</li>
 * </ul>
 * 
 * <p>Extracted from the radial-placement branch of the ray positioner.</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner
 * @see LayerOffset
 * @see LayerModeFactory
 */
public interface LayerModeStrategy {
    
    /**
     * Compute the offset for a layer.
     * 
     * @param shape The rays shape configuration
     * @param layerIndex Index of the layer (0 to layers-1)
     * @param layerSpacing Spacing between layers
     * @return LayerOffset with yOffset, radiusOffset, and angleOffset
     */
    LayerOffset computeOffset(RaysShape shape, int layerIndex, float layerSpacing);
    
    /**
     * Name for debugging.
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
