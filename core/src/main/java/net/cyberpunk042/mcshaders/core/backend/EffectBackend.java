package net.cyberpunk042.mcshaders.core.backend;

import net.cyberpunk042.mcshaders.core.graph.EffectGraph;

/**
 * The seam between the framework and the graphics API.
 *
 * <p>Everything above this interface is backend-neutral data: an {@link EffectGraph}
 * describes what the frame should look like, never how to draw it. Implementations
 * translate that into draw calls.
 *
 * <p>This exists because Minecraft 26.2 ships an experimental Vulkan renderer
 * alongside OpenGL, with OpenGL slated for eventual removal. A GLSL-first design
 * would have to be rewritten when that lands; behind this interface, a Vulkan
 * implementation is an addition rather than a migration, and both can ship at once
 * while the game's default backend is in flux.
 *
 * <p>Implementations are used from the render thread only and need not be
 * thread-safe.
 */
public interface EffectBackend extends AutoCloseable {

    /** A stable identifier for this backend, e.g. {@code "opengl"}. */
    String id();

    /** What this backend can render. Must not change after {@link #initialise}. */
    BackendCapabilities capabilities();

    /**
     * Prepares GPU resources.
     *
     * @return {@code true} if the backend is usable; {@code false} if it should be
     *         skipped in favour of another. Returning false must leave nothing
     *         allocated — a failed probe is expected during backend selection, not
     *         exceptional.
     */
    boolean initialise();

    /**
     * Renders one frame's worth of effects.
     *
     * <p>The graph is already filtered to supported kinds and ordered for execution,
     * so an implementation can walk it directly without re-validating.
     */
    void render(EffectGraph graph, FrameContext frame);

    /** Releases GPU resources. Must tolerate being called more than once. */
    @Override
    void close();

    /**
     * Per-frame values a backend needs that are not part of the effect description.
     *
     * @param width         framebuffer width in pixels
     * @param height        framebuffer height in pixels
     * @param partialTick   sub-tick interpolation factor in {@code [0, 1]}
     * @param elapsedTicks  monotonically increasing time, for animated effects
     */
    record FrameContext(int width, int height, float partialTick, double elapsedTicks) {
        public FrameContext {
            if (width <= 0 || height <= 0) {
                throw new IllegalArgumentException(
                        "Frame dimensions must be positive, got " + width + "x" + height);
            }
        }

        public float aspectRatio() {
            return (float) width / (float) height;
        }
    }
}
