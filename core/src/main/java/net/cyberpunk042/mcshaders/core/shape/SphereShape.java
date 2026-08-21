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

import net.cyberpunk042.mcshaders.core.pattern.CellType;
import org.joml.Vector3f;

import java.util.Map;
import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import net.cyberpunk042.mcshaders.core.serial.JsonField;

/**
 * Sphere shape with configurable tessellation.
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "shape": {
 *   "type": "sphere",
 *   "radius": 1.0,
 *   "latSteps": 32,
 *   "lonSteps": 64,
 *   "latStart": 0.0,
 *   "latEnd": 1.0,
 *   "algorithm": "LAT_LON"
 * }
 * </pre>
 * 
 * <h2>Lat/Lon Range (0-1 normalized)</h2>
 * <ul>
 *   <li>latStart=0.0 → top (north pole)</li>
 *   <li>latEnd=1.0 → bottom (south pole)</li>
 *   <li>lonStart=0.0 → 0°</li>
 *   <li>lonEnd=1.0 → 360°</li>
 * </ul>
 * 
 * <h2>Parts</h2>
 * <ul>
 *   <li><b>main</b> (QUAD) - Main sphere surface</li>
 *   <li><b>poles</b> (TRIANGLE) - Top/bottom pole caps</li>
 *   <li><b>equator</b> (QUAD) - Equatorial band</li>
 *   <li><b>hemisphereTop</b> (QUAD) - Top half</li>
 *   <li><b>hemisphereBottom</b> (QUAD) - Bottom half</li>
 * </ul>
 * 
 * @see SphereAlgorithm
 */
