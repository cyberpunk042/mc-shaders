package net.cyberpunk042.mcshaders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.cyberpunk042.mcshaders.core.animation.Animation;
import net.cyberpunk042.mcshaders.core.animation.Axis;
import net.cyberpunk042.mcshaders.core.appearance.Appearance;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Checks the constraints one primitive can put on another.
 *
 * <p>A link is not a drawn wire — it is a rule: take your radius from that one, mirror
 * its position, inherit its colour, orbit in step with it. {@link LinkResolver} turns
 * those rules into concrete values, and every one of them is a place where a field can
 * come out subtly wrong: a ring that no longer hugs its sphere, a mirrored primitive on
 * the wrong axis, a satellite drifting out of phase.
 *
 * <h2>Where the first three cases come from</h2>
 *
 * <p>They were written in the mod this code came from, as {@code LinkResolverTest} with
 * a {@code main} method and {@code System.out.println} for assertions — in
 * {@code src/main}, where no build would ever run it. The scenarios were worth keeping
 * and the harness was not, so they are rewritten here as ordinary tests that fail
 * loudly. The rest cover the resolvers that had nothing at all.
 */
class LinkResolverTest {

    private static final float TOLERANCE = 1e-3f;

    // ── the three scenarios recovered from the mod ─────────────────────────────

    @Nested
    @DisplayName("radius matching")
    class RadiusMatching {

        @Test
        @DisplayName("a ring takes its radius from a sphere, plus the offset")
        void ringTakesRadiusFromSphere() {
            SimplePrimitive sphere = primitive("sphere_1", "sphere", SphereShape.ofRadius(2.0f),
                    PrimitiveLink.NONE);
            SimplePrimitive ring = primitive("ring_1", "ring", RingShape.at(0.8f, 1.0f, 0.0f),
                    PrimitiveLink.radiusMatch("sphere_1", 0.5f));

            LinkResolver.ResolvedValues resolved = LinkResolver.resolveLinks(ring, index(sphere));

            assertTrue(resolved.hasRadius(), "the link resolved to no radius at all");
            assertEquals(2.5f, resolved.radius(), TOLERANCE,
                    "expected the sphere's 2.0 plus the link's 0.5");
        }

        @Test
        @DisplayName("a negative offset pulls the radius in")
        void negativeOffsetShrinks() {
            SimplePrimitive sphere = primitive("s", "sphere", SphereShape.ofRadius(2.0f),
                    PrimitiveLink.NONE);
            SimplePrimitive ring = primitive("r", "ring", RingShape.at(0.8f, 1.0f, 0.0f),
                    PrimitiveLink.radiusMatch("s", -0.5f));

            assertEquals(1.5f, LinkResolver.resolveLinks(ring, index(sphere)).radius(), TOLERANCE);
        }

        @Test
        @DisplayName("linking to a primitive that is not there resolves to nothing")
        void missingTargetResolvesToNothing() {
            // The alternative — throwing, or quietly resolving to zero — turns one bad
            // id in a content pack into either a crash or an invisible primitive.
            SimplePrimitive ring = primitive("r", "ring", RingShape.at(0.8f, 1.0f, 0.0f),
                    PrimitiveLink.radiusMatch("nobody", 0.5f));

            assertFalse(LinkResolver.resolveLinks(ring, Map.of()).hasRadius(),
                    "a link to a missing primitive produced a radius");
        }
    }

    @Nested
    @DisplayName("declaration order")
    class DeclarationOrder {

        @Test
        @DisplayName("linking to something declared later is an error, and says which two")
        void forwardReferenceIsRejected() {
            // Resolution walks the list once, so a primitive can only see what came
            // before it. A forward reference silently resolves to nothing at render
            // time; catching it at validation is the difference between a message and
            // a mystery.
            SimplePrimitive ring = primitive("ring_1", "ring", RingShape.at(0.8f, 1.0f, 0.0f),
                    PrimitiveLink.radiusMatch("sphere_1", 0.0f));
            SimplePrimitive sphere = primitive("sphere_1", "sphere", SphereShape.ofRadius(2.0f),
                    PrimitiveLink.NONE);

            List<String> errors = LinkResolver.validate(List.of(ring, sphere));

            assertFalse(errors.isEmpty(), "a forward reference passed validation");
            assertTrue(errors.stream().anyMatch(e ->
                            e.contains("Forward reference") && e.contains("ring_1")
                                    && e.contains("sphere_1")),
                    "the error names neither end of the link: " + errors);
        }

        @Test
        @DisplayName("linking to something declared earlier is fine")
        void backwardReferenceIsAccepted() {
            SimplePrimitive sphere = primitive("sphere_1", "sphere", SphereShape.ofRadius(2.0f),
                    PrimitiveLink.NONE);
            SimplePrimitive ring = primitive("ring_1", "ring", RingShape.at(0.8f, 1.0f, 0.0f),
                    PrimitiveLink.radiusMatch("sphere_1", 0.5f));

            assertEquals(List.of(), LinkResolver.validate(List.of(sphere, ring)),
                    "a valid backward reference was rejected");
            assertTrue(LinkResolver.isValid(List.of(sphere, ring)));
        }

        @Test
        @DisplayName("a primitive linking to itself is an error")
        void selfReferenceIsRejected() {
            SimplePrimitive self = primitive("me", "sphere", SphereShape.ofRadius(1.0f),
                    PrimitiveLink.radiusMatch("me", 0.0f));

            assertFalse(LinkResolver.isValid(List.of(self)),
                    "a primitive linked to itself passed validation");
        }
    }

