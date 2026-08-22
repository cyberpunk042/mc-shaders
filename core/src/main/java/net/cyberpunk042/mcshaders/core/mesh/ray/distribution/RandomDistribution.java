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
 * Random distribution - per-frame random offsets.
 * 
 * <p>Based on RayPositioner.computeDistribution RANDOM case:
 * - lengthMod = 0.5 + 0.5 * random (50%-100% of length)
 * - startOffset = constrained random within available range
 * - angleJitter = randomness * (random - 0.5) * 2π/count
 * - radiusJitter = randomness * (random - 0.5) * 0.3
 * </p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner#computeDistribution
 */
public final class RandomDistribution implements DistributionStrategy {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    public static final RandomDistribution INSTANCE = new RandomDistribution();
    
    private RandomDistribution() {}
    
    @Override
    public DistributionResult compute(RaysShape shape, int index, int count, Random rng) {
        // From RayPositioner.computeDistribution lines 1006-1019:
        // case RANDOM -> {
        //     float availableRange = outerRadius - innerRadius;
        //     float maxLength = Math.min(rayLength, availableRange);
        //     
        //     lengthMod = 0.5f + 0.5f * rng.nextFloat();
        //     float actualLength = maxLength * lengthMod;
        //     
        //     float maxStartOffset = availableRange - actualLength;
        //     if (maxStartOffset > 0) {
        //         startOffset = maxStartOffset * rng.nextFloat();
        //     }
        //     
        //     angleJitter = randomness * (rng.nextFloat() - 0.5f) * TWO_PI / count;
        //     radiusJitter = randomness * (rng.nextFloat() - 0.5f) * 0.3f;
        // }
        
        float innerRadius = shape.innerRadius();
        float outerRadius = shape.outerRadius();
        float rayLength = shape.rayLength();
        float randomness = shape.randomness();
        
        float availableRange = outerRadius - innerRadius;
        float maxLength = Math.min(rayLength, availableRange);
        
        float lengthMod = 0.5f + 0.5f * rng.nextFloat();
        float actualLength = maxLength * lengthMod;
        
        float startOffset = 0f;
        float maxStartOffset = availableRange - actualLength;
        if (maxStartOffset > 0) {
            startOffset = maxStartOffset * rng.nextFloat();
        }
        
        float angleJitter = randomness * (rng.nextFloat() - 0.5f) * TWO_PI / count;
        float radiusJitter = randomness * (rng.nextFloat() - 0.5f) * 0.3f;
        
        return new DistributionResult(startOffset, lengthMod, angleJitter, radiusJitter);
    }
}
