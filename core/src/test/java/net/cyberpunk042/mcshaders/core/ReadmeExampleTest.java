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
 * Compiles and exercises a core-level pipeline example: a stack, a binding, a frame.
 *
 * <p><strong>It does not guard README.md, and its previous claim to was false.</strong>
 * The wording said this "fails the build if the README drifts from the real surface".
 * It cannot: nothing here opens the page. The drift it was written to catch has since
 * happened unnoticed — README's one Java block now shows four
 * {@code McShadersAPI.register*} calls, and not one line of the example below appears
 * in it, or in any other page.
 *
 * <p>Core cannot fix that by pointing at the README instead, which is the useful part
 * of the finding rather than an excuse: {@code McShadersAPI} lives in {@code common},
 * so the surface the README actually advertises is not reachable from this module. A
 * test that guards the README's Java block has to live in {@code common} — and
 * {@code common}'s own {@code ReadmeExampleTest} already reads the page, for the
 * binding JSON rather than the Java.
 *
 * <p>What remains here is worth keeping on its own terms — it is the one place the
 * stack, the registry and the pipeline are driven end to end in core — so it is
 * described honestly rather than deleted or renamed. Two ways to make the old name
 * true again, both the operator's call: put this example back on the README, or move
 * the front-page guarantee to {@code common} where the advertised calls live. Neither
 * is a correction, so neither is taken here.
 *
 * @see net.cyberpunk042.mcshaders.core.LayerGeometryTest.Guide for the pattern a real
 *      doc-drift check follows in this module
 */
class ReadmeExampleTest {

    @Test
    @DisplayName("the stack, the binding and the pipeline drive one frame end to end")
    void readmeExampleWorks() {
        // ── begin core pipeline example ───────────────────────────────────────
        EffectStack netherHeat = EffectStack.of(
                EffectLayer.of("heat_haze", EffectKind.DISTORT,
                        EffectParams.builder().scalar("amplitude", 0.02).build()),
                EffectLayer.of("ember_grade", EffectKind.COLOR_GRADE,
                        EffectParams.builder().color("tint", 1.0f, 0.6f, 0.4f, 1.0f).build()));

        BindingRegistry registry = BindingRegistry.of(
                DimensionBinding.of("nether_base", DimensionId.minecraft("the_nether"), netherHeat));
        // ── end core pipeline example ─────────────────────────────────────────

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

    /**
     * The README's capability table lists "layers, params, conditions, priority merge,
     * easing" as <em>Tested</em>. It does not spell the semantics out, so this is the
     * evidence for that one word rather than a transcription of prose — which is why it
     * survives the correction above unchanged.
     */
    @Test
    @DisplayName("priority merge: the override wins its layer and the others survive")
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
