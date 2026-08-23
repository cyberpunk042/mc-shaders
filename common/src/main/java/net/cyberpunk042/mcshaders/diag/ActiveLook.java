package net.cyberpunk042.mcshaders.diag;

import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.api.Experimental;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.WorldState;

/**
 * Which bindings are winning, said once each time the answer changes.
 *
 * <p>{@link ChainCheck} answers "did the machinery run". This answers the question
 * after it: <em>is it doing the right thing</em>. With several bindings on one
 * dimension, separated by condition and priority, a look that is subtly wrong and a
 * look that is correct are equally silent — and walking into a forest during a storm
 * should visibly pick one binding over another, with no way to confirm which it chose.
 *
 * <h2>On change, not per frame</h2>
 *
 * <p>Reporting every frame would be sixty lines a second saying the same thing. What
 * carries information is the transition, so this reports only when the winning set
 * changes — which is also exactly when a player crosses a biome edge, a storm starts,
 * or they step through a portal.
 *
 * <p>Deliberately keyed on binding <em>ids</em>, not on the resolved stack. A stack
 * eases continuously while a transition runs, so keying on it would report every
 * frame of every fade; the set of bindings that won is stable across that.
 */
@Experimental
public final class ActiveLook {

    /**
     * Null means nothing has been reported yet, which is NOT the same as an empty set.
     *
     * <p>This started as an empty list and the two states collapsed: the first look in
     * a dimension with no bindings compared equal to "never reported" and was silently
     * suppressed — precisely the case worth hearing about, since a dimension the pack
     * was supposed to cover reporting nothing is the alarming answer.
     */
    private static volatile List<String> lastSeen;

    static {
        // Through reset() rather than an initialiser of its own. When the two were
        // written separately, mutating the initialiser changed nothing any test could
        // see — every test calls reset() first, so the field's real starting value was
        // exercised only in production. One definition, one behaviour.
        reset();
    }

    private ActiveLook() {
    }

    /**
     * A line worth logging, or null when nothing has changed.
     *
     * @param registry what is in force
     * @param state    this frame's world
     * @return a description of the new winning set, or null
     */
    public static String describeIfChanged(BindingRegistry registry, WorldState state) {
        if (registry == null || state == null) {
            return null;
        }
        List<String> ids = activeIds(registry, state);
        if (lastSeen != null && ids.equals(lastSeen)) {
            return null;
        }
        lastSeen = ids;
        return describe(ids, state);
    }

    /** Active binding ids in priority order — the order they merge in. */
    static List<String> activeIds(BindingRegistry registry, WorldState state) {
        List<DimensionBinding> active = registry.active(state);
        List<String> ids = new ArrayList<>(active.size());
        for (DimensionBinding binding : active) {
            ids.add(binding.id());
        }
        return List.copyOf(ids);
    }

    /**
     * The line itself.
     *
     * <p>Names the dimension as well as the bindings: "nothing is active" is the
     * expected answer in most of the world and an alarming one in a dimension the pack
     * was supposed to cover, and only the dimension tells those apart.
     */
    static String describe(List<String> ids, WorldState state) {
        if (ids.isEmpty()) {
            return "No binding is active in " + state.dimension() + " — it keeps its default look.";
        }
        return "Active in " + state.dimension() + ", in merge order: " + String.join(" -> ", ids);
    }

    /** Forgets what was last seen, so the next call reports. For tests. */
    public static void reset() {
        lastSeen = null;
    }
}
