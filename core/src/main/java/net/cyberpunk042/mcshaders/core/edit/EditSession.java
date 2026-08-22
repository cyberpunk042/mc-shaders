package net.cyberpunk042.mcshaders.core.edit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;

/**
 * One editing sitting: the values being edited, what may be done to them, and how
 * to take it back.
 *
 * <p>{@link EffectSchema} says what is tunable and {@link EffectParams} holds the
 * values. Between them sits the part every editor needs and none should reinvent —
 * coercing an edit to what the schema permits, remembering enough to undo it, and
 * knowing what has actually changed since the sitting began.
 *
 * <p>It carries no toolkit types, so it is testable without a screen, and the same
 * session drives whatever draws it.
 *
 * <h2>Why the session performs the edit</h2>
 *
 * <p>The obvious design is a history object a caller pushes to before mutating.
 * That works until someone forgets, and then an edit is quietly un-undoable — a
 * bug that shows up long after the omission and nowhere near it. Here the session
 * owns the mutation, so the history cannot be forgotten: there is no way to change
 * a value without going through the thing that records it.
 */
@Stable(since = "0.5.0")
public final class EditSession {

    /** How many steps back a sitting remembers before dropping its oldest. */
    public static final int DEFAULT_HISTORY_LIMIT = 64;

    private final EffectSchema schema;
    private final EffectParams original;
    private final int historyLimit;
    private final Deque<EffectParams> undone = new ArrayDeque<>();
    private final Deque<EffectParams> redone = new ArrayDeque<>();
    private EffectParams current;

    private EditSession(EffectSchema schema, EffectParams start, int historyLimit) {
        this.schema = schema;
        this.original = schema.coerce(start);
        this.current = this.original;
        this.historyLimit = historyLimit;
    }

    /** A sitting starting from the schema's own defaults. */
    public static EditSession of(EffectSchema schema) {
        return new EditSession(schema, schema.defaults(), DEFAULT_HISTORY_LIMIT);
    }

    /**
     * A sitting starting from values already in hand.
     *
     * <p>They are coerced on the way in, so a session never begins holding
     * something its own schema would refuse.
     */
    public static EditSession of(EffectSchema schema, EffectParams start) {
        return new EditSession(schema, start, DEFAULT_HISTORY_LIMIT);
    }

    public static EditSession of(EffectSchema schema, EffectParams start, int historyLimit) {
        if (historyLimit < 1) {
            throw new IllegalArgumentException("history limit must be at least 1, got " + historyLimit);
        }
        return new EditSession(schema, start, historyLimit);
    }

    public EffectSchema schema() {
        return schema;
    }

    /** The values as they stand. */
    public EffectParams current() {
        return current;
    }

    /** The values this sitting began with. */
    public EffectParams original() {
        return original;
    }

    public Optional<ParamValue> get(String key) {
        return current.get(key);
    }

    /**
     * Sets a value, coerced to what its spec permits.
     *
     * <p>A key the schema does not describe is set as given. An effect may
     * legitimately carry parameters no control reaches, and refusing to hold them
     * would make an editor lose data it never showed.
     *
     * @return whether anything actually changed
     */
    public boolean set(String key, ParamValue value) {
        Optional<ParamSpec> spec = schema.parameter(key);
        ParamValue coerced = spec.map(s -> s.coerce(value)).orElse(value);
        if (coerced == null) {
            return false;
        }
        return apply(current.with(key, coerced));
    }

    /** Puts one parameter back to the value its schema declares. */
    public boolean reset(String key) {
        return schema.parameter(key)
                .map(spec -> apply(current.with(key, spec.fallback())))
                .orElse(false);
    }

    /** Puts everything back to where this sitting started. */
    public boolean resetAll() {
        return apply(original);
    }

    /** Whether anything differs from where this sitting started. */
    public boolean isDirty() {
        return !current.equals(original);
    }

    /**
     * The keys whose values differ from where this sitting started.
     *
     * <p>What an editor needs to mark as touched, and what a caller needs to save
     * if it only wants to record deliberate changes.
     */
    public Set<String> changedKeys() {
        Set<String> changed = new LinkedHashSet<>();
        for (String key : current.keys()) {
            if (!current.get(key).equals(original.get(key))) {
                changed.add(key);
            }
        }
        for (String key : original.keys()) {
            if (current.get(key).isEmpty()) {
                changed.add(key);
            }
        }
        return changed;
    }

    public boolean canUndo() {
        return !undone.isEmpty();
    }

    public boolean canRedo() {
        return !redone.isEmpty();
    }

    /** @return whether anything was undone */
    public boolean undo() {
        if (undone.isEmpty()) {
            return false;
        }
        redone.push(current);
        current = undone.pop();
        return true;
    }

    /** @return whether anything was redone */
    public boolean redo() {
        if (redone.isEmpty()) {
            return false;
        }
        undone.push(current);
        current = redone.pop();
        return true;
    }

    /** How many steps back this sitting can go. */
    public int historyDepth() {
        return undone.size();
    }

    /**
     * Records the current values and moves to {@code next}.
     *
     * <p>A change that changes nothing is not recorded. Dragging a slider away and
     * back would otherwise fill the history with steps that do nothing when undone,
     * and an undo that appears to do nothing is worse than no undo at all.
     */
    private boolean apply(EffectParams next) {
        if (next.equals(current)) {
            return false;
        }
        undone.push(current);
        // A new edit is a new branch; whatever was undone is no longer reachable.
        redone.clear();
        while (undone.size() > historyLimit) {
            undone.removeLast();
        }
        current = next;
        return true;
    }
}
