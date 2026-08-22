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
 * Spiral layer mode - layers combine angular and radial offset.
 * 
 * <p>Based on RayPositioner.computeRadial lines 1070-1074:</p>
 * <pre>
 * layerAngleOffset = layerIndex * TWO_PI / 8; // 45° between layers
 * layerRadiusOffset = layerIndex * layerSpacing;
 * </pre>
 * 
 * <p>Creates a spiral pattern where each layer is both rotated
 * and pushed outward. This creates a pinwheel/spiral galaxy effect
 * when viewed from above.</p>
 * 
 * @see LayerModeStrategy
 */
public final class SpiralLayerMode implements LayerModeStrategy {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    /** Default angle step between layers: 45 degrees. */
    private static final float DEFAULT_ANGLE_STEP = TWO_PI / 8;
    
    public static final SpiralLayerMode INSTANCE = new SpiralLayerMode(DEFAULT_ANGLE_STEP);
    
    private final float angleStep;
    
    public SpiralLayerMode(float angleStep) {
        this.angleStep = angleStep;
    }
    
    @Override
    public LayerOffset computeOffset(RaysShape shape, int layerIndex, float layerSpacing) {
        // Angular offset: rotate each layer by angleStep (default 45°)
        float angleOffset = layerIndex * angleStep;
        
        // Radial offset: push each layer outward by layerSpacing
        float radiusOffset = layerIndex * layerSpacing;
        
        return LayerOffset.spiral(radiusOffset, angleOffset);
    }
    
    @Override
    public String name() {
        return "SPIRAL";
    }
}
