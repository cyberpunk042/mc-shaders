package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cyberpunk042.mcshaders.core.appearance.AlphaRange;
import net.cyberpunk042.mcshaders.core.appearance.Appearance;
import net.cyberpunk042.mcshaders.core.field.FieldLayer;
import net.cyberpunk042.mcshaders.core.field.LayerGeometry;
import net.cyberpunk042.mcshaders.core.field.Primitive;
import net.cyberpunk042.mcshaders.core.field.PrimitiveLink;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.mesh.Tessellator;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import net.cyberpunk042.mcshaders.core.transform.Transform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The layer-to-geometry sequence, and the step it refuses to choose for you.
 *
 * <p>The sequence was documented and manual before this, and the failure that
 * motivates it is the radius one: in the mod this model came from, a {@code radiusMatch}
 * link resolved to the right number and nothing ever applied it. So the interesting
 * tests here are not "does it tessellate" — {@code TessellationTest} covers that — but
 * that the radius step is reachable, that it is not taken behind your back, and that
 * choosing to skip it is a choice you can see at the call site.
 */
class LayerGeometryTest {

    private static Primitive sphere(String id, float radius) {
        return SimplePrimitive.of(id, SphereShape.of(radius).getType(), SphereShape.of(radius));
    }

    /** A second sphere that takes its radius from the first. */
    private static Primitive linkedTo(String id, String target, float ownRadius) {
        return SimplePrimitive.of(id, SphereShape.of(ownRadius).getType(),
                        SphereShape.of(ownRadius))
                .withLink(PrimitiveLink.radiusMatch(target, 0f));
    }

    @Nested
    @DisplayName("the radius step")
    class Radius {

        @Test
        @DisplayName("a policy is required, and the message says what to pass")
        void policyIsRequired() {
            IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                    () -> LayerGeometry.build(FieldLayer.of("l", List.of(sphere("a", 1f))), null));

            assertTrue(thrown.getMessage().contains("RadiusPolicy.IGNORE"),
                    () -> "a caller who has not thought about radius needs to be told what "
                            + "the do-nothing answer is called: " + thrown.getMessage());
        }

        @Test
        @DisplayName("IGNORE reproduces the old renderer: the link resolves and nothing resizes")
        void ignoreLeavesTheShapeAlone() {
            FieldLayer layer = FieldLayer.of("l", List.of(
                    sphere("big", 4.0f), linkedTo("small", "big", 1.0f)));

            List<LayerGeometry.Piece> pieces =
                    LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);

