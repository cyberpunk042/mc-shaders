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
package net.cyberpunk042.mcshaders.core.mesh.ray.distribution;

import net.cyberpunk042.mcshaders.core.shape.RaysShape;
import java.util.Random;

/**
 * Stochastic distribution - stable but random-looking patterns per ray.
 * 
 * <p>Based on RayPositioner.computeDistribution STOCHASTIC case:
 * - lengthMod = 0.2 + 0.8 * random (20%-100% of length)
 * - startOffset = constrained random with tighter bounds
 * - angleJitter = (random - 0.5) * 2π/count * 2 (double the angular variation)
 * - radiusJitter = (random - 0.5) * 0.5 (larger radial variation)
 * </p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner#computeDistribution
 */
public final class StochasticDistribution implements DistributionStrategy {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    public static final StochasticDistribution INSTANCE = new StochasticDistribution();
    
    private StochasticDistribution() {}
    
    @Override
    public DistributionResult compute(RaysShape shape, int index, int count, Random rng) {
        // From RayPositioner.computeDistribution lines 1021-1032:
        // case STOCHASTIC -> {
        //     float availableRange = outerRadius - innerRadius;
        //     
        //     lengthMod = 0.2f + 0.8f * rng.nextFloat();
        //     float actualLength = rayLength * lengthMod;
        //     
        //     float maxStartOffset = Math.max(0, availableRange - actualLength * 0.3f);
        //     startOffset = maxStartOffset * rng.nextFloat();
        //     
        //     angleJitter = (rng.nextFloat() - 0.5f) * TWO_PI / count * 2f;
        //     radiusJitter = (rng.nextFloat() - 0.5f) * 0.5f;
        // }
        
        float innerRadius = shape.innerRadius();
        float outerRadius = shape.outerRadius();
        float rayLength = shape.rayLength();
        
        float availableRange = outerRadius - innerRadius;
        
        float lengthMod = 0.2f + 0.8f * rng.nextFloat();
        float actualLength = rayLength * lengthMod;
        
        float maxStartOffset = Math.max(0, availableRange - actualLength * 0.3f);
        float startOffset = maxStartOffset * rng.nextFloat();
        
        float angleJitter = (rng.nextFloat() - 0.5f) * TWO_PI / count * 2f;
        float radiusJitter = (rng.nextFloat() - 0.5f) * 0.5f;
        
        return new DistributionResult(startOffset, lengthMod, angleJitter, radiusJitter);
    }
}
