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
 * Uniform distribution - even spacing with minimal variation.
 * 
 * <p>Based on RayPositioner.computeDistribution UNIFORM case:
 * - lengthMod = 1 - lengthVariation * random
 * - angleJitter = randomness * (random - 0.5) * 2π/count * 0.5
 * </p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner#computeDistribution
 */
public final class UniformDistribution implements DistributionStrategy {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    public static final UniformDistribution INSTANCE = new UniformDistribution();
    
    private UniformDistribution() {}
    
    @Override
    public DistributionResult compute(RaysShape shape, int index, int count, Random rng) {
        // From RayPositioner.computeDistribution lines 1002-1005:
        // case UNIFORM -> {
        //     lengthMod = 1f - lengthVariation * rng.nextFloat();
        //     angleJitter = randomness * (rng.nextFloat() - 0.5f) * TWO_PI / count * 0.5f;
        // }
        
        float lengthVariation = shape.lengthVariation();
        float randomness = shape.randomness();
        
        float lengthMod = 1f - lengthVariation * rng.nextFloat();
        float angleJitter = randomness * (rng.nextFloat() - 0.5f) * TWO_PI / count * 0.5f;
        
        return new DistributionResult(0f, lengthMod, angleJitter, 0f);
    }
}
