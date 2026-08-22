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
 * A dynamically generated segment pattern from shuffle exploration.
 * 
 * <p>Unlike static {@link SegmentPattern} enums, this allows runtime
 * exploration of all possible segment arrangements for rings.
 * 
 * @see ShuffleGenerator
 * @see SegmentPattern
 */
public record DynamicSegmentPattern(
    int skipInterval,
    int phaseOffset,
    boolean reverseWinding,
    int shuffleIndex,
    String description
) implements VertexPattern {
    
    @Override
    public String id() {
        return "shuffle_segment_" + shuffleIndex;
    }
    
    @Override
    public CellType cellType() {
        return CellType.SEGMENT;
    }
    
    @Override
    public boolean shouldRender(int index, int total) {
        // Apply skip interval and phase offset
        int adjusted = (index + phaseOffset) % total;
        return adjusted % skipInterval == 0;
    }
    
    @Override
    public int[][] getVertexOrder() {
        // Segments are rendered as line pairs
        // Winding determines direction
        if (reverseWinding) {
            return new int[][]{{1, 0}};  // Reversed
        }
        return new int[][]{{0, 1}};  // Normal
    }
    
    /**
     * Human-readable description of this pattern.
     */
    public String describe() {
        return String.format("skip=%d phase=%d %s", 
            skipInterval, phaseOffset, reverseWinding ? "reversed" : "normal");
    }
    
    /**
     * Creates from a ShuffleGenerator arrangement.
     */
    public static DynamicSegmentPattern fromArrangement(ShuffleGenerator.SegmentArrangement arr) {
        return new DynamicSegmentPattern(
            arr.skipInterval(),
            arr.phaseOffset(),
            arr.reverseWinding(),
            arr.index(),
            arr.describe()
        );
    }
}
