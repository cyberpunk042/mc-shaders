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

/**
 * Result of distribution computation.
 * 
 * <p>Based on RayPositioner.DistributionResult, contains all offset values
 * needed by arrangement strategies.</p>
 * 
 * @param startOffset Start position offset along the ray
 * @param lengthMod Length multiplier (0.5 = half length, 1.0 = full)
 * @param angleJitter Angular offset in radians
 * @param radiusJitter Radial position jitter multiplier
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner#computeDistribution
 */
public record DistributionResult(
    float startOffset,
    float lengthMod,
    float angleJitter,
    float radiusJitter
) {
    /**
     * No distribution effect - uniform with no variation.
     */
    public static final DistributionResult NONE = new DistributionResult(0f, 1f, 0f, 0f);
}
