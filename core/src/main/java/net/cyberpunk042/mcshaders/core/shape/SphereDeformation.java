/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 *
 * JSON binding was removed on the way across: the model stays free of
 * serialisation so it can be loaded from external content. The @JsonField
 * metadata is retained for a codec layer above core.
 */
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Controls deformation of sphere shapes into other polar forms.
 * 
 * <p>Uses parametric shape functions from {@link ShapeMath} to compute
 * vertex positions for various organic and geometric shapes.</p>
 * 
 * <h2>Shape Categories</h2>
 * <ul>
 *   <li><b>Symmetric:</b> SPHEROID (oblate/prolate)</li>
 *   <li><b>Organic:</b> OVOID, EGG, PEAR</li>
 *   <li><b>Directional:</b> DROPLET, BULLET, CONE</li>
 * </ul>
 * 
 * @see SphereShape
 * @see ShapeMath
 */
public enum SphereDeformation {
    
    // ─── Symmetric ───
    /** No deformation - standard sphere. */
    NONE("None", "Standard sphere"),
    
    /** Spheroid - sphere stretched/squashed along Y axis. */
    SPHEROID("Spheroid", "Stretched (prolate) or squashed (oblate) sphere"),
    
    // ─── Organic/Natural ───
    /** Ovoid - smooth egg-like, softer asymmetry. */
    OVOID("Ovoid", "Smooth egg-like shape"),
    
    /** Egg shape - asymmetric, fatter at bottom. */
    EGG("Egg", "Egg shape (fatter bottom)"),
    
    /** Pear shape - strong base mass, tapered top. */
    PEAR("Pear", "Pear shape (wide base, narrow top)"),
    
    // ─── Directional/Flow ───
    /** Droplet/teardrop shape - pointy at top, fat at bottom. */
    DROPLET("Droplet", "Teardrop (pointy top)"),
    
    /** Droplet reversed - fat at top, pointy at bottom. */
    DROPLET_INVERTED("Droplet ↓", "Teardrop (pointy bottom)"),
    
    /** Bullet shape - hemispherical tip, cylindrical body. */
    BULLET("Bullet", "Rounded tip + cylinder"),
    
    /** Cone - pointy tip, wide base. */
    CONE("Cone", "Conical taper"),
    
    // ─── Environmental/Atmospheric ───
    /** Cloud - fluffy, billowing cumulus-like shape. */
    CLOUD("Cloud", "Fluffy cumulus cloud"),
    
    // ─── Planetary ───
    /** Planet - procedural terrain with continents, craters, and mountains. */
    PLANET("Planet", "Procedural terrain (continents, craters, mountains)");
    
    private final String displayName;
    private final String description;
    
    SphereDeformation(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String displayName() { return displayName; }
    public String description() { return description; }
    
    /**
     * Computes the deformed vertex position for the given spherical coordinates.
     * 
     * <p>Uses proper parametric equations from {@link ShapeMath}.</p>
     * 
     * @param theta Polar angle (0 = top, π = bottom)
     * @param phi Azimuthal angle (0 to 2π)
     * @param radius Base radius
     * @param intensity Deformation intensity (0 = sphere, 1 = full effect)
     * @param length Axial stretch factor (1 = normal, >1 = elongated, <1 = squashed)
     * @return {x, y, z} vertex position
     */
    public float[] computeVertex(float theta, float phi, float radius, 
            float intensity, float length) {
        // Delegate to full version with default parameters
        return computeVertex(theta, phi, radius, intensity, length, 6, 0.5f, 0.5f);
    }
    
    /**
     * Computes the deformed vertex position with count and smoothness.
     * Backward compatibility - delegates to full version.
     */
    public float[] computeVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness) {
        return computeVertex(theta, phi, radius, intensity, length, count, smoothness, 0.5f,
            2f, 4, 2f, 0.5f, 0f, 0, 42, CloudStyle.GAUSSIAN, 42, 1.0f);
    }
    
