package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.util.ColorSupport;
import net.cyberpunk042.mcshaders.core.util.MathSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the primitives that replaced Minecraft's MathHelper and ColorHelper.
 *
 * <p>These are the only pieces in the port that were <em>reimplemented</em> rather
 * than moved, so they are the only ones where the original behaviour could silently
 * fail to be reproduced. Everything else is a copy; this is not.
 */
class SupportTest {

    private static final float EPS = 1e-5f;

    @Nested
    @DisplayName("catmull-rom spline")
    class Spline {

        @Test
        @DisplayName("the segment passes exactly through its two inner control points")
        void interpolatesThroughEndpoints() {
            // This is the defining property. A spline that misses its control points
            // would move every animation path slightly off its authored route.
            float p0 = 0f;
            float p1 = 1f;
            float p2 = 3f;
            float p3 = 4f;

            assertEquals(p1, MathSupport.catmullRom(0f, p0, p1, p2, p3), EPS,
                    "t=0 must land on p1");
            assertEquals(p2, MathSupport.catmullRom(1f, p0, p1, p2, p3), EPS,
                    "t=1 must land on p2");
        }

        @Test
        @DisplayName("evenly spaced control points give a straight line")
        void uniformPointsInterpolateLinearly() {
            // With p0..p3 collinear and evenly spaced, the spline degenerates to a
            // straight line, so every sample is predictable.
            for (float t = 0f; t <= 1f; t += 0.125f) {
                float actual = MathSupport.catmullRom(t, 0f, 1f, 2f, 3f);
                assertEquals(1f + t, actual, EPS, "spline bent at t=" + t);
            }
        }

        @Test
        void constantControlPointsGiveAConstant() {
            for (float t = 0f; t <= 1f; t += 0.2f) {
                assertEquals(5f, MathSupport.catmullRom(t, 5f, 5f, 5f, 5f), EPS);
            }
        }

        @Test
        void theCurveIsSymmetricUnderReversal() {
            // Reversing the control points and the parameter must trace the same curve.
            float t = 0.3f;
            float forward = MathSupport.catmullRom(t, 1f, 2f, 6f, 7f);
            float backward = MathSupport.catmullRom(1f - t, 7f, 6f, 2f, 1f);
            assertEquals(forward, backward, EPS);
        }
    }

    @Nested
    @DisplayName("clamp and lerp")
    class Basics {

        @Test
        void clampBounds() {
            assertEquals(0f, MathSupport.clamp(-5f, 0f, 1f), EPS);
            assertEquals(1f, MathSupport.clamp(5f, 0f, 1f), EPS);
            assertEquals(0.5f, MathSupport.clamp(0.5f, 0f, 1f), EPS);
            assertEquals(3, MathSupport.clamp(9, 0, 3));
        }

        @Test
        void lerpHitsItsEndpoints() {
            assertEquals(2f, MathSupport.lerp(0f, 2f, 8f), EPS);
            assertEquals(8f, MathSupport.lerp(1f, 2f, 8f), EPS);
            assertEquals(5f, MathSupport.lerp(0.5f, 2f, 8f), EPS);
        }

        @Test
        @DisplayName("lerp extrapolates rather than clamping, as the original did")
        void lerpExtrapolates() {
            assertEquals(14f, MathSupport.lerp(2f, 2f, 8f), EPS);
        }
    }

    @Nested
    @DisplayName("packed ARGB")
    class Argb {

        @Test
        void packingAndUnpackingRoundTrip() {
            int c = ColorSupport.argb(0x12, 0x34, 0x56, 0x78);
            assertEquals(0x12, ColorSupport.alpha(c));
            assertEquals(0x34, ColorSupport.red(c));
            assertEquals(0x56, ColorSupport.green(c));
            assertEquals(0x78, ColorSupport.blue(c));
        }

        @Test
        @DisplayName("alpha survives the top bit, which a signed shift would lose")
        void highAlphaIsPreserved() {
            int c = ColorSupport.argb(0xFF, 0, 0, 0);
            assertEquals(0xFF, ColorSupport.alpha(c),
                    "unsigned shift is required or opaque reads back as -1");
        }

        @Test
        void floatComponentsClampAndRound() {
            assertEquals(0xFFFFFFFF, ColorSupport.fromFloats(2f, 2f, 2f, 2f),
                    "out-of-range floats should clamp to full");
            assertEquals(0xFF000000, ColorSupport.fromFloats(1f, -1f, -1f, -1f));
        }

        @Test
        void whiteCarriesOnlyTheAlpha() {
            assertEquals(0xFFFFFFFF, ColorSupport.white(1f));
            assertEquals(0x00FFFFFF, ColorSupport.white(0f));
            assertEquals(0x80FFFFFF, ColorSupport.white(128 / 255f));
        }

