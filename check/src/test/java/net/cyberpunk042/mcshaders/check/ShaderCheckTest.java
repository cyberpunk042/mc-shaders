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
    @DisplayName("uniform blocks, end to end")
    class UniformBlocks {

        /**
         * A shader whose block holds a matrix, some reserved space, and a plain float —
         * the three shapes the real content turned out to be made of.
         */
        private static final String MATRIX_SHADER = """
                #version 150
                uniform sampler2D InSampler;
                layout(std140) uniform Config {
                    vec4 ViewProj[4];
                    float Radius;
                    float Reserved9_0;
                    float Reserved9_1;
                };
                out vec4 fragColor;
                void main() { fragColor = vec4(Radius); }
                """;

        /**
         * The case that produced 416 false findings against the real tree.
         *
         * <p>Minecraft's post-effect JSON has no matrix type and no vector type, so a
         * chain passing a {@code mat4} writes sixteen consecutive floats. The shader
         * declares {@code vec4[4]}. Those are the same sixty-four bytes, and a checker
         * that reports them as a disagreement is unusable on any real pack.
         *
         * <p>This is deliberately an end-to-end test rather than another
         * {@code LayoutComparison} unit test. The unit tests were green throughout:
         * the false positive lived in the whole path — the JSON codec reading loose
         * floats, the GLSL reader producing an array, and only then the comparison —
         * and nothing exercised that path against content shaped like the real thing.
         */
        @Test
        @DisplayName("a matrix written as sixteen loose floats is not a disagreement")
        void aMatrixSpelledAsScalarsIsAccepted(@TempDir Path root) throws IOException {
            write(root.resolve("example/shaders/post/blit.vsh"), SHADER);
            write(root.resolve("example/shaders/post/matrix.fsh"), MATRIX_SHADER);

            // The matrix comes first because std140 aligns a vec4 array to 16, so a
            // leading float would put the sixteen loose floats a slot out and the
            // fixture would be testing misalignment rather than spelling.
            StringBuilder members = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                members.append("{ \"name\": \"VP").append(i)
                        .append("\", \"type\": \"float\", \"value\": 0.0 },\n            ");
            }
            members.append("""
                    { "name": "Radius", "type": "float", "value": 1.0 },
                            { "name": "CameraX", "type": "float", "value": 0.0 },
                            { "name": "CameraY", "type": "float", "value": 0.0 }""");

            write(root.resolve("example/post_effect/matrix.json"), """
                    {
                      "targets": {},
                      "passes": [{
                        "vertex_shader": "example:post/blit",
                        "fragment_shader": "example:post/matrix",
                        "inputs": [{ "sampler_name": "In", "target": "minecraft:main" }],
                        "output": "minecraft:main",
                        "uniforms": { "Config": [
                            %s
                        ] }
                      }]
                    }
                    """.formatted(members));

            assertEquals(0, new ShaderCheck(new ResourceTree(root), true).run(),
                    "a matrix spelled as scalars, or a written reserved slot, failed the check");
        }

        /**
         * The counterpart: accepting spelling differences must not mean accepting
         * everything. A member the host simply does not write still has to fail.
         */
        @Test
        @DisplayName("a host that stops short of the shader still fails")
        void aShortHostStillFails(@TempDir Path root) throws IOException {
            write(root.resolve("example/shaders/post/blit.vsh"), SHADER);
            write(root.resolve("example/shaders/post/matrix.fsh"), MATRIX_SHADER);
            write(root.resolve("example/post_effect/matrix.json"), """
                    {
                      "targets": {},
                      "passes": [{
                        "vertex_shader": "example:post/blit",
                        "fragment_shader": "example:post/matrix",
                        "inputs": [{ "sampler_name": "In", "target": "minecraft:main" }],
                        "output": "minecraft:main",
                        "uniforms": { "Config": [
                            { "name": "Radius", "type": "float", "value": 1.0 }
                        ] }
                      }]
                    }
                    """);

            assertEquals(1, new ShaderCheck(new ResourceTree(root), true).run(),
                    "a host writing 4 bytes into a block the shader reads 96 from passed");
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
