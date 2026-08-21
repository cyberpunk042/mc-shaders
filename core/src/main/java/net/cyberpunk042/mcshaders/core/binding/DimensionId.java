package net.cyberpunk042.mcshaders.core.binding;

import java.util.Locale;

/**
 * A namespaced dimension identifier, e.g. {@code minecraft:the_nether}.
 *
 * <p>The core models this as its own type rather than reusing Minecraft's
 * identifier class, which is what keeps the framework free of a Minecraft
 * dependency and unit-testable in isolation. The Minecraft layer converts at the
 * boundary.
 *
 * <p>Validation mirrors vanilla's rules ({@code [a-z0-9_.-]} for the namespace,
 * plus {@code /} in the path) so an id accepted here cannot be rejected later.
 */
public record DimensionId(String namespace, String path) implements Comparable<DimensionId> {

    public static final String DEFAULT_NAMESPACE = "minecraft";

    public DimensionId {
        namespace = validate("namespace", namespace, false);
        path = validate("path", path, true);
    }

    /**
     * Parses {@code namespace:path}. A bare string is treated as a path in the
     * {@code minecraft} namespace, matching vanilla's shorthand.
     *
     * @throws IllegalArgumentException if the string is malformed
     */
    public static DimensionId parse(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Dimension id must not be blank");
        }
        int sep = id.indexOf(':');
        if (sep < 0) {
            return new DimensionId(DEFAULT_NAMESPACE, id);
        }
        if (id.indexOf(':', sep + 1) >= 0) {
            throw new IllegalArgumentException("Dimension id has more than one ':' separator: " + id);
        }
        return new DimensionId(id.substring(0, sep), id.substring(sep + 1));
    }

    public static DimensionId of(String namespace, String path) {
        return new DimensionId(namespace, path);
    }

    public static DimensionId minecraft(String path) {
        return new DimensionId(DEFAULT_NAMESPACE, path);
    }

    private static String validate(String field, String value, boolean allowSlash) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Dimension id " + field + " must not be blank");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '_' || c == '.' || c == '-'
                    || (allowSlash && c == '/');
            if (!ok) {
                throw new IllegalArgumentException(
                        "Illegal character '" + c + "' in dimension id " + field + ": " + value);
            }
        }
        return lower;
    }

    @Override
    public int compareTo(DimensionId other) {
        int ns = namespace.compareTo(other.namespace);
        return ns != 0 ? ns : path.compareTo(other.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
