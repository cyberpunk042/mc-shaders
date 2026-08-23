package net.cyberpunk042.mcshaders.fabric.render;

import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.vanilla.render.WorldSampler;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.minecraft.client.Minecraft;

/**
 * The per-frame call that was missing.
 *
 * <p>{@code ShaderPipeline#frame} existed and was called by nothing, so every binding
 * resolved to a look that was compiled and then dropped. This is the one caller.
 *
 * <h2>Once per frame, and only here</h2>
 *
 * <p>{@code frame} advances the transition, so calling it from more than one place
 * would ease at a multiple of the intended speed — visible as a tuning problem rather
 * than as the structural mistake it is. That is also why the fog mixin reads a
 * published value instead of asking the pipeline itself.
 */
public final class RenderHook {

    private RenderHook() {
    }

    /** Subscribes to the extraction event. Idempotent registration is the caller's job. */
    public static void register() {
        LevelExtractionEvents.END_EXTRACTION.register(RenderHook::onExtraction);
    }

    private static void onExtraction(LevelExtractionContext context) {
        WorldState state = WorldSampler.from(
                context.level(), context.camera(), context.deltaTracker(), context.levelState());
        if (state == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            // A minimised window reports zero, and FrameContext refuses those rather
            // than letting a divide-by-zero aspect ratio reach a backend. Nothing is
            // being drawn either way, so there is nothing to skip.
            return;
        }

        float partialTick = context.deltaTracker().getGameTimeDeltaPartialTick(false);
        // Two different clocks, and mixing them up is the easy mistake here.
        // deltaTicks is how far to advance the transition this frame; elapsedTicks is
        // monotonic time for effects that animate. Passing the delta as both would
        // leave anything animated stuttering in place.
        double deltaTicks = context.deltaTracker().getGameTimeDeltaTicks();
        double elapsedTicks = WorldSampler.elapsedTicks(context.level(), context.deltaTracker());

        McShaders.pipeline().frame(
                state,
                deltaTicks,
                new EffectBackend.FrameContext(width, height, partialTick, elapsedTicks));
    }
}
