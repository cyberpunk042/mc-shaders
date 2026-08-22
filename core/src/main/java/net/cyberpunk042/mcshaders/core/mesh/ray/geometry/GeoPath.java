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
 * Interface for ray path geometry.
 * 
 * Abstracts how a ray's path is computed - straight line, curved, 
 * with line shape modifiers, etc.
 */
public interface GeoPath {
    
    /**
     * Get position at parameter t (0-1 along path).
     * 
     * @param t Parameter along path (0 = start, 1 = end)
     * @return Position [x, y, z]
     */
    float[] positionAt(float t);
    
    /**
     * Get tangent direction at parameter t.
     * 
     * @param t Parameter along path
     * @return Normalized tangent direction [x, y, z]
     */
    float[] tangentAt(float t);
    
    /**
     * Total length of the path.
     */
    float length();
    
    /**
     * Start position.
     */
    default float[] start() {
        return positionAt(0f);
    }
    
    /**
     * End position.
     */
    default float[] end() {
        return positionAt(1f);
    }
}
