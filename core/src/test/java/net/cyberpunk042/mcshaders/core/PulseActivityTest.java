package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cyberpunk042.mcshaders.core.animation.AlphaPulseConfig;
import net.cyberpunk042.mcshaders.core.animation.Animation;
import net.cyberpunk042.mcshaders.core.animation.PulseConfig;
import net.cyberpunk042.mcshaders.core.animation.PulseMode;
import net.cyberpunk042.mcshaders.core.animation.Waveform;
import net.cyberpunk042.mcshaders.core.field.Primitive;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Whether a pulse says it is animating, and whether it actually is.
 *
 * <p>{@code isActive()} is not idle bookkeeping: {@code Animation.isActive()} ORs the
 * twelve config predicates and {@code Primitive.isAnimated()} reads that, so a wrong
 * answer here is a primitive that reports the opposite of what it does.
 *
 * <p>These pin the two directions it can be wrong in, because both were reachable.
 * {@code evaluate} reads {@code speed}, {@code min} and {@code max}; the predicate used
 * to read {@code scale} and {@code speed}, and {@code scale} is read by nothing that
 * computes a value.
 */
class PulseActivityTest {

    private static final float EPS = 1e-6f;

    /** A pulse with a real range whose vestigial amplitude field is zero. */
    private static PulseConfig rangeButNoScale() {
        return new PulseConfig(0f, 1.0f, Waveform.SINE, 0.9f, 1.1f, PulseMode.SCALE);
    }

    /** A pulse with an amplitude and a speed, but nothing to move between. */
    private static PulseConfig scaleButNoRange() {
        return new PulseConfig(0.1f, 1.0f, Waveform.SINE, 1.0f, 1.0f, PulseMode.SCALE);
    }

    @Nested
    @DisplayName("the predicate agrees with the method")
    class Agreement {

        @Test
        @DisplayName("a real range is active even when the amplitude field is zero")
        void rangeWithoutScaleIsActive() {
            PulseConfig pulse = rangeButNoScale();

            assertTrue(pulse.isActive(),
                    "evaluate reads min and max, and these differ — calling this inactive "
                            + "is how a configured pulse silently does nothing");
            assertEquals(1.1f, pulse.evaluate(0.25f), EPS,
                    "and it does move: SINE peaks at t=0.25");
        }

        @Test
        @DisplayName("an empty range is inactive even when the amplitude field is set")
        void scaleWithoutRangeIsInactive() {
            PulseConfig pulse = scaleButNoRange();

            assertFalse(pulse.isActive(),
                    "min equals max, so evaluate can only ever return a constant; saying "
                            + "otherwise makes a primitive claim an animation it has not got");
            assertEquals(1.0f, pulse.evaluate(0.25f), EPS);
        }

        @Test
        @DisplayName("whatever it claims, evaluate is constant exactly when it says inactive")
        void claimMatchesBehaviour() {
            List<PulseConfig> cases = List.of(
                    PulseConfig.NONE,
                    PulseConfig.DEFAULT,
                    rangeButNoScale(),
                    scaleButNoRange(),
                    PulseConfig.sine(0.2f, 1.0f),
                    PulseConfig.sine(0f, 1.0f),
                    new PulseConfig(0.1f, 0f, Waveform.SINE, 0.5f, 1.5f, PulseMode.SCALE));

            for (PulseConfig pulse : cases) {
                boolean varies = false;
                float first = pulse.evaluate(0f);
                for (float t = 0.05f; t <= 1.0f; t += 0.05f) {
                    if (Math.abs(pulse.evaluate(t) - first) > EPS) {
                        varies = true;
                        break;
                    }
                }
                assertEquals(varies, pulse.isActive(),
                        () -> "isActive must mean 'evaluate varies with time' for " + pulse);
            }
        }
    }

    @Nested
    @DisplayName("the sibling it now matches")
    class Sibling {

        @Test
        @DisplayName("AlphaPulseConfig has always asked the range, and still agrees")
        void alphaPulseAgreesToo() {
            AlphaPulseConfig flat = new AlphaPulseConfig(1.0f, 0.5f, 0.5f, Waveform.SINE);
            AlphaPulseConfig moving = new AlphaPulseConfig(1.0f, 0.5f, 1.0f, Waveform.SINE);

            assertFalse(flat.isActive());
            assertEquals(0.5f, flat.evaluate(0.25f), EPS);
            assertTrue(moving.isActive());
            assertEquals(1.0f, moving.evaluate(0.25f), EPS);
        }
    }

    @Nested
    @DisplayName("what reads it")
    class Consumers {

        @Test
        @DisplayName("a primitive reports the animation it actually has")
        void primitiveIsAnimatedFollows() {
            Primitive withPulse = SimplePrimitive.of("a", SphereShape.of(1f).getType(),
                            SphereShape.of(1f))
                    .withAnimation(Animation.NONE.withPulse(rangeButNoScale()));

            assertTrue(withPulse.isAnimated(),
                    "Primitive.isAnimated reads Animation.isActive, which ORs this one");
        }

        @Test
        @DisplayName("the shipped constants are unchanged, which is why this is safe")
        void constantsAreUnaffected() {
            assertFalse(PulseConfig.NONE.isActive(), "NONE has speed 0 and min == max");
            assertTrue(PulseConfig.DEFAULT.isActive(), "DEFAULT pulses between 0.9 and 1.1");
        }
    }
}
