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

import net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode;

/**
 * Factory for creating GeoDeformationStrategy instances.
 * 
 * <p>Based on FieldDeformationMode enum values.</p>
 * 
 * @see net.cyberpunk042.mcshaders.core.shape.FieldDeformationMode
 */
public final class GeoDeformationFactory {
    
    private GeoDeformationFactory() {}
    
    /**
     * Get the appropriate strategy for a deformation mode.
     */
    public static GeoDeformationStrategy get(FieldDeformationMode mode) {
        if (mode == null || mode == FieldDeformationMode.NONE) {
            return GeoNoDeformation.INSTANCE;
        }
        
        // All active deformation modes use GeoSpaghettification with the mode
        // to determine stretch direction (GRAVITATIONAL, REPULSION, TIDAL)
        return new GeoSpaghettification(mode);
    }
}
