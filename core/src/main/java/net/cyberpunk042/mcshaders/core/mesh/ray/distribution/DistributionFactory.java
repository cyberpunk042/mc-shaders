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

import net.cyberpunk042.mcshaders.core.shape.RayDistribution;

/**
 * Factory for creating DistributionStrategy instances.
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner#computeDistribution
 */
public final class DistributionFactory {
    
    private DistributionFactory() {}
    
    /**
     * Get the appropriate strategy for a distribution type.
     */
    public static DistributionStrategy get(RayDistribution distribution) {
        if (distribution == null) {
            return UniformDistribution.INSTANCE;
        }
        
        return switch (distribution) {
            case UNIFORM -> UniformDistribution.INSTANCE;
            case RANDOM -> RandomDistribution.INSTANCE;
            case STOCHASTIC -> StochasticDistribution.INSTANCE;
        };
    }
}
