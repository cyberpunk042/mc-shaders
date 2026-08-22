/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 *
 * JSON binding was removed on the way across: the model stays free of
 * serialisation so it can be loaded from external content. The @JsonField
 * metadata is retained for a codec layer above core.
 */
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Platonic solids with geometry data for tessellation.
 * 
 * <p>Each type provides its own vertices, faces, and normals, enabling
 * data-driven tessellation without switch statements.</p>
 * 
 * <h3>Mathematical Constants</h3>
 * <ul>
 *   <li>PHI (φ) = Golden ratio ≈ 1.618</li>
 *   <li>INV_PHI = 1/φ ≈ 0.618</li>
 *   <li>SQRT_3 = √3 ≈ 1.732</li>
 * </ul>
 * 
 * @see PolyhedronShape
 */
public enum PolyType {
    
    // =========================================================================
    // TETRAHEDRON - 4 triangular faces, self-dual
    // =========================================================================
    TETRAHEDRON("Tetrahedron", 4, 6, 4, "TETRAHEDRON", true) {
        @Override
        public float[][] unitVertices() {
            // 4 vertices at alternating corners of a unit cube
            float s = 1.0f / SQRT_3;
            return new float[][] {
                { s,  s,  s},   // 0: (+,+,+)
                { s, -s, -s},   // 1: (+,-,-)
                {-s,  s, -s},   // 2: (-,+,-)
                {-s, -s,  s}    // 3: (-,-,+)
            };
        }
        
        @Override
        public int[][] faces() {
            return new int[][] {
                {0, 1, 2},  // Face 0: opposite to vertex 3
                {0, 3, 1},  // Face 1: opposite to vertex 2
                {0, 2, 3},  // Face 2: opposite to vertex 1
                {1, 3, 2}   // Face 3: opposite to vertex 0 (base)
            };
        }
        
        @Override
        public float[][] faceNormals() {
            float n = 1.0f / SQRT_3;
            return new float[][] {
                { n,  n, -n},   // Face 0
                { n, -n,  n},   // Face 1
                {-n,  n,  n},   // Face 2
                {-n, -n, -n}    // Face 3
            };
        }
        
        @Override
        public FaceType faceType() { return FaceType.TRIANGLE; }
    },
    
    // =========================================================================
    // CUBE - 6 square faces, dual of octahedron
    // =========================================================================
    CUBE("Cube", 6, 12, 8, "OCTAHEDRON", false) {
        @Override
        public float[][] unitVertices() {
            float r = 1.0f / SQRT_3;
            return new float[][] {
                {-r, -r, -r},  // 0: BACK_BOTTOM_LEFT
                { r, -r, -r},  // 1: BACK_BOTTOM_RIGHT
                { r,  r, -r},  // 2: BACK_TOP_RIGHT
                {-r,  r, -r},  // 3: BACK_TOP_LEFT
                {-r, -r,  r},  // 4: FRONT_BOTTOM_LEFT
                { r, -r,  r},  // 5: FRONT_BOTTOM_RIGHT
                { r,  r,  r},  // 6: FRONT_TOP_RIGHT
                {-r,  r,  r}   // 7: FRONT_TOP_LEFT
            };
        }
        
        @Override
        public int[][] faces() {
            return new int[][] {
                {1, 0, 3, 2},  // BACK (-Z): CCW from -Z
                {4, 5, 6, 7},  // FRONT (+Z): CCW from +Z
                {0, 4, 7, 3},  // LEFT (-X)
                {5, 1, 2, 6},  // RIGHT (+X)
                {7, 6, 2, 3},  // TOP (+Y)
                {0, 1, 5, 4}   // BOTTOM (-Y)
            };
        }
        
        @Override
        public float[][] faceNormals() {
            return new float[][] {
                { 0,  0, -1},  // BACK
                { 0,  0,  1},  // FRONT
                {-1,  0,  0},  // LEFT
                { 1,  0,  0},  // RIGHT
                { 0,  1,  0},  // TOP
                { 0, -1,  0}   // BOTTOM
            };
        }
        
        @Override
        public FaceType faceType() { return FaceType.QUAD; }
    },
    
