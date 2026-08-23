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
package net.cyberpunk042.mcshaders.core.pattern;

/**
 * Interface for controlling vertex arrangement in tessellation.
 * 
 * <p>Each pattern type applies to a specific {@link CellType}:
 * <ul>
 *   <li>{@link QuadPattern} - For QUAD cells (sphere lat/lon, prism sides)</li>
 *   <li>{@link SegmentPattern} - For SEGMENT cells (rings)</li>
 *   <li>{@link SectorPattern} - For SECTOR cells (discs)</li>
 *   <li>{@link EdgePattern} - For EDGE cells (cages, wireframes)</li>
 *   <li>{@link TrianglePattern} - For TRIANGLE cells (icosphere, polyhedra)</li>
 * </ul>
 * 
 * <h2>Two Main Operations</h2>
 * <ol>
 *   <li><b>shouldRender()</b> - Filter: should this cell be rendered at all?</li>
 *   <li><b>getVertexOrder()</b> - Reorder: how to arrange vertices into triangles?</li>
 * </ol>
 * 
 * <h2>Semantic Vertex Naming</h2>
 * <p>Implementations use semantic enums internally for readability,
 * but return {@code int[][]} for rendering efficiency:
 * <ul>
 *   <li>{@link QuadPattern} uses {@link QuadPattern.Corner} (TOP_LEFT, TOP_RIGHT, etc.)</li>
 *   <li>{@link TrianglePattern} uses {@link TrianglePattern.Vertex} (A, B, C)</li>
 * </ul>
 * 
 * <h2>JSON Format</h2>
 * <pre>
 * "appearance": {
 *   "arrangement": "filled_1"   // pattern name
 * }
 * </pre>
 * 
 * @see CellType
 * @see QuadPattern.Corner
 * @see TrianglePattern.Vertex
 */
public interface VertexPattern {
    
    /**
     * Pattern identifier (lowercase, e.g., "filled_1", "alternating").
     */
    String id();
    
    /**
     * Display name for UI/commands. Defaults to id().
     */
    default String displayName() {
        return id();
    }
    
    /**
     * Which cell type this pattern is designed for.
     */
    CellType cellType();
    
    /**
     * Determines if a cell at the given index should be rendered.
     * 
     * <p>This is the primary filter method. Return false to skip a cell entirely.
     * 
     * @param index Cell index (0-based)
     * @param total Total number of cells in this shape
     * @return true if this cell should be rendered
     */
    boolean shouldRender(int index, int total);
    
    /**
     * Gets the vertex indices for each triangle to render.
     * 
     * <p>For a quad (4 vertices), this typically returns two triangles.
     * For a triangle (3 vertices), this returns one triangle.
     * 
     * <p>Each int[] is a triangle: {v0, v1, v2} where v0-v2 are indices
     * into the cell's vertex array.
     * 
     * <h3>Vertex Index Conventions</h3>
     * <ul>
     *   <li><b>Quad:</b> 0=TOP_LEFT, 1=TOP_RIGHT, 2=BOTTOM_LEFT, 3=BOTTOM_RIGHT</li>
     *   <li><b>Triangle:</b> 0=A (apex), 1=B (left), 2=C (right)</li>
     *   <li><b>Segment:</b> 0=inner0, 1=inner1, 2=outer0, 3=outer1</li>
     *   <li><b>Sector:</b> 0=center, 1=edge0, 2=edge1</li>
     *   <li><b>Edge:</b> 0=start, 1=end</li>
     * </ul>
     * 
     * @return Array of triangles, each triangle is 3 vertex indices
     */
    int[][] getVertexOrder();
    
    // =========================================================================
    // Static Utilities
    // =========================================================================
    
