package net.cyberpunk042.mcshaders.core.graph;

import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.backend.BackendCapabilities;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;

/**
 * Turns an effect stack into an executable {@link EffectGraph} for a given backend.
 *
 * <p>Compilation is where every "can this actually be drawn?" question is settled,
 * so that the render path stays a straight walk with no branching on capabilities.
 * Concretely it:
 *
 * <ol>
 *   <li>orders layers by priority,</li>
 *   <li>drops layers that contribute nothing,</li>
 *   <li>drops layers the backend cannot render, recording a warning,</li>
 *   <li>enforces the backend's pass limit,</li>
 *   <li>flags stacks whose accumulating blends are likely a pack mistake.</li>
 * </ol>
 *
 * <p>Every rejection is a warning rather than an exception. A malformed pack should
 * cost the player some effects, never their session.
 */
public final class EffectCompiler {

    /**
     * Number of accumulating layers ({@code ADD}/{@code SCREEN}) beyond which a
     * stack is flagged. Each one compounds brightness, so past a handful the frame
     * is almost certainly blowing out to white — a pack authoring error, not intent.
     */
    public static final int ACCUMULATION_WARN_THRESHOLD = 4;

    private final BackendCapabilities capabilities;

    public EffectCompiler(BackendCapabilities capabilities) {
        if (capabilities == null) {
            throw new IllegalArgumentException("EffectCompiler requires backend capabilities");
        }
        this.capabilities = capabilities;
    }

    public BackendCapabilities capabilities() {
        return capabilities;
    }

    /** Compiles {@code stack} into a graph the configured backend can execute. */
    public EffectGraph compile(EffectStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EffectGraph.empty();
        }

        List<GraphNode> nodes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int accumulating = 0;

        for (EffectLayer layer : stack.inRenderOrder()) {
            if (layer.isNoOp()) {
                // Silent: zero weight is a normal way to disable a layer, not a fault.
                continue;
            }

            if (!capabilities.supports(layer.kind())) {
                warnings.add(describeUnsupported(layer));
                continue;
            }

            if (capabilities.hasPassLimit() && nodes.size() >= capabilities.maxPasses()) {
                warnings.add("Pass limit of " + capabilities.maxPasses()
                        + " reached on backend '" + capabilities.api()
                        + "'; dropped layer '" + layer.id() + "' and any after it");
                break;
            }

            if (layer.blend().accumulates()) {
                accumulating++;
            }

            nodes.add(new GraphNode(
                    layer.id(),
                    layer.kind(),
                    layer.params(),
                    layer.blend(),
                    layer.weight(),
                    nodes.size()));
        }

        if (accumulating > ACCUMULATION_WARN_THRESHOLD) {
            warnings.add(accumulating + " accumulating layers (ADD/SCREEN) in one stack; "
                    + "the frame will likely blow out to white. Consider ALPHA blending "
                    + "or lower per-layer weights.");
        }

        return new EffectGraph(nodes, warnings);
    }

    private String describeUnsupported(EffectLayer layer) {
        boolean depthProblem = layer.kind().requiresDepth() && !capabilities.depthAvailable();
        String reason = depthProblem
                ? "it needs the depth buffer, which this backend does not expose"
                : "this backend does not implement that effect kind";
        return "Skipped layer '" + layer.id() + "' (" + layer.kind() + ") on backend '"
                + capabilities.api() + "': " + reason;
    }
}
