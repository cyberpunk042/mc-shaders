package net.cyberpunk042.mcshaders.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.cyberpunk042.mcshaders.BuiltinBackends;
import net.cyberpunk042.mcshaders.fog.FogSink;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the fog the pipeline resolved, after vanilla has finished deciding its own.
 *
 * <p>The values arrive through {@link FogSink} rather than by asking the pipeline
 * here, because {@code ShaderPipeline#frame} advances the transition and this method
 * runs however many times a frame the game needs fog. Driving the pipeline from here
 * would ease at a multiple of the intended speed.
 *
 * <h2>Why TAIL, and why that reaches the frame</h2>
 *
 * <p>Vanilla sets the render-distance pair as the last thing it does, so anything
 * written earlier would be overwritten. At {@code TAIL} the {@link FogData} is
 * complete, and it is the object the method returns — which is what
 * {@code GameRenderer} hands to the level renderer as {@code cameraState.fogData}.
 * So a value written here is the value drawn with.
 *
 * <p>That is a chain of read links, not something anyone has watched happen. See
 * <a href="../../../../../../../docs/RENDERING-26.2.md">RENDERING-26.2.md</a>.
 *
 * <h2>Which pair this writes, and the one it does not</h2>
 *
 * <p>{@code FogData} carries two distance pairs. This writes
 * {@code renderDistanceStart}/{@code End} for two reasons: vanilla sets that pair
 * unconditionally, so it is always meaningful; and {@code environmentalStart}/
 * {@code End} is only set when a {@code FogEnvironment} applies — in ordinary air
 * there may be none, which is exactly the case a dimension look cares about.
 *
 * <p>The counter-argument is real and recorded rather than dismissed: NeoForge's own
 * fog-modification event exposes the environmental pair, which suggests that is the
 * one a mod changing atmosphere is meant to write. If this renders as nothing
 * happening, that is the first thing to try — it is a two-line change, and the reason
 * it was not chosen is written above rather than left to be re-derived.
 *
 * <h2>The generic on the callback</h2>
 *
 * <p>{@code CallbackInfoReturnable<FogData>} matches what the method actually returns.
 * Mixin does not check that type argument — it is erased — so this is documentation
 * rather than enforcement, and Jade's {@code Vector4f} works for the same reason.
 *
 * <h2>What CI is and is not proving here</h2>
 *
 * <p>The field names are read out of vanilla 26.2: {@code renderDistanceStart} and
 * {@code End} are assigned in the method itself, and {@code fog.color} is passed to
 * {@code computeFogColor} as its destination. The component access below
 * ({@code .x}, {@code .y}, {@code .z}, {@code .w}) is JOML's, and is the one thing
 * here taken on the library's conventions rather than read — a wrong guess is a
 * failed compile against the real jar, not a wrong frame.
 *
 * <p>None of that shows the fog changes. Mixin resolves its target at runtime, so a
 * mixin that compiles has not even been shown to apply.
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private void mcshaders$applyFog(
            Camera camera,
            int renderDistanceInChunks,
            DeltaTracker deltaTracker,
            float darkenWorldAmount,
            ClientLevel level,
            CallbackInfoReturnable<FogData> cir,
            @Local(name = "fog") FogData fog) {
        FogSink.Reading reading = BuiltinBackends.fog().current();
        if (!reading.active()) {
            // No fog layer in force this frame. Leave vanilla's values alone rather
            // than writing zeroes over them.
            return;
        }

        // Blended against what vanilla just computed, because a layer easing in has a
        // weight below 1 and the value it eases from only exists here.
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
