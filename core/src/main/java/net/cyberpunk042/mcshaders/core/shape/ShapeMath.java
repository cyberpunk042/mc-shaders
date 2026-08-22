/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 */
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Pure math functions for polar shape deformation.
 * 
 * <p>These functions compute deformed vertex positions from spherical coordinates.
 * θ=0 is the "tip" (top/north pole), θ=π is the "base" (bottom/south pole).</p>
 * 
 * <h2>Shape Categories</h2>
 * <ul>
 *   <li><b>Symmetric:</b> Sphere, Spheroid (oblate/prolate), Ellipsoid</li>
 *   <li><b>Organic:</b> Ovoid, Egg, Pear</li>
 *   <li><b>Directional:</b> Teardrop/Droplet, Bullet, Cone</li>
 * </ul>
 * 
 * <h2>Key Parameters</h2>
 * <ul>
 *   <li><b>length:</b> Axial stretch (1 = sphere, &gt;1 = prolate, &lt;1 = oblate)</li>
 *   <li><b>intensity:</b> Deformation strength (0 = sphere, 1 = full effect)</li>
 * </ul>
 * 
 * @see SphereDeformation
 */
public final class ShapeMath {
    
    private static final float PI = (float) Math.PI;
    private static final float HALF_PI = (float) (Math.PI * 0.5);
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    private ShapeMath() {} // Utility class
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Radius Factor Functions (legacy - for simple radius scaling)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Sphere: constant radius. */
    public static float sphere(float theta) {
        return 1.0f;
    }
    
    /** Droplet radius factor: sin(θ/2)^power */
    public static float droplet(float theta, float power) {
        float base = (float) Math.sin(theta * 0.5f);
        return (float) Math.pow(Math.max(0.0001f, base), power);
    }
    
    /** Egg radius factor: 1 + asymmetry × cos(θ) */
    public static float egg(float theta, float asymmetry) {
        return 1.0f + asymmetry * (float) Math.cos(theta);
    }
    
    /** Bullet radius factor */
    public static float bullet(float theta) {
        if (theta < HALF_PI) {
            return (float) Math.sin(theta);
        }
        return 1.0f;
    }
    
    /** Cone radius factor: θ/π */
    public static float cone(float theta) {
        return theta / PI;
    }
    
    /** Inverted droplet radius factor */
    public static float dropletInverted(float theta, float power) {
        return droplet(PI - theta, power);
    }
    
    /** Blends between sphere and shape */
    public static float blend(float shapeFactor, float intensity) {
        return 1.0f + (shapeFactor - 1.0f) * Math.min(1.0f, intensity);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Direct Vertex Position Functions (Proper Parametric Geometry)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Sphere vertex position (standard spherical coordinates).
     */
    public static float[] sphereVertex(float theta, float phi, float radius) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        return new float[] {
            radius * sinTheta * cosPhi,  // X
            radius * cosTheta,           // Y (up)
            radius * sinTheta * sinPhi   // Z
        };
    }
    
    /**
     * Astrophysical spheroid vertex position (oblate with equatorial bulge).
     * 
     * <p><b>True oblate spheroid (like planets):</b><br>
     * When length &lt; 1: polar axis compresses AND equator bulges outward.<br>
     * When length &gt; 1: polar axis stretches AND equator contracts.<br>
     * This matches how rotating planets deform (centrifugal force at equator).</p>
     * 
     * <p>Formula uses volume-preserving deformation where a² * c = r³<br>
     * For oblate (c &lt; r): a = r * sqrt(r/c) = r / sqrt(length)<br>
     * For prolate (c &gt; r): a = r * sqrt(r/c) = r / sqrt(length)</p>
     * 
     * @param theta Polar angle (0 = top pole, π = bottom pole)
     * @param phi Azimuthal angle (0 to 2π)
     * @param radius Base radius (sphere radius at length=1)
     * @param length Polar axis ratio: &lt;1 = oblate (disc), &gt;1 = prolate (football)
     */
    public static float[] spheroidVertex(float theta, float phi, float radius, float length) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Polar radius (c) = radius * length
        float c = radius * length;
        
        // Equatorial radius (a) using volume-preserving formula: a²c = r³
        // a = r * sqrt(r/c) = r / sqrt(length)
        float a = radius / (float) Math.sqrt(length);
        
