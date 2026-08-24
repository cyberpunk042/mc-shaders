package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.cyberpunk042.mcshaders.core.mesh.Mesh;
import net.cyberpunk042.mcshaders.core.mesh.SphereTessellator;
import net.cyberpunk042.mcshaders.core.pattern.QuadPattern;
import net.cyberpunk042.mcshaders.core.pattern.VertexPattern;
import net.cyberpunk042.mcshaders.core.shape.SphereAlgorithm;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What each sphere algorithm says it can do, against what tessellating it actually does.
 *
 * <p>{@link SphereAlgorithm#supportsPartialSphere()} and
 * {@link SphereAlgorithm#supportsPatterns()} are capability claims: a GUI would grey out
 * a control on them, a validator would accept or reject content on them. Both had no
 * callers and no tests, and one of them was wrong — {@code supportsPartialSphere} claimed
 * {@code UV_SPHERE}, whose tessellator reads {@code latSteps}, {@code lonSteps} and
 * {@code radius} and generates the entire surface. Content asking for half a UV sphere
 * got a whole one, silently, and the predicate agreed it was fine.
 *
 * <p>The claim is only checkable against behaviour, so that is what these do: set the
 * thing the claim is about, and see whether the mesh changes.
 *
 * <h2>Why two algorithms are excluded</h2>
 *
 * <p>{@code TYPE_A} and {@code TYPE_E} have no mesh form and are substituted with a
 * lat-lon sphere — see {@code DirectRenderingSubstitutionTest}. Their meshes therefore
 * honour ranges and patterns, while the claims describe the direct renderer they stand
 * in for. Comparing the two would measure the substitution, not the claim, so they are
 * skipped explicitly rather than quietly: {@code requiresDirectRendering} is asked, so
 * if that set ever changes this test follows it.
 */
class SphereAlgorithmClaimsTest {

    /** The algorithms whose mesh is really their own. */
    private static List<SphereAlgorithm> meshBacked() {
        List<SphereAlgorithm> own = new ArrayList<>();
        for (SphereAlgorithm algorithm : SphereAlgorithm.values()) {
            if (!SphereTessellator.requiresDirectRendering(algorithm)) {
                own.add(algorithm);
            }
        }
        assertFalse(own.isEmpty(), "every algorithm is substituted — nothing left to check");
        return own;
    }

    private static SphereShape.Builder base(SphereAlgorithm algorithm) {
        return SphereShape.of(1.0f).toBuilder().algorithm(algorithm);
    }

    private static boolean identical(Mesh a, Mesh b) {
        return a.primitiveType() == b.primitiveType()
                && Arrays.equals(a.indices(), b.indices())
                && a.vertices().equals(b.vertices());
    }

    @Nested
    @DisplayName("supportsPartialSphere")
    class PartialSphere {

        private Mesh mesh(SphereAlgorithm algorithm, float latStart, float latEnd,
                float lonStart, float lonEnd) {
            return SphereTessellator.tessellate(base(algorithm)
                    .latStart(latStart).latEnd(latEnd)
                    .lonStart(lonStart).lonEnd(lonEnd).build());
        }

        @Test
        @DisplayName("true exactly when narrowing the latitude range changes the mesh")
        void latitudeRangeIsHonouredExactlyWhenClaimed() {
            for (SphereAlgorithm algorithm : meshBacked()) {
                Mesh whole = mesh(algorithm, 0f, 1f, 0f, 1f);
                Mesh half = mesh(algorithm, 0f, 0.5f, 0f, 1f);

                assertEquals(!identical(whole, half), algorithm.supportsPartialSphere(),
                        () -> "supportsPartialSphere() must mean 'a narrowed range produces "
                                + "different geometry'. " + algorithm + " says "
                                + algorithm.supportsPartialSphere() + " and does the opposite, "
                                + "so content asking for a hemisphere is silently given "
                                + "whatever this algorithm felt like.");
            }
        }

        @Test
        @DisplayName("and the longitude range agrees with the latitude range")
        void longitudeRangeIsHonouredExactlyWhenClaimed() {
            for (SphereAlgorithm algorithm : meshBacked()) {
                Mesh whole = mesh(algorithm, 0f, 1f, 0f, 1f);
                Mesh wedge = mesh(algorithm, 0f, 1f, 0f, 0.5f);

                assertEquals(!identical(whole, wedge), algorithm.supportsPartialSphere(),
                        () -> "the claim covers both axes; " + algorithm + " honours one and "
                                + "not the other, which the single predicate cannot express");
            }
        }

        @Test
        @DisplayName("lat_lon really does cut, so the true case is not vacuous")
        void latLonActuallyCuts() {
            Mesh whole = mesh(SphereAlgorithm.LAT_LON, 0f, 1f, 0f, 1f);
            Mesh half = mesh(SphereAlgorithm.LAT_LON, 0f, 0.5f, 0f, 1f);

            assertTrue(SphereAlgorithm.LAT_LON.supportsPartialSphere());
            assertFalse(identical(whole, half),
                    "if this ever stops cutting, the test above passes by agreeing that "
                            + "nothing supports partial spheres");
            assertFalse(half.isEmpty(), "half a sphere is still geometry");
        }
    }

    @Nested
    @DisplayName("supportsPatterns")
    class Patterns {

        private Mesh mesh(SphereAlgorithm algorithm, VertexPattern pattern) {
            return SphereTessellator.tessellate(base(algorithm).build(), pattern, null);
        }

        @Test
        @DisplayName("true exactly when swapping the pattern changes the mesh")
        void patternIsHonouredExactlyWhenClaimed() {
            for (SphereAlgorithm algorithm : meshBacked()) {
                Mesh plain = mesh(algorithm, QuadPattern.FILLED_1);
                Mesh shaped = mesh(algorithm, QuadPattern.TRIANGLE_1);

                assertEquals(!identical(plain, shaped), algorithm.supportsPatterns(),
                        () -> algorithm + " claims supportsPatterns() = "
                                + algorithm.supportsPatterns()
                                + " but the mesh says otherwise. Note that being handed a "
                                + "pattern is not the same as using one: uv_sphere passes it "
                                + "through and ignores it, which is why the claim is false "
                                + "there and correctly so.");
            }
        }
    }
}
