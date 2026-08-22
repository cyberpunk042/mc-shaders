package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cyberpunk042.mcshaders.core.layout.GlslBlocks;
import net.cyberpunk042.mcshaders.core.layout.GlslType;
import net.cyberpunk042.mcshaders.core.layout.LayoutComparison;
import net.cyberpunk042.mcshaders.core.layout.LayoutMismatch;
import net.cyberpunk042.mcshaders.core.layout.Std140;
import net.cyberpunk042.mcshaders.core.layout.Std140.Member;
import net.cyberpunk042.mcshaders.core.layout.UniformBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for uniform-block layout: the std140 offset rules, reading a block back
 * out of GLSL, and comparing two declarations of the same block.
 *
 * <p>The comparison is the point of all this. A block is bound by offset and
 * declared in two places that nothing checks against each other, so a drift
 * between them produces wrong pictures and no errors. These tests pin both halves
 * of that: real drift is caught, and the ways a block can be spelled differently
 * without meaning anything different are not reported as drift.
 */
class LayoutTest {

    private static UniformBlock block(String name, Object... pairs) {
        List<Member> members = new java.util.ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            members.add(new Member((String) pairs[i], (GlslType) pairs[i + 1]));
        }
        return new UniformBlock(name, members);
    }

    @Nested
    @DisplayName("std140 offsets")
    class Offsets {

        @Test
        void scalarsPackTightly() {
            List<Std140.Placement> p = Std140.place(List.of(
                    new Member("a", GlslType.FLOAT),
                    new Member("b", GlslType.FLOAT),
                    new Member("c", GlslType.FLOAT),
                    new Member("d", GlslType.FLOAT)));

            assertEquals(List.of(0, 4, 8, 12), p.stream().map(Std140.Placement::offset).toList());
        }

        @Test
        @DisplayName("a vec4 after three floats is pushed to the next 16-byte boundary")
        void vectorsAlign() {
            List<Std140.Placement> p = Std140.place(List.of(
                    new Member("a", GlslType.FLOAT),
                    new Member("b", GlslType.FLOAT),
                    new Member("c", GlslType.FLOAT),
                    new Member("v", GlslType.VEC4)));

            assertEquals(16, p.get(3).offset(), "the vec4 cannot start at byte 12");
        }

        @Test
        @DisplayName("vec3 is twelve bytes but leaves a four-byte hole")
        void vec3LeavesAHole() {
            List<Std140.Placement> p = Std140.place(List.of(
                    new Member("v", GlslType.VEC3),
                    new Member("f", GlslType.FLOAT)));

            // The float fits in the hole: vec3 occupies 0-11, so 12 is free and aligned.
            assertEquals(12, p.get(1).offset());
            assertEquals(16, Std140.sizeOf(List.of(new Member("v", GlslType.VEC3))));
        }

        @Test
        void arrayElementsAreStridedToSixteen() {
            // Four floats in an array take 64 bytes, not 16 — the classic surprise.
            assertEquals(64, Std140.sizeOf(List.of(new Member("a", GlslType.FLOAT, 4))));
        }

        @Test
        @DisplayName("expanding an array preserves the offsets of the array itself")
        void expandingAnArrayIsOffsetPreserving() {
            List<Member> array = List.of(new Member("a", GlslType.FLOAT, 4));

            assertEquals(Std140.sizeOf(array), Std140.sizeOf(Std140.expand(array)));
            assertEquals(List.of(0, 16, 32, 48),
                    Std140.place(Std140.expand(array)).stream().map(Std140.Placement::offset).toList());
        }

        @Test
        void blockSizeRoundsUp() {
            assertEquals(16, Std140.sizeOf(List.of(new Member("f", GlslType.FLOAT))));
        }

        @Test
        void matricesAreColumnsPaddedToSixteen() {
            assertEquals(64, Std140.sizeOf(List.of(new Member("m", GlslType.MAT4))));
            assertEquals(48, Std140.sizeOf(List.of(new Member("m", GlslType.MAT3))));
        }
    }

    @Nested
    @DisplayName("reading blocks out of GLSL")
    class Parsing {

        @Test
        void findsBlockAndMembersInOrder() {
            var blocks = GlslBlocks.blocks("""
                    #version 150
                    layout(std140) uniform Config {
                        float Radius;
                        vec4 Colour;
                        mat4 Transform;
                    };
                    """);

            assertEquals(List.of("Config"), List.copyOf(blocks.keySet()));
            assertEquals(List.of("Radius", "Colour", "Transform"),
                    blocks.get("Config").members().stream().map(Member::name).toList());
        }

        @Test
        @DisplayName("a member commented out is not a member")
        void commentsAreIgnored() {
            var blocks = GlslBlocks.blocks("""
                    layout(std140) uniform Config {
                        float Kept;
                        // float Removed;
                        /* float AlsoRemoved; */
                        float Also;
                    };
                    """);

            assertEquals(List.of("Kept", "Also"),
                    blocks.get("Config").members().stream().map(Member::name).toList());
        }

        @Test
        void readsArrayLengths() {
            var blocks = GlslBlocks.blocks("layout(std140) uniform B { vec4 Items[8]; };");
            assertEquals(8, blocks.get("B").members().get(0).arrayLength());
        }

        @Test
        void findsSeveralBlocks() {
            var blocks = GlslBlocks.blocks("""
                    layout(std140) uniform First { float a; };
                    layout(std140) uniform Second { float b; };
                    """);
            assertEquals(List.of("First", "Second"), List.copyOf(blocks.keySet()));
        }

        @Test
        void findsSamplers() {
            assertEquals(List.of("InSampler", "DepthSampler"), GlslBlocks.samplers("""
                    uniform sampler2D InSampler;
                    uniform sampler2D DepthSampler;
                    layout(std140) uniform B { float a; };
                    """));
        }

        @Test
        @DisplayName("a block with no std140 qualifier is not read")
        void onlyStd140Blocks() {
            assertTrue(GlslBlocks.blocks("uniform Config { float a; };").isEmpty());
        }
    }

    @Nested
    @DisplayName("comparing two declarations")
    class Comparison {

        @Test
        void identicalBlocksAgree() {
            UniformBlock b = block("C", "a", GlslType.FLOAT, "v", GlslType.VEC4);
            assertTrue(LayoutComparison.agree(b, b));
        }

        @Test
        @DisplayName("a matrix and the four vec4s that spell it are the same bytes")
        void matricesCompareAgainstTheirColumns() {
            // Minecraft's post-effect JSON has no matrix type, so a mat4 has to be
            // written as four vec4 rows. That is not a mismatch and must not be
            // reported as one, or the real mismatches drown.
            UniformBlock shader = block("Camera", "ViewProj", GlslType.MAT4);
            UniformBlock host = block("Camera",
                    "ViewProj[0]", GlslType.VEC4, "ViewProj[1]", GlslType.VEC4,
                    "ViewProj[2]", GlslType.VEC4, "ViewProj[3]", GlslType.VEC4);

            assertTrue(LayoutComparison.agree(shader, host), () -> LayoutComparison.compare(shader, host).toString());
        }

        @Test
        @DisplayName("an inserted member shifts everything after it")
        void insertionIsCaughtAtTheOffsetItHappens() {
            UniformBlock shader = block("C", "a", GlslType.FLOAT, "b", GlslType.FLOAT, "c", GlslType.FLOAT);
            UniformBlock host = block("C", "a", GlslType.FLOAT, "inserted", GlslType.FLOAT,
                    "b", GlslType.FLOAT, "c", GlslType.FLOAT);

            List<LayoutMismatch> problems = LayoutComparison.compare(shader, host);

            assertEquals(LayoutMismatch.Kind.DIVERGENT_MEMBER, problems.get(0).kind());
            assertEquals(4, problems.get(0).offset(), "everything from byte 4 on is misread");
        }

        @Test
        @DisplayName("only the first divergence is reported, not every member after it")
        void divergenceIsReportedOnce() {
            UniformBlock shader = block("C", "a", GlslType.FLOAT, "b", GlslType.FLOAT,
                    "c", GlslType.FLOAT, "d", GlslType.FLOAT);
            UniformBlock host = block("C", "a", GlslType.FLOAT, "x", GlslType.FLOAT,
                    "y", GlslType.FLOAT, "z", GlslType.FLOAT);

            assertEquals(1, LayoutComparison.compare(shader, host).size(),
                    "three consequences of one cause is not three findings");
        }

        @Test
        void aShorterHostLeavesTheTailUnwritten() {
            UniformBlock shader = block("C", "a", GlslType.FLOAT, "b", GlslType.FLOAT);
            UniformBlock host = block("C", "a", GlslType.FLOAT);

            List<LayoutMismatch> problems = LayoutComparison.compare(shader, host);

            assertEquals(LayoutMismatch.Kind.TRUNCATED, problems.get(0).kind());
            assertEquals(4, problems.get(0).offset());
            assertTrue(problems.get(0).detail().contains("more member"), problems.get(0).detail());
        }

        @Test
        void aLongerHostWritesPastTheEnd() {
            UniformBlock shader = block("C", "a", GlslType.FLOAT);
            UniformBlock host = block("C", "a", GlslType.FLOAT, "b", GlslType.FLOAT);

            List<LayoutMismatch> problems = LayoutComparison.compare(shader, host);

            assertEquals(LayoutMismatch.Kind.TRUNCATED, problems.get(0).kind());
            assertTrue(problems.get(0).detail().contains("past the end"), problems.get(0).detail());
        }

        @Test
        void renamingOneMemberIsCaught() {
            UniformBlock shader = block("C", "Radius", GlslType.FLOAT);
            UniformBlock host = block("C", "Size", GlslType.FLOAT);

            assertFalse(LayoutComparison.agree(shader, host),
                    "same offset and type, but the host is filling in a different parameter");
        }

        @Test
        void changingATypeAtTheSameOffsetIsCaught() {
            UniformBlock shader = block("C", "v", GlslType.VEC4);
            UniformBlock host = block("C", "v", GlslType.IVEC4);

            assertEquals(LayoutMismatch.Kind.TYPE_MISMATCH,
                    LayoutComparison.compare(shader, host).get(0).kind());
        }

        @Test
        @DisplayName("a rename that leaves the rest aligned is a warning, not an error")
        void aPureRenameIsNotAnError() {
            UniformBlock shader = block("C", "a", GlslType.FLOAT, "Radius", GlslType.FLOAT,
                    "c", GlslType.FLOAT);
            UniformBlock host = block("C", "a", GlslType.FLOAT, "Size", GlslType.FLOAT,
                    "c", GlslType.FLOAT);

            List<LayoutMismatch> problems = LayoutComparison.compare(shader, host);

            assertEquals(LayoutMismatch.Kind.RENAMED_MEMBER, problems.get(0).kind());
            assertEquals(LayoutMismatch.Severity.WARNING, problems.get(0).severity());
            assertTrue(LayoutComparison.errors(shader, host).isEmpty());
        }

        @Test
        @DisplayName("a rename that leaves the rest misaligned is the real thing")
        void driftMasqueradingAsARenameIsStillAnError() {
            UniformBlock shader = block("C", "a", GlslType.FLOAT, "b", GlslType.FLOAT,
                    "c", GlslType.FLOAT);
            UniformBlock host = block("C", "a", GlslType.FLOAT, "x", GlslType.FLOAT,
                    "y", GlslType.FLOAT);

            List<LayoutMismatch> problems = LayoutComparison.compare(shader, host);

            assertEquals(LayoutMismatch.Kind.DIVERGENT_MEMBER, problems.get(0).kind());
            assertTrue(problems.get(0).isError());
        }

        @Test
        @DisplayName("writing into a slot the shader reserves is a note, not a failure")
        void reservedSlotsAreInformational() {
            UniformBlock shader = block("C", "Reserved3_0", GlslType.FLOAT);
            UniformBlock host = block("C", "CameraX", GlslType.FLOAT);

            List<LayoutMismatch> problems = LayoutComparison.compare(shader, host);

            assertEquals(LayoutMismatch.Kind.RESERVED_SLOT_WRITTEN, problems.get(0).kind());
            assertEquals(LayoutMismatch.Severity.INFO, problems.get(0).severity());
            assertTrue(LayoutComparison.agree(shader, host),
                    "the bytes are fine, so this must not fail the comparison");
        }

        @Test
        @DisplayName("a matrix's columns compare by position, whatever the host calls them")
        void columnNamesAreNotCompared() {
            // The host has no matrix type and calls them rows; the expansion here calls
            // them indices. Neither name is the author's intent, so neither is evidence.
            UniformBlock shader = block("Camera", "ViewProjUBO", GlslType.MAT4);
            UniformBlock host = block("Camera",
                    "ViewProjRow0", GlslType.VEC4, "ViewProjRow1", GlslType.VEC4,
                    "ViewProjRow2", GlslType.VEC4, "ViewProjRow3", GlslType.VEC4);

            assertTrue(LayoutComparison.agree(shader, host),
                    () -> LayoutComparison.compare(shader, host).toString());
        }

        @Test
        @DisplayName("an array the host writes one element of leaves the rest unwritten")
        void anArrayCountsAsItsElements() {
            // One declaration, thirty-two slots. Counting declarations would call this
            // a size difference; counting slots calls it what it is.
            UniformBlock shader = new UniformBlock("C", List.of(
                    new Member("head", GlslType.VEC4),
                    new Member("tail", GlslType.VEC4, 32)));
            UniformBlock host = block("C", "head", GlslType.VEC4, "tail", GlslType.VEC4);

            List<LayoutMismatch> problems = LayoutComparison.compare(shader, host);

            assertEquals(LayoutMismatch.Kind.TRUNCATED, problems.get(0).kind());
            assertTrue(problems.get(0).isError());
            assertTrue(problems.get(0).detail().contains("31 more"), problems.get(0).detail());
        }

        @Test
        void aShortHostIsAnErrorAndALongOneAWarning() {
            UniformBlock one = block("C", "a", GlslType.FLOAT);
            UniformBlock two = block("C", "a", GlslType.FLOAT, "b", GlslType.FLOAT);

            assertEquals(LayoutMismatch.Severity.ERROR,
                    LayoutComparison.compare(two, one).get(0).severity(),
                    "the shader reads a member nobody wrote");
            assertEquals(LayoutMismatch.Severity.WARNING,
                    LayoutComparison.compare(one, two).get(0).severity(),
                    "the host writes a member nobody reads");
        }
    }
}
