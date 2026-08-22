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

import net.cyberpunk042.mcshaders.core.mesh.ray.arrangement.ArrangementFactory;
import net.cyberpunk042.mcshaders.core.mesh.ray.arrangement.ArrangementStrategy;
import net.cyberpunk042.mcshaders.core.mesh.ray.distribution.DistributionFactory;
import net.cyberpunk042.mcshaders.core.mesh.ray.distribution.DistributionResult;
import net.cyberpunk042.mcshaders.core.mesh.ray.distribution.DistributionStrategy;
import net.cyberpunk042.mcshaders.core.mesh.ray.flow.AnimationState;
import net.cyberpunk042.mcshaders.core.mesh.ray.flow.FlowContext;
import net.cyberpunk042.mcshaders.core.mesh.ray.flow.FlowPipeline;
import net.cyberpunk042.mcshaders.core.mesh.ray.layer.LayerModeFactory;
import net.cyberpunk042.mcshaders.core.mesh.ray.layer.LayerModeStrategy;
import net.cyberpunk042.mcshaders.core.mesh.ray.layer.LayerOffset;
import net.cyberpunk042.mcshaders.core.animation.WaveConfig;
import net.cyberpunk042.mcshaders.core.shape.RayArrangement;
import net.cyberpunk042.mcshaders.core.shape.RayCurvature;
import net.cyberpunk042.mcshaders.core.shape.RayFlowStage;
import net.cyberpunk042.mcshaders.core.shape.RayLayerMode;
import net.cyberpunk042.mcshaders.core.shape.RayLineShape;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;
import net.cyberpunk042.mcshaders.core.shape.ShapeState;

import java.util.Random;

/**
 * Computes ray positions based on arrangement and distribution.
 * 
 * <p>This extracts the common positioning logic from RaysTessellator,
 * computing start/end positions for each ray based on the shape's
 * arrangement mode, distribution, and randomness settings.
 * 
 * <h2>Usage</h2>
 * <pre>
 * RayContext context = RayPositioner.computeContext(shape, index, layerIndex, rng);
 * tessellator.tessellate(builder, shape, context);
 * </pre>
 * 
 * @see RayContext
 * @see RayTypeTessellator
 */
public final class RayPositioner {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final float GOLDEN_RATIO = 1.618033988749895f;
    
    private RayPositioner() {} // Utility class
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Internal Data Types
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Holds computed position data for a ray */
    private record PositionData(
        float[] start, 
        float[] end, 
        float[] direction, 
        float length, 
        int count,
        float[] orientationVector
    ) {}
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════════════
    
    /** Computes common position data for a ray */
    private static PositionData computePositionData(RaysShape shape, int index, int layerIndex, Random rng) {
        int count = shape.count();
        float layerSpacing = shape.layerSpacing();
        
        DistributionResult dist = computeDistribution(shape, index, count, rng);
        
        float[] start = new float[3];
        float[] end = new float[3];
        computePosition(shape, index, count, layerIndex, layerSpacing, dist, start, end);
        
        float dx = end[0] - start[0];
        float dy = end[1] - start[1];
        float dz = end[2] - start[2];
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        
        float[] direction = new float[3];
        if (length > 0.0001f) {
            direction[0] = dx / length;
            direction[1] = dy / length;
            direction[2] = dz / length;
        } else {
            direction[1] = 1.0f;
        }
        
        net.cyberpunk042.mcshaders.core.shape.RayOrientation orientation = shape.effectiveRayOrientation();
        float[] orientationVector = computeOrientationVector(orientation, start, direction);
        
        return new PositionData(start, end, direction, length, count, orientationVector);
    }
    
    /** Applies an offset along a direction to a position array */
    private static void applyOffset(float[] pos, float[] direction, float offset) {
        pos[0] += direction[0] * offset;
        pos[1] += direction[1] * offset;
        pos[2] += direction[2] * offset;
    }
    
    /** Computes flow position offset for EMISSION/ABSORPTION */
    private static float computeFlowOffset(
            RaysShape shape, 
            net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flowConfig,
            int index, int count, float time, float[] direction) {
        
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction radiative = shape.effectiveRadiativeInteraction();
        if (radiative != net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.EMISSION &&
            radiative != net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.ABSORPTION) {
            return 0.0f;
        }
        
        // trajectorySpan = travel range (NOT the ray's actual length)
        float trajectorySpan = shape.outerRadius() - shape.innerRadius();
        float phase = computeRayPhase(flowConfig, shape, index, count, time);
        
        if (radiative == net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.EMISSION) {
            return phase * trajectorySpan;
        } else {
            return (1.0f - phase) * trajectorySpan;
        }
    }
    
