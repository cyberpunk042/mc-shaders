package net.cyberpunk042.mcshaders.core.field;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.api.Stable;
import net.cyberpunk042.mcshaders.core.appearance.AlphaRange;
import net.cyberpunk042.mcshaders.core.appearance.Appearance;
import net.cyberpunk042.mcshaders.core.mesh.Mesh;
import net.cyberpunk042.mcshaders.core.mesh.Tessellator;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.transform.Transform;
import net.cyberpunk042.mcshaders.core.transform.TransformStack;

/**
 * Turns a whole {@link FieldLayer} into geometry, in one call.
 *
 * <p>The sequence this performs was already possible and already documented — build the
 * index, resolve each primitive's links, apply what resolved, tessellate each shape —
 * but every caller had to write it, and {@code docs/USING_AS_A_LIBRARY.md} spelled it
 * out precisely because nothing here did. A step a reader has to reassemble from prose
 * is a step some caller will get wrong, and one of them already did: in the mod this
 * model came from, <strong>nothing performed the radius step at all</strong>, so a
 * content author writing {@code radiusMatch} got a link that validated, resolved to the
 * right number, and changed nothing on screen.
 *
 * <h2>The one thing this will not decide</h2>
 *
 * <p>That bug is the reason {@link RadiusPolicy} is a required argument rather than a
 * default. "Set the radius" is not one operation across shape types — a sphere has one
 * radius, a ring has an inner and an outer, a cylinder has a radius and a top radius —
 * so choosing for you would be deciding how content behaves, which is not core's to
 * decide. Making it an argument moves that open question out of a paragraph and into
 * the type system: you cannot call this without answering it, and
 * {@link RadiusPolicy#IGNORE} is available for the answer "do what the old mod did",
 * which is at least then written down at the call site.
 *
 * <h2>What it decides, because the model already had</h2>
 *
 * <p>Everything else here is composition of things core already states:
 *
 * <ul>
 *   <li><strong>Transforms</strong> compose through {@link TransformStack}, whose rules
 *       — offsets add, rotations add, scales multiply, the applied one wins for
 *       anything not additive — are core's existing answer, not a new one. The layer's
 *       transform is applied first and each primitive's on top, which is what
 *       {@link FieldLayer#transform()} meaning "applied to every primitive" requires.</li>
 *   <li><strong>Colour and alpha</strong> resolve onto the primitive's
 *       {@link Appearance} through its own {@code withColor} / {@code withAlpha}. Unlike
 *       radius there is exactly one way to set them, which is why they are done here and
 *       radius is not.</li>
 *   <li><strong>The layer's alpha</strong> multiplies each primitive's, which is what
 *       {@link FieldLayer#alpha()} says it does.</li>
 * </ul>
 *
 * <h2>What it deliberately leaves alone</h2>
 *
 * <p><strong>Animation is untouched.</strong> Spin, pulse and orbit are functions of
 * time, and this produces geometry for no particular time. {@link Piece} hands back the
 * primitive so a renderer can drive {@link Primitive#animation()} per frame against its
 * own clock; baking a phase in here would freeze it.
 *
 * <p><strong>Visibility is not enforced.</strong> A layer with {@code visible = false}
 * still builds. {@link FieldLayer#isDrawable()} is the model's own predicate and the
 * caller should ask it — an editor previewing a hidden layer wants the geometry, and a
 * builder that silently returned nothing would be indistinguishable from one that
 * failed.
 */
@Stable(since = "0.6.0")
public final class LayerGeometry {

    private LayerGeometry() {
    }

    /**
     * How a resolved radius becomes a shape.
     *
     * <p>Required, and deliberately without a default. See the class documentation for
     * why: this is a statement about how content behaves, and core does not have one.
     */
    @FunctionalInterface
    public interface RadiusPolicy {

        /**
         * Returns {@code shape} resized to {@code radius}, or unchanged.
         *
         * @param shape  the primitive's shape, never null
         * @param radius the resolved radius, always {@code >= 0}
         * @return the shape to tessellate; returning {@code shape} is legal
         */
        Shape resize(Shape shape, float radius);

