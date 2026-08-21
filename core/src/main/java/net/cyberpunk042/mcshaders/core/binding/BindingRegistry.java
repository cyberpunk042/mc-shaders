package net.cyberpunk042.mcshaders.core.binding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;

/**
 * The set of all known dimension bindings.
 *
 * <p>Immutable: every mutating operation returns a new registry. Reloading a
 * datapack builds a fresh registry and swaps it in atomically, so a render pass
 * can never observe a half-applied reload.
 */
public final class BindingRegistry {

    private static final BindingRegistry EMPTY = new BindingRegistry(Map.of());

    private final Map<String, DimensionBinding> bindings;

    private BindingRegistry(Map<String, DimensionBinding> bindings) {
        this.bindings = bindings;
    }

    public static BindingRegistry empty() {
        return EMPTY;
    }

    /** Builds a registry; later entries replace earlier ones sharing an id. */
    public static BindingRegistry of(List<DimensionBinding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            return EMPTY;
        }
        Map<String, DimensionBinding> byId = new LinkedHashMap<>();
        for (DimensionBinding binding : bindings) {
            if (binding != null) {
                byId.put(binding.id(), binding);
            }
        }
        return new BindingRegistry(Map.copyOf(byId));
    }

    public static BindingRegistry of(DimensionBinding... bindings) {
        return of(List.of(bindings));
    }

    /** Returns a registry with {@code binding} added, replacing any same-id entry. */
    public BindingRegistry with(DimensionBinding binding) {
        Map<String, DimensionBinding> next = new LinkedHashMap<>(bindings);
        next.put(binding.id(), binding);
        return new BindingRegistry(Map.copyOf(next));
    }

    /** Returns a registry without the binding of the given id. */
    public BindingRegistry without(String id) {
        if (!bindings.containsKey(id)) {
            return this;
        }
        Map<String, DimensionBinding> next = new LinkedHashMap<>(bindings);
        next.remove(id);
        return new BindingRegistry(Map.copyOf(next));
    }

    public Optional<DimensionBinding> byId(String id) {
        return Optional.ofNullable(bindings.get(id));
    }

    public int size() {
        return bindings.size();
    }

    public boolean isEmpty() {
        return bindings.isEmpty();
    }

    /** All bindings targeting a dimension, regardless of condition, in priority order. */
    public List<DimensionBinding> forDimension(DimensionId dimension) {
        return sortedByPriority(bindings.values().stream()
                .filter(b -> b.dimension().equals(dimension))
                .toList());
    }

    /** The bindings currently active for {@code state}, in priority order. */
    public List<DimensionBinding> active(WorldState state) {
        return sortedByPriority(bindings.values().stream()
                .filter(b -> b.appliesTo(state))
                .toList());
    }

    /**
     * Resolves the effective stack for {@code state} by merging every active
     * binding in ascending priority.
     *
     * <p>Merging is by layer id, so a high-priority binding that redefines one
     * layer leaves the rest of a lower-priority binding's stack intact. That is
     * what lets a pack tweak a dimension's fog without restating its colour grade.
     */
    public EffectStack resolve(WorldState state) {
        EffectStack result = EffectStack.empty();
        for (DimensionBinding binding : active(state)) {
            result = result.merge(binding.stack());
        }
        return result.pruned();
    }

    private static List<DimensionBinding> sortedByPriority(List<DimensionBinding> input) {
        List<DimensionBinding> sorted = new ArrayList<>(input);
        // Priority first, then id — id breaks ties deterministically, since the
        // stream source is an unordered map view.
        sorted.sort(Comparator.comparingInt(DimensionBinding::priority)
                .thenComparing(DimensionBinding::id));
        return List.copyOf(sorted);
    }

    @Override
    public String toString() {
        return "BindingRegistry(" + bindings.size() + " bindings)";
    }
}
