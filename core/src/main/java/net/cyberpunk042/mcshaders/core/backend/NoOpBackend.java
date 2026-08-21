package net.cyberpunk042.mcshaders.core.backend;

import net.cyberpunk042.mcshaders.core.graph.EffectGraph;

/**
 * A backend that renders nothing.
 *
 * <p>Used on dedicated servers, in tests, and as the fallback when no graphics
 * backend initialises. Having a real object here rather than a null keeps every
 * caller free of null checks, and makes "effects disabled" a normal state rather
 * than an error path.
 */
public final class NoOpBackend implements EffectBackend {

    public static final String ID = "noop";

    private final BackendCapabilities capabilities = BackendCapabilities.none(ID);
    private int framesSeen;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public BackendCapabilities capabilities() {
        return capabilities;
    }

    @Override
    public boolean initialise() {
        return true;
    }

    @Override
    public void render(EffectGraph graph, FrameContext frame) {
        // Counting frames makes this useful as a test double for "was rendering driven?"
        framesSeen++;
    }

    /** Number of frames submitted so far. */
    public int framesSeen() {
        return framesSeen;
    }

    @Override
    public void close() {
        framesSeen = 0;
    }
}
