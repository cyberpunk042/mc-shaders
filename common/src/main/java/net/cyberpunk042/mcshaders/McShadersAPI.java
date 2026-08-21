package net.cyberpunk042.mcshaders;

import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.backend.BackendFactory;
import net.cyberpunk042.mcshaders.core.backend.BackendRegistry;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.effect.EffectDefinition;
import net.cyberpunk042.mcshaders.core.effect.EffectRegistry;

/**
 * The entry point for other mods.
 *
 * <p>This is the supported surface. Everything reachable from here is
 * {@link Stable} or explicitly experimental; anything else in the mod is internal
 * and may change without notice.
 *
 * <h2>Lifecycle</h2>
 *
 * Registration is open during mod initialisation and closes before the first frame.
 * That boundary is deliberate: once closed, the render path reads a set that cannot
 * change, so no mod can alter rendering mid-frame and no reader needs locking.
 * Registering after the close throws rather than being quietly dropped.
 *
 * <p>Order between mods is not guaranteed, so nothing here depends on it — backend
 * selection is by declared priority, and effect types are refused on collision
 * rather than last-write-wins.
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * // In your mod's initialiser:
 * EffectDefinition kaleidoscope = EffectDefinition
 *         .of("mymod:kaleidoscope", "mymod")
 *         .withDefaults(EffectParams.builder().scalar("segments", 6.0).build());
 *
 * McShadersAPI.registerEffect(kaleidoscope);
 *
 * McShadersAPI.registerBinding(DimensionBinding.of(
 *         "mymod:dreamscape",
 *         DimensionId.parse("mymod:dreamscape"),
 *         EffectStack.of(EffectLayer.builder("swirl").definition(kaleidoscope).build())));
 * }</pre>
 *
 * <p>A mod that only <em>optionally</em> integrates should guard its calls with a
 * loader-side check that this mod is present, rather than catching
 * {@link NoClassDefFoundError}.
 */
@Stable(since = "0.2.0")
public final class McShadersAPI {

    /**
     * The API contract version, independent of the mod version.
     *
     * <p>Compare against this when behaviour differs across releases. It changes
     * only when the supported surface changes, not on every mod release.
     */
    public static final String API_VERSION = "0.2.0";

    private static final EffectRegistry EFFECTS = new EffectRegistry();
    private static final BackendRegistry BACKENDS = new BackendRegistry();
    private static final List<DimensionBinding> PENDING_BINDINGS = new ArrayList<>();

    private static volatile boolean registrationClosed;

    private McShadersAPI() {
    }

    /**
     * Registers a new effect type.
     *
     * @throws IllegalStateException if registration has closed, or the type is taken
     *                               by another mod
     */
    public static void registerEffect(EffectDefinition definition) {
        EFFECTS.register(definition);
    }

    /**
     * Registers a rendering backend.
     *
     * <p>The highest-priority available backend is selected. To take over from the
     * built-in renderer, declare a priority above
     * {@link BackendFactory#DEFAULT_PRIORITY}; to act as a fallback, declare one
     * below it.
     *
     * @throws IllegalStateException if registration has closed, or the id is taken
     */
    public static void registerBackend(BackendFactory factory) {
        BACKENDS.register(factory);
    }

    /**
     * Contributes a dimension binding.
     *
     * <p>Bindings from every mod are merged by priority, and within a binding by
     * layer id — so contributing a binding that redefines one layer of a dimension's
     * look leaves the rest intact.
     *
     * @throws IllegalStateException if registration has closed
     */
    public static void registerBinding(DimensionBinding binding) {
        if (binding == null) {
            throw new IllegalArgumentException("Cannot register a null binding");
        }
        synchronized (PENDING_BINDINGS) {
            if (registrationClosed) {
                throw new IllegalStateException(
                        "Binding registration is closed; '" + binding.id() + "' arrived too late. "
                                + "Register during mod initialisation.");
            }
            PENDING_BINDINGS.add(binding);
        }
    }

    /** Whether registration is still accepting contributions. */
    public static boolean isRegistrationOpen() {
        return !registrationClosed;
    }

    /** The registered effect types. Read-only in practice once registration closes. */
    public static EffectRegistry effects() {
        return EFFECTS;
    }

    /** The registered backends. */
    public static BackendRegistry backends() {
        return BACKENDS;
    }

    /**
     * Closes registration and publishes the accumulated bindings.
     *
     * <p>Called by this mod at the end of the loader's initialisation phase.
     * Third-party mods should not call it — doing so early would lock out mods that
     * had not initialised yet. Idempotent, so a double call is harmless.
     */
    static void closeRegistration() {
        synchronized (PENDING_BINDINGS) {
            if (registrationClosed) {
                return;
            }
            registrationClosed = true;
            EFFECTS.freeze();
            BACKENDS.freeze();
            McShaders.setRegistry(BindingRegistry.of(List.copyOf(PENDING_BINDINGS)));
        }
    }
}
