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
 * Radial layer mode - layers extend radially outward.
 * 
 * <p>Based on RayPositioner.computeRadial lines 1062-1065:</p>
 * <pre>
 * layerRadiusOffset = layerIndex * rayLength;
 * </pre>
 * 
 * <p>Each layer starts where the previous layer ends.
 * Layer 0: innerRadius to innerRadius + rayLength
 * Layer 1: innerRadius + rayLength to innerRadius + 2*rayLength
 * etc.</p>
 * 
 * @see LayerModeStrategy
 */
public final class RadialLayerMode implements LayerModeStrategy {
    
    public static final RadialLayerMode INSTANCE = new RadialLayerMode();
    
    private RadialLayerMode() {}
    
    @Override
    public LayerOffset computeOffset(RaysShape shape, int layerIndex, float layerSpacing) {
        // Each layer extends outward by rayLength
        // This creates concentric rings where each layer starts where previous ends
        float rayLength = shape.rayLength();
        float radiusOffset = layerIndex * rayLength;
        
        return LayerOffset.radial(radiusOffset);
    }
    
    @Override
    public String name() {
        return "RADIAL";
    }
}
