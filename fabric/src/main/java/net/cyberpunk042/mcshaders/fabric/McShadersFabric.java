package net.cyberpunk042.mcshaders.fabric;

import net.cyberpunk042.mcshaders.McShaders;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Fabric main entrypoint. A thin adapter over the shared lifecycle. */
public final class McShadersFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    @Override
    public void onInitialize() {
        McShaders.init(LOGGER::info);
    }
}
