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
import net.cyberpunk042.mcshaders.core.shape.CapsuleShape;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;

/**
 * Tessellates capsule shapes (cylinder with hemispherical caps) into triangle meshes.
 * 
 * <h2>Structure</h2>
 * <p>A capsule consists of three parts:</p>
 * <ul>
 *   <li>Top hemisphere (rings from pole to equator)</li>
 *   <li>Cylindrical body (if height > 2*radius)</li>
 *   <li>Bottom hemisphere (rings from equator to pole)</li>
 * </ul>
 * 
 * @see CapsuleShape
 */
public final class CapsuleTessellator {
    
    private CapsuleTessellator() {}
    
    /**
     * Tessellates a capsule shape into a mesh with optional wave deformation.
     * 
     * @param shape The capsule shape definition
     * @param pattern Optional vertex pattern (can be null)
     * @param visibility Optional visibility mask (can be null)
     * @param wave Wave configuration for CPU deformation (null = no wave)
     * @param time Current time for wave animation
     * @return Generated mesh
     */
    public static Mesh tessellate(CapsuleShape shape, VertexPattern pattern,
                                   VisibilityMask visibility,
                                   WaveConfig wave, float time) {
        if (shape == null) {
            throw new IllegalArgumentException("CapsuleShape cannot be null");
        }
        
        if (pattern == null) {
            pattern = QuadPattern.FILLED_1;
        }
        
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        Diag.RENDER.topic("tessellate")
            .kv("shape", "capsule")
            .kv("radius", shape.radius())
            .kv("height", shape.height())
            .kv("segments", shape.segments())
            .kv("rings", shape.rings())
            .kv("wave", wave != null && wave.isActive())
            .debug("Tessellating capsule");
        
        MeshBuilder builder = MeshBuilder.triangles();
        
        float radius = shape.radius();
        float totalHeight = shape.height();
        float cylinderHeight = shape.cylinderHeight();
        int segments = shape.segments();
        int rings = shape.rings();
        
        // Y positions
        float topCapCenter = cylinderHeight / 2;
        float bottomCapCenter = -cylinderHeight / 2;
        
        // =================================================================
        // Top Hemisphere
        // =================================================================
        Vertex topPoleV = new Vertex(0, topCapCenter + radius, 0, 0, 1, 0, 0.5f, 0, 1.0f);
        if (applyWave) {
            topPoleV = WaveDeformer.applyToVertex(topPoleV, wave, time);
        }
        int topPoleIdx = builder.addVertex(topPoleV);
        
        // Rings from near-pole to equator
        int[][] topHemiVerts = new int[rings][segments + 1];
        for (int ring = 0; ring < rings; ring++) {
            // theta goes from near 0 (pole) to PI/2 (equator)
            float theta = GeometryMath.HALF_PI * (ring + 1) / rings;
            float y = topCapCenter + radius * (float) Math.cos(theta);
            float ringRadius = radius * (float) Math.sin(theta);
            
            for (int seg = 0; seg <= segments; seg++) {
                float phi = GeometryMath.TWO_PI * seg / segments;
                float x = ringRadius * (float) Math.cos(phi);
                float z = ringRadius * (float) Math.sin(phi);
                
                // Normal points outward from hemisphere center
                float nx = (float) Math.sin(theta) * (float) Math.cos(phi);
                float ny = (float) Math.cos(theta);
                float nz = (float) Math.sin(theta) * (float) Math.sin(phi);
                
                float u = seg / (float) segments;
                float v = (ring + 1) / (float) (rings * 2 + (cylinderHeight > 0 ? 2 : 0));
                
                Vertex vertex = new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f);
                if (applyWave) {
                    vertex = WaveDeformer.applyToVertex(vertex, wave, time);
                }
                topHemiVerts[ring][seg] = builder.addVertex(vertex);
            }
        }
        
