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

import net.cyberpunk042.mcshaders.core.mesh.MeshBuilder;
import net.cyberpunk042.mcshaders.core.mesh.ray.geometry3d.GeoDeformationFactory;
import net.cyberpunk042.mcshaders.core.mesh.ray.geometry3d.GeoDeformationStrategy;
import net.cyberpunk042.mcshaders.core.mesh.ray.geometry3d.GeoPolarSurfaceGenerator;
import net.cyberpunk042.mcshaders.core.mesh.ray.tessellation.TessEdgeResult;
import net.cyberpunk042.mcshaders.core.energy.EnergyTravel;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;
import net.cyberpunk042.mcshaders.core.shape.RayType;
import net.cyberpunk042.mcshaders.core.shape.SphereDeformation;

/**
 * Tessellator for 3D spherical ray types (DROPLET, CONE, EGG, BULLET, etc.).
 * 
 * <p>Uses the existing {@link SphereDeformation} infrastructure from ShapeMath
 * to generate proper parametric shapes with full intensity support:
 * <ul>
 *   <li>intensity 0 = sphere</li>
 *   <li>intensity 1 = full deformation (sharp droplet, etc.)</li>
 * </ul>
 * 
 * <p>This tessellator handles RayTypes that map to SphereDeformation shapes.
 * Non-spherical ray types (CUBES, CRYSTALS, etc.) need separate tessellators.</p>
 * 
 * @see SphereDeformation
 * @see GeoPolarSurfaceGenerator
 */
public class RaySphericalTessellator implements RayTypeTessellator {
    
    /** Singleton instance. */
    public static final RaySphericalTessellator INSTANCE = new RaySphericalTessellator();
    
    /** Minimum rings for smooth 3D shape. */
    private static final int MIN_RINGS = 6;
    
    /** Minimum segments for round cross-section. */
    private static final int MIN_SEGMENTS = 6;
    
    private RaySphericalTessellator() {} // Use INSTANCE
    
    @Override
    public void tessellate(MeshBuilder builder, RaysShape shape, RayContext context) {
        tessellate(builder, shape, context, null, null);
    }
    
