package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import net.cyberpunk042.mcshaders.core.mesh.Mesh;
import net.cyberpunk042.mcshaders.core.mesh.SphereTessellator;
import net.cyberpunk042.mcshaders.core.shape.SphereAlgorithm;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What {@code requiresDirectRendering} claims, and what {@code tessellate} does.
 *
 * <p>{@code TYPE_A} and {@code TYPE_E} draw overlapping cubes rather than a surface, so
 * they have no mesh form. {@link SphereTessellator#tessellate(SphereShape)} does not
 * refuse them: it logs a warning and hands back a lat-lon sphere instead, so a caller
 * gets geometry rather than nothing. That is a reasonable choice and a silent one — no
 * exception, a non-empty mesh, a different shape.
 *
 * <p>{@link SphereTessellator#requiresDirectRendering(SphereAlgorithm)} is the predicate
 * that names which algorithms this happens to. Before this test it had <strong>no
 * callers anywhere in the repository</strong> — its only occurrence was its own
 * declaration — which meant nothing would notice if it stopped agreeing with the
 * {@code switch} it describes. A predicate that names a hazard and is never consulted
 * cannot go wrong loudly.
 *
 * <p>It is not hypothetical: two of the 64 shape files in {@code the-virus-block-mc}
 * ask for these algorithms, so the substitution fires on real content today. See
 * {@code docs/VIRUS-BLOCK-FIELD-STATE.md}.
 *
 * <h2>The invariant</h2>
 *
 * <p>For every algorithm other than {@code LAT_LON} itself, the predicate is true
 * exactly when tessellating produces a mesh identical to {@code LAT_LON}'s. That one
 * statement catches all three ways this can rot: a new constant added to the
 * {@code switch} that also falls back but is missing from the predicate, the predicate
 * going stale if either algorithm gains a real tessellator, and the substitute quietly
 * becoming something other than lat-lon.
 */
class DirectRenderingSubstitutionTest {

    /** One radius, so any difference between two meshes is the algorithm's. */
    private static Mesh meshFor(SphereAlgorithm algorithm) {
        return SphereTessellator.tessellate(
                SphereShape.of(1.0f).toBuilder().algorithm(algorithm).build());
    }

    /** Same vertices, same indices, same topology — not merely the same size. */
    private static boolean identical(Mesh a, Mesh b) {
        return a.primitiveType() == b.primitiveType()
                && Arrays.equals(a.indices(), b.indices())
                && a.vertices().equals(b.vertices());
    }

    @Nested
    @DisplayName("the predicate agrees with the switch")
    class Agreement {

        @Test
        @DisplayName("true exactly for the algorithms that come back as lat-lon")
        void predicateMatchesSubstitution() {
            Mesh latLon = meshFor(SphereAlgorithm.LAT_LON);

            for (SphereAlgorithm algorithm : SphereAlgorithm.values()) {
                if (algorithm == SphereAlgorithm.LAT_LON) {
                    // Trivially identical to itself; it carries no information either way.
                    continue;
                }
                boolean substituted = identical(meshFor(algorithm), latLon);
                assertEquals(substituted,
                        SphereTessellator.requiresDirectRendering(algorithm),
                        () -> "requiresDirectRendering(" + algorithm + ") must mean 'tessellate "
                                + "hands back a lat-lon sphere instead'. It "
                                + (substituted ? "does and the predicate denies it"
                                                : "does not and the predicate claims it")
                                + ", so the predicate no longer describes the switch.");
            }
        }

        @Test
        @DisplayName("the two that fall back really do produce lat-lon, not just something")
        void theSubstituteIsLatLonItself() {
            Mesh latLon = meshFor(SphereAlgorithm.LAT_LON);

            for (SphereAlgorithm algorithm : List.of(SphereAlgorithm.TYPE_A,
                    SphereAlgorithm.TYPE_E)) {
                Mesh mesh = meshFor(algorithm);
                assertTrue(identical(mesh, latLon),
                        () -> algorithm + " is documented as falling back to LAT_LON; if the "
                                + "substitute changes, callers silently get a third shape");
                assertFalse(mesh.isEmpty(),
                        () -> algorithm + " must yield geometry — the whole point of the "
                                + "fallback is that the caller gets a mesh rather than nothing");
            }
        }
    }

    @Nested
    @DisplayName("every constant is handled")
    class Exhaustive {

        @Test
        @DisplayName("no algorithm throws, and none tessellates to nothing")
        void allAlgorithmsProduceGeometry() {
            for (SphereAlgorithm algorithm : SphereAlgorithm.values()) {
                Mesh mesh = meshFor(algorithm);
                assertFalse(mesh.isEmpty(),
                        () -> algorithm + " produced an empty mesh, which a caller cannot tell "
                                + "apart from a shape that was meant to be invisible");
                assertTrue(mesh.vertexCount() > 0 && mesh.indexCount() > 0,
                        () -> algorithm + " produced a mesh with no drawable content");
            }
        }
    }
}
