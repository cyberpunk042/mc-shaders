package net.cyberpunk042.mcshaders.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Tests for the optional GLSL compile step.
 *
 * <p>The text-handling parts are tested unconditionally, because that is where the
 * bugs would be. The compiling parts run only where a validator is installed —
 * skipped rather than failed elsewhere, since the whole point of the step is that
 * it is optional.
 */
class GlslCompilerTest {

    static boolean validatorPresent() {
        return new GlslCompiler().isAvailable();
    }

    @Test
    @DisplayName("#version is moved to the front, because flattening can bury it")
    void versionIsLifted() {
        String flattened = "// from an include\nfloat helper() { return 1.0; }\n#version 150\nvoid main() {}";

        String lifted = GlslCompiler.liftVersion(flattened);

        assertTrue(lifted.startsWith("#version 150"), lifted);
        assertTrue(lifted.contains("helper"), "nothing else may be lost");
    }

    @Test
    void sourceWithoutAVersionIsLeftAlone() {
        assertEquals("void main() {}", GlslCompiler.liftVersion("void main() {}"));
    }

    @Test
    void onlyTheFirstVersionIsLifted() {
        // A second #version is the shader's problem to answer for, not something to
        // quietly tidy away.
        String lifted = GlslCompiler.liftVersion("#version 150\nx\n#version 330\n");

        assertEquals("#version 150", lifted.lines().findFirst().orElseThrow());
        assertTrue(lifted.contains("#version 330"));
    }

    @Test
    @DisplayName("source using Minecraft's own include directive is not ours to judge")
    void mojImportIsNotCompiled() {
        // The game's loader expands #moj_import; glslang rejects it as an unknown
        // directive. Reporting that would blame the shader for a gap in the checker.
        assertFalse(GlslCompiler.canRead("#version 150\n#moj_import <minecraft:fog>\n"));
        assertTrue(GlslCompiler.canRead("#version 150\n#include \"lib.glsl\"\n"));
    }

    @Test
    @EnabledIf("validatorPresent")
    void validGlslCompiles() {
        List<String> errors = new GlslCompiler().compile(
                "#version 150\nout vec4 fragColor;\nvoid main() { fragColor = vec4(1.0); }\n", "frag");

        assertTrue(errors.isEmpty(), errors::toString);
    }

    @Test
    @EnabledIf("validatorPresent")
    @DisplayName("an undefined call is reported with its line")
    void invalidGlslIsReported() {
        List<String> errors = new GlslCompiler().compile(
                "#version 150\nout vec4 c;\nvoid main() { c = nope(1.0); }\n", "frag");

        assertFalse(errors.isEmpty());
        assertTrue(errors.get(0).contains("nope"), errors.toString());
        assertTrue(errors.get(0).contains("0:3"), "the line number is the useful part: " + errors);
    }

    @Test
    @EnabledIf("validatorPresent")
    void sourceItCannotReadYieldsNoErrors() {
        assertTrue(new GlslCompiler().compile("#version 150\n#moj_import <x>\n", "frag").isEmpty());
    }
}
