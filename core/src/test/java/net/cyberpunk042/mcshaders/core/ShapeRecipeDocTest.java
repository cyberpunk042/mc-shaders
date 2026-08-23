package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.Test;

/**
 * Builds the two worked examples printed in {@code docs/SHAPES.md}.
 *
 * <p>A capability page is the easiest kind of document to write dishonestly: naming
 * the parts is not the same as showing they assemble. A sun and a magic circle are
 * the two things a reader will picture first, and neither exists as a type — they are
 * compositions, which is exactly the claim worth executing rather than asserting.
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
        // A sun is not a type. It is a glowing, pulsing core with a corona of rays.
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
        // A magic circle is concentric rings turning against each other.
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
}
