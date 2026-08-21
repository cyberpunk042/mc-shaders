package net.cyberpunk042.mcshaders.core.graph;

import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.backend.BackendCapabilities;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectRegistry;
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
 *   <li>fills in defaults from any registered effect definition,</li>
 *   <li>drops layers naming an unregistered type, recording a warning,</li>
 *   <li>drops layers the backend cannot render, recording a warning,</li>
 *   <li>enforces the backend's pass limit,</li>
 *   <li>flags stacks whose accumulating blends are likely a pack mistake.</li>
 * </ol>
 *
 * <p>Every rejection is a warning rather than an exception. A malformed pack, or a
 * missing optional dependency whose effects a pack still references, should cost
 * the player some visuals — never their session.
 */
@Stable(since = "0.1.0")
public final class EffectCompiler {

    /**
     * Number of accumulating layers ({@code ADD}/{@code SCREEN}) beyond which a
     * stack is flagged. Each one compounds brightness, so past a handful the frame
     * is almost certainly blowing out to white — a pack authoring error, not intent.
     */
    public static final int ACCUMULATION_WARN_THRESHOLD = 4;

    private final BackendCapabilities capabilities;
    private final EffectRegistry effects;

    /** Compiles against {@code capabilities} with no third-party effect types. */
    public EffectCompiler(BackendCapabilities capabilities) {
        this(capabilities, new EffectRegistry());
    }

    public EffectCompiler(BackendCapabilities capabilities, EffectRegistry effects) {
        if (capabilities == null) {
            throw new IllegalArgumentException("EffectCompiler requires backend capabilities");
        }
        this.capabilities = capabilities;
        this.effects = effects == null ? new EffectRegistry() : effects;
    }

    public BackendCapabilities capabilities() {
        return capabilities;
    }

    public EffectRegistry effects() {
        return effects;
    }

    /** Compiles {@code stack} into a graph the configured backend can execute. */
    public EffectGraph compile(EffectStack stack) {
        if (stack == null || stack.isEmpty()) {
            return EffectGraph.empty();
        }

        List<GraphNode> nodes = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int accumulating = 0;

        for (EffectLayer declared : stack.inRenderOrder()) {
            if (declared.isNoOp()) {
                // Silent: zero weight is a normal way to disable a layer, not a fault.
                continue;
            }

            if (declared.hasDefinitionType() && effects.byType(declared.type()).isEmpty()) {
                warnings.add("Skipped layer '" + declared.id() + "': effect type '" + declared.type()
                        + "' is not registered. The mod providing it may be absent.");
                continue;
            }

            EffectLayer layer = effects.applyDefaults(declared);

            if (!capabilities.supports(layer)) {
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
                    layer.type(),
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
        if (layer.kind().requiresDepth() && !capabilities.depthAvailable()) {
            return "Skipped layer '" + layer.id() + "' (" + layer.kind() + ") on backend '"
                    + capabilities.api() + "': it needs the depth buffer, which this backend "
                    + "does not expose";
        }
        if (layer.hasDefinitionType()) {
            return "Skipped layer '" + layer.id() + "': backend '" + capabilities.api()
                    + "' does not implement effect type '" + layer.type() + "'";
        }
        return "Skipped layer '" + layer.id() + "' (" + layer.kind() + ") on backend '"
                + capabilities.api() + "': this backend does not implement that effect kind";
    }
}
