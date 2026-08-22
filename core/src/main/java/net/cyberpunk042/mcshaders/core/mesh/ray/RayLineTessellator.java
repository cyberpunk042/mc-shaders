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
package net.cyberpunk042.mcshaders.core.mesh.ray;

import net.cyberpunk042.mcshaders.core.mesh.MeshBuilder;
import net.cyberpunk042.mcshaders.core.mesh.ray.tessellation.TessEdgeModeFactory;
import net.cyberpunk042.mcshaders.core.mesh.ray.tessellation.TessEdgeResult;
import net.cyberpunk042.mcshaders.core.shape.RayCurvature;
import net.cyberpunk042.mcshaders.core.shape.RayLineShape;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;

/**
 * Tessellates LINE type rays as flat ribbon/line geometry.
 * 
 * <p>This is the default ray tessellator, producing the classic line-segment
 * appearance. Supports:
 * <ul>
 *   <li>Simple line (2 vertices)</li>
 *   <li>Segmented/dashed lines</li>
 *   <li>Shaped lines (wavy, corkscrew, etc.)</li>
 *   <li>Combined (segmented + shaped)</li>
 * </ul>
 * 
 * @see RayTypeTessellator
 * @see RayContext
 * @see RayGeometryUtils
 */
public class RayLineTessellator implements RayTypeTessellator {
    
    public static final RayLineTessellator INSTANCE = new RayLineTessellator();
    
