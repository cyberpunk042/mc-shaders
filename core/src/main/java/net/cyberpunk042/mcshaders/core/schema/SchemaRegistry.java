package net.cyberpunk042.mcshaders.core.schema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * Which effect types can be edited, and by what schema.
 *
 * <p>An {@link net.cyberpunk042.mcshaders.core.effect.EffectDefinition} says what an
 * effect <em>is</em> — its type, its kind, its default values. It does not say what is
 * <em>tunable</em>: which of those values a person may change, over what range, through
 * what sort of control. That is what an {@link EffectSchema} describes, and the two were
 * never connected — so an editor could be handed a schema but had no way to find one
 * from an effect, and a mod could register an effect that no editor could ever show.
 *
 * <p>This is the missing link, kept separate rather than folded into
 * {@code EffectDefinition} for two reasons. Being editable is optional: an effect with
 * nothing worth tuning should not have to declare an empty schema. And a schema is
 * larger than everything else in a definition put together, so an effect that is never
 * edited should not carry one.
 *
 * <p>Lifecycled like {@link net.cyberpunk042.mcshaders.core.effect.EffectRegistry}:
 * open during initialisation, frozen afterwards, synchronised while open because
 * loaders make no promise about which thread a mod initialises on.
 */
@Stable(since = "0.6.0")
public final class SchemaRegistry {

    private final Map<String, EffectSchema> schemas = new LinkedHashMap<>();
    private volatile boolean frozen;

    /**
     * Declares that {@code effectType} is editable through {@code schema}.
     *
     * @throws IllegalStateException if registration has closed, or the type already
     *                               has a schema
     */
    public synchronized SchemaRegistry register(String effectType, EffectSchema schema) {
        if (effectType == null || effectType.isBlank()) {
            throw new IllegalArgumentException("A schema has to say which effect type it describes");
        }
        if (schema == null) {
            throw new IllegalArgumentException(
                    "Cannot register a null schema for '" + effectType + "'");
        }
        if (frozen) {
            throw new IllegalStateException(
                    "Schema registration is closed; '" + effectType + "' arrived too late. "
                            + "Register during mod initialisation.");
        }
        EffectSchema existing = schemas.get(effectType);
        if (existing != null) {
            // Same reasoning as the effect registry: shadowing another mod's schema
            // would surface as an editor showing the wrong controls, not as an error.
            throw new IllegalStateException(
                    "Effect type '" + effectType + "' already has a schema ('"
                            + existing.displayName() + "'); it cannot be replaced.");
        }
        schemas.put(effectType, schema);
        return this;
    }

    /** Closes registration. Idempotent, so a loader may call it defensively. */
    public synchronized void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /** The schema for {@code effectType}, or empty if it declared none. */
    public synchronized Optional<EffectSchema> forType(String effectType) {
        return Optional.ofNullable(schemas.get(effectType));
    }

    /** Whether {@code effectType} declared a schema, and so can be edited. */
    public synchronized boolean isEditable(String effectType) {
        return schemas.containsKey(effectType);
    }

    /**
     * Every effect type that can be edited, in registration order.
     *
     * <p>A {@link List}, and copied from the key set rather than through
     * {@code Map.copyOf}: that returns a map whose iteration order is unspecified, so
     * the order this method promises would hold in testing and quietly stop holding
     * once enough entries were registered to change the hashing. An editor listing
     * effects in an order that shuffles between runs is the visible half of that.
     */
    public synchronized List<String> editableTypes() {
        return List.copyOf(schemas.keySet());
    }

    /** Every registered schema, in registration order. */
    public synchronized List<EffectSchema> all() {
        return List.copyOf(schemas.values());
    }

    public synchronized int size() {
        return schemas.size();
    }

    public synchronized boolean isEmpty() {
        return schemas.isEmpty();
    }
}
