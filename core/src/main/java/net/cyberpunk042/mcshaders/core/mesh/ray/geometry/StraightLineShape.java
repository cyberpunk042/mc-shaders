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
 * Straight line shape - no offset (identity).
 */
public final class StraightLineShape implements LineShapeStrategy {
    
    private static final float[] ZERO = { 0, 0, 0 };
    
    public static final StraightLineShape INSTANCE = new StraightLineShape();
    
    private StraightLineShape() {}
    
    @Override
    public float[] computeOffset(float t, float amplitude, float frequency, float phaseOffset) {
        return ZERO;
    }
    
    @Override
    public boolean needsSegments() {
        return false;
    }
    
    @Override
    public int suggestedMinSegments() {
        return 1;
    }
}
