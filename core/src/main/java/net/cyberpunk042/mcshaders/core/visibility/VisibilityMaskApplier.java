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
package net.cyberpunk042.mcshaders.core.visibility;

/**
 * Determines which cells should render based on a {@link VisibilityMask}.
 * 
 * <p>Mask types:</p>
 * <ul>
 *   <li>FULL - All cells visible</li>
 *   <li>BANDS - Horizontal bands (latitude)</li>
 *   <li>STRIPES - Vertical stripes (longitude)</li>
 *   <li>CHECKER - Alternating pattern</li>
 *   <li>RADIAL - Distance from center</li>
 *   <li>GRADIENT - Smooth transition</li>
 * </ul>
 * 
 * @see VisibilityMask
 * @see MaskType
 */
public final class VisibilityMaskApplier {
    
    private VisibilityMaskApplier() {}
    
    // =========================================================================
    // Visibility Check
    // =========================================================================
    
    /**
     * Determines if a cell should be rendered.
     * 
     * @param mask The visibility mask configuration
     * @param index Current cell index
     * @param totalCells Total number of cells
     * @param row Row index (for 2D patterns like checker)
     * @param col Column index (for 2D patterns)
     * @return true if the cell should render
     */
    public static boolean shouldRender(VisibilityMask mask, int index, int totalCells,
                                        int row, int col) {
        if (mask == null || mask.mask() == MaskType.FULL) {
            return true;
        }
        
        int count = Math.max(1, mask.count());
        float thickness = Math.max(0.01f, mask.thickness());
        
        boolean visible = switch (mask.mask()) {
            case FULL -> true;
            
            case BANDS -> {
                // Horizontal bands based on row
                float normalizedRow = (float) row / count;
                float bandPos = normalizedRow % 1.0f;
                yield bandPos < thickness;
            }
            
            case STRIPES -> {
                // Vertical stripes based on column
                float normalizedCol = (float) col / count;
                float stripePos = normalizedCol % 1.0f;
                yield stripePos < thickness;
            }
            
            case CHECKER -> {
                // Alternating pattern
                yield ((row + col) % 2) == 0;
            }
            
            case RADIAL -> {
                // Distance from center (for circular patterns)
                float progress = (float) index / totalCells;
                yield (progress * count) % 1.0f < thickness;
            }
            
            case GRADIENT -> {
                // Smooth gradient (all visible, but with varying alpha)
                yield true;  // Gradient affects alpha, not visibility
            }
            
            case CUSTOM -> {
                // Custom pattern - always visible, handled externally
                yield true;
            }
        };
        
        // Apply invert
        if (mask.invert()) {
            visible = !visible;
        }
        
        return visible;
    }
    
    /**
     * Simplified check for 1D patterns.
     * 
     * @param mask The visibility mask
     * @param index Current cell index
     * @param total Total cells
     * @return true if visible
     */
    public static boolean shouldRender(VisibilityMask mask, int index, int total) {
        // For 1D, derive row/col from index
        int cols = (int) Math.sqrt(total);
        if (cols == 0) cols = 1;
        int row = index / cols;
        int col = index % cols;
        return shouldRender(mask, index, total, row, col);
    }
    
    /**
     * Gets the alpha multiplier for gradient masks.
     * 
     * @param mask The visibility mask
     * @param index Current cell index  
     * @param total Total cells
     * @return Alpha multiplier (0-1)
     */
    public static float getAlphaMultiplier(VisibilityMask mask, int index, int total) {
        if (mask == null || mask.mask() != MaskType.GRADIENT) {
            return 1.0f;
        }
        
        float progress = (float) index / total;
        float alpha = mask.invert() ? (1.0f - progress) : progress;
        
        // Apply feather for smooth edges
        if (mask.feather() > 0) {
            alpha = smoothstep(0, mask.feather(), alpha);
        }

        return alpha;
    }
    
    /**
     * Smooth interpolation function.
     */
    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
        return t * t * (3 - 2 * t);
    }
}
