package net.cyberpunk042.mcshaders.core;

import java.util.Objects;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.EffectRegistry;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.graph.EffectCompiler;
import net.cyberpunk042.mcshaders.core.graph.EffectGraph;
import net.cyberpunk042.mcshaders.core.transition.Easing;
import net.cyberpunk042.mcshaders.core.transition.Transition;

/**
 * The framework's per-frame entry point, and the only stateful object in the core.
 *
 * <p>It owns the current transition and drives the cycle each frame:
 * resolve bindings for the world state, start a transition if the result changed,
 * advance it, compile the blend, and hand the graph to a backend.
 *
 * <p>The Minecraft layer's job reduces to sampling a {@link WorldState} and calling
 * {@link #frame}. Everything above the backend interface stays free of Minecraft
 * and of any graphics API, which is what makes this testable headlessly.
 *
 * <p>Not thread-safe: intended to be owned and called by the render thread.
 */
@Stable(since = "0.1.0")
public final class ShaderPipeline {

    /** Default cross-dimension blend length, in ticks (1.5s at 20 TPS). */
    public static final double DEFAULT_TRANSITION_TICKS = 30.0;

    private final EffectBackend backend;
    private final EffectCompiler compiler;

    private BindingRegistry registry;
    private double transitionTicks = DEFAULT_TRANSITION_TICKS;
    private Easing easing = Easing.SMOOTH;

    private Transition transition = Transition.settled(EffectStack.empty());
    private EffectStack lastResolved = EffectStack.empty();
    private EffectGraph lastGraph = EffectGraph.empty();

    public ShaderPipeline(EffectBackend backend, BindingRegistry registry) {
        this(backend, registry, new EffectRegistry());
    }

    /**
     * @param effects third-party effect definitions; should be frozen before the
     *                first frame so the render path reads a stable set
     */
    public ShaderPipeline(EffectBackend backend, BindingRegistry registry, EffectRegistry effects) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.registry = registry == null ? BindingRegistry.empty() : registry;
        this.compiler = new EffectCompiler(backend.capabilities(), effects);
    }

    public EffectBackend backend() {
        return backend;
    }

    public BindingRegistry registry() {
        return registry;
    }

    /**
     * Swaps in a new binding set, as happens on a datapack reload.
     *
     * <p>The current transition is left running: the next frame resolves against
     * the new registry and blends toward it, so a reload eases in rather than
     * snapping.
     */
    public void setRegistry(BindingRegistry newRegistry) {
        this.registry = newRegistry == null ? BindingRegistry.empty() : newRegistry;
    }

    public void setTransitionTicks(double ticks) {
        this.transitionTicks = Math.max(0.0, ticks);
    }

    public void setEasing(Easing newEasing) {
        this.easing = newEasing == null ? Easing.SMOOTH : newEasing;
    }

    /** The stack currently on screen, mid-blend included. */
    public EffectStack currentStack() {
        return transition.current();
    }

    /** The graph produced by the most recent {@link #frame} call. */
    public EffectGraph lastGraph() {
        return lastGraph;
    }

    public Transition transition() {
        return transition;
    }

    /**
     * Advances one frame and renders.
     *
     * @param state      the world as seen this frame
     * @param deltaTicks time since the previous frame, in ticks
     * @param frame      framebuffer and timing context for the backend
     * @return the compiled graph, exposed for logging, profiling and tests
     */
    public EffectGraph frame(WorldState state, double deltaTicks, EffectBackend.FrameContext frame) {
        EffectStack resolved = registry.resolve(state);

        // Only retarget when the resolution actually changed. Recomputing every frame
        // would restart the blend continuously and freeze it at t=0.
        if (!resolved.equals(lastResolved)) {
            transition = transition.retarget(resolved, transitionTicks, easing);
            lastResolved = resolved;
        }

        transition = transition.advance(deltaTicks);

        EffectGraph graph = compiler.compile(transition.current());
        lastGraph = graph;

        if (!graph.isEmpty()) {
            backend.render(graph, frame);
        }
        return graph;
    }

    /**
     * Jumps straight to the resolution for {@code state} with no blend.
     *
     * <p>For world join and respawn, where there is no previous frame to blend from
     * and easing in would read as a bug rather than an effect.
     */
    public void snapTo(WorldState state) {
        lastResolved = registry.resolve(state);
        transition = Transition.settled(lastResolved);
        lastGraph = compiler.compile(lastResolved);
    }
}
