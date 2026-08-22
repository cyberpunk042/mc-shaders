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
package net.cyberpunk042.mcshaders.core.mesh.ray.geometry3d;

/**
 * No deformation - identity transform.
 */
public final class GeoNoDeformation implements GeoDeformationStrategy {
    
    public static final GeoNoDeformation INSTANCE = new GeoNoDeformation();
    
    private GeoNoDeformation() {}
    
    @Override
    public void apply(float[] position, float t, float[] fieldCenter, float intensity, float fieldRadius) {
        // No change
    }
    
    @Override
    public boolean isActive() {
        return false;
    }
}
