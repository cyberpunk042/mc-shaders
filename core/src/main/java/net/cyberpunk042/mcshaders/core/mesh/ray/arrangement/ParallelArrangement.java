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
 * Parallel arrangement - rays arranged in a grid, all pointing the same direction.
 * 
 * <pre>
 *   →  →  →
 *   →  →  →
 *   →  →  →
 * </pre>
 * 
 * <p>Rays are positioned in a grid on the XZ plane, all pointing along the Y axis.</p>
 * 
 * <p>Based on RayPositioner.computeParallel lines 1140-1168.</p>
 * 
 * @see ArrangementStrategy
 */
public final class ParallelArrangement implements ArrangementStrategy {
    
    public static final ParallelArrangement INSTANCE = new ParallelArrangement();
    
    private ParallelArrangement() {}
    
    @Override
    public void compute(
            RaysShape shape,
            int index,
            int count,
            LayerOffset layerOffset,
            DistributionResult dist,
            float[] outStart,
            float[] outEnd) {
        
        float outerRadius = shape.outerRadius();
        float rayLength = shape.rayLength();
        
        // Arrange in a grid on XZ plane
        int gridSize = (int) Math.ceil(Math.sqrt(count));
        int gx = index % gridSize;
        int gz = index / gridSize;
        
        float spacing = outerRadius * 2 / gridSize;
        float x = (gx - gridSize / 2.0f + 0.5f) * spacing;
        float z = (gz - gridSize / 2.0f + 0.5f) * spacing;
        
        // Apply distribution jitter to grid positions
        x += dist.angleJitter() * spacing;
        z += dist.radiusJitter() * spacing;
        
        // For parallel, layer offset adds to Z (depth stacking)
        z += layerOffset.radiusOffset();
        
        // Rays point up (along Y axis)
        // Apply distribution modifiers
        float yStart = -rayLength * dist.lengthMod() / 2 + dist.startOffset();
        float yEnd = rayLength * dist.lengthMod() / 2 + dist.startOffset();
        
        outStart[0] = x;
        outStart[1] = yStart;
        outStart[2] = z;
        
        outEnd[0] = x;
        outEnd[1] = yEnd;
        outEnd[2] = z;
    }
    
    @Override
    public String name() {
        return "PARALLEL";
    }
}
