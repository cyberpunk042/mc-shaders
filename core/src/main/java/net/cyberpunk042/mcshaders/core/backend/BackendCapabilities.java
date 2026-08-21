package net.cyberpunk042.mcshaders.core.backend;

import java.util.Set;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;

/**
 * What a rendering backend can actually do.
 *
 * <p>Declared up front so the compiler can drop unsupported effects while building
 * the graph, rather than having the backend fail partway through a frame.
 *
 * @param api            human-readable backend name, e.g. {@code "OpenGL 3.2"} or {@code "Vulkan"}
 * @param supportedKinds built-in effect kinds this backend can render
 * @param supportedTypes namespaced {@link net.cyberpunk042.mcshaders.core.effect.EffectDefinition}
 *                       ids this backend understands, for third-party effects
 * @param depthAvailable whether the depth buffer can be sampled
 * @param maxPasses      maximum full-screen passes per frame; {@code <= 0} means unbounded
 */
@Stable(since = "0.1.0")
public record BackendCapabilities(
        String api,
        Set<EffectKind> supportedKinds,
        Set<String> supportedTypes,
        boolean depthAvailable,
        int maxPasses) {

    public BackendCapabilities {
        if (api == null || api.isBlank()) {
            throw new IllegalArgumentException("Backend must declare an api name");
        }
        supportedKinds = supportedKinds == null ? Set.of() : Set.copyOf(supportedKinds);
        supportedTypes = supportedTypes == null ? Set.of() : Set.copyOf(supportedTypes);
    }

    /** Capabilities for a backend supporting the given kinds and no custom types. */
    public BackendCapabilities(String api, Set<EffectKind> supportedKinds, boolean depthAvailable, int maxPasses) {
        this(api, supportedKinds, Set.of(), depthAvailable, maxPasses);
    }

    /** Every built-in kind, no custom types, no limits. */
    public static BackendCapabilities full(String api) {
        return new BackendCapabilities(api, Set.of(EffectKind.values()), Set.of(), true, 0);
    }

    /** Renders nothing. */
    public static BackendCapabilities none(String api) {
        return new BackendCapabilities(api, Set.of(), Set.of(), false, 0);
    }

    /** Returns a copy that also understands the given custom effect types. */
    public BackendCapabilities withTypes(Set<String> types) {
        return new BackendCapabilities(api, supportedKinds, types, depthAvailable, maxPasses);
    }

    /**
     * Whether a built-in effect kind can be rendered.
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

    /** Whether a specific third-party effect type is understood. */
    public boolean supportsType(String type) {
        return type != null && supportedTypes.contains(type);
    }

    /**
     * Whether a layer can be rendered.
     *
     * <p>A layer naming a definition type must have that exact type supported.
     * Claiming {@link EffectKind#CUSTOM} in general is not enough — the whole point
     * of a custom effect is that it means something specific this backend either
     * implements or does not.
     */
    public boolean supports(EffectLayer layer) {
        if (layer.hasDefinitionType()) {
            return supportsType(layer.type()) && (!layer.kind().requiresDepth() || depthAvailable);
        }
        return supports(layer.kind());
    }

    public boolean hasPassLimit() {
        return maxPasses > 0;
    }
}
