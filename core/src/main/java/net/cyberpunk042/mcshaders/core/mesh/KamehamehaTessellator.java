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

import net.cyberpunk042.mcshaders.core.pattern.QuadPattern;
import net.cyberpunk042.mcshaders.core.pattern.VertexPattern;
import net.cyberpunk042.mcshaders.core.shape.KamehamehaShape;
import net.cyberpunk042.mcshaders.core.shape.OrientationAxis;
import net.cyberpunk042.mcshaders.core.shape.RingShape;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;
import org.joml.Vector3f;

/**
 * Tessellates Kamehameha shapes into triangle meshes.
 * 
 * <h2>Components</h2>
 * <ul>
 *   <li><b>Orb</b> - Spherical energy core at origin</li>
 *   <li><b>Beam</b> - Tapered tube using Ring tessellation</li>
 *   <li><b>BeamTip</b> - End cap of beam (dome or flat)</li>
 * </ul>
 * 
 * @see KamehamehaShape
 */
public final class KamehamehaTessellator {
    
    private KamehamehaTessellator() {}
    
    // =========================================================================
    // Main Tessellation Entry Point
    // =========================================================================
    
    /**
     * Tessellates a Kamehameha shape into a complete mesh.
     */
    public static Mesh tessellate(KamehamehaShape shape, VertexPattern pattern,
                                   VisibilityMask visibility, float time) {
        MeshBuilder builder = MeshBuilder.triangles();
        
        if (pattern == null) pattern = QuadPattern.DEFAULT;
        
        // Tessellate orb components
        if (shape.isOrbVisible()) {
            tessellateOrb(builder, shape, time, pattern);
        }
        
        // Tessellate beam components
        if (shape.isBeamVisible()) {
            tessellateBeam(builder, shape, time, pattern);
            tessellateBeamTip(builder, shape, time, pattern);
        }
        
        return builder.build();
    }
    
    // =========================================================================
    // Orb Tessellation
    // =========================================================================
    
    private static void tessellateOrb(MeshBuilder builder, KamehamehaShape shape, float time, VertexPattern pattern) {
        float radius = shape.effectiveOrbRadius();
        int segments = shape.orbSegments();
        float alpha = shape.effectiveOrbAlpha();  // Use effective alpha with transition/progress
        OrientationAxis axis = shape.orientationAxis() != null ? shape.orientationAxis() : OrientationAxis.POS_Z;
        float offset = shape.originOffset();
        tessellateSphere(builder, radius, segments, segments / 2, time, alpha, axis, offset, pattern);
    }
    
    // =========================================================================
    // Beam Tessellation
    // =========================================================================
    
    private static void tessellateBeam(MeshBuilder builder, KamehamehaShape shape, float time, VertexPattern pattern) {
        float baseRadius = shape.effectiveBeamBaseRadius();
        float tipRadius = shape.effectiveBeamTipRadius();
        float length = shape.effectiveBeamLength();
        int segments = shape.beamSegments();
        OrientationAxis axis = shape.orientationAxis() != null ? shape.orientationAxis() : OrientationAxis.POS_Z;
        float beamStart = 0f;  // Start at origin - beam passes through orb center
        float offset = shape.originOffset();
        
        // Calculate taper ratio (tip radius / base radius)
        float taper = baseRadius > 0.001f ? tipRadius / baseRadius : 1.0f;
        
        // Create a Ring shape for the beam with proper orientation
        // The ring's Y axis will be transformed to the beam's orientation axis
        // y = beamStart positions the ring's center along the beam axis
        RingShape beamRing = RingShape.builder()
            .innerRadius(0)  // Solid beam (could be > 0 for hollow)
            .outerRadius(baseRadius)
            .segments(segments)
            .heightSegments(shape.beamLengthSegments())  // Segments along the length for alpha gradient
            .height(length)
            .y(beamStart + length / 2)  // Center of beam along axis
            .taper(taper)
            .twist(shape.beamTwist())    // Spiral twist
            .orientation(axis)
            .originOffset(offset)
            .bottomAlpha(shape.effectiveBeamBaseAlpha())  // Effective alpha at beam base
            .topAlpha(shape.effectiveBeamTipAlpha())      // Effective alpha at beam tip
            .build();
        
        // Tessellate and merge - RingTessellator now handles orientation internally
        Mesh beamMesh = RingTessellator.tessellate(beamRing, pattern, null);
        builder.mergeMesh(beamMesh);
    }
    
