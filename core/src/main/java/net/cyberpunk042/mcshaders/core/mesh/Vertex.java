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
 * Immutable vertex data for mesh rendering.
 * 
 * <h2>Components</h2>
 * <ul>
 *   <li><b>Position</b> (x, y, z): World-space coordinates</li>
 *   <li><b>Normal</b> (nx, ny, nz): Surface direction for lighting</li>
 *   <li><b>UV</b> (u, v): Texture coordinates (0-1 range)</li>
 *   <li><b>Alpha</b> (alpha): Per-vertex transparency (0=invisible, 1=opaque)</li>
 * </ul>
 * 
 * <h2>Factory Methods</h2>
 * <p>Use factory methods instead of constructor for common cases:
 * <pre>
 * // Position only (for wireframe, no lighting)
 * Vertex v1 = Vertex.pos(1, 0, 0);
 * 
 * // Position + normal (for lit geometry, no texture)
 * Vertex v2 = Vertex.posNormal(1, 0, 0, 1, 0, 0);
 * 
 * // Spherical coordinates (for sphere tessellation)
 * Vertex v3 = Vertex.spherical(theta, phi);
 * </pre>
 * 
 * <h2>Transforms</h2>
 * <p>All transforms return NEW vertices (immutable):
 * <pre>
 * Vertex scaled = vertex.scaled(2.0f);      // Double size
 * Vertex moved = vertex.translated(0, 1, 0); // Move up
 * </pre>
 * 
 * @see Mesh
 * @see MeshBuilder
 */
