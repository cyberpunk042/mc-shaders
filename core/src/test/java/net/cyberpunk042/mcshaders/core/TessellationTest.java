package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import net.cyberpunk042.mcshaders.core.mesh.Mesh;
import net.cyberpunk042.mcshaders.core.mesh.PrimitiveType;
import net.cyberpunk042.mcshaders.core.mesh.Tessellator;
import net.cyberpunk042.mcshaders.core.mesh.Vertex;
import net.cyberpunk042.mcshaders.core.shape.CylinderShape;
import net.cyberpunk042.mcshaders.core.shape.PolyType;
import net.cyberpunk042.mcshaders.core.shape.PolyhedronShape;
import net.cyberpunk042.mcshaders.core.shape.PrismShape;
import net.cyberpunk042.mcshaders.core.shape.RingShape;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Checks that the tessellators produce geometry a GPU would accept.
 *
 * <p>These came across from a mod that had no tests for them, so "it compiles" is
 * all that was established by the port itself. What follows are the invariants
 * whose violation is not visible in a screenshot until it is far too late: an index
 * past the end of the vertex list is a driver crash or a read of unrelated memory,
 * a NaN coordinate silently removes a triangle, and a mesh whose points sit outside
 * the bounds the shape declares will be culled at the wrong time or clipped.
 */
class TessellationTest {

