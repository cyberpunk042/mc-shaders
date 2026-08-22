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

import org.joml.Vector3f;

/**
 * Pure geometry math utilities for tessellation.
 * 
 * <p>This class provides static methods for calculating vertex positions
 * on various geometric primitives. All methods return {@link Vertex} objects
 * with position, normal, and UV coordinates.</p>
 * 
 * <h2>Coordinate System</h2>
 * <ul>
 *   <li>Y is up (Minecraft standard)</li>
 *   <li>Angles are in radians unless noted</li>
 *   <li>UV coordinates are 0-1 normalized</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * // Ring vertex at 45 degrees
 * Vertex v = GeometryMath.ringPoint(Math.PI/4, 0.8f, 1.0f, 0f, 0.5f);
 * 
 * // Disc center
 * Vertex center = GeometryMath.discCenter(0f);
 * </pre>
 * 
 * @see Vertex
 * @see SphereTessellator
 * @see RingTessellator
 */
public final class GeometryMath {
    
    private GeometryMath() {} // Static utility class
    
    // =========================================================================
    // Constants
    // =========================================================================
    
    /** Two times PI (full circle in radians) */
    public static final float TWO_PI = (float) (Math.PI * 2);
    
    /** PI (half circle in radians) */
    public static final float PI = (float) Math.PI;
    
    /** PI / 2 (quarter circle) */
    public static final float HALF_PI = (float) (Math.PI / 2);
    
    // =========================================================================
    // Ring Geometry
    // =========================================================================
    
