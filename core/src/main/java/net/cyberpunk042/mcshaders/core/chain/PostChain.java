package net.cyberpunk042.mcshaders.core.chain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.api.Stable;

/**
 * A sequence of full-screen passes and the intermediate targets they hand between
 * each other.
 *
 * <p>This is the concrete, authored form of a post-processing effect — the level a
 * shader pack works at, below
 * {@link net.cyberpunk042.mcshaders.core.graph.EffectGraph}'s backend-neutral
 * description. Content that already exists as pipeline definitions arrives here;
 * core models and checks it without owning it, which is what lets the engine stay
 * MIT while the shaders it loads stay under their own terms.
 *
 * <p>Core does no parsing. Building one of these from JSON, or from anything else,
 * belongs to a codec layer above.
 *
 * @param targets the intermediate targets, by name
 * @param passes  the passes, in execution order
 */
@Stable(since = "0.4.0")
public record PostChain(Map<String, TargetSpec> targets, List<Pass> passes) {

    public PostChain {
        targets = Collections.unmodifiableMap(new LinkedHashMap<>(targets));
        passes = List.copyOf(passes);
    }

    /** Whether {@code name} is a target this chain declares. */
    public boolean declares(String name) {
        return targets.containsKey(name);
    }
}
