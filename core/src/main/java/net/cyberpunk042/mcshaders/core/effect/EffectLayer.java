package net.cyberpunk042.mcshaders.core.effect;

import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.Interpolation;

/**
 * One effect in a stack: what to draw, how strongly, and in what order.
 *
 * @param id       stable identifier, unique within a stack; used for merging and overrides
 * @param kind     the category of effect
 * @param params   effect-specific parameters
 * @param blend    how the result composites onto the frame
 * @param weight   strength in {@code [0, 1]}; 0 contributes nothing
 * @param priority lower values run earlier; ties fall back to insertion order
 */
public record EffectLayer(
        String id,
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
    }

    /** A full-strength alpha-blended layer at default priority. */
    public static EffectLayer of(String id, EffectKind kind, EffectParams params) {
        return new EffectLayer(id, kind, params, BlendMode.ALPHA, 1.0, 0);
    }

    public EffectLayer withWeight(double newWeight) {
        return new EffectLayer(id, kind, params, blend, newWeight, priority);
    }

    public EffectLayer withParams(EffectParams newParams) {
        return new EffectLayer(id, kind, newParams, blend, weight, priority);
    }

    public EffectLayer withPriority(int newPriority) {
        return new EffectLayer(id, kind, params, blend, weight, newPriority);
    }

    /** True when this layer would contribute nothing and can be dropped. */
    public boolean isNoOp() {
        return weight <= 0.0;
    }

    /**
     * Blends toward {@code other}.
     *
     * <p>Only continuous properties are interpolated. Kind and blend mode are
     * discrete: they step at the halfway point, because there is no coherent
     * intermediate between, say, FOG and BLOOM. Two layers of differing kind
     * blending into each other is a pack decision, not something to smooth over.
     */
    public EffectLayer lerp(EffectLayer other, double t) {
        double c = Interpolation.clamp01(t);
        boolean past = c >= 0.5;
        return new EffectLayer(
                past ? other.id : this.id,
                past ? other.kind : this.kind,
                this.params.lerp(other.params, c),
                past ? other.blend : this.blend,
                Interpolation.mix(this.weight, other.weight, c),
                (int) Math.round(Interpolation.mix(this.priority, other.priority, c)));
    }
}