    /** Computes animated ShapeState from flow config */
    private static ShapeState<RayFlowStage> computeAnimatedState(
            RaysShape shape,
            net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flowConfig,
            int index, int count, float time) {
        
        ShapeState<RayFlowStage> userState = shape.effectiveShapeState();
        
        if (flowConfig != null && flowConfig.isActive()) {
            // Use FlowContext.create to include wave distribution from shape
            FlowContext flowCtx = FlowContext.create(
                flowConfig, shape, index, count, time, shape.innerRadius(), shape.outerRadius());
            AnimationState animResult = FlowPipeline.standard().compute(flowCtx);
            return userState.withPhase(animResult.phase());
        } else {
            return userState;
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Main API
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Computes the context for a single ray.
     * 
     * @param shape The rays shape configuration
     * @param index Ray index (0 to count-1)
     * @param layerIndex Layer index (0 to layers-1)
     * @param rng Random number generator for distribution randomness
     * @param wave Optional wave configuration (null if no wave)
     * @param time Current animation time
     * @return Computed RayContext with position and shape data
     */
    public static RayContext computeContext(
            RaysShape shape, 
            int index, 
            int layerIndex, 
            Random rng,
            WaveConfig wave,
            float time) {
        return computeContext(shape, index, layerIndex, rng, wave, time, null);
    }
    
    /**
     * Computes contexts for a single ray, returning MULTIPLE when in edge transition.
     * 
     * <p>During RADIATE/ABSORB edge transitions, we need TWO separate shapes:
     * <ul>
     *   <li>The "outgoing" shape at the far edge (despawning)</li>
     *   <li>The "incoming" shape at the near edge (spawning)</li>
     * </ul>
     *
     * <p>This prevents vertex sharing artifacts between the two.
     * 
     * @param shape The rays shape configuration
     * @param index Ray index (0 to count-1)
     * @param layerIndex Layer index (0 to layers-1)
     * @param rng Random number generator for distribution randomness
     * @param wave Optional wave configuration (null if no wave)
     * @param time Current animation time
     * @param flowConfig Optional flow animation config (null if no flow)
     * @return List of RayContexts (1 normally, 2 during edge transitions)
     */
    public static java.util.List<RayContext> computeContexts(
            RaysShape shape, 
            int index, 
            int layerIndex, 
            Random rng,
            WaveConfig wave,
            float time,
            net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flowConfig) {
        
        java.util.List<RayContext> result = new java.util.ArrayList<>(4);
        
        // Check if we're using CONTINUOUS mode with waveCount > 1
        // In this case, we render multiple phase-offset copies of the same ray
        net.cyberpunk042.mcshaders.core.animation.WaveDistribution waveDist = shape.effectiveWaveDistribution();
        float waveCount = shape.effectiveWaveCount();
        boolean isContinuousMultiCopy = (waveDist == net.cyberpunk042.mcshaders.core.animation.WaveDistribution.CONTINUOUS) 
                                        && waveCount > 1.0f;
        
        if (isContinuousMultiCopy) {
            // CONTINUOUS with waveCount > 1: render multiple copies at different phases
            // Example: waveCount=2 -> copies at phase offsets 0 and 0.5
            // This creates overlapping spawning - as one ray fades out, another is already spawning
            int copyCount = Math.max(1, (int) Math.floor(waveCount));
            
            // Get base phase - either from animation OR from manual ShapeState.phase control
            float basePhase;
            if (flowConfig != null && flowConfig.isActive() && flowConfig.radiativeSpeed() > 0.001f) {
                // Animated: phase from time
                basePhase = (time * flowConfig.radiativeSpeed()) % 1.0f;
                if (basePhase < 0) basePhase += 1.0f;
            } else {
                // Manual: phase from ShapeState (user control via slider)
                basePhase = shape.effectiveShapeState().phase();
            }
            
            for (int copy = 0; copy < copyCount; copy++) {
                float phaseOffset = (float) copy / copyCount;
                float copyPhase = (basePhase + phaseOffset) % 1.0f;
                
                // Each copy uses its OWN phase for ShapeState, so edge effects are computed
                // per-copy based on that copy's actual position in the animation cycle
                RayContext ctx = computeContextWithPhaseForCopy(
                    shape, index, layerIndex, rng, wave, time, flowConfig, copyPhase);
                result.add(ctx);
            }
            return result;
        }
        
        // Check if we're in edge transition (need two shapes) - only for 3D ray types
        // LINE rays don't need the dual-context pattern since they don't have linking artifacts
        boolean is3DRayType = shape.effectiveRayType().is3D();
        
        if (is3DRayType && flowConfig != null && flowConfig.isActive()) {
            // Get RadiativeInteraction from shape (not config) - Energy Interaction model
            net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction radiative = shape.effectiveRadiativeInteraction();
            if (radiative == net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.EMISSION ||
                radiative == net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.ABSORPTION) {
                
                // Compute edge width based on sweepCopies (same logic as computeFlowAnimation)
                float sweepCopies = Math.max(0.1f, shape.effectiveWaveCount());
                float edgeWidth = 0.1f / sweepCopies;
                edgeWidth = Math.max(0.01f, Math.min(0.1f, edgeWidth));
                
                // Compute the phase for this ray (includes wave distribution)
                float rayPhase = computeRayPhase(flowConfig, shape, index, shape.count(), time);
                
                // During edge transitions, generate two shapes to prevent linking artifacts
                if (rayPhase < edgeWidth || rayPhase > (1.0f - edgeWidth)) {
                    // Generate both the main shape AND the wrapped shape
                    RayContext primaryContext = computeContextWithPhase(
                        shape, index, layerIndex, rng, wave, time, flowConfig, rayPhase, false);
                    RayContext wrappedContext = computeContextWithPhase(
                        shape, index, layerIndex, rng, wave, time, flowConfig, rayPhase, true);
                    
                    result.add(primaryContext);
                    result.add(wrappedContext);
                    return result;
                }
            }
        }
        
        // Normal case: single context
        result.add(computeContext(shape, index, layerIndex, rng, wave, time, flowConfig));
        return result;
    }
    
    /**
     * Computes context for a ray copy with a specific phase (for multi-copy mode).
     * Each copy uses its own phase for ShapeState, so edge effects are computed independently.
     */
    private static RayContext computeContextWithPhaseForCopy(
            RaysShape shape,
            int index,
            int layerIndex,
            Random rng,
            WaveConfig wave,
            float time,
            net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flowConfig,
            float copyPhase) {
        
        // Compute common position data
        PositionData pos = computePositionData(shape, index, layerIndex, rng);
        
        int lineResolution = shape.effectivelineResolution();
        boolean hasWave = wave != null && wave.isActive() && wave.isCpuMode();
        if (hasWave && lineResolution < 16) {
            lineResolution = 16;
        }
        
        // No position offset for multi-copy mode (all copies at same position, different phases)
        float flowPositionOffset = 0.0f;
        
        // Create ShapeState with this copy's specific phase
        // This way TessEdgeModeFactory will compute edge effects based on THIS copy's phase
        ShapeState<RayFlowStage> shapeState = shape.effectiveShapeState().withPhase(copyPhase);
        
        return RayContextBuilder.build(shape, pos.start, pos.end, pos.direction, pos.length,
            index, pos.count, layerIndex, pos.orientationVector, lineResolution, hasWave,
            wave, time, flowConfig, flowPositionOffset, shapeState);
    }
    
    /**
     * Computes the context for a single ray with flow animation support.
     */
    public static RayContext computeContext(
            RaysShape shape, 
            int index, 
            int layerIndex, 
            Random rng,
            WaveConfig wave,
            float time,
            net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flowConfig) {
        
        // Compute common position data
        PositionData pos = computePositionData(shape, index, layerIndex, rng);
        
        // Determine shape segments
        int lineResolution = shape.effectivelineResolution();
        boolean hasWave = wave != null && wave.isActive() && wave.isCpuMode();
        if (hasWave && lineResolution < 16) {
            lineResolution = 16;
        }
        
        // Compute flow position offset
        float flowPositionOffset = 0.0f;
        if (flowConfig != null && flowConfig.isActive()) {
            flowPositionOffset = computeFlowOffset(shape, flowConfig, index, pos.count, time, pos.direction);
            
            // Apply position offset (unless pathFollowing is enabled)
            boolean shouldTranslate = Math.abs(flowPositionOffset) > 0.001f && !flowConfig.pathFollowing();
            if (shouldTranslate) {
                applyOffset(pos.start, pos.direction, flowPositionOffset);
                applyOffset(pos.end, pos.direction, flowPositionOffset);
            }
        }
        
        // Compute animated ShapeState
        ShapeState<RayFlowStage> shapeState = computeAnimatedState(shape, flowConfig, index, pos.count, time);
        
        return RayContextBuilder.build(shape, pos.start, pos.end, pos.direction, pos.length,
            index, pos.count, layerIndex, pos.orientationVector, lineResolution, hasWave,
            wave, time, flowConfig, flowPositionOffset, shapeState);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Phase Computation (used for edge transition wrapping only)
    // NOTE: Flow animation is now handled by FlowPipeline + TessEdgeModeFactory
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Computes the phase for a ray in flow animation.
     * Delegates to FlowPhaseStage.computePhase.
     */
    private static float computeRayPhase(
            net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flow,
            RaysShape shape,
            int rayIndex, int rayCount, float time) {
        return net.cyberpunk042.mcshaders.core.mesh.ray.flow.FlowPhaseStage.computePhase(
            flow, shape, rayIndex, rayCount, time);
    }
    
    /**
     * Computes context for a ray at a specific phase, with optional wrapping.
     * @param wrapped If true, compute the "wrapped" shape (at the opposite edge)
     */
    private static RayContext computeContextWithPhase(
            RaysShape shape,
            int index,
            int layerIndex,
            Random rng,
            WaveConfig wave,
            float time,
            net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flowConfig,
            float phase,
            boolean wrapped) {
        
        // Compute common position data
        PositionData pos = computePositionData(shape, index, layerIndex, rng);
        
        int lineResolution = shape.lineResolution();
        boolean hasWave = wave != null && wave.isActive() && wave.isCpuMode();
        
        // Compute position offset based on phase and wrapped flag
        float posOffset = computeWrappedOffset(shape, phase, wrapped);
        
        // Apply position offset
        if (Math.abs(posOffset) > 0.001f) {
            applyOffset(pos.start, pos.direction, posOffset);
            applyOffset(pos.end, pos.direction, posOffset);
        }
        
        // Create ShapeState with the passed phase
        ShapeState<RayFlowStage> shapeState = shape.effectiveShapeState().withPhase(phase);
        
        return RayContextBuilder.build(shape, pos.start, pos.end, pos.direction, pos.length,
            index, pos.count, layerIndex, pos.orientationVector, lineResolution, hasWave,
            wave, time, flowConfig, posOffset, shapeState);
    }
    
    /** Computes position offset for wrapped/primary shapes based on radiative mode */
    private static float computeWrappedOffset(RaysShape shape, float phase, boolean wrapped) {
        // trajectorySpan = travel range (NOT the ray's actual length)
        float trajectorySpan = shape.outerRadius() - shape.innerRadius();
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction radiative = shape.effectiveRadiativeInteraction();
        
        if (wrapped) {
            // WRAPPED shape: positioned at the opposite edge from primary
            float sweepCopies = Math.max(0.1f, shape.effectiveWaveCount());
            float phaseEdgeWidth = Math.max(0.01f, Math.min(0.1f, 0.1f / sweepCopies));
            
            if (phase < phaseEdgeWidth) {
                // Primary is spawning -> wrapped is at far edge
                return (radiative == net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.EMISSION) 
                    ? trajectorySpan : 0.0f;
            } else {
                // Primary is despawning -> wrapped is at near edge
                return (radiative == net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.EMISSION) 
                    ? 0.0f : trajectorySpan;
            }
        } else {
            // PRIMARY shape: position based on phase
            return (radiative == net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.EMISSION)
                ? phase * trajectorySpan 
                : (1.0f - phase) * trajectorySpan;
        }
    }
    
    // NOTE: computeFlowAnimation DELETED - replaced by TessEdgeModeFactory
    
    /**
     * Computes the orientation vector based on the orientation mode.
     * 
     * @param orientation Orientation mode
     * @param start Ray start position (used for OUTWARD/INWARD/TANGENT)
     * @param direction Ray direction (used for ALONG_RAY/AGAINST_RAY)
     * @return Normalized orientation direction [x, y, z]
     */
    private static float[] computeOrientationVector(
            net.cyberpunk042.mcshaders.core.shape.RayOrientation orientation, 
            float[] start, 
            float[] direction) {
        
        return switch (orientation) {
            case ALONG_RAY -> new float[] { direction[0], direction[1], direction[2] };
            
            case AGAINST_RAY -> new float[] { -direction[0], -direction[1], -direction[2] };
            
            case UPWARD -> new float[] { 0, 1, 0 };
            
            case DOWNWARD -> new float[] { 0, -1, 0 };
            
            case OUTWARD -> {
                // Direction from center (0,0,0) to ray start
                float len = (float) Math.sqrt(start[0]*start[0] + start[1]*start[1] + start[2]*start[2]);
                if (len > 0.0001f) {
                    yield new float[] { start[0]/len, start[1]/len, start[2]/len };
                }
                yield new float[] { 0, 1, 0 }; // Fallback if at center
            }
            
            case INWARD -> {
                // Direction from ray start to center (0,0,0)
                float len = (float) Math.sqrt(start[0]*start[0] + start[1]*start[1] + start[2]*start[2]);
                if (len > 0.0001f) {
                    yield new float[] { -start[0]/len, -start[1]/len, -start[2]/len };
                }
                yield new float[] { 0, -1, 0 }; // Fallback if at center
            }
            
            case TANGENT -> {
                // Perpendicular to radial direction in XZ plane (for circular motion effect)
                float len = (float) Math.sqrt(start[0]*start[0] + start[2]*start[2]);
                if (len > 0.0001f) {
                    // Tangent is perpendicular to radial: (-z, 0, x) normalized
                    yield new float[] { -start[2]/len, 0, start[0]/len };
                }
                yield new float[] { 1, 0, 0 }; // Fallback if on Y axis
            }
        };
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Distribution Calculation
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Computes distribution-based offsets for a ray.
     * <p>Delegates to {@link DistributionFactory} for strategy-based computation.</p>
     * 
     * @param shape The rays shape configuration
     * @param index Ray index (0 to count-1)
     * @param count Total ray count
     * @param rng Random number generator for distribution randomness
     * @return DistributionResult with startOffset, lengthMod, angleJitter, radiusJitter
     */
    public static DistributionResult computeDistribution(
            RaysShape shape, 
            int index, 
            int count, 
            Random rng) {
        
        DistributionStrategy strategy = DistributionFactory.get(shape.distribution());
        return strategy.compute(shape, index, count, rng);
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // Position Calculation
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Computes start and end positions based on arrangement mode.
     * <p>Uses strategy pattern for both layer offset and arrangement computation.</p>
     * 
     * @param shape The rays shape configuration
     * @param index Ray index (0 to count-1)
     * @param count Total ray count
     * @param layerIndex Layer index (0 to layers-1)
     * @param layerSpacing Spacing between layers
     * @param dist Pre-computed distribution offsets
     * @param start Output array for start position [x, y, z]
     * @param end Output array for end position [x, y, z]
     */
    public static void computePosition(
            RaysShape shape, 
            int index, 
            int count,
            int layerIndex, 
            float layerSpacing,
            DistributionResult dist, 
            float[] start, 
            float[] end) {
        
        // Compute layer offset using strategy
        // For SPHERICAL/CONVERGING, VERTICAL and SPIRAL fall back to SHELL
        RayArrangement arrangement = shape.arrangement();
        RayLayerMode effectiveLayerMode = shape.effectiveLayerMode();
        
        if (arrangement == RayArrangement.SPHERICAL || 
            arrangement == RayArrangement.CONVERGING || 
            arrangement == RayArrangement.DIVERGING) {
            // Spherical arrangements don't support VERTICAL/SPIRAL layer modes
            if (effectiveLayerMode == RayLayerMode.VERTICAL || effectiveLayerMode == RayLayerMode.SPIRAL) {
                effectiveLayerMode = RayLayerMode.SHELL;
            }
        }
        
        LayerModeStrategy layerStrategy = LayerModeFactory.get(effectiveLayerMode);
        LayerOffset layerOffset = layerStrategy.computeOffset(shape, layerIndex, layerSpacing);
        
        // Compute position using arrangement strategy
        ArrangementStrategy arrangementStrategy = ArrangementFactory.get(arrangement);
        arrangementStrategy.compute(shape, index, count, layerOffset, dist, start, end);
    }
}
