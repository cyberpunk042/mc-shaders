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

import net.cyberpunk042.mcshaders.core.animation.WaveConfig;
import net.cyberpunk042.mcshaders.core.pattern.VertexPattern;
import net.cyberpunk042.mcshaders.core.shape.ShapeMath;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;
import net.cyberpunk042.mcshaders.core.animation.WaveDeformer;

/**
 * Common 3D vector math and geometry utilities for mesh generation.
 * 
 * <p>Provides array-based vector operations and polar surface generation
 * used by tessellators. Uses float[3] arrays for position/direction vectors
 * to avoid object allocation in hot loops.</p>
 * 
 * <p><b>Core extraction from SphereTessellator - shared with Ray3DGeometryUtils.</b></p>
 * 
 * @see SphereTessellator
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.Ray3DGeometryUtils
 */
public final class VectorMath {
    
    public static final float PI = (float) Math.PI;
    public static final float TWO_PI = (float) (Math.PI * 2);
    public static final float HALF_PI = (float) (Math.PI * 0.5);
    
    private VectorMath() {} // Utility class
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Polar Surface Generation (EXTRACTED FROM SphereTessellator)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Functional interface for radius modifiers based on polar angle.
     * Used for droplets, eggs, cones, etc.
     */
    @FunctionalInterface
    public interface RadiusFunction {
        /** Returns radius modifier (0-1+) for given polar angle (0 to π). */
        float apply(float theta);
    }
    
    /**
     * Functional interface for computing deformed vertex positions.
     * Used for proper parametric shape deformation (droplet, cone, etc.).
     */
    @FunctionalInterface
    public interface VertexFunction {
        /** Returns {x, y, z} vertex position for given spherical coordinates. */
        float[] apply(float theta, float phi, float radius);
    }
    
    /**
     * Functional interface for computing BOTH position AND normal.
     * Required for proper spheroid lighting (normals differ from position direction).
     */
    @FunctionalInterface
    public interface FullVertexFunction {
        /** Returns {x, y, z, nx, ny, nz} position + normal for given spherical coordinates. */
        float[] apply(float theta, float phi, float radius);
    }
    
    /**
     * Functional interface for computing position, normal, AND alpha.
     * Used for 3D ray flow animation (CLIP mode per-vertex visibility).
     */
    @FunctionalInterface
    public interface FullVertexWithAlphaFunction {
        /** Returns {x, y, z, nx, ny, nz, alpha} position + normal + alpha for given spherical coordinates. */
        float[] apply(float theta, float phi, float radius);
    }
    
