package net.cyberpunk042.mcshaders.fog;

import java.util.Set;
import net.cyberpunk042.mcshaders.BuiltinEffects;
import net.cyberpunk042.mcshaders.core.api.Experimental;
import net.cyberpunk042.mcshaders.core.backend.BackendCapabilities;
import net.cyberpunk042.mcshaders.core.backend.BackendFactory;
import net.cyberpunk042.mcshaders.core.backend.EffectBackend;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.graph.EffectGraph;

/**
 * The first backend that does something.
 *
 * <p>It draws nothing, which is not a shortcut — it is what rendering fog on this
 * platform is. Vanilla owns the fog; a mod's part is to change the numbers vanilla is
 * about to use. So this backend's whole job is to take the fog out of the frame's
 * graph and put it somewhere the mixin inside {@code FogRenderer#setupFog} can reach
 * it. That is {@link FogSink}.
 *
 * <h2>Why a backend rather than a special case</h2>
 *
 * <p>Because the alternative is the framework's own renderer reaching past the
 * interface every third party has to use. The roadmap names that explicitly: if the
 * built-in renderer needs privileged access, that is a gap in the public API, not a
 * licence to skip it. Fog going through {@link BackendFactory} like anything else is
 * the first evidence that the extension point is actually usable.
 *
 * <h2>What it claims to support</h2>
 *
 * <p>{@link EffectKind#FOG} and the single type {@code mcshaders:fog}. Not
 * {@code full()} — claiming kinds it cannot render would let the compiler hand it
 * bloom and distortion nodes it would silently drop, and an effect that vanishes with
 * no message is worse than one that was never accepted.
 *
 * <p>{@code depthAvailable} is false and {@code maxPasses} is 1 for the same reason:
 * there is no framebuffer here and no pass to run.
 *
 * <h2>Priority</h2>
 *
 * <p>Above {@code NoOpBackend} so it is chosen, below the default so a real renderer
 * contributed later wins without anyone having to remove this. When that renderer
 * arrives it will need to keep feeding the sink, because fog will still be vanilla's
 * to draw.
 */
@Experimental
public final class FogBackend implements EffectBackend {

    /** Stable identifier of this backend. */
    public static final String ID = "fog";

    /**
     * Below {@link BackendFactory#DEFAULT_PRIORITY}, above nothing.
     *
     * <p>A backend that renders one effect should lose to one that renders many. This
     * is a floor, not a claim to be the right answer.
     */
    public static final int PRIORITY = BackendFactory.DEFAULT_PRIORITY - 100;

    private final FogSink sink;

    public FogBackend(FogSink sink) {
        if (sink == null) {
            throw new IllegalArgumentException("FogBackend needs a sink to publish to");
        }
        this.sink = sink;
    }

    /** Where this backend publishes. */
    public FogSink sink() {
        return sink;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public BackendCapabilities capabilities() {
        return new BackendCapabilities(
                "vanilla-fog", Set.of(EffectKind.FOG), Set.of(BuiltinEffects.FOG), false, 1);
    }

    /**
     * Always succeeds.
     *
     * <p>There is nothing to allocate and nothing to probe — publishing numbers into a
     * field cannot fail on some drivers and not others. A backend that always
     * initialises is the correct shape for one that owns no resources.
     */
    @Override
    public boolean initialise() {
        return true;
    }

    @Override
    public void render(EffectGraph graph, FrameContext frame) {
        sink.publish(graph);
    }

    /**
     * Clears the sink.
     *
     * <p>Leaving the last frame's fog published after shutdown would let a mixin still
     * running apply values from a pipeline that no longer exists. Idempotent, as the
     * interface requires.
     */
    @Override
    public void close() {
        sink.clear();
    }

    /** Contributes {@link FogBackend} through the ordinary registration path. */
    public static final class Factory implements BackendFactory {

        private final FogSink sink;

        public Factory(FogSink sink) {
            this.sink = sink;
        }

        @Override
        public String id() {
            return ID;
        }

        @Override
        public int priority() {
            return PRIORITY;
        }

        @Override
        public EffectBackend create() {
            return new FogBackend(sink);
        }
    }
}
