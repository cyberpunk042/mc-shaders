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
package net.cyberpunk042.mcshaders.core.fill;

import net.cyberpunk042.mcshaders.core.validation.Range;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;

/**
 * Cage options for cylinder shapes.
 * 
 * <p>Renders vertical lines and horizontal rings around the cylinder.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "cage": {
 *   "lineWidth": 1.0,
 *   "verticalLines": 16,
 *   "horizontalRings": 4,
 *   "showCaps": true
 * }
 * </pre>
 * 
 * @see CageOptions
 * @see net.cyberpunk042.mcshaders.core.shape.CylinderShape
 */
public record CylinderCageOptions(
    @Range(ValueRange.POSITIVE_NONZERO) float lineWidth,
    boolean showEdges,
    @Range(ValueRange.STEPS) int verticalLines,
    @Range(ValueRange.STEPS) int horizontalRings,
    boolean showCaps
) implements CageOptions {
    
    /** Default cylinder cage. */
    public static final CylinderCageOptions DEFAULT = new CylinderCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 16, 4, true);
    
    /** Minimal cylinder cage. */
    public static final CylinderCageOptions MINIMAL = new CylinderCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 8, 2, true);
    
    /**
     * Creates cylinder cage options.
     * @param vertLines Number of vertical lines
     * @param horzRings Number of horizontal rings
     */
    public static CylinderCageOptions of(int vertLines, int horzRings) {
        return new CylinderCageOptions(CageOptions.DEFAULT_LINE_WIDTH, true, 
            vertLines, horzRings, true);
    }
    
    // =========================================================================
    // Builder
    // =========================================================================
    
    public static Builder builder() { return new Builder(); }
    /** Create a builder pre-populated with this record's values. */
    public Builder toBuilder() {
        return new Builder()
            .lineWidth(lineWidth)
            .showEdges(showEdges)
            .verticalLines(verticalLines)
            .horizontalRings(horizontalRings)
            .showCaps(showCaps);
    }
    
    public static class Builder {
        private float lineWidth = CageOptions.DEFAULT_LINE_WIDTH;
        private boolean showEdges = true;
        private int verticalLines = 16;
        private int horizontalRings = 4;
        private boolean showCaps = true;
        
        public Builder lineWidth(float w) { this.lineWidth = w; return this; }
        public Builder showEdges(boolean s) { this.showEdges = s; return this; }
        public Builder verticalLines(int c) { this.verticalLines = c; return this; }
        public Builder horizontalRings(int c) { this.horizontalRings = c; return this; }
        public Builder showCaps(boolean s) { this.showCaps = s; return this; }
        
        public CylinderCageOptions build() {
            return new CylinderCageOptions(lineWidth, showEdges, verticalLines, 
                horizontalRings, showCaps);
        }
    }
}
