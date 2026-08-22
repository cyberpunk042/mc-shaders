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

import net.cyberpunk042.mcshaders.core.mesh.ray.geometry.CurvatureFactory;
import net.cyberpunk042.mcshaders.core.mesh.ray.geometry.CurvatureStrategy;
import net.cyberpunk042.mcshaders.core.mesh.ray.geometry.LineShapeFactory;
import net.cyberpunk042.mcshaders.core.mesh.ray.geometry.LineShapeStrategy;
import net.cyberpunk042.mcshaders.core.shape.RayCurvature;
import net.cyberpunk042.mcshaders.core.shape.RayLineShape;

/**
 * Common geometry utilities for ray tessellation.
 * 
 * <p>Provides reusable functions for:
 * <ul>
 *   <li>Perpendicular frame calculation (right/up vectors)</li>
 *   <li>Line shape offsets (sine wave, corkscrew, zigzag, etc.)</li>
 *   <li>Curvature calculations (vortex, spiral)</li>
 *   <li>Position interpolation</li>
 * </ul>
 * 
 * <p>These utilities are used by all RayTypeTessellator implementations.
 * 
 * @see RayTypeTessellator
 */
public final class RayGeometryUtils {
    
    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = 2 * PI;
    
    private RayGeometryUtils() {} // Utility class
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Position Utilities
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Interpolates between two positions.
     * @param start Start position [x, y, z]
     * @param end End position [x, y, z]
     * @param t Parametric value (0 = start, 1 = end)
     * @return Interpolated position [x, y, z]
     */
    public static float[] interpolate(float[] start, float[] end, float t) {
        return new float[] {
            start[0] + (end[0] - start[0]) * t,
            start[1] + (end[1] - start[1]) * t,
            start[2] + (end[2] - start[2]) * t
        };
    }
    
