package net.cyberpunk042.mcshaders.core;

import net.cyberpunk042.mcshaders.core.field.LinkResolver;
import net.cyberpunk042.mcshaders.core.field.Primitive;
import net.cyberpunk042.mcshaders.core.field.PrimitiveLink;
import net.cyberpunk042.mcshaders.core.field.SimplePrimitive;
import net.cyberpunk042.mcshaders.core.shape.RingShape;
import net.cyberpunk042.mcshaders.core.shape.SphereShape;
import net.cyberpunk042.mcshaders.core.animation.Axis;
import net.cyberpunk042.mcshaders.core.transform.Transform;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the "wires" half of the field model: one primitive taking a value from
 * another by id.
 *
 * <p>The upstream project checked this with a {@code main} method that printed
 * ticks and crosses and lived in {@code src/main/java}, so it never ran in a
 * build. The three cases it covered are the first three here; the rest are the
 * resolver surface it left untested.
 */
class LinkModelTest {

    private static SimplePrimitive sphere(String id, float radius) {
        return SimplePrimitive.of(id, "sphere", SphereShape.ofRadius(radius));
    }

    private static SimplePrimitive ring(String id, PrimitiveLink link) {
        return SimplePrimitive.of(id, "ring", RingShape.at(0.8f, 1.0f, 0.0f))
                .withLink(link);
    }

    // --- the three cases the upstream printf test covered -------------------

    @Test
    void radiusMatchTakesTargetRadiusPlusOffset() {
        Map<String, Primitive> index = Map.of("sphere_1", sphere("sphere_1", 2.0f));
        PrimitiveLink link = PrimitiveLink.radiusMatch("sphere_1", 0.5f);

        LinkResolver.ResolvedValues resolved =
                LinkResolver.resolveLinks(ring("ring_1", link), index);

        assertTrue(resolved.hasRadius());
        assertEquals(2.5f, resolved.radius(), 1.0E-4f);
    }

    @Test
    void forwardReferenceIsRejected() {
        List<Primitive> primitives = List.of(
                ring("ring_1", PrimitiveLink.radiusMatch("sphere_1", 0f)),
                sphere("sphere_1", 2.0f));

        List<String> errors = LinkResolver.validate(primitives);

        assertEquals(1, errors.size(), errors::toString);
        assertTrue(errors.get(0).contains("Forward reference"), errors.get(0));
        assertTrue(errors.get(0).contains("ring_1"), errors.get(0));
        assertTrue(errors.get(0).contains("sphere_1"), errors.get(0));
    }

    @Test
    void backwardReferenceIsAccepted() {
        List<Primitive> primitives = List.of(
                sphere("sphere_1", 2.0f),
                ring("ring_1", PrimitiveLink.radiusMatch("sphere_1", 0.5f)));

        assertTrue(LinkResolver.isValid(primitives), () -> LinkResolver.validate(primitives).toString());
    }

    // --- the rest of the resolver surface ----------------------------------

    @Test
    void selfReferenceIsRejected() {
        List<Primitive> primitives = List.of(ring("ring_1", PrimitiveLink.radiusMatch("ring_1", 0f)));

        List<String> errors = LinkResolver.validate(primitives);

        assertEquals(1, errors.size(), errors::toString);
        assertTrue(errors.get(0).contains("references itself"), errors.get(0));
    }

    @Test
    void referenceToSomethingThatDoesNotExistIsRejected() {
        List<Primitive> primitives = List.of(ring("ring_1", PrimitiveLink.radiusMatch("nope", 0f)));

        List<String> errors = LinkResolver.validate(primitives);

        assertEquals(1, errors.size(), errors::toString);
        assertTrue(errors.get(0).contains("Unknown reference"), errors.get(0));
    }

    @Test
    void duplicateIdsAreRejected() {
        List<Primitive> primitives = List.of(sphere("a", 1f), sphere("a", 2f));

        List<String> errors = LinkResolver.validate(primitives);

        assertEquals(1, errors.size(), errors::toString);
        assertTrue(errors.get(0).contains("Duplicate"), errors.get(0));
    }

    @Test
    void ringTargetContributesItsOuterRadius() {
        // Each shape family exposes a different field as "the" radius; a ring's is
        // its outer radius, not its inner one.
        Map<String, Primitive> index = Map.of("ring_1", ring("ring_1", PrimitiveLink.NONE));

        float resolved = LinkResolver.resolveRadius(PrimitiveLink.radiusMatch("ring_1", 0f), index);

        assertEquals(1.0f, resolved, 1.0E-4f);
    }

    @Test
    void radiusIsUnresolvedWhenTheTargetIsMissing() {
        // -1 rather than 0: a resolved radius of zero is a legitimate value, so the
        // caller has to be able to tell "no link" from "linked to something flat".
        assertEquals(-1f, LinkResolver.resolveRadius(PrimitiveLink.radiusMatch("gone", 0f), Map.of()));
    }

    @Test
    void mirrorInvertsOnlyTheNamedAxis() {
        Vector3f offset = new Vector3f(1, 2, 3);

        assertEquals(new Vector3f(-1, 2, 3), LinkResolver.resolveMirror(PrimitiveLink.mirror("t", Axis.X), offset));
        assertEquals(new Vector3f(1, -2, 3), LinkResolver.resolveMirror(PrimitiveLink.mirror("t", Axis.Y), offset));
        assertEquals(new Vector3f(1, 2, -3), LinkResolver.resolveMirror(PrimitiveLink.mirror("t", Axis.Z), offset));
    }

    @Test
    void mirrorDoesNotMutateTheOffsetItIsGiven() {
        Vector3f offset = new Vector3f(1, 2, 3);

        LinkResolver.resolveMirror(PrimitiveLink.mirror("t", Axis.X), offset);

        assertEquals(new Vector3f(1, 2, 3), offset);
    }

    @Test
    void followTakesTheTargetsOffset() {
        SimplePrimitive target = sphere("s", 1f).withTransform(Transform.offset(4, 5, 6));
        Map<String, Primitive> index = Map.of("s", target);

        assertEquals(new Vector3f(4, 5, 6), LinkResolver.resolveFollow(PrimitiveLink.follow("s"), index));
    }

    @Test
    void followIsNullWhenTheLinkDoesNotAskForIt() {
        Map<String, Primitive> index = Map.of("s", sphere("s", 1f));

        assertNull(LinkResolver.resolveFollow(PrimitiveLink.radiusMatch("s", 0f), index));
    }

    @Test
    void indexUpToStopsBeforeLaterPrimitives() {
        // This is what makes forward references unresolvable rather than merely
        // reported: at the point primitive i is resolved, i+1 is not in the index.
        List<Primitive> primitives = List.of(sphere("a", 1f), sphere("b", 2f), sphere("c", 3f));

        assertEquals(Map.of("a", primitives.get(0)).keySet(),
                LinkResolver.buildIndex(primitives, 0).keySet());
        assertEquals(3, LinkResolver.buildIndex(primitives).size());
    }
}
