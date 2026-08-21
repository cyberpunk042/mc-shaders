package net.cyberpunk042.mcshaders.core.effect;

import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.Interpolation;

/**
 * One effect in a stack: what to draw, how strongly, and in what order.
 *
 * <p>A layer names either a built-in {@link EffectKind} or, via {@code type}, a
 * registered {@link EffectDefinition} contributed by another mod. The two are not
 * exclusive — a definition also carries a kind, which is how a backend decides
 * roughly what sort of work it implies.
 *
 * @param id       instance name, unique within a stack; the key used for merging and overrides
 * @param type     namespaced {@link EffectDefinition} id, or {@code null} for a plain built-in effect
 * @param kind     the category of effect
 * @param params   effect-specific parameters
 * @param blend    how the result composites onto the frame
 * @param weight   strength in {@code [0, 1]}; 0 contributes nothing
 * @param priority lower values run earlier; ties fall back to declaration order
 */
@Stable(since = "0.1.0")
public record EffectLayer(
        String id,
        String type,
        EffectKind kind,
        EffectParams params,
        BlendMode blend,
        double weight,
        int priority) {

    public EffectLayer {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Effect layer id must not be blank");
        }
        if (kind == null) {
            throw new IllegalArgumentException("Effect layer '" + id + "' has no kind");
        }
        if (blend == null) {
            throw new IllegalArgumentException("Effect layer '" + id + "' has no blend mode");
        }
        params = params == null ? EffectParams.empty() : params;
        weight = Interpolation.clamp01(weight);
        type = (type == null || type.isBlank()) ? null : type;
    }

    /** A full-strength alpha-blended built-in layer at default priority. */
    public static EffectLayer of(String id, EffectKind kind, EffectParams params) {
        return new EffectLayer(id, null, kind, params, BlendMode.ALPHA, 1.0, 0);
    }

    /** A full-strength alpha-blended layer instantiating a registered definition. */
    public static EffectLayer ofType(String id, EffectDefinition definition, EffectParams params) {
        return new EffectLayer(id, definition.type(), definition.kind(), params, BlendMode.ALPHA, 1.0, 0);
    }

    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** The definition id this layer instantiates, if any. */
    public Optional<String> definitionType() {
        return Optional.ofNullable(type);
    }

    public boolean hasDefinitionType() {
        return type != null;
    }

    public EffectLayer withWeight(double newWeight) {
        return new EffectLayer(id, type, kind, params, blend, newWeight, priority);
    }

    public EffectLayer withParams(EffectParams newParams) {
        return new EffectLayer(id, type, kind, newParams, blend, weight, priority);
    }

    public EffectLayer withPriority(int newPriority) {
        return new EffectLayer(id, type, kind, params, blend, weight, newPriority);
    }

    /** True when this layer would contribute nothing and can be dropped. */
    public boolean isNoOp() {
        return weight <= 0.0;
    }

    /**
     * Blends toward {@code other}.
     *
     * <p>Only continuous properties are interpolated. Identity, kind and blend mode
     * are discrete: they step at the halfway point, because there is no coherent
     * intermediate between, say, FOG and BLOOM.
     */
    public EffectLayer lerp(EffectLayer other, double t) {
        double c = Interpolation.clamp01(t);
        boolean past = c >= 0.5;
        return new EffectLayer(
                past ? other.id : this.id,
                past ? other.type : this.type,
                past ? other.kind : this.kind,
                this.params.lerp(other.params, c),
                past ? other.blend : this.blend,
                Interpolation.mix(this.weight, other.weight, c),
                (int) Math.round(Interpolation.mix(this.priority, other.priority, c)));
    }

    /** Fluent construction, for when positional arguments stop being readable. */
    public static final class Builder {
        private final String id;
        private String type;
        private EffectKind kind = EffectKind.CUSTOM;
        private EffectParams params = EffectParams.empty();
        private BlendMode blend = BlendMode.ALPHA;
        private double weight = 1.0;
        private int priority;

        private Builder(String id) {
            this.id = id;
        }

        /** Sets kind and type together from a registered definition. */
        public Builder definition(EffectDefinition definition) {
            this.type = definition.type();
            this.kind = definition.kind();
            return this;
        }

        public Builder type(String definitionType) {
            this.type = definitionType;
            return this;
        }

        public Builder kind(EffectKind value) {
            this.kind = value;
            return this;
        }

        public Builder params(EffectParams value) {
            this.params = value;
            return this;
        }

        public Builder blend(BlendMode value) {
            this.blend = value;
            return this;
        }

        public Builder weight(double value) {
            this.weight = value;
            return this;
        }

        public Builder priority(int value) {
            this.priority = value;
            return this;
        }

        public EffectLayer build() {
            return new EffectLayer(id, type, kind, params, blend, weight, priority);
        }
    }
}
