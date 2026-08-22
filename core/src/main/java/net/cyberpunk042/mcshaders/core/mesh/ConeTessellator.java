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
import net.cyberpunk042.mcshaders.core.pattern.QuadPattern;
import net.cyberpunk042.mcshaders.core.pattern.VertexPattern;
import net.cyberpunk042.mcshaders.core.shape.ConeShape;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;

/**
 * Tessellates cone shapes into triangle meshes.
 * 
 * <h2>Structure</h2>
 * <p>Supports both true cones (pointed top) and frustums (flat top).</p>
 * <ul>
 *   <li>True cone: triangles from base to apex</li>
 *   <li>Frustum: quads from bottom circle to top circle</li>
 * </ul>
 * 
 * @see ConeShape
 */
public final class ConeTessellator {
    
    private ConeTessellator() {}
    
    /**
     * Tessellates a cone shape into a mesh with optional wave deformation.
     * 
     * @param shape The cone shape definition
     * @param pattern Optional vertex pattern (can be null)
     * @param visibility Optional visibility mask (can be null)
     * @param wave Wave configuration for CPU deformation (null = no wave)
     * @param time Current time for wave animation
     * @return Generated mesh
     */
    public static Mesh tessellate(ConeShape shape, VertexPattern pattern,
                                   VisibilityMask visibility,
                                   WaveConfig wave, float time) {
        if (shape == null) {
            throw new IllegalArgumentException("ConeShape cannot be null");
        }
        
        if (pattern == null) {
            pattern = QuadPattern.FILLED_1;
        }
        
        Diag.RENDER.topic("tessellate")
            .kv("shape", "cone")
            .kv("bottomR", shape.bottomRadius())
            .kv("topR", shape.topRadius())
            .kv("height", shape.height())
            .kv("segments", shape.segments())
            .kv("wave", wave != null && wave.isActive())
            .debug("Tessellating cone");
        
        MeshBuilder builder = MeshBuilder.triangles();
        
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        float bottomR = shape.bottomRadius();
        float topR = shape.topRadius();
        float height = shape.height();
        int segments = shape.segments();
        boolean isPointed = shape.isPointed();
        
        float halfHeight = height / 2;
        float bottomY = -halfHeight;
        float topY = halfHeight;
        
        // Calculate slant for normals
        float slantAngle = (float) Math.atan2(bottomR - topR, height);
        float normalY = (float) Math.cos(slantAngle);
        float normalXZScale = (float) Math.sin(slantAngle);
        
        // =================================================================
        // Generate vertices
        // =================================================================
        
        // Bottom ring vertices
        int[] bottomVerts = new int[segments + 1];
        for (int seg = 0; seg <= segments; seg++) {
            float phi = GeometryMath.TWO_PI * seg / segments;
            float x = bottomR * (float) Math.cos(phi);
            float z = bottomR * (float) Math.sin(phi);
            
            // Normal points outward along slant
            float nx = normalXZScale * (float) Math.cos(phi);
            float ny = normalY;
            float nz = normalXZScale * (float) Math.sin(phi);
            
            float u = seg / (float) segments;
            Vertex v = new Vertex(x, bottomY, z, nx, ny, nz, u, 1, 1.0f);
            if (applyWave) {
                v = WaveDeformer.applyToVertex(v, wave, time);
            }
            bottomVerts[seg] = builder.addVertex(v);
        }
        
        if (isPointed) {
            // =============================================================
            // True Cone - triangles to apex
            // =============================================================
            Vertex apexV = new Vertex(0, topY, 0, 0, 1, 0, 0.5f, 0, 1.0f);
            if (applyWave) {
                apexV = WaveDeformer.applyToVertex(apexV, wave, time);
            }
            int apexIdx = builder.addVertex(apexV);
            
            for (int seg = 0; seg < segments; seg++) {
                float segFrac = seg / (float) segments;
                if (visibility != null && !visibility.isVisible(0.5f, segFrac)) continue;
                if (!pattern.shouldRender(seg, segments)) continue;
                
                // Triangle from bottom edge to apex - CCW when viewed from outside
                // bottom[seg+1] → apex → bottom[seg] for CCW
                builder.triangle(bottomVerts[seg + 1], apexIdx, bottomVerts[seg]);
            }
        } else {
            // =============================================================
            // Frustum - quads to top ring
            // =============================================================
            int[] topVerts = new int[segments + 1];
            for (int seg = 0; seg <= segments; seg++) {
                float phi = GeometryMath.TWO_PI * seg / segments;
                float x = topR * (float) Math.cos(phi);
                float z = topR * (float) Math.sin(phi);
                
                float nx = normalXZScale * (float) Math.cos(phi);
                float ny = normalY;
                float nz = normalXZScale * (float) Math.sin(phi);
                
                float u = seg / (float) segments;
                Vertex v = new Vertex(x, topY, z, nx, ny, nz, u, 0, 1.0f);
                if (applyWave) {
                    v = WaveDeformer.applyToVertex(v, wave, time);
                }
                topVerts[seg] = builder.addVertex(v);
            }
            
            // Side quads
            for (int seg = 0; seg < segments; seg++) {
                float segFrac = seg / (float) segments;
                if (visibility != null && !visibility.isVisible(0.5f, segFrac)) continue;
                if (!pattern.shouldRender(seg, segments)) continue;
                
                int b0 = bottomVerts[seg];
                int b1 = bottomVerts[seg + 1];
                int t0 = topVerts[seg];
                int t1 = topVerts[seg + 1];
                
                // Side quad with pattern support
                // TL=t0, TR=t1, BR=b1, BL=b0
                builder.quadAsTrianglesFromPattern(t0, t1, b1, b0, pattern);
            }
            
            // Top cap (if not open)
            if (!shape.openTop()) {
                Vertex topCenterV = new Vertex(0, topY, 0, 0, 1, 0, 0.5f, 0.5f, 1.0f);
                if (applyWave) {
                    topCenterV = WaveDeformer.applyToVertex(topCenterV, wave, time);
                }
                int topCenterIdx = builder.addVertex(topCenterV);
                for (int seg = 0; seg < segments; seg++) {
                    float segFrac = seg / (float) segments;
                    if (visibility != null && !visibility.isVisible(0f, segFrac)) continue;
                    if (!pattern.shouldRender(seg, segments)) continue;
                    
                    // Top cap - CCW when viewed from above
                    // center → edge[seg+1] → edge[seg] for CCW (so edge[seg] is on the right)
                    builder.triangle(topCenterIdx, topVerts[seg + 1], topVerts[seg]);
                }
            }
        }
        
        // =================================================================
        // Bottom cap (if not open)
        // =================================================================
        if (!shape.openBottom()) {
            Vertex bottomCenterV = new Vertex(0, bottomY, 0, 0, -1, 0, 0.5f, 0.5f, 1.0f);
            if (applyWave) {
                bottomCenterV = WaveDeformer.applyToVertex(bottomCenterV, wave, time);
            }
            int bottomCenterIdx = builder.addVertex(bottomCenterV);
            for (int seg = 0; seg < segments; seg++) {
                float segFrac = seg / (float) segments;
                if (visibility != null && !visibility.isVisible(1f, segFrac)) continue;
                if (!pattern.shouldRender(seg, segments)) continue;
                
                // Reverse winding for bottom face
                builder.triangle(bottomVerts[seg + 1], bottomCenterIdx, bottomVerts[seg]);
            }
        }
        
        Diag.RENDER.topic("tessellate")
            .kv("vertices", builder.vertexCount())
            .trace("Cone tessellation complete");
        
        return builder.build();
    }
    
    /**
     * Tessellates a cone shape into a mesh (backward compatible).
     */
    public static Mesh tessellate(ConeShape shape, VertexPattern pattern,
                                   VisibilityMask visibility) {
        return tessellate(shape, pattern, visibility, null, 0);
    }
    
    public static Mesh tessellate(ConeShape shape) {
        return tessellate(shape, null, null, null, 0);
    }
}


