package net.cyberpunk042.mcshaders.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end tests over a real directory tree: resource resolution, and the
 * checker's exit code.
 *
 * <p>The exit code is the contract a build depends on, so it is worth a test of
 * its own — a checker that reports problems and exits 0 gates nothing.
 */
class ShaderCheckTest {

    private static final String SHADER = """
            #version 150
            uniform sampler2D InSampler;
            out vec4 fragColor;
            void main() { fragColor = texture(InSampler, vec2(0.0)); }
            """;

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    /** A tree with one sound chain in the {@code example} namespace. */
    private static Path soundTree(Path root) throws IOException {
        write(root.resolve("example/shaders/post/tint.fsh"), SHADER);
        write(root.resolve("example/shaders/post/blit.vsh"), SHADER);
        write(root.resolve("example/post_effect/tint.json"), """
                {
                  "targets": {},
                  "passes": [{
                    "vertex_shader": "example:post/blit",
                    "fragment_shader": "example:post/tint",
                    "inputs": [{ "sampler_name": "In", "target": "minecraft:main" }],
                    "output": "minecraft:main"
                  }]
                }
                """);
        return root;
    }

    @Nested
    @DisplayName("resolving resources")
    class Resolution {

        @Test
        void findsAShaderByItsNamespacedIdWithoutAnExtension(@TempDir Path root) throws IOException {
            ResourceTree tree = new ResourceTree(soundTree(root));

            assertTrue(tree.read("example:post/tint").isPresent(),
                    "a pass names one id for what is really a pair of files");
        }

        @Test
        void anIdInAKnownNamespaceThatIsAbsentIsMissing(@TempDir Path root) throws IOException {
            ResourceTree tree = new ResourceTree(soundTree(root));

            assertTrue(tree.read("example:post/gone").isEmpty());
            assertFalse(tree.isExternal("example:post/gone"), "the namespace is right here");
        }

        @Test
        @DisplayName("an id in a namespace this tree lacks is external, not missing")
        void anIdInAnAbsentNamespaceIsExternal(@TempDir Path root) throws IOException {
            // minecraft:post/blit is real; it lives in the game jar. Calling it missing
            // would bury the genuine findings under one per pass.
            ResourceTree tree = new ResourceTree(soundTree(root));

            assertTrue(tree.read("minecraft:post/blit").isEmpty());
            assertTrue(tree.isExternal("minecraft:post/blit"));
        }

        @Test
        @DisplayName("shader files are listed per file, while chains name a stem")
        void shaderFilesAreListedPerFile(@TempDir Path root) throws IOException {
            // A chain names one id for a pair of files; this set is about files that
            // exist, so each is named exactly once, extension included.
            List<String> files = new ResourceTree(soundTree(root)).shaderFiles();

            assertTrue(files.contains("example:post/tint.fsh"), files.toString());
            assertTrue(files.contains("example:post/blit.vsh"), files.toString());
        }

        @Test
        void listsTheChainsItFinds(@TempDir Path root) throws IOException {
            List<Path> chains = new ResourceTree(soundTree(root)).chains();

            assertEquals(1, chains.size());
            assertEquals("tint.json", chains.get(0).getFileName().toString());
        }
    }

    @Nested
    @DisplayName("exit code")
    class ExitCode {

        @Test
        void aSoundTreePasses(@TempDir Path root) throws IOException {
            assertEquals(0, new ShaderCheck(new ResourceTree(soundTree(root)), true).run());
        }

        @Test
        void aMissingShaderFails(@TempDir Path root) throws IOException {
            soundTree(root);
            Files.delete(root.resolve("example/shaders/post/tint.fsh"));

            assertEquals(1, new ShaderCheck(new ResourceTree(root), true).run());
        }

        @Test
        @DisplayName("a chain referring only to shaders this tree does not own still passes")
        void externalShadersDoNotFailTheCheck(@TempDir Path root) throws IOException {
            soundTree(root);
            write(root.resolve("example/post_effect/vanilla.json"), """
                    {
                      "targets": {},
                      "passes": [{
                        "vertex_shader": "minecraft:post/blit",
                        "fragment_shader": "minecraft:post/blit",
                        "inputs": [],
                        "output": "minecraft:main"
                      }]
                    }
                    """);

            assertEquals(0, new ShaderCheck(new ResourceTree(root), true).run());
        }

        @Test
        void unreadableJsonFails(@TempDir Path root) throws IOException {
            soundTree(root);
            write(root.resolve("example/post_effect/broken.json"), "{ not json");

            assertEquals(1, new ShaderCheck(new ResourceTree(root), true).run());
        }

        @Test
        @DisplayName("a shader nothing reaches is reported but does not fail")
        void orphansAreReportedNotFailed(@TempDir Path root) throws IOException {
            // A pack may load shaders by means this does not model, and a variant kept
            // on purpose is not a defect. Failing on these would make the check
            // unusable on any real tree.
            soundTree(root);
            write(root.resolve("example/shaders/post/_archive/old.fsh"), SHADER);

            assertEquals(0, new ShaderCheck(new ResourceTree(root), false).run());
        }

        @Test
        @DisplayName("a warning alone does not fail the build")
        void warningsDoNotGate(@TempDir Path root) throws IOException {
            soundTree(root);
            // A declared target nothing reads is worth saying; it is not a failure.
            write(root.resolve("example/post_effect/tint.json"), """
                    {
                      "targets": { "unused": {} },
                      "passes": [{
                        "vertex_shader": "example:post/blit",
                        "fragment_shader": "example:post/tint",
                        "inputs": [{ "sampler_name": "In", "target": "minecraft:main" }],
                        "output": "minecraft:main"
                      }]
                    }
                    """);

            assertEquals(0, new ShaderCheck(new ResourceTree(root), false).run());
        }
    }
}
