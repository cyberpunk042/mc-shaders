package net.cyberpunk042.mcshaders.core.binding;

import net.cyberpunk042.mcshaders.core.effect.EffectStack;

/**
 * Binds an effect stack to a dimension, optionally gated by a condition.
 *
 * <p>Several bindings may target the same dimension. They are applied in ascending
 * priority and merged, so a base "look" for a dimension can be layered with
 * conditional additions (night-only, underwater-only) without either one having to
 * know about the other.
 *
 * @param id        stable identifier, unique in a registry; also the override key
 * @param dimension the dimension this applies to
 * @param condition when it applies
 * @param stack     the effects it contributes
 * @param priority  lower merges first, so higher-priority bindings win on conflict
 */
public record DimensionBinding(
        String id,
        DimensionId dimension,
        Condition condition,
        EffectStack stack,
        int priority) {

    public DimensionBinding {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Binding id must not be blank");
        }
        if (dimension == null) {
            throw new IllegalArgumentException("Binding '" + id + "' has no dimension");
        }
        condition = condition == null ? Condition.always() : condition;
        stack = stack == null ? EffectStack.empty() : stack;
    }

    /** An unconditional binding at default priority. */
    public static DimensionBinding of(String id, DimensionId dimension, EffectStack stack) {
        return new DimensionBinding(id, dimension, Condition.always(), stack, 0);
    }

    public boolean appliesTo(WorldState state) {
        return dimension.equals(state.dimension()) && condition.test(state);
    }

    public DimensionBinding withPriority(int newPriority) {
        return new DimensionBinding(id, dimension, condition, stack, newPriority);
    }

    public DimensionBinding withCondition(Condition newCondition) {
        return new DimensionBinding(id, dimension, newCondition, stack, priority);
    }
}
