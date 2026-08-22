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
 * Radial arrangement - rays emanate outward on XZ plane like sun rays.
 * 
 * <pre>
 *          ↑
 *         /|\
 *      ←─●─→
 *         \|/
 *          ↓
 * </pre>
 * 
 * <p>Based on RayPositioner.computeRadial lines 1043-1077.</p>
 * 
 * @see ArrangementStrategy
 */
public final class RadialArrangement implements ArrangementStrategy {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    public static final RadialArrangement INSTANCE = new RadialArrangement();
    
    private RadialArrangement() {}
    
    @Override
    public void compute(
            RaysShape shape,
            int index,
            int count,
            LayerOffset layerOffset,
            DistributionResult dist,
            float[] outStart,
            float[] outEnd) {
        
        float innerRadius = shape.innerRadius();
        float outerRadius = shape.outerRadius();
        
        // Base angle with distribution jitter
        float angle = (index * TWO_PI / count) + dist.angleJitter();
        
        // Apply angular offset from layer (for SPIRAL mode)
        if (layerOffset.angleOffset() != 0) {
            angle += layerOffset.angleOffset();
        }
        
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        
        // Compute radii - SIMPLE: use the configured values directly
        // innerR = innerRadius, outerR = outerRadius
        // rayLength is NOT used here - it's used in tessellation for the animated segment
        boolean unifiedEnd = shape.unifiedEnd();
        float innerR;
        float outerR;
        
        if (unifiedEnd) {
            // Unified end: inner radius is SAME for all layers
            innerR = innerRadius + dist.startOffset();
            outerR = outerRadius + layerOffset.radiusOffset() + dist.startOffset();
        } else {
            // Standard: both radii include layer offset
            innerR = innerRadius + layerOffset.radiusOffset() + dist.startOffset();
            outerR = outerRadius + layerOffset.radiusOffset() + dist.startOffset();
        }
        
        // Apply radius jitter
        innerR *= (1 + dist.radiusJitter());
        outerR *= (1 + dist.radiusJitter());
        
        // Set output positions
        outStart[0] = cos * innerR;
        outStart[1] = layerOffset.yOffset();
        outStart[2] = sin * innerR;
        
        outEnd[0] = cos * outerR;
        outEnd[1] = layerOffset.yOffset();
        outEnd[2] = sin * outerR;
    }
    
    @Override
    public String name() {
        return "RADIAL";
    }
}
