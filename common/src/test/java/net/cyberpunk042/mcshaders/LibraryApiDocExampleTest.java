package net.cyberpunk042.mcshaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.binding.BindingRegistry;
import net.cyberpunk042.mcshaders.core.binding.Condition;
import net.cyberpunk042.mcshaders.core.binding.DimensionBinding;
import net.cyberpunk042.mcshaders.core.binding.DimensionId;
import net.cyberpunk042.mcshaders.core.binding.WorldState;
import net.cyberpunk042.mcshaders.core.effect.EffectDefinition;
import net.cyberpunk042.mcshaders.core.effect.EffectKind;
import net.cyberpunk042.mcshaders.core.effect.EffectLayer;
import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

/**
 * The library guide's API-level examples, run.
 *
 * <p>{@code LibraryDocExampleTest} in {@code core} pins the guide's core-level
 * examples, and the README says the guide's examples "are themselves tests". That was
 * true of half the document: every example calling {@link McShadersAPI} — registration,
 * contributing a look, contributing an effect, making one editable — was pinned by
 * nothing. Those are the examples a third party actually integrates against, and they
 * could have gone stale without any build noticing.
 *
 * <p>They cannot live in {@code core}: {@code McShadersAPI} is in {@code common}, which
 * depends on core and not the other way round. So they live here.
 *
 * <p><strong>Ordered first.</strong> These call {@code registerX}, which throws once
 * registration closes, and other classes here close it deliberately. {@code @Order(0)}
 * puts this ahead of them; it does not close registration itself.
 *
 * <h2>Not pinned to the guide, and not for want of trying</h2>
 *
 * <p>Two mechanisms in this repository hold a test to the page it copies, and neither
 * fits here. {@code ShapeRecipeDocTest.Page} reads the block between {@code begin}/
 * {@code end} markers and requires every line of it to appear on the page; there is no
 * verbatim block here to mark, because these examples are interleaved with setup and
 * assertions. {@code LibraryDocExampleTest.Guide} requires each {@code // ── docs: X ──}
 * marker to name a real heading; this file has no such markers.
 *
 * <p>The obvious substitute is the nested classes' display names, and it does not work:
 * three of the five correspond to a heading, "making an effect editable" and
 * "registration's own lifecycle" are deliberately worded differently from "Making it
 * reachable from the editor" and "Registration and its lifecycle", and the last one says
 * in its own name that the guide does not carry it. A check on those would fail on
 * phrasing rather than on drift, and making it pass would mean renaming these to suit
 * the check rather than the reader.
 *
 * <p>So this is recorded rather than closed. Adding markers here would make the existing
 * mechanism apply, but choosing which section each class belongs to is a claim about the
 * guide's structure, not a correction — and a wrong one would be worse than the gap,
 * because it would look guarded.
 */
@Order(0)
class LibraryApiDocExampleTest {

    private static final DimensionId DREAMSCAPE = DimensionId.parse("mymod:dreamscape");

    @Nested
    @DisplayName("contributing a dimension look")
    class ContributingALook {

        @Test
        @DisplayName("the guide's base-plus-conditional example merges as it claims")
        void layersMergeByIdAcrossBindings() {
            // Verbatim from the guide, minus the TimeOfDay condition — see the note in
            // TheTimeOfDayCaveat below for why that one cannot be exercised on 26.2.
            EffectStack look = EffectStack.of(
                    EffectLayer.builder("haze")
                            .kind(EffectKind.DISTORT)
                            .params(EffectParams.builder().scalar("amplitude", 0.02).build())
                            .build(),
                    EffectLayer.builder("grade")
                            .kind(EffectKind.COLOR_GRADE)
                            .params(EffectParams.builder().color("tint", 0.8f, 0.9f, 1.0f, 1.0f).build())
                            .build());

            DimensionBinding base = DimensionBinding.of("doc:dreamscape", DREAMSCAPE, look);

            DimensionBinding night = new DimensionBinding(
                    "doc:dreamscape_night",
                    DREAMSCAPE,
                    Condition.always(),
                    EffectStack.of(EffectLayer.builder("grade").kind(EffectKind.COLOR_GRADE)
                            .params(EffectParams.builder().color("tint", 0.3f, 0.3f, 0.6f, 1.0f).build())
                            .build()),
                    10);

            EffectStack resolved = BindingRegistry.of(base, night)
                    .resolve(WorldState.of(DREAMSCAPE));

            // The guide's claim, in its own words: "`haze` from the base binding
            // survives untouched; only `grade` is overridden."
            assertEquals(2, resolved.layers().size(), "haze should survive the override");
            assertTrue(resolved.layers().stream().anyMatch(l -> l.id().equals("haze")));

            EffectLayer grade = resolved.layers().stream()
                    .filter(l -> l.id().equals("grade")).findFirst().orElseThrow();
            ParamValue.Rgba tint = grade.params().color("tint").orElseThrow();
            assertEquals(0.3f, tint.r(), 0.001, "the higher-priority binding's grade should win");
        }
    }