    private static void tessellateBeamTip(MeshBuilder builder, KamehamehaShape shape, float time, VertexPattern pattern) {
        float tipRadius = shape.effectiveBeamTipRadius();
        float tipStart = shape.effectiveBeamLength();  // Tip at end of beam (beam starts at 0)
        int segments = shape.beamSegments();
        float tipAlpha = shape.effectiveBeamTipAlpha();  // Use effective alpha
        OrientationAxis axis = shape.orientationAxis() != null ? shape.orientationAxis() : OrientationAxis.POS_Z;
        float offset = shape.originOffset();
        
        if (shape.hasDomeTip()) {
            // Dome (hemisphere) tip
            tessellateHemisphere(builder, tipStart, tipRadius, segments, true, tipAlpha, axis, offset, pattern);
        } else {
            // Flat cap
            tessellateFlatCap(builder, tipStart, tipRadius, segments, true, tipAlpha, axis, offset, pattern);
        }
    }
    
    // =========================================================================
    // Primitive Geometry Helpers (with Orientation Transform)
    // =========================================================================
    
    /**
     * Tessellates a sphere centered at origin, transformed by orientation axis and offset.
     * 
     * @param builder Mesh builder
     * @param radius Sphere radius
     * @param lonSteps Longitude steps
     * @param latSteps Latitude steps
     * @param time Animation time
     * @param alpha Vertex alpha
     * @param axis Orientation axis for transform
     * @param offset Offset along beam axis
     */
    private static void tessellateSphere(MeshBuilder builder, float radius,
                                          int lonSteps, int latSteps,
                                          float time, float alpha,
                                          OrientationAxis axis, float offset, VertexPattern pattern) {
        // Distortion removed for now - can be re-added with wave animation
        
        // Create vertex grid in local space (Y-up), then transform
        int[][] indices = new int[latSteps + 1][lonSteps + 1];
        
        for (int lat = 0; lat <= latSteps; lat++) {
            float theta = (float) Math.PI * lat / latSteps;
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            
            for (int lon = 0; lon <= lonSteps; lon++) {
                float phi = (float) (2 * Math.PI * lon / lonSteps);
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);
                
                float r = radius;
                
                // Local coordinates (Y-up sphere)
                float localX = r * sinTheta * cosPhi;
                float localY = r * cosTheta;  // This becomes beam axis
                float localZ = r * sinTheta * sinPhi;
                
                // Local normal
                float lnx = sinTheta * cosPhi;
                float lny = cosTheta;
                float lnz = sinTheta * sinPhi;
                
                // Transform to world space using orientation
                Vector3f pos = axis.transformVertex(localX, localY, localZ, offset);
                Vector3f norm = axis.transformNormal(lnx, lny, lnz);
                
                float u = (float) lon / lonSteps;
                float v = (float) lat / latSteps;
                
                indices[lat][lon] = builder.addVertex(new Vertex(
                    pos.x, pos.y, pos.z, norm.x, norm.y, norm.z, u, v, alpha));
            }
        }
        
