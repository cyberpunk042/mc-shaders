package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.Interpolation;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ParamTest {

    @Nested
    @DisplayName("value interpolation")
    class Values {

        @Test
        void scalarsBlendLinearly() {
            ParamValue result = ParamValue.of(0.0).lerp(ParamValue.of(10.0), 0.25);
            assertEquals(new ParamValue.Scalar(2.5), result);
        }

        @Test
        void progressIsClampedOutsideUnitRange() {
            ParamValue low = ParamValue.of(0.0).lerp(ParamValue.of(10.0), -5.0);
            ParamValue high = ParamValue.of(0.0).lerp(ParamValue.of(10.0), 5.0);
            assertEquals(new ParamValue.Scalar(0.0), low);
            assertEquals(new ParamValue.Scalar(10.0), high);
        }

        @Test
        void colorsClampToUnitRangeOnConstruction() {
            ParamValue.Rgba c = new ParamValue.Rgba(2.0f, -1.0f, 0.5f, 4.0f);
            assertEquals(1.0f, c.r());
            assertEquals(0.0f, c.g());
            assertEquals(0.5f, c.b());
            assertEquals(1.0f, c.a());
        }

        @Test
        @DisplayName("NaN in a colour channel becomes 0 rather than propagating")
        void colorRejectsNaN() {
            assertEquals(0.0f, new ParamValue.Rgba(Float.NaN, 0f, 0f, 1f).r());
        }

        @Test
        @DisplayName("mismatched shapes step at the halfway point instead of throwing")
        void mismatchedShapesStep() {
            ParamValue from = ParamValue.of(1.0);
            ParamValue to = ParamValue.of("mode_b");
            assertEquals(from, from.lerp(to, 0.49));
            assertEquals(to, from.lerp(to, 0.5));
        }

        @Test
        void flagsStepAtHalfway() {
            ParamValue off = ParamValue.of(false);
            ParamValue on = ParamValue.of(true);
            assertEquals(off, off.lerp(on, 0.4));
            assertEquals(on, off.lerp(on, 0.6));
        }

        @Test
        void textValueRejectsNull() {
            assertThrows(IllegalArgumentException.class, () -> new ParamValue.Text(null));
        }

        @Test
        void clampMapsNaNToZero() {
            assertEquals(0.0, Interpolation.clamp01(Double.NaN));
        }
    }

    @Nested
    @DisplayName("parameter maps")
    class Maps {

        @Test
        void typedAccessorsReturnEmptyOnShapeMismatch() {
            EffectParams params = EffectParams.builder().scalar("intensity", 0.8).build();
            assertTrue(params.scalar("intensity").isPresent());
            assertFalse(params.color("intensity").isPresent());
            assertFalse(params.scalar("absent").isPresent());
            assertEquals(0.5, params.scalarOr("absent", 0.5));
        }

        @Test
        @DisplayName("explicit values win over defaults")
        void defaultsDoNotOverrideExplicitValues() {
            EffectParams explicit = EffectParams.builder().scalar("intensity", 1.0).build();
            EffectParams defaults = EffectParams.builder()
                    .scalar("intensity", 0.1)
                    .scalar("radius", 4.0)
                    .build();

            EffectParams merged = explicit.withDefaults(defaults);
            assertEquals(1.0, merged.scalarOr("intensity", -1));
            assertEquals(4.0, merged.scalarOr("radius", -1));
        }

        @Test
        @DisplayName("blending covers the union of both key sets")
        void lerpCoversUnionOfKeys() {
            EffectParams a = EffectParams.builder().scalar("shared", 0.0).scalar("onlyA", 7.0).build();
            EffectParams b = EffectParams.builder().scalar("shared", 1.0).scalar("onlyB", 9.0).build();

            EffectParams mid = a.lerp(b, 0.5);
            assertEquals(0.5, mid.scalarOr("shared", -1));
            assertEquals(7.0, mid.scalarOr("onlyA", -1), "unmatched source key must survive the blend");
            assertEquals(9.0, mid.scalarOr("onlyB", -1), "unmatched destination key must survive the blend");
        }

        @Test
        void lerpAtEndpointsReturnsOperandsUnchanged() {
            EffectParams a = EffectParams.builder().scalar("x", 0.0).build();
            EffectParams b = EffectParams.builder().scalar("x", 1.0).build();
            assertEquals(a, a.lerp(b, 0.0));
            assertEquals(b, a.lerp(b, 1.0));
        }

        @Test
        void immutabilityIsPreservedByWith() {
            EffectParams original = EffectParams.builder().scalar("x", 1.0).build();
            EffectParams modified = original.with("y", ParamValue.of(2.0));
            assertEquals(1, original.size());
            assertEquals(2, modified.size());
        }

        @Test
        void blankKeysAreRejected() {
            assertThrows(IllegalArgumentException.class,
                    () -> EffectParams.builder().scalar("  ", 1.0));
        }
    }
}
