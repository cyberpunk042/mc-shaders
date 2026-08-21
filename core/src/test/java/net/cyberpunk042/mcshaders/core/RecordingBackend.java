package net.cyberpunk042.mcshaders.core;

import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.backend.BackendCapabilities;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.graph.EffectGraph;

/**
 * A test double that records what it was asked to render.
 *
 * <p>Exists so tests can assert on backend interaction with arbitrary declared
 * capabilities, without production code having to open itself up for subclassing.
 */
final class RecordingBackend implements EffectBackend {

    private final BackendCapabilities capabilities;
    private final List<EffectGraph> rendered = new ArrayList<>();
    private boolean closed;

    RecordingBackend(BackendCapabilities capabilities) {
        this.capabilities = capabilities;
    }

    /** A backend that can render every effect kind, with depth and no pass limit. */
    static RecordingBackend capable() {
        return new RecordingBackend(BackendCapabilities.full("test"));
    }

    @Override
    public String id() {
        return "recording";
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
        rendered.add(graph);
    }

    @Override
    public void close() {
        closed = true;
    }

    List<EffectGraph> rendered() {
        return rendered;
    }

    int frameCount() {
        return rendered.size();
    }

    boolean isClosed() {
        return closed;
    }
}
