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

import net.cyberpunk042.mcshaders.core.pattern.ShuffleGenerator.*;
import net.cyberpunk042.mcshaders.core.pattern.TrianglePattern.Vertex;

/**
 * A dynamic VertexPattern created from a ShuffleGenerator permutation.
 * 
 * <p>This allows the Explorer tab to test any permutation for ALL cell types
 * in real-time. Each cell type has different shuffle behaviors:</p>
 * 
 * <ul>
 *   <li><b>QUAD</b>: Vertex reordering across 600 permutations</li>
 *   <li><b>TRIANGLE</b>: Winding order (A→B→C variants) + skip intervals</li>
 *   <li><b>SEGMENT</b>: Skip interval + phase offset + winding direction</li>
 *   <li><b>SECTOR</b>: Skip interval + phase offset + invert selection</li>
 *   <li><b>EDGE</b>: Lat/lon visibility + skip interval</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * <pre>
 * // From Explorer tab
 * VertexPattern pattern = ShufflePattern.fromPermutation(CellType.SEGMENT, 42);
 * 
 * // Or by parsing special name format
 * VertexPattern pattern = ShufflePattern.parse("shuffle_segment_42");
 * </pre>
 */
public class ShufflePattern implements VertexPattern {
    
    private final CellType cellType;
    private final int permutation;
    private final String id;
    private final int[][] vertexOrder;
    
    // Shuffle-specific properties for shouldRender()
    private final int skipInterval;
    private final int phaseOffset;
    private final boolean invertSelection;   // For SECTOR
    private final boolean reverseWinding;    // For SEGMENT
    private final boolean showLatitude;      // For EDGE
    private final boolean showLongitude;     // For EDGE
    
    private ShufflePattern(CellType cellType, int permutation, int[][] vertexOrder,
                           int skipInterval, int phaseOffset, 
                           boolean invertSelection, boolean reverseWinding,
                           boolean showLatitude, boolean showLongitude) {
        this.cellType = cellType;
        this.permutation = permutation;
        this.id = "shuffle_" + cellType.name().toLowerCase() + "_" + permutation;
        this.vertexOrder = vertexOrder;
        this.skipInterval = skipInterval;
        this.phaseOffset = phaseOffset;
        this.invertSelection = invertSelection;
        this.reverseWinding = reverseWinding;
        this.showLatitude = showLatitude;
        this.showLongitude = showLongitude;
    }
    
    /**
     * Creates a ShufflePattern from a permutation index.
     * 
     * @param cellType The cell type
     * @param permutation The permutation index (0-based)
     * @return The pattern, or null if invalid
     */
    public static ShufflePattern fromPermutation(CellType cellType, int permutation) {
        return switch (cellType) {
            case QUAD -> createQuadPattern(permutation);
            case TRIANGLE -> createTrianglePattern(permutation);
            case SEGMENT -> createSegmentPattern(permutation);
            case SECTOR -> createSectorPattern(permutation);
            case EDGE -> createEdgePattern(permutation);
        };
    }
    
    private static ShufflePattern createQuadPattern(int permutation) {
        if (permutation < 0 || permutation >= ShuffleGenerator.quadCount()) {
            return null;
        }
        QuadArrangement arr = ShuffleGenerator.getQuad(permutation);
        int[][] vertexOrder = arr.toVertexOrder();
        return new ShufflePattern(CellType.QUAD, permutation, vertexOrder,
            1, 0, false, false, true, true);  // Skip=1 means no skipping
    }
    
    private static ShufflePattern createTrianglePattern(int permutation) {
        if (permutation < 0 || permutation >= ShuffleGenerator.triangleCount()) {
            return null;
        }
        TriangleArrangement arr = ShuffleGenerator.getTriangle(permutation);
        
        // Convert Vertex enum to indices
        int[][] vertexOrder = new int[][] {
            { arr.vertices()[0].index, arr.vertices()[1].index, arr.vertices()[2].index }
        };
        
        return new ShufflePattern(CellType.TRIANGLE, permutation, vertexOrder,
            arr.skipInterval(), 0, false, false, true, true);
    }
    
