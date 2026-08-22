package net.cyberpunk042.mcshaders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.codec.BindingLoader;
import net.cyberpunk042.mcshaders.core.api.Experimental;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.backend.BackendFactory;
import net.cyberpunk042.mcshaders.core.backend.BackendRegistry;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.edit.TuningStore;
import net.cyberpunk042.mcshaders.core.effect.EffectDefinition;
import net.cyberpunk042.mcshaders.core.effect.EffectRegistry;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.SchemaRegistry;

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
    private static final SchemaRegistry SCHEMAS = new SchemaRegistry();
    private static final TuningStore TUNING = new TuningStore();
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
    /**
     * Declares that an effect type is editable, and what its controls are.
     *
     * <p>Optional, and independent of {@link #registerEffect}: an effect may be
     * registered without a schema and simply not appear in an editor. Registering a
     * schema for a type nothing ever registers is allowed too — a schema is a
     * description, and nothing here requires the thing it describes to exist yet.
     *
     * @param effectType the type this describes, matching an
     *                   {@link EffectDefinition#type()}
     * @param schema     what is tunable on it
     * @throws IllegalStateException if registration has closed, or the type already
     *                               has a schema
     */
    public static void registerSchema(String effectType, EffectSchema schema) {
        SCHEMAS.register(effectType, schema);
    }

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
     * The schemas that say which effects can be edited, and how.
     *
     * <p>Separate from {@link #effects()} because being editable is optional: an
     * effect with nothing worth tuning declares no schema and simply does not appear
     * in an editor.
     */
    public static SchemaRegistry schemas() {
        return SCHEMAS;
    }

    /**
     * The values effects have actually been tuned to.
     *
     * <p>Unlike every registry beside it this is not lifecycled, and deliberately so:
     * a registry accumulates during initialisation and freezes, whereas tuning is what
     * changes while the game runs. It is safe to read from the render path at any time
     * because it is concurrent and the values in it are immutable.
     *
     * <p>A renderer wants {@link TuningStore#effective}, which falls back to the
     * schema's defaults for effects nobody has touched, rather than {@code get}, which
     * distinguishes untouched from tuned-back-to-default.
     */
    public static TuningStore tuning() {
        return TUNING;
    }

    /**
     * The dimension bindings currently in force.
     *
     * <p>This was missing, and its absence was asymmetric: every other registry here
     * had a reader, so a mod could contribute a dimension's look and then have no
     * supported way to ask what the look actually became — whether its binding won,
     * what another mod had already said about that dimension, or whether a datapack
     * had replaced the lot.
     *
     * <p>Reading closes registration, because the answer is not meaningful until
     * every mod has had its turn.
     */
    public static BindingRegistry bindings() {
        McShaders.completeRegistration();
        return McShaders.registry();
    }

    /**
     * What a dimension looks like in a given world state.
     *
     * <p>The question a consumer actually has — "what is in force here?" — answered
     * without handing out the per-frame driver. Merging is by layer id and ascending
     * priority, so this is the same stack the renderer would draw.
     *
     * <p>A read. It advances no transition and disturbs no frame, so calling it from
     * a command, a HUD or another mod's logic is safe.
     */
    public static EffectStack look(WorldState state) {
        if (state == null) {
            throw new IllegalArgumentException("Cannot resolve a look for a null world state");
        }
        return bindings().resolve(state);
    }

    /**
     * Replaces every binding, as a datapack reload does.
     *
     * <p>Deliberately allowed <em>after</em> registration closes, which is what
     * separates it from {@link #registerBinding}. Registration is a startup
     * accumulation and is closed once so that mods cannot race; a reload is a
     * runtime replacement and is the whole point of datapack-driven looks. Refusing
     * it after close would mean {@code /reload} could never change anything.
     *
     * <p>It reaches the running pipeline, so the next frame resolves against the new
     * bindings and eases toward them rather than snapping.
     *
     * <p>Wholesale, not a merge: a reload's result is the complete new set, and
     * merging would leave bindings from a pack the player has just removed.
     *
     * @param replacement the new set; null is read as empty, which is what a reload
     *                    that found no binding files legitimately produces
     */
    @Experimental
    public static void reloadBindings(BindingRegistry replacement) {
        McShaders.completeRegistration();
        McShaders.setRegistry(replacement);
    }

    /**
     * Reads pack files into bindings and puts them in force.
     *
     * <p>{@link #reloadBindings} takes a {@link BindingRegistry}, and until now
     * nothing on this class said where a consumer holding a stack of pack files was
     * supposed to get one. {@code BindingLoader} did the work and was reachable, but
     * a capability that has to be found by reading the source is not part of an API.
     *
     * <p>The returned {@link BindingLoader.Result} is the reason this is not
     * {@code void}. One malformed file is skipped rather than blanking every
     * dimension's look, and a binding overridden by a later pack is recorded too — a
     * pack author whose work silently stopped applying deserves to know why.
     * <strong>A caller that discards this has turned a loud failure into a silent
     * one.</strong> Log {@link BindingLoader.Result#problems()}.
     *
     * <p>Applying is wholesale, exactly as {@link #reloadBindings} is, and for the
     * same reason: a reload's files are the complete new set. Passing no files
     * empties the registry, which is what removing the last pack should do.
     *
     * @param files pack file contents, keyed by whatever name should appear in an
     *              error message — a path a pack author would recognise; null is
     *              read as empty
     * @return what loaded, and everything that went wrong loading it
     */
    @Experimental
    public static BindingLoader.Result loadBindings(Map<String, String> files) {
        BindingLoader.Result result = BindingLoader.load(files == null ? Map.of() : files);
        reloadBindings(result.registry());
        return result;
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
            SCHEMAS.freeze();
            McShaders.setRegistry(BindingRegistry.of(List.copyOf(PENDING_BINDINGS)));
        }
    }
}
