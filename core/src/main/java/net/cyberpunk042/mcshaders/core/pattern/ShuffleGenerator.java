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

import net.cyberpunk042.mcshaders.core.pattern.QuadPattern.Corner;
import net.cyberpunk042.mcshaders.core.pattern.TrianglePattern.Vertex;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates ALL permutations of vertex patterns for exploration.
 * 
 * <p>Use this to discover new interesting patterns by shuffling through
 * all possible vertex arrangements for each cell type.
 * 
 * <h2>Usage</h2>
 * <pre>
 * /fieldtest shuffle next     - Cycle to next permutation
 * /fieldtest shuffle prev     - Go back one
 * /fieldtest shuffle jump 42  - Jump to permutation #42
 * /fieldtest shuffle type quad - Switch to quad patterns
 * </pre>
 * 
 * <h2>Cell Types</h2>
 * <ul>
 *   <li><b>QUAD</b>: All ways to arrange 4 corners into 2 triangles</li>
 *   <li><b>SEGMENT</b>: All skip/phase combinations for rings</li>
 *   <li><b>SECTOR</b>: All skip/phase combinations for discs</li>
 *   <li><b>EDGE</b>: All lat/lon/skip combinations for wireframes</li>
 *   <li><b>TRIANGLE</b>: All skip/invert combinations</li>
 * </ul>
 */
public final class ShuffleGenerator {
    
    // =========================================================================
    // Quad Arrangement (uses Corner enum for readability)
    // =========================================================================
    
    /**
     * A single generated quad arrangement.
     * Uses semantic {@link Corner} enum: TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
     */
    public record QuadArrangement(
        Corner[] tri1,      // First triangle (3 corners)
        Corner[] tri2,      // Second triangle (3 corners, can be null)
        int index,          // Index in the full list
        int total           // Total arrangements
    ) {
        /**
         * Describes the arrangement in human-readable form.
         */
        public String describe() {
            StringBuilder sb = new StringBuilder();
            sb.append("Tri1: ");
            sb.append(tri1[0].shortName()).append("→");
            sb.append(tri1[1].shortName()).append("→");
            sb.append(tri1[2].shortName());
            
            if (tri2 != null) {
                sb.append(" | Tri2: ");
                sb.append(tri2[0].shortName()).append("→");
                sb.append(tri2[1].shortName()).append("→");
                sb.append(tri2[2].shortName());
            } else {
                sb.append(" | (single triangle)");
            }
            return sb.toString();
        }
        
        /**
         * Returns vertex order as int[][] for rendering.
         */
        public int[][] toVertexOrder() {
            int[] t1 = {tri1[0].index, tri1[1].index, tri1[2].index};
            if (tri2 == null) {
                return new int[][]{t1};
            }
            int[] t2 = {tri2[0].index, tri2[1].index, tri2[2].index};
            return new int[][]{t1, t2};
        }
        
        /**
         * Returns detailed log string.
         */
        public String toLogString() {
            StringBuilder sb = new StringBuilder();
            sb.append("\n  Triangle 1: ");
            sb.append(tri1[0].shortName()).append(" → ");
            sb.append(tri1[1].shortName()).append(" → ");
            sb.append(tri1[2].shortName());
            
            if (tri2 != null) {
                sb.append("\n  Triangle 2: ");
                sb.append(tri2[0].shortName()).append(" → ");
                sb.append(tri2[1].shortName()).append(" → ");
                sb.append(tri2[2].shortName());
            } else {
                sb.append("\n  (Single triangle only)");
            }
            return sb.toString();
        }
    }
    
    // =========================================================================
    // Segment Arrangement
    // =========================================================================
    
    /**
     * A generated segment arrangement for rings.
     */
    public record SegmentArrangement(
        int skipInterval,
        int phaseOffset,
        boolean reverseWinding,
        int index,
        int total
    ) {
        public String describe() {
            return String.format("skip=%d phase=%d %s", 
                skipInterval, phaseOffset, reverseWinding ? "reversed" : "normal");
        }
        
        public String toLogString() {
            return String.format(
                "\n  Skip Interval: %d (render every %s segment)" +
                "\n  Phase Offset: %d" +
                "\n  Winding: %s",
                skipInterval,
                skipInterval == 1 ? "" : ordinal(skipInterval),
                phaseOffset,
                reverseWinding ? "REVERSED" : "normal"
            );
        }
        
        private String ordinal(int n) {
            return switch (n) {
                case 1 -> "1st";
                case 2 -> "2nd";
                case 3 -> "3rd";
                default -> n + "th";
            };
        }
    }
    
    // =========================================================================
    // Sector Arrangement
    // =========================================================================
    
    /**
     * A generated sector arrangement for discs.
     */
    public record SectorArrangement(
        int skipInterval,
        int phaseOffset,
        boolean invertSelection,
        int index,
        int total
    ) {
        public String describe() {
            return String.format("skip=%d phase=%d %s", 
                skipInterval, phaseOffset, invertSelection ? "inverted" : "normal");
        }
        
        public String toLogString() {
            return String.format(
                "\n  Skip Interval: %d" +
                "\n  Phase Offset: %d" +
                "\n  Selection: %s",
                skipInterval, phaseOffset,
                invertSelection ? "INVERTED" : "normal"
            );
        }
    }
    
