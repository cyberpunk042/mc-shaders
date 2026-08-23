package net.cyberpunk042.mcshaders.fog;

import java.util.concurrent.atomic.AtomicReference;
import net.cyberpunk042.mcshaders.BuiltinEffects;
import net.cyberpunk042.mcshaders.core.api.Experimental;
import net.cyberpunk042.mcshaders.core.graph.EffectGraph;
import net.cyberpunk042.mcshaders.core.graph.GraphNode;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;

/**
 * Where the resolved fog values wait between being computed and being applied.
 *
 * <p>Two things need to meet that cannot be in the same place. The pipeline resolves
 * a look once per frame, from a render hook. Vanilla's fog values only exist inside
 * {@code FogRenderer#setupFog}, reachable only from a mixin. So one publishes and the
 * other consumes, and this is the handoff.
 *
 * <h2>Why not call the pipeline from the mixin</h2>
 *
 * <p>Because {@code ShaderPipeline#frame} advances the transition. Calling it from
 * the fog hook would tie the pipeline's clock to however many times the game happens
 * to compute fog in a frame — and a transition advanced twice a frame eases at twice
 * the intended speed, which reads as a tuning problem rather than as the structural
 * mistake it is.
 *
 * <h2>Why weight is published rather than applied</h2>
 *
 * <p>A layer easing in has a weight below 1, and honouring it means blending between
 * what the fog would have been and what the layer asks for. But "what it would have
 * been" is only known inside {@code setupFog}, where vanilla has already filled
 * {@code FogData} in. So the blend cannot happen here: this publishes the target and
 * the weight, and the consumer mixes against the value it can see.
 *
 * <p>Dropping the weight instead would make every transition snap, which would look
 * exactly like the easing machinery not working at all.
 *
 * <h2>Threading</h2>
 *
 * <p>Published from a render hook and read from a mixin. Whether those are the same
 * thread is a property of the game, not of this code, so the value is swapped through
 * an {@link AtomicReference} and is immutable once published. A reader sees one
 * frame's values or another's, never half of each.
 */
@Experimental
public final class FogSink {

    private final AtomicReference<Reading> current = new AtomicReference<>(Reading.none());

    /**
     * One frame's worth of fog, as the pipeline resolved it.
     *
     * @param active whether any fog layer was in force; when false the rest is unset
     *               and the consumer should leave vanilla's fog alone
     * @param start  distance at which fog begins
     * @param end    distance at which it reaches full density
     * @param red    fog colour, red channel
     * @param green  fog colour, green channel
     * @param blue   fog colour, blue channel
     * @param alpha  fog colour, alpha channel
     * @param weight how far to blend from vanilla's value toward this one, 0 to 1
     */
    public record Reading(
            boolean active,
            double start,
            double end,
            float red,
            float green,
            float blue,
            float alpha,
            double weight) {

        /** No fog layer in force. */
        public static Reading none() {
            return new Reading(false, 0, 0, 0, 0, 0, 0, 0);
        }

        /**
         * Blends one of vanilla's values toward this reading's.
         *
         * @param vanilla the value the game had already computed
         * @param target  the corresponding value from this reading
         * @return the value to use this frame
         */
        public double blend(double vanilla, double target) {
            return vanilla + (target - vanilla) * weight;
        }
    }

    /**
     * Reads the fog out of a frame's graph and publishes it.
     *
     * <p>Fog nodes are applied in graph order, each replacing the one before rather
     * than being averaged with it. Two fog layers claiming different distances have no
     * obviously correct combination, and inventing one would produce a number no author
     * asked for. Last-in-order winning is at least a rule someone can predict.
     *
     * @param graph the compiled graph for this frame; null or empty publishes no fog
     */
    public void publish(EffectGraph graph) {
        current.set(read(graph));
    }

    /** The most recently published reading. Never null. */
    public Reading current() {
        return current.get();
    }

    /** Forgets the last reading, so a consumer leaves vanilla's fog alone. */
    public void clear() {
        current.set(Reading.none());
    }

    private static Reading read(EffectGraph graph) {
        if (graph == null) {
            return Reading.none();
        }
        // No isEmpty() check: a graph with no nodes, and one whose nodes are all some
        // other effect, are the same case, and the loop below already answers both
        // with none(). An early return for one of them would be a branch no test could
        // distinguish from its absence — which is how dead code passes for a guard.
        Reading found = Reading.none();
        for (GraphNode node : graph.nodes()) {
            if (!BuiltinEffects.FOG.equals(node.type())) {
                continue;
            }
            EffectParams params = node.params();
            ParamValue.Rgba colour = colourOf(params);
            found = new Reading(
                    true,
                    params.scalarOr(BuiltinEffects.START, 0),
                    params.scalarOr(BuiltinEffects.END, 0),
                    colour.r(),
                    colour.g(),
                    colour.b(),
                    colour.a(),
                    clamp(node.weight()));
        }
        return found;
    }

    private static ParamValue.Rgba colourOf(EffectParams params) {
        ParamValue value = params.asMap().get(BuiltinEffects.COLOR);
        if (value instanceof ParamValue.Rgba rgba) {
            return rgba;
        }
        // A fog layer with no colour, or a colour of some other shape, still has
        // distances worth applying. White leaves the tint alone rather than dropping
        // the whole layer over one bad field.
        return new ParamValue.Rgba(1, 1, 1, 1);
    }

    private static double clamp(double weight) {
        if (weight < 0) {
            return 0;
        }
        return weight > 1 ? 1 : weight;
    }
}
