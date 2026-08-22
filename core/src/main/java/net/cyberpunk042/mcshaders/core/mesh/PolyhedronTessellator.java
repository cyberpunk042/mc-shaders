/*
 * Ported from the-virus-block-mc (net.cyberpunk042.client.visual.mesh), where it
 * is tessellation code with no Minecraft or graphics-API dependency: the whole
 * subtree imported nothing from net.minecraft or com.mojang.
 * Relicensed to MIT here by the author, per the engine/content split recorded in
 * docs/PORTING.md.
 *
 * The mod's logging system was replaced with core's Diag, which routes to
 * java.lang.System.Logger. The call sites are unchanged apart from the name.
 */
package net.cyberpunk042.mcshaders.core.mesh;

import net.cyberpunk042.mcshaders.core.animation.WaveDeformer;
import net.cyberpunk042.mcshaders.core.diag.Diag;
import net.cyberpunk042.mcshaders.core.animation.WaveConfig;
import net.cyberpunk042.mcshaders.core.pattern.VertexPattern;
import net.cyberpunk042.mcshaders.core.pattern.TrianglePattern;
import net.cyberpunk042.mcshaders.core.shape.PolyhedronShape;
import net.cyberpunk042.mcshaders.core.shape.PolyType;

/**
 * Tessellates Platonic solid shapes into triangle meshes.
 * 
 * <p>Uses data-driven tessellation - geometry data is stored in {@link PolyType}
 * enum, making this class focused purely on mesh construction.</p>
 * 
 * <h2>Supported Shapes</h2>
 * <ul>
 *   <li>Tetrahedron (4 triangular faces)</li>
 *   <li>Cube (6 square faces)</li>
 *   <li>Octahedron (8 triangular faces)</li>
 *   <li>Icosahedron (20 triangular faces)</li>
 *   <li>Dodecahedron (12 pentagonal faces)</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * Mesh mesh = PolyhedronTessellator.fromShape(polyhedronShape).tessellate(0);
 * </pre>
 * 
 * @see PolyhedronShape
 * @see PolyType
 */
public final class PolyhedronTessellator implements Tessellator {
    
    // =========================================================================
    // Fields
    // =========================================================================
    
    private final PolyType polyType;
    private final float radius;
    private final int subdivisions;
    private final VertexPattern pattern;
    
    // Wave deformation state (set per-tessellate call)
    private WaveConfig wave;
    private float waveTime;
    
    // =========================================================================
    // Constructor & Factory
    // =========================================================================
    
    private PolyhedronTessellator(Builder builder) {
        this.polyType = builder.polyType;
        this.radius = builder.radius;
        this.subdivisions = builder.subdivisions;
        this.pattern = builder.pattern != null ? builder.pattern : TrianglePattern.DEFAULT;
    }
    
    /** Creates a builder for configuring the tessellator. */
    public static Builder builder() {
        return new Builder();
    }
    
    /** Creates a tessellator directly from a shape definition. */
    public static PolyhedronTessellator fromShape(PolyhedronShape shape) {
        return builder()
            .polyType(shape.polyType())
            .radius(shape.radius())
            .subdivisions(shape.subdivisions())
            .build();
    }
    
    // =========================================================================
    // Tessellator Interface
    // =========================================================================
    
    @Override
    public Mesh tessellate(int detail) {
        Diag.RENDER.topic("tessellate")
            .kv("type", polyType)
            .kv("radius", radius)
            .kv("subdivisions", subdivisions)
            .kv("wave", wave != null && wave.isActive())
            .debug("Tessellating polyhedron");
        
        // Data-driven: get geometry from PolyType enum
        float[][] vertices = polyType.vertices(radius);
        int[][] faces = polyType.faces();
        float[][] normals = polyType.faceNormals();
        PolyType.FaceType faceType = polyType.faceType();
        
        // Build mesh with appropriate primitive type
        MeshBuilder builder = faceType == PolyType.FaceType.QUAD
            ? MeshBuilder.quads()
            : MeshBuilder.triangles();
        
        // Emit each face
        int totalFaces = faces.length;
        for (int i = 0; i < totalFaces; i++) {
            // Pattern filtering (skip if subdividing - we want solid base)
            if (subdivisions == 0 && !pattern.shouldRender(i, totalFaces)) {
                continue;
            }
            
            // Dispatch based on face type
            switch (faceType) {
                case TRIANGLE -> emitTriangleFace(builder, vertices, faces[i], 
                    normals != null ? normals[i] : null);
                case QUAD -> emitQuadFace(builder, vertices, faces[i], 
                    normals != null ? normals[i] : null);
                case PENTAGON -> emitPentagonFace(builder, vertices, faces[i]);
            }
        }
        
        Mesh baseMesh = builder.build();
        
        // Apply geodesic subdivision if requested
        if (subdivisions > 0) {
            return GeodesicHelper.subdivide(baseMesh, subdivisions, radius, wave, waveTime);
        }
        
        return baseMesh;
    }
    
