package net.cyberpunk042.mcshaders.fabric.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.cyberpunk042.mcshaders.vanilla.fog.FogApply;
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
 * Reaches the frame's {@link FogData} so {@link FogApply} can write to it.
 *
 * <p>Fabric needs a mixin for this; NeoForge does not, because
 * {@code ViewportEvent.RenderFog} hands the same object over as an event. What gets
 * written is therefore not decided here — it lives in {@code FogApply}, so one
 * in-game test answers the open question for both loaders rather than for one.
 *
 * <h2>Why TAIL, and why that reaches the frame</h2>
 *
 * <p>Vanilla sets the render-distance pair as the last thing it does, so anything
 * written earlier is overwritten. At {@code TAIL} the {@code FogData} is complete, and
 * it is the object the method returns — which is what {@code GameRenderer} hands the
 * level renderer as {@code cameraState.fogData}. A value written here is the value
 * drawn with. That is a chain of read links, not something anyone has watched running.
 *
 * <p>The captured local is used rather than the return value because both name the
 * same object, and {@code @Local} does not depend on the callback's generic type being
 * right — the erased {@code CallbackInfoReturnable} makes that a weak guarantee.
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
        FogApply.apply(fog);
    }
}
