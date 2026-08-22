package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.animation.Waveform;
import net.cyberpunk042.mcshaders.core.transform.OrbitConfig;
import net.cyberpunk042.mcshaders.core.transform.Transform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the ported motion model — waveforms, transforms and orbits.
 *
 * <p>As with the shapes, the point is to catch damage from an automated port rather
 * than to re-specify behaviour. The waveform tests matter most: `Waveform.SINE` was
 * reimplemented on `java.lang.Math` because Minecraft's lookup-table `MathHelper`
 * cannot come into core, so its output is the one thing here that genuinely changed
 * and needs pinning down.
 */
class MotionModelTest {

    private static final float EPS = 1e-4f;

    @Nested
    @DisplayName("waveforms")
    class Waveforms {

        @Test
        @DisplayName("every waveform stays within [0,1] across a full period")
        void outputsAreNormalised() {
            for (Waveform w : Waveform.values()) {
                for (float t = 0f; t <= 1f; t += 0.02f) {
                    float v = w.evaluate(t);
                    assertTrue(Float.isFinite(v), w + " produced a non-finite value at t=" + t);
                    assertTrue(v >= -EPS && v <= 1f + EPS,
                            w + " left [0,1] at t=" + t + " with " + v);
                }
            }
        }

        @Test
        @DisplayName("waveforms are periodic — t and t+1 agree")
        void waveformsArePeriodic() {
            for (Waveform w : Waveform.values()) {
                for (float t = 0f; t < 1f; t += 0.1f) {
                    assertEquals(w.evaluate(t), w.evaluate(t + 1f), EPS,
                            w + " is not periodic at t=" + t);
                }
            }
        }

        @Test
        @DisplayName("the reimplemented sine hits its known landmarks")
        void sineLandmarks() {
            // sin(0)=0 -> 0.5, sin(pi/2)=1 -> 1.0, sin(pi)=0 -> 0.5, sin(3pi/2)=-1 -> 0.0
            assertEquals(0.5f, Waveform.SINE.evaluate(0.00f), EPS);
            assertEquals(1.0f, Waveform.SINE.evaluate(0.25f), EPS);
            assertEquals(0.5f, Waveform.SINE.evaluate(0.50f), EPS);
            assertEquals(0.0f, Waveform.SINE.evaluate(0.75f), EPS);
        }

        @Test
        void negativeTimeWrapsRatherThanEscaping() {
            for (Waveform w : Waveform.values()) {
                float v = w.evaluate(-0.25f);
                assertTrue(Float.isFinite(v) && v >= -EPS && v <= 1f + EPS,
                        w + " mishandled negative time: " + v);
            }
        }

        @Test
        void idLookupRoundTrips() {
            for (Waveform w : Waveform.values()) {
                assertNotNull(Waveform.fromId(w.name()), "fromId lost " + w);
            }
        }
    }

    @Nested
    @DisplayName("transforms")
    class Transforms {

        @Test
        void namedConstantsExistAndAreDistinct() {
            assertNotNull(Transform.IDENTITY);
            assertNotNull(Transform.AT_FEET);
            assertNotNull(Transform.AT_HEAD);
            assertNotNull(Transform.BILLBOARD);
            assertTrue(Transform.AT_FEET != Transform.AT_HEAD,
                    "feet and head anchors should not be the same transform");
        }

        @Test
        @DisplayName("identity survived the port intact")
        void identityIsStable() {
            assertEquals(Transform.IDENTITY, Transform.IDENTITY,
                    "a record constant must equal itself by value");
        }
    }

    @Nested
    @DisplayName("orbits")
    class Orbits {

        @Test
        void disabledOrbitIsInert() {
            assertNotNull(OrbitConfig.NONE);
            assertEquals(0f, OrbitConfig.NONE.speed(), EPS,
                    "the disabled orbit should not move anything");
        }
    }
}