    /**
     * Parses a pattern from a name alone, guessing which family it belongs to.
     *
     * <p><b>Prefer {@link #resolveForCellType(String, CellType)} wherever the cell type is
     * known.</b> A name on its own cannot always identify a pattern: five ids are declared by
     * more than one family, and this method takes the first family that claims one, in a fixed
     * order (quad, segment, sector, edge, triangle, then the {@code shuffle_<cellType>_<n>}
     * names, which spell their cell type out and so are never ambiguous). That means
     * {@code "full"}, {@code "alternating"}, {@code "sparse"}, {@code "quarter"} and
     * {@code "dashed"} always yield the segment pattern, and eight constants &mdash; among them
     * {@code TrianglePattern.FULL}, {@code EdgePattern.FULL} and {@code SectorPattern.FULL}
     * &mdash; cannot be reached through this method at all.
     *
     * <p>That matters because vertex indices mean different things per cell type (see
     * {@link #getVertexOrder()}). A pattern from the wrong family does not merely carry the wrong
     * label: used on a quad, a sector or triangle pattern covers half of it and an edge pattern
     * draws nothing, both silently.
     *
     * @param value Pattern name (e.g., "filled_1", "spiral", "alternating")
     * @return The pattern, or {@code QuadPattern.DEFAULT} if the name matches nothing.
     *         Never {@code null}, and never reports that the name was unrecognised.
     */
    static VertexPattern fromString(String value) {
        if (value == null || value.isEmpty()) {
            return QuadPattern.DEFAULT;
        }
        
        String lower = value.toLowerCase().trim();
        
        // Try each pattern type
        VertexPattern pattern = QuadPattern.fromId(lower);
        if (pattern != null) return pattern;
        
        pattern = SegmentPattern.fromId(lower);
        if (pattern != null) return pattern;
        
        pattern = SectorPattern.fromId(lower);
        if (pattern != null) return pattern;
        
        pattern = EdgePattern.fromId(lower);
        if (pattern != null) return pattern;
        
        pattern = TrianglePattern.fromId(lower);
        if (pattern != null) return pattern;
        
        // Try dynamic shuffle pattern (e.g., "shuffle_quad_42")
        pattern = ShufflePattern.parse(value);
        if (pattern != null) return pattern;
        
        // Unrecognised. There is no channel to report it on from here, so this is
        // indistinguishable to the caller from an explicit "filled_1".
        return QuadPattern.DEFAULT;
    }
    
    /**
     * Resolves a pattern for a specific CellType.
     * <p>If the pattern's cellType doesn't match, first tries to find a pattern with
     * the same name in the expected CellType. Falls back to default if not found.</p>
     * 
     * @param patternName Pattern name
     * @param expectedCellType The CellType the shape expects
     * @return The pattern, or equivalent pattern for the expected CellType
     */
    static VertexPattern resolveForCellType(String patternName, CellType expectedCellType) {
        VertexPattern pattern = fromString(patternName);
        
        if (pattern.cellType() != expectedCellType) {
            // Try to find a pattern with the same name in the expected CellType
            VertexPattern equivalentPattern = findPatternByNameForCellType(patternName, expectedCellType);
            if (equivalentPattern != null) {
                return equivalentPattern;
            }
            
            // Fall back to default for this CellType
            return defaultForCellType(expectedCellType);
        }
        
        return pattern;
    }
    
    /**
     * Tries to find a pattern by name in a specific CellType.
     * @return The pattern if found, or null
     */
    private static VertexPattern findPatternByNameForCellType(String patternName, CellType cellType) {
        if (patternName == null) return null;
        String lower = patternName.toLowerCase().trim();
        
        return switch (cellType) {
            case QUAD -> QuadPattern.fromId(lower);
            case SEGMENT -> SegmentPattern.fromId(lower);
            case SECTOR -> SectorPattern.fromId(lower);
            case EDGE -> EdgePattern.fromId(lower);
            case TRIANGLE -> TrianglePattern.fromId(lower);
        };
    }
    
    /**
     * Returns a default pattern for the given CellType.
     */
    static VertexPattern defaultForCellType(CellType cellType) {
        return switch (cellType) {
            case QUAD -> QuadPattern.DEFAULT;
            case SEGMENT -> SegmentPattern.FULL;
            case SECTOR -> SectorPattern.FULL;
            case EDGE -> EdgePattern.FULL;
            case TRIANGLE -> TrianglePattern.DEFAULT;
        };
    }
    
    /**
     * Returns all pattern names across all types.
     */
    static String[] allNames() {
        var names = new java.util.ArrayList<String>();
        for (QuadPattern p : QuadPattern.values()) names.add(p.id());
        for (SegmentPattern p : SegmentPattern.values()) names.add(p.id());
        for (SectorPattern p : SectorPattern.values()) names.add(p.id());
        for (EdgePattern p : EdgePattern.values()) names.add(p.id());
        for (TrianglePattern p : TrianglePattern.values()) names.add(p.id());
        return names.toArray(new String[0]);
    }
}
