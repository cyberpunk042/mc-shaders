package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.codec.FieldCodec;
import net.cyberpunk042.mcshaders.codec.FieldLoader;
import net.cyberpunk042.mcshaders.core.field.FieldLayer;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a reload does with several field files, one of which is broken.
 *
 * <p>Same shape as {@code BindingLoaderTest}, and for the same reason: the half that
 * matters is what a player sees when one pack in their folder has a typo, and the
 * answer has to be "the other packs still work, and something says which one was
 * wrong".
 */
class FieldLoaderTest {

    /**
     * A layer with one sphere, which is the smallest thing that draws.
     *
     * <p>Written by the codec rather than spelled out here. A hand-typed fixture would
     * be a second, silently drifting statement of what a shape looks like on disk; the
     * codec is the only one this project has, and the loader's job is what surrounds
     * it, not what it emits.
     */
    private static String layer(String id, float radius) {
        return FieldCodec.write(FieldLayer.of(id, List.of(
                SimplePrimitive.of("core", SphereShape.of(radius).getType(),
                        SphereShape.of(radius))))).toString();
    }

    /** The same, with a second primitive whose shape names a type that does not exist. */
    private static String layerWithOneBadPrimitive(String id) {
        String good = layer(id, 1.0f);
        String bad = "{\"id\":\"bad\",\"type\":\"banana\",\"shape\":{\"type\":\"banana\"}}";
        int close = good.lastIndexOf("]");
        return good.substring(0, close) + "," + bad + good.substring(close);
    }

