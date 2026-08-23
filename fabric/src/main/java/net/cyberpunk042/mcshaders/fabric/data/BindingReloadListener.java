package net.cyberpunk042.mcshaders.fabric.data;

import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.vanilla.BindingScan;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/**
 * Fabric's half of the datapack reload: registration, and nothing else.
 *
 * <p>What happens once the game hands over a {@link ResourceManager} is identical on
 * both loaders and lives in {@link BindingScan}. Only the way a listener gets
 * registered differs — here {@code ResourceLoader}, on NeoForge an event — so only
 * that is here.
 *
 * <p>Registered on the main entrypoint rather than the client one: bindings are
 * {@code data/} content, so {@link PackType#SERVER_DATA} is the reload that carries
 * them, and in singleplayer it is the integrated server that runs it.
 */
public final class BindingReloadListener implements ResourceManagerReloadListener {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(McShaders.MOD_ID, "bindings");

    private BindingReloadListener() {
    }

    /** Wires this into the datapack reload. Call once, from the main entrypoint. */
    public static void register() {
        ResourceLoader.get(PackType.SERVER_DATA)
                .registerReloadListener(ID, new BindingReloadListener());
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        BindingScan.reload(manager);
    }
}
