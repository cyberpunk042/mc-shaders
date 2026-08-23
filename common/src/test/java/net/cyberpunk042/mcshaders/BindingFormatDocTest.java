package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.codec.BindingLoader;
import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.Weather;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Executes {@code docs/BINDINGS.md}, which is the format reference a pack author reads.
 *
 * <p>Before it existed, the project told authors a look was theirs to write in datapack
 * JSON and then documented four of the nine condition types in prose, none of their
 * fields, and neither field list. The only complete statement of the format was
 * {@code BindingCodec} itself. A reference fixes that exactly once and then starts
 * rotting, so every snippet on the page is parsed here rather than trusted.
 *
 * <p>Blocks come in three shapes and each is executed as what it is: a whole binding,
 * an array of them, a bare condition, or a bare parameter object. The last two are
 * wrapped in the smallest binding that can carry them — a fragment nobody parses is
 * the thing this test exists to prevent.
 */
class BindingFormatDocTest {

    private static final String DOC = "docs/BINDINGS.md";

    // ── reading the document ─────────────────────────────────────────────────

    private static List<String> jsonBlocks(String docName) {
        Path doc = findDoc(docName);
        List<String> blocks = new ArrayList<>();
        try {
            StringBuilder current = null;
            for (String line : Files.readAllLines(doc)) {
                if (line.startsWith("```json")) {
                    current = new StringBuilder();
                } else if (line.startsWith("```") && current != null) {
                    blocks.add(current.toString());
                    current = null;
                } else if (current != null) {
                    current.append(line).append('\n');
                }
            }
        } catch (IOException e) {
            throw new AssertionError("could not read " + doc, e);
        }
        return blocks;
    }

