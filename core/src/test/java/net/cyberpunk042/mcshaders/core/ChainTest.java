package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.cyberpunk042.mcshaders.core.chain.ChainProblem;
import net.cyberpunk042.mcshaders.core.chain.ChainValidator;
import net.cyberpunk042.mcshaders.core.chain.Input;
import net.cyberpunk042.mcshaders.core.chain.Pass;
import net.cyberpunk042.mcshaders.core.chain.PostChain;
import net.cyberpunk042.mcshaders.core.chain.TargetSpec;
import net.cyberpunk042.mcshaders.core.glsl.SourceProvider;
import net.cyberpunk042.mcshaders.core.layout.GlslType;
import net.cyberpunk042.mcshaders.core.layout.LayoutMismatch;
import net.cyberpunk042.mcshaders.core.layout.Std140;
import net.cyberpunk042.mcshaders.core.layout.UniformBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for validating a post-processing chain against the shaders it names.
 *
 * <p>These are the checks that do not need a GPU, which turns out to be most of
 * them: a chain usually fails because a shader moved, a target is read before it is
 * written, or a uniform block means two different things — not because the GLSL
 * would not compile.
 */
class ChainTest {

    private static final String MAIN = "minecraft:main";
    private static final Set<String> HOST_TARGETS = Set.of(MAIN);

    private static final String TRIVIAL = """
            #version 150
            uniform sampler2D InSampler;
            out vec4 fragColor;
            void main() { fragColor = texture(InSampler, vec2(0.0)); }
            """;

    private static SourceProvider provider(Map<String, String> files) {
        return path -> Optional.ofNullable(files.get(path));
    }

    private static SourceProvider trivial() {
        return provider(Map.of("post/vert", TRIVIAL, "post/frag", TRIVIAL));
    }

    private static Pass pass(String output, Input... inputs) {
        return new Pass("post/vert", "post/frag", List.of(inputs), output, List.of());
    }

    private static List<ChainProblem> validate(PostChain chain, SourceProvider provider) {
        return new ChainValidator(provider, HOST_TARGETS).validate(chain);
    }

    @Nested
    @DisplayName("targets")
    class Targets {

        @Test
        void aChainReadingAndWritingTheHostTargetIsSound() {
            PostChain chain = new PostChain(Map.of(), List.of(pass(MAIN, new Input("In", MAIN))));

            assertTrue(new ChainValidator(trivial(), HOST_TARGETS).isSound(chain),
                    () -> validate(chain, trivial()).toString());
        }

        @Test
        void readingATargetNobodyDeclaresIsAnError() {
            PostChain chain = new PostChain(Map.of(), List.of(pass(MAIN, new Input("In", "ghost"))));

            List<ChainProblem> problems = validate(chain, trivial());

            assertEquals(ChainProblem.Kind.UNKNOWN_TARGET, problems.get(0).kind());
            assertTrue(problems.get(0).isError());
        }

        @Test
        void writingATargetNobodyDeclaresIsAnError() {
            PostChain chain = new PostChain(Map.of(), List.of(pass("ghost", new Input("In", MAIN))));

            assertTrue(validate(chain, trivial()).stream()
                    .anyMatch(p -> p.kind() == ChainProblem.Kind.UNKNOWN_OUTPUT && p.isError()));
        }

        @Test
        @DisplayName("reading a declared target before any pass has written it is a warning")
        void readBeforeWriteIsCaught() {
            // Legal, and occasionally deliberate across frames — but far more often a
            // pass order that was rearranged and not re-checked.
            PostChain chain = new PostChain(
                    Map.of("swap", TargetSpec.SCREEN_SIZED),
                    List.of(pass(MAIN, new Input("In", "swap"))));

            List<ChainProblem> problems = validate(chain, trivial());

            assertTrue(problems.stream().anyMatch(p -> p.kind() == ChainProblem.Kind.READ_BEFORE_WRITE));
        }

        @Test
        void writingThenReadingATargetIsFine() {
            PostChain chain = new PostChain(
                    Map.of("swap", TargetSpec.SCREEN_SIZED),
                    List.of(pass("swap", new Input("In", MAIN)),
                            pass(MAIN, new Input("In", "swap"))));

            assertTrue(validate(chain, trivial()).stream()
                    .noneMatch(p -> p.kind() == ChainProblem.Kind.READ_BEFORE_WRITE));
        }

        @Test
        void aTargetNothingReadsIsWorthMentioning() {
            PostChain chain = new PostChain(
                    Map.of("unused", TargetSpec.SCREEN_SIZED),
                    List.of(pass(MAIN, new Input("In", MAIN))));

            List<ChainProblem> problems = validate(chain, trivial());

            assertEquals(ChainProblem.Kind.UNUSED_TARGET, problems.get(0).kind());
            assertEquals(LayoutMismatch.Severity.INFO, problems.get(0).severity());
        }