    /**
     * Computes the deformed vertex position with full control for CLOUD.
     * Backward compatibility - delegates to full version with planet defaults.
     */
    public float[] computeVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness,
            float bumpSize) {
        return computeVertex(theta, phi, radius, intensity, length, count, smoothness, bumpSize,
            2f, 4, 2f, 0.5f, 0f, 0, 42, CloudStyle.GAUSSIAN, 42, 1.0f);
    }
    
    /**
     * Computes the deformed vertex position with FULL control including PLANET.
     * 
     * @param theta Polar angle (0 = top, π = bottom)
     * @param phi Azimuthal angle (0 to 2π)
     * @param radius Base radius
     * @param intensity Deformation intensity (0 = sphere, 1 = full effect)
     * @param length Axial stretch factor
     * @param count Number of lobes for CLOUD, 1-20
     * @param smoothness Roundness of bumps (0 = sharp, 1 = smooth)
     * @param bumpSize Size of individual bumps (0.1-2.0)
     * @param planetFrequency Base noise frequency for PLANET (0.5-10)
     * @param planetOctaves Noise octaves for PLANET (1-8)
     * @param planetLacunarity Frequency multiplier for PLANET (1.5-3.5)
     * @param planetPersistence Amplitude decay for PLANET (0.2-0.8)
     * @param planetRidged Mountain sharpness for PLANET (0-1)
     * @param planetCraterCount Number of craters for PLANET (0-20)
     * @param planetSeed Random seed for PLANET (0-999)
     * @param cloudStyle Cloud algorithm style (GAUSSIAN, FRACTAL, BILLOWING, WORLEY)
     * @param cloudSeed Random seed for CLOUD (0-999)
     * @param cloudWidth Horizontal stretch for CLOUD (0.5-2.0)
     * @return {x, y, z} vertex position
     */
    public float[] computeVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness,
            float bumpSize,
            float planetFrequency, int planetOctaves, float planetLacunarity, float planetPersistence,
            float planetRidged, int planetCraterCount, int planetSeed, 
            CloudStyle cloudStyle, int cloudSeed, float cloudWidth) {
        // Get sphere position for blending
        float[] spherePos = ShapeMath.sphereVertex(theta, phi, radius);
        
        if (intensity <= 0.0f || this == NONE) {
            // Apply only length stretch to sphere
            if (length != 1.0f) {
                return ShapeMath.spheroidVertex(theta, phi, radius, length);
            }
            return spherePos;
        }
        
        // Get target shape position
        float[] shapePos = switch (this) {
            case NONE -> spherePos;
            
            case SPHEROID -> {
                // SPHEROID: Independent control of polar stretch and equatorial bulge
                // - length controls polar axis (height: <1 = squashed, >1 = stretched)
                // - intensity controls equatorial bulge (width: 0 = sphere, 1 = 50% wider)
                // This creates the spinning planet effect (centrifugal bulge at equator)
                float bulge = intensity * 0.5f;  // 0 to 50% equatorial bulge
                yield ShapeMath.spheroidWithBulge(theta, phi, radius, length, bulge);
            }
            
            case OVOID -> {
                float asymmetry = intensity * 0.3f;  // 0 to 0.3
                yield ShapeMath.ovoidVertex(theta, phi, radius, asymmetry, length);
            }
            
            case EGG -> {
                float asymmetry = intensity * 0.4f;  // 0 to 0.4
                yield ShapeMath.eggVertex(theta, phi, radius, asymmetry, length);
            }
            
            case PEAR -> ShapeMath.pearVertex(theta, phi, radius, intensity, length);
            
            case DROPLET -> {
                float power = 0.5f + intensity * 1.5f;  // 0.5 to 2.0
                yield ShapeMath.dropletVertex(theta, phi, radius, power, length);
            }
            
            case DROPLET_INVERTED -> {
                float power = 0.5f + intensity * 1.5f;
                yield ShapeMath.dropletInvertedVertex(theta, phi, radius, power, length);
            }
            
            case BULLET -> ShapeMath.bulletVertex(theta, phi, radius, length);
            
            case CONE -> ShapeMath.coneVertex(theta, phi, radius, length);
            
            case CLOUD -> ShapeMath.cloudVertex(theta, phi, radius, intensity, length, cloudWidth, count, smoothness, bumpSize, cloudStyle, cloudSeed);
            
            case PLANET -> ShapeMath.planetVertex(theta, phi, radius, intensity, length,
                planetFrequency, planetOctaves, planetLacunarity, planetPersistence,
                planetRidged, planetCraterCount, planetSeed);
        };
        
        // For SPHEROID, CLOUD, PLANET - intensity is already applied, return directly
        if (this == SPHEROID || this == CLOUD || this == PLANET) {
            return shapePos;
        }
        
        // Blend between sphere (with length) and target shape
        float[] baseSphere = length != 1.0f 
            ? ShapeMath.spheroidVertex(theta, phi, radius, length) 
            : spherePos;
        return ShapeMath.blendVertex(baseSphere, shapePos, intensity);
    }
    
    /**
     * Computes BOTH position AND normal for proper lighting.
     * Delegates to full version with default parameters.
     */
    public float[] computeFullVertex(float theta, float phi, float radius, 
            float intensity, float length) {
        return computeFullVertex(theta, phi, radius, intensity, length, 6, 0.5f, 0.5f,
            2f, 4, 2f, 0.5f, 0f, 0, 42, CloudStyle.GAUSSIAN, 42, 1.0f);
    }
    
    /**
     * Computes BOTH position AND normal for proper lighting with count and smoothness.
     * Backward compatibility - delegates to full version.
     */
    public float[] computeFullVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness) {
        return computeFullVertex(theta, phi, radius, intensity, length, count, smoothness, 0.5f,
            2f, 4, 2f, 0.5f, 0f, 0, 42, CloudStyle.GAUSSIAN, 42, 1.0f);
    }
    
    /**
     * Computes BOTH position AND normal - backward compatible.
     */
    public float[] computeFullVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness,
            float bumpSize) {
        return computeFullVertex(theta, phi, radius, intensity, length, count, smoothness, bumpSize,
            2f, 4, 2f, 0.5f, 0f, 0, 42, CloudStyle.GAUSSIAN, 42, 1.0f);
    }
    
    /**
     * Computes BOTH position AND normal for proper lighting with full control including PLANET.
     * 
     * <p>For spheroids, normals ≠ normalize(position). Uses gradient-based normals.</p>
     * 
     * @return {x, y, z, nx, ny, nz} - 6 element array
     */
    public float[] computeFullVertex(float theta, float phi, float radius, 
            float intensity, float length, int count, float smoothness,
            float bumpSize,
            float planetFrequency, int planetOctaves, float planetLacunarity, float planetPersistence,
            float planetRidged, int planetCraterCount, int planetSeed, 
            CloudStyle cloudStyle, int cloudSeed, float cloudWidth) {
        float[] pos = computeVertex(theta, phi, radius, intensity, length, count, smoothness, bumpSize,
            planetFrequency, planetOctaves, planetLacunarity, planetPersistence, planetRidged, planetCraterCount, planetSeed, 
            cloudStyle, cloudSeed, cloudWidth);
        
        // Compute proper normal based on shape type
        float[] normal;
        
        float a, c;
        if (this == SPHEROID) {
            // SPHEROID uses spheroidWithBulge: independent bulge and length
            // c = radius * length (polar axis controlled by length)
            // a = radius * (1 + bulge) where bulge = intensity * 0.5
            float bulge = intensity * 0.5f;
            c = radius * length;
            a = radius * (1.0f + bulge);
        } else {
            // Other shapes use volume-preserving spheroid formula
            c = radius * length;
            a = radius / (float) Math.sqrt(length);
        }
        
        if (this == NONE || this == SPHEROID) {
            // Use proper spheroid normal with correct a and c
            normal = ShapeMath.spheroidNormal(pos[0], pos[1], pos[2], a, c);
        } else {
            // For other shapes, approximate with spheroid normal
            normal = ShapeMath.spheroidNormal(pos[0], pos[1], pos[2], a, c);
        }
        
        return new float[] { 
            pos[0], pos[1], pos[2],
            normal[0], normal[1], normal[2] 
        };
    }
    
    /**
     * Simplified computeVertex without length (uses length = 1.0).
     */
    public float[] computeVertex(float theta, float phi, float radius, float intensity) {
        return computeVertex(theta, phi, radius, intensity, 1.0f);
    }
    
    /**
     * Computes the radius multiplier (legacy method).
     */
    public float computeRadiusFactor(float theta, float intensity) {
        if (intensity <= 0.0f || this == NONE) {
            return 1.0f;
        }
        
        return switch (this) {
            case NONE, SPHEROID -> ShapeMath.sphere(theta);
            case OVOID, EGG -> ShapeMath.egg(theta, intensity * 0.4f);
            case PEAR -> ShapeMath.egg(theta, intensity * 0.5f);
            case DROPLET -> ShapeMath.droplet(theta, 1.0f + intensity * 2.0f);
            case DROPLET_INVERTED -> ShapeMath.dropletInverted(theta, 1.0f + intensity * 2.0f);
            case BULLET -> ShapeMath.bullet(theta);
            case CONE -> ShapeMath.blend(ShapeMath.cone(theta), intensity);
            case CLOUD -> 1.0f + intensity * 0.3f;  // Slight average bulge for legacy
            case PLANET -> 1.0f + intensity * 0.15f;  // Average terrain for planet
        };
    }
    
    /**
     * Whether this deformation creates a pointy end.
     */
    public boolean hasPointyEnd() {
        return this == DROPLET || this == DROPLET_INVERTED || this == CONE || this == PEAR;
    }
}
