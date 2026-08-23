package net.cyberpunk042.mcshaders.neoforge.data;

import net.cyberpunk042.mcshaders.McShaders;
import net.cyberpunk042.mcshaders.vanilla.BindingScan;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;

/**
 * NeoForge's half of the datapack reload: registration, and nothing else.
 *
 * <p>The twin of the Fabric listener. Both delegate to {@link BindingScan}, which is
 * where the vanilla work lives; what differs is only how a listener is registered.
 *
 * <p>NeoForge has no {@code ResourceLoader}: listeners arrive by responding to
 * {@code AddServerReloadListenersEvent}, which its own javadoc says is fired on
 * {@link NeoForge#EVENT_BUS} — the game bus, not the mod bus. That distinction is
 * the one thing here worth getting wrong: subscribing on the wrong bus produces no
 * error, just a listener that never runs.
 *
 * <p>Server-side, matching Fabric's {@code SERVER_DATA}. The event's name says so,
 * and bindings are {@code data/} content.
 */
public final class BindingReloadListener implements ResourceManagerReloadListener {

    private static final Identifier ID =
            Identifier.fromNamespaceAndPath(McShaders.MOD_ID, "bindings");

    private BindingReloadListener() {
    }

    /** Wires this into the datapack reload. Call once, from the mod constructor. */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(AddServerReloadListenersEvent.class,
                event -> event.addListener(ID, new BindingReloadListener()));
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        BindingScan.reload(manager);
    }
}
