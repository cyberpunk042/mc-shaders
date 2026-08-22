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
package net.cyberpunk042.mcshaders.core.mesh.ray.arrangement;

import net.cyberpunk042.mcshaders.core.shape.RayArrangement;

/**
 * Factory for creating ArrangementStrategy instances.
 */
public final class ArrangementFactory {
    
    private ArrangementFactory() {}
    
    /**
     * Get the appropriate strategy for an arrangement type.
     */
    public static ArrangementStrategy get(RayArrangement arrangement) {
        if (arrangement == null) {
            return RadialArrangement.INSTANCE;
        }
        
        return switch (arrangement) {
            case RADIAL -> RadialArrangement.INSTANCE;
            case SPHERICAL, DIVERGING -> SphericalArrangement.DIVERGING;
            case CONVERGING -> SphericalArrangement.CONVERGING;
            case PARALLEL -> ParallelArrangement.INSTANCE;
        };
    }
}
