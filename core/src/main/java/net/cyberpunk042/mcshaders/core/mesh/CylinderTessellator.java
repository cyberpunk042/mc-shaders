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
import net.cyberpunk042.mcshaders.core.shape.CylinderShape;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;

/**
 * Tessellates cylinder shapes into triangle meshes.
 * 
 * <h2>Geometry</h2>
 * <p>A cylinder is a circular column. Consists of:</p>
 * <ul>
 *   <li>Side wall (quads)</li>
 *   <li>Top cap (disc, optional)</li>
 *   <li>Bottom cap (disc, optional)</li>
 * </ul>
 * 
 * <h2>Taper (Cone)</h2>
 * <p>When topRadius != radius, creates a cone or truncated cone.</p>
 * 
 * <h2>Partial Arc</h2>
 * <p>When arc != 360, creates a partial cylinder (like a pipe section).</p>
 * 
 * @see CylinderShape
 * @see GeometryMath#cylinderPoint
 */
public final class CylinderTessellator {
    
    private CylinderTessellator() {}
    
    /**
     * Tessellates a cylinder shape into a mesh with separate patterns for parts.
     * 
     * @param shape The cylinder shape
     * @param sidesPattern Pattern for side walls (QUAD)
     * @param capPattern Pattern for top/bottom caps (SECTOR)
     * @param visibility Visibility mask
     * @param wave Wave configuration for CPU deformation (null = no wave)
     * @param time Current time for wave animation
     */
    public static Mesh tessellate(CylinderShape shape, VertexPattern sidesPattern,
                                   VertexPattern capPattern, VisibilityMask visibility,
                                   WaveConfig wave, float time) {
        if (shape == null) {
            throw new IllegalArgumentException("CylinderShape cannot be null");
        }
        
        Diag.RENDER.topic("tessellate")
            .kv("shape", "cylinder")
            .kv("radius", shape.radius())
            .kv("height", shape.height())
            .kv("segments", shape.segments())
            .kv("wave", wave != null && wave.isActive())
            .debug("Tessellating cylinder");
        
        MeshBuilder builder = MeshBuilder.triangles();
        
        int segments = shape.segments();
        float bottomR = shape.radius();
        float topR = shape.topRadius();
        float height = shape.height();
        int heightSegs = shape.heightSegments();
        float arc = GeometryMath.toRadians(shape.arc());
        
        float yBase = -height / 2;
        float yTop = height / 2;
        
        // === SIDE WALL (uses sidesPattern) ===
        tessellateSideWall(builder, segments, bottomR, topR, height, heightSegs,
                           yBase, arc, sidesPattern, visibility, wave, time);
        
        // === CAPS (uses capPattern) ===
        if (shape.capBottom()) {
            tessellateCap(builder, segments, bottomR, yBase, false, arc, capPattern, wave, time);
        }
        if (shape.capTop()) {
            tessellateCap(builder, segments, topR, yTop, true, arc, capPattern, wave, time);
        }
        
        return builder.build();
    }
    
    /**
     * Tessellates with separate patterns (backward compatible).
     */
    public static Mesh tessellate(CylinderShape shape, VertexPattern sidesPattern,
                                   VertexPattern capPattern, VisibilityMask visibility) {
        return tessellate(shape, sidesPattern, capPattern, visibility, null, 0);
    }
    
    /**
     * Tessellates with single pattern for all parts (legacy compatibility).
     */
    public static Mesh tessellate(CylinderShape shape, VertexPattern pattern,
                                   VisibilityMask visibility) {
        return tessellate(shape, pattern, pattern, visibility, null, 0);
    }
    
    public static Mesh tessellate(CylinderShape shape) {
        return tessellate(shape, null, null, null, null, 0);
    }
    
    // =========================================================================
    // Side Wall
    // =========================================================================
    