    /**
     * Generates a polar surface with FULL feature support.
     * 
     * <p><b>Core algorithm extracted from SphereTessellator.tessellateUvSphere().</b></p>
     * 
     * <p>Supports all sphere features:</p>
     * <ul>
     *   <li>Arbitrary orientation (direction)</li>
     *   <li>Shape deformation (radiusFunc)</li>
     *   <li>Vertex patterns</li>
     *   <li>Visibility masks</li>
     *   <li>Wave deformation</li>
     * </ul>
     * 
     * @param builder MeshBuilder to add vertices/triangles to
     * @param center Center position {x, y, z}
     * @param direction Axis direction (normalized) - pole points this way
     * @param baseRadius Maximum radius of the shape
     * @param rings Number of latitude rings
     * @param segments Number of longitude segments
     * @param radiusFunc Radius modifier function (null = sphere)
     * @param pattern Vertex pattern for cell rendering (null = render all)
     * @param visibility Visibility mask (null = all visible)
     * @param wave Wave deformation config (null = no wave)
     * @param time Current time for wave animation
     */
    public static void generatePolarSurface(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float baseRadius,
            int rings,
            int segments,
            RadiusFunction radiusFunc,
            VertexPattern pattern,
            VisibilityMask visibility,
            WaveConfig wave,
            float time) {
        
        // Ensure we have at least a minimal mesh
        rings = Math.max(2, rings);
        segments = Math.max(3, segments);
        
        // Check if wave deformation should be applied
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        // Build perpendicular frame for orientation
        float[] right = new float[3];
        float[] up = new float[3];
        computePerpendicularFrame(direction, right, up);
        
        // Default to sphere if no radius function
        if (radiusFunc == null) {
            radiusFunc = theta -> 1.0f;
        }
        
        // Top pole vertex (theta = 0)
        float topRadiusMod = radiusFunc.apply(0);
        float[] topPos = add(center, scale(direction, baseRadius * topRadiusMod));
        Vertex topPoleV = Vertex.pos(topPos[0], topPos[1], topPos[2])
            .withNormal(direction[0], direction[1], direction[2]);
        if (applyWave) topPoleV = WaveDeformer.applyToVertex(topPoleV, wave, time);
        int topPole = builder.addVertex(topPoleV);
        
        // Ring vertices (theta from nearly 0 to nearly π)
        int[] ringStartIndices = new int[rings - 1];
        
        for (int ring = 1; ring < rings; ring++) {
            float theta = PI * ring / rings;
            float radiusMod = radiusFunc.apply(theta);
            float effectiveRadius = baseRadius * radiusMod;
            float ringRadius = effectiveRadius * (float) Math.sin(theta);
            float axialDist = effectiveRadius * (float) Math.cos(theta);
            
            // Ring center is along the axis
            float[] ringCenter = add(center, scale(direction, axialDist));
            
            ringStartIndices[ring - 1] = builder.vertexCount();
            
            for (int seg = 0; seg < segments; seg++) {
                float phi = TWO_PI * seg / segments;
                float cosP = (float) Math.cos(phi);
                float sinP = (float) Math.sin(phi);
                
                // Position in the ring plane
                float[] offset = add(scale(right, ringRadius * cosP), scale(up, ringRadius * sinP));
                float[] pos = add(ringCenter, offset);
                
                // Normal: direction from center to this point, normalized
                float[] toPoint = sub(pos, center);
                float[] normal = normalize(toPoint);
                
                Vertex v = Vertex.pos(pos[0], pos[1], pos[2])
                    .withNormal(normal[0], normal[1], normal[2]);
                if (applyWave) v = WaveDeformer.applyToVertex(v, wave, time);
                builder.addVertex(v);
            }
        }
        
        // Bottom pole vertex (theta = π)
        float bottomRadiusMod = radiusFunc.apply(PI);
        float[] bottomPos = add(center, scale(direction, -baseRadius * bottomRadiusMod));
        float[] bottomNormal = scale(direction, -1);
        Vertex bottomPoleV = Vertex.pos(bottomPos[0], bottomPos[1], bottomPos[2])
            .withNormal(bottomNormal[0], bottomNormal[1], bottomNormal[2]);
        if (applyWave) bottomPoleV = WaveDeformer.applyToVertex(bottomPoleV, wave, time);
        int bottomPole = builder.addVertex(bottomPoleV);
        
        // Connect top pole to first ring
        int firstRing = ringStartIndices[0];
        for (int seg = 0; seg < segments; seg++) {
            int nextSeg = (seg + 1) % segments;
            builder.triangle(topPole, firstRing + nextSeg, firstRing + seg);
        }
        
        // Connect rings (quads as triangles) with pattern and visibility
        int totalCells = (rings - 2) * segments;
        for (int ringIdx = 0; ringIdx < ringStartIndices.length - 1; ringIdx++) {
            int ringA = ringStartIndices[ringIdx];
            int ringB = ringStartIndices[ringIdx + 1];
            
            for (int seg = 0; seg < segments; seg++) {
                int nextSeg = (seg + 1) % segments;
                
                // Check visibility mask
                if (visibility != null) {
                    float latFrac = (float) (ringIdx + 1) / rings;
                    float lonFrac = (float) seg / segments;
                    if (!visibility.isVisible(lonFrac, latFrac)) {
                        continue;
                    }
                }
                
                // Check pattern
                if (pattern != null) {
                    int cellIdx = ringIdx * segments + seg;
                    if (!pattern.shouldRender(cellIdx, totalCells)) {
                        continue;
                    }
                }
                
                // Quad as two triangles
                builder.triangle(ringA + seg, ringA + nextSeg, ringB + nextSeg);
                builder.triangle(ringA + seg, ringB + nextSeg, ringB + seg);
            }
        }
        
        // Connect last ring to bottom pole
        int lastRing = ringStartIndices[ringStartIndices.length - 1];
        for (int seg = 0; seg < segments; seg++) {
            int nextSeg = (seg + 1) % segments;
            builder.triangle(lastRing + nextSeg, lastRing + seg, bottomPole);
        }
    }
    