    private static Map<String, String> files(String... pairs) {
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            out.put(pairs[i], pairs[i + 1]);
        }
        return out;
    }

    @Nested
    @DisplayName("the happy path")
    class Loading {

        @Test
        @DisplayName("several files become several layers, keyed by id")
        void severalFilesLoad() {
            FieldLoader.Result result = FieldLoader.load(files(
                    "sun.json", layer("sun", 1.0f),
                    "rings.json", layer("magic_circle", 1.4f)));

            assertTrue(result.isClean(), () -> "problems: " + result.problems());
            assertEquals(2, result.layers().size());
            assertTrue(result.layer("sun").isPresent());
            assertTrue(result.layer("magic_circle").isPresent());
        }

        @Test
        @DisplayName("the layer that loaded is the layer that was written")
        void contentSurvives() {
            FieldLoader.Result result = FieldLoader.load(files("sun.json", layer("sun", 1.0f)));

            assertEquals(1, result.layer("sun").orElseThrow().primitives().size(),
                    "the primitive has to survive the loader, not just the id");
        }

        @Test
        @DisplayName("load order is the iteration order of the result")
        void loadOrderIsKept() {
            FieldLoader.Result result = FieldLoader.load(files(
                    "b.json", layer("second", 1.0f),
                    "a.json", layer("first", 1.0f)));

            assertEquals(List.of("second", "first"), List.copyOf(result.layers().keySet()),
                    "file order, not alphabetical — nothing here re-sorts");
        }

        @Test
        @DisplayName("no files is no layers, not a failure")
        void emptyLoadIsClean() {
            FieldLoader.Result result = FieldLoader.load(Map.of());

            assertTrue(result.isClean());
            assertTrue(result.layers().isEmpty());
        }

        @Test
        @DisplayName("the result's map cannot be edited under its owner")
        void layersAreImmutable() {
            FieldLoader.Result result = FieldLoader.load(files("sun.json", layer("sun", 1.0f)));

            // A reload's result is shared: the renderer reads it while the next reload
            // is built. A caller that could clear() it would take fields off the screen
            // from somewhere with no idea it had.
            assertThrows(UnsupportedOperationException.class, () -> result.layers().clear());
        }

        @Test
        @DisplayName("a layer nobody declared is absent, not null")
        void unknownIdIsEmpty() {
            FieldLoader.Result result = FieldLoader.load(files("sun.json", layer("sun", 1.0f)));

            assertTrue(result.layer("no_such_layer").isEmpty());
        }
    }

    @Nested
    @DisplayName("one bad file")
    class Failures {

        @Test
        @DisplayName("the broken file is skipped and the others still load")
        void badFileDoesNotBlankTheWorld() {
            FieldLoader.Result result = FieldLoader.load(files(
                    "good.json", layer("keeps_working", 1.0f),
                    "broken.json", "{ not json at all",
                    "also_good.json", layer("also_works", 1.0f)));

            assertEquals(2, result.layers().size(),
                    "the two valid packs must survive the broken one");
            assertTrue(result.layer("keeps_working").isPresent());
            assertTrue(result.layer("also_works").isPresent());
        }

        @Test
        @DisplayName("the skipped file is named, with the reason")
        void skippedFileIsReported() {
            FieldLoader.Result result = FieldLoader.load(files(
                    "broken.json", "{\"primitives\":[]}"));

            assertFalse(result.isClean());
            assertTrue(result.hasFailures());

            FieldLoader.Problem problem = result.problems().get(0);
            assertEquals(FieldLoader.Problem.Kind.SKIPPED, problem.kind());
            assertEquals("broken.json", problem.source());
            assertTrue(problem.message().contains("id"),
                    () -> "message was: " + problem.message());
        }

        @Test
        @DisplayName("a shape naming a type that does not exist is a skip, not a crash")
        void unknownShapeTypeIsSkipped() {
            FieldLoader.Result result = FieldLoader.load(files("bad.json",
                    "{\"id\":\"x\",\"primitives\":[{\"id\":\"p\",\"type\":\"banana\","
                            + "\"shape\":{\"type\":\"banana\"}}]}"));

            assertTrue(result.hasFailures());
            assertTrue(result.layers().isEmpty());
        }

        @Test
        @DisplayName("a file failing part-way contributes nothing")
        void partialFileContributesNothing() {
            // The layer's id parses; its second primitive does not. Loading the first
            // would leave a layer no pack author wrote — half a file.
            FieldLoader.Result result = FieldLoader.load(
                    files("pack.json", layerWithOneBadPrimitive("half")));

            assertTrue(result.layers().isEmpty(),
                    "the valid half must not load on its own");
            assertTrue(result.hasFailures());
        }
    }

    @Nested
    @DisplayName("two packs, one id")
    class Overrides {

        @Test
        @DisplayName("the later file wins, because that is what load order means")
        void laterFileWins() {
            FieldLoader.Result result = FieldLoader.load(files(
                    "base.json", layer("shared", 1.0f),
                    "override.json", layer("shared", 9.0f)));

            assertEquals(1, result.layers().size());
            assertEquals(1, result.layer("shared").orElseThrow().primitives().size());
        }

        @Test
        @DisplayName("an override is reported, but is not a failure")
        void overrideIsReportedNotFailed() {
            FieldLoader.Result result = FieldLoader.load(files(
                    "base.json", layer("shared", 1.0f),
                    "override.json", layer("shared", 9.0f)));

            assertFalse(result.isClean(), "an override is worth reporting");
            assertFalse(result.hasFailures(), "but it is not a failure");

            FieldLoader.Problem problem = result.problems().get(0);
            assertEquals(FieldLoader.Problem.Kind.OVERRIDDEN, problem.kind());
            assertEquals("override.json", problem.source());
            assertTrue(problem.message().contains("base.json"),
                    () -> "the message should name what was overridden: " + problem.message());
        }

        @Test
        @DisplayName("a file that skipped does not count as having declared its id")
        void skippedFileDoesNotClaimTheId() {
            // The broken file names 'shared' too, but it never loaded, so the file
            // after it is not overriding anything and must not be reported as if it
            // were — a pack author chasing a phantom override is chasing nothing.
            FieldLoader.Result result = FieldLoader.load(files(
                    "broken.json", layerWithOneBadPrimitive("shared"),
                    "good.json", layer("shared", 1.0f)));

            assertTrue(result.layer("shared").isPresent());
            assertEquals(1, result.problems().size(), () -> "problems: " + result.problems());
            assertEquals(FieldLoader.Problem.Kind.SKIPPED, result.problems().get(0).kind());
        }
    }

    @Nested
    @DisplayName("reading from readers")
    class Readers {

        @Test
        @DisplayName("readers load the same as strings")
        void readersLoad() {
            Map<String, Reader> files = new LinkedHashMap<>();
            files.put("sun.json", new StringReader(layer("sun", 1.0f)));

            FieldLoader.Result result = FieldLoader.loadReaders(files);

            assertTrue(result.isClean(), () -> "problems: " + result.problems());
            assertTrue(result.layer("sun").isPresent());
        }

        @Test
        @DisplayName("a reader that throws is skipped, and the rest still load")
        void unreadableReaderIsSkipped() {
            Map<String, Reader> files = new LinkedHashMap<>();
            files.put("broken.json", new Reader() {
                @Override
                public int read(char[] buffer, int offset, int length) throws java.io.IOException {
                    throw new java.io.IOException("disk went away");
                }

                @Override
                public void close() {
                }
            });
            files.put("good.json", new StringReader(layer("sun", 1.0f)));

            FieldLoader.Result result = FieldLoader.loadReaders(files);

            assertTrue(result.layer("sun").isPresent(), "the readable file must still load");
            assertTrue(result.hasFailures());
            assertEquals("broken.json", result.problems().get(0).source());
            assertTrue(result.problems().get(0).message().contains("disk went away"),
                    () -> "the cause should reach the pack author: "
                            + result.problems().get(0).message());
        }

        @Test
        @DisplayName("the readers are not closed, because the loader did not open them")
        void readersAreLeftOpen() {
            boolean[] closed = {false};
            Map<String, Reader> files = new LinkedHashMap<>();
            files.put("sun.json", new StringReader(layer("sun", 1.0f)) {
                @Override
                public void close() {
                    closed[0] = true;
                    super.close();
                }
            });

            FieldLoader.loadReaders(files);

            assertFalse(closed[0],
                    "closing what you did not open double-closes the caller's stream");
        }
    }
}
