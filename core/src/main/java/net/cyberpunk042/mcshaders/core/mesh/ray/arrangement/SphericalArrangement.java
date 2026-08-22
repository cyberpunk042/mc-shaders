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

import net.cyberpunk042.mcshaders.core.mesh.ray.distribution.DistributionResult;
import net.cyberpunk042.mcshaders.core.mesh.ray.layer.LayerOffset;
import net.cyberpunk042.mcshaders.core.shape.RaysShape;

/**
 * Spherical arrangement - rays distributed uniformly on a sphere surface.
 * Uses Fibonacci sphere algorithm for even distribution.
 * 
 * <pre>
 *            ↑
 *          ↗ | ↖
 *       ←──●──→
 *          ↙ | ↘
 *            ↓
 * </pre>
 * 
 * <p>Supports both DIVERGING (outward) and CONVERGING (inward) modes.</p>
 * 
 * <p>Based on RayPositioner.computeSpherical lines 1079-1137.</p>
 * 
 * @see ArrangementStrategy
 */
public final class SphericalArrangement implements ArrangementStrategy {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    private static final float GOLDEN_RATIO = (float) ((1 + Math.sqrt(5)) / 2);
    
    private final boolean converging;
    
    public static final SphericalArrangement DIVERGING = new SphericalArrangement(false);
    public static final SphericalArrangement CONVERGING = new SphericalArrangement(true);
    
    /**
     * @param converging If true, rays point inward (CONVERGING). If false, outward (DIVERGING/SPHERICAL).
     */
    public SphericalArrangement(boolean converging) {
        this.converging = converging;
    }
    
    @Override
    public void compute(
            RaysShape shape,
            int index,
            int count,
            LayerOffset layerOffset,
            DistributionResult dist,
            float[] outStart,
            float[] outEnd) {
        
        float innerRadius = shape.innerRadius();
        float outerRadius = shape.outerRadius();
        
        // Fibonacci sphere distribution for even coverage
        // With angle jitter from distribution
        float phi = (float) Math.acos(1 - 2 * (index + 0.5f) / count);
        float theta = TWO_PI * index / GOLDEN_RATIO + dist.angleJitter();
        
        float sinPhi = (float) Math.sin(phi);
        float cosPhi = (float) Math.cos(phi);
        float cosTheta = (float) Math.cos(theta);
        float sinTheta = (float) Math.sin(theta);
        
        // Direction vector (normalized, points outward from center)
        float dx = sinPhi * cosTheta;
        float dy = cosPhi;
        float dz = sinPhi * sinTheta;
        
        // For spherical, we use radiusOffset for layer spacing
        // (yOffset and angleOffset don't apply to spherical)
        float shellOffset = layerOffset.radiusOffset();
        
        // Check unifiedEnd - when true, all layers converge to same inner/outer radius
        boolean unifiedEnd = shape.unifiedEnd();
        
        // SIMPLE: use innerRadius and outerRadius directly
        // rayLength is NOT used here - it's used in tessellation for the animated segment
        
        if (converging) {
            // Converging: start at outer, end at inner
            float outerR;
            float innerR;
            
            if (unifiedEnd) {
                innerR = innerRadius * (1 + dist.radiusJitter());
                outerR = (outerRadius + shellOffset) * (1 + dist.radiusJitter());
            } else {
                innerR = (innerRadius + shellOffset) * (1 + dist.radiusJitter());
                outerR = (outerRadius + shellOffset) * (1 + dist.radiusJitter());
            }
            outerR += dist.startOffset();
            innerR += dist.startOffset();
            if (innerR < 0) innerR = 0;
            
            outStart[0] = dx * outerR;
            outStart[1] = dy * outerR;
            outStart[2] = dz * outerR;
            
            outEnd[0] = dx * innerR;
            outEnd[1] = dy * innerR;
            outEnd[2] = dz * innerR;
        } else {
            // Diverging: start at inner, end at outer
            float innerR;
            float outerR;
            
            if (unifiedEnd) {
                innerR = (innerRadius + dist.startOffset()) * (1 + dist.radiusJitter());
                outerR = (outerRadius + shellOffset + dist.startOffset()) * (1 + dist.radiusJitter());
            } else {
                innerR = (innerRadius + shellOffset + dist.startOffset()) * (1 + dist.radiusJitter());
                outerR = (outerRadius + shellOffset + dist.startOffset()) * (1 + dist.radiusJitter());
            }
            
            outStart[0] = dx * innerR;
            outStart[1] = dy * innerR;
            outStart[2] = dz * innerR;
            
            outEnd[0] = dx * outerR;
            outEnd[1] = dy * outerR;
            outEnd[2] = dz * outerR;
        }
    }
    
    @Override
    public String name() {
        return converging ? "CONVERGING" : "SPHERICAL";
    }
}
