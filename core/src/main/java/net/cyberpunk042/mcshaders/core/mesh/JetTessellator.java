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
import net.cyberpunk042.mcshaders.core.pattern.QuadPattern;
import net.cyberpunk042.mcshaders.core.shape.JetShape;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;


/**
 * Tessellates jet shapes into triangle meshes.
 * 
 * <h2>Geometry</h2>
 * <p>A jet consists of one or two opposing cones/tubes emanating from a center
 * point. Each jet segment is a cone frustum (truncated cone) or cylinder.</p>
 * 
 * <h2>Hollow Jets</h2>
 * <p>When hollow=true, creates inner walls similar to RingTessellator's 
 * inner/outer wall approach. Caps become rings instead of discs.</p>
 * 
 * <h2>Parts</h2>
 * <ul>
 *   <li>outerWall - External surface of the cone/tube</li>
 *   <li>innerWall - Internal surface if hollow</li>
 *   <li>capBase - Cap at the base (where jet meets center)</li>
 *   <li>capTip - Cap at the tip (open or closed)</li>
 * </ul>
 * 
 * @see JetShape
 * @see GeometryMath
 */
public final class JetTessellator {
    
    private JetTessellator() {}
    
    /**
     * Tessellates a jet shape into a mesh with optional wave deformation.
     * 
     * @param shape The jet shape definition
     * @param pattern Vertex pattern for walls (QUAD)
     * @param capPattern Pattern for caps (SECTOR)
     * @param visibility Visibility mask
     * @param wave Wave configuration for CPU deformation
     * @param time Current time for wave animation
     * @return Generated mesh
     */
    public static Mesh tessellate(JetShape shape, VertexPattern pattern,
                                   VertexPattern capPattern, VisibilityMask visibility,
                                   WaveConfig wave, float time) {
        if (shape == null) {
            throw new IllegalArgumentException("JetShape cannot be null");
        }
        
        if (pattern == null) pattern = QuadPattern.DEFAULT;
        if (capPattern == null) capPattern = pattern;
        
        Diag.RENDER.topic("tessellate")
            .kv("shape", "jet")
            .kv("length", shape.length())
            .kv("baseR", shape.baseRadius())
            .kv("topTipR", shape.topTipRadius())
            .kv("bottomTipR", shape.bottomTipRadius())
            .kv("dualJets", shape.dualJets())
            .kv("hollow", shape.hollow())
            .debug("Tessellating jet");
        
        MeshBuilder builder = MeshBuilder.triangles();
        boolean applyWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        float halfGap = shape.gap() / 2;
        
        // Tessellate top jet (upward, +Y direction)
        tessellateJetSegment(builder, shape, true, halfGap, pattern, capPattern,
            visibility, wave, time, applyWave);
        
        // Tessellate bottom jet if dual
        if (shape.dualJets()) {
            tessellateJetSegment(builder, shape, false, halfGap, pattern, capPattern,
                visibility, wave, time, applyWave);
        }
        
        Diag.RENDER.topic("tessellate")
            .kv("vertices", builder.vertexCount())
            .trace("Jet tessellation complete");
        
        return builder.build();
    }
    
