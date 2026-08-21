package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.cyberpunk042.mcshaders.core.glsl.IncludeResolver;
import net.cyberpunk042.mcshaders.core.glsl.ResolvedShader;
import net.cyberpunk042.mcshaders.core.glsl.SourceProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IncludeResolverTest {

    /** An in-memory filesystem, which is all the resolver's seam needs. */
    private static final class Files implements SourceProvider {
        private final Map<String, String> files = new HashMap<>();

        Files put(String path, String content) {
            files.put(path, content);
            return this;
        }

        @Override
        public Optional<String> read(String path) {
            return Optional.ofNullable(files.get(path));
        }
    }

    private static IncludeResolver resolverOf(Files files) {
        return new IncludeResolver(files);
    }

    @Nested
    @DisplayName("path resolution")
    class Paths {

        @Test
        void includesResolveRelativeToTheIncludingFile() {
            Files files = new Files()
                    .put("shaders/post/main.fsh", "#include \"include/math.glsl\"\nbody();")
                    .put("shaders/post/include/math.glsl", "float m() { return 1.0; }");

            ResolvedShader result = resolverOf(files).resolve("shaders/post/main.fsh");

            assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
            assertTrue(result.source().contains("float m()"));
        }

        @Test
        void nestedIncludesResolveRelativeToTheirOwnDirectory() {
            Files files = new Files()
                    .put("shaders/post/main.fsh", "#include \"include/a.glsl\"")
                    .put("shaders/post/include/a.glsl", "#include \"sub/b.glsl\"\nfloat a();")
                    .put("shaders/post/include/sub/b.glsl", "float b();");

            ResolvedShader result = resolverOf(files).resolve("shaders/post/main.fsh");

            assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
            assertTrue(result.source().contains("float b();"));
            assertTrue(result.source().contains("float a();"));
        }

        @Test
        void parentSegmentsAreCollapsed() {
            Files files = new Files()
                    .put("shaders/post/main.fsh", "#include \"../core/shared.glsl\"")
                    .put("shaders/core/shared.glsl", "float shared();");

            ResolvedShader result = resolverOf(files).resolve("shaders/post/main.fsh");

            assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
            assertTrue(result.source().contains("float shared();"));
        }

        @Test
        void leadingSlashMeansNamespaceAbsolute() {
            Files files = new Files()
                    .put("shaders/post/deep/main.fsh", "#include \"/shaders/lib.glsl\"")
                    .put("shaders/lib.glsl", "float lib();");

            assertFalse(resolverOf(files).resolve("shaders/post/deep/main.fsh").hasErrors());
        }
    }

    @Nested
    @DisplayName("include-once and cycles")
    class Cycles {

        @Test
        @DisplayName("a shared dependency is expanded once, not duplicated")
        void diamondDependencyExpandsOnce() {
            Files files = new Files()
                    .put("main.fsh", "#include \"a.glsl\"\n#include \"b.glsl\"")
                    .put("a.glsl", "#include \"shared.glsl\"\nfloat a();")
                    .put("b.glsl", "#include \"shared.glsl\"\nfloat b();")
                    .put("shared.glsl", "float shared();");

            ResolvedShader result = resolverOf(files).resolve("main.fsh");

            assertFalse(result.hasErrors(), () -> result.diagnostics().toString());
            int occurrences = result.source().split("float shared\\(\\);", -1).length - 1;
            assertEquals(1, occurrences, "a duplicated definition would fail to compile");
        }

        @Test
        @DisplayName("a cycle is reported with its chain instead of overflowing the stack")
        void directCycleIsReported() {
            Files files = new Files()
                    .put("a.glsl", "#include \"b.glsl\"")
                    .put("b.glsl", "#include \"a.glsl\"");

            ResolvedShader result = resolverOf(files).resolve("a.glsl");

            assertTrue(result.hasErrors());
            String message = result.errors().get(0).message();
            assertTrue(message.contains("Circular include"), message);
            assertTrue(message.contains("a.glsl"), message);
        }

        @Test
        void indirectCycleIsReported() {
            Files files = new Files()
                    .put("a.glsl", "#include \"b.glsl\"")
                    .put("b.glsl", "#include \"c.glsl\"")
                    .put("c.glsl", "#include \"a.glsl\"");

            assertTrue(resolverOf(files).resolve("a.glsl").hasErrors());
        }

        @Test
        @DisplayName("deep nesting is capped rather than run to exhaustion")
        void depthIsBounded() {
            Files files = new Files();
            for (int i = 0; i < 12; i++) {
                files.put("f" + i + ".glsl", "#include \"f" + (i + 1) + ".glsl\"");
            }
            files.put("f12.glsl", "float leaf();");

            ResolvedShader result = new IncludeResolver(files, 4).resolve("f0.glsl");

            assertTrue(result.hasErrors());
            assertTrue(result.errors().get(0).message().contains("depth exceeded"));
        }
    }

    @Nested
    @DisplayName("failure handling")
    class Failures {

        @Test
        @DisplayName("a missing include is a diagnostic, not an exception")
        void missingIncludeIsReported() {
            Files files = new Files().put("main.fsh", "#include \"nope.glsl\"\nbody();");

            ResolvedShader result = resolverOf(files).resolve("main.fsh");

            assertTrue(result.hasErrors());
            ResolvedShader.Diagnostic error = result.errors().get(0);
            assertTrue(error.message().contains("nope.glsl"), error.message());
            assertEquals(1, error.line(), "the diagnostic should point at the directive");
            assertTrue(result.source().contains("body();"), "the rest still expands");
        }

        @Test
        @DisplayName("the result is never the input unchanged, which is what caused the recursion bug")
        void resultIsAlwaysDistinguishableFromInput() {
            // The original preprocessor returned its input on failure. A compile hook
            // then re-entered with identical input and recursed until the stack died.
            String source = "#include \"missing.glsl\"\nbody();";
            Files files = new Files().put("main.fsh", source);

            ResolvedShader result = resolverOf(files).resolve("main.fsh");

            assertFalse(result.source().equals(source),
                    "a result equal to its input cannot be distinguished from 'did nothing'");
            assertTrue(result.hasErrors(), "and the failure must be reported, not swallowed");
        }

        @Test
        void aMissingRootIsACallerErrorNotADiagnostic() {
            assertThrows(IllegalArgumentException.class,
                    () -> resolverOf(new Files()).resolve("absent.fsh"));
        }

        @Test
        @DisplayName("content containing regex replacement metacharacters survives verbatim")
        void dollarAndBackslashInIncludedContentAreNotInterpreted() {
            // Expanding via Matcher.appendReplacement would treat $ and \ in the
            // included text as group references and escapes, corrupting the source.
            Files files = new Files()
                    .put("main.fsh", "#include \"weird.glsl\"")
                    .put("weird.glsl", "// $1 \\n literal $$ chars");

            ResolvedShader result = resolverOf(files).resolve("main.fsh");

            assertTrue(result.source().contains("// $1 \\n literal $$ chars"),
                    "included content must be inserted literally");
        }
    }

    @Nested
    @DisplayName("GLSL correctness of the output")
    class Output {

        @Test
        @DisplayName("#version stays the first line, ahead of any #line directive")
        void versionRemainsFirst() {
            Files files = new Files()
                    .put("main.fsh", "#version 150\n#include \"a.glsl\"\nbody();")
                    .put("a.glsl", "float a();");

            ResolvedShader result = resolverOf(files).resolve("main.fsh");

            assertTrue(result.source().startsWith("#version 150\n"),
                    "a driver rejects #version that is not the first directive");
        }

        @Test
        @DisplayName("#line uses integer source indices, as GLSL 150 requires")
        void lineDirectivesAreIntegerForm() {
            Files files = new Files()
                    .put("shaders/main.fsh", "#version 150\n#include \"a.glsl\"\nbody();")
                    .put("shaders/a.glsl", "float a();");

            ResolvedShader result = resolverOf(files).resolve("shaders/main.fsh");

            for (String line : result.source().split("\n")) {
                if (line.startsWith("#line")) {
                    assertTrue(line.matches("#line \\d+ \\d+"),
                            "GLSL 150 #line takes integers only, got: " + line);
                }
            }
        }

        @Test
        @DisplayName("the source map turns a driver position back into a file and line")
        void sourceMapNamesTheRealFile() {
            Files files = new Files()
                    .put("shaders/main.fsh", "#version 150\n#include \"a.glsl\"\nbody();")
                    .put("shaders/a.glsl", "float a();");

            ResolvedShader result = resolverOf(files).resolve("shaders/main.fsh");

            assertEquals("shaders/main.fsh", result.sourceMap().pathOf(0).orElseThrow());
            assertEquals("shaders/a.glsl", result.sourceMap().pathOf(1).orElseThrow());
            assertEquals("shaders/a.glsl:7", result.sourceMap().describe(1, 7));
            assertTrue(result.sourceMap().describe(99, 3).contains("unknown"));
        }

        @Test
        void expansionResumesTheParentLineNumberingAfterAnInclude() {
            Files files = new Files()
                    .put("main.fsh", "#version 150\n#include \"a.glsl\"\nafterInclude();")
                    .put("a.glsl", "inA();");

            String out = resolverOf(files).resolve("main.fsh").source();

            // The line after the directive is line 3 of source 0.
            assertTrue(out.contains("#line 3 0"), out);
        }

        @Test
        void directivesWithTrailingCommentsAreRecognised() {
            Files files = new Files()
                    .put("main.fsh", "  #include \"a.glsl\"   // why we need this")
                    .put("a.glsl", "float a();");

            assertFalse(resolverOf(files).resolve("main.fsh").hasErrors());
        }

        @Test
        void aTextMentionOfIncludeInsideACommentIsNotExpanded() {
            Files files = new Files()
                    .put("main.fsh", "// see #include \"a.glsl\" for details\nbody();");

            ResolvedShader result = resolverOf(files).resolve("main.fsh");

            assertFalse(result.hasErrors(), "a commented mention must not be treated as a directive");
        }
    }
}
