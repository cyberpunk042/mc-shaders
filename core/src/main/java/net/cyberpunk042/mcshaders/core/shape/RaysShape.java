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
 * Rays shape - a collection of straight line segments in 3D space.
 * 
 * <h2>Concept</h2>
 * <p>Rays are pure LINE geometry (1D rendered with thickness). Unlike jets which are
 * 3D volumes (cones/cylinders), rays are straight lines that can be arranged in
 * various patterns.</p>
 * 
 * <h2>Arrangement Patterns</h2>
 * <pre>
 * RADIAL (XZ plane):        SPHERICAL (3D):          PARALLEL:
 *          ↑                      ↑                   →  →  →
 *         /|\                   ↗ | ↖                 →  →  →
 *      ←─●─→                 ←──●──→                  →  →  →
 *         \|/                   ↙ | ↘
 *          ↓                      ↓
 * 
 * CONVERGING (inward):      DIVERGING (outward):
 *     ↘   ↓   ↙                ↗   ↑   ↖
 *         ●                        ●
 *     ↗   ↑   ↖                ↘   ↓   ↙
 * </pre>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "shape": {
 *   "type": "rays",
 *   "rayLength": 2.0,
 *   "rayWidth": 1.0,
 *   "count": 12,
 *   "arrangement": "RADIAL",
 *   "innerRadius": 0.5,
 *   "outerRadius": 3.0,
 *   "layers": 1,
 *   "layerSpacing": 0.5,
 *   "randomness": 0.0,
 *   "lengthVariation": 0.0,
 *   "fadeStart": 1.0,
 *   "fadeEnd": 1.0,
 *   "segments": 1,
 *   "segmentGap": 0.0
 * }
 * </pre>
 * 
 * @see RayArrangement
 * @see Shape
 */