    @Override
    public void tessellate(MeshBuilder builder, RaysShape shape, RayContext context) {
        // Determine which tessellation path to use
        boolean isSegmented = shape.isSegmented();
        boolean needsMultiSegment = context.needsMultiSegment();
        
        if (isSegmented && needsMultiSegment) {
            // Combined: dashed + curved path
            tessellateDashedCurvedLine(builder, shape, context);
        } else if (isSegmented) {
            // Simple dashed line
            tessellateDashedLine(builder, shape, context, shape.segments(), shape.segmentGap());
        } else if (needsMultiSegment) {
            // Curved/wavy line (multi-segment for curvature/wave)
            tessellateCurvedLine(builder, shape, context);
        } else {
            // Simple straight line (2 vertices)
            tessellateSimpleLine(builder, shape, context);
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Simple Line
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Tessellates a simple 2-vertex line.
     * 
     * <p>Uses RadiativeInteraction + EdgeMode for animation:
     * <ul>
     *   <li>RadiativeInteraction: WHERE the segment is positioned</li>
     *   <li>EdgeMode CLIP: Geometric clipping at edges</li>
     *   <li>EdgeMode FADE: Alpha modulation at edges</li>
     *   <li>EdgeMode SCALE: Width scaling at edges</li>
     * </ul>
     */
    private void tessellateSimpleLine(MeshBuilder builder, RaysShape shape, RayContext context) {
        // === COMPUTE PHASE-BASED POSITION ===
        // Lines should work like 3D shapes: phase determines WHERE the segment is positioned
        // along the ray axis, not which portion of the full ray is visible.
        
        // Get radiative mode and phase
        var radiativeMode = shape != null ? shape.effectiveRadiativeInteraction() 
            : net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE;
        float userPhase = context.effectiveShapeState().phase();
        
        // Get segment length as a FRACTION of the trajectory
        // segmentLength = rayLength / trajectorySpan
        // This ensures the visible ray segment has length = rayLength
        float trajectorySpan = shape != null ? shape.trajectorySpan() : 1.0f;
        float rayLength = shape != null ? shape.effectiveRayLength() : 1.0f;
        float segmentLength = trajectorySpan > 0 ? Math.min(1.0f, rayLength / trajectorySpan) : 1.0f;
        boolean startFullLength = shape != null && shape.startFullLength();
        
        // Compute clip range from RadiativeInteraction (determines segment position)
        var clipRange = net.cyberpunk042.mcshaders.core.mesh.ray.tessellation.RadiativeInteractionFactory.compute(
            radiativeMode, userPhase, segmentLength, startFullLength);
        
        // Apply EdgeMode effects
        var edgeMode = context.effectiveShapeState().edgeMode();
        float edgeIntensity = context.effectiveShapeState().edgeIntensity();
        TessEdgeResult edgeResult = TessEdgeModeFactory.compute(edgeMode, clipRange, edgeIntensity);
        
        // Skip if completely hidden
        if (!edgeResult.isVisible()) {
            return;
        }
        
        float[] start = context.start();
        float[] end = context.end();
        float[] dir = context.direction();
        
        // === COMPUTE VISIBLE SEGMENT POSITION ===
        // The segment is positioned based on the clip range from RadiativeInteraction
        float segmentStart = clipRange.start();
        float segmentEnd = clipRange.end();
        
        // For SCALE mode: shrink the segment LENGTH based on edge proximity
        // The scale factor (0-1) determines how long the segment is
        float scale = edgeResult.scale();
        if (scale < 0.999f) {
            // Shrink from center
            float center = (segmentStart + segmentEnd) * 0.5f;
            float halfLength = (segmentEnd - segmentStart) * 0.5f * scale;
            segmentStart = center - halfLength;
            segmentEnd = center + halfLength;
        }
        
        // Clamp to ray bounds [0, 1]
        float tStart = Math.max(0f, segmentStart);
        float tEnd = Math.min(1f, segmentEnd);
        
        // Skip if segment is completely outside ray bounds or too small
        if (tStart >= tEnd) {
            return;
        }
        
        // Get base alpha from edge mode
        float baseAlpha = edgeResult.alpha();
        
        // Compute actual start/end positions by interpolating along the ray
        float[] actualStart = RayGeometryUtils.interpolate(start, end, tStart);
        float[] actualEnd = RayGeometryUtils.interpolate(start, end, tEnd);
        
        // For FADE mode, compute per-vertex alpha based on position along the ray
        // tStart/tEnd are positions along the ray (0=inner, 1=outer)
        // When near trajectory edges (phase ~0 or ~1), vertices fade based on edge distances
        float alphaStart = edgeResult.computeFadeAlpha(tStart, baseAlpha);
        float alphaEnd = edgeResult.computeFadeAlpha(tEnd, baseAlpha);
        
        // u = t value for shader, alpha from EdgeMode
        int v0 = builder.vertex(actualStart[0], actualStart[1], actualStart[2], dir[0], dir[1], dir[2], tStart, 0, alphaStart);
        int v1 = builder.vertex(actualEnd[0], actualEnd[1], actualEnd[2], dir[0], dir[1], dir[2], tEnd, 0, alphaEnd);
        builder.line(v0, v1);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Dashed Line (segmented into multiple short segments with gaps)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Tessellates a dashed line (multiple short segments with gaps).
     * <p>Uses RadiativeInteraction + EdgeMode for animation.
     * <p>Phase controls segment positioning along the ray axis.
     * <p>NOTE: This is only called when needsMultiSegment() is FALSE,
     * meaning no curvature/lineShape/wave. Curved dashed goes to tessellateDashedCurvedLine.
     */
    private void tessellateDashedLine(MeshBuilder builder, RaysShape shape, RayContext context, 
                                        int dashCount, float dashGap) {
        // === COMPUTE PHASE-BASED POSITION ===
        
        // Get radiative mode and phase
        var radiativeMode = shape != null ? shape.effectiveRadiativeInteraction() 
            : net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE;
        float userPhase = context.effectiveShapeState().phase();
        
        // Get segment length as a FRACTION of the trajectory
        float trajectorySpan = shape != null ? shape.trajectorySpan() : 1.0f;
        float rayLength = shape != null ? shape.effectiveRayLength() : 1.0f;
        float patternLength = trajectorySpan > 0 ? Math.min(1.0f, rayLength / trajectorySpan) : 1.0f;
        boolean startFullLength = shape != null && shape.startFullLength();
        
        // Compute clip range from RadiativeInteraction (determines pattern position)
        var clipRange = net.cyberpunk042.mcshaders.core.mesh.ray.tessellation.RadiativeInteractionFactory.compute(
            radiativeMode, userPhase, patternLength, startFullLength);
        
        // Apply EdgeMode effects
        var edgeMode = context.effectiveShapeState().edgeMode();
        float edgeIntensity = context.effectiveShapeState().edgeIntensity();
        TessEdgeResult edgeResult = TessEdgeModeFactory.compute(edgeMode, clipRange, edgeIntensity);
        
        // Skip if completely hidden
        if (!edgeResult.isVisible()) {
            return;
        }
        
        float[] start = context.start();
        float[] end = context.end();
        float[] dir = context.direction();
        float baseAlpha = edgeResult.alpha();
        
        // === POSITION THE DASHED PATTERN ALONG THE RAY ===
        float patternCenter = (clipRange.start() + clipRange.end()) * 0.5f;
        float halfPattern = patternLength * 0.5f;
        float patternStart = patternCenter - halfPattern;
        
        // Calculate dash and gap proportions within the pattern
        float totalParts = dashCount + (dashCount - 1) * dashGap;
        float dashLength = patternLength / totalParts;
        float gapLength = dashLength * dashGap;
        
        float t = patternStart;
        for (int dash = 0; dash < dashCount; dash++) {
            float dashStart = t;
            float dashEnd = t + dashLength;
            
            // Clamp to ray bounds [0, 1]
            float tStart = Math.max(0f, dashStart);
            float tEnd = Math.min(1f, dashEnd);
            
            // Skip if dash is completely outside ray bounds
            if (tStart >= tEnd) {
                t = dashEnd + gapLength;
                continue;
            }
            
            // Compute normalized position for each dash vertex (for fade gradient)
            float tNormStart = patternLength > 0 ? (tStart - patternStart) / patternLength : 0f;
            float tNormEnd = patternLength > 0 ? (tEnd - patternStart) / patternLength : 1f;
            
            float alphaStart = edgeResult.computeFadeAlpha(tNormStart, baseAlpha);
            float alphaEnd = edgeResult.computeFadeAlpha(tNormEnd, baseAlpha);
            
            // Simple straight line segment (no curvature/shape)
            float[] segStart = RayGeometryUtils.interpolate(start, end, tStart);
            float[] segEnd = RayGeometryUtils.interpolate(start, end, tEnd);
            
            int v0 = builder.vertex(segStart[0], segStart[1], segStart[2], dir[0], dir[1], dir[2], tStart, 0, alphaStart);
            int v1 = builder.vertex(segEnd[0], segEnd[1], segEnd[2], dir[0], dir[1], dir[2], tEnd, 0, alphaEnd);
            builder.line(v0, v1);
            
            t = dashEnd + gapLength;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Curved Line (curvature, wiggle, wave deformation)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Tessellates a curved/wavy line (curvature, wiggle, wave effects).
     * <p>Phase controls segment positioning along the ray axis.
     */
    private void tessellateCurvedLine(MeshBuilder builder, RaysShape shape, RayContext context) {
        // === COMPUTE PHASE-BASED POSITION ===
        // Phase determines WHERE the curved segment is positioned along the ray axis
        
        // Get radiative mode and phase
        var radiativeMode = shape != null ? shape.effectiveRadiativeInteraction() 
            : net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE;
        float userPhase = context.effectiveShapeState().phase();
        
        // Get segment length as a FRACTION of the trajectory
        float trajectorySpan = shape != null ? shape.trajectorySpan() : 1.0f;
        float rayLength = shape != null ? shape.effectiveRayLength() : 1.0f;
        float segmentLength = trajectorySpan > 0 ? Math.min(1.0f, rayLength / trajectorySpan) : 1.0f;
        boolean startFullLength = shape != null && shape.startFullLength();
        
        // Compute clip range from RadiativeInteraction (determines segment position)
        var clipRange = net.cyberpunk042.mcshaders.core.mesh.ray.tessellation.RadiativeInteractionFactory.compute(
            radiativeMode, userPhase, segmentLength, startFullLength);
        
        // Apply EdgeMode effects
        var edgeMode = context.effectiveShapeState().edgeMode();
        float edgeIntensity = context.effectiveShapeState().edgeIntensity();
        TessEdgeResult edgeResult = TessEdgeModeFactory.compute(edgeMode, clipRange, edgeIntensity);
        
        // Skip if completely hidden
        if (!edgeResult.isVisible()) {
            return;
        }
        
        float[] start = context.start();
        float[] end = context.end();
        float[] dir = context.direction();
        int tessSegments = context.lineResolution();
        
        if (context.length() < 0.0001f) {
            builder.vertex(start[0], start[1], start[2], 0, 0, 0, 0.0f, 0, edgeResult.alpha());
            return;
        }
        
        // Get base alpha from edge mode (for per-vertex fade calculation)
        float baseAlpha = edgeResult.alpha();
        
        // === POSITION THE CURVED SEGMENT ALONG THE RAY ===
        float segmentCenter = (clipRange.start() + clipRange.end()) * 0.5f;
        float halfLength = segmentLength * 0.5f;
        
        // Segment bounds (clamped to ray bounds [0, 1])
        float segStart = Math.max(0f, segmentCenter - halfLength);
        float segEnd = Math.min(1f, segmentCenter + halfLength);
        
        // Skip if segment is completely outside ray bounds
        if (segStart >= segEnd) {
            return;
        }
        
        // Skip if degenerate
        if (context.length() < 0.0001f) {
            builder.vertex(start[0], start[1], start[2], 0, 0, 0, 0.0f, 0, baseAlpha);
            return;
        }
        
        // Compute perpendicular frame using shared utility
        float[] right = new float[3];
        float[] up = new float[3];
        RayGeometryUtils.computePerpendicularFrame(dir[0], dir[1], dir[2], right, up);
        
        RayLineShape lineShape = context.lineShape();
        float shapeAmplitude = context.lineShapeAmplitude();
        float shapeFrequency = context.lineShapeFrequency();
        float curvatureIntensity = context.curvatureIntensity();
        
        // DOUBLE_HELIX: emit TWO intertwined helixes
        int helixCount = (lineShape == RayLineShape.DOUBLE_HELIX) ? 2 : 1;
        
        for (int helix = 0; helix < helixCount; helix++) {
            float phaseOffset = helix * (float) Math.PI;
            
            int[] vertexIndices = new int[tessSegments + 1];
            
            for (int i = 0; i <= tessSegments; i++) {
                // Map tessellation index to the segment range [segStart, segEnd]
                float tLocal = (float) i / tessSegments;
                float t = segStart + tLocal * (segEnd - segStart);
                
                // Apply curvature using shared utility
                float[] curvedPos = RayGeometryUtils.computeCurvedPosition(
                    start, end, t, context.curvature(), curvatureIntensity);
                float px = curvedPos[0];
                float py = curvedPos[1];
                float pz = curvedPos[2];
                
                // Apply line shape offset using shared utility
                float[] offset = RayGeometryUtils.computeLineShapeOffsetWithPhase(
                    lineShape, t, shapeAmplitude, shapeFrequency, right, up, phaseOffset);
                px += offset[0];
                py += offset[1];
                pz += offset[2];
                
                // Apply wave deformation if active
                if (context.hasWave() && context.wave() != null) {
                    float[] deformed = net.cyberpunk042.mcshaders.core.animation.WaveDeformer.applyLinear(
                        px, py, pz, context.wave(), context.time()
                    );
                    px = deformed[0];
                    py = deformed[1];
                    pz = deformed[2];
                }
                
                // Apply field deformation if active
                if (context.fieldDeformation().isActive()) {
                    float innerR = context.innerRadius();
                    float outerR = context.outerRadius();
                    float dist = (float) Math.sqrt(px * px + py * py + pz * pz);
                    
                    if (dist > 0.001f && outerR > innerR) {
                        float normalizedDist = Math.clamp((dist - innerR) / (outerR - innerR), 0.01f, 1f);
                        float stretch = context.fieldDeformation().computeStretch(
                            normalizedDist, context.fieldDeformationIntensity());
                        float scaleFactor = (dist * stretch) / dist;
                        px *= scaleFactor;
                        py *= scaleFactor;
                        pz *= scaleFactor;
                    }
                }
                
                // Compute per-vertex alpha using edge proximity fade
                float vertexAlpha = edgeResult.computeFadeAlpha(tLocal, baseAlpha);
                
                // Create vertex with per-vertex alpha from edge mode
                vertexIndices[i] = builder.vertex(px, py, pz, dir[0], dir[1], dir[2], t, 0, vertexAlpha);
            }
            
            // Connect with lines
            for (int i = 0; i < tessSegments; i++) {
                builder.line(vertexIndices[i], vertexIndices[i + 1]);
            }
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Dashed + Curved Line (combined: dashed segments along curved path)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Tessellates a dashed line that ALSO follows a curved/wavy path.
     * Phase controls segment positioning along the ray axis.
     */
    private void tessellateDashedCurvedLine(MeshBuilder builder, RaysShape shape, RayContext context) {
        // === COMPUTE PHASE-BASED POSITION ===
        // Phase determines WHERE the dashed+curved pattern is positioned along the ray axis
        
        // Get radiative mode and phase
        var radiativeMode = shape != null ? shape.effectiveRadiativeInteraction() 
            : net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE;
        float userPhase = context.effectiveShapeState().phase();
        
        // Get segment length as a FRACTION of the trajectory
        float trajectorySpan = shape != null ? shape.trajectorySpan() : 1.0f;
        float rayLength = shape != null ? shape.effectiveRayLength() : 1.0f;
        float patternLength = trajectorySpan > 0 ? Math.min(1.0f, rayLength / trajectorySpan) : 1.0f;
        boolean startFullLength = shape != null && shape.startFullLength();
        
        // Compute clip range from RadiativeInteraction (determines pattern position)
        var clipRange = net.cyberpunk042.mcshaders.core.mesh.ray.tessellation.RadiativeInteractionFactory.compute(
            radiativeMode, userPhase, patternLength, startFullLength);
        
        // Apply EdgeMode effects
        var edgeMode = context.effectiveShapeState().edgeMode();
        float edgeIntensity = context.effectiveShapeState().edgeIntensity();
        TessEdgeResult edgeResult = TessEdgeModeFactory.compute(edgeMode, clipRange, edgeIntensity);
        
        if (!edgeResult.isVisible()) {
            return;
        }
        
        float[] start = context.start();
        float[] end = context.end();
        float[] dir = context.direction();
        int dashCount = shape.segments();
        float dashGap = shape.segmentGap();
        
        // Get base alpha from edge mode (for per-vertex fade)
        float baseAlpha = edgeResult.alpha();
        
        // === POSITION THE DASHED PATTERN ALONG THE RAY ===
        float patternCenter = (clipRange.start() + clipRange.end()) * 0.5f;
        float halfPattern = patternLength * 0.5f;
        float patternStart = patternCenter - halfPattern;
        
        // Calculate dash and gap proportions within the pattern
        float totalParts = dashCount + (dashCount - 1) * dashGap;
        float dashLength = patternLength / totalParts;
        float gapLength = dashLength * dashGap;
        
        // Compute perpendicular frame
        float[] right = new float[3];
        float[] up = new float[3];
        RayGeometryUtils.computePerpendicularFrame(dir[0], dir[1], dir[2], right, up);
        
        RayLineShape lineShape = context.lineShape();
        float shapeAmplitude = context.lineShapeAmplitude();
        float shapeFrequency = context.lineShapeFrequency();
        float curvatureIntensity = context.curvatureIntensity();
        
        float t = patternStart;
        for (int dash = 0; dash < dashCount; dash++) {
            float dashStart = t;
            float dashEnd = t + dashLength;
            
            // Clamp to ray bounds [0, 1]
            float tStart = Math.max(0f, dashStart);
            float tEnd = Math.min(1f, dashEnd);
            
            // Skip if dash is completely outside ray bounds
            if (tStart >= tEnd) {
                t = dashEnd + gapLength;
                continue;
            }
            
            // Tessellate each dash into multiple segments so it follows the curved path
            int dashSegments = Math.max(4, context.lineResolution() / dashCount);
            float dashSpan = tEnd - tStart;
            
            int[] vertexIndices = new int[dashSegments + 1];
            for (int s = 0; s <= dashSegments; s++) {
                float segT = tStart + (s / (float) dashSegments) * dashSpan;
                float[] pos = computeShapedPosition(start, end, segT, context, right, up, 
                    lineShape, shapeAmplitude, shapeFrequency, curvatureIntensity);
                    
                // Compute normalized position for fade gradient
                float tNorm = patternLength > 0 ? (segT - patternStart) / patternLength : 0.5f;
                float vertexAlpha = edgeResult.computeFadeAlpha(tNorm, baseAlpha);
                
                vertexIndices[s] = builder.vertex(pos[0], pos[1], pos[2], dir[0], dir[1], dir[2], segT, 0, vertexAlpha);
            }
            
            // Connect with lines
            for (int s = 0; s < dashSegments; s++) {
                builder.line(vertexIndices[s], vertexIndices[s + 1]);
            }
            
            t = dashEnd + gapLength;
        }
    }
    
    /**
     * Compute a shaped position at parametric t.
     */
    private float[] computeShapedPosition(float[] start, float[] end, float t, RayContext context,
            float[] right, float[] up, RayLineShape lineShape, float amplitude, float frequency, float curvatureIntensity) {
        // Apply curvature
        float[] curved = RayGeometryUtils.computeCurvedPosition(start, end, t, context.curvature(), curvatureIntensity);
        
        // Apply line shape offset
        float[] offset = RayGeometryUtils.computeLineShapeOffset(lineShape, t, amplitude, frequency, right, up);
        
        float px = curved[0] + offset[0];
        float py = curved[1] + offset[1];
        float pz = curved[2] + offset[2];
        
        // Apply field deformation if active
        if (context.fieldDeformation().isActive()) {
            float innerR = context.innerRadius();
            float outerR = context.outerRadius();
            float dist = (float) Math.sqrt(px * px + py * py + pz * pz);
            
            if (dist > 0.001f && outerR > innerR) {
                float normalizedDist = Math.clamp((dist - innerR) / (outerR - innerR), 0.01f, 1f);
                float stretch = context.fieldDeformation().computeStretch(
                    normalizedDist, context.fieldDeformationIntensity());
                float scaleFactor = (dist * stretch) / dist;
                px *= scaleFactor;
                py *= scaleFactor;
                pz *= scaleFactor;
            }
        }
        
        return new float[]{px, py, pz};
    }
}
