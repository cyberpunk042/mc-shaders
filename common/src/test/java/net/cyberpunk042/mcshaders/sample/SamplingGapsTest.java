package net.cyberpunk042.mcshaders.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Turning an invisible gap into a list of names.
 *
 * <p>A binding gated on night, in a game whose clock cannot be read, does not fail. It
 * never activates — which looks exactly like a typo, a priority problem, or a pack that
 * did not load. These tests are about the case that makes such an audit worthless: a
 * nested condition whose dependency the walk misses, which would report the gap as
 * smaller than it is and read as a clean bill of health.
 */
class SamplingGapsTest {

    private static final DimensionId BEYOND = DimensionId.of("mcshaders", "beyond");

    private static DimensionBinding binding(String id, Condition condition) {
        return new DimensionBinding(id, BEYOND, condition, EffectStack.empty(), 0);
    }

    private static final Condition NIGHT = new Condition.TimeOfDay(13000, 23000);
    private static final Condition DEEP = new Condition.YRange(0, 48);

    @Nested
    @DisplayName("what a condition depends on")
    class Dependencies {

        @Test
        @DisplayName("a bare condition reports its own field")
        void bareConditions() {
            assertEquals(Set.of(SamplingGaps.Field.DAY_TIME), SamplingGaps.dependencies(NIGHT));
            assertEquals(Set.of(SamplingGaps.Field.Y_LEVEL), SamplingGaps.dependencies(DEEP));
            assertEquals(
                    Set.of(SamplingGaps.Field.WEATHER),
                    SamplingGaps.dependencies(new Condition.InWeather(Weather.RAIN)));
            assertEquals(
                    Set.of(SamplingGaps.Field.BIOME_TAGS),
                    SamplingGaps.dependencies(new Condition.HasBiomeTag("is_nether")));
            assertEquals(
                    Set.of(SamplingGaps.Field.SUBMERGED),
                    SamplingGaps.dependencies(new Condition.Submerged()));
        }

        @Test
        @DisplayName("always and never depend on nothing")
        void unconditional() {
            assertTrue(SamplingGaps.dependencies(Condition.always()).isEmpty());
            assertTrue(SamplingGaps.dependencies(Condition.never()).isEmpty());
        }

        @Test
        @DisplayName("a null condition reports nothing rather than throwing")
        void nullCondition() {
            assertTrue(SamplingGaps.dependencies(null).isEmpty());
        }

        @Test
        @DisplayName("finds a dependency nested inside an all")
        void insideAll() {
            Condition nested = new Condition.All(List.of(DEEP, NIGHT));

            assertTrue(SamplingGaps.dependencies(nested).contains(SamplingGaps.Field.DAY_TIME));
        }

        @Test
        @DisplayName("finds a dependency nested inside an any")
        void insideAny() {
            Condition nested = new Condition.Any(List.of(new Condition.Submerged(), NIGHT));

            assertTrue(SamplingGaps.dependencies(nested).contains(SamplingGaps.Field.DAY_TIME));
        }

        @Test
        @DisplayName("finds a dependency under a not")
        void insideNot() {
            assertTrue(SamplingGaps.dependencies(new Condition.Not(NIGHT))
                    .contains(SamplingGaps.Field.DAY_TIME));
        }

        @Test
        @DisplayName("finds one buried several levels down")
        void deeplyNested() {
            Condition buried = new Condition.Not(
                    new Condition.Any(List.of(DEEP, new Condition.All(List.of(NIGHT)))));

            assertTrue(
                    SamplingGaps.dependencies(buried).contains(SamplingGaps.Field.DAY_TIME),
                    "an audit that stops at the first level reports the gap as smaller "
                            + "than it is, which is worse than not auditing");
        }

        @Test
        @DisplayName("reports every distinct field in a compound condition")
        void reportsAllOfThem() {
            Condition compound = new Condition.All(List.of(DEEP, NIGHT, new Condition.Submerged()));

            assertEquals(
                    Set.of(
                            SamplingGaps.Field.Y_LEVEL,
                            SamplingGaps.Field.DAY_TIME,
                            SamplingGaps.Field.SUBMERGED),
                    SamplingGaps.dependencies(compound));
        }
    }

    @Nested
    @DisplayName("naming the affected bindings")
    class Affected {

        @Test
        @DisplayName("names the one that cannot work")
        void namesIt() {
            BindingRegistry registry = BindingRegistry.of(
                    binding("depths", DEEP), binding("nightfall", NIGHT));

            assertEquals(
                    List.of("nightfall"),
                    SamplingGaps.affected(registry, SamplingGaps.UNAVAILABLE));
        }

        @Test
        @DisplayName("leaves alone the ones that can")
        void leavesTheRest() {
            BindingRegistry registry = BindingRegistry.of(binding("depths", DEEP));

            assertTrue(SamplingGaps.affected(registry, SamplingGaps.UNAVAILABLE).isEmpty());
        }

        @Test
        @DisplayName("catches one whose dependency is nested")
        void catchesNested() {
            BindingRegistry registry = BindingRegistry.of(
                    binding("storm_at_night", new Condition.All(List.of(
                            new Condition.InWeather(Weather.THUNDER), NIGHT))));

            assertEquals(
                    List.of("storm_at_night"),
                    SamplingGaps.affected(registry, SamplingGaps.UNAVAILABLE));
        }

        @Test
        @DisplayName("reports nothing when nothing is missing")
        void nothingMissing() {
            BindingRegistry registry = BindingRegistry.of(binding("nightfall", NIGHT));

            assertTrue(SamplingGaps.affected(registry, Set.of()).isEmpty());
        }

        @Test
        @DisplayName("tolerates a null registry")
        void nullRegistry() {
            assertTrue(SamplingGaps.affected(null, SamplingGaps.UNAVAILABLE).isEmpty());
        }

        @Test
        @DisplayName("day time is what is currently unavailable")
        void dayTimeIsTheGap() {
            assertTrue(SamplingGaps.UNAVAILABLE.contains(SamplingGaps.Field.DAY_TIME));
            assertFalse(SamplingGaps.UNAVAILABLE.contains(SamplingGaps.Field.Y_LEVEL),
                    "Y is sampled from the camera and must not be reported as a gap");
        }
    }

    @Nested
    @DisplayName("the line worth logging")
    class Describe {

        @Test
        @DisplayName("names the bindings, not just how many")
        void namesThem() {
            BindingRegistry registry = BindingRegistry.of(
                    binding("depths", DEEP), binding("nightfall", NIGHT));

            String line = SamplingGaps.describe(registry, SamplingGaps.UNAVAILABLE);

            assertTrue(line.contains("nightfall"), "a count is a fact nobody can act on");
            assertFalse(line.contains("depths"), "must not blame a binding that works");
        }

        @Test
        @DisplayName("says nothing when there is nothing to say")
        void silentWhenFine() {
            BindingRegistry registry = BindingRegistry.of(binding("depths", DEEP));

            assertEquals("", SamplingGaps.describe(registry, SamplingGaps.UNAVAILABLE),
                    "an empty registry of problems must not produce a warning that "
                            + "trains people to ignore warnings");
        }
    }
}