    /**
     * Simple version without pattern/visibility/wave (for rays).
     */
    public static void generatePolarSurface(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float baseRadius,
            int rings,
            int segments,
            RadiusFunction radiusFunc) {
        generatePolarSurface(builder, center, direction, baseRadius, rings, segments, 
            radiusFunc, null, null, null, 0);
    }
    
    /**
     * Convenience: generates a sphere (uniform radius).
     */
    public static void generateSphere(MeshBuilder builder, float[] center, float radius, int rings, int segments) {
        generatePolarSurface(builder, center, new float[]{0, 1, 0}, radius, rings, segments, null);
    }
    
    /**
     * Convenience: generates a droplet shape.
     */
    public static void generateDroplet(MeshBuilder builder, float[] center, float[] direction, 
            float radius, float sharpness, int rings, int segments) {
        generatePolarSurface(builder, center, direction, radius, rings, segments,
            theta -> ShapeMath.droplet(theta, sharpness));
    }
    
    /**
     * Generates a latitude/longitude grid surface with partial sphere support.
     * 
     * <p><b>Core algorithm extracted from SphereTessellator.tessellateLatLon().</b></p>
     * 
     * <p>Supports partial spheres via lat/lon start/end parameters.</p>
     * 
     * @param builder MeshBuilder to add vertices/triangles to
     * @param radius Base radius of the sphere
     * @param latSteps Number of latitude steps
     * @param lonSteps Number of longitude steps
     * @param latStart Start latitude (0 = top, 1 = bottom)
     * @param latEnd End latitude (0 = top, 1 = bottom)
     * @param lonStart Start longitude (0 = front, 1 = wrap around)
     * @param lonEnd End longitude (0 = front, 1 = wrap around)
     * @param radiusFunc Radius modifier function (null = sphere)
     * @param pattern Vertex pattern for cell rendering
     * @param visibility Visibility mask (null = all visible)
     * @param wave Wave deformation config (null = no wave)
     * @param time Current time for wave animation
     */
    public static void generateLatLonGrid(
            MeshBuilder builder,
            float radius,
            int latSteps,
            int lonSteps,
            float latStart,
            float latEnd,
            float lonStart,
            float lonEnd,
            RadiusFunction radiusFunc,
            VertexPattern pattern,
            VisibilityMask visibility,
            WaveConfig wave,
            float time) {
        
        float latRange = latEnd - latStart;
        float lonRange = lonEnd - lonStart;
        
        // Check if wave deformation should be applied
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        // Generate vertex grid
        int[][] vertexIndices = new int[latSteps + 1][lonSteps + 1];
        
        for (int lat = 0; lat <= latSteps; lat++) {
            float latNorm = latStart + (lat / (float) latSteps) * latRange;
            float theta = latNorm * PI;  // 0 to PI (top to bottom)
            
            // Compute deformed radius at this latitude
            float deformedRadius = radius;
            if (radiusFunc != null) {
                float radiusFactor = radiusFunc.apply(theta);
                deformedRadius = radius * radiusFactor;
            }
            
            for (int lon = 0; lon <= lonSteps; lon++) {
                float lonNorm = lonStart + (lon / (float) lonSteps) * lonRange;
                float phi = lonNorm * TWO_PI;  // 0 to 2PI (around)
                
                // Use Vertex.spherical for vertex position (consistent with existing code)
                Vertex v = Vertex.spherical(theta, phi, deformedRadius);
                
                // Apply wave deformation if active
                if (applyWave) {
                    v = WaveDeformer.applyToVertex(v, wave, time);
                }
                
                vertexIndices[lat][lon] = builder.addVertex(v);
            }
        }
        
        // Generate triangles for each quad cell
        for (int lat = 0; lat < latSteps; lat++) {
            float latFrac = lat / (float) latSteps;
            
            for (int lon = 0; lon < lonSteps; lon++) {
                float lonFrac = lon / (float) lonSteps;
                
                // Check visibility mask
                if (visibility != null && !visibility.isVisible(lonFrac, latFrac)) {
                    continue;
                }
                
                // Check pattern
                if (pattern != null && !pattern.shouldRender(lat * lonSteps + lon, latSteps * lonSteps)) {
                    continue;
                }
                
                // Get quad corner indices
                int topLeft = vertexIndices[lat][lon];
                int topRight = vertexIndices[lat][lon + 1];
                int bottomLeft = vertexIndices[lat + 1][lon];
                int bottomRight = vertexIndices[lat + 1][lon + 1];
                
                // Use pattern-aware quad emission if available
                if (pattern != null) {
                    builder.quadAsTrianglesFromPattern(topLeft, topRight, bottomRight, bottomLeft, pattern);
                } else {
                    builder.triangle(topLeft, topRight, bottomRight);
                    builder.triangle(topLeft, bottomRight, bottomLeft);
                }
            }
        }
    }
    
