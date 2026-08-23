package net.cyberpunk042.mcshaders;

import java.util.function.Consumer;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.backend.NoOpBackend;
import net.cyberpunk042.mcshaders.core.ShaderPipeline;
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
    private static ShaderPipeline pipeline;
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
     * @param logger where to send lifecycle messages, supplied by the loader adapter
     */
    public static synchronized void init(Consumer<String> logger) {
        if (initialised) {
            return;
        }
        initialised = true;
        log = logger == null ? message -> { } : logger;
        // Before any third party registers, so a mod that wants to see what is
        // already present can, and so the built-ins cannot lose a name race.
        BuiltinEffects.register();
        // After the effects, because a binding names an effect type and a mod
        // reading the registries mid-init should not see a look referring to
        // something that is not there yet.
        BuiltinBindings.register();
        // Last, because a backend names the effect types it supports and those have
        // to exist by then. Without this, selection falls through to NoOpBackend and
        // every resolved effect is compiled and then discarded.
        BuiltinBackends.register();
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

        // Build the thing that actually uses all of the above. Until this existed,
        // registration filled a registry nothing read: bindings accumulated, the
        // registry was set, and no ShaderPipeline was ever constructed anywhere in
        // the mod, so the per-frame entry point could not be called even in
        // principle. Three working pieces with nothing joining them.
        pipeline = new ShaderPipeline(selected, registry, McShadersAPI.effects());

        // Not "third-party": the built-ins live in the same registry, so that
        // wording became wrong the moment BuiltinEffects.register() was added.
        log.accept(MOD_NAME + " ready — backend '" + selected.id()
                + "', " + McShadersAPI.effects().size() + " effect type(s), "
                + McShadersAPI.schemas().all().size() + " editable, "
                + registry.size() + " binding(s)");
    }

    /** The active binding set. Replaced wholesale on datapack reload. */
    public static synchronized BindingRegistry registry() {
        return registry;
    }

    /**
     * Swaps the binding set, as a datapack reload does.
     *
     * <p>Reaches the live pipeline as well as the stored field. Updating only the
     * field would leave a running pipeline resolving against the bindings it was
     * constructed with — a reload that appears to work and changes nothing, which
     * is worse than one that fails.
     *
     * <p>Before the pipeline exists there is nothing to update; the registry stored
     * here is what it will be built with.
     */
    public static synchronized void setRegistry(BindingRegistry newRegistry) {
        registry = newRegistry == null ? BindingRegistry.empty() : newRegistry;
        if (pipeline != null) {
            pipeline.setRegistry(registry);
        }
    }

    /**
     * The pipeline that drives a frame, closing registration on first call.
     *
     * <p>Internal, like the rest of this class: the render hook owns calling
     * {@code frame}, and a second caller driving it would advance transitions twice
     * per tick. What a third party wants is {@link McShadersAPI#bindings()} to read
     * the bindings, or {@link McShadersAPI#look} to ask what a world state resolves
     * to — neither of which can disturb the frame.
     */
    public static synchronized ShaderPipeline pipeline() {
        completeRegistration();
        return pipeline;
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
