package net.cyberpunk042.mcshaders.fog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cyberpunk042.mcshaders.BuiltinEffects;
import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.graph.EffectGraph;
import net.cyberpunk042.mcshaders.core.graph.GraphNode;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What the fog mixin will read, tested without a game.
 *
 * <p>The mixin itself cannot be tested here — Mixin resolves its target at runtime, so
 * a compiling mixin proves nothing. What can be pinned is everything upstream of it:
 * which node wins, what a partly-eased layer publishes, and what happens on the frames
 * where there is no fog at all. Those are where a wrong answer would look like a
 * rendering problem rather than a logic one.
 */
class FogSinkTest {

    private static GraphNode fog(String id, double start, double end, double weight, int order) {
        return new GraphNode(
                id,
                BuiltinEffects.FOG,
                EffectKind.FOG,
                EffectParams.builder()
                        .scalar(BuiltinEffects.START, start)
                        .scalar(BuiltinEffects.END, end)
                        .color(BuiltinEffects.COLOR, 0.1f, 0.2f, 0.3f, 1.0f)
                        .build(),
                BlendMode.ALPHA,
                weight,
                order);
    }

    private static GraphNode notFog(String id) {
        return new GraphNode(
                id,
                "example:bloom",
                EffectKind.BLOOM,
                EffectParams.builder().scalar("radius", 4).build(),
                BlendMode.ADD,
                1.0,
                0);
    }

    private static EffectGraph graphOf(GraphNode... nodes) {
        return new EffectGraph(List.of(nodes), List.of());
    }

    @Nested
    @DisplayName("reading a frame")
    class Reading {

        @Test
        @DisplayName("publishes the fog layer's values")
        void publishesFog() {
            FogSink sink = new FogSink();

            sink.publish(graphOf(fog("depth", 1, 24, 1.0, 0)));

            FogSink.Reading reading = sink.current();
            assertTrue(reading.active());
            assertEquals(1, reading.start());
            assertEquals(24, reading.end());
            assertEquals(0.1f, reading.red(), 1e-6);
            assertEquals(1.0, reading.weight());
        }

        @Test
        @DisplayName("ignores layers that are not fog")
        void ignoresOtherEffects() {
            FogSink sink = new FogSink();

            sink.publish(graphOf(notFog("glow")));

            assertFalse(sink.current().active(),
                    "a bloom layer must not be read as fog with zero distances");
        }

        @Test
        @DisplayName("finds the fog among other layers")
        void findsFogAmongOthers() {
            FogSink sink = new FogSink();

            sink.publish(graphOf(notFog("glow"), fog("depth", 2, 30, 1.0, 1)));

            assertTrue(sink.current().active());
            assertEquals(30, sink.current().end());
        }

        @Test
        @DisplayName("the last fog layer in the graph wins")
        void lastOneWins() {
            FogSink sink = new FogSink();

            sink.publish(graphOf(fog("base", 0, 192, 1.0, 0), fog("depth", 1, 24, 1.0, 1)));

            assertEquals(24, sink.current().end(),
                    "averaging two fog distances would produce a number nobody asked for");
        }
    }

    @Nested
    @DisplayName("frames with no fog")
    class NoFog {

        @Test
        @DisplayName("an empty graph publishes nothing")
        void emptyGraph() {
            FogSink sink = new FogSink();

            sink.publish(EffectGraph.empty());

            assertFalse(sink.current().active());
        }

        @Test
        @DisplayName("a null graph publishes nothing rather than throwing")
        void nullGraph() {
            FogSink sink = new FogSink();

            sink.publish(null);

            assertFalse(sink.current().active());
        }

        @Test
        @DisplayName("starts inactive, before any frame has run")
        void startsInactive() {
            assertFalse(new FogSink().current().active(),
                    "a mixin running before the first frame must leave vanilla alone");
        }

        @Test
        @DisplayName("a fogless frame clears the previous one")
        void foglessFrameClearsTheLast() {
            FogSink sink = new FogSink();
            sink.publish(graphOf(fog("depth", 1, 24, 1.0, 0)));

            sink.publish(EffectGraph.empty());

            assertFalse(sink.current().active(),
                    "leaving the last frame's fog up is how a dimension's look follows "
                            + "the player out of it");
        }

        @Test
        @DisplayName("clear() forgets the reading")
        void clearForgets() {
            FogSink sink = new FogSink();
            sink.publish(graphOf(fog("depth", 1, 24, 1.0, 0)));

            sink.clear();

            assertFalse(sink.current().active());
        }
    }

    @Nested
    @DisplayName("a layer part-way through easing in")
    class Weight {

        @Test
        @DisplayName("publishes the weight rather than pre-applying it")
        void publishesWeight() {
            FogSink sink = new FogSink();

            sink.publish(graphOf(fog("depth", 1, 24, 0.25, 0)));

            FogSink.Reading reading = sink.current();
            assertEquals(0.25, reading.weight());
            assertEquals(24, reading.end(),
                    "the target is published whole; the consumer blends toward it");
        }

        @Test
        @DisplayName("blends from vanilla's value toward the target")
        void blendsTowardTarget() {
            FogSink sink = new FogSink();
            sink.publish(graphOf(fog("depth", 1, 24, 0.25, 0)));
            FogSink.Reading reading = sink.current();

            // Vanilla would have used 64; the layer wants 24 and is a quarter in.
            assertEquals(54, reading.blend(64, reading.end()), 1e-9);
        }

        @Test
        @DisplayName("a fully eased layer reaches its target exactly")
        void fullWeightReachesTarget() {
            FogSink sink = new FogSink();
            sink.publish(graphOf(fog("depth", 1, 24, 1.0, 0)));
            FogSink.Reading reading = sink.current();

            assertEquals(24, reading.blend(64, reading.end()), 1e-9);
        }

        @Test
        @DisplayName("a layer at zero weight leaves vanilla's value untouched")
        void zeroWeightChangesNothing() {
            FogSink sink = new FogSink();
            sink.publish(graphOf(fog("depth", 1, 24, 0.0, 0)));
            FogSink.Reading reading = sink.current();

            assertEquals(64, reading.blend(64, reading.end()), 1e-9,
                    "the first frame of a transition must be indistinguishable from none");
        }

        @Test
        @DisplayName("a weight outside 0..1 is clamped rather than overshooting")
        void clampsWeight() {
            FogSink over = new FogSink();
            over.publish(graphOf(fog("depth", 1, 24, 4.0, 0)));
            assertEquals(1.0, over.current().weight());

            FogSink under = new FogSink();
            under.publish(graphOf(fog("depth", 1, 24, -2.0, 0)));
            assertEquals(0.0, under.current().weight());
        }
    }

    @Nested
    @DisplayName("a fog layer missing a field")
    class Incomplete {

        @Test
        @DisplayName("keeps the distances when the colour is absent")
        void missingColour() {
            FogSink sink = new FogSink();
            GraphNode noColour = new GraphNode(
                    "depth",
                    BuiltinEffects.FOG,
                    EffectKind.FOG,
                    EffectParams.builder()
                            .scalar(BuiltinEffects.START, 1)
                            .scalar(BuiltinEffects.END, 24)
                            .build(),
                    BlendMode.ALPHA,
                    1.0,
                    0);

            sink.publish(graphOf(noColour));

            FogSink.Reading reading = sink.current();
            assertTrue(reading.active(), "one absent field should not drop the whole layer");
            assertEquals(24, reading.end());
            assertEquals(1.0f, reading.red(), 1e-6, "white leaves the tint alone");
            assertEquals(1.0f, reading.alpha(), 1e-6);
        }
    }
}
