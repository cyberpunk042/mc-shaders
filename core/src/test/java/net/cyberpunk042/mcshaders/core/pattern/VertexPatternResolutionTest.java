package net.cyberpunk042.mcshaders.core.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins how a pattern name becomes a {@link VertexPattern}.
 *
 * <p>The package has two resolvers. {@link VertexPattern#resolveForCellType} knows what
 * kind of cell it is resolving for; {@link VertexPattern#fromString} does not. Ids collide
 * across the five families, so the one that does not know cannot be correct — these tests
 * pin which constants it therefore cannot reach, and prove the other one reaches all of them.
 */
class VertexPatternResolutionTest {

    /** Every declared pattern, grouped by the cell type it is written for. */
    private static Map<CellType, List<VertexPattern>> byCellType() {
        Map<CellType, List<VertexPattern>> all = new LinkedHashMap<>();
        all.put(CellType.QUAD, List.of(QuadPattern.values()));
        all.put(CellType.SEGMENT, List.of(SegmentPattern.values()));
        all.put(CellType.SECTOR, List.of(SectorPattern.values()));
        all.put(CellType.EDGE, List.of(EdgePattern.values()));
        all.put(CellType.TRIANGLE, List.of(TrianglePattern.values()));
        return all;
    }

    /** How many vertices a cell of each type has — the range its indices must stay inside. */
    private static int vertexCount(CellType cellType) {
        return switch (cellType) {
            case QUAD -> 4;      // TL, TR, BL, BR
            case SEGMENT -> 4;   // inner0, inner1, outer0, outer1
            case SECTOR -> 3;    // center, edge0, edge1
            case EDGE -> 2;      // start, end
            case TRIANGLE -> 3;  // A, B, C
        };
    }

    // =========================================================================
    // The resolver that knows the cell type
    // =========================================================================

    /**
     * The invariant that matters: whatever a pattern is called, asking for it by the cell
     * type it was written for gets you that exact pattern. This is what makes the eight
     * ids that {@link VertexPattern#fromString} cannot reach reachable at all.
     */
    @Test
    void everyDeclaredPatternIsReachableThroughItsOwnCellType() {
        List<String> unreachable = new ArrayList<>();
        for (var entry : byCellType().entrySet()) {
            CellType cellType = entry.getKey();
            for (VertexPattern declared : entry.getValue()) {
                VertexPattern got = VertexPattern.resolveForCellType(declared.id(), cellType);
                if (got == null || !declared.id().equals(got.id()) || got.cellType() != cellType) {
                    unreachable.add(cellType + "." + declared.id() + " -> " + describe(got));
                }
            }
        }
        assertTrue(unreachable.isEmpty(),
                "resolveForCellType failed to reach declared patterns: " + unreachable);
    }

    /** An unknown name yields that family's own default, never another family's. */
    @Test
    void unknownNameFallsBackWithinTheRequestedCellType() {
        for (CellType cellType : CellType.values()) {
            VertexPattern fallback = VertexPattern.resolveForCellType("no_such_pattern_xyz", cellType);
            assertNotNull(fallback, "no fallback for " + cellType);
            assertEquals(cellType, fallback.cellType(),
                    "fallback for " + cellType + " came from the wrong family: " + describe(fallback));
            assertSame(VertexPattern.defaultForCellType(cellType), fallback,
                    "fallback for " + cellType + " is not that family's default");
        }
    }

    /**
     * Resolution always yields a pattern, so a caller has nothing to test for and no way to
     * learn that a name went unrecognised. The javadoc used to promise a null on an
     * incompatible cell type; this is what actually happens, so that a caller writing the
     * skip-on-null branch would have been writing dead code.
     */
    @Test
    void resolutionNeverReturnsNull() {
        ArrangementConfig config = ArrangementConfig.of("full");
        for (CellType cellType : CellType.values()) {
            assertNotNull(VertexPattern.resolveForCellType(null, cellType),
                    "null name, " + cellType);
            assertNotNull(VertexPattern.resolveForCellType("", cellType),
                    "empty name, " + cellType);
            assertNotNull(config.resolvePattern("main", cellType),
                    "ArrangementConfig.resolvePattern, " + cellType);
        }
    }

    // =========================================================================
    // The resolver that does not
    // =========================================================================

    /**
     * {@code fromString} takes the first family that claims the id, in a fixed order, so a
     * colliding id can only ever produce one family's pattern. Pinned so that reordering
     * the lookup is a test failure rather than a silent change of meaning for content.
     */
    @Test
    void fromStringCannotDisambiguateCollidingIds() {
        // Every one of these is declared by two or more families; SEGMENT is looked at first.
        for (String colliding : List.of("full", "alternating", "sparse", "quarter", "dashed")) {
            VertexPattern got = VertexPattern.fromString(colliding);
            assertEquals(CellType.SEGMENT, got.cellType(),
                    "'" + colliding + "' no longer resolves to SEGMENT: " + describe(got));
        }
    }

    /** The other side of the same fact: which constants fromString therefore cannot return. */
    @Test
    void fromStringCannotReachShadowedConstants() {
        List<VertexPattern> shadowed = List.of(
                SectorPattern.FULL, EdgePattern.FULL, TrianglePattern.FULL,
                TrianglePattern.ALTERNATING, EdgePattern.SPARSE, TrianglePattern.SPARSE,
                TrianglePattern.QUARTER, EdgePattern.DASHED);

        for (VertexPattern hidden : shadowed) {
            assertEquals(CellType.SEGMENT, VertexPattern.fromString(hidden.id()).cellType(),
                    hidden.cellType() + "." + hidden.id() + " became reachable via fromString");
            // ...and is reachable the moment the cell type is supplied.
            assertEquals(hidden.id(),
                    VertexPattern.resolveForCellType(hidden.id(), hidden.cellType()).id());
        }
    }

    // =========================================================================
    // Why supplying the cell type is not optional
    // =========================================================================

    /**
     * Index meanings are per cell type, so a pattern used for the wrong one is not merely
     * mislabelled — it indexes vertices that mean something else. This pins the extent of
     * the damage a quad consumer would take, which is what {@code resolveForCellType} averts.
     */
    @Test
    void patternsFromTheWrongFamilyDegradeSilentlyOnAQuad() {
        // A quad consumer emits a triangle per entry and skips entries shorter than 3
        // (MeshBuilder.quadAsTrianglesFromPattern). Count what each family would produce.
        assertEquals(2, trianglesEmittedOnAQuad(QuadPattern.DEFAULT), "a quad pattern fills the quad");
        assertEquals(2, trianglesEmittedOnAQuad(SegmentPattern.FULL),
                "segment cells are also 4 vertices, so this one survives the mix-up");
        assertEquals(1, trianglesEmittedOnAQuad(SectorPattern.FULL), "sector: half the quad");
        assertEquals(1, trianglesEmittedOnAQuad(TrianglePattern.FULL), "triangle: half the quad");
        assertEquals(0, trianglesEmittedOnAQuad(EdgePattern.FULL),
                "edge cells are 2 vertices, so every entry is skipped and nothing is drawn");
    }

    private static int trianglesEmittedOnAQuad(VertexPattern pattern) {
        int emitted = 0;
        for (int[] triangle : pattern.getVertexOrder()) {
            if (triangle.length >= 3) {
                emitted++;
            }
        }
        return emitted;
    }

    // =========================================================================
    // The index convention the interface documents
    // =========================================================================

    /** No pattern may index past the end of its own cell. */
    @Test
    void vertexOrderStaysInsideItsOwnCell() {
        List<String> outOfRange = new ArrayList<>();
        for (var entry : byCellType().entrySet()) {
            int vertices = vertexCount(entry.getKey());
            for (VertexPattern pattern : entry.getValue()) {
                for (int[] triangle : pattern.getVertexOrder()) {
                    for (int index : triangle) {
                        if (index < 0 || index >= vertices) {
                            outOfRange.add(entry.getKey() + "." + pattern.id() + " uses index " + index
                                    + " but the cell has " + vertices + " vertices");
                        }
                    }
                }
            }
        }
        assertTrue(outOfRange.isEmpty(), String.join("; ", outOfRange));
    }

    /**
     * A quad consumer has two paths — the semantic {@code Corner} one and the numeric
     * {@code getVertexOrder} one — and they must agree, or the same pattern draws
     * differently depending on which overload the caller happened to reach.
     */
    @Test
    void quadVertexOrderAgreesWithTheCornerEnum() {
        for (QuadPattern pattern : QuadPattern.values()) {
            QuadPattern.Corner[][] semantic = pattern.triangles();
            int[][] numeric = pattern.getVertexOrder();
            assertEquals(semantic.length, numeric.length, pattern.id() + ": triangle count");
            for (int t = 0; t < semantic.length; t++) {
                assertEquals(semantic[t].length, numeric[t].length, pattern.id() + " triangle " + t);
                for (int v = 0; v < semantic[t].length; v++) {
                    assertEquals(semantic[t][v].index, numeric[t][v],
                            pattern.id() + " triangle " + t + " vertex " + v
                                    + ": " + semantic[t][v] + " should be index " + semantic[t][v].index);
                }
            }
        }
    }

    /** The interface documents 0=TL, 1=TR, 2=BL, 3=BR; a consumer's array is built to match. */
    @Test
    void cornerIndicesAreTheDocumentedQuadOrder() {
        assertEquals(0, QuadPattern.Corner.TOP_LEFT.index);
        assertEquals(1, QuadPattern.Corner.TOP_RIGHT.index);
        assertEquals(2, QuadPattern.Corner.BOTTOM_LEFT.index);
        assertEquals(3, QuadPattern.Corner.BOTTOM_RIGHT.index);
    }

    // =========================================================================
    // The one family whose names carry their own cell type
    // =========================================================================

    private static int shuffleCount(CellType cellType) {
        return switch (cellType) {
            case QUAD -> ShuffleGenerator.quadCount();
            case SEGMENT -> ShuffleGenerator.segmentCount();
            case SECTOR -> ShuffleGenerator.sectorCount();
            case EDGE -> ShuffleGenerator.edgeCount();
            case TRIANGLE -> ShuffleGenerator.triangleCount();
        };
    }

    /**
     * {@code shuffle_<cellType>_<n>} spells out the cell type, so unlike the named patterns
     * every one of these round-trips through {@link VertexPattern#fromString} intact.
     */
    @Test
    void everyShufflePermutationRoundTripsThroughItsName() {
        int checked = 0;
        for (CellType cellType : CellType.values()) {
            for (int n = 0; n < shuffleCount(cellType); n++) {
                ShufflePattern declared = ShufflePattern.fromPermutation(cellType, n);
                assertNotNull(declared, cellType + " permutation " + n + " is within range but absent");

                String name = declared.id();
                assertEquals(declared.id(), VertexPattern.fromString(name).id(), "fromString: " + name);
                VertexPattern resolved = VertexPattern.resolveForCellType(name, cellType);
                assertEquals(declared.id(), resolved.id(), "resolveForCellType: " + name);
                assertEquals(cellType, resolved.cellType(), "cell type of " + name);
                checked++;
            }
        }
        assertTrue(checked > 0, "no shuffle permutations were generated to check");
    }

    /** Asking for a shuffle of one cell type as another falls back rather than mis-indexing. */
    @Test
    void aShuffleNamedForAnotherCellTypeIsNotAccepted() {
        for (CellType declaredFor : CellType.values()) {
            ShufflePattern shuffle = ShufflePattern.fromPermutation(declaredFor, 0);
            assertNotNull(shuffle, "no permutation 0 for " + declaredFor);
            for (CellType askedFor : CellType.values()) {
                if (askedFor == declaredFor) {
                    continue;
                }
                VertexPattern got = VertexPattern.resolveForCellType(shuffle.id(), askedFor);
                assertEquals(askedFor, got.cellType(),
                        shuffle.id() + " asked for as " + askedFor + " gave " + describe(got));
                assertSame(VertexPattern.defaultForCellType(askedFor), got,
                        shuffle.id() + " asked for as " + askedFor + " should fall back to its default");
            }
        }
    }

    /** Generated orders obey the same index range as the hand-written ones. */
    @Test
    void shuffleVertexOrderStaysInsideItsOwnCell() {
        List<String> outOfRange = new ArrayList<>();
        for (CellType cellType : CellType.values()) {
            int vertices = vertexCount(cellType);
            for (int n = 0; n < shuffleCount(cellType); n++) {
                ShufflePattern shuffle = ShufflePattern.fromPermutation(cellType, n);
                assertNotNull(shuffle, cellType + " permutation " + n);
                for (int[] triangle : shuffle.getVertexOrder()) {
                    for (int index : triangle) {
                        if (index < 0 || index >= vertices) {
                            outOfRange.add(shuffle.id() + " uses index " + index
                                    + " but a " + cellType + " cell has " + vertices + " vertices");
                        }
                    }
                }
            }
        }
        assertTrue(outOfRange.isEmpty(), String.join("; ", outOfRange));
    }

    private static String describe(VertexPattern pattern) {
        return pattern == null ? "null" : pattern.cellType() + "." + pattern.id();
    }
}
