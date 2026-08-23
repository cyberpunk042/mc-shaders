package net.cyberpunk042.mcshaders.vanilla.render;

import java.util.LinkedHashSet;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.sample.WorldSample;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.FogType;

/**
 * Reads the game, so {@link WorldSample} does not have to.
 *
 * <p>Everything here touches Minecraft and therefore cannot be tested from a build
 * machine. That is the reason it is this thin: it fetches values and hands them over,
 * and every decision about what those values mean lives on the other side, where there
 * are tests.
 *
 * <h2>Why the extraction phase</h2>
 *
 * <p>26.2 splits a frame into extracting render state and drawing from it. Both
 * loaders fire an event between the two — Fabric's
 * {@code LevelExtractionEvents.END_EXTRACTION} and NeoForge's
 * {@code ExtractLevelRenderStateEvent} — which is early enough to publish before
 * anything is drawn, and hands over exactly the four values below. The obvious Fabric
 * alternative, {@code LevelRenderEvents.START_MAIN}, fires during drawing and provably
 * after fog is computed; see {@code docs/RENDERING-26.2.md}.
 *
 * <p>That the two loaders' events agree on all four types is what lets this be one
 * method rather than two. They are not merely similar: both hand back the same
 * {@link LevelRenderState}, and both fire after all vanilla state is extracted.
 *
 * <h2>What is not sampled</h2>
 *
 * <p>Time of day. {@code Level#getDayTime()} does not exist on 26.2 and no client
 * accessor for its replacement has been found, so {@link WorldSample} substitutes
 * {@link WorldState#UNKNOWN_DAY_TIME} and {@code SamplingGaps} names the bindings that
 * cannot work as a result.
 */
public final class WorldSampler {

    private WorldSampler() {
    }

    /**
     * Builds the state this frame should be resolved against.
     *
     * @param level       the level being extracted
     * @param camera      where it is being observed from
     * @param deltaTracker the frame's partial tick
     * @param levelState  the render state, for what the camera is submerged in
     * @return the sampled state, or null when there is nothing to sample
     */
    public static WorldState from(ClientLevel level, Camera camera,
            DeltaTracker deltaTracker, LevelRenderState levelState) {
        if (level == null || camera == null || deltaTracker == null || levelState == null) {
            return null;
        }

        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        BlockPos at = camera.blockPosition();

        return WorldSample.of(
                dimensionOf(level),
                camera.position().y,
                level.getRainLevel(partialTick),
                level.getThunderLevel(partialTick),
                biomeTagsAt(level, at),
                isSubmerged(levelState));
    }

    /**
     * Monotonic time, for effects that animate.
     *
     * <p>Not the frame delta — {@code FrameContext.elapsedTicks} is documented as
     * increasing without going backwards, and handing it a delta would make anything
     * driven by it stutter in place rather than run.
     *
     * <p>{@code getGameTime()} is the world's total age in ticks and only advances;
     * the partial tick is added so an effect animates smoothly between them rather
     * than stepping twenty times a second.
     */
    public static double elapsedTicks(ClientLevel level, DeltaTracker deltaTracker) {
        if (level == null || deltaTracker == null) {
            return 0;
        }
        return level.getGameTime() + deltaTracker.getGameTimeDeltaPartialTick(false);
    }

    /**
     * The dimension, as this framework names dimensions.
     *
     * <p>{@code ResourceKey#identifier()}, not {@code location()} — 26.2 renamed
     * {@code ResourceLocation} to {@code Identifier} but the accessors did not move
     * together, and {@code TagKey} below still uses {@code location()}.
     */
    private static DimensionId dimensionOf(ClientLevel level) {
        Identifier id = level.dimension().identifier();
        return DimensionId.of(id.getNamespace(), id.getPath());
    }

    /**
     * The biome's tags, without the leading {@code #} the condition strips anyway.
     *
     * <p>{@code TagKey#location()}, which is the asymmetry warned about above: the same
     * expression in NeoForge's own {@code TagsCommand} uses {@code identifier()} for a
     * {@code ResourceKey} and {@code location()} for a {@code TagKey}.
     */
    private static Set<String> biomeTagsAt(ClientLevel level, BlockPos at) {
        Set<String> tags = new LinkedHashSet<>();
        level.getBiome(at).tags().forEach(tag -> tags.add(tag.location().toString()));
        return tags;
    }

    /**
     * Whether the camera is inside a fluid.
     *
     * <p>Taken from the camera's render state rather than by probing the level, which
     * is what vanilla itself consults and needs no mixin.
     *
     * <p><b>Two of these constants are unverified.</b> {@code FogType.LAVA} and
     * {@code POWDER_SNOW} appear in 26.2 sources; {@code WATER} and {@code NONE} do
     * not, in anything reachable from here. Naming them is therefore a claim the
     * compiler settles — CI builds against the real 26.2 jar, so a wrong constant is a
     * red build rather than a wrong frame. That is a different risk from an API whose
     * shape only fails at runtime, and it is why this one is written rather than
     * deferred.
     */
    private static boolean isSubmerged(LevelRenderState levelState) {
        FogType fog = levelState.cameraRenderState.fogType;
        return fog == FogType.WATER || fog == FogType.LAVA || fog == FogType.POWDER_SNOW;
    }
}
