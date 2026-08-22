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
import java.util.List;

/**
 * Builder for creating {@link Mesh} instances.
 * 
 * <h2>Basic Usage</h2>
 * <pre>
 * // Create a simple triangle
 * Mesh triangle = MeshBuilder.triangles()
 *     .vertex(0, 0, 0)    // Returns index 0
 *     .vertex(1, 0, 0)    // Returns index 1
 *     .vertex(0, 1, 0)    // Returns index 2
 *     .triangle(0, 1, 2)  // Connect vertices
 *     .build();
 * </pre>
 * 
 * <h2>Sphere Tessellation</h2>
 * <pre>
 * // Create a sphere using spherical coordinates
 * MeshBuilder builder = MeshBuilder.triangles();
 * int segments = 16;
 * 
 * // Add vertices for each latitude/longitude
 * for (int lat = 0; lat &lt;= segments; lat++) {
 *     float theta = lat * PI / segments;
 *     for (int lon = 0; lon &lt; segments; lon++) {
 *         float phi = lon * 2 * PI / segments;
 *         builder.sphericalVertex(theta, phi, radius);
 *     }
 * }
 * 
 * // Add triangles connecting vertices...
 * Mesh sphere = builder.build();
 * </pre>
 * 
 * <h2>Wireframe</h2>
 * <pre>
 * // Create wireframe using LINES
 * Mesh wireframe = MeshBuilder.lines()
 *     .vertex(0, 0, 0)
 *     .vertex(1, 0, 0)
 *     .vertex(1, 1, 0)
 *     .line(0, 1)   // First edge
 *     .line(1, 2)   // Second edge
 *     .build();
 * </pre>
 * 
 * <h2>Reusing Builder</h2>
 * <p>Call {@link #clear()} to reuse a builder for multiple meshes.
 * 
 * @see Mesh
 * @see Vertex
 * @see PrimitiveType
 */
public final class MeshBuilder {
    
    // ─────────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────────
    
    private final List<Vertex> vertices = new ArrayList<>();
    private final List<Integer> indices = new ArrayList<>();
    private final PrimitiveType primitiveType;  // Set at construction, immutable
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Constructors / Factory Methods
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Creates a new mesh builder for the given primitive type.
     * <p>Prefer static factory methods: {@link #triangles()}, {@link #lines()}, etc.
     */
    public MeshBuilder(PrimitiveType primitiveType) {
        this.primitiveType = primitiveType;
    }
    
    /**
     * Creates a builder for triangle meshes.
     * <p>Most common - used for solid/translucent geometry.
     */
    public static MeshBuilder triangles() {
        return new MeshBuilder(PrimitiveType.TRIANGLES);
    }
    
    /**
     * Creates a builder for quad meshes.
     * <p>Used for billboard/sprite geometry.
     */
    public static MeshBuilder quads() {
        return new MeshBuilder(PrimitiveType.QUADS);
    }
    
    /**
     * Creates a builder for line meshes.
     * <p>Used for wireframe rendering.
     */
    public static MeshBuilder lines() {
        return new MeshBuilder(PrimitiveType.LINES);
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Adding Vertices
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Adds a vertex and returns its index.
     * <p>Store the returned index to reference this vertex when adding primitives.
     * 
     * @param vertex the vertex to add
     * @return index of the added vertex (for use in triangle/line/quad calls)
     */
    public int addVertex(Vertex vertex) {
        int index = vertices.size();
        vertices.add(vertex);
        return index;
    }
    
    /**
     * Adds a position-only vertex.
     * <p>Normal and UV are zeroed. Use for wireframe.
     * 
     * @return index of the added vertex
     */
    public int vertex(float x, float y, float z) {
        return addVertex(Vertex.pos(x, y, z));
    }
    
    /**
     * Adds a vertex with position and normal.
     * <p>UV is zeroed. Use for lit geometry without textures.
     * 
     * @return index of the added vertex
     */
    public int vertex(float x, float y, float z, float nx, float ny, float nz) {
        return addVertex(Vertex.posNormal(x, y, z, nx, ny, nz));
    }
    
    /**
     * Adds a complete vertex with position, normal, and UV.
     * <p>Alpha defaults to 1.0 (fully opaque).
     * 
     * @return index of the added vertex
     */
    public int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v) {
        return addVertex(new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f));
    }
    
    /**
     * Adds a complete vertex with position, normal, UV, and alpha.
     * 
     * @param x X position
     * @param y Y position
     * @param z Z position
     * @param nx Normal X
     * @param ny Normal Y
     * @param nz Normal Z
     * @param u Texture U
     * @param v Texture V
     * @param alpha Vertex alpha (0=invisible, 1=opaque)
     * @return index of the added vertex
     */
    public int vertex(float x, float y, float z, float nx, float ny, float nz, float u, float v, float alpha) {
        return addVertex(new Vertex(x, y, z, nx, ny, nz, u, v, alpha));
    }
    