public record SphereShape(
    @Range(ValueRange.RADIUS) float radius,
    @Range(ValueRange.STEPS) int latSteps,
    @Range(ValueRange.STEPS) int lonSteps,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float latStart,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1") float latEnd,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float lonStart,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1") float lonEnd,
    @JsonField(skipIfEqualsConstant = "LAT_LON") SphereAlgorithm algorithm,
    // === Deformation (transform sphere into droplet, egg, cloud, molecule, planet, etc.) ===
    @JsonField(skipIfDefault = true) SphereDeformation deformation,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float deformationIntensity,
    /** Axial stretch: 1.0 = normal, >1 = elongated (prolate), <1 = squashed (oblate) */
    @JsonField(skipIfDefault = true, defaultValue = "1") float deformationLength,
    /** Number of lobes for CLOUD (1-20, default 6) */
    @JsonField(skipIfDefault = true, defaultValue = "6") int deformationCount,
    /** Smoothness for rounding spikes in CLOUD (0 = sharp, 1 = smooth, default 0.5) */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "0.5") float deformationSmoothness,
    /** Size of individual bumps for CLOUD (0.1-2.0, default 0.5). CLOUD: bump prominence. */
    @JsonField(skipIfDefault = true, defaultValue = "0.5") float deformationBumpSize,
    /** CLOUD: Algorithm style (GAUSSIAN, FRACTAL, BILLOWING, WORLEY). Default GAUSSIAN. */
    @JsonField(skipIfEqualsConstant = "GAUSSIAN") CloudStyle cloudStyle,
    /** CLOUD: Random seed for reproducibility (0-999, default 42). */
    @JsonField(skipIfDefault = true, defaultValue = "42") int cloudSeed,
    /** CLOUD: Horizontal stretch (0.5-2.0, default 1). >1 = wider, flatter cloud. */
    @JsonField(skipIfDefault = true, defaultValue = "1") float cloudWidth,
    // === PLANET-specific parameters ===
    /** PLANET: Base noise frequency (0.5-10, default 2). Higher = more detail at smaller scale. */
    @JsonField(skipIfDefault = true, defaultValue = "2") float planetFrequency,
    /** PLANET: Number of noise octaves (1-8, default 4). More = finer detail. */
    @JsonField(skipIfDefault = true, defaultValue = "4") int planetOctaves,
    /** PLANET: Frequency multiplier per octave (1.5-3.5, default 2). Controls complexity. */
    @JsonField(skipIfDefault = true, defaultValue = "2") float planetLacunarity,
    /** PLANET: Amplitude decay per octave (0.2-0.8, default 0.5). Lower = smoother terrain. */
    @JsonField(skipIfDefault = true, defaultValue = "0.5") float planetPersistence,
    /** PLANET: Mountain sharpness (0-1, default 0). 0 = rolling hills, 1 = sharp ridges. */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float planetRidged,
    /** PLANET: Number of craters (0-20, default 0). Impact features on surface. */
    @JsonField(skipIfDefault = true) int planetCraterCount,
    /** PLANET: Random seed for reproducibility (0-999, default 42). */
    @JsonField(skipIfDefault = true, defaultValue = "42") int planetSeed,
    // === HORIZON effect (rim/edge glow) ===
    /** HORIZON: Enable rim lighting effect (default false). */
    @JsonField(skipIfDefault = true) boolean horizonEnabled,
    /** HORIZON: Edge sharpness (1-10, default 3). Lower = softer glow, higher = sharper edge. */
    @JsonField(skipIfDefault = true, defaultValue = "3") float horizonPower,
    /** HORIZON: Brightness multiplier (0-5, default 1.5). 0 = off, higher = brighter. */
    @JsonField(skipIfDefault = true, defaultValue = "1.5") float horizonIntensity,
    /** HORIZON: Rim color red component (0-1, default 1). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1") float horizonRed,
    /** HORIZON: Rim color green component (0-1, default 1). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1") float horizonGreen,
    /** HORIZON: Rim color blue component (0-1, default 1). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1") float horizonBlue,
    // === CORONA effect (additive overlay glow) ===
    /** CORONA: Enable additive glow overlay (default false). */
    @JsonField(skipIfDefault = true) boolean coronaEnabled,
    /** CORONA: Edge sharpness (1-10, default 2). Lower = wider glow, higher = tighter edge. */
    @JsonField(skipIfDefault = true, defaultValue = "2") float coronaPower,
    /** CORONA: Brightness multiplier (0-5, default 1). 0 = off, higher = brighter. */
    @JsonField(skipIfDefault = true, defaultValue = "1") float coronaIntensity,
    /** CORONA: Falloff rate (0.1-2, default 0.5). Lower = wider glow spread. */
    @JsonField(skipIfDefault = true, defaultValue = "0.5") float coronaFalloff,
    /** CORONA: Glow color red component (0-1, default 1). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1") float coronaRed,
    /** CORONA: Glow color green component (0-1, default 1). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1") float coronaGreen,
    /** CORONA: Glow color blue component (0-1, default 1). */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1") float coronaBlue,
    /** CORONA: Vertical offset of glow layer (-1 to 1, default 0). Positive = outward. */
    @JsonField(skipIfDefault = true) float coronaOffset,
    /** CORONA: Width/spread of the glow (0.1-3, default 1). Higher = wider glow band. */
    @JsonField(skipIfDefault = true, defaultValue = "1") float coronaWidth
) implements Shape {
    public static final String DEFAULT_ALGORITHM = "uv";

    
    /** Default sphere (1.0 radius, medium detail, full sphere). */
    public static SphereShape of(float radius) { 
        return new SphereShape(radius, 16, 32, 0f, 1f, 0f, 1f, SphereAlgorithm.values()[0], 
            SphereDeformation.NONE, 0f, 1f, 6, 0.5f, 0.5f, CloudStyle.GAUSSIAN, 42, 1.0f,
            2f, 4, 2f, 0.5f, 0f, 0, 42,
            false, 3f, 1.5f, 1f, 1f, 1f,  // horizon defaults
            false, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f); // corona defaults (+ offset, width)
    }
    public static SphereShape defaults() { return DEFAULT; }
    
    public static final SphereShape DEFAULT = new SphereShape(
        1.0f, 32, 64, 0.0f, 1.0f, 0.0f, 1.0f, SphereAlgorithm.LAT_LON,
        SphereDeformation.NONE, 0f, 1f, 6, 0.5f, 0.5f, CloudStyle.GAUSSIAN, 42, 1.0f,
        2f, 4, 2f, 0.5f, 0f, 0, 42,
        false, 3f, 1.5f, 1f, 1f, 1f,
        false, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f);
    
    /** Low-poly sphere for performance. */
    public static final SphereShape LOW_POLY = new SphereShape(
        1.0f, 8, 16, 0.0f, 1.0f, 0.0f, 1.0f, SphereAlgorithm.LAT_LON,
        SphereDeformation.NONE, 0f, 1f, 6, 0.5f, 0.5f, CloudStyle.GAUSSIAN, 42, 1.0f,
        2f, 4, 2f, 0.5f, 0f, 0, 42,
        false, 3f, 1.5f, 1f, 1f, 1f,
        false, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f);
    
    /** High-detail sphere. */
    public static final SphereShape HIGH_DETAIL = new SphereShape(
        1.0f, 64, 128, 0.0f, 1.0f, 0.0f, 1.0f, SphereAlgorithm.LAT_LON,
        SphereDeformation.NONE, 0f, 1f, 6, 0.5f, 0.5f, CloudStyle.GAUSSIAN, 42, 1.0f,
        2f, 4, 2f, 0.5f, 0f, 0, 42,
        false, 3f, 1.5f, 1f, 1f, 1f,
        false, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f);
    
    /**
     * Creates a simple sphere with default tessellation.
     * @param radius Sphere radius
     */
    public static SphereShape ofRadius(@Range(ValueRange.RADIUS) float radius) {
        return new SphereShape(radius, 32, 64, 0.0f, 1.0f, 0.0f, 1.0f, SphereAlgorithm.LAT_LON,
            SphereDeformation.NONE, 0f, 1f, 6, 0.5f, 0.5f, CloudStyle.GAUSSIAN, 42, 1.0f,
            2f, 4, 2f, 0.5f, 0f, 0, 42,
            false, 3f, 1.5f, 1f, 1f, 1f,
            false, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f);
    }
    
    /**
     * Creates a top hemisphere (top half of sphere).
     * @param radius Sphere radius
     */
    public static SphereShape hemisphereTop(@Range(ValueRange.RADIUS) float radius) {
        return new SphereShape(radius, 16, 64, 0.0f, 0.5f, 0.0f, 1.0f, SphereAlgorithm.LAT_LON,
            SphereDeformation.NONE, 0f, 1f, 6, 0.5f, 0.5f, CloudStyle.GAUSSIAN, 42, 1.0f,
            2f, 4, 2f, 0.5f, 0f, 0, 42,
            false, 3f, 1.5f, 1f, 1f, 1f,
            false, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f);
    }
    
    /**
     * Creates a bottom hemisphere (bottom half of sphere).
     * @param radius Sphere radius
     */
    public static SphereShape hemisphereBottom(@Range(ValueRange.RADIUS) float radius) {
        return new SphereShape(radius, 16, 64, 0.5f, 1.0f, 0.0f, 1.0f, SphereAlgorithm.LAT_LON,
            SphereDeformation.NONE, 0f, 1f, 6, 0.5f, 0.5f, CloudStyle.GAUSSIAN, 42, 1.0f,
            2f, 4, 2f, 0.5f, 0f, 0, 42,
            false, 3f, 1.5f, 1f, 1f, 1f,
            false, 2f, 1f, 0.5f, 1f, 1f, 1f, 0f, 1f);
    }
    
    @Override
    public String getType() {
        return "sphere";
    }
    
    @Override
    public Vector3f getBounds() {
        float d = radius * 2;
        return new Vector3f(d, d, d);
    }
    
    @Override
    public CellType primaryCellType() {
        return CellType.QUAD;
    }
    
    @Override
    public Map<String, CellType> getParts() {
        return Map.of(
            "main", CellType.QUAD,
            "poles", CellType.TRIANGLE,
            "equator", CellType.QUAD,
            "hemisphereTop", CellType.QUAD,
            "hemisphereBottom", CellType.QUAD
        );
    }
    
    @Override
    public float getRadius() {
        return radius;
    }
    
    /** Whether this is a full sphere (lat 0-1, lon 0-1). */
    public boolean isFullSphere() {
        return latStart == 0.0f && latEnd == 1.0f && lonStart == 0.0f && lonEnd == 1.0f;
    }
    

    // =========================================================================
    // JSON Parsing
    // =========================================================================
    
    
    
    

    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this shape's values. */
    public Builder toBuilder() {
        return new Builder()
            .radius(radius)
            .latSteps(latSteps)
            .lonSteps(lonSteps)
            .latStart(latStart)
            .latEnd(latEnd)
            .lonStart(lonStart)
            .lonEnd(lonEnd)
            .algorithm(algorithm)
            .deformation(deformation)
            .deformationIntensity(deformationIntensity)
            .deformationLength(deformationLength)
            .deformationCount(deformationCount)
            .deformationSmoothness(deformationSmoothness)
            .deformationBumpSize(deformationBumpSize)
            .cloudStyle(cloudStyle)
            .cloudSeed(cloudSeed)
            .cloudWidth(cloudWidth)
            .planetFrequency(planetFrequency)
            .planetOctaves(planetOctaves)
            .planetLacunarity(planetLacunarity)
            .planetPersistence(planetPersistence)
            .planetRidged(planetRidged)
            .planetCraterCount(planetCraterCount)
            .planetSeed(planetSeed)
            .horizonEnabled(horizonEnabled)
            .horizonPower(horizonPower)
            .horizonIntensity(horizonIntensity)
            .horizonRed(horizonRed)
            .horizonGreen(horizonGreen)
            .horizonBlue(horizonBlue)
            .coronaEnabled(coronaEnabled)
            .coronaPower(coronaPower)
            .coronaIntensity(coronaIntensity)
            .coronaFalloff(coronaFalloff)
            .coronaRed(coronaRed)
            .coronaGreen(coronaGreen)
            .coronaBlue(coronaBlue)
            .coronaOffset(coronaOffset)
            .coronaWidth(coronaWidth);
    }
    
    /** Returns effective deformation, defaulting to NONE if null. */
    public SphereDeformation effectiveDeformation() {
        return deformation != null ? deformation : SphereDeformation.NONE;
    }
    
    /** Whether deformation is active. */
    public boolean hasDeformation() {
        return deformation != null && deformation != SphereDeformation.NONE && deformationIntensity > 0;
    }
    
    /** 
     * Creates a HorizonEffect from this shape's horizon parameters.
     * @return HorizonEffect representing the rim glow settings
     */
    public net.cyberpunk042.mcshaders.core.effect.HorizonEffect toHorizonEffect() {
        return new net.cyberpunk042.mcshaders.core.effect.HorizonEffect(
            horizonEnabled, horizonPower, horizonIntensity,
            horizonRed, horizonGreen, horizonBlue
        );
    }
    
    /** Whether horizon effect is enabled for this shape. */
    public boolean hasHorizonEffect() {
        return horizonEnabled && horizonIntensity > 0;
    }
    
    /** 
     * Creates a CoronaEffect from this shape's corona parameters.
     * @return CoronaEffect representing the additive overlay glow settings
     */
    public net.cyberpunk042.mcshaders.core.effect.CoronaEffect toCoronaEffect() {
        return new net.cyberpunk042.mcshaders.core.effect.CoronaEffect(
            coronaEnabled, coronaPower, coronaIntensity, coronaFalloff,
            coronaRed, coronaGreen, coronaBlue, coronaOffset, coronaWidth
        );
    }
    
    /** Whether corona effect is enabled for this shape. */
    public boolean hasCoronaEffect() {
        return coronaEnabled && coronaIntensity > 0;
    }
    
    public static class Builder {
        private @Range(ValueRange.RADIUS) float radius = 1.0f;
        private @Range(ValueRange.STEPS) int latSteps = 32;
        private @Range(ValueRange.STEPS) int lonSteps = 64;
        private @Range(ValueRange.NORMALIZED) float latStart = 0.0f;
        private @Range(ValueRange.NORMALIZED) float latEnd = 1.0f;
        private @Range(ValueRange.NORMALIZED) float lonStart = 0.0f;
        private @Range(ValueRange.NORMALIZED) float lonEnd = 1.0f;
        private SphereAlgorithm algorithm = SphereAlgorithm.LAT_LON;
        private SphereDeformation deformation = SphereDeformation.NONE;
        private float deformationIntensity = 0f;
        private float deformationLength = 1f;
        private int deformationCount = 6;
        private float deformationSmoothness = 0.5f;
        private float deformationBumpSize = 0.5f;
        private CloudStyle cloudStyle = CloudStyle.GAUSSIAN;
        private int cloudSeed = 42;
        private float cloudWidth = 1.0f;
        // Planet parameters
        private float planetFrequency = 2f;
        private int planetOctaves = 4;
        private float planetLacunarity = 2f;
        private float planetPersistence = 0.5f;
        private float planetRidged = 0f;
        private int planetCraterCount = 0;
        private int planetSeed = 42;
        // Horizon effect parameters
        private boolean horizonEnabled = false;
        private float horizonPower = 3f;
        private float horizonIntensity = 1.5f;
        private float horizonRed = 1f;
        private float horizonGreen = 1f;
        private float horizonBlue = 1f;
        // Corona effect parameters
        private boolean coronaEnabled = false;
        private float coronaPower = 2f;
        private float coronaIntensity = 1f;
        private float coronaFalloff = 0.5f;
        private float coronaRed = 1f;
        private float coronaGreen = 1f;
        private float coronaBlue = 1f;
        private float coronaOffset = 0f;
        private float coronaWidth = 1f;
        
        public Builder radius(float r) { this.radius = r; return this; }
        public Builder latSteps(int s) { this.latSteps = s; return this; }
        public Builder lonSteps(int s) { this.lonSteps = s; return this; }
        public Builder latStart(float l) { this.latStart = l; return this; }
        public Builder latEnd(float l) { this.latEnd = l; return this; }
        public Builder lonStart(float l) { this.lonStart = l; return this; }
        public Builder lonEnd(float l) { this.lonEnd = l; return this; }
        public Builder algorithm(SphereAlgorithm a) { this.algorithm = a != null ? a : SphereAlgorithm.LAT_LON; return this; }
        public Builder deformation(SphereDeformation d) { this.deformation = d != null ? d : SphereDeformation.NONE; return this; }
        public Builder deformationIntensity(float i) { this.deformationIntensity = i; return this; }
        public Builder deformationLength(float l) { this.deformationLength = l; return this; }
        public Builder deformationCount(int c) { this.deformationCount = Math.max(1, Math.min(20, c)); return this; }
        public Builder deformationSmoothness(float s) { this.deformationSmoothness = Math.max(0, Math.min(1, s)); return this; }
        public Builder deformationBumpSize(float s) { this.deformationBumpSize = Math.max(0.1f, Math.min(2f, s)); return this; }
        public Builder cloudStyle(CloudStyle s) { this.cloudStyle = s != null ? s : CloudStyle.GAUSSIAN; return this; }
        public Builder cloudSeed(int s) { this.cloudSeed = Math.max(0, Math.min(999, s)); return this; }
        public Builder cloudWidth(float w) { this.cloudWidth = Math.max(0.5f, Math.min(2f, w)); return this; }
        // Planet setters
        public Builder planetFrequency(float f) { this.planetFrequency = Math.max(0.5f, Math.min(10f, f)); return this; }
        public Builder planetOctaves(int o) { this.planetOctaves = Math.max(1, Math.min(8, o)); return this; }
        public Builder planetLacunarity(float l) { this.planetLacunarity = Math.max(1.5f, Math.min(3.5f, l)); return this; }
        public Builder planetPersistence(float p) { this.planetPersistence = Math.max(0.2f, Math.min(0.8f, p)); return this; }
        public Builder planetRidged(float r) { this.planetRidged = Math.max(0f, Math.min(1f, r)); return this; }
        public Builder planetCraterCount(int c) { this.planetCraterCount = Math.max(0, Math.min(20, c)); return this; }
        public Builder planetSeed(int s) { this.planetSeed = Math.max(0, Math.min(999, s)); return this; }
        // Horizon setters
        public Builder horizonEnabled(boolean e) { this.horizonEnabled = e; return this; }
        public Builder horizonPower(float p) { this.horizonPower = Math.max(1f, Math.min(10f, p)); return this; }
        public Builder horizonIntensity(float i) { this.horizonIntensity = Math.max(0f, Math.min(5f, i)); return this; }
        public Builder horizonRed(float r) { this.horizonRed = Math.max(0f, Math.min(1f, r)); return this; }
        public Builder horizonGreen(float g) { this.horizonGreen = Math.max(0f, Math.min(1f, g)); return this; }
        public Builder horizonBlue(float b) { this.horizonBlue = Math.max(0f, Math.min(1f, b)); return this; }
        // Corona setters
        public Builder coronaEnabled(boolean e) { this.coronaEnabled = e; return this; }
        public Builder coronaPower(float p) { this.coronaPower = Math.max(1f, Math.min(10f, p)); return this; }
        public Builder coronaIntensity(float i) { this.coronaIntensity = Math.max(0f, Math.min(5f, i)); return this; }
        public Builder coronaFalloff(float f) { this.coronaFalloff = Math.max(0.1f, Math.min(2f, f)); return this; }
        public Builder coronaRed(float r) { this.coronaRed = Math.max(0f, Math.min(1f, r)); return this; }
        public Builder coronaGreen(float g) { this.coronaGreen = Math.max(0f, Math.min(1f, g)); return this; }
        public Builder coronaBlue(float b) { this.coronaBlue = Math.max(0f, Math.min(1f, b)); return this; }
        public Builder coronaOffset(float o) { this.coronaOffset = Math.max(0f, Math.min(1f, o)); return this; }
        public Builder coronaWidth(float w) { this.coronaWidth = Math.max(0.1f, Math.min(3f, w)); return this; }
        
        public SphereShape build() {
            return new SphereShape(radius, latSteps, lonSteps, latStart, latEnd, lonStart, lonEnd, algorithm,
                deformation, deformationIntensity, deformationLength, deformationCount, deformationSmoothness,
                deformationBumpSize, cloudStyle, cloudSeed, cloudWidth,
                planetFrequency, planetOctaves, planetLacunarity, planetPersistence, planetRidged, planetCraterCount, planetSeed,
                horizonEnabled, horizonPower, horizonIntensity, horizonRed, horizonGreen, horizonBlue,
                coronaEnabled, coronaPower, coronaIntensity, coronaFalloff, coronaRed, coronaGreen, coronaBlue, coronaOffset, coronaWidth);
        }
    }
}