    @Override
    public void tessellate(MeshBuilder builder, RaysShape shape, RayContext context,
                          net.cyberpunk042.mcshaders.core.pattern.VertexPattern pattern,
                          net.cyberpunk042.mcshaders.core.visibility.VisibilityMask visibility) {
        
        // === MAP RAY TYPE TO SPHERE DEFORMATION ===
        RayType rayType = shape != null ? shape.effectiveRayType() : RayType.DROPLET;
        SphereDeformation deformation = mapToSphereDeformation(rayType);
        float intensity = context.shapeIntensity();
        
        // === FLOW CONFIG ===
        net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flowConfig = context.flowConfig();
        
        // === ENERGY MODE (must compute FIRST to know if animation should play) ===
        net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction radiativeMode = 
            shape != null ? shape.effectiveRadiativeInteraction() 
            : net.cyberpunk042.mcshaders.core.energy.RadiativeInteraction.NONE;
        
        // === RADIATIVE PHASE ===
        // Phase is now pre-computed in RayPositioner.computeContexts() which handles:
        // - Manual phase (from user slider)
        // - Animated phase (from time * radiativeSpeed)
        // - Multi-copy phase offsets (for CONTINUOUS + waveCount > 1)
        // The tessellator just uses the phase as-is from ShapeState.
        final float userPhase = context.effectiveShapeState().phase();
        
        // Check if radiative animation is conceptually active (for wave distribution offset)
        final boolean animationPlaying = flowConfig != null 
            && flowConfig.hasRadiative() 
            && radiativeMode.isActive();
        
        // Add wave distribution offset for this ray (ONLY during animation)
        // In manual mode, all rays should have the same phase for consistent user control
        float waveOffset = 0f;
        if (animationPlaying) {
            waveOffset = net.cyberpunk042.mcshaders.core.mesh.ray.flow.FlowPhaseStage.computeRayPhaseOffset(
                shape, context.index(), context.count());
        }
        
        // Use the pre-computed phase + wave offset
        float rayPhase = (userPhase + waveOffset) % 1.0f;
        if (rayPhase < 0) rayPhase += 1.0f;
        
        // === COMPUTE CLIP RANGE (where the shape is on the ray) ===
        // segmentLength = rayLength / trajectorySpan (as a fraction)
        float trajectorySpan = shape != null ? shape.trajectorySpan() : 1.0f;
        float rayLength = shape != null ? shape.effectiveRayLength() : 1.0f;
        float segmentLength = trajectorySpan > 0 ? Math.min(1.0f, rayLength / trajectorySpan) : 1.0f;
        boolean startFullLength = shape != null && shape.startFullLength();
        
        var clipRange = net.cyberpunk042.mcshaders.core.mesh.ray.tessellation.RadiativeInteractionFactory.compute(
            radiativeMode, rayPhase, segmentLength, startFullLength);
        
        // === APPLY EDGE MODE (HOW the shape looks at edges) ===
        net.cyberpunk042.mcshaders.core.shape.EdgeTransitionMode edgeMode = context.effectiveShapeState().edgeMode();
        float edgeIntensity = context.effectiveShapeState().edgeIntensity();
        TessEdgeResult edgeResult = net.cyberpunk042.mcshaders.core.mesh.ray.tessellation.TessEdgeModeFactory.compute(
            edgeMode, clipRange, edgeIntensity);
        
        // Skip if completely hidden
        if (!edgeResult.isVisible()) {
            return;
        }
        
        // Position is the CENTER of the clip range
        // Note: rayPhase already includes all phase offsets (time, wave, layer)
        // so we don't add userPhase here - that would cause double-offset
        float positionT = (clipRange.start() + clipRange.end()) * 0.5f;
        
        // === COMPUTE CENTER POSITION ===
        float length = context.length();
        
        // EdgeMode scale affects WIDTH only (radius)
        // RadiativeInteraction scale affects overall shape size (from modes like OSCILLATION)
        float edgeScale = edgeResult.scale();         // From EdgeMode (CLIP=1, SCALE=varies, FADE=1)
        float radiativeScale = clipRange.scale();     // From RadiativeInteraction (OSCILLATION etc)
        
        // Shape dimensions:
        // - shapeSize (from rayLength): overall size/radius of the shape
        // - shapeLength: axial stretch factor (<1 = squashed, 1 = sphere, >1 = elongated)
        float shapeSize = context.shapeSize();       // Overall size (from rayLength)
        float shapeStretch = context.shapeLength();  // Axial stretch factor
        
        // Base size with radiative scale applied
        float scaledSize = shapeSize * radiativeScale;
        
        // Axial length = base size * stretch factor
        float axialLength = scaledSize * shapeStretch;
        // Radius (equatorial width) = half of base size, affected by edge scale
        float baseRadius = (scaledSize * 0.5f) * edgeScale;
        
        float[] start = context.start();
        float[] end = context.end();
        float[] center;
        float[] direction;
        
        // Apply curvature if present
        net.cyberpunk042.mcshaders.core.shape.RayCurvature curvature = context.curvature();
        float curvatureIntensity = context.curvatureIntensity();
        boolean hasCurvature = curvature != null 
            && curvature != net.cyberpunk042.mcshaders.core.shape.RayCurvature.NONE 
            && curvatureIntensity > 0.001f;
        
        if (hasCurvature) {
            // With curvature: position on curved path at positionT
            center = RayGeometryUtils.computeCurvedPosition(start, end, positionT, curvature, curvatureIntensity);
            direction = RayGeometryUtils.computeCurvedTangent(start, end, positionT, curvature, curvatureIntensity);
        } else {
            // Without curvature: linear interpolation at positionT
            center = new float[] {
                start[0] + (end[0] - start[0]) * positionT,
                start[1] + (end[1] - start[1]) * positionT,
                start[2] + (end[2] - start[2]) * positionT
            };
            direction = context.orientationVector();
        }
        
        // === MESH RESOLUTION ===
        int totalSegs = Math.max(12, context.lineResolution());
        int rings = Math.max(MIN_RINGS, totalSegs / 2);
        int segments = Math.max(MIN_SEGMENTS, totalSegs / 2);
        
        // === EDGE MODE PARAMETERS ===
        float visibleTStart = edgeResult.clipStart();
        float visibleTEnd = edgeResult.clipEnd();
        float alpha = edgeResult.alpha();
        float edgeDistStart = edgeResult.edgeDistanceStart();
        float edgeDistEnd = edgeResult.edgeDistanceEnd();
        
        // === FLICKER ANIMATION ===
        if (flowConfig != null && flowConfig.hasFlicker()) {
            float flickerAlpha = net.cyberpunk042.mcshaders.core.mesh.ray.flow.FlowFlickerStage.computeFlickerAlpha(
                flowConfig.effectiveFlicker(), context.time(), context.index(),
                flowConfig.flickerIntensity(), flowConfig.flickerFrequency());
            alpha *= flickerAlpha;
        }
        
        // === CHECK FOR FIELD DEFORMATION ===
        net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode deformMode = context.fieldDeformation();
        GeoDeformationStrategy fieldDeformation = GeoDeformationFactory.get(deformMode);
        
        // === CHECK FOR TRAVEL EFFECT ===
        EnergyTravel travelMode = null;
        float travelPhase = 0f;
        int chaseCount = 1;
        float chaseWidth = 0.3f;
        
        if (flowConfig != null && flowConfig.hasTravel()) {
            travelMode = flowConfig.effectiveTravel();
            travelPhase = (context.time() * flowConfig.travelSpeed() * 0.3f) % 1.0f;
            chaseCount = flowConfig.chaseCount();
            chaseWidth = flowConfig.chaseWidth();
        }

        
        // === GENERATE MESH ===
        if (fieldDeformation != null && fieldDeformation.isActive()) {
            // With deformation - use full generator with deformation callback
            float[] fieldCenter = new float[] {0, 0, 0};
            float outerRadius = shape != null ? shape.outerRadius() : 3.0f;
            float deformIntensity = context.fieldDeformationIntensity();
            
            GeoPolarSurfaceGenerator.generateWithSphereDeformation(
                builder, deformation, intensity, center, direction, axialLength, baseRadius,
                rings, segments, pattern, visibility,
                visibleTStart, visibleTEnd, alpha,
                travelMode, travelPhase, chaseCount, chaseWidth,
                fieldDeformation, fieldCenter, deformIntensity, outerRadius,
                0xFFFFFFFF,  // Color will be set by renderer
                edgeDistStart, edgeDistEnd
            );
        } else {
            // No field deformation - use standard generator
            GeoPolarSurfaceGenerator.generateWithSphereDeformation(
                builder, deformation, intensity, center, direction, axialLength, baseRadius,
                rings, segments, pattern, visibility,
                visibleTStart, visibleTEnd, alpha,
                travelMode, travelPhase, chaseCount, chaseWidth,
                null, null, 0f, 0f,
                0xFFFFFFFF,  // Color will be set by renderer
                edgeDistStart, edgeDistEnd
            );
        }
    }
    
