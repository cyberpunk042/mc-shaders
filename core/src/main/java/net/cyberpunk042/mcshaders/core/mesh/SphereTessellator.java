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

import net.cyberpunk042.mcshaders.core.diag.Diag;
import net.cyberpunk042.mcshaders.core.animation.WaveConfig;
import net.cyberpunk042.mcshaders.core.pattern.VertexPattern;
import net.cyberpunk042.mcshaders.core.pattern.QuadPattern;
import net.cyberpunk042.mcshaders.core.pattern.TrianglePattern;
import net.cyberpunk042.mcshaders.core.shape.ShapeMath;
import net.cyberpunk042.mcshaders.core.shape.SphereAlgorithm;
import net.cyberpunk042.mcshaders.core.shape.SphereDeformation;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import net.cyberpunk042.mcshaders.core.shape.CloudStyle;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;
import net.cyberpunk042.mcshaders.core.animation.WaveDeformer;

import java.util.HashMap;
import java.util.Map;

/**
 * Tessellates sphere shapes into triangle meshes.
 * 
 * <h2>Algorithms</h2>
 * <p>Supports multiple tessellation algorithms via {@link SphereAlgorithm}:</p>
 * <ul>
 *   <li><b>LAT_LON</b>: Latitude/longitude grid. Best for patterns and partial spheres.</li>
 *   <li><b>UV_SPHERE</b>: Standard UV mapping with shared pole vertices.</li>
 *   <li><b>ICO_SPHERE</b>: Icosahedron subdivision. Uniform distribution, no pole artifacts.</li>
 *   <li><b>TYPE_A/TYPE_E</b>: Direct rendering (handled by SphereRenderer, not tessellation).</li>
 * </ul>
 * 
 * <h2>Partial Spheres</h2>
 * <p>Supports partial spheres via latStart/latEnd and lonStart/lonEnd parameters:
 * <ul>
 *   <li>Hemisphere: latStart=0, latEnd=0.5 (top half)</li>
 *   <li>Band: latStart=0.3, latEnd=0.7</li>
 *   <li>Arc: lonStart=0, lonEnd=0.5 (half sphere around Y)</li>
 * </ul>
 * 
 * <h2>Patterns</h2>
 * <p>Supports vertex patterns via {@link VertexPattern} for visual variety.
 * Pattern's {@code shouldRender()} determines which cells are visible.</p>
 * 
 * <h2>Usage</h2>
 * <pre>
 * Mesh mesh = SphereTessellator.tessellate(sphereShape, QuadPattern.FILLED_1, null);
 * </pre>
 * 
 * @see SphereShape
 * @see SphereAlgorithm
 * @see QuadPattern
 * @see Vertex#spherical(float, float, float)
 */
public final class SphereTessellator {
    
    private SphereTessellator() {} // Static utility class
    
    // Golden ratio for icosahedron
    private static final float PHI = (1.0f + (float) Math.sqrt(5.0)) / 2.0f;
    
    // =========================================================================
    // Main API
    // =========================================================================
    
    /**
     * Tessellates a sphere shape into a mesh.
     * 
     * <p>Dispatches to the appropriate algorithm based on {@link SphereShape#algorithm()}.</p>
     * 
     * <p><b>Note:</b> TYPE_A and TYPE_E are not mesh-based algorithms - they use
     * direct rendering with overlapping cubes, which is a renderer's job rather than
     * a tessellator's — core has no renderer. For those algorithms this method falls
     * back to LAT_LON tessellation and warns, so the caller gets a mesh rather than
     * nothing.</p>
     * 
     * @param shape The sphere shape definition
     * @param pattern Vertex pattern for visual variety (null = default)
     * @param visibility Visibility mask for cell filtering (null = all visible)
     * @param wave Wave config for CPU deformation (null or inactive = no deformation)
     * @param time Current time in ticks for wave animation
     * @return Generated mesh
     */
    public static Mesh tessellate(SphereShape shape, VertexPattern pattern, 
                                   VisibilityMask visibility, WaveConfig wave, float time) {
        if (shape == null) {
            throw new IllegalArgumentException("SphereShape cannot be null");
        }
        
        // Use default pattern if none provided
        if (pattern == null) {
            pattern = QuadPattern.FILLED_1;
        }
        
        SphereAlgorithm algorithm = shape.algorithm();
        
        Diag.RENDER.topic("tessellate")
            .kv("shape", "sphere")
            .kv("algorithm", algorithm)
            .kv("radius", shape.radius())
            .kv("wave", wave != null && wave.isActive() ? "active" : "none")
            .debug("Tessellating sphere");
        
        // Dispatch based on algorithm
        return switch (algorithm) {
            case LAT_LON -> tessellateLatLon(shape, pattern, visibility, wave, time);
            case UV_SPHERE -> tessellateUvSphere(shape, pattern, visibility, wave, time);
            case ICO_SPHERE -> tessellateIcoSphere(shape, wave, time);
            // TYPE_A and TYPE_E are direct rendering, not mesh tessellation
            // Fall back to LAT_LON for mesh generation
            case TYPE_A, TYPE_E -> {
                Diag.RENDER.topic("tessellate")
                    .kv("algorithm", algorithm)
                    .warn("TYPE_A/TYPE_E use direct rendering. Falling back to LAT_LON for mesh.");
                yield tessellateLatLon(shape, pattern, visibility, wave, time);
            }
        };
    }
    
