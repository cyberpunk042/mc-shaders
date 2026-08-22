package net.cyberpunk042.mcshaders.core.portal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Whether the flood fill finds the opening a portal would be lit in.
 *
 * <p>The reason this class exists at all is that the Ancient City frame's exact shape
 * could not be established from any source reachable from here — two descriptions of
 * it disagreed with each other. So the scan was written not to need it. These tests
 * hold it to that: every frame below is a different shape and size, and none of those
 * shapes appear anywhere in the production code.
 *
 * <p>The worlds are drawn rather than built. {@link #world} reads a picture, so a
 * failing test can be understood by looking at it instead of by reconstructing from a
 * list of coordinates which blocks were set.
 */
class FrameScanTest {

    // -- the drawing surface -------------------------------------------------

    /**
     * Builds a world from a picture.
     *
     * <p>Each argument is one Z-slice, in order from {@code z=0}. Within a slice, rows
     * are separated by {@code /} and are listed top-down, so the first row is the
     * highest Y. {@code #} is frame, {@code .} is empty, and anything else is some
     * other solid block.
     *
     * <p>Everything outside the picture is solid. That is what lets a single-slice
     * picture stand for a sealed frame: the scan is three-dimensional, so a flat
     * drawing would otherwise leak sideways off the page.
     */
    private static FrameScan.Blocks world(String... layers) {
        String[][] slices = new String[layers.length][];
        for (int z = 0; z < layers.length; z++) {
            slices[z] = layers[z].split("/");
        }
        return new Picture(slices);
    }

    private static final class Picture implements FrameScan.Blocks {

        private final String[][] slices;

        Picture(String[][] slices) {
            this.slices = slices;
        }

        private char at(int x, int y, int z) {
            if (z < 0 || z >= slices.length) {
                return 'X';
            }
            String[] rows = slices[z];
            int row = rows.length - 1 - y;
            if (row < 0 || row >= rows.length) {
                return 'X';
            }
            String line = rows[row];
            if (x < 0 || x >= line.length()) {
                return 'X';
            }
            return line.charAt(x);
        }

        @Override
        public boolean isEmpty(int x, int y, int z) {
            return at(x, y, z) == '.';
        }

        @Override
        public boolean isFrame(int x, int y, int z) {
            return at(x, y, z) == '#';
        }
    }

    /** A closed rectangular frame with a 2-wide, 3-tall opening. */
    private static final FrameScan.Blocks RECTANGLE = world("####/#..#/#..#/#..#/####");

    // -- the ordinary case ---------------------------------------------------

    @Nested
    @DisplayName("a closed frame")
    class Closed {

        @Test
        @DisplayName("finds the whole opening and nothing else")
        void findsTheOpening() {
            FrameScan.Result result = FrameScan.from(RECTANGLE, 1, 2, 0);

            assertTrue(result.isPortal(), "a closed frame around empty space is a portal");
            assertTrue(result.bounded());
            assertEquals(6, result.size(), "the opening is 2 wide and 3 tall");
        }

        @Test
        @DisplayName("finds the same opening from any block in it")
        void startPositionDoesNotMatter() {
            int fromBottomLeft = FrameScan.from(RECTANGLE, 1, 1, 0).size();
            int fromTopRight = FrameScan.from(RECTANGLE, 2, 3, 0).size();

            assertEquals(6, fromBottomLeft);
            assertEquals(6, fromTopRight, "where the igniter struck cannot change the frame");
        }

        @Test
        @DisplayName("includes the block that was lit")
        void includesTheLitBlock() {
            FrameScan.Result result = FrameScan.from(RECTANGLE, 2, 1, 0);

            assertTrue(result.interior().contains(new FrameScan.Pos(2, 1, 0)));
        }

        @Test
        @DisplayName("reports each block once")
        void noDuplicates() {
            FrameScan.Result result = FrameScan.from(RECTANGLE, 1, 2, 0);

            Set<FrameScan.Pos> distinct = new HashSet<>(result.interior());
            assertEquals(result.size(), distinct.size());
        }

        @Test
        @DisplayName("finds an opening of a shape the code has never seen")
        void shapeIsNotHardcoded() {
            // An L. Nothing in FrameScan knows a portal is allowed to be this.
            FrameScan.Blocks lShaped = world("#####/#.###/#.###/#...#/#####");

            FrameScan.Result result = FrameScan.from(lShaped, 1, 3, 0);

            assertTrue(result.isPortal());
            assertEquals(5, result.size(), "two down the upright, then three along the foot");
        }
    }

    // -- the cases that are not portals --------------------------------------

    @Nested
    @DisplayName("what is not a portal")
    class NotAPortal {

        @Test
        @DisplayName("lighting the frame itself")
        void lightingTheFrame() {
            FrameScan.Result result = FrameScan.from(RECTANGLE, 0, 0, 0);

            assertFalse(result.isPortal());
            assertFalse(result.bounded());
            assertTrue(result.interior().isEmpty());
        }

        @Test
        @DisplayName("lighting a solid block that is not frame")
        void lightingSomethingElse() {
            FrameScan.Blocks withStone = world("####/#..#/#XX#/#..#/####");

            FrameScan.Result result = FrameScan.from(withStone, 1, 2, 0);

            assertFalse(result.isPortal(), "there is nothing to fill where you struck");
        }

        @Test
        @DisplayName("an opening that runs on further than a frame ever would")
        void openGround() {
            FrameScan.Blocks field = world("......./......./......./......./.......");

            FrameScan.Result result = FrameScan.from(field, 3, 2, 0, 10);

            assertFalse(result.bounded(), "room to keep going means nothing closed around it");
            assertTrue(result.interior().isEmpty(), "an unbounded fill reports no opening");
        }
    }

    // -- the two decisions the algorithm makes -------------------------------

    @Nested
    @DisplayName("six-way, not diagonal")
    class SixWay {

        /**
         * Two empty blocks touching only at a corner, with frame on both sides of that
         * corner. A diagonal step would cross a wall that is closed.
         */
        private final FrameScan.Blocks corner = world("####/##.#/#.##/####");

        @Test
        @DisplayName("does not step through a corner where two frame blocks meet")
        void doesNotCutCorners() {
            FrameScan.Result result = FrameScan.from(corner, 1, 1, 0);

            assertTrue(result.bounded());
            assertEquals(1, result.size(), "only the block that was lit");
            assertFalse(
                    result.interior().contains(new FrameScan.Pos(2, 2, 0)),
                    "the diagonal neighbour is on the other side of a closed wall");
        }

        @Test
        @DisplayName("fills along Z as readily as along X and Y")
        void fillsInThreeDimensions() {
            FrameScan.Blocks tunnel =
                    world("###/###/###", "###/#.#/###", "###/#.#/###", "###/###/###");

            FrameScan.Result result = FrameScan.from(tunnel, 1, 1, 1);

            assertTrue(result.isPortal());
            assertEquals(2, result.size(), "the opening is one block deep at z=1 and z=2");
            assertTrue(result.interior().contains(new FrameScan.Pos(1, 1, 2)));
        }
    }

    @Nested
    @DisplayName("a block in the way")
    class Obstruction {

        /** A good frame with one stone block sitting inside the opening. */
        private final FrameScan.Blocks withRubble = world("####/#..#/#X.#/#..#/####");

        @Test
        @DisplayName("is a wall, not a leak")
        void straySolidBlockIsAWall() {
            FrameScan.Result result = FrameScan.from(withRubble, 1, 3, 0);

            assertTrue(result.isPortal(), "still a portal, one block smaller");
            assertEquals(5, result.size());
            assertFalse(result.interior().contains(new FrameScan.Pos(1, 2, 0)));
        }

        @Test
        @DisplayName("does not cut off what is behind it")
        void reachesAroundTheObstruction() {
            FrameScan.Result result = FrameScan.from(withRubble, 1, 3, 0);

            assertTrue(
                    result.interior().contains(new FrameScan.Pos(1, 1, 0)),
                    "the block below the rubble is reached the long way round");
        }
    }

    // -- the limit -----------------------------------------------------------

    @Nested
    @DisplayName("the search limit")
    class Limit {

        /** Exactly 35 empty blocks, sealed by the edge of the picture. */
        private final FrameScan.Blocks room = world("...../...../...../...../...../...../.....");

        @Test
        @DisplayName("admits an opening exactly as large as the limit")
        void exactlyAtTheLimit() {
            FrameScan.Result result = FrameScan.from(room, 2, 3, 0, 35);

            assertTrue(result.bounded());
            assertEquals(35, result.size());
        }

        @Test
        @DisplayName("refuses one block over")
        void oneOverTheLimit() {
            FrameScan.Result result = FrameScan.from(room, 2, 3, 0, 34);

            assertFalse(result.bounded(), "the same room, one block too large to accept");
        }

        @Test
        @DisplayName("defaults to something a structure could plausibly generate")
        void defaultLimitAcceptsTheRoom() {
            FrameScan.Result byDefault = FrameScan.from(room, 2, 3, 0);

            assertTrue(byDefault.bounded());
            assertEquals(35, byDefault.size());
            assertTrue(FrameScan.DEFAULT_LIMIT > 35);
        }

        @Test
        @DisplayName("cannot be zero or negative")
        void rejectsUselessLimits() {
            assertThrows(
                    IllegalArgumentException.class, () -> FrameScan.from(RECTANGLE, 1, 2, 0, 0));
            assertThrows(
                    IllegalArgumentException.class, () -> FrameScan.from(RECTANGLE, 1, 2, 0, -1));
        }
    }

    @Test
    @DisplayName("refuses a null world rather than reporting no portal")
    void rejectsNullBlocks() {
        assertThrows(IllegalArgumentException.class, () -> FrameScan.from(null, 0, 0, 0));
    }
}
