package net.cyberpunk042.mcshaders.sample;

import java.util.Set;
import net.cyberpunk042.mcshaders.core.api.Experimental;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.binding.WorldState;

/**
 * Turns what the game can be asked for into what a binding is resolved against.
 *
 * <p>Everything Minecraft-shaped stays on the other side of this: the caller reads
 * the level, the camera and the render state, and hands over primitives. So the
 * translation — which is where the judgement calls live — is testable, and the part
 * that needs a running game is reduced to fetching values.
 *
 * <h2>The one field that cannot be sampled</h2>
 *
 * <p>{@link WorldState} carries a {@code dayTime} and 26.2 has no reachable way to
 * read it: {@code Level#getDayTime()} is gone, and no client accessor for its
 * replacement has been established (see
 * <a href="../../../../../../docs/RENDERING-26.2.md">RENDERING-26.2.md</a>).
 *
 * <p>So {@link #DAY_TIME_UNAVAILABLE} is what gets passed, and it is deliberately a
 * value no real clock produces rather than a plausible-looking noon. A time-gated
 * binding is then reliably off instead of accidentally on for half the day, and
 * {@link SamplingGaps} can name the bindings this affects instead of leaving someone
 * to wonder why their night look never appears.
 */
@Experimental
public final class WorldSample {

    /**
     * The {@code dayTime} passed while the clock cannot be read.
     *
     * <p>An alias for {@link WorldState#UNKNOWN_DAY_TIME}, which is where the meaning
     * lives — the record itself preserves it rather than folding it into a day, and
     * every time-gated condition is false against it.
     */
    public static final long DAY_TIME_UNAVAILABLE = WorldState.UNKNOWN_DAY_TIME;

    /**
     * How much rain or thunder counts as weather.
     *
     * <p>Vanilla's levels ramp between 0 and 1 rather than switching, so testing for
     * "above zero" would flip the state on the first frame of a shower and flicker
     * either side of it. This is a chosen threshold, not a value read out of vanilla.
     */
    public static final float WEATHER_THRESHOLD = 0.2f;

    private WorldSample() {
    }

    /**
     * Which weather a pair of vanilla levels amounts to.
     *
     * <p>Thunder wins, because vanilla only raises the thunder level while it is
     * already raining — so both being high means a storm, not an ambiguity.
     *
     * @param rainLevel    vanilla's rain level, 0 to 1
     * @param thunderLevel vanilla's thunder level, 0 to 1
     */
    public static Weather weatherOf(float rainLevel, float thunderLevel) {
        if (thunderLevel > WEATHER_THRESHOLD) {
            return Weather.THUNDER;
        }
        return rainLevel > WEATHER_THRESHOLD ? Weather.RAIN : Weather.CLEAR;
    }

    /**
     * Builds the state a frame is resolved against.
     *
     * @param dimension  where the viewer is
     * @param yLevel     the camera's height
     * @param rainLevel  vanilla's rain level, 0 to 1
     * @param thunderLevel vanilla's thunder level, 0 to 1
     * @param biomeTags  the biome's tags, without the {@code #} prefix
     * @param submerged  whether the camera is in a fluid
     */
    public static WorldState of(
            DimensionId dimension,
            double yLevel,
            float rainLevel,
            float thunderLevel,
            Set<String> biomeTags,
            boolean submerged) {
        return new WorldState(
                dimension,
                DAY_TIME_UNAVAILABLE,
                yLevel,
                weatherOf(rainLevel, thunderLevel),
                biomeTags == null ? Set.of() : biomeTags,
                submerged);
    }

    /** Whether a state's time of day is a real reading rather than the placeholder. */
    public static boolean hasRealDayTime(WorldState state) {
        return state != null && state.hasDayTime();
    }
}