    /**
     * Generates a latitude/longitude grid with PROPER vertex deformation.
     * 
     * <p>Uses {@link VertexFunction} to compute vertex positions directly,
     * enabling proper parametric shape deformations (droplet, cone, etc.).</p>
     * 
     * @param builder MeshBuilder to add vertices/triangles to
     * @param radius Base radius
     * @param latSteps Number of latitude steps
     * @param lonSteps Number of longitude steps
     * @param latStart Start latitude (0 = top, 1 = bottom)
     * @param latEnd End latitude
     * @param lonStart Start longitude
     * @param lonEnd End longitude  
     * @param vertexFunc Vertex position function (takes theta, phi, radius → {x,y,z})
     * @param pattern Vertex pattern for cell rendering
     * @param visibility Visibility mask (null = all visible)
     * @param wave Wave deformation config (null = no wave)
     * @param time Current time for wave animation
     */
    public static void generateLatLonGridVertex(
            MeshBuilder builder,
            float radius,
            int latSteps,
            int lonSteps,
            float latStart,
            float latEnd,
            float lonStart,
            float lonEnd,
            VertexFunction vertexFunc,
            VertexPattern pattern,
            VisibilityMask visibility,
            WaveConfig wave,
            float time) {
        
        float latRange = latEnd - latStart;
        float lonRange = lonEnd - lonStart;
        
        // Check if wave deformation should be applied
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        // Generate vertex grid
        int[][] vertexIndices = new int[latSteps + 1][lonSteps + 1];
        
        for (int lat = 0; lat <= latSteps; lat++) {
            float latNorm = latStart + (lat / (float) latSteps) * latRange;
            float theta = latNorm * PI;  // 0 to PI (top to bottom)
            
            for (int lon = 0; lon <= lonSteps; lon++) {
                float lonNorm = lonStart + (lon / (float) lonSteps) * lonRange;
                float phi = lonNorm * TWO_PI;  // 0 to 2PI (around)
                
                // Compute deformed vertex position using the vertex function
                float[] pos = vertexFunc.apply(theta, phi, radius);
                
                // Compute normal (approximate as direction from origin)
                float[] normal = normalize(pos);
                
                // Create vertex
                Vertex v = Vertex.pos(pos[0], pos[1], pos[2])
                    .withNormal(normal[0], normal[1], normal[2]);
                
                // Apply wave deformation if active
                if (applyWave) {
                    v = WaveDeformer.applyToVertex(v, wave, time);
                }
                
                vertexIndices[lat][lon] = builder.addVertex(v);
            }
        }
        
        // Generate triangles for each quad cell
        for (int lat = 0; lat < latSteps; lat++) {
            float latFrac = lat / (float) latSteps;
            
            for (int lon = 0; lon < lonSteps; lon++) {
                float lonFrac = lon / (float) lonSteps;
                
                // Check visibility mask
                if (visibility != null && !visibility.isVisible(lonFrac, latFrac)) {
                    continue;
                }
                
                // Check pattern
                if (pattern != null && !pattern.shouldRender(lat * lonSteps + lon, latSteps * lonSteps)) {
                    continue;
                }
                
                // Get quad corner indices
                int topLeft = vertexIndices[lat][lon];
                int topRight = vertexIndices[lat][lon + 1];
                int bottomLeft = vertexIndices[lat + 1][lon];
                int bottomRight = vertexIndices[lat + 1][lon + 1];
                
                // Use pattern-aware quad emission if available
                if (pattern != null) {
                    builder.quadAsTrianglesFromPattern(topLeft, topRight, bottomRight, bottomLeft, pattern);
                } else {
                    builder.triangle(topLeft, topRight, bottomRight);
                    builder.triangle(topLeft, bottomRight, bottomLeft);
                }
            }
        }
    }
    
