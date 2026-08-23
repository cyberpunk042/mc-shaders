package net.cyberpunk042.mcshaders.sample;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.api.Experimental;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;

/**
 * Which bindings cannot work, because the world state they need cannot be sampled.
 *
 * <p>A gap in sampling is not visible from the outside. A binding gated on night, in a
 * game whose clock cannot be read, does not fail — it simply never activates, and that
 * looks exactly like a typo in the condition, a priority problem, or a pack that did
 * not load. The author has no way to tell those apart.
 *
 * <p>So the gap is turned into a list of names. Given the registry and what the
 * sampler cannot supply, this says <em>which bindings</em> are affected, so the mod can
 * log something an author can act on rather than leaving them to guess.
 *
 * <h2>Why it walks the whole condition tree</h2>
 *
 * <p>Conditions nest: {@code Any(TimeOfDay, Submerged)} still depends on the clock, and
 * {@code Not(All(TimeOfDay, ...))} does too. Checking only the outermost condition would
 * clear most affected bindings and report the gap as smaller than it is — which is worse
 * than not checking, because it would look like a completed audit.
 *
 * <h2>The compiler keeps this honest</h2>
 *
 * <p>{@code Condition} is a sealed interface, and the walk below switches over it with
 * no {@code default} branch. Adding a condition type therefore breaks this file's
 * compilation rather than letting it quietly return an incomplete answer — which is the
 * only way an audit like this stays true as the algebra grows. Do not add a
 * {@code default} to silence that; the error is the feature.
 */
@Experimental
public final class SamplingGaps {

    /** A piece of world state a condition can depend on. */
    public enum Field {
        /** Time of day. Not sampleable on 26.2 — see {@link WorldSample}. */
        DAY_TIME,
        /** The viewer's height. */
        Y_LEVEL,
        /** Rain and thunder. */
        WEATHER,
        /** Tags of the biome at the viewer. */
        BIOME_TAGS,
        /** Whether the camera is in a fluid. */
        SUBMERGED
    }

    /** What cannot currently be sampled, on any loader, on 26.2. */
    public static final Set<Field> UNAVAILABLE = Set.of(Field.DAY_TIME);

    private SamplingGaps() {
    }

    /**
     * Every field a condition consults, including through nesting.
     *
     * @param condition the condition to inspect; null reports nothing
     */
    public static Set<Field> dependencies(Condition condition) {
        Set<Field> found = new LinkedHashSet<>();
        collect(condition, found);
        return Set.copyOf(found);
    }

    /**
     * Ids of the bindings that depend on something the sampler cannot supply.
     *
     * <p>In registry order, so the same registry reports the same list and a log line
     * can be compared between runs.
     *
     * @param registry  the bindings in force; null reports nothing
     * @param missing   what cannot be sampled, usually {@link #UNAVAILABLE}
     * @return the affected binding ids, empty when everything they need is available
     */
    public static List<String> affected(BindingRegistry registry, Set<Field> missing) {
        if (registry == null || missing == null || missing.isEmpty()) {
            return List.of();
        }
        List<String> affected = new ArrayList<>();
        for (DimensionBinding binding : registry.all()) {
            Set<Field> needs = dependencies(binding.condition());
            if (needs.stream().anyMatch(missing::contains)) {
                affected.add(binding.id());
            }
        }
        return List.copyOf(affected);
    }

    /**
     * A line worth logging, or empty when there is nothing to say.
     *
     * <p>Names the bindings rather than the count. "3 bindings cannot be evaluated" is
     * a fact nobody can act on; the ids are the part an author needs.
     */
    public static String describe(BindingRegistry registry, Set<Field> missing) {
        List<String> affected = affected(registry, missing);
        if (affected.isEmpty()) {
            return "";
        }
        return "Cannot evaluate " + missing + " on this version, so these bindings will "
                + "never activate: " + String.join(", ", affected);
    }

    private static void collect(Condition condition, Set<Field> into) {
        switch (condition) {
            case null -> {
                // Nothing to inspect. A binding with no condition is unconditional.
            }
            case Condition.TimeOfDay ignored -> into.add(Field.DAY_TIME);
            case Condition.YRange ignored -> into.add(Field.Y_LEVEL);
            case Condition.InWeather ignored -> into.add(Field.WEATHER);
            case Condition.HasBiomeTag ignored -> into.add(Field.BIOME_TAGS);
            case Condition.Submerged ignored -> into.add(Field.SUBMERGED);
            case Condition.All all -> all.children().forEach(child -> collect(child, into));
            case Condition.Any any -> any.children().forEach(child -> collect(child, into));
            case Condition.Not not -> collect(not.child(), into);
            case Condition.Always ignored -> {
                // Depends on nothing.
            }
            case Condition.Never ignored -> {
                // Depends on nothing.
            }
        }
    }
}