    /**
     * Tessellates a single jet segment (upward or downward).
     * 
     * @param builder Mesh builder
     * @param shape Jet shape config
     * @param isTop true for upward jet (+Y), false for downward (-Y)
     * @param halfGap Half the gap distance from center
     * @param pattern Wall pattern
     * @param capPattern Cap pattern
     * @param visibility Visibility mask
     * @param wave Wave config
     * @param time Current time
     * @param applyWave Whether to apply wave deformation
     */
    private static void tessellateJetSegment(MeshBuilder builder, JetShape shape,
                                              boolean isTop, float halfGap,
                                              VertexPattern pattern, VertexPattern capPattern,
                                              VisibilityMask visibility,
                                              WaveConfig wave, float time, boolean applyWave) {
        int segments = shape.segments();
        int lengthSegs = shape.lengthSegments();
        float length = shape.length();
        float baseR = shape.baseRadius();
        float tipR = isTop ? shape.topTipRadius() : shape.bottomTipRadius();
        
        // Alpha gradient: base to tip
        float baseAlpha = shape.baseAlpha();
        float tipAlpha = shape.tipAlpha();
        
        // Y coordinates: base at gap edge, tip at base + length
        float yBase = isTop ? halfGap : -halfGap;
        float yTip = isTop ? (halfGap + length) : -(halfGap + length);
        
        // Direction multiplier for normals
        float dir = isTop ? 1.0f : -1.0f;
        
        // =====================================================================
        // Outer Wall
        // =====================================================================
        tessellateConeFrustum(builder, segments, lengthSegs,
            baseR, tipR, yBase, yTip, dir,
            true, // isOuter
            baseAlpha, tipAlpha,
            pattern, visibility, wave, time, applyWave);
        
        // =====================================================================
        // Inner Wall (if hollow)
        // =====================================================================
        if (shape.hollow()) {
            float innerBaseR, innerTipR;
            
            if (shape.unifiedInner()) {
                // Calculate inner radii from wall thickness
                float thickness = shape.innerWallThickness();
                innerBaseR = Math.max(0.001f, baseR - thickness);
                innerTipR = Math.max(0.001f, tipR - thickness);
            } else {
                // Use custom inner radii
                innerBaseR = shape.innerBaseRadius();
                innerTipR = shape.innerTipRadius();
            }
            
            if (innerBaseR > 0.001f || innerTipR > 0.001f) {
                tessellateConeFrustum(builder, segments, lengthSegs,
                    innerBaseR, innerTipR, yBase, yTip, dir,
                    false, // inner wall (normals point inward)
                    baseAlpha, tipAlpha,
                    pattern, visibility, wave, time, applyWave);
            }
        }
        
        // =====================================================================
        // Base Cap (at yBase) - Ring tessellation (disc is ring with innerR ≈ 0)
        // =====================================================================
        if (shape.capBase()) {
            float innerR;
            if (shape.hollow()) {
                if (shape.unifiedInner()) {
                    innerR = Math.max(0.001f, baseR - shape.innerWallThickness());
                } else {
                    innerR = shape.innerBaseRadius() > 0 ? shape.innerBaseRadius() : 0.001f;
                }
            } else {
                innerR = 0.001f;  // Tiny inner for solid disc
            }
            tessellateRingCap(builder, segments, innerR, baseR,
                yBase, !isTop, // bottom cap for top jet points down
                capPattern, visibility, wave, time, applyWave);
        }
        
        // =====================================================================
        // Tip Cap (at yTip) - only if tip radius > 0
        // =====================================================================
        if (shape.capTip() && tipR > 0) {
            float innerR;
            if (shape.hollow()) {
                if (shape.unifiedInner()) {
                    innerR = Math.max(0.001f, tipR - shape.innerWallThickness());
                } else {
                    innerR = shape.innerTipRadius() > 0 ? shape.innerTipRadius() : 0.001f;
                }
            } else {
                innerR = 0.001f;  // Tiny inner for solid disc
            }
            tessellateRingCap(builder, segments, innerR, tipR,
                yTip, isTop, // tip cap for top jet points up
                capPattern, visibility, wave, time, applyWave);
        }
    }
    
    /**
     * Tessellates a cone frustum (truncated cone) or cylinder wall.
     * This creates quads running from base to tip with proper normals.
     */
    private static void tessellateConeFrustum(MeshBuilder builder,
                                               int segments, int lengthSegs,
                                               float baseR, float tipR,
                                               float yBase, float yTip, float dir,
                                               boolean isOuter,
                                               float baseAlpha, float tipAlpha,
                                               VertexPattern pattern, VisibilityMask visibility,
                                               WaveConfig wave, float time, boolean applyWave) {
        
        // Calculate slope for normal calculation
        float height = Math.abs(yTip - yBase);
        float radiusDiff = baseR - tipR;
        
        // Normal vector components for the cone surface
        // For a cone, the normal is tilted based on the slope
        float normalY = radiusDiff / height;  // Vertical component based on taper
        float normalScale = (float) Math.sqrt(1.0 / (1.0 + normalY * normalY));
        
        // Generate vertices for each ring along the length
        int[][] vertexIndices = new int[lengthSegs + 1][segments + 1];
        
        for (int h = 0; h <= lengthSegs; h++) {
            float t = h / (float) lengthSegs;
            float y = yBase + (yTip - yBase) * t;
            float r = baseR + (tipR - baseR) * t;
            
            // Interpolate alpha from base to tip
            float alpha = baseAlpha + (tipAlpha - baseAlpha) * t;
            
            for (int s = 0; s <= segments; s++) {
                float angle = (s / (float) segments) * GeometryMath.TWO_PI;
                float cos = (float) Math.cos(angle);
                float sin = (float) Math.sin(angle);
                
                float x = cos * r;
                float z = sin * r;
                
                // Normal for cone surface
                // Outer: points away from axis
                // Inner: points toward axis
                float nMult = isOuter ? 1.0f : -1.0f;
                float nx = cos * normalScale * nMult;
                float nz = sin * normalScale * nMult;
                float ny = normalY * normalScale * nMult * dir;
                
                // UV coordinates
                float u = s / (float) segments;
                float v = t;
                
                Vertex vert = new Vertex(x, y, z, nx, ny, nz, u, v, alpha);
                if (applyWave) {
                    vert = WaveDeformer.applyToVertex(vert, wave, time);
                }
                vertexIndices[h][s] = builder.addVertex(vert);
            }
        }
        
        // Generate quads between rings
        for (int h = 0; h < lengthSegs; h++) {
            for (int s = 0; s < segments; s++) {
                float segFrac = s / (float) segments;
                float heightFrac = h / (float) lengthSegs;
                
                if (visibility != null && !visibility.isVisible(heightFrac, segFrac)) {
                    continue;
                }
                if (!pattern.shouldRender(s, segments)) {
                    continue;
                }
                
                // Quad vertices
                int bl = vertexIndices[h][s];
                int br = vertexIndices[h][s + 1];
                int tr = vertexIndices[h + 1][s + 1];
                int tl = vertexIndices[h + 1][s];
                
                // Winding order depends on direction and inner/outer
                if (isOuter) {
                    // Outer wall: CCW from outside
                    // For upward jet: tl→tr→br, tl→br→bl
                    // For downward jet: same pattern (normals handle direction)
                    builder.quadAsTrianglesFromPattern(tl, tr, br, bl, pattern);
                } else {
                    // Inner wall: reversed winding (CW from outside = CCW from inside)
                    builder.quadAsTrianglesFromPattern(tl, bl, br, tr, pattern);
                }
            }
        }
    }
    
