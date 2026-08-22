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
 * Cage options for sphere shapes.
 * 
 * <p>Renders latitude and longitude lines as a structured grid.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "cage": {
 *   "lineWidth": 1.0,
 *   "latitudeCount": 8,
 *   "longitudeCount": 16,
 *   "showEquator": true,
 *   "showPoles": true
 * }
 * </pre>
 * 
 * @see CageOptions
 * @see net.cyberpunk042.mcshaders.core.shape.SphereShape
 */
public record SphereCageOptions(
    @Range(ValueRange.POSITIVE_NONZERO) float lineWidth,
    boolean showEdges,
    @Range(ValueRange.STEPS) int latitudeCount,
    @Range(ValueRange.STEPS) int longitudeCount,
    boolean showEquator,
    boolean showPoles
) implements CageOptions {
    
    /** Default sphere cage (8 lat, 16 lon, show equator and poles). */
    public static final SphereCageOptions DEFAULT = new SphereCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 8, 16, true, true);
    
    /** Minimal sphere cage (4 lat, 8 lon). */
    public static final SphereCageOptions MINIMAL = new SphereCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 4, 8, true, false);
    
    /** Dense sphere cage (16 lat, 32 lon). */
    public static final SphereCageOptions DENSE = new SphereCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, 16, 32, true, true);
    
    /**
     * Creates sphere cage options with specified counts.
     * @param latCount Number of latitude lines
     * @param lonCount Number of longitude lines
     */
    public static SphereCageOptions of(int latCount, int lonCount) {
        return new SphereCageOptions(CageOptions.DEFAULT_LINE_WIDTH, true, 
            latCount, lonCount, true, true);
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
            .latitudeCount(latitudeCount)
            .longitudeCount(longitudeCount)
            .showEquator(showEquator)
            .showPoles(showPoles);
    }
    
    public static class Builder {
        private float lineWidth = CageOptions.DEFAULT_LINE_WIDTH;
        private boolean showEdges = true;
        private int latitudeCount = 8;
        private int longitudeCount = 16;
        private boolean showEquator = true;
        private boolean showPoles = true;
        
        public Builder lineWidth(float w) { this.lineWidth = w; return this; }
        public Builder showEdges(boolean s) { this.showEdges = s; return this; }
        public Builder latitudeCount(int c) { this.latitudeCount = c; return this; }
        public Builder longitudeCount(int c) { this.longitudeCount = c; return this; }
        public Builder showEquator(boolean s) { this.showEquator = s; return this; }
        public Builder showPoles(boolean s) { this.showPoles = s; return this; }
        
        public SphereCageOptions build() {
            return new SphereCageOptions(lineWidth, showEdges, latitudeCount, 
                longitudeCount, showEquator, showPoles);
        }
    }
}
