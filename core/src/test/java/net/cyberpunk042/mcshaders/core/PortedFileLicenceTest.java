package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Every file ported from {@code the-virus-block-mc} says it was relicensed.
 *
 * <p>The repository ships one {@code LICENSE}, MIT, and CONTRIBUTING tells a contributor
 * that ported code carries a header saying its author relicensed it. That is a claim
 * about the terms the whole repository is distributed under, which makes it the last
 * claim that should rest on everyone remembering to paste a comment.
 *
 * <p>So it is checked instead. A file that names the origin without naming the
 * relicence fails here, at the point the header is written rather than whenever
 * someone next audits the licensing.
 */
class PortedFileLicenceTest {

    private static final String ORIGIN = "Ported from the-virus-block-mc";
    private static final String RELICENCE = "Relicensed to MIT";

    @Test
    @DisplayName("a ported file that does not claim the relicence fails the build")
    void everyPortedFileCarriesTheRelicence() {
        Path sources = repoRoot().resolve("core/src/main/java");
        assertTrue(Files.isDirectory(sources), "expected sources at " + sources);

        List<String> ported = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(sources)) {
            tree.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                String text = read(p);
                if (!text.contains(ORIGIN)) {
                    return;
                }
                ported.add(sources.relativize(p).toString());
                if (!text.contains(RELICENCE)) {
                    missing.add(sources.relativize(p).toString());
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // Without this the test passes loudest when the marker is renamed and it
        // stops finding anything at all — which is the failure it would least catch.
        assertTrue(ported.size() > 100,
                "expected the ported corpus to be substantial, found " + ported.size()
                        + " — has the origin header been reworded?");
        assertTrue(missing.isEmpty(),
                "these name the origin but not the relicence, so the terms they are "
                        + "distributed under are unstated: " + missing);
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
                    && Files.isDirectory(dir.resolve("core/src/main/java"))) {
                return dir;
            }
        }
        throw new AssertionError("could not find the repository root above "
                + Path.of("").toAbsolutePath());
    }
}