    /**
     * Generates a lat/lon grid with arbitrary orientation (center + direction).
     * 
     * <p>For 3D ray types (droplet, etc.) that need to point in any direction.</p>
     * 
     * @param builder MeshBuilder
     * @param center Center position
     * @param direction Direction the pole points (normalized)
     * @param radius Base radius
     * @param latSteps Latitude resolution
     * @param lonSteps Longitude resolution
     * @param fullVertexFunc Returns {x,y,z,nx,ny,nz} for theta,phi,radius
     */
    public static void generateLatLonGridFullOriented(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float radius,
            int latSteps,
            int lonSteps,
            FullVertexFunction fullVertexFunc) {
        generateLatLonGridFullOriented(builder, center, direction, radius, latSteps, lonSteps, 
            fullVertexFunc, null, null);
    }
    
    /**
     * Generates a lat/lon grid with arbitrary orientation and pattern.
     */
    public static void generateLatLonGridFullOriented(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float radius,
            int latSteps,
            int lonSteps,
            FullVertexFunction fullVertexFunc,
            net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern) {
        generateLatLonGridFullOriented(builder, center, direction, radius, latSteps, lonSteps, 
            fullVertexFunc, pattern, null);
    }
    
    /**
     * Generates a lat/lon grid with arbitrary orientation, pattern, and visibility.
     */
    public static void generateLatLonGridFullOriented(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float radius,
            int latSteps,
            int lonSteps,
            FullVertexFunction fullVertexFunc,
            net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern,
            net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility) {
        
        // Build basis vectors (u, v perpendicular to direction)
        // Use right-handed coordinate system for correct winding
        float[] up = new float[] { 0, 1, 0 };
        if (Math.abs(dot(direction, up)) > 0.99f) {
            up = new float[] { 1, 0, 0 };
        }
        float[] u = normalize(cross(up, direction));  // Right-handed
        float[] v = normalize(cross(direction, u));   // Right-handed
        
        int[][] vertexIndices = new int[latSteps + 1][lonSteps + 1];
        
        for (int lat = 0; lat <= latSteps; lat++) {
            float theta = lat / (float) latSteps * PI;
            
            for (int lon = 0; lon <= lonSteps; lon++) {
                float phi = lon / (float) lonSteps * TWO_PI;
                
                // Get position + normal from vertex function (in local coords)
                float[] full = fullVertexFunc.apply(theta, phi, radius);
                float lx = full[0], ly = full[1], lz = full[2];
                float lnx = full[3], lny = full[4], lnz = full[5];
                
                // Transform local coords to world coords using basis
                // Local Y = direction, Local X = u, Local Z = v
                float[] pos = add(center, 
                    add(scale(u, lx),
                        add(scale(direction, ly),
                            scale(v, lz))));
                            
                float[] normal = add(scale(u, lnx),
                    add(scale(direction, lny),
                        scale(v, lnz)));
                normal = normalize(normal);
                
                vertexIndices[lat][lon] = builder.addVertex(
                    Vertex.pos(pos[0], pos[1], pos[2])
                        .withNormal(normal[0], normal[1], normal[2]));
            }
        }
        
        // Generate triangles with pattern and visibility
        int totalCells = latSteps * lonSteps;
        for (int lat = 0; lat < latSteps; lat++) {
            float latFrac = lat / (float) latSteps;
            
            for (int lon = 0; lon < lonSteps; lon++) {
                float lonFrac = lon / (float) lonSteps;
                
                // Check visibility mask
                if (visibility != null && !visibility.isVisible(lonFrac, latFrac)) {
                    continue;
                }
                
                // Check pattern
                if (pattern != null && !pattern.shouldRender(lat * lonSteps + lon, totalCells)) {
                    continue;
                }
                
                int topLeft = vertexIndices[lat][lon];
                int topRight = vertexIndices[lat][lon + 1];
                int bottomLeft = vertexIndices[lat + 1][lon];
                int bottomRight = vertexIndices[lat + 1][lon + 1];
                
                // Use pattern-aware quad emission if it's a QuadPattern
                if (pattern instanceof net.cyberpunk042.mcshaders.core.pattern.QuadPattern quadPattern) {
                    builder.quadAsTrianglesFromPattern(topLeft, topRight, bottomRight, bottomLeft, quadPattern);
                } else {
                    builder.triangle(topLeft, topRight, bottomRight);
                    builder.triangle(topLeft, bottomRight, bottomLeft);
                }
            }
        }
    }
    