        // Generate quads with pattern support
        for (int lat = 0; lat < latSteps; lat++) {
            for (int lon = 0; lon < lonSteps; lon++) {
                int i00 = indices[lat][lon];
                int i10 = indices[lat + 1][lon];
                int i11 = indices[lat + 1][lon + 1];
                int i01 = indices[lat][lon + 1];
                
                // Quad with pattern: TL=i00, TR=i01, BR=i11, BL=i10
                builder.quadAsTrianglesFromPattern(i00, i01, i11, i10, pattern);
            }
        }
    }
    
    private static void tessellateHemisphere(MeshBuilder builder,
                                              float basePos, float radius, int segments, boolean facingUp, float alpha,
                                              OrientationAxis axis, float offset, VertexPattern pattern) {
        int latSteps = segments / 2;
        
        int[][] indices = new int[latSteps + 1][segments + 1];
        
        for (int lat = 0; lat <= latSteps; lat++) {
            float theta = (float) Math.PI * lat / (latSteps * 2);
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            
            // Use constant alpha for the hemisphere (no artificial fade)
            float vertAlpha = alpha;
            
            for (int lon = 0; lon <= segments; lon++) {
                float phi = (float) (2 * Math.PI * lon / segments);
                float sinPhi = (float) Math.sin(phi);
                float cosPhi = (float) Math.cos(phi);
                
                // Local coordinates
                float localY = facingUp ? basePos + radius * cosTheta : basePos - radius * cosTheta;
                float localX = radius * sinTheta * cosPhi;
                float localZ = radius * sinTheta * sinPhi;
                
                float lny = facingUp ? cosTheta : -cosTheta;
                float lnx = sinTheta * cosPhi;
                float lnz = sinTheta * sinPhi;
                
                Vector3f pos = axis.transformVertex(localX, localY, localZ, offset);
                Vector3f norm = axis.transformNormal(lnx, lny, lnz);
                
                indices[lat][lon] = builder.addVertex(new Vertex(pos.x, pos.y, pos.z,
                    norm.x, norm.y, norm.z, (float)lon / segments, (float)lat / latSteps, vertAlpha));
            }
        }
        
        // Generate quads with pattern support - use same winding as sphere
        for (int lat = 0; lat < latSteps; lat++) {
            for (int lon = 0; lon < segments; lon++) {
                int i00 = indices[lat][lon];
                int i01 = indices[lat][lon + 1];
                int i10 = indices[lat + 1][lon];
                int i11 = indices[lat + 1][lon + 1];
                
                // EXACT same winding as sphere: TL=i00, TR=i01, BR=i11, BL=i10
                builder.quadAsTrianglesFromPattern(i00, i01, i11, i10, pattern);
            }
        }
    }
    
    private static void tessellateFlatCap(MeshBuilder builder,
                                           float capPos, float radius, int segments, boolean facingUp, float alpha,
                                           OrientationAxis axis, float offset, VertexPattern pattern) {
        float lny = facingUp ? 1.0f : -1.0f;
        
        Vector3f centerPos = axis.transformVertex(0, capPos, 0, offset);
        Vector3f centerNorm = axis.transformNormal(0, lny, 0);
        int centerIdx = builder.addVertex(new Vertex(centerPos.x, centerPos.y, centerPos.z,
            centerNorm.x, centerNorm.y, centerNorm.z, 0.5f, 0.5f, alpha));
        
        int[] edgeIndices = new int[segments + 1];
        for (int s = 0; s <= segments; s++) {
            float angle = (float) (2 * Math.PI * s / segments);
            float localX = (float)Math.cos(angle) * radius;
            float localZ = (float)Math.sin(angle) * radius;
            
            Vector3f pos = axis.transformVertex(localX, capPos, localZ, offset);
            Vector3f norm = axis.transformNormal(0, lny, 0);
            
            edgeIndices[s] = builder.addVertex(new Vertex(pos.x, pos.y, pos.z,
                norm.x, norm.y, norm.z, (float)s / segments, 0, alpha));
        }
        
        // Fan pattern - triangles from center (pattern doesn't apply cleanly)
        for (int s = 0; s < segments; s++) {
            if (facingUp) {
                builder.triangle(centerIdx, edgeIndices[s], edgeIndices[s + 1]);
            } else {
                builder.triangle(centerIdx, edgeIndices[s + 1], edgeIndices[s]);
            }
        }
    }
    
    // =========================================================================
    // Convenience Overloads
    // =========================================================================
    
    public static Mesh tessellate(KamehamehaShape shape, VertexPattern pattern,
                                   VisibilityMask visibility) {
        return tessellate(shape, pattern, visibility, 0);
    }
    
    public static Mesh tessellate(KamehamehaShape shape, VertexPattern pattern) {
        return tessellate(shape, pattern, VisibilityMask.FULL, 0);
    }
    
    public static Mesh tessellate(KamehamehaShape shape) {
        return tessellate(shape, QuadPattern.DEFAULT, VisibilityMask.FULL, 0);
    }
}