    // =========================================================================
    // OCTAHEDRON - 8 triangular faces, dual of cube
    // =========================================================================
    OCTAHEDRON("Octahedron", 8, 12, 6, "CUBE", true) {
        @Override
        public float[][] unitVertices() {
            return new float[][] {
                { 0,  1,  0},   // 0: TOP (+Y)
                { 0, -1,  0},   // 1: BOTTOM (-Y)
                { 1,  0,  0},   // 2: +X
                {-1,  0,  0},   // 3: -X
                { 0,  0,  1},   // 4: +Z
                { 0,  0, -1}    // 5: -Z
            };
        }
        
        @Override
        public int[][] faces() {
            return new int[][] {
                // Top pyramid
                {0, 4, 2},   // TOP, +Z, +X
                {0, 2, 5},   // TOP, +X, -Z
                {0, 5, 3},   // TOP, -Z, -X
                {0, 3, 4},   // TOP, -X, +Z
                // Bottom pyramid
                {1, 2, 4},   // BOTTOM, +X, +Z
                {1, 5, 2},   // BOTTOM, -Z, +X
                {1, 3, 5},   // BOTTOM, -X, -Z
                {1, 4, 3}    // BOTTOM, +Z, -X
            };
        }
        
        @Override
        public float[][] faceNormals() {
            float n = 1.0f / SQRT_3;
            return new float[][] {
                { n,  n,  n},   // face 0
                { n,  n, -n},   // face 1
                {-n,  n, -n},   // face 2
                {-n,  n,  n},   // face 3
                { n, -n,  n},   // face 4
                { n, -n, -n},   // face 5
                {-n, -n, -n},   // face 6
                {-n, -n,  n}    // face 7
            };
        }
        
        @Override
        public FaceType faceType() { return FaceType.TRIANGLE; }
    },
    
    // =========================================================================
    // ICOSAHEDRON - 20 triangular faces, dual of dodecahedron
    // =========================================================================
    ICOSAHEDRON("Icosahedron", 20, 30, 12, "DODECAHEDRON", true) {
        @Override
        public float[][] unitVertices() {
            // Vertices from 3 orthogonal golden rectangles, normalized
            float scale = 1.0f / ICOSA_NORM;
            float a = scale;
            float b = scale * PHI;
            return new float[][] {
                // YZ plane rectangle
                { 0,  a,  b},   // 0
                { 0,  a, -b},   // 1
                { 0, -a,  b},   // 2
                { 0, -a, -b},   // 3
                // XY plane rectangle
                { a,  b,  0},   // 4
                { a, -b,  0},   // 5
                {-a,  b,  0},   // 6
                {-a, -b,  0},   // 7
                // XZ plane rectangle
                { b,  0,  a},   // 8
                {-b,  0,  a},   // 9
                { b,  0, -a},   // 10
                {-b,  0, -a}    // 11
            };
        }
        
        @Override
        public int[][] faces() {
            return new int[][] {
                // 5 triangles around top vertex 0
                {0, 2, 8}, {0, 8, 4}, {0, 4, 6}, {0, 6, 9}, {0, 9, 2},
                // 5 triangles around bottom vertex 3
                {3, 10, 5}, {3, 5, 7}, {3, 7, 11}, {3, 11, 1}, {3, 1, 10},
                // 10 triangles around middle band
                {2, 5, 8}, {8, 5, 10}, {8, 10, 4}, {4, 10, 1},
                {4, 1, 6}, {6, 1, 11}, {6, 11, 9}, {9, 11, 7},
                {9, 7, 2}, {2, 7, 5}
            };
        }
        
        @Override
        public float[][] faceNormals() {
            // Normals computed from face centers (average of vertices)
            // For an icosahedron, face normal = normalized(v0 + v1 + v2)
            return null; // Computed dynamically in tessellator
        }
        
        @Override
        public FaceType faceType() { return FaceType.TRIANGLE; }
    },
    
    // =========================================================================
    // DODECAHEDRON - 12 pentagonal faces, dual of icosahedron
    // =========================================================================
    DODECAHEDRON("Dodecahedron", 12, 30, 20, "ICOSAHEDRON", false) {
        @Override
        public float[][] unitVertices() {
            float s = 1.0f / SQRT_3;
            float phi = PHI;
            float invPhi = INV_PHI;
            return new float[][] {
                {-s, -s, -s},  // 0 - cube vertex
                {-s, -s,  s},  // 1
                {-s,  s, -s},  // 2
                {-s,  s,  s},  // 3
                { s, -s, -s},  // 4
                { s, -s,  s},  // 5
                { s,  s, -s},  // 6
                { s,  s,  s},  // 7
                { 0, -invPhi * s, -phi * s},  // 8 - golden rectangle vertices
                { 0, -invPhi * s,  phi * s},  // 9
                { 0,  invPhi * s, -phi * s},  // 10
                { 0,  invPhi * s,  phi * s},  // 11
                {-invPhi * s, -phi * s,  0},  // 12
                {-invPhi * s,  phi * s,  0},  // 13
                { invPhi * s, -phi * s,  0},  // 14
                { invPhi * s,  phi * s,  0},  // 15
                {-phi * s,  0, -invPhi * s},  // 16
                {-phi * s,  0,  invPhi * s},  // 17
                { phi * s,  0, -invPhi * s},  // 18
                { phi * s,  0,  invPhi * s}   // 19
            };
        }
        
        @Override
        public int[][] faces() {
            // 12 pentagonal faces
            return new int[][] {
                { 0,  8,  4, 14, 12},
                { 0,  8, 10,  2, 16},
                { 0, 12,  1, 17, 16},
                { 1,  9,  5, 14, 12},
                { 1,  9, 11,  3, 17},
                { 2, 10,  6, 15, 13},
                { 2, 13,  3, 17, 16},
                { 3, 11,  7, 15, 13},
                { 4,  8, 10,  6, 18},
                { 4, 14,  5, 19, 18},
                { 5,  9, 11,  7, 19},
                { 6, 15,  7, 19, 18}
            };
        }
        
        @Override
        public float[][] faceNormals() {
            return null; // Computed dynamically in tessellator
        }
        
        @Override
        public FaceType faceType() { return FaceType.PENTAGON; }
    };
    
