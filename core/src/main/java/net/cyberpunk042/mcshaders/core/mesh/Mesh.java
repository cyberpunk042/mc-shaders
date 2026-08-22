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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable mesh data containing vertices and indices.
 * 
 * <h2>Structure</h2>
 * <ul>
 *   <li><b>Vertices</b>: List of {@link Vertex} objects (position, normal, UV)</li>
 *   <li><b>Indices</b>: Array of integers pointing to vertices</li>
 *   <li><b>Primitive Type</b>: How indices are interpreted (triangles, lines, etc.)</li>
 * </ul>
 * 
 * <h2>Creating Meshes</h2>
 * <p>Use {@link MeshBuilder} to create meshes:
 * <pre>
 * Mesh sphere = MeshBuilder.triangles()
 *     .addVertex(Vertex.spherical(0, 0))
 *     .addVertex(Vertex.spherical(theta, phi))
 *     // ... more vertices
 *     .triangle(0, 1, 2)
 *     .build();
 * </pre>
 * 
 * <p>Or use a {@link net.cyberpunk042.mcshaders.core.mesh.Tessellator}:
 * <pre>
 * Mesh sphere = Tessellator.tessellate(sphereShape, 32);
 * </pre>
 * 
 * <h2>Rendering</h2>
 * <p>A mesh is plain data — positions, normals, texture coordinates and
 * indices. Handing it to a graphics API is the consumer's job, and what that
 * looks like depends entirely on the API; core deliberately does not reach
 * for one.
 * 
 * <h2>Immutability</h2>
 * <p>Meshes are immutable. Transform methods return NEW meshes:
 * <pre>
 * Mesh bigger = mesh.scaled(2.0f);  // New mesh, original unchanged
 * </pre>
 * 
 * @see MeshBuilder
 * @see Vertex
 * @see PrimitiveType
 */
public final class Mesh {
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Fields - All immutable
    // ─────────────────────────────────────────────────────────────────────────────
    
    private final List<Vertex> vertices;      // Immutable list (List.copyOf)
    private final int[] indices;              // Cloned on construction
    private final PrimitiveType primitiveType;
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Constructor - Package-private, use MeshBuilder
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Creates a mesh. Use {@link MeshBuilder} instead of calling directly.
     */
    Mesh(List<Vertex> vertices, int[] indices, PrimitiveType primitiveType) {
        // Defensive copies for immutability
        this.vertices = List.copyOf(vertices);
        this.indices = indices.clone();
        this.primitiveType = primitiveType;
    }
    