    private static ShufflePattern createSegmentPattern(int permutation) {
        if (permutation < 0 || permutation >= ShuffleGenerator.segmentCount()) {
            return null;
        }
        SegmentArrangement arr = ShuffleGenerator.getSegment(permutation);
        
        // Segment cells are quads with 4 vertices: inner0, inner1, outer0, outer1
        // Standard winding: two triangles forming the quad segment
        int[][] vertexOrder;
        if (arr.reverseWinding()) {
            // Reversed winding
            vertexOrder = new int[][] {
                { 0, 1, 3 },  // inner0 -> inner1 -> outer1
                { 0, 3, 2 }   // inner0 -> outer1 -> outer0
            };
        } else {
            // Normal winding
            vertexOrder = new int[][] {
                { 0, 2, 1 },  // inner0 -> outer0 -> inner1
                { 1, 2, 3 }   // inner1 -> outer0 -> outer1
            };
        }
        
        return new ShufflePattern(CellType.SEGMENT, permutation, vertexOrder,
            arr.skipInterval(), arr.phaseOffset(), false, arr.reverseWinding(), true, true);
    }
    
    private static ShufflePattern createSectorPattern(int permutation) {
        if (permutation < 0 || permutation >= ShuffleGenerator.sectorCount()) {
            return null;
        }
        SectorArrangement arr = ShuffleGenerator.getSector(permutation);
        
        // Sector cells are triangles: center, edge0, edge1
        int[][] vertexOrder = new int[][] {
            { 0, 1, 2 }  // center -> edge0 -> edge1
        };
        
        return new ShufflePattern(CellType.SECTOR, permutation, vertexOrder,
            arr.skipInterval(), arr.phaseOffset(), arr.invertSelection(), false, true, true);
    }
    
    private static ShufflePattern createEdgePattern(int permutation) {
        if (permutation < 0 || permutation >= ShuffleGenerator.edgeCount()) {
            return null;
        }
        EdgeArrangement arr = ShuffleGenerator.getEdge(permutation);
        
        // Edges are line segments: start, end
        int[][] vertexOrder = new int[][] {
            { 0, 1 }
        };
        
        return new ShufflePattern(CellType.EDGE, permutation, vertexOrder,
            arr.skipInterval(), 0, false, false, arr.latitude(), arr.longitude());
    }
    
    /**
     * Parses a shuffle pattern name like "shuffle_quad_42".
     * 
     * @param name Pattern name
     * @return The pattern, or null if not a shuffle pattern
     */
    public static ShufflePattern parse(String name) {
        if (name == null || !name.startsWith("shuffle_")) {
            return null;
        }
        
        // Format: shuffle_<celltype>_<permutation>
        String[] parts = name.split("_");
        if (parts.length != 3) {
            return null;
        }
        
        try {
            CellType cellType = CellType.valueOf(parts[1].toUpperCase());
            int permutation = Integer.parseInt(parts[2]);
            return fromPermutation(cellType, permutation);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    @Override
    public String id() {
        return id;
    }
    
    @Override
    public String displayName() {
        return "Shuffle #" + (permutation + 1);
    }
    
    @Override
    public CellType cellType() {
        return cellType;
    }
    
    @Override
    public boolean shouldRender(int index, int total) {
        // Apply skip interval and phase offset
        // Skip interval of 1 = render all, 2 = render every 2nd, etc.
        if (skipInterval > 1) {
            boolean shouldShow = ((index + phaseOffset) % skipInterval) == 0;
            if (invertSelection) {
                shouldShow = !shouldShow;  // SECTOR: invert selection
            }
            return shouldShow;
        }
        
        // Special case for EDGE: lat/lon filtering
        // Note: This requires the tessellator to pass edge type info - 
        // for now we return true and rely on the tessellator to check
        
        return true;
    }
    
    @Override
    public int[][] getVertexOrder() {
        return vertexOrder;
    }
    
    /**
     * Gets the permutation index.
     */
    public int permutation() {
        return permutation;
    }
    
    /**
     * Gets the skip interval for this shuffle.
     */
    public int skipInterval() {
        return skipInterval;
    }
    
    /**
     * Gets the phase offset for this shuffle.
     */
    public int phaseOffset() {
        return phaseOffset;
    }
    
    /**
     * For SEGMENT patterns: whether winding is reversed.
     */
    public boolean isReverseWinding() {
        return reverseWinding;
    }
    
    /**
     * For SECTOR patterns: whether selection is inverted.
     */
    public boolean isInvertSelection() {
        return invertSelection;
    }
    
    /**
     * For EDGE patterns: whether latitude edges are shown.
     */
    public boolean showLatitude() {
        return showLatitude;
    }
    
    /**
     * For EDGE patterns: whether longitude edges are shown.
     */
    public boolean showLongitude() {
        return showLongitude;
    }
}
