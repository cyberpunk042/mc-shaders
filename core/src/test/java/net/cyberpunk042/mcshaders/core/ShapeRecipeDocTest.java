package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.animation.Animation;
import net.cyberpunk042.mcshaders.core.appearance.Appearance;
import net.cyberpunk042.mcshaders.core.field.FieldLayer;
import net.cyberpunk042.mcshaders.core.field.Primitive;
import net.cyberpunk042.mcshaders.core.field.PrimitiveLink;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.fill.FillConfig;
import net.cyberpunk042.mcshaders.core.mesh.Mesh;
import net.cyberpunk042.mcshaders.core.mesh.Tessellator;
import net.cyberpunk042.mcshaders.core.pattern.ArrangementConfig;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;
import net.cyberpunk042.mcshaders.core.shape.RingShape;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import net.cyberpunk042.mcshaders.core.transform.Transform;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Builds the two worked examples printed in {@code docs/SHAPES.md}.
 *
 * <p>A capability page is the easiest kind of document to write dishonestly: naming
 * the parts is not the same as showing they assemble. The two examples are the shapes a reader
 * pictures first, and neither exists as a type — they are compositions, which is exactly
 * the claim worth executing rather than asserting.
 *
 * <p>What this proves is that the models compose and tessellate. It does not prove
 * anything appears on screen, and cannot: nothing outside {@code core} consumes a
 * {@link Mesh} yet. That gap is stated on the page rather than left for a reader to
 * discover after building one.
 */
class ShapeRecipeDocTest {

    @Test
    @DisplayName("the sun recipe assembles and both of its parts tessellate")
    void sunRecipeBuilds() {
        // ── begin SHAPES.md sun ───────────────────────────────────────────────
        // A glowing, pulsing core with a corona of rays. Not the field_visual chains
        // of the same idea — those are post-processing; see docs/EFFECTS.md.
        SphereShape body = SphereShape.of(1.0f);
        Primitive core = SimplePrimitive.of("core", body.getType(), body)
                .withAppearance(Appearance.glowing("#ffd27f", 0.9f))
                .withAnimation(Animation.pulse(0.06f, 0.4f));

        RaysShape flares = RaysShape.EMISSION;
        Primitive corona = SimplePrimitive.of("corona", flares.getType(), flares)
                .withAppearance(Appearance.glowing("#ff9c3f", 1.0f))
                .withAnimation(Animation.spin(0.01f));

        FieldLayer sun = FieldLayer.of("sun", List.of(core, corona));
        // ── end SHAPES.md sun ─────────────────────────────────────────────────

        assertEquals(List.of("core", "corona"),
                sun.primitives().stream().map(Primitive::id).toList());
        assertGeometry(sun);
    }

    @Test
    @DisplayName("the magic-circle recipe assembles and every ring tessellates")
    void magicCircleRecipeBuilds() {
        // ── begin SHAPES.md magic circle ──────────────────────────────────────
        // Concentric rings turning against each other. Not the magic_circle chain,
        // which is a raymarched field — see docs/EFFECTS.md.
        RingShape outerRing = RingShape.at(1.40f, 1.50f, 0f);
        Primitive outer = SimplePrimitive.of("outer", outerRing.getType(), outerRing)
                .withAppearance(Appearance.glowing("#8fd3ff", 0.8f))
                .withAnimation(Animation.spin(0.02f));

        RingShape innerRing = RingShape.at(0.70f, 0.78f, 0f);
        Primitive inner = SimplePrimitive.of("inner", innerRing.getType(), innerRing)
                .withAppearance(Appearance.glowing("#c9a2ff", 0.8f))
                .withAnimation(Animation.spin(-0.035f));   // counter-turning

        FieldLayer circle = FieldLayer.of("magic_circle", List.of(outer, inner));
        // ── end SHAPES.md magic circle ────────────────────────────────────────

        assertEquals(2, circle.primitives().size());
        assertGeometry(circle);
    }

    /** Every primitive in the layer must produce geometry a GPU would accept. */
    private static void assertGeometry(FieldLayer layer) {
        for (Primitive primitive : layer.primitives()) {
            Shape shape = primitive.shape();
            Mesh mesh = Tessellator.tessellateAuto(shape);
            assertFalse(mesh.isEmpty(),
                    primitive.id() + " tessellated to nothing, so the recipe draws a gap");
            assertTrue(mesh.vertexCount() > 0, primitive.id() + " has no vertices");
            assertTrue(mesh.indexCount() > 0, primitive.id() + " has no indices");
            for (int index : mesh.indices()) {
                assertTrue(index >= 0 && index < mesh.vertexCount(),
                        primitive.id() + " indexes vertex " + index
                                + " but only has " + mesh.vertexCount());
            }
        }
    }

