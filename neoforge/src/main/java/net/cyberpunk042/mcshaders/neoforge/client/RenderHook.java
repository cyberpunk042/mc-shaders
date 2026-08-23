package net.cyberpunk042.mcshaders.neoforge.client;

import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.vanilla.render.WorldSampler;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge's per-frame call, the twin of the Fabric hook.
 *
 * <p>Both subscribe to their loader's extraction event and hand the same four values
 * to {@link WorldSampler}. {@code ExtractLevelRenderStateEvent} is not merely the
 * nearest NeoForge equivalent — its javadoc says it fires "after all vanilla states
 * have been extracted", which is what Fabric's {@code END_EXTRACTION} means, and it
 * exposes {@code getLevel} / {@code getCamera} / {@code getDeltaTracker} /
 * {@code getRenderState} with the same types Fabric's context does.
 *
 * <h2>Once per frame, and only here</h2>
 *
 * <p>{@code frame} advances the transition, so calling it from more than one place
 * would ease at a multiple of the intended speed. That is also why the fog handler
 * reads a published value rather than asking the pipeline itself.
 *
 * <p>Fired on {@link NeoForge#EVENT_BUS} — the game bus, per the event's own javadoc,
 * and client-only. Subscribing on the mod bus would produce no error and never run.
 */
public final class RenderHook {

    private RenderHook() {
    }

    /** Subscribes to the extraction event. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(ExtractLevelRenderStateEvent.class, RenderHook::onExtraction);
    }

    private static void onExtraction(ExtractLevelRenderStateEvent event) {
        WorldState state = WorldSampler.from(
                event.getLevel(), event.getCamera(), event.getDeltaTracker(), event.getRenderState());
        if (state == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.getWindow().getWidth();
        int height = minecraft.getWindow().getHeight();
        if (width <= 0 || height <= 0) {
            // A minimised window reports zero, and FrameContext refuses those rather
            // than letting a divide-by-zero aspect ratio reach a backend.
            return;
        }

        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        // Two different clocks: deltaTicks advances the transition this frame,
        // elapsedTicks is monotonic time for effects that animate. Passing the delta
        // as both would leave anything animated stuttering in place.
        double deltaTicks = event.getDeltaTracker().getGameTimeDeltaTicks();
        double elapsedTicks = WorldSampler.elapsedTicks(event.getLevel(), event.getDeltaTracker());

        McShaders.pipeline().frame(
                state,
                deltaTicks,
                new EffectBackend.FrameContext(width, height, partialTick, elapsedTicks));
    }
}
