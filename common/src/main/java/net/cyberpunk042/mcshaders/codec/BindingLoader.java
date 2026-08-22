package net.cyberpunk042.mcshaders.codec;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;

/**
 * Turns a set of pack files into a registry.
 *
 * <p>{@link BindingCodec} reads one document. This is everything around that: many
 * files from many packs, what to do when one of them is wrong, and what to do when
 * two of them claim the same binding id.
 *
 * <p>Minecraft's part of a datapack reload is handing over the files. That part needs
 * the game; this part does not, which is why it lives here and is tested here.
 *
 * <h2>One bad file does not blank the world</h2>
 *
 * <p>A reload that failed wholesale on the first syntax error would mean one broken
 * pack — possibly not even the player's — takes every dimension's visuals down with
 * it. So a file that fails to parse is skipped and reported, and the rest load. The
 * result carries both halves: the registry that was built, and every problem found
 * building it.
 *
 * <p>That choice is only defensible because the problems are not swallowed.
 * {@link Result#problems()} is the whole list, and a caller that logs nothing has
 * turned a loud failure into a silent one — which is worse than the crash it
 * replaced. Callers should log them.
 *
 * <h2>Two packs, one id</h2>
 *
 * <p>Binding ids are how packs compose: two packs defining {@code nether_base} are
 * saying something about the same binding, and the second one wins. That is the
 * behaviour a pack author expects from a load order, and it is the reason
 * {@code BindingRegistry.of} is given a de-duplicated list rather than being asked to
 * resolve collisions it has no load order to resolve them with.
 *
 * <p>The override is recorded as a problem too. It is not an error — it is what
 * overriding looks like — but a pack author whose binding silently stopped applying
 * deserves to find out why without guessing.
 */
public final class BindingLoader {

    private BindingLoader() {
    }

    /**
     * What a load produced: the registry, and everything that went wrong making it.
     *
     * @param registry the bindings that loaded, which may be empty
     * @param problems what was skipped or overridden, in the order encountered
     */
    public record Result(BindingRegistry registry, List<Problem> problems) {

        public Result {
            registry = registry == null ? BindingRegistry.empty() : registry;
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
    }

    /** Something worth telling a pack author about. */
    public record Problem(Kind kind, String source, String message) {

        public enum Kind {
            /** The file did not parse, and none of its bindings loaded. */
            SKIPPED,
            /** A binding replaced one of the same id from an earlier file. */
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
        // Keyed by binding id so a later file replaces an earlier one. Ordered for
        // the sake of the problem list and of anything reading this map, NOT for the
        // registry's benefit: BindingRegistry.of copies into an unordered map and
        // sorts by priority then id, so file order does not survive into it and
        // nothing here should imply it does.
        Map<String, DimensionBinding> byId = new LinkedHashMap<>();
        Map<String, String> declaredIn = new LinkedHashMap<>();

        for (Map.Entry<String, String> file : files.entrySet()) {
            String source = file.getKey();
            List<DimensionBinding> parsed;
            try {
                parsed = BindingCodec.readAll(new StringReader(file.getValue()), source);
            } catch (PackException e) {
                problems.add(new Problem(Problem.Kind.SKIPPED, source, e.getMessage()));
                continue;
            }
            for (DimensionBinding binding : parsed) {
                String previous = declaredIn.put(binding.id(), source);
                if (previous != null) {
                    problems.add(new Problem(Problem.Kind.OVERRIDDEN, source,
                            "binding '" + binding.id() + "' overrides the one from " + previous));
                }
                byId.put(binding.id(), binding);
            }
        }

        return new Result(BindingRegistry.of(List.copyOf(byId.values())), List.copyOf(problems));
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
        return new Result(loaded.registry(), all);
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
