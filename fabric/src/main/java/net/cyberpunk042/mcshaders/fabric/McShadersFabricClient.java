package net.cyberpunk042.mcshaders.fabric;

import net.cyberpunk042.mcshaders.McShaders;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entrypoint.
 *
 * <p>Backend selection and the per-frame render hook belong here. Both are
 * deliberately absent from this first commit: wiring them means calling into
 * Minecraft's 26.2 render internals, and the exact API surface has not been
 * verified against the real sources yet. See docs/ROADMAP.md, milestone M2.
 */
public final class McShadersFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    @Override
    public void onInitializeClient() {
        LOGGER.info("{} client init — backend: {}", McShaders.MOD_NAME, McShaders.backend().id());
    }
}
