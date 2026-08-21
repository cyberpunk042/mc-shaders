package net.cyberpunk042.mcshaders.core.backend;

import java.util.Set;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;

/**
 * What a rendering backend can actually do.
 *
 * <p>Declared up front so the compiler can drop unsupported effects while building
 * the graph, rather than having the backend fail partway through a frame.
 *
 * @param api            human-readable backend name, e.g. {@code "OpenGL 3.2"} or {@code "Vulkan"}
 * @param supportedKinds effect kinds this backend can render
 * @param depthAvailable whether the depth buffer can be sampled
 * @param maxPasses      maximum full-screen passes per frame; {@code <= 0} means unbounded
 */
public record BackendCapabilities(
        String api,
        Set<EffectKind> supportedKinds,
        boolean depthAvailable,
        int maxPasses) {

    public BackendCapabilities {
        if (api == null || api.isBlank()) {
            throw new IllegalArgumentException("Backend must declare an api name");
        }
        supportedKinds = supportedKinds == null ? Set.of() : Set.copyOf(supportedKinds);
    }

    /** Capabilities for a backend that supports everything without limits. */
    public static BackendCapabilities full(String api) {
        return new BackendCapabilities(api, Set.of(EffectKind.values()), true, 0);
    }

    /** Capabilities for a backend that renders nothing. */
    public static BackendCapabilities none(String api) {
        return new BackendCapabilities(api, Set.of(), false, 0);
    }

    /**
     * Whether an effect kind can be rendered.
     *
     * <p>A depth-reading kind is unsupported when depth is unavailable even if the
     * kind itself is listed — rendering fog without depth produces a flat wash, not
     * fog, and shipping that silently would be worse than skipping it.
     */
    public boolean supports(EffectKind kind) {
        if (!supportedKinds.contains(kind)) {
            return false;
        }
        return !kind.requiresDepth() || depthAvailable;
    }

    public boolean hasPassLimit() {
        return maxPasses > 0;
    }
}
