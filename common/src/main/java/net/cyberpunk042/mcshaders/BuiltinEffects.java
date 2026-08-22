package net.cyberpunk042.mcshaders;

import net.cyberpunk042.mcshaders.core.effect.EffectDefinition;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;

/**
 * The effects the mod ships itself.
 *
 * <p>Until this existed the mod registered none, so a fresh install opened an editor
 * with nothing in it. That was the correct behaviour for an empty registry and a
 * useless thing to hand someone: an editor with no contents cannot be judged, so
 * nobody could say whether the screens were right.
 *
 * <h2>Why fog, and only fog</h2>
 *
 * <p>Because it is the one effect whose entry point has been read out of a mod that
 * compiles on 26.2, rather than remembered. {@code FogRenderer#setupFog} and the
 * {@code FogData} it works through are recorded in
 * <a href="../../../../../docs/RENDERING-26.2.md">RENDERING-26.2.md</a>, and M2's
 * acceptance criterion is a fog binding changing the frame. Shipping the effect that
 * matches the hook we actually have is a different thing from shipping a catalogue
 * of effects nothing can render.
 *
 * <p>The obvious alternative was to expose {@code CoronaEffect} and
 * {@code HorizonEffect}, which are already in core and already carry documented
 * ranges. They were left out on purpose: both are geometry effects that change how a
 * surface shades, neither maps to an {@link EffectKind} the compiler understands as
 * anything but {@code CUSTOM}, and no hook for them has been established. They would
 * have filled the editor without connecting it to anything.
 *
 * <h2>What the parameters are, and how sure we are</h2>
 *
 * <table border="1">
 *   <caption>Provenance of each parameter</caption>
 *   <tr><th>Parameter</th><th>Maps to</th><th>Confidence</th></tr>
 *   <tr><td>{@code start}</td><td>{@code FogData.renderDistanceStart}</td>
 *       <td>Read from Jade's mixin — the field exists and is public</td></tr>
 *   <tr><td>{@code end}</td><td>{@code FogData.renderDistanceEnd}</td>
 *       <td>Same</td></tr>
 *   <tr><td>{@code color}</td><td>the {@code Vector4f} {@code setupFog} returns</td>
 *       <td><b>Inferred.</b> The return type is established; that it is the fog
 *       colour is not, because Jade ignores the return value</td></tr>
 * </table>
 *
 * <p>The defaults are a plausible starting point, not a claim about what vanilla
 * uses. Nothing here has been compared against a running game.
 *
 * <h2>What this does not do</h2>
 *
 * <p>Registering an effect and describing it does not render it. Nothing reads these
 * values yet — the mixin that would is M2. What it does buy is an editor with real
 * contents, so its screens can be looked at and its tuning exercised, and a worked
 * example of the binding format for a pack author to copy.
 */
public final class BuiltinEffects {

    /** The namespaced type of the built-in fog effect. */
    public static final String FOG = McShaders.MOD_ID + ":fog";

    /** Blocks from the camera at which fog starts. */
    public static final String START = "start";

    /** Blocks from the camera at which fog reaches full density. */
    public static final String END = "end";

    /** Fog colour. */
    public static final String COLOR = "color";

    /**
     * The furthest either distance may be set.
     *
     * <p>512 blocks is 32 chunks, the largest render distance the game offers, so a
     * fog distance beyond it is describing something nobody can see.
     */
    public static final double MAX_DISTANCE = 512;

    private BuiltinEffects() {
    }

    /** The definition, with the defaults an unconfigured layer starts from. */
    public static EffectDefinition fogDefinition() {
        return new EffectDefinition(FOG, EffectKind.FOG, fogDefaults(), false, McShaders.MOD_ID);
    }

    /** What a fog layer holds before anybody tunes it. */
    public static EffectParams fogDefaults() {
        return EffectParams.builder()
                .scalar(START, 0)
                .scalar(END, 192)
                .color(COLOR, 0.6f, 0.7f, 0.9f, 1.0f)
                .build();
    }

    /**
     * What is tunable on it.
     *
     * <p>Every key here appears in {@link #fogDefaults()} and vice versa. That is not
     * a coincidence to be maintained by hand — a test audits it, because a schema
     * describing a parameter the effect does not have is a control that edits nothing,
     * and an effect with a parameter no schema mentions is a value nobody can reach.
     */
    public static EffectSchema fogSchema() {
        return EffectSchema.builder("Fog", FOG, 1)
                .group("Distance",
                        ParamSpec.slider(START, "Start", 0, MAX_DISTANCE, 0, "Distance"),
                        ParamSpec.slider(END, "End", 0, MAX_DISTANCE, 192, "Distance"))
                .group("Colour",
                        ParamSpec.color(COLOR, "Colour",
                                new ParamValue.Rgba(0.6f, 0.7f, 0.9f, 1.0f), "Colour"))
                .build();
    }

    /**
     * Registers everything the mod ships.
     *
     * <p>Called from {@link McShaders#init} — before third-party registration, so a
     * mod that wants to know what is already present can look. The type is namespaced,
     * so this claims {@code mcshaders:fog} and nothing else; another mod's fog effect
     * has its own namespace and does not collide.
     */
    static void register() {
        McShadersAPI.registerEffect(fogDefinition());
        McShadersAPI.registerSchema(FOG, fogSchema());
    }
}
