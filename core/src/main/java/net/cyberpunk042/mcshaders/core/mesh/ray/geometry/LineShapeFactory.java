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
package net.cyberpunk042.mcshaders.core.mesh.ray.geometry;

import net.cyberpunk042.mcshaders.core.shape.RayLineShape;

/**
 * Factory for creating LineShapeStrategy instances.
 * 
 * <p>Maps RayLineShape enum values to their corresponding strategy implementations.</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayGeometryUtils#computeLineShapeOffsetWithPhase
 */
public final class LineShapeFactory {
    
    private LineShapeFactory() {}
    
    /**
     * Get the appropriate strategy for a line shape type.
     */
    public static LineShapeStrategy get(RayLineShape lineShape) {
        if (lineShape == null) {
            return StraightLineShape.INSTANCE;
        }
        
        // Matches RayGeometryUtils.computeLineShapeOffsetWithPhase switch cases
        return switch (lineShape) {
            case STRAIGHT -> StraightLineShape.INSTANCE;
            case SINE_WAVE -> SineWaveLineShape.INSTANCE;
            case CORKSCREW, DOUBLE_HELIX -> CorkscrewLineShape.INSTANCE;
            case SPRING -> SpringLineShape.INSTANCE;
            case ZIGZAG -> ZigzagLineShape.INSTANCE;
            case SAWTOOTH -> SawtoothLineShape.INSTANCE;
            case SQUARE_WAVE -> SquareWaveLineShape.INSTANCE;
            case ARC -> ArcLineShape.INSTANCE;
            case S_CURVE -> SCurveLineShape.INSTANCE;
        };
    }
}
