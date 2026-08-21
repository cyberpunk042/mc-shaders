package net.cyberpunk042.mcshaders.core.graph;

import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.param.EffectParams;

/**
 * One resolved pass in an {@link EffectGraph}.
 *
 * <p>Unlike an {@code EffectLayer}, a node carries no conditions and no open
 * questions: conditions have been evaluated, transitions applied, defaults filled
 * in, and unsupported effects removed. A backend can execute it as-is.
 *
 * @param id     the originating layer id, preserved for debugging and profiling
 * @param kind   what to render
 * @param params fully resolved parameters
 * @param blend  how to composite the result
 * @param weight final strength in {@code [0, 1]}
 * @param order  zero-based execution index within the graph
 */
public record GraphNode(
        String id,
        EffectKind kind,
        EffectParams params,
        BlendMode blend,
        double weight,
        int order) {

    public GraphNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Graph node id must not be blank");
        }
        params = params == null ? EffectParams.empty() : params;
    }
}