    private static Path findDoc(String name) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new AssertionError("could not find " + name + " above " + Path.of("").toAbsolutePath());
    }

    /** Wraps a fragment into the smallest binding that can carry it, so it gets parsed. */
    private static String asLoadableBinding(String block) {
        String trimmed = block.strip();
        if (trimmed.startsWith("[") || trimmed.contains("\"dimension\"")) {
            return trimmed;                        // already a binding, or an array of them
        }
        if (trimmed.contains("\"type\"")) {        // a bare condition
            return """
                    { "id": "doc_fragment", "dimension": "minecraft:overworld",
                      "condition": %s }""".formatted(trimmed);
        }
        return """                                 
                { "id": "doc_fragment", "dimension": "minecraft:overworld",
                  "stack": { "layers": [
                    { "id": "doc_layer", "kind": "custom", "params": %s }
                  ] } }""".formatted(trimmed);     // a bare parameter object
    }

    // ── the tests ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("every example in the format reference is one the loader accepts")
    void everyBlockParses() {
        List<String> blocks = jsonBlocks(DOC);
        assertTrue(blocks.size() >= 6,
                "the reference should carry an example per section, found " + blocks.size());

        for (int i = 0; i < blocks.size(); i++) {
            String source = DOC + " block " + (i + 1);
            BindingLoader.Result result =
                    BindingLoader.load(Map.of(source, asLoadableBinding(blocks.get(i))));

            assertTrue(result.isClean(),
                    "a reader copying " + source + " would get: " + result.problems());
            assertFalse(result.registry().isEmpty(), source + " defined no bindings");
        }
    }

    /** The smallest condition of each type that the codec will accept. */
    private static final Map<String, String> CONDITION_FIXTURES = Map.ofEntries(
            Map.entry("always", """
                    { "type": "always" }"""),
            Map.entry("never", """
                    { "type": "never" }"""),
            Map.entry("submerged", """
                    { "type": "submerged" }"""),
            Map.entry("y_range", """
                    { "type": "y_range", "min": 0.0, "max": 48.0 }"""),
            Map.entry("weather", """
                    { "type": "weather", "weather": "thunder" }"""),
            Map.entry("biome_tag", """
                    { "type": "biome_tag", "tag": "#minecraft:is_forest" }"""),
            Map.entry("time_of_day", """
                    { "type": "time_of_day", "from": 13000, "to": 23000 }"""),
            Map.entry("all", """
                    { "type": "all", "of": [{ "type": "always" }] }"""),
            Map.entry("any", """
                    { "type": "any", "of": [{ "type": "always" }] }"""),
            Map.entry("not", """
                    { "type": "not", "of": { "type": "submerged" } }"""));

    @Test
    @DisplayName("every condition type the codec admits to is one it parses and one the reference lists")
    void conditionTypesAreParseableAndDocumented() {
        // Derived from the codec rather than restated here. A list copied into a test
        // is a second thing to keep in step, and it would pass while the codec grew a
        // type nobody wrote down — which is the drift this is meant to catch.
        BindingLoader.Result rejected = BindingLoader.load(Map.of("x", """
                { "id": "x", "dimension": "minecraft:overworld",
                  "condition": { "type": "no_such_condition" } }"""));
        assertFalse(rejected.isClean(), "an unknown condition type should be reported");

        String message = rejected.problems().toString();
        int listed = message.indexOf("Known types:");
        assertTrue(listed >= 0, "the error should name the types it knows; it said: " + message);
        List<String> known = List.of(message.substring(listed + "Known types:".length())
                .replaceAll("[^a-z_,]", "").split(","));
        assertEquals(10, known.size(), "unexpected condition-type count in " + known);

        String doc = read(DOC);
        for (String type : known) {
            assertTrue(doc.contains("`" + type + "`"),
                    "the codec offers condition type '" + type + "' and the reference omits it");

            String fixture = CONDITION_FIXTURES.get(type);
            assertNotNull(fixture, "no fixture for condition type '" + type
                    + "' — the codec grew one; document it and add it here");
            BindingLoader.Result parsed = BindingLoader.load(Map.of(type, """
                    { "id": "c", "dimension": "minecraft:overworld", "condition": %s }"""
                    .formatted(fixture)));
            assertTrue(parsed.isClean(),
                    "'" + type + "' is offered by the error message but did not parse: "
                            + parsed.problems());
        }
    }

    @Test
    @DisplayName("the reference lists every kind and blend mode, spelled as JSON takes them")
    void enumTablesAreComplete() {
        String doc = read(DOC);
        for (EffectKind kind : EffectKind.values()) {
            assertTrue(doc.contains("`" + kind.name().toLowerCase() + "`"),
                    "kind '" + kind + "' is not in the reference");
        }
        for (BlendMode blend : BlendMode.values()) {
            assertTrue(doc.contains("`" + blend.name().toLowerCase() + "`"),
                    "blend mode '" + blend + "' is not in the reference");
        }
        for (Weather weather : Weather.values()) {
            assertTrue(doc.contains(weather.name().toLowerCase()),
                    "weather '" + weather + "' is not in the reference");
        }
    }

    @Test
    @DisplayName("merging by layer id works the way the reference says it does")
    void sameLayerIdReplacesRatherThanAdds() {
        // This is the claim the reference leads with, so it is the one worth executing:
        // same id replaces, different id adds, and priority decides which wins.
        BindingLoader.Result result = BindingLoader.load(Map.of("doc", """
                [
                  { "id": "base", "dimension": "minecraft:overworld", "priority": 0,
                    "stack": { "layers": [
                      { "id": "atmosphere", "kind": "fog", "params": { "start": 48.0 } },
                      { "id": "grade", "kind": "color_grade" }
                    ] } },
                  { "id": "deep", "dimension": "minecraft:overworld", "priority": 10,
                    "stack": { "layers": [
                      { "id": "atmosphere", "kind": "fog", "params": { "start": 4.0 } }
                    ] } }
                ]"""));
        assertTrue(result.isClean(), result.problems().toString());

        BindingRegistry registry = result.registry();
        EffectStack merged = registry.resolve(WorldState.of(DimensionId.minecraft("overworld")));

        assertEquals(2, merged.layers().size(),
                "redefining one layer should leave the other intact, not replace the stack");
        assertNotNull(merged.byId("grade").orElse(null),
                "the lower-priority binding's other layer should survive");
        assertEquals(4.0,
                merged.byId("atmosphere").orElseThrow()
                        .params().scalar("start").orElseThrow(),
                1e-9,
                "the higher-priority binding should win for the layer it redefines");
    }

    @Test
    @DisplayName("a quoted number is text, exactly as the reference warns")
    void theDocumentedTrapIsReal() {
        BindingLoader.Result result = BindingLoader.load(Map.of("doc", """
                { "id": "trap", "dimension": "minecraft:overworld",
                  "stack": { "layers": [
                    { "id": "l", "kind": "custom", "params": { "speed": "0.5" } }
                  ] } }"""));

        assertTrue(result.isClean(), "a quoted number is legal JSON for a text parameter");
        var params = result.registry().all().get(0).stack().layers().get(0).params();
        assertTrue(params.scalar("speed").isEmpty(),
                "if this ever became a scalar, the warning in the reference is wrong");
        assertEquals("0.5", params.text("speed").orElseThrow(),
                "it should have been read as text, which is what makes the trap a trap");
    }

    private static String read(String name) {
        try {
            return Files.readString(findDoc(name));
        } catch (IOException e) {
            throw new AssertionError("could not read " + name, e);
        }
    }
}
