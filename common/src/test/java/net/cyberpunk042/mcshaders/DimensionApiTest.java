package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.ShaderPipeline;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Whether the dimension half of the API is connected to anything.
 *
 * <p>It was not. Registration accumulated bindings, {@code closeRegistration} handed
 * them to a static field, and no {@code ShaderPipeline} was ever constructed
 * anywhere in the mod — so the per-frame entry point could not have been called
 * even in principle. Three pieces that each worked and tested cleanly, with nothing
 * joining them.
 *
 * <p>The load-bearing test here is the reload one. A reload that updates a stored
 * field while a live pipeline goes on resolving against the bindings it was built
 * with is the worst kind of failure: {@code /reload} appears to succeed and changes
 * nothing.
 *
 * <p>These run against static state, so they assert on relationships that hold
 * whatever else has registered rather than on absolute counts.
 */
class DimensionApiTest {

    private static final DimensionId NOWHERE = DimensionId.of("mcshaders", "test_nowhere");

    private static WorldState at(DimensionId dimension) {
        return WorldState.of(dimension);
    }

    private static DimensionBinding binding(String id, DimensionId dimension, String layer) {
        return DimensionBinding.of(id, dimension,
                EffectStack.of(EffectLayer.of(layer, EffectKind.FOG, EffectParams.empty())));
    }

    @Nested
    @DisplayName("the pipeline exists")
    class Pipeline {

        @Test
        @DisplayName("the mod builds one, rather than leaving the registry unread")
        void pipelineIsBuilt() {
            assertNotNull(McShaders.pipeline(),
                    "nothing consumed the bindings before this existed");
        }

        @Test
        @DisplayName("it is built over the bindings that registration produced")
        void pipelineSeesTheRegistry() {
            ShaderPipeline pipeline = McShaders.pipeline();

            assertEquals(McShadersAPI.bindings().size(), pipeline.registry().size(),
                    "the pipeline must resolve against the same set the API reports");
        }
    }

    @Nested
    @DisplayName("reading what is in force")
    class Reading {

        @Test
        @DisplayName("bindings() answers, where before there was no reader at all")
        void bindingsAreReadable() {
            assertNotNull(McShadersAPI.bindings());
        }

        @Test
        @DisplayName("look() resolves a world state to the stack that would be drawn")
        void lookResolves() {
            McShadersAPI.reloadBindings(BindingRegistry.of(
                    binding("test_look", NOWHERE, "haze")));

            EffectStack stack = McShadersAPI.look(at(NOWHERE));

            assertEquals(1, stack.layers().size());
            assertEquals("haze", stack.layers().get(0).id());
        }

        @Test
        @DisplayName("a dimension nothing binds resolves to an empty stack, not a failure")
        void unboundDimensionIsEmpty() {
            McShadersAPI.reloadBindings(BindingRegistry.empty());

            assertTrue(McShadersAPI.look(at(NOWHERE)).layers().isEmpty());
        }

        @Test
        @DisplayName("a null world state is refused rather than resolved")
        void nullStateRefused() {
            assertThrows(IllegalArgumentException.class, () -> McShadersAPI.look(null));
        }
    }

    @Nested
    @DisplayName("reload")
    class Reload {

        @Test
        @DisplayName("a reload reaches the live pipeline, not just the stored field")
        void reloadReachesThePipeline() {
            // The bug this guards. Updating only the field leaves a running pipeline
            // resolving against what it was constructed with, so /reload looks like
            // it worked and changes nothing.
            ShaderPipeline pipeline = McShaders.pipeline();

            McShadersAPI.reloadBindings(BindingRegistry.of(
                    binding("test_reload_a", NOWHERE, "first")));
            assertEquals("first",
                    pipeline.registry().resolve(at(NOWHERE)).layers().get(0).id());

            McShadersAPI.reloadBindings(BindingRegistry.of(
                    binding("test_reload_b", NOWHERE, "second")));
            assertEquals("second",
                    pipeline.registry().resolve(at(NOWHERE)).layers().get(0).id(),
                    "the pipeline still held the first set");
        }

        @Test
        @DisplayName("reloading is allowed after registration closes, unlike registering")
        void reloadWorksAfterClose() {
            McShaders.completeRegistration();
            assertTrue(McShaders.isRegistrationComplete());

            // registerBinding is a startup accumulation and refuses now; a reload is
            // a runtime replacement and must not, or /reload could never do anything.
            assertThrows(IllegalStateException.class,
                    () -> McShadersAPI.registerBinding(binding("too_late", NOWHERE, "x")));

            McShadersAPI.reloadBindings(BindingRegistry.of(
                    binding("test_after_close", NOWHERE, "allowed")));
            assertEquals("allowed",
                    McShadersAPI.look(at(NOWHERE)).layers().get(0).id());
        }

        @Test
        @DisplayName("null means empty, which is what a reload finding no files produces")
        void nullReloadIsEmpty() {
            McShadersAPI.reloadBindings(null);

            assertTrue(McShadersAPI.bindings().isEmpty());
        }
    }
}
