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
 * Cage options for torus (donut) shapes.
 * 
 * <p>Renders major rings (around the hole) and minor rings (around the tube).</p>
 * 
 * <h2>Visual Pattern</h2>
 * <pre>
 *      _____
 *     /     \
 *    |  ___  |    ← major rings (go around the hole)
 *    | |   | |
 *    |  ---  |    ← minor rings (go around the tube cross-section)
 *     \_____/
 * </pre>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "cage": {
 *   "lineWidth": 1.0,
 *   "majorRings": 16,
 *   "minorRings": 8
 * }
 * </pre>
 * 
 * @see CageOptions
 * @see net.cyberpunk042.mcshaders.core.shape.TorusShape
 */
public record TorusCageOptions(
    @Range(ValueRange.POSITIVE_NONZERO) float lineWidth,
    boolean showEdges,
    @Range(ValueRange.STEPS) int majorRings,
    @Range(ValueRange.STEPS) int minorRings
) implements CageOptions {
    
    /** Default torus cage (16 major, 8 minor rings). */
    public static final TorusCageOptions DEFAULT = new TorusCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 16, 8);
    
    /** Minimal torus cage (8 major, 4 minor rings). */
    public static final TorusCageOptions MINIMAL = new TorusCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 8, 4);
    
    /** Dense torus cage (32 major, 16 minor rings). */
    public static final TorusCageOptions DENSE = new TorusCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 32, 16);
    
    /**
     * Creates torus cage options.
     * @param major Number of major rings (around the hole)
     * @param minor Number of minor rings (around the tube)
     */
    public static TorusCageOptions of(int major, int minor) {
        return new TorusCageOptions(CageOptions.DEFAULT_LINE_WIDTH, true, major, minor);
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
            .majorRings(majorRings)
            .minorRings(minorRings);
    }
    
    public static class Builder {
        private float lineWidth = CageOptions.DEFAULT_LINE_WIDTH;
        private boolean showEdges = true;
        private int majorRings = 16;
        private int minorRings = 8;
        
        public Builder lineWidth(float w) { this.lineWidth = w; return this; }
        public Builder showEdges(boolean s) { this.showEdges = s; return this; }
        public Builder majorRings(int c) { this.majorRings = c; return this; }
        public Builder minorRings(int c) { this.minorRings = c; return this; }
        
        public TorusCageOptions build() {
            return new TorusCageOptions(lineWidth, showEdges, majorRings, minorRings);
        }
    }
}