        // Top pole triangles
        for (int seg = 0; seg < segments; seg++) {
            float segFrac = seg / (float) segments;
            if (visibility != null && !visibility.isVisible(0f, segFrac)) continue;
            if (!pattern.shouldRender(seg, segments)) continue;
            
            // Top pole triangle - CCW when viewed from outside
            // pole → ring[seg+1] → ring[seg] for CCW
            builder.triangle(topPoleIdx, topHemiVerts[0][seg + 1], topHemiVerts[0][seg]);
        }
        
        // Top hemisphere quads
        for (int ring = 0; ring < rings - 1; ring++) {
            for (int seg = 0; seg < segments; seg++) {
                float segFrac = seg / (float) segments;
                float ringFrac = ring / (float) rings;
                if (visibility != null && !visibility.isVisible(ringFrac, segFrac)) continue;
                if (!pattern.shouldRender(ring * segments + seg, rings * segments)) continue;
                
                // Same indices: i0=TL, i1=TR, i2=BR, i3=BL
                int i0 = topHemiVerts[ring][seg];
                int i1 = topHemiVerts[ring][seg + 1];
                int i2 = topHemiVerts[ring + 1][seg + 1];
                int i3 = topHemiVerts[ring + 1][seg];
                
                // Use quadAsTrianglesFromPattern for pattern support
                // TL=i0, TR=i1, BR=i2, BL=i3
                builder.quadAsTrianglesFromPattern(i0, i1, i2, i3, pattern);
            }
        }
        
        // =================================================================
        // Cylinder Body (if present)
        // =================================================================
        int[] topRingVerts = topHemiVerts[rings - 1];  // Last ring of top hemisphere
        int[] bottomRingVerts;
        
        if (cylinderHeight > 0.001f) {
            // Create bottom ring of cylinder
            bottomRingVerts = new int[segments + 1];
            for (int seg = 0; seg <= segments; seg++) {
                float phi = GeometryMath.TWO_PI * seg / segments;
                float x = radius * (float) Math.cos(phi);
                float z = radius * (float) Math.sin(phi);
                
                float nx = (float) Math.cos(phi);
                float nz = (float) Math.sin(phi);
                
                float u = seg / (float) segments;
                float v = 0.5f;
                
                Vertex vertex = new Vertex(x, bottomCapCenter, z, nx, 0, nz, u, v, 1.0f);
                if (applyWave) {
                    vertex = WaveDeformer.applyToVertex(vertex, wave, time);
                }
                bottomRingVerts[seg] = builder.addVertex(vertex);
            }
            
            // Cylinder quads
            for (int seg = 0; seg < segments; seg++) {
                float segFrac = seg / (float) segments;
                if (visibility != null && !visibility.isVisible(0.5f, segFrac)) continue;
                if (!pattern.shouldRender(seg, segments)) continue;
                
                int i0 = topRingVerts[seg];
                int i1 = topRingVerts[seg + 1];
                int i2 = bottomRingVerts[seg + 1];
                int i3 = bottomRingVerts[seg];
                
                // Use quadAsTrianglesFromPattern for pattern support
                // TL=i0, TR=i1, BR=i2, BL=i3
                builder.quadAsTrianglesFromPattern(i0, i1, i2, i3, pattern);
            }
        } else {
            bottomRingVerts = topRingVerts;
        }
        
