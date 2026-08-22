/*
 * Ported from the-virus-block-mc (net.cyberpunk042.field.FieldLayer), where it is
 * a plain grouping record with no Minecraft or graphics-API dependency.
 * Relicensed to MIT here by the author, per the engine/content split recorded in
 * docs/PORTING.md.
 *
 * JSON binding was removed on the way across, as it was for Primitive and Shape:
 * core does no parsing, so a codec layer above it is free to bind this to whatever
 * format the content uses. The mod's own BlendMode was folded into core's, which
 * describes the same operations — see BlendMode for the mapping.
 */
package net.cyberpunk042.mcshaders.core.field;

import java.util.List;
import java.util.Objects;
import net.cyberpunk042.mcshaders.core.animation.Animation;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.transform.Transform;

/**
 * A group of primitives that share a transform, an animation and a blend.
 *
 * <p>A {@link Primitive} is one shape with its own configuration. A layer is what makes
 * several of them one thing: move the layer and they all move, spin the layer and they
 * all spin, fade the layer and they all fade together. Links between primitives
 * ({@link PrimitiveLink}) are resolved within a layer, so a layer is also the scope in
 * which "take your radius from that one" has a meaning.
 *
 * <p>Layers are immutable. {@link Builder} is there for the cases with more than a
 * couple of non-default settings; {@link #of} covers the rest.
 *
 * <h2>What a layer does not do</h2>
 *
 * <p>It holds; it does not render, and it does not resolve. Turning a layer into
 * geometry is a sequence the caller drives — build the index, resolve each primitive's
 * links, apply what resolved, tessellate each shape — and core stops short of doing it
 * in one call on purpose: applying a resolved radius means choosing what "set the
 * radius" means for each shape type, and that is a decision about how content behaves
 * rather than a gap in the plumbing. {@link LinkResolver} has the detail and a worked
 * example.
 *
 * @param id         identifies the layer, and is what other content refers to it by
 * @param primitives the shapes in this layer, in declaration order — links may only
 *                   point backwards, so order is meaningful
 * @param transform  applied to every primitive in the layer
 * @param animation  applied to the layer as a whole, on top of each primitive's own
 * @param alpha      multiplies every primitive's alpha; 1 leaves them as they are
 * @param visible    whether to draw the layer at all
 * @param blendMode  how the layer combines with what is behind it
 */
@Stable(since = "0.5.0")
public record FieldLayer(
        String id,
        List<Primitive> primitives,
        Transform transform,
        Animation animation,
        float alpha,
        boolean visible,
        BlendMode blendMode) {

    /** Rejects the states that would fail later, in a place that says less about why. */
    public FieldLayer {
        Objects.requireNonNull(id, "a layer needs an id; other content refers to it by that");
        primitives = primitives == null ? List.of() : List.copyOf(primitives);
        transform = transform == null ? Transform.IDENTITY : transform;
        animation = animation == null ? Animation.NONE : animation;
        blendMode = blendMode == null ? BlendMode.ALPHA : blendMode;
    }

    /** An empty layer, which is the starting point a builder or an editor wants. */
    public static FieldLayer empty(String id) {
        return new FieldLayer(id, List.of(), Transform.IDENTITY, Animation.NONE,
                1.0f, true, BlendMode.ALPHA);
    }

    /** A visible, unblended layer of {@code primitives} at the origin. */
    public static FieldLayer of(String id, List<Primitive> primitives) {
        return new FieldLayer(id, primitives, Transform.IDENTITY, Animation.NONE,
                1.0f, true, BlendMode.ALPHA);
    }

    /** As {@link #of(String, List)}, with the whole layer moved by {@code transform}. */
    public static FieldLayer of(String id, List<Primitive> primitives, Transform transform) {
        return new FieldLayer(id, primitives, transform, Animation.NONE,
                1.0f, true, BlendMode.ALPHA);
    }

    /** Starts a builder for a layer called {@code id}. */
    public static Builder builder(String id) {
        return new Builder(id);
    }

    /** Whether this layer would draw anything: visible, not transparent, not empty. */
    public boolean isDrawable() {
        return visible && alpha > 0 && !primitives.isEmpty();
    }

    /** The primitive with this id, or empty if the layer has none. */
    public java.util.Optional<Primitive> primitive(String primitiveId) {
        return primitives.stream().filter(p -> p.id().equals(primitiveId)).findFirst();
    }

    /** A copy with a different primitive list. */
    public FieldLayer withPrimitives(List<Primitive> replacement) {
        return new FieldLayer(id, replacement, transform, animation, alpha, visible, blendMode);
    }

    /** A copy with a different transform. */
    public FieldLayer withTransform(Transform replacement) {
        return new FieldLayer(id, primitives, replacement, animation, alpha, visible, blendMode);
    }

    /** A copy that is shown or hidden. */
    public FieldLayer withVisible(boolean shown) {
        return new FieldLayer(id, primitives, transform, animation, alpha, shown, blendMode);
    }

    /** Builds a layer that sets more than one or two things away from their defaults. */
    public static final class Builder {

        private final String id;
        private List<Primitive> primitives = List.of();
        private Transform transform = Transform.IDENTITY;
        private Animation animation = Animation.NONE;
        private float alpha = 1.0f;
        private boolean visible = true;
        private BlendMode blendMode = BlendMode.ALPHA;

        private Builder(String id) {
            this.id = id;
        }

        public Builder primitives(List<Primitive> primitives) {
            this.primitives = primitives;
            return this;
        }

        public Builder primitives(Primitive... primitives) {
            this.primitives = List.of(primitives);
            return this;
        }

        public Builder transform(Transform transform) {
            this.transform = transform;
            return this;
        }

        public Builder animation(Animation animation) {
            this.animation = animation;
            return this;
        }

        public Builder alpha(float alpha) {
            this.alpha = alpha;
            return this;
        }

        public Builder visible(boolean visible) {
            this.visible = visible;
            return this;
        }

        public Builder blendMode(BlendMode blendMode) {
            this.blendMode = blendMode;
            return this;
        }

        public FieldLayer build() {
            return new FieldLayer(id, primitives, transform, animation, alpha, visible, blendMode);
        }
    }
}
