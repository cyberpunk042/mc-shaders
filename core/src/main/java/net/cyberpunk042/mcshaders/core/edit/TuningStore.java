package net.cyberpunk042.mcshaders.core.edit;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;

/**
 * Where an editor's results live once the editor has closed.
 *
 * <p>Without this an {@link EditSession} is a scratch object: something builds one,
 * something changes values on it, and when the screen holding it goes away so do the
 * changes. That is not a small gap. It is the difference between an editor and a
 * screen that looks like an editor — every control works, every value coerces, undo
 * and redo behave, and none of it survives pressing Escape.
 *
 * <p>So a store has exactly two jobs, and {@link #sessionFor} and {@link #commit} are
 * the pair that does them: hand out a session that starts where the last one finished,
 * and take a finished session back. Anything reading tuned values later — a renderer,
 * a command, a config writer — reads {@link #get}.
 *
 * <h2>Not a registry</h2>
 *
 * <p>The registries beside this one are lifecycled: open during initialisation, frozen
 * afterwards, never written again, which is what lets the render path read them without
 * synchronisation. A tuning store is the opposite by design. Its whole purpose is to
 * change while the game runs, so it is backed by a concurrent map and is never frozen.
 * {@link EffectParams} is immutable, so a reader always sees one whole set of values
 * rather than half of two.
 *
 * <h2>What it does not do</h2>
 *
 * <p>It holds no schemas and validates nothing on {@link #put}. Values arriving through
 * {@link #commit} were coerced by the session that produced them; values arriving
 * through {@code put} are stored as given. It also does not persist across restarts —
 * this is in-memory tuning, not a config file.
 */
@Stable(since = "0.6.0")
public final class TuningStore {

    private final Map<String, EffectParams> tuned = new ConcurrentHashMap<>();

    /**
     * The tuned values for an effect type, if it has been edited.
     *
     * <p>Empty means untouched, which is not the same as tuned back to the defaults —
     * see {@link #commit} for why the difference is kept.
     */
    public Optional<EffectParams> get(String effectType) {
        return Optional.ofNullable(tuned.get(requireType(effectType)));
    }

    /**
     * The values an effect should be rendered with: tuned if it has been, otherwise
     * the schema's own defaults.
     *
     * <p>This is what a renderer wants, and it is a single call so that no caller has
     * to remember that "no entry" means "defaults" rather than "nothing".
     */
    public EffectParams effective(EffectSchema schema) {
        requireSchema(schema);
        return get(schema.effectType()).orElseGet(schema::defaults);
    }

    /**
     * A session starting from wherever this type was last left.
     *
     * <p>Opening an editor twice and seeing the second one back at the defaults is the
     * symptom this method exists to remove, so it deliberately does not offer a variant
     * that starts fresh — {@link EditSession#of(EffectSchema)} is still there for a
     * caller that genuinely wants one.
     */
    public EditSession sessionFor(EffectSchema schema) {
        requireSchema(schema);
        return EditSession.of(schema, effective(schema));
    }

    /**
     * Takes a finished — or in-progress — session's values and stores them.
     *
     * <p>The effect type comes from the session's own schema rather than from the
     * caller, so values cannot be filed under a type they do not belong to.
     *
     * <p>Committing values equal to the defaults still leaves an entry. The store
     * records what the editor last held, not whether it differs from the defaults, and
     * the alternative is worse: a session opened over stored values and closed without
     * a change looks identical to one reset to the defaults, so "remove when it matches
     * the defaults" would quietly discard earlier tuning. {@link #clear} is how an entry
     * goes away, and it says so at the call site.
     */
    public void commit(EditSession session) {
        if (session == null) {
            throw new IllegalArgumentException("Cannot commit a null session");
        }
        tuned.put(session.schema().effectType(), session.current());
    }

    /** Stores values directly. Prefer {@link #commit}, which cannot mismatch the type. */
    public void put(String effectType, EffectParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Cannot store null params for '" + effectType + "'");
        }
        tuned.put(requireType(effectType), params);
    }

    /**
     * Forgets an effect's tuning, so it returns to its schema's defaults.
     *
     * @return whether there was anything to forget
     */
    public boolean clear(String effectType) {
        return tuned.remove(requireType(effectType)) != null;
    }

    /** Forgets every effect's tuning. */
    public void clearAll() {
        tuned.clear();
    }

    /** Whether this type has been edited at all. */
    public boolean isTuned(String effectType) {
        return tuned.containsKey(requireType(effectType));
    }

    /**
     * The types that have been edited, in no particular order.
     *
     * <p>Unordered because the backing map is concurrent, and a store is not a
     * declaration order the way a registry is — nothing downstream should depend on
     * the sequence tuning happened to arrive in.
     */
    public List<String> tunedTypes() {
        return List.copyOf(tuned.keySet());
    }

    public boolean isEmpty() {
        return tuned.isEmpty();
    }

    public int size() {
        return tuned.size();
    }

    private static String requireType(String effectType) {
        if (effectType == null || effectType.isBlank()) {
            throw new IllegalArgumentException("Effect type must not be blank");
        }
        return effectType;
    }

    private static void requireSchema(EffectSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("Cannot tune a null schema");
        }
    }
}