    // =========================================================================
    // Mathematical Constants
    // =========================================================================
    
    /** Golden ratio φ = (1 + √5) / 2 ≈ 1.618 */
    public static final float PHI = (1.0f + (float) Math.sqrt(5)) / 2.0f;
    
    /** Inverse golden ratio 1/φ ≈ 0.618 */
    public static final float INV_PHI = 1.0f / PHI;
    
    /** √3 ≈ 1.732 - Used for tetrahedron/cube scaling */
    public static final float SQRT_3 = (float) Math.sqrt(3);
    
    /** √(1 + φ²) - Used for icosahedron vertex normalization */
    public static final float ICOSA_NORM = (float) Math.sqrt(1 + PHI * PHI);
    
    // =========================================================================
    // Face Type for Tessellation
    // =========================================================================
    
    /** How faces should be tessellated */
    public enum FaceType {
        TRIANGLE,  // Emit as single triangle
        QUAD,      // Emit as quad (2 triangles with pattern)
        PENTAGON   // Emit as 5 triangles from center
    }
    
    // =========================================================================
    // Instance Fields
    // =========================================================================
    
    private final String label;
    private final int faceCount;
    private final int edgeCount;
    private final int vertexCount;
    private final String dualName;
    private final boolean triangleFaces;
    
    PolyType(String label, int faces, int edges, int vertices, 
             String dualName, boolean triangleFaces) {
        this.label = label;
        this.faceCount = faces;
        this.edgeCount = edges;
        this.vertexCount = vertices;
        this.dualName = dualName;
        this.triangleFaces = triangleFaces;
    }
    
    // =========================================================================
    // Abstract Geometry Methods (implemented per type)
    // =========================================================================
    
    /**
     * Returns unit vertices (circumradius = 1).
     * Scale by desired radius for actual geometry.
     */
    public abstract float[][] unitVertices();
    
    /**
     * Returns face indices referencing vertices.
     * Each face is an array of vertex indices.
     */
    public abstract int[][] faces();
    
    /**
     * Returns pre-computed face normals, or null if computed dynamically.
     */
    public abstract float[][] faceNormals();
    
    /**
     * Returns how this polyhedron's faces should be tessellated.
     */
    public abstract FaceType faceType();
    
    /**
     * Returns vertices scaled to the given radius.
     */
    public float[][] vertices(float radius) {
        float[][] unit = unitVertices();
        float[][] scaled = new float[unit.length][3];
        for (int i = 0; i < unit.length; i++) {
            scaled[i][0] = unit[i][0] * radius;
            scaled[i][1] = unit[i][1] * radius;
            scaled[i][2] = unit[i][2] * radius;
        }
        return scaled;
    }
    
    // =========================================================================
    // Existing Accessors
    // =========================================================================
    
    /** Display label for GUI */
    public String label() { return label; }
    
    @Override
    public String toString() { return label; }
    
    /** Number of faces */
    public int getFaces() { return faceCount; }
    
    /** Number of edges */
    public int getEdges() { return edgeCount; }
    
    /** Number of vertices */
    public int getVertices() { return vertexCount; }
    
    /** Returns true if faces are triangles */
    public boolean hasTriangleFaces() { return triangleFaces; }
    
    /** Returns the dual polyhedron type */
    public PolyType getDual() {
        return valueOf(dualName);
    }
    
    /**
     * Parse from string (case-insensitive).
     */
    public static PolyType fromName(String name) {
        if (name == null || name.isEmpty()) {
            return CUBE;
        }
        try {
            return valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUBE;
        }
    }
}