    // =========================================================================
    // Edge Arrangement
    // =========================================================================
    
    /**
     * A generated edge arrangement for wireframes.
     */
    public record EdgeArrangement(
        boolean latitude,
        boolean longitude,
        int skipInterval,
        int index,
        int total
    ) {
        public String describe() {
            String dirs = (latitude ? "LAT" : "") + (latitude && longitude ? "+" : "") + (longitude ? "LON" : "");
            return String.format("%s skip=%d", dirs, skipInterval);
        }
        
        public String toLogString() {
            return String.format(
                "\n  Latitude Lines: %s" +
                "\n  Longitude Lines: %s" +
                "\n  Skip Interval: %d",
                latitude ? "YES" : "no",
                longitude ? "YES" : "no",
                skipInterval
            );
        }
    }
    
    // =========================================================================
    // Triangle Arrangement (uses Vertex enum for readability)
    // =========================================================================
    
    /**
     * A generated triangle arrangement.
     * Uses semantic {@link Vertex} enum: A, B, C
     */
    public record TriangleArrangement(
        Vertex[] vertices,
        int skipInterval,
        int index,
        int total
    ) {
        public String describe() {
            return String.format("%s→%s→%s skip=%d", 
                vertices[0].name(), vertices[1].name(), vertices[2].name(),
                skipInterval);
        }
        
        public boolean isInverted() {
            // Standard is A→B→C, inverted is A→C→B
            return vertices[1] == Vertex.C && vertices[2] == Vertex.B;
        }
        
        public String toLogString() {
            return String.format(
                "\n  Winding: %s → %s → %s" +
                "\n  Skip Interval: %d" +
                "\n  Inverted: %s",
                vertices[0].name(), vertices[1].name(), vertices[2].name(),
                skipInterval,
                isInverted() ? "YES" : "no"
            );
        }
    }
    
    // =========================================================================
    // Pre-generated Lists
    // =========================================================================
    
    private static final List<QuadArrangement> QUAD_ARRANGEMENTS = new ArrayList<>();
    private static final List<SegmentArrangement> SEGMENT_ARRANGEMENTS = new ArrayList<>();
    private static final List<SectorArrangement> SECTOR_ARRANGEMENTS = new ArrayList<>();
    private static final List<EdgeArrangement> EDGE_ARRANGEMENTS = new ArrayList<>();
    private static final List<TriangleArrangement> TRIANGLE_ARRANGEMENTS = new ArrayList<>();
    
    static {
        generateQuadArrangements();
        generateSegmentArrangements();
        generateSectorArrangements();
        generateEdgeArrangements();
        generateTriangleArrangements();

    }
    
    // =========================================================================
    // Quad Generation (All Corner permutations)
    // =========================================================================
    
    private static void generateQuadArrangements() {
        Corner[] corners = Corner.values();
        int idx = 0;
        
        // Generate all 2-triangle combinations using Corner enum
        for (Corner a : corners) {
            for (Corner b : corners) {
                if (b == a) continue;
                for (Corner c : corners) {
                    if (c == a || c == b) continue;
                    
                    Corner[] tri1 = {a, b, c};
                    
                    // For tri2, try different combinations
                    for (Corner d : corners) {
                        for (Corner e : corners) {
                            if (e == d) continue;
                            for (Corner f : corners) {
                                if (f == d || f == e) continue;
                                
                                Corner[] tri2 = {d, e, f};
                                QUAD_ARRANGEMENTS.add(new QuadArrangement(tri1, tri2, idx++, 0));
                            }
                        }
                    }
                }
            }
        }
        
        // Also add single-triangle variants
        for (Corner a : corners) {
            for (Corner b : corners) {
                if (b == a) continue;
                for (Corner c : corners) {
                    if (c == a || c == b) continue;
                    Corner[] tri1 = {a, b, c};
                    QUAD_ARRANGEMENTS.add(new QuadArrangement(tri1, null, idx++, 0));
                }
            }
        }
        
        // Update totals
        int total = QUAD_ARRANGEMENTS.size();
        for (int i = 0; i < total; i++) {
            QuadArrangement old = QUAD_ARRANGEMENTS.get(i);
            QUAD_ARRANGEMENTS.set(i, new QuadArrangement(old.tri1, old.tri2, i, total));
        }
    }
    
    // =========================================================================
    // Segment Generation
    // =========================================================================
    
    private static void generateSegmentArrangements() {
        int idx = 0;
        
        for (int skip = 1; skip <= 8; skip++) {
            for (int phase = 0; phase < skip; phase++) {
                for (boolean reverse : new boolean[]{false, true}) {
                    SEGMENT_ARRANGEMENTS.add(new SegmentArrangement(skip, phase, reverse, idx++, 0));
                }
            }
        }
        
        int total = SEGMENT_ARRANGEMENTS.size();
        for (int i = 0; i < total; i++) {
            SegmentArrangement old = SEGMENT_ARRANGEMENTS.get(i);
            SEGMENT_ARRANGEMENTS.set(i, new SegmentArrangement(
                old.skipInterval, old.phaseOffset, old.reverseWinding, i, total));
        }
    }
    
