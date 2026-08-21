package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.cyberpunk042.mcshaders.core.backend.BackendCapabilities;
import net.cyberpunk042.mcshaders.core.backend.BackendFactory;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.EffectDefinition;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Compiles and exercises the core-side examples from docs/USING_AS_A_LIBRARY.md.
 *
 * <p>A library's documentation is its contract. These examples are what a new
 * consumer copies first, so the build fails if they stop compiling or stop behaving
 * as the prose claims. Change them here and there together.
 */
class LibraryDocExampleTest {

    @Test
    @DisplayName("the dimension-look example compiles, and the conditional override merges per layer")
    void dimensionLookExample() {
        EffectStack look = EffectStack.of(
                EffectLayer.builder("haze")
                        .kind(EffectKind.DISTORT)
                        .params(EffectParams.builder().scalar("amplitude", 0.02).build())
                        .build(),
                EffectLayer.builder("grade")
                        .kind(EffectKind.COLOR_GRADE)
                        .params(EffectParams.builder().color("tint", 0.8f, 0.9f, 1.0f, 1.0f).build())
                        .build());

        DimensionId dreamscape = DimensionId.parse("mymod:dreamscape");

        BindingRegistry registry = BindingRegistry.of(
                DimensionBinding.of("mymod:dreamscape", dreamscape, look),
                new DimensionBinding(
                        "mymod:dreamscape_night",
                        dreamscape,
                        new Condition.TimeOfDay(13000, 1000),
                        EffectStack.of(EffectLayer.builder("grade").kind(EffectKind.COLOR_GRADE)
                                .params(EffectParams.builder()
                                        .color("tint", 0.3f, 0.3f, 0.6f, 1.0f).build())
                                .build()),
                        10));

        EffectStack atNight = registry.resolve(WorldState.of(dreamscape).withDayTime(18000));

        // The doc claims haze survives untouched and only grade is overridden.
        assertEquals(2, atNight.size());
        assertTrue(atNight.byId("haze").isPresent(), "the untouched layer must survive");
        assertEquals(0.3f, atNight.byId("grade").orElseThrow()
                .params().color("tint").orElseThrow().r(), 1e-6);

        EffectStack atNoon = registry.resolve(WorldState.of(dreamscape).withDayTime(6000));
        assertEquals(0.8f, atNoon.byId("grade").orElseThrow()
                .params().color("tint").orElseThrow().r(), 1e-6,
                "outside the night window the base look applies");
    }

    @Test
    @DisplayName("the custom effect example compiles, and defaults fill only the gaps")
    void customEffectExample() {
        EffectDefinition kaleidoscope = EffectDefinition.of("mymod:kaleidoscope", "mymod")
                .withDefaults(EffectParams.builder()
                        .scalar("segments", 6.0)
                        .scalar("rotation", 0.0)
                        .build());

        EffectLayer layer = EffectLayer.builder("swirl")
                .definition(kaleidoscope)
                .params(EffectParams.builder().scalar("segments", 12.0).build())
                .build();

        EffectLayer resolved = new net.cyberpunk042.mcshaders.core.effect.EffectRegistry()
                .register(kaleidoscope)
                .applyDefaults(layer);

        assertEquals(12.0, resolved.params().scalarOr("segments", -1), "explicit value wins");
        assertEquals(0.0, resolved.params().scalarOr("rotation", -1), "default fills the gap");
    }

    @Test
    @DisplayName("the backend example compiles and declares capabilities as documented")
    void backendExample() {
        BackendFactory factory = new BackendFactory() {
            @Override
            public String id() {
                return "mymod:fancy";
            }

            @Override
            public int priority() {
                return BackendFactory.DEFAULT_PRIORITY + 100;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public EffectBackend create() {
                return new RecordingBackend(new BackendCapabilities(
                        "MyGraphics 1.0",
                        Set.of(EffectKind.COLOR_GRADE, EffectKind.FOG),
                        Set.of("mymod:kaleidoscope"),
                        true,
                        8));
            }
        };

        BackendCapabilities caps = factory.create().capabilities();
        assertTrue(caps.supportsType("mymod:kaleidoscope"));
        assertTrue(caps.supports(EffectKind.FOG), "depth is available, so FOG is renderable");
        assertEquals(8, caps.maxPasses());
        assertTrue(caps.hasPassLimit());
        assertEquals(BackendFactory.DEFAULT_PRIORITY + 100, factory.priority());
    }

    @Test
    @DisplayName("the off-Minecraft usage example compiles and drives a frame")
    void standaloneUsageExample() {
        DimensionId scene = DimensionId.parse("app:scene");
        EffectStack look = EffectStack.of(
                EffectLayer.of("grade", EffectKind.COLOR_GRADE, EffectParams.empty()));

        BindingRegistry bindings = BindingRegistry.of(
                DimensionBinding.of("scene", scene, look));

        RecordingBackend backend = RecordingBackend.capable();
        ShaderPipeline pipeline = new ShaderPipeline(backend, bindings);

        WorldState state = WorldState.of(scene)
                .withDayTime(18000)
                .withWeather(Weather.RAIN);

        pipeline.snapTo(state);
        pipeline.frame(state, 1.0, new EffectBackend.FrameContext(1920, 1080, 0f, 0.0));

        assertEquals(1, backend.frameCount());
    }
}
