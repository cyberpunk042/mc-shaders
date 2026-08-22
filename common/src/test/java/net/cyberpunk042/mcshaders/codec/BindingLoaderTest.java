package net.cyberpunk042.mcshaders.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What a reload does with several packs, one of which is broken.
 *
 * <p>The behaviour under test is mostly about failure. A loader that works when every
 * file is valid is the easy half; the half that matters is what a player sees when
 * one pack in their folder has a typo, and the answer has to be "the other packs
 * still work, and something says which one was wrong".
 */
class BindingLoaderTest {

    private static String binding(String id, String dimension) {
        return "{\"id\":\"" + id + "\",\"dimension\":\"" + dimension + "\"}";
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
        @DisplayName("several files become one registry")
        void severalFilesLoad() {
            BindingLoader.Result result = BindingLoader.load(files(
                    "a.json", binding("overworld_haze", "minecraft:overworld"),
                    "b.json", binding("nether_base", "minecraft:the_nether")));

            assertTrue(result.isClean());
            assertEquals(2, result.registry().size());
            assertTrue(result.registry().byId("overworld_haze").isPresent());
            assertTrue(result.registry().byId("nether_base").isPresent());
        }

        @Test
        @DisplayName("a file holding an array contributes all of its bindings")
        void arrayFileLoads() {
            BindingLoader.Result result = BindingLoader.load(files("pack.json",
                    "[" + binding("a", "minecraft:overworld") + ","
                        + binding("b", "minecraft:the_end") + "]"));

            assertTrue(result.isClean());
            assertEquals(2, result.registry().size());
        }

        @Test
        @DisplayName("no files is an empty registry, not a failure")
        void emptyLoadIsClean() {
            BindingLoader.Result result = BindingLoader.load(Map.of());

            assertTrue(result.isClean());
            assertTrue(result.registry().isEmpty());
        }
    }

    @Nested
    @DisplayName("one bad file")
    class Failures {

        @Test
        @DisplayName("the broken file is skipped and the others still load")
        void badFileDoesNotBlankTheWorld() {
            BindingLoader.Result result = BindingLoader.load(files(
                    "good.json", binding("keeps_working", "minecraft:overworld"),
                    "broken.json", "{ not json at all",
                    "also_good.json", binding("also_works", "minecraft:the_end")));

            assertEquals(2, result.registry().size(),
                    "the two valid packs must survive the broken one");
            assertTrue(result.registry().byId("keeps_working").isPresent());
            assertTrue(result.registry().byId("also_works").isPresent());
        }

        @Test
        @DisplayName("the skipped file is named, with the reason")
        void skippedFileIsReported() {
            BindingLoader.Result result = BindingLoader.load(files(
                    "broken.json", "{\"dimension\":\"minecraft:overworld\"}"));

            assertFalse(result.isClean());
            assertTrue(result.hasFailures());

            BindingLoader.Problem problem = result.problems().get(0);
            assertEquals(BindingLoader.Problem.Kind.SKIPPED, problem.kind());
            assertEquals("broken.json", problem.source());
            assertTrue(problem.message().contains("missing 'id'"),
                    () -> "message was: " + problem.message());
        }

        @Test
        @DisplayName("a file failing part-way contributes none of its bindings")
        void partialFileContributesNothing() {
            // The second entry is bad. Loading the first would leave the registry in a
            // state no pack author wrote — half a file.
            BindingLoader.Result result = BindingLoader.load(files("pack.json",
                    "[" + binding("first", "minecraft:overworld") + ",{\"id\":\"second\"}]"));

            assertTrue(result.registry().isEmpty());
            assertTrue(result.hasFailures());
        }
    }

    @Nested
    @DisplayName("two packs, one id")
    class Overrides {

        @Test
        @DisplayName("the later file wins, because that is what load order means")
        void laterFileWins() {
            BindingLoader.Result result = BindingLoader.load(files(
                    "base.json", binding("shared", "minecraft:overworld"),
                    "override.json", binding("shared", "minecraft:the_end")));

            assertEquals(1, result.registry().size());
            assertEquals("minecraft:the_end",
                    result.registry().byId("shared").orElseThrow().dimension().toString());
        }

        @Test
        @DisplayName("an override is reported, but is not a failure")
        void overrideIsReportedNotFailed() {
            BindingLoader.Result result = BindingLoader.load(files(
                    "base.json", binding("shared", "minecraft:overworld"),
                    "override.json", binding("shared", "minecraft:the_end")));

            assertFalse(result.isClean(), "an override is worth reporting");
            assertFalse(result.hasFailures(), "but it is not a failure");

            BindingLoader.Problem problem = result.problems().get(0);
            assertEquals(BindingLoader.Problem.Kind.OVERRIDDEN, problem.kind());
            assertEquals("override.json", problem.source());
            assertTrue(problem.message().contains("base.json"),
                    () -> "the message should name what was overridden: " + problem.message());
        }

        @Test
        @DisplayName("priority decides order, not which file came first")
        void priorityDecidesOrderNotFileOrder() {
            // Worth pinning because the opposite is the natural assumption. The
            // registry copies into an unordered map and sorts by priority then id,
            // so a pack author sequencing files to control layering would be relying
            // on something that does not hold — priority is the control.
            BindingLoader.Result result = BindingLoader.load(files(
                    "loaded_first.json",
                    "{\"id\":\"late\",\"dimension\":\"minecraft:overworld\",\"priority\":10}",
                    "loaded_second.json",
                    "{\"id\":\"early\",\"dimension\":\"minecraft:overworld\",\"priority\":1}"));

            assertTrue(result.isClean());
            assertEquals(List.of("early", "late"),
                    result.registry()
                            .forDimension(DimensionId.minecraft("overworld"))
                            .stream().map(DimensionBinding::id).toList(),
                    "ascending priority, regardless of the order the files loaded in");
        }
    }
}
