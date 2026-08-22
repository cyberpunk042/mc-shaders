package net.cyberpunk042.mcshaders.fabric;

import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.fabric.gui.EditorKey;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric client entrypoint.
 *
 * <p>Backend selection and the per-frame render hook still belong here and are still
 * absent: wiring them means calling into Minecraft's 26.2 post-processing internals,
 * and unlike the GUI surface those have not been established against a mod that
 * compiles on this version. See docs/RENDERING-26.2.md and ROADMAP.md milestone M2.
 *
 * <p>What is wired is the editing screen's key. The GUI API it needs <em>was</em>
 * established from source, which is why that half could go in.
 */
public final class McShadersFabricClient implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(McShaders.MOD_NAME);

    @Override
    public void onInitializeClient() {
        LOGGER.info("{} client init — backend: {}", McShaders.MOD_NAME, McShaders.backend().id());
        EditorKey.register();
    }
}
