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
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates the flow animation pipeline.
 * 
 * Runs a sequence of FlowStage processors to compute the final AnimationState.
 * 
 * <h2>Pipeline Stages</h2>
 * <ol>
 *   <li><b>PhaseStage:</b> Compute base phase from time (RADIATE, ABSORB, PULSE)</li>
 *   <li><b>TravelStage:</b> Apply travel effects (CHASE, SCROLL, BOUNCE)</li>
 *   <li><b>FlickerStage:</b> Apply flicker alpha overlay</li>
 * </ol>
 * 
 * <h2>Usage</h2>
 * <pre>
 * FlowPipeline pipeline = FlowPipeline.standard();
 * AnimationState result = pipeline.compute(ctx);
 * shape.getShapeState().withPhase(result.phase());
 * </pre>
 */
public final class FlowPipeline {
    
    private final List<FlowStage> stages;
    
    private FlowPipeline(List<FlowStage> stages) {
        this.stages = stages;
    }
    
    /**
     * Creates the standard pipeline with all stages.
     */
    public static FlowPipeline standard() {
        return new FlowPipeline(List.of(
            FlowPhaseStage.INSTANCE,
            FlowTravelStage.INSTANCE,
            FlowFlickerStage.INSTANCE
        ));
    }
    
    /**
     * Creates a minimal pipeline (phase only, no travel/flicker).
     */
    public static FlowPipeline minimal() {
        return new FlowPipeline(List.of(
            FlowPhaseStage.INSTANCE
        ));
    }
    
    /**
     * Creates a custom pipeline with specific stages.
     */
    public static FlowPipeline custom(FlowStage... stages) {
        return new FlowPipeline(List.of(stages));
    }
    
    /**
     * Compute the animation state by running all pipeline stages.
     */
    public AnimationState compute(FlowContext ctx) {
        AnimationState state = AnimationState.ZERO;
        
        for (FlowStage stage : stages) {
            if (stage.shouldRun(ctx)) {
                state = stage.process(state, ctx);
            }
        }
        
        return state;
    }
    
    /**
     * Convenience method to compute animation for a single ray.
     */
    public static AnimationState computeForRay(
            RayFlowConfig config,
            int rayIndex, int rayCount,
            float time,
            float innerRadius, float outerRadius) {
        
        FlowContext ctx = new FlowContext(
            config, rayIndex, rayCount, time, innerRadius, outerRadius
        );
        
        return standard().compute(ctx);
    }
    
    /**
     * Get the number of stages in this pipeline.
     */
    public int stageCount() {
        return stages.size();
    }
    
    /**
     * Get stage names for debugging.
     */
    public List<String> stageNames() {
        List<String> names = new ArrayList<>(stages.size());
        for (FlowStage stage : stages) {
            names.add(stage.name());
        }
        return names;
    }
}
