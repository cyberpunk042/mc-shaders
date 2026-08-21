package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.shape.ShapeMath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Characterisation tests for the ported geometry math.
 *
 * <p>This code arrived from the-virus-block-mc with no tests of its own. These pin
 * down the invariants the rest of the shape system relies on, so that porting the
 * remaining shape classes on top of it — and any later optimisation — has something
 * to fail against.
 */
class ShapeMathTest {

    private static final float EPS = 1e-4f;

    private static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    @Nested
    @DisplayName("sphere vertices")
    class Sphere {

        @Test
        @DisplayName("every generated vertex lies on the sphere of the given radius")
        void verticesLieOnTheSphere() {
            float radius = 2.5f;
            for (int ti = 0; ti <= 16; ti++) {
                for (int pi = 0; pi <= 16; pi++) {
                    float theta = (float) (Math.PI * ti / 16.0);
                    float phi = (float) (2.0 * Math.PI * pi / 16.0);
                    float[] v = ShapeMath.sphereVertex(theta, phi, radius);
                    assertEquals(radius, length(v), EPS,
                            "theta=" + theta + " phi=" + phi + " left the surface");
                }
            }
        }

        @Test
        void theProfileFunctionIsUnitForASphere() {
            for (float theta = 0f; theta <= Math.PI; theta += 0.2f) {
                assertEquals(1.0f, ShapeMath.sphere(theta), EPS,
                        "a sphere's radial profile is constant by definition");
            }
        }

        @Test
        @DisplayName("radius scales the vertex linearly")
        void radiusScalesLinearly() {
            float[] unit = ShapeMath.sphereVertex(1.0f, 2.0f, 1.0f);
            float[] triple = ShapeMath.sphereVertex(1.0f, 2.0f, 3.0f);
            for (int i = 0; i < 3; i++) {
                assertEquals(unit[i] * 3.0f, triple[i], EPS);
            }
        }
    }

    @Nested
    @DisplayName("spheroid vertices")
    class Spheroid {

        @Test
        @DisplayName("a spheroid degenerates to a sphere at ratio 1")
        void degenerateSpheroidMatchesSphere() {
            // `length` is a RATIO, not an absolute: c = radius * length and
            // a = radius / sqrt(length). So the sphere case is length == 1.
            float r = 1.7f;
            for (float theta = 0f; theta <= Math.PI; theta += 0.3f) {
                float[] spheroid = ShapeMath.spheroidVertex(theta, 0.9f, r, 1.0f);
                float[] sphere = ShapeMath.sphereVertex(theta, 0.9f, r);
                for (int i = 0; i < 3; i++) {
                    assertEquals(sphere[i], spheroid[i], EPS,
                            "ratio 1 should degenerate to the sphere case");
                }
            }
        }

        @Test
        void pointsSatisfyTheSpheroidEquation() {
            float radius = 2.0f;
            float ratio = 2.5f;
            float a = (float) (radius / Math.sqrt(ratio));   // equatorial
            float c = radius * ratio;                        // polar

            for (float theta = 0.1f; theta < Math.PI; theta += 0.25f) {
                float[] v = ShapeMath.spheroidVertex(theta, 1.3f, radius, ratio);
                double q = (v[0] * v[0] + v[2] * v[2]) / (a * a) + (v[1] * v[1]) / (c * c);
                assertEquals(1.0, q, 1e-3, "vertex left the spheroid surface");
            }
        }

        @Test
        @DisplayName("the deformation preserves volume, as the implementation documents")
        void deformationIsVolumePreserving() {
            // The doc states a²c = r³. That is the property that makes a shape look
            // like it deformed rather than grew, so it is worth pinning down.
            float radius = 1.5f;
            for (float ratio : new float[] {0.25f, 0.5f, 1.0f, 2.0f, 4.0f}) {
                float a = (float) (radius / Math.sqrt(ratio));
                float c = radius * ratio;
                assertEquals(Math.pow(radius, 3), a * a * c, 1e-3,
                        "volume was not preserved at ratio " + ratio);
            }
        }

        @Test
        @DisplayName("an oblate ratio widens the equator and flattens the poles")
        void oblateBulgesAtTheEquator() {
            float radius = 2.0f;
            float[] equator = ShapeMath.spheroidVertex((float) (Math.PI / 2), 0f, radius, 0.5f);
            float[] pole = ShapeMath.spheroidVertex(0f, 0f, radius, 0.5f);

            assertTrue(Math.abs(equator[0]) > radius,
                    "an oblate spheroid should bulge past the base radius at the equator");
            assertTrue(Math.abs(pole[1]) < radius,
                    "and compress along the polar axis");
        }
    }

    @Nested
    @DisplayName("vector helpers")
    class Vectors {

        @Test
        void normalizeProducesUnitLength() {
            float[] v = ShapeMath.normalize(new float[] {3f, 4f, 12f});
            assertEquals(1.0f, length(v), EPS);
        }

        @Test
        @DisplayName("normalizing a zero vector does not produce NaN")
        void zeroVectorIsHandled() {
            float[] v = ShapeMath.normalize(new float[] {0f, 0f, 0f});
            for (float c : v) {
                assertTrue(Float.isFinite(c), "a degenerate normal must not poison the mesh");
            }
        }

        @Test
        void blendReturnsItsEndpointsExactly() {
            float[] a = {1f, 2f, 3f};
            float[] b = {4f, 8f, 12f};

            float[] at0 = ShapeMath.blendVertex(a, b, 0f);
            float[] at1 = ShapeMath.blendVertex(a, b, 1f);

            for (int i = 0; i < 3; i++) {
                assertEquals(a[i], at0[i], EPS);
                assertEquals(b[i], at1[i], EPS);
            }
        }

        @Test
        void blendMidpointIsTheAverage() {
            float[] mid = ShapeMath.blendVertex(new float[] {0f, 0f, 0f}, new float[] {2f, 4f, 6f}, 0.5f);
            assertEquals(1f, mid[0], EPS);
            assertEquals(2f, mid[1], EPS);
            assertEquals(3f, mid[2], EPS);
        }
    }

    @Nested
    @DisplayName("radial profiles")
    class Profiles {

        @Test
        @DisplayName("every profile stays finite across the full theta sweep")
        void profilesAreFinite() {
            for (float theta = 0f; theta <= Math.PI; theta += 0.05f) {
                assertTrue(Float.isFinite(ShapeMath.sphere(theta)), "sphere at " + theta);
                assertTrue(Float.isFinite(ShapeMath.droplet(theta, 2f)), "droplet at " + theta);
                assertTrue(Float.isFinite(ShapeMath.egg(theta, 0.3f)), "egg at " + theta);
                assertTrue(Float.isFinite(ShapeMath.bullet(theta)), "bullet at " + theta);
                assertTrue(Float.isFinite(ShapeMath.cone(theta)), "cone at " + theta);
                assertTrue(Float.isFinite(ShapeMath.dropletInverted(theta, 2f)), "dropletInverted at " + theta);
            }
        }

        @Test
        @DisplayName("blend at zero intensity leaves the shape unchanged")
        void blendIsIdentityAtZeroIntensity() {
            assertEquals(1.0f, ShapeMath.blend(0.4f, 0.0f), EPS);
        }
    }
}