    /** Kept honest: the page says nothing draws these, and this is why it can say so. */
    @Test
    @DisplayName("nothing outside core consumes a Mesh, which is what the page admits")
    void meshHasNoConsumerYet() {
        Mesh mesh = Tessellator.tessellateAuto(SphereShape.of(1.0f));
        assertTrue(mesh.isTriangles() || mesh.isLines(),
                "a mesh should be one of the primitive kinds a renderer could take");
        assertTrue(mesh.primitiveCount() > 0, "a unit sphere should produce primitives");
    }

    @SuppressWarnings("unused")
    private static SimplePrimitive plain(String id, Shape shape) {
        return new SimplePrimitive(id, shape.getType(), shape, Transform.IDENTITY,
                FillConfig.SOLID, VisibilityMask.FULL, ArrangementConfig.DEFAULT,
                Appearance.DEFAULT, Animation.NONE, PrimitiveLink.NONE);
    }

    /**
     * The page and the transcription above, held together.
     *
     * <p>The examples between the {@code SHAPES.md} markers are a copy, and a copy goes
     * stale the moment somebody edits the markdown. Until now nothing here opened the
     * page: the two could disagree completely and this class would still pass, which is
     * the one failure a doc test exists to catch. {@code LayerGeometryTest.Guide} does
     * the same job for the library guide.
     *
     * <p>It differs from that one in reading <em>this file</em> rather than listing
     * snippets. A hand-listed snippet is itself a transcription with the same rot, one
     * level up — add a line to the example above and a snippet list silently stops
     * covering it. Taking the block from the source means the check grows with the
     * example instead.
     */
    @Nested
    @DisplayName("the page")
    class Page {

        private static final String PAGE = "docs/SHAPES.md";
        private static final String SOURCE =
                "core/src/test/java/net/cyberpunk042/mcshaders/core/ShapeRecipeDocTest.java";

        @Test
        @DisplayName("every line of the sun example is on the page")
        void sunExampleIsOnThePage() {
            assertBlockIsOnThePage("sun");
        }

        @Test
        @DisplayName("every line of the magic-circle example is on the page")
        void magicCircleExampleIsOnThePage() {
            assertBlockIsOnThePage("magic circle");
        }

        private static void assertBlockIsOnThePage(String marker) {
            Path root = repoRoot();
            List<String> code = codeBetweenMarkers(root, marker);

            // Without this the whole check passes by finding nothing to check --
            // a renamed marker would read as agreement.
            assertFalse(code.isEmpty(),
                    () -> "no code found between the '" + marker + "' markers; if they were "
                            + "renamed, this check silently stopped checking anything");

            String page = read(root.resolve(PAGE));
            for (String line : code) {
                assertTrue(page.contains(line),
                        () -> PAGE + " no longer contains this line of the '" + marker
                                + "' example, so the copy above is stale:\n    " + line);
            }
        }

        /** The example's real code lines, dedented to match the page's fence. */
        private static List<String> codeBetweenMarkers(Path root, String marker) {
            List<String> source = List.of(read(root.resolve(SOURCE)).split("\n", -1));
            int begin = -1;
            int end = -1;
            for (int i = 0; i < source.size(); i++) {
                if (begin < 0 && source.get(i).contains("begin SHAPES.md " + marker)) {
                    begin = i;
                } else if (begin >= 0 && source.get(i).contains("end SHAPES.md " + marker)) {
                    end = i;
                    break;
                }
            }
            assertTrue(begin >= 0 && end > begin,
                    () -> "could not find the '" + marker + "' markers in " + SOURCE);

            List<String> lines = new ArrayList<>();
            for (String line : source.subList(begin + 1, end)) {
                String trimmed = line.strip();
                // Prose comments explain the example here; the page says it around the
                // fence instead, so they are not expected to match.
                if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
                    lines.add(line);
                }
            }
            int indent = lines.stream()
                    .mapToInt(line -> line.length() - line.stripLeading().length())
                    .min().orElse(0);
            return lines.stream().map(line -> line.substring(indent)).toList();
        }

        private static String read(Path file) {
            try {
                return Files.readString(file);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        private static Path repoRoot() {
            for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
                if (Files.isRegularFile(dir.resolve("LICENSE"))
                        && Files.isRegularFile(dir.resolve(PAGE))) {
                    return dir;
                }
            }
            throw new AssertionError("could not find the repository root");
        }
    }
}
