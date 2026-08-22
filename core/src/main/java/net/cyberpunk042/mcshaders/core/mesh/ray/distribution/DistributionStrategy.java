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
 * Strategy for computing distribution offsets for ray positioning.
 * 
 * <p>Distribution strategies add variation to ray positions based on
 * different algorithms (UNIFORM, RANDOM, STOCHASTIC).</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner#computeDistribution
 */
public interface DistributionStrategy {
    
    /**
     * Compute distribution offsets for a ray.
     * 
     * @param shape The rays shape configuration
     * @param index Ray index (0 to count-1)
     * @param count Total number of rays
     * @param rng Random number generator
     * @return Distribution result with offset values
     */
    DistributionResult compute(RaysShape shape, int index, int count, Random rng);
}