    /**
     * One entry per shape the dispatcher claims to handle, at a size where the
     * numbers are easy to reason about.
     */
    static Stream<org.junit.jupiter.params.provider.Arguments> shapes() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("sphere", SphereShape.of(1.0f)),
                org.junit.jupiter.params.provider.Arguments.of("ring", RingShape.DEFAULT),
                org.junit.jupiter.params.provider.Arguments.of("prism", PrismShape.of(6, 1.0f, 2.0f)),
                org.junit.jupiter.params.provider.Arguments.of("cylinder", CylinderShape.of(1.0f, 2.0f)),
                org.junit.jupiter.params.provider.Arguments.of("tetrahedron",
                        PolyhedronShape.of(PolyType.TETRAHEDRON, 1.0f)),
                org.junit.jupiter.params.provider.Arguments.of("icosahedron",
                        PolyhedronShape.of(PolyType.ICOSAHEDRON, 1.0f)));
    }

    @ParameterizedTest(name = "{0} tessellates to something non-empty")
    @MethodSource("shapes")
    void producesGeometry(String name, Shape shape) {
        Mesh mesh = Tessellator.tessellate(shape, 16);
        assertFalse(mesh.isEmpty(), name + " tessellated to nothing");
        assertTrue(mesh.vertexCount() > 0, name + " produced no vertices");
        assertTrue(mesh.indexCount() > 0, name + " produced no indices");
    }

    @ParameterizedTest(name = "{0} indices all address a vertex that exists")
    @MethodSource("shapes")
    void indicesAreInRange(String name, Shape shape) {
        Mesh mesh = Tessellator.tessellate(shape, 16);
        int vertices = mesh.vertexCount();
        for (int i = 0; i < mesh.indices().length; i++) {
            int index = mesh.indices()[i];
            assertTrue(index >= 0 && index < vertices,
                    name + " index[" + i + "] is " + index + ", outside [0, " + vertices + ")");
        }
    }

    @ParameterizedTest(name = "{0} index count divides into whole primitives")
    @MethodSource("shapes")
    void indexCountMatchesPrimitiveStride(String name, Shape shape) {
        Mesh mesh = Tessellator.tessellate(shape, 16);
        PrimitiveType type = mesh.primitiveType();
        assertEquals(0, mesh.indexCount() % type.verticesPerPrimitive(),
                name + " has " + mesh.indexCount() + " indices, which is not a whole number of "
                        + type + " (" + type.verticesPerPrimitive() + " each)");
    }

    @ParameterizedTest(name = "{0} has no NaN or infinite coordinate")
    @MethodSource("shapes")
    void coordinatesAreFinite(String name, Shape shape) {
        Mesh mesh = Tessellator.tessellate(shape, 16);
        List<Vertex> vertices = mesh.vertices();
        for (int i = 0; i < vertices.size(); i++) {
            Vertex v = vertices.get(i);
            assertTrue(finite(v.x()) && finite(v.y()) && finite(v.z()),
                    name + " vertex[" + i + "] is not finite: (" + v.x() + ", " + v.y() + ", " + v.z() + ")");
            assertTrue(finite(v.nx()) && finite(v.ny()) && finite(v.nz()),
                    name + " vertex[" + i + "] has a non-finite normal: ("
                            + v.nx() + ", " + v.ny() + ", " + v.nz() + ")");
        }
    }

    @ParameterizedTest(name = "{0} keeps its vertices inside the bounds it declares")
    @MethodSource("shapes")
    void verticesRespectDeclaredBounds(String name, Shape shape) {
        Mesh mesh = Tessellator.tessellate(shape, 16);
        org.joml.Vector3f bounds = shape.getBounds();
        // getBounds() is the full extent, so half of it in each direction from the
        // origin. A little slack absorbs float error in the trigonometry.
        float slack = 1e-3f;
        float halfX = bounds.x / 2 + slack;
        float halfY = bounds.y / 2 + slack;
        float halfZ = bounds.z / 2 + slack;
        for (Vertex v : mesh.vertices()) {
            assertTrue(Math.abs(v.x()) <= halfX,
                    name + " vertex x=" + v.x() + " exceeds declared half-extent " + halfX);
            assertTrue(Math.abs(v.z()) <= halfZ,
                    name + " vertex z=" + v.z() + " exceeds declared half-extent " + halfZ);
            assertTrue(Math.abs(v.y()) <= halfY,
                    name + " vertex y=" + v.y() + " exceeds declared half-extent " + halfY);
        }
    }

    @Test
    @DisplayName("a sphere's points sit on the sphere")
    void sphereIsSpherical() {
        // The strongest available check: every vertex of a unit sphere should be one
        // unit from the origin. Nothing else catches an axis swapped or a radius
        // applied twice, both of which still produce a plausible-looking blob.
        SphereShape shape = SphereShape.of(1.0f);
        Mesh mesh = Tessellator.tessellate(shape, 32);
        for (Vertex v : mesh.vertices()) {
            double r = Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
            assertTrue(Math.abs(r - 1.0) < 1e-3,
                    "vertex at radius " + r + " on a unit sphere");
        }
    }

    @Test
    @DisplayName("a polyhedron's triangles all have area")
    void polyhedronHasNoDegenerateTriangles() {
        // A zero-area triangle rasterises to nothing, so a mesh full of them looks
        // like a tessellator that ran and produced an invisible object — the most
        // confusing failure there is, because every count is right.
        Mesh mesh = Tessellator.tessellate(PolyhedronShape.of(PolyType.ICOSAHEDRON, 1.0f), 0);
        assertTrue(mesh.isTriangles(), "expected a triangle mesh, got " + mesh.primitiveType());
        int[] degenerate = {0};
        mesh.forEachTriangle((a, b, c) -> {
            if (area(a, b, c) < 1e-9) {
                degenerate[0]++;
            }
        });
        assertEquals(0, degenerate[0],
                degenerate[0] + " of " + mesh.primitiveCount() + " triangles have no area");
    }

    @Test
    @DisplayName("scaling a mesh scales its vertices and nothing else")
    void scalingIsPositionOnly() {
        Mesh mesh = Tessellator.tessellate(SphereShape.of(1.0f), 16);
        Mesh doubled = mesh.scaled(2.0f);
        assertEquals(mesh.vertexCount(), doubled.vertexCount(), "scaling changed the vertex count");
        assertArrayEqualsInt(mesh.indices(), doubled.indices());
        for (int i = 0; i < mesh.vertexCount(); i++) {
            assertEquals(mesh.vertex(i).x() * 2, doubled.vertex(i).x(), 1e-5f);
            assertEquals(mesh.vertex(i).y() * 2, doubled.vertex(i).y(), 1e-5f);
            assertEquals(mesh.vertex(i).z() * 2, doubled.vertex(i).z(), 1e-5f);
        }
    }

    @Test
    @DisplayName("an unrecognised shape yields an empty mesh rather than throwing")
    void unknownShapeIsEmpty() {
        // The dispatcher's default branch. A consumer feeding in a shape type core
        // does not know should get nothing drawn, not an exception mid-frame.
        Mesh mesh = Tessellator.tessellate(new UnknownShape(), 16);
        assertTrue(mesh.isEmpty(), "an unknown shape produced " + mesh.vertexCount() + " vertices");
    }

    /**
     * The {@code detail} argument reaches none of the tessellators.
     *
     * <p>This is not an assertion that the behaviour is right — it pins what the ported
     * code does, so that changing it is deliberate and has a failing test to point at.
     *
     * <p>{@link Tessellator#tessellate(Shape, int)} documents {@code detail} as
     * controlling mesh quality and offers a {@link Tessellator.DetailLevel} enum with six
     * settings from {@code MINIMAL} to {@code MAXIMUM}. Every branch of the dispatcher
     * discards it. Sphere, ring, prism, cylinder and molecule call tessellators that take
     * no detail argument at all and read a segment count off the shape record;
     * {@code PolyhedronTessellator.tessellate(int)} accepts the parameter and never
     * reads it, using the shape's {@code subdivisions} instead.
     *
     * <p>So the resolution the shapes carry is the real mechanism, and {@code detail} is
     * a leftover. The check below is what makes that statement testable rather than a
     * claim in a comment.
     */
    @Test
    @DisplayName("detail is ignored by every tessellator, including the polyhedron")
    void detailReachesNoTessellator() {
        for (int[] pair : new int[][] {{Tessellator.DetailLevel.MINIMAL.value,
                Tessellator.DetailLevel.MAXIMUM.value}}) {
            SphereShape sphere = SphereShape.of(1.0f);
            assertEquals(Tessellator.tessellate(sphere, pair[0]).vertexCount(),
                    Tessellator.tessellate(sphere, pair[1]).vertexCount(),
                    "the sphere tessellator started honouring detail — say so in its javadoc");

            PolyhedronShape poly = PolyhedronShape.of(PolyType.ICOSAHEDRON, 1.0f);
            assertEquals(Tessellator.tessellate(poly, pair[0]).vertexCount(),
                    Tessellator.tessellate(poly, pair[1]).vertexCount(),
                    "the polyhedron tessellator started honouring detail — say so in its javadoc");

            CylinderShape cylinder = CylinderShape.of(1.0f, 2.0f);
            assertEquals(Tessellator.tessellate(cylinder, pair[0]).vertexCount(),
                    Tessellator.tessellate(cylinder, pair[1]).vertexCount(),
                    "the cylinder tessellator started honouring detail — say so in its javadoc");
        }
    }

    /**
     * Resolution comes from the shape, which is what a caller should reach for.
     *
     * <p>The counterpart to the test above: {@code detail} does nothing, but changing the
     * segment count on the shape record does exactly what {@code detail} advertises.
     */
    @Test
    @DisplayName("a shape's own segment count is what changes the mesh")
    void resolutionComesFromTheShape() {
        CylinderShape coarse = CylinderShape.of(1.0f, 2.0f);
        CylinderShape fine = new CylinderShape(coarse.radius(), coarse.height(),
                coarse.segments() * 4, coarse.topRadius(), coarse.heightSegments(),
                coarse.capTop(), coarse.capBottom(), coarse.arc());
        assertTrue(Tessellator.tessellate(fine, 0).vertexCount()
                        > Tessellator.tessellate(coarse, 0).vertexCount(),
                "quadrupling the segment count did not produce more vertices");
    }

    private static boolean finite(float f) {
        return !Float.isNaN(f) && !Float.isInfinite(f);
    }

    private static double area(Vertex a, Vertex b, Vertex c) {
        double ux = b.x() - a.x();
        double uy = b.y() - a.y();
        double uz = b.z() - a.z();
        double vx = c.x() - a.x();
        double vy = c.y() - a.y();
        double vz = c.z() - a.z();
        double cx = uy * vz - uz * vy;
        double cy = uz * vx - ux * vz;
        double cz = ux * vy - uy * vx;
        return 0.5 * Math.sqrt(cx * cx + cy * cy + cz * cz);
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual) {
        assertEquals(expected.length, actual.length, "index count changed");
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "index[" + i + "] changed");
        }
    }

    /** A shape type the dispatcher has never heard of. */
    private record UnknownShape() implements Shape {
        @Override
        public String getType() {
            return "unknown";
        }

        @Override
        public org.joml.Vector3f getBounds() {
            return new org.joml.Vector3f(1, 1, 1);
        }

        @Override
        public net.cyberpunk042.mcshaders.core.pattern.CellType primaryCellType() {
            return net.cyberpunk042.mcshaders.core.pattern.CellType.TRIANGLE;
        }

        @Override
        public java.util.Map<String, net.cyberpunk042.mcshaders.core.pattern.CellType> getParts() {
            return java.util.Map.of();
        }
    }
}