    private static void tessellateSideWall(MeshBuilder builder, int segments,
                                            float bottomR, float topR, float height,
                                            int heightSegs, float yBase, float arc,
                                            VertexPattern pattern, VisibilityMask visibility,
                                            WaveConfig wave, float time) {
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        for (int h = 0; h < heightSegs; h++) {
            float t0 = h / (float) heightSegs;
            float t1 = (h + 1) / (float) heightSegs;
            
            for (int s = 0; s < segments; s++) {
                float segFrac = s / (float) segments;
                
                if (visibility != null && !visibility.isVisible(t0, segFrac)) {
                    continue;
                }
                
                if (pattern != null && !pattern.shouldRender(h * segments + s, heightSegs * segments)) {
                    continue;
                }
                
                float angle0 = (s / (float) segments) * arc;
                float angle1 = ((s + 1) / (float) segments) * arc;
                
                // Four corners
                Vertex v00 = GeometryMath.cylinderTaperedPoint(angle0, t0, bottomR, topR, height, yBase);
                Vertex v10 = GeometryMath.cylinderTaperedPoint(angle1, t0, bottomR, topR, height, yBase);
                Vertex v01 = GeometryMath.cylinderTaperedPoint(angle0, t1, bottomR, topR, height, yBase);
                Vertex v11 = GeometryMath.cylinderTaperedPoint(angle1, t1, bottomR, topR, height, yBase);
                
                // Apply wave deformation
                if (applyWave) {
                    v00 = WaveDeformer.applyToVertex(v00, wave, time);
                    v10 = WaveDeformer.applyToVertex(v10, wave, time);
                    v01 = WaveDeformer.applyToVertex(v01, wave, time);
                    v11 = WaveDeformer.applyToVertex(v11, wave, time);
                }
                
                int i00 = builder.addVertex(v00);
                int i10 = builder.addVertex(v10);
                int i01 = builder.addVertex(v01);
                int i11 = builder.addVertex(v11);
                
                // When viewed from OUTSIDE the cylinder, angle1 (s+1) is on LEFT, angle0 (s) is on RIGHT
                // (because angles go CCW when viewed from above)
                // So: TL=i11 (angle1,top), TR=i01 (angle0,top), BR=i00 (angle0,bot), BL=i10 (angle1,bot)
                builder.quadAsTrianglesFromPattern(i11, i01, i00, i10, pattern);
            }
        }
    }
    
    // =========================================================================
    // Caps
    // =========================================================================
    
    private static void tessellateCap(MeshBuilder builder, int segments, 
                                       float radius, float y, boolean isTop, float arc,
                                       VertexPattern pattern,
                                       WaveConfig wave, float time) {
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        float ny = isTop ? 1 : -1;
        
        // Center vertex
        Vertex center = GeometryMath.discCenter(y);
        center = new Vertex(center.x(), center.y(), center.z(), 0, ny, 0, 0.5f, 0.5f, 1.0f);
        if (applyWave) {
            center = WaveDeformer.applyToVertex(center, wave, time);
        }
        int centerIdx = builder.addVertex(center);
        
        // Edge vertices
        int[] edgeIndices = new int[segments + 1];
        for (int s = 0; s <= segments; s++) {
            float angle = (s / (float) segments) * arc;
            Vertex v = GeometryMath.discPoint(angle, radius, y);
            v = new Vertex(v.x(), v.y(), v.z(), 0, ny, 0, v.u(), v.v(), 1.0f);
            if (applyWave) {
                v = WaveDeformer.applyToVertex(v, wave, time);
            }
            edgeIndices[s] = builder.addVertex(v);
        }
        
        // Emit triangles
        for (int s = 0; s < segments; s++) {
            if (pattern != null && !pattern.shouldRender(s, segments)) {
                continue;
            }
            
            int[] vertices;
            if (isTop) {
                // Top: s+1, s
                vertices = new int[] { centerIdx, edgeIndices[s + 1], edgeIndices[s] };
            } else {
                // Bottom: s+1, s (same as top)
                vertices = new int[] { centerIdx, edgeIndices[s + 1], edgeIndices[s] };
            }
            builder.emitCellFromPattern(vertices, pattern);
        }
    }
}
