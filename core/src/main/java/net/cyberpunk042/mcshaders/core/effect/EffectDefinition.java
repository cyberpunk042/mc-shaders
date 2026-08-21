package net.cyberpunk042.mcshaders.core.effect;

import java.util.Set;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.param.EffectParams;

/**
 * Declares an effect <em>type</em> that layers can instantiate.
 *
 * <p>Built-in effects are identified by {@link EffectKind} alone. A definition is
 * what lets a third party add a genuinely new effect: it names the type, supplies
 * the defaults so packs need only override what they care about, and states what a
 * backend must be able to do in order to render it.
 *
 * <p>The type id is namespaced (e.g. {@code mymod:kaleidoscope}) so two mods can
 * ship an effect of the same name without colliding.
 *
 * @param type           namespaced type id, unique across the registry
 * @param kind           the category a backend should treat this as; usually {@link EffectKind#CUSTOM}
 * @param defaultParams  parameter values applied wherever a layer does not set them
 * @param requiresDepth  whether rendering needs the depth buffer
 * @param owner          the mod or pack that registered this, for diagnostics
 */
@Stable(since = "0.2.0")
public record EffectDefinition(
        String type,
        EffectKind kind,
        EffectParams defaultParams,
        boolean requiresDepth,
        String owner) {

    public EffectDefinition {
        type = validateType(type);
        if (kind == null) {
            throw new IllegalArgumentException("Effect definition '" + type + "' has no kind");
        }
        defaultParams = defaultParams == null ? EffectParams.empty() : defaultParams;
        owner = owner == null || owner.isBlank() ? "unknown" : owner;
    }

    /** A custom-kind definition with no defaults and no depth requirement. */
    public static EffectDefinition of(String type, String owner) {
        return new EffectDefinition(type, EffectKind.CUSTOM, EffectParams.empty(), false, owner);
    }

    public EffectDefinition withDefaults(EffectParams defaults) {
        return new EffectDefinition(type, kind, defaults, requiresDepth, owner);
    }

    public EffectDefinition requiringDepth() {
        return new EffectDefinition(type, kind, defaultParams, true, owner);
    }

    /** The namespace portion of the type id. */
    public String namespace() {
        return type.substring(0, type.indexOf(':'));
    }

    /**
     * Whether a backend able to do {@code kinds} and {@code types} can render this.
     *
     * <p>Both the kind and the explicit type must be supported: recognising
     * {@code CUSTOM} in general says nothing about understanding this particular
     * custom effect.
     */
    public boolean isRenderableBy(Set<EffectKind> kinds, Set<String> types, boolean depthAvailable) {
        if (requiresDepth && !depthAvailable) {
            return false;
        }
        if (kind == EffectKind.CUSTOM) {
            return types.contains(type);
        }
        return kinds.contains(kind);
    }

    private static String validateType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Effect definition type must not be blank");
        }
        int sep = value.indexOf(':');
        if (sep <= 0 || sep == value.length() - 1) {
            throw new IllegalArgumentException(
                    "Effect definition type must be namespaced as 'namespace:name', got: " + value);
        }
        if (value.indexOf(':', sep + 1) >= 0) {
            throw new IllegalArgumentException(
                    "Effect definition type has more than one ':' separator: " + value);
        }
        return value;
    }
}
