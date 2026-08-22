package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.cyberpunk042.mcshaders.core.animation.Animation;
import net.cyberpunk042.mcshaders.core.appearance.Appearance;
import net.cyberpunk042.mcshaders.core.effect.BlendMode;
import net.cyberpunk042.mcshaders.core.field.FieldLayer;
import net.cyberpunk042.mcshaders.core.field.LinkResolver;
import net.cyberpunk042.mcshaders.core.field.Primitive;
import net.cyberpunk042.mcshaders.core.field.PrimitiveLink;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.fill.FillConfig;
import net.cyberpunk042.mcshaders.core.pattern.ArrangementConfig;
import net.cyberpunk042.mcshaders.core.shape.RingShape;
import net.cyberpunk042.mcshaders.core.shape.Shape;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import net.cyberpunk042.mcshaders.core.transform.Transform;
import net.cyberpunk042.mcshaders.core.visibility.VisibilityMask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Checks the unit that makes several primitives one thing.
 *
 * <p>A layer's job is grouping: move it and they all move, fade it and they all fade,
 * and links resolve within it. The tests here are about the two ways a grouping record
 * goes wrong — letting a caller hold a reference into its own contents, and accepting a
 * half-built state that fails somewhere else later.
 */
class FieldLayerTest {

    @Test
    @DisplayName("a layer holds its primitives in declaration order, because links depend on it")
    void orderIsPreserved() {
        // Links may only point backwards, so a layer that reorders its contents would
        // turn valid content into forward references.
        SimplePrimitive first = primitive("a", SphereShape.ofRadius(1.0f), PrimitiveLink.NONE);
        SimplePrimitive second = primitive("b", RingShape.at(0.8f, 1.0f, 0f),
                PrimitiveLink.radiusMatch("a", 0.5f));

        FieldLayer layer = FieldLayer.of("layer", List.of(first, second));

        assertEquals(List.of("a", "b"), layer.primitives().stream().map(Primitive::id).toList());
        assertTrue(LinkResolver.isValid(layer.primitives()),
                "the layer reordered its primitives into a forward reference");
    }

    @Test
    @DisplayName("the list a layer was built from cannot be changed underneath it")
    void primitivesAreCopiedIn() {
        // Without the copy, a caller keeping their own list could add a primitive after
        // construction and change what the layer contains — including turning a valid
        // link order into an invalid one.
        List<Primitive> mutable = new ArrayList<>();
        mutable.add(primitive("a", SphereShape.ofRadius(1.0f), PrimitiveLink.NONE));

        FieldLayer layer = FieldLayer.of("layer", mutable);
        mutable.add(primitive("b", SphereShape.ofRadius(1.0f), PrimitiveLink.NONE));

        assertEquals(1, layer.primitives().size(),
                "adding to the caller's list changed the layer");
        assertThrows(UnsupportedOperationException.class,
                () -> layer.primitives().add(primitive("c", SphereShape.ofRadius(1.0f),
                        PrimitiveLink.NONE)),
                "the layer handed out a list that can be modified");
    }

    @Test
    @DisplayName("the optional pieces default rather than staying null")
    void absentPiecesGetDefaults() {
        // A null transform or animation would reach whatever consumes the layer and fail
        // there, far from the construction that omitted it.
        FieldLayer layer = new FieldLayer("layer", null, null, null, 1.0f, true, null);

        assertSame(Transform.IDENTITY, layer.transform());
        assertSame(Animation.NONE, layer.animation());
        assertSame(BlendMode.ALPHA, layer.blendMode(), "the default blend should be plain alpha");
        assertEquals(List.of(), layer.primitives());
    }

    @Test
    @DisplayName("a layer without an id is refused where it is made")
    void idIsRequired() {
        assertThrows(NullPointerException.class,
                () -> FieldLayer.of(null, List.of()),
                "a layer with no id was accepted; other content refers to layers by id");
    }

    @Test
    @DisplayName("drawable means visible, not transparent, and not empty")
    void drawabilityCoversAllThreeWays() {
        SimplePrimitive one = primitive("a", SphereShape.ofRadius(1.0f), PrimitiveLink.NONE);

        assertTrue(FieldLayer.of("l", List.of(one)).isDrawable());
        assertFalse(FieldLayer.empty("l").isDrawable(), "an empty layer draws nothing");
        assertFalse(FieldLayer.of("l", List.of(one)).withVisible(false).isDrawable());
        assertFalse(FieldLayer.builder("l").primitives(one).alpha(0f).build().isDrawable(),
                "a fully transparent layer draws nothing");
    }

    @Test
    @DisplayName("the builder and the factories agree")
    void builderMatchesFactories() {
        SimplePrimitive one = primitive("a", SphereShape.ofRadius(1.0f), PrimitiveLink.NONE);

        assertEquals(FieldLayer.of("l", List.of(one)),
                FieldLayer.builder("l").primitives(one).build());
        assertEquals(FieldLayer.empty("l"), FieldLayer.builder("l").build());
    }

    @Test
    @DisplayName("the with-methods change one thing and keep the rest")
    void withMethodsAreNarrow() {
        FieldLayer original = FieldLayer.builder("l")
                .primitives(primitive("a", SphereShape.ofRadius(1.0f), PrimitiveLink.NONE))
                .alpha(0.5f)
                .blendMode(BlendMode.ADD)
                .build();

        FieldLayer hidden = original.withVisible(false);

        assertFalse(hidden.visible());
        assertEquals(original.alpha(), hidden.alpha(), 1e-6f);
        assertEquals(original.blendMode(), hidden.blendMode());
        assertEquals(original.primitives(), hidden.primitives());
        assertTrue(original.visible(), "withVisible mutated the original");
    }

    @Test
    @DisplayName("looking a primitive up by id finds it, or says it is not there")
    void lookupByIdIsAnOptional() {
        FieldLayer layer = FieldLayer.of("l", List.of(
                primitive("a", SphereShape.ofRadius(1.0f), PrimitiveLink.NONE),
                primitive("b", SphereShape.ofRadius(2.0f), PrimitiveLink.NONE)));

        assertEquals("b", layer.primitive("b").orElseThrow().id());
        assertTrue(layer.primitive("nobody").isEmpty());
    }

    private static SimplePrimitive primitive(String id, Shape shape, PrimitiveLink link) {
        return new SimplePrimitive(id, shape.getType(), shape, Transform.IDENTITY,
                FillConfig.SOLID, VisibilityMask.FULL, ArrangementConfig.DEFAULT,
                Appearance.DEFAULT, Animation.NONE, link);
    }
}
