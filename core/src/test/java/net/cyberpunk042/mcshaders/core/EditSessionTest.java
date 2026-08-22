package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import net.cyberpunk042.mcshaders.core.edit.EditSession;
import net.cyberpunk042.mcshaders.core.param.EffectParams;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for one editing sitting.
 *
 * <p>The session owns the mutation so that history cannot be forgotten, which is
 * the property most of these exist to pin. The rest are about undo meaning what a
 * person expects it to mean — which is mostly about what is deliberately <em>not</em>
 * recorded.
 */
class EditSessionTest {

    private static EffectSchema schema() {
        return EffectSchema.builder("Orb", "energy_orb", 1)
                .group("Core",
                        ParamSpec.slider("core.size", "Core Size", 0, 1, 0.15, "Core"),
                        ParamSpec.toggle("core.glow", "Glow", true, "Core"))
                .build();
    }

    private static ParamValue.Scalar scalar(double v) {
        return new ParamValue.Scalar(v);
    }

    @Nested
    @DisplayName("editing")
    class Editing {

        @Test
        void startsFromTheSchemasDefaults() {
            EditSession session = EditSession.of(schema());

            assertEquals(0.15, session.current().scalarOr("core.size", -1), 1e-9);
            assertFalse(session.isDirty());
        }

        @Test
        void aSetIsCoercedToWhatTheSpecPermits() {
            EditSession session = EditSession.of(schema());

            session.set("core.size", scalar(99));

            assertEquals(1.0, session.current().scalarOr("core.size", -1), 1e-9);
        }

        @Test
        @DisplayName("values handed in at the start are coerced too")
        void aSessionNeverBeginsHoldingSomethingItsSchemaWouldRefuse() {
            EffectParams outOfRange = EffectParams.builder().scalar("core.size", 99).build();

            EditSession session = EditSession.of(schema(), outOfRange);

            assertEquals(1.0, session.current().scalarOr("core.size", -1), 1e-9);
            assertFalse(session.isDirty(), "coercing the starting values is not an edit");
        }

        @Test
        @DisplayName("a key the schema does not describe is held as given")
        void unknownKeysAreKept() {
            // An effect may carry parameters no control reaches. Refusing them would
            // make an editor lose data it never showed.
            EditSession session = EditSession.of(schema());

            assertTrue(session.set("hidden.knob", scalar(7)));
            assertEquals(7.0, session.current().scalarOr("hidden.knob", -1), 1e-9);
        }

        @Test
        void resetPutsOneParameterBackToItsDeclaredValue() {
            EditSession session = EditSession.of(schema());
            session.set("core.size", scalar(0.9));

            assertTrue(session.reset("core.size"));
            assertEquals(0.15, session.current().scalarOr("core.size", -1), 1e-9);
        }

        @Test
        void changedKeysAreTheOnesThatDiffer() {
            EditSession session = EditSession.of(schema());
            session.set("core.size", scalar(0.9));

            assertEquals(Set.of("core.size"), session.changedKeys());
            assertTrue(session.isDirty());
        }

        @Test
        void resetAllReturnsToWhereTheSittingBegan() {
            EditSession session = EditSession.of(schema());
            session.set("core.size", scalar(0.9));
            session.set("core.glow", new ParamValue.Flag(false));

            assertTrue(session.resetAll());
            assertFalse(session.isDirty());
            assertEquals(Set.of(), session.changedKeys());
        }
    }

    @Nested
    @DisplayName("history")
    class History {

        @Test
        void anEditCanBeTakenBack() {
            EditSession session = EditSession.of(schema());
            session.set("core.size", scalar(0.9));

            assertTrue(session.undo());
            assertEquals(0.15, session.current().scalarOr("core.size", -1), 1e-9);
            assertTrue(session.redo());
            assertEquals(0.9, session.current().scalarOr("core.size", -1), 1e-9);
        }

        @Test
        @DisplayName("history cannot be forgotten, because the session performs the edit")
        void everyEditIsRecorded() {
            // The alternative design — a history object callers push to before
            // mutating — makes an edit quietly un-undoable the first time someone
            // forgets, and the bug surfaces nowhere near the omission.
            EditSession session = EditSession.of(schema());

            session.set("core.size", scalar(0.3));
            session.set("core.glow", new ParamValue.Flag(false));
            session.reset("core.size");

            assertEquals(3, session.historyDepth());
        }

        @Test
        @DisplayName("a change that changes nothing is not a step")
        void noOpsAreNotRecorded() {
            // Dragging a slider away and back would otherwise fill the history with
            // steps that do nothing when undone, and an undo that appears to do
            // nothing is worse than no undo at all.
            EditSession session = EditSession.of(schema());

            assertFalse(session.set("core.size", scalar(0.15)), "already that value");
            session.set("core.size", scalar(0.5));
            assertFalse(session.set("core.size", scalar(0.5)), "same again");

            assertEquals(1, session.historyDepth());
        }

        @Test
        @DisplayName("coercion can make two different sets the same edit")
        void coercionCollapsesEditsThatLandOnTheSameValue() {
            EditSession session = EditSession.of(schema());

            session.set("core.size", scalar(50));
            assertFalse(session.set("core.size", scalar(99)),
                    "both clamp to 1.0, so the second changes nothing");
            assertEquals(1, session.historyDepth());
        }

        @Test
        void editingAfterAnUndoDiscardsWhatWasUndone() {
            EditSession session = EditSession.of(schema());
            session.set("core.size", scalar(0.3));
            session.undo();

            session.set("core.size", scalar(0.7));

            assertFalse(session.canRedo(), "a new edit is a new branch");
        }

        @Test
        void undoOnAFreshSessionDoesNothing() {
            EditSession session = EditSession.of(schema());

            assertFalse(session.undo());
            assertFalse(session.redo());
            assertFalse(session.canUndo());
        }

        @Test
        void theOldestStepsAreDroppedPastTheLimit() {
            EditSession session = EditSession.of(schema(), schema().defaults(), 2);

            session.set("core.size", scalar(0.2));
            session.set("core.size", scalar(0.3));
            session.set("core.size", scalar(0.4));

            assertEquals(2, session.historyDepth());
            session.undo();
            session.undo();
            assertFalse(session.canUndo());
            assertEquals(0.2, session.current().scalarOr("core.size", -1), 1e-9,
                    "the sitting can no longer reach its starting value");
        }

        @Test
        void aHistoryLimitBelowOneIsRefused() {
            assertThrows(IllegalArgumentException.class,
                    () -> EditSession.of(schema(), schema().defaults(), 0));
        }
    }
}