    /**
     * Tessellates a sphere with default pattern and no visibility mask or wave.
     */
    public static Mesh tessellate(SphereShape shape) {
        return tessellate(shape, null, null, null, 0);
    }
    
    /**
     * Tessellates a sphere with pattern and visibility but no wave (backward compatible).
     */
    public static Mesh tessellate(SphereShape shape, VertexPattern pattern, VisibilityMask visibility) {
        return tessellate(shape, pattern, visibility, null, 0);
    }
    
    /**
     * Check if the algorithm requires direct rendering (TYPE_A/TYPE_E).
     * These algorithms cannot be tessellated into a mesh.
     */
    public static boolean requiresDirectRendering(SphereAlgorithm algorithm) {
        return algorithm == SphereAlgorithm.TYPE_A || algorithm == SphereAlgorithm.TYPE_E;
    }
    
    // =========================================================================
    // LAT_LON Algorithm (Default)
    // =========================================================================
    
    /**
     * Tessellates using latitude/longitude grid.
     * 
     * <p><b>Delegates to {@link VectorMath#generateLatLonGridVertex}</b> for proper
     * vertex position deformation (droplet, egg, cone, etc.).</p>
     */
    private static Mesh tessellateLatLon(SphereShape shape, VertexPattern pattern,
                                          VisibilityMask visibility, WaveConfig wave, float time) {
        MeshBuilder builder = MeshBuilder.triangles();
        
        int latSteps = shape.latSteps();
        int lonSteps = shape.lonSteps();
        float radius = shape.radius();
        float latStart = shape.latStart();
        float latEnd = shape.latEnd();
        float lonStart = shape.lonStart();
        float lonEnd = shape.lonEnd();
        
        // Get deformation settings
        boolean applyDeformation = shape.hasDeformation();
        SphereDeformation deformation = shape.effectiveDeformation();
        float deformIntensity = shape.deformationIntensity();
        float deformLength = shape.deformationLength();
        int deformCount = shape.deformationCount();
        float deformSmoothness = shape.deformationSmoothness();
        float deformBumpSize = shape.deformationBumpSize();
        CloudStyle cloudStyle = shape.cloudStyle() != null ? shape.cloudStyle() : CloudStyle.GAUSSIAN;
        int cloudSeed = shape.cloudSeed();
        float cloudWidth = shape.cloudWidth();
        // Planet parameters
        float planetFrequency = shape.planetFrequency();
        int planetOctaves = shape.planetOctaves();
        float planetLacunarity = shape.planetLacunarity();
        float planetPersistence = shape.planetPersistence();
        float planetRidged = shape.planetRidged();
        int planetCraterCount = shape.planetCraterCount();
        int planetSeed = shape.planetSeed();
        
        // Build the FULL vertex function (position + normal) for proper spheroid lighting
        VectorMath.FullVertexFunction fullVertexFunc = (theta, phi, r) -> 
            deformation.computeFullVertex(theta, phi, r, deformIntensity, deformLength, 
                deformCount, deformSmoothness, deformBumpSize,
                planetFrequency, planetOctaves, planetLacunarity, planetPersistence,
                planetRidged, planetCraterCount, planetSeed, cloudStyle, cloudSeed, cloudWidth);
        
        // Generate the entire surface using the shared algorithm with proper vertex positions AND normals
        VectorMath.generateLatLonGridFull(
            builder, radius, latSteps, lonSteps,
            latStart, latEnd, lonStart, lonEnd,
            fullVertexFunc, pattern, visibility, wave, time
        );
        
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        return logAndBuild(builder, applyWave ? "LAT_LON+WAVE" : (applyDeformation ? "LAT_LON+DEFORM" : "LAT_LON"));
    }
    