public record Vertex(
        float x, float y, float z,      // Position in local space
        float nx, float ny, float nz,   // Normal vector (should be normalized)
        float u, float v,               // Texture coordinates (0-1)
        float alpha                     // Per-vertex alpha (0=invisible, 1=opaque)
) {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Factory Methods - Use these instead of constructor
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Creates a vertex with position only.
     * <p>Normal and UV are zeroed. Alpha is 1.0 (opaque). Use for wireframe or when lighting is not needed.
     */
    public static Vertex pos(float x, float y, float z) {
        return new Vertex(x, y, z, 0, 0, 0, 0, 0, 1.0f);
    }
    
    /**
     * Creates a vertex with position and normal.
     * <p>UV is zeroed. Alpha is 1.0 (opaque). Use for lit geometry without textures.
     */
    public static Vertex posNormal(float x, float y, float z, float nx, float ny, float nz) {
        return new Vertex(x, y, z, nx, ny, nz, 0, 0, 1.0f);
    }
    
    /**
     * Creates a vertex with position and UV, auto-calculating normal from position.
     * <p>Assumes vertex is on a sphere centered at origin - normal = normalized position.
     * Alpha is 1.0 (opaque). Falls back to (0,1,0) if position is at origin.
     */
    public static Vertex posUV(float x, float y, float z, float u, float v) {
        // Calculate length for normalization
        float len = (float) Math.sqrt(x * x + y * y + z * z);
        
        // Avoid division by zero - use up vector as fallback
        if (len > 0.0001f) {
            return new Vertex(x, y, z, x / len, y / len, z / len, u, v, 1.0f);
        }
        return new Vertex(x, y, z, 0, 1, 0, u, v, 1.0f);
    }
    
    /**
     * Creates a vertex for a unit sphere at the given spherical coordinates.
     * 
     * <p>Coordinate system:
     * <ul>
     *   <li>theta = 0: top pole (y = 1)</li>
     *   <li>theta = PI: bottom pole (y = -1)</li>
     *   <li>phi = 0: positive X axis</li>
     *   <li>phi = PI/2: positive Z axis</li>
     * </ul>
     * 
     * <p>UV mapping: u = phi/2PI (horizontal), v = theta/PI (vertical)
     * 
     * @param theta polar angle in radians (0 = top, PI = bottom)
     * @param phi azimuthal angle in radians (0 to 2*PI around Y axis)
     * @return vertex on unit sphere with position, normal, and UV
     */
    public static Vertex spherical(float theta, float phi) {
        // Convert spherical to cartesian
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Position on unit sphere
        float x = sinTheta * cosPhi;
        float y = cosTheta;           // Y is up (theta=0 -> y=1)
        float z = sinTheta * sinPhi;
        
        // For a unit sphere, normal equals position (already normalized)
        // UV: wrap texture around sphere
        float u = phi / (float) (2 * Math.PI);   // 0-1 around
        float v = theta / (float) Math.PI;        // 0-1 top to bottom
        
        return new Vertex(x, y, z, x, y, z, u, v, 1.0f);
    }
    
    /**
     * Creates a vertex for a sphere at the given spherical coordinates with radius.
     * <p>Convenience method - creates unit sphere vertex then scales.
     * 
     * @param theta polar angle (0 = top, PI = bottom)
     * @param phi azimuthal angle (0 to 2*PI)
     * @param radius sphere radius
     */
    public static Vertex spherical(float theta, float phi, float radius) {
        Vertex unit = spherical(theta, phi);
        return unit.scaled(radius);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Transform Methods - Return NEW vertices (immutable)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns a new vertex with position scaled by the given factor.
     * <p>Normal, UV, and alpha are preserved.
     * 
     * @param scale multiplier for position
     * @return new scaled vertex
     */
    public Vertex scaled(float scale) {
        return new Vertex(
            x * scale, y * scale, z * scale,
            nx, ny, nz,  // Normal unchanged
            u, v,        // UV unchanged
            alpha        // Alpha unchanged
        );
    }
    
    /**
     * Returns a new vertex with position offset by the given delta.
     * <p>Normal, UV, and alpha are preserved.
     * 
     * @param dx X offset
     * @param dy Y offset
     * @param dz Z offset
     * @return new translated vertex
     */
    public Vertex translated(float dx, float dy, float dz) {
        return new Vertex(
            x + dx, y + dy, z + dz,
            nx, ny, nz,  // Normal unchanged
            u, v,        // UV unchanged
            alpha        // Alpha unchanged
        );
    }
    
    /**
     * Returns a new vertex with a different normal.
     * <p>Position, UV, and alpha are preserved.
     */
    public Vertex withNormal(float nx, float ny, float nz) {
        return new Vertex(x, y, z, nx, ny, nz, u, v, alpha);
    }
    
    /**
     * Returns a new vertex with different UV coordinates.
     * <p>Position, normal, and alpha are preserved.
     */
    public Vertex withUV(float u, float v) {
        return new Vertex(x, y, z, nx, ny, nz, u, v, alpha);
    }
    
    /**
     * Returns a new vertex with different alpha.
     * <p>Position, normal, and UV are preserved.
     */
    public Vertex withAlpha(float alpha) {
        return new Vertex(x, y, z, nx, ny, nz, u, v, alpha);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Conversion Methods
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Returns position as a JOML Vector3f.
     * <p>Creates a new vector - caller owns it.
     */
    public Vector3f position() {
        return new Vector3f(x, y, z);
    }
    
    /**
     * Returns normal as a JOML Vector3f.
     * <p>Creates a new vector - caller owns it.
     */
    public Vector3f normal() {
        return new Vector3f(nx, ny, nz);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Interpolation
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Linearly interpolates between this vertex and another.
     * <p>All components are interpolated: position, normal, UV, alpha.
     * 
     * <p>Note: Normal interpolation may produce non-unit normals.
     * Renormalize if needed for lighting.
     * 
     * @param other target vertex
     * @param t interpolation factor (0 = this, 1 = other)
     * @return new interpolated vertex
     */
    public Vertex lerp(Vertex other, float t) {
        return new Vertex(
            x + (other.x - x) * t,
            y + (other.y - y) * t,
            z + (other.z - z) * t,
            nx + (other.nx - nx) * t,
            ny + (other.ny - ny) * t,
            nz + (other.nz - nz) * t,
            u + (other.u - u) * t,
            v + (other.v - v) * t,
            alpha + (other.alpha - alpha) * t
        );
    }
}
