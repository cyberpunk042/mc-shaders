package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BindingTest {

    private static final DimensionId OVERWORLD = DimensionId.minecraft("overworld");
    private static final DimensionId NETHER = DimensionId.minecraft("the_nether");

    private static EffectLayer layer(String id, double weight) {
        return new EffectLayer(id, EffectKind.COLOR_GRADE, EffectParams.empty(),
                net.cyberpunk042.mcshaders.core.effect.BlendMode.ALPHA, weight, 0);
    }

    @Nested
    @DisplayName("dimension ids")
    class Ids {

        @Test
        void bareStringDefaultsToMinecraftNamespace() {
            assertEquals(DimensionId.minecraft("overworld"), DimensionId.parse("overworld"));
        }

        @Test
        void namespacedStringParses() {
            DimensionId id = DimensionId.parse("mcshaders:void_reach");
            assertEquals("mcshaders", id.namespace());
            assertEquals("void_reach", id.path());
        }

        @Test
        void pathsMayContainSlashesButNamespacesMayNot() {
            DimensionId id = DimensionId.parse("mcshaders:deep/void");
            assertEquals("deep/void", id.path());
            assertThrows(IllegalArgumentException.class, () -> DimensionId.of("bad/ns", "path"));
        }

        @Test
        void malformedIdsAreRejected() {
            assertThrows(IllegalArgumentException.class, () -> DimensionId.parse(""));
            assertThrows(IllegalArgumentException.class, () -> DimensionId.parse("a:b:c"));
            assertThrows(IllegalArgumentException.class, () -> DimensionId.parse("Bad Space:x"));
        }

        @Test
        void idsAreCaseNormalised() {
            assertEquals(DimensionId.parse("mcshaders:void"), DimensionId.parse("MCShaders:VOID"));
        }
    }

    @Nested
    @DisplayName("conditions")
    class Conditions {

        @Test
        @DisplayName("a time range spanning midnight wraps correctly")
        void timeOfDayWrapsAroundMidnight() {
            Condition night = new Condition.TimeOfDay(13000, 1000);
            WorldState base = WorldState.of(OVERWORLD);

            assertTrue(night.test(base.withDayTime(18000)), "deep night is inside the range");
            assertTrue(night.test(base.withDayTime(500)), "after midnight is inside the range");
            assertFalse(night.test(base.withDayTime(6000)), "noon is outside the range");
        }

        @Test
        void worldStateNormalisesDayTimeIntoOneDay() {
            WorldState state = WorldState.of(OVERWORLD).withDayTime(24000L * 7 + 300);
            assertEquals(300, state.dayTime());
        }

        @Test
        void yRangeToleratesInvertedBounds() {
            Condition band = new Condition.YRange(100, 20);
            assertTrue(band.test(WorldState.of(OVERWORLD).withYLevel(50)));
            assertFalse(band.test(WorldState.of(OVERWORLD).withYLevel(150)));
        }

        @Test
        void biomeTagIgnoresLeadingHash() {
            Condition tagged = new Condition.HasBiomeTag("#is_cave");
            WorldState state = WorldState.of(OVERWORLD).withBiomeTags(Set.of("is_cave"));
            assertTrue(tagged.test(state));
        }

        @Test
        void combinatorsCompose() {
            Condition c = new Condition.InWeather(Weather.THUNDER)
                    .and(new Condition.YRange(0, 128));
            WorldState storm = WorldState.of(OVERWORLD).withWeather(Weather.THUNDER).withYLevel(64);
            assertTrue(c.test(storm));
            assertFalse(c.test(storm.withWeather(Weather.CLEAR)));
            assertTrue(c.negate().test(storm.withWeather(Weather.CLEAR)));
        }

        @Test
        @DisplayName("empty All is true, empty Any is false")
        void emptyCombinatorsHaveDefinedIdentity() {
            WorldState state = WorldState.of(OVERWORLD);
            assertTrue(new Condition.All(List.of()).test(state));
            assertFalse(new Condition.Any(List.of()).test(state));
        }
    }

    @Nested
    @DisplayName("registry resolution")
    class Resolution {

        @Test
        void onlyBindingsForTheCurrentDimensionApply() {
            BindingRegistry registry = BindingRegistry.of(
                    DimensionBinding.of("over", OVERWORLD, EffectStack.of(layer("grade", 1.0))),
                    DimensionBinding.of("neth", NETHER, EffectStack.of(layer("heat", 1.0))));

            EffectStack resolved = registry.resolve(WorldState.of(OVERWORLD));
            assertEquals(1, resolved.size());
            assertTrue(resolved.byId("grade").isPresent());
        }

        @Test
        @DisplayName("higher priority overrides a layer without discarding the rest")
        void higherPriorityOverridesPerLayer() {
            DimensionBinding base = new DimensionBinding("base", OVERWORLD, Condition.always(),
                    EffectStack.of(layer("grade", 1.0), layer("fog", 1.0)), 0);
            DimensionBinding tweak = new DimensionBinding("tweak", OVERWORLD, Condition.always(),
                    EffectStack.of(layer("fog", 0.25)), 10);

            EffectStack resolved = BindingRegistry.of(base, tweak).resolve(WorldState.of(OVERWORLD));

            assertEquals(2, resolved.size(), "the untouched layer must survive the override");
            assertEquals(0.25, resolved.byId("fog").orElseThrow().weight());
            assertEquals(1.0, resolved.byId("grade").orElseThrow().weight());
        }

        @Test
        void inactiveConditionsContributeNothing() {
            DimensionBinding nightOnly = new DimensionBinding("night", OVERWORLD,
                    new Condition.TimeOfDay(13000, 1000),
                    EffectStack.of(layer("stars", 1.0)), 0);

            BindingRegistry registry = BindingRegistry.of(nightOnly);
            assertTrue(registry.resolve(WorldState.of(OVERWORLD).withDayTime(6000)).isEmpty());
            assertEquals(1, registry.resolve(WorldState.of(OVERWORLD).withDayTime(18000)).size());
        }

        @Test
        void zeroWeightLayersArePrunedFromTheResult() {
            BindingRegistry registry = BindingRegistry.of(
                    DimensionBinding.of("b", OVERWORLD, EffectStack.of(layer("off", 0.0), layer("on", 1.0))));
            EffectStack resolved = registry.resolve(WorldState.of(OVERWORLD));
            assertEquals(1, resolved.size());
            assertTrue(resolved.byId("on").isPresent());
        }

        @Test
        void registryIsImmutable() {
            BindingRegistry original = BindingRegistry.of(
                    DimensionBinding.of("a", OVERWORLD, EffectStack.empty()));
            BindingRegistry extended = original.with(
                    DimensionBinding.of("b", NETHER, EffectStack.empty()));
            assertEquals(1, original.size());
            assertEquals(2, extended.size());
            assertEquals(1, extended.without("a").size());
        }

        @Test
        @DisplayName("resolution is deterministic regardless of insertion order")
        void resolutionIsOrderIndependent() {
            DimensionBinding a = new DimensionBinding("a", OVERWORLD, Condition.always(),
                    EffectStack.of(layer("x", 1.0)), 5);
            DimensionBinding b = new DimensionBinding("b", OVERWORLD, Condition.always(),
                    EffectStack.of(layer("x", 0.5)), 10);

            EffectStack forward = BindingRegistry.of(a, b).resolve(WorldState.of(OVERWORLD));
            EffectStack reverse = BindingRegistry.of(b, a).resolve(WorldState.of(OVERWORLD));
            assertEquals(forward, reverse);
            assertEquals(0.5, forward.byId("x").orElseThrow().weight());
        }
    }
}
