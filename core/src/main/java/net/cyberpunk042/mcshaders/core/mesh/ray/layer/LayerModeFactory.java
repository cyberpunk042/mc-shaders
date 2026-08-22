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
package net.cyberpunk042.mcshaders.core.mesh.ray.layer;

import net.cyberpunk042.mcshaders.core.shape.RayLayerMode;

/**
 * Factory for creating LayerModeStrategy instances.
 * 
 * <p>Maps {@link RayLayerMode} enum values to their strategy implementations:</p>
 * <ul>
 *   <li>{@link RayLayerMode#VERTICAL} → {@link VerticalLayerMode}</li>
 *   <li>{@link RayLayerMode#RADIAL} → {@link RadialLayerMode}</li>
 *   <li>{@link RayLayerMode#SHELL} → {@link ShellLayerMode}</li>
 *   <li>{@link RayLayerMode#SPIRAL} → {@link SpiralLayerMode}</li>
 * </ul>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner#computePosition
 */
public final class LayerModeFactory {
    
    private LayerModeFactory() {}
    
    /**
     * Get the appropriate strategy for a layer mode.
     * 
     * @param layerMode The layer mode type
     * @return Layer mode strategy (never null - defaults to VERTICAL)
     */
    public static LayerModeStrategy get(RayLayerMode layerMode) {
        if (layerMode == null) {
            return VerticalLayerMode.INSTANCE;
        }
        
        return switch (layerMode) {
            case VERTICAL -> VerticalLayerMode.INSTANCE;
            case RADIAL -> RadialLayerMode.INSTANCE;
            case SHELL -> ShellLayerMode.INSTANCE;
            case SPIRAL -> SpiralLayerMode.INSTANCE;
        };
    }
}
