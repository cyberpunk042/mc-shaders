package net.cyberpunk042.mcshaders.core.transition;

import net.cyberpunk042.mcshaders.core.effect.EffectStack;
import net.cyberpunk042.mcshaders.core.param.Interpolation;

/**
 * A timed blend from one effect stack to another.
 *
 * <p>This is what stops a dimension change from being a visible pop. It is
 * immutable and advanced by {@link #advance}, so the render thread can hold a
 * reference to a consistent snapshot for the duration of a frame.
 *
 * <p>Time is carried in ticks rather than seconds to match the game's own clock;
 * partial ticks are fine, which is what keeps the blend smooth above 20 FPS.
 */
public final class Transition {

    private final EffectStack from;
    private final EffectStack to;
    private final double durationTicks;
    private final double elapsedTicks;
    private final Easing easing;

    private Transition(EffectStack from, EffectStack to, double durationTicks, double elapsedTicks, Easing easing) {
        this.from = from;
        this.to = to;
        this.durationTicks = durationTicks;
        this.elapsedTicks = elapsedTicks;
        this.easing = easing;
    }

    /**
     * Starts a transition.
     *
     * <p>A non-positive duration yields an already-complete transition rather than
     * an error: "change instantly" is a legitimate request, and dividing by it
     * later would not be.
     */
    public static Transition start(EffectStack from, EffectStack to, double durationTicks, Easing easing) {
        EffectStack safeFrom = from == null ? EffectStack.empty() : from;
        EffectStack safeTo = to == null ? EffectStack.empty() : to;
        Easing safeEasing = easing == null ? Easing.SMOOTH : easing;
        if (durationTicks <= 0.0 || Double.isNaN(durationTicks)) {
            return new Transition(safeFrom, safeTo, 0.0, 0.0, safeEasing);
        }
        return new Transition(safeFrom, safeTo, durationTicks, 0.0, safeEasing);
    }

    /** An already-settled transition holding {@code stack}. */
    public static Transition settled(EffectStack stack) {
        EffectStack safe = stack == null ? EffectStack.empty() : stack;
        return new Transition(safe, safe, 0.0, 0.0, Easing.SMOOTH);
    }

    /** Raw progress in {@code [0, 1]} before easing. */
    public double rawProgress() {
        if (durationTicks <= 0.0) {
            return 1.0;
        }
        return Interpolation.clamp01(elapsedTicks / durationTicks);
    }

    /** Eased progress in {@code [0, 1]}. */
    public double progress() {
        return easing.apply(rawProgress());
    }

    public boolean isComplete() {
        return rawProgress() >= 1.0;
    }

    public EffectStack from() {
        return from;
    }

    public EffectStack to() {
        return to;
    }

    public Easing easing() {
        return easing;
    }

    public double durationTicks() {
        return durationTicks;
    }

    public double elapsedTicks() {
        return elapsedTicks;
    }

    /** The blended stack at the current point in the transition. */
    public EffectStack current() {
        if (isComplete()) {
            return to;
        }
        return from.lerp(to, progress());
    }

    /** Returns a copy advanced by {@code deltaTicks}. Negative deltas are ignored. */
    public Transition advance(double deltaTicks) {
        if (deltaTicks <= 0.0 || Double.isNaN(deltaTicks) || isComplete()) {
            return this;
        }
        return new Transition(from, to, durationTicks, elapsedTicks + deltaTicks, easing);
    }

    /**
     * Retargets to a new destination, starting from whatever is on screen right now.
     *
     * <p>Redirecting mid-blend — a player crossing two portals in quick succession —
     * must not snap back to the original source. Starting the new transition from
     * {@link #current()} keeps the image continuous through the redirect.
     */
    public Transition retarget(EffectStack newTo, double durationTicks, Easing newEasing) {
        return start(current(), newTo, durationTicks, newEasing);
    }

    @Override
    public String toString() {
        return "Transition(" + String.format("%.2f", rawProgress() * 100.0) + "%, "
                + easing + ", " + durationTicks + " ticks)";
    }
}