    // ── the resolvers that had no coverage at all ──────────────────────────────

    @Nested
    @DisplayName("mirroring")
    class Mirroring {

        @Test
        @DisplayName("each axis negates only its own component")
        void mirrorNegatesOneAxis() {
            org.joml.Vector3f original = new org.joml.Vector3f(1, 2, 3);

            assertVector(-1, 2, 3, LinkResolver.resolveMirror(
                    PrimitiveLink.mirror("t", Axis.X), original));
            assertVector(1, -2, 3, LinkResolver.resolveMirror(
                    PrimitiveLink.mirror("t", Axis.Y), original));
            assertVector(1, 2, -3, LinkResolver.resolveMirror(
                    PrimitiveLink.mirror("t", Axis.Z), original));
        }

        @Test
        @DisplayName("mirroring twice on the same axis is the identity")
        void mirroringIsItsOwnInverse() {
            org.joml.Vector3f original = new org.joml.Vector3f(1, 2, 3);
            PrimitiveLink link = PrimitiveLink.mirror("t", Axis.X);

            assertVector(1, 2, 3,
                    LinkResolver.resolveMirror(link, LinkResolver.resolveMirror(link, original)));
        }
    }

    @Nested
    @DisplayName("phase")
    class Phase {

        @Test
        @DisplayName("a phase offset comes back as it was given")
        void phaseOffsetPassesThrough() {
            assertEquals(0.25f,
                    LinkResolver.resolvePhaseOffset(
                            PrimitiveLink.builder("t").phaseOffset(0.25f).build()),
                    TOLERANCE);
        }

        @Test
        @DisplayName("orbit sync carries its own phase offset, separate from the animation one")
        void orbitPhaseIsSeparate() {
            // Two different clocks: one for the primitive's own animation, one for its
            // position on an orbit. Collapsing them puts a satellite in the wrong place.
            PrimitiveLink link = PrimitiveLink.orbitSync("t", 0.5f);

            assertEquals(0.5f, LinkResolver.resolveOrbitPhaseOffset(link), TOLERANCE);
            assertEquals(0f, LinkResolver.resolvePhaseOffset(link), TOLERANCE,
                    "orbit sync leaked its phase into the animation phase");
        }

        @Test
        @DisplayName("no link means no phase offset")
        void noLinkNoPhase() {
            assertEquals(0f, LinkResolver.resolvePhaseOffset(PrimitiveLink.NONE), TOLERANCE);
            assertEquals(0f, LinkResolver.resolveOrbitPhaseOffset(PrimitiveLink.NONE), TOLERANCE);
        }
    }

    @Nested
    @DisplayName("appearance")
    class InheritedAppearance {

        @Test
        @DisplayName("colour and alpha are inherited independently")
        void colourAndAlphaAreSeparate() {
            SimplePrimitive target = primitive("t", "sphere", SphereShape.ofRadius(1.0f),
                    PrimitiveLink.NONE);
            Map<String, Primitive> index = index(target);

            PrimitiveLink colourOnly = PrimitiveLink.appearance("t", true, false);
            PrimitiveLink alphaOnly = PrimitiveLink.appearance("t", false, true);

            assertNotNull(LinkResolver.resolveColor(colourOnly, index),
                    "asking for colour produced none");
            assertEquals(null, LinkResolver.resolveColor(alphaOnly, index),
                    "asking for alpha only produced a colour too");
            assertTrue(LinkResolver.resolveAlpha(alphaOnly, index) >= 0,
                    "asking for alpha produced none");
            assertTrue(LinkResolver.resolveAlpha(colourOnly, index) < 0,
                    "asking for colour only produced an alpha too");
        }
    }

    @Test
    @DisplayName("the builder combines constraints the named factories cannot")
    void builderCombinesConstraints() {
        SimplePrimitive target = primitive("core", "sphere", SphereShape.ofRadius(2.0f),
                PrimitiveLink.NONE);
        PrimitiveLink combined = PrimitiveLink.builder("core")
                .radiusMatch(true).radiusOffset(0.5f)
                .colorMatch(true)
                .build();

        Map<String, Primitive> index = index(target);
        assertEquals(2.5f, LinkResolver.resolveRadius(combined, index), TOLERANCE,
                "the radius half of the combined link did not resolve");
        assertNotNull(LinkResolver.resolveColor(combined, index),
                "the colour half of the combined link did not resolve");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static SimplePrimitive primitive(String id, String type, Shape shape,
                                             PrimitiveLink link) {
        return new SimplePrimitive(id, type, shape, Transform.IDENTITY, FillConfig.SOLID,
                VisibilityMask.FULL, ArrangementConfig.DEFAULT, Appearance.DEFAULT,
                Animation.NONE, link);
    }

    private static Map<String, Primitive> index(Primitive... primitives) {
        Map<String, Primitive> out = new HashMap<>();
        for (Primitive p : primitives) {
            out.put(p.id(), p);
        }
        return out;
    }

    private static void assertVector(float x, float y, float z, org.joml.Vector3f actual) {
        assertNotNull(actual, "expected a vector, got none");
        assertEquals(x, actual.x, TOLERANCE, "x");
        assertEquals(y, actual.y, TOLERANCE, "y");
        assertEquals(z, actual.z, TOLERANCE, "z");
    }
}
