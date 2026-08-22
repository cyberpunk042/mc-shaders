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

/**
 * Immutable context passed to FlowStage processors.
 * 
 * <p>Contains all the information a FlowStage needs to compute the animation.</p>
 * 
 * <p>Architecture note: Wave parameters (waveArc, waveDistribution, waveCount)
 * come from the SHAPE, not from the animation config. This supports the
 * Energy Interaction model where the shape defines appearance and
 * animation config defines timing.</p>
 * 
 * @param config The flow animation configuration (timing/speed)
 * @param rayIndex Index of the current ray (0 to rayCount-1)
 * @param rayCount Total number of rays
 * @param time Current animation time
 * @param innerRadius Inner radius of ray field
 * @param outerRadius Outer radius of ray field
 * @param waveArc Wave arc from RaysShape (phase scaling)
 * @param waveDistribution Wave distribution from RaysShape
 * @param waveCount Wave count (sweep copies) from RaysShape
 */
public record FlowContext(
    RayFlowConfig config,
    int rayIndex,
    int rayCount,
    float time,
    float innerRadius,
    float outerRadius,
    // Wave parameters from shape
    float waveArc,
    WaveDistribution waveDistribution,
    float waveCount
) {
    /**
     * Simplified constructor without wave parameters (uses defaults).
     */
    public FlowContext(RayFlowConfig config, int rayIndex, int rayCount, 
                       float time, float innerRadius, float outerRadius) {
        this(config, rayIndex, rayCount, time, innerRadius, outerRadius,
             1.0f, WaveDistribution.CONTINUOUS, 2.0f);
    }
    
    /**
     * Travel distance (outer - inner).
     */
    public float travelDistance() {
        return outerRadius - innerRadius;
    }
    
    /**
     * Normalized ray index (0-1 across all rays).
     */
    public float normalizedIndex() {
        return rayCount > 1 ? (float) rayIndex / (rayCount - 1) : 0f;
    }
    
    /**
     * Create a context with wave parameters from a RaysShape.
     */
    public static FlowContext create(
            RayFlowConfig config,
            net.cyberpunk042.mcshaders.core.shape.RaysShape shape,
            int rayIndex, int rayCount,
            float time, float innerRadius, float outerRadius) {
        
        float waveArc = shape != null ? shape.effectiveWaveArc() : 1.0f;
        WaveDistribution waveDist = shape != null ? shape.effectiveWaveDistribution() : WaveDistribution.CONTINUOUS;
        float waveCount = shape != null ? shape.effectiveWaveCount() : 2.0f;
        
        return new FlowContext(config, rayIndex, rayCount, time, innerRadius, outerRadius,
                               waveArc, waveDist, waveCount);
    }
}
