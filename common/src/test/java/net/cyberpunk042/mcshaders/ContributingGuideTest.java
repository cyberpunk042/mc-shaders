package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The contributing guide names the tests that read a document; those tests read one.
 *
 * <p>It is the page that tells a first contributor how this repository works, and the
 * paragraph in question is the house rule itself: put the example on the page and let
 * the test parse it from there, because "a copy passes forever while the page it came
 * from drifts". It named three tests as that pattern, and one of them —
 * {@code LibraryApiDocExampleTest} — keeps a copy. That test is not at fault and does
 * not pretend otherwise: its javadoc carries a section headed "Not pinned to the guide,
 * and not for want of trying", explaining that neither pinning mechanism in this
 * repository fits it. The guide was citing it as the opposite of what it says it is.
 *
 * <p>So the rule now holds its own example. A test named in that paragraph has to read
 * a document; one that does not either belongs in the paragraph below it, which is
 * about the tests that hold a copy, or does not belong on the page.
 */
class ContributingGuideTest {

    private static final String GUIDE = "CONTRIBUTING.md";

    /** The paragraph the rule lives in, up to the one about tests that hold a copy. */
    private static final Pattern CLAIM = Pattern.compile(
            "\\*\\*Documentation that runs\\.\\*\\*(.*?)\\n\\nTwo tests hold a copy",
            Pattern.DOTALL);

    private static final Pattern TEST_NAME = Pattern.compile("`([A-Z][A-Za-z0-9]*Test)`");

    @Test
    @DisplayName("every test the rule names reads a document")
    void namedTestsReadADocument() {
        Path root = repoRoot();

        Matcher claim = CLAIM.matcher(read(root.resolve(GUIDE)));
        assertTrue(claim.find(),
                "the 'Documentation that runs' paragraph is not where it was, so this test "
                        + "is checking nothing — find it and fix the pattern, or the rule is "
                        + "unguarded again");

        List<String> named = new ArrayList<>();
        Matcher name = TEST_NAME.matcher(claim.group(1));
        while (name.find()) {
            named.add(name.group(1));
        }
        assertFalse(named.isEmpty(), "the paragraph names no test at all");

        TreeMap<String, String> wrong = new TreeMap<>();
        for (String test : named) {
            List<Path> sources = sourcesFor(root, test);
            if (sources.isEmpty()) {
                wrong.put(test, "no source file of that name exists");
                continue;
            }
            if (sources.stream().noneMatch(ContributingGuideTest::readsADocument)) {
                wrong.put(test, "exists but reads no .md file — it holds a copy, which is "
                        + "the shape this paragraph tells contributors not to write");
            }
        }

        assertTrue(wrong.isEmpty(),
                () -> "the contributing guide names these as tests that parse their page:\n  "
                        + String.join("\n  ", wrong.entrySet().stream()
                                .map(e -> e.getKey() + " — " + e.getValue()).toList()));
    }

    /** Every test source of that simple name, in any module. */
    private static List<Path> sourcesFor(Path root, String simpleName) {
        List<Path> found = new ArrayList<>();
        for (String module : List.of("core", "common", "check")) {
            Path tests = root.resolve(module).resolve("src/test/java");
            if (!Files.isDirectory(tests)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(tests)) {
                walk.filter(p -> p.getFileName().toString().equals(simpleName + ".java"))
                        .forEach(found::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return found;
    }

    /** Whether the source names a markdown file, which is how these tests reach a page. */
    private static boolean readsADocument(Path source) {
        return Pattern.compile("\"[A-Za-z0-9_/.-]+\\.md\"").matcher(read(source)).find();
    }

    private static Path repoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve(GUIDE))) {
                return dir;
            }
        }
        throw new AssertionError("could not find " + GUIDE + " from "
                + Path.of("").toAbsolutePath());
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