    /**
     * Generates a lat/lon grid with arbitrary orientation, pattern, visibility, AND per-vertex alpha.
     * 
     * <p>Used for 3D ray flow animations where vertices have flow-based alpha (CLIP mode).</p>
     */
    public static void generateLatLonGridFullOrientedWithAlpha(
            MeshBuilder builder,
            float[] center,
            float[] direction,
            float radius,
            int latSteps,
            int lonSteps,
            FullVertexWithAlphaFunction fullVertexFunc,
            net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern,
            net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility) {
        
        // Build basis vectors (u, v perpendicular to direction)
        // Use right-handed coordinate system for correct winding
        float[] up = new float[] { 0, 1, 0 };
        if (Math.abs(dot(direction, up)) > 0.99f) {
            up = new float[] { 1, 0, 0 };
        }
        float[] u = normalize(cross(up, direction));  // Right-handed
        float[] v = normalize(cross(direction, u));   // Right-handed
        
        int[][] vertexIndices = new int[latSteps + 1][lonSteps + 1];
        float[][] vertexAlphas = new float[latSteps + 1][lonSteps + 1];
        
        for (int lat = 0; lat <= latSteps; lat++) {
            float theta = lat / (float) latSteps * PI;
            
            for (int lon = 0; lon <= lonSteps; lon++) {
                float phi = lon / (float) lonSteps * TWO_PI;
                
                // Get position + normal + alpha from vertex function (in local coords)
                float[] full = fullVertexFunc.apply(theta, phi, radius);
                float lx = full[0], ly = full[1], lz = full[2];
                float lnx = full[3], lny = full[4], lnz = full[5];
                float alpha = full.length > 6 ? full[6] : 1.0f;
                
                // Transform local coords to world coords using basis
                // Local Y = direction, Local X = u, Local Z = v
                float[] pos = add(center, 
                    add(scale(u, lx),
                        add(scale(direction, ly),
                            scale(v, lz))));
                            
                float[] normal = add(scale(u, lnx),
                    add(scale(direction, lny),
                        scale(v, lnz)));
                normal = normalize(normal);
                
                // Store alpha for later use in triangle emission
                vertexAlphas[lat][lon] = alpha;
                
                vertexIndices[lat][lon] = builder.addVertex(
                    Vertex.pos(pos[0], pos[1], pos[2])
                        .withNormal(normal[0], normal[1], normal[2]));
            }
        }
        
        // Generate triangles with pattern and visibility
        int totalCells = latSteps * lonSteps;
        for (int lat = 0; lat < latSteps; lat++) {
            float latFrac = lat / (float) latSteps;
            
            for (int lon = 0; lon < lonSteps; lon++) {
                float lonFrac = lon / (float) lonSteps;
                
                // Check visibility mask
                if (visibility != null && !visibility.isVisible(lonFrac, latFrac)) {
                    continue;
                }
                
                // Check pattern
                if (pattern != null && !pattern.shouldRender(lat * lonSteps + lon, totalCells)) {
                    continue;
                }
                
                // Check if all quad vertices have alpha > 0 (for CLIP mode efficiency)
                float minAlpha = Math.min(
                    Math.min(vertexAlphas[lat][lon], vertexAlphas[lat][lon + 1]),
                    Math.min(vertexAlphas[lat + 1][lon], vertexAlphas[lat + 1][lon + 1]));
                if (minAlpha <= 0.01f) {
                    // All vertices are hidden - skip this quad
                    continue;
                }
                
                int topLeft = vertexIndices[lat][lon];
                int topRight = vertexIndices[lat][lon + 1];
                int bottomLeft = vertexIndices[lat + 1][lon];
                int bottomRight = vertexIndices[lat + 1][lon + 1];
                
                // Use pattern-aware quad emission if it's a QuadPattern
                if (pattern instanceof net.cyberpunk042.mcshaders.core.pattern.QuadPattern quadPattern) {
                    builder.quadAsTrianglesFromPattern(topLeft, topRight, bottomRight, bottomLeft, quadPattern);
                } else {
                    builder.triangle(topLeft, topRight, bottomRight);
                    builder.triangle(topLeft, bottomRight, bottomLeft);
                }
            }
        }
    }
    