public record RaysShape(
    // === Individual Ray Geometry ===
    @Range(ValueRange.POSITIVE_NONZERO) float rayLength,
    @Range(ValueRange.POSITIVE_NONZERO) float rayWidth,
    
    // === Distribution & Count ===
    @Range(ValueRange.STEPS) int count,
    RayArrangement arrangement,
    RayDistribution distribution, // UNIFORM, RANDOM, STOCHASTIC
    @Range(ValueRange.POSITIVE) float innerRadius,
    @Range(ValueRange.POSITIVE_NONZERO) float outerRadius,
    
    // === Multi-Layer Support ===
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true, defaultValue = "1") int layers,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float layerSpacing,
    @JsonField(skipIfDefault = true) RayLayerMode layerMode,
    /** When true, all layers use the same inner radius as endpoint (for radial/shell/spiral modes). */
    @JsonField(skipIfDefault = true, defaultValue = "false") boolean unifiedEnd,
    
    // === Randomness ===
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float randomness,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float lengthVariation,
    
    // === Fading (per-ray alpha gradient) ===
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1.0") float fadeStart,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1.0") float fadeEnd,
    
    // === Segmentation (for dashed/dotted effects) ===
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true, defaultValue = "1") int segments,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float segmentGap,
    
    // === Ray Line Shape (NEW: how individual rays are curved) ===
    @JsonField(skipIfDefault = true) RayLineShape lineShape,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "0.1") float lineShapeAmplitude,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "2.0") float lineShapeFrequency,
    @Range(ValueRange.STEPS) @JsonField(skipIfDefault = true, defaultValue = "16") int lineResolution,
    
    // === Field Curvature (how rays curve around center) ===
    @JsonField(skipIfDefault = true) RayCurvature curvature,
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true) float curvatureIntensity,
    
    // === Ray Type (visual appearance: LINE, DROPLET, KAMEHAMEHA, etc.) ===
    @JsonField(skipIfDefault = true) RayType rayType,
    
    // === 3D Shape Control (for DROPLET, CONE, ARROW, etc.) ===
    /** Deformation intensity for 3D ray types (0 = default, 1 = maximum effect) */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1.0") float shapeIntensity,
    /** Axial stretch for 3D ray types: <1 oblate, 1 = normal, >1 = prolate */
    @Range(ValueRange.POSITIVE_NONZERO) @JsonField(skipIfDefault = true, defaultValue = "1.0") float shapeLength,
    
    // === Ray Orientation (direction for 3D ray types) ===
    @JsonField(skipIfDefault = true) RayOrientation rayOrientation,
    
    // === Field Deformation (gravitational distortion based on distance) ===
    @JsonField(skipIfDefault = true) FieldDeformationMode fieldDeformation,
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true) float fieldDeformationIntensity,
    
    // === Shape State (Stage/Phase model for animation) ===
    /** Shape lifecycle state: stage, phase, and edge transition mode. */
    @JsonField(skipIfDefault = true) ShapeState<RayFlowStage> shapeState,
    
    // === Energy Interaction (how shape renders based on phase) ===
    /** Radiative interaction mode: defines how energy interacts with shape. */
    @JsonField(skipIfDefault = true) net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction radiativeInteraction,
    /** Visible segment length (0-1) for TRANSMISSION mode. */
    @Range(ValueRange.NORMALIZED) @JsonField(skipIfDefault = true, defaultValue = "1.0") float segmentLength,
    /** Wave arc: <1.0 compression, =1.0 normal, >1.0 faster cycling */
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "1.0") float waveArc,
    /** Wave distribution pattern (SEQUENTIAL or RANDOM). */
    @JsonField(skipIfDefault = true) net.cyberpunk042.mcshaders.core.animation.WaveDistribution waveDistribution,
    /** Sweep copies: <1.0 trims arc, =1.0 normal, >1.0 duplicates sweeps */
    @Range(ValueRange.POSITIVE) @JsonField(skipIfDefault = true, defaultValue = "2.0") float waveCount,
    
    // === Animation Behavior (moved from RayFlowConfig) ===
    /** Whether rays spawn at full length (true) or grow progressively (false). */
    @JsonField(skipIfDefault = true, defaultValue = "false") boolean startFullLength,
    /** Whether segment slides along fixed curve path. */
    @JsonField(skipIfDefault = true, defaultValue = "true") boolean followCurve
) implements Shape {
    
    // =========================================================================
    // Presets
    // =========================================================================
    
    /** Default rays (radial sun-ray pattern). */
    public static final RaysShape DEFAULT = new RaysShape(
        2.0f,           // rayLength
        1.0f,           // rayWidth
        12,             // count
        RayArrangement.RADIAL, // arrangement
        RayDistribution.UNIFORM, // distribution
        0.5f,           // innerRadius
        3.0f,           // outerRadius
        1,              // layers
        0.5f,           // layerSpacing
        RayLayerMode.VERTICAL, // layerMode
        false,          // unifiedEnd
        0.0f,           // randomness
        0.0f,           // lengthVariation
        1.0f,           // fadeStart
        1.0f,           // fadeEnd
        1,              // segments
        0.0f,           // segmentGap
        // Shape modifiers
        RayLineShape.STRAIGHT, // lineShape
        0.1f,           // lineShapeAmplitude
        2.0f,           // lineShapeFrequency
        16,             // lineResolution
        RayCurvature.NONE, // curvature
        0.0f,           // curvatureIntensity
        RayType.LINE,   // rayType
        1.0f,           // shapeIntensity
        1.0f,           // shapeLength
        RayOrientation.ALONG_RAY,  // rayOrientation
        FieldDeformationMode.NONE, // fieldDeformation
        0.0f,           // fieldDeformationIntensity
        null,           // shapeState (null = use default ACTIVE state)
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE, // radiativeInteraction
        1.0f,           // segmentLength
        1.0f,           // waveArc
        net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS, // waveDistribution
        2.0f,           // waveCount
        false,          // startFullLength
        true            // followCurve
    );
    
    /** Spherical absorption rays (converging to center). */
    public static final RaysShape ABSORPTION = new RaysShape(
        2.0f, 1.0f, 48, RayArrangement.CONVERGING, RayDistribution.RANDOM, 0.5f, 3.5f,
        8, 0.3f, RayLayerMode.SHELL, false, 0.1f, 0.1f, 1.0f, 0.2f, 1, 0.0f,
        RayLineShape.STRAIGHT, 0.1f, 2.0f, 16, RayCurvature.NONE, 0.0f, RayType.LINE, 1.0f, 1.0f, RayOrientation.ALONG_RAY,
        FieldDeformationMode.NONE, 0.0f, null,
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.ABSORPTION, 1.0f, 1.0f, net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS, 2.0f, false, true);
    
    /** Spherical emission rays (diverging from center). */
    public static final RaysShape EMISSION = new RaysShape(
        2.0f, 1.0f, 48, RayArrangement.DIVERGING, RayDistribution.RANDOM, 0.5f, 3.5f,
        8, 0.3f, RayLayerMode.SHELL, false, 0.1f, 0.1f, 0.2f, 1.0f, 1, 0.0f,
        RayLineShape.STRAIGHT, 0.1f, 2.0f, 16, RayCurvature.NONE, 0.0f, RayType.LINE, 1.0f, 1.0f, RayOrientation.ALONG_RAY,
        FieldDeformationMode.NONE, 0.0f, null,
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.EMISSION, 1.0f, 1.0f, net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS, 2.0f, false, true);
    
    /** Parallel laser grid. */
    public static final RaysShape LASER_GRID = new RaysShape(
        5.0f, 1.5f, 16, RayArrangement.PARALLEL, RayDistribution.UNIFORM, 0.0f, 2.0f,
        4, 0.3f, RayLayerMode.VERTICAL, false, 0.0f, 0.0f, 1.0f, 1.0f, 1, 0.0f,
        RayLineShape.STRAIGHT, 0.1f, 2.0f, 16, RayCurvature.NONE, 0.0f, RayType.LINE, 1.0f, 1.0f, RayOrientation.ALONG_RAY,
        FieldDeformationMode.NONE, 0.0f, null,
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE, 1.0f, 1.0f, net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS, 2.0f, false, true);
    
    /** Dashed pulse rays. */
    public static final RaysShape PULSE = new RaysShape(
        3.0f, 1.0f, 8, RayArrangement.RADIAL, RayDistribution.UNIFORM, 0.3f, 2.5f,
        1, 0.5f, RayLayerMode.VERTICAL, false, 0.0f, 0.0f, 1.0f, 0.5f, 4, 0.2f,
        RayLineShape.STRAIGHT, 0.1f, 2.0f, 16, RayCurvature.NONE, 0.0f, RayType.LINE, 1.0f, 1.0f, RayOrientation.ALONG_RAY,
        FieldDeformationMode.NONE, 0.0f, null,
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.OSCILLATION, 1.0f, 1.0f, net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS, 2.0f, false, true);
    
    /** Sparse random stars. */
    public static final RaysShape STARS = new RaysShape(
        1.5f, 0.5f, 24, RayArrangement.SPHERICAL, RayDistribution.STOCHASTIC, 0.8f, 2.0f,
        1, 0.5f, RayLayerMode.SHELL, false, 0.3f, 0.3f, 0.8f, 0.3f, 1, 0.0f,
        RayLineShape.STRAIGHT, 0.1f, 2.0f, 16, RayCurvature.NONE, 0.0f, RayType.LINE, 1.0f, 1.0f, RayOrientation.ALONG_RAY,
        FieldDeformationMode.NONE, 0.0f, null,
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE, 1.0f, 1.0f, net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS, 2.0f, false, true);
    
    /** Vortex rays (spiraling into center). */
    public static final RaysShape VORTEX = new RaysShape(
        2.5f, 1.0f, 24, RayArrangement.RADIAL, RayDistribution.UNIFORM, 0.3f, 3.0f,
        1, 0.5f, RayLayerMode.VERTICAL, false, 0.0f, 0.0f, 1.0f, 0.8f, 1, 0.0f,
        RayLineShape.STRAIGHT, 0.1f, 2.0f, 32, RayCurvature.VORTEX, 0.5f, RayType.LINE, 1.0f, 1.0f, RayOrientation.ALONG_RAY,
        FieldDeformationMode.NONE, 0.0f, null,
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE, 1.0f, 1.0f, net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS, 2.0f, false, true);
    
    /** Corkscrew rays (helical shape). */
    public static final RaysShape CORKSCREW = new RaysShape(
        2.0f, 1.0f, 12, RayArrangement.RADIAL, RayDistribution.UNIFORM, 0.5f, 2.5f,
        1, 0.5f, RayLayerMode.VERTICAL, false, 0.0f, 0.0f, 1.0f, 1.0f, 1, 0.0f,
        RayLineShape.CORKSCREW, 0.15f, 3.0f, 32, RayCurvature.NONE, 0.0f, RayType.LINE, 1.0f, 1.0f, RayOrientation.ALONG_RAY,
        FieldDeformationMode.NONE, 0.0f, null,
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE, 1.0f, 1.0f, net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS, 2.0f, false, true);
    
    public static RaysShape defaults() { return DEFAULT; }
    
    // =========================================================================
    // Factory Methods
    // =========================================================================
    
    /**
     * Creates simple radial rays.
     */
    public static RaysShape radial(int count, float length) {
        return builder().count(count).rayLength(length).arrangement(RayArrangement.RADIAL).build();
    }
    
    /**
     * Creates spherical rays emanating from center.
     */
    public static RaysShape spherical(int count, float length, int layers) {
        return builder().count(count).rayLength(length).layers(layers)
            .arrangement(RayArrangement.SPHERICAL).build();
    }
    
    /**
     * Creates converging absorption rays.
     */
    public static RaysShape converging(int count, float innerRadius, float outerRadius) {
        return builder().count(count).innerRadius(innerRadius).outerRadius(outerRadius)
            .arrangement(RayArrangement.CONVERGING).build();
    }
    
    // =========================================================================
    // Shape Interface
    // =========================================================================
    
    @Override
    public String getType() {
        return "rays";
    }
    
    @Override
    public Vector3f getBounds() {
        float size = outerRadius * 2;
        return new Vector3f(size, layers > 1 ? layerSpacing * (layers - 1) : rayLength, size);
    }
    
    @Override
    public CellType primaryCellType() {
        // 3D ray types use quads (like sphere), 2D types use edges (line segments)
        return effectiveRayType().is3D() ? CellType.QUAD : CellType.EDGE;
    }
    
    @Override
    public Map<String, CellType> getParts() {
        CellType cellType = primaryCellType();
        return Map.of("rays", cellType);
    }
    
    @Override
    public float getRadius() {
        return outerRadius;
    }
    
    // =========================================================================
    // Utility Methods
    // =========================================================================
    
    /** Total number of rays (count * layers). */
    public int totalRayCount() {
        return count * layers;
    }
    
    /** Whether rays fade along their length. */
    public boolean hasFade() {
        return fadeStart != fadeEnd || fadeStart < 1.0f || fadeEnd < 1.0f;
    }
    
    /** Whether rays are segmented (dashed). */
    public boolean isSegmented() {
        return segments > 1 && segmentGap > 0;
    }
    
    /** Ray length - the actual length of the visible ray segment. */
    public float effectiveRayLength() {
        // ALWAYS returns the configured rayLength, never computed from radii
        return rayLength > 0 ? rayLength : 1.0f;
    }
    
    /** Trajectory span - the distance the ray travels from innerRadius to outerRadius. */
    public float trajectorySpan() {
        return outerRadius - innerRadius;
    }
    
    /** Whether rays have a non-straight line shape. */
    public boolean hasLineShape() {
        return lineShape != null && lineShape != RayLineShape.STRAIGHT;
    }
    
    /** Returns the line shape, defaulting to STRAIGHT if null. */
    public RayLineShape effectiveLineShape() {
        return lineShape != null ? lineShape : RayLineShape.STRAIGHT;
    }
    
    /** Whether rays have field curvature (spiral/vortex). */
    public boolean hasCurvature() {
        return curvature != null && curvature != RayCurvature.NONE && curvatureIntensity > 0;
    }
    
    /** Returns the curvature mode, defaulting to NONE if null. */
    public RayCurvature effectiveCurvature() {
        return curvature != null ? curvature : RayCurvature.NONE;
    }
    
    /** Returns the ray type, defaulting to LINE if null. */
    public RayType effectiveRayType() {
        return rayType != null ? rayType : RayType.LINE;
    }
    
    /** Returns the ray orientation, defaulting to ALONG_RAY if null. */
    public RayOrientation effectiveRayOrientation() {
        return rayOrientation != null ? rayOrientation : RayOrientation.ALONG_RAY;
    }
    
    /** Returns the layer mode, defaulting to VERTICAL if null. */
    public RayLayerMode effectiveLayerMode() {
        return layerMode != null ? layerMode : RayLayerMode.VERTICAL;
    }
    
    /** Number of segments needed to render this ray shape properly. */
    public int effectivelineResolution() {
        if (hasLineShape()) {
            // Complex line shape - use at least the suggested minimum
            return Math.max(lineResolution, lineShape.suggestedMinSegments());
        } else if (hasCurvature()) {
            // Curvature needs multiple segments for smooth curves
            return Math.max(lineResolution, 16);
        }
        // For straight rays, respect user's lineResolution setting
        // (needed for travel animations like CHASE/SCROLL to work smoothly)
        return Math.max(1, lineResolution);
    }
    
    /** Returns the field deformation mode, defaulting to NONE if null. */
    public FieldDeformationMode effectiveFieldDeformation() {
        return fieldDeformation != null ? fieldDeformation : FieldDeformationMode.NONE;
    }
    
    /** Whether field deformation is active. */
    public boolean hasFieldDeformation() {
        return fieldDeformation != null && fieldDeformation != FieldDeformationMode.NONE 
               && fieldDeformationIntensity > 0;
    }
    
    /** Returns the shape state, defaulting to ACTIVE stage at full phase. */
    public ShapeState<RayFlowStage> effectiveShapeState() {
        if (shapeState != null) {
            return shapeState;
        }
        // Default: fully active (phase 1.0 = full visibility)
        return new ShapeState<>(RayFlowStage.ACTIVE, 1.0f, EdgeTransitionMode.CLIP);
    }
    
    // ─────────────────── Energy Interaction Accessors ───────────────────
    
    /** Returns the radiative interaction mode, defaulting to NONE if null. */
    public net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction effectiveRadiativeInteraction() {
        return radiativeInteraction != null ? radiativeInteraction : net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE;
    }
    
    /** Whether radiative interaction is active. */
    public boolean hasRadiativeInteraction() {
        return radiativeInteraction != null && radiativeInteraction.isActive();
    }
    
    /** Returns effective segment length, defaulting to 1.0 if not set. */
    public float effectiveSegmentLength() {
        return segmentLength <= 0 ? 1.0f : segmentLength;
    }
    
    /** Returns effective wave arc, defaulting to 1.0 if not set. */
    public float effectiveWaveArc() {
        return waveArc <= 0 ? 1.0f : waveArc;
    }
    
    /** Returns effective wave distribution, defaulting to CONTINUOUS if null. */
    public net.cyberpunk042.mcshaders.core.animation.WaveDistribution effectiveWaveDistribution() {
        return waveDistribution != null ? waveDistribution : net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS;
    }
    
    /** Returns effective wave count (sweep copies), defaulting to 2.0 if not set. */
    public float effectiveWaveCount() {
        return waveCount <= 0 ? 2.0f : waveCount;
    }
    
    
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    
    public Builder toBuilder() {
        return new Builder()
            .rayLength(rayLength)
            .rayWidth(rayWidth)
            .count(count)
            .arrangement(arrangement)
            .distribution(distribution)
            .innerRadius(innerRadius)
            .outerRadius(outerRadius)
            .layers(layers)
            .layerSpacing(layerSpacing)
            .layerMode(layerMode)
            .unifiedEnd(unifiedEnd)
            .randomness(randomness)
            .lengthVariation(lengthVariation)
            .fadeStart(fadeStart)
            .fadeEnd(fadeEnd)
            .segments(segments)
            .segmentGap(segmentGap)
            // Shape modifiers
            .lineShape(lineShape)
            .lineShapeAmplitude(lineShapeAmplitude)
            .lineShapeFrequency(lineShapeFrequency)
            .lineResolution(lineResolution)
            .curvature(curvature)
            .curvatureIntensity(curvatureIntensity)
            .rayType(rayType)
            .shapeIntensity(shapeIntensity)
            .shapeLength(shapeLength)
            .rayOrientation(rayOrientation)
            // Field deformation
            .fieldDeformation(fieldDeformation)
            .fieldDeformationIntensity(fieldDeformationIntensity)
            // Shape state
            .shapeState(shapeState)
            // Energy interaction
            .radiativeInteraction(radiativeInteraction)
            .segmentLength(segmentLength)
            .waveArc(waveArc)
            .waveDistribution(waveDistribution)
            .waveCount(waveCount)
            // Animation behavior
            .startFullLength(startFullLength)
            .followCurve(followCurve);
    }
    
    public static class Builder {
        private float rayLength = 2.0f;
        private float rayWidth = 1.0f;
        private int count = 12;
        private RayArrangement arrangement = RayArrangement.RADIAL;
        private RayDistribution distribution = RayDistribution.UNIFORM;
        private float innerRadius = 0.5f;
        private float outerRadius = 3.0f;
        private int layers = 1;
        private float layerSpacing = 0.5f;
        private RayLayerMode layerMode = RayLayerMode.VERTICAL;
        private boolean unifiedEnd = false;
        private float randomness = 0.0f;
        private float lengthVariation = 0.0f;
        private float fadeStart = 1.0f;
        private float fadeEnd = 1.0f;
        private int segments = 1;
        private float segmentGap = 0.0f;
        // Shape modifiers
        private RayLineShape lineShape = RayLineShape.STRAIGHT;
        private float lineShapeAmplitude = 0.1f;
        private float lineShapeFrequency = 2.0f;
        private int lineResolution = 16;
        private RayCurvature curvature = RayCurvature.NONE;
        private float curvatureIntensity = 0.0f;
        private RayType rayType = RayType.LINE;
        private float shapeIntensity = 1.0f;
        private float shapeLength = 1.0f;
        private RayOrientation rayOrientation = RayOrientation.ALONG_RAY;
        private FieldDeformationMode fieldDeformation = FieldDeformationMode.NONE;
        private float fieldDeformationIntensity = 0.0f;
        private ShapeState<RayFlowStage> shapeState = null;
        // Energy interaction
        private net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction radiativeInteraction = net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE;
        private float segmentLength = 1.0f;
        private float waveArc = 1.0f;
        private net.cyberpunk042.mcshaders.core.animation.WaveDistribution waveDistribution = net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS;
        private float waveCount = 2.0f;
        // Animation behavior (moved from RayFlowConfig)
        private boolean startFullLength = false;
        private boolean followCurve = true;
        
        public Builder rayLength(float v) { this.rayLength = v; return this; }
        public Builder rayWidth(float v) { this.rayWidth = v; return this; }
        public Builder count(int v) { this.count = v; return this; }
        public Builder arrangement(RayArrangement v) { this.arrangement = v; return this; }
        public Builder distribution(RayDistribution v) { this.distribution = v; return this; }
        public Builder innerRadius(float v) { this.innerRadius = v; return this; }
        public Builder outerRadius(float v) { this.outerRadius = v; return this; }
        public Builder layers(int v) { this.layers = v; return this; }
        public Builder layerSpacing(float v) { this.layerSpacing = v; return this; }
        public Builder layerMode(RayLayerMode v) { this.layerMode = v != null ? v : RayLayerMode.VERTICAL; return this; }
        public Builder unifiedEnd(boolean v) { this.unifiedEnd = v; return this; }
        public Builder randomness(float v) { this.randomness = v; return this; }
        public Builder lengthVariation(float v) { this.lengthVariation = v; return this; }
        public Builder fadeStart(float v) { this.fadeStart = v; return this; }
        public Builder fadeEnd(float v) { this.fadeEnd = v; return this; }
        public Builder segments(int v) { this.segments = v; return this; }
        public Builder segmentGap(float v) { this.segmentGap = v; return this; }
        // Shape modifier setters
        public Builder lineShape(RayLineShape v) { this.lineShape = v != null ? v : RayLineShape.STRAIGHT; return this; }
        public Builder lineShapeAmplitude(float v) { this.lineShapeAmplitude = v; return this; }
        public Builder lineShapeFrequency(float v) { this.lineShapeFrequency = v; return this; }
        public Builder lineResolution(int v) { this.lineResolution = v; return this; }
        public Builder curvature(RayCurvature v) { this.curvature = v != null ? v : RayCurvature.NONE; return this; }
        public Builder curvatureIntensity(float v) { this.curvatureIntensity = v; return this; }
        public Builder rayType(RayType v) { this.rayType = v != null ? v : RayType.LINE; return this; }
        public Builder shapeIntensity(float v) { this.shapeIntensity = v; return this; }
        public Builder shapeLength(float v) { this.shapeLength = v; return this; }
        public Builder rayOrientation(RayOrientation v) { this.rayOrientation = v != null ? v : RayOrientation.ALONG_RAY; return this; }
        public Builder fieldDeformation(FieldDeformationMode v) { this.fieldDeformation = v != null ? v : FieldDeformationMode.NONE; return this; }
        public Builder fieldDeformationIntensity(float v) { this.fieldDeformationIntensity = v; return this; }
        public Builder shapeState(ShapeState<RayFlowStage> v) { this.shapeState = v; return this; }
        // Energy interaction setters
        public Builder radiativeInteraction(net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction v) { this.radiativeInteraction = v != null ? v : net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE; return this; }
        public Builder segmentLength(float v) { this.segmentLength = v; return this; }
        public Builder waveArc(float v) { this.waveArc = v; return this; }
        public Builder waveDistribution(net.cyberpunk042.mcshaders.core.animation.WaveDistribution v) { this.waveDistribution = v != null ? v : net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS; return this; }
        public Builder waveCount(float v) { this.waveCount = v; return this; }
        // Animation behavior setters (moved from RayFlowConfig)
        public Builder startFullLength(boolean v) { this.startFullLength = v; return this; }
        public Builder followCurve(boolean v) { this.followCurve = v; return this; }
        
        public RaysShape build() {
            return new RaysShape(
                rayLength, rayWidth, count, arrangement, distribution, innerRadius, outerRadius,
                layers, layerSpacing, layerMode, unifiedEnd, randomness, lengthVariation,
                fadeStart, fadeEnd, segments, segmentGap,
                lineShape, lineShapeAmplitude, lineShapeFrequency, lineResolution,
                curvature, curvatureIntensity, rayType, shapeIntensity, shapeLength, rayOrientation,
                fieldDeformation, fieldDeformationIntensity, shapeState,
                radiativeInteraction, segmentLength, waveArc, waveDistribution, waveCount,
                startFullLength, followCurve
            );
        }
    }
}

