package net.cyberpunk042.mcshaders.core.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.param.Interpolation;

/**
 * An ordered, immutable set of effect layers.
 *
 * <p>A stack is the unit a dimension binds to. Layer ids are unique within a
 * stack: {@link #merge} overlays one stack onto another by id, which is how a
 * pack overrides a single effect of a dimension's look without restating the rest.
 */
public final class EffectStack {

    private static final EffectStack EMPTY = new EffectStack(List.of());

    private final List<EffectLayer> layers;

    private EffectStack(List<EffectLayer> layers) {
        this.layers = layers;
    }

    public static EffectStack empty() {
        return EMPTY;
    }

    /**
     * Builds a stack from {@code layers}.
     *
     * <p>Later entries sharing an id replace earlier ones, keeping the position of
     * the first occurrence so that an override does not silently reorder the stack.
     */
    public static EffectStack of(List<EffectLayer> layers) {
        if (layers == null || layers.isEmpty()) {
            return EMPTY;
        }
        Map<String, EffectLayer> byId = new LinkedHashMap<>();
        for (EffectLayer layer : layers) {
            if (layer != null) {
                byId.put(layer.id(), layer);
            }
        }
        return new EffectStack(List.copyOf(byId.values()));
    }

    public static EffectStack of(EffectLayer... layers) {
        return of(List.of(layers));
    }

    public List<EffectLayer> layers() {
        return layers;
    }

    public boolean isEmpty() {
        return layers.isEmpty();
    }

    public int size() {
        return layers.size();
    }

    public Optional<EffectLayer> byId(String id) {
        return layers.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    /**
     * Returns the layers in execution order: ascending priority, ties broken by
     * declaration order.
     *
     * <p>The sort is stable, so two layers at the same priority always render in
     * the order the pack declared them — a pack author's ordering is meaningful
     * and must not shift between runs.
     */
    public List<EffectLayer> inRenderOrder() {
        List<EffectLayer> sorted = new ArrayList<>(layers);
        sorted.sort(Comparator.comparingInt(EffectLayer::priority));
        return Collections.unmodifiableList(sorted);
    }

    /**
     * Overlays {@code overrides} onto this stack.
     *
     * <p>Layers sharing an id are replaced in place; new ids are appended. This is
     * additive by design — merging never removes a layer the base stack declared.
     */
    public EffectStack merge(EffectStack overrides) {
        if (overrides.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return overrides;
        }
        Map<String, EffectLayer> byId = new LinkedHashMap<>();
        for (EffectLayer layer : this.layers) {
            byId.put(layer.id(), layer);
        }
        for (EffectLayer layer : overrides.layers) {
            byId.put(layer.id(), layer);
        }
        return new EffectStack(List.copyOf(byId.values()));
    }

    /** Returns a copy without layers that would contribute nothing. */
    public EffectStack pruned() {
        List<EffectLayer> kept = layers.stream().filter(l -> !l.isNoOp()).toList();
        return kept.size() == layers.size() ? this : new EffectStack(kept);
    }

    /** Returns a copy with every layer's weight scaled by {@code factor}. */
    public EffectStack scaled(double factor) {
        double f = Interpolation.clamp01(factor);
        if (f >= 1.0) {
            return this;
        }
        return new EffectStack(layers.stream().map(l -> l.withWeight(l.weight() * f)).toList());
    }

    /**
     * Blends this stack toward {@code other}.
     *
     * <p>Layers matched by id blend directly. Unmatched layers are faded rather
     * than dropped: one present only in the source fades out, one present only in
     * the destination fades in. That is what makes a portal crossing continuous
     * instead of a visible pop.
     */
    public EffectStack lerp(EffectStack other, double t) {
        double c = Interpolation.clamp01(t);
        if (c <= 0.0) {
            return this;
        }
        if (c >= 1.0) {
            return other;
        }

        Map<String, EffectLayer> result = new LinkedHashMap<>();
        for (EffectLayer from : this.layers) {
            Optional<EffectLayer> to = other.byId(from.id());
            if (to.isPresent()) {
                result.put(from.id(), from.lerp(to.get(), c));
            } else {
                // Absent at the destination: fade out over the transition.
                result.put(from.id(), from.withWeight(from.weight() * (1.0 - c)));
            }
        }
        for (EffectLayer to : other.layers) {
            if (!result.containsKey(to.id())) {
                // New at the destination: fade in over the transition.
                result.put(to.id(), to.withWeight(to.weight() * c));
            }
        }
        return new EffectStack(List.copyOf(result.values()));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EffectStack s && layers.equals(s.layers);
    }

    @Override
    public int hashCode() {
        return layers.hashCode();
    }

    @Override
    public String toString() {
        return "EffectStack" + layers;
    }
}