        /**
         * Leaves every shape as it is.
         *
         * <p>This is what the mod this model came from actually did, and its
         * {@code radiusMatch} links therefore did nothing. Naming it is the point:
         * choosing it is a choice, where the old behaviour was an omission.
         */
        RadiusPolicy IGNORE = (shape, radius) -> shape;
    }

    /**
     * One primitive, resolved and turned into geometry.
     *
     * @param primitive  the primitive this came from, so a renderer can reach its
     *                   animation and fill without a second lookup
     * @param mesh       its shape tessellated, after {@link RadiusPolicy}
     * @param transform  the layer's transform, the primitive's, and whatever its links
     *                   resolved to, composed
     * @param appearance the primitive's appearance with resolved colour and alpha
     *                   applied, and the layer's alpha multiplied in
     */
    public record Piece(Primitive primitive, Mesh mesh, Transform transform,
            Appearance appearance) {

        /** The primitive's id, which is what links and content refer to it by. */
        public String id() {
            return primitive.id();
        }
    }

    /**
     * Builds every primitive in {@code layer}, in declaration order.
     *
     * @param layer  the layer to build; null yields an empty list
     * @param radius how a resolved radius becomes a shape — see {@link RadiusPolicy}
     * @return one {@link Piece} per primitive, in the layer's own order
     * @throws IllegalArgumentException if {@code radius} is null
     */
    public static List<Piece> build(FieldLayer layer, RadiusPolicy radius) {
        return build(layer, radius, 0);
    }

    /**
     * As {@link #build(FieldLayer, RadiusPolicy)}, at an explicit detail level.
     *
     * @param detail passed to {@link Tessellator#tessellate}; 0 means the shape's own
     *               segment count decides
     */
    public static List<Piece> build(FieldLayer layer, RadiusPolicy radius, int detail) {
        if (radius == null) {
            throw new IllegalArgumentException(
                    "A radius policy is required. Pass RadiusPolicy.IGNORE for the "
                            + "behaviour of a renderer that never applied radiusMatch.");
        }
        if (layer == null) {
            return List.of();
        }

        List<Primitive> primitives = layer.primitives();
        // Links may only point backwards, so the full index is safe here: resolveLinks
        // is what enforces direction, not the index's contents.
        Map<String, Primitive> index = LinkResolver.buildIndex(primitives);

        List<Piece> pieces = new ArrayList<>(primitives.size());
        for (Primitive primitive : primitives) {
            LinkResolver.ResolvedValues resolved = LinkResolver.resolveLinks(primitive, index);
            pieces.add(new Piece(
                    primitive,
                    mesh(primitive, resolved, radius, detail),
                    transform(layer, primitive, resolved),
                    appearance(layer, primitive, resolved)));
        }
        return List.copyOf(pieces);
    }

    private static Mesh mesh(Primitive primitive, LinkResolver.ResolvedValues resolved,
            RadiusPolicy radius, int detail) {
        Shape shape = primitive.shape();
        if (shape == null) {
            return Mesh.empty();
        }
        if (resolved.hasRadius()) {
            Shape resized = radius.resize(shape, resolved.radius());
            shape = resized == null ? shape : resized;
        }
        return Tessellator.tessellate(shape, detail);
    }

    private static Transform transform(FieldLayer layer, Primitive primitive,
            LinkResolver.ResolvedValues resolved) {
        TransformStack stack = new TransformStack();
        stack.apply(layer.transform());
        stack.apply(primitive.transform());
        return LinkResolver.applyToTransform(stack.peek(), resolved);
    }

    private static Appearance appearance(FieldLayer layer, Primitive primitive,
            LinkResolver.ResolvedValues resolved) {
        Appearance look = primitive.appearance();
        if (look == null) {
            look = Appearance.DEFAULT;
        }
        if (resolved.hasColor()) {
            look = look.withColor(resolved.color());
        }
        if (resolved.hasAlpha()) {
            look = look.withAlpha(AlphaRange.of(resolved.alpha()));
        }
        if (layer.alpha() != 1.0f) {
            AlphaRange base = look.alpha() == null ? AlphaRange.FULL : look.alpha();
            look = look.withAlpha(AlphaRange.of(
                    base.min() * layer.alpha(), base.max() * layer.alpha()));
        }
        return look;
    }
}