    /**
     * Adds a vertex on a unit sphere at the given spherical coordinates.
     * <p>See {@link Vertex#spherical(float, float)} for coordinate system.
     * 
     * @param theta polar angle (0 = top, PI = bottom)
     * @param phi azimuthal angle (0 to 2*PI)
     * @return index of the added vertex
     */
    public int sphericalVertex(float theta, float phi) {
        return addVertex(Vertex.spherical(theta, phi));
    }
    
    /**
     * Adds a vertex on a sphere at the given spherical coordinates with radius.
     * 
     * @param theta polar angle (0 = top, PI = bottom)
     * @param phi azimuthal angle (0 to 2*PI)
     * @param radius sphere radius
     * @return index of the added vertex
     */
    public int sphericalVertex(float theta, float phi, float radius) {
        return addVertex(Vertex.spherical(theta, phi, radius));
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Adding Primitives (indices)
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Adds a triangle connecting three vertices by index.
     * <p>Only valid for {@link PrimitiveType#TRIANGLES} builders.
     * 
     * <p>Winding order matters for face culling:
     * Counter-clockwise = front face (usually visible).
     * 
     * @param i0 first vertex index
     * @param i1 second vertex index
     * @param i2 third vertex index
     * @return this builder for chaining
     * @throws IllegalStateException if not a TRIANGLES builder
     */
    public MeshBuilder triangle(int i0, int i1, int i2) {
        if (primitiveType != PrimitiveType.TRIANGLES) {
            throw new IllegalStateException("Cannot add triangle to " + primitiveType + " mesh");
        }
        indices.add(i0);
        indices.add(i1);
        indices.add(i2);
        return this;
    }
    
    /**
     * Emits triangles for a cell of ANY type using the pattern's vertex ordering.
     * 
     * <p>This is the <b>universal</b> method for pattern-based rendering. It works with:
     * <ul>
     *   <li>QUAD cells (4 vertices) -> pattern returns 2 triangles</li>
     *   <li>SECTOR cells (3 vertices: center, edge0, edge1) -> pattern returns 1 triangle</li>
     *   <li>TRIANGLE cells (3 vertices: A, B, C) -> pattern returns 1 triangle</li>
     *   <li>SEGMENT cells (4 vertices: inner0, inner1, outer0, outer1) -> pattern returns 2 triangles</li>
     * </ul>
     * 
     * <p>Usage example:
     * <pre>
     * // For a sector (disc slice)
     * int[] cellVertices = {centerIdx, edge0Idx, edge1Idx};
     * builder.emitCellFromPattern(cellVertices, sectorPattern);
     * 
     * // For a quad
     * int[] cellVertices = {topLeft, topRight, bottomLeft, bottomRight};
     * builder.emitCellFromPattern(cellVertices, quadPattern);
     * </pre>
     * 
     * @param cellVertices Array of vertex indices for this cell (3 for triangles/sectors, 4 for quads/segments)
     * @param pattern The pattern that defines how to triangulate this cell
     * @return this builder for chaining
     */
    public MeshBuilder emitCellFromPattern(int[] cellVertices, net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern) {
        if (cellVertices == null || cellVertices.length < 3) {
            return this;
        }
        
        // Get vertex ordering from pattern (or use default if null)
        int[][] vertexOrder;
        if (pattern == null) {
            // Default: single triangle using first 3 vertices
            vertexOrder = new int[][] {{ 0, 1, 2 }};
        } else {
            vertexOrder = pattern.getVertexOrder();
            if (vertexOrder == null || vertexOrder.length == 0) {
                vertexOrder = new int[][] {{ 0, 1, 2 }};
            }
        }
        
        // Emit each triangle specified by the pattern
        for (int[] tri : vertexOrder) {
            if (tri.length >= 3) {
                // Map pattern indices to actual vertex indices
                int i0 = tri[0] < cellVertices.length ? cellVertices[tri[0]] : cellVertices[0];
                int i1 = tri[1] < cellVertices.length ? cellVertices[tri[1]] : cellVertices[1];
                int i2 = tri[2] < cellVertices.length ? cellVertices[tri[2]] : cellVertices[2];
                triangle(i0, i1, i2);
            }
        }
        return this;
    }
    
    /**
     * Adds a quad connecting four vertices by index.
     * <p>Only valid for {@link PrimitiveType#QUADS} builders.
     * 
     * @param i0 first vertex index
     * @param i1 second vertex index
     * @param i2 third vertex index
     * @param i3 fourth vertex index
     * @return this builder for chaining
     * @throws IllegalStateException if not a QUADS builder
     */
    public MeshBuilder quad(int i0, int i1, int i2, int i3) {
        if (primitiveType != PrimitiveType.QUADS) {
            throw new IllegalStateException("Cannot add quad to " + primitiveType + " mesh");
        }
        indices.add(i0);
        indices.add(i1);
        indices.add(i2);
        indices.add(i3);
        return this;
    }
    
    /**
     * Adds a line segment connecting two vertices by index.
     * <p>Only valid for {@link PrimitiveType#LINES} builders.
     * 
     * @param i0 first vertex index (line start)
     * @param i1 second vertex index (line end)
     * @return this builder for chaining
     * @throws IllegalStateException if not a LINES builder
     */
    public MeshBuilder line(int i0, int i1) {
        if (primitiveType != PrimitiveType.LINES) {
            throw new IllegalStateException("Cannot add line to " + primitiveType + " mesh");
        }
        indices.add(i0);
        indices.add(i1);
        return this;
    }
    
    /**
     * Adds a quad as two triangles (for TRIANGLES builders).
     * <p>Useful when you need quads but are building a triangle mesh.
     * 
     * <p>Vertex order (counter-clockwise):
     * <pre>
     * topLeft ─── topRight
     *    │           │
     * bottomLeft ─ bottomRight
     * </pre>
     * 
     * @param topLeft top-left vertex index
     * @param topRight top-right vertex index
     * @param bottomRight bottom-right vertex index
     * @param bottomLeft bottom-left vertex index
     * @return this builder for chaining
     */
    public MeshBuilder quadAsTriangles(int topLeft, int topRight, int bottomRight, int bottomLeft) {
        // Use working sphere inline pattern: BL→BR→TL, BR→TR→TL (CCW from outside)
        // Quad layout:
        //   topLeft ─── topRight
        //      │           │
        //   bottomLeft ─ bottomRight
        triangle(bottomLeft, bottomRight, topLeft);
        triangle(bottomRight, topRight, topLeft);
        return this;
    }
    
    /**
     * Adds a quad as triangles using a specific triangle pattern.
     * 
     * @param topLeft top-left vertex index
     * @param topRight top-right vertex index
     * @param bottomRight bottom-right vertex index
     * @param bottomLeft bottom-left vertex index
     * @param pattern the triangle pattern to use
     * @return this builder for chaining
     */
    public MeshBuilder quadAsTriangles(int topLeft, int topRight, int bottomRight, int bottomLeft, 
                                        net.cyberpunk042.mcshaders.core.pattern.QuadPattern pattern) {
        if (pattern == null) {
            return quadAsTriangles(topLeft, topRight, bottomRight, bottomLeft);
        }
        
        // Map corner indices
        int[] corners = {topLeft, topRight, bottomRight, bottomLeft};
        
        // Emit triangles based on pattern
        for (net.cyberpunk042.mcshaders.core.pattern.QuadPattern.Corner[] tri : pattern.triangles()) {
            if (tri.length >= 3) {
                int v0 = cornerToIndex(corners, tri[0]);
                int v1 = cornerToIndex(corners, tri[1]);
                int v2 = cornerToIndex(corners, tri[2]);
                triangle(v0, v1, v2);
            }
        }
        return this;
    }
    
    /**
     * Maps a Corner enum to the corresponding vertex index.
     */
    private int cornerToIndex(int[] corners, net.cyberpunk042.mcshaders.core.pattern.QuadPattern.Corner corner) {
        return switch (corner) {
            case TOP_LEFT -> corners[0];
            case TOP_RIGHT -> corners[1];
            case BOTTOM_RIGHT -> corners[2];
            case BOTTOM_LEFT -> corners[3];
        };
    }
    
    /**
     * Adds a quad as triangles using a list of triangle corner arrays.
     * Used for dynamic/shuffled patterns.
     *
     * @param topLeft top-left vertex index
     * @param topRight top-right vertex index
     * @param bottomRight bottom-right vertex index
     * @param bottomLeft bottom-left vertex index
     * @param triangles list of Corner arrays (each with 3 corners)
     * @return this builder for chaining
     */
    public MeshBuilder quadAsTriangles(int topLeft, int topRight, int bottomRight, int bottomLeft, 
                                        java.util.List<net.cyberpunk042.mcshaders.core.pattern.QuadPattern.Corner[]> triangles) {
        if (triangles == null || triangles.isEmpty()) {
            return quadAsTriangles(topLeft, topRight, bottomRight, bottomLeft);
        }
        
        // Map corner indices
        int[] corners = {topLeft, topRight, bottomRight, bottomLeft};
        
        // Emit triangles based on provided list
        for (net.cyberpunk042.mcshaders.core.pattern.QuadPattern.Corner[] tri : triangles) {
            if (tri.length >= 3) {
                int v0 = cornerToIndex(corners, tri[0]);
                int v1 = cornerToIndex(corners, tri[1]);
                int v2 = cornerToIndex(corners, tri[2]);
                triangle(v0, v1, v2);
            }
        }
        return this;
    }
    
    /**
     * Adds a quad as triangles using any VertexPattern.
     * Uses getVertexOrder() which works for both QuadPattern and ShufflePattern.
     *
     * @param topLeft top-left vertex index
     * @param topRight top-right vertex index
     * @param bottomRight bottom-right vertex index
     * @param bottomLeft bottom-left vertex index
     * @param pattern any VertexPattern (QuadPattern, ShufflePattern, etc.)
     * @return this builder for chaining
     */
    public MeshBuilder quadAsTrianglesFromPattern(int topLeft, int topRight, int bottomRight, int bottomLeft, 
                                        net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern) {
        if (pattern == null) {
            return quadAsTriangles(topLeft, topRight, bottomRight, bottomLeft);
        }
        
        // If it's a QuadPattern, use the specialized method
        if (pattern instanceof net.cyberpunk042.mcshaders.core.pattern.QuadPattern qp) {
            return quadAsTriangles(topLeft, topRight, bottomRight, bottomLeft, qp);
        }
        
        // Map corner indices to match Corner enum: 0=TL, 1=TR, 2=BL, 3=BR
        // Method takes (topLeft, topRight, bottomRight, bottomLeft) but 
        // Corner enum has BL=2, BR=3, so we must reorder correctly
        int[] corners = {topLeft, topRight, bottomLeft, bottomRight};
        
        // Get vertex order from pattern
        int[][] vertexOrder = pattern.getVertexOrder();
        if (vertexOrder == null || vertexOrder.length == 0) {
            return quadAsTriangles(topLeft, topRight, bottomRight, bottomLeft);
        }
        
        // Emit triangles based on vertex order
        for (int[] tri : vertexOrder) {
            if (tri.length >= 3) {
                int v0 = corners[tri[0]];
                int v1 = corners[tri[1]];
                int v2 = corners[tri[2]];
                triangle(v0, v1, v2);
            }
        }
        return this;
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // State Queries
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Returns the current number of vertices added.
     */
    public int vertexCount() {
        return vertices.size();
    }
    
    /**
     * Returns a vertex by index (for icosphere midpoint calculation).
     * @param index Vertex index
     * @return The vertex at that index
     * @throws IndexOutOfBoundsException if index is invalid
     */
    public Vertex getVertex(int index) {
        return vertices.get(index);
    }
    
    /**
     * Returns the current number of indices added.
     */
    public int indexCount() {
        return indices.size();
    }
    
    // ─────────────────────────────────────────────────────────────────────────────
    // Build / Reset
    // ─────────────────────────────────────────────────────────────────────────────
    
    /**
     * Clears all vertices and indices, allowing reuse of this builder.
     * <p>Primitive type remains the same.
     * 
     * @return this builder for chaining
     */
    public MeshBuilder clear() {
        vertices.clear();
        indices.clear();
        return this;
    }
    
    /**
     * Merges another mesh into this builder.
     * <p>All vertices and indices from the source mesh are added to this builder.
     * Index values are offset by the current vertex count.
     * 
     * @param mesh Source mesh to merge
     * @return this builder for chaining
     */
    public MeshBuilder mergeMesh(Mesh mesh) {
        if (mesh == null) return this;
        
        int vertexOffset = vertices.size();
        
        // Add all vertices from source mesh
        for (Vertex v : mesh.vertices()) {
            vertices.add(v);
        }
        
        // Add all indices, offset by existing vertex count
        for (int idx : mesh.indices()) {
            indices.add(idx + vertexOffset);
        }
        
        return this;
    }
    
    /**
     * Builds and returns the immutable {@link Mesh}.
     * <p>The builder can still be used after this (add more, build again).
     * 
     * @return new immutable Mesh containing all added vertices and indices
     */
    public Mesh build() {
        // Convert List<Integer> to int[] for Mesh constructor
        int[] indexArray = new int[indices.size()];
        for (int i = 0; i < indices.size(); i++) {
            indexArray[i] = indices.get(i);
        }
        return new Mesh(vertices, indexArray, primitiveType);
    }
}
