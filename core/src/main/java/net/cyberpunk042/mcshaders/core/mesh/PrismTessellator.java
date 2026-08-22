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
import net.cyberpunk042.mcshaders.core.shape.PrismShape;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;

/**
 * Tessellates prism shapes into triangle meshes.
 * 
 * <h2>Geometry</h2>
 * <p>A prism is an extruded N-sided polygon. Consists of:</p>
 * <ul>
 *   <li>N side faces (quads)</li>
 *   <li>Top cap (N-gon, optional)</li>
 *   <li>Bottom cap (N-gon, optional)</li>
 * </ul>
 * 
 * <h2>Twist</h2>
 * <p>When twist != 0, the top is rotated relative to bottom.</p>
 * 
 * <h2>Taper</h2>
 * <p>When topRadius != radius, creates a pyramid-like shape.</p>
 * 
 * @see PrismShape
 * @see GeometryMath#prismCorner
 */
public final class PrismTessellator {
    
    private PrismTessellator() {}
    
    /**
     * Tessellates a prism shape into a mesh with separate patterns for parts.
     * 
     * @param shape The prism shape
     * @param sidesPattern Pattern for side walls (QUAD)
     * @param capPattern Pattern for top/bottom caps (SECTOR)
     * @param visibility Visibility mask
     * @param wave Wave configuration for CPU deformation (null = no wave)
     * @param time Current time for wave animation
     */
    public static Mesh tessellate(PrismShape shape, VertexPattern sidesPattern,
                                   VertexPattern capPattern, VisibilityMask visibility,
                                   WaveConfig wave, float time) {
        if (shape == null) {
            throw new IllegalArgumentException("PrismShape cannot be null");
        }
        
        Diag.RENDER.topic("tessellate")
            .kv("shape", "prism")
            .kv("sides", shape.sides())
            .kv("radius", shape.radius())
            .kv("height", shape.height())
            .kv("wave", wave != null && wave.isActive())
            .debug("Tessellating prism");
        if (shape == null) {
            throw new IllegalArgumentException("PrismShape cannot be null");
        }
        
        Diag.RENDER.topic("tessellate")
            .kv("shape", "prism")
            .kv("sides", shape.sides())
            .kv("radius", shape.radius())
            .kv("height", shape.height())
            .debug("Tessellating prism");
        
        MeshBuilder builder = MeshBuilder.triangles();
        
        int sides = shape.sides();
        float bottomR = shape.radius();
        float topR = shape.topRadius();
        float height = shape.height();
        float twist = GeometryMath.toRadians(shape.twist());
        int heightSegs = shape.heightSegments();
        
        float yBase = -height / 2;
        float yTop = height / 2;
        
        // === SIDE FACES (uses sidesPattern) ===
        tessellateSides(builder, sides, bottomR, topR, height, twist, heightSegs, 
                        yBase, sidesPattern, visibility, wave, time);
        
        // === CAPS (uses capPattern) ===
        if (shape.capBottom()) {
            tessellateCap(builder, sides, bottomR, yBase, false, twist, height, yBase, capPattern, wave, time);
        }
        if (shape.capTop()) {
            tessellateCap(builder, sides, topR, yTop, true, twist, height, yBase, capPattern, wave, time);
        }
        
        return builder.build();
    }
    
    /**
     * Tessellates with separate patterns (backward compatible).
     */
    public static Mesh tessellate(PrismShape shape, VertexPattern sidesPattern,
                                   VertexPattern capPattern, VisibilityMask visibility) {
        return tessellate(shape, sidesPattern, capPattern, visibility, null, 0);
    }
    
    /**
     * Tessellates with single pattern for all parts (legacy compatibility).
     */
    public static Mesh tessellate(PrismShape shape, VertexPattern pattern,
                                   VisibilityMask visibility) {
        return tessellate(shape, pattern, pattern, visibility, null, 0);
    }
    
    public static Mesh tessellate(PrismShape shape) {
        return tessellate(shape, null, null, null, null, 0);
    }
    
    // =========================================================================
    // Side Faces
    // =========================================================================
    
