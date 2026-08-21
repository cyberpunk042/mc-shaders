package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.pattern.CellType;
import net.cyberpunk042.mcshaders.core.shape.CylinderShape;
import net.cyberpunk042.mcshaders.core.shape.PrismShape;
import net.cyberpunk042.mcshaders.core.shape.RingShape;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the ported shape model.
 *
 * <p>These exist mainly to catch damage from the port itself. Each shape lost its
 * JSON binding on the way across, and the strip was automated — so what matters is
 * that the geometry contract every shape must satisfy still holds for all of them,
 * uniformly. A test written per-shape would not have caught a strip that quietly
 * broke one of them.
 */
class ShapeModelTest {

    /** Every shape in the ported set, built with defaults. */
    private static List<Shape> allShapes() {
        return List.of(
                SphereShape.builder().build(),
                RingShape.builder().build(),
                CylinderShape.builder().build(),
                PrismShape.builder().build());
    }

    @Nested
    @DisplayName("the Shape contract holds for every ported shape")
    class Contract {

        @Test
        void everyShapeReportsAType() {
            for (Shape s : allShapes()) {
                assertNotNull(s.getType(), s.getClass().getSimpleName() + " has no type");
                assertFalse(s.getType().isBlank(),
                        s.getClass().getSimpleName() + " has a blank type");
            }
        }

        @Test
        void typesAreDistinct() {
            List<String> types = allShapes().stream().map(Shape::getType).toList();
            assertEquals(types.size(), types.stream().distinct().count(),
                    "two shapes claiming the same type would collide in a registry");
        }

        @Test
        @DisplayName("bounds are finite and non-negative")
        void boundsAreUsable() {
            for (Shape s : allShapes()) {
                Vector3f b = s.getBounds();
                assertNotNull(b, s.getType() + " has no bounds");
                for (float c : new float[] {b.x, b.y, b.z}) {
                    assertTrue(Float.isFinite(c), s.getType() + " has a non-finite bound");
                    assertTrue(c >= 0f, s.getType() + " has a negative bound");
                }
            }
        }

        @Test
        @DisplayName("the default radius derives from the largest bound")
        void radiusFollowsBounds() {
            for (Shape s : allShapes()) {
                Vector3f b = s.getBounds();
                float expected = Math.max(b.x, Math.max(b.y, b.z)) / 2f;
                assertEquals(expected, s.getRadius(), 1e-5f,
                        s.getType() + " radius disagrees with its bounds");
            }
        }

        @Test
        void everyShapeHasAPrimaryCellType() {
            for (Shape s : allShapes()) {
                assertNotNull(s.primaryCellType(), s.getType() + " has no primary cell type");
            }
        }

        @Test
        @DisplayName("parts are named, non-empty, and carry a cell type each")
        void partsAreWellFormed() {
            for (Shape s : allShapes()) {
                Map<String, CellType> parts = s.getParts();
                assertNotNull(parts, s.getType() + " has no parts map");
                assertFalse(parts.isEmpty(), s.getType() + " declares no parts");
                parts.forEach((name, cell) -> {
                    assertFalse(name == null || name.isBlank(),
                            s.getType() + " has an unnamed part");
                    assertNotNull(cell, s.getType() + " part '" + name + "' has no cell type");
                });
            }
        }
    }

    @Nested
    @DisplayName("builders and clamping survived the port")
    class Builders {

        @Test
        void ringBuilderRoundTripsItsValues() {
            RingShape ring = RingShape.builder()
                    .innerRadius(0.5f)
                    .outerRadius(2.0f)
                    .segments(32)
                    .build();

            assertEquals(0.5f, ring.innerRadius(), 1e-6f);
            assertEquals(2.0f, ring.outerRadius(), 1e-6f);
            assertEquals(32, ring.segments());
        }

        @Test
        @DisplayName("alpha stays within [0,1] — clamping is part of the model, not the binding")
        void alphaIsClamped() {
            RingShape over = RingShape.builder().topAlpha(5.0f).bottomAlpha(-3.0f).build();
            assertEquals(1.0f, over.topAlpha(), 1e-6f);
            assertEquals(0.0f, over.bottomAlpha(), 1e-6f);
        }

        @Test
        void heightSegmentsCannotDropBelowOne() {
            assertEquals(1, RingShape.builder().heightSegments(0).build().heightSegments());
            assertEquals(1, RingShape.builder().heightSegments(-9).build().heightSegments());
        }

        @Test
        void taperCannotGoNegative() {
            assertEquals(0f, RingShape.builder().taper(-2f).build().taper(), 1e-6f);
        }

        @Test
        @DisplayName("a rebuilt shape equals the original")
        void toBuilderRoundTrips() {
            RingShape original = RingShape.builder()
                    .innerRadius(0.3f).outerRadius(1.4f).segments(48).twist(30f).build();
            assertEquals(original, original.toBuilder().build(),
                    "toBuilder() must preserve every component");
        }
    }

    @Nested
    @DisplayName("sphere specifics")
    class Sphere {

        @Test
        void defaultSphereHasNoDeformationOrHorizon() {
            SphereShape s = SphereShape.builder().build();
            assertFalse(s.hasDeformation(), "a default sphere should be undeformed");
            assertFalse(s.hasHorizonEffect(), "a default sphere should have no rim glow");
        }

        @Test
        @DisplayName("the effect records still build from shape parameters after the JSON strip")
        void effectConversionSurvived() {
            SphereShape s = SphereShape.builder().build();
            assertNotNull(s.toHorizonEffect(), "horizon conversion was lost in the port");
            assertNotNull(s.toCoronaEffect(), "corona conversion was lost in the port");
        }
    }
}