    /**
     * Tessellates a ring cap using Ring's EXACT 3D ring face tessellation.
     * 
     * Copied from RingTessellator lines 237-253:
     * - Top face:    quadAsTrianglesFromPattern(topInner[i], topInner[i + 1], topOuter[i + 1], topOuter[i], pattern)
     * - Bottom face: quadAsTrianglesFromPattern(bottomInner[i + 1], bottomInner[i], bottomOuter[i], bottomOuter[i + 1], pattern)
     */
    private static void tessellateRingCap(MeshBuilder builder,
                                           int segments, float innerR, float outerR,
                                           float y, boolean faceUp,
                                           VertexPattern pattern, VisibilityMask visibility,
                                           WaveConfig wave, float time, boolean applyWave) {
        // Ring's arc is full circle for jet caps
        float arcStart = 0f;
        float arcRange = GeometryMath.TWO_PI;
        float ny = faceUp ? 1.0f : -1.0f;
        
        // Create vertex arrays (Ring pattern from line 200-205)
        int[] inner = new int[segments + 1];
        int[] outer = new int[segments + 1];
        
        // Generate vertices (Ring pattern from line 207-231)
        for (int i = 0; i <= segments; i++) {
            float t = i / (float) segments;
            float angle = arcStart + t * arcRange;
            
            // Create vertices with ringVertex pattern (Ring line 362-368)
            Vertex vInner = ringVertex(angle, innerR, y, 0, ny, 0);
            Vertex vOuter = ringVertex(angle, outerR, y, 0, ny, 0);
            
            // Apply wave deformation (Ring line 219-225)
            if (applyWave) {
                vInner = WaveDeformer.applyToVertex(vInner, wave, time);
                vOuter = WaveDeformer.applyToVertex(vOuter, wave, time);
            }
            
            inner[i] = builder.addVertex(vInner);
            outer[i] = builder.addVertex(vOuter);
        }
        
        // Generate quads (Ring pattern from line 237-254)
        for (int i = 0; i < segments; i++) {
            float segFrac = i / (float) segments;
            
            // Visibility check (Ring line 240-242)
            if (visibility != null && !visibility.isVisible(0.5f, segFrac)) {
                continue;
            }
            // Pattern check (Ring line 243-245)
            if (!pattern.shouldRender(i, segments)) {
                continue;
            }
            
            if (faceUp) {
                // Top face - Ring line 249 EXACTLY:
                // builder.quadAsTrianglesFromPattern(topInner[i], topInner[i + 1], topOuter[i + 1], topOuter[i], pattern);
                builder.quadAsTrianglesFromPattern(inner[i], inner[i + 1], outer[i + 1], outer[i], pattern);
            } else {
                // Bottom face - Ring line 253 EXACTLY:
                // builder.quadAsTrianglesFromPattern(bottomInner[i + 1], bottomInner[i], bottomOuter[i], bottomOuter[i + 1], pattern);
                builder.quadAsTrianglesFromPattern(inner[i + 1], inner[i], outer[i], outer[i + 1], pattern);
            }
        }
    }
    
    /**
     * Creates a vertex at a ring position with custom normal.
     * Copied from RingTessellator line 362-368.
     */
    private static Vertex ringVertex(float angle, float radius, float y, 
                                      float nx, float ny, float nz) {
        float x = (float) Math.cos(angle) * radius;
        float z = (float) Math.sin(angle) * radius;
        float u = angle / GeometryMath.TWO_PI;
        float v = (ny > 0) ? 1 : 0;  // Top = 1, bottom = 0
        return new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f);
    }
    
    // =========================================================================
    // Convenience Overloads
    // =========================================================================
    
    public static Mesh tessellate(JetShape shape, VertexPattern pattern,
                                   VisibilityMask visibility) {
        return tessellate(shape, pattern, pattern, visibility, null, 0);
    }
    
    public static Mesh tessellate(JetShape shape, VertexPattern pattern) {
        return tessellate(shape, pattern, pattern, null, null, 0);
    }
    
    public static Mesh tessellate(JetShape shape) {
        return tessellate(shape, null, null, null, null, 0);
    }
}
