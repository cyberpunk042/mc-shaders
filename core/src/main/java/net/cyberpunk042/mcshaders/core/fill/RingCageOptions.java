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
 * Cage options for ring (annulus) shapes.
 * 
 * <p>Renders radial lines and concentric rings for both inner and outer edges.</p>
 * 
 * <h2>Visual Pattern</h2>
 * <pre>
 *     ___________
 *    /   _____   \
 *   /   /     \   \   ← outer edge ring
 *  |   |       |   |  ← radial lines span inner to outer
 *   \   \_____/   /   ← inner edge ring
 *    \___________/
 * </pre>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "cage": {
 *   "lineWidth": 1.0,
 *   "radialLines": 16,
 *   "concentricRings": 2,
 *   "showInnerEdge": true,
 *   "showOuterEdge": true
 * }
 * </pre>
 * 
 * @see CageOptions
 * @see net.cyberpunk042.mcshaders.core.shape.RingShape
 */
public record RingCageOptions(
    @Range(ValueRange.POSITIVE_NONZERO) float lineWidth,
    boolean showEdges,
    @Range(ValueRange.STEPS) int radialLines,
    @Range(ValueRange.STEPS) int concentricRings,
    boolean showInnerEdge,
    boolean showOuterEdge
) implements CageOptions {
    
    /** Default ring cage (16 radials, 2 rings, show both edges). */
    public static final RingCageOptions DEFAULT = new RingCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 16, 2, true, true);
    
    /** Minimal ring cage (8 radials, 0 internal rings). */
    public static final RingCageOptions MINIMAL = new RingCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 8, 0, true, true);
    
    /**
     * Creates ring cage options.
     * @param radials Number of radial lines
     * @param rings Number of concentric rings between inner and outer edge
     */
    public static RingCageOptions of(int radials, int rings) {
        return new RingCageOptions(CageOptions.DEFAULT_LINE_WIDTH, true, 
            radials, rings, true, true);
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
            .radialLines(radialLines)
            .concentricRings(concentricRings)
            .showInnerEdge(showInnerEdge)
            .showOuterEdge(showOuterEdge);
    }
    
    public static class Builder {
        private float lineWidth = CageOptions.DEFAULT_LINE_WIDTH;
        private boolean showEdges = true;
        private int radialLines = 16;
        private int concentricRings = 2;
        private boolean showInnerEdge = true;
        private boolean showOuterEdge = true;
        
        public Builder lineWidth(float w) { this.lineWidth = w; return this; }
        public Builder showEdges(boolean s) { this.showEdges = s; return this; }
        public Builder radialLines(int c) { this.radialLines = c; return this; }
        public Builder concentricRings(int c) { this.concentricRings = c; return this; }
        public Builder showInnerEdge(boolean s) { this.showInnerEdge = s; return this; }
        public Builder showOuterEdge(boolean s) { this.showOuterEdge = s; return this; }
        
        public RingCageOptions build() {
            return new RingCageOptions(lineWidth, showEdges, radialLines, 
                concentricRings, showInnerEdge, showOuterEdge);
        }
    }
}
