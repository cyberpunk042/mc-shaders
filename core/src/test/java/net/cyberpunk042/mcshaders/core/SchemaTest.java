package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.Bounds;
import net.cyberpunk042.mcshaders.core.schema.ControlKind;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import net.cyberpunk042.mcshaders.core.schema.SchemaAudit;
import net.cyberpunk042.mcshaders.core.schema.SchemaProblem;
import net.cyberpunk042.mcshaders.core.validation.ValueRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the description of what is tunable about an effect.
 *
 * <p>The engine already knows what a parameter is and what an effect holds. This
 * layer adds what an editor needs — a name, a control, limits, a group — and its
 * job is to make a malformed description impossible to construct, because a
 * schema is read by a UI that has no way to complain.
 */
class SchemaTest {

    private static final ParamValue.Rgba WHITE = new ParamValue.Rgba(1f, 1f, 1f, 1f);

    @Nested
    @DisplayName("bounds")
    class BoundsTests {

        @Test
        void takeTheirLimitsAndUnitFromANamedRange() {
            // The project already has a vocabulary of ranges; a schema restating
            // 0f, 1f by hand is one that drifts from it silently.
            Bounds bounds = Bounds.of(ValueRange.DEGREES);

            assertEquals(0, bounds.min());
            assertEquals(360, bounds.max());
            assertEquals("°", bounds.unit());
        }

        @Test
        void clampToTheirLimits() {
            Bounds bounds = Bounds.between(0, 1);

            assertEquals(1.0, bounds.coerce(5));
            assertEquals(0.0, bounds.coerce(-5));
            assertEquals(0.5, bounds.coerce(0.5));
        }

        @Test
        void snapToTheirStep() {
            assertEquals(4.0, Bounds.stepped(0, 10, 2).coerce(4.4));
            assertEquals(6.0, Bounds.stepped(0, 10, 2).coerce(5.2));
        }

        @Test
        @DisplayName("a step that does not divide the range cannot snap past the maximum")
        void snappingNeverEscapesTheBounds() {
            // 0..10 by 3 snaps 10 to 9, not to 12.
            Bounds bounds = Bounds.stepped(0, 10, 3);

            assertTrue(bounds.contains(bounds.coerce(10)), "snapped value must stay in range");
            assertEquals(9.0, bounds.coerce(10));
        }

        @Test
        void anInvertedRangeIsRefused() {
            assertThrows(IllegalArgumentException.class, () -> new Bounds(10, 0, 0, null));
        }
    }

    @Nested
    @DisplayName("a parameter spec")
    class Specs {

        @Test
        @DisplayName("refuses a default its control could never hold")
        void defaultMustSuitTheControl() {
            // Left unchecked this gives an editor that looks right and cannot
            // round-trip its own starting value.
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> new ParamSpec("tint", "Tint", ControlKind.COLOR, Bounds.NONE,
                            new ParamValue.Scalar(1), "Look", null, List.of()));

            assertTrue(e.getMessage().contains("COLOR"), e.getMessage());
        }

        @Test
        void aColourIsOneControlNotFourSliders() {
            ParamSpec spec = ParamSpec.color("tint", "Tint", WHITE, "Look");

            assertEquals(ControlKind.COLOR, spec.control());
            assertFalse(spec.control().isBounded());
        }

        @Test
        void aChoiceNeedsOptionsAndADefaultAmongThem() {
            assertThrows(IllegalArgumentException.class,
                    () -> ParamSpec.choice("mode", "Mode", List.of(), "a", "Look"));
            assertThrows(IllegalArgumentException.class,
                    () -> ParamSpec.choice("mode", "Mode", List.of("a", "b"), "c", "Look"));
        }

        @Test
        void coercesNumbersIntoRange() {
            ParamSpec spec = ParamSpec.slider("size", "Size", 0, 1, 0.5, "Core");

            assertEquals(new ParamValue.Scalar(1.0), spec.coerce(new ParamValue.Scalar(9)));
        }

        @Test
        @DisplayName("a value of the wrong shape falls back rather than passing through")
        void wrongShapedValuesFallBack() {
            ParamSpec spec = ParamSpec.toggle("glow", "Glow", true, "Look");

            assertEquals(new ParamValue.Flag(true), spec.coerce(new ParamValue.Scalar(0)));
            assertEquals(new ParamValue.Flag(true), spec.coerce(null));
        }

        @Test
        void aChoiceOutsideItsOptionsFallsBack() {
            ParamSpec spec = ParamSpec.choice("mode", "Mode", List.of("soft", "hard"), "soft", "Look");

            assertEquals(new ParamValue.Text("soft"), spec.coerce(new ParamValue.Text("elsewhere")));
            assertEquals(new ParamValue.Text("hard"), spec.coerce(new ParamValue.Text("hard")));
        }