    // =========================================================================
    // Sector Generation
    // =========================================================================
    
    private static void generateSectorArrangements() {
        int idx = 0;
        
        for (int skip = 1; skip <= 8; skip++) {
            for (int phase = 0; phase < skip; phase++) {
                for (boolean invert : new boolean[]{false, true}) {
                    SECTOR_ARRANGEMENTS.add(new SectorArrangement(skip, phase, invert, idx++, 0));
                }
            }
        }
        
        int total = SECTOR_ARRANGEMENTS.size();
        for (int i = 0; i < total; i++) {
            SectorArrangement old = SECTOR_ARRANGEMENTS.get(i);
            SECTOR_ARRANGEMENTS.set(i, new SectorArrangement(
                old.skipInterval, old.phaseOffset, old.invertSelection, i, total));
        }
    }
    
    // =========================================================================
    // Edge Generation
    // =========================================================================
    
    private static void generateEdgeArrangements() {
        int idx = 0;
        
        for (boolean lat : new boolean[]{false, true}) {
            for (boolean lon : new boolean[]{false, true}) {
                if (!lat && !lon) continue;
                
                for (int skip = 1; skip <= 6; skip++) {
                    EDGE_ARRANGEMENTS.add(new EdgeArrangement(lat, lon, skip, idx++, 0));
                }
            }
        }
        
        int total = EDGE_ARRANGEMENTS.size();
        for (int i = 0; i < total; i++) {
            EdgeArrangement old = EDGE_ARRANGEMENTS.get(i);
            EDGE_ARRANGEMENTS.set(i, new EdgeArrangement(
                old.latitude, old.longitude, old.skipInterval, i, total));
        }
    }
    
    // =========================================================================
    // Triangle Generation (uses Vertex enum)
    // =========================================================================
    
    private static void generateTriangleArrangements() {
        int idx = 0;
        Vertex[] allVertices = Vertex.values();
        
        // Generate ALL 6 vertex permutations (3! = 6)
        // This gives full control over triangle winding
        for (Vertex a : allVertices) {
            for (Vertex b : allVertices) {
                if (b == a) continue;
                for (Vertex c : allVertices) {
                    if (c == a || c == b) continue;
                    
                    Vertex[] winding = {a, b, c};
                    
                    // Combine with skip intervals 1-8
                    for (int skip = 1; skip <= 8; skip++) {
                        TRIANGLE_ARRANGEMENTS.add(new TriangleArrangement(winding.clone(), skip, idx++, 0));
                    }
                }
            }
        }
        
        int total = TRIANGLE_ARRANGEMENTS.size();
        for (int i = 0; i < total; i++) {
            TriangleArrangement old = TRIANGLE_ARRANGEMENTS.get(i);
            TRIANGLE_ARRANGEMENTS.set(i, new TriangleArrangement(
                old.vertices, old.skipInterval, i, total));
        }
    }
    
    // =========================================================================
    // Public API
    // =========================================================================
    
    public static int quadCount() { return QUAD_ARRANGEMENTS.size(); }
    public static int segmentCount() { return SEGMENT_ARRANGEMENTS.size(); }
    public static int sectorCount() { return SECTOR_ARRANGEMENTS.size(); }
    public static int edgeCount() { return EDGE_ARRANGEMENTS.size(); }
    public static int triangleCount() { return TRIANGLE_ARRANGEMENTS.size(); }
    
    public static QuadArrangement getQuad(int index) {
        return QUAD_ARRANGEMENTS.get(Math.floorMod(index, QUAD_ARRANGEMENTS.size()));
    }
    
    public static SegmentArrangement getSegment(int index) {
        return SEGMENT_ARRANGEMENTS.get(Math.floorMod(index, SEGMENT_ARRANGEMENTS.size()));
    }
    
    public static SectorArrangement getSector(int index) {
        return SECTOR_ARRANGEMENTS.get(Math.floorMod(index, SECTOR_ARRANGEMENTS.size()));
    }
    
    public static EdgeArrangement getEdge(int index) {
        return EDGE_ARRANGEMENTS.get(Math.floorMod(index, EDGE_ARRANGEMENTS.size()));
    }
    
    public static TriangleArrangement getTriangle(int index) {
        return TRIANGLE_ARRANGEMENTS.get(Math.floorMod(index, TRIANGLE_ARRANGEMENTS.size()));
    }
    
    // =========================================================================
    // Logging Helpers
    // =========================================================================
    
    public static void logQuad(int index) {
        QuadArrangement arr = getQuad(index);

    }
    
    public static void logSegment(int index) {
        SegmentArrangement arr = getSegment(index);

    }
    
    public static void logSector(int index) {
        SectorArrangement arr = getSector(index);

    }
    
    public static void logEdge(int index) {
        EdgeArrangement arr = getEdge(index);

    }
    
    public static void logTriangle(int index) {
        TriangleArrangement arr = getTriangle(index);

    }
}
