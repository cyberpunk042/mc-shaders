package net.cyberpunk042.mcshaders.core.effect;

/**
 * The category of a visual effect.
 *
 * <p>Kind is deliberately coarse. It tells a backend what class of work an effect
 * implies — and therefore whether the backend can render it at all — without
 * prescribing the implementation. Two backends may realise {@link #FOG} very
 * differently; both are correct as long as the parameters are honoured.
 */
public enum EffectKind {

    /** Tone mapping, exposure, saturation, channel curves. */
    COLOR_GRADE,

    /** Distance and height fog, atmospheric scattering. */
    FOG,

    /** Screen-space warping: heat haze, refraction, gravitational lensing. */
    DISTORT,

    /** Light bleed around bright regions. */
    BLOOM,

    /** Edge darkening or tinting. */
    VIGNETTE,

    /** Per-channel offset producing colour fringing. */
    CHROMATIC,

    /** Additive grain or noise overlay. */
    GRAIN,

    /**
     * An effect the framework has no built-in meaning for, identified entirely by
     * its parameters. A backend that does not recognise it must skip it rather
     * than fail — this is the extension point for add-on mods.
     */
    CUSTOM;

    /**
     * Whether this kind needs to read the depth buffer.
     *
     * <p>Used to reject an effect early on a backend that cannot supply depth,
     * instead of producing a silently wrong image.
     */
    public boolean requiresDepth() {
        return this == FOG || this == DISTORT;
    }
}
