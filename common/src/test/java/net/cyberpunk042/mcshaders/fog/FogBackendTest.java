package net.cyberpunk042.mcshaders.fog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cyberpunk042.mcshaders.BuiltinEffects;
import net.cyberpunk042.mcshaders.core.backend.BackendFactory;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.backend.NoOpBackend;
import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.graph.EffectGraph;
import net.cyberpunk042.mcshaders.core.graph.GraphNode;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The first backend that is not a placeholder.
 *
 * <p>What matters here is less that it works — it forwards one call — than that it is
 * honest about itself. A backend that over-claims its capabilities gets handed nodes
 * it cannot render and drops them silently, and an effect that vanishes with no
 * message is worse than one that was never accepted.
 */
class FogBackendTest {

    private static final EffectBackend.FrameContext FRAME =
            new EffectBackend.FrameContext(1920, 1080, 0.5f, 100);

    private static EffectGraph fogGraph(double end) {
        GraphNode node = new GraphNode(
                "depth",
                BuiltinEffects.FOG,
                EffectKind.FOG,
                EffectParams.builder()
                        .scalar(BuiltinEffects.START, 1)
                        .scalar(BuiltinEffects.END, end)
                        .build(),
                BlendMode.ALPHA,
                1.0,
                0);
        return new EffectGraph(List.of(node), List.of());
    }

    @Nested
    @DisplayName("rendering")
    class Rendering {

        @Test
        @DisplayName("a rendered frame reaches the sink")
        void renderPublishes() {
            FogSink sink = new FogSink();
            FogBackend backend = new FogBackend(sink);

            backend.render(fogGraph(24), FRAME);

            assertTrue(sink.current().active());
            assertEquals(24, sink.current().end());
        }

        @Test
        @DisplayName("closing clears what was published")
        void closeClears() {
            FogSink sink = new FogSink();
            FogBackend backend = new FogBackend(sink);
            backend.render(fogGraph(24), FRAME);

            backend.close();

            assertFalse(sink.current().active(),
                    "fog left published after shutdown would be applied by a mixin "
                            + "against a pipeline that no longer exists");
        }

        @Test
        @DisplayName("closing twice is harmless, as the interface requires")
        void closeIsIdempotent() {
            FogBackend backend = new FogBackend(new FogSink());

            backend.close();
            backend.close();
        }

        @Test
        @DisplayName("initialises, having nothing to allocate")
        void initialises() {
            assertTrue(new FogBackend(new FogSink()).initialise());
        }

        @Test
        @DisplayName("refuses to be built without a sink")
        void refusesNullSink() {
            assertThrows(IllegalArgumentException.class, () -> new FogBackend(null));
        }
    }

    @Nested
    @DisplayName("what it claims")
    class Capabilities {

        @Test
        @DisplayName("supports fog and nothing else")
        void supportsOnlyFog() {
            var caps = new FogBackend(new FogSink()).capabilities();

            assertEquals(java.util.Set.of(EffectKind.FOG), caps.supportedKinds(),
                    "claiming a kind it cannot render means nodes dropped in silence");
            assertEquals(java.util.Set.of(BuiltinEffects.FOG), caps.supportedTypes());
        }

        @Test
        @DisplayName("claims no depth buffer and a single pass")
        void claimsNoFramebuffer() {
            var caps = new FogBackend(new FogSink()).capabilities();

            assertFalse(caps.depthAvailable(), "there is no framebuffer here");
            assertEquals(1, caps.maxPasses());
        }
    }

    @Nested
    @DisplayName("selection")
    class Selection {

        @Test
        @DisplayName("is chosen over doing nothing")
        void beatsNoOp() {
            assertTrue(FogBackend.PRIORITY > Integer.MIN_VALUE);
            assertFalse(FogBackend.ID.equals(NoOpBackend.ID));
        }

        @Test
        @DisplayName("loses to a backend that renders more than one effect")
        void losesToARealRenderer() {
            assertTrue(FogBackend.PRIORITY < BackendFactory.DEFAULT_PRIORITY,
                    "a real renderer contributed later must win without anyone "
                            + "having to remove this one");
        }

        @Test
        @DisplayName("the factory produces a backend bound to the same sink")
        void factoryBindsTheSink() {
            FogSink sink = new FogSink();

            EffectBackend made = new FogBackend.Factory(sink).create();
            made.render(fogGraph(30), FRAME);

            assertEquals(30, sink.current().end(),
                    "a factory handing out a backend wired to some other sink would "
                            + "publish where nothing reads");
        }

        @Test
        @DisplayName("the factory agrees with the backend on id and priority")
        void factoryAgrees() {
            BackendFactory factory = new FogBackend.Factory(new FogSink());

            assertEquals(FogBackend.ID, factory.id());
            assertEquals(FogBackend.PRIORITY, factory.priority());
            assertEquals(FogBackend.ID, factory.create().id());
        }
    }
}
