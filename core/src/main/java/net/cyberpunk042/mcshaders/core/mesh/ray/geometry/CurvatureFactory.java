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

import net.cyberpunk042.mcshaders.core.shape.RayCurvature;

/**
 * Factory for creating CurvatureStrategy instances.
 * 
 * <p>Maps RayCurvature enum values to their corresponding strategy implementations.</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayGeometryUtils#computeCurvedPosition
 */
public final class CurvatureFactory {
    
    private CurvatureFactory() {}
    
    /**
     * Get the appropriate strategy for a curvature type.
     */
    public static CurvatureStrategy get(RayCurvature curvature) {
        if (curvature == null) {
            return NoCurvature.INSTANCE;
        }
        
        // Matches RayGeometryUtils.computeCurvatureAngle switch cases
        return switch (curvature) {
            case NONE -> NoCurvature.INSTANCE;
            case VORTEX -> VortexCurvature.INSTANCE;
            case SPIRAL_ARM -> SpiralCurvature.INSTANCE;
            case TANGENTIAL -> TangentialCurvature.INSTANCE;
            case LOGARITHMIC -> LogarithmicCurvature.INSTANCE;
            case PINWHEEL -> PinwheelCurvature.INSTANCE;
            case ORBITAL -> OrbitalCurvature.INSTANCE;
        };
    }
}