    /** Tessellates with wave deformation support. */
    public Mesh tessellate(int detail, WaveConfig wave, float time) {
        this.wave = wave;
        this.waveTime = time;
        Mesh result = tessellate(detail);
        this.wave = null;
        this.waveTime = 0;
        return result;
    }
    
    @Override
    public int defaultDetail() { return 0; }
    
    @Override
    public int minDetail() { return 0; }
    
    @Override
    public int maxDetail() { return 8; }
    
    // =========================================================================
    // Face Emission (unified for all shapes)
    // =========================================================================
    
    private void emitTriangleFace(MeshBuilder builder, float[][] vertices, 
                                   int[] face, float[] precomputedNormal) {
        float[] v0 = vertices[face[0]];
        float[] v1 = vertices[face[1]];
        float[] v2 = vertices[face[2]];
        
        // Compute normal if not provided
        float[] normal = precomputedNormal != null 
            ? precomputedNormal 
            : computeNormal(v0, v1, v2);
        
        // Front face
        int i0 = emitVertex(builder, v0, normal, 0, 0);
        int i1 = emitVertex(builder, v1, normal, 1, 0);
        int i2 = emitVertex(builder, v2, normal, 1, 1);
        builder.emitCellFromPattern(new int[]{i0, i1, i2}, pattern);
        
        // Back face (double-sided) - ONLY if not subdividing
        // When subdividing, back faces are added after subdivision completes
        if (subdivisions == 0) {
            float[] invN = {-normal[0], -normal[1], -normal[2]};
            int j0 = emitVertex(builder, v0, invN, 0, 0);
            int j1 = emitVertex(builder, v2, invN, 1, 1);
            int j2 = emitVertex(builder, v1, invN, 1, 0);
            builder.emitCellFromPattern(new int[]{j0, j1, j2}, pattern);
        }
    }
    
    private void emitQuadFace(MeshBuilder builder, float[][] vertices,
                               int[] face, float[] normal) {
        float[] v0 = vertices[face[0]];
        float[] v1 = vertices[face[1]];
        float[] v2 = vertices[face[2]];
        float[] v3 = vertices[face[3]];
        
        // Front face
        int i0 = emitVertex(builder, v0, normal, 0, 0);
        int i1 = emitVertex(builder, v1, normal, 1, 0);
        int i2 = emitVertex(builder, v2, normal, 1, 1);
        int i3 = emitVertex(builder, v3, normal, 0, 1);
        builder.quad(i0, i1, i2, i3);
        
        // Back face (double-sided) - ONLY if not subdividing
        if (subdivisions == 0) {
            float[] invN = {-normal[0], -normal[1], -normal[2]};
            int j0 = emitVertex(builder, v0, invN, 0, 0);
            int j1 = emitVertex(builder, v3, invN, 0, 1);
            int j2 = emitVertex(builder, v2, invN, 1, 1);
            int j3 = emitVertex(builder, v1, invN, 1, 0);
            builder.quad(j0, j1, j2, j3);
        }
    }
    
    private void emitPentagonFace(MeshBuilder builder, float[][] vertices, int[] face) {
        // Calculate center
        float cx = 0, cy = 0, cz = 0;
        for (int idx : face) {
            cx += vertices[idx][0];
            cy += vertices[idx][1];
            cz += vertices[idx][2];
        }
        cx /= 5; cy /= 5; cz /= 5;
        float[] center = {cx, cy, cz};
        
        // Compute normal from first 3 vertices
        float[] normal = computeNormal(vertices[face[0]], vertices[face[1]], vertices[face[2]]);
        
        // Emit 5 triangles from center to each edge
        for (int i = 0; i < 5; i++) {
            float[] v0 = vertices[face[i]];
            float[] v1 = vertices[face[(i + 1) % 5]];
            
            // Front
            int ci = emitVertex(builder, center, normal, 0.5f, 0.5f);
            int vi0 = emitVertex(builder, v0, normal, 0, 0);
            int vi1 = emitVertex(builder, v1, normal, 1, 0);
            builder.emitCellFromPattern(new int[]{ci, vi0, vi1}, pattern);
            
            // Back - ONLY if not subdividing
            if (subdivisions == 0) {
                float[] invN = {-normal[0], -normal[1], -normal[2]};
                int cj = emitVertex(builder, center, invN, 0.5f, 0.5f);
                int vj0 = emitVertex(builder, v1, invN, 1, 0);
                int vj1 = emitVertex(builder, v0, invN, 0, 0);
                builder.emitCellFromPattern(new int[]{cj, vj0, vj1}, pattern);
            }
        }
    }
    
