package net.cyberpunk042.mcshaders.core.effect;

/**
 * How something's output combines with what is already there.
 *
 * <p>Used in two places, because it is the same set of operations in both: compositing
 * a post-processing effect over the frame, and blending a
 * {@link net.cyberpunk042.mcshaders.core.field.FieldLayer} of geometry over the layers
 * behind it. Shipping two enums whose constants mean the same arithmetic is the sort of
 * duplicate declaration that drifts.
 *
 * <p>The field system this was reconciled with called {@link #ALPHA} "NORMAL" and had no
 * equivalent of {@link #REPLACE}; content using the old spelling maps onto {@code ALPHA}.
 */
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
