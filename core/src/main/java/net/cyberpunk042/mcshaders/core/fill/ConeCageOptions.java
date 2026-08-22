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
 * Cage options for cone shapes.
 * 
 * <p>Renders radial lines from apex to base edge and horizontal ring sections.</p>
 * 
 * <h2>Visual Pattern</h2>
 * <pre>
 *         *        ← apex
 *        /|\
 *       / | \      ← radial lines from apex
 *      /--|--\     ← horizontal rings
 *     /   |   \
 *    /_________\   ← base circle
 * </pre>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "cage": {
 *   "lineWidth": 1.0,
 *   "radialLines": 12,
 *   "horizontalRings": 3,
 *   "showBase": true,
 *   "showApex": true
 * }
 * </pre>
 * 
 * @see CageOptions
 * @see net.cyberpunk042.mcshaders.core.shape.ConeShape
 */
public record ConeCageOptions(
    @Range(ValueRange.POSITIVE_NONZERO) float lineWidth,
    boolean showEdges,
    @Range(ValueRange.STEPS) int radialLines,
    @Range(ValueRange.STEPS) int horizontalRings,
    boolean showBase,
    boolean showApex
) implements CageOptions {
    
    /** Default cone cage (12 radials, 3 rings, show base and apex). */
    public static final ConeCageOptions DEFAULT = new ConeCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 12, 3, true, true);
    
    /** Minimal cone cage (6 radials, 1 ring). */
    public static final ConeCageOptions MINIMAL = new ConeCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 6, 1, true, true);
    
    /**
     * Creates cone cage options.
     * @param radials Number of radial lines from apex to base
     * @param rings Number of horizontal rings
     */
    public static ConeCageOptions of(int radials, int rings) {
        return new ConeCageOptions(CageOptions.DEFAULT_LINE_WIDTH, true, 
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
            .horizontalRings(horizontalRings)
            .showBase(showBase)
            .showApex(showApex);
    }
    
    public static class Builder {
        private float lineWidth = CageOptions.DEFAULT_LINE_WIDTH;
        private boolean showEdges = true;
        private int radialLines = 12;
        private int horizontalRings = 3;
        private boolean showBase = true;
        private boolean showApex = true;
        
        public Builder lineWidth(float w) { this.lineWidth = w; return this; }
        public Builder showEdges(boolean s) { this.showEdges = s; return this; }
        public Builder radialLines(int c) { this.radialLines = c; return this; }
        public Builder horizontalRings(int c) { this.horizontalRings = c; return this; }
        public Builder showBase(boolean s) { this.showBase = s; return this; }
        public Builder showApex(boolean s) { this.showApex = s; return this; }
        
        public ConeCageOptions build() {
            return new ConeCageOptions(lineWidth, showEdges, radialLines, 
                horizontalRings, showBase, showApex);
        }
    }
}
