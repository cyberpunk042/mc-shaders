package net.cyberpunk042.mcshaders.core.glsl;

import java.util.List;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * Maps GLSL compiler error positions back to the file they came from.
 *
 * <p>Expanding includes inline destroys the correspondence between the line the
 * driver complains about and the file a human has to edit — which is what makes
 * include-based GLSL painful to debug. The resolver emits {@code #line} directives
 * to preserve it, and this maps the resulting source indices back to paths.
 *
 * <p>GLSL 150's {@code #line} takes <em>integers only</em> — {@code #line 12 3} —
 * so the file name cannot be embedded in the directive. Hence this side table.
 *
 * @param paths file paths, indexed by the source number used in {@code #line}
 */
@Stable(since = "0.3.0")
public record SourceMap(List<String> paths) {

    public SourceMap {
        paths = List.copyOf(paths);
    }

    /** The path for a {@code #line} source index, if it is one this map knows. */
    public Optional<String> pathOf(int sourceIndex) {
        if (sourceIndex < 0 || sourceIndex >= paths.size()) {
            return Optional.empty();
        }
        return Optional.of(paths.get(sourceIndex));
    }

    /**
     * Renders a driver-reported position as something a human can act on.
     *
     * <p>Falls back to naming the raw index when it is unknown, rather than
     * pretending: an unrecognised index usually means the driver reported against
     * a source we did not generate.
     */
    public String describe(int sourceIndex, int line) {
        return pathOf(sourceIndex)
                .map(path -> path + ":" + line)
                .orElse("<unknown source " + sourceIndex + ">:" + line);
    }

    public int size() {
        return paths.size();
    }
}
