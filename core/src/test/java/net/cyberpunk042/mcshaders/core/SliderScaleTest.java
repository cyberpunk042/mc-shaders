package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.schema.Bounds;
import net.cyberpunk042.mcshaders.core.schema.SliderScale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The arithmetic between a slider's 0–1 and a parameter's own range.
 *
 * <p>This is tested at all because the screen that uses it cannot be: every case
 * below produces a wrong number rather than an exception, so a control would look
 * like it works and set something else.
 */
class SliderScaleTest {

    private static final double EPSILON = 1e-9;

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @Test
        @DisplayName("a value survives being turned into a position and back")
        void valueSurvivesTheRoundTrip() {
            Bounds bounds = Bounds.between(0, 100);
            for (double value : new double[] {0, 0.5, 1, 33.3, 99.9, 100}) {
                double back = SliderScale.toValue(bounds, SliderScale.toPosition(bounds, value));
                assertEquals(value, back, 1e-6, "value " + value + " did not survive");
            }
        }

        @Test
        @DisplayName("a range that does not start at zero round-trips too")
        void offsetRangeRoundTrips() {
            // The obvious implementation divides by max and works only when min is 0.
            Bounds bounds = Bounds.between(-50, 50);
            for (double value : new double[] {-50, -25, 0, 25, 50}) {
                double back = SliderScale.toValue(bounds, SliderScale.toPosition(bounds, value));
                assertEquals(value, back, 1e-6, "value " + value + " did not survive");
            }
        }

        @Test
        @DisplayName("the ends map to the ends")
        void endsAreExact() {
            Bounds bounds = Bounds.between(2, 8);
            assertEquals(0, SliderScale.toPosition(bounds, 2), EPSILON);
            assertEquals(1, SliderScale.toPosition(bounds, 8), EPSILON);
            assertEquals(2, SliderScale.toValue(bounds, 0), EPSILON);
            assertEquals(8, SliderScale.toValue(bounds, 1), EPSILON);
        }
    }

    @Nested
    @DisplayName("the cases that would not throw")
    class SilentFailures {

        @Test
        @DisplayName("a range with no width does not divide by zero")
        void zeroWidthRangeIsSafe() {
            // Bounds.NONE is (0, 0) and is the default for controls with no range,
            // so this is reached by anything that reads a bound it was never given.
            assertEquals(0, SliderScale.toPosition(Bounds.NONE, 5), EPSILON);
            assertEquals(0, SliderScale.toValue(Bounds.NONE, 0.5), EPSILON);

            Bounds degenerate = Bounds.between(7, 7);
            assertEquals(0, SliderScale.toPosition(degenerate, 7), EPSILON);
            assertEquals(7, SliderScale.toValue(degenerate, 0.5), EPSILON,
                    "a range of one value should yield that value");
        }

        @Test
        @DisplayName("a position past the ends clamps rather than extrapolating")
        void positionsOutsideZeroOneClamp() {
            // A drag to the very edge can hand back slightly outside 0-1. Extrapolating
            // writes a value the bounds reject, and nothing downstream would notice.
            Bounds bounds = Bounds.between(0, 10);
            assertEquals(0, SliderScale.toValue(bounds, -0.2), EPSILON);
            assertEquals(10, SliderScale.toValue(bounds, 1.2), EPSILON);
        }

        @Test
        @DisplayName("a value past the ends clamps rather than leaving the slider")
        void valuesOutsideBoundsClamp() {
            Bounds bounds = Bounds.between(0, 10);
            assertEquals(0, SliderScale.toPosition(bounds, -5), EPSILON);
            assertEquals(1, SliderScale.toPosition(bounds, 50), EPSILON);
        }
    }

    @Nested
    @DisplayName("stepping")
    class Stepping {

        @Test
        @DisplayName("a stepped range snaps to its steps")
        void steppedRangeSnaps() {
            Bounds bounds = Bounds.stepped(0, 10, 2);
            assertEquals(4, SliderScale.toValue(bounds, 0.42), EPSILON);
            assertEquals(6, SliderScale.toValue(bounds, 0.55), EPSILON);
        }

        @Test
        @DisplayName("steps are counted from the minimum, so the minimum is reachable")
        void stepsStartAtTheMinimum() {
            // Snapping relative to zero on a 1-9 range in steps of 2 offers 2,4,6,8 and
            // makes 1 unreachable — the minimum being unselectable is the kind of thing
            // nobody reports as a bug, they just work around it.
            Bounds bounds = Bounds.stepped(1, 9, 2);

            assertEquals(1, SliderScale.toValue(bounds, 0), EPSILON);
            assertEquals(9, SliderScale.toValue(bounds, 1), EPSILON);
            for (double position = 0; position <= 1.0001; position += 0.05) {
                double value = SliderScale.toValue(bounds, position);
                double stepsFromMin = (value - 1) / 2;
                assertEquals(Math.round(stepsFromMin), stepsFromMin, 1e-6,
                        "position " + position + " gave " + value + ", not on a step");
            }
        }

        @Test
        @DisplayName("a continuous range is left alone")
        void continuousRangeDoesNotSnap() {
            Bounds bounds = Bounds.between(0, 1);
            assertTrue(bounds.isContinuous(), "the fixture stopped being continuous");
            assertEquals(0.37, SliderScale.toValue(bounds, 0.37), 1e-9);
        }

        @Test
        @DisplayName("snapping never leaves the bounds")
        void snappingStaysInBounds() {
            // Rounding up at the top edge can step past max. The result is then written
            // back and silently rejected or clamped somewhere far away.
            //
            // The step has to divide the range unevenly for this to bite: 0-10 in steps
            // of 4 offers 0, 4, 8, and then 12, which is past the end. A step of 3 gives
            // 0, 3, 6, 9 and never overshoots, so it would pass this test whether the
            // implementation clamped or not — which is what the first fixture here did.
            Bounds bounds = Bounds.stepped(0, 10, 4);
            for (double position = 0; position <= 1.0001; position += 0.01) {
                double value = SliderScale.toValue(bounds, position);
                assertTrue(bounds.contains(value),
                        "position " + position + " gave " + value + ", outside the bounds");
            }
        }
    }
}
