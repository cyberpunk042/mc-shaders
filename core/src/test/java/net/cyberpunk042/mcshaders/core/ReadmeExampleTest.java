package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.graph.EffectGraph;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Compiles and exercises the example printed in README.md.
 *
 * <p>Documentation that does not compile is worse than none, and an API example is
 * exactly the thing that rots first. This test fails the build if the README drifts
 * from the real surface — if you change it here, change it there.
 */
class ReadmeExampleTest {

    @Test
    @DisplayName("the README example compiles and produces a renderable graph")
    void readmeExampleWorks() {
        // ── begin README example ──────────────────────────────────────────────
        EffectStack netherHeat = EffectStack.of(
                EffectLayer.of("heat_haze", EffectKind.DISTORT,
                        EffectParams.builder().scalar("amplitude", 0.02).build()),
                EffectLayer.of("ember_grade", EffectKind.COLOR_GRADE,
                        EffectParams.builder().color("tint", 1.0f, 0.6f, 0.4f, 1.0f).build()));

        BindingRegistry registry = BindingRegistry.of(
                DimensionBinding.of("nether_base", DimensionId.minecraft("the_nether"), netherHeat));
        // ── end README example ────────────────────────────────────────────────

        RecordingBackend backend = RecordingBackend.capable();
        ShaderPipeline pipeline = new ShaderPipeline(backend, registry);

        WorldState inNether = WorldState.of(DimensionId.minecraft("the_nether"));
        pipeline.snapTo(inNether);

        EffectGraph graph = pipeline.frame(
                inNether, 1.0, new EffectBackend.FrameContext(1280, 720, 0f, 0.0));

        assertEquals(2, graph.passCount(), "both declared effects should compile to passes");
        assertTrue(graph.warnings().isEmpty(), "a well-formed stack should compile without warnings");
        assertEquals(1, backend.frameCount(), "the backend should have been driven once");
    }

    @Test
    @DisplayName("the per-layer merge described in the README behaves as documented")
    void perLayerMergeMatchesTheDocumentedBehaviour() {
        EffectStack base = EffectStack.of(
                EffectLayer.of("heat_haze", EffectKind.DISTORT, EffectParams.empty()),
                EffectLayer.of("ember_grade", EffectKind.COLOR_GRADE, EffectParams.empty()));

        EffectStack packOverride = EffectStack.of(
                EffectLayer.of("heat_haze", EffectKind.DISTORT,
                        EffectParams.builder().scalar("amplitude", 0.5).build()));

        EffectStack merged = base.merge(packOverride);

        assertEquals(2, merged.size(), "overriding one layer must not drop the other");
        assertEquals(0.5,
                merged.byId("heat_haze").orElseThrow().params().scalarOr("amplitude", -1),
                "the override should win on the layer it names");
        assertTrue(merged.byId("ember_grade").isPresent(),
                "the untouched layer should survive verbatim");
    }
}
