package net.cyberpunk042.mcshaders.core.binding;

import java.util.Set;

/**
 * A snapshot of everything a binding condition may test against.
 *
 * <p>This is plain data with no Minecraft types. The Minecraft layer samples the
 * live world once per frame and fills this in; the core then evaluates conditions
 * against it. Keeping the snapshot explicit means condition logic is fully
 * testable without a running game.
 *
 * @param dimension  the dimension the viewer is currently in
 * @param dayTime    time of day in ticks, {@code [0, 24000)}
 * @param yLevel     the viewer's vertical position
 * @param weather    current weather
 * @param biomeTags  tags of the biome at the viewer's position, without the {@code #} prefix
 * @param submerged  whether the view is underwater or in another fluid
 */
public record WorldState(
        DimensionId dimension,
        long dayTime,
        double yLevel,
        Weather weather,
        Set<String> biomeTags,
        boolean submerged) {

    /** Length of a full Minecraft day in ticks. */
    public static final long DAY_LENGTH_TICKS = 24000L;

    /**
     * The time of day, when there is no way to read it.
     *
     * <p>A real state of the world rather than a placeholder: Minecraft 26.2 removed
     * {@code Level#getDayTime()} and no client accessor for its replacement has been
     * established, so a client-side sampler genuinely cannot answer the question.
     *
     * <p>Outside {@code [0, DAY_LENGTH_TICKS)} on purpose, and any negative reduces to
     * it. Every time-gated condition is false against it, so such a look is reliably
     * off rather than arbitrarily on — and something that quietly substituted a
     * plausible hour would instead make it on for part of every day, which is far
     * harder to notice and impossible to distinguish from a working clock.
     */
    public static final long UNKNOWN_DAY_TIME = -1L;

    /** Whether this state's time of day is a reading rather than {@link #UNKNOWN_DAY_TIME}. */
    public boolean hasDayTime() {
        return dayTime >= 0;
    }

    public WorldState {
        if (dimension == null) {
            throw new IllegalArgumentException("WorldState requires a dimension");
        }
        weather = weather == null ? Weather.CLEAR : weather;
        biomeTags = biomeTags == null ? Set.of() : Set.copyOf(biomeTags);
        // Normalise into a single day so conditions never have to handle wrap-around
        // from a world age that grows without bound. Negatives are left alone: they
        // are not times, they are UNKNOWN_DAY_TIME, and folding one into the day
        // would turn "the clock cannot be read" into a specific late-night hour.
        if (dayTime >= 0) {
            dayTime = Math.floorMod(dayTime, DAY_LENGTH_TICKS);
        } else {
            dayTime = UNKNOWN_DAY_TIME;
        }
    }

    /** A minimal state for the given dimension: noon, clear, at sea level. */
    public static WorldState of(DimensionId dimension) {
        return new WorldState(dimension, 6000L, 64.0, Weather.CLEAR, Set.of(), false);
    }

    public WorldState withDayTime(long ticks) {
        return new WorldState(dimension, ticks, yLevel, weather, biomeTags, submerged);
    }

    public WorldState withYLevel(double y) {
        return new WorldState(dimension, dayTime, y, weather, biomeTags, submerged);
    }

    public WorldState withWeather(Weather w) {
        return new WorldState(dimension, dayTime, yLevel, w, biomeTags, submerged);
    }

    public WorldState withBiomeTags(Set<String> tags) {
        return new WorldState(dimension, dayTime, yLevel, weather, tags, submerged);
    }

    public WorldState withSubmerged(boolean value) {
        return new WorldState(dimension, dayTime, yLevel, weather, biomeTags, value);
    }
}
