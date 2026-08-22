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

import net.cyberpunk042.mcshaders.core.shape.RaysShape;

/**
 * Vertical layer mode - layers stacked along Y axis.
 * 
 * <p>Based on RayPositioner.computeRadial lines 1058-1061:</p>
 * <pre>
 * layerY = (layerIndex - (layers - 1) / 2.0f) * layerSpacing;
 * </pre>
 * 
 * <p>Layers are centered around Y=0, so with 3 layers:
 * layer 0 = -spacing, layer 1 = 0, layer 2 = +spacing</p>
 * 
 * @see LayerModeStrategy
 */
public final class VerticalLayerMode implements LayerModeStrategy {
    
    public static final VerticalLayerMode INSTANCE = new VerticalLayerMode();
    
    private VerticalLayerMode() {}
    
    @Override
    public LayerOffset computeOffset(RaysShape shape, int layerIndex, float layerSpacing) {
        int totalLayers = shape.layers();
        
        // Center layers around Y=0
        // With 3 layers: 0→-1*spacing, 1→0, 2→+1*spacing
        float yOffset = (layerIndex - (totalLayers - 1) / 2.0f) * layerSpacing;
        
        return LayerOffset.vertical(yOffset);
    }
    
    @Override
    public String name() {
        return "VERTICAL";
    }
}
