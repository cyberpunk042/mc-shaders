package net.cyberpunk042.mcshaders.fabric;

import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.fabric.data.BindingReloadListener;
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
 *
 * <p>The datapack reload listener is registered here rather than on the client
 * entrypoint: bindings are {@code data/} content, so the reload that carries them is
 * a server-side one, run by the integrated server in singleplayer. Registering a
 * listener is not the same as closing registration — it fires at world load, well
 * after every mod initialiser has run.
 */
public final class McShadersFabric implements ModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    @Override
    public void onInitialize() {
        McShaders.init(LOGGER::info);

        // Before this, the datapack format was one nothing read: the loader existed,
        // the API entry point existed, and no code ever handed them a file.
        BindingReloadListener.register();
    }
}
