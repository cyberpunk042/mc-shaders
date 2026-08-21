package net.cyberpunk042.mcshaders.core.param;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * An immutable, named bag of shader parameters.
 *
 * <p>Parameters are addressed by string key so that a datapack or config file can
 * declare them without the framework knowing every effect's schema up front. Typed
 * accessors return {@link Optional} rather than throwing, because a pack that names
 * a parameter an effect does not use should be ignored, not fatal.
 *
 * <p>Iteration order is the insertion order of the underlying builder, which keeps
 * serialised output stable and diffs readable.
 */
public final class EffectParams {

    private static final EffectParams EMPTY = new EffectParams(Map.of());

    private final Map<String, ParamValue> values;

    private EffectParams(Map<String, ParamValue> values) {
        this.values = values;
    }

    public static EffectParams empty() {
        return EMPTY;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EffectParams of(Map<String, ParamValue> values) {
        if (values.isEmpty()) {
            return EMPTY;
        }
        return new EffectParams(Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }

    public boolean isEmpty() {
        return values.isEmpty();
    }

    public int size() {
        return values.size();
    }

    public Set<String> keys() {
        return values.keySet();
    }

    public Map<String, ParamValue> asMap() {
        return values;
    }

    public Optional<ParamValue> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public Optional<Double> scalar(String key) {
        return get(key)
                .filter(ParamValue.Scalar.class::isInstance)
                .map(v -> ((ParamValue.Scalar) v).value());
    }

    public double scalarOr(String key, double fallback) {
        return scalar(key).orElse(fallback);
    }

    public Optional<ParamValue.Vec3> vec3(String key) {
        return get(key)
                .filter(ParamValue.Vec3.class::isInstance)
                .map(ParamValue.Vec3.class::cast);
    }

    public Optional<ParamValue.Rgba> color(String key) {
        return get(key)
                .filter(ParamValue.Rgba.class::isInstance)
                .map(ParamValue.Rgba.class::cast);
    }

    public Optional<Boolean> flag(String key) {
        return get(key)
                .filter(ParamValue.Flag.class::isInstance)
                .map(v -> ((ParamValue.Flag) v).value());
    }

    public boolean flagOr(String key, boolean fallback) {
        return flag(key).orElse(fallback);
    }

    public Optional<String> text(String key) {
        return get(key)
                .filter(ParamValue.Text.class::isInstance)
                .map(v -> ((ParamValue.Text) v).value());
    }

    /**
     * Returns a copy where any key absent here is taken from {@code defaults}.
     * Keys present here always win — an effect's declared defaults must never
     * override what a pack explicitly set.
     */
    public EffectParams withDefaults(EffectParams defaults) {
        if (defaults.isEmpty()) {
            return this;
        }
        if (this.isEmpty()) {
            return defaults;
        }
        Map<String, ParamValue> merged = new LinkedHashMap<>(defaults.values);
        merged.putAll(this.values);
        return new EffectParams(Collections.unmodifiableMap(merged));
    }

    /** Returns a copy with {@code key} set to {@code value}, replacing any previous entry. */
    public EffectParams with(String key, ParamValue value) {
        Map<String, ParamValue> next = new LinkedHashMap<>(values);
        next.put(requireKey(key), requireValue(value));
        return new EffectParams(Collections.unmodifiableMap(next));
    }

    /**
     * Blends every parameter toward {@code other}.
     *
     * <p>The result covers the union of both key sets. A key present on only one
     * side has no counterpart to blend against, so it is carried through unchanged;
     * dropping it would make an effect flicker mid-transition.
     */
    public EffectParams lerp(EffectParams other, double t) {
        double clamped = Interpolation.clamp01(t);
        if (clamped <= 0.0) {
            return this;
        }
        if (clamped >= 1.0) {
            return other;
        }

        Map<String, ParamValue> result = new LinkedHashMap<>();
        // Deterministic key order across the union keeps blended output stable.
        Set<String> allKeys = new TreeSet<>(this.values.keySet());
        allKeys.addAll(other.values.keySet());

        for (String key : allKeys) {
            ParamValue from = this.values.get(key);
            ParamValue to = other.values.get(key);
            if (from != null && to != null) {
                result.put(key, from.lerp(to, clamped));
            } else {
                result.put(key, from != null ? from : to);
            }
        }
        return new EffectParams(Collections.unmodifiableMap(result));
    }

    private static String requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Parameter key must not be blank");
        }
        return key;
    }

    private static ParamValue requireValue(ParamValue value) {
        if (value == null) {
            throw new IllegalArgumentException("Parameter value must not be null");
        }
        return value;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EffectParams p && values.equals(p.values);
    }

    @Override
    public int hashCode() {
        return values.hashCode();
    }

    @Override
    public String toString() {
        return "EffectParams" + values;
    }

    /** Fluent builder. Not thread-safe; build once, share the immutable result. */
    public static final class Builder {
        private final Map<String, ParamValue> values = new LinkedHashMap<>();

        public Builder set(String key, ParamValue value) {
            values.put(requireKey(key), requireValue(value));
            return this;
        }

        public Builder scalar(String key, double value) {
            return set(key, new ParamValue.Scalar(value));
        }

        public Builder vec3(String key, double x, double y, double z) {
            return set(key, new ParamValue.Vec3(x, y, z));
        }

        public Builder color(String key, float r, float g, float b, float a) {
            return set(key, new ParamValue.Rgba(r, g, b, a));
        }

        public Builder flag(String key, boolean value) {
            return set(key, new ParamValue.Flag(value));
        }

        public Builder text(String key, String value) {
            return set(key, new ParamValue.Text(value));
        }

        public EffectParams build() {
            return EffectParams.of(values);
        }
    }
}
