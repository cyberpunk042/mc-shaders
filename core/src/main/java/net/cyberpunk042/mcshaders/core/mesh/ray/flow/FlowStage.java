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

/**
 * Interface for flow animation pipeline stages.
 * 
 * Each stage processes the animation state and returns a new state.
 * Stages are executed in order by FlowPipeline.
 */
public interface FlowStage {
    
    /**
     * Process the animation state.
     * 
     * @param state Current animation state
     * @param ctx Context with config and ray info
     * @return New animation state (may be same if no changes)
     */
    AnimationState process(AnimationState state, FlowContext ctx);
    
    /**
     * Whether this stage should run for the given context.
     * Override to skip stages based on config.
     */
    default boolean shouldRun(FlowContext ctx) {
        return true;
    }
    
    /**
     * Name for debugging/logging.
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
