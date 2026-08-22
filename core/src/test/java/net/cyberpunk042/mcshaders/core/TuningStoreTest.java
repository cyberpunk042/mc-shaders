package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.cyberpunk042.mcshaders.core.edit.EditSession;
import net.cyberpunk042.mcshaders.core.edit.TuningStore;
import net.cyberpunk042.mcshaders.core.param.ParamValue;
import net.cyberpunk042.mcshaders.core.schema.EffectSchema;
import net.cyberpunk042.mcshaders.core.schema.ParamSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * What happens to an edit after the editor closes.
 *
 * <p>The gap this closes was that nothing did. A screen built a session from a
 * schema's defaults, changed it, and dropped it — so every control worked, every
 * value coerced, undo and redo behaved, and pressing Escape threw all of it away.
 * Reopening started again from the defaults. These tests are mostly about the one
 * question that exposes it: does a second session begin where the first one ended?
 */
class TuningStoreTest {

    private static final EffectSchema ORB = EffectSchema.builder("Orb", "orb", 1)
            .group("Core",
                    ParamSpec.slider("size", "Size", 0, 10, 5, "Core"),
                    ParamSpec.slider("glow", "Glow", 0, 1, 0.25, "Core"))
            .build();

    @Nested
    @DisplayName("the round trip")
    class RoundTrip {

        @Test
        @DisplayName("a second session begins where the first one ended")
        void secondSessionResumesTheFirst() {
            TuningStore store = new TuningStore();

            EditSession first = store.sessionFor(ORB);
            first.set("size", new ParamValue.Scalar(8));
            store.commit(first);

            // The assertion that fails if sessionFor ever goes back to
            // EditSession.of(schema): the reopened session would hold the default 5.
            EditSession second = store.sessionFor(ORB);
            assertEquals(8.0, second.current().scalar("size").orElseThrow(), 1e-9);
        }

        @Test
        @DisplayName("an untouched type reads its schema defaults, not nothing")
        void untouchedFallsBackToDefaults() {
            TuningStore store = new TuningStore();

            assertTrue(store.get("orb").isEmpty());
            assertEquals(5.0, store.effective(ORB).scalar("size").orElseThrow(), 1e-9);
        }

        @Test
        @DisplayName("committing files values under the session's own type")
        void commitDerivesTheType() {
            TuningStore store = new TuningStore();

            EditSession session = store.sessionFor(ORB);
            session.set("glow", new ParamValue.Scalar(0.75));
            store.commit(session);

            assertTrue(store.isTuned("orb"));
            assertEquals(0.75, store.get("orb").orElseThrow().scalar("glow").orElseThrow(), 1e-9);
        }

        @Test
        @DisplayName("values a session refused never reach the store")
        void outOfBoundsIsCoercedBeforeItArrives() {
            TuningStore store = new TuningStore();

            EditSession session = store.sessionFor(ORB);
            session.set("size", new ParamValue.Scalar(999));
            store.commit(session);

            assertEquals(10.0, store.get("orb").orElseThrow().scalar("size").orElseThrow(), 1e-9,
                    "the session coerces to the schema's bounds, so the store cannot hold 999");
        }
    }

    @Nested
    @DisplayName("forgetting")
    class Forgetting {

        @Test
        @DisplayName("a session opened and closed unchanged keeps the earlier tuning")
        void closingWithoutChangesDoesNotDiscard() {
            TuningStore store = new TuningStore();

            EditSession first = store.sessionFor(ORB);
            first.set("size", new ParamValue.Scalar(9));
            store.commit(first);

            // The trap avoided by not treating "not dirty" as "forget me": this
            // session's original IS 9, so it is clean, and dropping the entry here
            // would silently undo the previous sitting.
            store.commit(store.sessionFor(ORB));

            assertEquals(9.0, store.effective(ORB).scalar("size").orElseThrow(), 1e-9);
        }

        @Test
        @DisplayName("clearing returns the effect to its defaults")
        void clearingRestoresDefaults() {
            TuningStore store = new TuningStore();
            EditSession session = store.sessionFor(ORB);
            session.set("size", new ParamValue.Scalar(1));
            store.commit(session);

            assertTrue(store.clear("orb"));
            assertFalse(store.isTuned("orb"));
            assertEquals(5.0, store.effective(ORB).scalar("size").orElseThrow(), 1e-9);
        }

        @Test
        @DisplayName("clearing something untuned reports that there was nothing to clear")
        void clearingUntunedIsFalse() {
            assertFalse(new TuningStore().clear("orb"));
        }

        @Test
        @DisplayName("clearAll empties the store")
        void clearAllEmpties() {
            TuningStore store = new TuningStore();
            store.put("orb", ORB.defaults());
            store.put("halo", ORB.defaults());

            assertEquals(2, store.size());
            store.clearAll();
            assertTrue(store.isEmpty());
            assertTrue(store.tunedTypes().isEmpty());
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("a blank type is refused rather than stored under an empty key")
        void blankTypeRefused() {
            TuningStore store = new TuningStore();
            assertThrows(IllegalArgumentException.class, () -> store.put("  ", ORB.defaults()));
            assertThrows(IllegalArgumentException.class, () -> store.get(null));
        }

        @Test
        @DisplayName("null params and null sessions are refused")
        void nullsRefused() {
            TuningStore store = new TuningStore();
            assertThrows(IllegalArgumentException.class, () -> store.put("orb", null));
            assertThrows(IllegalArgumentException.class, () -> store.commit(null));
            assertThrows(IllegalArgumentException.class, () -> store.sessionFor(null));
        }
    }
}
