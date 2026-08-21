package net.cyberpunk042.mcshaders;

import java.util.function.Consumer;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.backend.NoOpBackend;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;

/**
 * Loader-independent lifecycle.
 *
 * <p>Every loader's initialiser funnels through {@link #init} and then
 * {@link #completeRegistration}. Keeping the shared lifecycle here means the Fabric
 * and NeoForge modules stay thin adapters, and the behaviour they adapt cannot
 * drift apart between loaders.
 *
 * <p>This is internal. The surface other mods build against is
 * {@link McShadersAPI}.
 *
 * <p>Note this class imports no Minecraft types. That is deliberate and worth
 * preserving: it is what allows the mod's logic to be exercised without a game.
 */
public final class McShaders {

    public static final String MOD_ID = "mcshaders";
    public static final String MOD_NAME = "MC Shaders";

    private static BindingRegistry registry = BindingRegistry.empty();
    private static EffectBackend backend = new NoOpBackend();
    private static Consumer<String> log = message -> { };
    private static boolean initialised;
    private static boolean registrationComplete;

    private McShaders() {
    }

    /**
     * Runs shared startup, before third-party registration.
     *
     * <p>Idempotent: NeoForge and Fabric differ in how many times a mod's
     * construction may be observed, so this guards rather than assuming.
     *
     * @param log where to send lifecycle messages, supplied by the loader adapter
     */
    public static synchronized void init(Consumer<String> logger) {
        if (initialised) {
            return;
        }
        initialised = true;
        log = logger == null ? message -> { } : logger;
        log.accept(MOD_NAME + " common init — API " + McShadersAPI.API_VERSION);
    }

    /**
     * Closes registration and selects a backend.
     *
     * <p>Deliberately <em>lazy</em>: it runs the first time something actually needs
     * the backend, not at a fixed point in the loader's startup. Closing at a fixed
     * point would mean picking a loader lifecycle event that runs after every mod
     * has initialised — and Fabric and NeoForge disagree about which event that is.
     * First use is a point both loaders reach only once every mod has had its turn,
     * so the same code is correct on both.
     *
     * <p>Idempotent. A loader may call it explicitly to make the timing deterministic
     * and get the summary logged at a predictable moment.
     */
    public static synchronized void completeRegistration() {
        if (registrationComplete) {
            return;
        }
        registrationComplete = true;

        McShadersAPI.closeRegistration();

        EffectBackend selected = McShadersAPI.backends().select(reason -> log.accept("  " + reason));
        backend = selected;

        log.accept(MOD_NAME + " ready — backend '" + selected.id()
                + "', " + McShadersAPI.effects().size() + " third-party effect type(s), "
                + registry.size() + " binding(s)");
    }

    /** The active binding set. Replaced wholesale on datapack reload. */
    public static synchronized BindingRegistry registry() {
        return registry;
    }

    public static synchronized void setRegistry(BindingRegistry newRegistry) {
        registry = newRegistry == null ? BindingRegistry.empty() : newRegistry;
    }

    /**
     * The active rendering backend, closing registration on first call.
     *
     * <p>Never null: a {@link NoOpBackend} is the correct state on a dedicated
     * server and the safe fallback when no graphics backend initialises.
     */
    public static synchronized EffectBackend backend() {
        completeRegistration();
        return backend;
    }

    /** Whether registration has closed and a backend has been selected. */
    public static synchronized boolean isRegistrationComplete() {
        return registrationComplete;
    }

    public static synchronized boolean isInitialised() {
        return initialised;
    }
}