    // =========================================================================
    // UV_SPHERE Algorithm (Delegates to VectorMath.generatePolarSurface)
    // =========================================================================
    
    /**
     * Tessellates using UV sphere with shared pole vertices.
     * 
     * <p><b>Delegates to {@link VectorMath#generatePolarSurface}</b> for all features.</p>
     */
    private static Mesh tessellateUvSphere(SphereShape shape, VertexPattern pattern,
                                            VisibilityMask visibility, WaveConfig wave, float time) {
        MeshBuilder builder = MeshBuilder.triangles();
        
        int rings = shape.latSteps();
        int segments = shape.lonSteps();
        float radius = shape.radius();
        
        // Get deformation settings
        boolean applyDeformation = shape.hasDeformation();
        SphereDeformation deformation = shape.effectiveDeformation();
        float deformIntensity = shape.deformationIntensity();
        
        // Build the radius function for deformation
        VectorMath.RadiusFunction radiusFunc = applyDeformation 
            ? theta -> deformation.computeRadiusFactor(theta, deformIntensity)
            : null;
        
        // Use Y-up direction for standard sphere, centered at origin
        float[] direction = new float[] { 0, 1, 0 };
        float[] center = new float[] { 0, 0, 0 };
        
        // Generate the entire surface using the shared algorithm
        VectorMath.generatePolarSurface(
            builder, center, direction, radius, rings, segments,
            radiusFunc, pattern, visibility, wave, time
        );
        
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        return logAndBuild(builder, applyWave ? "UV_SPHERE+WAVE" : (applyDeformation ? "UV_SPHERE+DEFORM" : "UV_SPHERE"));
    }
    
    // =========================================================================
    // ICO_SPHERE Algorithm
    // =========================================================================
    
    /**
     * Tessellates using icosahedron subdivision.
     * Uniform vertex distribution, no pole artifacts.
     * Does NOT support partial spheres or patterns.
     * 
     * <p>Subdivision levels:</p>
     * <ul>
     *   <li>0: 12 vertices, 20 triangles (base icosahedron)</li>
     *   <li>1: 42 vertices, 80 triangles</li>
     *   <li>2: 162 vertices, 320 triangles</li>
     *   <li>3: 642 vertices, 1280 triangles</li>
     * </ul>
     */
    private static Mesh tessellateIcoSphere(SphereShape shape, WaveConfig wave, float time) {
        MeshBuilder builder = MeshBuilder.triangles();
        float radius = shape.radius();
        
        // Check if wave deformation should be applied
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        // Derive subdivision level from latSteps (approximate mapping)
        // latSteps 8-16 → subdiv 1, 17-32 → subdiv 2, 33-64 → subdiv 3, etc.
        int subdivisions = Math.max(0, Math.min(4, 
            (int) (Math.log(shape.latSteps() / 8.0) / Math.log(2)) + 1));
        
        // Icosahedron base vertices (12 vertices)
        float[][] icoVerts = {
            {-1,  PHI, 0}, { 1,  PHI, 0}, {-1, -PHI, 0}, { 1, -PHI, 0},
            { 0, -1,  PHI}, { 0,  1,  PHI}, { 0, -1, -PHI}, { 0,  1, -PHI},
            { PHI, 0, -1}, { PHI, 0,  1}, {-PHI, 0, -1}, {-PHI, 0,  1},
        };
        
        // Add normalized and scaled vertices (with wave deformation)
        int[] vertexIndices = new int[12];
        for (int i = 0; i < 12; i++) {
            float x = icoVerts[i][0];
            float y = icoVerts[i][1];
            float z = icoVerts[i][2];
            float len = (float) Math.sqrt(x*x + y*y + z*z);
            x = x/len * radius;
            y = y/len * radius;
            z = z/len * radius;
            
            Vertex v = Vertex.pos(x, y, z).withNormal(x/radius, y/radius, z/radius);
            if (applyWave) v = WaveDeformer.applyToVertex(v, wave, time);
            vertexIndices[i] = builder.addVertex(v);
        }
        
        // Icosahedron faces (20 triangles)
        int[][] icoFaces = {
            {0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11},
            {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9},
            {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1},
        };
        
        // Build initial triangle list
        int[][] triangles = new int[icoFaces.length][3];
        for (int i = 0; i < icoFaces.length; i++) {
            triangles[i] = new int[]{
                vertexIndices[icoFaces[i][0]],
                vertexIndices[icoFaces[i][1]],
                vertexIndices[icoFaces[i][2]]
            };
        }
        
        // Subdivision with midpoint cache
        // Note: Wave is applied in getMidpointWithWave for subdivided vertices
        Map<Long, Integer> midpointCache = new HashMap<>();
        
        for (int level = 0; level < subdivisions; level++) {
            int[][] newTriangles = new int[triangles.length * 4][3];
            midpointCache.clear();
            
            int newIdx = 0;
            for (int[] tri : triangles) {
                int a = getMidpointWithWave(builder, midpointCache, tri[0], tri[1], radius, wave, time, applyWave);
                int b = getMidpointWithWave(builder, midpointCache, tri[1], tri[2], radius, wave, time, applyWave);
                int c = getMidpointWithWave(builder, midpointCache, tri[2], tri[0], radius, wave, time, applyWave);
                
                newTriangles[newIdx++] = new int[]{tri[0], a, c};
                newTriangles[newIdx++] = new int[]{tri[1], b, a};
                newTriangles[newIdx++] = new int[]{tri[2], c, b};
                newTriangles[newIdx++] = new int[]{a, b, c};
            }
            
            triangles = newTriangles;
        }
        
        // Add triangles to mesh using emitCellFromPattern for proper vertex ordering
        VertexPattern trianglePattern = TrianglePattern.DEFAULT;
        for (int[] tri : triangles) {
            builder.emitCellFromPattern(tri, trianglePattern);
        }
        
        return logAndBuild(builder, applyWave ? "ICO_SPHERE+WAVE (subdiv=" + subdivisions + ")" : "ICO_SPHERE (subdiv=" + subdivisions + ")");
    }
    
