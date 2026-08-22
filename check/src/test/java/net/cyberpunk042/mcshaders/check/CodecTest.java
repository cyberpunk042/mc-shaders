package net.cyberpunk042.mcshaders.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import net.cyberpunk042.mcshaders.core.chain.Pass;
import net.cyberpunk042.mcshaders.core.chain.PostChain;
import net.cyberpunk042.mcshaders.core.chain.TargetSpec;
import net.cyberpunk042.mcshaders.core.layout.GlslType;
import net.cyberpunk042.mcshaders.core.layout.Std140;
import net.cyberpunk042.mcshaders.core.layout.UniformBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for reading Minecraft's post-effect JSON.
 *
 * <p>The codec is where all the format knowledge lives, so it is where getting it
 * wrong is expensive: a chain read with its uniform entries in a different order
 * than the file gave them would make the layout comparison confidently report the
 * wrong answer, which is worse than not checking at all.
 */
class CodecTest {

    private static PostChain read(String json) throws IOException {
        return PostChainCodec.read(new StringReader(json));
    }

    private static final String MINIMAL = """
            {
              "targets": { "swap": {} },
              "passes": [{
                "vertex_shader": "minecraft:post/blit",
                "fragment_shader": "example:post/tint",
                "inputs": [{ "sampler_name": "In", "target": "minecraft:main" }],
                "output": "swap"
              }]
            }
            """;

    @Nested
    @DisplayName("structure")
    class Structure {

        @Test
        void readsTargetsAndPasses() throws IOException {
            PostChain chain = read(MINIMAL);

            assertEquals(List.of("swap"), List.copyOf(chain.targets().keySet()));
            assertEquals(1, chain.passes().size());
            assertEquals("example:post/tint", chain.passes().get(0).fragmentShader());
            assertEquals("swap", chain.passes().get(0).output());
        }

        @Test
        @DisplayName("an empty target spec means screen-sized")
        void emptyTargetSpecFollowsTheScreen() throws IOException {
            assertTrue(read(MINIMAL).targets().get("swap").isScreenSized());
        }

        @Test
        void anExplicitTargetSizeIsKept() throws IOException {
            PostChain chain = read("""
                    { "targets": { "half": { "width": 640, "height": 360 } }, "passes": [] }
                    """);

            assertEquals(new TargetSpec(640, 360), chain.targets().get("half"));
        }

        @Test
        void passOrderIsPreserved() throws IOException {
            PostChain chain = read("""
                    { "targets": {}, "passes": [
                      { "vertex_shader": "v", "fragment_shader": "first",  "output": "minecraft:main" },
                      { "vertex_shader": "v", "fragment_shader": "second", "output": "minecraft:main" }
                    ] }
                    """);

            assertEquals(List.of("first", "second"),
                    chain.passes().stream().map(Pass::fragmentShader).toList());
        }

        @Test
        void aPassWithoutInputsOrUniformsIsFine() throws IOException {
            PostChain chain = read("""
                    { "targets": {}, "passes": [
                      { "vertex_shader": "v", "fragment_shader": "f", "output": "minecraft:main" }
                    ] }
                    """);

            assertTrue(chain.passes().get(0).inputs().isEmpty());
            assertTrue(chain.passes().get(0).uniforms().isEmpty());
        }

        @Test
        void somethingThatIsNotAnObjectIsRejected() {
            assertThrows(JsonSyntaxException.class, () -> read("[]"));
        }
    }

    @Nested
    @DisplayName("inputs")
    class Inputs {

        @Test
        void depthBufferDefaultsToFalseAndIsReadWhenPresent() throws IOException {
            PostChain chain = read("""
                    { "targets": {}, "passes": [{
                      "vertex_shader": "v", "fragment_shader": "f", "output": "minecraft:main",
                      "inputs": [
                        { "sampler_name": "In",    "target": "minecraft:main" },
                        { "sampler_name": "Depth", "target": "minecraft:main", "use_depth_buffer": true }
                      ]
                    }] }
                    """);

            assertFalse(chain.passes().get(0).inputs().get(0).useDepthBuffer());
            assertTrue(chain.passes().get(0).inputs().get(1).useDepthBuffer());
        }

