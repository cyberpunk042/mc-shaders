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
import net.cyberpunk042.mcshaders.core.pattern.SegmentPattern;
import net.cyberpunk042.mcshaders.core.shape.OrientationAxis;
import net.cyberpunk042.mcshaders.core.shape.RingShape;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;
import org.joml.Vector3f;

/**
 * Tessellates ring shapes into triangle meshes.
 * 
 * <h2>Geometry</h2>
 * <p>A ring is a flat band with inner and outer radii, positioned at a Y height.
 * Each segment is a quad connecting inner and outer edges.</p>
 * 
 * <h2>Partial Arcs</h2>
 * <p>Supports partial arcs via arcStart/arcEnd (degrees).</p>
 * 
 * <h2>3D Rings</h2>
 * <p>When height > 0, creates a 3D ring (tube section) with:</p>
 * <ul>
 *   <li>Top ring face</li>
 *   <li>Bottom ring face</li>
 *   <li>Outer cylinder wall</li>
 *   <li>Inner cylinder wall</li>
 * </ul>
 * <p>Ring is centered at y (extends from y-height/2 to y+height/2).</p>
 * 
 * <h2>Twist</h2>
 * <p>When twist != 0, the top is rotated relative to bottom (Möbius-like effect).</p>
 * 
 * @see RingShape
 * @see SegmentPattern
 * @see GeometryMath#ringPoint
 */
public final class RingTessellator {
    
    private RingTessellator() {}
    
    /**
     * Tessellates a ring shape into a mesh with optional wave deformation.
     * 
     * @param shape The ring shape definition
     * @param pattern Vertex pattern (null = SegmentPattern.DEFAULT)
     * @param visibility Visibility mask (null = all visible)
     * @param wave Wave configuration for CPU deformation (null = no wave)
     * @param time Current time for wave animation
     * @return Generated mesh
     */
    public static Mesh tessellate(RingShape shape, VertexPattern pattern,
                                   VisibilityMask visibility,
                                   WaveConfig wave, float time) {
        if (shape == null) {
            throw new IllegalArgumentException("RingShape cannot be null");
        }
        
        // Use default pattern if none provided
        if (pattern == null) {
            pattern = SegmentPattern.DEFAULT;
        }
        
        Diag.RENDER.topic("tessellate")
            .kv("shape", "ring")
            .kv("inner", shape.innerRadius())
            .kv("outer", shape.outerRadius())
            .kv("segments", shape.segments())
            .kv("height", shape.height())
            .kv("wave", wave != null && wave.isActive())
            .debug("Tessellating ring");
        
        if (shape.height() > 0) {
            return tessellate3DRing(shape, pattern, visibility, wave, time);
        }
        return tessellateFlatRing(shape, pattern, visibility, wave, time);
    }
    
    /**
     * Tessellates a ring shape into a mesh (backward compatible).
     */
    public static Mesh tessellate(RingShape shape, VertexPattern pattern,
                                   VisibilityMask visibility) {
        return tessellate(shape, pattern, visibility, null, 0);
    }
    
    public static Mesh tessellate(RingShape shape) {
        return tessellate(shape, null, null, null, 0);
    }
    
    // =========================================================================
    // Flat Ring (height = 0)
    // =========================================================================
    
    private static Mesh tessellateFlatRing(RingShape shape, VertexPattern pattern,
                                            VisibilityMask visibility,
                                            WaveConfig wave, float time) {
        MeshBuilder builder = MeshBuilder.triangles();
        
        int segments = shape.segments();
        float innerR = shape.innerRadius();
        float outerR = shape.outerRadius();
        float y = shape.y();
        float arcStart = GeometryMath.toRadians(shape.arcStart());
        float arcEnd = GeometryMath.toRadians(shape.arcEnd());
        float arcRange = arcEnd - arcStart;
        
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        // Generate vertices for inner and outer edges
        int[] innerIndices = new int[segments + 1];
        int[] outerIndices = new int[segments + 1];
        
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float angle = arcStart + t * arcRange;
            
            Vertex inner = GeometryMath.ringInnerPoint(angle, innerR, y);
            Vertex outer = GeometryMath.ringOuterPoint(angle, outerR, y);
            
            // Apply wave deformation
            if (applyWave) {
                inner = WaveDeformer.applyToVertex(inner, wave, time);
                outer = WaveDeformer.applyToVertex(outer, wave, time);
            }
            
            innerIndices[i] = builder.addVertex(inner);
            outerIndices[i] = builder.addVertex(outer);
        }
        
