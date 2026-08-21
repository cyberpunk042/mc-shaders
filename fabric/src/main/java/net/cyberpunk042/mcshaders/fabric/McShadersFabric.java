package net.cyberpunk042.mcshaders.fabric;

import net.cyberpunk042.mcshaders.McShaders;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric main entrypoint. A thin adapter over the shared lifecycle.
 *
 * <p>Registration is not closed here. Other mods register from their own
 * initialisers, and Fabric gives no ordering guarantee between them, so closing at
 * this point could lock out a mod that has not run yet. It closes on first use
 * instead — see {@link McShaders#completeRegistration()}.
 */
public final class McShadersFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    @Override
    public void onInitialize() {
        McShaders.init(LOGGER::info);
    }
}
