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
package net.cyberpunk042.mcshaders.core.mesh.ray.flow;

import net.cyberpunk042.mcshaders.core.animation.RayFlowConfig;
import net.cyberpunk042.mcshaders.core.animation.WaveDistribution;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;

/**
 * Computes the base phase from time, ray index, and config.
 * 
 * <p>Phase is computed based on:
 * <ul>
 *   <li>radiativeSpeed from RayFlowConfig (how fast animation runs)</li>
 *   <li>waveArc, waveDistribution, waveCount from RaysShape (wave pattern)</li>
 * </ul>
 * 
 * <p>Wave distribution controls how rays are phased relative to each other:
 * <ul>
 *   <li>CONTINUOUS: Golden ratio scrambled phases (no visible sweep pattern)</li>
 *   <li>RANDOM: Hash-based random phases (per-ray stable but random)</li>
 *   <li>SEQUENTIAL: Rays phased based on angular position (visible sweep)</li>
 * </ul>
 * 
 * @see RayFlowConfig
 * @see RaysShape
 */
public final class FlowPhaseStage implements FlowStage {
    
    private static final float GOLDEN_RATIO = 1.618033988749895f;
    
    public static final FlowPhaseStage INSTANCE = new FlowPhaseStage();
    
    private FlowPhaseStage() {}
    
    @Override
    public AnimationState process(AnimationState state, FlowContext ctx) {
        RayFlowConfig config = ctx.config();
        if (config == null || !config.hasRadiative()) {
            return state;
        }
        
        // Wave config comes from shape (via context)
        float waveArc = ctx.waveArc();
        WaveDistribution waveDist = ctx.waveDistribution();
        float waveCount = ctx.waveCount();
        
        // Compute per-ray angular position based on distribution
        float rayAngle = computeRayAngle(waveDist, ctx.rayIndex(), ctx.rayCount());
        
        // Apply wave arc scaling
        float scaledAngle = rayAngle * waveArc;
        
        // Sweep copies
        float sweepCopies = Math.max(0.1f, waveCount);
        
        // Compute phase from time and speed
        float basePhase = (ctx.time() * config.radiativeSpeed()) % 1.0f;
        if (basePhase < 0) basePhase += 1.0f;
        
        float phase;
        if (waveDist == WaveDistribution.CONTINUOUS) {
            // CONTINUOUS: All rays have the same phase (uniform animation)
            phase = basePhase;
        } else {
            // SEQUENTIAL/RANDOM: Apply sweepCopies for sweep effect
            phase = (basePhase + scaledAngle * sweepCopies) % 1.0f;
        }
        
        if (phase < 0) phase += 1.0f;
        
        return state.withPhase(phase);
    }
    
    /**
     * Compute phase for a ray based on config, shape, ray index, ray count, and time.
     * 
     * <p>This static method can be called directly from RayPositioner.</p>
     * 
     * @param config RayFlowConfig (speed/enable)
     * @param shape RaysShape (wave parameters)
     * @param rayIndex Index of this ray
     * @param rayCount Total number of rays
     * @param time Current animation time
     * @return Phase value (0-1)
     */
    public static float computePhase(RayFlowConfig config, RaysShape shape, 
            int rayIndex, int rayCount, float time) {
        if (config == null || !config.hasRadiative()) {
            return 0f;
        }
        
        // Get wave config from shape
        WaveDistribution waveDist = shape != null ? shape.effectiveWaveDistribution() : WaveDistribution.CONTINUOUS;
        float waveArc = shape != null ? shape.effectiveWaveArc() : 1.0f;
        float waveCount = shape != null ? shape.effectiveWaveCount() : 2.0f;
        
        // Compute per-ray angular position based on distribution
        float rayAngle = computeRayAngle(waveDist, rayIndex, rayCount);
        
        // Apply wave arc scaling
        float scaledAngle = rayAngle * waveArc;
        
        // Sweep copies
        float sweepCopies = Math.max(0.1f, waveCount);
        
        // Compute phase
        float basePhase = (time * config.radiativeSpeed()) % 1.0f;
        if (basePhase < 0) basePhase += 1.0f;
        
        float phase;
        if (waveDist == WaveDistribution.CONTINUOUS) {
            // CONTINUOUS: All rays have the same phase (uniform animation)
            phase = basePhase;
        } else {
            // SEQUENTIAL/RANDOM: Apply sweepCopies for sweep effect
            phase = (basePhase + scaledAngle * sweepCopies) % 1.0f;
        }
        
        if (phase < 0) phase += 1.0f;
        return phase;
    }
    
    /**
     * Legacy overload for backward compatibility.
     * Uses defaults for wave parameters.
     */
    public static float computePhase(RayFlowConfig config, int rayIndex, int rayCount, float time) {
        return computePhase(config, null, rayIndex, rayCount, time);
    }
    
    /**
     * Compute per-ray phase offset for wave distribution.
     * 
     * <p>This is the OFFSET that gets added to the base phase for each ray,
     * based on wave distribution settings. Used for both animated and static mode.</p>
     * 
     * @param shape RaysShape with wave parameters
     * @param rayIndex Index of this ray
     * @param rayCount Total number of rays
     * @return Phase offset (0-1) to add to base phase
     */
    public static float computeRayPhaseOffset(RaysShape shape, int rayIndex, int rayCount) {
        if (shape == null || rayCount <= 1) {
            return 0f;
        }
        
        WaveDistribution waveDist = shape.effectiveWaveDistribution();
        
        // CONTINUOUS: All rays have the SAME phase (no offset)
        if (waveDist == WaveDistribution.CONTINUOUS) {
            return 0f;
        }
        
        // Compute per-ray angular position based on distribution
        float rayAngle = computeRayAngle(waveDist, rayIndex, rayCount);
        
        // Apply waveArc and sweepCopies for sweep effect
        float waveArc = shape.effectiveWaveArc();
        float waveCount = Math.max(0.1f, shape.effectiveWaveCount());
        float scaledAngle = rayAngle * waveArc;
        return (scaledAngle * waveCount) % 1.0f;
    }
    
    /**
     * Compute per-ray angle based on wave distribution.
     * 
     * @param waveDist Wave distribution mode
     * @param rayIndex Index of this ray
     * @param rayCount Total number of rays
     * @return Ray angle offset (0-1)
     */
    public static float computeRayAngle(WaveDistribution waveDist, int rayIndex, int rayCount) {
        if (waveDist == WaveDistribution.CONTINUOUS) {
            // CONTINUOUS: All rays same phase - no offset
            return 0f;
        } else if (waveDist == WaveDistribution.GOLDEN_RATIO) {
            // GOLDEN_RATIO: Aesthetically pleasing distribution using golden ratio
            return (rayIndex * GOLDEN_RATIO) % 1.0f;
        } else if (waveDist == WaveDistribution.RANDOM) {
            // Use hash for consistent but random-looking distribution
            int hash = (int)(rayIndex * 2654435761L);
            return (hash & 0x7FFFFFFF) / (float) Integer.MAX_VALUE;
        } else {
            // SEQUENTIAL: Rays are phased based on angular position
            return rayCount > 1 ? (float) rayIndex / rayCount : 0f;
        }
    }
    
    @Override
    public boolean shouldRun(FlowContext ctx) {
        if (ctx.config() == null) return false;
        return ctx.config().hasRadiative();
    }
    
    @Override
    public String name() {
        return "PhaseStage";
    }
}