        // =================================================================
        // Bottom Hemisphere
        // =================================================================
        int[][] bottomHemiVerts = new int[rings][segments + 1];
        for (int ring = 0; ring < rings; ring++) {
            // theta goes from PI/2 (equator) to near PI (pole)
            float theta = GeometryMath.HALF_PI + GeometryMath.HALF_PI * (ring + 1) / rings;
            float y = bottomCapCenter + radius * (float) Math.cos(theta);
            float ringRadius = radius * (float) Math.abs(Math.sin(theta));
            
            for (int seg = 0; seg <= segments; seg++) {
                float phi = GeometryMath.TWO_PI * seg / segments;
                float x = ringRadius * (float) Math.cos(phi);
                float z = ringRadius * (float) Math.sin(phi);
                
                float nx = (float) Math.sin(theta) * (float) Math.cos(phi);
                float ny = (float) Math.cos(theta);
                float nz = (float) Math.sin(theta) * (float) Math.sin(phi);
                
                float u = seg / (float) segments;
                float v = 0.5f + (ring + 1) / (float) (rings * 2);
                
                Vertex vertex = new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f);
                if (applyWave) {
                    vertex = WaveDeformer.applyToVertex(vertex, wave, time);
                }
                bottomHemiVerts[ring][seg] = builder.addVertex(vertex);
            }
        }
        
        // Connect bottom of cylinder/top hemisphere to bottom hemisphere
        for (int seg = 0; seg < segments; seg++) {
            float segFrac = seg / (float) segments;
            if (visibility != null && !visibility.isVisible(0.5f, segFrac)) continue;
            if (!pattern.shouldRender(seg, segments)) continue;
            
            int i0 = bottomRingVerts[seg];
            int i1 = bottomRingVerts[seg + 1];
            int i2 = bottomHemiVerts[0][seg + 1];
            int i3 = bottomHemiVerts[0][seg];
            
            // Use quadAsTrianglesFromPattern for pattern support
            // TL=i0, TR=i1, BR=i2, BL=i3
            builder.quadAsTrianglesFromPattern(i0, i1, i2, i3, pattern);
        }
        
        // Bottom hemisphere quads
        for (int ring = 0; ring < rings - 1; ring++) {
            for (int seg = 0; seg < segments; seg++) {
                float segFrac = seg / (float) segments;
                float ringFrac = (ring + rings) / (float) (rings * 2);
                if (visibility != null && !visibility.isVisible(ringFrac, segFrac)) continue;
                if (!pattern.shouldRender(ring * segments + seg, rings * segments)) continue;
                
                int i0 = bottomHemiVerts[ring][seg];
                int i1 = bottomHemiVerts[ring][seg + 1];
                int i2 = bottomHemiVerts[ring + 1][seg + 1];
                int i3 = bottomHemiVerts[ring + 1][seg];
                
                // Use quadAsTrianglesFromPattern for pattern support
                // TL=i0, TR=i1, BR=i2, BL=i3
                builder.quadAsTrianglesFromPattern(i0, i1, i2, i3, pattern);
            }
        }
        
        // Bottom pole triangles
        Vertex bottomPoleV = new Vertex(0, bottomCapCenter - radius, 0, 0, -1, 0, 0.5f, 1, 1.0f);
        if (applyWave) {
            bottomPoleV = WaveDeformer.applyToVertex(bottomPoleV, wave, time);
        }
        int bottomPoleIdx = builder.addVertex(bottomPoleV);
        int[] lastBottomRing = bottomHemiVerts[rings - 1];
        
        for (int seg = 0; seg < segments; seg++) {
            float segFrac = seg / (float) segments;
            if (visibility != null && !visibility.isVisible(1f, segFrac)) continue;
            if (!pattern.shouldRender(seg, segments)) continue;
            
            // Bottom pole triangle - CCW when viewed from below
            // ring[seg] → pole → ring[seg+1] is CCW from below, so flip it
            builder.triangle(lastBottomRing[seg + 1], bottomPoleIdx, lastBottomRing[seg]);
        }
        
        Diag.RENDER.topic("tessellate")
            .kv("vertices", builder.vertexCount())
            .trace("Capsule tessellation complete");
        
        return builder.build();
    }
    
    /**
     * Tessellates a capsule shape into a mesh (backward compatible).
     */
    public static Mesh tessellate(CapsuleShape shape, VertexPattern pattern,
                                   VisibilityMask visibility) {
        return tessellate(shape, pattern, visibility, null, 0);
    }
    
    public static Mesh tessellate(CapsuleShape shape) {
        return tessellate(shape, null, null, null, 0);
    }
}


