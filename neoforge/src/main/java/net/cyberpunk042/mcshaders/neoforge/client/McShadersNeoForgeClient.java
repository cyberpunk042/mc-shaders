package net.cyberpunk042.mcshaders.neoforge.client;

import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.McShadersAPI;
import net.cyberpunk042.mcshaders.sample.SamplingGaps;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge client entrypoint — the twin of {@code McShadersFabricClient}.
 *
 * <p>A separate {@code @Mod} class marked {@code dist = Dist.CLIENT}, which is
 * NeoForge's own pattern for client-only code (see its {@code ClientNeoForgeMod}).
 * The render and fog events this registers are client-only classes; naming them from
 * the common mod class would load them on a dedicated server.
 *
 * <p>Not registered here: the editor. {@code SchemaScreen} lives in the Fabric module
 * and would need moving before NeoForge could open it, which is a larger change than
 * this one and is not pretended to be done.
 */
@Mod(value = McShaders.MOD_ID, dist = Dist.CLIENT)
public final class McShadersNeoForgeClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    public McShadersNeoForgeClient() {
        LOGGER.info("{} client init — backend: {}", McShaders.MOD_NAME, McShaders.backend().id());
        RenderHook.register();
        FogHook.register();
        warnAboutUnevaluableBindings();
    }

    /**
     * Says which bindings cannot work on this version, if any.
     *
     * <p>Time of day cannot be read on 26.2, so a binding gated on it never activates —
     * a failure that looks exactly like a typo in the condition or a pack that did not
     * load. This is the only thing that turns it into something an author can act on.
     */
    private void warnAboutUnevaluableBindings() {
        String problem = SamplingGaps.describe(McShadersAPI.bindings(), SamplingGaps.UNAVAILABLE);
        if (!problem.isEmpty()) {
            LOGGER.warn("{}", problem);
        }
    }
}