        @Test
        void withAlphaLeavesTheColourChannelsAlone() {
            assertEquals(0x80123456, ColorSupport.withAlpha(0xFF123456, 128));
            assertEquals(0x00123456, ColorSupport.withAlpha(0xFF123456, 0f));
            assertEquals(0xFF123456, ColorSupport.withAlpha(0x00123456, 1f));
        }

        @Test
        void scaleRgbKeepsAlphaAndScalesTheRest() {
            assertEquals(0xFF102030, ColorSupport.scaleRgb(0xFF204060, 0.5f));
        }

        @Test
        @DisplayName("brightening saturates instead of wrapping to black")
        void scaleRgbClamps() {
            // Masking rather than clamping would turn 0x80 * 4 = 0x200 into 0x00.
            assertEquals(0xFFFFFFFF, ColorSupport.scaleRgb(0xFF808080, 4f));
            assertEquals(0xFF000000, ColorSupport.scaleRgb(0xFF808080, -1f));
        }

        @Test
        void scaleRgbTakesAPerChannelFactor() {
            assertEquals(0xFF800040, ColorSupport.scaleRgb(0xFF808080, 1f, 0f, 0.5f));
        }

        @Test
        void lerpMovesEveryChannelIncludingAlpha() {
            assertEquals(0x00000000, ColorSupport.lerp(0f, 0x00000000, 0xFFFFFFFF));
            assertEquals(0xFFFFFFFF, ColorSupport.lerp(1f, 0x00000000, 0xFFFFFFFF));
            assertEquals(0x80808080, ColorSupport.lerp(0.5f, 0x00000000, 0xFFFFFFFF),
                    "half of 255 should round to 128, not truncate to 127");
        }

        @Test
        @DisplayName("extrapolating past an endpoint saturates rather than wrapping")
        void lerpClampsChannelsButNotT() {
            assertEquals(0xFFFFFFFF, ColorSupport.lerp(2f, 0x00000000, 0xFFFFFFFF));
            // Overshooting below zero: 128 - 2 * 127 is negative, and must floor at 0
            // rather than wrap round to a bright channel.
            assertEquals(0xFF000000, ColorSupport.lerp(-2f, 0xFF808080, 0xFFFFFFFF));
        }
    }

    @Nested
    @DisplayName("HSV conversion")
    class Hsv {

        private static int rgb(float h, float s, float v) {
            return ColorSupport.hsvToArgb(h, s, v, 255) & 0xFFFFFF;
        }

        @Test
        @DisplayName("the six primary hues land on their expected corners")
        void primaryHues() {
            assertEquals(0xFF0000, rgb(0f / 6f, 1f, 1f), "0 turns is red");
            assertEquals(0xFFFF00, rgb(1f / 6f, 1f, 1f), "1/6 is yellow");
            assertEquals(0x00FF00, rgb(2f / 6f, 1f, 1f), "2/6 is green");
            assertEquals(0x00FFFF, rgb(3f / 6f, 1f, 1f), "3/6 is cyan");
            assertEquals(0x0000FF, rgb(4f / 6f, 1f, 1f), "4/6 is blue");
            assertEquals(0xFF00FF, rgb(5f / 6f, 1f, 1f), "5/6 is magenta");
        }

        @Test
        @DisplayName("hue wraps rather than clamping — it is an angle")
        void hueWraps() {
            assertEquals(rgb(0.25f, 1f, 1f), rgb(1.25f, 1f, 1f));
            assertEquals(rgb(0.25f, 1f, 1f), rgb(-0.75f, 1f, 1f));
        }

        @Test
        void zeroSaturationIsGrey() {
            int c = rgb(0.42f, 0f, 0.5f);
            int r = (c >> 16) & 0xFF;
            int g = (c >> 8) & 0xFF;
            int b = c & 0xFF;
            assertEquals(r, g, "unsaturated colour must be grey");
            assertEquals(g, b, "unsaturated colour must be grey");
        }

        @Test
        void zeroValueIsBlackWhateverTheHue() {
            for (float h = 0f; h < 1f; h += 0.1f) {
                assertEquals(0x000000, rgb(h, 1f, 0f), "value 0 must be black at hue " + h);
            }
        }

        @Test
        void alphaPassesThroughUnchanged() {
            assertEquals(0x80, ColorSupport.alpha(ColorSupport.hsvToArgb(0.5f, 1f, 1f, 0x80)));
        }

        @Test
        void everyHueProducesAValidColour() {
            for (float h = 0f; h < 1f; h += 0.01f) {
                int c = rgb(h, 1f, 1f);
                assertTrue(c >= 0 && c <= 0xFFFFFF, "hue " + h + " produced " + c);
            }
        }
    }
}
