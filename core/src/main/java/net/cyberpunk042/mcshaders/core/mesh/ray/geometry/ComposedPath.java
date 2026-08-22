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
package net.cyberpunk042.mcshaders.core.mesh.ray.geometry;

/**
 * Composed path that applies curvature and optionally line shape to a base path.
 * 
 * <h2>Composition Order</h2>
 * <ol>
 *   <li>Get base position from start/end interpolation</li>
 *   <li>Apply curvature transformation (vortex, spiral, etc.)</li>
 *   <li>Apply line shape offset (wave, corkscrew, etc.)</li>
 * </ol>
 */
public final class ComposedPath implements GeoPath {
    
    private final float[] start;
    private final float[] end;
    private final float[] center;
    private final float length;
    private final float[] direction;
    
    private final CurvatureStrategy curvature;
    private final float curvatureIntensity;
    
    private final LineShapeStrategy lineShape;
    private final float lineShapeAmplitude;
    private final float lineShapeFrequency;
    private final float lineShapePhase;
    
    // Perpendicular frame for line shape offsets
    private final float[] right = new float[3];
    private final float[] up = new float[3];
    
    public ComposedPath(
            float[] start, float[] end, float[] center,
            CurvatureStrategy curvature, float curvatureIntensity,
            LineShapeStrategy lineShape, float lineShapeAmplitude, 
            float lineShapeFrequency, float lineShapePhase) {
        
        this.start = start.clone();
        this.end = end.clone();
        this.center = center != null ? center.clone() : new float[] { 0, 0, 0 };
        
        this.curvature = curvature != null ? curvature : NoCurvature.INSTANCE;
        this.curvatureIntensity = curvatureIntensity;
        
        this.lineShape = lineShape != null ? lineShape : StraightLineShape.INSTANCE;
        this.lineShapeAmplitude = lineShapeAmplitude;
        this.lineShapeFrequency = lineShapeFrequency;
        this.lineShapePhase = lineShapePhase;
        
        // Compute direction and length
        float dx = end[0] - start[0];
        float dy = end[1] - start[1];
        float dz = end[2] - start[2];
        this.length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (length > 0.0001f) {
            this.direction = new float[] { dx / length, dy / length, dz / length };
        } else {
            this.direction = new float[] { 0, 1, 0 };
        }
        
        // Compute perpendicular frame for line shape
        computePerpendicularFrame(direction[0], direction[1], direction[2], right, up);
    }
    
    @Override
    public float[] positionAt(float t) {
        // 1. Base interpolation
        float[] pos = new float[] {
            start[0] + (end[0] - start[0]) * t,
            start[1] + (end[1] - start[1]) * t,
            start[2] + (end[2] - start[2]) * t
        };
        
        // 2. Apply curvature
        pos = curvature.apply(pos, t, curvatureIntensity, center);
        
        // 3. Apply line shape offset
        float[] offset = lineShape.computeOffset(t, lineShapeAmplitude, lineShapeFrequency, lineShapePhase);
        if (offset[0] != 0 || offset[1] != 0) {
            // Transform offset from local (right/up) to world space
            pos[0] += right[0] * offset[0] + up[0] * offset[1];
            pos[1] += right[1] * offset[0] + up[1] * offset[1];
            pos[2] += right[2] * offset[0] + up[2] * offset[1];
        }
        
        return pos;
    }
    
    @Override
    public float[] tangentAt(float t) {
        // Compute tangent by sampling nearby points
        float dt = 0.02f;
        float t1 = Math.max(0, t - dt);
        float t2 = Math.min(1, t + dt);
        
        float[] p1 = positionAt(t1);
        float[] p2 = positionAt(t2);
        
        float[] tangent = new float[] {
            p2[0] - p1[0],
            p2[1] - p1[1],
            p2[2] - p1[2]
        };
        
        // Normalize
        float mag = (float) Math.sqrt(tangent[0] * tangent[0] + tangent[1] * tangent[1] + tangent[2] * tangent[2]);
        if (mag > 0.0001f) {
            tangent[0] /= mag;
            tangent[1] /= mag;
            tangent[2] /= mag;
        }
        
        return tangent;
    }
    
    @Override
    public float length() {
        return length;
    }
    
    @Override
    public float[] start() {
        return positionAt(0);
    }
    
    @Override
    public float[] end() {
        return positionAt(1);
    }
    
    // ──────────────────── Frame Calculation ────────────────────
    
    private static void computePerpendicularFrame(float dx, float dy, float dz, float[] outRight, float[] outUp) {
        // Choose reference axis (avoid parallel to direction)
        float refX, refY, refZ;
        if (Math.abs(dy) > 0.9f) {
            refX = 1; refY = 0; refZ = 0;
        } else {
            refX = 0; refY = 1; refZ = 0;
        }
        
        // right = normalize(direction × reference)
        float rx = dy * refZ - dz * refY;
        float ry = dz * refX - dx * refZ;
        float rz = dx * refY - dy * refX;
        float rmag = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rmag > 0.0001f) {
            outRight[0] = rx / rmag;
            outRight[1] = ry / rmag;
            outRight[2] = rz / rmag;
        } else {
            outRight[0] = 1; outRight[1] = 0; outRight[2] = 0;
        }
        
        // up = normalize(right × direction)
        outUp[0] = outRight[1] * dz - outRight[2] * dy;
        outUp[1] = outRight[2] * dx - outRight[0] * dz;
        outUp[2] = outRight[0] * dy - outRight[1] * dx;
        float umag = (float) Math.sqrt(outUp[0] * outUp[0] + outUp[1] * outUp[1] + outUp[2] * outUp[2]);
        if (umag > 0.0001f) {
            outUp[0] /= umag;
            outUp[1] /= umag;
            outUp[2] /= umag;
        }
    }
}
