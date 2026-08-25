package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The version matrix says what the build pins, so it has to be what the build pins.
 *
 * <p>{@code settings.gradle.kts} already refuses to configure when {@code core_version}
 * is declared twice and the two disagree, and says why: "Two declarations of one fact
 * with a comment asking a human to keep them in step is the exact shape of drift this
 * project's own tooling exists to catch, so the build checks it rather than trusting
 * the comment." {@code docs/VERSIONS.md} is a whole page of that shape and had nothing
 * checking it.
 *
 * <p>It had drifted in two places when this was written, both in the direction the
 * doctrine predicts — the page was right when a value was chosen and was not revisited
 * when the value changed.
 */
class VersionMatrixDocTest {

    private static final String MATRIX = "docs/VERSIONS.md";

    /** A table row whose first two cells are backticked: {@code | `key` | `value` | …}. */
    private static final Pattern ROW = Pattern.compile(
            "^\\|\\s*`([^`]+)`\\s*\\|\\s*`([^`]+)`\\s*\\|", Pattern.MULTILINE);

    @Nested
    @DisplayName("the pinned-versions table")
    class Pinned {

        @Test
        @DisplayName("every row naming a build property carries that property's value")
        void rowsMatchTheBuild() {
            Path root = repoRoot();
            Map<String, String> properties = properties(root.resolve("gradle.properties"));
            String page = read(root.resolve(MATRIX));

            Map<String, String> checked = new LinkedHashMap<>();
            Map<String, String> wrong = new TreeMap<>();
            Matcher row = ROW.matcher(page);
            while (row.find()) {
                String key = row.group(1);
                String documented = row.group(2);
                // Rows naming something other than a build property — the Loom plugin
                // id, the `mc_26_3_*` group, Jade's own key names — describe facts this
                // file is not the source of. They are covered by their own tests below
                // or by nothing, and either way are not this assertion's business.
                if (!properties.containsKey(key)) {
                    continue;
                }
                checked.put(key, documented);
                if (!properties.get(key).equals(documented)) {
                    wrong.put(key, "page says " + documented
                            + ", gradle.properties says " + properties.get(key));
                }
            }

            // A regex that matched nothing would agree with the assertion below.
            assertFalse(checked.isEmpty(),
                    "no table row named a build property — the scan is not reading "
                            + MATRIX + " as it is written");
            assertTrue(wrong.isEmpty(),
                    () -> "the version matrix disagrees with the build:\n  "
                            + String.join("\n  ", wrong.entrySet().stream()
                                    .map(e -> e.getKey() + ": " + e.getValue()).toList()));
        }
    }

    @Nested
    @DisplayName("the 26.3 placeholders")
    class Placeholders {

        @Test
        @DisplayName("every coordinate is inert, so a premature retarget fails on it")
        void placeholdersAreInert() {
            Map<String, String> properties = properties(repoRoot().resolve("gradle.properties"));

            Map<String, String> live = new TreeMap<>();
            int seen = 0;
            for (Map.Entry<String, String> entry : properties.entrySet()) {
                if (!entry.getKey().startsWith("mc_26_3_")) {
                    continue;
                }
                seen++;
                // The Minecraft version is a name rather than a coordinate: nothing is
                // resolved from it that could quietly succeed.
                if (entry.getKey().equals("mc_26_3_minecraft")) {
                    continue;
                }
                if (!entry.getValue().equals("PIN_ON_RELEASE")) {
                    live.put(entry.getKey(), entry.getValue());
                }
            }

            assertTrue(seen >= 4, "expected the 26.3 block; found " + seen + " keys");
            assertTrue(live.isEmpty(),
                    () -> "these would resolve if someone flipped mc_version to 26.3 today, "
                            + "so the retarget would build quietly against the wrong "
                            + "versions instead of failing on the coordinate — which is "
                            + "what the block says it prevents: " + live);
        }
    }

    @Nested
    @DisplayName("the cross-check against Jade")
    class CrossCheck {

        @Test
        @DisplayName("the 'Here' column is what is actually here")
        void hereIsHere() {
            Path root = repoRoot();
            String page = read(root.resolve(MATRIX));
            String pinned = properties(root.resolve("gradle.properties")).get("fabric_loom_version");

            // | `loom_version` | <Jade> | <Here> | … — a three-value row, so the general
            // scan above reads its second cell as the value and would compare Jade's
            // number to ours. This is the row's own check.
            Matcher row = Pattern.compile(
                    "^\\|\\s*`loom_version`\\s*\\|\\s*`([^`]+)`\\s*\\|\\s*`([^`]+)`\\s*\\|",
                    Pattern.MULTILINE).matcher(page);
            assertTrue(row.find(), "the loom row of the cross-check table is not where it was");
            assertEquals(pinned, row.group(2),
                    "the cross-check table's 'Here' column does not name the pinned Loom "
                            + "version. It said 1.17 for a while — the value tried first, "
                            + "which CI rejected, explained in the paragraph directly below "
                            + "the table that contradicted it");
        }
    }

    private static Map<String, String> properties(Path file) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : read(file).lines().toList()) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int split = trimmed.indexOf('=');
            if (split > 0) {
                out.put(trimmed.substring(0, split).strip(), trimmed.substring(split + 1).strip());
            }
        }
        return out;
    }

    private static Path repoRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve(MATRIX))) {
                return dir;
            }
        }
        throw new AssertionError("could not find " + MATRIX + " from "
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
