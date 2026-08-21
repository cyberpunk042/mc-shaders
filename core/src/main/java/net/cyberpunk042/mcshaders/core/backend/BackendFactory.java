package net.cyberpunk042.mcshaders.core.backend;

import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * Creates an {@link EffectBackend}, and knows whether it can run here.
 *
 * <p>Backends are contributed rather than hardcoded so a third party can supply a
 * different renderer — a Vulkan implementation, a debug capture backend, or one
 * tuned for a specific driver — without the framework knowing about it.
 *
 * <p>Selection is by priority among the factories that report themselves available.
 * {@link #isAvailable()} must be cheap and side-effect free; the expensive work
 * belongs in {@link EffectBackend#initialise()}, which may still fail and cause the
 * next candidate to be tried.
 */
@Stable(since = "0.2.0")
public interface BackendFactory {

    /** Priority of the framework's own backends. Third parties can sit either side. */
    int DEFAULT_PRIORITY = 0;

    /** Stable identifier of the backend this produces, e.g. {@code "opengl"}. */
    String id();

    /**
     * Higher wins. Ties are broken by id so selection is deterministic rather than
     * dependent on mod load order.
     */
    default int priority() {
        return DEFAULT_PRIORITY;
    }

    /**
     * Whether this backend could run in the current environment.
     *
     * <p>Cheap checks only — driver presence, a feature flag, whether this is a
     * dedicated server. Must not allocate GPU resources.
     */
    default boolean isAvailable() {
        return true;
    }

    /** Creates the backend. Called at most once per selection. */
    EffectBackend create();
}
