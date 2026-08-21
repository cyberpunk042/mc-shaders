package net.cyberpunk042.mcshaders.core.backend;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * The set of contributed backends, and the selection among them.
 *
 * <p>Like {@link net.cyberpunk042.mcshaders.core.effect.EffectRegistry} this is
 * lifecycled: open during initialisation, frozen before selection. Selection walks
 * the available factories from highest priority down, initialising each until one
 * succeeds — so a backend that probes fine but fails to allocate falls through to
 * the next candidate rather than leaving the game with no renderer.
 *
 * <p>If nothing succeeds the result is {@link NoOpBackend}, never null. "No effects"
 * is a valid state — it is what a dedicated server should have — and making it a
 * real object keeps every caller free of null handling.
 */
@Stable(since = "0.2.0")
public final class BackendRegistry {

    private final Map<String, BackendFactory> factories = new LinkedHashMap<>();
    private volatile boolean frozen;

    /**
     * Registers a backend factory.
     *
     * @throws IllegalStateException if frozen, or the id is taken
     */
    public synchronized BackendRegistry register(BackendFactory factory) {
        if (factory == null) {
            throw new IllegalArgumentException("Cannot register a null backend factory");
        }
        if (frozen) {
            throw new IllegalStateException(
                    "Backend registration is closed; '" + factory.id() + "' arrived too late. "
                            + "Register during mod initialisation.");
        }
        if (factories.containsKey(factory.id())) {
            throw new IllegalStateException(
                    "Backend id '" + factory.id() + "' is already registered. "
                            + "Use a namespaced id if you did not mean to replace it.");
        }
        factories.put(factory.id(), factory);
        return this;
    }

    /** Closes registration. Idempotent. */
    public synchronized void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public synchronized Optional<BackendFactory> byId(String id) {
        return Optional.ofNullable(factories.get(id));
    }

    public synchronized int size() {
        return factories.size();
    }

    /** Registered factories in selection order: priority descending, then id. */
    public synchronized List<BackendFactory> inSelectionOrder() {
        List<BackendFactory> sorted = new ArrayList<>(factories.values());
        sorted.sort(Comparator.comparingInt(BackendFactory::priority).reversed()
                .thenComparing(BackendFactory::id));
        return List.copyOf(sorted);
    }

    /**
     * Picks and initialises the best usable backend.
     *
     * @param onSkip notified for each candidate passed over, with the reason; use it
     *               to log why the chosen backend was chosen
     * @return the first backend that initialised, or a {@link NoOpBackend}
     */
    public EffectBackend select(Consumer<String> onSkip) {
        Consumer<String> report = onSkip == null ? msg -> { } : onSkip;

        for (BackendFactory factory : inSelectionOrder()) {
            if (!factory.isAvailable()) {
                report.accept("Backend '" + factory.id() + "' reported itself unavailable");
                continue;
            }
            EffectBackend backend;
            try {
                backend = factory.create();
            } catch (RuntimeException e) {
                // A third party's factory throwing must not take the game down with it.
                report.accept("Backend '" + factory.id() + "' threw while being created: " + e);
                continue;
            }
            if (backend == null) {
                report.accept("Backend factory '" + factory.id() + "' returned null");
                continue;
            }
            try {
                if (backend.initialise()) {
                    return backend;
                }
                report.accept("Backend '" + factory.id() + "' declined to initialise");
                backend.close();
            } catch (RuntimeException e) {
                report.accept("Backend '" + factory.id() + "' threw while initialising: " + e);
                closeQuietly(backend, report);
            }
        }
        report.accept("No backend initialised; effects are disabled");
        return new NoOpBackend();
    }

    /** Selects without reporting skipped candidates. */
    public EffectBackend select() {
        return select(null);
    }

    private static void closeQuietly(EffectBackend backend, Consumer<String> report) {
        try {
            backend.close();
        } catch (RuntimeException e) {
            report.accept("Backend '" + backend.id() + "' also threw while closing: " + e);
        }
    }
}
