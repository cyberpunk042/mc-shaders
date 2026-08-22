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

/**
 * Result of layer offset computation.
 * 
 * <p>Layer modes produce different combinations of offsets:</p>
 * <ul>
 *   <li><b>VERTICAL:</b> Only yOffset (stacked vertically)</li>
 *   <li><b>RADIAL:</b> Only radiusOffset (rays extend further outward)</li>
 *   <li><b>SHELL:</b> Only radiusOffset (concentric shells at increasing radii)</li>
 *   <li><b>SPIRAL:</b> Both radiusOffset and angleOffset (spiral pattern)</li>
 * </ul>
 * 
 * @param yOffset Vertical offset (Y axis displacement)
 * @param radiusOffset Radial offset (adds to inner radius)
 * @param angleOffset Angular offset in radians (rotates ray position)
 * 
 * @see LayerModeStrategy
 * @see net.cyberpunk042.mcshaders.core.mesh.ray.RayPositioner#computePosition
 */
public record LayerOffset(
    float yOffset,
    float radiusOffset,
    float angleOffset
) {
    /** No offset - identity. */
    public static final LayerOffset ZERO = new LayerOffset(0f, 0f, 0f);
    
    /** Whether any offset is applied. */
    public boolean hasOffset() {
        return Math.abs(yOffset) > 0.0001f || 
               Math.abs(radiusOffset) > 0.0001f || 
               Math.abs(angleOffset) > 0.0001f;
    }
    
    /** Create with only Y offset. */
    public static LayerOffset vertical(float y) {
        return new LayerOffset(y, 0f, 0f);
    }
    
    /** Create with only radius offset. */
    public static LayerOffset radial(float radius) {
        return new LayerOffset(0f, radius, 0f);
    }
    
    /** Create with radius and angle offset (spiral). */
    public static LayerOffset spiral(float radius, float angle) {
        return new LayerOffset(0f, radius, angle);
    }
}