        @Test
        @DisplayName("the sampler the shader must declare is the input name plus Sampler")
        void samplerNamingConvention() throws IOException {
            assertEquals("InSampler", read(MINIMAL).passes().get(0).inputs().get(0).declaredSampler());
        }
    }

    @Nested
    @DisplayName("uniform blocks")
    class Uniforms {

        private UniformBlock blockOf(String uniforms) throws IOException {
            return read("""
                    { "targets": {}, "passes": [{
                      "vertex_shader": "v", "fragment_shader": "f", "output": "minecraft:main",
                      "uniforms": %s
                    }] }
                    """.formatted(uniforms)).passes().get(0).uniforms().get(0);
        }

        @Test
        @DisplayName("entry order is the layout, so it is preserved exactly")
        void entryOrderIsPreserved() throws IOException {
            // Not a bag of named values: position decides which bytes the shader reads
            // each entry from. Reordering changes the meaning of everything after.
            UniformBlock block = blockOf("""
                    { "Config": [
                      { "name": "First",  "type": "float", "value": 0.0 },
                      { "name": "Second", "type": "float", "value": 1.0 },
                      { "name": "Third",  "type": "vec4",  "value": [0,0,0,0] }
                    ] }
                    """);

            assertEquals(List.of("First", "Second", "Third"),
                    block.members().stream().map(Std140.Member::name).toList());
        }

        @Test
        void typesAreRead() throws IOException {
            UniformBlock block = blockOf("""
                    { "Config": [
                      { "name": "F", "type": "float", "value": 0.0 },
                      { "name": "V", "type": "vec4",  "value": [0,0,0,0] }
                    ] }
                    """);

            assertEquals(GlslType.FLOAT, block.members().get(0).type());
            assertEquals(GlslType.VEC4, block.members().get(1).type());
        }

        @Test
        void countMakesAnArray() throws IOException {
            UniformBlock block = blockOf("""
                    { "Config": [{ "name": "Items", "type": "vec4", "count": 8, "value": [0,0,0,0] }] }
                    """);

            assertEquals(8, block.members().get(0).arrayLength());
            assertEquals(128, block.sizeInBytes());
        }

        @Test
        @DisplayName("an unknown type is refused rather than guessed at")
        void unknownTypesAreRejected() {
            // Silently skipping it would shift every later member's offset, producing a
            // confident and wrong layout comparison.
            JsonSyntaxException e = assertThrows(JsonSyntaxException.class, () -> blockOf("""
                    { "Config": [{ "name": "Odd", "type": "quaternion", "value": 0.0 }] }
                    """));

            assertTrue(e.getMessage().contains("quaternion"), e.getMessage());
            assertTrue(e.getMessage().contains("Odd"), e.getMessage());
        }

        @Test
        @DisplayName("values are ignored: defaults do not affect layout")
        void valuesDoNotMatter() throws IOException {
            assertEquals(blockOf("""
                    { "C": [{ "name": "F", "type": "float", "value": 0.0 }] }
                    """).members(),
                    blockOf("""
                    { "C": [{ "name": "F", "type": "float", "value": 99.5 }] }
                    """).members());
        }

        @Test
        void blockSizeFollowsStd140() throws IOException {
            // Three floats then a vec4: the vec4 cannot start at byte 12.
            UniformBlock block = blockOf("""
                    { "C": [
                      { "name": "a", "type": "float", "value": 0.0 },
                      { "name": "b", "type": "float", "value": 0.0 },
                      { "name": "c", "type": "float", "value": 0.0 },
                      { "name": "v", "type": "vec4",  "value": [0,0,0,0] }
                    ] }
                    """);

            assertEquals(32, block.sizeInBytes());
        }
    }
}
