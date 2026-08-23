package net.cyberpunk042.mcshaders.diag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.cyberpunk042.mcshaders.core.api.Experimental;

/**
 * Whether each link of the chain actually ran, and what it means when one did not.
 *
 * <p>Every link between a binding file and a changed frame is now written, and
 * <strong>not one of them has been observed running.</strong> CI compiles this mod; it
 * cannot fire a hook, apply a mixin or open a screen. So the failures that remain
 * possible are the silent kind: a mixin that never applied, an event subscribed on the
 * wrong bus, a binding that matched no dimension. All three look identical from a
 * chair — the fog simply does not change.
 *
 * <p>This is the difference between "it does not work" and "link 3 of 5 never ran".
 * The counters are trivial; {@link Report#findings()} is the point, because it turns
 * a pattern of zeroes into the specific thing to go and look at.
 *
 * <h2>Why this lives in {@code common}</h2>
 *
 * <p>Counting is not Minecraft's business, and neither is the interpretation. Kept
 * free of Minecraft, the diagnosis is <em>testable on a build machine</em> — which
 * matters more here than usual, since it is the one part of the render path that can
 * be checked without the game.
 *
 * <h2>Reported once</h2>
 *
 * <p>After {@link #FRAMES_BEFORE_REPORT} frames, and never again. A line that repeats
 * every frame is not a diagnostic, it is noise, and the thing being diagnosed is
 * whether something happened at all — which a single look answers.
 */
@Experimental
public final class ChainCheck {

    /**
     * How many frames to watch before saying anything.
     *
     * <p>Long enough that a slow first frame, a world still loading, or a player who
     * has not moved yet cannot masquerade as a broken link; short enough to appear
     * within seconds of joining a world.
     */
    public static final int FRAMES_BEFORE_REPORT = 200;

    private static final AtomicLong RELOADS = new AtomicLong();
    private static final AtomicLong BINDING_FILES = new AtomicLong();
    private static final AtomicLong BINDINGS_IN_FORCE = new AtomicLong();
    private static final AtomicLong FRAMES = new AtomicLong();
    private static final AtomicLong FOG_REACHED = new AtomicLong();
    private static final AtomicLong FOG_APPLIED = new AtomicLong();
    private static final AtomicBoolean REPORTED = new AtomicBoolean();

    private ChainCheck() {
    }

    /** A datapack reload completed. */
    public static void bindingsLoaded(int fileCount, int inForce) {
        RELOADS.incrementAndGet();
        BINDING_FILES.set(Math.max(0, fileCount));
        BINDINGS_IN_FORCE.set(Math.max(0, inForce));
    }

    /** A frame was sampled — the render hook is alive. */
    public static void frameSampled() {
        FRAMES.incrementAndGet();
    }

    /** Something reached the fog applier, whatever it decided to do. */
    public static void fogReached() {
        FOG_REACHED.incrementAndGet();
    }

    /** The fog applier actually wrote values. */
    public static void fogApplied() {
        FOG_APPLIED.incrementAndGet();
    }

    /** A snapshot, for a caller that wants one regardless of timing. */
    public static Report report() {
        return new Report(RELOADS.get(), BINDING_FILES.get(), BINDINGS_IN_FORCE.get(),
                FRAMES.get(), FOG_REACHED.get(), FOG_APPLIED.get());
    }

    /**
     * The report, the first time enough frames have passed, and null afterwards.
     *
     * <p>Null rather than an empty Optional so a per-frame caller allocates nothing on
     * the overwhelming majority of frames where there is nothing to say.
     */
    public static Report dueReport() {
        if (FRAMES.get() < FRAMES_BEFORE_REPORT || !REPORTED.compareAndSet(false, true)) {
            return null;
        }
        return report();
    }

    /** Forgets everything. For tests; nothing in the mod calls it. */
    public static void reset() {
        RELOADS.set(0);
        BINDING_FILES.set(0);
        BINDINGS_IN_FORCE.set(0);
        FRAMES.set(0);
        FOG_REACHED.set(0);
        FOG_APPLIED.set(0);
        REPORTED.set(false);
    }

    /** What ran, and how many times. */
    public record Report(long reloads, long bindingFiles, long bindingsInForce,
            long frames, long fogReached, long fogApplied) {

        /** Whether every link ran at least once. */
        public boolean healthy() {
            return frames > 0 && fogApplied > 0;
        }

        /**
         * What is wrong, in the order worth checking, or a single line saying it works.
         *
         * <p>Each finding names the <em>next thing to look at</em> rather than
         * restating the symptom. A count of zero is only useful if it says which of the
         * several things that produce it is likeliest.
         */
        public List<String> findings() {
            List<String> out = new ArrayList<>();

            if (frames == 0) {
                out.add("The render hook never fired: no frame was ever sampled. "
                        + "On Fabric check LevelExtractionEvents.END_EXTRACTION is registered; "
                        + "on NeoForge check ExtractLevelRenderStateEvent is on NeoForge.EVENT_BUS "
                        + "(the game bus) — the mod bus fails silently.");
                // Everything downstream is explained by this, so do not pile on.
                return out;
            }

            if (fogReached == 0) {
                out.add("Frames are being sampled but nothing ever reached the fog applier. "
                        + "On Fabric that means FogRendererMixin did not apply — check "
                        + "mcshaders.mixins.json is listed in fabric.mod.json and that the "
                        + "setupFog descriptor still matches. On NeoForge it means "
                        + "ViewportEvent.RenderFog never fired.");
            } else if (fogApplied == 0) {
                out.add("The fog applier ran " + fogReached + " time(s) and never had anything "
                        + "to write, so the pipeline resolved no fog layer for this frame.");
                if (bindingsInForce == 0) {
                    out.add("No bindings are in force at all — which is why. "
                            + (reloads == 0
                                    ? "The datapack reload never ran: check the reload listener is registered."
                                    : "The reload ran but produced nothing; check the pack path is "
                                            + "data/<ns>/mcshaders/bindings/*.json."));
                } else {
                    out.add(bindingsInForce + " binding(s) are in force, so the likeliest cause is "
                            + "that none of them matches the dimension you are standing in, or "
                            + "their conditions are not met here.");
                }
            }

            if (reloads == 0) {
                out.add("The datapack reload never ran, so any binding in force came from Java "
                        + "registration rather than a pack file.");
            } else if (bindingFiles == 0) {
                out.add("The reload ran but found no binding files. If you expected some, check "
                        + "they are at data/<ns>/mcshaders/bindings/*.json — the doubled "
                        + "namespace is deliberate.");
            }

            if (out.isEmpty()) {
                out.add("Chain complete: " + frames + " frames sampled, fog applied "
                        + fogApplied + " of " + fogReached + " time(s), "
                        + bindingsInForce + " binding(s) in force from "
                        + bindingFiles + " pack file(s).");
            }
            return out;
        }
    }
}
