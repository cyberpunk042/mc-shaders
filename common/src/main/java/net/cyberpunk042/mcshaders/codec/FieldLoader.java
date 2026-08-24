package net.cyberpunk042.mcshaders.codec;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.field.FieldLayer;

/**
 * Turns a set of pack files into the field layers they declare.
 *
 * <p>{@link FieldCodec} reads one document. This is everything around that: many files
 * from many packs, what to do when one of them is wrong, and what to do when two of
 * them claim the same layer id. It is {@link BindingLoader} for fields, and
 * deliberately the same shape — a pack author who has learned what a skipped binding
 * looks like has learned what a skipped layer looks like.
 *
 * <p>Minecraft's part of a datapack reload is handing over the files. That part needs
 * the game; this part does not, which is why it lives here and is tested here.
 *
 * <h2>One bad file does not blank the world</h2>
 *
 * <p>A reload that failed wholesale on the first syntax error would mean one broken
 * pack — possibly not even the player's — takes every field down with it. So a file
 * that fails to parse is skipped and reported, and the rest load. The result carries
 * both halves: the layers that were built, and every problem found building them.
 *
 * <p>That choice is only defensible because the problems are not swallowed.
 * {@link Result#problems()} is the whole list, and a caller that logs nothing has
 * turned a loud failure into a silent one — which is worse than the crash it replaced.
 * Callers should log them.
 *
 * <h2>One file, one layer</h2>
 *
 * <p>This differs from {@link BindingLoader}, where a document may hold several
 * bindings: {@link FieldCodec#read(java.io.Reader, String)} reads exactly one layer,
 * so a file that declares two of them declares one and some ignored keys. That is
 * the codec's contract, not a limit imposed here, and it is why a skipped file costs
 * exactly one layer.
 *
 * <h2>Two packs, one id</h2>
 *
 * <p>Layer ids are how packs compose: two packs defining {@code portal_ring} are
 * saying something about the same layer, and the second one wins. That is the
 * behaviour a pack author expects from a load order.
 *
 * <p>The override is recorded as a problem too. It is not an error — it is what
 * overriding looks like — but a pack author whose layer silently stopped drawing
 * deserves to find out why without guessing.
 *
 * <h2>Why this hands back layers and not a registry</h2>
 *
 * <p>{@link BindingLoader} returns a {@code BindingRegistry} because there is one: it
 * sorts by priority and matches dimensions, work that has to happen somewhere. Fields
 * have no such behaviour to house, so inventing a {@code FieldRegistry} to wrap an
 * ordered map would add a type without adding a decision. {@link Result#layers()} is
 * that map, in load order, and {@link Result#layer(String)} is the lookup.
 */
public final class FieldLoader {

    private FieldLoader() {
    }

    /**
     * What a load produced: the layers, and everything that went wrong making them.
     *
     * @param layers   the layers that loaded, keyed by id in load order, which may be
     *                 empty
     * @param problems what was skipped or overridden, in the order encountered
     */
    public record Result(Map<String, FieldLayer> layers, List<Problem> problems) {

        public Result {
            layers = layers == null ? Map.of() : java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(layers));
            problems = problems == null ? List.of() : List.copyOf(problems);
        }

        /** Whether everything loaded cleanly. */
        public boolean isClean() {
            return problems.isEmpty();
        }

        /** Whether anything was skipped, as opposed to merely overridden. */
        public boolean hasFailures() {
            return problems.stream().anyMatch(p -> p.kind() == Problem.Kind.SKIPPED);
        }

        /** The layer with this id, or empty if no pack declared one. */
        public Optional<FieldLayer> layer(String id) {
            return Optional.ofNullable(layers.get(id));
        }
    }

    /** Something worth telling a pack author about. */
    public record Problem(Kind kind, String source, String message) {

        public enum Kind {
            /** The file did not parse, and its layer did not load. */
            SKIPPED,
            /** A layer replaced one of the same id from an earlier file. */
            OVERRIDDEN
        }

        @Override
        public String toString() {
            return switch (kind) {
                case SKIPPED -> "skipped " + source + ": " + message;
                case OVERRIDDEN -> source + ": " + message;
            };
        }
    }

    /**
     * Loads every file, skipping the ones that fail.
     *
     * @param files source name to content, in load order — later entries override
     *              earlier ones, so this wants an ordered map
     */
    public static Result load(Map<String, String> files) {
        List<Problem> problems = new ArrayList<>();
        // Keyed by layer id so a later file replaces an earlier one, and ordered so a
        // caller iterating the result sees pack load order rather than hash order.
        Map<String, FieldLayer> byId = new LinkedHashMap<>();
        Map<String, String> declaredIn = new LinkedHashMap<>();

        for (Map.Entry<String, String> file : files.entrySet()) {
            String source = file.getKey();
            FieldLayer parsed;
            try {
                parsed = FieldCodec.read(new StringReader(file.getValue()), source);
            } catch (PackException e) {
                problems.add(new Problem(Problem.Kind.SKIPPED, source, e.getMessage()));
                continue;
            }
            String previous = declaredIn.put(parsed.id(), source);
            if (previous != null) {
                problems.add(new Problem(Problem.Kind.OVERRIDDEN, source,
                        "layer '" + parsed.id() + "' overrides the one from " + previous));
            }
            byId.put(parsed.id(), parsed);
        }

        return new Result(byId, List.copyOf(problems));
    }

    /**
     * Loads from readers rather than strings, for a caller streaming out of a
     * resource manager.
     *
     * <p>Each reader is read once and not closed — closing what you did not open is
     * how a caller's try-with-resources ends up closing an already-closed stream.
     */
    public static Result loadReaders(Map<String, Reader> files) {
        Map<String, String> contents = new LinkedHashMap<>();
        List<Problem> problems = new ArrayList<>();
        for (Map.Entry<String, Reader> file : files.entrySet()) {
            try {
                contents.put(file.getKey(), readFully(file.getValue()));
            } catch (java.io.IOException e) {
                problems.add(new Problem(Problem.Kind.SKIPPED, file.getKey(),
                        "could not be read: " + e.getMessage()));
            }
        }
        Result loaded = load(contents);
        if (problems.isEmpty()) {
            return loaded;
        }
        // Read failures come first: they happened before any parsing did.
        List<Problem> all = new ArrayList<>(problems);
        all.addAll(loaded.problems());
        return new Result(loaded.layers(), all);
    }

    private static String readFully(Reader reader) throws java.io.IOException {
        StringBuilder out = new StringBuilder();
        char[] buffer = new char[8192];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            out.append(buffer, 0, read);
        }
        return out.toString();
    }
}