        @Test
        void relabellingKeepsEverythingElse() {
            ParamSpec spec = ParamSpec.slider("size", "Core Size", 0, 1, 0.5, "Core");
            ParamSpec renamed = spec.withLabel("Star Size");

            assertEquals("Star Size", renamed.label());
            assertEquals(spec.key(), renamed.key());
            assertEquals(spec.bounds(), renamed.bounds());
        }
    }

    @Nested
    @DisplayName("a schema")
    class Schemas {

        private EffectSchema orb() {
            return EffectSchema.builder("Energy Orb", "energy_orb", 1)
                    .group("Core",
                            ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"),
                            ParamSpec.slider("core.edge", "Edge Sharpness", 1, 10, 4, "Core"))
                    .group("Look",
                            ParamSpec.color("look.tint", "Tint", WHITE, "Look"),
                            ParamSpec.toggle("look.glow", "Glow", true, "Look"))
                    .build();
        }

        @Test
        void keepsGroupsAndParametersInDeclarationOrder() {
            // That order is the only layout information an editor gets.
            EffectSchema schema = orb();

            assertEquals(List.of("Core", "Look"), schema.groupNames());
            assertEquals(List.of("core.size", "core.edge", "look.tint", "look.glow"),
                    schema.parameters().stream().map(ParamSpec::key).toList());
        }

        @Test
        void findsAParameterByKey() {
            assertTrue(orb().describes("look.tint"));
            assertFalse(orb().describes("nothing"));
            assertEquals("Tint", orb().parameter("look.tint").orElseThrow().label());
        }

        @Test
        @DisplayName("an override wins on content but keeps the original position")
        void overridesDoNotReshuffleTheLayout() {
            // A later version narrowing an inherited parameter should not make the
            // panel jump around.
            EffectSchema schema = EffectSchema.builder("Orb", "energy_orb", 2)
                    .extending(orb())
                    .group("Core", ParamSpec.slider("core.size", "Star Size", 0, 2, 0.4, "Core"))
                    .build();

            assertEquals(List.of("core.size", "core.edge", "look.tint", "look.glow"),
                    schema.parameters().stream().map(ParamSpec::key).toList());
            assertEquals("Star Size", schema.parameter("core.size").orElseThrow().label());
            assertEquals(4, schema.parameterCount(), "an override is not an extra parameter");
        }

        @Test
        void offersItsDefaultsAsParameters() {
            EffectParams defaults = orb().defaults();

            assertEquals(0.15, defaults.scalarOr("core.size", -1), 1e-9);
            assertTrue(defaults.flagOr("look.glow", false));
            assertEquals(4, defaults.size());
        }

        @Test
        void bringsEditedValuesBackIntoRange() {
            EffectParams edited = orb().defaults().with("core.size", new ParamValue.Scalar(99));

            assertEquals(1.0, orb().coerce(edited).scalarOr("core.size", -1), 1e-9);
        }

        @Test
        @DisplayName("keys the schema does not describe survive coercion")
        void unknownKeysAreLeftAlone() {
            // An editor is not the only thing that writes parameters. Dropping what it
            // does not recognise would make opening a panel destructive.
            EffectParams params = orb().defaults().with("legacy.knob", new ParamValue.Scalar(7));

            assertEquals(7.0, orb().coerce(params).scalarOr("legacy.knob", -1), 1e-9);
        }

        @Test
        void versionsStartAtOne() {
            assertThrows(IllegalArgumentException.class,
                    () -> EffectSchema.builder("Orb", "energy_orb", 0).build());
        }
    }

    @Nested
    @DisplayName("auditing a schema against what an effect carries")
    class Audit {

        private final EffectSchema schema = EffectSchema.builder("Orb", "energy_orb", 1)
                .group("Core",
                        ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"),
                        ParamSpec.toggle("core.glow", "Glow", true, "Core"))
                .build();

        @Test
        void amatchingPairAgrees() {
            assertTrue(SchemaAudit.agree(schema, schema.defaults()),
                    () -> SchemaAudit.audit(schema, schema.defaults()).toString());
        }

        @Test
        @DisplayName("a control bound to a key the effect lacks does nothing when dragged")
        void aControlWithNothingBehindItIsReported() {
            EffectParams effect = EffectParams.builder().flag("core.glow", true).build();

            List<SchemaProblem> problems = SchemaAudit.audit(schema, effect);

            assertEquals(SchemaProblem.Kind.UNBACKED, problems.get(0).kind());
            assertEquals("core.size", problems.get(0).key());
        }

        @Test
        @DisplayName("a parameter no control reaches is not editable, and is worth saying")
        void anUnreachableParameterIsReported() {
            EffectParams effect = schema.defaults().with("hidden.knob", new ParamValue.Scalar(1));

            List<SchemaProblem> problems = SchemaAudit.audit(schema, effect);

            assertEquals(1, problems.size());
            assertEquals(SchemaProblem.Kind.UNREACHABLE, problems.get(0).kind());
            assertTrue(SchemaAudit.agree(schema, effect), "informational, not a failure");
        }

        @Test
        @DisplayName("a shipped default outside its own declared range changes on first open")
        void anOutOfRangeDefaultIsReported() {
            // A fresh install and an install where someone opened the panel and closed
            // it again would then render differently, from a control nobody touched.
            EffectParams effect = schema.defaults().with("core.size", new ParamValue.Scalar(5));

            List<SchemaProblem> problems = SchemaAudit.audit(schema, effect);

            assertEquals(SchemaProblem.Kind.DEFAULT_OUT_OF_RANGE, problems.get(0).kind());
            assertTrue(problems.get(0).detail().contains("1.0"), problems.get(0).detail());
        }

        @Test
        void aValueOfTheWrongShapeIsAnError() {
            EffectParams effect = schema.defaults().with("core.glow", new ParamValue.Scalar(1));

            List<SchemaProblem> problems = SchemaAudit.audit(schema, effect);

            assertEquals(SchemaProblem.Kind.SHAPE_MISMATCH, problems.get(0).kind());
            assertTrue(problems.get(0).isError());
        }
    }
}
