package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The versions the library guide tells a reader to depend on are the ones the build
 * publishes.
 *
 * <p>A guide that names a coordinate is making a promise no compiler checks. The
 * examples in it are already executed by {@code LibraryApiDocExampleTest}, but an
 * example can compile perfectly against a version nobody can download — the two claims
 * fail independently, so they need checking independently.
 *
 * <p>This one is worth the check because it breaks on a routine act: bumping a version
 * in {@code gradle.properties} and not touching the prose, which is exactly the edit
 * least likely to prompt anyone to reread a document.
 */
class LibraryGuideCoordinatesTest {

    private static final String GUIDE = "docs/USING_AS_A_LIBRARY.md";

    @Test
    @DisplayName("the guide's coordinates carry the versions the build publishes")
    void guideVersionsMatchTheBuild() {
        Path root = repoRoot();
        String guide = read(root.resolve(GUIDE));

        // mcshaders-api is published at the mod version; mcshaders-core at its own.
        assertEquals(property(root.resolve("gradle.properties"), "mod_version"),
                versionIn(guide, "mcshaders-api"),
                "the guide's mcshaders-api version is not the one :common publishes");
        assertEquals(property(root.resolve("core/gradle.properties"), "core_version"),
                versionIn(guide, "mcshaders-core"),
                "the guide's mcshaders-core version is not the one the core build publishes");
    }

    @Test
    @DisplayName("the artifact ids in the guide are the ones the builds actually declare")
    void guideArtifactIdsExist() {
        Path root = repoRoot();
        assertTrue(read(root.resolve("common/build.gradle.kts")).contains("\"mcshaders-api\""),
                "common no longer publishes mcshaders-api, which the guide still names");
        assertTrue(read(root.resolve("check/build.gradle.kts")).contains("\"mcshaders-check\""),
                "check no longer publishes mcshaders-check");
    }

    /** The version in {@code implementation("net.cyberpunk042:<artifact>:<version>")}. */
    private static String versionIn(String guide, String artifact) {
        Matcher m = Pattern.compile(
                "net\\.cyberpunk042:" + Pattern.quote(artifact) + ":([0-9]+\\.[0-9]+\\.[0-9]+)")
                .matcher(guide);
        assertTrue(m.find(), GUIDE + " no longer names a version for " + artifact);
        String version = m.group(1);
        // The guide names it twice — once live, once commented. Both must agree, or a
        // reader who uncomments the second line gets a different answer than the first.
        while (m.find()) {
            assertEquals(version, m.group(1),
                    GUIDE + " names two versions for " + artifact);
        }
        return version;
    }

    private static String property(Path file, String key) {
        for (String line : read(file).split("\n")) {
            if (line.startsWith(key + "=")) {
                return line.substring(key.length() + 1).trim();
            }
        }
        throw new AssertionError("no " + key + " in " + file);
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