        // Generate quads between inner and outer edges
        for (int i = 0; i < segments; i++) {
            float segFrac = i / (float) segments;
            
            // Check visibility
            if (visibility != null && !visibility.isVisible(0.5f, segFrac)) {
                continue;
            }
            
            // Check pattern
            if (!pattern.shouldRender(i, segments)) {
                continue;
            }
            
            // Quad viewed from above (normal pointing up):
            //   o0 (outer, angle i) ---- o1 (outer, angle i+1)
            //          |                       |
            //   i0 (inner, angle i) ---- i1 (inner, angle i+1)
            // CCW from above: i0 → i1 → o1, and i0 → o1 → o0
            int i0 = innerIndices[i];
            int i1 = innerIndices[i + 1];
            int o0 = outerIndices[i];
            int o1 = outerIndices[i + 1];
            
            // Use quadAsTrianglesFromPattern for pattern support
            // SegmentPattern expects: inner0, inner1, outer0, outer1
            // Map to quadAsTriangles params: TL=i0, TR=i1, BR=o1, BL=o0
            builder.quadAsTrianglesFromPattern(i0, i1, o1, o0, pattern);
        }
        
        return builder.build();
    }
    
    // =========================================================================
    // 3D Ring (height > 0)
    // =========================================================================
    
    /**
     * Tessellates a 3D ring (tube section) with 4 surfaces:
     * - Top face
     * - Bottom face
     * - Outer wall
     * - Inner wall
     */
    private static Mesh tessellate3DRing(RingShape shape, VertexPattern pattern,
                                          VisibilityMask visibility,
                                          WaveConfig wave, float time) {
        MeshBuilder builder = MeshBuilder.triangles();
        
        int segments = shape.segments();
        float innerR = shape.innerRadius();
        float outerR = shape.outerRadius();
        float topInnerR = shape.topInnerRadius();  // May differ from innerR if tapered
        float topOuterR = shape.topOuterRadius();  // May differ from outerR if tapered
        float height = shape.height();
        float yCenter = shape.y();
        float yBottom = yCenter - height / 2;
        float yTop = yCenter + height / 2;
        float twist = GeometryMath.toRadians(shape.twist());
        float arcStart = GeometryMath.toRadians(shape.arcStart());
        float arcEnd = GeometryMath.toRadians(shape.arcEnd());
        float arcRange = arcEnd - arcStart;
        boolean isFullRing = shape.isFullRing();
        OrientationAxis orientation = shape.effectiveOrientation();
        float originOffset = shape.originOffset();
        float bottomAlpha = shape.bottomAlpha();
        float topAlpha = shape.topAlpha();
        
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        // =====================================================================
        // Generate all vertices
        // =====================================================================
        
        // Bottom ring vertices (inner and outer)
        int[] bottomInner = new int[segments + 1];
        int[] bottomOuter = new int[segments + 1];
        
        // Top ring vertices (inner and outer) - with twist and taper applied
        int[] topInner = new int[segments + 1];
        int[] topOuter = new int[segments + 1];
        
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float baseAngle = arcStart + t * arcRange;
            float topAngle = baseAngle + twist;  // Apply twist to top
            
            // Bottom vertices (normal pointing down for bottom face)
            Vertex bInner = ringVertex(baseAngle, innerR, yBottom, 0, -1, 0, orientation, originOffset, bottomAlpha);
            Vertex bOuter = ringVertex(baseAngle, outerR, yBottom, 0, -1, 0, orientation, originOffset, bottomAlpha);
            // Top vertices with tapered radii (normal pointing up for top face)
            Vertex tInner = ringVertex(topAngle, topInnerR, yTop, 0, 1, 0, orientation, originOffset, topAlpha);
            Vertex tOuter = ringVertex(topAngle, topOuterR, yTop, 0, 1, 0, orientation, originOffset, topAlpha);
            
            // Apply wave deformation
            if (applyWave) {
                bInner = WaveDeformer.applyToVertex(bInner, wave, time);
                bOuter = WaveDeformer.applyToVertex(bOuter, wave, time);
                tInner = WaveDeformer.applyToVertex(tInner, wave, time);
                tOuter = WaveDeformer.applyToVertex(tOuter, wave, time);
            }
            
            bottomInner[i] = builder.addVertex(bInner);
            bottomOuter[i] = builder.addVertex(bOuter);
            topInner[i] = builder.addVertex(tInner);
            topOuter[i] = builder.addVertex(tOuter);
        }
        
        // =====================================================================
        // Top and Bottom Ring Faces
        // =====================================================================
        
        for (int i = 0; i < segments; i++) {
            float segFrac = i / (float) segments;
            
            if (visibility != null && !visibility.isVisible(0.5f, segFrac)) {
                continue;
            }
            if (!pattern.shouldRender(i, segments)) {
                continue;
            }
            
            // Top face - SegmentPattern expects: inner0, inner1, outer0, outer1
            // TL=tI[i], TR=tI[i+1], BR=tO[i+1], BL=tO[i]
            builder.quadAsTrianglesFromPattern(topInner[i], topInner[i + 1], topOuter[i + 1], topOuter[i], pattern);
            
            // Bottom face - REVERSED winding for below view (swap i and i+1)
            // TL=bI[i+1], TR=bI[i], BR=bO[i], BL=bO[i+1]
            builder.quadAsTrianglesFromPattern(bottomInner[i + 1], bottomInner[i], bottomOuter[i], bottomOuter[i + 1], pattern);
        }
        
        // =====================================================================
        // Outer Wall (connects outer edges of top and bottom)
        // Now with multiple height segments for smooth alpha gradient
        // =====================================================================
        
        int heightSegs = Math.max(1, shape.heightSegments());
        
        // Create a 2D array of vertex indices: [heightLevel][segment]
        int[][] outerWall = new int[heightSegs + 1][segments + 1];
        
        for (int h = 0; h <= heightSegs; h++) {
            float hT = h / (float) heightSegs;  // 0 at bottom, 1 at top
            float y = yBottom + hT * height;
            float alpha = bottomAlpha + hT * (topAlpha - bottomAlpha);  // Interpolate alpha
            float twistAtH = hT * twist;  // Interpolate twist
            float radiusAtH = outerR + hT * (topOuterR - outerR);  // Interpolate radius (taper)
            
            for (int i = 0; i <= segments; i++) {
                float t = i / (float) segments;
                float baseAngle = arcStart + t * arcRange;
                float angle = baseAngle + twistAtH;
                
                // Normal points outward (radial direction)
                float nx = (float) Math.cos(angle);
                float nz = (float) Math.sin(angle);
                Vertex v = ringVertex(angle, radiusAtH, y, nx, 0, nz, orientation, originOffset, alpha);
                
                if (applyWave) {
                    v = WaveDeformer.applyToVertex(v, wave, time);
                }
                
                outerWall[h][i] = builder.addVertex(v);
            }
        }
        
        // Generate quads between adjacent height levels
        for (int h = 0; h < heightSegs; h++) {
            for (int i = 0; i < segments; i++) {
                float segFrac = i / (float) segments;
                if (visibility != null && !visibility.isVisible(1.0f, segFrac)) continue;
                if (!pattern.shouldRender(i, segments)) continue;
                
                // Quad: top-left, top-right, bottom-right, bottom-left
                // TL = outerWall[h+1][i], TR = outerWall[h+1][i+1]
                // BL = outerWall[h][i], BR = outerWall[h][i+1]
                builder.quadAsTrianglesFromPattern(
                    outerWall[h + 1][i], outerWall[h + 1][i + 1], 
                    outerWall[h][i + 1], outerWall[h][i], pattern);
            }
        }
        
        // =====================================================================
        // Inner Wall (connects inner edges of top and bottom)
        // Now with multiple height segments for smooth alpha gradient
        // =====================================================================
        
        // Only generate inner wall if there's an inner radius (hollow ring)
        if (innerR > 0.001f) {
            // Create a 2D array of vertex indices: [heightLevel][segment]
            int[][] innerWall = new int[heightSegs + 1][segments + 1];
            
            for (int h = 0; h <= heightSegs; h++) {
                float hT = h / (float) heightSegs;  // 0 at bottom, 1 at top
                float y = yBottom + hT * height;
                float alpha = bottomAlpha + hT * (topAlpha - bottomAlpha);  // Interpolate alpha
                float twistAtH = hT * twist;  // Interpolate twist
                float radiusAtH = innerR + hT * (topInnerR - innerR);  // Interpolate radius (taper)
                
                for (int i = 0; i <= segments; i++) {
                    float t = i / (float) segments;
                    float baseAngle = arcStart + t * arcRange;
                    float angle = baseAngle + twistAtH;
                    
                    // Normal points inward (negative radial direction)
                    float nx = -(float) Math.cos(angle);
                    float nz = -(float) Math.sin(angle);
                    Vertex v = ringVertex(angle, radiusAtH, y, nx, 0, nz, orientation, originOffset, alpha);
                    
                    if (applyWave) {
                        v = WaveDeformer.applyToVertex(v, wave, time);
                    }
                    
                    innerWall[h][i] = builder.addVertex(v);
                }
            }
            
            // Generate quads between adjacent height levels
            for (int h = 0; h < heightSegs; h++) {
                for (int i = 0; i < segments; i++) {
                    float segFrac = i / (float) segments;
                    if (visibility != null && !visibility.isVisible(0.0f, segFrac)) continue;
                    if (!pattern.shouldRender(i, segments)) continue;
                    
                    // Quad with reversed winding for inner wall (normals face inward)
                    builder.quadAsTrianglesFromPattern(
                        innerWall[h + 1][i], innerWall[h + 1][i + 1], 
                        innerWall[h][i + 1], innerWall[h][i], pattern);
                }
            }
        }
        
        // =====================================================================
        // Arc End Caps (only if not a full ring)
        // =====================================================================
        
        // TEMPORARILY DISABLED FOR DEBUGGING - uncomment when fixed
        // if (!isFullRing) {
        //     // Start cap (at arcStart angle)
        //     tessellateArcEndCap(builder, arcStart, innerR, outerR, yBottom, yTop, true);
        //     
        //     // End cap (at arcEnd angle + twist)
        //     float endAngle = arcEnd + twist;
        //     tessellateArcEndCap(builder, endAngle, innerR, outerR, yBottom, yTop, false);
        // }
        
        Diag.RENDER.topic("tessellate")
            .kv("vertices", builder.vertexCount())
            .trace("3D ring tessellation complete");
        
        return builder.build();
    }
    
    /**
     * Creates a vertex at a ring position with custom normal.
     * Local space is Y-up (ring plane is XZ, height extends along Y).
     */
    private static Vertex ringVertex(float angle, float radius, float y, 
                                      float nx, float ny, float nz,
                                      float alpha) {
        return ringVertex(angle, radius, y, nx, ny, nz, null, 0, alpha);
    }
    
    /**
     * Creates a vertex at a ring position with custom normal and orientation transform.
     * Local space is Y-up (ring plane is XZ, height extends along Y).
     * If orientation is provided, transforms from local space to oriented space.
     */
    private static Vertex ringVertex(float angle, float radius, float y, 
                                      float nx, float ny, float nz,
                                      OrientationAxis orientation, float originOffset, float alpha) {
        // Local Y-up coordinates
        float localX = (float) Math.cos(angle) * radius;
        float localZ = (float) Math.sin(angle) * radius;
        float localY = y;
        
        float u = angle / GeometryMath.TWO_PI;
        float v = (ny > 0) ? 1 : 0;  // Top = 1, bottom = 0
        
        // Apply orientation transform if specified
        if (orientation != null && orientation != OrientationAxis.POS_Y) {
            Vector3f pos = orientation.transformVertex(localX, localY, localZ, originOffset);
            Vector3f norm = orientation.transformNormal(nx, ny, nz);
            return new Vertex(pos.x(), pos.y(), pos.z(), norm.x(), norm.y(), norm.z(), u, v, alpha);
        }
        
        return new Vertex(localX, localY, localZ, nx, ny, nz, u, v, alpha);
    }
    
    /**
     * Tessellates an end cap for a partial arc (rectangular face).
     * 
     * @param builder Mesh builder
     * @param angle Angle of the end cap
     * @param innerR Inner radius
     * @param outerR Outer radius
     * @param yBottom Bottom Y
     * @param yTop Top Y
     * @param isStart true for start cap (normal points backward), false for end cap
     */
    private static void tessellateArcEndCap(MeshBuilder builder, float angle,
                                             float innerR, float outerR,
                                             float yBottom, float yTop, boolean isStart) {
        // Normal perpendicular to the arc at this angle
        // For start cap: points in -tangent direction
        // For end cap: points in +tangent direction
        float tangentAngle = angle + (isStart ? -GeometryMath.HALF_PI : GeometryMath.HALF_PI);
        float nx = (float) Math.cos(tangentAngle);
        float nz = (float) Math.sin(tangentAngle);
        
        // Four corners of the rectangular cap
        float cosA = (float) Math.cos(angle);
        float sinA = (float) Math.sin(angle);
        
        Vertex innerBottom = new Vertex(cosA * innerR, yBottom, sinA * innerR, nx, 0, nz, 0, 0, 1.0f);
        Vertex outerBottom = new Vertex(cosA * outerR, yBottom, sinA * outerR, nx, 0, nz, 1, 0, 1.0f);
        Vertex innerTop = new Vertex(cosA * innerR, yTop, sinA * innerR, nx, 0, nz, 0, 1, 1.0f);
        Vertex outerTop = new Vertex(cosA * outerR, yTop, sinA * outerR, nx, 0, nz, 1, 1, 1.0f);
        
        int iIB = builder.addVertex(innerBottom);
        int iOB = builder.addVertex(outerBottom);
        int iIT = builder.addVertex(innerTop);
        int iOT = builder.addVertex(outerTop);
        
        // Two triangles for the quad
        if (isStart) {
            // Start cap faces backward along arc
            builder.triangle(iIB, iOB, iOT);
            builder.triangle(iIB, iOT, iIT);
        } else {
            // End cap faces forward along arc
            builder.triangle(iIB, iOT, iOB);
            builder.triangle(iIB, iIT, iOT);
        }
    }
}
