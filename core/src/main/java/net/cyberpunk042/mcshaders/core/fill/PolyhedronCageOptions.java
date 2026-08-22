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
 * Cage options for polyhedron shapes.
 * 
 * <p>Renders edges and optionally face outlines for polyhedra.</p>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "cage": {
 *   "lineWidth": 1.0,
 *   "allEdges": true,
 *   "faceOutlines": false
 * }
 * </pre>
 * 
 * @see CageOptions
 * @see net.cyberpunk042.mcshaders.core.shape.PolyhedronShape
 */
public record PolyhedronCageOptions(
    @Range(ValueRange.POSITIVE_NONZERO) float lineWidth,
    boolean showEdges,
    boolean allEdges,
    boolean faceOutlines
) implements CageOptions {
    
    /** Default polyhedron cage (all edges). */
    public static final PolyhedronCageOptions DEFAULT = new PolyhedronCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, true, false);
    
    /** Face outlines only (no internal edges). */
    public static final PolyhedronCageOptions FACE_OUTLINES = new PolyhedronCageOptions(
        CageOptions.DEFAULT_LINE_WIDTH, true, false, true);
    
    /**
     * Creates polyhedron cage options.
     * @param allEdges Whether to show all edges
     * @param faceOutlines Whether to show face outlines
     */
    public static PolyhedronCageOptions of(boolean allEdges, boolean faceOutlines) {
        return new PolyhedronCageOptions(CageOptions.DEFAULT_LINE_WIDTH, true, 
            allEdges, faceOutlines);
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
            .allEdges(allEdges)
            .faceOutlines(faceOutlines);
    }
    
    public static class Builder {
        private float lineWidth = CageOptions.DEFAULT_LINE_WIDTH;
        private boolean showEdges = true;
        private boolean allEdges = true;
        private boolean faceOutlines = false;
        
        public Builder lineWidth(float w) { this.lineWidth = w; return this; }
        public Builder showEdges(boolean s) { this.showEdges = s; return this; }
        public Builder allEdges(boolean a) { this.allEdges = a; return this; }
        public Builder faceOutlines(boolean f) { this.faceOutlines = f; return this; }
        
        public PolyhedronCageOptions build() {
            return new PolyhedronCageOptions(lineWidth, showEdges, allEdges, faceOutlines);
        }
    }
}