    private static void tessellateSides(MeshBuilder builder, int sides, 
                                         float bottomR, float topR, float height,
                                         float twist, int heightSegs,
                                         float yBase, VertexPattern pattern,
                                         VisibilityMask visibility,
                                         WaveConfig wave, float time) {
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        // For each height segment
        for (int h = 0; h < heightSegs; h++) {
            float t0 = h / (float) heightSegs;
            float t1 = (h + 1) / (float) heightSegs;
            float y0 = yBase + t0 * height;
            float y1 = yBase + t1 * height;
            float r0 = GeometryMath.lerp(bottomR, topR, t0);
            float r1 = GeometryMath.lerp(bottomR, topR, t1);
            
            // For each side
            for (int s = 0; s < sides; s++) {
                float sideFrac = s / (float) sides;
                
                if (visibility != null && !visibility.isVisible(t0, sideFrac)) {
                    continue;
                }
                
                if (pattern != null && !pattern.shouldRender(h * sides + s, heightSegs * sides)) {
                    continue;
                }
                
                // Four corners of this quad
                Vertex v00 = GeometryMath.prismCorner(s, sides, y0, r0, twist, height, yBase);
                Vertex v10 = GeometryMath.prismCorner(s + 1, sides, y0, r0, twist, height, yBase);
                Vertex v01 = GeometryMath.prismCorner(s, sides, y1, r1, twist, height, yBase);
                Vertex v11 = GeometryMath.prismCorner(s + 1, sides, y1, r1, twist, height, yBase);
                
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
                
                // Indices: i00=BL(bot,s), i10=BR(bot,s+1), i01=TL(top,s), i11=TR(top,s+1)
                // TL=i01, TR=i11, BR=i10, BL=i00
                builder.quadAsTrianglesFromPattern(i01, i11, i10, i00, pattern);
            }
        }
    }
    
    // =========================================================================
    // Caps (Top/Bottom)
    // =========================================================================
    
    private static void tessellateCap(MeshBuilder builder, int sides, float radius,
                                       float y, boolean isTop, float twist, 
                                       float height, float yBase, VertexPattern pattern,
                                       WaveConfig wave, float time) {
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        float ny = isTop ? 1 : -1;
        
        // Center vertex
        Vertex center = GeometryMath.prismFaceCenter(sides, y, radius);
        center = new Vertex(center.x(), center.y(), center.z(), 0, ny, 0, 0.5f, 0.5f, 1.0f);
        if (applyWave) {
            center = WaveDeformer.applyToVertex(center, wave, time);
        }
        int centerIdx = builder.addVertex(center);
        
        // Edge vertices
        int[] edgeIndices = new int[sides];
        for (int s = 0; s < sides; s++) {
            Vertex v = GeometryMath.prismCorner(s, sides, y, radius, twist, height, yBase);
            v = new Vertex(v.x(), v.y(), v.z(), 0, ny, 0, v.u(), v.v(), 1.0f);
            if (applyWave) {
                v = WaveDeformer.applyToVertex(v, wave, time);
            }
            edgeIndices[s] = builder.addVertex(v);
        }
        
        // Emit triangles with correct winding for front-face visibility
        // OpenGL uses CCW as front-face by default
        // Top cap: viewed from +Y (looking down) - center → next → s is CCW from above
        // Bottom cap: viewed from -Y (looking up) - center → s → next is CCW from below
        for (int s = 0; s < sides; s++) {
            if (pattern != null && !pattern.shouldRender(s, sides)) {
                continue;
            }
            
            int next = (s + 1) % sides;
            
            int[] vertices;
            if (isTop) {
                // Top cap: next, s (matching cylinder's s+1, s)
                vertices = new int[] { centerIdx, edgeIndices[next], edgeIndices[s] };
            } else {
                // Bottom cap: reversed winding (next, s) for visibility from below
                vertices = new int[] { centerIdx, edgeIndices[next], edgeIndices[s] };
            }
            builder.emitCellFromPattern(vertices, pattern);
        }
    }
}