    @Nested
    @DisplayName("contributing a new effect type")
    class ContributingAnEffect {

        @Test
        @DisplayName("defaults fill gaps only, exactly as the guide says")
        void defaultsFillGapsOnly() {
            EffectDefinition kaleidoscope = EffectDefinition.of("doc:kaleidoscope", "doc")
                    .withDefaults(EffectParams.builder()
                            .scalar("segments", 6.0)
                            .scalar("rotation", 0.0)
                            .build());

            EffectLayer layer = EffectLayer.builder("swirl")
                    .definition(kaleidoscope)
                    .params(EffectParams.builder().scalar("segments", 12.0).build())
                    .build();

            EffectParams effective = layer.params().withDefaults(kaleidoscope.defaultParams());

            // "Defaults fill gaps only — `segments` stays 12, `rotation` becomes 0."
            assertEquals(12.0, effective.scalar("segments").orElseThrow(), 0.001);
            assertEquals(0.0, effective.scalar("rotation").orElseThrow(), 0.001);
        }

        @Test
        @DisplayName("a colliding effect id is refused rather than shadowing the first")
        void collisionIsRefused() {
            // The guide: "Effect types are refused on collision rather than
            // last-write-wins, so one mod cannot silently shadow another's effect."
            EffectDefinition first = EffectDefinition.of("doc:collide", "doc");
            McShadersAPI.registerEffect(first);

            assertThrows(IllegalStateException.class,
                    () -> McShadersAPI.registerEffect(EffectDefinition.of("doc:collide", "other")));
        }
    }

    @Nested
    @DisplayName("making an effect editable")
    class MakingItEditable {

        @Test
        @DisplayName("a registered schema is reachable through schemas(), which is what the editor reads")
        void registeredSchemaIsReachable() {
            // The gap this test exists for: the guide explained how to BUILD a schema
            // and never how to register one, so an effect could be perfectly described
            // and still never appear in the editor. registerSchema is the link.
            EffectSchema schema = EffectSchema.builder("Doc Orb", "doc_orb", 1)
                    .group("Core", ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"))
                    .build();

            McShadersAPI.registerSchema("doc:orb", schema);

            assertTrue(McShadersAPI.schemas().all().stream()
                            .anyMatch(s -> s.effectType().equals("doc_orb")),
                    "a registered schema must be findable, or the editor cannot show it");
        }
    }

    @Nested
    @DisplayName("registration's own lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("isRegistrationOpen answers before registerX would throw")
        void registrationIsOpenBeforeItCloses() {
            // isRegistrationOpen had no caller, no test and no mention in the guide.
            // It is the polite alternative to catching IllegalStateException, and this
            // is the behaviour that makes it worth keeping.
            assertTrue(McShadersAPI.isRegistrationOpen(),
                    "this class is ordered first precisely so registration is still open");
        }
    }

    @Nested
    @DisplayName("the time-of-day caveat the guide did not carry")
    class TheTimeOfDayCaveat {

        @Test
        @DisplayName("a TimeOfDay binding cannot activate on 26.2, so the guide must say so")
        void timeOfDayNeverActivates() {
            // The guide's conditional-binding example used Condition.TimeOfDay. On 26.2
            // the day time cannot be read, so a reader following that example gets a
            // binding that never fires and looks exactly like a typo. The example now
            // carries a warning; this pins the reason.
            DimensionBinding night = new DimensionBinding(
                    "doc:night", DREAMSCAPE,
                    new Condition.TimeOfDay(13000, 1000),
                    EffectStack.of(EffectLayer.of("grade", EffectKind.COLOR_GRADE, EffectParams.empty())),
                    10);

            assertFalse(night.appliesTo(WorldState.of(DREAMSCAPE).withDayTime(WorldState.UNKNOWN_DAY_TIME)),
                    "with no readable day time, a TimeOfDay binding must not activate");
        }
    }
}
