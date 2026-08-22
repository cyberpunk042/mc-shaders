package net.cyberpunk042.mcshaders.core.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Comment stripping is package-private, and stays that way — but it carries an
 * invariant worth pinning, so this test lives in the package rather than the
 * method being widened to public for a test's convenience.
 */
class GlslBlocksInternalTest {

    @Test
    @DisplayName("stripping comments preserves line numbering")
    void lineCountIsUnchanged() {
        // Diagnostics point at line numbers in the original source, so removing a
        // block comment must leave its newlines behind or every line after it
        // reports one too high.
        String source = "one\n// two\n/* three\nfour */\nfive";

        String stripped = GlslBlocks.stripComments(source);

        assertEquals(source.lines().count(), stripped.lines().count());
        assertFalse(stripped.contains("three"));
        assertEquals("one", stripped.lines().findFirst().orElseThrow());
    }

    @Test
    void anUnterminatedBlockCommentSwallowsTheRest() {
        assertEquals("code ", GlslBlocks.stripComments("code /* never closed"));
    }
}
