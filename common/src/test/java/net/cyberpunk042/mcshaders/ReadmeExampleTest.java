package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.codec.BindingLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The binding JSON shown in README.md and SHADERS.md, parsed.
 *
 * <p>Both open with a snippet a reader is invited to drop into a datapack. It is the
 * first thing anyone sees and the first thing they will copy, so it is the worst
 * possible place for a stale field name — and nothing was checking it. The mod's own
 * pack is pinned by {@code PackDimensionsTest}; the examples in the prose were not.
 *
 * <p>Extracted from the documents themselves rather than duplicated here. A copy would
 * pass forever while the page it was copied from drifted, which is the failure this is
 * meant to prevent.
 */
class ReadmeExampleTest {

    /** Every fenced ```json block in a document, in order. */
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

    private static void assertParses(String docName) {
        List<String> blocks = jsonBlocks(docName);
        assertTrue(!blocks.isEmpty(), docName + " should carry at least one json example");

        for (int i = 0; i < blocks.size(); i++) {
            String source = docName + " block " + (i + 1);
            BindingLoader.Result result = BindingLoader.load(Map.of(source, blocks.get(i)));

            assertTrue(result.isClean(),
                    "a reader copying " + source + " would get: " + result.problems());
            assertEquals(1, result.registry().size(),
                    source + " should define exactly one binding");
        }
    }

    @Test
    @DisplayName("the README's opening example is a binding the loader accepts")
    void readmeExampleParses() {
        // Including the optional fields it leaves out: blend, weight and per-layer
        // priority are all absent from the snippet, and it has to work without them or
        // the shortest version of the format is a lie.
        assertParses("README.md");
    }

    @Test
    @DisplayName("the shader guide's example is too")
    void shadersDocExampleParses() {
        assertParses("docs/SHADERS.md");
    }
}