        @Test
        void aTargetIsEitherScreenSizedOrFixed() {
            assertTrue(TargetSpec.SCREEN_SIZED.isScreenSized());
            assertFalse(new TargetSpec(256, 256).isScreenSized());
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                    () -> new TargetSpec(256, 0));
        }
    }

    @Nested
    @DisplayName("shaders")
    class Shaders {

        @Test
        void aMissingShaderIsAnError() {
            PostChain chain = new PostChain(Map.of(), List.of(pass(MAIN, new Input("In", MAIN))));

            List<ChainProblem> problems = validate(chain, provider(Map.of()));

            assertTrue(problems.stream().anyMatch(p -> p.kind() == ChainProblem.Kind.MISSING_SHADER));
        }

        @Test
        @DisplayName("an include that moved away is reported as such, not as a missing shader")
        void anUnresolvedIncludeIsItsOwnFinding() {
            // The shader is present; what moved is a file it pulls in. Saying "shader
            // missing" would send someone looking in the wrong place.
            SourceProvider p = provider(Map.of(
                    "post/vert", TRIVIAL,
                    "post/frag", "#version 150\n#include \"gone.glsl\"\n" + TRIVIAL));
            PostChain chain = new PostChain(Map.of(), List.of(pass(MAIN, new Input("In", MAIN))));

            List<ChainProblem> problems = validate(chain, p);

            assertTrue(problems.stream().anyMatch(x -> x.kind() == ChainProblem.Kind.UNRESOLVED_INCLUDE));
            assertTrue(problems.stream().noneMatch(x -> x.kind() == ChainProblem.Kind.MISSING_SHADER));
        }

        @Test
        @DisplayName("nothing is read out of a shader whose includes failed")
        void aBrokenIncludeStopsFurtherChecks() {
            // The flattened source is incomplete, so its samplers and blocks would be a
            // guess. One real finding beats a page of consequences.
            SourceProvider p = provider(Map.of(
                    "post/vert", TRIVIAL,
                    "post/frag", "#version 150\n#include \"gone.glsl\"\n"));
            PostChain chain = new PostChain(Map.of(), List.of(pass(MAIN, new Input("In", MAIN))));

            assertTrue(validate(chain, p).stream()
                    .noneMatch(x -> x.kind() == ChainProblem.Kind.SAMPLER_MISMATCH));
        }
    }

    @Nested
    @DisplayName("samplers")
    class Samplers {

        @Test
        @DisplayName("an input named In must find a sampler named InSampler")
        void theSuffixConventionIsEnforced() {
            PostChain chain = new PostChain(Map.of(), List.of(pass(MAIN, new Input("Depth", MAIN))));

            List<ChainProblem> problems = validate(chain, trivial());

            assertTrue(problems.stream().anyMatch(p -> p.kind() == ChainProblem.Kind.SAMPLER_MISMATCH
                    && p.isError() && p.detail().contains("DepthSampler")));
        }

        @Test
        void aSamplerNothingIsBoundToIsAWarning() {
            SourceProvider p = provider(Map.of(
                    "post/vert", TRIVIAL,
                    "post/frag", "#version 150\nuniform sampler2D InSampler;\nuniform sampler2D DepthSampler;\n"));
            PostChain chain = new PostChain(Map.of(), List.of(pass(MAIN, new Input("In", MAIN))));

            List<ChainProblem> problems = validate(chain, p);

            assertTrue(problems.stream().anyMatch(x -> x.kind() == ChainProblem.Kind.SAMPLER_MISMATCH
                    && x.severity() == LayoutMismatch.Severity.WARNING));
        }
    }

    @Nested
    @DisplayName("uniform blocks")
    class Uniforms {

        private Pass passWith(UniformBlock host) {
            return new Pass("post/vert", "post/frag", List.of(new Input("In", MAIN)), MAIN, List.of(host));
        }

        private SourceProvider withBlock(String glsl) {
            return provider(Map.of("post/vert", TRIVIAL, "post/frag", TRIVIAL + glsl));
        }

        @Test
        void anAgreeingBlockIsSilent() {
            UniformBlock host = new UniformBlock("Config", List.of(
                    new Std140.Member("Radius", GlslType.FLOAT)));
            PostChain chain = new PostChain(Map.of(), List.of(passWith(host)));

            assertTrue(validate(chain, withBlock("layout(std140) uniform Config { float Radius; };"))
                    .stream().noneMatch(p -> p.kind() == ChainProblem.Kind.LAYOUT_MISMATCH));
        }

        @Test
        @DisplayName("a block the host and the shader disagree about is an error, with the offset")
        void driftIsReportedThroughTheChain() {
            UniformBlock host = new UniformBlock("Config", List.of(
                    new Std140.Member("Radius", GlslType.FLOAT),
                    new Std140.Member("Extra", GlslType.FLOAT),
                    new Std140.Member("Tail", GlslType.FLOAT)));
            PostChain chain = new PostChain(Map.of(), List.of(passWith(host)));
            SourceProvider p = withBlock(
                    "layout(std140) uniform Config { float Radius; float Tail; float Other; };");

            List<ChainProblem> problems = validate(chain, p);

            assertTrue(problems.stream().anyMatch(x -> x.kind() == ChainProblem.Kind.LAYOUT_MISMATCH
                    && x.isError() && x.detail().contains("byte 4")), problems::toString);
        }

        @Test
        @DisplayName("a block this shader does not declare is a note, not a failure")
        void anUnusedBlockIsInformational() {
            // A chain commonly declares one block for several passes, and not every
            // pass reads it.
            UniformBlock host = new UniformBlock("Config", List.of(
                    new Std140.Member("Radius", GlslType.FLOAT)));
            PostChain chain = new PostChain(Map.of(), List.of(passWith(host)));

            List<ChainProblem> problems = validate(chain, trivial());

            assertTrue(problems.stream().anyMatch(x -> x.kind() == ChainProblem.Kind.LAYOUT_MISMATCH
                    && x.severity() == LayoutMismatch.Severity.INFO));
            assertTrue(new ChainValidator(trivial(), HOST_TARGETS).isSound(chain));
        }
    }
}