            assertEquals(Tessellator.tessellate(SphereShape.of(1.0f), 0).vertexCount(),
                    pieces.get(1).mesh().vertexCount(),
                    "IGNORE means the linked primitive keeps its own radius — this is the "
                            + "behaviour that made radiusMatch do nothing, named rather than "
                            + "stumbled into");
        }

        @Test
        @DisplayName("a policy that resizes actually changes the mesh")
        void policyIsApplied() {
            FieldLayer layer = FieldLayer.of("l", List.of(
                    sphere("big", 4.0f), linkedTo("small", "big", 1.0f)));

            List<LayerGeometry.Piece> ignored =
                    LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);
            List<LayerGeometry.Piece> applied = LayerGeometry.build(layer,
                    (shape, radius) -> shape instanceof SphereShape s
                            ? SphereShape.of(radius) : shape);

            assertNotEquals(
                    ignored.get(1).mesh().vertices().get(0).position(),
                    applied.get(1).mesh().vertices().get(0).position(),
                    "the whole point of the policy is that taking it changes geometry");
        }

        @Test
        @DisplayName("a policy is only consulted when a link actually resolved a radius")
        void policyIsNotConsultedWithoutALink() {
            FieldLayer layer = FieldLayer.of("l", List.of(sphere("lonely", 1.0f)));

            LayerGeometry.build(layer, (shape, radius) -> {
                throw new AssertionError("no link resolved a radius, so nothing should ask");
            });
        }

        @Test
        @DisplayName("a policy returning null keeps the original rather than exploding")
        void nullFromPolicyIsSurvivable() {
            FieldLayer layer = FieldLayer.of("l", List.of(
                    sphere("big", 4.0f), linkedTo("small", "big", 1.0f)));

            List<LayerGeometry.Piece> pieces =
                    LayerGeometry.build(layer, (shape, radius) -> null);

            assertTrue(pieces.get(1).mesh().vertexCount() > 0,
                    "a policy that declines should behave like IGNORE, not blank the mesh");
        }
    }

    @Nested
    @DisplayName("what it composes")
    class Composition {

        @Test
        @DisplayName("the layer's transform reaches every primitive")
        void layerTransformApplies() {
            Transform moved = Transform.offset(10f, 0f, 0f);
            FieldLayer layer = FieldLayer.of("l", List.of(sphere("a", 1f)), moved);

            List<LayerGeometry.Piece> pieces =
                    LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);

            assertEquals(10f, pieces.get(0).transform().offset().x(), 1e-6f,
                    "FieldLayer.transform is documented as applied to every primitive");
        }

        @Test
        @DisplayName("layer and primitive offsets add, which is what TransformStack says")
        void offsetsCompose() {
            Primitive shifted = SimplePrimitive.of("a", SphereShape.of(1f).getType(),
                    SphereShape.of(1f)).withTransform(Transform.offset(3f, 0f, 0f));
            FieldLayer layer = FieldLayer.of("l", List.of(shifted),
                    Transform.offset(10f, 0f, 0f));

            List<LayerGeometry.Piece> pieces =
                    LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);

            assertEquals(13f, pieces.get(0).transform().offset().x(), 1e-6f,
                    "core's own composition rule is that offsets add; this must not "
                            + "invent a different one");
        }

        @Test
        @DisplayName("the layer's alpha multiplies the primitive's")
        void layerAlphaMultiplies() {
            Primitive half = SimplePrimitive.of("a", SphereShape.of(1f).getType(),
                            SphereShape.of(1f))
                    .withAppearance(Appearance.DEFAULT.withAlpha(AlphaRange.of(0.5f)));
            FieldLayer layer = FieldLayer.builder("l").primitives(half).alpha(0.5f).build();

            List<LayerGeometry.Piece> pieces =
                    LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);

            assertEquals(0.25f, pieces.get(0).appearance().alpha().max(), 1e-6f,
                    "FieldLayer.alpha is documented as multiplying, not replacing");
        }

        @Test
        @DisplayName("animation is handed back untouched, because it is a function of time")
        void animationIsNotBaked() {
            Primitive spinning = SimplePrimitive.of("a", SphereShape.of(1f).getType(),
                    SphereShape.of(1f));
            FieldLayer layer = FieldLayer.of("l", List.of(spinning));

            List<LayerGeometry.Piece> pieces =
                    LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);

            assertSame(spinning, pieces.get(0).primitive(),
                    "a renderer needs the primitive to drive its own clock; baking a "
                            + "phase here would freeze it");
        }
    }

    @Nested
    @DisplayName("the guide")
    class Guide {

        private static final String GUIDE = "docs/USING_AS_A_LIBRARY.md";

        // The guide's examples are transcribed here, and a transcription goes stale the
        // moment someone edits the markdown. LibraryDocExampleTest has that gap on the
        // core side: it runs its copies and never opens the page. These assertions are
        // the other half, and core/build.gradle.kts declares the page a test input so
        // that a doc-only edit actually re-runs them.

        @Test
        @DisplayName("the documented call is the one that exists")
        void documentedCallRuns() {
            FieldLayer layer = FieldLayer.of("l", List.of(sphere("a", 1f)));

            List<LayerGeometry.Piece> pieces =
                    LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);

            assertEquals(1, pieces.size());
            assertGuideShows("LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);");
        }

        @Test
        @DisplayName("the documented resizing policy compiles and resizes")
        void documentedPolicyRuns() {
            FieldLayer layer = FieldLayer.of("l", List.of(
                    sphere("big", 4.0f), linkedTo("small", "big", 1.0f)));

            List<LayerGeometry.Piece> pieces = LayerGeometry.build(layer, (shape, radius) ->
                    shape instanceof SphereShape ? SphereShape.of(radius) : shape);

            assertEquals(Tessellator.tessellate(SphereShape.of(4.0f), 0).vertexCount(),
                    pieces.get(1).mesh().vertexCount(),
                    "the guide's policy example should make the linked sphere match");
            assertGuideShows("shape instanceof SphereShape ? SphereShape.of(radius) : shape);");
        }

        private static void assertGuideShows(String snippet) {
            String guide;
            try {
                guide = java.nio.file.Files.readString(repoRoot().resolve(GUIDE));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            assertTrue(guide.contains(snippet),
                    () -> "the guide no longer contains this, so the example is stale:\n"
                            + snippet);
        }

        private static java.nio.file.Path repoRoot() {
            for (java.nio.file.Path dir = java.nio.file.Path.of("").toAbsolutePath();
                    dir != null; dir = dir.getParent()) {
                if (java.nio.file.Files.isRegularFile(dir.resolve("LICENSE"))
                        && java.nio.file.Files.isRegularFile(dir.resolve(GUIDE))) {
                    return dir;
                }
            }
            throw new AssertionError("could not find the repository root");
        }
    }

    @Nested
    @DisplayName("edges")
    class Edges {

        @Test
        @DisplayName("declaration order is preserved, because links point backwards")
        void orderIsPreserved() {
            FieldLayer layer = FieldLayer.of("l",
                    List.of(sphere("first", 1f), sphere("second", 1f), sphere("third", 1f)));

            List<LayerGeometry.Piece> pieces =
                    LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE);

            assertEquals(List.of("first", "second", "third"),
                    pieces.stream().map(LayerGeometry.Piece::id).toList());
        }

        @Test
        @DisplayName("a hidden layer still builds; asking isDrawable is the caller's job")
        void invisibleLayerStillBuilds() {
            FieldLayer hidden = FieldLayer.builder("l")
                    .primitives(sphere("a", 1f)).visible(false).build();

            assertEquals(1, LayerGeometry.build(hidden,
                            LayerGeometry.RadiusPolicy.IGNORE).size(),
                    "an editor previewing a hidden layer wants the geometry, and returning "
                            + "nothing would look identical to a failure");
        }

        @Test
        @DisplayName("an empty layer is an empty list, and a null one too")
        void emptyAndNull() {
            assertTrue(LayerGeometry.build(FieldLayer.empty("l"),
                    LayerGeometry.RadiusPolicy.IGNORE).isEmpty());
            assertTrue(LayerGeometry.build(null,
                    LayerGeometry.RadiusPolicy.IGNORE).isEmpty());
        }

        @Test
        @DisplayName("a primitive with no shape yields an empty mesh, not a crash")
        void missingShapeIsEmptyMesh() {
            Primitive shapeless = SimplePrimitive.of("a", "sphere", (Shape) null);
            FieldLayer layer = FieldLayer.of("l", List.of(shapeless));

            assertEquals(0, LayerGeometry.build(layer, LayerGeometry.RadiusPolicy.IGNORE)
                    .get(0).mesh().vertexCount());
        }
    }
}
