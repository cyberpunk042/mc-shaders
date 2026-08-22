/*
 * Ported from the-virus-block-mc (net.cyberpunk042.visual), where it is
 * geometry model code with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split
 * recorded in docs/PORTING.md.
 *
 * JSON binding was removed on the way across: the model stays free of
 * serialisation so it can be loaded from external content. The @JsonField
 * metadata is retained for a codec layer above core.
 */
package net.cyberpunk042.mcshaders.core.shape;

/**
 * Controls how multiple ray layers are arranged in space.
 * 
 * <p>When {@code layers > 1}, this determines whether additional layers
 * are stacked vertically, extend radially outward, or both.</p>
 * 
 * <h2>Layer Modes</h2>
 * <ul>
 *   <li><b>VERTICAL</b>: Layers stacked along Y axis (default for RADIAL arrangement)</li>
 *   <li><b>RADIAL</b>: Layers extend outward at increasing radii</li>
 *   <li><b>SHELL</b>: Layers form concentric spherical shells (for SPHERICAL arrangement)</li>
 *   <li><b>SPIRAL</b>: Layers spiral outward, combining radial and angular offset</li>
 * </ul>
 * 
 * @see RaysShape
 */
public enum RayLayerMode {
    /**
     * Layers stacked vertically (Y axis).
     * <p>Each layer is at a different height: layerY = layerIndex * layerSpacing</p>
     */
    VERTICAL("Vertical"),
    
    /**
     * Layers extend radially outward.
     * <p>Each layer starts where the previous layer ends.
     * Layer 0: innerRadius to innerRadius + rayLength
     * Layer 1: (innerRadius + rayLength) to (innerRadius + 2*rayLength)
     * etc.</p>
     */
    RADIAL("Radial"),
    
    /**
     * Layers form concentric spherical shells.
     * <p>Each layer is a shell offset from the previous by layerSpacing.
     * Best used with SPHERICAL arrangement.</p>
     */
    SHELL("Shell"),
    
    /**
     * Layers spiral outward.
     * <p>Each layer is offset both radially and angularly, creating a spiral pattern.</p>
     */
    SPIRAL("Spiral");
    
    private final String displayName;
    
    RayLayerMode(String displayName) {
        this.displayName = displayName;
    }
    
    public String displayName() {
        return displayName;
    }
    
    /**
     * Checks if this mode uses radial offset (extends outward).
     */
    public boolean usesRadialOffset() {
        return this == RADIAL || this == SPIRAL;
    }
    
    /**
     * Checks if this mode uses vertical offset (stacked).
     */
    public boolean usesVerticalOffset() {
        return this == VERTICAL;
    }
}