    // =========================================================================
    // Vertex Emission Helpers
    // =========================================================================
    
    private int emitVertex(MeshBuilder builder, float[] pos, float[] normal, float u, float v) {
        Vertex vertex = new Vertex(pos[0], pos[1], pos[2], normal[0], normal[1], normal[2], u, v, 1.0f);
        if (wave != null && wave.isActive() && wave.isCpuMode()) {
            vertex = WaveDeformer.applyToVertex(vertex, wave, waveTime);
        }
        return builder.vertex(vertex.x(), vertex.y(), vertex.z(),
                              vertex.nx(), vertex.ny(), vertex.nz(),
                              vertex.u(), vertex.v());
    }
    
    private float[] computeNormal(float[] v0, float[] v1, float[] v2) {
        float[] edge1 = GeometryMath.subtract(v1, v0);
        float[] edge2 = GeometryMath.subtract(v2, v0);
        float[] normal = GeometryMath.crossProduct(edge1, edge2);
        GeometryMath.normalizeInPlace(normal);
        return normal;
    }
    
    // =========================================================================
    // Geodesic Subdivision (inner helper class)
    // =========================================================================
    
    /**
     * Geodesic subdivision for creating smoother sphere-like shapes.
     * Splits each triangle into 4 and projects new vertices onto sphere.
     */
    private static final class GeodesicHelper {
        
        static Mesh subdivide(Mesh mesh, int levels, float radius, 
                              WaveConfig wave, float time) {
            if (levels <= 0) return mesh;
            
            Diag.RENDER.topic("tessellate")
                .kv("levels", levels)
                .kv("initialFaces", mesh.primitiveCount())
                .debug("Starting geodesic subdivision");
            
            Mesh result = mesh;
            for (int level = 0; level < levels; level++) {
                result = subdivideOnce(result, radius, wave, time);
            }
            
            // Add back faces for double-sided rendering
            // The subdivision only processes front faces; now we duplicate with reversed winding
            result = addBackFaces(result, wave, time);
            
            Diag.RENDER.topic("tessellate")
                .kv("finalFaces", result.primitiveCount())
                .debug("Subdivision complete (with back faces)");
            
            return result;
        }
        
        /**
         * Duplicates all triangles with reversed winding for double-sided rendering.
         */
        private static Mesh addBackFaces(Mesh mesh, WaveConfig wave, float time) {
            MeshBuilder builder = MeshBuilder.triangles();
            
            // Copy front faces
            mesh.forEachTriangle((v0, v1, v2) -> {
                int i0 = builder.vertex(v0.x(), v0.y(), v0.z(), v0.nx(), v0.ny(), v0.nz(), v0.u(), v0.v());
                int i1 = builder.vertex(v1.x(), v1.y(), v1.z(), v1.nx(), v1.ny(), v1.nz(), v1.u(), v1.v());
                int i2 = builder.vertex(v2.x(), v2.y(), v2.z(), v2.nx(), v2.ny(), v2.nz(), v2.u(), v2.v());
                builder.triangle(i0, i1, i2);
            });
            
            // Add back faces (reversed winding, inverted normals)
            mesh.forEachTriangle((v0, v1, v2) -> {
                // Reversed vertex order for back face: v0, v2, v1
                // Inverted normals
                int j0 = builder.vertex(v0.x(), v0.y(), v0.z(), -v0.nx(), -v0.ny(), -v0.nz(), v0.u(), v0.v());
                int j1 = builder.vertex(v2.x(), v2.y(), v2.z(), -v2.nx(), -v2.ny(), -v2.nz(), v2.u(), v2.v());
                int j2 = builder.vertex(v1.x(), v1.y(), v1.z(), -v1.nx(), -v1.ny(), -v1.nz(), v1.u(), v1.v());
                builder.triangle(j0, j1, j2);
            });
            
            return builder.build();
        }
        
