package net.cyberpunk042.mcshaders.vanilla.fog;

import net.cyberpunk042.mcshaders.BuiltinBackends;
import net.cyberpunk042.mcshaders.fog.FogSink;
import net.minecraft.client.renderer.fog.FogData;

/**
 * Writes the fog the pipeline resolved into the {@link FogData} the game is about to
 * draw with.
 *
 * <p>Both loaders reach a {@code FogData} — Fabric by mixing into
 * {@code FogRenderer#setupFog}, NeoForge through {@code ViewportEvent.RenderFog},
 * which exposes the very same object via {@code getFogData()}. What to write into it
 * is identical, so it is written once here. That matters more than the usual
 * deduplication argument: <strong>which fields these are is an open question, and one
 * in-game test should answer it for both loaders rather than for one.</strong>
 *
 * <h2>The open question</h2>
 *
 * <p>{@code FogData} carries two distance pairs. This writes
 * {@code renderDistanceStart}/{@code End}, because vanilla sets that pair
 * unconditionally so it is always meaningful, whereas {@code environmentalStart}/
 * {@code End} is only set when a {@code FogEnvironment} applies — and ordinary air may
 * have none, which is exactly the case a dimension look cares about.
 *
 * <p><strong>The evidence now leans the other way.</strong> NeoForge's
 * {@code ViewportEvent.RenderFog} names its near and far plane accessors over
 * {@code environmentalStart} and {@code environmentalEnd} — read from its source, not
 * inferred — so the loader with the longest history of fog-modifying mods treats that
 * pair as <em>the</em> fog-distance API. That is a strong prior, not a proof, and it
 * does not dispose of the argument above.
 *
 * <p>So if fog does not visibly change in game, swapping the two assignments below to
 * the environmental pair is the first experiment, and it is now a one-line change in
 * one file affecting both loaders.
 */
public final class FogApply {

    private FogApply() {
    }

    /**
     * Applies the current reading, if there is one.
     *
     * <p>Blended against what vanilla just computed: a layer easing in has a weight
     * below 1, and the value it eases <em>from</em> only exists at this point.
     *
     * @param fog the frame's fog parameters, mutated in place
     */
    public static void apply(FogData fog) {
        if (fog == null) {
            return;
        }
        FogSink.Reading reading = BuiltinBackends.fog().current();
        if (!reading.active()) {
            // No fog layer in force this frame. Leave vanilla's values alone rather
            // than writing zeroes over them.
            return;
        }

        fog.renderDistanceStart = (float) reading.blend(fog.renderDistanceStart, reading.start());
        fog.renderDistanceEnd = (float) reading.blend(fog.renderDistanceEnd, reading.end());

        // The colour is the least ambiguous of the three: GameRenderer passes
        // cameraState.fogData.color straight into the level renderer, so unlike the
        // distances there is no second candidate field it might have meant.
        fog.color.set(
                (float) reading.blend(fog.color.x, reading.red()),
                (float) reading.blend(fog.color.y, reading.green()),
                (float) reading.blend(fog.color.z, reading.blue()),
                (float) reading.blend(fog.color.w, reading.alpha()));
    }
}
