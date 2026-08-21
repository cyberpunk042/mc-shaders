package net.cyberpunk042.mcshaders.core.glsl;

import java.util.List;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * The result of expanding a shader's includes.
 *
 * @param source      expanded GLSL, ready to hand to the driver
 * @param sourceMap   maps {@code #line} source indices back to file paths
 * @param diagnostics problems found while expanding; see {@link Diagnostic}
 */
@Stable(since = "0.3.0")
public record ResolvedShader(String source, SourceMap sourceMap, List<Diagnostic> diagnostics) {

    public ResolvedShader {
        diagnostics = List.copyOf(diagnostics);
    }

    /** Whether anything went wrong badly enough to affect the compiled result. */
    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR);
    }

    public boolean hasDiagnostics() {
        return !diagnostics.isEmpty();
    }

    public List<Diagnostic> errors() {
        return diagnostics.stream().filter(d -> d.severity() == Severity.ERROR).toList();
    }

    /** How much a diagnostic matters. */
    public enum Severity {
        /** The expansion is wrong; the shader will very likely fail to compile. */
        ERROR,
        /** Suspicious but survivable. */
        WARNING
    }

    /**
     * One problem found while expanding.
     *
     * @param severity how much it matters
     * @param path     the file the problem is in
     * @param line     the 1-based line within that file, or 0 if not line-specific
     * @param message  what is wrong, phrased for whoever has to fix the shader
     */
    public record Diagnostic(Severity severity, String path, int line, String message) {

        public static Diagnostic error(String path, int line, String message) {
            return new Diagnostic(Severity.ERROR, path, line, message);
        }

        public static Diagnostic warning(String path, int line, String message) {
            return new Diagnostic(Severity.WARNING, path, line, message);
        }

        @Override
        public String toString() {
            String where = line > 0 ? path + ":" + line : path;
            return severity + " " + where + " — " + message;
        }
    }
}
