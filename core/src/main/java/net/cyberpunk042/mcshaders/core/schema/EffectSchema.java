package net.cyberpunk042.mcshaders.core.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;

/**
 * Everything tunable about one effect, in the order it should be shown.
 *
 * <p>An effect's parameters are a flat map at runtime, which is right for the
 * render path and useless for editing: nothing in it says which of forty numbers
 * belong together, what any of them is called, or what a sane value looks like.
 * A schema supplies that, and nothing else — it holds no values, only a
 * description of them.
 *
 * <p>Groups are ordered, and so are the parameters within a group, because that
 * order is the only layout information an editor gets.
 *
 * <p>Effects are versioned: the same effect can expose a different set of knobs
 * as it develops, and the schema is what pins which set a given version means.
 *
 * @param displayName what to call the effect in front of a person
 * @param effectType  the effect's type id, matching {@code EffectDefinition.type()}
 * @param version     which revision of this effect's parameter set
 * @param groups      parameters by group, both ordered
 */
@Stable(since = "0.5.0")
public record EffectSchema(String displayName, String effectType, int version,
                           Map<String, List<ParamSpec>> groups) {

    public EffectSchema {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("a schema needs a display name");
        }
        if (effectType == null || effectType.isBlank()) {
            throw new IllegalArgumentException("schema '" + displayName + "' needs an effect type");
        }
        if (version < 1) {
            throw new IllegalArgumentException("schema '" + displayName + "' has version " + version
                    + "; versions start at 1");
        }
        Map<String, List<ParamSpec>> copy = new LinkedHashMap<>();
        groups.forEach((name, specs) -> copy.put(name, List.copyOf(specs)));
        groups = Collections.unmodifiableMap(copy);
    }

    public static Builder builder(String displayName, String effectType, int version) {
        return new Builder(displayName, effectType, version);
    }

    /** Group names, in display order. */
    public List<String> groupNames() {
        return List.copyOf(groups.keySet());
    }

    /** The parameters in one group, or empty if there is no such group. */
    public List<ParamSpec> group(String name) {
        return groups.getOrDefault(name, List.of());
    }

    /**
     * Every parameter, in order, one per key.
     *
     * <p>A key appearing in more than one group is not an error: a later group
     * overriding an earlier one is how a version narrows or relabels an inherited
     * parameter. The last occurrence wins, and it keeps the position of the first,
     * so an override does not reshuffle the layout.
     */
    public List<ParamSpec> parameters() {
        Map<String, ParamSpec> byKey = new LinkedHashMap<>();
        for (List<ParamSpec> specs : groups.values()) {
            for (ParamSpec spec : specs) {
                byKey.merge(spec.key(), spec, (first, later) -> later);
            }
        }
        return List.copyOf(byKey.values());
    }

    public Optional<ParamSpec> parameter(String key) {
        ParamSpec found = null;
        for (List<ParamSpec> specs : groups.values()) {
            for (ParamSpec spec : specs) {
                if (spec.key().equals(key)) {
                    found = spec;
                }
            }
        }
        return Optional.ofNullable(found);
    }

    public boolean describes(String key) {
        return parameter(key).isPresent();
    }

    public int parameterCount() {
        return parameters().size();
    }

    /** The starting point this schema describes, as parameters an effect can take. */
    public EffectParams defaults() {
        EffectParams.Builder builder = EffectParams.builder();
        for (ParamSpec spec : parameters()) {
            if (spec.fallback() != null) {
                builder.set(spec.key(), spec.fallback());
            }
        }
        return builder.build();
    }

    /**
     * Brings a set of edited values back within what this schema permits.
     *
     * <p>Keys the schema does not describe are kept as they are. An editor is not
     * the only thing that writes parameters, and silently dropping what it does not
     * recognise would make opening a panel destructive.
     */
    public EffectParams coerce(EffectParams params) {
        EffectParams result = params;
        for (ParamSpec spec : parameters()) {
            Optional<ParamValue> current = params.get(spec.key());
            if (current.isEmpty()) {
                continue;
            }
            ParamValue coerced = spec.coerce(current.get());
            if (!coerced.equals(current.get())) {
                result = result.with(spec.key(), coerced);
            }
        }
        return result;
    }

    /** Builds a schema group by group, in the order they should appear. */
    public static final class Builder {

        private final String displayName;
        private final String effectType;
        private final int version;
        private final Map<String, List<ParamSpec>> groups = new LinkedHashMap<>();

        private Builder(String displayName, String effectType, int version) {
            this.displayName = displayName;
            this.effectType = effectType;
            this.version = version;
        }

        /** Adds a group, or appends to one already started under this name. */
        public Builder group(String name, List<ParamSpec> specs) {
            groups.computeIfAbsent(name, ignored -> new ArrayList<>()).addAll(specs);
            return this;
        }

        public Builder group(String name, ParamSpec... specs) {
            return group(name, List.of(specs));
        }

        /**
         * Starts from another schema, so a version can state its differences rather
         * than restating everything an earlier one already said.
         */
        public Builder extending(EffectSchema base) {
            base.groups().forEach((name, specs) -> group(name, specs));
            return this;
        }

        public EffectSchema build() {
            return new EffectSchema(displayName, effectType, version, groups);
        }
    }
}