    /**
     * Maps RayType to the appropriate SphereDeformation.
     * 
     * @param rayType The ray type
     * @return Corresponding sphere deformation, defaults to DROPLET
     */
    private static SphereDeformation mapToSphereDeformation(RayType rayType) {
        if (rayType == null) {
            return SphereDeformation.DROPLET;
        }
        
        return switch (rayType) {
            // Direct mappings - Basic shapes
            case DROPLET -> SphereDeformation.DROPLET;
            case CONE -> SphereDeformation.CONE;
            case ARROW -> SphereDeformation.CONE;  // Arrow uses cone (pointed tip)
            case CAPSULE -> SphereDeformation.BULLET;  // Capsule uses bullet (hemisphere + cylinder)
            
            // Direct mappings - Organic/natural shapes
            case SPHERE -> SphereDeformation.NONE;  // Perfect sphere (no deformation)
            case SPHEROID -> SphereDeformation.SPHEROID;  // Oblate/prolate spheroid
            case OVOID -> SphereDeformation.OVOID;  // Smooth egg-like
            case EGG -> SphereDeformation.EGG;  // Asymmetric egg
            case PEAR -> SphereDeformation.PEAR;  // Wide base, narrow top
            case BULLET -> SphereDeformation.BULLET;  // Hemisphere tip + cylinder
            
            // Energy effects - use spheroid or droplet
            case KAMEHAMEHA -> SphereDeformation.SPHEROID;  // Spherical energy ball
            case LASER -> SphereDeformation.SPHEROID;  // Uniform beam
            case FIRE_JET -> SphereDeformation.DROPLET;  // Wide base, narrow tip
            case PLASMA -> SphereDeformation.DROPLET;  // Organic shape
            
            // Particle types - use sphere (no deformation)
            case BEADS -> SphereDeformation.NONE;  // Perfect spheres
            
            // Organic types
            case TENDRIL -> SphereDeformation.EGG;  // Organic asymmetry
            case SPINE -> SphereDeformation.BULLET;  // Segmented with rounded ends
            case ROOT -> SphereDeformation.PEAR;  // Natural taper
            
            // Fallback for non-spherical types (shouldn't reach here)
            case LINE, LIGHTNING, CUBES, STARS, CRYSTALS -> SphereDeformation.NONE;
        };
    }
    
    @Override
    public String name() {
        return "RaySphericalTessellator";
    }
}