        return new float[] {
            a * sinTheta * cosPhi,      // X (equatorial, bulges when length < 1)
            c * cosTheta,               // Y (polar axis)
            a * sinTheta * sinPhi       // Z (equatorial, bulges when length < 1)
        };
    }
    
    /**
     * Spheroid with independent equatorial bulge control (planetary distortion).
     * 
     * <p>Unlike volume-preserving spheroidVertex, this allows independent control:
     * <ul>
     *   <li><b>length:</b> Controls polar axis stretch (c = radius * length)</li>
     *   <li><b>bulge:</b> Controls additional equatorial bulge (0 = no extra, 0.5 = 50% wider)</li>
     * </ul>
     * </p>
     * 
     * <p>This simulates a spinning planet where centrifugal force makes the equator
     * bulge outward independently of how tall/short the poles are.</p>
     * 
     * @param theta Polar angle (0 = top pole, π = bottom pole)
     * @param phi Azimuthal angle (0 to 2π)
     * @param radius Base radius
     * @param length Polar axis ratio (1 = sphere height, &lt;1 = squashed, &gt;1 = stretched)
     * @param bulge Equatorial bulge factor (0 = sphere width, 0.5 = 50% wider equator)
     */
    public static float[] spheroidWithBulge(float theta, float phi, float radius, 
            float length, float bulge) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Polar radius (c) = radius * length - controlled by length slider
        float c = radius * length;
        
        // Equatorial radius (a) = radius * (1 + bulge) - controlled by intensity slider
        // bulge = 0 -> sphere radius (a = radius)
        // bulge = 0.5 -> 50% extra equatorial width (a = radius * 1.5)
        float a = radius * (1.0f + bulge);
        
        return new float[] {
            a * sinTheta * cosPhi,      // X (equatorial, bulges with intensity)
            c * cosTheta,               // Y (polar axis, controlled by length)
            a * sinTheta * sinPhi       // Z (equatorial, bulges with intensity)
        };
    }
    
    /**
     * Ellipsoid vertex position (three unequal axes).
     * 
     * @param theta Polar angle
     * @param phi Azimuthal angle
     * @param radius Base radius (X axis)
     * @param lengthY Y axis scale factor
     * @param lengthZ Z axis scale factor
     */
    public static float[] ellipsoidVertex(float theta, float phi, float radius, 
            float lengthY, float lengthZ) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        return new float[] {
            radius * sinTheta * cosPhi,           // X
            radius * lengthY * cosTheta,          // Y (scaled)
            radius * lengthZ * sinTheta * sinPhi  // Z (scaled)
        };
    }
    
    /**
     * Ovoid vertex position (smooth egg-like, symmetric but stretched at one end).
     * 
     * <p>Similar to egg but with softer asymmetry.</p>
     * 
     * @param theta Polar angle
     * @param phi Azimuthal angle
     * @param radius Base radius
     * @param asymmetry Asymmetry factor (0 = sphere, 0.2-0.4 = typical ovoid)
     * @param length Axial stretch
     */
    public static float[] ovoidVertex(float theta, float phi, float radius, 
            float asymmetry, float length) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Ovoid profile: smoother transition than egg
        // Use cosine-based asymmetry that's gentler
        float radiusFactor = 1.0f + asymmetry * 0.5f * (1.0f + (float) Math.cos(theta));
        float modRadius = radius * radiusFactor;
        
        return new float[] {
            modRadius * sinTheta * cosPhi,  // X
            radius * length * cosTheta,     // Y (axial)
            modRadius * sinTheta * sinPhi   // Z
        };
    }
    
    /**
     * Egg vertex position (asymmetric - fatter at bottom).
     */
    public static float[] eggVertex(float theta, float phi, float radius, 
            float asymmetry, float length) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Egg profile: wider at bottom (θ=π), narrower at top (θ=0)
        float radiusFactor = 1.0f + asymmetry * (float) Math.cos(theta);
        float modRadius = radius * radiusFactor;
        
        return new float[] {
            modRadius * sinTheta * cosPhi,  // X
            radius * length * cosTheta,     // Y (axial)
            modRadius * sinTheta * sinPhi   // Z
        };
    }
    
    /**
     * Pear vertex position (strong base mass, tapered top).
     * 
     * <p>Uses piriform (pear-shaped) curve profile.</p>
     * 
     * @param theta Polar angle (0 = narrow top, π = wide base)
     * @param phi Azimuthal angle
     * @param radius Base radius at widest point
     * @param intensity Pear-ness (0 = sphere, 1 = strong pear)
     * @param length Axial stretch
     */
    public static float[] pearVertex(float theta, float phi, float radius, 
            float intensity, float length) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Piriform profile: (1 + sin(θ)) * cos(θ) for radial component
        // Creates a pear shape with narrow top and wide bottom
        float normalized = theta / PI;  // 0 to 1
        float pearProfile = (float) Math.pow(normalized, 0.7f + 0.3f * intensity);
        
        // Blend with sphere
        float sphereRadius = radius * sinTheta;
        float pearRadius = radius * pearProfile * sinTheta;
        float finalRadius = sphereRadius * (1 - intensity) + pearRadius * intensity;
        
        return new float[] {
            finalRadius * cosPhi,           // X
            radius * length * cosTheta,     // Y (axial)
            finalRadius * sinPhi            // Z
        };
    }
    
    /**
     * Droplet/teardrop vertex position.
     * 
     * <p>Pointed tip at θ=0, rounded base at θ=π.</p>
     */
    public static float[] dropletVertex(float theta, float phi, float radius, 
            float power, float length) {
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Teardrop profile: (1 - cos(θ)) creates the taper
        float profile = (1.0f - cosTheta) * (float) Math.pow(Math.abs(sinTheta), power);
        float normalizedProfile = profile * radius * 0.5f;
        
        return new float[] {
            normalizedProfile * cosPhi,     // X
            radius * length * cosTheta,     // Y (axial)
            normalizedProfile * sinPhi      // Z
        };
    }
    
    /**
     * Droplet inverted vertex position (pointy at bottom).
     */
    public static float[] dropletInvertedVertex(float theta, float phi, float radius, 
            float power, float length) {
        float[] pos = dropletVertex(PI - theta, phi, radius, power, length);
        pos[1] = -pos[1];  // Flip Y
        return pos;
    }
    
    /**
     * Bullet/capsule-tip vertex position.
     * 
     * <p>Hemisphere on top, cylinder in middle, flat cap at bottom.</p>
     */
    public static float[] bulletVertex(float theta, float phi, float radius, float length) {
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        float x, y, z;
        
        // Use last 10% of theta range for the flat base cap
        float capStart = PI * 0.9f;
        
        if (theta < HALF_PI) {
            // Top hemisphere (0 to π/2)
            float sinTheta = (float) Math.sin(theta);
            float cosTheta = (float) Math.cos(theta);
            
            x = radius * sinTheta * cosPhi;
            y = radius * cosTheta;  // Top part
            z = radius * sinTheta * sinPhi;
        } else if (theta < capStart) {
            // Middle cylinder (π/2 to 0.9π)
            float t = (theta - HALF_PI) / (capStart - HALF_PI);  // 0 to 1
            
            x = radius * cosPhi;
            y = -radius * length * t;  // Extends down based on length
            z = radius * sinPhi;
        } else {
            // Bottom cap (0.9π to π) - flat circular disc
            float t = (theta - capStart) / (PI - capStart);  // 0 to 1
            float capRadius = radius * (1.0f - t);  // Shrinks from full radius to 0
            
            x = capRadius * cosPhi;
            y = -radius * length;  // Fixed at bottom
            z = capRadius * sinPhi;
        }
        
        return new float[] { x, y, z };
    }
    
    /**
     * Cone vertex position.
     * 
     * <p>Tapered sides from tip to base, flat cap at bottom.</p>
     */
    public static float[] coneVertex(float theta, float phi, float radius, float length) {
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Use last 10% of theta range for the flat base cap
        float capStart = PI * 0.9f;
        
        float x, y, z;
        
        if (theta < capStart) {
            // Tapered cone surface (0 to 0.9π)
            // Linear taper: 0 at tip (θ=0), full radius at capStart
            float taper = theta / capStart;
            float ringRadius = radius * taper;
            
            // Y position: tip is at top (y = radius*length), base cap is at bottom (y = -radius*length)
            // Map theta [0, capStart] to y [radius*length, -radius*length]
            float yProgress = theta / capStart;  // 0 to 1
            y = radius * length * (1.0f - 2.0f * yProgress);  // Goes from +length to -length
            
            x = ringRadius * cosPhi;
            z = ringRadius * sinPhi;
        } else {
            // Bottom cap (0.9π to π) - flat circular disc
            float t = (theta - capStart) / (PI - capStart);  // 0 to 1
            float capRadius = radius * (1.0f - t);  // Shrinks from full radius to 0
            
            x = capRadius * cosPhi;
            y = -radius * length;  // Fixed at bottom
            z = capRadius * sinPhi;
        }
        
        return new float[] { x, y, z };
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Normal Calculation (Proper Gradient-Based)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Computes the proper normal for a spheroid at the given position.
     * 
     * <p>For spheroid with equatorial radius a and polar radius c:
     * Normal = normalize(x/a², y/a², z/c²)</p>
     * 
     * <p>Note: For a sphere (a=c), this reduces to normalize(position).</p>
     * 
     * @param x Position X
     * @param y Position Y (polar axis)
     * @param z Position Z
     * @param a Equatorial radius
     * @param c Polar radius (a*length)
     * @return Normalized normal vector {nx, ny, nz}
     */
    public static float[] spheroidNormal(float x, float y, float z, float a, float c) {
        // Gradient of implicit surface: (x/a², y/c², z/a²)
        // Note: Y is the polar axis in our coordinate system
        float a2 = a * a;
        float c2 = c * c;
        
        float nx = x / a2;
        float ny = y / c2;  // Y is polar axis
        float nz = z / a2;
        
        return normalize(new float[] { nx, ny, nz });
    }
    
    /**
     * Normalizes a 3D vector.
     */
    public static float[] normalize(float[] v) {
        float len = (float) Math.sqrt(v[0]*v[0] + v[1]*v[1] + v[2]*v[2]);
        if (len < 0.0001f) return new float[] { 0, 1, 0 };  // Default up
        return new float[] { v[0]/len, v[1]/len, v[2]/len };
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Blending Utilities
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Blends between two vertex positions.
     */
    public static float[] blendVertex(float[] a, float[] b, float t) {
        float inv = 1.0f - t;
        return new float[] {
            a[0] * inv + b[0] * t,
            a[1] * inv + b[1] * t,
            a[2] * inv + b[2] * t
        };
    }
    
    /**
     * Cloud vertex position using multi-octave spherical noise.
     * Legacy overload - delegates to full version with default bumpSize.
     */
    public static float[] cloudVertex(float theta, float phi, float radius, 
            float intensity, float length) {
        return cloudVertex(theta, phi, radius, intensity, length, 6, 0.5f, 0.5f, CloudStyle.GAUSSIAN);
    }
    
    /**
     * Cloud vertex position with count and smoothness (backward compat).
     * Delegates to full version with default bumpSize.
     */
    public static float[] cloudVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness) {
        return cloudVertex(theta, phi, radius, intensity, length, count, smoothness, 0.5f, CloudStyle.GAUSSIAN);
    }
    
    /**
     * Cloud vertex position (backward compat - GAUSSIAN style).
     */
    public static float[] cloudVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness, float bumpSize) {
        return cloudVertex(theta, phi, radius, intensity, length, count, smoothness, bumpSize, CloudStyle.GAUSSIAN, 42);
    }
    
    /**
     * Cloud vertex position with style (backward compat - default seed).
     */
    public static float[] cloudVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness, float bumpSize,
            CloudStyle style) {
        return cloudVertex(theta, phi, radius, intensity, length, count, smoothness, bumpSize, style, 42);
    }
    
    /**
     * Cloud vertex position with MULTIPLE ALGORITHM STYLES.
     * 
     * <p><b>Cloud Styles:</b></p>
     * <ul>
     *   <li><b>GAUSSIAN:</b> Spherical Gaussian bumps - "grape cluster" (original)</li>
     *   <li><b>FRACTAL:</b> Multi-octave fBm noise - organic turbulent surface</li>
     *   <li><b>BILLOWING:</b> Layered puffs + fractal detail - realistic cumulus</li>
     *   <li><b>WORLEY:</b> Cellular noise - puffy cell-like structure</li>
     * </ul>
     * 
     * <p><b>Parameter Meanings by Style:</b></p>
     * <table>
     *   <tr><th>Param</th><th>GAUSSIAN</th><th>FRACTAL</th><th>BILLOWING</th><th>WORLEY</th></tr>
     *   <tr><td>count</td><td>Bump count</td><td>Octaves</td><td>Large puff count</td><td>Cell layers</td></tr>
     *   <tr><td>smoothness</td><td>Bump width</td><td>Persistence</td><td>Blend smoothness</td><td>Cell softness</td></tr>
     *   <tr><td>bumpSize</td><td>Bump height</td><td>Amplitude</td><td>Puff size</td><td>Cell size</td></tr>
     * </table>
     * 
     * @param theta Polar angle (0 = top, π = bottom)
     * @param phi Azimuthal angle (0 to 2π)
     * @param radius Base radius
     * @param intensity Overall effect strength (0 = sphere, 1 = full clouds)
     * @param length Axial stretch (1 = spherical, >1 = elongated)
     * @param count Primary parameter (meaning depends on style)
     * @param smoothness Secondary parameter (meaning depends on style)
     * @param bumpSize Tertiary parameter (meaning depends on style)
     * @param style Cloud algorithm to use
     * @param seed Random seed for reproducibility (0-999)
     * @return {x, y, z} vertex position
     */
    public static float[] cloudVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness, float bumpSize,
            CloudStyle style, int seed) {
        return cloudVertex(theta, phi, radius, intensity, length, 1.0f, count, smoothness, bumpSize, style, seed);
    }
    
    /**
     * Cloud vertex position with FULL PARAMETERS including width for horizontal stretch.
     */
    public static float[] cloudVertex(float theta, float phi, float radius, 
            float intensity, float length, float width, int count, float smoothness, float bumpSize,
            CloudStyle style, int seed) {
        
        // Base trigonometry
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Current vertex direction (unit vector)
        float vx = sinTheta * cosPhi;
        float vy = cosTheta;
        float vz = sinTheta * sinPhi;
        
        // Clamp width
        width = Math.max(0.5f, Math.min(2.0f, width));
        
        // At intensity 0, return pure spheroid with stretch
        if (intensity < 0.01f) {
            return new float[] {
                radius * width * vx,   // Width stretches X
                radius * length * vy,  // Length stretches Y
                radius * width * vz    // Width stretches Z
            };
        }
        
        // Clamp parameters
        count = Math.max(1, Math.min(20, count));
        smoothness = Math.max(0f, Math.min(1f, smoothness));
        bumpSize = Math.max(0.1f, Math.min(2f, bumpSize));
        
        // ======================================================================
        // PROCEDURAL CLOUD GENERATION (like planetVertex but for cumulus)
        // ======================================================================
        
        float displacement = 0;
        
        // Use different algorithms based on style
        displacement = switch (style) {
            case GAUSSIAN -> cloudProceduralPuffs(vx, vy, vz, count, smoothness, bumpSize, seed);
            case FRACTAL -> cloudProceduralFractal(vx, vy, vz, count, smoothness, bumpSize, seed);
            case BILLOWING -> cloudProceduralBillowing(vx, vy, vz, count, smoothness, bumpSize, seed);
            case WORLEY -> cloudProceduralCellular(vx, vy, vz, count, smoothness, bumpSize, seed);
        };
        
        // ======================================================================
        // APPLY DISPLACEMENT
        // ======================================================================
        
        // Scale by intensity
        displacement *= intensity;
        
        // Final radius: base + displacement (ADDITIVE like planet)
        float finalRadius = radius * (1.0f + displacement);
        
        // Apply to position with axial stretches (width for X/Z, length for Y)
        return new float[] {
            finalRadius * width * vx,   // Horizontal width stretch
            finalRadius * length * vy,  // Vertical length stretch
            finalRadius * width * vz    // Horizontal width stretch
        };
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // PROCEDURAL PUFFS: Wyvill metaball-style puffs with Fibonacci distribution
    // Creates distinct rounded bumps like cauliflower
    // ─────────────────────────────────────────────────────────────────────────
    
    private static float cloudProceduralPuffs(float vx, float vy, float vz,
            int count, float smoothness, float bumpSize, int seed) {
        
        float goldenAngle = 2.399963f;
        float totalDisp = 0;
        
        // Puff angular radius (controls how wide each puff is)
        // Higher smoothness = larger puffs = more blending
        float puffAngularRadius = 0.4f + smoothness * 0.5f;  // radians (23° to 51°)
        
        for (int i = 0; i < count; i++) {
            // Fibonacci spiral distribution (even coverage)
            float t = (i + 0.5f) / (float) count;
            float puffTheta = (float) Math.acos(1.0f - 2.0f * t);
            float puffPhi = goldenAngle * i;
            
            // Puff center direction
            float px = (float) Math.sin(puffTheta) * (float) Math.cos(puffPhi);
            float py = (float) Math.cos(puffTheta);
            float pz = (float) Math.sin(puffTheta) * (float) Math.sin(puffPhi);
            
            // Angular distance using dot product
            float dot = vx * px + vy * py + vz * pz;
            float angularDist = (float) Math.acos(Math.max(-1, Math.min(1, dot)));
            
            // Size variation per puff (seeded for reproducibility)
            float sizeVar = 0.6f + 0.8f * (float) Math.pow(Math.sin((i + seed) * 1.3f + 0.7f), 2);
            float thisPuffRadius = puffAngularRadius * sizeVar;
            
            // Wyvill polynomial falloff (smooth, C2-continuous)
            if (angularDist < thisPuffRadius) {
                float r = angularDist / thisPuffRadius;
                float term = 1.0f - r * r;
                float influence = term * term * term;  // (1 - r²)³
                totalDisp += influence * bumpSize * sizeVar;
            }
        }
        
        return totalDisp * 0.3f;
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // PROCEDURAL FRACTAL: Multi-octave fBm noise for organic turbulence
    // Creates rough, natural-looking surface
    // ─────────────────────────────────────────────────────────────────────────
    
    private static float cloudProceduralFractal(float vx, float vy, float vz,
            int octaves, float persistence, float amplitude, int seed) {
        
        // Low frequency base + high frequency detail
        float frequency = 2.0f + amplitude;
        
        // Sample fBm noise at vertex direction
        float noise = SimplexNoise.fBm(
            vx * frequency, 
            vy * frequency, 
            vz * frequency,
            octaves,
            2.0f,           // lacunarity
            persistence,
            seed
        );
        
        // fBm returns [-1, 1], convert to positive displacement
        // Use absolute value for billowy effect (ridges on both sides of zero)
        float absNoise = Math.abs(noise);
        
        // Add a base layer of low-frequency swell
        float baseNoise = SimplexNoise.fBm(
            vx * 1.5f, vy * 1.5f, vz * 1.5f,
            2, 2.0f, 0.5f, seed + 100
        );
        float baseSwell = (baseNoise + 1.0f) * 0.5f;  // [0, 1]
        
        // Combine: base swell modulates the detail
        return (baseSwell * 0.5f + absNoise * 0.5f) * amplitude * 0.5f;
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // PROCEDURAL BILLOWING: Layered puffs + fractal detail = realistic cumulus
    // Best for puffy, cauliflower-like clouds
    // ─────────────────────────────────────────────────────────────────────────
    
    private static float cloudProceduralBillowing(float vx, float vy, float vz,
            int puffCount, float smoothness, float puffSize, int seed) {
        
        float goldenAngle = 2.399963f;
        float totalDisp = 0;
        
        // ─── LAYER 1: Primary large puffs ───
        int primaryCount = Math.max(3, puffCount / 2);
        float primaryRadius = 0.6f + smoothness * 0.4f;  // Wide puffs
        
        for (int i = 0; i < primaryCount; i++) {
            float t = (i + 0.5f) / (float) primaryCount;
            float pTheta = (float) Math.acos(1.0f - 2.0f * t);
            float pPhi = goldenAngle * i;
            
            float px = (float) Math.sin(pTheta) * (float) Math.cos(pPhi);
            float py = (float) Math.cos(pTheta);
            float pz = (float) Math.sin(pTheta) * (float) Math.sin(pPhi);
            
            float dot = vx * px + vy * py + vz * pz;
            float angularDist = (float) Math.acos(Math.max(-1, Math.min(1, dot)));
            
            // Size variation per puff
            float sizeVar = 0.7f + 0.6f * (float) Math.pow(Math.sin((i + seed) * 1.1f), 2);
            float thisRadius = primaryRadius * sizeVar;
            
            if (angularDist < thisRadius) {
                float r = angularDist / thisRadius;
                float term = 1.0f - r * r;
                float influence = term * term * term;
                totalDisp += influence * puffSize * sizeVar * 0.8f;
            }
        }
        
        // ─── LAYER 2: Secondary smaller puffs (fills gaps) ───
        int secondaryCount = puffCount;
        float secondaryRadius = 0.3f + smoothness * 0.2f;
        
        for (int i = 0; i < secondaryCount; i++) {
            float t = (i + 0.5f) / (float) secondaryCount;
            float pTheta = (float) Math.acos(1.0f - 2.0f * t);
            float pPhi = goldenAngle * i * 1.618f;  // Different spiral
            
            float px = (float) Math.sin(pTheta) * (float) Math.cos(pPhi);
            float py = (float) Math.cos(pTheta);
            float pz = (float) Math.sin(pTheta) * (float) Math.sin(pPhi);
            
            float dot = vx * px + vy * py + vz * pz;
            float angularDist = (float) Math.acos(Math.max(-1, Math.min(1, dot)));
            
            float sizeVar = 0.5f + 0.5f * (float) Math.sin((i + seed + 50) * 2.1f);
            float thisRadius = secondaryRadius * sizeVar;
            
            if (angularDist < thisRadius) {
                float r = angularDist / thisRadius;
                float term = 1.0f - r * r;
                float influence = term * term * term;
                totalDisp += influence * puffSize * sizeVar * 0.4f;
            }
        }
        
        // ─── LAYER 3: Fractal surface detail (turbulence) ───
        float detailFreq = 4.0f + (1.0f - smoothness) * 4.0f;
        float detailNoise = SimplexNoise.fBm(
            vx * detailFreq, vy * detailFreq, vz * detailFreq,
            3, 2.0f, 0.5f, seed + 200
        );
        // Add positive bias so detail mostly adds volume, rarely subtracts
        float detail = (detailNoise * 0.5f + 0.5f) * 0.15f * (1.0f - smoothness);
        
        return totalDisp * 0.35f + detail;
    }
    
    // ─────────────────────────────────────────────────────────────────────────
    // PROCEDURAL CELLULAR: Voronoi/Worley noise for puffy cell structure
    // Creates soft, cellular texture
    // ─────────────────────────────────────────────────────────────────────────
    
    private static float cloudProceduralCellular(float vx, float vy, float vz,
            int cellLayers, float softness, float amplitude, int seed) {
        
        float goldenAngle = 2.399963f;
        int cellCount = cellLayers * 6;  // More cells for finer structure
        
        float minDist1 = Float.MAX_VALUE;
        float minDist2 = Float.MAX_VALUE;
        
        for (int i = 0; i < cellCount; i++) {
            // Cell center (Fibonacci spiral)
            float t = (i + 0.5f) / (float) cellCount;
            float cTheta = (float) Math.acos(1.0f - 2.0f * t);
            float cPhi = goldenAngle * i;
            
            float cx = (float) Math.sin(cTheta) * (float) Math.cos(cPhi);
            float cy = (float) Math.cos(cTheta);
            float cz = (float) Math.sin(cTheta) * (float) Math.sin(cPhi);
            
            // Angular distance
            float dot = vx * cx + vy * cy + vz * cz;
            float angularDist = (float) Math.acos(Math.max(-1, Math.min(1, dot)));
            
            // Track two nearest cells
            if (angularDist < minDist1) {
                minDist2 = minDist1;
                minDist1 = angularDist;
            } else if (angularDist < minDist2) {
                minDist2 = angularDist;
            }
        }
        
        // Cell angular radius
        float cellRadius = (float) Math.PI / (float) Math.sqrt(cellCount) * 1.5f;
        
        // F1 pattern: height based on proximity to cell center (puffy centers)
        float f1 = 1.0f - Math.min(1.0f, minDist1 / cellRadius);
        f1 = f1 * f1;  // Square for rounder falloff
        
        // F2-F1 pattern: highlights boundaries between cells
        float f2f1 = Math.min(1.0f, (minDist2 - minDist1) / (cellRadius * 0.5f));
        
        // Blend based on softness: high softness = puffy (f1), low = cellular (f2-f1)
        float pattern = softness * f1 + (1.0f - softness) * (f1 * 0.5f + f2f1 * 0.3f);
        
        return pattern * amplitude * 0.4f;
    }

    // =========================================================================
    // PLANET DEFORMATION
    // =========================================================================
    
    /**
     * Planet vertex position using PROCEDURAL TERRAIN GENERATION.
     * 
     * <p><b>Algorithm:</b> Combines fractal Brownian motion (fBm) noise with
     * optional ridged multifractal for mountains and crater profiles for
     * impact features.</p>
     * 
     * <p><b>Key Features:</b></p>
     * <ul>
     *   <li>Terrain: fBm noise for continents and hills</li>
     *   <li>Mountains: Ridged multifractal for sharp peaks (ridged > 0)</li>
     *   <li>Craters: Paraboloid depressions with raised rims (craterCount > 0)</li>
     * </ul>
     * 
     * @param theta Polar angle (0 = top, π = bottom)
     * @param phi Azimuthal angle (0 to 2π)
     * @param radius Base radius
     * @param intensity Overall terrain prominence (0 = sphere, 1 = full terrain)
     * @param length Axial stretch
     * @param frequency Base noise scale (0.5-10)
     * @param octaves Noise detail layers (1-8)
     * @param lacunarity Frequency growth per octave (1.5-3.5)
     * @param persistence Amplitude decay per octave (0.2-0.8)
     * @param ridged Mountain sharpness (0 = rolling hills, 1 = sharp ridges)
     * @param craterCount Number of impact craters (0-20)
     * @param seed Random seed for reproducibility
     * @return {x, y, z} vertex position
     */
    public static float[] planetVertex(float theta, float phi, float radius,
            float intensity, float length,
            float frequency, int octaves, float lacunarity, float persistence,
            float ridged, int craterCount, int seed) {
        
        // Base trigonometry
        float sinTheta = (float) Math.sin(theta);
        float cosTheta = (float) Math.cos(theta);
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        
        // Current vertex direction (unit vector on sphere)
        float vx = sinTheta * cosPhi;
        float vy = cosTheta;
        float vz = sinTheta * sinPhi;
        
        // At intensity 0, return pure spheroid
        if (intensity < 0.01f) {
            return new float[] {
                radius * vx,
                radius * length * vy,
                radius * vz
            };
        }
        
        // Clamp parameters
        octaves = Math.max(1, Math.min(8, octaves));
        frequency = Math.max(0.5f, Math.min(10f, frequency));
        lacunarity = Math.max(1.5f, Math.min(3.5f, lacunarity));
        persistence = Math.max(0.2f, Math.min(0.8f, persistence));
        ridged = Math.max(0f, Math.min(1f, ridged));
        craterCount = Math.max(0, Math.min(20, craterCount));
        
        // ======================================================================
        // TERRAIN GENERATION
        // ======================================================================
        
        float displacement = 0;
        
        // Sample noise at vertex position (scaled by frequency)
        float nx = vx * frequency;
        float ny = vy * frequency;
        float nz = vz * frequency;
        
        // Use terrain noise (blends fBm and ridged based on ridged parameter)
        float terrainValue = SimplexNoise.terrainNoise(
            nx, ny, nz,
            octaves, lacunarity, persistence,
            ridged, seed
        );
        
        // Scale terrain to reasonable displacement range
        displacement += terrainValue * 0.3f;
        
        // ======================================================================
        // CRATER GENERATION
        // ======================================================================
        
        if (craterCount > 0) {
            float craterDisp = 0;
            float goldenAngle = 2.399963f;
            
            for (int i = 0; i < craterCount; i++) {
                // Crater center position (Fibonacci spiral for even distribution)
                float t = (i + 0.5f) / (float) craterCount;
                float craterTheta = (float) Math.acos(1.0f - 2.0f * t);
                float craterPhi = goldenAngle * i;
                
                // Crater direction
                float cx = (float) Math.sin(craterTheta) * (float) Math.cos(craterPhi);
                float cy = (float) Math.cos(craterTheta);
                float cz = (float) Math.sin(craterTheta) * (float) Math.sin(craterPhi);
                
                // Dot product = cos(angle) between vertex and crater center
                float dot = vx * cx + vy * cy + vz * cz;
                
                // Angular distance (0 when aligned, π when opposite)
                float angularDist = (float) Math.acos(Math.max(-1, Math.min(1, dot)));
                
                // Crater size varies by index (pseudo-random via seed)
                float sizeVariation = 0.7f + 0.6f * (float) Math.sin((i + seed) * 1.7f);
                float craterRadius = 0.25f * sizeVariation;  // Angular radius in radians
                
                // Crater depth (depth-to-radius ratio ~0.3 for realistic craters)
                float craterDepth = 0.15f * sizeVariation;
                float rimHeight = craterDepth * 0.3f;
                
                // Apply crater profile
                craterDisp += craterProfile(angularDist, craterRadius, craterDepth, rimHeight);
            }
            
            displacement += craterDisp;
        }
        
        // ======================================================================
        // APPLY DISPLACEMENT
        // ======================================================================
        
        // Scale by intensity
        displacement *= intensity;
        
        // Final radius: base + displacement (ADDITIVE)
        float finalRadius = radius * (1.0f + displacement);
        
        // Apply to position with axial stretch
        return new float[] {
            finalRadius * vx,
            finalRadius * length * vy,
            finalRadius * vz
        };
    }
    
    /**
     * Crater profile function - paraboloid depression with raised rim.
     * 
     * @param distance Angular distance from crater center (radians)
     * @param craterRadius Angular radius of crater (radians)
     * @param depth Maximum depression depth
     * @param rimHeight Height of raised rim
     * @return Displacement value (negative = depression, positive = rim)
     */
    private static float craterProfile(float distance, float craterRadius, float depth, float rimHeight) {
        // Normalized distance (0 at center, 1 at rim)
        float r = distance / craterRadius;
        
        if (r > 2.0f) {
            return 0;  // Outside influence
        }
        
        if (r <= 1.0f) {
            // Inside crater: paraboloid depression
            // At center (r=0): -depth
            // At rim (r=1): 0
            return -depth * (1.0f - r * r);
        } else {
            // Rim and ejecta blanket (r > 1, up to 2)
            float rimFalloff = r - 1.0f;  // 0 at rim, 1 at edge
            // Raised rim with exponential falloff
            return rimHeight * (float) Math.exp(-rimFalloff * 3.0f) * (1.0f - rimFalloff);
        }
    }
}

