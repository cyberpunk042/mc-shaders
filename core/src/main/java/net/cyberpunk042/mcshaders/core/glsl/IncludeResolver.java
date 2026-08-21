package net.cyberpunk042.mcshaders.core.glsl;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * Expands {@code #include "path"} directives in GLSL.
 *
 * <p>GLSL has no include mechanism, so any shader library of real size grows one.
 * This is that mechanism, with the properties a shader author actually needs:
 *
 * <ul>
 *   <li><b>Include-once</b> — a file pulled in twice is expanded once, so shared
 *       dependencies do not produce duplicate definitions.</li>
 *   <li><b>Cycle-safe</b> — a cycle is reported with the chain that formed it,
 *       rather than recursing until the stack dies.</li>
 *   <li><b>Debuggable</b> — {@code #line} directives keep driver errors pointing at
 *       the file a human has to edit. Without them, every error lands on a line
 *       number in a concatenated blob.</li>
 *   <li><b>Total</b> — every call either returns a result or throws; it never
 *       returns its input unchanged as a failure signal.</li>
 * </ul>
 *
 * <p>That last property is deliberate. Returning the original source on failure
 * reads as harmless, but if the caller is a compile hook it re-enters the same
 * path with the same input and recurses until the stack overflows. Failures here
 * are reported as {@link ResolvedShader.Diagnostic}s attached to a result that is
 * always distinguishable from its input.
 *
 * <p>Stateless and thread-safe; caching belongs to the caller, which knows when
 * resources reload.
 */
@Stable(since = "0.3.0")
public final class IncludeResolver {

    /**
     * Matches {@code #include "path"}, allowing leading whitespace and a trailing
     * line comment.
     */
    private static final Pattern INCLUDE = Pattern.compile(
            "^\\s*#include\\s+\"([^\"]+)\"\\s*(?://.*)?$");

    /** Matches the {@code #version} directive, which must stay the first line. */
    private static final Pattern VERSION = Pattern.compile("^\\s*#version\\b.*$");

    /** Guards against pathological nesting even when no cycle is present. */
    public static final int MAX_DEPTH = 32;

    private final SourceProvider provider;
    private final int maxDepth;

    public IncludeResolver(SourceProvider provider) {
        this(provider, MAX_DEPTH);
    }

    public IncludeResolver(SourceProvider provider, int maxDepth) {
        if (provider == null) {
            throw new IllegalArgumentException("IncludeResolver requires a source provider");
        }
        if (maxDepth < 1) {
            throw new IllegalArgumentException("maxDepth must be at least 1, got " + maxDepth);
        }
        this.provider = provider;
        this.maxDepth = maxDepth;
    }

    /**
     * Expands the includes of the shader at {@code rootPath}.
     *
     * @throws IllegalArgumentException if the root itself cannot be read — unlike a
     *                                  missing include, a missing root is a caller
     *                                  error, not shader content to diagnose
     */
    public ResolvedShader resolve(String rootPath) {
        String source = provider.read(rootPath).orElseThrow(() -> new IllegalArgumentException(
                "Shader source not found: " + rootPath));
        return resolve(rootPath, source);
    }

    /** Expands includes in {@code source}, resolving relative paths against {@code rootPath}. */
    public ResolvedShader resolve(String rootPath, String source) {
        if (rootPath == null || rootPath.isBlank()) {
            throw new IllegalArgumentException("Root path must not be blank");
        }
        if (source == null) {
            throw new IllegalArgumentException("Source must not be null for " + rootPath);
        }

        Expansion expansion = new Expansion();
        StringBuilder out = new StringBuilder();

        // #version must remain the very first line, so it is lifted out before any
        // #line directive is emitted. A driver rejects a #version that is not first.
        String body = source;
        String versionLine = null;
        int bodyStartLine = 1;

        List<String> lines = splitLines(source);
        int firstCode = indexOfFirstSignificantLine(lines);
        if (firstCode >= 0 && VERSION.matcher(lines.get(firstCode)).matches()) {
            versionLine = lines.get(firstCode).strip();
            body = String.join("\n", lines.subList(firstCode + 1, lines.size()));
            bodyStartLine = firstCode + 2;
        }

        if (versionLine != null) {
            out.append(versionLine).append('\n');
        }

        expansion.markVisited(rootPath);
        expansion.expand(rootPath, body, bodyStartLine, 0, out);

        return new ResolvedShader(
                out.toString(),
                new SourceMap(new ArrayList<>(expansion.sourceIndices.keySet())),
                expansion.diagnostics);
    }

    /**
     * Resolves an include path against the directory of the file including it.
     *
     * <p>{@code shaders/post/a.fsh} including {@code include/b.glsl} yields
     * {@code shaders/post/include/b.glsl}. A path starting with {@code /} is treated
     * as namespace-absolute.
     */
    static String resolvePath(String includingFile, String includePath) {
        if (includePath.startsWith("/")) {
            return normalise(includePath.substring(1));
        }
        int lastSlash = includingFile.lastIndexOf('/');
        String dir = lastSlash < 0 ? "" : includingFile.substring(0, lastSlash + 1);
        return normalise(dir + includePath);
    }

    /** Collapses {@code .} and {@code ..} segments so equivalent paths compare equal. */
    private static String normalise(String path) {
        Deque<String> parts = new ArrayDeque<>();
        for (String segment : path.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) {
                continue;
            }
            if (segment.equals("..")) {
                // Popping past the root would escape the namespace; keep the segment
                // so the failure surfaces as "not found" against a visible path
                // rather than silently resolving somewhere unintended.
                if (!parts.isEmpty() && !parts.peekLast().equals("..")) {
                    parts.removeLast();
                } else {
                    parts.addLast(segment);
                }
                continue;
            }
            parts.addLast(segment);
        }
        return String.join("/", parts);
    }

    private static List<String> splitLines(String source) {
        // -1 keeps trailing empty lines, so line numbers stay faithful to the file.
        return List.of(source.split("\r\n|\r|\n", -1));
    }

    private static int indexOfFirstSignificantLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String stripped = lines.get(i).strip();
            if (!stripped.isEmpty() && !stripped.startsWith("//")) {
                return i;
            }
        }
        return -1;
    }

    /** Mutable state for one resolve call; never shared across calls. */
    private final class Expansion {
        private final Map<String, Integer> sourceIndices = new LinkedHashMap<>();
        private final Set<String> visited = new LinkedHashSet<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final List<ResolvedShader.Diagnostic> diagnostics = new ArrayList<>();

        void markVisited(String path) {
            visited.add(path);
        }

        int indexOf(String path) {
            return sourceIndices.computeIfAbsent(path, p -> sourceIndices.size());
        }

        void expand(String path, String source, int startLine, int depth, StringBuilder out) {
            stack.addLast(path);
            int index = indexOf(path);
            emitLineDirective(out, startLine, index);

            List<String> lines = splitLines(source);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                int lineNumber = startLine + i;

                Matcher matcher = INCLUDE.matcher(line);
                if (!matcher.matches()) {
                    out.append(line).append('\n');
                    continue;
                }

                String requested = matcher.group(1);
                String target = resolvePath(path, requested);

                // The cycle check must precede the include-once check. Include-once
                // alone already prevents infinite recursion, so a cycle would be
                // silently swallowed as "already expanded" — but in GLSL that is not
                // benign: declaration order is load-bearing, so a cycle means
                // something is referenced before it is declared. Report it.
                if (stack.contains(target)) {
                    diagnostics.add(ResolvedShader.Diagnostic.error(path, lineNumber,
                            "Circular include: " + String.join(" -> ", stack) + " -> " + target
                                    + ". GLSL has no forward declarations, so the cycle would "
                                    + "reference symbols before they are declared."));
                    out.append("// circular include skipped: ").append(requested).append('\n');
                    emitLineDirective(out, lineNumber + 1, index);
                    continue;
                }

                if (visited.contains(target)) {
                    // Include-once: already expanded, so this is a no-op, not a fault.
                    out.append("// include-once: ").append(requested).append('\n');
                    emitLineDirective(out, lineNumber + 1, index);
                    continue;
                }

                if (depth + 1 > maxDepth) {
                    diagnostics.add(ResolvedShader.Diagnostic.error(path, lineNumber,
                            "Include depth exceeded " + maxDepth + " at " + target));
                    out.append("// include depth exceeded: ").append(requested).append('\n');
                    emitLineDirective(out, lineNumber + 1, index);
                    continue;
                }

                Optional<String> content = provider.read(target);
                if (content.isEmpty()) {
                    diagnostics.add(ResolvedShader.Diagnostic.error(path, lineNumber,
                            "Include not found: \"" + requested + "\" (resolved to " + target + ")"));
                    out.append("// include not found: ").append(requested).append('\n');
                    emitLineDirective(out, lineNumber + 1, index);
                    continue;
                }

                visited.add(target);
                expand(target, content.get(), 1, depth + 1, out);
                // Resume the parent's numbering so errors after the include still
                // point at the right line of the right file.
                emitLineDirective(out, lineNumber + 1, index);
            }

            stack.removeLast();
        }

        private void emitLineDirective(StringBuilder out, int nextLine, int sourceIndex) {
            // GLSL 150 takes integers only, so the file name lives in the SourceMap.
            out.append("#line ").append(nextLine).append(' ').append(sourceIndex).append('\n');
        }
    }
}