    /**
     * Generates a latitude/longitude grid with FULL vertex data (position + normal).
     * 
     * <p>Uses {@link FullVertexFunction} for proper spheroid normals.</p>
     */
    public static void generateLatLonGridFull(
            MeshBuilder builder,
            float radius,
            int latSteps,
            int lonSteps,
            float latStart,
            float latEnd,
            float lonStart,
            float lonEnd,
            FullVertexFunction fullVertexFunc,
            VertexPattern pattern,
            VisibilityMask visibility,
            WaveConfig wave,
            float time) {
        
        float latRange = latEnd - latStart;
        float lonRange = lonEnd - lonStart;
        
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        int[][] vertexIndices = new int[latSteps + 1][lonSteps + 1];
        
        for (int lat = 0; lat <= latSteps; lat++) {
            float latNorm = latStart + (lat / (float) latSteps) * latRange;
            float theta = latNorm * PI;
            
            for (int lon = 0; lon <= lonSteps; lon++) {
                float lonNorm = lonStart + (lon / (float) lonSteps) * lonRange;
                float phi = lonNorm * TWO_PI;
                
                // Get position + normal (6 elements: x, y, z, nx, ny, nz)
                float[] fullVertex = fullVertexFunc.apply(theta, phi, radius);
                
                Vertex v = Vertex.pos(fullVertex[0], fullVertex[1], fullVertex[2])
                    .withNormal(fullVertex[3], fullVertex[4], fullVertex[5]);
                
                if (applyWave) {
                    v = WaveDeformer.applyToVertex(v, wave, time);
                }
                
                vertexIndices[lat][lon] = builder.addVertex(v);
            }
        }
        
        // Generate triangles
        for (int lat = 0; lat < latSteps; lat++) {
            float latFrac = lat / (float) latSteps;
            
            for (int lon = 0; lon < lonSteps; lon++) {
                float lonFrac = lon / (float) lonSteps;
                
                if (visibility != null && !visibility.isVisible(lonFrac, latFrac)) {
                    continue;
                }
                
                if (pattern != null && !pattern.shouldRender(lat * lonSteps + lon, latSteps * lonSteps)) {
                    continue;
                }
                
                int topLeft = vertexIndices[lat][lon];
                int topRight = vertexIndices[lat][lon + 1];
                int bottomLeft = vertexIndices[lat + 1][lon];
                int bottomRight = vertexIndices[lat + 1][lon + 1];
                
                if (pattern != null) {
                    builder.quadAsTrianglesFromPattern(topLeft, topRight, bottomRight, bottomLeft, pattern);
                } else {
                    builder.triangle(topLeft, topRight, bottomRight);
                    builder.triangle(topLeft, bottomRight, bottomLeft);
                }
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Basic Vector Operations
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Vector addition: a + b */
    public static float[] add(float[] a, float[] b) {
        return new float[] { a[0] + b[0], a[1] + b[1], a[2] + b[2] };
    }
    
    /** Vector subtraction: a - b */
    public static float[] sub(float[] a, float[] b) {
        return new float[] { a[0] - b[0], a[1] - b[1], a[2] - b[2] };
    }
    
    /** Scalar multiplication: v * s */
    public static float[] scale(float[] v, float s) {
        return new float[] { v[0] * s, v[1] * s, v[2] * s };
    }
    
    /** Cross product: a × b */
    public static float[] cross(float[] a, float[] b) {
        return new float[] {
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }
    
    /** Dot product: a · b */
    public static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
    
    /** Vector length: |v| */
    public static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
    
    /** Normalize vector (returns new array). Returns (0,1,0) if zero-length. */
    public static float[] normalize(float[] v) {
        float len = length(v);
        if (len > 0.0001f) {
            return new float[] { v[0] / len, v[1] / len, v[2] / len };
        }
        return new float[] { 0, 1, 0 }; // Default up
    }
    
    /** Linear interpolation between two points: a + (b - a) * t */
    public static float[] lerp(float[] a, float[] b, float t) {
        return new float[] {
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t
        };
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Coordinate Frames
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Computes a stable perpendicular frame (right, up) for a given direction.
     * Useful for orienting shapes along an arbitrary axis.
     * 
     * @param direction Primary axis direction (must be normalized)
     * @param outRight Output: right vector perpendicular to direction
     * @param outUp Output: up vector perpendicular to both
     */
    public static void computePerpendicularFrame(float[] direction, float[] outRight, float[] outUp) {
        // Choose a reference vector that's not parallel to direction
        float[] ref;
        if (Math.abs(direction[1]) < 0.9f) {
            ref = new float[] { 0, 1, 0 };
        } else {
            ref = new float[] { 1, 0, 0 };
        }
        
        // right = normalize(cross(direction, ref))
        float[] right = cross(direction, ref);
        float rightLen = length(right);
        if (rightLen > 0.0001f) {
            outRight[0] = right[0] / rightLen;
            outRight[1] = right[1] / rightLen;
            outRight[2] = right[2] / rightLen;
        } else {
            outRight[0] = 1; outRight[1] = 0; outRight[2] = 0;
        }
        
        // up = cross(right, direction)
        float[] up = cross(outRight, direction);
        outUp[0] = up[0];
        outUp[1] = up[1];
        outUp[2] = up[2];
    }
    
    /**
     * Rotates a point around an axis using Rodrigues' formula.
     */
    public static float[] rotateAroundAxis(float[] point, float[] axis, float angle) {
        float cos = (float) Math.cos(angle);
        float sin = (float) Math.sin(angle);
        float oneMinusCos = 1.0f - cos;
        
        float[] kCrossV = cross(axis, point);
        float kDotV = dot(axis, point);
        
        return new float[] {
            point[0] * cos + kCrossV[0] * sin + axis[0] * kDotV * oneMinusCos,
            point[1] * cos + kCrossV[1] * sin + axis[1] * kDotV * oneMinusCos,
            point[2] * cos + kCrossV[2] * sin + axis[2] * kDotV * oneMinusCos
        };
    }
}
