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

    public WorldState {
        if (dimension == null) {
            throw new IllegalArgumentException("WorldState requires a dimension");
        }
        weather = weather == null ? Weather.CLEAR : weather;
        biomeTags = biomeTags == null ? Set.of() : Set.copyOf(biomeTags);
        // Normalise into a single day so conditions never have to handle wrap-around
        // from a world age that grows without bound.
        dayTime = Math.floorMod(dayTime, DAY_LENGTH_TICKS);
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