    /**
     * Get or create midpoint vertex between two vertices, projected to sphere surface.
     */
    private static int getMidpoint(MeshBuilder builder, Map<Long, Integer> cache,
                                    int v0, int v1, float radius) {
        // Canonical key (smaller index first)
        long key = Math.min(v0, v1) * 100000L + Math.max(v0, v1);
        
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        
        Vertex vert0 = builder.getVertex(v0);
        Vertex vert1 = builder.getVertex(v1);
        
        // Midpoint
        float mx = (vert0.x() + vert1.x()) / 2;
        float my = (vert0.y() + vert1.y()) / 2;
        float mz = (vert0.z() + vert1.z()) / 2;
        
        // Project to sphere surface
        float len = (float) Math.sqrt(mx*mx + my*my + mz*mz);
        mx = mx/len * radius;
        my = my/len * radius;
        mz = mz/len * radius;
        
        int idx = builder.addVertex(
            Vertex.pos(mx, my, mz).withNormal(mx/radius, my/radius, mz/radius));
        cache.put(key, idx);
        return idx;
    }
    
    /**
     * Get or create midpoint vertex between two vertices, projected to sphere surface,
     * with optional wave deformation applied.
     */
    private static int getMidpointWithWave(MeshBuilder builder, Map<Long, Integer> cache,
                                            int v0, int v1, float radius,
                                            WaveConfig wave, float time, boolean applyWave) {
        // Canonical key (smaller index first)
        long key = Math.min(v0, v1) * 100000L + Math.max(v0, v1);
        
        Integer cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        
        Vertex vert0 = builder.getVertex(v0);
        Vertex vert1 = builder.getVertex(v1);
        
        // Midpoint (use original sphere positions, not deformed ones)
        // We need the base positions before wave deformation
        float mx = (vert0.x() + vert1.x()) / 2;
        float my = (vert0.y() + vert1.y()) / 2;
        float mz = (vert0.z() + vert1.z()) / 2;
        
        // Project to sphere surface
        float len = (float) Math.sqrt(mx*mx + my*my + mz*mz);
        mx = mx/len * radius;
        my = my/len * radius;
        mz = mz/len * radius;
        
        Vertex v = Vertex.pos(mx, my, mz).withNormal(mx/radius, my/radius, mz/radius);
        if (applyWave) {
            v = WaveDeformer.applyToVertex(v, wave, time);
        }
        
        int idx = builder.addVertex(v);
        cache.put(key, idx);
        return idx;
    }
    
    // =========================================================================
    // Utilities
    // =========================================================================
    
    private static Mesh logAndBuild(MeshBuilder builder, String algorithm) {
        Mesh mesh = builder.build();
        Diag.RENDER.topic("tessellate")
            .kv("algorithm", algorithm)
            .kv("vertices", mesh.vertexCount())
            .kv("triangles", mesh.indexCount() / 3)
            .trace("Sphere tessellation complete");
        return mesh;
    }
}
