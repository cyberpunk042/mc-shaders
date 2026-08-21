package net.cyberpunk042.mcshaders.core.effect;

/** How an effect's output combines with what is already on screen. */
public enum BlendMode {

    /** Overwrite entirely, ignoring weight-driven mixing. */
    REPLACE,

    /** Standard source-over compositing, driven by the layer weight. */
    ALPHA,

    /** Adds light. Brightens; never darkens. */
    ADD,

    /** Multiplies. Darkens; never brightens. */
    MULTIPLY,

    /** Inverse-multiply. Brightens toward white, softer than {@link #ADD}. */
    SCREEN;

    /**
     * Whether stacking this mode repeatedly compounds without converging.
     *
     * <p>The compiler uses this to warn on pathological stacks — twelve additive
     * bloom layers is almost certainly a pack authoring mistake, not an intent.
     */
    public boolean accumulates() {
        return this == ADD || this == SCREEN;
    }
}
