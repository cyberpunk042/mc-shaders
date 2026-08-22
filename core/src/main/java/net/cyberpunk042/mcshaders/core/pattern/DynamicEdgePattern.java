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
 * A dynamically generated edge pattern from shuffle exploration.
 * 
 * <p>Unlike static {@link EdgePattern} enums, this allows runtime
 * exploration of all possible edge arrangements for wireframes.
 * 
 * @see ShuffleGenerator
 * @see EdgePattern
 */
public record DynamicEdgePattern(
    boolean latitude,
    boolean longitude,
    int skipInterval,
    int shuffleIndex,
    String description
) implements VertexPattern {
    
    @Override
    public String id() {
        return "shuffle_edge_" + shuffleIndex;
    }
    
    @Override
    public CellType cellType() {
        return CellType.EDGE;
    }
    
    @Override
    public boolean shouldRender(int index, int total) {
        // Apply skip interval
        return index % skipInterval == 0;
    }
    
    @Override
    public int[][] getVertexOrder() {
        // Edges are lines - just two vertices
        return new int[][]{{0, 1}};
    }
    
    /**
     * Whether to render latitude (horizontal) lines.
     */
    public boolean renderLatitude() {
        return latitude;
    }
    
    /**
     * Whether to render longitude (vertical) lines.
     */
    public boolean renderLongitude() {
        return longitude;
    }
    
    /**
     * Human-readable description of this pattern.
     */
    public String describe() {
        String dirs = (latitude ? "LAT" : "") + (latitude && longitude ? "+" : "") + (longitude ? "LON" : "");
        return String.format("%s skip=%d", dirs, skipInterval);
    }
    
    /**
     * Creates from a ShuffleGenerator arrangement.
     */
    public static DynamicEdgePattern fromArrangement(ShuffleGenerator.EdgeArrangement arr) {
        return new DynamicEdgePattern(
            arr.latitude(),
            arr.longitude(),
            arr.skipInterval(),
            arr.index(),
            arr.describe()
        );
    }
}
