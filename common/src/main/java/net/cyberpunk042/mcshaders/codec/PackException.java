package net.cyberpunk042.mcshaders.codec;

/**
 * A pack was wrong, and this says where.
 *
 * <p>The message a pack author sees is the whole point of this class. "Expected a
 * number" is useless in a file with forty numbers in it; "nether.json at
 * stack.layers[2].params.speed: expected a number, found a string" can be acted on
 * without opening a debugger. So the location travels with the failure rather than
 * being reconstructed from a stack trace, which would name this file's line numbers
 * instead of theirs.
 *
 * <p>The path is built as the reader descends, by {@link JsonPath}. A reader that
 * forgets to descend produces a shorter path, not a wrong one — the failure still
 * names the file and as much of the location as was tracked.
 */
public final class PackException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String source;
    private final String path;
    private final String problem;

    /**
     * @param source where the JSON came from — a file name, a resource id, anything
     *               the person editing it would recognise
     * @param path   the location within it, such as {@code stack.layers[2].params.speed}
     * @param problem what was wrong, phrased so it can follow the location
     */
    public PackException(String source, String path, String problem) {
        super(format(source, path, problem));
        this.source = source == null ? "<unknown>" : source;
        this.path = path == null ? "" : path;
        this.problem = problem;
    }

    public PackException(String source, String path, String problem, Throwable cause) {
        super(format(source, path, problem), cause);
        this.source = source == null ? "<unknown>" : source;
        this.path = path == null ? "" : path;
        this.problem = problem;
    }

    /** Where the JSON came from. */
    public String source() {
        return source;
    }

    /** The location within it, empty when the failure was at the top level. */
    public String path() {
        return path;
    }

    /** What was wrong, without the location prefix. */
    public String problem() {
        return problem;
    }

    private static String format(String source, String path, String problem) {
        StringBuilder out = new StringBuilder(source == null ? "<unknown>" : source);
        if (path != null && !path.isEmpty()) {
            out.append(" at ").append(path);
        }
        return out.append(": ").append(problem).toString();
    }
}
