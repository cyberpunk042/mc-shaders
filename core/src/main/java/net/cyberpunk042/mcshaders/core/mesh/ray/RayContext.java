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

import net.cyberpunk042.mcshaders.core.animation.RayFlowConfig;
import net.cyberpunk042.mcshaders.core.animation.WaveConfig;
import net.cyberpunk042.mcshaders.core.shape.RayCurvature;
import net.cyberpunk042.mcshaders.core.shape.RayFlowStage;
import net.cyberpunk042.mcshaders.core.shape.RayLineShape;
import net.cyberpunk042.mcshaders.core.shape.ShapeState;

/**
 * Computed context for a single ray, containing all position and shape data.
 * 
 * <p>This is computed by {@link RayPositioner} and passed to 
 * {@link RayTypeTessellator} implementations for geometry generation.
 * 
 * <h2>Coordinate System</h2>
 * <p>All positions are in local space relative to the field origin.
 * 
 * @see RayPositioner
 * @see RayTypeTessellator
 */
public record RayContext(
    // ═══════════════════════════════════════════════════════════════════════════
    // Position Data
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Start position [x, y, z] of the ray. */
    float[] start,
    
    /** End position [x, y, z] of the ray. */
    float[] end,
    
    /** Normalized direction vector [x, y, z] from start to end. */
    float[] direction,
    
    /** Actual length of this ray (may vary due to randomness). */
    float length,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Ray Identity
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Ray index (0 to count-1). */
    int index,
    
    /** Total count of rays. */
    int count,
    
    /** Layer index (0 to layers-1). */
    int layerIndex,
    
    /** Normalized t value for this ray (index / count). */
    float t,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Appearance
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Ray width (thickness). */
    float width,
    
    /** Fade alpha at start (0-1). */
    float fadeStart,
    
    /** Fade alpha at end (0-1). */
    float fadeEnd,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Shape Modifiers
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Line shape (how the ray is bent: straight, wavy, corkscrew). */
    RayLineShape lineShape,
    
    /** Line shape amplitude. */
    float lineShapeAmplitude,
    
    /** Line shape frequency. */
    float lineShapeFrequency,
    
    /** Curvature mode (how rays curve around center: vortex, spiral). */
    RayCurvature curvature,
    
    /** Curvature intensity. */
    float curvatureIntensity,
    
    /** Number of segments for complex shapes. */
    int lineResolution,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Orientation (for 3D ray types)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Orientation mode for 3D ray types. */
    net.cyberpunk042.mcshaders.core.shape.RayOrientation orientation,
    
    /** Computed orientation direction vector [x, y, z] based on orientation mode. */
    float[] orientationVector,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // 3D Shape Parameters (for DROPLET, CONE, ARROW, etc.)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Shape deformation intensity (0-1). */
    float shapeIntensity,
    
    /** Axial stretch factor (<1 oblate, 1 normal, >1 prolate). */
    float shapeLength,
    
    /** Overall size of 3D shape (from rayLength). */
    float shapeSize,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Animation
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Wave configuration (or null if no wave). */
    WaveConfig wave,
    
    /** Current animation time. */
    float time,
    
    /** Whether wave deformation is active. */
    boolean hasWave,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Flow Animation (for 3D rays)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Flow animation config (or null if no flow). */
    RayFlowConfig flowConfig,
    
    /** Animated position offset along ray axis (from flow animation). */
    float flowPositionOffset,
    
    /** Travel range for flow animation (outerRadius - innerRadius). */
    float travelRange,
    
    /** Inner radius of the field (for geometric edge clipping). */
    float innerRadius,
    
    /** Outer radius of the field (for geometric edge clipping). */
    float outerRadius,
    
    // NOTE: flowScale, visibleTStart, visibleTEnd, flowAlpha REMOVED
    // These are now computed by RadiativeInteractionFactory + TessEdgeModeFactory
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Field Deformation (gravitational distortion)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Field deformation mode (NONE, GRAVITATIONAL, REPULSION, TIDAL). */
    net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode fieldDeformation,
    
    /** Field deformation intensity. */
    float fieldDeformationIntensity,
    
    /** Normalized distance from field center (0=at center, 1=at outer edge). */
    float normalizedDistance,
    
    /** Computed axial stretch from field deformation. */
    float fieldStretch,
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Shape State (Stage/Phase model)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Shape lifecycle state (stage, phase, edgeMode). */
    ShapeState<RayFlowStage> shapeState
) {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Utility Methods
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** 
     * Creates a copy of the start position array.
     * Use when you need to modify values without affecting the original.
     */
    public float[] startCopy() {
        return new float[] { start[0], start[1], start[2] };
    }
    
    /** 
     * Creates a copy of the end position array.
     * Use when you need to modify values without affecting the original.
     */
    public float[] endCopy() {
        return new float[] { end[0], end[1], end[2] };
    }
    
    /** 
     * Creates a copy of the direction vector array.
     */
    public float[] directionCopy() {
        return new float[] { direction[0], direction[1], direction[2] };
    }
    
    /**
     * Interpolates a position along the ray.
     * @param t Parametric value (0 = start, 1 = end)
     * @return Interpolated position [x, y, z]
     */
    public float[] interpolate(float t) {
        return new float[] {
            start[0] + (end[0] - start[0]) * t,
            start[1] + (end[1] - start[1]) * t,
            start[2] + (end[2] - start[2]) * t
        };
    }
    
    /**
     * Calculates the fade alpha at a given t.
     * @param t Parametric value (0 = start, 1 = end)
     * @return Alpha value (0-1)
     */
    public float fadeAt(float t) {
        return fadeStart + (fadeEnd - fadeStart) * t;
    }
    
    /**
     * Whether this ray needs multi-segment tessellation.
     * lineResolution > 1 always triggers subdivision for smooth lines.
     */
    public boolean needsMultiSegment() {
        boolean hasShape = lineShape != RayLineShape.STRAIGHT;
        boolean hasCurve = curvature != RayCurvature.NONE;
        // lineResolution > 1 always triggers subdivision
        return hasShape || hasCurve || lineResolution > 1 || hasWave;
    }
    
    /**
     * Whether the ray has fading (non-uniform alpha).
     */
    public boolean hasFade() {
        return fadeStart != fadeEnd || fadeStart < 1.0f || fadeEnd < 1.0f;
    }
    
    /**
     * Returns the shape state, defaulting to ACTIVE at full phase.
     */
    public ShapeState<RayFlowStage> effectiveShapeState() {
        if (shapeState != null) {
            return shapeState;
        }
        // Default: fully active state
        return new ShapeState<>(RayFlowStage.ACTIVE, 1.0f, net.cyberpunk042.mcshaders.core.shape.EdgeTransitionMode.CLIP);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Builder
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Creates a builder for constructing RayContext instances. */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private float[] start = new float[3];
        private float[] end = new float[3];
        private float[] direction = new float[3];
        private float length = 1.0f;
        private int index = 0;
        private int count = 1;
        private int layerIndex = 0;
        private float t = 0.0f;
        private float width = 1.0f;
        private float fadeStart = 1.0f;
        private float fadeEnd = 1.0f;
        private RayLineShape lineShape = RayLineShape.STRAIGHT;
        private float lineShapeAmplitude = 0.1f;
        private float lineShapeFrequency = 2.0f;
        private RayCurvature curvature = RayCurvature.NONE;
        private float curvatureIntensity = 0.0f;
        private int lineResolution = 1;
        private net.cyberpunk042.mcshaders.core.shape.RayOrientation orientation = net.cyberpunk042.mcshaders.core.shape.RayOrientation.ALONG_RAY;
        private float[] orientationVector = new float[] { 0, 0, 1 };
        private float shapeIntensity = 1.0f;
        private float shapeLength = 1.0f;
        private float shapeSize = 1.0f;  // Overall size of 3D shape (separate from stretch)
        private WaveConfig wave = null;
        private float time = 0.0f;
        private boolean hasWave = false;
        private RayFlowConfig flowConfig = null;
        private float flowPositionOffset = 0.0f;
        private float travelRange = 1.0f;
        private float innerRadius = 0.0f;
        private float outerRadius = 1.0f;
        // NOTE: flowScale, visibleTStart, visibleTEnd, flowAlpha REMOVED from Builder
        private net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode fieldDeformation = net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode.NONE;
        private float fieldDeformationIntensity = 0.0f;
        private float normalizedDistance = 0.0f;
        private float fieldStretch = 1.0f;
        private ShapeState<RayFlowStage> shapeState = null;
        
        public Builder start(float[] v) { 
            this.start = v != null ? v : new float[3]; 
            return this; 
        }
        public Builder start(float x, float y, float z) { 
            this.start = new float[] { x, y, z }; 
            return this; 
        }
        public Builder end(float[] v) { 
            this.end = v != null ? v : new float[3]; 
            return this; 
        }
        public Builder end(float x, float y, float z) { 
            this.end = new float[] { x, y, z }; 
            return this; 
        }
        public Builder direction(float[] v) { 
            this.direction = v != null ? v : new float[3]; 
            return this; 
        }
        public Builder length(float v) { this.length = v; return this; }
        public Builder index(int v) { this.index = v; return this; }
        public Builder count(int v) { this.count = v; return this; }
        public Builder layerIndex(int v) { this.layerIndex = v; return this; }
        public Builder t(float v) { this.t = v; return this; }
        public Builder width(float v) { this.width = v; return this; }
        public Builder fadeStart(float v) { this.fadeStart = v; return this; }
        public Builder fadeEnd(float v) { this.fadeEnd = v; return this; }
        public Builder lineShape(RayLineShape v) { 
            this.lineShape = v != null ? v : RayLineShape.STRAIGHT; 
            return this; 
        }
        public Builder lineShapeAmplitude(float v) { this.lineShapeAmplitude = v; return this; }
        public Builder lineShapeFrequency(float v) { this.lineShapeFrequency = v; return this; }
        public Builder curvature(RayCurvature v) { 
            this.curvature = v != null ? v : RayCurvature.NONE; 
            return this; 
        }
        public Builder curvatureIntensity(float v) { this.curvatureIntensity = v; return this; }
        public Builder lineResolution(int v) { this.lineResolution = v; return this; }
        public Builder orientation(net.cyberpunk042.mcshaders.core.shape.RayOrientation v) { 
            this.orientation = v != null ? v : net.cyberpunk042.mcshaders.core.shape.RayOrientation.ALONG_RAY; 
            return this; 
        }
        public Builder orientationVector(float[] v) { 
            this.orientationVector = v != null ? v : new float[] { 0, 0, 1 }; 
            return this; 
        }
        public Builder shapeIntensity(float v) { this.shapeIntensity = v; return this; }
        public Builder shapeLength(float v) { this.shapeLength = v; return this; }
        public Builder shapeSize(float v) { this.shapeSize = v; return this; }
        public Builder wave(WaveConfig v) { this.wave = v; return this; }
        public Builder time(float v) { this.time = v; return this; }
        public Builder hasWave(boolean v) { this.hasWave = v; return this; }
        public Builder flowConfig(RayFlowConfig v) { this.flowConfig = v; return this; }
        public Builder flowPositionOffset(float v) { this.flowPositionOffset = v; return this; }
        public Builder travelRange(float v) { this.travelRange = v; return this; }
        public Builder innerRadius(float v) { this.innerRadius = v; return this; }
        public Builder outerRadius(float v) { this.outerRadius = v; return this; }
        public Builder fieldDeformation(net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode v) { this.fieldDeformation = v != null ? v : net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode.NONE; return this; }
        public Builder fieldDeformationIntensity(float v) { this.fieldDeformationIntensity = v; return this; }
        public Builder normalizedDistance(float v) { this.normalizedDistance = v; return this; }
        public Builder fieldStretch(float v) { this.fieldStretch = v; return this; }
        public Builder shapeState(ShapeState<RayFlowStage> v) { this.shapeState = v; return this; }
        
        /**
         * Computes direction from start and end, and sets length.
         * Call this after setting start and end.
         */
        public Builder computeDirectionAndLength() {
            float dx = end[0] - start[0];
            float dy = end[1] - start[1];
            float dz = end[2] - start[2];
            this.length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (length > 0.0001f) {
                this.direction = new float[] { dx / length, dy / length, dz / length };
            } else {
                this.direction = new float[] { 0, 1, 0 }; // Default up
            }
            return this;
        }
        
        public RayContext build() {
            return new RayContext(
                start, end, direction, length,
                index, count, layerIndex, t,
                width, fadeStart, fadeEnd,
                lineShape, lineShapeAmplitude, lineShapeFrequency,
                curvature, curvatureIntensity, lineResolution,
                orientation, orientationVector,
                shapeIntensity, shapeLength, shapeSize,
                wave, time, hasWave,
                flowConfig, flowPositionOffset, travelRange, innerRadius, outerRadius,
                fieldDeformation, fieldDeformationIntensity, normalizedDistance, fieldStretch,
                shapeState
            );
        }
    }
}
