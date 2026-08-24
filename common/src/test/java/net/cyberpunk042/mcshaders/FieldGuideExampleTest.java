package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.codec.FieldCodec;
import net.cyberpunk042.mcshaders.codec.FieldLoader;
import net.cyberpunk042.mcshaders.core.appearance.Appearance;
import net.cyberpunk042.mcshaders.core.field.FieldLayer;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The library guide's two field-JSON examples, run — and checked against the guide.
 *
 * <p>Running an example proves the API exists and behaves. It does not prove the
 * document still says so: a transcribed example goes stale the moment someone edits
 * the markdown, and a test that only runs its own copy would keep passing while the
 * page told a reader something else. So each example is asserted to still appear in
 * the guide, verbatim.
 *
 * <p>That is the same failure the guide's other examples had before
 * {@code LibraryApiDocExampleTest} — the code a third party integrates against,
 * pinned by nothing.
 */
class FieldGuideExampleTest {

    private static final String GUIDE = "docs/USING_AS_A_LIBRARY.md";

    private static FieldLayer sun() {
        return FieldLayer.of("sun", List.of(
                SimplePrimitive.of("core", SphereShape.of(1.0f).getType(), SphereShape.of(1.0f))
                        .withAppearance(Appearance.glowing("#ffd27f", 0.9f))));
    }

    @Test
    @DisplayName("the round-trip example works, and the guide still shows it")
    void roundTripExample() {
        FieldLayer layer = sun();

        // From the guide, verbatim:
        JsonObject json = FieldCodec.write(layer);
        FieldLayer back = FieldCodec.read(json.toString(), "sun.json");

        assertEquals(layer, back, "the guide promises equals(layer)");

        assertGuideShows("JsonObject json = FieldCodec.write(layer);");
        assertGuideShows("Only `id` is required.");
        assertGuideShows(
                "FieldCodec.write(FieldLayer.empty(\"empty\")).toString();   // {\"id\":\"empty\"}");
        assertGuideShows(
                "FieldLayer back = FieldCodec.read(json.toString(), \"sun.json\");");
    }

    @Test
    @DisplayName("the loader example works, and the guide still shows it")
    void loaderExample() {
        Map<String, String> files = Map.of("sun.json", FieldCodec.write(sun()).toString());

        // From the guide, verbatim:
        var result = FieldLoader.load(files);
        FieldLayer sun = result.layer("sun").orElseThrow();

        assertEquals("sun", sun.id());
        assertTrue(result.isClean(), () -> "problems: " + result.problems());

        assertGuideShows("var result = FieldLoader.load(files);");
        assertGuideShows("FieldLayer sun = result.layer(\"sun\").orElseThrow();");
    }

    @Test
    @DisplayName("the guide's claim that hasFailures() excludes overrides holds")
    void overrideIsNotAFailure() {
        String layer = FieldCodec.write(sun()).toString();
        Map<String, String> files = new java.util.LinkedHashMap<>();
        files.put("base.json", layer);
        files.put("override.json", layer);

        FieldLoader.Result result = FieldLoader.load(files);

        assertTrue(result.hasFailures() == false,
                "the guide says hasFailures() is true only when something was skipped");
        assertTrue(result.problems().size() == 1, "the override is still reported");

        // Deliberately ASCII: a snippet spanning the sentence's em-dash would make
// this assertion depend on the file encoding as well as on the wording.
        assertGuideShows("`hasFailures()` distinguishes the two");
    }

    @Test
    @DisplayName("the guide's claim that layers() is unmodifiable holds")
    void layersAreUnmodifiable() {
        FieldLoader.Result result = FieldLoader.load(
                Map.of("sun.json", FieldCodec.write(sun()).toString()));

        assertTrue(result.layers() instanceof Map, "sanity");
        try {
            result.layers().clear();
            throw new AssertionError("the guide says this map is unmodifiable");
        } catch (UnsupportedOperationException expected) {
            // what the guide promises
        }

        assertGuideShows("keyed by id in load order, and unmodifiable");
    }

    private static void assertGuideShows(String snippet) {
        String guide = read(repoRoot().resolve(GUIDE));
        assertTrue(guide.contains(snippet),
                () -> "the guide no longer contains this, so the example is stale:\n" + snippet);
    }

    private static String read(Path p) {
        try {
            return Files.readString(p);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path repoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("LICENSE"))
                    && Files.isRegularFile(dir.resolve(GUIDE))) {
                return dir;
            }
        }
        throw new AssertionError("could not find the repository root");
    }
}
