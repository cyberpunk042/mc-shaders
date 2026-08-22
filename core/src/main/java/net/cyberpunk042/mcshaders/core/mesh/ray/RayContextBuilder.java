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

import net.cyberpunk042.mcshaders.core.mesh.ray.distribution.DistributionResult;
import net.cyberpunk042.mcshaders.core.animation.WaveConfig;
import net.cyberpunk042.mcshaders.core.shape.*;

/**
 * Builds RayContext instances from computed position data.
 * 
 * <p>Extracted from RayPositioner to centralize context building logic
 * and eliminate duplication between computeContext and computeContextWithPhase.</p>
 */
public final class RayContextBuilder {
    
    private RayContextBuilder() {} // Utility class
    
    /**
     * Builds a RayContext from position data and shape configuration.
     * 
     * @param shape The rays shape
     * @param start Computed start position [x, y, z]
     * @param end Computed end position [x, y, z]
     * @param direction Computed direction vector [x, y, z]
     * @param length Computed ray length
     * @param index Ray index
     * @param count Total ray count
     * @param layerIndex Layer index
     * @param orientationVector Computed orientation vector [x, y, z]
     * @param lineResolution Number of segments for tessellation
     * @param hasWave Whether wave animation is active
     * @param wave Wave config (may be null)
     * @param time Current animation time
     * @param flowConfig Flow animation config (may be null)
     * @param flowPositionOffset Position offset from flow animation
     * @param shapeState Computed shape state with animated phase
     * @return Built RayContext
     */
    public static RayContext build(
            RaysShape shape,
            float[] start,
            float[] end,
            float[] direction,
            float length,
            int index,
            int count,
            int layerIndex,
            float[] orientationVector,
            int lineResolution,
            boolean hasWave,
            WaveConfig wave,
            float time,
            net.cyberpunk042.mcshaders.core.animation.RayFlowConfig flowConfig,
            float flowPositionOffset,
            ShapeState<RayFlowStage> shapeState) {
        
        // Compute field deformation
        FieldDeformationMode fieldDeformation = shape.effectiveFieldDeformation();
        float fieldDeformationIntensity = shape.fieldDeformationIntensity();
        float normalizedDistance = 0.5f;
        float fieldStretch = 1.0f;
        
        if (fieldDeformation.isActive()) {
            float distFromCenter = (float) Math.sqrt(
                start[0] * start[0] + start[1] * start[1] + start[2] * start[2]);
            float innerR = shape.innerRadius();
            float outerR = shape.outerRadius();
            if (outerR > innerR) {
                normalizedDistance = Math.max(0.01f, Math.min(1.0f, 
                    (distFromCenter - innerR) / (outerR - innerR)));
            }
            fieldStretch = fieldDeformation.computeStretch(normalizedDistance, fieldDeformationIntensity);
        }
        
        return RayContext.builder()
            .start(start)
            .end(end)
            .direction(direction)
            .length(length)
            .index(index)
            .count(count)
            .layerIndex(layerIndex)
            .t(count > 1 ? (float) index / (count - 1) : 0f)
            .width(shape.rayWidth())
            .fadeStart(shape.fadeStart())
            .fadeEnd(shape.fadeEnd())
            .lineShape(shape.effectiveLineShape())
            .lineShapeAmplitude(shape.lineShapeAmplitude())
            .lineShapeFrequency(shape.lineShapeFrequency())
            .curvature(shape.effectiveCurvature())
            .curvatureIntensity(shape.curvatureIntensity())
            .lineResolution(lineResolution)
            .orientation(shape.effectiveRayOrientation())
            .orientationVector(orientationVector)
            .shapeIntensity(shape.shapeIntensity())
            .shapeLength(shape.shapeLength())  // Axial stretch (<1 squashed, >1 elongated)
            .shapeSize(shape.rayLength())      // Overall size of 3D shape
            .wave(wave)
            .time(time)
            .hasWave(hasWave)
            .flowConfig(flowConfig)
            .flowPositionOffset(flowPositionOffset)
            .travelRange(shape.outerRadius() - shape.innerRadius())
            .innerRadius(shape.innerRadius())
            .outerRadius(shape.outerRadius())
            .fieldDeformation(fieldDeformation)
            .fieldDeformationIntensity(fieldDeformationIntensity)
            .normalizedDistance(normalizedDistance)
            .fieldStretch(fieldStretch)
            .shapeState(shapeState)
            .build();
    }
}
