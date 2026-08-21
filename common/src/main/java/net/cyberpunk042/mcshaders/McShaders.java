package net.cyberpunk042.mcshaders;

import java.util.function.Consumer;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.backend.NoOpBackend;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;

/**
 * Loader-independent entry point.
 *
 * <p>Every loader's initialiser funnels through {@link #init}. Keeping the shared
 * lifecycle here means the Fabric and NeoForge modules stay thin adapters, and the
 * behaviour they adapt cannot drift apart between loaders.
 *
 * <p>Note this class imports no Minecraft types. That is deliberate and worth
 * preserving: it is what allows the mod's logic to be exercised without a game.
 */
public final class McShaders {

    public static final String MOD_ID = "mcshaders";
    public static final String MOD_NAME = "MC Shaders";

    private static BindingRegistry registry = BindingRegistry.empty();
    private static EffectBackend backend = new NoOpBackend();
    private static boolean initialised;

    private McShaders() {
    }

    /**
     * Runs shared startup.
     *
     * <p>Idempotent: NeoForge and Fabric differ in how many times a mod's
     * construction may be observed, so this guards rather than assuming.
     *
     * @param log where to send lifecycle messages, supplied by the loader adapter
     */
    public static synchronized void init(Consumer<String> log) {
        if (initialised) {
            return;
        }
        initialised = true;
        log.accept(MOD_NAME + " common init");
    }

    /** The active binding set. Replaced wholesale on datapack reload. */
    public static synchronized BindingRegistry registry() {
        return registry;
    }

    public static synchronized void setRegistry(BindingRegistry newRegistry) {
        registry = newRegistry == null ? BindingRegistry.empty() : newRegistry;
    }

    /**
     * The active rendering backend.
     *
     * <p>Defaults to {@link NoOpBackend}, which is the correct state on a dedicated
     * server and the safe fallback when no graphics backend initialises.
     */
    public static synchronized EffectBackend backend() {
        return backend;
    }

    public static synchronized void setBackend(EffectBackend newBackend) {
        backend = newBackend == null ? new NoOpBackend() : newBackend;
    }

    public static synchronized boolean isInitialised() {
        return initialised;
    }
}
