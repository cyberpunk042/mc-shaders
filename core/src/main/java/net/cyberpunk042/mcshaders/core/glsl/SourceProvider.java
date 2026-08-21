package net.cyberpunk042.mcshaders.core.glsl;

import java.util.Optional;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * Supplies GLSL source by path.
 *
 * <p>The seam that keeps {@link IncludeResolver} free of Minecraft: in the game
 * this reads from the resource manager, in tests it reads from a map, and in a
 * build-time checker it reads from disk. The resolver neither knows nor cares.
 *
 * <p>Paths are namespace-relative and always use {@code /} — e.g.
 * {@code shaders/post/include/core/noise_3d.glsl}.
 */
@FunctionalInterface
@Stable(since = "0.3.0")
public interface SourceProvider {

    /**
     * Reads the source at {@code path}.
     *
     * @return the content, or empty if there is no such file. Empty is an expected
     *         outcome, not an error — a shader may reference an include that a
     *         resource pack did not ship, and the resolver reports that as a
     *         diagnostic rather than an exception.
     */
    Optional<String> read(String path);
}
