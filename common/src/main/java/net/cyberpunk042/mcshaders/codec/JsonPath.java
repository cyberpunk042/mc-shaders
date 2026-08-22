package net.cyberpunk042.mcshaders.codec;

/**
 * The breadcrumb a reader leaves as it descends, so a failure can say where it was.
 *
 * <p>Immutable and persistent: {@link #field} and {@link #index} return a new path
 * rather than mutating this one. That matters because a reader descends into a list,
 * fails on element two, and must not have corrupted the path its caller still holds
 * for element three. A mutable builder with matching push/pop calls would work until
 * an exception skipped a pop, which is exactly when the path is being read.
 */
public final class JsonPath {

    private static final JsonPath ROOT = new JsonPath("");

    private final String rendered;

    private JsonPath(String rendered) {
        this.rendered = rendered;
    }

    /** The empty path, meaning the top level of the document. */
    public static JsonPath root() {
        return ROOT;
    }

    /** This path, then a named member. */
    public JsonPath field(String name) {
        return new JsonPath(rendered.isEmpty() ? name : rendered + "." + name);
    }

    /** This path, then an array element. */
    public JsonPath index(int i) {
        return new JsonPath(rendered + "[" + i + "]");
    }

    /** Empty at the root; otherwise something like {@code stack.layers[2].params.speed}. */
    @Override
    public String toString() {
        return rendered;
    }
}
