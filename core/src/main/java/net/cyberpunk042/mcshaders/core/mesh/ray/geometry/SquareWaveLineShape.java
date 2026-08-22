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

/**
 * Square wave line shape - binary step pattern.
 * 
 * <p>Based on RayGeometryUtils.computeLineShapeOffsetWithPhase:
 * wave = amplitude * (sin(theta) > 0 ? 1 : -1)
 * Offset in right direction.</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayGeometryUtils#computeLineShapeOffsetWithPhase
 */
public final class SquareWaveLineShape implements LineShapeStrategy {
    
    private static final float TWO_PI = (float) (Math.PI * 2);
    
    public static final SquareWaveLineShape INSTANCE = new SquareWaveLineShape();
    
    private SquareWaveLineShape() {}
    
    @Override
    public float[] computeOffset(float t, float amplitude, float frequency, float phaseOffset) {
        // From RayGeometryUtils.computeLineShapeOffsetWithPhase lines 199-203:
        // theta = t * frequency * TWO_PI + phaseOffset
        // wave = amplitude * (sin(theta) > 0 ? 1 : -1)
        
        float theta = t * frequency * TWO_PI + phaseOffset;
        float wave = amplitude * ((float) Math.sin(theta) > 0 ? 1f : -1f);
        
        return new float[] { wave, 0, 0 };
    }
    
    @Override
    public int suggestedMinSegments() {
        return 12;
    }
}