    /**
     * Creates an empty mesh (no vertices, no indices).
     * <p>Useful as a fallback or placeholder.
     */
    public static Mesh empty() {
        return new Mesh(Collections.emptyList(), new int[0], PrimitiveType.TRIANGLES);
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Accessors
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Returns the immutable list of vertices.
     * <p>Safe to iterate - cannot be modified.
     */
    public List<Vertex> vertices() {
        return vertices;
    }
    
    /**
     * Returns a COPY of the index array.
     * <p>Caller owns the returned array and can modify it.
     */
    public int[] indices() {
        return indices.clone();  // Defensive copy
    }
    
    /**
     * Returns the primitive type (how indices are interpreted).
     */
    public PrimitiveType primitiveType() {
        return primitiveType;
    }
    
    /**
     * Returns the number of vertices in this mesh.
     */
    public int vertexCount() {
        return vertices.size();
    }
    
    /**
     * Returns the number of indices in this mesh.
     */
    public int indexCount() {
        return indices.length;
    }
    
    /**
     * Returns the vertex at the given index.
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public Vertex vertex(int index) {
        return vertices.get(index);
    }
    
    /**
     * Returns the number of primitives (triangles, quads, or lines).
     * <p>Calculated from index count and primitive type.
     */
    public int primitiveCount() {
        if (!primitiveType.isIndexed()) {
            return vertices.size();  // Non-indexed: one primitive per vertex
        }
        return indices.length / primitiveType.verticesPerPrimitive();
    }
    
    /**
     * Returns true if this mesh has no vertices.
     */
    public boolean isEmpty() {
        return vertices.isEmpty();
    }
    
    /**
     * Returns true if this mesh uses LINES primitive type.
     */
    public boolean isLines() {
        return primitiveType == PrimitiveType.LINES;
    }
    
    /**
     * Returns true if this mesh uses TRIANGLES primitive type.
     */
    public boolean isTriangles() {
        return primitiveType == PrimitiveType.TRIANGLES;
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Iteration - Functional interface for each primitive type
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Iterates over each triangle in this mesh.
     * <p>Only valid for {@link PrimitiveType#TRIANGLES} meshes.
     * 
     * <p>Usage:
     * <pre>
     * mesh.forEachTriangle((v0, v1, v2) -> {
     *     // Process triangle vertices
     * });
     * </pre>
     * 
     * @param consumer receives (v0, v1, v2) for each triangle
     * @throws IllegalStateException if mesh is not TRIANGLES type
     */
    public void forEachTriangle(TriangleConsumer consumer) {
        if (primitiveType != PrimitiveType.TRIANGLES) {
            throw new IllegalStateException("Mesh is not TRIANGLES type, is: " + primitiveType);
        }
        // Process indices in groups of 3
        for (int i = 0; i < indices.length; i += 3) {
            Vertex v0 = vertices.get(indices[i]);
            Vertex v1 = vertices.get(indices[i + 1]);
            Vertex v2 = vertices.get(indices[i + 2]);
            consumer.accept(v0, v1, v2);
        }
    }
    
    /**
     * Iterates over each quad in this mesh.
     * <p>Only valid for {@link PrimitiveType#QUADS} meshes.
     * 
     * @param consumer receives (v0, v1, v2, v3) for each quad
     * @throws IllegalStateException if mesh is not QUADS type
     */
    public void forEachQuad(QuadConsumer consumer) {
        if (primitiveType != PrimitiveType.QUADS) {
            throw new IllegalStateException("Mesh is not QUADS type, is: " + primitiveType);
        }
        // Process indices in groups of 4
        for (int i = 0; i < indices.length; i += 4) {
            Vertex v0 = vertices.get(indices[i]);
            Vertex v1 = vertices.get(indices[i + 1]);
            Vertex v2 = vertices.get(indices[i + 2]);
            Vertex v3 = vertices.get(indices[i + 3]);
            consumer.accept(v0, v1, v2, v3);
        }
    }
    
    /**
     * Iterates over each line in this mesh.
     * <p>Only valid for {@link PrimitiveType#LINES} meshes.
     * 
     * @param consumer receives (v0, v1) for each line segment
     * @throws IllegalStateException if mesh is not LINES type
     */
    public void forEachLine(LineConsumer consumer) {
        if (primitiveType != PrimitiveType.LINES) {
            throw new IllegalStateException("Mesh is not LINES type, is: " + primitiveType);
        }
        // Process indices in groups of 2
        for (int i = 0; i < indices.length; i += 2) {
            Vertex v0 = vertices.get(indices[i]);
            Vertex v1 = vertices.get(indices[i + 1]);
            consumer.accept(v0, v1);
        }
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Transforms - Return NEW meshes (immutable)
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Returns a NEW mesh with all vertex positions scaled.
     * <p>Original mesh is unchanged.
     * 
     * @param scale multiplier for all positions
     * @return new scaled mesh
     */
    public Mesh scaled(float scale) {
        List<Vertex> scaledVerts = new ArrayList<>(vertices.size());
        for (Vertex v : vertices) {
            scaledVerts.add(v.scaled(scale));
        }
        // Indices unchanged - still point to same relative positions
        return new Mesh(scaledVerts, indices, primitiveType);
    }
    
    /**
     * Returns a NEW mesh with all vertex positions translated.
     * <p>Original mesh is unchanged.
     * 
     * @param dx X offset
     * @param dy Y offset  
     * @param dz Z offset
     * @return new translated mesh
     */
    public Mesh translated(float dx, float dy, float dz) {
        List<Vertex> translatedVerts = new ArrayList<>(vertices.size());
        for (Vertex v : vertices) {
            translatedVerts.add(v.translated(dx, dy, dz));
        }
        return new Mesh(translatedVerts, indices, primitiveType);
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Functional Interfaces for Iteration
    // ─────────────────────────────────────────────────────────────────────────────
    
    /** Consumer for triangle iteration. */
    @FunctionalInterface
    public interface TriangleConsumer {
        void accept(Vertex v0, Vertex v1, Vertex v2);
    }
    
    /** Consumer for quad iteration. */
    @FunctionalInterface
    public interface QuadConsumer {
        void accept(Vertex v0, Vertex v1, Vertex v2, Vertex v3);
    }
    
    /** Consumer for line iteration. */
    @FunctionalInterface
    public interface LineConsumer {
        void accept(Vertex v0, Vertex v1);
    }
}
