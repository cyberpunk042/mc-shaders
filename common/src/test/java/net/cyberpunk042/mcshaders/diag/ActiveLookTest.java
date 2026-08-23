package net.cyberpunk042.mcshaders.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Whether the "which look is winning" line says the right thing at the right time.
 *
 * <p>Its whole value is being quiet until something changes, so the tests are mostly
 * about silence. A version that reported every frame would be technically correct and
 * useless.
 */
class ActiveLookTest {

    private static final DimensionId HERE = DimensionId.of("mcshaders", "test_active");

    @BeforeEach
    void forget() {
        ActiveLook.reset();
    }

    private static DimensionBinding binding(String id, int priority, Condition when) {
        return new DimensionBinding(id, HERE, when,
                EffectStack.of(EffectLayer.of("atmosphere", EffectKind.FOG, EffectParams.empty())),
                priority);
    }

    private static WorldState at(Weather weather) {
        return new WorldState(HERE, WorldState.UNKNOWN_DAY_TIME, 64.0, weather, Set.of(), false);
    }

    private static BindingRegistry twoLayers() {
        return BindingRegistry.of(
                binding("base", 0, Condition.always()),
                binding("storm", 20, new Condition.InWeather(Weather.THUNDER)));
    }

    @Nested
    @DisplayName("when it speaks")
    class Speaks {

        @Test
        @DisplayName("the first look is always reported, since there is nothing to compare to")
        void firstIsReported() {
            assertNotNull(ActiveLook.describeIfChanged(twoLayers(), at(Weather.CLEAR)));
        }

        @Test
        @DisplayName("a changed winning set is reported")
        void changeIsReported() {
            BindingRegistry registry = twoLayers();
            ActiveLook.describeIfChanged(registry, at(Weather.CLEAR));

            String line = ActiveLook.describeIfChanged(registry, at(Weather.THUNDER));

            assertNotNull(line, "the storm starting should be reported");
            assertTrue(line.contains("storm"));
        }

        @Test
        @DisplayName("the merge order is named, because that is what decides the outcome")
        void namesMergeOrder() {
            String line = ActiveLook.describeIfChanged(twoLayers(), at(Weather.THUNDER));

            assertTrue(line.contains("base -> storm"),
                    "priority order is what decides which wins; it should be visible: " + line);
        }

        @Test
        @DisplayName("having nothing active is reported too, and names the dimension")
        void emptyIsReported() {
            BindingRegistry elsewhere = BindingRegistry.of(
                    binding("never", 0, Condition.never()));

            String line = ActiveLook.describeIfChanged(elsewhere, at(Weather.CLEAR));

            // "nothing is active" is expected almost everywhere and alarming in a
            // dimension the pack was meant to cover. Only the dimension separates them.
            assertTrue(line.contains("No binding is active"));
            assertTrue(line.contains("test_active"));
        }
    }

    @Nested
    @DisplayName("when it stays quiet")
    class StaysQuiet {

        @Test
        @DisplayName("an unchanged look is not reported again")
        void unchangedIsSilent() {
            BindingRegistry registry = twoLayers();
            ActiveLook.describeIfChanged(registry, at(Weather.CLEAR));

            assertNull(ActiveLook.describeIfChanged(registry, at(Weather.CLEAR)));
            assertNull(ActiveLook.describeIfChanged(registry, at(Weather.CLEAR)));
        }

        @Test
        @DisplayName("a state that changes without changing the winner is silent")
        void irrelevantChangeIsSilent() {
            // Rain is not thunder: the storm binding does not activate, so the winning
            // set is unchanged and there is nothing to say. Keying on the world state
            // rather than the winners would report this, wrongly.
            BindingRegistry registry = twoLayers();
            ActiveLook.describeIfChanged(registry, at(Weather.CLEAR));

            assertNull(ActiveLook.describeIfChanged(registry, at(Weather.RAIN)));
        }

        @Test
        @DisplayName("returning to a previous look reports it, rather than staying silent")
        void returningIsReported() {
            BindingRegistry registry = twoLayers();
            ActiveLook.describeIfChanged(registry, at(Weather.CLEAR));
            ActiveLook.describeIfChanged(registry, at(Weather.THUNDER));

            assertNotNull(ActiveLook.describeIfChanged(registry, at(Weather.CLEAR)),
                    "the storm ending is as much a transition as it starting");
        }

        @Test
        @DisplayName("nulls are tolerated rather than thrown, this being a diagnostic")
        void nullsAreSafe() {
            // A diagnostic that can crash the render path is worse than no diagnostic.
            assertNull(ActiveLook.describeIfChanged(null, at(Weather.CLEAR)));
            assertNull(ActiveLook.describeIfChanged(twoLayers(), null));
        }
    }

    @Nested
    @DisplayName("the ids themselves")
    class Ids {

        @Test
        @DisplayName("only active bindings are listed")
        void onlyActive() {
            assertEquals(java.util.List.of("base"),
                    ActiveLook.activeIds(twoLayers(), at(Weather.CLEAR)));
        }
    }
}
