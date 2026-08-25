package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A page saying how many tests there are says when it counted them.
 *
 * <p>Three documents had a stale figure at once — the README and {@code SHADERS.md}
 * said 585 where there were 736, and {@code ARCHITECTURE.md} said 52 where {@code core}
 * alone had 471. None of them was wrong when written. A count is a measurement, and a
 * measurement with no date reads as current forever, which is exactly how it goes bad:
 * the edit that changes the number is adding a test, and nothing about adding a test
 * makes anyone reread a page.
 *
 * <p>So the rule is not that the number must be right — nothing here can run the other
 * modules' suites to know that. It is that the number must say when it was taken, which
 * a reader can act on and a reviewer can see going stale.
 *
 * <p><strong>{@code ROADMAP.md} is exempt, and deliberately.</strong> Its counts sit
 * under {@code ✅ done} headings — "Verified: 127 tests, 0 failures, on JDK 21" is what
 * that milestone delivered when it closed. Restamping those with today's date would
 * make the record say something it does not mean, and correcting the numbers would
 * falsify it outright.
 */
class StatedTestCountsTest {

    /** "736 tests", "45 test files" — a measurement of how many tests exist. */
    private static final Pattern COUNT = Pattern.compile("\\b\\d[\\d,]*\\s+tests?(\\s+files?)?\\b");

    /** What makes it a measurement rather than a claim: when it was taken. */
    private static final Pattern DATED = Pattern.compile("counted\\s+\\d{4}-\\d{2}-\\d{2}");

    /** The one page whose counts are history. See the class documentation. */
    private static final String HISTORICAL = "ROADMAP.md";

    @Test
    @DisplayName("every stated test count carries the date it was counted")
    void countsAreDated() {
        List<String> pages = new ArrayList<>();
        List<String> bare = new ArrayList<>();

        for (Path page : documents()) {
            if (page.getFileName().toString().equals(HISTORICAL)) {
                continue;
            }
            pages.add(page.getFileName().toString());
            int lineNumber = 0;
            for (String line : read(page).lines().toList()) {
                lineNumber++;
                Matcher count = COUNT.matcher(line);
                if (count.find() && !DATED.matcher(line).find()) {
                    bare.add(page.getFileName() + ":" + lineNumber + "  " + line.strip());
                }
            }
        }

        // A scan over no pages would agree with the assertion below.
        assertFalse(pages.isEmpty(), "no documents found to scan");

        assertTrue(bare.isEmpty(),
                () -> "these state a number of tests without saying when it was counted, "
                        + "which is how the last three went stale unnoticed — add "
                        + "\"counted YYYY-MM-DD\" on the same line, or drop the number:\n  "
                        + String.join("\n  ", bare));
    }

    private static List<Path> documents() {
        Path root = repoRoot();
        List<Path> pages = new ArrayList<>(List.of(root.resolve("README.md"),
                root.resolve("CONTRIBUTING.md")));
        try (Stream<Path> walk = Files.walk(root.resolve("docs"), 1)) {
            walk.filter(p -> p.toString().endsWith(".md")).sorted().forEach(pages::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return pages.stream().filter(Files::isRegularFile).toList();
    }

    private static Path repoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("README.md"))
                    && Files.isDirectory(dir.resolve("docs"))) {
                return dir;
            }
        }
        throw new AssertionError("could not find the repository root from "
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
