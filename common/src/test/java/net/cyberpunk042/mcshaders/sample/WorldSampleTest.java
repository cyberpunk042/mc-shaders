package net.cyberpunk042.mcshaders.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The translation from what the game reports to what a binding is resolved against.
 *
 * <p>Two things here would be invisible if wrong. Weather comes out of vanilla as a
 * pair of ramping floats rather than a state, so where the line falls decides whether
 * a storm look flickers at the edges of a shower. And time of day cannot be read at
 * all on this version — what gets substituted decides whether a time-gated binding is
 * reliably off or accidentally on for half of every day.
 */
class WorldSampleTest {

    private static final DimensionId BEYOND = DimensionId.of("mcshaders", "beyond");

    private static WorldState sample(float rain, float thunder) {
        return WorldSample.of(BEYOND, 40, rain, thunder, Set.of(), false);
    }

    @Nested
    @DisplayName("weather")
    class WeatherMapping {

        @Test
        @DisplayName("a clear sky is clear")
        void clear() {
            assertEquals(Weather.CLEAR, WorldSample.weatherOf(0, 0));
        }

        @Test
        @DisplayName("full rain is rain")
        void rain() {
            assertEquals(Weather.RAIN, WorldSample.weatherOf(1, 0));
        }

        @Test
        @DisplayName("thunder wins over rain, because vanilla only thunders while raining")
        void thunderWins() {
            assertEquals(Weather.THUNDER, WorldSample.weatherOf(1, 1));
        }

        @Test
        @DisplayName("thunder alone still reads as thunder")
        void thunderAlone() {
            assertEquals(Weather.THUNDER, WorldSample.weatherOf(0, 1));
        }

        @Test
        @DisplayName("the first trace of a shower is not yet rain")
        void doesNotFlickerAtTheEdges() {
            assertEquals(Weather.CLEAR, WorldSample.weatherOf(0.01f, 0),
                    "switching on any level above zero would flip state on the first "
                            + "frame of a shower and flicker either side of it");
        }

        @Test
        @DisplayName("the threshold is where it says it is")
        void thresholdHolds() {
            float t = WorldSample.WEATHER_THRESHOLD;

            assertEquals(Weather.CLEAR, WorldSample.weatherOf(t, 0), "at the threshold");
            assertEquals(Weather.RAIN, WorldSample.weatherOf(t + 0.01f, 0), "just over it");
        }
    }

    @Nested
    @DisplayName("the time of day that cannot be read")
    class DayTime {

        @Test
        @DisplayName("is a value no real clock produces")
        void isOutOfRange() {
            WorldState state = sample(0, 0);

            assertTrue(state.dayTime() < 0,
                    "a plausible value like noon would be indistinguishable from a "
                            + "working clock that is stuck");
            assertEquals(WorldSample.DAY_TIME_UNAVAILABLE, state.dayTime());
        }

        @Test
        @DisplayName("is reported as not real")
        void reportsItself() {
            assertFalse(WorldSample.hasRealDayTime(sample(0, 0)));
        }

        @Test
        @DisplayName("a genuine reading would be reported as real")
        void wouldAcceptARealOne() {
            assertTrue(WorldSample.hasRealDayTime(sample(0, 0).withDayTime(6000)),
                    "the check must pass once a clock accessor exists, or it would have "
                            + "to be found and removed later");
        }

        @Test
        @DisplayName("tolerates a null state rather than throwing")
        void nullIsNotReal() {
            assertFalse(WorldSample.hasRealDayTime(null));
        }
    }

    @Nested
    @DisplayName("the rest of the state")
    class Passthrough {

        @Test
        @DisplayName("carries dimension, height and submersion through")
        void carriesTheSampledFields() {
            WorldState state =
                    WorldSample.of(BEYOND, 12.5, 0, 0, Set.of("is_nether"), true);

            assertEquals(BEYOND, state.dimension());
            assertEquals(12.5, state.yLevel());
            assertTrue(state.submerged());
            assertTrue(state.biomeTags().contains("is_nether"));
        }

        @Test
        @DisplayName("null biome tags become none rather than a crash mid-frame")
        void nullTagsAreEmpty() {
            WorldState state = WorldSample.of(BEYOND, 0, 0, 0, null, false);

            assertTrue(state.biomeTags().isEmpty());
        }
    }
}
