package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.cyberpunk042.mcshaders.core.backend.BackendCapabilities;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.backend.NoOpBackend;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.graph.EffectCompiler;
import net.cyberpunk042.mcshaders.core.graph.EffectGraph;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.transition.Easing;
import net.cyberpunk042.mcshaders.core.transition.Transition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PipelineTest {

    private static final DimensionId OVERWORLD = DimensionId.minecraft("overworld");
    private static final DimensionId NETHER = DimensionId.minecraft("the_nether");
    private static final EffectBackend.FrameContext FRAME =
            new EffectBackend.FrameContext(1920, 1080, 0.5f, 0.0);

    private static EffectLayer layer(String id, EffectKind kind, double weight, int priority) {
        return new EffectLayer(id, kind, EffectParams.empty(), BlendMode.ALPHA, weight, priority);
    }

    @Nested
    @DisplayName("stack ordering and merging")
    class Stacks {

        @Test
        void renderOrderFollowsPriorityAndIsStableOnTies() {
            EffectStack stack = EffectStack.of(
                    layer("third", EffectKind.BLOOM, 1.0, 10),
                    layer("first", EffectKind.COLOR_GRADE, 1.0, -5),
                    layer("tieA", EffectKind.VIGNETTE, 1.0, 0),
                    layer("tieB", EffectKind.GRAIN, 1.0, 0));

            assertEquals(
                    java.util.List.of("first", "tieA", "tieB", "third"),
                    stack.inRenderOrder().stream().map(EffectLayer::id).toList());
        }

        @Test
        void duplicateIdsCollapseKeepingTheFirstPosition() {
            EffectStack stack = EffectStack.of(
                    layer("a", EffectKind.BLOOM, 1.0, 0),
                    layer("b", EffectKind.FOG, 1.0, 0),
                    layer("a", EffectKind.BLOOM, 0.5, 0));

            assertEquals(2, stack.size());
            assertEquals(0.5, stack.byId("a").orElseThrow().weight(), "later entry wins");
            assertEquals("a", stack.layers().get(0).id(), "original position is kept");
        }

        @Test
        void mergeIsAdditiveAndNeverRemoves() {
            EffectStack base = EffectStack.of(layer("a", EffectKind.FOG, 1.0, 0));
            EffectStack overlay = EffectStack.of(layer("b", EffectKind.BLOOM, 1.0, 0));
            assertEquals(2, base.merge(overlay).size());
        }
    }

    @Nested
    @DisplayName("transitions")
    class Transitions {

        @Test
        @DisplayName("layers absent on one side fade rather than pop")
        void unmatchedLayersFade() {
            EffectStack from = EffectStack.of(layer("old", EffectKind.FOG, 1.0, 0));
            EffectStack to = EffectStack.of(layer("new", EffectKind.BLOOM, 1.0, 0));

            EffectStack mid = from.lerp(to, 0.25);
            assertEquals(0.75, mid.byId("old").orElseThrow().weight(), 1e-9, "source fades out");
            assertEquals(0.25, mid.byId("new").orElseThrow().weight(), 1e-9, "destination fades in");
        }

        @Test
        void zeroDurationCompletesImmediatelyRatherThanDividingByZero() {
            Transition t = Transition.start(EffectStack.empty(),
                    EffectStack.of(layer("a", EffectKind.FOG, 1.0, 0)), 0.0, Easing.SMOOTH);
            assertTrue(t.isComplete());
            assertEquals(1, t.current().size());
        }

        @Test
        void advancingPastDurationSettlesAtTheDestination() {
            EffectStack to = EffectStack.of(layer("a", EffectKind.FOG, 1.0, 0));
            Transition t = Transition.start(EffectStack.empty(), to, 10.0, Easing.LINEAR).advance(999.0);
            assertTrue(t.isComplete());
            assertEquals(to, t.current());
        }

        @Test
        @DisplayName("retargeting mid-blend continues from what is on screen")
        void retargetPreservesVisualContinuity() {
            EffectStack a = EffectStack.of(layer("x", EffectKind.FOG, 1.0, 0));
            EffectStack b = EffectStack.of(layer("x", EffectKind.FOG, 0.0, 0));

            Transition t = Transition.start(a, b, 10.0, Easing.LINEAR).advance(5.0);
            double midWeight = t.current().byId("x").orElseThrow().weight();

            Transition redirected = t.retarget(a, 10.0, Easing.LINEAR);
            assertEquals(midWeight, redirected.current().byId("x").orElseThrow().weight(), 1e-9,
                    "the redirect must start from the on-screen value, not snap back");
        }

        @Test
        void easingCurvesStayWithinUnitRangeAndHitTheirEndpoints() {
            for (Easing easing : Easing.values()) {
                assertEquals(0.0, easing.apply(0.0), 1e-9, easing + " must start at 0");
                assertEquals(1.0, easing.apply(1.0), 1e-9, easing + " must end at 1");
                for (double t = 0.0; t <= 1.0; t += 0.05) {
                    double v = easing.apply(t);
                    assertTrue(v >= -1e-9 && v <= 1.0 + 1e-9, easing + " left [0,1] at t=" + t);
                }
            }
        }

        @Test
        void negativeDeltaDoesNotRewindATransition() {
            Transition t = Transition.start(EffectStack.empty(), EffectStack.empty(), 10.0, Easing.LINEAR)
                    .advance(5.0);
            assertEquals(t.elapsedTicks(), t.advance(-3.0).elapsedTicks());
        }
    }

    @Nested
    @DisplayName("compilation against backend capabilities")
    class Compilation {

        @Test
        void unsupportedKindsAreSkippedWithAWarningRatherThanThrowing() {
            BackendCapabilities limited = new BackendCapabilities(
                    "test", Set.of(EffectKind.COLOR_GRADE), true, 0);

            EffectGraph graph = new EffectCompiler(limited).compile(EffectStack.of(
                    layer("keep", EffectKind.COLOR_GRADE, 1.0, 0),
                    layer("drop", EffectKind.BLOOM, 1.0, 1)));

            assertEquals(1, graph.passCount());
            assertEquals("keep", graph.nodes().get(0).id());
            assertTrue(graph.hasWarnings());
            assertTrue(graph.warnings().get(0).contains("drop"));
        }

        @Test
        @DisplayName("depth-reading effects are rejected when depth is unavailable")
        void depthDependentKindsNeedDepth() {
            BackendCapabilities noDepth = new BackendCapabilities(
                    "test", Set.of(EffectKind.FOG), false, 0);

            EffectGraph graph = new EffectCompiler(noDepth)
                    .compile(EffectStack.of(layer("fog", EffectKind.FOG, 1.0, 0)));

            assertTrue(graph.isEmpty());
            assertTrue(graph.warnings().get(0).contains("depth"));
        }

        @Test
        void passLimitTruncatesTheGraph() {
            BackendCapabilities capped = new BackendCapabilities(
                    "test", Set.of(EffectKind.values()), true, 2);

            EffectGraph graph = new EffectCompiler(capped).compile(EffectStack.of(
                    layer("a", EffectKind.BLOOM, 1.0, 0),
                    layer("b", EffectKind.BLOOM, 1.0, 1),
                    layer("c", EffectKind.BLOOM, 1.0, 2)));

            assertEquals(2, graph.passCount());
            assertTrue(graph.hasWarnings());
        }

        @Test
        void nodesAreNumberedInExecutionOrder() {
            EffectGraph graph = new EffectCompiler(BackendCapabilities.full("test")).compile(
                    EffectStack.of(
                            layer("late", EffectKind.BLOOM, 1.0, 5),
                            layer("early", EffectKind.FOG, 1.0, 1)));

            assertEquals("early", graph.nodes().get(0).id());
            assertEquals(0, graph.nodes().get(0).order());
            assertEquals(1, graph.nodes().get(1).order());
        }

        @Test
        void excessiveAccumulatingLayersAreFlagged() {
            EffectLayer[] layers = new EffectLayer[EffectCompiler.ACCUMULATION_WARN_THRESHOLD + 1];
            for (int i = 0; i < layers.length; i++) {
                layers[i] = new EffectLayer("add" + i, EffectKind.BLOOM, EffectParams.empty(),
                        BlendMode.ADD, 1.0, i);
            }

            EffectGraph graph = new EffectCompiler(BackendCapabilities.full("test"))
                    .compile(EffectStack.of(layers));

            assertTrue(graph.warnings().stream().anyMatch(w -> w.contains("blow out")));
        }
    }

    @Nested
    @DisplayName("pipeline driving")
    class Driving {

        private ShaderPipeline pipelineWith(BindingRegistry registry, NoOpBackend backend) {
            ShaderPipeline pipeline = new ShaderPipeline(backend, registry);
            pipeline.setEasing(Easing.LINEAR);
            return pipeline;
        }

        @Test
        void anEmptyRegistryRendersNothing() {
            NoOpBackend backend = new NoOpBackend();
            ShaderPipeline pipeline = pipelineWith(BindingRegistry.empty(), backend);

            EffectGraph graph = pipeline.frame(WorldState.of(OVERWORLD), 1.0, FRAME);

            assertTrue(graph.isEmpty());
            assertEquals(0, backend.framesSeen(), "an empty graph must not reach the backend");
        }

        @Test
        @DisplayName("a steady world does not restart the blend every frame")
        void steadyStateAllowsTheTransitionToProgress() {
            // A capable backend is required, otherwise the graph compiles to nothing
            // and the assertion would pass for the wrong reason.
            RecordingBackend backend = RecordingBackend.capable();

            BindingRegistry registry = BindingRegistry.of(DimensionBinding.of(
                    "b", OVERWORLD, EffectStack.of(layer("fog", EffectKind.FOG, 1.0, 0))));

            ShaderPipeline pipeline = new ShaderPipeline(backend, registry);
            pipeline.setEasing(Easing.LINEAR);
            pipeline.setTransitionTicks(10.0);

            WorldState state = WorldState.of(OVERWORLD);
            for (int i = 0; i < 5; i++) {
                pipeline.frame(state, 1.0, FRAME);
            }

            assertEquals(0.5, pipeline.transition().rawProgress(), 1e-9,
                    "five ticks of a ten-tick blend should be half done");
            assertFalse(pipeline.transition().isComplete());
        }

        @Test
        @DisplayName("changing dimension starts a blend instead of snapping")
        void dimensionChangeBlends() {
            RecordingBackend backend = RecordingBackend.capable();

            BindingRegistry registry = BindingRegistry.of(
                    DimensionBinding.of("o", OVERWORLD,
                            EffectStack.of(layer("calm", EffectKind.COLOR_GRADE, 1.0, 0))),
                    DimensionBinding.of("n", NETHER,
                            EffectStack.of(layer("heat", EffectKind.COLOR_GRADE, 1.0, 0))));

            ShaderPipeline pipeline = new ShaderPipeline(backend, registry);
            pipeline.setEasing(Easing.LINEAR);
            pipeline.setTransitionTicks(20.0);

            pipeline.snapTo(WorldState.of(OVERWORLD));
            assertEquals(1.0, pipeline.currentStack().byId("calm").orElseThrow().weight());

            pipeline.frame(WorldState.of(NETHER), 10.0, FRAME);

            EffectStack mid = pipeline.currentStack();
            assertEquals(0.5, mid.byId("calm").orElseThrow().weight(), 1e-9, "old look fades out");
            assertEquals(0.5, mid.byId("heat").orElseThrow().weight(), 1e-9, "new look fades in");
        }

        @Test
        void snapToSkipsTheBlendEntirely() {
            NoOpBackend backend = new NoOpBackend();
            BindingRegistry registry = BindingRegistry.of(DimensionBinding.of(
                    "b", OVERWORLD, EffectStack.of(layer("fog", EffectKind.FOG, 1.0, 0))));

            ShaderPipeline pipeline = pipelineWith(registry, backend);
            pipeline.snapTo(WorldState.of(OVERWORLD));

            assertTrue(pipeline.transition().isComplete());
            assertEquals(1.0, pipeline.currentStack().byId("fog").orElseThrow().weight());
        }

        @Test
        void frameContextRejectsDegenerateDimensions() {
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new EffectBackend.FrameContext(0, 1080, 0f, 0.0));
        }
    }
}