    /**
     * Computes the direction and length from start to end.
     * @param start Start position
     * @param end End position
     * @return [dx, dy, dz, length] - normalized direction + length
     */
    public static float[] computeDirectionAndLength(float[] start, float[] end) {
        float dx = end[0] - start[0];
        float dy = end[1] - start[1];
        float dz = end[2] - start[2];
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        if (length > 0.0001f) {
            return new float[] { dx / length, dy / length, dz / length, length };
        }
        return new float[] { 0, 1, 0, 0 }; // Default up, zero length
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Frame Calculation
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Computes a stable perpendicular frame (right, up) for a given direction vector.
     * 
     * <p>Uses the "arbitrary axis algorithm" with Y-preference:
     * <ul>
     *   <li>If direction is mostly vertical, use X as reference</li>
     *   <li>Otherwise, use Y as reference</li>
     * </ul>
     * 
     * @param dx Direction X
     * @param dy Direction Y
     * @param dz Direction Z
     * @param right Output: the "right" perpendicular vector [3]
     * @param up Output: the "up" perpendicular vector [3]
     */
    public static void computePerpendicularFrame(float dx, float dy, float dz,
                                                  float[] right, float[] up) {
        // Choose reference axis (avoid parallel to direction)
        float refX, refY, refZ;
        if (Math.abs(dy) > 0.9f) {
            // Direction is mostly vertical, use X axis as reference
            refX = 1; refY = 0; refZ = 0;
        } else {
            // Use Y axis as reference
            refX = 0; refY = 1; refZ = 0;
        }
        
        // right = normalize(direction × reference)
        float rx = dy * refZ - dz * refY;
        float ry = dz * refX - dx * refZ;
        float rz = dx * refY - dy * refX;
        float rmag = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (rmag > 0.0001f) {
            right[0] = rx / rmag;
            right[1] = ry / rmag;
            right[2] = rz / rmag;
        } else {
            right[0] = 1; right[1] = 0; right[2] = 0;
        }
        
        // up = normalize(right × direction)
        up[0] = right[1] * dz - right[2] * dy;
        up[1] = right[2] * dx - right[0] * dz;
        up[2] = right[0] * dy - right[1] * dx;
        float umag = (float) Math.sqrt(up[0] * up[0] + up[1] * up[1] + up[2] * up[2]);
        if (umag > 0.0001f) {
            up[0] /= umag;
            up[1] /= umag;
            up[2] /= umag;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Line Shape Offsets
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Computes the offset for a given line shape at parameter t.
     * 
     * @param lineShape The line shape type
     * @param t Parametric position (0 = start, 1 = end)
     * @param amplitude How pronounced the shape is
     * @param frequency Number of waves/coils along the ray
     * @param right The "right" perpendicular vector
     * @param up The "up" perpendicular vector
     * @return [x, y, z] offset to add to base position
     */
    public static float[] computeLineShapeOffset(RayLineShape lineShape, float t,
                                                  float amplitude, float frequency,
                                                  float[] right, float[] up) {
        return computeLineShapeOffsetWithPhase(lineShape, t, amplitude, frequency, right, up, 0f);
    }
    
    /**
     * Computes line shape offset with an additional phase offset.
     * <p>Delegates to {@link LineShapeFactory} for strategy-based computation.</p>
     * 
     * @param lineShape Line shape type
     * @param t Parametric position (0 = start, 1 = end)
     * @param amplitude How pronounced the shape is
     * @param frequency Number of waves/coils along the ray
     * @param right The "right" perpendicular vector
     * @param up The "up" perpendicular vector
     * @param phaseOffset Phase offset (e.g., for second strand in DOUBLE_HELIX)
     * @return [x, y, z] offset to add to base position
     */
    public static float[] computeLineShapeOffsetWithPhase(RayLineShape lineShape, float t,
                                                           float amplitude, float frequency,
                                                           float[] right, float[] up, float phaseOffset) {
        
        // Use strategy pattern for line shape computation
        LineShapeStrategy strategy = LineShapeFactory.get(lineShape);
        
        // Get frame-relative offset [right_amount, up_amount, 0]
        float[] relativeOffset = strategy.computeOffset(t, amplitude, frequency, phaseOffset);
        
        // Transform to world space using perpendicular frame
        float[] offset = new float[3];
        offset[0] = right[0] * relativeOffset[0] + up[0] * relativeOffset[1];
        offset[1] = right[1] * relativeOffset[0] + up[1] * relativeOffset[1];
        offset[2] = right[2] * relativeOffset[0] + up[2] * relativeOffset[1];
        
        return offset;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Curvature Calculations
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Computes a curved position along the ray based on curvature mode.
     * <p>Delegates to {@link CurvatureFactory} for strategy-based computation.</p>
     * 
     * @param start Ray start position
     * @param end Ray end position
     * @param t Parametric value (0-1)
     * @param curvature Curvature mode
     * @param intensity Curvature intensity
     * @return Curved position [x, y, z]
     */
    public static float[] computeCurvedPosition(float[] start, float[] end, float t,
                                                 RayCurvature curvature, float intensity) {
        if (curvature == null || curvature == RayCurvature.NONE || intensity < 0.001f) {
            return interpolate(start, end, t);
        }
        
        float[] pos = interpolate(start, end, t);
        CurvatureStrategy strategy = CurvatureFactory.get(curvature);
        return strategy.apply(pos, t, intensity, start);
    }
    
    /**
     * Computes the tangent direction at a point on a curved path.
     * 
     * <p>The tangent is computed by sampling two nearby points on the curve
     * and computing the normalized direction between them.</p>
     * 
     * @param start Ray start position
     * @param end Ray end position
     * @param t Parametric value (0-1)
     * @param curvature Curvature mode
     * @param intensity Curvature intensity
     * @return Tangent direction [x, y, z] (normalized)
     */
    public static float[] computeCurvedTangent(float[] start, float[] end, float t,
                                               RayCurvature curvature, float intensity) {
        if (curvature == null || curvature == RayCurvature.NONE || intensity < 0.001f) {
            // Straight line - tangent is just the direction
            float[] dir = computeDirectionAndLength(start, end);
            return new float[] { dir[0], dir[1], dir[2] };
        }
        
        // Sample two nearby points to compute tangent
        float dt = 0.02f;
        float t1 = Math.max(0, t - dt);
        float t2 = Math.min(1, t + dt);
        
        float[] p1 = computeCurvedPosition(start, end, t1, curvature, intensity);
        float[] p2 = computeCurvedPosition(start, end, t2, curvature, intensity);
        
        // Direction from p1 to p2
        float[] tangent = new float[] {
            p2[0] - p1[0],
            p2[1] - p1[1],
            p2[2] - p1[2]
        };
        normalize(tangent);
        
        return tangent;
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Vector Math Utilities
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Normalizes a vector in-place.
     * @return The magnitude before normalization
     */
    public static float normalize(float[] v) {
        float mag = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (mag > 0.0001f) {
            v[0] /= mag;
            v[1] /= mag;
            v[2] /= mag;
        }
        return mag;
    }
    
    /**
     * Computes the dot product of two vectors.
     */
    public static float dot(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
    
    /**
     * Computes the cross product: result = a × b
     */
    public static void cross(float[] a, float[] b, float[] result) {
        result[0] = a[1] * b[2] - a[2] * b[1];
        result[1] = a[2] * b[0] - a[0] * b[2];
        result[2] = a[0] * b[1] - a[1] * b[0];
    }
    
    /**
     * Scales a vector: result = v * scale
     */
    public static void scale(float[] v, float scale, float[] result) {
        result[0] = v[0] * scale;
        result[1] = v[1] * scale;
        result[2] = v[2] * scale;
    }
    
    /**
     * Adds two vectors: result = a + b
     */
    public static void add(float[] a, float[] b, float[] result) {
        result[0] = a[0] + b[0];
        result[1] = a[1] + b[1];
        result[2] = a[2] + b[2];
    }
}
