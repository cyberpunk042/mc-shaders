package net.cyberpunk042.mcshaders.core.effect;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * The set of effect types third parties have contributed.
 *
 * <p>Unlike most of the core this is mutable, because registration is inherently a
 * startup-time accumulation from many mods. It is therefore explicitly
 * <em>lifecycled</em>: open during initialisation, {@link #freeze() frozen}
 * afterwards. Once frozen it never changes again, so the render path can read it
 * without synchronisation and without the possibility of a mod registering an
 * effect mid-frame.
 *
 * <p>Registration is synchronised because loaders make no guarantee that mod
 * initialisers run on a single thread.
 */
@Stable(since = "0.2.0")
public final class EffectRegistry {

    private final Map<String, EffectDefinition> definitions = new LinkedHashMap<>();
    private volatile boolean frozen;

    /**
     * Registers an effect type.
     *
     * @throws IllegalStateException if the registry is frozen, or the type is taken
     */
    public synchronized EffectRegistry register(EffectDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("Cannot register a null effect definition");
        }
        if (frozen) {
            throw new IllegalStateException(
                    "Effect registration is closed; '" + definition.type() + "' arrived too late. "
                            + "Register during mod initialisation.");
        }
        EffectDefinition existing = definitions.get(definition.type());
        if (existing != null) {
            // Refusing beats last-write-wins: silently shadowing another mod's effect
            // would surface as an unexplained visual difference, not an error.
            throw new IllegalStateException(
                    "Effect type '" + definition.type() + "' is already registered by '"
                            + existing.owner() + "'; '" + definition.owner() + "' cannot replace it.");
        }
        definitions.put(definition.type(), definition);
        return this;
    }

    /** Closes registration. Idempotent, so a loader may call it defensively. */
    public synchronized void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    public synchronized Optional<EffectDefinition> byType(String type) {
        return Optional.ofNullable(definitions.get(type));
    }

    public synchronized Collection<EffectDefinition> all() {
        return java.util.List.copyOf(definitions.values());
    }

    public synchronized int size() {
        return definitions.size();
    }

    /**
     * Applies a layer's definition defaults, if it names one.
     *
     * <p>Values already on the layer win — a definition supplies fallbacks, it does
     * not override what a pack explicitly asked for. A layer naming an unregistered
     * type is returned untouched; the compiler reports that separately, so a missing
     * optional dependency degrades rather than throwing here.
     */
    public EffectLayer applyDefaults(EffectLayer layer) {
        if (!layer.hasDefinitionType()) {
            return layer;
        }
        return byType(layer.type())
                .map(def -> layer.withParams(layer.params().withDefaults(def.defaultParams())))
                .orElse(layer);
    }
}
