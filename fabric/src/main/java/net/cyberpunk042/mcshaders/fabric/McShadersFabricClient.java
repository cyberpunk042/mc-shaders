package net.cyberpunk042.mcshaders.fabric;

import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.McShadersAPI;
import net.cyberpunk042.mcshaders.fabric.gui.EditorKey;
import net.cyberpunk042.mcshaders.fabric.render.RenderHook;
import net.cyberpunk042.mcshaders.sample.SamplingGaps;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entrypoint.
 *
 * <p>The per-frame hook is wired here, into
 * {@code LevelExtractionEvents.END_EXTRACTION}. Before this, {@code ShaderPipeline}
 * existed and nothing called it, so every frame resolved a look and dropped it.
 *
 * <p>The mixin that applies those values now exists — {@code FogRendererMixin}, at
 * the tail of {@code FogRenderer#setupFog}. Which of {@code FogData}'s two distance
 * pairs a dimension look should write is still a judgement rather than a finding; the
 * choice and its counter-argument are recorded in that file, and settling it needs a
 * running game.
 */
public final class McShadersFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    @Override
    public void onInitializeClient() {
        LOGGER.info("{} client init — backend: {}", McShaders.MOD_NAME, McShaders.backend().id());
        EditorKey.register();
        RenderHook.register();
        warnAboutUnevaluableBindings();
    }

    /**
     * Says which bindings cannot work on this version, if any.
     *
     * <p>Time of day cannot be read on 26.2, so a binding gated on it never activates.
     * That failure is silent by nature — it looks exactly like a typo in the condition
     * or a pack that did not load — and this is the only thing that turns it into
     * something an author can act on.
     *
     * <p>Logged once, and only when there is something to say. A warning that appears
     * on every start regardless teaches people to stop reading warnings.
     */
    private void warnAboutUnevaluableBindings() {
        String problem = SamplingGaps.describe(McShadersAPI.bindings(), SamplingGaps.UNAVAILABLE);
        if (!problem.isEmpty()) {
            LOGGER.warn("{}", problem);
        }
    }
}
