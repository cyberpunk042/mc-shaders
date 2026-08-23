package net.cyberpunk042.mcshaders.diag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The diagnosis, which is the only part of the render path testable without a game.
 *
 * <p>Everything it diagnoses — a mixin that did not apply, an event on the wrong bus, a
 * binding matching no dimension — is invisible from a build machine. This is not.
 * Getting the interpretation wrong would be worse than having none: it would send
 * someone to look at the wrong link.
 */
class ChainCheckTest {

    @BeforeEach
    void clearCounters() {
        ChainCheck.reset();
    }

    private static ChainCheck.Report report(long reloads, long files, long inForce,
            long frames, long fogReached, long fogApplied) {
        return new ChainCheck.Report(reloads, files, inForce, frames, fogReached, fogApplied);
    }

    private static String joined(ChainCheck.Report r) {
        return String.join(" | ", r.findings());
    }

    @Nested
    @DisplayName("counting")
    class Counting {

        @Test
        @DisplayName("starts at nothing, so an unrun chain differs from a working one")
        void startsEmpty() {
            ChainCheck.Report r = ChainCheck.report();

            assertEquals(0, r.frames());
            assertFalse(r.healthy());
        }

        @Test
        @DisplayName("records what each link reported")
        void recordsLinks() {
            ChainCheck.bindingsLoaded(2, 3);
            ChainCheck.frameSampled();
            ChainCheck.fogReached();
            ChainCheck.fogApplied();

            ChainCheck.Report r = ChainCheck.report();

            assertEquals(1, r.reloads());
            assertEquals(2, r.bindingFiles());
            assertEquals(3, r.bindingsInForce());
            assertEquals(1, r.frames());
            assertEquals(1, r.fogReached());
            assertEquals(1, r.fogApplied());
        }

        @Test
        @DisplayName("a later reload replaces the counts rather than adding to them")
        void reloadReplacesCounts() {
            ChainCheck.bindingsLoaded(5, 5);
            ChainCheck.bindingsLoaded(1, 1);

            ChainCheck.Report r = ChainCheck.report();

            assertEquals(2, r.reloads(), "reloads accumulate");
            assertEquals(1, r.bindingFiles(), "but the counts are current state, not a total");
            assertEquals(1, r.bindingsInForce());
        }
    }

    @Nested
    @DisplayName("when the report is due")
    class Timing {

        @Test
        @DisplayName("says nothing before enough frames have passed")
        void silentEarly() {
            for (int i = 0; i < ChainCheck.FRAMES_BEFORE_REPORT - 1; i++) {
                ChainCheck.frameSampled();
                assertNull(ChainCheck.dueReport(), "reported after only " + (i + 1) + " frames");
            }
        }

        @Test
        @DisplayName("reports once, and then never again")
        void reportsExactlyOnce() {
            for (int i = 0; i < ChainCheck.FRAMES_BEFORE_REPORT; i++) {
                ChainCheck.frameSampled();
            }

            assertNotNull(ChainCheck.dueReport(), "should have reported at the threshold");
            assertNull(ChainCheck.dueReport(), "a diagnostic that repeats every frame is noise");
            assertNull(ChainCheck.dueReport());
        }
    }

    @Nested
    @DisplayName("what the findings say")
    class Findings {

        @Test
        @DisplayName("a working chain says so, and says nothing else")
        void healthyIsOneLine() {
            ChainCheck.Report r = report(1, 1, 1, 200, 200, 200);

            assertTrue(r.healthy());
            assertEquals(1, r.findings().size(), "a healthy chain must not also list problems");
            assertTrue(joined(r).contains("Chain complete"));
        }

        @Test
        @DisplayName("no frames blames the render hook and names both loaders' causes")
        void noFrames() {
            String found = joined(report(1, 1, 1, 0, 0, 0));

            assertTrue(found.contains("render hook never fired"));
            assertTrue(found.contains("END_EXTRACTION"), "Fabric's cause should be named");
            assertTrue(found.contains("EVENT_BUS"), "NeoForge's bus trap should be named");
        }

        @Test
        @DisplayName("no frames suppresses the downstream findings it would explain")
        void noFramesDoesNotPileOn() {
            // Without this, a dead render hook produces several findings, all but the
            // first a consequence of it — leaving the reader to work out which matters.
            ChainCheck.Report r = report(0, 0, 0, 0, 0, 0);

            assertEquals(1, r.findings().size(), "only the root cause should be reported");
        }

        @Test
        @DisplayName("frames but no fog reached points at the mixin and the event")
        void fogNeverReached() {
            String found = joined(report(1, 1, 1, 200, 0, 0));

            assertTrue(found.contains("nothing ever reached the fog applier"));
            assertTrue(found.contains("FogRendererMixin"), "the Fabric cause");
            assertTrue(found.contains("ViewportEvent.RenderFog"), "the NeoForge cause");
        }

        @Test
        @DisplayName("fog reached but never applied with no bindings blames the bindings")
        void reachedButNoBindings() {
            String found = joined(report(1, 0, 0, 200, 200, 0));

            assertTrue(found.contains("no fog layer"));
            assertTrue(found.contains("No bindings are in force"));
        }

        @Test
        @DisplayName("the same symptom WITH bindings blames the conditions instead")
        void reachedButUnmatched() {
            // Same zero, different cause. If the finding did not distinguish them it
            // would send someone to check a pack that loaded perfectly well.
            String found = joined(report(1, 1, 3, 200, 200, 0));

            assertTrue(found.contains("3 binding(s) are in force"));
            assertTrue(found.contains("none of them matches the dimension"));
            assertFalse(found.contains("No bindings are in force"));
        }

        @Test
        @DisplayName("a reload that never ran is distinguished from one that found nothing")
        void reloadNeverRanVersusEmpty() {
            String never = joined(report(0, 0, 0, 200, 200, 0));
            String empty = joined(report(1, 0, 0, 200, 200, 0));

            assertTrue(never.contains("reload never ran"));
            assertTrue(empty.contains("reload ran but"));
            assertFalse(empty.contains("reload never ran"));
        }

        @Test
        @DisplayName("bindings from Java only is reported, being easy to mistake for working")
        void javaOnlyBindings() {
            // Everything renders, so nothing looks wrong — but no pack file was read,
            // which is exactly the state M3 existed to leave behind.
            String found = joined(report(0, 0, 2, 200, 200, 200));

            assertTrue(found.contains("came from Java registration"));
        }

        @Test
        @DisplayName("every finding is a sentence a person can act on, not a bare count")
        void findingsAreActionable() {
            List<ChainCheck.Report> broken = List.of(
                    report(0, 0, 0, 0, 0, 0),
                    report(1, 1, 1, 200, 0, 0),
                    report(1, 0, 0, 200, 200, 0),
                    report(1, 1, 3, 200, 200, 0));

            for (ChainCheck.Report r : broken) {
                for (String finding : r.findings()) {
                    assertTrue(finding.length() > 40, "too terse to act on: " + finding);
                    assertTrue(finding.endsWith("."), "not a sentence: " + finding);
                }
            }
        }
    }
}