        private static Mesh subdivideOnce(Mesh mesh, float radius,
                                          WaveConfig wave, float time) {
            MeshBuilder builder = MeshBuilder.triangles();
            boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
            
            // Handle QUADS by treating as 2 triangles
            if (mesh.primitiveType() == PrimitiveType.QUADS) {
                mesh.forEachQuad((v0, v1, v2, v3) -> {
                    subdivideTriangle(builder, v0, v1, v2, radius, wave, time, applyWave);
                    subdivideTriangle(builder, v0, v2, v3, radius, wave, time, applyWave);
                });
            } else {
                mesh.forEachTriangle((v0, v1, v2) -> 
                    subdivideTriangle(builder, v0, v1, v2, radius, wave, time, applyWave));
            }
            
            return builder.build();
        }
        
        private static void subdivideTriangle(MeshBuilder builder, 
                                              Vertex v0, Vertex v1, Vertex v2,
                                              float radius, WaveConfig wave, 
                                              float time, boolean applyWave) {
            // Edge midpoints
            Vertex m01 = projectToSphere(midpoint(v0, v1), radius);
            Vertex m12 = projectToSphere(midpoint(v1, v2), radius);
            Vertex m20 = projectToSphere(midpoint(v2, v0), radius);
            
            // Emit 4 new triangles
            emitTriangle(builder, v0, m01, m20, wave, time, applyWave);
            emitTriangle(builder, m01, v1, m12, wave, time, applyWave);
            emitTriangle(builder, m01, m12, m20, wave, time, applyWave);
            emitTriangle(builder, m20, m12, v2, wave, time, applyWave);
        }
        
        private static void emitTriangle(MeshBuilder builder, 
                                         Vertex v0, Vertex v1, Vertex v2,
                                         WaveConfig wave, float time, boolean applyWave) {
            if (applyWave) {
                v0 = WaveDeformer.applyToVertex(v0, wave, time);
                v1 = WaveDeformer.applyToVertex(v1, wave, time);
                v2 = WaveDeformer.applyToVertex(v2, wave, time);
            }
            int i0 = builder.vertex(v0.x(), v0.y(), v0.z(), v0.nx(), v0.ny(), v0.nz(), v0.u(), v0.v());
            int i1 = builder.vertex(v1.x(), v1.y(), v1.z(), v1.nx(), v1.ny(), v1.nz(), v1.u(), v1.v());
            int i2 = builder.vertex(v2.x(), v2.y(), v2.z(), v2.nx(), v2.ny(), v2.nz(), v2.u(), v2.v());
            builder.triangle(i0, i1, i2);
        }
        
        private static Vertex midpoint(Vertex a, Vertex b) {
            return new Vertex(
                (a.x() + b.x()) / 2, (a.y() + b.y()) / 2, (a.z() + b.z()) / 2,
                0, 0, 0,  // Normal recalculated when projected
                (a.u() + b.u()) / 2, (a.v() + b.v()) / 2,
                1.0f
            );
        }
        
        private static Vertex projectToSphere(Vertex v, float radius) {
            float length = (float) Math.sqrt(v.x() * v.x() + v.y() * v.y() + v.z() * v.z());
            if (length < 0.0001f) return v;
            
            float scale = radius / length;
            float px = v.x() * scale;
            float py = v.y() * scale;
            float pz = v.z() * scale;
            
            // Normal = direction from origin
            float nx = v.x() / length;
            float ny = v.y() / length;
            float nz = v.z() / length;
            
            return new Vertex(px, py, pz, nx, ny, nz, v.u(), v.v(), 1.0f);
        }
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static final class Builder {
        private PolyType polyType = PolyType.ICOSAHEDRON;
        private float radius = 1.0f;
        private int subdivisions = 0;
        private VertexPattern pattern = null;
        
        /** Sets the polyhedron type. */
        public Builder polyType(PolyType type) {
            this.polyType = type;
            return this;
        }
        
        /** Sets the circumscribed radius. */
        public Builder radius(float radius) {
            this.radius = radius;
            return this;
        }
        
        /** Sets subdivision level for geodesic effect. */
        public Builder subdivisions(int subdivisions) {
            this.subdivisions = subdivisions;
            return this;
        }
        
        /** Sets the vertex pattern for face rendering. */
        public Builder pattern(VertexPattern pattern) {
            this.pattern = pattern;
            return this;
        }
        
        public PolyhedronTessellator build() {
            return new PolyhedronTessellator(this);
        }
    }
}
