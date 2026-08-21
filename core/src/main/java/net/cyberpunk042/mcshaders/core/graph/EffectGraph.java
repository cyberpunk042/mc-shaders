package net.cyberpunk042.mcshaders.core.graph;

import java.util.List;

/**
 * A backend-neutral description of one frame's post-processing.
 *
 * <p>This is the framework's intermediate representation and the contract every
 * backend consumes. It states what the frame should look like; it says nothing
 * about shaders, pipelines, or draw calls. A Vulkan and an OpenGL backend consume
 * the identical graph.
 *
 * @param nodes    passes in execution order
 * @param warnings non-fatal problems found while compiling, for logging and pack debugging
 */
public record EffectGraph(List<GraphNode> nodes, List<String> warnings) {

    private static final EffectGraph EMPTY = new EffectGraph(List.of(), List.of());

    public EffectGraph {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    /** A graph with nothing to draw. The common case: a dimension with no bindings. */
    public static EffectGraph empty() {
        return EMPTY;
    }

    /** True when there is nothing to render and the whole pipeline can be skipped. */
    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int passCount() {
        return nodes.size();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public String toString() {
        return "EffectGraph(" + nodes.size() + " passes, " + warnings.size() + " warnings)";
    }
}
