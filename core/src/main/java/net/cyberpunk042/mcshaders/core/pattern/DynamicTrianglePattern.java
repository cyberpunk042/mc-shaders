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

import net.cyberpunk042.mcshaders.core.pattern.TrianglePattern.Vertex;

/**
 * A dynamically generated triangle pattern from shuffle exploration.
 * 
 * <p>Unlike static {@link TrianglePattern} enums, this allows runtime
 * exploration of all possible vertex arrangements for triangle faces.
 * 
 * <p>Uses semantic {@link Vertex} enum for readability (A, B, C).
 * 
 * @see ShuffleGenerator
 * @see TrianglePattern
 */
public record DynamicTrianglePattern(
    Vertex[] vertices,
    int skipInterval,
    int shuffleIndex,
    String description
) implements VertexPattern {
    
    @Override
    public String id() {
        return "shuffle_triangle_" + shuffleIndex;
    }
    
    @Override
    public CellType cellType() {
        return CellType.TRIANGLE;
    }
    
    @Override
    public boolean shouldRender(int index, int total) {
        return index % skipInterval == 0;
    }
    
    @Override
    public int[][] getVertexOrder() {
        return new int[][]{{vertices[0].index, vertices[1].index, vertices[2].index}};
    }
    
    /**
     * Whether the winding is inverted (A→C→B instead of A→B→C).
     */
    public boolean isInverted() {
        return vertices[1] == Vertex.C && vertices[2] == Vertex.B;
    }
    
    /**
     * Human-readable description of this pattern.
     */
    public String describe() {
        return String.format("%s→%s→%s skip=%d", 
            vertices[0].name(), vertices[1].name(), vertices[2].name(),
            skipInterval);
    }
    
    /**
     * Creates from a ShuffleGenerator arrangement.
     */
    public static DynamicTrianglePattern fromArrangement(ShuffleGenerator.TriangleArrangement arr) {
        return new DynamicTrianglePattern(
            arr.vertices(),
            arr.skipInterval(),
            arr.index(),
            arr.describe()
        );
    }
}
