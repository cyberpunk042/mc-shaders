package net.cyberpunk042.mcshaders.neoforge;

import net.cyberpunk042.mcshaders.McShaders;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** NeoForge entrypoint. A thin adapter over the shared lifecycle. */
@Mod(McShaders.MOD_ID)
public final class McShadersNeoForge {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    public McShadersNeoForge() {
        McShaders.init(LOGGER::info);
    }
}