    /**
     * Generates a vertex on a ring surface.
     * 
     * <p>A ring is defined by inner and outer radii. The {@code t} parameter
     * interpolates between inner (t=0) and outer (t=1) edges.</p>
     * 
     * @param angle Angle around Y axis in radians (0 = +X direction)
     * @param innerRadius Inner ring radius
     * @param outerRadius Outer ring radius
     * @param y Y position of the ring plane
     * @param t Interpolation factor (0 = inner edge, 1 = outer edge)
     * @return Vertex with position, normal (pointing up), and UV
     */
    public static Vertex ringPoint(float angle, float innerRadius, float outerRadius, 
                                    float y, float t) {
        float radius = lerp(innerRadius, outerRadius, t);
        float x = (float) Math.cos(angle) * radius;
        float z = (float) Math.sin(angle) * radius;
        
        // Normal points up for flat ring
        float nx = 0, ny = 1, nz = 0;
        
        // UV: u = angle normalized, v = t (inner to outer)
        float u = angle / TWO_PI;
        float v = t;
        
        return new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f);
    }
    
    /**
     * Generates a vertex on the inner edge of a ring.
     */
    public static Vertex ringInnerPoint(float angle, float innerRadius, float y) {
        return ringPoint(angle, innerRadius, innerRadius, y, 0);
    }
    
    /**
     * Generates a vertex on the outer edge of a ring.
     */
    public static Vertex ringOuterPoint(float angle, float outerRadius, float y) {
        return ringPoint(angle, outerRadius, outerRadius, y, 1);
    }
    
    // =========================================================================
    // Disc Geometry
    // =========================================================================
    
    /**
     * Generates a vertex on a disc surface.
     * 
     * <p>A disc is a filled circle. Vertices are positioned radially from center.</p>
     * 
     * @param angle Angle around Y axis in radians
     * @param radius Distance from center
     * @param y Y position of the disc plane
     * @return Vertex with position, normal (pointing up), and UV
     */
    public static Vertex discPoint(float angle, float radius, float y) {
        float x = (float) Math.cos(angle) * radius;
        float z = (float) Math.sin(angle) * radius;
        
        // Normal points up for flat disc
        float nx = 0, ny = 1, nz = 0;
        
        // UV: centered at (0.5, 0.5), radius 0.5
        float u = 0.5f + (float) Math.cos(angle) * 0.5f;
        float v = 0.5f + (float) Math.sin(angle) * 0.5f;
        
        return new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f);
    }
    
    /**
     * Generates the center vertex of a disc.
     */
    public static Vertex discCenter(float y) {
        return new Vertex(0, y, 0, 0, 1, 0, 0.5f, 0.5f, 1.0f);
    }
    
    // =========================================================================
    // Cylinder Geometry
    // =========================================================================
    
    /**
     * Generates a vertex on a cylinder wall.
     * 
     * @param angle Angle around Y axis in radians
     * @param y Y position along cylinder height
     * @param radius Cylinder radius at this height
     * @param height Total cylinder height (for UV calculation)
     * @param yOffset Y offset of cylinder base
     * @return Vertex with position, outward-facing normal, and UV
     */
    public static Vertex cylinderPoint(float angle, float y, float radius, 
                                        float height, float yOffset) {
        float x = (float) Math.cos(angle) * radius;
        float z = (float) Math.sin(angle) * radius;
        
        // Normal points outward (radial direction)
        float nx = (float) Math.cos(angle);
        float ny = 0;
        float nz = (float) Math.sin(angle);
        
        // UV: u = angle normalized, v = height normalized
        float u = angle / TWO_PI;
        float v = (y - yOffset) / height;
        
        return new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f);
    }
    
    /**
     * Generates a vertex for a tapered cylinder (cone-like).
     * 
     * @param angle Angle around Y axis
     * @param t Height fraction (0 = bottom, 1 = top)
     * @param bottomRadius Radius at bottom
     * @param topRadius Radius at top
     * @param height Total height
     * @param yOffset Y offset of base
     * @return Vertex with interpolated radius and adjusted normal
     */
    public static Vertex cylinderTaperedPoint(float angle, float t, 
                                               float bottomRadius, float topRadius,
                                               float height, float yOffset) {
        float radius = lerp(bottomRadius, topRadius, t);
        float y = yOffset + t * height;
        float x = (float) Math.cos(angle) * radius;
        float z = (float) Math.sin(angle) * radius;
        
        // Calculate normal for tapered surface
        // The normal tilts based on the taper angle
        float radiusDiff = bottomRadius - topRadius;
        float tiltAngle = (float) Math.atan2(radiusDiff, height);
        float cosAngle = (float) Math.cos(angle);
        float sinAngle = (float) Math.sin(angle);
        float cosTilt = (float) Math.cos(tiltAngle);
        float sinTilt = (float) Math.sin(tiltAngle);
        
        float nx = cosAngle * cosTilt;
        float ny = sinTilt;
        float nz = sinAngle * cosTilt;
        
        // UV
        float u = angle / TWO_PI;
        float v = t;
        
        return new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f);
    }
    
    // =========================================================================
    // Prism Geometry
    // =========================================================================
    
    /**
     * Generates a vertex on a prism edge.
     * 
     * @param side Side index (0 to sides-1)
     * @param totalSides Total number of sides
     * @param y Y position
     * @param radius Distance from center to vertex
     * @param twist Twist angle in radians (applied proportionally with height)
     * @param height Total prism height (for twist calculation)
     * @param yBase Y position of prism base
     * @return Vertex on prism corner
     */
    public static Vertex prismCorner(int side, int totalSides, float y, float radius,
                                      float twist, float height, float yBase) {
        // Base angle for this side
        float baseAngle = (side / (float) totalSides) * TWO_PI;
        
        // Apply twist based on height fraction
        float heightFrac = (y - yBase) / height;
        float twistAmount = twist * heightFrac;
        float angle = baseAngle + twistAmount;
        
        float x = (float) Math.cos(angle) * radius;
        float z = (float) Math.sin(angle) * radius;
        
        // Normal points outward perpendicular to the face
        // For a face between this corner and the next, normal is at angle + half step
        float faceAngle = baseAngle + (PI / totalSides);
        float nx = (float) Math.cos(faceAngle);
        float ny = 0;
        float nz = (float) Math.sin(faceAngle);
        
        // UV: u = side fraction, v = height fraction
        float u = side / (float) totalSides;
        float v = heightFrac;
        
        return new Vertex(x, y, z, nx, ny, nz, u, v, 1.0f);
    }
    
    /**
     * Calculates the center of a prism face (for triangulating polygonal caps).
     */
    public static Vertex prismFaceCenter(int totalSides, float y, float radius) {
        // Center is at origin X/Z
        return new Vertex(0, y, 0, 0, y > 0 ? 1 : -1, 0, 0.5f, 0.5f, 1.0f);
    }
    
    // =========================================================================
    // Utility Methods
    // =========================================================================
    
    /**
     * Linear interpolation between two values.
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
    
    /**
     * Clamps a value between min and max.
     */
    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * Normalizes an angle to 0-2PI range.
     */
    public static float normalizeAngle(float angle) {
        while (angle < 0) angle += TWO_PI;
        while (angle >= TWO_PI) angle -= TWO_PI;
        return angle;
    }
    
    /**
     * Converts degrees to radians.
     */
    public static float toRadians(float degrees) {
        return degrees * PI / 180f;
    }
    
    /**
     * Generates an array of angles for a given arc.
     * 
     * @param arcStart Start angle in degrees
     * @param arcEnd End angle in degrees
     * @param segments Number of segments
     * @return Array of angles in radians
     */
    public static float[] arcAngles(float arcStart, float arcEnd, int segments) {
        float[] angles = new float[segments + 1];
        float startRad = toRadians(arcStart);
        float endRad = toRadians(arcEnd);
        float step = (endRad - startRad) / segments;
        
        for (int i = 0; i <= segments; i++) {
            angles[i] = startRad + i * step;
        }
        return angles;
    }
    
    /**
     * Calculates face normal from 3 vertices (counter-clockwise winding).
     */
    public static Vector3f faceNormal(Vertex v0, Vertex v1, Vertex v2) {
        // Edge vectors
        float ax = v1.x() - v0.x();
        float ay = v1.y() - v0.y();
        float az = v1.z() - v0.z();
        
        float bx = v2.x() - v0.x();
        float by = v2.y() - v0.y();
        float bz = v2.z() - v0.z();
        
        // Cross product
        float nx = ay * bz - az * by;
        float ny = az * bx - ax * bz;
        float nz = ax * by - ay * bx;
        
        // Normalize
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0.0001f) {
            nx /= len;
            ny /= len;
            nz /= len;
        }
        
        return new Vector3f(nx, ny, nz);
    }
    
    // =========================================================================
    // Vector Array Utilities (for tessellators using float[] vertices)
    // =========================================================================
    
    /**
     * Subtracts vector b from vector a.
     * @param a First vector [x, y, z]
     * @param b Second vector [x, y, z]
     * @return New vector a - b
     */
    public static float[] subtract(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }
    
    /**
     * Computes the cross product of two vectors.
     * @param a First vector [x, y, z]
     * @param b Second vector [x, y, z]
     * @return New vector a × b
     */
    public static float[] crossProduct(float[] a, float[] b) {
        return new float[]{
            a[1] * b[2] - a[2] * b[1],  // x = ay*bz - az*by
            a[2] * b[0] - a[0] * b[2],  // y = az*bx - ax*bz
            a[0] * b[1] - a[1] * b[0]   // z = ax*by - ay*bx
        };
    }
    
    /**
     * Normalizes a vector in place.
     * @param v Vector to normalize [x, y, z] - modified in place
     */
    public static void normalizeInPlace(float[] v) {
        float length = (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length > 0.0001f) {
            v[0] /= length;
            v[1] /= length;
            v[2] /= length;
        }
    }
    
    /**
     * Returns the length of a vector.
     * @param v Vector [x, y, z]
     * @return Euclidean length
     */
    public static float length(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
    
    /**
     * Scales vertex positions by a factor.
     * @param vertices Array of vertex positions
     * @param scale Scale factor
     * @return New array of scaled vertices
     */
    public static float[][] scaleVertices(float[][] vertices, float scale) {
        float[][] scaled = new float[vertices.length][3];
        for (int i = 0; i < vertices.length; i++) {
            scaled[i][0] = vertices[i][0] * scale;
            scaled[i][1] = vertices[i][1] * scale;
            scaled[i][2] = vertices[i][2] * scale;
        }
        return scaled;
    }
}
